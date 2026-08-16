# 已验证方法论手册

> 本文件整理在 NexAI 与 Project-Lumen 两个仓库的批量缺陷审查/修复任务中**完整验证过**的流程与技巧。凡标"已验证"的条目，均在此类任务中实际执行并产生过正确结果。

- 适用仓库：Android/Kotlin、Flutter/Dart 等一切由 GitHub Actions 负责构建的仓库
- 验证来源：`F:\Repositories\GitHub\NexAI`、`F:\Repositories\GitHub\Project-Lumen`（2026-08，`ui` 分支）
- 关联记忆：`feedback-nexai-workflow`、`win-git-push-credential-fix`
- 配套文档：`code-audit-methodology.md`（漏洞与错误代码审计——"审什么、找什么"；本文档是"怎么执行、怎么验证"）

---

## 一、核心原则（已逐条验证）

| # | 原则 | 原因 |
|---|---|---|
| 1 | **禁止本地构建/测试/安装依赖** | 本地机器性能不足；一切构建、测试、lint 只由 GitHub Actions 执行 |
| 2 | **并行 subagent 分组审查，各组可修改文件两两不相交** | 避免并发冲突；每组一个 subagent，只允许编辑指定文件 |
| 3 | **收到每个 subagent 结果后逐份核查 `git diff` 才采信** | 曾发生 agent 报告"完成"但实际未落盘任何修改；也发生过"改完但带编译错误" |
| 4 | **直接修改代码，不做 diff 预览** | 用户明确纠正过：要真实编辑，不是贴改动预览 |
| 5 | **修完自动生成 commit message 并 commit + push** | 仓库 CLAUDE.md 硬性要求；GPG 签名可省略 |
| 6 | **静态命令检查为主，最终正确性以 CI 为准** | 静态检查抓不到运行时/编译问题，GitHub Actions 才是唯一裁决者 |

---

## 二、完整工作流

### 阶段 0：准备
1. 先读仓库 `CLAUDE.md` / `AGENTS.md`，确认：工作分支、是否禁本地构建、commit/push 要求、特殊编码约定。
2. 确认 git 凭证可用（见 §三）。
3. `git status` 确认工作树干净、当前分支正确。
4. 建任务清单（TaskCreate），把"审查、分组修复、CI 验证"拆成任务跟踪。

### 阶段 1：并行审查
1. 按模块/包将仓库切成 N 个**不相交**文件组（如：核心架构组、数据层组、服务组、安全组、UI 组…）。
2. 对每组派一个并行 subagent，提示词写明：
   - 只允许编辑指定文件清单（两两不相交）
   - 禁止运行任何命令（构建/测试/lint）
   - 逐行读文件找缺陷（线程安全、主线程阻塞、资源泄漏、竞态、空指针、越界等）
   - 完成后报告：改了哪些文件、每个改动的原因
3. 每收到一个结果，**立刻 `git diff` 核查**：改动是否真实落盘、是否符合原因描述、有无语法/结构错误。

### 阶段 2：修复
1. 按缺陷类型合理分组，一次修一批，避免碎片化提交。
2. 直接 Edit 工具改代码（不是给预览）。
3. 每批改完自查 diff。
4. 可做静态检查兜底（非构建）：
   ```bash
   # 花括号平衡（kapt 曾因缺右括号挂掉）
   python -c "s=open('File.kt',encoding='utf-8').read(); print(s.count('{')==s.count('}'))"
   ```

### 阶段 3：CI 验证迭代（核心循环）
```bash
# 1. 提交并推送（凭证见 §三）
git add <file...>   # 显式列文件，不用 git add -A
git commit --no-gpg-sign -m "<conventional message>"
GIT_TERMINAL_PROMPT=0 git push origin <branch>

# 2. 找新运行的 run id
gh run list --branch <branch> --limit 1

# 3. 后台盯 CI（网络抖动用容错轮询，见 §四-4）
# 4. 失败时拉日志
gh run view <RUN_ID> --log-failed
# 5. 从日志提取真实错误（过滤 Gradle 插件内部噪音）
gh run view <RUN_ID> --log-failed | grep -aE "FAILED|Syntax error|Location:|ExceptionInInitializerError|BUILD "
# 6. 静态修复 → 重新 commit + push → 回到步骤 2，直到全绿
```
判定"全绿"：`gh run view <RUN_ID> --json conclusion` 返回 `success`，且所有 job 步骤均为 success。

### 阶段 4：收尾
1. 确认最终 commit 已推送、CI 全绿。
2. 把流程经验/新坑写回记忆（memory）与本文档（避免重复踩坑）。
3. 向用户输出简洁总结：提交列表 + 每批修复内容 + CI 迭代结论。

---

## 三、Git 与凭证（Windows 已验证）

- **push 必须**：`GIT_TERMINAL_PROMPT=0` + `gh auth setup-git`，否则 push 会挂起/超时。
- **commit 偶发卡死**：gpg 可能在非交互终端等 pinentry。仓库允许时直接 `git commit --no-gpg-sign`。
- **推荐显式列文件** `git add <file...>`，避免 `-A` 带入敏感文件。
- **严禁** `git rebase -i` / `git add -i` 等交互命令。

---

## 四、Windows 环境细节（已验证）

1. **Bash 噪音**：每条 Bash 命令开头出现
   `\377\376export': command not found` 是 `.bashrc` 的 BOM 所致，**无害，忽略**。
2. **乱码**：中文"乱码"不是文件损坏（文件是 UTF-8）。PowerShell 读取：
   ```powershell
   [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
   Get-Content -Encoding UTF8 <file>
   ```
3. **路径**：Read 工具要用 Windows 绝对路径；Bash 工具用 `/tmp/...` 可以，但 Read 读不到 `/tmp`。
4. **CI 轮询**：网络抖动会导致 `gh run watch` 因 TLS 超时退出（exit 0 但没真完成）。用容错轮询：
   ```bash
   while true; do
     r=$(gh run view <ID> --json status,conclusion 2>/dev/null)
     [ -z "$r" ] && sleep 30 && continue
     status=$(echo "$r" | sed -n 's/.*"status":"\([^"]*\)".*/\1/p')
     concl=$(echo "$r" | sed -n 's/.*"conclusion":"\([^"]*\)".*/\1/p')
     [ "$status" = "completed" ] && echo "DONE $concl" && break
     sleep 30
   done
   ```
   轮询间隔 30s+（GitHub API 限流）。

---

## 五、踩坑记录（全部真实发生）

1. **subagent 报告完成但未落盘**：0 字节 transcript，声称改完实际什么都没写 → 必须逐份核查 diff。
2. **subagent 改完带语法错误**：`FaceDistanceAnalyzer.kt` 资源清理改造漏掉右括号，kapt 报 `Syntax error: Missing '}'` → 只有 CI 能抓到。
3. **类加载期 Android 框架初始化**：`private val mainHandler = Handler(Looper.getMainLooper())` 在纯 JVM 单元测试里 `getMainLooper()` 返回 null → NPE → `ExceptionInInitializerError`，4 个测试全挂。修复：`by lazy`。
4. **UI 基类与主题不兼容**：`MainActivity` 从 `ComponentActivity` 改成 `AppCompatActivity`，但应用主题是平台 Material 主题（非 AppCompat），`onCreate` 抛异常 → 基线档案模拟器上"应用启动但从未可见、进程消失"。修复：回退基类。
5. **kapt 只报第一个语法错误**：一次运行可能只暴露一处错误，修完再推可能暴露下一处 → 耐心多轮迭代，或用花括号平衡静态检查兜底。
6. **基线档案失败常是模糊的**：`Target package ... failed to stay running after launch` 无 logcat 细节时，用"最后一个通过的 commit 与当前 commit 的差异"来锁定肇事改动（二分思维）。
7. **`--log-failed` 偶发抓空**：网络抖动时输出 0 行，重试即可。
8. **注释里的 `/*` 会开启未闭合块注释**：KDoc 中写 `audio/*` 之类措辞，Kotlin 把其中的 `/*` 当作块注释起始，外层 `/**` 永不闭合，编译器报 `Syntax error: Missing '}'` + `Unclosed comment`（错误往往在文件另一处），且静态花括号平衡检查查不出。写注释时避免裸 `/*`，用"audio 类型"等说法。
9. **在自身初始化表达式内引用变量是未解析引用**：`val media = MediaPlayer().apply { ... player === media ... }` 与 `val tts = TextToSpeech(ctx) { ... tts ... }` 中引用同名变量会报 `Unresolved reference 'media'/'tts'`。修复：apply 块内用 `this`（接收者即实例）；异步回调捕获自身用先声明后赋值的 `var`。

---

## 六、经验法则（已验证）

- **CI 是唯一裁判**：静态审查 + 人工读码都漏过编译/运行时缺陷，GitHub Actions 每次都抓到了。
- **用 diff 隔离肇事改动**：比较"最后一个成功 commit"与当前 commit，凡是启动路径/构建路径上的差异优先怀疑。
- **改动要最小化**：只修缺陷本身，不顺手重构；仓库规则明确禁写"超级文件"（大型聚合文件）。
- **修完每批就 push 验证**：不要攒一大堆改动再推，否则一个错误要重跑全量 CI 且难定位。
- **命名与提交规范**：Conventional Commits（`fix: ...`），message 说明"为什么"而非"改了什么"。
