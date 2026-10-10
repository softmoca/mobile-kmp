package kr.jachwisunbae.app.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.jachwisunbae.app.data.TokenStore

/** Only authenticated ciphertext and a random IV leave Android Keystore. */
class AndroidTokenStore(context: Context) : TokenStore {
    private val preferences = context.getSharedPreferences("secure_session", Context.MODE_PRIVATE)
    private val alias = "${context.packageName}.access-token"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(alias, null) as? SecretKey ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore",
        ).apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        val encrypted = preferences.getString("ciphertext", null) ?: return@withContext null
        try {
            val encodedIv = preferences.getString("iv", null) ?: throw IllegalArgumentException("Missing token IV")
            val iv = Base64.decode(encodedIv, Base64.NO_WRAP)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).toString(Charsets.UTF_8)
            }
        } catch (_: java.security.GeneralSecurityException) {
            preferences.edit().clear().commit()
            null
        } catch (_: IllegalArgumentException) {
            preferences.edit().clear().commit()
            null
        }
    }

    override suspend fun write(token: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        check(preferences.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .commit()) { "로그인 정보를 안전하게 저장하지 못했습니다." }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        check(preferences.edit().clear().commit()) { "로그인 정보를 삭제하지 못했습니다." }
    }
}

@Composable
actual fun rememberTokenStore(): TokenStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidTokenStore(context) }
}
