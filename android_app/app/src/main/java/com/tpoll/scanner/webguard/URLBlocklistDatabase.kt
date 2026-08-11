// Copyright (c) 2026 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.webguard

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class BlockResult(
    val domain: String,
    val category: String,
    val categoryDescription: String,
    val severity: String,
    val isWildcardMatch: Boolean = false
)

class URLBlocklistDatabase private constructor(private val context: Context) {

    private val blockedDomains = mutableMapOf<String, BlockResult>()
    private val wildcardTlds = mutableMapOf<String, MutableSet<String>>()
    private var isLoaded = false

    fun isLoaded(): Boolean = isLoaded

    fun load() {
        if (isLoaded) return
        try {
            val inputStream = context.assets.open("url_blocklist.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonStr = reader.readText()
            reader.close()

            val gson = Gson()
            val root = gson.fromJson(jsonStr, JsonObject::class.java)
            val categories = root.getAsJsonObject("categories")

            for ((categoryKey, categoryObj) in categories.entrySet()) {
                val cat = categoryObj as JsonObject
                val description = cat.get("description")?.asString ?: categoryKey
                val severity = cat.get("severity")?.asString ?: "medium"
                val domains = cat.getAsJsonArray("domains")

                for (domainElement in domains) {
                    val domain = domainElement.asString.lowercase().trim()
                    if (domain.isNotEmpty() && !blockedDomains.containsKey(domain)) {
                        blockedDomains[domain] = BlockResult(
                            domain = domain,
                            category = categoryKey,
                            categoryDescription = description,
                            severity = severity
                        )
                    }
                }
            }

            val wildcardObj = root.getAsJsonObject("wildcard_tlds")
            if (wildcardObj != null) {
                for ((cat, tlds) in wildcardObj.entrySet()) {
                    val tldSet = mutableSetOf<String>()
                    for (tldElement in tlds.asJsonArray) {
                        tldSet.add(tldElement.asString.lowercase())
                    }
                    wildcardTlds[cat] = tldSet
                }
            }

            isLoaded = true
        } catch (_: Exception) {
            isLoaded = false
        }
    }

    fun reload() {
        isLoaded = false
        blockedDomains.clear()
        wildcardTlds.clear()
        load()
    }

    fun checkDomain(domain: String): BlockResult? {
        if (!isLoaded) return null
        val normalized = domain.lowercase().trim()
            .removePrefix("www.")
            .removePrefix("m.")
            .removePrefix("mobile.")

        blockedDomains[normalized]?.let { return it }

        for ((category, tlds) in wildcardTlds) {
            for (tld in tlds) {
                if (normalized.endsWith(tld)) {
                    val catObj = context.assets.open("url_blocklist.json").bufferedReader().use { reader ->
                        val root = Gson().fromJson(reader.readText(), JsonObject::class.java)
                        val catDesc = root.getAsJsonObject("categories")
                            ?.getAsJsonObject(category)
                            ?.get("description")?.asString ?: category
                        val severity = root.getAsJsonObject("categories")
                            ?.getAsJsonObject(category)
                            ?.get("severity")?.asString ?: "medium"
                        BlockResult(
                            domain = normalized,
                            category = category,
                            categoryDescription = catDesc,
                            severity = severity,
                            isWildcardMatch = true
                        )
                    }
                    return catObj
                }
            }
        }

        val parts = normalized.split(".")
        if (parts.size > 2) {
            val baseDomain = parts.takeLast(2).joinToString(".")
            blockedDomains[baseDomain]?.let {
                return it.copy(domain = normalized)
            }
        }

        return null
    }

    fun getBlockedCount(): Int = blockedDomains.size

    fun getCategoryCount(category: String): Int {
        return blockedDomains.values.count { it.category == category }
    }

    fun getAllCategories(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val inputStream = context.assets.open("url_blocklist.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val root = Gson().fromJson(reader.readText(), JsonObject::class.java)
            val categories = root.getAsJsonObject("categories")
            for ((key, value) in categories.entrySet()) {
                val desc = (value as JsonObject).get("description")?.asString ?: key
                map[key] = desc
            }
            reader.close()
        } catch (_: Exception) {}
        return map
    }

    companion object {
        @Volatile
        private var instance: URLBlocklistDatabase? = null

        fun getInstance(context: Context): URLBlocklistDatabase {
            return instance ?: synchronized(this) {
                instance ?: URLBlocklistDatabase(context.applicationContext).also {
                    instance = it
                    it.load()
                }
            }
        }
    }
}
