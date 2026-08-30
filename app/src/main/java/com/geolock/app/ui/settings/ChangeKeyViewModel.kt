package com.geolock.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geolock.app.security.UnlockKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChangeKeyViewModel @Inject constructor(
    private val unlockKeyManager: UnlockKeyManager
) : ViewModel() {
    private val _state = MutableStateFlow(ChangeKeyState())
    val state = _state.asStateFlow()

    fun updateCurrent(value: String) = _state.update { it.copy(current = value, message = null) }
    fun updateNext(value: String) = _state.update { it.copy(next = value, message = null) }
    fun updateConfirm(value: String) = _state.update { it.copy(confirm = value, message = null) }

    fun save() {
        viewModelScope.launch {
            val current = _state.value
            if (!unlockKeyManager.verify(current.current)) {
                _state.update { it.copy(message = "Current key is incorrect.") }
                return@launch
            }
            if (current.next.length < UnlockKeyManager.MIN_LENGTH) {
                _state.update { it.copy(message = "New key must be at least ${UnlockKeyManager.MIN_LENGTH} characters.") }
                return@launch
            }
            if (current.next != current.confirm) {
                _state.update { it.copy(message = "New keys do not match.") }
                return@launch
            }
            unlockKeyManager.setKey(current.next)
            _state.update { it.copy(success = true, message = "Unlock key updated.") }
        }
    }
}
