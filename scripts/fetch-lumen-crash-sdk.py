#!/usr/bin/env python3
"""Materialize the pinned lumen-crash Maven coordinates from Project-Lumen release assets.

Reads the version and the per-asset sha256 digests from `lumen-crash.version`, downloads the
`lumen-crash` and `lumen-crash-core` artifacts into `local-maven/` so Gradle can resolve them,
and writes the version to `lumen-crash.resolved.version` for app/build.gradle.kts.

There is no "latest" mode on purpose: the SDK ends up inside signed release APKs, so which
build goes in has to be a reviewed commit. Every downloaded byte is checked against the
recorded digest.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import urllib.error
import urllib.request
from pathlib import Path


DEFAULT_OWNER = "Chloemlla"
DEFAULT_REPO = "Project-Lumen"
DEFAULT_VERSION_FILE = Path("lumen-crash.version")
DEFAULT_RESOLVED_VERSION_FILE = Path("lumen-crash.resolved.version")
DEFAULT_LOCAL_MAVEN = Path("local-maven")

TAG_PREFIX = "lumen-crash-v"

# Packages published to the release that dependents must materialize together.
PACKAGES = ["lumen-crash", "lumen-crash-core"]


def read_pin(path: Path) -> tuple[str, dict[str, str]]:
    """Return (version, {asset name: sha256}) from the committed pin file."""
    if not path.is_file():
        raise SystemExit(
            f"{path} is missing: pin the lumen-crash SDK version and asset digests there"
        )
    version = ""
    digests: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        text = line.strip()
        if not text or text.startswith("#"):
            continue
        parts = text.split()
        if parts[0] == "sha256":
            if len(parts) != 3:
                raise SystemExit(f"{path}: malformed sha256 line: {text}")
            digests[parts[1]] = parts[2].lower()
        elif not version:
            version = text
    if not version:
        raise SystemExit(f"{path}: no version found")
    return version, digests


def auth_headers() -> dict[str, str]:
    headers = {
        "Accept": "*/*",
        "User-Agent": "cdict-lumen-crash-bootstrap",
    }
    token = (
        os.environ.get("LUMEN_CRASH_READ_PACKAGES_TOKEN")
        or os.environ.get("GH_TOKEN")
        or os.environ.get("GITHUB_TOKEN")
        or ""
    ).strip()
    if token:
        headers["Authorization"] = f"Bearer {token}"
        headers["X-GitHub-Api-Version"] = "2022-11-28"
    return headers


def http_get(url: str) -> bytes:
    request = urllib.request.Request(url, headers=auth_headers())
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise SystemExit(f"HTTP {error.code} for {url}: {body}") from error


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def write_resolved_version(path: Path, version: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(version.strip() + "\n", encoding="utf-8")
    print(f"Wrote resolved version to {path}")


def materialize(version: str, owner: str, repo: str, local_maven: Path, digests: dict[str, str]) -> None:
    tag = f"{TAG_PREFIX}{version}"
    base = f"https://github.com/{owner}/{repo}/releases/download/{tag}"

    for package in PACKAGES:
        required = [f"{package}-{version}.aar", f"{package}-{version}.pom"]
        missing = [name for name in required if name not in digests]
        if missing:
            raise SystemExit(
                "No sha256 recorded for " + ", ".join(missing) + "; record the digests of "
                f"version {version} in the pin file before building against it"
            )
        extras = sorted(
            name
            for name in digests
            if name.startswith(f"{package}-{version}") and name not in required
        )

        # Keep only the currently pinned version tree so stale coordinates do not linger.
        package_root = local_maven / "com" / "chloemlla" / "lumen" / package
        if package_root.exists():
            shutil.rmtree(package_root)
        target_dir = package_root / version
        target_dir.mkdir(parents=True, exist_ok=True)

        for name in required + extras:
            data = http_get(f"{base}/{name}")
            actual = sha256_bytes(data)
            if actual != digests[name]:
                raise SystemExit(
                    f"sha256 mismatch for {name}: expected {digests[name]}, got {actual}"
                )
            (target_dir / name).write_bytes(data)

        print(f"Materialized com.chloemlla.lumen:{package}:{version} into {target_dir} (sha256 verified)")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version-file", type=Path, default=DEFAULT_VERSION_FILE)
    parser.add_argument(
        "--resolved-version-file",
        type=Path,
        default=DEFAULT_RESOLVED_VERSION_FILE,
    )
    parser.add_argument("--local-maven", type=Path, default=DEFAULT_LOCAL_MAVEN)
    parser.add_argument("--owner", default=DEFAULT_OWNER)
    parser.add_argument("--repo", default=DEFAULT_REPO)
    args = parser.parse_args()

    version, digests = read_pin(args.version_file)
    print(f"Using pinned lumen-crash version: {version}")

    write_resolved_version(args.resolved_version_file, version)
    materialize(version, args.owner, args.repo, args.local_maven, digests)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
