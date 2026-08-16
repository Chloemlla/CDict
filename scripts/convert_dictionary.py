#!/usr/bin/env python3
"""Build the offline dictionary SQLite asset from an authorized source export.

Supported inputs are JSON/JSONL exports, base85-wrapped JSON, and Brotli-compressed
JSON (raw or after base85 decoding). The source must be supplied explicitly; this
script never downloads or invents dictionary records.
"""
from __future__ import annotations

import argparse
import base64
import binascii
import json
import re
import shutil
import sqlite3
import subprocess
import sys
from pathlib import Path
from typing import Any, Iterable

SCHEMA = """
PRAGMA foreign_keys = ON;
CREATE TABLE metadata (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL);
CREATE TABLE groups (
  id INTEGER PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  sortOrder INTEGER NOT NULL
);
CREATE TABLE words (
  id INTEGER PRIMARY KEY NOT NULL,
  word TEXT NOT NULL,
  phoneticUk TEXT,
  phoneticUs TEXT,
  translation TEXT,
  definition TEXT,
  mnemonic TEXT,
  emotionColor TEXT,
  register TEXT,
  nuanceDescription TEXT,
  usageWarning TEXT,
  collocations TEXT,
  aiSupplemented TEXT,
  frequencyGroup INTEGER NOT NULL DEFAULT 0,
  frequency INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE derived_terms (wordId INTEGER NOT NULL, term TEXT NOT NULL, PRIMARY KEY(wordId, term), FOREIGN KEY(wordId) REFERENCES words(id));
CREATE TABLE roots (wordId INTEGER NOT NULL, root TEXT NOT NULL, meaning TEXT, PRIMARY KEY(wordId, root), FOREIGN KEY(wordId) REFERENCES words(id));
CREATE TABLE sentences (id INTEGER PRIMARY KEY NOT NULL, english TEXT NOT NULL, chinese TEXT);
CREATE TABLE word_sentence_links (wordId INTEGER NOT NULL, sentenceId INTEGER NOT NULL, PRIMARY KEY(wordId, sentenceId), FOREIGN KEY(wordId) REFERENCES words(id), FOREIGN KEY(sentenceId) REFERENCES sentences(id));
CREATE TABLE heatmap_entries (wordId INTEGER NOT NULL, period TEXT NOT NULL, score REAL NOT NULL, PRIMARY KEY(wordId, period), FOREIGN KEY(wordId) REFERENCES words(id));
CREATE VIRTUAL TABLE word_search USING fts4(content=`words`, translation, word, definition);
CREATE INDEX idx_words_group_frequency ON words(frequencyGroup, frequency, word);
CREATE INDEX idx_words_translation ON words(translation);
CREATE INDEX idx_links_word ON word_sentence_links(wordId);
"""

ALIASES = {
    "word": ("word", "term", "headword", "name"),
    "phoneticUk": ("phoneticUk", "uk", "ukPhonetic", "phonetic_uk", "phonetic"),
    "phoneticUs": ("phoneticUs", "us", "usPhonetic", "phonetic_us"),
    "translation": ("translation", "chinese", "meaningZh", "cn", "zh"),
    "definition": ("definition", "definitions", "englishDefinition", "explanation"),
    "mnemonic": ("mnemonic", "memory", "remember"),
    "emotionColor": ("emotionColor", "emotion_color"),
    "register": ("register", "registerStyle", "style"),
    "nuanceDescription": ("nuanceDescription", "nuance", "nuance_description"),
    "usageWarning": ("usageWarning", "usage_warning"),
    "collocations": ("collocations", "common_collocations", "collocation"),
    "frequencyGroup": ("frequencyGroup", "group", "frequency_group", "level"),
    "frequency": ("frequency", "count", "rank", "order"),
}


def get(record: dict[str, Any], field: str, default: Any = None) -> Any:
    for key in ALIASES[field]:
        if key in record and record[key] is not None:
            return record[key]
    return default


def decode_source(path: Path) -> Any:
    raw = path.read_bytes()
    candidates = [raw]
    try:
        candidates.append(base64.b85decode(re.sub(rb"\s+", b"", raw)))
    except (ValueError, binascii.Error):
        pass
    for candidate in candidates:
        for decoded in (candidate, _brotli(candidate)):
            if decoded is None:
                continue
            try:
                return _parse_json(decoded)
            except (UnicodeDecodeError, json.JSONDecodeError):
                pass
    raise ValueError(f"could not decode {path}; expected JSON, JSONL, base85 JSON, or Brotli JSON")


def _brotli(data: bytes) -> bytes | None:
    if not data.startswith((b"\x8b", b"\xce")) and len(data) < 2:
        return None
    try:
        import brotli  # type: ignore
        return brotli.decompress(data)
    except Exception:
        brotli_cli = shutil.which("brotli")
        if brotli_cli:
            result = subprocess.run([brotli_cli, "--decompress", "--stdout"], input=data, capture_output=True)
            if result.returncode == 0:
                return result.stdout
    return None


def _parse_json(data: bytes) -> Any:
    text = data.decode("utf-8-sig")
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        records = [json.loads(line) for line in text.splitlines() if line.strip()]
        if not records:
            raise
        return records


def records_from_payload(payload: Any) -> list[dict[str, Any]]:
    if isinstance(payload, dict) and isinstance(payload.get("g"), list) and isinstance(payload.get("p"), dict):
        return compact_site_records(payload)
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    if not isinstance(payload, dict):
        raise ValueError("source root must be an object or array")
    for key in ("words", "data", "entries", "items", "dictionary"):
        value = payload.get(key)
        if isinstance(value, list):
            return [item for item in value if isinstance(item, dict)]
    if get(payload, "word") is not None:
        return [payload]
    raise ValueError("source object has no words/data/entries/items/dictionary array")


def compact_site_records(payload: dict[str, Any]) -> list[dict[str, Any]]:
    """Expand the site's compact g/p/d payload to the converter's neutral shape."""
    tables = payload["p"]
    derived_table = tables.get("v", [])
    root_table = tables.get("r", [])
    sentence_table = tables.get("s", [])
    group_names = [(index, str(group.get("n", f"Group {index}")), index) for index, group in enumerate(payload["g"], start=1)]
    records: list[dict[str, Any]] = []
    for group_index, group in enumerate(payload["g"], start=1):
        for rank, source in enumerate(group.get("ws", []), start=1):
            if not isinstance(source, dict) or not source.get("w"):
                continue
            record: dict[str, Any] = {
                "id": len(records) + 1,
                "_groupNames": group_names,
                "word": source.get("w"),
                "phoneticUk": source.get("p"),
                "translation": source.get("t"),
                "definition": source.get("ed"),
                "mnemonic": source.get("ax"),
                "frequencyGroup": group_index,
                "frequency": source.get("oc", rank),
                "derivedTerms": [],
                "roots": [],
                "sentences": [],
                "heatmap": {},
            }
            for index in source.get("dv", []) or []:
                if isinstance(index, int) and 0 <= index < len(derived_table):
                    try:
                        derived = json.loads(derived_table[index]) if isinstance(derived_table[index], str) else derived_table[index]
                    except json.JSONDecodeError:
                        continue
                    if isinstance(derived, dict) and derived.get("word"):
                        record["derivedTerms"].append(derived["word"])
            root_index = source.get("rt")
            if isinstance(root_index, int) and 0 <= root_index < len(root_table):
                try:
                    root = json.loads(root_table[root_index]) if isinstance(root_table[root_index], str) else root_table[root_index]
                except json.JSONDecodeError:
                    root = None
                if isinstance(root, dict) and root.get("root"):
                    record["roots"].append({"root": root["root"], "meaning": root.get("root_meaning") or root.get("mnemonic")})
            for values in (source.get("dt") or {}).values():
                for item in values:
                    if not item or not isinstance(item[0], int) or not 0 <= item[0] < len(sentence_table):
                        continue
                    record["sentences"].append({"english": sentence_table[item[0]]})
            for period, exams in (source.get("cl") or {}).items():
                if isinstance(exams, dict):
                    record["heatmap"][str(period)] = sum(as_int(value) for value in exams.values())
            records.append(record)
    return records


def text(value: Any) -> str | None:
    if value is None:
        return None
    if isinstance(value, list):
        return "; ".join(str(item) for item in value)
    return str(value).strip() or None


def normalize_delimited_text(value: Any, separators: str) -> str | None:
    normalized = text(value)
    if normalized is None:
        return None
    parts = [part.strip() for part in re.split(separators, normalized) if part.strip()]
    unique_parts: list[str] = []
    seen: set[str] = set()
    for part in parts:
        if part in seen:
            continue
        seen.add(part)
        unique_parts.append(part)
    return "；".join(unique_parts) or None


def as_int(value: Any, default: int = 0) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="authorized source export; no network fetch is performed")
    parser.add_argument("output", type=Path, help="output SQLite asset, normally app/src/main/assets/dict.db")
    parser.add_argument("--expected-groups", type=int, default=None)
    parser.add_argument("--source-sha256", default=None, help="record the source snapshot hash in metadata")
    parser.add_argument("--expected-word-count", type=int, default=None, help="fail unless this exact number of words is produced")
    parser.add_argument("--min-word-count", type=int, default=1)
    args = parser.parse_args()
    if not args.source.is_file():
        parser.error(f"source file does not exist: {args.source}; download/export it explicitly first")

    try:
        records = records_from_payload(decode_source(args.source))
    except ValueError as error:
        parser.error(str(error))
    if len(records) < args.min_word_count:
        parser.error(f"only {len(records)} words found; expected at least {args.min_word_count}")
    if args.expected_word_count is not None and len(records) != args.expected_word_count:
        parser.error(f"word count {len(records)} does not match expected {args.expected_word_count}")
    if args.expected_groups is not None:
        groups = {as_int(get(record, "frequencyGroup")) for record in records}
        if len(groups) != args.expected_groups:
            parser.error(f"frequency group count {len(groups)} does not match expected {args.expected_groups}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.output.exists():
        args.output.unlink()
    db = sqlite3.connect(args.output)
    try:
        # PRD §4.2 physical-page tuning: a larger page (matching modern flash block size) and
        # full auto-vacuum shrink the asset, then VACUUM compacts fragmented pages. Both pragmas
        # must be set before tables are created to take effect.
        db.execute("PRAGMA page_size = 4096;")
        db.execute("PRAGMA auto_vacuum = FULL;")
        db.executescript(SCHEMA)
        metadata = {
            "source": "https://isdc.pages.dev/",
            "sourceSha256": args.source_sha256 or "unspecified",
            "wordCount": str(len(records)),
            "groupCount": str(len({as_int(get(record, "frequencyGroup")) for record in records})),
        }
        db.executemany("INSERT INTO metadata(key, value) VALUES (?, ?)", metadata.items())
        if records and records[0].get("_groupNames"):
            db.executemany("INSERT INTO groups(id, name, sortOrder) VALUES (?, ?, ?)", records[0]["_groupNames"])
        else:
            generic_groups = sorted({as_int(get(record, "frequencyGroup")) for record in records})
            db.executemany("INSERT INTO groups(id, name, sortOrder) VALUES (?, ?, ?)", [(group, f"Group {group}", group) for group in generic_groups])
        seen: set[str] = set()
        sentences: dict[str, int] = {}
        for index, record in enumerate(records, start=1):
            word = text(get(record, "word"))
            if not word or word.casefold() in seen:
                continue
            seen.add(word.casefold())
            word_id = as_int(record.get("id"), index)
            translation = normalize_delimited_text(get(record, "translation"), r"[；\r\n]+")
            definition = normalize_delimited_text(get(record, "definition"), r"；")
            db.execute("INSERT INTO words VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", (word_id, word, text(get(record, "phoneticUk")), text(get(record, "phoneticUs")), translation, definition, text(get(record, "mnemonic")), text(get(record, "emotionColor")), text(get(record, "register")), text(get(record, "nuanceDescription")), text(get(record, "usageWarning")), normalize_delimited_text(get(record, "collocations"), r"[；;、,]+"), as_int(get(record, "frequencyGroup")), as_int(get(record, "frequency"))))
            db.execute("INSERT INTO word_search(rowid, word, translation, definition) VALUES (?, ?, ?, ?)", (word_id, word, translation, definition))
            for term in record.get("derivedTerms", record.get("derivatives", [])) or []:
                value = text(term.get("term") if isinstance(term, dict) else term)
                if value:
                    db.execute("INSERT OR IGNORE INTO derived_terms VALUES (?, ?)", (word_id, value))
            roots = record.get("roots", record.get("affixes", [])) or []
            for root in roots:
                root_value = text(root.get("root") if isinstance(root, dict) else root)
                meaning = text(root.get("meaning") if isinstance(root, dict) else None)
                if root_value:
                    db.execute("INSERT OR IGNORE INTO roots VALUES (?, ?, ?)", (word_id, root_value, meaning))
            for sentence in record.get("sentences", record.get("examSentences", [])) or []:
                english = text(sentence.get("english") if isinstance(sentence, dict) else sentence)
                chinese = text(sentence.get("chinese") if isinstance(sentence, dict) else None)
                if not english:
                    continue
                sentence_id = sentences.setdefault(english, len(sentences) + 1)
                db.execute("INSERT OR IGNORE INTO sentences VALUES (?, ?, ?)", (sentence_id, english, chinese))
                db.execute("INSERT OR IGNORE INTO word_sentence_links VALUES (?, ?)", (word_id, sentence_id))
            for period, score in (record.get("heatmap", {}) or {}).items() if isinstance(record.get("heatmap", {}), dict) else []:
                try:
                    db.execute("INSERT OR REPLACE INTO heatmap_entries VALUES (?, ?, ?)", (word_id, str(period), float(score)))
                except (TypeError, ValueError):
                    raise ValueError(f"invalid heatmap score for {word}")
        db.commit()
        # Compact freed space so the shipped asset reflects the smaller page/free-list layout.
        db.execute("VACUUM;")
        counts = {table: db.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0] for table in ("words", "derived_terms", "roots", "sentences", "word_sentence_links", "heatmap_entries")}
        if counts["words"] != len(seen):
            raise ValueError(f"word count mismatch after normalization: {counts['words']} != {len(seen)}")
        print(json.dumps({"output": str(args.output), "counts": counts}, ensure_ascii=False, sort_keys=True))
    finally:
        db.close()
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, sqlite3.Error, ValueError) as error:
        print(f"convert-dictionary: error: {error}", file=sys.stderr)
        raise SystemExit(1)
