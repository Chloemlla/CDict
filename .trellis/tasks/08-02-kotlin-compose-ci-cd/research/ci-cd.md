# Research: CI/CD

## Reusable workflow shape

- Use Project-Lumen's `.github/workflows/build.yml` as the reference for Android setup, signing validation, build, APK verification, checksums, and artifacts.
- Generalize all Project-Lumen-specific names, modules, metadata, backend synchronization, and extra secrets.
- Run read-only tests/lint on pushes and pull requests; gate signed artifact creation and publishing to tags or explicit manual dispatch rather than every branch push.

## Signing

- Inject exactly `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.
- Check presence without printing secret values, strip base64 whitespace, decode into `$RUNNER_TEMP`, reject an empty/invalid keystore, and validate alias/password using `keytool`.
- Pass temporary signing properties to Gradle; do not commit credentials or keystore files. Clean up temporary files.
- Verify the resulting APK with `apksigner` where available and upload APK/AAB, checksums, and a build manifest as artifacts.

## References

- `F:\Repositories\GitHub\Project-Lumen\.github\workflows\build.yml`
- `F:\Repositories\GitHub\Project-Lumen\.github\workflows\release.yml`
- `F:\Repositories\GitHub\Project-Lumen\app/build.gradle.kts`
- `F:\Repositories\GitHub\CLens\.github\workflows\clens-android.yml`
