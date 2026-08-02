# Release workflow audit

- **Query**: Audit the planned Project-Lumen-derived GitHub Actions workflow for safe push/PR verification, tag/manual signed release, exactly four secrets, temporary keystore handling, Gradle signing properties, artifact output, and no secret leakage.
- **Scope**: mixed (repository/task requirements plus Project-Lumen and CLens reference workflows)
- **Date**: 2026-08-02

## Findings

### Files Found

| File Path | Description |
|---|---|
| `.trellis/tasks/08-02-kotlin-compose-ci-cd/prd.md:41-56` | Confirmed CI/CD requirements: four signing secrets, push/PR verification, tag/manual signed release, artifact, invalid-secret failure, and no plaintext credential/keystore leakage. |
| `.trellis/tasks/08-02-kotlin-compose-ci-cd/prd.md:77-79` | Explicit implementation notes: decode keystore under `$RUNNER_TEMP`, validate with `keytool`, use temporary Gradle signing properties, and do not copy Project-Lumen's all-branch automatic releases. |
| `.trellis/tasks/08-02-kotlin-compose-ci-cd/research/ci-cd.md:3-13` | Existing task research describing the expected signing and artifact shape. |
| `.github/workflows/` in CDict | Not found in the inspected target repository/worktree; there is no current workflow to validate against. |
| `.gitignore:25-29` | Existing user change ignores `.kotlin/`, `keystore_base64.txt`, and `cdict-release.jks`; this must remain intact. |
| `F:/Repositories/GitHub/Project-Lumen/.github/workflows/build.yml:2-12` | Project-Lumen combined push/PR/manual workflow; it grants `contents: write` and prepares signing on every trigger. |
| `F:/Repositories/GitHub/Project-Lumen/.github/workflows/build.yml:93-163` | Project-Lumen signing materialization, validation, Gradle-property injection, and logging pattern. |
| `F:/Repositories/GitHub/Project-Lumen/.github/workflows/build.yml:227-380` | APK inspection, release asset/checksum/manifest creation, GitHub Release, and artifact upload. |
| `F:/Repositories/GitHub/Project-Lumen/.github/workflows/release.yml:2-9` | Project-Lumen tag/manual release trigger and write permission shape. |
| `F:/Repositories/GitHub/Project-Lumen/.github/workflows/release.yml:114-202` | Project-Lumen release signing validation and release build pattern. |
| `F:/Repositories/GitHub/CLens/.github/workflows/clens-android.yml:1-30` | Safer reference separation: push/PR verification plus explicit manual publication input and read-only default permissions. |
| `F:/Repositories/GitHub/CLens/.github/workflows/clens-android.yml:121-215` | CLens release job gate, four-secret presence check, `$RUNNER_TEMP` keystore path, and Gradle environment-property injection. |
| `F:/Repositories/GitHub/CLens/.github/workflows/clens-android.yml:221-323` | Signed APK discovery, checksums/manifest, artifact upload, and GitHub Release publication. |
| `F:/Repositories/GitHub/CLens/android/app/build.gradle.kts:28-44` | Gradle reads `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` from environment first, then Gradle properties; all four are required for a release signing config. |
| `F:/Repositories/GitHub/CLens/android/app/build.gradle.kts:88-110` | Release signing config maps the four values to `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`; absent values leave release unsigned. |

### Required workflow shape checklist

Use this as the implementation acceptance checklist. A checked item means the requirement is present in the PRD/reference evidence; it is not a claim that an unimplemented CDict workflow already satisfies it.

#### 1. Push/PR verification

- [ ] Trigger on `push` and `pull_request` for the repository's intended branches and relevant Android/workflow/data-pipeline paths.
- [ ] Verification runs for pull requests without exposing any signing secret. Fork PRs must remain buildable because the verification job does not depend on repository secrets.
- [ ] Verification job runs unit tests and Android lint, and may assemble an **unsigned/debug or otherwise non-secret verification build** if the project requires release-variant compilation.
- [ ] Verification job uses `permissions: contents: read` (and only other permissions actually required). Do not grant `contents: write` to the normal push/PR job.
- [ ] Verification uploads reports with `if: always()` and `if-no-files-found: error` where a required report/artifact is expected. Report paths must not use broad globs that could include `.jks`, `gradle.properties`, or generated secret material.
- [ ] Verification does not create a GitHub Release, push a tag, or upload a signed artifact.
- [ ] The workflow must not copy Project-Lumen's `push.branches: ["**"]` plus unconditional signed release behavior from `build.yml:2-12, 364-382`.

#### 2. Tag/manual signed release

- [ ] Signed release is a separate job or workflow gated by a tag pattern such as `push.tags: ["v*"]` and/or an explicit `workflow_dispatch` release entry point.
- [ ] Release depends on successful verification (`needs: verify`) when verification is in the same workflow; a failed test/lint job must prevent signed publication.
- [ ] Manual dispatch has an explicit release intent/input or is otherwise unambiguously a release operation. It must not silently turn ordinary branch pushes into releases.
- [ ] Tag builds use the checked-out tag/ref for the release version and publication metadata; the uploaded artifact and GitHub Release must not claim a different tag.
- [ ] Release job has `contents: write` only at the job level when it actually creates/updates a GitHub Release. A manual build that only uploads an Actions artifact can remain `contents: read`.
- [ ] Only the release job receives the four signing secrets. The verification job and all PR paths must not reference `${{ secrets.KEYSTORE_BASE64 }}`, `${{ secrets.KEYSTORE_PASSWORD }}`, `${{ secrets.KEY_ALIAS }}`, or `${{ secrets.KEY_PASSWORD }}`.
- [ ] If manual dispatch is allowed to build without creating a GitHub Release, its behavior is explicit: signed Actions artifact is produced, while GitHub Release publication is limited to a real tag. If manual dispatch is intended to publish, it must use a deterministic tag and collision policy.

#### 3. Exactly four signing secrets

The only repository signing secret names permitted by the PRD are:

1. `KEYSTORE_BASE64`
2. `KEYSTORE_PASSWORD`
3. `KEY_ALIAS`
4. `KEY_PASSWORD`

- [ ] Workflow YAML contains no additional repository secret references for Android signing or release metadata. In particular, do not port Project-Lumen's API, telemetry, request-signing, certificate-pinning, admin, or backend secrets.
- [ ] `GITHUB_TOKEN` is treated as GitHub's automatically supplied workflow token rather than an additional repository signing secret; its permissions must still be explicit and minimal.
- [ ] The four values are passed to shell steps through `env`, not interpolated into `run:` script text. This keeps shell quoting and command rendering from exposing values.
- [ ] Presence checks report only secret names and generic missing/incomplete errors. They do not print values, lengths, aliases, passwords, base64 text, or command lines containing secrets.
- [ ] Empty, whitespace-only, or malformed values fail the release before any release artifact is published.
- [ ] Do not add a secret for certificate SHA-256, application ID, version, repository name, keystore path, or release URL; those are derived/public configuration values and are not part of the four-secret contract.

#### 4. Temporary keystore handling

- [ ] Decode `KEYSTORE_BASE64` only after checking all four values are non-empty.
- [ ] Strip transport whitespace from the base64 input before decoding, but do not log the normalized value.
- [ ] Materialize the decoded keystore below `$RUNNER_TEMP`, for example `$RUNNER_TEMP/cdict-release.jks`; do not write it at `$GITHUB_WORKSPACE`, in the repository, or in an upload directory.
- [ ] Use a strict decode/failure check and verify the output is non-empty. A decoder that accepts invalid trailing content must not be the sole validation.
- [ ] Run `keytool` against the temporary file with the store password and expected alias. Fail with a generic error if the keystore, store password, or alias is invalid.
- [ ] Do not print `KEY_ALIAS`. It is one of the four configured secrets even though it is commonly non-password metadata. Do not dump `keytool` output containing the alias; redirect it and report only a generic success/failure or non-secret certificate fingerprint if needed.
- [ ] Ensure file permissions are restrictive (`chmod 600` where applicable).
- [ ] Do not upload the workspace or `$RUNNER_TEMP` wholesale. Artifact paths must be explicitly limited to APK/AAB, checksums, manifest, and intended reports.
- [ ] Add unconditional cleanup (`if: always()`) that deletes the decoded keystore, temporary Gradle properties, and any copied signing files. Cleanup must still run after decode, validation, Gradle, or artifact-preparation failure.
- [ ] Existing `.gitignore` entries remain, but ignore rules are only a defense-in-depth measure; they do not replace runtime cleanup or prevent a workflow artifact from containing the file.

#### 5. Gradle signing properties

- [ ] The Gradle script reads the same four logical values used by the workflow. The CLens reference uses `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` (`android/app/build.gradle.kts:28-44`), while Project-Lumen uses project-specific `PROJECT_LUMEN_*` properties (`app/build.gradle.kts:8-18`). CDict must use one consistent CDict naming scheme in both files.
- [ ] `KEYSTORE_FILE` points to the `$RUNNER_TEMP` keystore, not a repository-relative path.
- [ ] Release signing is configured only when all four values are present. A release build must not silently fall back to debug signing when the release job is intended to publish.
- [ ] The release job checks that the temporary signing properties are present before `assembleRelease`/`bundleRelease`; this should be a presence check with password values redacted.
- [ ] Signing properties are temporary. If written to a project `gradle.properties`, remove them first, append only during the release job, and delete/truncate the file in unconditional cleanup. Prefer environment-backed properties or a temporary Gradle property source if the build script supports it.
- [ ] Do not pass passwords as visible command-line arguments, write them to `GITHUB_ENV`/`GITHUB_OUTPUT`, include them in build manifests, or expose them through `BuildConfig`, resources, APK assets, or release notes.
- [ ] Ensure Gradle/AGP output, exception handling, and optional build scans do not echo signing values. Do not run diagnostic `cat gradle.properties`; if properties must be inspected, print only property names and a fixed `<redacted>` marker.
- [ ] The workflow verifies the **signed** output after Gradle completes. Merely finding `*-release.apk` is insufficient because a release variant can be unsigned if signing configuration was omitted.

#### 6. Artifact output

- [ ] Locate the exact CDict release output path and fail if the expected APK/AAB is absent. Do not use a glob that accidentally selects `*-unsigned.apk`.
- [ ] At minimum upload the signed release APK or AAB required by the product, a SHA-256 checksum file, and any required release manifest. If both APK and AAB are built, name and validate them separately.
- [ ] Run `apksigner verify --print-certs --verbose` on every APK intended for distribution when `apksigner` is available; fail on verification failure. For an AAB, use the appropriate bundle/signing validation and do not claim APK verification covers the bundle.
- [ ] Verify output is signed with the expected alias/certificate without printing passwords or the alias secret. A certificate fingerprint is public release metadata and can be recorded if the product requires it.
- [ ] Generate checksums only after copying the final signed files into a dedicated release-assets directory. Ensure `checksums.txt` names exactly the files uploaded.
- [ ] Use `actions/upload-artifact` with a fixed artifact name, explicit paths, `if-no-files-found: error`, and retention appropriate to releases. Do not upload `**/*` from the workspace.
- [ ] GitHub Release publication, if used, receives only the prepared release assets. It must not include the keystore, temporary Gradle properties, logs containing secret values, or unrelated workspace files.

#### 7. No-secret-leakage controls

- [ ] Never echo or print any of the four secret values. Avoid printing `KEY_ALIAS` and avoid printing base64 length or other unnecessary derived data because it adds metadata without helping release correctness.
- [ ] Never place secrets in a heredoc, generated JSON/manifest, URL, action input, artifact name, commit message, tag name, or output variable.
- [ ] Do not use `set -x` or shell tracing in signing steps. If global tracing is enabled by a wrapper, explicitly disable it around secret handling.
- [ ] Do not `cat` the keystore, signing properties, shell environment, or Gradle debug output. Do not run `env`, `printenv`, or equivalent in the release job.
- [ ] Keep secret-bearing steps out of PR execution. A workflow condition that merely checks a secret in a PR can still behave unexpectedly for forked PRs; isolate secrets in a release job gated by tag/manual conditions.
- [ ] Do not use `pull_request_target` to obtain secrets for code from an untrusted PR checkout.
- [ ] Review every artifact path and every release file glob for `.jks`, `.properties`, `.env`, shell transcripts, and generated reports before enabling publication.
- [ ] Add an unconditional cleanup step and ensure it does not itself print the path contents or fail before removing files.

## Concrete failure cases

| ID | Failure case | Expected workflow result | Evidence / detection point |
|---|---|---|---|
| F1 | `KEYSTORE_BASE64` is missing on a tag/manual release. | Release fails before decode/build; error identifies the missing secret name only; no artifact or GitHub Release is published. | Four-secret presence check; PRD acceptance at `prd.md:53-56`. |
| F2 | One of the three text secrets is empty or whitespace-only. | Release fails before Gradle; no values are printed. | Normalize/check `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` before writing properties. |
| F3 | Base64 contains invalid data or decodes to a zero-byte file. | Decode step fails and cleanup runs; no release asset is uploaded. | Strict decode status plus non-empty-file check. |
| F4 | Base64 decodes to a non-empty file that is not a keystore. | `keytool` validation fails with a generic message; raw keytool output and secret values are not emitted. | `keytool -list`/equivalent validation. |
| F5 | Store password is wrong. | Keystore validation fails before Gradle; no release publication. | `keytool` store validation. |
| F6 | `KEY_ALIAS` is wrong or absent from the keystore. | Keystore validation fails before Gradle; the error does not quote the alias value. | Alias validation; do not copy Project-Lumen's `echo ... '$KEY_ALIAS'` pattern at `build.yml:140-147`. |
| F7 | `KEY_PASSWORD` is wrong while store password and alias are valid. | The workflow must fail during an explicit key-password/signing validation or the signed Gradle build; it must not upload an unsigned APK. | Gradle signing task plus post-build `apksigner` verification. A store/alias-only `keytool -list` check does not prove the private-key password is correct. |
| F8 | Release signing properties are absent or use names different from the Gradle script. | Build fails before `assembleRelease`, rather than silently creating a debug-signed or unsigned release. | Pre-build property presence check and Gradle release signing config. |
| F9 | Keystore is written to `$GITHUB_WORKSPACE` or repository root. | This is a workflow nonconformance; implementation must be rejected/audit failure even if the build succeeds. | Compare path against `$RUNNER_TEMP`; Project-Lumen uses workspace path at `release.yml:151-155`, whereas PRD requires `$RUNNER_TEMP`. |
| F10 | Temporary keystore/properties remain after a failed Gradle build. | Cleanup runs with `if: always()` and removes all signing material. | Cleanup step tested after failures in decode, validation, Gradle, and artifact preparation. |
| F11 | A broad artifact glob includes the keystore or temporary `gradle.properties`. | Artifact preparation must fail review or be corrected before publication; no secret-bearing artifact may be uploaded. | Inspect every `upload-artifact.path`, GitHub Release `files`, and copied asset directory. |
| F12 | A normal push to a feature branch occurs. | Push/PR verification runs; no signing secret is requested, no signed artifact is published, and no GitHub Release is created. | Release condition excludes ordinary branch pushes. |
| F13 | A pull request, including a fork PR, is opened. | Tests/lint run with read-only permissions and no signing secrets; PR cannot publish a release. | `pull_request` verification job isolation. |
| F14 | Verification test or lint fails before release. | Signed release job is skipped through `needs: verify`; no release artifact/publication. | Job dependency and result condition. |
| F15 | A non-version tag is pushed, for example `docs-2026`. | No signed-release workflow runs if the contract is `v*`; if all tags are accepted, the version/publication policy must explicitly define the result. | Tag filter and version parsing. |
| F16 | A `v1.2.3` tag is checked out but workflow derives version from a different file/run number. | Release must fail or produce metadata matching the tag; it must not publish an artifact with a mismatched version/tag. | Tag-aware version step and manifest/release-name consistency. |
| F17 | Manual release dispatch is run from a branch without explicit release intent. | Workflow either fails validation or performs the documented manual signed-artifact behavior; it must not silently create a release with a synthetic or colliding tag. | `workflow_dispatch` input/condition and publication gate. |
| F18 | `assembleRelease` succeeds but output is `app-release-unsigned.apk`. | Asset preparation fails; unsigned output is never renamed as a release APK or uploaded. | Exact filename selection plus `apksigner verify`. |
| F19 | APK exists but `apksigner verify` fails. | Release fails before artifact/GitHub Release upload. | Post-build signature verification. |
| F20 | APK is signed with debug certificate because release signing was unavailable. | Signature check against the expected release certificate/identity fails; no release publication. | Release signing must not fall back to debug; inspect certificate. |
| F21 | Action or shell logs print `$KEY_ALIAS`, a password, or normalized base64. | This is a secret-leakage failure even if GitHub masking later redacts some output; remove the logging and rotate affected credentials if it occurred. | Review shell commands, `keytool` output, Gradle logs, and action inputs. |
| F22 | A secret is passed in a `run:` expression rather than through `env`. | Workflow is an audit failure because rendered command text can expose the value; convert to environment-based handling. | YAML review of `${{ secrets.* }}` placement. |
| F23 | Workflow adds Project-Lumen API/admin/telemetry secrets during porting. | Fails the exactly-four-secret contract; remove unrelated integrations and secret references. | Compare all `${{ secrets.* }}` names with the four allowed names. |
| F24 | Cleanup step is skipped because its preceding step failed. | Workflow is nonconformant; cleanup must use `if: always()` (or an equivalent job-level finally mechanism). | Failure injection at each signing stage. |

## Code/reference patterns

### Project-Lumen patterns that are relevant but must be adapted

- The reference separates tag/manual release triggers in `release.yml:2-6` and performs release asset preparation, checksums, and upload at `release.yml:207-309`.
- Its signing flow checks and normalizes the four named values and validates the decoded file with `keytool` (`release.yml:114-171`).
- Its release build checks for all four Gradle properties before `assembleRelease` (`release.yml:190-203`).
- Its APK inspection uses `apksigner`, then creates checksums and a manifest (`build.yml:227-362`).
- The Project-Lumen implementation is not safe to copy verbatim for this task: it writes the keystore under `${{ github.workspace }}` (`release.yml:151-155`), logs the alias (`release.yml:128-134` and `build.yml:107-113,140-147`), does not show an unconditional cleanup step, and its broad combined workflow grants `contents: write` (`build.yml:11-12`) while building/signing on ordinary pushes.
- It also injects many Project-Lumen-specific secrets into the build (`build.yml:199-208` and `release.yml:180-189`), which conflicts with CDict's exactly-four-secret contract.

### CLens patterns that align more closely with CDict

- CLens keeps default verification permissions read-only and gates its release job on `verify`, non-PR, main-branch/publish conditions (`clens-android.yml:27-30,121-132`). CDict should use the equivalent tag/manual condition rather than CLens's main-branch policy because the CDict PRD specifies tag/manual release.
- CLens validates exactly the four named signing secrets without printing values (`clens-android.yml:161-180`).
- CLens materializes its keystore under `$RUNNER_TEMP` (`clens-android.yml:182-193`) and passes the temporary path plus the three text values to Gradle through environment variables (`clens-android.yml:204-215`).
- CLens prepares a dedicated release-assets directory, rejects a missing aggregated APK, generates `checksums.txt` and a manifest, and uploads only that directory (`clens-android.yml:221-309`). It still needs an explicit cleanup review when adapted to CDict.

## Related Specs

- `.trellis/tasks/08-02-kotlin-compose-ci-cd/prd.md` — authoritative task requirements and acceptance criteria for CI/CD.
- `.trellis/tasks/08-02-kotlin-compose-ci-cd/research/ci-cd.md` — existing task-level signing/artifact research.
- `.trellis/tasks/08-02-kotlin-compose-ci-cd/research/target-repo.md` — greenfield repository and `.gitignore` constraints.
- `.trellis/spec/guides/cross-layer-thinking-guide.md` — relevant for preserving data flow from workflow secrets to Gradle signing and final artifact verification.

## External References

- [GitHub Actions workflow syntax](https://docs.github.com/en/actions/writing-workflows/workflow-syntax-for-github-actions) — trigger filters, job conditions, permissions, environments, and workflow dispatch inputs.
- [GitHub Actions security hardening](https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions) — secret handling, untrusted pull requests, least-privilege permissions, and action hardening.
- [GitHub Actions secrets](https://docs.github.com/en/actions/security-for-github-actions/security-guides/using-secrets-in-github-actions) — repository secret availability and fork pull-request behavior.
- [GitHub artifact attestations / artifact upload guidance](https://docs.github.com/en/actions/using-workflows/storing-workflow-data-as-artifacts) — explicit artifact paths, retention, and workflow output handling.
- [Android `apksigner` documentation](https://developer.android.com/tools/apksigner) — APK signature verification and certificate inspection.
- [Android app signing](https://developer.android.com/studio/publish/app-signing) — release signing concepts and keystore/key credentials.
- [Gradle project properties](https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties) — environment/system/project property sources used to feed signing configuration.

## Caveats / Not Found

- No CDict `.github/workflows/` file or Gradle project exists in the inspected target snapshot, so this report audits requirements and reference patterns rather than a concrete CDict YAML implementation.
- The task command reported no active task source in this worktree (`Current task: (none)`), although the requested task directory and PRD exist; the report was written to the explicit task path supplied by the caller.
- No local Android build or test command was run. Repository instructions require actual builds/tests to execute in GitHub Actions.
- The audit cannot prove the final CDict Gradle output filename, APK/AAB choice, or certificate identity until the application and workflow files exist. The implementation must make those paths explicit and fail closed when they are absent.
- GitHub automatically masks configured secret values in many log contexts, but masking is not a substitute for avoiding logging, temporary-file cleanup, least privilege, and restricted artifact globs.
