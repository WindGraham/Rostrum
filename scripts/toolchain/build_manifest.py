#!/usr/bin/env python3
"""Generate or validate toolchain manifest with SHA256 checks.

Usage:
  python scripts/toolchain/build_manifest.py generate --abi-dir <dir> --version v1
  python scripts/toolchain/build_manifest.py validate --abi-dir <dir>
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import sys
from typing import Any

MANIFEST_NAME = "manifest.json"
EXCLUDED_FILES = {MANIFEST_NAME, "README.md", "README.txt"}
EXCLUDED_FILES.update({"metadata.auto.json", "metadata.json"})
EXCLUDED_PATH_PREFIXES = ("jre/",)
DEFAULT_REQUIRED_ARTIFACT_NAMES = {"aapt2", "d8", "zipalign", "apksigner", "android.jar"}


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def list_artifact_files(abi_dir: pathlib.Path) -> list[pathlib.Path]:
    files: list[pathlib.Path] = []
    for p in sorted(abi_dir.rglob("*")):
        if not p.is_file() or p.name in EXCLUDED_FILES:
            continue

        rel = p.relative_to(abi_dir).as_posix()
        if any(rel.startswith(prefix) for prefix in EXCLUDED_PATH_PREFIXES):
            continue

        files.append(p)
    return files


def load_metadata(metadata_path: pathlib.Path | None) -> dict[str, Any]:
    if metadata_path is None:
        return {}
    if not metadata_path.exists():
        raise FileNotFoundError(f"Metadata file not found: {metadata_path}")
    return json.loads(metadata_path.read_text(encoding="utf-8"))


def load_existing_manifest_metadata(abi_dir: pathlib.Path) -> dict[str, Any]:
    manifest_path = abi_dir / MANIFEST_NAME
    if not manifest_path.exists():
        return {}

    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except Exception:
        return {}

    artifacts = manifest.get("artifacts", [])
    result: dict[str, Any] = {}
    if not isinstance(artifacts, list):
        return result

    for item in artifacts:
        if not isinstance(item, dict):
            continue
        rel = item.get("relativePath")
        if not isinstance(rel, str) or not rel.strip():
            continue
        result[rel] = {
            "name": item.get("name", ""),
            "sourceUrl": item.get("sourceUrl", ""),
            "versionOrCommit": item.get("versionOrCommit", ""),
            "license": item.get("license", ""),
            "executable": bool(item.get("executable", False)),
        }
    return result


def make_artifact_entry(
    file_path: pathlib.Path,
    abi_dir: pathlib.Path,
    metadata: dict[str, Any],
) -> dict[str, Any]:
    rel = file_path.relative_to(abi_dir).as_posix()
    info = metadata.get(rel, {})

    name = info.get("name") or file_path.name
    source_url = info.get("sourceUrl", "")
    version_or_commit = info.get("versionOrCommit", "")
    license_name = info.get("license", "")
    executable = bool(info.get("executable", os.access(file_path, os.X_OK)))

    return {
        "name": name,
        "relativePath": rel,
        "sha256": sha256_file(file_path),
        "sizeBytes": file_path.stat().st_size,
        "executable": executable,
        "sourceUrl": source_url,
        "versionOrCommit": version_or_commit,
        "license": license_name,
    }


def command_generate(args: argparse.Namespace) -> int:
    abi_dir = pathlib.Path(args.abi_dir).resolve()
    if not abi_dir.exists() or not abi_dir.is_dir():
        print(f"[FAIL] abi dir not found: {abi_dir}")
        return 1

    metadata = load_existing_manifest_metadata(abi_dir)
    external_metadata = load_metadata(pathlib.Path(args.metadata).resolve() if args.metadata else None)
    metadata.update(external_metadata)

    artifacts = [make_artifact_entry(f, abi_dir, metadata) for f in list_artifact_files(abi_dir)]

    manifest = {
        "schemaVersion": "1.0.0",
        "abi": args.abi,
        "version": args.version,
        "artifacts": artifacts,
    }

    output = pathlib.Path(args.output).resolve() if args.output else abi_dir / MANIFEST_NAME
    output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"[PASS] manifest generated: {output}")
    print(f"       artifacts: {len(artifacts)}")
    return 0


def parse_required_artifacts(required_arg: str) -> set[str]:
    if not required_arg.strip():
        return set(DEFAULT_REQUIRED_ARTIFACT_NAMES)

    required = {
        name.strip()
        for name in required_arg.split(",")
        if name.strip()
    }
    return required or set(DEFAULT_REQUIRED_ARTIFACT_NAMES)


def command_validate(args: argparse.Namespace) -> int:
    abi_dir = pathlib.Path(args.abi_dir).resolve()
    manifest_path = pathlib.Path(args.manifest).resolve() if args.manifest else abi_dir / MANIFEST_NAME

    if not manifest_path.exists():
        print(f"[FAIL] manifest not found: {manifest_path}")
        return 1

    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except Exception as exc:
        print(f"[FAIL] invalid manifest JSON: {exc}")
        return 1

    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list):
        print("[FAIL] manifest.artifacts must be a list")
        return 1
    if not artifacts:
        print("[FAIL] manifest.artifacts must not be empty")
        return 1

    required_artifacts = parse_required_artifacts(args.required)
    errors: list[str] = []
    artifact_names: set[str] = set()
    for item in artifacts:
        rel = item.get("relativePath")
        sha = item.get("sha256")
        size = item.get("sizeBytes")
        item_name = item.get("name")

        if not rel or not isinstance(rel, str):
            errors.append("artifact missing relativePath")
            continue

        target = abi_dir / rel
        artifact_names.add(target.name)
        if isinstance(item_name, str) and item_name.strip():
            artifact_names.add(item_name.strip())
        if not target.exists() or not target.is_file():
            errors.append(f"missing file: {rel}")
            continue

        actual_sha = sha256_file(target)
        if not isinstance(sha, str) or actual_sha.lower() != sha.lower():
            errors.append(f"sha mismatch: {rel}")

        if isinstance(size, int) and size > 0 and target.stat().st_size != size:
            errors.append(f"size mismatch: {rel}")

        if item.get("executable") is True and not os.access(target, os.X_OK):
            errors.append(f"not executable: {rel}")

        # Ensure traceability metadata is present for commercial compliance.
        for field in ("name", "sourceUrl", "versionOrCommit", "license"):
            value = item.get(field)
            if not isinstance(value, str) or not value.strip():
                errors.append(f"missing {field}: {rel}")

    missing_required = sorted(required_artifacts - artifact_names)
    if missing_required:
        errors.append(f"missing required artifacts: {', '.join(missing_required)}")

    # If java is provided as a wrapper that forwards to a bundled runtime,
    # enforce that runtime exists to avoid late build-stage failures.
    java_wrapper = abi_dir / "java"
    if java_wrapper.exists() and java_wrapper.is_file():
        wrapper_text = ""
        try:
            wrapper_text = java_wrapper.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            wrapper_text = ""

        if "jre/bin/java" in wrapper_text:
            has_system_fallback = (
                "/system/bin/java" in wrapper_text
                or "/system/bin/dalvikvm" in wrapper_text
                or "/apex/com.android.art/bin/dalvikvm" in wrapper_text
            )
            if has_system_fallback:
                wrapper_text = ""

        if "jre/bin/java" in wrapper_text:
            bundled_java = abi_dir / "jre/bin/java"
            if not bundled_java.exists() or not bundled_java.is_file():
                errors.append("java wrapper references jre/bin/java but file is missing")
            elif not os.access(bundled_java, os.X_OK):
                errors.append("java wrapper target not executable: jre/bin/java")

    if errors:
        print("[FAIL] toolchain manifest validation failed:")
        for err in errors:
            print(f"  - {err}")
        return 1

    print(f"[PASS] toolchain manifest validated: {manifest_path}")
    print(f"       artifacts: {len(artifacts)}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate/validate Android toolchain manifest")
    sub = parser.add_subparsers(dest="command", required=True)

    generate = sub.add_parser("generate", help="Generate manifest from abi dir")
    generate.add_argument("--abi-dir", required=True)
    generate.add_argument("--abi", default="arm64-v8a")
    generate.add_argument("--version", default="v1")
    generate.add_argument("--metadata", default="")
    generate.add_argument("--output", default="")

    validate = sub.add_parser("validate", help="Validate manifest and file checksums")
    validate.add_argument("--abi-dir", required=True)
    validate.add_argument("--manifest", default="")
    validate.add_argument(
        "--required",
        default="",
        help=(
            "Comma-separated required artifact names. "
            f"Default: {','.join(sorted(DEFAULT_REQUIRED_ARTIFACT_NAMES))}"
        ),
    )

    args = parser.parse_args()

    if args.command == "generate":
        return command_generate(args)
    if args.command == "validate":
        return command_validate(args)

    print(f"[FAIL] unknown command: {args.command}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
