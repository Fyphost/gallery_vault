package com.vaultgallery.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vault_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ViewMode { GRID, LIST }
enum class SortOrder { DATE_DESC, DATE_ASC, NAME, SIZE }
enum class DisguiseSkin { CALCULATOR, NOTES, CLEANER }

/**
 * Typed wrapper around DataStore for all non-secret user settings.
 * (Secrets like the PIN hash live in [com.vaultgallery.app.security.crypto.CredentialManager].)
 */
@Singleton
class VaultPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val LANGUAGE = stringPreferencesKey("language")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val AUTO_LOCK = booleanPreferencesKey("auto_lock")
        val BLOCK_SCREENSHOT = booleanPreferencesKey("block_screenshot")
        val INTRUDER_SELFIE = booleanPreferencesKey("intruder_selfie")
        val DECOY_ENABLED = booleanPreferencesKey("decoy_enabled")
        val USE_EXTERNAL_PLAYER = booleanPreferencesKey("use_external_player")
        val EXTERNAL_PLAYER_PKG = stringPreferencesKey("external_player_pkg")
        val DISGUISE_SKIN = stringPreferencesKey("disguise_skin")
        val INTRUDER_THRESHOLD = intPreferencesKey("intruder_threshold")
    }

    val settings: Flow<VaultSettings> = context.dataStore.data.map { p ->
        VaultSettings(
            onboarded = p[Keys.ONBOARDED] ?: false,
            themeMode = p[Keys.THEME]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
            viewMode = p[Keys.VIEW_MODE]?.let { ViewMode.valueOf(it) } ?: ViewMode.GRID,
            sortOrder = p[Keys.SORT_ORDER]?.let { SortOrder.valueOf(it) } ?: SortOrder.DATE_DESC,
            language = p[Keys.LANGUAGE] ?: "system",
            biometricEnabled = p[Keys.BIOMETRIC] ?: false,
            autoLock = p[Keys.AUTO_LOCK] ?: true,
            blockScreenshots = p[Keys.BLOCK_SCREENSHOT] ?: true,
            intruderSelfie = p[Keys.INTRUDER_SELFIE] ?: false,
            decoyEnabled = p[Keys.DECOY_ENABLED] ?: false,
            useExternalPlayer = p[Keys.USE_EXTERNAL_PLAYER] ?: false,
            externalPlayerPackage = p[Keys.EXTERNAL_PLAYER_PKG] ?: DEFAULT_EXTERNAL_PLAYER,
            disguiseSkin = p[Keys.DISGUISE_SKIN]?.let { DisguiseSkin.valueOf(it) } ?: DisguiseSkin.CALCULATOR,
            intruderThreshold = p[Keys.INTRUDER_THRESHOLD] ?: 3
        )
    }

    suspend fun setOnboarded(value: Boolean) = edit { it[Keys.ONBOARDED] = value }
    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = value }
    suspend fun setViewMode(mode: ViewMode) = edit { it[Keys.VIEW_MODE] = mode.name }
    suspend fun setSortOrder(order: SortOrder) = edit { it[Keys.SORT_ORDER] = order.name }
    suspend fun setLanguage(tag: String) = edit { it[Keys.LANGUAGE] = tag }
    suspend fun setBiometric(value: Boolean) = edit { it[Keys.BIOMETRIC] = value }
    suspend fun setAutoLock(value: Boolean) = edit { it[Keys.AUTO_LOCK] = value }
    suspend fun setBlockScreenshots(value: Boolean) = edit { it[Keys.BLOCK_SCREENSHOT] = value }
    suspend fun setIntruderSelfie(value: Boolean) = edit { it[Keys.INTRUDER_SELFIE] = value }
    suspend fun setDecoyEnabled(value: Boolean) = edit { it[Keys.DECOY_ENABLED] = value }
    suspend fun setUseExternalPlayer(value: Boolean) = edit { it[Keys.USE_EXTERNAL_PLAYER] = value }
    suspend fun setExternalPlayerPackage(pkg: String) = edit { it[Keys.EXTERNAL_PLAYER_PKG] = pkg }
    suspend fun setDisguiseSkin(skin: DisguiseSkin) = edit { it[Keys.DISGUISE_SKIN] = skin.name }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    companion object {
        // XPlayer free package id.
        const val DEFAULT_EXTERNAL_PLAYER = "video.player.videoplayer"
    }
}

data class VaultSettings(
    val onboarded: Boolean,
    val themeMode: ThemeMode,
    val dynamicColor: Boolean,
    val viewMode: ViewMode,
    val sortOrder: SortOrder,
    val language: String,
    val biometricEnabled: Boolean,
    val autoLock: Boolean,
    val blockScreenshots: Boolean,
    val intruderSelfie: Boolean,
    val decoyEnabled: Boolean,
    val useExternalPlayer: Boolean,
    val externalPlayerPackage: String,
    val disguiseSkin: DisguiseSkin,
    val intruderThreshold: Int
)
