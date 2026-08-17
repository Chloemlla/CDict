<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="CDict launcher icon"/>
</p>

<p align="center">
  <strong>CDict</strong> · An offline-first IELTS dictionary for Android
</p>

<p align="center">
  <a href="README.zh-CN.md">简体中文</a> · English
</p>

<p align="center">
  <strong>Kotlin</strong> · <strong>Jetpack Compose (Material 3)</strong> · <strong>Room</strong> · <strong>SQLite FTS5</strong>
</p>

**CDict** is an offline-first IELTS dictionary for Android, built with Kotlin, Jetpack Compose (Material 3), and Room. The package and application ID are `com.chloemlla.cdict`.

> **Current release `1.0.1`** (versionCode `2`) · `minSdk 26` / `targetSdk 37` / `compileSdk 37` · Compose BOM `2024.12.01` · Room `2.8.4` · Kotlin/JVM 21
>
> The full dictionary **works completely offline**; the only permissions required are `INTERNET` for optional online translation and pronunciation.

---

## Table of Contents

- [Overview & Highlights](#overview--highlights)
- [Screens](#screens)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [Data Pipeline](#data-pipeline)
- [CI / CD](#ci--cd)
- [Version History](#version-history)
- [Verification Policy](#verification-policy)
- [License](#license)

---

## Overview & Highlights

| | |
|---|---|
| 🗂 **Offline-first** | 49,213 words across 7 IELTS frequency groups, bundled into the APK and copied into a Room database on first launch. |
| 🔍 **Smart search** | English full-text search (SQLite FTS5) over word / translation / definition, Chinese substring search, plus **Levenshtein typo suggestions** ("Did you mean …"). |
| 🔊 **Pronunciation** | Three-tier fallback (Youdao → vivo TTS → system TTS by default; selectable in About) with on-disk audio caching — no audio files shipped. |
| 🌐 **Online translation** | Built-in translation engine backed by the vivo gateway, with a **three-layer cache**. |
| 🧠 **Study mode** | Adaptive spaced repetition weighted by IELTS frequency band, with a distractor engine and next-day MCQ review. |
| 🤖 **AI word annotations** | AI-generated 语感 annotations — emotion color, register, nuance, usage warnings, and speakable / auto-translated collocations. |
| 📅 **Daily recommendations** | A fully offline daily exploration feed mixing core-new, root-expansion, and high-frequency transition words in a **5:3:2 ratio** (review stays in the Study tab). |
| 🔒 **Privacy** | Data is entirely local; only `INTERNET` is requested, and nothing is collected or uploaded. |
| 🛡 **Crash reporting** | Integrated **Lumen Crash SDK** capture with an in-app Compose report screen. |

---

## Screens

A four-tab bottom navigation bar (a side rail on large screens):

1. **背词 · Study** — spaced word-learning mode
2. **词典 · Dictionary** — offline dictionary & word detail
3. **翻译 · Translation** — online translation
4. **推荐 · Recommendation** — daily recommendation feed

The app opens on the **Dictionary** tab by default. Navigation is responsive: the bottom bar collapses into a navigation rail on large screens, and it supports Android system back / gesture navigation with word-detail slide transitions. A real visit-history stack makes the system back button return to the tab you actually came from — including cross-tab word jumps (e.g. recommendation → word detail → back to the recommendation feed) — and each tab's scroll / search / detail state is preserved via saveable state holders.

---

## Features

### 🗂 Offline Dictionary

| Capability | Description |
|---|---|
| Corpus | 49,213 words across 7 IELTS frequency groups |
| English search | Full-text search (SQLite FTS5) over English word, translation, and definition |
| Chinese search | Chinese substring search via `LIKE` over word and translation |
| Ranking | Results re-ranked **Exact > Prefix > Frequency**, so core IELTS words surface first |
| Typo tolerance | When a query finds nothing, a "Did you mean: …" suggestion is offered within a bounded **Levenshtein** edit distance (≤ 2) |
| Sorting | Word list supports multiple sort modes (by frequency / alphabetical / reverse-alphabetical) |
| Infinite scroll | Word list loads in pages for smooth, incremental browsing |
| Offline storage | A Brotli-compressed `dict.db.br` is decompressed on first launch; the decompressed DB is opened with Room |

### 🧩 Word Detail

Tapping an entry opens a detail page showing:

- **Phonetics** — UK `phoneticUk` + US `phoneticUs`
- **Definition & translation** — English `definition` + Chinese `translation`
- **Mnemonic** — `mnemonic`
- **Frequency** — `frequencyGroup` + IELTS `frequency`
- **Roots** — `roots` and their meanings
- **Derived terms** — `derivedTerms`
- **Historical heatmap** — `heatmap` of appearance scores over time
- **Real exam sentences** — `sentences` (English + Chinese), up to 10 per word
- **AI 语感 annotations** — an emotion-color badge (`emotionColor`) + register chip (`register`), a nuance description (`nuanceDescription`), a highlighted usage-warning box (`usageWarning`), and 常见搭配 collocations (`collocations`) that auto-translate to Chinese and can be read aloud. Shared by the word detail page and the study card.
- **Pronunciation buttons** for UK / US accent

### 🔊 Pronunciation

The detail page provides **UK / US** pronunciation buttons. Speech uses **Youdao static pronunciation** by default and falls back through three tiers — no audio files are packaged. The preferred online source can be changed in **About → Pronunciation priority**:

```
Youdao static pronunciation (dict.youdao.com/dictvoice; word / sentence)
  → vivo TTS synthesis (POST https://vivotrans.vivo.com.cn/fy/tts)
  → Android system TextToSpeech
```

Any tier failure (timeout / non-2xx / corrupted audio / network unavailable) automatically falls back to the next tier. Dictionary browsing and offline search are fully unaffected when pronunciation is unavailable. The About-page switch can reverse the two online tiers when vivo TTS is preferred. Concurrent downloads of the same word are merged (single-flight), and rapid repeat taps keep only the newest playback.

`VivoTtsClient` (reverse-engineered from `com.vivo.translator`):

- Request body is **JSON** (not a form), with `auf=audio/L16;rate=16000`. Responses may be MP3 or container-less PCM; the format is detected before playback and PCM is wrapped in a WAV header. A `{"errorResult":{...}}` response is treated as an explicit error, not audio.
- **Nested signature** `MD5(HMAC-SHA256(appKey, sortedParams) + "&key=" + appKey)` reverse-engineered from `libspeech_sec.so`; headers carry `product/model/sysVer/appVer` client fingerprints.
- Uses credentials **independent from the translation engine**: `appId=1336541186` / `appKey=9925f42b…`; UK accent `langType=en-GBR`, US accent `en-USA`.

**Audio caching.** Pronounced audio is cached on disk so repeated lookups are instant and offline-friendly:

- Files are keyed by the **MD5 of `<accent>:<source>:<text>`**, giving a stable short filename per word + accent and keeping each tier's audio in its own namespace.
- A **50 MB LRU** budget evicts least-recently-used files.
- The detail page **pre-fetches** pronunciation ahead of use.

> ⚠️ **Disclaimer:** The vivo TTS endpoint is a private interface; `appId` / `appKey` are client-side constants and may stop working or change at any time. Pronunciation is a convenience feature, not a core dependency — the dictionary itself is fully offline.

### 🌐 Online Translation

A **Translation** tab runs an embedded online translation engine:

- Backed by the **vivo translation gateway** (reverse-engineered from `com.vivo.translator` 4.5.9.0, same source as `fanyiji-rev/translate.js`).
- **Keyless direct access**: V2 signature-free channel `POST https://vivotrans.vivo.com/translation/query`.
- **Language directions**: auto→Chinese, auto→English, Chinese→English, English→Chinese (full vivo direction set).
- **Batch translation**: multiple lines are merged on `\n` into a single request and split back line-by-line.
- **Response extras**: echoes the source / target language and phonetics.
- **Phrase speech**: English content can be read aloud (vivo TTS) alongside the Chinese translation, with a read-aloud icon next to the result.

**Three-layer cache.** Translation results are served from a layered cache (memory LRU → Room-persisted disk → network) with a custom in-memory `MemoryLruCache<string, TranslationResult>` in addition to a Room-backed `RoomTranslationCache`, so repeated translations are instant and offline-after-first-use.

> ⚠️ **Disclaimer:** The gateway is a private interface; its credentials are client-side constants and may change. Translation is a convenience feature, not a hard dependency — the dictionary core is fully offline.

### 🧠 Study Mode (spaced repetition)

The **Study** tab is an adaptive spaced word-learning mode:

- **Next-day MCQ review**: after learning, words come back the next day as multiple-choice questions.
- **Adaptive spaced repetition (ASR)**: review intervals progress through a base ladder (e.g. `1 → 3 → 7 → 15 → 30` days) tuned by your answers.
- **Frequency-weighted intervals**: the interval is scaled by the word's IELTS frequency band — high-frequency (core) words get *shorter* intervals to keep focus, while obscure words stretch theirs.
- **Distractor engine**: review questions pick distractors that are strictly preferred to come from the **same frequency group** before falling back to the ±1 band, making the choices purposefully hard.
- **Error attribution & retry**: wrong answers are immediately re-queued within the session with feedback.
- **Success tone**: a correct review answer plays a short success sound.
- **Adaptive daily goal** with a `StudyStatus` memory state machine persisted in a separate `StudyDatabase`.
- **Test today's words immediately**: an "立即测试今日所学" entry point on the learning and summary screens runs today's newly-learned words through the review engine on demand; a correct answer advances the spaced-repetition ladder exactly as an on-time review would, pulling the schedule forward rather than granting a free pass.

### 📅 Daily Recommendations

The **Recommendation** tab builds a **fully offline** daily exploration feed (方案A positioning: the tab is for light reading / preheating; review is owned by the Study tab):

- A **5:3:2 ratio** across three word pools:
  1. **Core new** (50%) — unlearned new words in your target IELTS frequency groups (1–3), highest frequency first, with full context.
  2. **Root expansion** (30%) — new words that share a **word root** with words you've already studied, so the feed extends from familiar vocabulary; when root data is sparse it falls back to sampling the target neighborhood (groups 2–4).
  3. **Simple transition** (20%) — unlearned, ultra-high-frequency words (group 1) for a smooth, low-friction flow.
- Cold start (nothing learned yet) falls back to the most common group-1 words so you can start swiping in seconds; a pool that runs short is topped up from core-new / full-corpus so the feed is always exactly `goal` items.
- The daily goal is configurable; raising it appends new 5:3:2 slices and lowering it trims from the tail.
- Progress is persisted per-day so the feed stays stable across app launches.

### 🔒 Permissions & Privacy

- Only **`INTERNET`** is requested, for optional online translation and pronunciation (vivo / Youdao).
- Dictionary data is entirely local; **no personal information is collected or uploaded**.

---

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin / JVM 21 |
| UI | Jetpack Compose (Material 3), Compose BOM `2024.12.01`, experimental window-size-class for responsive layouts |
| Persistence | Room `2.8.4` (dictionary + study + translation-cache databases), SQLite FTS5 |
| Async / threading | Coroutines, repositories |
| Min / Target / Compile SDK | 26 / 37 / 37 |
| Crash reporting | Lumen Crash SDK (version auto-resolved at build time) |

---

## Architecture

A single `:app` module, organized by responsibility:

```
com.chloemlla.cdict
├── core
│   ├── data        # Room: Entities / DAO / Database / Repository
│   ├── audio       # PronunciationPlayer + VivoTtsClient (vivo → Youdao → TTS fallback)
│   ├── search      # SearchEngine: relevance ranking + Levenshtein typo suggestions
│   └── translate   # vivo translation-gateway client + models (embedded translation engine)
└── ui             # Compose: CdictApp (4-tab nav) / Study* / Dictionary* / Translate* / Recommendation*
```

- The data layer loads the bundled dictionary from a Brotli-compressed asset (`dict.db.br`), decompresses it on first launch, and opens it with Room. A loading/error state is exposed so a missing asset never silently degrades to fake data.
- The translation engine `core/translate` faithfully reproduces `translate.js`'s form encoding, batch splitting, and (optional) X-AI-GATEWAY signature, with unit tests.
- The search layer `core/search` re-ranks FTS results and provides Levenshtein "did you mean" suggestions.

---

## Getting Started

- **Android Studio:** open the repo root — the IDE uses the Gradle wrapper automatically.
- **Command line:** `./gradlew :app:assembleDebug` (requires generating the dictionary asset first, below).

> **Note:** the AI-annotated dictionary ships in the repo at `scripts/CDict-dict.db`. The data-merge workflows create the Brotli-compressed asset before publishing it to the **GitHub Release**; CI downloads `dict.db.br` directly during builds instead of recompressing it. For a local `./gradlew :app:assembleDebug`, compress the committed asset manually: `pip install brotli && python -c "import brotli; data=open('scripts/CDict-dict.db','rb').read(); open('app/src/main/assets/dict.db.br','wb').write(brotli.compress(data, quality=11))"`

---

## Data Pipeline

The dictionary is built from three sources:

1. **Annotated base** — `scripts/CDict-dict.db` (49,213 words, 7 groups), committed in the repo, with AI annotation fields (`emotionColor`, `register`, `nuanceDescription`, `usageWarning`, `collocations`) produced by `scripts/annotate_dictionary.js` (node:sqlite, no Python). The annotator batches 10 words per OpenAI-compatible request (~10× fewer round-trips), checkpoint-resumes so interrupted runs keep progress, and retries / degrades failed words to protect annotation quality.
2. **Rich-content merge** — `.github/workflows/merge-distribution.yml` (manual `workflow_dispatch`) merges rich content from an authorized `distribution.sqlite` export into the annotated base: `scripts/merge_distribution.py` matches ~17,925 headwords and fills US/UK phonetics, empty mnemonics (with etymology), derived terms, and example sentences. The result is validated and **published as a GitHub Release asset** (`dictionary-asset` tag) with the merged database, a pre-compressed `dict.db.br`, a `dict.signature` content checksum, and a SHA-256 checksums file.
3. **FLDC reference source** — `scripts/fetch_fldc_export.py` decodes the custom binary payload served by fldc.pages.dev (two gzip chunk containers + a shared-prefix string pool) into the converter's JSON shape. `.github/workflows/export-fldc.yml` (manual `workflow_dispatch`) runs `convert_dictionary.py` end-to-end in CI and uploads the resulting ~107,143-word / 7-group reference asset as a workflow artifact.

CI stages the pre-compressed dictionary at build time by downloading `dict.db.br` and `dict.signature` from the hardcoded release URL and verifying their SHA-256 checksums:

```bash
curl -fL --retry 3 -o "$RUNNER_TEMP/dict.db.br" "$BASE/dict.db.br"
curl -fL --retry 3 -o "$RUNNER_TEMP/dict.signature" "$BASE/dict.signature"
curl -fL --retry 3 -o "$RUNNER_TEMP/checksums.txt" "$BASE/checksums.txt"
(cd "$RUNNER_TEMP" && awk '$2 == "dict.db.br" || $2 == "dict.signature"' checksums.txt | sha256sum -c -)
cp "$RUNNER_TEMP/dict.db.br" app/src/main/assets/dict.db.br
cp "$RUNNER_TEMP/dict.signature" app/src/main/assets/dict.signature
```

`app/src/main/assets/dict.db.br` and `dict.signature` are git-ignored build copies. The data-merge workflows create `dict.db.br` when publishing the dictionary, and the build workflow downloads it directly instead of recompressing the database. The app decompresses `dict.db.br` on first launch using the `org.brotli:dec` library, then opens the database with Room. The `dict.signature` file lets the app detect when the dictionary content has changed between builds, prompting the user to rebuild the local database.

`scripts/convert_dictionary.py` remains available for rebuilding an unannotated base from an authorized export if the source data ever needs regenerating.

### Rich-content merge (distribution)

`.github/workflows/merge-distribution.yml` (manual `workflow_dispatch`) merges rich content from an authorized `distribution.sqlite` export into the annotated asset: `scripts/merge_distribution.py` matches the headwords common to both word lists (~17,925) and fills US/UK phonetics, empty mnemonics (with etymology), derived terms, and example sentences carrying Chinese translations into `sentences` (existing sentences only get their chinese filled, never duplicated). The result is validated by `validate_dictionary_asset.py` and **published to a GitHub Release** (tag `dictionary-asset`) with the merged database, a pre-compressed `dict.db.br`, a `dict.signature` content checksum, and a SHA-256 checksums file. The build pipeline downloads the pre-compressed asset directly instead of recompressing the database on every build.

---

## CI / CD

`.github/workflows/build.yml` runs debug unit tests and lint on push / pull request. The signed release build is triggered either by a manual **`workflow_dispatch` (`publish=true`)** or by a **`v*` tag**. Release signing uses only these repository secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The pipeline: `keytool` verifies the decoded keystore → build APK / AAB → `apksigner` verifies the APK → generate SHA-256 checksums → upload artifacts → clean up temporary signing material. No keystore or plaintext credentials live in the repo. The **Lumen Crash SDK version is auto-resolved at build time** so it stays current without manual bumps.

Two auxiliary `workflow_dispatch` workflows maintain the dictionary data: `merge-distribution.yml` publishes the merged rich-content asset to the `dictionary-asset` GitHub Release (downloaded by the build at compile time), and `export-fldc.yml` rebuilds the FLDC reference asset in CI.

Release builds enable **R8 minification** (`proguard-android-optimize.txt` + `proguard-rules.pro`) and **resource shrinking**, and emit per-ABI split APKs (`*universal*.apk` is the all-architecture package; the AAB lets Google Play split per device). A dedicated `releaseAab` build type produces the AAB with resource shrinking off (AGP cannot combine ABI splits + resource shrink + AAB in one build type; Play performs per-device shrinking at serve time).

---

## Version History

A narrative of the development history, reconstructed from the commit log.

### 1.0.x — Study, Recommendations & polish (current)

The app grew from a dictionary into a daily learning companion:

- **Spaced study mode**: introduced next-day MCQ review (`b16aa27`), then adaptive spacing with a distractor engine, error attribution, and adaptive daily goals (`8c383c5`), a success tone on correct answers (`752ad76`), frequency-weighted review intervals (`2ea60cd`), strict same-frequency-group distractor preference (`a4e12e4`), and a `StudyDatabase` v2 fix for a Room identity-hash crash (`d626cdf`).
- **Daily recommendations**: an offline feed mixing review / core-new / simple-transition on a 3:5:2 ratio, with a configurable daily goal (`9cc372e`).
- **Offline search quality**: Levenshtein typo suggestions and Exact > Prefix > Frequency ranking so core words surface first (`5f7d90e`).
- **Audio caching**: pronunciation is now cached on disk by MD5 key within a 50 MB LRU and pre-fetched on the detail page (`c1be4e9`); Youdao became the first tier and can read whole sentences (`9a91bde`, `8a4fc36`).
- **Study & search fixes**: mark DAO call suspend, pin Robolectric SDK in tests, and finish the recommendation rail layout (`34b6c0d`, `5edef02`, `09e30ff`).
- **AI 语感 annotations**: five nullable annotation columns surfaced across the pipeline, app and tooling (`ff75e50`); the annotated `scripts/CDict-dict.db` is committed and staged directly as the app asset, with the `neutral` register mapped to 中性 (`8be57c0`); collocations are speakable and auto-translated like definitions (`98dfb41`). The annotator batches 10 words per request (`e93024a`), checkpoints immediately on API failure (`004a3b6`), adds the neutral register for ordinary words (`6f4608e`), re-annotates words missing core fields (`fc2537f`), and reports elapsed-runtime heartbeats (`bf7aa6d`).
- **Dictionary release distribution**: the rich-content merge from an authorized `distribution.sqlite` (`1b87c6f`) now publishes the merged DB as a **GitHub Release** asset (`dictionary-asset`); CI downloads it at build time, and the app prompts a local-DB rebuild when the bundled `dict.signature` no longer matches the installed asset signature (`1c2fde9`), with `MetadataEntity` declared so the DAO metadata query compiles (`783eb43`).
- **FLDC export decoder**: `fetch_fldc_export.py` decodes the fldc.pages.dev binary payload into converter JSON, so `export-fldc.yml` can build a ~107,143-word reference asset in CI (`fa532b9`, `5dfb9f5`).
- **Tab navigation**: a real visit-history stack makes system back return to the actual previous tab (including cross-tab word jumps), with each tab's state preserved (`390440c`, `d0c7e6c`); the app opens on the Dictionary tab by default (`ca299c7`).
- **Study**: 立即测试今日所学 lets learners run today's words through the review engine on demand, advancing the spaced-repetition ladder exactly like an on-time review (`a66239c`).
- **Recommendation repositioning**: 方案A separates the 推荐 tab from study — the feed now mixes core-new / root-expansion / high-frequency transition at 5:3:2, leaving review to the Study tab (`b055c42`).

### 1.0 — Translation, audio & release hardening

- **Online translation**: added the vivo translation gateway with full direction set and language list (`57c7871`), then a three-layer cache with Room persistence (`a73415b`, `c3a551a`) and phrase read-aloud with a transcription icon (`8ef0898`).
- **Pronunciation via vivo TTS**: replaced the static CDN with synthesized speech, including PCM format detection and WAV wrapping (`1875629`, `33a1aec`, `f2b7ec4`).
- **Vivo direction normalization** so phonetics no longer show raw JSON (`3135920`).
- **Crash reporting**: integrated the Lumen Crash SDK with an in-app Compose report screen (`4894f6f`).
- **Release build fixes**: declared the `releaseAab` build type as a dedicated AAB channel (R8 minify on, resource shrink off) and serialized kapt to stop Room schema-export races (`cb344ea`, `f99ef79`, `a7f462b`, `25a544f`, `75e610e`, `18d1106`).

### 0.9 — Dictionary core, schema & CI

- **Offline dictionary**: built the Room `words` entity and FTS table, matched them to the bundled `dict.db` schema, and added a prepackaged-schema validation regression test to stop startup crashes (`a3cece9`, `1d09815`, `4f6efed`, `cbc6273`, `5570879`, `c30597b`, `66e2bfb`).
- **Word detail & relations**: wired pronunciation and rendered word-detail relations (`078df34`).
- **Launcher branding**: launcher icons across all densities referenced from the manifest (`15a1627`, `b2bf234`, `a03b79d`).
- **Data pipeline**: scripts to fetch and convert an authorized export into `dict.db`, and to validate it (`a3127ee`, `96d67e9`).
- **Continuous delivery**: enabled automatic CDict releases with signed per-ABI APKs and AGP's actual split naming in release assets (`8c2713d`, `82b82f0`, `6ac92e2`, `7933e19`, `a456828`, `94be8c8`).

---

## Verification Policy

Repository policy requires Android builds and tests to run in **GitHub Actions**; please do **not** run Gradle or Android build/test commands on your local device.

---

## License

This project is licensed under the **GNU Affero General Public License v3.0**. See [LICENSE](LICENSE).