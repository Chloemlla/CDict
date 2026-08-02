# Android Data and CI/CD Contracts

## Scenario: Authorized dictionary asset generation and signed Android release

### 1. Scope / Trigger

This contract applies when changing `scripts/fetch_isdc_export.py`, `scripts/convert_dictionary.py`, `scripts/validate_dictionary_asset.py`, Room entities/database setup, `app/src/main/assets/dict.db`, or `.github/workflows/build.yml`. It is mandatory because the flow crosses the external source, SQLite schema, Android runtime, and GitHub signing environment.

### 2. Signatures

- `python scripts/fetch_isdc_export.py --output <path> [--url <url>] [--html-file <path>] [--expected-html-sha256 <hex>] [--expected-json-sha256 <hex>]`
  - Writes decoded source JSON and exits non-zero on missing `asp-data`, invalid Brotli, or hash mismatch.
- `python scripts/convert_dictionary.py <source> <output> [--expected-word-count <n>] [--expected-groups <n>] [--source-sha256 <hex>]`
  - Reads an explicit local authorized export; never downloads source data.
  - Writes SQLite `dict.db` containing `metadata`, `groups`, `words`, `derived_terms`, `roots`, `sentences`, `word_sentence_links`, `heatmap_entries`, and `word_search` FTS.
- `python scripts/validate_dictionary_asset.py <database> --expected-word-count <n> --expected-groups <n>`
  - Verifies required tables, counts, FTS row parity, and foreign-key integrity.
- Android runtime opens `Room.databaseBuilder(...).createFromAsset("dict.db")`; loading happens off the UI path and exposes loading/error state.

### 3. Contracts

#### Source payload

- URL: `https://isdc.pages.dev/`.
- Required response marker: `<script type="application/json" id="asp-data">`.
- Encoding: 16 newline-separated segments; site-specific 85-character printable ASCII alphabet; each segment independently Brotli-decompressed and concatenated before UTF-8 JSON parsing.
- Expected snapshot used by CI: HTML SHA-256 `c5cab0349b5fcf3e56904619a5f15c8923c7021a1f30c2c20639e2e597459c20`; decoded JSON SHA-256 `f83cddde1f09a8c4a15e97a6502187c935ba7dbf028e1c45812abd912cebecef`.
- Expected records: 7 groups and exactly 49,213 words.

#### SQLite contract

- `words.id` is the stable primary key; `words.word` is non-null and unique after case-fold normalization.
- `word_search` contains one FTS row per `words` row and indexes English word, translation, and definition.
- `word_sentence_links`, `derived_terms`, `roots`, and `heatmap_entries` use foreign keys to `words`; sentences are deduplicated by English text.
- Nullable source fields remain nullable; missing definitions, mnemonics, phonetics, or statistics must not abort conversion.
- Room schema declarations must stay aligned with the generated SQLite schema: `WordEntity` is `@Entity(tableName = "words")`, DAO queries and inserts target `words`, and `WordSearchEntity` retains `@Fts4(contentEntity = WordEntity::class)`.
- Asset path is `app/src/main/assets/dict.db`, generated in CI and ignored by Git.

#### CI environment and secrets

- Verification jobs: push and pull request; `contents: read`; no signing secrets.
- Release jobs: `workflow_dispatch` with `publish=true` or `v*` tag; release job alone has `contents: write`.
- Required secrets, and no others: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Temporary signing file: `$RUNNER_TEMP/cdict-release.jks`.
- Unix Gradle wrapper contract: `gradlew` is tracked as executable (`100755`), and each job runs `chmod +x ./gradlew` immediately after checkout before any `./gradlew` invocation; `gradlew.bat` remains available for Windows.
- Kotlin/AGP release signing must explicitly map the four environment values to `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`; the `KEY_PASSWORD` value must be non-null when the release signing config is created.
- Release workflow passes only `KEYSTORE_FILE` to the Gradle build step; signing passwords and aliases remain job environment inputs and are never written to `GITHUB_ENV` or logs. The temporary keystore is mode `600`, and keytool diagnostics are removed without printing their contents.
- Audio paths are remote and use CDN → Youdao → Android TextToSpeech fallback; audio files are not bundled.

### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| Missing/empty source | Converter exits non-zero with an actionable path error. |
| Missing `asp-data` or malformed segment | Fetcher exits non-zero; no partial DB is used. |
| HTML/JSON hash mismatch | Fetcher exits non-zero and reports expected vs actual hash without secrets. |
| Word count/group count mismatch | Converter/validator exits non-zero; Gradle does not run. |
| Missing required SQLite table, FTS parity mismatch, or FK violation | Validator exits non-zero. |
| Missing `dict.db` at runtime | App shows failure state; it must not invent sample records. |
| Missing signing secret | Release job fails before Gradle and names only the missing secret. |
| Invalid Base64/empty keystore/wrong alias or password | Release job fails; password values and keystore content are never printed. |
| Fork pull request | Verification may run, but release job cannot access or attempt to use secrets. |
| CDN audio unavailable | Try Youdao, then TextToSpeech; dictionary browsing remains available. |

### 5. Good/Base/Bad Cases

- **Good**: CI installs Python `brotli`, fetches the pinned source snapshot, generates exactly 49,213 words and 7 groups, validates the DB, then runs Gradle.
- **Base**: A local authorized export is converted with an explicit path and a supplied expected count; no network is used by the converter.
- **Bad**: A missing asset is silently replaced with fixture data, a hash mismatch is ignored, or a release falls back to debug signing.

### 6. Tests Required

- Fetch/converter tests assert the site alphabet, segment decoding, compact `g/d/p` expansion, exact word/group counts, and deterministic SQLite output from `tests/fixtures/minimal.json`.
- Validator tests assert required table presence, FTS row parity, and foreign-key failure detection.
- Android unit tests assert repository search behavior, empty/error/loading state transitions, and audio fallback order without requiring network access.
- GitHub Actions must run `testDebugUnitTest`, `:app:lintDebug`, source generation, asset validation, and release `apksigner` verification.
- Static security review asserts no Base64 preview, plaintext password, keystore, or generated `dict.db` is tracked.

### 7. Wrong vs Correct

#### Wrong

```bash
# Builds a runtime that may contain no dictionary and ignores source drift.
./gradlew :app:assembleRelease
```

```powershell
# Leaks signing material into terminal logs.
Write-Host "preview: $($keystoreBase64.Substring(0, 50))"
```

#### Correct

```bash
python scripts/fetch_isdc_export.py \
  --output "$RUNNER_TEMP/isdc-export.json" \
  --expected-html-sha256 "$ISDC_HTML_SHA256" \
  --expected-json-sha256 "$ISDC_JSON_SHA256"
python scripts/convert_dictionary.py "$RUNNER_TEMP/isdc-export.json" app/src/main/assets/dict.db \
  --expected-word-count 49213 --expected-groups 7
python scripts/validate_dictionary_asset.py app/src/main/assets/dict.db \
  --expected-word-count 49213 --expected-groups 7
./gradlew :app:assembleRelease
```

```powershell
# Report only non-sensitive metadata.
Write-Success "Base64 编码完成 (长度: $($keystoreBase64.Length), 值已隐藏)"
```
