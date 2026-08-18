package xyz.mufanc.parrot

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.service.notification.INotificationListener
import android.service.notification.StatusBarNotification
import java.lang.reflect.InvocationTargetException
import kotlin.system.exitProcess

class ParrotUserService(private val context: Context) : IParrotService.Stub(), FrameworkNotificationListener.Callback {
    private val listener = FrameworkNotificationListener(this)
    private val users = sourceUsers()
    private var manager: Any? = null
    @Volatile private var notificationSink: PendingIntent? = null
    @Volatile private var selectedUserId: Int? = null
    @Volatile private var registeredUserId: Int? = null
    @Volatile private var listening = false
    @Volatile private var lastNotification: String? = null
    @Volatile private var error: String? = null

    override fun getState(): Bundle = Bundle().apply {
        putIntArray(STATE_USER_IDS, users.map(SourceUser::id).toIntArray())
        putStringArray(STATE_USER_NAMES, users.map(SourceUser::name).toTypedArray())
        putInt(STATE_SELECTED_USER, selectedUserId ?: USER_NULL)
        putBoolean(STATE_LISTENING, listening)
        putString(STATE_LAST_NOTIFICATION, lastNotification)
        putString(STATE_ERROR, error)
    }

    override fun selectUser(userId: Int) {
        check(!listening && users.any { it.id == userId })
        selectedUserId = userId
        error = null
    }

    override fun startListening() {
        runCatching {
            val userId = checkNotNull(selectedUserId) { "Select a source user first." }
            val service = notificationManager()
            registeredUserId = userId
            manager = service
            service.javaClass.getMethod(
                "registerListener",
                INotificationListener::class.java,
                ComponentName::class.java,
                Int::class.javaPrimitiveType,
            ).invoke(service, listener, ComponentName(context, ParrotUserService::class.java), userId)
            listening = true
            error = null
        }.onFailure {
            registeredUserId = null
            listening = false
            error = it.message()
        }
    }

    override fun stopListening() {
        val service = manager
        val userId = registeredUserId
        if (service != null && userId != null) {
            runCatching {
                service.javaClass.getMethod(
                    "unregisterListener",
                    INotificationListener::class.java,
                    Int::class.javaPrimitiveType,
                ).invoke(service, listener, userId)
            }.onFailure { error = it.message() }
        }
        manager = null
        registeredUserId = null
        listening = false
    }

    override fun setNotificationSink(sink: PendingIntent) {
        notificationSink = sink
    }

    override fun destroy() {
        stopListening()
        exitProcess(0)
    }

    override fun onConnected() {
        listening = registeredUserId != null
        if (listening) {
            runCatching(::reconcile).onSuccess { error = null }.onFailure { error = it.message() }
        }
    }

    override fun onPosted(notification: StatusBarNotification) {
        val userId = registeredUserId ?: return
        if (notification.userId != userId) return
        if (notification.isSummary()) {
            runCatching { remove(notification.key) }.onFailure { error = it.message() }
            return
        }
        runCatching {
            mirror(notification, userId)
            error = null
        }.onFailure { error = it.message() }
    }

    override fun onRemoved(notification: StatusBarNotification) {
        if (notification.userId == registeredUserId) {
            runCatching { remove(notification.key) }.onFailure { error = it.message() }
        }
    }

    private fun reconcile() {
        val userId = checkNotNull(registeredUserId)
        val active = activeNotifications()
            .filter { it.userId == userId && !it.isSummary() }
        active.forEach { mirror(it, userId) }
        deliver(
            Intent().putExtra(
                MirrorReceiver.EXTRA_SYNC_KEYS,
                active.map(StatusBarNotification::getKey).toTypedArray(),
            ),
        )
    }

    private fun mirror(notification: StatusBarNotification, userId: Int) {
        val extras = notification.notification.extras
        val appName = appName(notification.packageName, userId)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            .ifBlank { appName }
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        deliver(
            Intent()
                .putExtra(MirrorReceiver.EXTRA_KEY, notification.key)
                .putExtra(MirrorReceiver.EXTRA_TITLE, title)
                .putExtra(MirrorReceiver.EXTRA_TEXT, text)
                .putExtra(MirrorReceiver.EXTRA_SUBTEXT, appName)
                .putExtra(MirrorReceiver.EXTRA_WHEN, notification.postTime)
                .putExtra(MirrorReceiver.EXTRA_ONGOING, notification.isOngoing),
        )
        lastNotification = "$appName: $title"
    }

    private fun remove(key: String) {
        deliver(
            Intent()
                .putExtra(MirrorReceiver.EXTRA_KEY, key)
                .putExtra(MirrorReceiver.EXTRA_REMOVE, true),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun activeNotifications(): List<StatusBarNotification> {
        val service = checkNotNull(manager)
        val getActive = service.javaClass.methods.single { it.name == "getActiveNotificationsFromListener" }
        val slice = getActive.invoke(service, listener, null, 0)
        return slice.javaClass.getMethod("getList").invoke(slice) as List<StatusBarNotification>
    }

    private fun StatusBarNotification.isSummary(): Boolean =
        notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

    private fun deliver(intent: Intent) {
        checkNotNull(notificationSink) { "Open Parrot once to initialize notification delivery." }
            .send(context, 0, intent)
    }

    private fun sourceUsers(): List<SourceUser> {
        val service = userManager()
        val getUsers = service.javaClass.methods.single { it.name == "getUsers" }
        val rawUsers = getUsers.invoke(service, *Array(getUsers.parameterCount) { true }) as List<*>
        val currentUserId = Process.myUid() / 100_000
        return rawUsers.mapNotNull { info ->
            info ?: return@mapNotNull null
            val id = info.javaClass.getField("id").getInt(info)
            val type = info.javaClass.getField("userType").get(info) as? String
            if (!isSelectableSourceUser(id, type, currentUserId)) return@mapNotNull null
            SourceUser(id, (info.javaClass.getField("name").get(info) as? String).orEmpty().ifBlank { "User $id" })
        }.sortedBy(SourceUser::id)
    }

    private fun userManager(): Any = systemService("android.os.IUserManager\$Stub", Context.USER_SERVICE)

    private fun notificationManager(): Any =
        systemService("android.app.INotificationManager\$Stub", Context.NOTIFICATION_SERVICE)

    private fun appName(packageName: String, userId: Int): String = runCatching {
        val service = systemService("android.content.pm.IPackageManager\$Stub", "package")
        val getApplicationInfo = service.javaClass.methods.single { it.name == "getApplicationInfo" }
        val flags = if (getApplicationInfo.parameterTypes[1] == Long::class.javaPrimitiveType) 0L else 0
        val info = getApplicationInfo.invoke(service, packageName, flags, userId) as ApplicationInfo
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    private fun systemService(stubClass: String, serviceName: String): Any {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, serviceName) as IBinder
        return requireNotNull(
            Class.forName(stubClass)
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder),
        )
    }

    private fun Throwable.message(): String {
        val cause = (this as? InvocationTargetException)?.targetException ?: this
        return cause.message ?: cause.javaClass.simpleName
    }

}
