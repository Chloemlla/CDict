#!/usr/bin/env python3
"""Merge rich content from a distribution.sqlite source into CDict-dict.db.

For every headword present in both datasets the distribution's richer fields are
merged into the existing CDict words schema:

- phoneticUs / phoneticUk <- pos_group_pronunciations (US/UK-tagged IPA, slashes
                             stripped); the source value overwrites the base for
                             that accent whenever the source has one
- mnemonic                <- the curated base mnemonic is kept and the source
                             entries.memory_hook (with etymology_note appended) is
                             added after it, so neither source is lost
- derived_terms           <- pos_group_relations type='derived_term'
- sentences + links       <- meaning_examples (english text + chinese translation);
                             an example whose english already exists in `sentences`
                             gets its empty chinese filled instead of being duplicated
- headwordSummary         <- entries.headword_summary (a one-sentence gloss)
- word_relations          <- pos_group_relations types synonym/antonym/related_term
- word_forms              <- pos_group_forms (inflection forms with POS/tense tags)
- etymologies             <- etymologies.text (structured etymology paragraphs)
- study_notes             <- entry_study_notes (learner-oriented notes, mostly zh)

The four relation/form/etymology/study-note tables and the headwordSummary column are
created on the output when missing, matching the Room entity schema exactly (column
types, primary keys, foreign keys and index names), so the published asset passes
Room's createFromAsset schema validation.

Each enriched word also gets an `aiSupplemented` column (added when missing) storing
a comma-separated list of the fields that came from the distribution source, so the
app can flag AI-supplemented content in the UI. The `curriculumTags` column is also
created when missing for workflow-applied curriculum labels.

Words present in only one dataset are left untouched, so the base word count,
the 7 frequency groups and the FTS index (word/translation/definition) all remain
valid. Run this in CI on an authorized distribution export.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import sqlite3
import sys
from pathlib import Path

SLASH = re.compile(r"^/*(.*?)/*$")


def strip_ipa(value: str | None) -> str | None:
    """Remove surrounding slashes/whitespace from an IPA token (CDict stores bare IPA)."""
    if not value:
        return None
    cleaned = SLASH.sub(r"\1", value.strip())
    return cleaned or None


def read_distribution(path: Path) -> dict[str, dict]:
    """Preload the distribution tables once; returns per-headword enrichments."""
    db = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    try:
        entries: dict[str, tuple[str, str | None, str | None, str | None]] = {}
        for entry_id, headword, normalized, memory_hook, etymology_note, headword_summary in db.execute(
            "SELECT entry_id, headword, normalized_headword, memory_hook, etymology_note, headword_summary FROM entries"
        ):
            key = headword.strip().casefold()
            entries.setdefault(key, (entry_id, memory_hook, etymology_note, headword_summary))
            nkey = (normalized or "").strip().casefold()
            if nkey and nkey != key:
                entries.setdefault(nkey, (entry_id, memory_hook, etymology_note, headword_summary))

        pronunciations: dict[str, list[tuple[str | None, str | None, frozenset[str]]]] = {}
        for entry_id, ipa, text, tags_json in db.execute(
            "SELECT entry_id, ipa, text, tags_json FROM pos_group_pronunciations"
        ):
            tags = frozenset(json.loads(tags_json)) if tags_json else frozenset()
            pronunciations.setdefault(entry_id, []).append((ipa, text, tags))

        examples: dict[str, list[tuple[str, str]]] = {}
        for entry_id, text, translation in db.execute(
            "SELECT entry_id, text, translation FROM meaning_examples"
        ):
            if text and text.strip():
                examples.setdefault(entry_id, []).append((text.strip(), translation or ""))

        derived: dict[str, list[str]] = {}
        for entry_id, word in db.execute(
            "SELECT entry_id, word FROM pos_group_relations WHERE type = 'derived_term'"
        ):
            if word and word.strip():
                derived.setdefault(entry_id, []).append(word.strip())

        relations: dict[str, list[tuple[str, str]]] = {}
        for entry_id, rtype, word in db.execute(
            "SELECT entry_id, type, word FROM pos_group_relations "
            "WHERE type IN ('synonym', 'antonym', 'related_term')"
        ):
            if word and word.strip():
                relations.setdefault(entry_id, []).append((rtype, word.strip()))

        forms: dict[str, list[tuple[str, str]]] = {}
        for entry_id, text, tags_json in db.execute(
            "SELECT entry_id, text, tags_json FROM pos_group_forms "
            "ORDER BY pos_group_index, form_index"
        ):
            cleaned = (text or "").strip()
            if cleaned and not re.fullmatch(r"[^a-zA-Z0-9]+", cleaned):
                tags = ",".join(sorted(json.loads(tags_json))) if tags_json else ""
                forms.setdefault(entry_id, []).append((cleaned, tags))

        etymologies: dict[str, list[str]] = {}
        for entry_id, text in db.execute(
            "SELECT entry_id, text FROM etymologies "
            "WHERE text IS NOT NULL AND length(trim(text)) > 0 ORDER BY etymology_index"
        ):
            etymologies.setdefault(entry_id, []).append(text.strip())

        study_notes: dict[str, list[str]] = {}
        for entry_id, note_text in db.execute(
            "SELECT entry_id, note_text FROM entry_study_notes "
            "WHERE note_text IS NOT NULL AND length(trim(note_text)) > 0 ORDER BY note_index"
        ):
            study_notes.setdefault(entry_id, []).append(note_text.strip())
    finally:
        db.close()

    return {
        "entries": entries,
        "pronunciations": pronunciations,
        "examples": examples,
        "derived": derived,
        "relations": relations,
        "forms": forms,
        "etymologies": etymologies,
        "study_notes": study_notes,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base", type=Path, help="current CDict-dict.db")
    parser.add_argument("distribution", type=Path, help="distribution.sqlite source")
    parser.add_argument("output", type=Path, help="merged output database")
    parser.add_argument("--expected-word-count", type=int, default=None)
    parser.add_argument("--expected-groups", type=int, default=None)
    args = parser.parse_args()

    if not args.base.is_file():
        parser.error(f"base database missing: {args.base}")
    if not args.distribution.is_file():
        parser.error(f"distribution source missing: {args.distribution}")

    data = read_distribution(args.distribution)
    entries = data["entries"]
    pronunciations = data["pronunciations"]
    examples = data["examples"]
    derived = data["derived"]
    relations = data["relations"]
    forms = data["forms"]
    etymologies = data["etymologies"]
    study_notes = data["study_notes"]

    args.output.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(args.base, args.output)
    db = sqlite3.connect(args.output)
    try:
        db.execute("PRAGMA foreign_keys = ON;")
        words_columns = {row[1] for row in db.execute("PRAGMA table_info(words)")}
        if "aiSupplemented" not in words_columns:
            db.execute("ALTER TABLE words ADD COLUMN aiSupplemented TEXT")
        if "headwordSummary" not in words_columns:
            db.execute("ALTER TABLE words ADD COLUMN headwordSummary TEXT")
        if "curriculumTags" not in words_columns:
            db.execute("ALTER TABLE words ADD COLUMN curriculumTags TEXT")

        # Distribution tables created on the output so the published asset matches
        # the Room entity schema (columns, PKs, FKs, index names) exactly.
        new_table_ddl = [
            """
            CREATE TABLE IF NOT EXISTS word_relations (
                wordId INTEGER NOT NULL,
                relationType TEXT NOT NULL,
                targetWord TEXT NOT NULL,
                PRIMARY KEY (wordId, relationType, targetWord),
                FOREIGN KEY (wordId) REFERENCES words (id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """,
            "CREATE INDEX IF NOT EXISTS index_word_relations_wordId ON word_relations (wordId)",
            """
            CREATE TABLE IF NOT EXISTS word_forms (
                wordId INTEGER NOT NULL,
                formIndex INTEGER NOT NULL,
                formText TEXT NOT NULL,
                formTags TEXT NOT NULL,
                PRIMARY KEY (wordId, formIndex),
                FOREIGN KEY (wordId) REFERENCES words (id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """,
            "CREATE INDEX IF NOT EXISTS index_word_forms_wordId ON word_forms (wordId)",
            """
            CREATE TABLE IF NOT EXISTS etymologies (
                wordId INTEGER NOT NULL,
                etymologyIndex INTEGER NOT NULL,
                text TEXT NOT NULL,
                PRIMARY KEY (wordId, etymologyIndex),
                FOREIGN KEY (wordId) REFERENCES words (id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """,
            "CREATE INDEX IF NOT EXISTS index_etymologies_wordId ON etymologies (wordId)",
            """
            CREATE TABLE IF NOT EXISTS study_notes (
                wordId INTEGER NOT NULL,
                noteIndex INTEGER NOT NULL,
                noteText TEXT NOT NULL,
                PRIMARY KEY (wordId, noteIndex),
                FOREIGN KEY (wordId) REFERENCES words (id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """,
            "CREATE INDEX IF NOT EXISTS index_study_notes_wordId ON study_notes (wordId)",
        ]
        for ddl in new_table_ddl:
            db.execute(ddl)
        base_words = db.execute("SELECT COUNT(*) FROM words").fetchone()[0]
        base_groups = db.execute("SELECT COUNT(*) FROM groups").fetchone()[0]
        if args.expected_word_count is not None and base_words != args.expected_word_count:
            parser.error(f"base word count {base_words} != expected {args.expected_word_count}")
        if args.expected_groups is not None and base_groups != args.expected_groups:
            parser.error(f"base group count {base_groups} != expected {args.expected_groups}")

        # Existing sentences keyed by trimmed english -> id, so duplicates get their
        # chinese filled instead of being inserted a second time.
        sentence_ids: dict[str, int] = {}
        next_sentence_id = 1
        for sid, english in db.execute("SELECT id, english FROM sentences"):
            key = (english or "").strip()
            if key:
                sentence_ids[key] = sid
                if sid >= next_sentence_id:
                    next_sentence_id = sid + 1

        stats = {
            "wordsEnriched": 0,
            "aiSupplementedWords": 0,
            "phoneticUkFilled": 0,
            "phoneticUsFilled": 0,
            "mnemonicFilled": 0,
            "derivedAdded": 0,
            "examplesAdded": 0,
            "examplesChineseFilled": 0,
            "headwordSummaryFilled": 0,
            "relationsAdded": 0,
            "formsAdded": 0,
            "etymologiesAdded": 0,
            "studyNotesAdded": 0,
        }
        insert_sentences: list[tuple[int, str, str | None]] = []
        fill_sentences: list[tuple[str, int]] = []
        batch_links: list[tuple[int, int]] = []
        batch_derived: list[tuple[int, str]] = []
        batch_relations: list[tuple[int, str, str]] = []
        batch_forms: list[tuple[int, int, str, str]] = []
        batch_etymologies: list[tuple[int, int, str]] = []
        batch_study_notes: list[tuple[int, int, str]] = []
        word_updates: list[tuple[str | None, str | None, str | None, str | None, str | None, int]] = []

        for word_id, word, phonetic_uk, phonetic_us, mnemonic, existing_summary in db.execute(
            "SELECT id, word, phoneticUk, phoneticUs, mnemonic, headwordSummary FROM words"
        ):
            entry = entries.get(word.strip().casefold())
            if entry is None:
                continue
            entry_id, memory_hook, etymology_note, headword_summary = entry
            stats["wordsEnriched"] += 1
            supplements: list[str] = []

            # Full overwrite: the distribution's US/UK pronunciation replaces the
            # base value whenever the source has one; the base value is kept only
            # when the source carries nothing for that accent.
            new_uk: str | None = None
            new_us: str | None = None
            for ipa, text, tags in pronunciations.get(entry_id, []):
                value = strip_ipa(ipa) or strip_ipa(text)
                if value is None:
                    continue
                if "US" in tags and new_us is None:
                    new_us = value
                elif "UK" in tags and new_uk is None:
                    new_uk = value
            if new_uk is None:
                new_uk = phonetic_uk
            else:
                supplements.append("phoneticUk")
                stats["phoneticUkFilled"] += 1
            if new_us is None:
                new_us = phonetic_us
            else:
                supplements.append("phoneticUs")
                stats["phoneticUsFilled"] += 1

            # Keep both sources: the curated base mnemonic is preserved and the
            # distribution memory hook (with the etymology note appended) is added
            # after it, so the original curated content is never discarded.
            new_mnemonic = mnemonic
            ai_parts = [memory_hook] if memory_hook and memory_hook.strip() else []
            if etymology_note and etymology_note.strip():
                ai_parts.append(f"[词源] {etymology_note.strip()}")
            if ai_parts:
                ai_text = "\n".join(ai_parts)
                if new_mnemonic and new_mnemonic.strip():
                    new_mnemonic = f"{new_mnemonic.strip()}\n\n{ai_text}"
                else:
                    new_mnemonic = ai_text
                supplements.append("mnemonic")
                stats["mnemonicFilled"] += 1

            for term in derived.get(entry_id, []):
                batch_derived.append((word_id, term))
            if derived.get(entry_id):
                supplements.append("derived")

            for english, translation in examples.get(entry_id, []):
                existing = sentence_ids.get(english)
                if existing is not None:
                    batch_links.append((word_id, existing))
                    if translation:
                        fill_sentences.append((translation, existing))
                        stats["examplesChineseFilled"] += 1
                    continue
                sentence_id = next_sentence_id
                next_sentence_id += 1
                sentence_ids[english] = sentence_id
                batch_links.append((word_id, sentence_id))
                insert_sentences.append((sentence_id, english, translation or None))
                stats["examplesAdded"] += 1
            if examples.get(entry_id):
                supplements.append("sentences")

            new_summary = existing_summary
            if headword_summary and headword_summary.strip():
                new_summary = headword_summary.strip()
                supplements.append("headwordSummary")
                stats["headwordSummaryFilled"] += 1

            for rtype, target in relations.get(entry_id, []):
                batch_relations.append((word_id, rtype, target))
            if relations.get(entry_id):
                supplements.append("relations")

            for form_index, (form_text, form_tags) in enumerate(forms.get(entry_id, [])):
                batch_forms.append((word_id, form_index, form_text, form_tags))
            if forms.get(entry_id):
                supplements.append("forms")

            for etymology_index, etymology_text in enumerate(etymologies.get(entry_id, [])):
                batch_etymologies.append((word_id, etymology_index, etymology_text))
            if etymologies.get(entry_id):
                supplements.append("etymology")

            for note_index, note_text in enumerate(study_notes.get(entry_id, [])):
                batch_study_notes.append((word_id, note_index, note_text))
            if study_notes.get(entry_id):
                supplements.append("studyNotes")

            marker = ",".join(sorted(set(supplements))) if supplements else None
            if marker:
                stats["aiSupplementedWords"] += 1
            if marker or new_uk != phonetic_uk or new_us != phonetic_us or new_mnemonic != mnemonic or new_summary != existing_summary:
                word_updates.append((new_uk, new_us, new_mnemonic, new_summary, marker, word_id))

        db.executemany(
            "UPDATE words SET phoneticUk = ?, phoneticUs = ?, mnemonic = ?, headwordSummary = ?, aiSupplemented = ? WHERE id = ?",
            word_updates,
        )
        db.executemany("INSERT OR IGNORE INTO derived_terms VALUES (?, ?)", batch_derived)
        stats["derivedAdded"] = len(batch_derived)
        db.executemany("INSERT OR IGNORE INTO sentences VALUES (?, ?, ?)", insert_sentences)
        db.executemany("UPDATE sentences SET chinese = ? WHERE id = ?", fill_sentences)
        db.executemany("INSERT OR IGNORE INTO word_sentence_links VALUES (?, ?)", batch_links)
        db.executemany("INSERT OR IGNORE INTO word_relations VALUES (?, ?, ?)", batch_relations)
        stats["relationsAdded"] = len(batch_relations)
        db.executemany("INSERT OR IGNORE INTO word_forms VALUES (?, ?, ?, ?)", batch_forms)
        stats["formsAdded"] = len(batch_forms)
        db.executemany("INSERT OR IGNORE INTO etymologies VALUES (?, ?, ?)", batch_etymologies)
        stats["etymologiesAdded"] = len(batch_etymologies)
        db.executemany("INSERT OR IGNORE INTO study_notes VALUES (?, ?, ?)", batch_study_notes)
        stats["studyNotesAdded"] = len(batch_study_notes)

        db.executemany(
            "INSERT OR IGNORE INTO metadata(key, value) VALUES (?, ?)",
            [
                ("distributionSource", str(args.distribution)),
                ("distributionMerged", "true"),
                ("mergedWords", str(stats["wordsEnriched"])),
                ("mergedAiSupplementedWords", str(stats["aiSupplementedWords"])),
                ("mergedExamplesAdded", str(stats["examplesAdded"])),
                ("mergedExamplesChineseFilled", str(stats["examplesChineseFilled"])),
                ("mergedDerivedAdded", str(stats["derivedAdded"])),
                ("mergedPhoneticUkFilled", str(stats["phoneticUkFilled"])),
                ("mergedPhoneticUsFilled", str(stats["phoneticUsFilled"])),
                ("mergedMnemonicFilled", str(stats["mnemonicFilled"])),
                ("mergedHeadwordSummaryFilled", str(stats["headwordSummaryFilled"])),
                ("mergedRelationsAdded", str(stats["relationsAdded"])),
                ("mergedFormsAdded", str(stats["formsAdded"])),
                ("mergedEtymologiesAdded", str(stats["etymologiesAdded"])),
                ("mergedStudyNotesAdded", str(stats["studyNotesAdded"])),
            ],
        )
        db.commit()

        final_words = db.execute("SELECT COUNT(*) FROM words").fetchone()[0]
        if final_words != base_words:
            raise RuntimeError(f"word count changed during merge: {base_words} -> {final_words}")
        violations = db.execute("PRAGMA foreign_key_check").fetchall()
        if violations:
            raise RuntimeError(f"foreign key violations after merge: {violations[:3]}")
        db.execute("VACUUM;")

        # Compute content signature: SHA256 of all word content columns.
        h = hashlib.sha256()
        for row in db.execute(
            "SELECT id, word, phoneticUk, phoneticUs, translation, definition, mnemonic, "
            "frequencyGroup, frequency, emotionColor, register, nuanceDescription, "
            "usageWarning, collocations, aiSupplemented, headwordSummary, curriculumTags FROM words ORDER BY id"
        ):
            h.update("|".join(str(v or "") for v in row).encode("utf-8"))
            h.update(b"\n")
        signature = h.hexdigest()
        db.execute(
            "INSERT OR REPLACE INTO metadata(key, value) VALUES ('assetSignature', ?)",
            (signature,),
        )
        db.commit()
    finally:
        db.close()

    print(json.dumps({"output": str(args.output), **stats}, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, sqlite3.Error, RuntimeError) as error:
        print(f"merge-distribution: error: {error}", file=sys.stderr)
        raise SystemExit(1)
