# Android Development Specifications

This layer covers the greenfield Kotlin/Compose Android application under `app/` and its repository data tooling.

## Pre-Development Checklist

- [ ] Package and namespace remain `com.chloemlla.cdict`.
- [ ] Runtime dictionary data comes from the generated Room/SQLite `dict.db` asset; no runtime parsing of the source HTML payload.
- [ ] Source conversion validates the snapshot hash, group count, and exact word count before producing an asset.
- [ ] Android build and test commands run in GitHub Actions, not on the local device.
- [ ] Signing values remain GitHub Secrets and temporary keystore files remain under `$RUNNER_TEMP`.

## Quality Check

- [ ] Python converter/fetch/asset validator syntax checks pass.
- [ ] CI generates and validates `dict.db` before Gradle tasks.
- [ ] Push/PR jobs do not need write permissions or signing secrets.
- [ ] Release jobs validate all four signing secrets and verify the resulting APK signature.
- [ ] No keystore, Base64 payload, generated database, or plaintext password is tracked.

## Guides

- [Data and CI/CD Contracts](./data-ci-contracts.md)
