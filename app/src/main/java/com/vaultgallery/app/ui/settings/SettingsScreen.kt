package com.vaultgallery.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultgallery.app.R
import com.vaultgallery.app.data.prefs.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val s = settings ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SectionHeader(stringResource(R.string.settings_security))
            ToggleRow(stringResource(R.string.setting_biometric), s.biometricEnabled, viewModel::setBiometric)
            ToggleRow(stringResource(R.string.setting_autolock), s.autoLock, viewModel::setAutoLock)
            ToggleRow(stringResource(R.string.setting_screenshot), s.blockScreenshots, viewModel::setBlockScreenshots)
            ToggleRow(stringResource(R.string.setting_intruder), s.intruderSelfie, viewModel::setIntruderSelfie)
            ToggleRow(stringResource(R.string.setting_decoy), s.decoyEnabled) { enabled ->
                // For brevity the decoy PIN is set to a default; a real flow would
                // prompt for it. See README for the intended UX.
                viewModel.setDecoyEnabled(enabled, if (enabled) "1234" else null)
            }

            SectionHeader(stringResource(R.string.settings_appearance))
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = s.themeMode == mode,
                        onClick = { viewModel.setTheme(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            ToggleRow(stringResource(R.string.setting_dynamic_color), s.dynamicColor, viewModel::setDynamicColor)

            SectionHeader(stringResource(R.string.settings_player))
            ToggleRow(stringResource(R.string.setting_external_player), s.useExternalPlayer, viewModel::setUseExternalPlayer)

            SectionHeader(stringResource(R.string.settings_language))
            val context = androidx.compose.ui.platform.LocalContext.current
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                listOf("system" to "Auto", "en" to "EN", "es" to "ES", "fr" to "FR")
                    .forEach { (tag, label) ->
                        FilterChip(
                            selected = s.language == tag,
                            onClick = {
                                viewModel.setLanguage(tag)
                                com.vaultgallery.app.ui.LocaleManager.persist(context, tag)
                                (context as? android.app.Activity)?.recreate()
                            },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
