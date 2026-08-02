# 发布 CDict APK

## Goal

通过 GitHub Actions 的手动发布入口构建签名 CDict APK，并上传 `cdict-release` artifact，供下载使用；不在本地执行 Android/Gradle 构建，不创建未经明确指定的版本 tag 或 GitHub Release。

## What I already know

* `.github/workflows/build.yml` 的 `workflow_dispatch` 有布尔输入 `publish`，必须设置为 `true` 才会运行 release job。
* release job 依赖 verify job，先生成并验证授权字典数据库，再执行签名构建。
* release job 只读取 `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD` 四个 secrets。
* release job 上传 artifact `cdict-release`，其中包含 `CDict.apk`、`CDict.aab` 和 `checksums.txt`。
* 只有 `v*` tag 触发时才执行 `softprops/action-gh-release` 创建 GitHub Release；手动 `publish=true` 不创建 GitHub Release，但会上传 Actions artifact。
* APK 由 Actions 使用 `apksigner` 和 `jarsigner` 验证，临时 keystore 位于 `$RUNNER_TEMP/cdict-release.jks` 并在结束时删除。
* 仓库禁止在本地运行 Android/Gradle 构建或测试。

## Requirements

* 使用 `gh workflow run` 触发 `CDict Android` workflow 的手动发布，输入 `publish=true`。
* 使用当前 `main` 分支提交 `a3cece9` 作为发布来源，避免发布未验证或未推送的代码。
* 不打印、读取或提交 signing secrets、keystore、Base64 内容或生成的 `dict.db`。
* 等待并检查 verify/release jobs 的结果。
* 成功后确认 `cdict-release` artifact 和 APK 文件名；失败时读取日志定位原因，不绕过签名或验证步骤。

## Acceptance Criteria

* [ ] 手动 workflow dispatch 成功创建并运行。
* [ ] verify job 成功完成数据生成、unit tests 和 lint。
* [ ] release job 成功验证 keystore、构建 signed APK/AAB，并通过 `apksigner` 与 `jarsigner`。
* [ ] `cdict-release` artifact 可在 Actions run 中下载，包含 `CDict.apk`、`CDict.aab`、`checksums.txt`。
* [ ] 不创建未经明确要求的 `v*` tag 或 GitHub Release。
* [ ] 不在本地执行 Android/Gradle build/test，任务结束时工作树保持干净。

## Definition of Done

* Actions 手动发布运行完成并记录 run ID、job 结果和 artifact 名称。
* 若发布失败，保留失败原因和下一步修复范围，不声称 APK 已发布。
* Trellis 任务收尾，工作树干净。

## Technical Approach

调用现有 workflow 的 `workflow_dispatch`，传入 `publish=true` 和 `ref=main`。由于 release job 的 GitHub Release 步骤仅在 `v*` tag 下运行，本次只生成 Actions artifact；若用户后续需要公开 GitHub Release，再单独确认版本号并创建 tag。

## Decision (ADR-lite)

**Context**: 用户需要 APK，但 workflow 区分 Actions artifact 与 tag-based GitHub Release。

**Decision**: 推荐手动 `publish=true`，先产出签名 APK artifact，不自动创建 tag 或 GitHub Release。

**Consequences**: APK 可从 Actions artifact 下载且不改变仓库版本历史；如果需要正式 GitHub Release，必须另行指定版本 tag。

## Out of Scope

* 不修改 Android 应用代码或 CI workflow。
* 不创建或推送 `v*` tag。
* 不创建 GitHub Release。
* 不在本地运行 Gradle、Android build 或 test。
* 不处理 signing secrets 配置本身；缺失或无效 secrets 由 workflow 报错。

## Follow-up Failure and Fix

* Run `30740239483` 的 verify job 成功，release job 成功验证四个 secrets 和 keystore。
* release 构建在 `:app:packageRelease` 失败，错误为 `SigningConfig "release" is missing required property "keyPassword"`。
* 修复要求：Kotlin/AGP signing config 必须对 `storeFile`、`storePassword`、`keyAlias`、`keyPassword` 做显式非空映射；workflow 只向 Gradle build step 传入 keystore 路径，不将密码和 alias 写入 `GITHUB_ENV`。
* release source conversion 必须传入 `--source-sha256 "$ISDC_JSON_SHA256"`；临时 keystore 使用 mode `600`，keytool 临时诊断文件不打印并在校验后删除。

## Technical Notes

* Workflow: `.github/workflows/build.yml`
* Workflow name: `CDict Android`
* Dispatch input: `publish=true`
* Release artifact: `cdict-release`
* APK path in artifact: `CDict.apk`
* Required secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
