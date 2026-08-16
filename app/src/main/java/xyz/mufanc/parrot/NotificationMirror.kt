package xyz.mufanc.parrot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.service.notification.INotificationListener
import android.service.notification.StatusBarNotification
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.InvocationTargetException

data class SourceUser(val id: Int, val name: String)

data class MirrorState(
    val shizukuConnected: Boolean = false,
    val shizukuGranted: Boolean = false,
    val sourceUsers: List<SourceUser> = emptyList(),
    val selectedUserId: Int? = null,
    val listening: Boolean = false,
    val lastNotification: String? = null,
    val error: String? = null,
)

class NotificationMirror(
    context: Context,
    private val onStateChanged: (MirrorState) -> Unit,
) : FrameworkNotificationListener.Callback {
    private val context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val listener = FrameworkNotificationListener(this)
    private var manager: Any? = null
    @Volatile private var registeredUserId: Int? = null
    private var state = MirrorState()

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        manager = null
        registeredUserId = null
        publish(
            state.copy(
                shizukuConnected = false,
                shizukuGranted = false,
                sourceUsers = emptyList(),
                selectedUserId = null,
                listening = false,
            ),
        )
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST) {
            refresh()
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                publish(state.copy(error = context.getString(R.string.shizuku_permission_denied)))
            }
        }
    }

    init {
        notifications.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.mirrored_notifications),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun refresh() {
        val connected = Shizuku.pingBinder()
        val granted = connected && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            publish(
                state.copy(
                    shizukuConnected = connected,
                    shizukuGranted = false,
                    sourceUsers = emptyList(),
                    selectedUserId = null,
                    listening = false,
                    error = if (connected) state.error else null,
                ),
            )
            return
        }
        runCatching { sourceUsers() }
            .onSuccess { users ->
                val selectedUserId = preferences.getInt(SELECTED_USER, Int.MIN_VALUE)
                    .takeIf { selected -> users.any { it.id == selected } }
                publish(
                    state.copy(
                        shizukuConnected = true,
                        shizukuGranted = true,
                        sourceUsers = users,
                        selectedUserId = selectedUserId,
                        listening = state.listening,
                        error = null,
                    ),
                )
            }
            .onFailure(::publishError)
    }

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST) }
            .onFailure(::publishError)
    }

    fun selectUser(userId: Int) {
        check(!state.listening && state.sourceUsers.any { it.id == userId })
        preferences.edit().putInt(SELECTED_USER, userId).apply()
        publish(state.copy(selectedUserId = userId, error = null))
    }

    fun startListening() {
        runCatching {
            check(Shizuku.pingBinder()) { context.getString(R.string.shizuku_unavailable) }
            check(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                context.getString(R.string.shizuku_permission_required)
            }
            val userId = checkNotNull(state.selectedUserId) {
                context.getString(R.string.source_user_required)
            }
            val service = notificationManager()
            registeredUserId = userId
            service.javaClass.getMethod(
                "registerListener",
                INotificationListener::class.java,
                ComponentName::class.java,
                Int::class.javaPrimitiveType,
            ).invoke(service, listener, ComponentName(context, MainActivity::class.java), userId)
            manager = service
            publish(state.copy(listening = true, error = null))
        }.onFailure {
            registeredUserId = null
            publishError(it)
        }
    }

    fun stopListening() {
        val service = manager
        val userId = registeredUserId
        if (service != null && userId != null) {
            runCatching {
                service.javaClass.getMethod(
                    "unregisterListener",
                    INotificationListener::class.java,
                    Int::class.javaPrimitiveType,
                ).invoke(service, listener, userId)
            }.onFailure(::publishError)
        }
        manager = null
        registeredUserId = null
        publish(state.copy(listening = false))
    }

    fun close() {
        stopListening()
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
    }

    override fun onConnected() {
        publish(state.copy(listening = true, error = null))
    }

    @Suppress("DEPRECATION")
    override fun onPosted(notification: StatusBarNotification) {
        val userId = registeredUserId ?: return
        if (notification.userId != userId) return
        mainHandler.post {
            val extras = notification.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                .ifBlank { notification.packageName }
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            val mirrored = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_parrot)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText("${notification.packageName} · user $userId")
                .setWhen(notification.postTime)
                .setLocalOnly(true)
                .setOnlyAlertOnce(true)
                .setOngoing(notification.isOngoing)
                .build()
            notifications.notify(notification.key, MIRROR_NOTIFICATION_ID, mirrored)
            publish(state.copy(lastNotification = "${notification.packageName}: $title", error = null))
        }
    }

    @Suppress("DEPRECATION")
    override fun onRemoved(notification: StatusBarNotification) {
        if (notification.userId == registeredUserId) {
            notifications.cancel(notification.key, MIRROR_NOTIFICATION_ID)
        }
    }

    private fun sourceUsers(): List<SourceUser> {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, Context.USER_SERVICE) as IBinder
        val service = requireNotNull(
            Class.forName("android.os.IUserManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, ShizukuBinderWrapper(binder)),
        )
        val getUsers = service.javaClass.methods.single { it.name == "getUsers" }
        val users = getUsers.invoke(service, *Array(getUsers.parameterCount) { true }) as List<*>
        val currentUserId = Process.myUid() / 100_000
        return users.mapNotNull { info ->
            info ?: return@mapNotNull null
            val id = info.javaClass.getField("id").getInt(info)
            val type = info.javaClass.getField("userType").get(info) as? String
            if (!isSelectableSourceUser(id, type, currentUserId)) return@mapNotNull null
            SourceUser(id, (info.javaClass.getField("name").get(info) as? String).orEmpty().ifBlank { "User $id" })
        }.sortedBy(SourceUser::id)
    }

    private fun notificationManager(): Any {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, Context.NOTIFICATION_SERVICE) as IBinder
        return requireNotNull(
            Class.forName("android.app.INotificationManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, ShizukuBinderWrapper(binder)),
        )
    }

    private fun publishError(error: Throwable) {
        val cause = (error as? InvocationTargetException)?.targetException ?: error
        publish(state.copy(error = cause.message ?: cause.javaClass.simpleName, listening = false))
    }

    private fun publish(newState: MirrorState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            state = newState
            onStateChanged(state)
        } else {
            mainHandler.post { publish(newState) }
        }
    }

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST = 1
        private const val SELECTED_USER = "selected_user"
        private const val CHANNEL_ID = "mirrored_notifications"
        private const val MIRROR_NOTIFICATION_ID = 1
    }
}

internal fun isSelectableSourceUser(id: Int, userType: String?, currentUserId: Int): Boolean =
    id != currentUserId && userType?.startsWith("android.os.usertype.full.") == true
