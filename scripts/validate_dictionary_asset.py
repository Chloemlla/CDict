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
    parser.add_argument("--expected-word-count", type=int, required=True)
    parser.add_argument("--expected-groups", type=int, required=True)
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
        if words != args.expected_word_count or groups != args.expected_groups:
            parser.error(f"unexpected counts: words={words}, groups={groups}")
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
    print(f"validated {args.database}: words={args.expected_word_count}, groups={args.expected_groups}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
