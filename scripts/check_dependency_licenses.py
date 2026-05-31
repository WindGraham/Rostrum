#!/usr/bin/env python3
"""Dependency license gate for OmniMaster.

- Parses dependency coordinates from Gradle Kotlin script.
- Verifies coordinates against allowlist/blocklist patterns in BOM.
"""

from __future__ import annotations

import argparse
import fnmatch
import json
import pathlib
import re
import sys
from dataclasses import dataclass


COORD_RE = re.compile(
    r'(?:implementation|api|ksp|kapt|testImplementation|androidTestImplementation|debugImplementation)\("([^"]+:[^"]+:[^"]+)"'
)


@dataclass(frozen=True)
class Coordinate:
    group: str
    artifact: str
    version: str

    @property
    def key(self) -> str:
        return f"{self.group}:{self.artifact}"



def parse_coordinates(gradle_file: pathlib.Path) -> list[Coordinate]:
    coords: list[Coordinate] = []
    for line in gradle_file.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped.startswith("//"):
            continue
        if not stripped:
            continue
        for raw in COORD_RE.findall(line):
            parts = raw.split(":")
            if len(parts) != 3:
                continue
            group, artifact, version = parts
            if group.strip() == "":
                continue
            coords.append(Coordinate(group.strip(), artifact.strip(), version.strip()))
    return coords



def matches_any(value: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatch(value, pattern) for pattern in patterns)



def main() -> int:
    parser = argparse.ArgumentParser(description="Check dependency coordinates against legal BOM policies")
    parser.add_argument("--bom", default="docs/legal/THIRD_PARTY_BOM.json")
    parser.add_argument("--gradle", default="app/build.gradle.kts")
    args = parser.parse_args()

    bom_path = pathlib.Path(args.bom)
    gradle_path = pathlib.Path(args.gradle)

    if not bom_path.exists():
        print(f"[FAIL] BOM file not found: {bom_path}")
        return 1
    if not gradle_path.exists():
        print(f"[FAIL] Gradle file not found: {gradle_path}")
        return 1

    bom = json.loads(bom_path.read_text(encoding="utf-8"))
    allowed_patterns = bom.get("allowedCoordinatePatterns", [])
    blocked_patterns = bom.get("blockedCoordinates", [])

    if not allowed_patterns:
        print("[FAIL] allowedCoordinatePatterns is empty in BOM")
        return 1

    coordinates = parse_coordinates(gradle_path)
    unique = sorted({c.key for c in coordinates})

    errors: list[str] = []
    for key in unique:
        if matches_any(key, blocked_patterns):
            errors.append(f"Blocked dependency: {key}")
            continue
        if not matches_any(key, allowed_patterns):
            errors.append(f"Dependency not in allowlist patterns: {key}")

    if errors:
        print("[FAIL] License gate rejected dependencies:")
        for e in errors:
            print(f"  - {e}")
        return 1

    print(f"[PASS] License gate passed ({len(unique)} unique dependencies checked)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
