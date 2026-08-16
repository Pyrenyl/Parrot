package xyz.mufanc.parrot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lsposed.hiddenapibypass.HiddenApiBypass

class MainActivity : ComponentActivity() {
    private var state by mutableStateOf(MirrorState())
    private lateinit var mirror: NotificationMirror

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
        mirror = NotificationMirror(this) { state = it }
        mirror.start()
        setContent { ParrotScreen() }
    }

    override fun onResume() {
        super.onResume()
        mirror.refresh()
    }

    override fun onDestroy() {
        mirror.close()
        super.onDestroy()
    }

    @Composable
    private fun ParrotScreen() {
        var notificationGranted by remember {
            mutableStateOf(
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            )
        }
        val requestNotifications = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { notificationGranted = it }

        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        stringResource(R.string.tagline),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()

                    SourceUserSelector()
                    StatusRow(
                        stringResource(R.string.shizuku),
                        stringResource(if (state.shizukuConnected) R.string.connected else R.string.unavailable),
                    )
                    StatusRow(
                        stringResource(R.string.shizuku_permission),
                        stringResource(if (state.shizukuGranted) R.string.granted else R.string.required),
                    )
                    StatusRow(
                        stringResource(R.string.listener),
                        stringResource(if (state.listening) R.string.listening else R.string.stopped),
                    )
                    StatusRow(
                        stringResource(R.string.notification_permission),
                        stringResource(if (notificationGranted) R.string.granted else R.string.required),
                    )

                    state.lastNotification?.let {
                        Text(stringResource(R.string.last_notification, it))
                    }
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }

                    if (state.shizukuConnected && !state.shizukuGranted) {
                        Button(onClick = mirror::requestPermission, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.grant_shizuku))
                        }
                    }
                    if (state.shizukuGranted) {
                        Button(
                            onClick = if (state.listening) mirror::stopListening else mirror::startListening,
                            enabled = state.listening || state.selectedUserId != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(if (state.listening) R.string.stop_listening else R.string.start_listening))
                        }
                    }
                    if (!notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Button(
                            onClick = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.grant_notification_permission))
                        }
                    }
                    Text(
                        stringResource(R.string.prototype_notice),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    @Composable
    private fun SourceUserSelector() {
        var expanded by remember { mutableStateOf(false) }
        val selected = state.sourceUsers.firstOrNull { it.id == state.selectedUserId }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.source_user))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { expanded = true },
                    enabled = !state.listening && state.sourceUsers.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        selected?.let { "${it.name} (user ${it.id})" }
                            ?: stringResource(
                                if (state.sourceUsers.isEmpty()) R.string.no_source_users
                                else R.string.select_source_user,
                            ),
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    state.sourceUsers.forEach { user ->
                        DropdownMenuItem(
                            text = { Text("${user.name} (user ${user.id})") },
                            onClick = {
                                mirror.selectUser(user.id)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
