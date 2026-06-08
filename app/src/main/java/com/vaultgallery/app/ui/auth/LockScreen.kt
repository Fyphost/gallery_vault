package com.vaultgallery.app.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultgallery.app.R
import com.vaultgallery.app.security.BiometricAuthenticator

private const val PIN_LENGTH = 6

/**
 * The in-app lock screen shown when the session is locked (e.g. after auto-lock).
 * Disguised as a generic numeric keypad; supports biometric unlock too.
 */
@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // Offer biometric immediately if enabled & available.
    LaunchedEffect(state.biometricEnabled) {
        if (state.biometricEnabled && activity != null && BiometricAuthenticator.isAvailable(activity)) {
            BiometricAuthenticator.authenticate(
                activity = activity,
                title = context.getString(R.string.biometric_title),
                subtitle = context.getString(R.string.biometric_subtitle),
                negativeButton = context.getString(R.string.biometric_cancel),
                onSuccess = { viewModel.onBiometricSuccess(onUnlocked) },
                onError = {},
                onFailed = {}
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.enter_pin),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(24.dp))

            // PIN dots
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(PIN_LENGTH) { i ->
                    val filled = i < state.pin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .then(Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(16.dp),
                            shape = CircleShape,
                            color = if (filled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {}
                    }
                }
            }
            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.wrong_pin),
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(32.dp))

            Keypad(
                onDigit = { d ->
                    if (state.pin.length < PIN_LENGTH) {
                        val newPin = state.pin + d
                        viewModel.onPinChange(newPin)
                        if (newPin.length == PIN_LENGTH && activity != null) {
                            viewModel.submitPin(activity, onUnlocked)
                        }
                    }
                },
                onBackspace = { viewModel.onPinChange(state.pin.dropLast(1)) },
                onBiometric = {
                    if (state.biometricEnabled && activity != null) {
                        BiometricAuthenticator.authenticate(
                            activity = activity,
                            title = context.getString(R.string.biometric_title),
                            subtitle = context.getString(R.string.biometric_subtitle),
                            negativeButton = context.getString(R.string.biometric_cancel),
                            onSuccess = { viewModel.onBiometricSuccess(onUnlocked) },
                            onError = {},
                            onFailed = {}
                        )
                    }
                },
                showBiometric = state.biometricEnabled
            )
        }
    }
}

@Composable
private fun Keypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit,
    showBiometric: Boolean
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("bio", "0", "del")
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key ->
                    when (key) {
                        "bio" -> KeypadButton(enabled = showBiometric, onClick = onBiometric) {
                            if (showBiometric) Icon(Icons.Default.Fingerprint, null, Modifier.size(28.dp))
                        }
                        "del" -> KeypadButton(onClick = onBackspace) {
                            Icon(Icons.Default.Backspace, null, Modifier.size(28.dp))
                        }
                        else -> KeypadButton(onClick = { onDigit(key) }) {
                            Text(key, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun KeypadButton(
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
