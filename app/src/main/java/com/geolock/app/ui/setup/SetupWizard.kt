package com.geolock.app.ui.setup

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geolock.app.ui.apps.AppList
import com.geolock.app.ui.components.ScreenTitle
import com.geolock.app.ui.components.SectionCard
import com.geolock.app.ui.components.WarningCard

@Composable
fun SetupWizard(
    viewModel: SetupViewModel,
    onFinished: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        LinearProgressIndicator(
            progress = { (state.step + 1) / 6f },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        when (state.step) {
            0 -> WelcomeStep(onNext = viewModel::next)
            1 -> PermissionsStep(viewModel = viewModel, onNext = viewModel::next, onBack = viewModel::back)
            2 -> KeyStep(state = state, viewModel = viewModel, onBack = viewModel::back)
            3 -> FirstZoneStep(state = state, viewModel = viewModel, onNext = viewModel::next, onBack = viewModel::back)
            4 -> FirstAppsStep(state = state, viewModel = viewModel, onNext = viewModel::next, onBack = viewModel::back)
            else -> EnableStep(state = state, onBack = viewModel::back, onFinish = { viewModel.finish(onFinished) })
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("Welcome to GeoLock", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Text(
                "GeoLock restricts selected apps when you’re inside specific locations.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Text("Define a place, pick the apps that get in the way, and GeoLock keeps them locked while you stay there.")
        }
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Get Started") }
    }
}

@Composable
private fun PermissionsStep(
    viewModel: SetupViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val monitor = viewModel.protectionMonitor
    var refresh by remember { mutableStateOf(0) }
    val fine = remember(refresh) { monitor.hasFineLocation() }
    val background = remember(refresh) { monitor.hasBackgroundLocation() }
    val notifications = remember(refresh) { monitor.hasNotifications() }
    val accessibility = remember(refresh) { monitor.isAccessibilityEnabled() }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            ScreenTitle("Permissions", "GeoLock only uses these locally, on this phone.")
            Spacer(Modifier.height(20.dp))
            PermissionCard(
                title = "Location",
                body = "Needed to know when you enter or leave a zone.",
                granted = fine,
                action = "Allow location"
            ) {
                locationLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                title = "Background location",
                body = "Needed so zones still work when GeoLock is not open.",
                granted = background,
                action = "Allow all the time"
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    })
                }
            }
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                title = "Notifications",
                body = "Shows whether protection is active, and warns you if it stops.",
                granted = notifications,
                action = "Allow notifications"
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                title = "Accessibility service",
                body = "Watches which app is in front so a lock screen can appear. It does not read your content.",
                granted = accessibility,
                action = "Open accessibility settings"
            ) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            Spacer(Modifier.height(12.dp))
            val adminGranted = remember(refresh) {
                com.geolock.app.service.GeoLockDeviceAdminReceiver.isActive(context)
            }
            PermissionCard(
                title = "Prevent uninstall",
                body = "Makes GeoLock a device admin so it cannot be removed until you unlock Settings and turn this off.",
                granted = adminGranted,
                action = "Enable device admin"
            ) {
                context.startActivity(com.geolock.app.service.GeoLockDeviceAdminReceiver.enableIntent(context))
            }
            Spacer(Modifier.height(12.dp))
            val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                refresh++
            }
            val vpnReady = remember(refresh) {
                !com.geolock.app.service.DnsVpnService.needsConsent(context)
            }
            PermissionCard(
                title = "Domain filter",
                body = "A local VPN that only filters DNS on this phone, so listed websites fail inside a zone.",
                granted = vpnReady,
                action = "Allow VPN"
            ) {
                val prepare = android.net.VpnService.prepare(context)
                if (prepare != null) vpnLauncher.launch(prepare)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Allow unrestricted battery use") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { refresh++ }) { Text("I’ve enabled them — refresh") }
        }
        Column {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    granted: Boolean,
    action: String,
    onClick: () -> Unit
) {
    SectionCard {
        Text(title, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (granted) {
            Text("Enabled", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        } else {
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(action) }
        }
    }
}

@Composable
private fun KeyStep(state: SetupUiState, viewModel: SetupViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            ScreenTitle("Create unlock key", "This key is stored on the device, never as plaintext.")
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = state.key,
                onValueChange = viewModel::updateKey,
                label = { Text("Unlock key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.confirmKey,
                onValueChange = viewModel::updateConfirm,
                label = { Text("Confirm key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            if (state.keyError != null) {
                Spacer(Modifier.height(12.dp))
                Text(state.keyError, color = MaterialTheme.colorScheme.error)
            }
        }
        Column {
            Button(onClick = { viewModel.saveKey() }, modifier = Modifier.fillMaxWidth()) { Text("Save key") }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        }
    }
}

@Composable
private fun FirstZoneStep(
    state: SetupUiState,
    viewModel: SetupViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenTitle("Create your first zone", "A place where selected apps should lock.")
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = state.zoneName,
            onValueChange = viewModel::updateZoneName,
            label = { Text("Zone name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search a place") },
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = { viewModel.searchLocation(query) }) { Text("Search") }
        OutlinedButton(onClick = viewModel::useCurrentLocation, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.locating) "Finding location…" else "Use current location")
        }
        Spacer(Modifier.height(12.dp))
        if (state.latitude != null && state.longitude != null) {
            Text(state.locationLabel.ifBlank { "Selected location" }, fontWeight = FontWeight.Medium)
            Text(
                "Lat ${"%.5f".format(state.latitude)}  ·  Lng ${"%.5f".format(state.longitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Radius  ${state.radius.toInt()} m")
        Slider(
            value = state.radius,
            onValueChange = viewModel::updateRadius,
            valueRange = 50f..1000f
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onNext,
            enabled = state.latitude != null && state.zoneName.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Continue") }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun FirstAppsStep(
    state: SetupUiState,
    viewModel: SetupViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Blocked applications", "Choose apps that should lock inside this zone.")
        Spacer(Modifier.height(16.dp))
        AppList(
            apps = state.apps,
            selected = state.selectedPackages,
            onToggle = viewModel::toggleApp,
            modifier = Modifier.weight(1f)
        )
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun EnableStep(
    state: SetupUiState,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            ScreenTitle("Enable protection", "GeoLock will start watching your zones.")
            Spacer(Modifier.height(16.dp))
            WarningCard("If Accessibility or location is later turned off, GeoLock will warn you instead of failing silently.")
            Spacer(Modifier.height(16.dp))
            Text("You can change zones, apps, and the unlock key any time from Settings.")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = onFinish, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.saving) "Starting…" else "Enable protection")
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        }
    }
}
