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

    def test_publish_workflow_requires_and_verifies_release_certificate(self) -> None:
        workflow = (
            ROOT / ".github/workflows/build-and-publish-apk.yml"
        ).read_text(encoding="utf-8")

        required_secrets = {
            "RELEASE_KEYSTORE_BASE64",
            "RELEASE_STORE_PASSWORD",
            "RELEASE_KEY_ALIAS",
            "RELEASE_KEY_PASSWORD",
            "RELEASE_CERT_SHA256",
        }
        for secret in required_secrets:
            self.assertIn(secret, workflow)

        self.assertIn("CN=Android Debug", workflow)
        self.assertIn('ACTUAL_CERT', workflow)
        self.assertIn('EXPECTED_CERT', workflow)
        self.assertNotIn("APK will be signed with debug fallback key", workflow)
        self.assertNotIn("assembleDebug", workflow)
        self.assertNotIn("pull_request:", workflow)
        self.assertIn("./gradlew clean assembleRelease", workflow)

    def test_automatic_updater_validates_identity_before_installing(self) -> None:
        installer = (
            ROOT
            / "android_app/app/src/main/java/com/tpoll/scanner/updater/ApkInstaller.kt"
        ).read_text(encoding="utf-8")
        manifest = (
            ROOT / "android_app/app/src/main/AndroidManifest.xml"
        ).read_text(encoding="utf-8")

        self.assertIn("archiveInfo.packageName != context.packageName", installer)
        self.assertIn("signingCertificateDigests", installer)
        self.assertIn("USER_ACTION_NOT_REQUIRED", installer)
        self.assertIn("UPDATE_PACKAGES_WITHOUT_USER_ACTION", manifest)
        self.assertIn("UpdateInstallReceiver", manifest)


if __name__ == "__main__":
    unittest.main()
