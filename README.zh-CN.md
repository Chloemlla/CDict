<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="CDict 启动图标"/>
</p>

<p align="center">
  <strong>CDict</strong> · 一款离线优先的雅思词典 Android 应用
</p>

<p align="center">
  简体中文 · <a href="README.md">English</a>
</p>

<p align="center">
  <strong>Kotlin</strong> · <strong>Jetpack Compose (Material 3)</strong> · <strong>Room</strong> · <strong>SQLite FTS5</strong>
</p>

**CDict** 是一款**离线优先**的雅思词典 Android 应用,基于 Kotlin、Jetpack Compose(Material 3)与 Room 构建,包名与应用 ID 均为 `com.chloemlla.cdict`。

> **当前版本 `1.0.1`**(versionCode `2`) · `minSdk 26` / `targetSdk 37` / `compileSdk 37` · Compose BOM `2024.12.01` · Room `2.8.4` · Kotlin/JVM 21
>
> 词典核心**完全离线可用**;仅申请 `INTERNET` 权限,用于可选的在线翻译与发音。

---

## 目录

- [概览与亮点](#概览与亮点)
- [界面结构](#界面结构)
- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [技术架构](#技术架构)
- [快速上手](#快速上手)
- [数据管线](#数据管线)
- [CI / CD](#ci--cd)
- [版本历史](#版本历史)
- [验证策略](#验证策略)
- [开源协议](#开源协议)

---

## 概览与亮点

| | |
|---|---|
| 🗂 **离线优先** | 内置 49,213 词条,分属 7 个 IELTS 频率组;首次启动复制到 Room 数据库,完全离线。 |
| 🔍 **智能搜索** | 英文全文搜索(SQLite FTS5,覆盖单词 / 翻译 / 释义)、中文子串搜索,以及 **Levenshtein 拼写纠错**(“你是不是想找……”)。 |
| 🔊 **发音** | 三级回退(vivo 合成 → 有道 → 系统 TTS),配合磁盘音频缓存,无需打包任何音频文件。 |
| 🌐 **在线翻译** | 内置 vivo 网关注入的翻译引擎,带**三层缓存**。 |
| 🧠 **背词模式** | 按 IELTS 频率加权的自适应间隔重复,含干扰项引擎与次日四选一复习。 |
| 🤖 **AI 语感标注** | AI 逐词生成的语感标注——感情色彩、语体、精细语意、避坑提示,以及可朗读、自动译文的常见搭配。 |
| 📅 **每日推荐** | 完全离线的每日探索流,按 **5:3:2** 配比混合核心新词 / 词根拓展 / 高频过渡词(复习留在背词页)。 |
| 🔒 **隐私** | 数据完全本地;仅申请 `INTERNET`,不收集、不上传任何个人信息。 |
| 🛡 **崩溃上报** | 集成 **Lumen Crash SDK** 采集,并内置 Compose 报告页。 |

---

## 界面结构

底部导航共四个标签页(大屏自动切换为侧边导航栏):

1. **背词 · Study** —— 自适应间隔重复背词模式
2. **词典 · Dictionary** —— 离线词典与词详情
3. **翻译 · Translation** —— 在线翻译
4. **推荐 · Recommendation** —— 每日推荐流

App 启动默认打开**词典**标签页。导航是响应式的:窄窗口用底部导航栏,平板 / 大屏用侧边导航栏;支持 Android 系统返回 / 手势导航与词详情滑页切换。外壳维护真实的访问历史栈,系统返回回到你真正来源的标签页(含跨标签跳词,如 推荐 → 词详情 → 返回推荐流),并通过 saveable state holder 保存每个标签页的滚动 / 搜索 / 详情状态。

---

## 功能特性

### 🗂 离线词典

| 能力 | 说明 |
|---|---|
| 词库规模 | 49,213 词条,分属 7 个 IELTS 频率组 |
| 英文搜索 | 英文全文搜索(SQLite FTS5),覆盖单词 / 翻译 / 释义 |
| 中文搜索 | 中文子串搜索(`LIKE` 匹配单词与翻译) |
| 排序 | 结果按 **精确 > 前缀 > 频率** 重排,核心 IELTS 词优先浮现 |
| 拼写纠错 | 无结果时给出 **Levenshtein** 编辑距离(≤ 2)内的“你是不是想找……”建议 |
| 排序方式 | 词表支持切换排序(按频率 / 按字母 / 字母倒序) |
| 无限滚动 | 词表分页加载,浏览流畅 |
| 离线存储 | Room 首次启动将内置 `dict.db` 复制到应用数据库 |

### 🧩 词详情页

点击词条进入详情页,展示:

- **音标**:英式 `phoneticUk` + 美式 `phoneticUs`
- **释义与翻译**:英文释义 `definition` + 中文翻译 `translation`
- **助记词** `mnemonic`
- **频率**:频率组 `frequencyGroup` + IELTS 频率 `frequency`
- **词根** `roots` 及其含义
- **派生词** `derivedTerms`
- **历年出现频率热力图** `heatmap`:各时间段出现得分
- **真题句子** `sentences`:英文原文 + 中文翻译,每词最多 10 条
- **AI 语感标注**:感情色彩徽标(`emotionColor`)+ 语体标签(`register`)、精细语意(`nuanceDescription`)、高亮避坑提示(`usageWarning`),以及**常见搭配**(`collocations`)——每条搭配自动翻译为中文并可朗读。词详情页与背词卡片共用。
- **英音 / 美音发音按钮**

### 🔊 发音

词详情页提供 **英音 / 美音** 发音按钮。发音由内置的 **vivo 语音合成**客户端默认生成,按三级顺序回退,无需打包任何音频文件:

```
Youdao 静态发音 (dict.youdao.com/dictvoice;整句/词)
  → vivo 语音合成 (POST https://vivotrans.vivo.com.cn/fy/tts)
  → Android 系统 TextToSpeech
```

任一级失败(超时 / 非 2xx / 音频损坏 / 网络不可用)自动降级到下一级;发音不可用时词典浏览与离线搜索完全不受影响。有道 `dictvoice` 只稳定读单词,遇到整句时先整句试一次,失败则按空格**拆词逐词读**,再失败才回退 vivo → 系统 TTS。

`VivoTtsClient`(逆向 `com.vivo.translator` 的语音合成链路):

- 请求体为 **JSON**(非表单),`auf=audio/L16;rate=16000`;有返回 MP3 也有返回无容器 PCM 的情况,播放前会识别格式并给 PCM 补 WAV 头;`{"errorResult":{...}}` 会被识别为明确错误而非当作音频。
- **HMAC-SHA256** 签名 `Sign.sign`(`hmacSha256Hex`),请求头含 `product/model/sysVer/appVer` 客户端指纹。
- 使用**独立于翻译引擎**的凭证 `appId=1336541186` / `appKey=9925f42b…`;英音 `langType=en-GBR`、美音 `en-USA`。

**音频缓存。** 发音会缓存到磁盘,重复查询即时返回、且离线更友好:

- 文件以 **`<accent>:<text>` 的 MD5** 为键,生成稳定且简短的文件名。
- 采用 **50 MB LRU** 预算,超出时按最近最少使用淘汰。
- 词详情页会对发音进行**预取**。

> ⚠️ **免责声明**:vivo 语音合成为私有接口,`appId` / `appKey` 为客户端常量,可能随时失效或变更。发音是便利功能,并非核心依赖;词典核心功能完全离线。

### 🌐 在线翻译

**翻译**标签页内置在线翻译引擎:

- 基于 **vivo 翻译网关**(逆向 `com.vivo.translator` 4.5.9.0,与 `fanyiji-rev/translate.js` 同源)。
- **免密钥直连**:V2 无签名通道 `POST https://vivotrans.vivo.com/translation/query`。
- **语言方向**:自动 → 中文、自动 → 英文、中文 → 英文、英文 → 中文(即 vivo 全方向集)。
- **批量翻译**:多行文本按 `\n` 合并为单次请求,响应逐行拆回。
- **响应附加信息**:源 / 目标语言回显、音标。
- **短语朗读**:英文内容可实时朗读(vivo 合成),结果旁带朗读图标。

**三层缓存。** 翻译结果按 内存 LRU → Room 磁盘缓存 → 网络 三层提供服务;除 Room 持久化的 `RoomTranslationCache` 外,还有自定义内存 `MemoryLruCache<string, TranslationResult>`。重复翻译即时返回,且“首次在线后离线可用”。

> ⚠️ **免责声明**:该网关为私有接口,凭证为客户端常量,可能随时失效或变更。翻译是便利功能,并非应用的硬性依赖;词典核心功能完全离线。

### 🧠 背词模式(间隔重复)

**背词**标签页是自适应间隔重复学习模式:

- **次日四选一复习**:学过的词明天回来以选择题复习。
- **自适应间隔重复(ASR)**:复习间隔沿基础阶梯(如 `1 → 3 → 7 → 15 → 30` 天)按你的作答动态调整。
- **按频率加权**:间隔按词的 IELTS 频率带缩放——高频(核心)词间隔**更短**以保持专注,生僻词则拉长。
- **干扰项引擎**:复习题优先从**同一频率组**取干扰项,退而再取 ±1 带,让选项刻意具有难度。
- **错误归因与重试**:答错会在本次会话内立即重新入队并给出反馈。
- **答对音效**:答对复习题播放短暂成功音。
- **自适应每日目标**:以独立的 `StudyDatabase` 持久化 `StudyStatus` 记忆状态机。
- **立即测试今日所学**:学习页与小结页提供「立即测试今日所学」入口,按需把今日新学词跑一遍复习引擎;答对时与按时复习完全一样推进间隔阶梯——把计划提前,而非免费放行。

### 📅 每日推荐

**推荐**标签页构建**完全离线**的每日探索流(方案A 定位分离:本页只做轻度阅读 / 预热,复习权交还背词页):

- 按 **5:3:2** 配比混合三种词池:
  1. **核心新词**(50%)——目标雅思频率组(组 1..3)的未学新词,高频优先,带完整上下文。
  2. **词根拓展**(30%)——与你已学词**共享词根**派生出的新词,从熟悉词汇向外延伸;词根数据稀疏时退回目标邻域抽样(组 2..4)。
  3. **简单过渡**(20%)——未学的绝对高频词(组 1),给流式心流体验。
- 冷启动(整库未学)退回「组 1 最常见词」,3 秒内即可开刷;任一词池不足时用核心新词 / 全域兜底,保证整流恰好等于 `goal`。
- 每日目标可配置:调高会追加新的 5:3:2 切片,调低则从队尾裁剪。
- 进度按天持久化,跨启动保持稳定。

### 🔒 权限与隐私

- 仅申请 **`INTERNET`** 权限,用于在线翻译与发音(vivo 合成 / 有道)。
- 词典数据完全本地;**不收集、不上传任何个人信息**。

---

## 技术栈

| 层次 | 选型 |
|---|---|
| 语言 | Kotlin / JVM 21 |
| UI | Jetpack Compose (Material 3),Compose BOM `2024.12.01`,实验版 window-size-class 实现响应式布局 |
| 持久化 | Room `2.8.4`(词典 / 背词 / 翻译缓存三库),SQLite FTS5 |
| 异步 | 协程、Repository 仓库模式 |
| 版本 | min / target / compile SDK `26 / 37 / 37` |
| 崩溃上报 | Lumen Crash SDK(版本在构建时自动解析) |

---

## 技术架构

单一 `:app` 模块,按职责分包:

```
com.chloemlla.cdict
├── core
│   ├── data        # Room: Entities / DAO / Database / Repository
│   ├── audio        # PronunciationPlayer + VivoTtsClient (vivo → 有道 → 系统 TTS 回退)
│   ├── search       # SearchEngine: 相关性排序 + Levenshtein 拼写纠错
│   └── translate    # vivo 翻译网关客户端 + 模型(内嵌翻译引擎)
└── ui             # Compose: CdictApp(四标签导航)/ Study* / Dictionary* / Translate* / Recommendation*
```

- 数据层通过 Room 的 `createFromAsset("dict.db")` 加载内置词典,首次打开复制到应用数据库,并暴露用户可见的加载 / 错误状态——缺少资源时绝不会静默降级为示例或伪造数据。
- 翻译引擎 `core/translate` 完整复刻 `translate.js` 的表单编码、批量拆分与(可选的)X-AI-GATEWAY 签名,并配有单元测试。
- 搜索层 `core/search` 对 FTS 结果重排,并提供 Levenshtein“你是不是想找……”建议。

---

## 快速上手

- **Android Studio**:直接打开仓库根目录即可,IDE 会自动使用 Gradle Wrapper。
- **命令行**:`./gradlew :app:assembleDebug`(需先生成词典资产,见下)。

> **注意**:AI 语感标注后的词典随仓库携带,位于 `scripts/CDict-dict.db`。CI 在构建阶段从 **GitHub Release** 下载合并后的词典。本地执行 `./gradlew :app:assembleDebug` 前需手动复制: `cp scripts/CDict-dict.db app/src/main/assets/dict.db`(签名文件 `dict.signature` 为可选,本地开发可忽略)。

---

## 数据管线

词典由三路数据源构建:

1. **已标注底库** — `scripts/CDict-dict.db`(49,213 词,7 组),随仓库提交,AI 标注字段(`emotionColor`、`register`、`nuanceDescription`、`usageWarning`、`collocations`)由 `scripts/annotate_dictionary.js`(node:sqlite,不使用 Python)生成。标注脚本每批 10 词合并为一次 OpenAI 兼容请求(往返次数降约 90%),带断点续传(中断后进度不丢),并对失败词重试 / 降级兜底以保证标注质量。
2. **富内容合并** — `.github/workflows/merge-distribution.yml`(手动 `workflow_dispatch`)把授权导出的 `distribution.sqlite` 富内容并入已标注底库:`scripts/merge_distribution.py` 匹配约 17,925 个共有词,补充 US/UK 音标、空位助记(含词源)、派生词,以及带中文译文的例句。产物经校验后**发布到 GitHub Release**(tag `dictionary-asset`),包含合并数据库、`dict.signature` 内容校验和、以及 SHA-256 校验文件。
3. **FLDC 参考数据源** — `scripts/fetch_fldc_export.py` 解码 fldc.pages.dev 分发的自定义二进制载荷(两个 gzip 分块容器 + 共享前缀字符串池)为转换器 JSON。`.github/workflows/export-fldc.yml`(手动 `workflow_dispatch`)在 CI 中端到端运行 `convert_dictionary.py`,并把产出的约 107,143 词 / 7 组参考资产上传为工作流构件。

CI 在构建时从硬编码的 Release 地址下载合并后的词典,校验 SHA-256 后复制到 `app/src/main/assets/dict.db`:

```bash
curl -fL --retry 3 -o "$RUNNER_TEMP/dict.db" "$BASE/CDict-dict.db"
curl -fL --retry 3 -o "$RUNNER_TEMP/dict.signature" "$BASE/dict.signature"
curl -fL --retry 3 -o "$RUNNER_TEMP/checksums.txt" "$BASE/checksums.txt"
(cd "$RUNNER_TEMP" && sha256sum -c checksums.txt)
cp "$RUNNER_TEMP/dict.db" app/src/main/assets/dict.db
cp "$RUNNER_TEMP/dict.signature" app/src/main/assets/dict.signature
python scripts/validate_dictionary_asset.py app/src/main/assets/dict.db \
  --expected-word-count 49213 --expected-groups 7
```

`app/src/main/assets/dict.db` 和 `dict.signature` 是 Git 忽略的构建副本;它们保持**压缩**存储(不设 `noCompress` 覆盖),并交给 AAB 按设备分发。随附的 `dict.signature` 文件让 App 能检测到词典内容在两次构建之间是否发生变化,从而提示用户删除并重建本地数据库。

`scripts/convert_dictionary.py` 仍可用于从授权导出重建无标注的底库。

### 富内容合并(distribution)

`.github/workflows/merge-distribution.yml`(手动 `workflow_dispatch`)把授权分发的 `distribution.sqlite` 富内容并入已标注资产:`scripts/merge_distribution.py` 按 headword 匹配两个词表共有的词(约 17,925 个),补 US/UK 音标、空位助记(含词源)、派生词,并把带中文译文的例句并入 `sentences`(已存在的例句只补中文,不重复插入)。产物经 `validate_dictionary_asset.py` 校验后**发布到 GitHub Release**(tag `dictionary-asset`),包含合并数据库、`dict.signature` 内容校验和、以及 SHA-256 校验文件。构建管线在构建时从 Release 下载合并数据库,不再提交到仓库(合并后数据库超 100 MB,GitHub 单文件限制)。

---

## CI / CD

`.github/workflows/build.yml` 在 push / pull request 时运行 debug 单元测试与 lint;签名发布构建由手动 **`workflow_dispatch`(`publish=true`)** 或 **`v*` tag** 触发。发布签名仅使用以下仓库 secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

工作流依次:`keytool` 校验解码后的 keystore → 构建 APK / AAB → `apksigner` 校验 APK → 生成 SHA-256 校验和 → 上传产物 → 清理临时签名材料。仓库不含任何 keystore 或明文凭据。**Lumen Crash SDK 版本在构建时自动解析**,无需手动升级。

发布构建开启 **R8 混淆**(`proguard-android-optimize.txt` + `proguard-rules.pro`)与**资源收缩**,并按 ABI 产出拆分 APK(`*universal*.apk` 为全架构包;AAB 交给 Google Play 按设备拆分)。专门的 `releaseAab` buildType 以关闭资源收缩的方式产出 AAB(AGP 无法在同一种 buildType 内同时启用 ABI 拆分 + 资源收缩 + AAB;Play 在服务端按设备做资源收缩)。

另有两条手动 `workflow_dispatch` 工作流维护词典数据:`merge-distribution.yml` 把合并后的富内容资产发布到 `dictionary-asset` GitHub Release(构建阶段下载),`export-fldc.yml` 在 CI 中重建 FLDC 参考资产。

---

## 版本历史

依据提交历史重建的演进脉络。

### 1.0.x —— 背词、推荐与打磨(当前)

应用从词典演进为每日学习伴侣:

- **间隔重复背词模式**:引入次日四选一复习(`b16aa27`),随后加入自适应间隔、干扰项引擎、错误归因与自适应每日目标(`8c383c5`)、答对音效(`752ad76`)、按频率加权的复习间隔(`2ea60cd`)、严格的同频率组干扰项偏好(`a4e12e4`),以及修复 Room identity-hash 崩溃的 `StudyDatabase` v2(`d626cdf`)。
- **每日推荐**:离线流按 3:5:2 配比混合 复习 / 核心新词 / 简单过渡,每日目标可配置(`9cc372e`)。
- **离线搜索质量**:Levenshtein 拼写纠错,以及 精确 > 前缀 > 频率 排序,让核心词优先浮现(`5f7d90e`)。
- **音频缓存**:发音按 MD5 键缓存到磁盘(50 MB LRU),并在详情页预取(`c1be4e9`);有道成为第一级且可整句朗读(`9a91bde`、`8a4fc36`)。
- **背词与搜索修复**:DAO 调用改为 suspend、测试锁定 Robolectric SDK、补全推荐页导航栏布局(`34b6c0d`、`5edef02`、`09e30ff`)。
- **AI 语感标注**:新增 5 个可空标注列,贯通管线、App 与工具(`ff75e50`);把已标注的 `scripts/CDict-dict.db` 直接提交并作为 App 资产,`neutral` 语体映射为「中性」(`8be57c0`);常见搭配可自动译文并朗读(`98dfb41`)。标注脚本每批 10 词合并请求(`e93024a`)、API 失败立即打点续传(`004a3b6`)、为普通词补「中性」语体(`6f4608e`)、重标缺失核心字段的词(`fc2537f`)、每 10s 上报运行时长心跳(`bf7aa6d`)。
- **词典 Release 分发**:授权 `distribution.sqlite` 富内容合并(`1b87c6f`)后,合并库改为**发布为 GitHub Release** 资产(`dictionary-asset`);CI 在构建时下载,App 在内置 `dict.signature` 与已装库资产签名不一致时提示重建本地库(`1c2fde9`),并声明 `MetadataEntity` 使 DAO 元数据查询可编译(`783eb43`)。
- **FLDC 导出解码器**:`fetch_fldc_export.py` 把 fldc.pages.dev 的二进制载荷解码为转换器 JSON,`export-fldc.yml` 可在 CI 中构建约 107,143 词的参考资产(`fa532b9`、`5dfb9f5`)。
- **标签页导航**:外壳维护真实访问历史栈,系统返回回到实际来源标签页(含跨标签跳词),各标签页状态经 saveable state holder 保存(`390440c`、`d0c7e6c`);启动默认打开词典标签页(`ca299c7`)。
- **背词**:「立即测试今日所学」让你按需用复习引擎测今日新学词,答对与按时复习同样推进间隔阶梯(`a66239c`)。
- **推荐定位调整**:方案A 将推荐页与背词分离——流改为 核心新词 / 词根拓展 / 高频过渡 的 5:3:2 配比,复习交还背词页(`b055c42`)。

### 1.0 —— 翻译、发音与发布加固

- **在线翻译**:接入 vivo 翻译网关,支持全方向集与语言列表(`57c7871`),随后加入 Room 持久化的三层缓存(`a73415b`、`c3a551a`),以及带转录图标的短语朗读(`8ef0898`)。
- **vivo TTS 发音**:用合成语音替换静态 CDN,含 PCM 格式识别与 WAV 包裹(`1875629`、`33a1aec`、`f2b7ec4`)。
- **vivo 方向归一化**,音标不再显示原生 JSON(`3135920`)。
- **崩溃上报**:集成 Lumen Crash SDK,并内置 Compose 报告页(`4894f6f`)。
- **发布构建修复**:声明 `releaseAab` buildType 为专属 AAB 通道(R8 混淆开、资源收缩关),并串行化 kapt 以消除 Room schema 导出的竞态(`cb344ea`、`f99ef79`、`a7f462b`、`25a544f`、`75e610e`、`18d1106`)。

### 0.9 —— 词典核心、schema 与 CI

- **离线词典**:构建 Room `words` 实体与 FTS 表,与内置 `dict.db` schema 对齐,并新增内置 schema 校验回归测试以杜绝启动崩溃(`a3cece9`、`1d09815`、`4f6efed`、`cbc6273`、`5570879`、`c30597b`、`66e2bfb`)。
- **词详情与关联**:接通发音并渲染词详情关联(`078df34`)。
- **启动品牌**:全密度启动图标并从清单引用(`15a1627`、`b2bf234`、`a03b79d`)。
- **数据管线**:抓取并转换授权导出为 `dict.db` 的脚本及校验脚本(`a3127ee`、`96d67e9`)。
- **持续交付**:开启自动 CDict 发布,产出签名逐 ABI APK,并在发布资产中使用 AGP 真实的拆分命名(`8c2713d`、`82b82f0`、`6ac92e2`、`7933e19`、`a456828`、`94be8c8`)。

---

## 验证策略

仓库策略要求 Android 构建与测试在 **GitHub Actions** 中执行;请勿在本地设备上运行 Gradle 或 Android 构建 / 测试命令。

---

## 开源协议

本项目基于 **GNU Affero General Public License v3.0 (AGPL-3.0)** 开源,详见 [LICENSE](LICENSE)。