package xyz.mufanc.parrot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku
import java.lang.reflect.InvocationTargetException
import androidx.core.content.edit

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
) {
    private val context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private var remote: IParrotService? = null
    private var binding = false
    private var state = MirrorState()

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(context, ParrotUserService::class.java),
    ).daemon(true)
        .processNameSuffix("service")
        .tag("parrot")
        .version(2)

    private val notificationSink = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, MirrorReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            binding = false
            remote = IParrotService.Stub.asInterface(binder)
            remote?.setNotificationSink(notificationSink)
            syncState()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            binding = false
            remote = null
            publish(state.copy(sourceUsers = emptyList(), selectedUserId = null, listening = false))
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        binding = false
        remote = null
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
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
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
        publish(state.copy(shizukuConnected = true, shizukuGranted = true, error = null))
        if (remote != null) syncState() else bindService()
    }

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST) }
            .onFailure(::publishError)
    }

    fun selectUser(userId: Int) {
        runRemote { service ->
            service.selectUser(userId)
            preferences.edit { putInt(SELECTED_USER, userId) }
        }
    }

    fun startListening() = runRemote(IParrotService::startListening)

    fun stopListening() = runRemote(IParrotService::stopListening)

    fun close() {
        if (binding || remote != null) {
            runCatching { Shizuku.unbindUserService(serviceArgs, serviceConnection, false) }
        }
        binding = false
        remote = null
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
    }

    private fun bindService() {
        if (binding) return
        runCatching {
            check(Shizuku.getVersion() >= 13) { context.getString(R.string.shizuku_version_required) }
            binding = true
            Shizuku.bindUserService(serviceArgs, serviceConnection)
        }.onFailure {
            binding = false
            publishError(it)
        }
    }

    private fun runRemote(block: (IParrotService) -> Unit) {
        runCatching {
            val service = checkNotNull(remote) { context.getString(R.string.user_service_unavailable) }
            block(service)
            syncState()
        }.onFailure(::publishError)
    }

    private fun syncState() {
        runCatching {
            val service = checkNotNull(remote)
            var remoteState = service.state
            val users = remoteState.sourceUsers()
            var selectedUserId = remoteState.selectedUserId()
            if (selectedUserId == null) {
                preferences.getInt(SELECTED_USER, USER_NULL)
                    .takeIf { saved -> users.any { it.id == saved } }
                    ?.let {
                        service.selectUser(it)
                        remoteState = service.state
                        selectedUserId = it
                    }
            } else {
                preferences.edit { putInt(SELECTED_USER, selectedUserId!!) }
            }
            publish(
                MirrorState(
                    shizukuConnected = true,
                    shizukuGranted = true,
                    sourceUsers = remoteState.sourceUsers(),
                    selectedUserId = selectedUserId,
                    listening = remoteState.getBoolean(STATE_LISTENING),
                    lastNotification = remoteState.getString(STATE_LAST_NOTIFICATION),
                    error = remoteState.getString(STATE_ERROR),
                ),
            )
        }.onFailure(::publishError)
    }

    private fun Bundle.sourceUsers(): List<SourceUser> {
        val ids = getIntArray(STATE_USER_IDS) ?: intArrayOf()
        val names = getStringArray(STATE_USER_NAMES) ?: emptyArray()
        return ids.indices.map { index -> SourceUser(ids[index], names.getOrElse(index) { "User ${ids[index]}" }) }
    }

    private fun Bundle.selectedUserId(): Int? = getInt(STATE_SELECTED_USER, USER_NULL).takeUnless { it == USER_NULL }

    private fun publishError(error: Throwable) {
        val cause = (error as? InvocationTargetException)?.targetException ?: error
        publish(state.copy(error = cause.message ?: cause.javaClass.simpleName))
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
    }
}

internal const val USER_NULL = -10_000
internal const val STATE_USER_IDS = "user_ids"
internal const val STATE_USER_NAMES = "user_names"
internal const val STATE_SELECTED_USER = "selected_user"
internal const val STATE_LISTENING = "listening"
internal const val STATE_LAST_NOTIFICATION = "last_notification"
internal const val STATE_ERROR = "error"

internal fun isSelectableSourceUser(id: Int, userType: String?, currentUserId: Int): Boolean =
    id != currentUserId && userType?.startsWith("android.os.usertype.full.") == true
