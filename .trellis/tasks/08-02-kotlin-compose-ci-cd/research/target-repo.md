# Research: Target repository

- CDict is greenfield for Android: no Gradle, Kotlin, Compose, manifest, package, or workflow files exist.
- Preserve the existing `.gitignore` change, including `.kotlin/`, `keystore_base64.txt`, and `cdict-release.jks`.
- Existing signing helper provides naming hints: keystore `cdict-release.jks`, alias `cdict`, and the four requested GitHub Secrets.
- No package ID or formal application name is currently declared.
- Existing `.trellis/spec/frontend` and `.trellis/spec/backend` describe web TypeScript stacks and should not be mechanically applied to Android.
