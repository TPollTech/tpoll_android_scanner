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
        return {
            "version_code": 24,
            "version_name": "1.8.11",
            "changelog": "Teste",
            "download_url": "https://example.com/download",
            "apk_url": expected_apk_url("1.8.11"),
            "sha256": "0" * 64,
            "size_bytes": 1,
            "released_at": "2026-08-08T00:00:00Z",
            "min_version_code": 1,
        }

    def test_next_version_increments_code_and_patch(self) -> None:
        self.assertEqual((25, "1.8.12"), next_version(self.base_manifest()))

    def test_finalize_uses_real_apk_hash_and_size(self) -> None:
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
            )

            self.assertEqual(apk.stat().st_size, manifest["size_bytes"])
            self.assertEqual(64, len(manifest["sha256"]))
            self.assertEqual([], validate_manifest(manifest, apk))

    def test_validation_rejects_stale_hash(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            apk = pathlib.Path(directory) / "release.apk"
            apk.write_bytes(b"new-apk")
            errors = validate_manifest(self.base_manifest(), apk)
            self.assertIn("APK SHA-256 does not match manifest", errors)

    def test_validation_rejects_impossible_minimum_version(self) -> None:
        manifest = self.base_manifest() | {"min_version_code": 25}
        self.assertIn(
            "min_version_code must be between 1 and version_code",
            validate_manifest(manifest),
        )

    def test_validation_rejects_ambiguous_release_filename(self) -> None:
        manifest = self.base_manifest() | {
            "apk_url": (
                "https://github.com/TPollTech/tpoll_android_scanner/"
                "releases/download/v1.8.11/TPollScanner-release.apk"
            )
        }
        self.assertIn(
            "apk_url must use the canonical versioned release filename",
            validate_manifest(manifest),
        )

    def test_write_manifest_preserves_unicode(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "update.json"
            manifest = self.base_manifest() | {"changelog": "Atualização — segurança"}
            write_manifest(path, manifest)
            loaded = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual("Atualização — segurança", loaded["changelog"])


if __name__ == "__main__":
    unittest.main()
