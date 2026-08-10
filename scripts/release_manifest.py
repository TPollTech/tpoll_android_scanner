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


def expected_apk_url(version_name: str) -> str:
    return (
        f"{RELEASE_BASE_URL}/v{version_name}/"
        f"TPollScanner-{version_name}-release.apk"
    )


def load_manifest(path: pathlib.Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        raise ValueError("update manifest must be a JSON object")
    return data


def next_version(manifest: dict) -> tuple[int, str]:
    current_code = int(manifest["version_code"])
    match = VERSION_PATTERN.fullmatch(str(manifest["version_name"]))
    if current_code <= 0 or match is None:
        raise ValueError("current version_code/version_name is invalid")
    major, minor, patch = (int(part) for part in match.groups())
    return current_code + 1, f"{major}.{minor}.{patch + 1}"


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def finalize_manifest(
    manifest: dict,
    apk_path: pathlib.Path,
    version_code: int,
    version_name: str,
    apk_url: str,
    released_at: str | None = None,
) -> dict:
    if not apk_path.is_file() or apk_path.stat().st_size <= 0:
        raise ValueError("release APK is missing or empty")
    if version_code <= 0 or VERSION_PATTERN.fullmatch(version_name) is None:
        raise ValueError("release version is invalid")
    if not apk_url.startswith("https://"):
        raise ValueError("APK URL must use HTTPS")

    result = dict(manifest)
    result.update(
        version_code=version_code,
        version_name=version_name,
        apk_url=apk_url,
        sha256=sha256(apk_path),
        size_bytes=apk_path.stat().st_size,
        released_at=released_at
        or datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    )
    return result


def validate_manifest(manifest: dict, apk_path: pathlib.Path | None = None) -> list[str]:
    errors: list[str] = []
    try:
        version_code = int(manifest.get("version_code", 0))
        if version_code <= 0:
            errors.append("version_code must be positive")
    except (TypeError, ValueError):
        version_code = 0
        errors.append("version_code must be an integer")
    if VERSION_PATTERN.fullmatch(str(manifest.get("version_name", ""))) is None:
        errors.append("version_name must use major.minor.patch")
    if not str(manifest.get("download_url", "")).startswith("https://"):
        errors.append("download_url must use HTTPS")
    apk_url = str(manifest.get("apk_url", ""))
    version_name = str(manifest.get("version_name", ""))
    if not apk_url.startswith("https://"):
        errors.append("apk_url must use HTTPS")
    elif VERSION_PATTERN.fullmatch(version_name) is not None and apk_url != expected_apk_url(
        version_name
    ):
        errors.append("apk_url must use the canonical versioned release filename")
    manifest_hash = str(manifest.get("sha256", "")).upper()
    if SHA_256_PATTERN.fullmatch(manifest_hash) is None:
        errors.append("sha256 must contain exactly 64 hexadecimal characters")
    try:
        manifest_size = int(manifest.get("size_bytes", 0))
        if manifest_size <= 0 or manifest_size > MAX_APK_BYTES:
            errors.append("size_bytes must be between 1 and 250 MiB")
    except (TypeError, ValueError):
        manifest_size = 0
        errors.append("size_bytes must be an integer")
    if not str(manifest.get("released_at", "")):
        errors.append("released_at is required")
    else:
        try:
            datetime.fromisoformat(str(manifest["released_at"]).replace("Z", "+00:00"))
        except ValueError:
            errors.append("released_at must be an ISO-8601 timestamp")
    try:
        minimum_version_code = int(manifest.get("min_version_code", 0))
        if minimum_version_code <= 0 or minimum_version_code > version_code:
            errors.append("min_version_code must be between 1 and version_code")
    except (TypeError, ValueError):
        errors.append("min_version_code must be an integer")

    if apk_path is not None:
        if not apk_path.is_file():
            errors.append("APK file does not exist")
        else:
            if apk_path.stat().st_size != manifest_size:
                errors.append("APK size does not match size_bytes")
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
    output = f"version_code={version_code}\nversion_name={version_name}\n"
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8", newline="\n") as handle:
            handle.write(output)
    else:
        print(output, end="")


def command_finalize(args: argparse.Namespace) -> None:
    manifest = finalize_manifest(
        manifest=load_manifest(args.manifest),
        apk_path=args.apk,
        version_code=args.version_code,
        version_name=args.version_name,
        apk_url=args.apk_url,
        released_at=args.released_at,
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
    next_parser.add_argument("--github-output", type=pathlib.Path)
    next_parser.set_defaults(func=command_next)

    finalize_parser = subcommands.add_parser("finalize")
    finalize_parser.add_argument("--manifest", type=pathlib.Path, required=True)
    finalize_parser.add_argument("--apk", type=pathlib.Path, required=True)
    finalize_parser.add_argument("--version-code", type=int, required=True)
    finalize_parser.add_argument("--version-name", required=True)
    finalize_parser.add_argument("--apk-url", required=True)
    finalize_parser.add_argument("--released-at")
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
