package dev.wiptv.player

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the sign-in details on this TV only, encrypted with a key that lives in Android's
 * keystore and never leaves the device. Nothing is sent anywhere except to the provider itself.
 */
object LoginStore {

    private const val PREFS = "login"
    private const val ENTRY = "details"
    private const val KEY_ALIAS = "wiptv-player-login"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun save(context: Context, login: Login) {
        val json = JSONObject()
            .put("server", login.server)
            .put("username", login.username)
            .put("password", login.password)
            .toString()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        prefs(context).edit().putString(ENTRY, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }

    /** The saved details, or null when there are none (or they can't be read any more). */
    fun load(context: Context): Login? = runCatching {
        val stored = prefs(context).getString(ENTRY, null) ?: return null
        val sealed = Base64.decode(stored, Base64.NO_WRAP)
        val iv = sealed.copyOfRange(0, 12)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        }
        val json = JSONObject(String(cipher.doFinal(sealed.copyOfRange(12, sealed.size)), Charsets.UTF_8))
        Login(json.getString("server"), json.getString("username"), json.getString("password"))
    }.getOrNull()

    fun clear(context: Context) {
        prefs(context).edit().remove(ENTRY).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }
}
