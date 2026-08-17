#!/usr/bin/env python3
"""Build a phrase dictionary SQLite asset from a docx word-list source.

Reads a structured phrase docx (section headers + numbered entries), builds a
standalone SQLite database matching the CDict schema, then optionally merges the
new entries into an existing CDict-dict.db.

The source document format is assumed to be:
    Section title (e.g. "一、形容词/副词短语")
    N. phrase Chinese translation

Each section becomes a `groups` row, each phrase becomes a `words` row with
its Chinese translation, and every word gets a ``curriculumTags`` value of
"高中短语" so the app's curriculum filter can isolate phrase entries.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sqlite3
import subprocess
import sys
from pathlib import Path

ENTRY = re.compile(r"^(\d+)\.\s+(.*)$")
CJK_START = re.compile(r"([一-鿿（(])")
SECTION = re.compile(r"^[一二三四五六七八九十]+、(.+)$")
SIGNATURE_COLUMNS = (
    "id, word, phoneticUk, phoneticUs, translation, definition, mnemonic, "
    "frequencyGroup, frequency, emotionColor, register, nuanceDescription, "
    "usageWarning, collocations, aiSupplemented, headwordSummary, curriculumTags"
)

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
  headwordSummary TEXT,
  aiSupplemented TEXT,
  curriculumTags TEXT,
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


def parse_docx(path: Path) -> list[dict]:
    """Parse a structured phrase docx into a list of section-grouped entries.

    Returns a list of dicts with keys: section, entries (list of (phrase, translation)).
    """
    result = subprocess.run(
        ["pandoc", str(path), "-t", "plain", "--wrap=none"],
        capture_output=True,
    )
    if result.returncode != 0:
        raise RuntimeError(f"pandoc failed: {result.stderr.decode('utf-8', errors='replace')}")
    text = result.stdout.decode("utf-8", errors="replace")
    sections: list[dict] = []
    current_section: dict | None = None
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        m = SECTION.match(line)
        if m:
            current_section = {"section": m.group(1), "entries": []}
            sections.append(current_section)
            continue
        m = ENTRY.match(line)
        if m and current_section is not None:
            text = m.group(2).strip()
            # Split at the first CJK character: English phrase before, Chinese translation after
            split_m = CJK_START.search(text)
            if split_m:
                phrase = text[: split_m.start()].strip()
                translation = text[split_m.start() :].strip()
            else:
                phrase = text
                translation = ""
            if phrase:
                current_section["entries"].append((phrase, translation))

    return sections


def phrase_key(phrase: str) -> str:
    """Normalize a phrase for dedup: lowercase, collapse whitespace."""
    return re.sub(r"\s+", " ", phrase.strip().casefold())


def build_phrase_db(
    sections: list[dict],
    output: Path,
    curriculum_tag: str = "高中短语",
    next_group_id: int = 1,
    next_word_id: int = 1,
) -> dict:
    """Build a standalone phrase SQLite database matching the CDict schema.

    Returns metadata about the built database (word count, group count, etc.).
    """
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        output.unlink()

    db = sqlite3.connect(output)
    try:
        db.execute("PRAGMA page_size = 4096;")
        db.execute("PRAGMA auto_vacuum = FULL;")
        db.executescript(SCHEMA)

        group_id = next_group_id
        word_id = next_word_id
        seen: set[str] = set()
        total_words = 0
        group_count = 0

        for section in sections:
            name = section["section"]
            db.execute(
                "INSERT INTO groups(id, name, sortOrder) VALUES (?, ?, ?)",
                (group_id, name, group_id),
            )
            group_count += 1

            for rank, (phrase, translation) in enumerate(section["entries"], start=1):
                key = phrase_key(phrase)
                if key in seen:
                    continue
                seen.add(key)

                db.execute(
                    "INSERT INTO words VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    (
                        word_id, phrase, None, None, translation, None, None,
                        None, None, None, None, None, None, None, curriculum_tag,
                        group_id, rank,
                    ),
                )
                db.execute(
                    "INSERT INTO word_search(rowid, word, translation, definition) VALUES (?, ?, ?, ?)",
                    (word_id, phrase, translation, None),
                )
                word_id += 1
                total_words += 1

            group_id += 1

        db.execute(
            "INSERT INTO metadata(key, value) VALUES ('source', ?)",
            (str(sections[0]["section"]) if sections else "phrase-docx",),
        )
        db.execute("INSERT INTO metadata(key, value) VALUES ('wordCount', ?)", (str(total_words),))
        db.execute("INSERT INTO metadata(key, value) VALUES ('groupCount', ?)", (str(group_count),))
        db.execute("INSERT INTO metadata(key, value) VALUES ('curriculumTag', ?)", (curriculum_tag,))
        db.commit()

        # Compact and compute signature
        db.execute("VACUUM;")
        signature = _recompute_signature(db)

        db.execute(
            "INSERT OR REPLACE INTO metadata(key, value) VALUES ('assetSignature', ?)",
            (signature,),
        )
        db.commit()

        counts = {
            "words": total_words,
            "groups": group_count,
            "signature": signature,
        }
        return counts
    finally:
        db.close()


def _recompute_signature(db: sqlite3.Connection) -> str:
    h = hashlib.sha256()
    for row in db.execute(f"SELECT {SIGNATURE_COLUMNS} FROM words ORDER BY id"):
        h.update("|".join(str(value or "") for value in row).encode("utf-8"))
        h.update(b"\n")
    return h.hexdigest()


def merge_phrase_db(
    phrase_db_path: Path,
    target_db_path: Path,
    curriculum_tag: str = "高中短语",
) -> dict:
    """Merge phrase database entries into an existing CDict-dict.db.

    New words are appended; existing words with the same normalized text are
    skipped. New groups are inserted after the existing groups. Missing columns
    (aiSupplemented, headwordSummary, curriculumTags) are added to the target
    as needed.
    """
    phrase_db = sqlite3.connect(f"file:{phrase_db_path}?mode=ro", uri=True)
    target = sqlite3.connect(target_db_path)
    try:
        # Get current max IDs
        max_group_id = target.execute("SELECT COALESCE(MAX(id), 0) FROM groups").fetchone()[0]
        max_word_id = target.execute("SELECT COALESCE(MAX(id), 0) FROM words").fetchone()[0]

        # Detect target columns and add missing ones
        target_cols = [row[1] for row in target.execute("PRAGMA table_info(words)")]
        target_col_set = set(target_cols)
        for col in ("aiSupplemented", "headwordSummary", "curriculumTags"):
            if col not in target_col_set:
                target.execute(f"ALTER TABLE words ADD COLUMN {col} TEXT")
                target_cols.append(col)

        # Build INSERT dynamically
        target_placeholders = ",".join("?" for _ in target_cols)
        insert_sql = f"INSERT INTO words ({','.join(target_cols)}) VALUES ({target_placeholders})"

        # Build lookup of existing words (casefolded text -> id)
        existing: dict[str, int] = {}
        for wid, wtext in target.execute("SELECT id, word FROM words"):
            existing[phrase_key(wtext)] = wid

        # Read phrase groups and words
        phrase_groups = list(phrase_db.execute("SELECT id, name, sortOrder FROM groups ORDER BY id"))
        phrase_words = list(phrase_db.execute(
            "SELECT id, word, translation, frequencyGroup, frequency FROM words ORDER BY id"
        ))

        # Map column name -> index in target_cols for value assignment
        col_index = {name: i for i, name in enumerate(target_cols)}

        # Insert new groups
        group_id_map: dict[int, int] = {}
        for pg_id, name, sort_order in phrase_groups:
            max_group_id += 1
            group_id_map[pg_id] = max_group_id
            target.execute(
                "INSERT INTO groups(id, name, sortOrder) VALUES (?, ?, ?)",
                (max_group_id, name, max_group_id),
            )

        # Insert new words
        new_words = 0
        skipped = 0
        for pw_id, word, translation, freq_group, frequency in phrase_words:
            key = phrase_key(word)
            if key in existing:
                skipped += 1
                continue
            max_word_id += 1
            new_group = group_id_map.get(freq_group, freq_group)

            # Build values row matching target columns
            values = [None] * len(target_cols)
            values[col_index["id"]] = max_word_id
            values[col_index["word"]] = word
            values[col_index["translation"]] = translation
            values[col_index["frequencyGroup"]] = new_group
            values[col_index["frequency"]] = frequency
            if "curriculumTags" in col_index:
                values[col_index["curriculumTags"]] = curriculum_tag

            target.execute(insert_sql, values)
            target.execute(
                "INSERT INTO word_search(rowid, word, translation, definition) VALUES (?, ?, ?, ?)",
                (max_word_id, word, translation, None),
            )
            existing[key] = max_word_id
            new_words += 1

        # Update metadata
        word_count = target.execute("SELECT COUNT(*) FROM words").fetchone()[0]
        group_count = target.execute("SELECT COUNT(*) FROM groups").fetchone()[0]
        target.execute(
            "INSERT OR REPLACE INTO metadata(key, value) VALUES ('wordCount', ?)",
            (str(word_count),),
        )
        target.execute(
            "INSERT OR REPLACE INTO metadata(key, value) VALUES ('groupCount', ?)",
            (str(group_count),),
        )
        target.execute(
            "INSERT OR REPLACE INTO metadata(key, value) VALUES ('phraseMerged', ?)",
            (curriculum_tag,),
        )
        target.execute(
            "INSERT OR REPLACE INTO metadata(key, value) VALUES ('phraseMergedNewWords', ?)",
            (str(new_words),),
        )

        # Recompute signature
        target.commit()
        signature = _recompute_signature(target)
        target.execute(
            "INSERT OR REPLACE INTO metadata(key, value) VALUES ('assetSignature', ?)",
            (signature,),
        )
        target.commit()
        target.execute("VACUUM;")

        # Verify foreign keys
        violations = target.execute("PRAGMA foreign_key_check").fetchall()
        if violations:
            raise RuntimeError(f"foreign key violations after merge: {violations[:3]}")

        return {
            "newWords": new_words,
            "skipped": skipped,
            "totalWords": word_count,
            "totalGroups": group_count,
            "signature": signature,
        }
    finally:
        phrase_db.close()
        target.close()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("docx", type=Path, help="path to the phrase docx file")
    parser.add_argument("output", type=Path, help="output SQLite database path")
    parser.add_argument(
        "--merge-into", type=Path, default=None,
        help="existing CDict-dict.db to merge into (in-place update)",
    )
    parser.add_argument(
        "--curriculum-tag", default="高中短语",
        help="curriculum tag to apply to all phrase entries",
    )
    parser.add_argument(
        "--expected-entries", type=int, default=None,
        help="fail if the number of parsed entries does not match",
    )
    args = parser.parse_args()

    if not args.docx.is_file():
        parser.error(f"docx file not found: {args.docx}")

    sections = parse_docx(args.docx)
    if not sections:
        parser.error("no sections parsed from the docx")

    total_entries = sum(len(s["entries"]) for s in sections)
    print(f"Parsed {len(sections)} sections, {total_entries} entries", file=sys.stderr)

    if args.expected_entries is not None and total_entries != args.expected_entries:
        parser.error(
            f"entry count {total_entries} does not match expected {args.expected_entries}"
        )

    if args.merge_into:
        if not args.merge_into.is_file():
            parser.error(f"target database not found: {args.merge_into}")

        # Build phrase db first, then merge
        tmp_phrase_db = args.output.with_suffix(".phrase-tmp.db")
        try:
            build_stats = build_phrase_db(sections, tmp_phrase_db, args.curriculum_tag)
            print(f"Built phrase DB: {build_stats}", file=sys.stderr)

            merge_stats = merge_phrase_db(tmp_phrase_db, args.merge_into, args.curriculum_tag)
            print(json.dumps({"output": str(args.merge_into), **merge_stats}, ensure_ascii=False, sort_keys=True))
        finally:
            if tmp_phrase_db.exists():
                tmp_phrase_db.unlink()
    else:
        build_stats = build_phrase_db(sections, args.output, args.curriculum_tag)
        print(json.dumps({"output": str(args.output), **build_stats}, ensure_ascii=False, sort_keys=True))

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, sqlite3.Error, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"build-phrase-db: error: {error}", file=sys.stderr)
        raise SystemExit(1)