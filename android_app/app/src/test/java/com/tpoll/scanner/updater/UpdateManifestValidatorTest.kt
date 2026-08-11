package com.tpoll.scanner.updater

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestValidatorTest {

    @Test
    fun acceptsCompleteHttpsManifest() {
        assertNull(UpdateManifestValidator.error(validManifest()))
    }

    @Test
    fun rejectsMissingOrMalformedHash() {
        assertEquals(
            "O manifesto não contém um SHA-256 válido.",
            UpdateManifestValidator.error(validManifest().copy(sha256 = ""))
        )
        assertEquals(
            "O manifesto não contém um SHA-256 válido.",
            UpdateManifestValidator.error(validManifest().copy(sha256 = "abc"))
        )
    }

    @Test
    fun rejectsInsecureApkUrlAndInvalidSize() {
        assertEquals(
            "O manifesto não informa um endereço HTTPS para o APK.",
            UpdateManifestValidator.error(validManifest().copy(apkUrl = "http://example.com/app.apk"))
        )
        assertEquals(
            "O manifesto informa um tamanho de APK inválido.",
            UpdateManifestValidator.error(validManifest().copy(sizeBytes = 0L))
        )
    }

    @Test
    fun rejectsApkUrlWhoseFilenameDoesNotIdentifyTheRelease() {
        assertEquals(
            "O manifesto não aponta para o APK oficial desta versão.",
            UpdateManifestValidator.error(
                validManifest().copy(
                    apkUrl = "https://github.com/TPollTech/tpoll_android_scanner/" +
                        "releases/download/v1.8.12/TPollScanner-release.apk"
                )
            )
        )
    }

    @Test
    fun rejectsMinimumVersionNewerThanOfferedApk() {
        assertEquals(
            "O manifesto exige uma versão mínima maior que a atualização disponível.",
            UpdateManifestValidator.error(validManifest().copy(minVersionCode = 26))
        )
    }

    @Test
    fun comparesInstalledVersionAndMandatoryFlag() {
        val info = validManifest().copy(versionCode = 25, minVersionCode = 20)
        assertTrue(info.isNewerThan(24))
        assertFalse(info.isNewerThan(25))
        assertTrue(info.isMandatoryFor(19))
        assertFalse(info.isMandatoryFor(20))
        assertTrue(info.copy(mandatory = true).isMandatoryFor(25))
    }

    @Test
    fun parsesCanonicalAndLegacyManifestNames() {
        val canonical = Gson().fromJson(
            """{"versionCode":27,"versionName":"1.8.14","apkUrl":"https://example.com/app.apk","sizeBytes":42,"releaseNotes":["Nota"]}""",
            UpdateInfo::class.java
        )
        val legacy = Gson().fromJson(
            """{"version_code":26,"version_name":"1.8.13","apk_url":"https://example.com/old.apk","size_bytes":41,"changelog":"Legado"}""",
            UpdateInfo::class.java
        )

        assertEquals(27, canonical.versionCode)
        assertEquals("1.8.14", canonical.versionName)
        assertEquals(listOf("Nota"), canonical.notesForDisplay())
        assertEquals(26, legacy.versionCode)
        assertEquals("1.8.13", legacy.versionName)
        assertEquals(listOf("Legado"), legacy.notesForDisplay())
    }

    @Test
    fun checksAgainAfterSixHoursAndAfterClockRollback() {
        val lastCheck = 1_000L
        assertFalse(UpdateChecker.shouldCheckAt(lastCheck, lastCheck + 1_000L))
        assertTrue(
            UpdateChecker.shouldCheckAt(
                lastCheck,
                lastCheck + UpdateChecker.CHECK_INTERVAL_MILLIS
            )
        )
        assertTrue(UpdateChecker.shouldCheckAt(lastCheck, lastCheck - 1L))
    }

    private fun validManifest() = UpdateInfo(
        versionCode = 25,
        versionName = "1.8.12",
        apkUrl = "https://github.com/TPollTech/tpoll_android_scanner/" +
            "releases/download/v1.8.12/TPollScanner-1.8.12-release.apk",
        sha256 = "A".repeat(64),
        mandatory = false,
        releaseNotes = listOf("Correções"),
        downloadUrl = "https://example.com/download",
        sizeBytes = 4_000_000L,
        releasedAt = "2026-08-08T00:00:00Z",
        minVersionCode = 1
    )
}
