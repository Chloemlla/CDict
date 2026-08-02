# Research: Android Kotlin Compose Architecture

## Findings

### Project-Lumen

- Root Kotlin DSL build with `:app`, library/sample modules, and optional baseline profile; plugin versions are centralized.
- App uses Compose, Material 3, Compose BOM, lifecycle/navigation, Room, KSP, and test/debug tooling.
- UI entrypoint delegates from `MainActivity` to an app-level composable; theme owns Material 3 colors, typography, shapes, light/dark and dynamic colors.
- CI decodes and validates a keystore, builds signed release artifacts, verifies APKs, emits checksums/manifests, and uploads release assets.

### CLens

- Single `:app` Android module with Kotlin DSL, Compose, Room, one production flavor, ABI/universal outputs, and Java/Kotlin 21.
- UI is grouped under `ui/`, storage/domain under `core/`; `MainActivity` hosts the root composable and ViewModel.
- Workflow separates verification from gated signed release publishing and documents required signing secrets.

## Recommendation

Use CLens as the minimal baseline: one Android app module, Kotlin DSL, Compose BOM/Material 3, `core` plus `ui` packages, Room and CI-only test/lint/release. Borrow Project-Lumen's keystore validation, APK verification, checksums, and manifests. Defer baseline profiles and extra modules until performance requirements justify them.

## References

- `F:\Repositories\GitHub\Project-Lumen\settings.gradle.kts`
- `F:\Repositories\GitHub\Project-Lumen\app/build.gradle.kts`
- `F:\Repositories\GitHub\Project-Lumen\.github\workflows\build.yml`
- `F:\Repositories\GitHub\CLens\android/settings.gradle.kts`
- `F:\Repositories\GitHub\CLens\android/app/build.gradle.kts`
- `F:\Repositories\GitHub\CLens\.github\workflows\clens-android.yml`
