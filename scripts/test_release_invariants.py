import json
import pathlib
import re
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]


class ReleaseInvariantTests(unittest.TestCase):
    def test_release_build_never_uses_debug_signing(self) -> None:
        gradle = (ROOT / "android_app/app/build.gradle.kts").read_text(encoding="utf-8")

        self.assertIn("Release signing is required", gradle)
        self.assertNotRegex(
            gradle,
            re.compile(r"signingConfig\s*=.*getByName\(\"debug\"\)", re.DOTALL),
        )

    def test_publish_workflow_is_release_only_and_fail_closed(self) -> None:
        workflow = (
            ROOT / ".github/workflows/build-and-publish-apk.yml"
        ).read_text(encoding="utf-8")

        for secret in {
            "RELEASE_KEYSTORE_BASE64",
            "RELEASE_STORE_PASSWORD",
            "RELEASE_KEY_ALIAS",
            "RELEASE_KEY_PASSWORD",
            "RELEASE_CERT_SHA256",
        }:
            self.assertIn(secret, workflow)

        self.assertIn("CN=Android Debug", workflow)
        self.assertIn("ACTUAL_PACKAGE", workflow)
        self.assertIn("ACTUAL_VERSION_CODE", workflow)
        self.assertIn("ACTUAL_VERSION_NAME", workflow)
        self.assertIn("./gradlew clean testReleaseUnitTest lintRelease assembleRelease", workflow)
        self.assertNotIn("assembleDebug", workflow)
        self.assertNotIn("pull_request:", workflow)
        self.assertNotIn("Commit build failure report", workflow)
        self.assertNotIn("git add -f BUILD_FAILURE.txt", workflow)

    def test_publication_uses_immutable_asset_and_publishes_manifest_last(self) -> None:
        workflow = (
            ROOT / ".github/workflows/build-and-publish-apk.yml"
        ).read_text(encoding="utf-8")
        landing = (ROOT / "index.html").read_text(encoding="utf-8")
        upgrade_smoke = (
            ROOT / "scripts/smoke_test_android_upgrade.sh"
        ).read_text(encoding="utf-8")

        self.assertIn("scripts/release_manifest.py finalize", workflow)
        self.assertIn("scripts/release_manifest.py verify", workflow)
        self.assertIn("gh release create", workflow)
        self.assertIn("Publish immutable release and manifest last", workflow)
        self.assertIn("cleanup_unpublished_release", workflow)
        self.assertIn("--release-config release-config.json", workflow)
        self.assertGreaterEqual(workflow.count("--release-config release-config.json"), 2)
        self.assertIn('gh release verify "$RELEASE_TAG"', workflow)
        self.assertIn('gh release verify-asset "$RELEASE_TAG"', workflow)
        self.assertIn("--draft", workflow)
        self.assertNotIn("--clobber", workflow)
        self.assertIn("bash scripts/smoke_test_android_upgrade.sh", workflow)
        self.assertIn('adb install "$PREVIOUS_APK"', upgrade_smoke)
        self.assertIn('adb install -r "$RELEASE_APK"', upgrade_smoke)
        self.assertIn("automatic_updates_enabled", upgrade_smoke)
        self.assertIn("upgrade-preservation-marker.txt", upgrade_smoke)
        self.assertIn("prefs_contents=$(adb shell cat", upgrade_smoke)
        self.assertNotIn("adb shell grep -q", upgrade_smoke)
        self.assertIn("android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d", workflow)
        self.assertIn("99-kvm4all.rules", workflow)
        self.assertIn("set -euo pipefail", upgrade_smoke)
        self.assertIn('APK_FILE_NAME="TPollScanner-$VERSION_NAME-release.apk"', workflow)
        self.assertIn('gh release create "$RELEASE_TAG" "$APK_FILE_NAME"', workflow)
        self.assertNotIn("releases/latest/download/TPollScanner-release.apk", landing)
        self.assertIn("data-apk-download", landing)
        self.assertIn("fetch('update.json', { cache: 'no-store' })", landing)
        self.assertIn("release.versionName ?? release.version_name", landing)
        self.assertIn("TPollScanner-${versionName}-release.apk", landing)
        self.assertNotIn('href="TPollScanner-release.apk"', landing)

    def test_update_manifest_contains_mandatory_integrity_metadata(self) -> None:
        manifest = json.loads((ROOT / "update.json").read_text(encoding="utf-8"))
        release_config = json.loads(
            (ROOT / "release-config.json").read_text(encoding="utf-8")
        )

        self.assertRegex(manifest["sha256"], r"^[0-9A-Fa-f]{64}$")
        self.assertGreater(manifest["sizeBytes"], 0)
        self.assertTrue(manifest["releasedAt"])
        self.assertTrue(manifest["apkUrl"].startswith("https://"))
        self.assertIsInstance(manifest["mandatory"], bool)
        self.assertTrue(manifest["releaseNotes"])
        self.assertEqual(manifest["versionCode"], manifest["version_code"])
        self.assertEqual(manifest["apkUrl"], manifest["apk_url"])
        self.assertIn(
            release_config["versionCode"],
            {manifest["versionCode"], manifest["versionCode"] + 1},
        )
        if release_config["versionCode"] == manifest["versionCode"]:
            self.assertEqual(manifest["versionName"], release_config["versionName"])

    def test_automatic_updater_validates_identity_and_splits_network_work(self) -> None:
        installer = (
            ROOT
            / "android_app/app/src/main/java/com/tpoll/scanner/updater/ApkInstaller.kt"
        ).read_text(encoding="utf-8")
        scheduler = (
            ROOT
            / "android_app/app/src/main/java/com/tpoll/scanner/updater/UpdateScheduler.kt"
        ).read_text(encoding="utf-8")
        manifest = (
            ROOT / "android_app/app/src/main/AndroidManifest.xml"
        ).read_text(encoding="utf-8")

        self.assertIn("archiveInfo.packageName != EXPECTED_PACKAGE_NAME", installer)
        self.assertIn("signingCertificateDigests", installer)
        self.assertIn("SHA_256_PATTERN.matches(info.sha256)", installer)
        self.assertIn("EXPECTED_RELEASE_CERT_SHA256", installer)
        self.assertIn("FileProvider.getUriForFile", installer)
        self.assertIn("FLAG_GRANT_READ_URI_PERMISSION", installer)
        self.assertNotIn("PackageInstaller", installer)
        self.assertNotIn("USER_ACTION_NOT_REQUIRED", installer)
        self.assertIn("NetworkType.CONNECTED", scheduler)
        self.assertIn("NetworkType.UNMETERED", scheduler)
        self.assertIn("PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)", scheduler)
        self.assertIn("BackoffPolicy.EXPONENTIAL", scheduler)
        self.assertIn("androidx.core.content.FileProvider", manifest)
        self.assertIn("${applicationId}.updateprovider", manifest)
        self.assertNotIn("UPDATE_PACKAGES_WITHOUT_USER_ACTION", manifest)
        self.assertNotIn("UpdateInstallReceiver", manifest)

    def test_android_toolchain_targets_current_api_without_forcing_compose_migration(self) -> None:
        root_gradle = (ROOT / "android_app/build.gradle.kts").read_text(encoding="utf-8")
        app_gradle = (ROOT / "android_app/app/build.gradle.kts").read_text(encoding="utf-8")
        wrapper = (
            ROOT / "android_app/gradle/wrapper/gradle-wrapper.properties"
        ).read_text(encoding="utf-8")

        self.assertIn('version "8.13.2"', root_gradle)
        self.assertIn('id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"', root_gradle)
        self.assertIn('id("org.jetbrains.kotlin.plugin.compose")', app_gradle)
        self.assertNotIn("kotlinCompilerExtensionVersion", app_gradle)
        self.assertIn('JsonSlurper().parse(rootProject.file("../update.json"))', app_gradle)
        self.assertIn('manifestValue("versionCode", "version_code")', app_gradle)
        self.assertIn("gradle-8.13-bin.zip", wrapper)
        self.assertIn("compileSdk = 36", app_gradle)
        self.assertIn("targetSdk = 36", app_gradle)
        self.assertIn('androidx.work:work-runtime-ktx:2.11.2', app_gradle)

    def test_background_workers_do_not_start_foreground_protection_from_application(self) -> None:
        application = (
            ROOT / "android_app/app/src/main/java/com/tpoll/scanner/TPollApp.kt"
        ).read_text(encoding="utf-8")
        activity = (
            ROOT / "android_app/app/src/main/java/com/tpoll/scanner/MainActivity.kt"
        ).read_text(encoding="utf-8")
        shield = (
            ROOT
            / "android_app/app/src/main/java/com/tpoll/scanner/protection/ShieldService.kt"
        ).read_text(encoding="utf-8")

        self.assertNotIn("ShieldService.start(this)", application)
        self.assertIn("ShieldService.startIfEnabled(this)", activity)
        self.assertIn("realtime_protection_enabled", shield)
        self.assertIn("androidx.compose.ui.platform.LocalLifecycleOwner", activity)
        self.assertNotIn("androidx.lifecycle.compose.LocalLifecycleOwner", activity)


if __name__ == "__main__":
    unittest.main()
