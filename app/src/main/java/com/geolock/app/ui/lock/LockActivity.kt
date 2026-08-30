package com.geolock.app.ui.lock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geolock.app.domain.UnlockDurations
import com.geolock.app.domain.UnlockMode
import com.geolock.app.ui.theme.GeoLockTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty().ifBlank { packageName }
        val zoneNames = intent.getStringExtra(EXTRA_ZONE_NAMES).orEmpty()
        val zoneId = intent.getStringExtra(EXTRA_ZONE_ID).orEmpty()
        val minutes = intent.getIntExtra(EXTRA_UNLOCK_MINUTES, 5)
        val mode = runCatching {
            UnlockMode.valueOf(intent.getStringExtra(EXTRA_UNLOCK_MODE) ?: UnlockMode.TEMPORARY.name)
        }.getOrDefault(UnlockMode.TEMPORARY)
        val systemLock = intent.getBooleanExtra(EXTRA_SYSTEM_LOCK, false)
        val resetBlocked = intent.getBooleanExtra(EXTRA_RESET_BLOCKED, false)

        setContent {
            GeoLockTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (resetBlocked) {
                        ResetBlockedScreen(onHome = { goHome() })
                        return@Surface
                    }
                    LockScreen(
                        appName = if (systemLock) "Settings hidden" else appName,
                        zoneNames = zoneNames,
                        durationMinutes = minutes,
                        mode = mode,
                        systemLock = systemLock,
                        onUnlock = { viewModel ->
                            viewModel.unlock(packageName, appName, zoneId, zoneNames, mode, minutes, systemLock)
                        },
                        onUnlocked = { finish() }
                    )
                }
            }
        }
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "package_name"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_ZONE_NAMES = "zone_names"
        const val EXTRA_ZONE_ID = "zone_id"
        const val EXTRA_UNLOCK_MINUTES = "unlock_minutes"
        const val EXTRA_UNLOCK_MODE = "unlock_mode"
        const val EXTRA_SYSTEM_LOCK = "system_lock"
        const val EXTRA_RESET_BLOCKED = "reset_blocked"
    }
}

@Composable
private fun ResetBlockedScreen(onHome: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔒", fontSize = 48.sp)
        Spacer(Modifier.height(20.dp))
        Text("Reset blocked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Factory reset is turned off while GeoLock protection is on.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Go home") }
    }
}

@Composable
private fun LockScreen(
    appName: String,
    zoneNames: String,
    durationMinutes: Int,
    mode: UnlockMode,
    systemLock: Boolean = false,
    viewModel: LockViewModel = hiltViewModel(),
    onUnlock: (LockViewModel) -> Unit,
    onUnlocked: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.unlocked) {
        if (state.unlocked) onUnlocked()
    }
    val durationLabel = when {
        mode == UnlockMode.UNTIL_LEAVE_ZONE || durationMinutes == UnlockDurations.UNTIL_LEAVE ->
            "Unlock lasts until you leave this zone."
        else -> "Unlock lasts ${UnlockDurations.label(durationMinutes)}."
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔒", fontSize = 48.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            if (systemLock) "Settings hidden" else "$appName Locked",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                systemLock -> "Settings and uninstall are locked. Enter your key to change them for a few minutes."
                zoneNames.isBlank() -> "This app is locked by GeoLock."
                else -> "You are currently inside \"$zoneNames\" zone."
            },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (systemLock) "Protection lock · $durationLabel" else "Geographic restriction · $durationLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = state.key,
            onValueChange = viewModel::updateKey,
            label = { Text("Enter unlock key") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onUnlock(viewModel) }),
            isError = state.error != null,
            supportingText = state.error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onUnlock(viewModel) }, modifier = Modifier.fillMaxWidth()) {
            Text("Unlock")
        }
    }
}
