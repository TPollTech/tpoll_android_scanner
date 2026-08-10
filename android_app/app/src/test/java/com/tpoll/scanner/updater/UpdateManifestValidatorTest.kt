package com.tpoll.scanner.updater

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
            UpdateManifestValidator.error(validManifest().copy(apk_url = "http://example.com/app.apk"))
        )
        assertEquals(
            "O manifesto informa um tamanho de APK inválido.",
            UpdateManifestValidator.error(validManifest().copy(size_bytes = 0L))
        )
    }

    @Test
    fun rejectsApkUrlWhoseFilenameDoesNotIdentifyTheRelease() {
        assertEquals(
            "O manifesto não aponta para o APK oficial desta versão.",
            UpdateManifestValidator.error(
                validManifest().copy(
                    apk_url = "https://github.com/TPollTech/tpoll_android_scanner/" +
                        "releases/download/v1.8.12/TPollScanner-release.apk"
                )
            )
        )
    }

    @Test
    fun rejectsMinimumVersionNewerThanOfferedApk() {
        assertEquals(
            "O manifesto exige uma versão mínima maior que a atualização disponível.",
            UpdateManifestValidator.error(validManifest().copy(min_version_code = 26))
        )
    }

    @Test
    fun comparesInstalledVersionWithoutGlobalMutableState() {
        val info = validManifest().copy(version_code = 25, min_version_code = 20)
        assertTrue(info.isNewerThan(24))
        assertFalse(info.isNewerThan(25))
        assertTrue(info.isMandatoryFor(19))
        assertFalse(info.isMandatoryFor(20))
    }

    private fun validManifest() = UpdateInfo(
        version_code = 25,
        version_name = "1.8.12",
        changelog = "Correções",
        download_url = "https://example.com/download",
        apk_url = "https://github.com/TPollTech/tpoll_android_scanner/" +
            "releases/download/v1.8.12/TPollScanner-1.8.12-release.apk",
        sha256 = "A".repeat(64),
        size_bytes = 4_000_000L,
        released_at = "2026-08-08T00:00:00Z",
        min_version_code = 1
    )
}
