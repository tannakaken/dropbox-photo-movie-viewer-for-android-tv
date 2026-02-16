package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class Auth(
    val accessToken: String,
    val refreshToken: String,
    val deviceId: String,
    val deviceGenerateId: String
) {
    fun toMap() = mapOf(
        "accessToken" to accessToken,
        "refreshToken" to refreshToken,
        "deviceId" to deviceId,
        "deviceGenerateId" to deviceGenerateId
    )
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "secure_tokens"
)

@Singleton
class SecureAuthStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    tinkManager: TinkManager
) {
    // AEADとは「認証付き暗号（Authenticated Encryption with Associated Data）」のこと
    // https://ja.wikipedia.org/wiki/%E8%AA%8D%E8%A8%BC%E4%BB%98%E3%81%8D%E6%9A%97%E5%8F%B7
    private val aead = tinkManager.getOrCreateAead()

    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("encrypted_access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("encrypted_refresh_token")
        private val DEVICE_ID_KEY = stringPreferencesKey("encrypted_device_id")
        private val DEVICE_GENERATE_ID_KEY = stringPreferencesKey("encrypted_device_generate_id")
        private val KEY_MAP = mapOf(
            "accessToken" to ACCESS_TOKEN_KEY,
            "refreshToken" to REFRESH_TOKEN_KEY,
            "deviceId" to DEVICE_ID_KEY,
            "deviceGenerateId" to DEVICE_GENERATE_ID_KEY
        )
    }

    private fun toAuth(map: Map<String, String?>): Auth? =
        try {
            Auth(
                map["accessToken"]!!,
                map["refreshToken"]!!,
                map["deviceId"]!!,
                map["deviceGenerateId"]!!
            )
        } catch (e: NullPointerException) {
            null
        }

    // リフレッシュトークンの保存
    suspend fun saveAuth(auth: Auth) {
        auth.toMap().forEach { (fieldName, value) ->
            val encrypted = aead.encrypt(value.toByteArray(), null)
            val base64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)

            context.dataStore.edit { preferences ->
                preferences[KEY_MAP[fieldName]!!] = base64
            }
        }
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        val encryptedAccessToken = aead.encrypt(accessToken.toByteArray(), null)
        val base64AccessToken = Base64.encodeToString(encryptedAccessToken, Base64.NO_WRAP)

        val encryptedRefreshToken = aead.encrypt(refreshToken.toByteArray(), null)
        val base64RefreshToken = Base64.encodeToString(encryptedRefreshToken, Base64.NO_WRAP)

        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = base64AccessToken
            preferences[REFRESH_TOKEN_KEY] = base64RefreshToken
        }
    }

    // リフレッシュトークンの取得
    fun getAuth(): Flow<Auth?> {
        return context.dataStore.data.map { preferences ->
            toAuth(KEY_MAP.mapValues { (_, key) ->
                preferences[key]?.let { encryptedBase64 ->
                    try {
                        val encrypted = Base64.decode(encryptedBase64, Base64.NO_WRAP)
                        val decrypted = aead.decrypt(encrypted, null)
                        String(decrypted)
                    } catch (e: Exception) {
                        null
                    }
                }
            })
        }
    }

    // リフレッシュトークンの削除
    suspend fun clearAuth() {
        context.dataStore.edit { preferences ->
            KEY_MAP.values.forEach { key ->
                preferences.remove(key)
            }

        }
    }
}