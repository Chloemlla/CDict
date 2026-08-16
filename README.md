# CDict · 雅思离线词典

**CDict** is an offline-first IELTS dictionary for Android, built with **Kotlin**, **Jetpack Compose (Material 3)**, and **Room**. The package and application ID are `com.chloemlla.cdict`.

**CDict** 是一款**离线优先**的雅思词典 Android 应用,基于 Kotlin、Jetpack Compose Material 3 与 Room 构建,包名与应用 ID 均为 `com.chloemlla.cdict`。

> **当前版本 `1.0.1`**(versionCode `2`) · `minSdk 26` / `targetSdk 37` / `compileSdk 37` · Compose BOM `2024.12.01` · Room `2.8.4` · Kotlin/JVM 17
>
> Current release **`1.0.1`** (versionCode `2`): `minSdk 26` / `targetSdk 37` / `compileSdk 37`, Compose BOM `2024.12.01`, Room `2.8.4`, Kotlin/JVM 17.

---

## 功能特性 Features

### 📖 离线词典 Offline Dictionary

| 能力 | 英文 | 中文 |
|---|---|---|
| 词库规模 | 49,213 words across 7 IELTS frequency groups | 49,213 词条,分属 7 个 IELTS 频率组 |
| 英文搜索 | Full-text search (SQLite FTS4) over English word, translation, and definition | 英文全文搜索(FTS4),覆盖单词 / 翻译 / 释义 |
| 中文搜索 | Chinese substring search via `LIKE` over word and translation | 中文子串搜索(`LIKE` 匹配单词与翻译) |
| 离线存储 | Room copies the bundled asset `dict.db` into the app database on first launch | Room 首次启动将内置 `dict.db` 复制到应用数据库,完全离线可用 |

### 🧩 词详情页 Word Detail

点击词条进入详情页,展示:

- **音标**:英式 `phoneticUk` + 美式 `phoneticUs`
- **释义与翻译**:英文释义 `definition` + 中文翻译 `translation`
- **助记词** `mnemonic`
- **频率**:频率组 `frequencyGroup` + IELTS 频率 `frequency`
- **词根** `roots`:词根及其含义
- **派生词** `derivedTerms`
- **历年出现频率热力图** `heatmap`:各时间段出现得分
- **真题句子** `sentences`:英文原文 + 中文翻译,每词最多 10 条

### 🔊 发音 Pronunciation

词详情页提供 **英音 / 美音** 按钮,发音由内置的 **vivo 语音合成**客户端默认生成,按三级顺序回退,无需打包任何音频文件:

```
vivo 语音合成 (POST https://vivotrans.vivo.com.cn/fy/tts,返回 MP3)
  → Youdao 静态发音 (dict.youdao.com/dictvoice)
  → Android 系统 TextToSpeech
```

任一级失败(超时 / 非 2xx / 音频损坏 / 网络不可用)自动降级到下一级;发音不可用时词典浏览与离线搜索完全不受影响。

`VivoTtsClient`(逆向 `com.vivo.translator` 的语音合成链路):

- 请求体为 JSON(非表单),`auf=audio/L16;rate=16000`、`aue=3` → 返回 MP3 二进制;失败返回 `{"errorResult":{...}}`
- HMAC-SHA256 签名`Sign.sign`(`hmacSha256Hex`),请求头含 `product/model/sysVer/appVer` 客户端指纹
- 使用**独立于翻译引擎**的凭证 `appId=1336541186` / `appKey=9925f42b…`;英音 `langType=en-GBR`、美音 `en-USA`

> ⚠️ **免责声明**:vivo 语音合成为私有接口,`appId`/`appKey` 为客户端常量,可能随时失效或变更。发音是便利功能,并非核心依赖;词典核心功能完全离线。

### 🌐 在线翻译 Online Translation

底部导航新增 **翻译** 标签页,内置在线翻译引擎:

- 基于 **vivo 翻译网关**(逆向 `com.vivo.translator` 4.5.9.0,与 `fanyiji-rev/translate.js` 同源)
- **免密钥直连**:V2 无签名通道 `POST https://vivotrans.vivo.com/translation/query`
- **语言方向**:自动→中文、自动→英文、中文→英文、英文→中文
- **批量翻译**:多行文本按 `\n` 合并为单次请求,响应逐行拆回
- 响应附加信息:源/目标语言回显、音标

> ⚠️ **免责声明**:该网关为私有接口,`appId`/`appKey` 为客户端常量,可能随时失效或变更。翻译是便利功能,并非应用的硬性依赖;词典核心功能完全离线。

### 🔒 权限与隐私 Permissions & Privacy

- 仅申请 **`INTERNET`** 权限,用于在线翻译与发音(vivo 合成 / Youdao / CDN)
- 词典数据完全本地;不收集、不上传任何个人信息

---

## 快速上手 Getting Started

- **Android Studio**:直接打开仓库根目录即可,IDE 会自动使用 Gradle Wrapper。
- **命令行**:`./gradlew :app:assembleDebug`(需先生成词典资产,见下)。

> **注意**:`dict.db` 由 CI 生成并被 Git 忽略(仓库刻意不携带 50–70 MB 二进制)。**本地构建前必须先执行数据生成步骤**;代码绝不会静默替换成示例或伪造数据。

## 数据管线 Data pipeline

仓库刻意**不包含伪造词典或未经授权的源数据副本**。生产资产由用户提供的授权导出文件生成:

```bash
# 本地转换(基于授权导出文件)
python scripts/convert_dictionary.py /path/to/authorized-export.json app/src/main/assets/dict.db \
  --expected-word-count 49213
```

转换器支持 JSON / JSONL、base85 包裹 JSON、Brotli 压缩 JSON。它生成规范化的 `words`、`derived_terms`、`roots`、`sentences`、`word_sentence_links`、`heatmap_entries` 表,以及 `word_search` 英文 FTS 表;校验记录数并输出各表计数(JSON)。转换器不抓取网页——数据来源与 schema 必须显式、可复现。

从授权站点完整生成并校验:

```bash
python -m pip install brotli==1.1.0
python scripts/fetch_isdc_export.py --output /tmp/isdc-export.json \
  --expected-html-sha256 c5cab0349b5fcf3e56904619a5f15c8923c7021a1f30c2c20639e2e597459c20 \
  --expected-json-sha256 f83cddde1f09a8c4a15e97a6502187c935ba7dbf028e1c45812abd912cebecef
python scripts/convert_dictionary.py /tmp/isdc-export.json app/src/main/assets/dict.db \
  --expected-word-count 49213 --expected-groups 7
python scripts/validate_dictionary_asset.py app/src/main/assets/dict.db \
  --expected-word-count 49213 --expected-groups 7
```

`dict.db` 在 CI 中生成并校验,仅作为构建/发布产物上传,不进 Git 仓库。

## 技术架构 Architecture

单一 `:app` 模块,按职责分包:

```
com.chloemlla.cdict
├── core
│   ├── data        # Room: Entities / DAO / Database / Repository
│   ├── audio       # PronunciationPlayer + VivoTtsClient(vivo 合成 → Youdao → TTS 回退)
│   └── translate   # vivo 翻译网关客户端 + 模型(内嵌翻译引擎)
└── ui             # Compose: CdictApp(底部导航)/ Dictionary* / Translate*
```

- 数据层通过 Room 的 `createFromAsset("dict.db")` 加载内置词典,首次打开复制到应用数据库,并暴露用户可见的加载/错误状态。
- 翻译引擎 `core/translate` 完整复刻 `translate.js` 的表单编码、批量拆分与(可选的)X-AI-GATEWAY 签名,并配有单元测试。

## CI/CD

`.github/workflows/build.yml` 在 push / pull request 时运行 debug 单元测试与 lint;签名发布构建由**手动 `workflow_dispatch`(`publish=true`)**或 **`v*` tag** 触发。发布签名仅使用以下仓库 secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

工作流依次:`keytool` 校验解码后的 keystore → 构建 APK / AAB → `apksigner` 校验 APK → 生成 SHA-256 校验和 → 上传产物 → 清理临时签名材料。仓库不含任何 keystore 或明文凭据。

发布构建开启 **R8 混淆**(`proguard-android-optimize.txt` + `proguard-rules.pro`)与**资源收缩**,并按 ABI 产出拆分 APK(`*universal*.apk` 为全架构包;AAB 交给 Google Play 按设备拆分)。拆分收益当前很小——原生库仅 `libandroidx.graphics.path.so` 数十 KB——主体体积在 83 MB 的 `dict.db`,已由 AAB 按需分发。

## 验证策略 Verification policy

仓库策略要求 Android 构建与测试在 **GitHub Actions** 中执行;请勿在本地设备上运行 Gradle 或 Android 构建/测试命令。

## 开源协议 License

本项目基于 **GNU Affero General Public License v3.0 (AGPL-3.0)** 开源,详见 [LICENSE](LICENSE)。

This project is licensed under the **GNU Affero General Public License v3.0**. See [LICENSE](LICENSE).
