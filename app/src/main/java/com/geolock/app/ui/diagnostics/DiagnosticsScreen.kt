package com.geolock.app.ui.diagnostics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.geolock.app.data.repository.SettingsRepository
import com.geolock.app.data.repository.ZoneRepository
import com.geolock.app.domain.DiagnosticStatus
import com.geolock.app.domain.ProtectionMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    zoneRepository: ZoneRepository,
    monitor: ProtectionMonitor
) : ViewModel() {
    val status = combine(settingsRepository.settings, zoneRepository.zones) { settings, zones ->
        monitor.diagnostics(settings, zones)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DiagnosticStatus(
            locationGranted = false,
            backgroundLocationGranted = false,
            locationServicesOn = false,
            geofencingReady = false,
            accessibilityEnabled = false,
            protectionEnabled = false,
            lastLocationEvent = "",
            lastForegroundApp = "",
            lastBlockedAttempt = "",
            locationUncertain = false,
            activeZoneNames = emptyList()
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
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
            CheckRow("Location", status.locationGranted)
            CheckRow("Background location", status.backgroundLocationGranted)
            CheckRow("Location services", status.locationServicesOn)
            CheckRow("Geofencing", status.geofencingReady)
            CheckRow("Accessibility Service", status.accessibilityEnabled)
            CheckRow("Protection", status.protectionEnabled)
            Spacer(Modifier.height(20.dp))
            Text("Current zones")
            Text(status.activeZoneNames.joinToString(", ").ifBlank { "None" })
            Spacer(Modifier.height(16.dp))
            Text("Last location event")
            Text(status.lastLocationEvent.ifBlank { "—" })
            Spacer(Modifier.height(16.dp))
            Text("Last foreground app")
            Text(status.lastForegroundApp.ifBlank { "—" })
            Spacer(Modifier.height(16.dp))
            Text("Last blocked attempt")
            Text(status.lastBlockedAttempt.ifBlank { "—" })
            if (status.locationUncertain) {
                Spacer(Modifier.height(16.dp))
                Text("Location is uncertain. GeoLock is keeping the last known zone state.")
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, ok: Boolean) {
    Text("$label: ${if (ok) "✓" else "✗"}")
    Spacer(Modifier.height(8.dp))
}
