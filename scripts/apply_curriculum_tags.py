#!/usr/bin/env python3
"""Apply a curriculum word-list label to a merged dictionary asset.

Downloads nothing itself: it takes a plain-text curriculum word list (e.g. the 高中
3500 词 list) and a dictionary database, and tags every headword that also exists in
the `words` table with the given label in the `curriculumTags` column. The tag is
appended idempotently (never duplicated) and existing labels are preserved, so the
workflow can be re-run safely. Finally the `assetSignature` is recomputed over the
same content columns used by merge_distribution.py so the published signature stays
consistent with the tagged asset (the app compares dict.signature against this row).

The curriculum list is not a strict "one headword per line" file: it interleaves
headwords with bracketed IPA lines and Chinese gloss lines, and a headword may have
several phonetic lines (even with blank lines between them). Instead of assuming a
fixed line stride, a line is treated as a headword only when it is neither a
bracketed/slashed phonetic line nor a Chinese gloss / POS-prefixed gloss line.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sqlite3
import sys
import unicodedata
from pathlib import Path

CJK = re.compile(r"[一-鿿]")
PHONETIC = re.compile(r"^[\[/].*[\]/]$")
POS_HEAD = re.compile(
    r"^(?:[\[\（(][^\]）)]{0,10}[\]）)]\s*)?"
    r"(?:n|v|vt|vi|adj|adv|prep|conj|pron|num|int|art|aux|abbr|pl|a|ad)\.?(?=\s|$)"
)
# OCR drops closing parens in this source and mangles modal verbs ("could modal ",
# "can (could) can't = can not modal "); strip trailing gloss tokens and cut each
# alias at its first open paren so the headword survives.
ALIAS_SEP = re.compile(r"[、/=]")
TRAILING_POS = re.compile(
    r"\s+(?:n|v|vt|vi|adj|adv|prep|conj|pron|num|int|art|aux|abbr|pl|a|ad|modal)\.?$"
)
STRIP_TRAILING = " \t*·•●‥…"
DOUBLE_HYPHEN = re.compile(r"-{2,}")

SIGNATURE_COLUMNS = (
    "id, word, phoneticUk, phoneticUs, translation, definition, mnemonic, "
    "frequencyGroup, frequency, emotionColor, register, nuanceDescription, "
    "usageWarning, collocations, aiSupplemented, headwordSummary, curriculumTags"
)


def fold_key(text: str) -> str:
    """NFKC-fold, strip combining marks (café -> cafe), then casefold."""
    text = unicodedata.normalize("NFKC", text)
    text = "".join(ch for ch in unicodedata.normalize("NFD", text) if not unicodedata.combining(ch))
    return text.casefold()


def normalize_headword(raw: str) -> set[str]:
    """Return the set of normalized lookup keys for one headword line."""
    text = fold_key(raw)
    text = TRAILING_POS.sub("", text)
    keys: set[str] = set()
    for part in ALIAS_SEP.split(text):
        candidate = part.strip(" \t[]()（）《》【】")
        cut = re.search(r"[（(]", candidate)
        if cut:
            candidate = candidate[: cut.start()]
        candidate = candidate.strip(STRIP_TRAILING)
        candidate = DOUBLE_HYPHEN.sub("-", candidate)
        if candidate and re.search(r"[a-z0-9]", candidate):
            keys.add(candidate)
    return keys


def parse_headwords(path: Path) -> set[str]:
    """Extract normalized headword keys from a curriculum list."""
    keys: set[str] = set()
    with open(path, encoding="utf-8", errors="replace") as handle:
        for raw in handle:
            line = unicodedata.normalize("NFKC", raw).strip()
            if not line:
                continue
            if PHONETIC.match(line):
                continue
            if CJK.search(line) or POS_HEAD.match(line):
                continue
            keys.update(normalize_headword(line))
    return keys


def add_tag(existing: str | None, tag: str) -> tuple[str, bool]:
    """Return (new_curriculumTags, changed); preserves labels, never duplicates."""
    labels = [label.strip() for label in re.split(r"[,，;；]", existing or "") if label.strip()]
    if tag in labels:
        return (existing or ""), False
    labels.append(tag)
    return ",".join(labels), True


def recompute_signature(db: sqlite3.Connection) -> str:
    h = hashlib.sha256()
    for row in db.execute(f"SELECT {SIGNATURE_COLUMNS} FROM words ORDER BY id"):
        h.update("|".join(str(value or "") for value in row).encode("utf-8"))
        h.update(b"\n")
    return h.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("database", type=Path, help="merged dictionary asset (modified in place)")
    parser.add_argument("word_list", type=Path, help="plain-text curriculum word list")
    parser.add_argument("--tag", required=True, help="curriculum label to apply, e.g. 高中 3500 词")
    parser.add_argument("--source", help="optional source URL recorded in metadata")
    args = parser.parse_args()

    if not args.database.is_file() or not args.word_list.is_file():
        parser.error("database or word list missing")

    parsed = parse_headwords(args.word_list)
    if not parsed:
        parser.error(f"no headwords parsed from {args.word_list}")

    db = sqlite3.connect(args.database)
    try:
        words_columns = {row[1] for row in db.execute("PRAGMA table_info(words)")}
        # Mirror merge_distribution.py: the signature covers these columns even when
        # this script runs against a base asset that has not been merged yet.
        if "aiSupplemented" not in words_columns:
            db.execute("ALTER TABLE words ADD COLUMN aiSupplemented TEXT")
        if "headwordSummary" not in words_columns:
            db.execute("ALTER TABLE words ADD COLUMN headwordSummary TEXT")
        if "curriculumTags" not in words_columns:
            db.execute("ALTER TABLE words ADD COLUMN curriculumTags TEXT")

        lookup: dict[str, int] = {}
        collisions: list[tuple[str, str, str]] = []
        for word_id, word in db.execute("SELECT id, word FROM words"):
            key = fold_key(word)
            previous = lookup.get(key)
            if previous is not None and previous != word_id:
                collisions.append((key, str(previous), str(word_id)))
            else:
                lookup[key] = word_id

        updates: list[tuple[str, int]] = []
        matched = 0
        for key in sorted(parsed):
            word_id = lookup.get(key)
            if word_id is None:
                continue
            matched += 1
            current = db.execute(
                "SELECT curriculumTags FROM words WHERE id = ?", (word_id,)
            ).fetchone()[0]
            new_tags, changed = add_tag(current, args.tag)
            if changed:
                updates.append((new_tags, word_id))

        db.executemany("UPDATE words SET curriculumTags = ? WHERE id = ?", updates)

        signature = recompute_signature(db)
        db.execute(
            "INSERT OR REPLACE INTO metadata(key, value) VALUES ('assetSignature', ?)",
            (signature,),
        )
        db.execute(
            "INSERT OR REPLACE INTO metadata(key, value) VALUES ('curriculumTag', ?)",
            (args.tag,),
        )
        db.execute(
            "INSERT OR REPLACE INTO metadata(key, value) VALUES ('curriculumMatched', ?)",
            (str(matched),),
        )
        if args.source:
            db.execute(
                "INSERT OR REPLACE INTO metadata(key, value) VALUES ('curriculumSource', ?)",
                (args.source,),
            )
        db.commit()
        db.execute("VACUUM;")
    finally:
        db.close()

    stats = {
        "output": str(args.database),
        "curriculumTag": args.tag,
        "headwordsParsed": len(parsed),
        "headwordsMatched": matched,
        "wordsTagged": len(updates),
        "signature": signature,
    }
    print(json.dumps(stats, ensure_ascii=False, sort_keys=True))

    if matched == 0:
        print(f"apply-curriculum-tags: error: zero headwords matched {args.database}", file=sys.stderr)
        return 1
    if collisions:
        print(
            f"apply-curriculum-tags: warning: {len(collisions)} duplicate normalized words "
            f"(first {collisions[:3]}); first id won the tag",
            file=sys.stderr,
        )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, sqlite3.Error, RuntimeError) as error:
        print(f"apply-curriculum-tags: error: {error}", file=sys.stderr)
        raise SystemExit(1)
