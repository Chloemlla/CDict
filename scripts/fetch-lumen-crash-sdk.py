#!/usr/bin/env python3
"""Materialize the latest lumen-crash Maven coordinates from Project-Lumen release assets.

Resolves the newest `lumen-crash-v*` auto-release and downloads the `lumen-crash` and
`lumen-crash-core` AAR/POM/module pairs into `local-maven/` so Gradle can resolve them
without a committed version pin. Writes the resolved version to `lumen-crash.resolved.version`.

Version selection:
  - "latest" (default): newest main auto-release under tag lumen-crash-v*
  - explicit version: e.g. 0.1.0-de4c16b1
  - env override: LUMEN_CRASH_SDK_VERSION
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
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
# main auto-release: 0.1.0-8e73f18d
MAIN_AUTO_VERSION_RE = re.compile(r"^\d+\.\d+\.\d+-[0-9a-f]{7,40}$", re.IGNORECASE)

# Packages published to the release that dependents must materialize together.
PACKAGES = ["lumen-crash", "lumen-crash-core"]


def read_version_policy(path: Path) -> str:
    if not path.is_file():
        return "latest"
    for line in path.read_text(encoding="utf-8").splitlines():
        text = line.strip()
        if text and not text.startswith("#"):
            return text
    return "latest"


def auth_headers(accept: str = "*/*") -> dict[str, str]:
    headers = {
        "Accept": accept,
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


def http_get(url: str, accept: str = "*/*") -> bytes:
    request = urllib.request.Request(url, headers=auth_headers(accept))
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise SystemExit(f"HTTP {error.code} for {url}: {body}") from error


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def version_from_tag(tag_name: str) -> str | None:
    if not tag_name.startswith(TAG_PREFIX):
        return None
    return tag_name[len(TAG_PREFIX):]


def is_main_auto_version(version: str) -> bool:
    return bool(MAIN_AUTO_VERSION_RE.match(version))


def list_lumen_crash_releases(owner: str, repo: str) -> list[dict]:
    """Return lumen-crash releases newest-first via GitHub Releases API."""
    releases: list[dict] = []
    page = 1
    while page <= 5:
        url = (
            f"https://api.github.com/repos/{owner}/{repo}/releases"
            f"?per_page=100&page={page}"
        )
        payload = json.loads(http_get(url, accept="application/vnd.github+json").decode("utf-8"))
        if not payload:
            break
        for item in payload:
            tag = str(item.get("tag_name") or "")
            version = version_from_tag(tag)
            if not version:
                continue
            if item.get("draft"):
                continue
            releases.append(
                {
                    "tag_name": tag,
                    "version": version,
                    "published_at": item.get("published_at") or item.get("created_at") or "",
                }
            )
        if len(payload) < 100:
            break
        page += 1
    # API is usually newest-first; keep stable sort by published_at desc as fallback.
    releases.sort(key=lambda item: item["published_at"], reverse=True)
    return releases


def resolve_version(policy: str, owner: str, repo: str) -> str:
    env_override = (os.environ.get("LUMEN_CRASH_SDK_VERSION") or "").strip()
    if env_override:
        print(f"Using LUMEN_CRASH_SDK_VERSION override: {env_override}")
        return env_override

    policy = (policy or "latest").strip()
    if policy and policy.lower() != "latest":
        print(f"Using explicit version policy: {policy}")
        return policy

    releases = list_lumen_crash_releases(owner, repo)
    if not releases:
        raise SystemExit(f"No lumen-crash releases found in {owner}/{repo}")

    main_auto = [item for item in releases if is_main_auto_version(item["version"])]
    chosen = main_auto[0] if main_auto else releases[0]
    print(
        f"Resolved latest lumen-crash release: {chosen['version']} "
        f"(tag {chosen['tag_name']}, published {chosen['published_at']})"
    )
    return chosen["version"]


def write_resolved_version(path: Path, version: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(version.strip() + "\n", encoding="utf-8")
    print(f"Wrote resolved version to {path}")


def materialize(version: str, owner: str, repo: str, local_maven: Path) -> None:
    tag = f"{TAG_PREFIX}{version}"
    base = f"https://github.com/{owner}/{repo}/releases/download/{tag}"

    for package in PACKAGES:
        required = [f"{package}-{version}.aar", f"{package}-{version}.pom"]
        optional = [f"{package}-{version}.module", f"{package}-{version}-sources.jar"]

        # Keep only the currently resolved version tree so stale coordinates do not linger.
        package_root = local_maven / "com" / "chloemlla" / "lumen" / package
        if package_root.exists():
            shutil.rmtree(package_root)
        target_dir = package_root / version
        target_dir.mkdir(parents=True, exist_ok=True)

        downloaded: dict[str, bytes] = {}
        for name in required:
            data = http_get(f"{base}/{name}")
            downloaded[name] = data
            (target_dir / name).write_bytes(data)

        for name in optional:
            try:
                data = http_get(f"{base}/{name}")
            except SystemExit as error:
                print(f"Optional asset skipped: {name} ({error})")
                continue
            downloaded[name] = data
            (target_dir / name).write_bytes(data)

        if not downloaded[required[0]].startswith(b"PK"):
            raise SystemExit(f"{required[0]} does not look like a ZIP/AAR (missing PK header)")

        print(f"Materialized com.chloemlla.lumen:{package}:{version} into {target_dir}")


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
    parser.add_argument("--version", default="")
    args = parser.parse_args()

    policy = args.version.strip() or read_version_policy(args.version_file)
    version = resolve_version(policy=policy, owner=args.owner, repo=args.repo)

    write_resolved_version(args.resolved_version_file, version)
    materialize(version, args.owner, args.repo, args.local_maven)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
