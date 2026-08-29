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

> **当前版本 `1.1.0`** · `minSdk 26` / `targetSdk 37` / `compileSdk 37` · Compose BOM `2026.08.00` · Room `2.8.4` · Kotlin/JVM 21 · AGP `9.3.1` · `versionCode` = HEAD 上的提交数(由 CI 计算)
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
| 🗂 **离线优先** | 内置 49,213 词条 + 高中短语手札(1,262 条短语,11 个分类),分属 7 个 IELTS 频率组;首次启动复制到 Room 数据库,完全离线。 |
| 🔍 **智能搜索** | 英文全文搜索(SQLite FTS5,覆盖单词 / 翻译 / 释义)、中文子串搜索,以及 **Levenshtein 拼写纠错**(“你是不是想找……”)。 |
| 🏷 **课程标签** | 通过下拉菜单按课程标签(如高中 3500 词、高中短语)筛选词典,带标签的词条在详情页展示标签胶囊。 |
| 🔊 **发音** | 三级回退(默认词典音频 → 在线合成 → 系统 TTS,可在「关于」中切换在线来源优先级),配合磁盘音频缓存,无需打包任何音频文件。 |
| 🌐 **在线翻译** | 由自有后端提供的内置翻译引擎,带**三层缓存**。 |
| 🧠 **背词模式** | 按 IELTS 频率加权的自适应间隔重复,含干扰项引擎与次日四选一复习。 |
| 🤖 **AI 语感标注** | AI 逐词生成的语感标注——感情色彩、语体、精细语意、避坑提示,以及可朗读、自动译文的常见搭配。 |
| 📅 **每日推荐** | 完全离线的每日探索流,按 **5:3:2** 配比混合核心新词 / 词根拓展 / 高频过渡词(复习留在背词页)。 |
| ✂️ **划词翻译工具条** | Android 文本选择工具条操作——在任意应用选中文字,点击 *CDict 翻译*,即可跳转翻译或直达匹配的词典词条。 |
| 🔒 **隐私** | 词典与学习数据完全本地;可选联网请求携带所需内容与随机安装标识,后者只用于区分请求额度,不读取硬件标识。 |
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
| 词库规模 | 49,213 词条 + 高中短语手札(1,262 条短语,11 个分类),分属 7 个 IELTS 频率组 |
| 英文搜索 | 英文全文搜索(SQLite FTS5),覆盖单词 / 翻译 / 释义 |
| 中文搜索 | 中文子串搜索(`LIKE` 匹配单词与翻译) |
| 课程标签筛选 | 通过下拉菜单按课程标签(如高中 3500 词、高中短语)筛选,词详情页展示标签胶囊 |
| 排序 | 结果按 **精确 > 前缀 > 频率** 重排,核心 IELTS 词优先浮现 |
| 拼写纠错 | 无结果时给出 **Levenshtein** 编辑距离(≤ 2)内的“你是不是想找……”建议 |
| 排序方式 | 词表支持切换排序(按频率 / 按字母 / 字母倒序) |
| 无限滚动 | 词表分页加载,浏览流畅 |
| 离线存储 | Brotli 压缩的 `dict.db.br` 首次启动解压到本地,然后用 Room 打开数据库 |

### 🧩 词详情页

点击词条进入详情页,展示:

- **音标**:英式 `phoneticUk` + 美式 `phoneticUs`
- **释义与翻译**:英文释义 `definition` + 中文翻译 `translation`
- **助记词** `mnemonic`
- **频率**:频率组 `frequencyGroup` + IELTS 频率 `frequency`
- **词根** `roots` 及其含义
- **派生词** `derivedTerms`
- **课程标签** `curriculumTags` 以 FlowRow 标签胶囊展示(如高中 3500 词、高中短语)
- **历年出现频率热力图** `heatmap`:各时间段出现得分
- **真题句子** `sentences`:英文原文 + 中文翻译,每词最多 10 条
- **AI 语感标注**:感情色彩徽标(`emotionColor`)+ 语体标签(`register`)、精细语意(`nuanceDescription`)、高亮避坑提示(`usageWarning`),以及**常见搭配**(`collocations`)——每条搭配自动翻译为中文并可朗读。词详情页与背词卡片共用。
- **英音 / 美音发音按钮**

### 🔊 发音

词详情页提供 **英音 / 美音** 发音按钮。默认使用 **词典静态发音**,按三级顺序回退,无需打包任何音频文件;可在「关于 → 朗读优先来源」中切换两级在线来源的优先级:

```
词典静态发音 (GET https://tts.chloemlla.com/api/cdict/tts?source=youdao)
  → 在线语音合成 (GET https://tts.chloemlla.com/api/cdict/tts?source=engine)
  → Android 系统 TextToSpeech
```

任一级失败(超时 / 非 2xx / 音频损坏 / 网络不可用)自动降级到下一级;发音不可用时词典浏览与离线搜索完全不受影响。切换为在线合成优先后,两级在线来源的顺序反转;词与整句都直接交给所选的在线来源整句朗读(绝不逐词拆读,以免按词打断句子)。播放按"单飞"合并同词并发下载,同词快速连点只保留最新一次发音。

**两级在线来源都只请求本项目自有后端**(`CDictBackend`,`https://tts.chloemlla.com`),由服务端代理到上游;安装包内**不含任何第三方凭据**——上游地址、`appId` / `appKey` 与嵌套签名全部留在服务端。

- `VivoTtsClient` 只发一次普通 `GET /api/cdict/tts?source=engine&text=…&langType=…`,仅接受 `audio/*` 响应;返回 JSON 会被识别为明确错误而非当作音频。
- 响应可能是 MP3,也可能是无容器 PCM(`audio/L16; rate=16000`);播放前会识别格式并给 PCM 补 WAV 头。
- 英音映射 `langType=en-GBR`、美音 `en-USA`;词典来源用 `type=1`(英音)/ `type=2`(美音)。

**音频缓存。** 发音会缓存到磁盘,重复查询即时返回、且离线更友好:

- 文件以 **`<accent>:<source>:<text>` 的 MD5** 为键,生成稳定且简短的文件名,各级音频各占独立命名空间。
- 采用 **50 MB LRU** 预算,超出时按最近最少使用淘汰。
- 词详情页会对发音进行**预取**。

> ⚠️ **免责声明**:在线发音依赖本项目后端及其背后的上游服务,两者都可能随时失效或变更。发音是便利功能,并非核心依赖;词典核心功能完全离线。

### 🌐 在线翻译

**翻译**标签页内置在线翻译引擎:

- 由本项目**自有后端**提供:`POST https://tts.chloemlla.com/api/cdict/translate`(语言列表 `GET /api/cdict/languages`)。请求体只带 `text` / `from` / `to`,上游凭据、设备参数与签名由服务端补全。
- **语言方向**:自动 → 中文、自动 → 英文、中文 → 英文、英文 → 中文(共 21 个方向)。
- **批量翻译**:多行文本按 `\n` 合并为单次请求,响应逐行拆回。
- **响应附加信息**:源 / 目标语言回显、音标。
- **短语朗读**:英文内容可实时朗读(在线合成),结果旁带朗读图标。

**三层缓存。** 翻译结果按 内存 LRU → Room 磁盘缓存 → 网络 三层提供服务;除 Room 持久化的 `RoomTranslationCache` 外,还有自定义内存 `MemoryLruCache<string, TranslationResult>`。重复翻译即时返回,且“首次在线后离线可用”。

> ⚠️ **免责声明**:在线翻译依赖本项目后端及其背后的上游网关,两者都可能随时失效或变更。翻译是便利功能,并非应用的硬性依赖;词典核心功能完全离线。

### ❤️ 赞赏支持(完全自愿)

应用**永久免费**,所有功能都不需要付费解锁;赞赏只是可选的支持方式,**不解锁任何东西**。

| 支付宝 | 微信 |
|:---:|:---:|
| <img src="https://bee-reg-ab.imagency.cn/p/a2df2e95b7dc5c235e9e5bd51a5d7d56.jpg" alt="支付宝赞赏码" width="220"> | <img src="https://bee-reg-ab.imagency.cn/p/1ef3d8b53b69cf08a9fa7d6e98f779f4.png" alt="微信赞赏码" width="220"> |

- **收款码不内置**:安装包内没有任何收款码或收款账号。打开赞赏页时向自有后端拉取 `GET https://tts.chloemlla.com/api/cdict/donate`(渠道、文案与鸣谢名单)与 `GET /api/cdict/donate/<渠道 id>`(收款码),换码换文案换名单都无需发版。
- **图片地址原样下发**:`/api/cdict/donate/<渠道 id>` 在后台填了图床地址时返回 `302`,直接指向后台填写的那个地址——后端不下载、不缓存、不改写图片字节。因此取图这一跳会直连该图床;地址留空时才由后端返回内置图片。客户端始终忽略响应体里的绝对地址,一律用 `CDictBackend.BASE_URL + /api/cdict/donate/<id>` 重建请求,渠道 id 按 `[a-z0-9-]{1,32}` 校验。
- **署名鸣谢**:转账备注里写上想展示的称呼,开发者核实后加入后台名单,应用内「赞赏支持」页的鸣谢名单随即实时更新;不写备注即匿名支持。
- **应用内申请署名**:赞赏页底部的表单可直接提交「交易号 + 希望展示的称呼」(`POST /api/cdict/donate/claim`),核实后加入名单;请求正文只包含这两项,另附一个仅用于区分请求额度的随机安装标识,不读取硬件标识。同一交易号幂等。提交成功会有 🎉 洒落一遍。
- **提交限流两层**:客户端本地 `DonationClaimQuota` 限定两次至少间隔 30 秒、一小时内最多 5 次(仅存本地 prefs `claim_window_start` / `claim_window_count` / `claim_last_millis`),后端另有每 IP 每小时 10 次的独立限流。
- **名单两处可见**:「赞赏支持」页底部与「开源许可声明」页的「赞赏鸣谢名单」分区都以圆角标签展示同一份名单;首启强制阅读许可声明时不会为此联网。
- **只提醒一次**:`MainActivity` 的 `onResume` / `onPause` 用 `SystemClock.elapsedRealtime()` 累加前台时长写入 `AboutStore`;累计 ≥ 30 分钟且完成过至少一轮复习后,在回到主标签时判定一次,于学习小结页给出一次入口 + 一条底部提示条。无论点开还是关掉,之后都不再自动出现。
- **不新增权限、不埋点**:前台时长只存在本地 prefs(`foreground_millis` / `tip_prompt_shown` / `tip_prompt_dismissed` / `review_round_done`),不联网上报;划词翻译弹窗走独立 Activity,显式排除在判定路径之外。

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

### 🤝 伙伴应用(Clash Meta for Android)

CDict 是 [Clash Meta for Android](https://github.com/MetaCubeX/ClashMetaForAndroid) 登记在册的**伙伴应用**,双向都只读:

- CDict 在 `<application>` 内声明 `com.github.kr328.clash.partner` 标记,并在 `<queries>` 中列出 Clash 的包名;被其 `PartnerApps` 注册表识别后会自动纳入 VPN 访问控制。
- CDict 只*读取* Clash 导出的 `partnerStatus`(内核是否运行 / 隧道状态 / 配置名称),不会启动、停止或切换 VPN,也不会读取订阅与密钥。
- 当 Clash 内核在跑但**未开隧道**时,在线翻译 / 朗读 / 更新检查改走其本地混合端口 `127.0.0.1:7890`;隧道已连接或该端口连不上时立即回退直连,适配本身不会把联网功能弄坏。
- 「关于 → 伙伴应用」可查看实时状态,也可以随时关掉跟随。
- 伙伴状态读不到时(如 Clash 未安装或其 provider 不可达),界面会给出**可操作的原因**,让你知道该怎么做。
- 进前台时 CDict 可通过 `startActivityForResult` 拉起 **CMFA 配对确认窗**,配对握手无需离开应用。

### ✂️ 划词翻译工具条

CDict 注册了 Android **文本选择 `PROCESS_TEXT` 操作**。在设备上任意位置选中文字后,点击工具条菜单中的 *CDict 翻译*,即可弹出浮动翻译窗口;若选中文字匹配词典 headword,则直接跳转词条详情。

划词翻译 Activity 运行在独立任务中,显式排除在赞赏前台时长判定路径之外。

### 🔒 权限与隐私

- 仅申请 **`INTERNET`** 权限,用于在线翻译与发音。
- 词典与学习数据完全本地。在线翻译、发音和赞赏页等可选联网功能会发送完成请求所需的内容，并附带仅用于区分请求额度的随机安装标识；不读取硬件标识，也不关联账号或手机号。

---

## 技术栈

| 层次 | 选型 |
|---|---|
| 语言 | Kotlin / JVM 21 |
| UI | Jetpack Compose (Material 3),Compose BOM `2026.08.00`,activity-compose `1.13.0`,实验版 window-size-class 实现响应式布局 |
| 持久化 | Room `2.8.4`(词典 / 背词 / 翻译缓存三库),SQLite FTS5 |
| 异步 | 协程、Repository 仓库模式 |
| 版本 | min / target / compile SDK `26 / 37 / 37` |
| 构建系统 | AGP `9.3.1`,Kotlin `2.4.10` |
| 崩溃上报 | Lumen Crash SDK(版本在构建时自动解析) |

---

## 技术架构

单一 `:app` 模块,按职责分包:

```
com.chloemlla.cdict
├── core
│   ├── data        # Room: Entities / DAO / Database / Repository
│   ├── audio        # PronunciationPlayer + VivoTtsClient (可配置:词典音频 / 在线合成 → 另一在线来源 → 系统 TTS 回退)
│   ├── net          # CDictBackend: 所有网络请求统一指向的自有后端
│   ├── search       # SearchEngine: 相关性排序 + Levenshtein 拼写纠错
│   └── translate    # 自有后端翻译客户端 + 模型(内嵌翻译引擎)
└── ui             # Compose: CdictApp(四标签导航)/ Study* / Dictionary* / Translate* / Recommendation*
```

- 数据层通过 Brotli 压缩的离线词典资源（`dict.db.br`）加载内置词典，首次启动时解压到本地，然后用 Room 打开数据库，并暴露用户可见的加载 / 错误状态——缺少资源时绝不会静默降级为示例或伪造数据。
- 翻译引擎 `core/translate` 负责表单编码与批量拆分,只把 `text` / `from` / `to` 提交到自有后端,并配有单元测试断言客户端不外发任何上游凭据。
- 搜索层 `core/search` 对 FTS 结果重排,并提供 Levenshtein“你是不是想找……”建议。

---

## 快速上手

- **Android Studio**:直接打开仓库根目录即可,IDE 会自动使用 Gradle Wrapper。
- **命令行**:`./gradlew :app:assembleDebug`(需先生成词典资产,见下)。

> **注意**:AI 语感标注后的词典随仓库携带,位于 `scripts/CDict-dict.db`。数据合并工作流会在发布到 **GitHub Release** 前生成 Brotli 压缩资产；CI 构建阶段直接下载 `dict.db.br`，不再重复压缩。本地执行 `./gradlew :app:assembleDebug` 前需先压缩: `pip install brotli && python -c "import brotli; data=open('scripts/CDict-dict.db','rb').read(); open('app/src/main/assets/dict.db.br','wb').write(brotli.compress(data, quality=11))"`。构建配置通过 `androidResources.localeFilters` 只保留中英文资源。

---

## 数据管线

词典由四路数据源构建:

1. **已标注底库** — `scripts/CDict-dict.db`(49,213 词,7 组),随仓库提交,AI 标注字段(`emotionColor`、`register`、`nuanceDescription`、`usageWarning`、`collocations`)由 `scripts/annotate_dictionary.js`(node:sqlite,不使用 Python)生成。标注脚本每批 10 词合并为一次 OpenAI 兼容请求(往返次数降约 90%),带断点续传(中断后进度不丢),并对失败词重试 / 降级兜底以保证标注质量。
2. **富内容合并** — `.github/workflows/merge-distribution.yml`(手动 `workflow_dispatch`)把授权导出的 `distribution.sqlite` 富内容并入已标注底库:`scripts/merge_distribution.py` 匹配约 17,925 个共有词,补充 US/UK 音标、空位助记(含词源)、派生词,以及带中文译文的例句。产物经校验后**发布到 GitHub Release**(tag `dictionary-asset`),包含合并数据库、预压缩的 `dict.db.br`、`dict.signature` 内容校验和、以及 SHA-256 校验文件。
3. **短语库** — `.github/workflows/merge-phrases.yml`(手动 `workflow_dispatch`,或使用仓库提交的 `scripts/phrases.docx`)通过 `scripts/build_phrase_db.py` 解析结构化 docx 词表(11 节,1,413 条),去重后约 1,262 个独特短语,并入发布的 CDict-dict.db,并标记 `curriculumTags = "高中短语"`,让 App 的课程标签筛选可以隔离短语条目。
4. **FLDC 参考数据源** — `scripts/fetch_fldc_export.py` 解码 fldc.pages.dev 分发的自定义二进制载荷(两个 gzip 分块容器 + 共享前缀字符串池)为转换器 JSON。`.github/workflows/export-fldc.yml`(手动 `workflow_dispatch`)在 CI 中端到端运行 `convert_dictionary.py`,并把产出的约 107,143 词 / 7 组参考资产上传为工作流构件。

**课程标签标注。** `scripts/apply_curriculum_tags.py`(作为分发流水线的 CI 步骤运行)以幂等方式对 `curriculumTags` 列中匹配的 headword 应用课程标签(如高中 3500 词),然后重新计算资产签名。标签不会重复、已有标签被保留,工作流可安全重复运行。

CI 在构建时从硬编码的 Release 地址下载已由数据合并工作流生成的 `dict.db.br` 和 `dict.signature`，只校验这两个构建资产的 SHA-256 后直接打包:

```bash
curl -fL --retry 3 -o "$RUNNER_TEMP/dict.db.br" "$BASE/dict.db.br"
curl -fL --retry 3 -o "$RUNNER_TEMP/dict.signature" "$BASE/dict.signature"
curl -fL --retry 3 -o "$RUNNER_TEMP/checksums.txt" "$BASE/checksums.txt"
(cd "$RUNNER_TEMP" && awk '$2 == "dict.db.br" || $2 == "dict.signature"' checksums.txt | sha256sum -c -)
cp "$RUNNER_TEMP/dict.db.br" app/src/main/assets/dict.db.br
cp "$RUNNER_TEMP/dict.signature" app/src/main/assets/dict.signature
```

`app/src/main/assets/dict.db.br` 和 `dict.signature` 是 Git 忽略的构建副本。数据合并工作流在发布数据库时生成 `dict.db.br`，构建工作流直接下载它，不再重复执行 Brotli 压缩。App 首次启动时使用 `org.brotli:dec` 库将 `dict.db.br` 解压到本地，然后用 Room 打开数据库。随附的 `dict.signature` 文件让 App 能检测到词典内容在两次构建之间是否发生变化,从而提示用户重建本地数据库。

`scripts/convert_dictionary.py` 仍可用于从授权导出重建无标注的底库。

### 富内容合并(distribution)

`.github/workflows/merge-distribution.yml`(手动 `workflow_dispatch`)把授权分发的 `distribution.sqlite` 富内容并入已标注资产:`scripts/merge_distribution.py` 按 headword 匹配两个词表共有的词(约 17,925 个),补 US/UK 音标、空位助记(含词源)、派生词,并把带中文译文的例句并入 `sentences`(已存在的例句只补中文,不重复插入)。产物经 `validate_dictionary_asset.py` 校验后**发布到 GitHub Release**(tag `dictionary-asset`),包含合并数据库、预压缩的 `dict.db.br`、`dict.signature` 内容校验和、以及 SHA-256 校验文件。构建管线直接从 Release 下载预压缩资产,不再在每次构建时压缩数据库。

---

## CI / CD

`.github/workflows/build.yml` 在 push / pull request 时运行 debug 单元测试与 lint。`.github/workflows/codeql.yml` 在 push / pull request 时对默认分支运行 CodeQL 分析(Java/Kotlin autobuild)。签名发布构建由手动 **`workflow_dispatch`(`publish=true`)** 或 **`v*` tag** 触发。发布签名仅使用以下仓库 secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

工作流依次:`keytool` 校验解码后的 keystore → 构建 APK / AAB → `apksigner` 校验 APK → 生成 SHA-256 校验和 → 上传产物 → 清理临时签名材料。仓库不含任何 keystore 或明文凭据。**Lumen Crash SDK 版本在构建时自动解析**,无需手动升级。

发布构建开启 **R8 混淆**(`proguard-android-optimize.txt` + `proguard-rules.pro`)与**资源收缩**,并按 ABI 产出拆分 APK(`*universal*.apk` 为全架构包;AAB 交给 Google Play 按设备拆分)。专门的 `releaseAab` buildType 以关闭资源收缩的方式产出 AAB(AGP 无法在同一种 buildType 内同时启用 ABI 拆分 + 资源收缩 + AAB;Play 在服务端按设备做资源收缩)。仅打包 `zh` 和 `en` 语言资源(通过 `androidResources.localeFilters`),进一步缩小 APK。

另有三条手动 `workflow_dispatch` 工作流维护词典数据:`merge-distribution.yml` 把合并后的富内容资产发布到 `dictionary-asset` GitHub Release,`merge-phrases.yml` 将短语库合并到同一 Release,`export-fldc.yml` 在 CI 中重建 FLDC 参考资产。

---

## 版本历史

依据提交历史重建的演进脉络。

### 1.1.1 —— UX 大改、划词翻译、伙伴配对与赞赏页(当前)

全面 UX 改进、新增划词翻译入口、后端统一,以及赞赏支持页:

- **UX 大改**:全标签页统一 shimmer 加载 / 错误重试 / 可操作空状态(`efda5b3`);列表/详情动画与页面切换过渡(`65a9eb5`、`5d554f6`);全站说明性文字支持长按选中复制(`0a11242`);水波纹裁剪到组件圆角(`b6a96a6`);卡片阴影移除,扁平 Material 3 风格(`de730d8`、`b4a9d52`);释义与译文完整显示不再被省略号截断(`71255f7`)。
- **紧凑窗口高度**:五大页面在低窗口高度下可滚动,底部操作不再被裁切(`f42e606`、`635d096`)。五页面无障碍与 UX 再优化(`73ab473`)。
- **划词翻译工具条**:Android `PROCESS_TEXT` 操作——在任意位置选中文字,点击 *CDict 翻译*,跳转翻译或直达匹配词条(`8dd0a86`)。
- **更新流程重设计**:全新更新检查对话框(`d732026`);检查更新优先读 `release-manifest.json` 并展示安装包体积(`2180c99`);APK 资产改名为 `CDict_android_<版本>-<短哈希>[_<ABI>].apk`(`f8afe2f`)。关于页新增自动检查更新开关(`f45b21b`)。
- **自有后端统一**:翻译与朗读全部走自有后端,客户端不再直连第三方(`ad77ee5`)。客户端注释移除厂商名称与凭据字段名(`f2fb577`、`8922467`)。
- **背词与推荐引擎共享**:背词与推荐共用推荐引擎、每日额度与筛选范围(课程标签 + 频率组)(`f4f582a`、`65e98f9`)。
- **官方客户端额度隔离**:官方签名客户端的请求走独立额度桶,第三方构建不会消耗官方用户的配额(`8be8749`)。
- **赞赏页**:收款码由后端下发(安装包内无任何收款信息);累计前台 30 分钟且完成过复习后一次性提示;应用内署名申请表单含限流;鸣谢名单同步展示在赞赏页与开源许可声明页(`8239cc0`、`d97f274`、`7175425`)。
- **伙伴应用增强**:伙伴状态读不到时给出可操作的原因(`400a824`);前台时机通过 `startActivityForResult` 拉起 CMFA 配对确认窗(`9957a98`、`9ea1e62`)。关于页给出伙伴应用下载入口(`e5dc383`)。
- **构建修复**:KDoc 嵌套块注释(`6fe72dd`)、Kotlin 编译告警清零(`a0c28f4`)、测试断言清理(`8cb0055`)。

### 1.1 —— 短语库、课程标签与数据管线加固

词典新增结构化短语库、课程标签筛选,以及多项基础设施改进:

- **短语库**:从结构化 docx(11 节,1,413 条)解析并去重为约 1,262 个独特短语,构建匹配 CDict schema 的 SQLite 资产(`scripts/build_phrase_db.py`),并入发布的 CDict-dict.db,标记 `curriculumTags = "高中短语"` 以便 App 的课程标签筛选隔离短语条目(`127e546`)。docx 源文件提交在仓库中(`b80a334`),`merge-phrases.yml` 通过 `workflow_run` 串联构建工作流(`230aa3c`)。
- **课程标签**:Schema v5(`MIGRATION_4_5`)为 `WordEntity` 新增 `curriculumTags` 列,在词详情页以 FlowRow 标签胶囊展示。`scripts/apply_curriculum_tags.py` 在 CI 管线中幂等地应用标签(如高中 3500 词),`validate_dictionary_asset.py` 现在要求该列存在(`d703a53`)。
- **排序与课程标签下拉菜单**:词典页新增排序方式下拉菜单(按频率 / 按字母 / 字母倒序)和课程标签筛选下拉菜单(9195b6d)。
- **APK 瘦身**:构建时用 Brotli 压缩 `dict.db`(`d85b618`),使用原生 Brotli + `ACCESS_BUFFER` 实现快速 I/O(`07da17d`)。首次启动解压前检查存储空间(`8a7843d`),自动检测版本号变化并重建数据库(`8a7843d`),用 `androidResources.localeFilters`(zh/en)替代已弃用的 `resourceConfigurations`(`49408d5`)。数据库解压互斥串行化(`f6a3dcf`),校验返回值防止创建空库(`f92019f`)。
- **搜索与翻译加固**:FTS 前缀查询清理操作符字符,翻译在输入/方向变化时重新翻译并取消过期请求,详情页在网络错误时显示错误+重试而非无限转圈(`d703a53`)。`CancellationException` 正确向上抛出,阻止过期响应覆盖最新结果(`2b21ff4`)。
- **崩溃 SDK 修复**:SDK 安装移至 `onCreate`(`3a1c601`),解压前创建目录(`5c6e0f0`),使用可移植存储可用性检查(`d7425a9`),SDK 安装失败暴露到 UI(`21f8182`),启用默认后端上传(`696b809`)。
- **CodeQL 与安全**:新增 `codeql.yml` 工作流,CodeQL Action v3→v4,切换为 Java/Kotlin autobuild(`4f747ec`、`b9387a1`、`65a9c22`)。使用纯 Java `org.brotli:dec` 库解决 CI 构建障碍(`58685d0`)。
- **CI 管线**:新增 `merge-phrases.yml` 工作流(`127e546`);build.yml 通过 `workflow_run` 串联在 merge-phrases 之后(`230aa3c`);Brotli 资产在 Android 构建前发布(`4fb5b2c`);CodeQL v3→v4、setup-python 5→7、setup-java 4→5、gradle/actions 4→6、softprops/action-gh-release 2→3(多个 dependabot 提交)。
- **依赖升级**:Compose BOM `2024.12.01` → `2026.08.00`,AGP `9.3.0` → `9.3.1`,activity-compose `1.13.0`,Kotlin 2.4.10。

### 1.0.x —— 背词、推荐与打磨(上一版)

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