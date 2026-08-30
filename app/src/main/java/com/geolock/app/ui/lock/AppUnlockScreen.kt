package com.geolock.app.ui.lock

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.geolock.app.data.repository.UnlockRepository
import com.geolock.app.domain.LockManager
import com.geolock.app.security.UnlockKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUnlockState(
    val key: String = "",
    val error: String? = null,
    val unlocked: Boolean = false
)

@HiltViewModel
class AppUnlockViewModel @Inject constructor(
    private val unlockKeyManager: UnlockKeyManager,
    private val unlockRepository: UnlockRepository,
    private val lockManager: LockManager
) : ViewModel() {
    private val _state = MutableStateFlow(AppUnlockState())
    val state = _state.asStateFlow()

    fun updateKey(value: String) = _state.update { it.copy(key = value, error = null) }

    fun unlock() {
        viewModelScope.launch {
            if (!unlockKeyManager.verify(_state.value.key)) {
                _state.update { it.copy(error = "Incorrect key") }
                return@launch
            }
            unlockRepository.grantAppUnlock()
            lockManager.notifyChanged()
            _state.update { it.copy(unlocked = true, key = "") }
        }
    }
}

@Composable
fun AppUnlockScreen(
    viewModel: AppUnlockViewModel = hiltViewModel(),
    onUnlocked: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.unlocked) {
        if (state.unlocked) onUnlocked()
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
        Text("GeoLock locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Enter your unlock key to open GeoLock.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = state.key,
            onValueChange = viewModel::updateKey,
            label = { Text("Unlock key") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { viewModel.unlock() }),
            isError = state.error != null,
            supportingText = state.error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = viewModel::unlock, modifier = Modifier.fillMaxWidth()) {
            Text("Open")
        }
    }
}
