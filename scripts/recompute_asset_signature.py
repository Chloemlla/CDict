#!/usr/bin/env python3
"""Recompute `metadata.assetSignature` of a dictionary asset in place.

The signature is a sha256 over every content column of `words`, ordered by id; the app uses it
to decide whether the bundled dictionary changed. The column list must stay identical wherever
it is computed, so workflows call this script instead of inlining another copy.
"""

from __future__ import annotations

import argparse
import hashlib
import sqlite3
from pathlib import Path


SIGNATURE_COLUMNS = (
    "id",
    "word",
    "phoneticUk",
    "phoneticUs",
    "translation",
    "definition",
    "mnemonic",
    "frequencyGroup",
    "frequency",
    "emotionColor",
    "register",
    "nuanceDescription",
    "usageWarning",
    "collocations",
    "aiSupplemented",
    "headwordSummary",
    "curriculumTags",
)


def compute_asset_signature(db: sqlite3.Connection) -> str:
    digest = hashlib.sha256()
    query = "SELECT " + ", ".join(SIGNATURE_COLUMNS) + " FROM words ORDER BY id"
    for row in db.execute(query):
        digest.update("|".join(str(value or "") for value in row).encode("utf-8"))
        digest.update(b"\n")
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("database", type=Path, help="dictionary asset, updated in place")
    args = parser.parse_args()

    db = sqlite3.connect(args.database)
    try:
        signature = compute_asset_signature(db)
        cursor = db.execute(
            "UPDATE metadata SET value = ? WHERE key = 'assetSignature'", (signature,)
        )
        if cursor.rowcount != 1:
            raise SystemExit(f"{args.database}: no assetSignature row in metadata")
        db.commit()
    finally:
        db.close()

    print("assetSignature=" + signature)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
