import json
import pathlib
import tempfile
import unittest

from release_manifest import (
    expected_apk_url,
    finalize_manifest,
    next_version,
    validate_manifest,
    write_manifest,
)


class ReleaseManifestTests(unittest.TestCase):
    def base_manifest(self) -> dict:
        notes = ["Correção", "Melhoria"]
        return {
            "versionCode": 24,
            "versionName": "1.8.11",
            "apkUrl": expected_apk_url("1.8.11"),
            "sha256": "0" * 64,
            "mandatory": False,
            "releaseNotes": notes,
            "downloadUrl": "https://example.com/download",
            "sizeBytes": 1,
            "releasedAt": "2026-08-08T00:00:00Z",
            "minVersionCode": 1,
            "version_code": 24,
            "version_name": "1.8.11",
            "apk_url": expected_apk_url("1.8.11"),
            "changelog": "\n".join(notes),
            "download_url": "https://example.com/download",
            "size_bytes": 1,
            "released_at": "2026-08-08T00:00:00Z",
            "min_version_code": 1,
        }

    def release_config(self) -> dict:
        return {
            "versionCode": 25,
            "versionName": "1.8.12",
            "mandatory": False,
            "minVersionCode": 1,
            "releaseNotes": ["Novo updater", "Instalação oficial"],
        }

    def test_next_version_increments_code_and_patch(self) -> None:
        self.assertEqual((25, "1.8.12"), next_version(self.base_manifest()))

    def test_finalize_uses_real_apk_hash_size_notes_and_aliases(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            apk = pathlib.Path(directory) / "release.apk"
            apk.write_bytes(b"signed-apk-fixture")
            manifest = finalize_manifest(
                self.base_manifest(),
                apk,
                version_code=25,
                version_name="1.8.12",
                apk_url=expected_apk_url("1.8.12"),
                released_at="2026-08-08T01:00:00Z",
                release_config=self.release_config(),
            )

            self.assertEqual(apk.stat().st_size, manifest["sizeBytes"])
            self.assertEqual(64, len(manifest["sha256"]))
            self.assertEqual(manifest["versionCode"], manifest["version_code"])
            self.assertEqual(manifest["apkUrl"], manifest["apk_url"])
            self.assertEqual("\n".join(manifest["releaseNotes"]), manifest["changelog"])
            self.assertEqual([], validate_manifest(manifest, apk))

    def test_finalize_rejects_stale_release_config(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            apk = pathlib.Path(directory) / "release.apk"
            apk.write_bytes(b"signed-apk-fixture")
            stale_config = self.release_config() | {
                "versionCode": 24,
                "versionName": "1.8.11",
            }

            with self.assertRaisesRegex(ValueError, "versionCode must match"):
                finalize_manifest(
                    self.base_manifest(),
                    apk,
                    version_code=25,
                    version_name="1.8.12",
                    apk_url=expected_apk_url("1.8.12"),
                    release_config=stale_config,
                )

    def test_validation_rejects_stale_hash(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            apk = pathlib.Path(directory) / "release.apk"
            apk.write_bytes(b"new-apk")
            errors = validate_manifest(self.base_manifest(), apk)
            self.assertIn("APK SHA-256 does not match manifest", errors)

    def test_validation_rejects_impossible_minimum_version(self) -> None:
        manifest = self.base_manifest() | {
            "minVersionCode": 25,
            "min_version_code": 25,
        }
        self.assertIn(
            "minVersionCode must be between 1 and versionCode",
            validate_manifest(manifest),
        )

    def test_validation_rejects_ambiguous_release_filename(self) -> None:
        bad_url = (
            "https://github.com/TPollTech/tpoll_android_scanner/"
            "releases/download/v1.8.11/TPollScanner-release.apk"
        )
        manifest = self.base_manifest() | {"apkUrl": bad_url, "apk_url": bad_url}
        self.assertIn(
            "apkUrl must use the canonical versioned release filename",
            validate_manifest(manifest),
        )

    def test_validation_rejects_divergent_legacy_alias(self) -> None:
        manifest = self.base_manifest() | {"version_code": 999}
        self.assertIn("versionCode and version_code must match", validate_manifest(manifest))

    def test_write_manifest_preserves_unicode(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "update.json"
            manifest = self.base_manifest() | {
                "releaseNotes": ["Atualização — segurança"],
                "changelog": "Atualização — segurança",
            }
            write_manifest(path, manifest)
            loaded = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual("Atualização — segurança", loaded["releaseNotes"][0])


if __name__ == "__main__":
    unittest.main()
