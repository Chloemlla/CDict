# 修复 Room KSP 实体编译失败

## Goal

修复 GitHub Actions run `30739029864` 在 `:app:kspDebugKotlin` 阶段的 Room 编译失败，使 `WordEntity` 正确声明为 `words` 表实体，并让 `@Fts4(contentEntity = WordEntity::class)`、DAO 查询和 `DictionaryDatabase` 的实体注册保持一致。

## What I already know

* Actions 已通过 checkout、wrapper 权限、数据生成、Java 和 Gradle 初始化；失败不再是 wrapper 权限问题。
* KSP 报告 `WordEntity` 被 FTS content entity 引用但未标注 `@Entity`。
* 同一缺失注解导致 DAO 查询报 `no such table: words`，并无法将查询结果转换为 `WordEntity`。
* `DictionaryDatabase` 已将 `WordEntity` 注册为数据库实体，DAO 已查询 `words` 表，因此预期修复是恢复实体声明，而不是改写查询或数据库管线。
* 仓库禁止在本地执行 Android/Gradle build/test；实际验证必须由 GitHub Actions 执行。

## Requirements

* 为 `WordEntity` 添加 `@Entity(tableName = "words")`，保留现有字段、主键和默认值。
* 不改变 DAO 查询、FTS 表定义、数据库版本、数据转换脚本或 CI 权限逻辑。
* 更新 Android CI code-spec，记录 Room entity、DAO table name 和 FTS content entity 必须一致的约束，防止同类 KSP 回归。
* 通过 GitHub Actions 执行 unit tests 和 lint；不在本地运行 Gradle/Android 构建或测试。

## Acceptance Criteria

* [ ] `WordEntity` 编译为 Room `words` 实体，`id` 仍为主键。
* [ ] `@Fts4(contentEntity = WordEntity::class)` 的 KSP 校验不再报告缺少 `@Entity`。
* [ ] DAO 对 `words` 的查询和 `insertWords(List<WordEntity>)` 能通过 KSP 类型检查。
* [ ] GitHub Actions 的 `Run unit tests` 不再因该 KSP 错误失败，并继续执行 lint。
* [ ] 修复经过 trellis-check，提交并推送，工作树保持干净。

## Definition of Done

* 代码变更完成并由专用 implement/check subagents 审查。
* Android CI 执行实际 unit tests 和 lint 验证。
* 相关 Android code-spec 更新。
* 创建提交并推送到 `origin/main`。

## Technical Approach

在 `Entities.kt` 为 `WordEntity` 添加与 converter SQLite 表名一致的 `@Entity(tableName = "words")`。保持 `WordSearchEntity` 的 external content FTS 关系不变；Room 将据此生成 `words` schema、DAO adapters 和 FTS content linkage。

## Decision (ADR-lite)

**Context**: KSP 不能将 `WordEntity` 视为 Room entity，导致 FTS content entity 和全部 `words` DAO 查询同时失败。

**Decision**: 直接补充缺失的 `@Entity(tableName = "words")` 注解，不通过修改查询、移除 FTS 或绕过 KSP 来隐藏 schema 不一致。

**Consequences**: Room schema 与 CI 生成的 SQLite `words` 表保持一致，KSP 能生成 DAO/FTS 代码；数据库版本无需变化，因为这是恢复声明而非数据库结构变更。

## Out of Scope

* 不修改数据库表结构、字段、迁移版本或生产字典数据。
* 不修改 Gradle/Kotlin/Room 依赖版本。
* 不修改 GitHub Actions 权限、签名 secrets 或 release 流程。
* 不在本地执行 Android/Gradle build/test。

## Technical Notes

* Failure run: `30739029864`, task `:app:kspDebugKotlin`.
* Relevant files: `app/src/main/java/com/chloemlla/cdict/core/data/Entities.kt`, `DictionaryDao.kt`, `DictionaryDatabase.kt`.
* Contract: `.trellis/spec/android/data-ci-contracts.md`.
