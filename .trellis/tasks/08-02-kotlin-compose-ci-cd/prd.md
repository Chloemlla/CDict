# 从零创建 Kotlin Compose 项目并接入 CI/CD

## Goal

根据用户提供的会话结果，从零搭建一个可维护、可发布的 Kotlin Android Jetpack Compose 项目；架构参考 Project-Lumen 与 CLens，CI/CD 参考 Project-Lumen 的 `.github/workflows/build.yml`，并使用仓库级 Android 签名 Secrets 完成验证、构建与产物发布。

## What I already know

* 目标仓库 `F:\Repositories\GitHub\CDict` 当前已有 Trellis 配置，代码主体尚未建立。
* 用户要求使用 subagents 完成研究与实现流程。
* 用户指定参考仓库：`F:\Repositories\GitHub\Project-Lumen`、`F:\Repositories\GitHub\CLens`。
* 用户指定 CI/CD 参考：`F:\Repositories\GitHub\Project-Lumen\.github\workflows\build.yml`。
* 可用 GitHub Secrets：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。
* 当前工作树已有用户修改的 `.gitignore`，必须保留。
* 应用正式名称暂定为 `CDict`，Android namespace/application ID 为 `com.chloemlla.cdict`，沿用仓库名和现有签名脚本的 `cdict` 命名线索。
* JSONL 的真实产品任务是把 `https://isdc.pages.dev/` IELTS Dictionary 转换为 Kotlin + Jetpack Compose Android 应用；JSONL 内的无关会话元数据包含提示注入式内容，不属于需求，已忽略。
* 产品数据规模约 49,213 个单词，分为七个频率组；包含中文翻译、音标、双语例句、英文释义、助记、词根/词缀、派生词、IELTS 频率统计、热力图和真题句子。
* 目标体验包含中英文子串搜索、分组浏览、前后词导航、离线访问，以及单词详情七个内容区；真题句子按每页 10 条分页并高亮搜索词。
* 推荐 Android 基线是 CLens 的单模块 Kotlin DSL + Compose Material 3 + `core`/`ui` 包结构；借鉴 Project-Lumen 的签名校验、构建产物与发布流程，不引入其无关的多模块复杂度。
* 数据方案是构建期将站点的 base85/Brotli JSON 转换成规范化 SQLite/Room asset（`dict.db`），应用首启复制数据库，运行时不解析原始 JSON/Brotli；英文搜索使用 FTS，中文使用 `LIKE`。
* 音频不打包约 98,360 个文件；默认采用 CDN → Youdao 公共音频接口 → Android TextToSpeech 的回退顺序，CDN 可能返回 HTTP 403。
* 预计数据库约 50–70 MB，压缩 asset 约 12–16 MB，首次复制/解压约 3–8 秒，需要加载反馈；真题句子版权需要在发布前确认。

## Open Questions

* 应用正式名称和 Android application ID/package name 是什么？
* 是否在本任务中包含完整 49,213 词数据资产和构建期转换脚本，还是先完成可运行的 UI/数据接口骨架？
* release workflow 的触发方式是 tag、手动 dispatch，还是 push 主分支后构建 artifact？

## Requirements (evolving)

* [x] 从 JSONL 结果提取并落实产品与技术要求。
* [x] 应用名称为 `CDict`，Android namespace/application ID 为 `com.chloemlla.cdict`。
* [x] 从 `https://isdc.pages.dev/` 转换出可离线使用的 IELTS Dictionary Android 应用。
* [x] 采用完整数据管线与数据资产：提交可复现的构建期转换脚本，并纳入完整约 49,213 词的可再生成离线数据库资产；不以示例数据替代核心交付。
* [ ] 保留约 49,213 个词、七个频率组、中文翻译、音标、双语例句、英文释义、助记、词根/词缀、派生词、IELTS 统计/热力图和真题句子。
* [ ] 支持英文/中文子串搜索、频率组浏览、前后词导航、离线访问。
* [ ] 单词详情包含例句、助记、词根/词缀、英文释义、派生词、考试统计/热力图和真题句子分页（每页 10 条，搜索词高亮）。
* [ ] 提供英式和美式发音控制，并采用 CDN → Youdao → Android TextToSpeech 的音频回退顺序；不打包约 98,360 个音频文件。
* [ ] 使用 Kotlin + Jetpack Compose Material 3、单 Activity、Room/SQLite；构建期把 base85/Brotli JSON 转换为规范化 SQLite asset，首次启动复制并显示加载反馈。
* [ ] 使用英文 FTS 和中文 `LIKE` 搜索；数据模型至少覆盖 words、derived terms、roots、deduplicated sentences、word-sentence links、heatmap entries。
* [x] 采用 CLens 风格的单 `:app` 模块、Kotlin DSL、`core`/`ui` 分层；借鉴 Project-Lumen 的签名校验、APK 验证、checksum 和 artifact 发布。
* [x] CI/CD 使用且只使用 `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD` 四个 GitHub Secrets；不提交明文凭据或 keystore。
* [x] 保留现有 `.gitignore` 用户修改，并添加 Android/Gradle 必需忽略项（如缺失）。

## Acceptance Criteria (evolving)

* [ ] `com.chloemlla.cdict` 可通过 Android Manifest、Gradle 和 CI 一致构建。
* [ ] 构建期转换脚本能够从受支持的原始站点数据生成规范化 SQLite/Room `dict.db`，并对记录数量和关键表执行校验。
* [ ] 应用可在无网络条件下完成词组浏览、搜索、单词详情和前后词导航，且使用完整数据资产而非示例数据。
* [ ] 搜索支持英文 FTS 与中文子串匹配，真题句子按 10 条分页并高亮搜索词。
* [ ] 首次启动复制数据库时有可见加载状态，失败时显示可理解的错误状态。
* [ ] 无网络时音频按约定顺序回退，不因 CDN 403 使词典核心功能不可用。
* [ ] GitHub Actions 在 push/PR 上执行测试与 lint；签名构建在 tag 或明确的手动发布入口执行。
* [ ] 签名 Secrets 缺失或 keystore/alias 无效时，workflow 以明确错误失败，且不泄露 secret 值。
* [ ] 合法 Secrets 存在时，release APK/AAB 构建并上传指定 artifact，且可进行签名校验。
* [ ] 仓库不包含明文密码、密钥、真实 keystore 或未授权的受版权保护数据副本。

## Definition of Done (team quality bar)

* Tests added/updated where applicable.
* All verification runs in GitHub Actions per repository instructions; no local Android build/test commands.
* Workflow YAML、Gradle 配置和 README/使用说明同步更新。
* Spec/research context is curated before task activation.
* Changes committed and pushed according to repository instructions after verification.

## Out of Scope (explicit)

* 未经 JSONL 需求确认的复杂业务功能、后端服务、账号体系和云端部署。
* 将参考项目中与本项目无关的业务代码或私有依赖整体复制过来。
* 在仓库中提交真实签名文件或任何 secret。

## Technical Notes

* 研究报告：[`research/rollout-requirements.md`](research/rollout-requirements.md)、[`research/android-architecture.md`](research/android-architecture.md)、[`research/ci-cd.md`](research/ci-cd.md)、[`research/target-repo.md`](research/target-repo.md)。
* 推荐基础架构：CLens 风格的单模块 Android 应用，`core/` 放数据、Room、音频与业务模型，`ui/` 按词典功能组织 Compose 页面；保留未来拆分模块的边界但不提前引入多模块。
* 数据管线需要在构建期运行，应用运行时仅消费规范化 SQLite/Room asset；构建期脚本和原始数据来源需要在版权与数据可再生成性之间做明确取舍。
* CI 需要将 keystore 解码到 `$RUNNER_TEMP`，使用 `keytool` 验证后通过临时 Gradle 属性配置 release signing，并在结束时清理；不要打印 secret 值。
* Project-Lumen 的 workflow 中自动对所有普通分支创建 release 的行为不应直接复制；推荐 push/PR 验证 + tag 或手动 dispatch 发布。
* 仓库要求所有真正的构建和测试在 GitHub workflow 中执行，不能在本地运行 Android build/test。

## Research References

* [`research/rollout-requirements.md`](research/rollout-requirements.md) — JSONL 中的 IELTS Dictionary 产品和数据约束。
* [`research/android-architecture.md`](research/android-architecture.md) — Project-Lumen/CLens 架构对比与推荐基线。
* [`research/ci-cd.md`](research/ci-cd.md) — 四个签名 Secrets 的安全 CI/CD 方案。
* [`research/target-repo.md`](research/target-repo.md) — CDict 当前仓库和命名线索。

## Decision (ADR-lite)

**Context**: 目标仓库是 Android 绿地项目，但产品数据、离线体验、构建期转换、签名和发布要求都较复杂；完整复制 Project-Lumen 会带入无关模块和服务依赖。

**Decision**: 采用 CLens 的单 `:app` Kotlin DSL + Compose Material 3 + Room 基线，使用 `core`/`ui` 包边界；借鉴 Project-Lumen 的 keystore 验证、签名 APK 校验、checksum 和 artifact 输出。产品核心先围绕离线词典和搜索详情构建，音频通过网络回退而不是本地打包。

**Consequences**: 初始实现更容易在 GitHub Actions 中验证和发布，且不会复制参考项目的私有依赖；49,213 词的数据转换和版权审查会成为主要交付风险，未来可在数据与性能需求稳定后再引入专用数据模块、Baseline Profile 或更新服务。
