#!/usr/bin/env python3
"""Decode the authorized isdc.pages.dev embedded payload into converter JSON.

The website embeds sixteen newline-separated base85 segments. Each segment is
Brotli-compressed independently and uses the site's custom printable alphabet.
This script keeps the source transformation reproducible without placing the
copyrighted source payload in the repository.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import shutil
import subprocess
from pathlib import Path
from urllib.request import Request, urlopen

SITE = "https://isdc.pages.dev/"
ALPHABET = "".join(chr(code) for code in range(33, 127) if code not in (34, 39, 60))[:85]
INDEX = {char: index for index, char in enumerate(ALPHABET)}


def decode_segment(segment: str) -> bytes:
    """Decode and decompress one segment. Brotli may return output with a non-zero
    status when a segment has an intentional concatenated stream boundary."""
    packed = bytearray()
    for offset in range(0, len(segment), 5):
        group = segment[offset : offset + 5]
        count = len(group)
        value = 0
        for char in group:
            value = value * 85 + INDEX[char]
        for _ in range(5 - count):
            value = value * 85 + 84
        packed.extend((value >> (8 * (3 - index))) & 0xFF for index in range(count * 4 // 5))
    try:
        import brotli
    except ImportError:
        brotli = None
    if brotli is not None:
        try:
            return brotli.decompress(bytes(packed))
        except brotli.error as error:
            python_error = str(error)
    else:
        python_error = "Python Brotli module is unavailable"

    executable = shutil.which("brotli")
    if executable is None:
        raise RuntimeError(f"Brotli decompression failed: {python_error}; install brotli or provide the brotli CLI")
    result = subprocess.run([executable, "--decompress", "--stdout"], input=packed, capture_output=True, check=False)
    if result.returncode and not result.stdout:
        raise RuntimeError(result.stderr.decode("utf-8", "replace").strip() or "Brotli decompression failed")
    return result.stdout


def fetch_html(url: str) -> str:
    request = Request(url, headers={"User-Agent": "Mozilla/5.0 CDict authorized data exporter"})
    with urlopen(request, timeout=60) as response:
        return response.read().decode("utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default=SITE)
    parser.add_argument("--html-file", type=Path, default=None, help="use a saved HTML snapshot instead of downloading")
    parser.add_argument("--expected-html-sha256", default=None)
    parser.add_argument("--expected-json-sha256", default=None)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    html = args.html_file.read_text(encoding="utf-8") if args.html_file else fetch_html(args.url)
    html_hash = hashlib.sha256(html.encode("utf-8")).hexdigest()
    if args.expected_html_sha256 and html_hash != args.expected_html_sha256:
        raise RuntimeError(f"HTML SHA-256 mismatch: {html_hash} != {args.expected_html_sha256}")
    marker = '<script type="application/json" id="asp-data">'
    start = html.find(marker)
    if start < 0:
        raise RuntimeError("source page did not contain the asp-data payload")
    start += len(marker)
    end = html.find("</script>", start)
    if end < 0:
        raise RuntimeError("source page contained an unterminated asp-data payload")
    payload = html[start:end].strip()
    decoded = b"".join(decode_segment(segment) for segment in payload.splitlines())
    source_hash = hashlib.sha256(decoded).hexdigest()
    if args.expected_json_sha256 and source_hash != args.expected_json_sha256:
        raise RuntimeError(f"JSON SHA-256 mismatch: {source_hash} != {args.expected_json_sha256}")
    source = json.loads(decoded)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(source, ensure_ascii=False), encoding="utf-8")
    groups = source.get("g", [])
    words = sum(len(group.get("ws", [])) for group in groups)
    print(json.dumps({"source": args.url, "output": str(args.output), "htmlSha256": html_hash, "jsonSha256": source_hash, "groups": len(groups), "words": words}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
