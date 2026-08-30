package com.geolock.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geolock.app.domain.UnlockDurations
import com.geolock.app.ui.components.HorizontalDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onChangeKey: () -> Unit,
    onDiagnostics: () -> Unit,
    onLogs: () -> Unit,
    onManageApps: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val monitor = viewModel.protectionMonitor

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            SectionTitle("Security")
            TextButton(onClick = onChangeKey) { Text("Change unlock key") }
            Spacer(Modifier.height(8.dp))
            Text("Temporary unlock duration", style = MaterialTheme.typography.bodyMedium)
            val options = UnlockDurations.OPTIONS + UnlockDurations.UNTIL_LEAVE
            options.forEach { minutes ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = settings.unlockDurationMinutes == minutes,
                            onClick = { viewModel.setUnlockDuration(minutes) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = settings.unlockDurationMinutes == minutes,
                        onClick = { viewModel.setUnlockDuration(minutes) }
                    )
                    Text(UnlockDurations.label(minutes), modifier = Modifier.padding(start = 8.dp))
                }
            }
            SettingSwitch(
                title = "Require key every time",
                subtitle = "Ignore temporary unlocks and ask for the key on each launch.",
                checked = settings.requireKeyEveryTime,
                onCheckedChange = viewModel::setRequireKey
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            SectionTitle("Location")
            Text("Default zone radius  ${settings.defaultRadiusMeters.toInt()} m")
            Slider(
                value = settings.defaultRadiusMeters,
                onValueChange = viewModel::setDefaultRadius,
                valueRange = 50f..1000f
            )
            Text(
                "Location ${if (monitor.hasFineLocation()) "✓" else "✗"}   Background ${if (monitor.hasBackgroundLocation()) "✓" else "✗"}   Services ${if (monitor.locationServicesOn()) "✓" else "✗"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = viewModel::refreshGeofences) { Text("Re-register geofences") }
            TextButton(onClick = viewModel::evaluateLocation) { Text("Recheck current location") }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            SectionTitle("Applications")
            Text("Blocked apps are chosen per zone. Open a zone to change them.")
            TextButton(onClick = onManageApps) { Text("Go to zones") }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            SectionTitle("Protection")
            SettingSwitch(
                title = "Enable protection",
                subtitle = "Turn geographic locking on or off.",
                checked = settings.protectionEnabled,
                onCheckedChange = viewModel::setProtection
            )
            Text(
                "Accessibility ${if (monitor.isAccessibilityEnabled()) "✓" else "✗"}   Geofencing ${if (settings.geofencesRegistered) "✓" else "✗"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open accessibility settings") }
            Spacer(Modifier.height(8.dp))
            Text(
                "Factory reset from Settings is blocked while protection is on. A recovery wipe can still happen if someone has the phone and uses hardware buttons.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(com.geolock.app.service.GeoLockDeviceAdminReceiver.enableIntent(context))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Enable uninstall protection") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val prepare = android.net.VpnService.prepare(context)
                    if (prepare != null) context.startActivity(prepare)
                    else com.geolock.app.service.DnsVpnService.start(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Enable domain filter") }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            SectionTitle("Diagnostics")
            TextButton(onClick = onDiagnostics) { Text("Open diagnostics") }
            TextButton(onClick = onLogs) { Text("Activity log") }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeKeyScreen(
    viewModel: ChangeKeyViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Change unlock key") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(20.dp)) {
            androidx.compose.material3.OutlinedTextField(
                value = state.current,
                onValueChange = viewModel::updateCurrent,
                label = { Text("Current key") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedTextField(
                value = state.next,
                onValueChange = viewModel::updateNext,
                label = { Text("New key") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedTextField(
                value = state.confirm,
                onValueChange = viewModel::updateConfirm,
                label = { Text("Confirm new key") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            if (state.message != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    state.message!!,
                    color = if (state.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text("Update key")
            }
        }
    }
}
