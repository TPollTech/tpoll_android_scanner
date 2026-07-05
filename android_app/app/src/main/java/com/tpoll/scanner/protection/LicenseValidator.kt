package com.tpoll.scanner.protection

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

object LicenseValidator {

    private const val EXPECTED_SIGNATURE = "tpoll_release_2026"
    private const val PREFS_NAME = "license_prefs"
    private const val KEY_VERIFIED = "signature_verified"
    private const val KEY_FIRST_CHECK = "first_check_done"

    fun isSignatureValid(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VERIFIED, false)
    }

    fun checkAndStoreSignature(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_FIRST_CHECK, false)) return

        val valid = verifySignature(context)
        prefs.edit()
            .putBoolean(KEY_VERIFIED, valid)
            .putBoolean(KEY_FIRST_CHECK, true)
            .apply()
    }

    private fun verifySignature(context: Context): Boolean {
        return try {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            val cert = info.signatures.firstOrNull() ?: return false
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(cert.toByteArray())
            val hashHex = hash.joinToString("") { "%02x".format(it) }
            hashHex.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }
}
