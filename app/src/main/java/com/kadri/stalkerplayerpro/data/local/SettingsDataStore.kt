package com.kadri.stalkerplayerpro.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "stalker_tv_settings")

data class TvUserSettings(
    val themeId: Int = 0,
    val fontScaleId: Int = -1,
    val language: String = "ar",
    val parentalPin: String = "0000",
    val clockCorner: Int = 0,
    val showClock: Boolean = true,
    val autoPipEnabled: Boolean = true,
    val preferredAudioLang: String = "en",
    val preferredSubtitleLang: String = "ar",
    val subtitleEngine: String = "BOTH",
    val openSubApiKey: String = "",
    val openSubUsername: String = "",
    val openSubPassword: String = "",
    val openSubJwtToken: String = "",
    val openSubBaseUrl: String = "api.opensubtitles.com",
    val subdlApiKey: String = "",
    val subTextColor: Long = 0xFFFFD600,
    val subFontSizeSp: Float = 38f,
    val subBgAlpha: Float = 0.65f
)

class SettingsDataStore(private val context: Context) {

    companion object {
        val KEY_THEME_ID = intPreferencesKey("theme_id")
        val KEY_FONT_SCALE = intPreferencesKey("font_scale")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_PIN = stringPreferencesKey("parental_pin")
        val KEY_CLOCK_CORNER = intPreferencesKey("clock_corner")
        val KEY_SHOW_CLOCK = intPreferencesKey("show_clock")
        val KEY_AUTO_PIP = intPreferencesKey("auto_pip_enabled")
        val KEY_AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
        val KEY_SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_lang")

        val KEY_SUBTITLE_ENGINE = stringPreferencesKey("subtitle_engine")
        val KEY_OS_API_KEY = stringPreferencesKey("os_api_key")
        val KEY_OS_USER = stringPreferencesKey("os_user")
        val KEY_OS_PASS = stringPreferencesKey("os_pass")
        val KEY_OS_TOKEN = stringPreferencesKey("os_token")
        val KEY_OS_BASE_URL = stringPreferencesKey("os_base_url")
        val KEY_SUBDL_API_KEY = stringPreferencesKey("subdl_api_key")

        val KEY_SUB_COLOR = longPreferencesKey("sub_color")
        val KEY_SUB_SIZE = floatPreferencesKey("sub_size")
        val KEY_SUB_BG_ALPHA = floatPreferencesKey("sub_bg_alpha")
    }

    val settingsFlow: Flow<TvUserSettings> = context.dataStore.data.map { prefs ->
        TvUserSettings(
            themeId = prefs[KEY_THEME_ID] ?: 0,
            fontScaleId = prefs[KEY_FONT_SCALE] ?: -1,
            language = prefs[KEY_LANGUAGE] ?: "ar",
            parentalPin = prefs[KEY_PIN] ?: "0000",
            clockCorner = prefs[KEY_CLOCK_CORNER] ?: 0,
            showClock = (prefs[KEY_SHOW_CLOCK] ?: 1) == 1,
            autoPipEnabled = (prefs[KEY_AUTO_PIP] ?: 1) == 1,
            preferredAudioLang = prefs[KEY_AUDIO_LANG] ?: "en",
            preferredSubtitleLang = prefs[KEY_SUBTITLE_LANG] ?: "ar",
            subtitleEngine = prefs[KEY_SUBTITLE_ENGINE] ?: "BOTH",
            openSubApiKey = prefs[KEY_OS_API_KEY] ?: "",
            openSubUsername = prefs[KEY_OS_USER] ?: "",
            openSubPassword = prefs[KEY_OS_PASS] ?: "",
            openSubJwtToken = prefs[KEY_OS_TOKEN] ?: "",
            openSubBaseUrl = prefs[KEY_OS_BASE_URL] ?: "api.opensubtitles.com",
            subdlApiKey = prefs[KEY_SUBDL_API_KEY] ?: "",
            subTextColor = prefs[KEY_SUB_COLOR] ?: 0xFFFFD600,
            subFontSizeSp = prefs[KEY_SUB_SIZE] ?: 38f,
            subBgAlpha = prefs[KEY_SUB_BG_ALPHA] ?: 0.65f
        )
    }

    suspend fun updateTheme(themeId: Int) {
        context.dataStore.edit { it[KEY_THEME_ID] = themeId }
    }

    suspend fun updateFontScale(scaleId: Int) {
        context.dataStore.edit { it[KEY_FONT_SCALE] = scaleId }
    }

    suspend fun updateLanguage(lang: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = lang }
    }

    suspend fun updateParentalPin(pin: String) {
        context.dataStore.edit { it[KEY_PIN] = pin }
    }

    suspend fun updateClockCorner(corner: Int) {
        context.dataStore.edit { it[KEY_CLOCK_CORNER] = corner }
    }

    suspend fun updateShowClock(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_CLOCK] = if (show) 1 else 0 }
    }

    suspend fun updateAutoPipEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_PIP] = if (enabled) 1 else 0 }
    }

    suspend fun updatePreferredAudioLang(lang: String) {
        context.dataStore.edit { it[KEY_AUDIO_LANG] = lang }
    }

    suspend fun updatePreferredSubtitleLang(lang: String) {
        context.dataStore.edit { it[KEY_SUBTITLE_LANG] = lang }
    }

    suspend fun updateSubtitleEngine(engine: String) {
        context.dataStore.edit { it[KEY_SUBTITLE_ENGINE] = engine }
    }

    suspend fun updateOpenSubSession(token: String, baseUrl: String) {
        context.dataStore.edit {
            it[KEY_OS_TOKEN] = token
            if (baseUrl.isNotBlank()) it[KEY_OS_BASE_URL] = baseUrl
        }
    }

    suspend fun updateSubtitleAccounts(
        osApiKey: String,
        osUser: String,
        osPass: String,
        osToken: String,
        subdlApiKey: String,
        osBaseUrl: String = "api.opensubtitles.com"
    ) {
        context.dataStore.edit {
            it[KEY_OS_API_KEY] = osApiKey
            it[KEY_OS_USER] = osUser
            it[KEY_OS_PASS] = osPass
            it[KEY_OS_TOKEN] = osToken
            it[KEY_OS_BASE_URL] = osBaseUrl.ifBlank { "api.opensubtitles.com" }
            it[KEY_SUBDL_API_KEY] = subdlApiKey
        }
    }

    suspend fun updateSubtitleStyle(color: Long, sizeSp: Float, bgAlpha: Float) {
        context.dataStore.edit {
            it[KEY_SUB_COLOR] = color
            it[KEY_SUB_SIZE] = sizeSp
            it[KEY_SUB_BG_ALPHA] = bgAlpha
        }
    }

    suspend fun updateUnifiedAudioSubtitleSettings(
        audioLang: String,
        subtitleLang: String,
        subtitleEngine: String,
        osApiKey: String,
        osUser: String,
        osPass: String,
        osToken: String,
        osBaseUrl: String,
        subdlApiKey: String,
        subColor: Long,
        subSizeSp: Float,
        subBgAlpha: Float
    ) {
        context.dataStore.edit {
            it[KEY_AUDIO_LANG] = audioLang
            it[KEY_SUBTITLE_LANG] = subtitleLang
            it[KEY_SUBTITLE_ENGINE] = subtitleEngine
            it[KEY_OS_API_KEY] = osApiKey
            it[KEY_OS_USER] = osUser
            it[KEY_OS_PASS] = osPass
            it[KEY_OS_TOKEN] = osToken
            it[KEY_OS_BASE_URL] = osBaseUrl.ifBlank { "api.opensubtitles.com" }
            it[KEY_SUBDL_API_KEY] = subdlApiKey
            it[KEY_SUB_COLOR] = subColor
            it[KEY_SUB_SIZE] = subSizeSp
            it[KEY_SUB_BG_ALPHA] = subBgAlpha
        }
    }
}
