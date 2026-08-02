# CDict

CDict is an offline-first IELTS dictionary Android application built with Kotlin, Jetpack Compose Material 3, and Room. The package and application ID are `com.chloemlla.cdict`.

## Data pipeline

The repository deliberately does not contain a fabricated dictionary or an unauthorized copy of the source dataset. Generate the production asset from an authorized export supplied as a local file:

```bash
python scripts/convert_dictionary.py /path/to/authorized-export.json app/src/main/assets/dict.db \
  --expected-word-count 49213
```

The converter accepts JSON/JSONL, base85-wrapped JSON, and Brotli-compressed JSON. It creates normalized `words`, `derived_terms`, `roots`, `sentences`, `word_sentence_links`, and `heatmap_entries` tables plus the `word_search` English FTS table. It validates the resulting record count and prints table counts as JSON. It does not fetch the website, because source access and source schema must be explicit and reproducible.

The current public site returned HTTP 403 for requests without a browser User-Agent, but the authorized exporter uses `Mozilla/5.0` and can retrieve the page. Generate and validate the production asset with:

```bash
python -m pip install brotli==1.1.0
python scripts/fetch_isdc_export.py --output /tmp/isdc-export.json \
  --expected-html-sha256 c5cab0349b5fcf3e56904619a5f15c8923c7021a1f30c2c20639e2e597459c20 \
  --expected-json-sha256 f83cddde1f09a8c4a15e97a6502187c935ba7dbf028e1c45812abd912cebecef
python scripts/convert_dictionary.py /tmp/isdc-export.json app/src/main/assets/dict.db \
  --expected-word-count 49213 --expected-groups 7
python scripts/validate_dictionary_asset.py app/src/main/assets/dict.db \
  --expected-word-count 49213 --expected-groups 7
```

`dict.db` is generated in CI and intentionally ignored by Git, so the repository does not carry a 50–70 MB binary. The workflow uploads the generated database only as part of build/release outputs. A local Android build also requires this generation step first; it never silently substitutes sample or fabricated data.

## Android development

Open the repository in Android Studio, or use the included Gradle wrapper. The project uses one `:app` module with `core` data/audio packages and `ui` Compose packages. Room copies `dict.db` from the APK assets on first open and reports a user-visible loading/error state.

The app searches English through FTS and Chinese through substring `LIKE` matching. Pronunciation does not bundle audio: it falls back from the CDN to Youdao and then Android TextToSpeech.

## CI/CD

`.github/workflows/build.yml` runs debug unit tests and lint on pushes and pull requests. Signed release builds are gated to a manual workflow dispatch with `publish=true` or a `v*` tag. Release signing uses only these repository secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The workflow validates the decoded keystore with `keytool`, builds APK/AAB artifacts, verifies the APK with `apksigner`, creates SHA-256 checksums, uploads artifacts, and removes temporary signing material. No keystore or plaintext credentials belong in this repository.

## Verification policy

Repository policy requires Android builds and tests to run in GitHub Actions. Do not run Gradle or Android build/test commands on the local device.
