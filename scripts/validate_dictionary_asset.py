#!/usr/bin/env python3
"""Static validation for a generated dictionary database."""
from __future__ import annotations
import argparse
import sqlite3
from pathlib import Path

TABLES = {
    "metadata",
    "groups",
    "words",
    "derived_terms",
    "roots",
    "sentences",
    "word_sentence_links",
    "heatmap_entries",
    "word_relations",
    "word_forms",
    "etymologies",
    "study_notes",
    "word_search",
}

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("database", type=Path)
    parser.add_argument("--expected-word-count", type=int, default=None)
    parser.add_argument("--expected-groups", type=int, default=None)
    args = parser.parse_args()
    if not args.database.is_file() or args.database.stat().st_size == 0:
        parser.error(f"database missing or empty: {args.database}")
    db = sqlite3.connect(args.database)
    try:
        tables = {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type IN ('table', 'view')")}
        missing = TABLES - tables
        if missing:
            parser.error(f"database missing required tables: {sorted(missing)}")
        words = db.execute("SELECT COUNT(*) FROM words").fetchone()[0]
        groups = db.execute("SELECT COUNT(*) FROM groups").fetchone()[0]
        expected_words = args.expected_word_count
        expected_groups = args.expected_groups
        if expected_words is None:
            meta = db.execute("SELECT value FROM metadata WHERE key = 'wordCount'").fetchone()
            expected_words = int(meta[0]) if meta else words
        if expected_groups is None:
            meta = db.execute("SELECT value FROM metadata WHERE key = 'groupCount'").fetchone()
            expected_groups = int(meta[0]) if meta else groups
        if words != expected_words or groups != expected_groups:
            parser.error(f"unexpected counts: words={words} (expected {expected_words}), groups={groups} (expected {expected_groups})")
        words_columns = {row[1] for row in db.execute("PRAGMA table_info(words)")}
        if "aiSupplemented" not in words_columns:
            parser.error("words table missing required aiSupplemented column")
        if "headwordSummary" not in words_columns:
            parser.error("words table missing required headwordSummary column")
        if "curriculumTags" not in words_columns:
            parser.error("words table missing required curriculumTags column")
        fts = db.execute("SELECT COUNT(*) FROM word_search").fetchone()[0]
        if fts != words:
            parser.error(f"FTS row count {fts} does not match words {words}")
        violations = db.execute("PRAGMA foreign_key_check").fetchall()
        if violations:
            parser.error(f"foreign key violations: {violations[:3]}")
    finally:
        db.close()
    print(f"validated {args.database}: words={expected_words}, groups={expected_groups}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
