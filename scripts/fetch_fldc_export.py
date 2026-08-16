#!/usr/bin/env python3
"""Decode the fldc.pages.dev binary payload into converter JSON.

The site loads two container files (data.1.bin, data.2.bin). Each container
is a sequence of gzip-compressed chunks preceded by a varint header. After
decompression the two halves are concatenated and parsed as a custom binary
format with a shared-prefix string pool, then 7 frequency groups of words.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import struct
import sys
from pathlib import Path
from typing import Any
from urllib.request import Request, urlopen

SITE = "https://fldc.pages.dev/"
DATA_URLS = ["https://fldc.pages.dev/data.1.bin", "https://fldc.pages.dev/data.2.bin"]


def _read_varint(data: bytes, offset: int) -> tuple[int, int]:
    """Decode an unsigned LEB128 varint; returns (value, new_offset)."""
    x = 0
    shift = 0
    while True:
        b = data[offset]
        offset += 1
        x |= (b & 0x7F) << shift
        shift += 7
        if not (b & 0x80):
            break
    return x, offset


def _read_str(data: bytes, offset: int, pool: list[str], prev: bytes) -> tuple[str, int, bytes]:
    """Read a string entry from the shared-prefix pool, returning (string, new_offset, raw_bytes)."""
    sh, offset = _read_varint(data, offset)
    sl, offset = _read_varint(data, offset)
    raw = prev[:sh] + data[offset : offset + sl]
    offset += sl
    text = raw.decode("utf-8")
    return text, offset, raw


def decode_container(path: Path) -> bytes:
    """Decode a container file: varint header then gzip-compressed chunks."""
    raw = path.read_bytes()
    offset = 0
    count, offset = _read_varint(raw, offset)
    lengths: list[int] = []
    for _ in range(count):
        length, offset = _read_varint(raw, offset)
        lengths.append(length)
    chunks: list[bytes] = []
    for length in lengths:
        compressed = raw[offset : offset + length]
        offset += length
        try:
            chunks.append(gzip.decompress(compressed))
        except Exception as exc:
            raise RuntimeError(f"gzip decompression failed at offset {offset - length}: {exc}") from exc
    return b"".join(chunks)


def parse_binary(data: bytes) -> dict[str, Any]:
    """Parse the custom binary format into the converter's {g: [...], p: {...}} shape."""
    offset = 4  # skip 4-byte header
    _total_words, offset = _read_varint(data, offset)
    pool_count, offset = _read_varint(data, offset)

    # Decode string pool (shared-prefix compression)
    pool: list[str] = [""]
    prev = b""
    for _ in range(pool_count):
        text, offset, prev = _read_str(data, offset, pool, prev)
        pool.append(text)

    ri = lambda: _read_varint(data, offset)[0]
    # Helper that reads a varint and updates offset
    def read_int() -> int:
        nonlocal offset
        v, offset = _read_varint(data, offset)
        return v

    def read_str() -> str:
        nonlocal offset
        idx, offset = _read_varint(data, offset)
        return pool[idx]

    # 7 frequency groups
    groups: list[dict[str, Any]] = []
    for _ in range(7):
        name = read_str()
        cnt = read_int()
        words: list[dict[str, Any]] = []
        for _ in range(cnt):
            w: dict[str, Any] = {
                "w": read_str(),
                "t": read_str(),
                "p": read_str(),
                "e": read_str(),
                "ec": read_str(),
                "ay": read_str(),
                "am": read_str(),
            }
            # Derivatives
            dvn = read_int()
            dv: list[dict[str, str]] = []
            for _ in range(dvn):
                dv.append({
                    "word": read_str(),
                    "definition": read_str(),
                    "pos": read_str(),
                    "form": read_str(),
                })
            w["dv"] = dv
            # English definition & mnemonic
            w["ed"] = read_str()
            w["ax"] = read_str()
            # Root info
            rtr = read_str()
            rtp = read_str()
            rts = read_str()
            rtm = read_str()
            if rtr or rtp or rts or rtm:
                w["rt"] = {"root": rtr, "prefix": rtp, "suffix": rts, "mnemonic": rtm}
            # Occurrence count
            w["oc"] = read_int()
            # Heatmap
            cln = read_int()
            if cln:
                cl: dict[str, dict[str, int]] = {}
                for _ in range(cln):
                    bk = read_str()
                    tn = read_int()
                    ts: dict[str, int] = {}
                    for _ in range(tn):
                        tk = read_str()
                        ts[tk] = read_int()
                    cl[bk] = ts
                w["cl"] = cl
            # Exam sentences
            dtn = read_int()
            if dtn:
                dt: list[list[str]] = []
                for _ in range(dtn):
                    dt.append([read_str(), read_str(), read_str(), read_str(), read_str()])
                w["dt"] = dt
            words.append(w)
        groups.append({"n": name, "ws": words})

    return {"g": groups, "p": {"v": [], "r": [], "s": []}}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=None, help="directory with pre-downloaded data.1.bin and data.2.bin")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    if args.data_dir:
        bin1 = args.data_dir / "data.1.bin"
        bin2 = args.data_dir / "data.2.bin"
        if not bin1.is_file() or not bin2.is_file():
            parser.error(f"data files not found in {args.data_dir}")
    else:
        # Download
        for url in DATA_URLS:
            name = url.rstrip("/").rsplit("/", 1)[-1]
            dest = Path(name)
            print(f"Downloading {url}...", file=sys.stderr)
            req = Request(url, headers={"User-Agent": "Mozilla/5.0 CDict authorized data exporter"})
            with urlopen(req, timeout=300) as resp:
                dest.write_bytes(resp.read())
            print(f"  -> {dest} ({dest.stat().st_size} bytes)", file=sys.stderr)
        bin1 = Path("data.1.bin")
        bin2 = Path("data.2.bin")
        if not bin1.is_file() or not bin2.is_file():
            parser.error("download failed")

    # Decode containers
    part1 = decode_container(bin1)
    part2 = decode_container(bin2)
    decompressed = part1 + part2
    sha256 = hashlib.sha256(decompressed).hexdigest()
    print(f"Decompressed payload: {len(decompressed)} bytes, SHA-256: {sha256}", file=sys.stderr)

    # Parse binary
    payload = parse_binary(decompressed)
    groups = payload["g"]
    words = sum(len(group.get("ws", [])) for group in groups)

    # Write output
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({
        "source": SITE,
        "output": str(args.output),
        "sha256": sha256,
        "groups": len(groups),
        "words": words,
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())