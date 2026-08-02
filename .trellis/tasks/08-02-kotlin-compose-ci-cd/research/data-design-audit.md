# Research: Data design audit

- The full source count is now verifiable from the authorized page snapshot: seven groups and exactly 49,213 words; the converter must assert this and supporting table counts or record the observed snapshot values.
- Normalize source data into words/groups, derived terms, roots/affixes, deduplicated exam sentences, word-sentence links, heatmap/statistics, and audio fields. Keep nullable source fields nullable and preserve source indexes during conversion.
- English search should use an FTS5 virtual table with a stable row mapping; Chinese substring search should use indexed word/translation columns where possible plus `LIKE` fallback.
- Add foreign keys, uniqueness constraints, and indexes for word/group, links, sentence, derived/root rows, frequency and headword ordering.
- Database asset installation must be idempotent, run off the main thread, expose progress/failure state, and not mark initialization complete until integrity/count validation succeeds.
- Do not represent the approximate word count as verified unless the converter has actually fetched/decoded the authorized snapshot and passed assertions.
