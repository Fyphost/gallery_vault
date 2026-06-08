package com.vaultgallery.app.ui.auth

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultgallery.app.R
import com.vaultgallery.app.security.BiometricAuthenticator

private const val MAX_PIN = 10

/**
 * In-app lock screen shown when the session locks (e.g. after auto-lock). Modern
 * Material 3 keypad with ripple feedback, supports variable-length PINs (confirm
 * with the check key) and biometric unlock.
 */
@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContextActivity()

    fun promptBiometric() {
        val activity = context ?: return
        if (!BiometricAuthenticator.isAvailable(activity)) return
        BiometricAuthenticator.authenticate(
            activity = activity,
            title = activity.getString(R.string.biometric_title),
            subtitle = activity.getString(R.string.biometric_subtitle),
            negativeButton = activity.getString(R.string.biometric_cancel),
            onSuccess = { viewModel.onBiometricSuccess(onUnlocked) },
            onError = {},
            onFailed = {}
        )
    }

    LaunchedEffect(state.biometricEnabled) {
        if (state.biometricEnabled) promptBiometric()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.enter_pin),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(20.dp))

            PinDots(count = state.pin.length, error = state.error != null)

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.wrong_pin), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(28.dp))

            Keypad(
                onDigit = { d -> if (state.pin.length < MAX_PIN) viewModel.onPinChange(state.pin + d) },
                onBackspace = { viewModel.onPinChange(state.pin.dropLast(1)) },
                onConfirm = { context?.let { viewModel.submitPin(it, onUnlocked) } },
                onBiometric = { promptBiometric() },
                showBiometric = state.biometricEnabled
            )
        }
    }
}

@Composable
private fun PinDots(count: Int, error: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val shown = count.coerceAtMost(MAX_PIN)
        repeat(if (shown == 0) 4 else shown.coerceAtLeast(4)) { i ->
            val filled = i < count
            val dotSize by animateDpAsState(targetValue = if (filled) 16.dp else 12.dp, label = "dot")
            Surface(
                modifier = Modifier.size(dotSize),
                shape = CircleShape,
                color = when {
                    error -> MaterialTheme.colorScheme.error
                    filled -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ) {}
        }
    }
}

@Composable
private fun Keypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    onBiometric: () -> Unit,
    showBiometric: Boolean
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("bio", "0", "del")
    )
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { key ->
                    when (key) {
                        "bio" -> KeyButton(onClick = onBiometric, enabled = showBiometric) {
                            if (showBiometric) Icon(Icons.Default.Fingerprint, "Biometric")
                        }
                        "del" -> KeyButton(onClick = onBackspace) {
                            Icon(Icons.AutoMirrored.Filled.Backspace, "Delete")
                        }
                        else -> KeyButton(onClick = { onDigit(key) }) {
                            Text(key, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        FilledTonalIconButton(
            onClick = onConfirm,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Default.Check, contentDescription = "Unlock", modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun KeyButton(
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(72.dp)
    ) {
        if (enabled) content() else Spacer(Modifier.size(1.dp))
    }
}

/** Resolves the current [FragmentActivity] (needed for BiometricPrompt). */
@Composable
private fun LocalContextActivity(): FragmentActivity? {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return ctx as? FragmentActivity
}
