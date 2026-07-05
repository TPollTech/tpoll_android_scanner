package com.tpoll.scanner.updater

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class RemoteVirusDb(
    val known_threats: Map<String, RemoteVirusDbEntry> = emptyMap(),
    val suspicious_patterns: List<RemoteSuspiciousPattern> = emptyList()
)

data class RemoteVirusDbEntry(
    val type: String = "",
    val severity: String = "low",
    val description: String = ""
)

data class RemoteSuspiciousPattern(
    val pattern: String = "",
    val reason: String = ""
)

data class RemoteRules(
    val trusted_installers: List<String> = emptyList(),
    val suspicious_terms: List<String> = emptyList(),
    val high_risk_permissions: Map<String, Int> = emptyMap(),
    val high_risk_appops: Map<String, Int> = emptyMap(),
    val dangerous_combinations: List<Map<String, Any>> = emptyList()
)

class RemoteConfig(private val context: Context) {

    companion object {
        private const val VIRUS_DB_URL = "https://raw.githubusercontent.com/TPollTech/tpoll_android_scanner/main/android_app/app/src/main/assets/virus_db.json"
        private const val RULES_URL = "https://raw.githubusercontent.com/TPollTech/tpoll_android_scanner/main/android_app/app/src/main/assets/rules.json"
        private const val PREFS_NAME = "remote_config"
        private const val KEY_VIRUS_DB = "cached_virus_db"
        private const val KEY_RULES = "cached_rules"
    }

    private val gson = Gson()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val virusJson = fetchRemote(VIRUS_DB_URL)
        val rulesJson = fetchRemote(RULES_URL)
        if (virusJson != null) {
            prefs.edit().putString(KEY_VIRUS_DB, virusJson).apply()
        }
        if (rulesJson != null) {
            prefs.edit().putString(KEY_RULES, rulesJson).apply()
        }
    }

    fun getCachedVirusDb(): RemoteVirusDb? {
        val json = prefs.getString(KEY_VIRUS_DB, null) ?: return null
        return try {
            val type = object : TypeToken<RemoteVirusDb>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) { null }
    }

    fun getCachedRules(): RemoteRules? {
        val json = prefs.getString(KEY_RULES, null) ?: return null
        return try {
            val type = object : TypeToken<RemoteRules>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) { null }
    }

    private fun fetchRemote(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = true

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val text = reader.readText()
            reader.close()
            connection.disconnect()
            text
        } catch (e: Exception) { null }
    }
}
