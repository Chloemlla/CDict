# 修复 GitHub Actions 构建失败

## Goal

修复 GitHub Actions job `91469904335`（run `30737888955`）在 `Run unit tests` 步骤因 `./gradlew: Permission denied`（exit 126）导致的失败，并确保后续验证与 release job 能稳定调用 Gradle wrapper。

## What I already know

* 失败日志明确显示 `/home/runner/work/_temp/...sh: line 1: ./gradlew: Permission denied`。
* 根因是仓库中的 `gradlew` 没有 Git executable bit；脚本内容存在但 checkout 后不可执行。
* `.github/workflows/build.yml` 当前 unit test、lint、release 都直接调用 `./gradlew`。
* 仓库禁止本地 Android/Gradle 构建测试，验证必须在 GitHub Actions 中执行。

## Requirements

* [ ] 将 `gradlew` 以可执行模式提交（Git mode `100755`）。
* [ ] workflow 在调用 wrapper 前提供显式 `chmod +x ./gradlew` 兜底，避免 checkout/归档过程丢失权限。
* [ ] 保持 Windows `gradlew.bat` 和 Gradle wrapper jar/properties 完整。
* [ ] 不修改无关 Android、数据转换、签名和发布逻辑。

## Acceptance Criteria

* [ ] GitHub checkout 后 `./gradlew --version` 不再因权限返回 exit 126。
* [ ] unit test、lint 和 release 中所有 wrapper 调用都能执行。
* [ ] 不在本地运行 Android/Gradle 构建测试。
* [ ] 修复提交并推送后，新的 GitHub Actions run 至少通过 wrapper 启动阶段。

## Definition of Done

* 代码/文件模式修复完成并经过 diff 检查。
* 实现和检查上下文已配置。
* 提交并推送修复。
* 由 GitHub Actions 执行实际验证。

## Out of Scope

* 不重写 Gradle、Kotlin、Compose 或数据管线。
* 不修改 release secrets、keystore 或 GitHub 权限模型。
* 不在本地执行 Android/Gradle build/test。

## Technical Notes

* 失败 job: `https://github.com/Chloemlla/CDict/actions/runs/30737888955/job/91469904335`
* 失败命令：`./gradlew testDebugUnitTest --no-daemon --stacktrace`
* 直接修复：将 `gradlew` mode 设置为 executable；workflow 可在首次 Gradle 调用前执行 `chmod +x ./gradlew`。
