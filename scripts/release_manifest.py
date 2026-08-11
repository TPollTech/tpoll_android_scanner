#!/usr/bin/env python3
"""Prepare and validate the canonical self-hosted Android release manifest."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import re
from datetime import datetime, timezone


VERSION_PATTERN = re.compile(r"^(\d+)\.(\d+)\.(\d+)$")
SHA_256_PATTERN = re.compile(r"^[0-9A-F]{64}$")
MAX_APK_BYTES = 250 * 1024 * 1024
RELEASE_BASE_URL = (
    "https://github.com/TPollTech/tpoll_android_scanner/releases/download"
)

# compatibility: clients through 1.8.13 read these snake_case names. Keep both
# representations identical until those clients are outside the support window.
LEGACY_ALIASES = {
    "versionCode": "version_code",
    "versionName": "version_name",
    "apkUrl": "apk_url",
    "downloadUrl": "download_url",
    "sizeBytes": "size_bytes",
    "releasedAt": "released_at",
    "minVersionCode": "min_version_code",
}


def expected_apk_url(version_name: str) -> str:
    return (
        f"{RELEASE_BASE_URL}/v{version_name}/"
        f"TPollScanner-{version_name}-release.apk"
    )


def load_json_object(path: pathlib.Path, label: str) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        raise ValueError(f"{label} must be a JSON object")
    return data


def load_manifest(path: pathlib.Path) -> dict:
    return load_json_object(path, "update manifest")


def manifest_value(manifest: dict, canonical: str):
    legacy = LEGACY_ALIASES.get(canonical)
    has_canonical = canonical in manifest
    has_legacy = legacy is not None and legacy in manifest
    if not has_canonical and not has_legacy:
        raise KeyError(canonical)
    if has_canonical and has_legacy and manifest[canonical] != manifest[legacy]:
        raise ValueError(f"{canonical} and {legacy} must match")
    return manifest[canonical] if has_canonical else manifest[legacy]


def next_version(manifest: dict) -> tuple[int, str]:
    current_code = int(manifest_value(manifest, "versionCode"))
    current_name = str(manifest_value(manifest, "versionName"))
    match = VERSION_PATTERN.fullmatch(current_name)
    if current_code <= 0 or match is None:
        raise ValueError("current versionCode/versionName is invalid")
    major, minor, patch = (int(part) for part in match.groups())
    return current_code + 1, f"{major}.{minor}.{patch + 1}"


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def sync_legacy_fields(manifest: dict) -> dict:
    result = dict(manifest)
    for canonical, legacy in LEGACY_ALIASES.items():
        result[legacy] = result[canonical]
    result["changelog"] = "\n".join(result["releaseNotes"])
    return result


def validate_release_config_version(
    release_config: dict,
    version_code: int,
    version_name: str,
) -> None:
    if release_config.get("versionCode") != version_code:
        raise ValueError(
            "release config versionCode must match the version being published"
        )
    if release_config.get("versionName") != version_name:
        raise ValueError(
            "release config versionName must match the version being published"
        )


def finalize_manifest(
    manifest: dict,
    apk_path: pathlib.Path,
    version_code: int,
    version_name: str,
    apk_url: str,
    released_at: str | None = None,
    release_config: dict | None = None,
) -> dict:
    if not apk_path.is_file() or apk_path.stat().st_size <= 0:
        raise ValueError("release APK is missing or empty")
    if version_code <= 0 or VERSION_PATTERN.fullmatch(version_name) is None:
        raise ValueError("release version is invalid")
    if not apk_url.startswith("https://"):
        raise ValueError("APK URL must use HTTPS")

    result = dict(manifest)
    if release_config is not None:
        validate_release_config_version(release_config, version_code, version_name)
        release_notes = release_config.get("releaseNotes")
        mandatory = release_config.get("mandatory")
        minimum = release_config.get("minVersionCode", 1)
        if (
            not isinstance(release_notes, list)
            or not release_notes
            or not all(isinstance(note, str) and note.strip() for note in release_notes)
        ):
            raise ValueError("release config must contain non-empty releaseNotes")
        if not isinstance(mandatory, bool):
            raise ValueError("release config mandatory must be boolean")
        result["releaseNotes"] = [note.strip() for note in release_notes]
        result["mandatory"] = mandatory
        result["minVersionCode"] = int(minimum)

    result.update(
        versionCode=version_code,
        versionName=version_name,
        apkUrl=apk_url,
        sha256=sha256(apk_path),
        sizeBytes=apk_path.stat().st_size,
        releasedAt=released_at
        or datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    )
    return sync_legacy_fields(result)


def validate_manifest(manifest: dict, apk_path: pathlib.Path | None = None) -> list[str]:
    errors: list[str] = []

    for canonical, legacy in LEGACY_ALIASES.items():
        if canonical not in manifest:
            errors.append(f"{canonical} is required")
        if legacy not in manifest:
            errors.append(f"{legacy} compatibility alias is required")
        if canonical in manifest and legacy in manifest and manifest[canonical] != manifest[legacy]:
            errors.append(f"{canonical} and {legacy} must match")

    try:
        version_code = int(manifest.get("versionCode", 0))
        if version_code <= 0:
            errors.append("versionCode must be positive")
    except (TypeError, ValueError):
        version_code = 0
        errors.append("versionCode must be an integer")

    version_name = str(manifest.get("versionName", ""))
    if VERSION_PATTERN.fullmatch(version_name) is None:
        errors.append("versionName must use major.minor.patch")

    if not str(manifest.get("downloadUrl", "")).startswith("https://"):
        errors.append("downloadUrl must use HTTPS")

    apk_url = str(manifest.get("apkUrl", ""))
    if not apk_url.startswith("https://"):
        errors.append("apkUrl must use HTTPS")
    elif VERSION_PATTERN.fullmatch(version_name) is not None and apk_url != expected_apk_url(
        version_name
    ):
        errors.append("apkUrl must use the canonical versioned release filename")

    manifest_hash = str(manifest.get("sha256", "")).upper()
    if SHA_256_PATTERN.fullmatch(manifest_hash) is None:
        errors.append("sha256 must contain exactly 64 hexadecimal characters")

    try:
        manifest_size = int(manifest.get("sizeBytes", 0))
        if manifest_size <= 0 or manifest_size > MAX_APK_BYTES:
            errors.append("sizeBytes must be between 1 and 250 MiB")
    except (TypeError, ValueError):
        manifest_size = 0
        errors.append("sizeBytes must be an integer")

    if not str(manifest.get("releasedAt", "")):
        errors.append("releasedAt is required")
    else:
        try:
            datetime.fromisoformat(str(manifest["releasedAt"]).replace("Z", "+00:00"))
        except ValueError:
            errors.append("releasedAt must be an ISO-8601 timestamp")

    try:
        minimum_version_code = int(manifest.get("minVersionCode", 0))
        if minimum_version_code <= 0 or minimum_version_code > version_code:
            errors.append("minVersionCode must be between 1 and versionCode")
    except (TypeError, ValueError):
        errors.append("minVersionCode must be an integer")

    if not isinstance(manifest.get("mandatory"), bool):
        errors.append("mandatory must be boolean")

    release_notes = manifest.get("releaseNotes")
    if (
        not isinstance(release_notes, list)
        or not release_notes
        or not all(isinstance(note, str) and note.strip() for note in release_notes)
    ):
        errors.append("releaseNotes must contain at least one non-empty item")
    elif manifest.get("changelog") != "\n".join(release_notes):
        errors.append("changelog compatibility field must match releaseNotes")

    if apk_path is not None:
        if not apk_path.is_file():
            errors.append("APK file does not exist")
        else:
            if apk_path.stat().st_size != manifest_size:
                errors.append("APK size does not match sizeBytes")
            if sha256(apk_path) != manifest_hash:
                errors.append("APK SHA-256 does not match manifest")
    return errors


def write_manifest(path: pathlib.Path, manifest: dict) -> None:
    temporary_path = path.with_suffix(path.suffix + ".tmp")
    with temporary_path.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(manifest, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    os.replace(temporary_path, path)


def command_next(args: argparse.Namespace) -> None:
    version_code, version_name = next_version(load_manifest(args.manifest))
    if args.release_config:
        validate_release_config_version(
            load_json_object(args.release_config, "release config"),
            version_code,
            version_name,
        )
    output = f"version_code={version_code}\nversion_name={version_name}\n"
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8", newline="\n") as handle:
            handle.write(output)
    else:
        print(output, end="")


def command_finalize(args: argparse.Namespace) -> None:
    release_config = (
        load_json_object(args.release_config, "release config")
        if args.release_config
        else None
    )
    manifest = finalize_manifest(
        manifest=load_manifest(args.manifest),
        apk_path=args.apk,
        version_code=args.version_code,
        version_name=args.version_name,
        apk_url=args.apk_url,
        released_at=args.released_at,
        release_config=release_config,
    )
    write_manifest(args.manifest, manifest)
    errors = validate_manifest(manifest, args.apk)
    if errors:
        raise ValueError("; ".join(errors))


def command_verify(args: argparse.Namespace) -> None:
    errors = validate_manifest(load_manifest(args.manifest), args.apk)
    if errors:
        raise ValueError("; ".join(errors))
    print("Release manifest matches the APK.")


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser()
    subcommands = root.add_subparsers(dest="command", required=True)

    next_parser = subcommands.add_parser("next")
    next_parser.add_argument("--manifest", type=pathlib.Path, required=True)
    next_parser.add_argument("--release-config", type=pathlib.Path)
    next_parser.add_argument("--github-output", type=pathlib.Path)
    next_parser.set_defaults(func=command_next)

    finalize_parser = subcommands.add_parser("finalize")
    finalize_parser.add_argument("--manifest", type=pathlib.Path, required=True)
    finalize_parser.add_argument("--apk", type=pathlib.Path, required=True)
    finalize_parser.add_argument("--version-code", type=int, required=True)
    finalize_parser.add_argument("--version-name", required=True)
    finalize_parser.add_argument("--apk-url", required=True)
    finalize_parser.add_argument("--released-at")
    finalize_parser.add_argument("--release-config", type=pathlib.Path)
    finalize_parser.set_defaults(func=command_finalize)

    verify_parser = subcommands.add_parser("verify")
    verify_parser.add_argument("--manifest", type=pathlib.Path, required=True)
    verify_parser.add_argument("--apk", type=pathlib.Path, required=True)
    verify_parser.set_defaults(func=command_verify)
    return root


def main() -> None:
    args = parser().parse_args()
    try:
        args.func(args)
    except (KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
        raise SystemExit(f"release manifest error: {error}") from error


if __name__ == "__main__":
    main()
