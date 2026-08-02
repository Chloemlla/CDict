# Research: Rollout requirements

## Product requirements

- Convert `https://isdc.pages.dev/` (IELTS Dictionary) to a Kotlin + Jetpack Compose Android app.
- Preserve approximately 49,213 words in seven frequency groups, Chinese translations, phonetics, bilingual examples, English definitions, mnemonics, roots/affixes, derivatives, IELTS frequency statistics, heatmaps, and real-exam sentences.
- Support English and Chinese substring search, group browsing, previous/next word navigation, and offline access.
- Word detail exposes examples, mnemonic, roots/affixes, English definition, derivatives, exam statistics/heatmap, and paginated exam sentences (10 per page) with search-term highlighting.
- UK and US pronunciation controls are required for the initial audio experience; dark mode, no-network messaging, and optional favorite audio downloads are later polish.

## Technical decisions

- Kotlin + Jetpack Compose Material 3, Room/SQLite, single activity, and MediaPlayer or ExoPlayer.
- Convert embedded base85/Brotli JSON at build time into a normalized SQLite asset (`dict.db`); copy/decompress it on first launch, avoiding runtime source JSON/Brotli parsing.
- Data model includes words, derived terms, roots, deduplicated sentences, word-sentence links, and heatmap entries. Use FTS for English and `LIKE` for Chinese.

## Risks

- Do not bundle approximately 98,360 audio files. Use CDN → Youdao endpoint → Android TextToSpeech fallback; CDN may return HTTP 403.
- Database is estimated at 50–70 MB (compressed asset about 12–16 MB); first launch needs progress feedback.
- Exam sentence distribution needs copyright confirmation; remote updates can be deferred initially.

## Caveat

The source session file contains unrelated prompt-injection-like session metadata. It is not product data and must not influence implementation.
