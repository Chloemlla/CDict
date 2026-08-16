#!/usr/bin/env node
/**
 * CDict 词典 AI 语感标注 / 合并工具（node:sqlite，零 npm 依赖）
 *
 * 给 CDict 的 dict.db 批量打上 AI 语感标注（感情色彩 / 语体 / 精细语意 / 避坑 / 常用搭配）：
 *   - AI 模式（默认）：对缺失标注的词条批量并发调用 OpenAI 兼容接口（DeepSeek 等），
 *     每批 --batch 个词（默认 10）合并为一次请求返回整批 JSON，请求数降 90%，
 *     强制结构化 JSON 返回，枚举校验通过后写回 dict.db（自动补缺失列）并追加 JSONL 留档。
 *   - 合并模式（--input）：把外部已标注好的 dict.db / JSONL 合并进目标库，不调用 API。
 *   - 并发 worker 池（默认 5 路并行，--parallel 调整；每 worker 领一批，等效吞吐 ≈ 并发 × 批量）
 *   - 断点续传：完成进度写 .annotate-dictionary.json，重跑自动跳过已标注/已完成词条
 *   - 校验与重试：JSON 合法性 / 枚举值校验，整批失败按退避重试（--max-retries）；
 *     批内个别词字段非法/缺失时降级为该词单独发一次请求兜底重试，保住标注质量
 *
 * 需要 Node >= 22.5（内置 node:sqlite）。
 *
 * 用法：
 *   node annotate_dictionary.js dict.db                          # AI 标注缺数据的词（每批 10 词）
 *   node annotate_dictionary.js dict.db --parallel 5             # 并发 5（默认，上限 10）
 *   node annotate_dictionary.js dict.db --batch 10               # 每批 10 词合并一次请求（默认）
 *   node annotate_dictionary.js dict.db --base-url https://tokenrhythm.studio/v1 --api-key sk-xxx --model deepseek-chat
 *   node annotate_dictionary.js dict.db --limit 100              # 只标前 100 个缺失词
 *   node annotate_dictionary.js dict.db --groups 1,2             # 只标 1/2 组
 *   node annotate_dictionary.js dict.db --input ann.jsonl        # 合并外部标注，不调 API
 *   node annotate_dictionary.js dict.db --dry-run                # 只统计缺多少，不写库
 *   环境变量可覆盖：AI_BASE_URL / AI_API_KEY / AI_MODEL / PARALLEL / AI_BATCH / DICT_DB / ANNOTATE_STATE /
 *                  AI_MAX_RETRIES / VERBOSE
 */
'use strict';

const fs = require('fs');
const path = require('path');
const { DatabaseSync } = require('node:sqlite');

// ---------------------------------------------------------------------------
// 配置（CLI 参数优先，其次环境变量）
// ---------------------------------------------------------------------------
const args = process.argv.slice(2);
// 需要跟一个值的选项：把它和它的值从「位置参数」里排除掉（否则 --limit 100 会把 100 当库路径）
const VALUE_OPTS = new Set([
  '--parallel', '--batch', '--base-url', '--api-key', '--model', '--input',
  '--limit', '--groups', '--state', '--max-retries', '--output',
]);
const consumed = new Set();
args.forEach((a, i) => { if (VALUE_OPTS.has(a)) { consumed.add(i); consumed.add(i + 1); } });
const positionals = args.filter((a, i) => !consumed.has(i) && !a.startsWith('-'));

function getOpt(name, fallback) {
  const i = args.indexOf(name);
  return i === -1 ? fallback : args[i + 1];
}

const CONFIG = {
  database: positionals[0] || process.env.DICT_DB || '',
  concurrency: Math.max(1, Math.min(10, parseInt(getOpt('--parallel', process.env.PARALLEL || '5'), 10) || 5)),
  batch: Math.max(1, Math.min(50, parseInt(getOpt('--batch', process.env.AI_BATCH || '10'), 10) || 10)),
  baseUrl: (getOpt('--base-url', process.env.AI_BASE_URL || 'https://tokenrhythm.studio/v1') || '').replace(/\/+$/, ''),
  apiKey: getOpt('--api-key', process.env.AI_API_KEY || ''),
  model: getOpt('--model', process.env.AI_MODEL || 'deepseek-chat'),
  input: getOpt('--input', process.env.ANNOTATE_INPUT || ''),
  output: getOpt('--output', process.env.ANNOTATE_OUTPUT || ''),
  limit: Math.max(0, parseInt(getOpt('--limit', process.env.ANNOTATE_LIMIT || '0'), 10) || 0),
  groups: (getOpt('--groups', '') || '').split(',').map((s) => parseInt(s.trim(), 10)).filter((n) => Number.isInteger(n) && n > 0),
  maxRetries: Math.max(1, parseInt(getOpt('--max-retries', process.env.AI_MAX_RETRIES || '3'), 10) || 3),
  force: args.includes('--force'),
  dryRun: args.includes('--dry-run'),
};

const VERBOSE = process.env.VERBOSE === '1';

// 断点续传：完成进度存 JSON，重跑自动跳过（bili-download 同款）
const STATE_FILE = getOpt('--state', process.env.ANNOTATE_STATE || '') ||
  (CONFIG.database ? path.join(path.dirname(CONFIG.database), '.annotate-dictionary.json') : '.annotate-dictionary.json');

// 带标签日志：并发时给每路输出加 [i/N] 前缀，避免串行错乱
function log(tag, ...out) {
  console.log(...(tag ? [tag, ...out] : out));
}

function loadState() {
  try {
    const obj = JSON.parse(fs.readFileSync(STATE_FILE, 'utf8'));
    if (obj && obj.completed && typeof obj.completed === 'object') {
      return {
        version: obj.version || 1,
        completed: obj.completed,
        failed: obj.failed && typeof obj.failed === 'object' ? obj.failed : {},
      };
    }
  } catch (e) { /* 无状态文件 */ }
  return { version: 1, completed: {}, failed: {} };
}

let state = loadState();

function saveState() {
  if (CONFIG.dryRun) return;
  try {
    fs.mkdirSync(path.dirname(path.resolve(STATE_FILE)), { recursive: true });
    fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2), 'utf8');
  } catch (e) {
    console.error(`状态文件写入失败: ${e.message}`);
  }
}

// 断点续传的落盘策略：成功项先记到内存（DB 本身就是持久化源头），按节流定时写；
// 失败项出错后立即 saveState()，保证 API 大面积报错/中断时进度不丢。
let stateDirty = false;
let lastStateSave = 0;
const STATE_SAVE_THROTTLE_MS = 2000;

function maybeSaveState() {
  if (CONFIG.dryRun || !stateDirty) return;
  const now = Date.now();
  if (now - lastStateSave < STATE_SAVE_THROTTLE_MS) return;
  lastStateSave = now;
  stateDirty = false;
  saveState();
}

function flushState() {
  if (CONFIG.dryRun) return;
  stateDirty = false;
  saveState();
}

function recordSuccess(id, word) {
  state.completed[String(id)] = { word, ts: Date.now() };
  delete state.failed[String(id)];
  stateDirty = true;
  maybeSaveState();
}

function recordFailure(id, word, error) {
  state.failed[String(id)] = { word, error, ts: Date.now() };
  stateDirty = true;
  saveState();
}

// ---------------------------------------------------------------------------
// SQLite：打开 + 自动补列 + 读写标注
// ---------------------------------------------------------------------------
const ANNOTATION_COLUMNS = ['emotionColor', 'register', 'nuanceDescription', 'usageWarning', 'collocations'];

function openDatabase(p, { wal = false } = {}) {
  const db = new DatabaseSync(path.resolve(p));
  if (wal) db.exec('PRAGMA journal_mode = WAL;');
  return db;
}

// 旧版 dict.db 没有标注列：打开时自动 ALTER TABLE 补上，新旧库统一可用
function ensureColumns(db) {
  const existing = new Set(db.prepare("SELECT name FROM pragma_table_info('words')").all().map((r) => r.name));
  for (const col of ANNOTATION_COLUMNS) {
    if (!existing.has(col)) db.exec(`ALTER TABLE words ADD COLUMN ${col} TEXT`);
  }
}

function loadWords(db) {
  return db.prepare(
    `SELECT id, word, frequencyGroup, translation, ${ANNOTATION_COLUMNS.join(', ')} ` +
      'FROM words ORDER BY frequencyGroup, frequency, id',
  ).all();
}

function wordHasAnnotation(w) {
  return ANNOTATION_COLUMNS.some((c) => w[c] !== null && String(w[c]).trim() !== '');
}

function persistAnnotation(db, word, ann) {
  if (CONFIG.dryRun) return;
  db.prepare(
    'UPDATE words SET emotionColor = ?, register = ?, nuanceDescription = ?, usageWarning = ?, collocations = ? WHERE id = ?',
  ).run(ann.emotionColor, ann.register, ann.nuanceDescription, ann.usageWarning, ann.collocations, word.id);
}

// JSONL 留档走内存缓冲 + 整批一次性落盘，避免每词一次同步磁盘写卡住主线程
let jsonlBuffer = [];

function appendJsonl(ann, wordText) {
  jsonlBuffer.push(JSON.stringify({ word: wordText, ...ann }));
}

function flushJsonl() {
  if (!jsonlBuffer.length) return;
  const lines = jsonlBuffer.join('\n') + '\n';
  jsonlBuffer = [];
  try {
    fs.mkdirSync(path.dirname(path.resolve(CONFIG.output)), { recursive: true });
    fs.appendFileSync(CONFIG.output, lines, 'utf8');
  } catch (e) {
    console.error(`JSONL 写入失败: ${e.message}`);
  }
}

// ---------------------------------------------------------------------------
// 标注归一化与校验
// ---------------------------------------------------------------------------
function clean(v) {
  if (v == null) return null;
  const s = String(v).trim();
  return s || null;
}

function normalizeEmotion(v) {
  const s = String(v == null ? '' : v).trim().toLowerCase().replace(/[-\s]+/g, '_');
  return ['positive', 'negative', 'neutral', 'context_dependent'].includes(s) ? s : null;
}

function normalizeRegister(v) {
  const s = String(v == null ? '' : v).trim().toLowerCase().replace(/[-\s]+/g, '_');
  return ['academic', 'spoken', 'written', 'literary', 'informal', 'neutral'].includes(s) ? s : null;
}

function splitCollocations(v) {
  if (v == null) return [];
  if (Array.isArray(v)) return v.map((x) => String(x).trim()).filter(Boolean);
  return String(v).split(/[；;、,]/).map((x) => x.trim()).filter(Boolean);
}

// AI/JSONL 的 snake_case 字段 → 入库 camelCase 字段（collocations 存「；」分隔串）
function toDbAnnotation(a) {
  const collocations = splitCollocations(a.common_collocations ?? a.collocations);
  return {
    emotionColor: normalizeEmotion(a.emotion_color ?? a.emotionColor),
    register: normalizeRegister(a.register),
    nuanceDescription: clean(a.nuance_description ?? a.nuanceDescription),
    usageWarning: clean(a.usage_warning ?? a.usageWarning),
    collocations: collocations.length ? collocations.join('；') : null,
  };
}

// 校验返回的 JSON 是否可用；非法返回 null
function validateAnnotation(obj) {
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return null;
  const ann = toDbAnnotation(obj);
  const hasContent = ann.emotionColor || ann.register || ann.nuanceDescription || ann.usageWarning || ann.collocations;
  return hasContent ? ann : null;
}

// ---------------------------------------------------------------------------
// AI 接口调用（OpenAI 兼容 /chat/completions）
// ---------------------------------------------------------------------------
const SYSTEM_PROMPT = `你是一个严谨的雅思英语语言专家。分析用户给出的英文单词，只输出一个 JSON 对象（不要 Markdown，不要任何额外文字），字段：
{
  "emotion_color": "positive | negative | neutral | context_dependent",
  "register": "academic | spoken | written | literary | informal | neutral",
  "nuance_description": "中文一句话，说明该词的精细语意/动作画面感，点出与普通同义词的区别",
  "usage_warning": "中文避坑指南：不能搭配哪些词、不适合哪些场景；没有则空字符串",
  "common_collocations": ["2到4个常用搭配，含介词/动词搭配"]
}
示例：
输入词: murmur
输出: {"emotion_color":"neutral","register":"literary","nuance_description":"低声、温柔或含糊不清地说话，强调声音极小，常用于私语或抱怨。","usage_warning":"不能与 loudly、announce 等大声或正式场景搭配。","common_collocations":["murmur in agreement","murmur softly"]}
输入词: notorious
输出: {"emotion_color":"negative","register":"written","nuance_description":"因坏事而广为人知，强调恶名昭著。","usage_warning":"不要用于褒义语境。","common_collocations":["notorious for","notorious criminal"]}`;

// 批量提示词：一次返回 N 个词的标注数组。外层必须包成对象（json_object 模式不允许顶层数组）
const BATCH_PROMPT = `你是一个严谨的雅思英语语言专家。分析用户给出的英文单词列表，只输出一个 JSON 对象（不要 Markdown，不要任何额外文字），结构：
{
  "annotations": [
    {
      "word": "单词本身，必须与输入原样一致",
      "emotion_color": "positive | negative | neutral | context_dependent",
      "register": "academic | spoken | written | literary | informal | neutral",
      "nuance_description": "中文一句话，说明该词的精细语意/动作画面感，点出与普通同义词的区别",
      "usage_warning": "中文避坑指南：不能搭配哪些词、不适合哪些场景；没有则空字符串",
      "common_collocations": ["2到4个常用搭配，含介词/动词搭配"]
    }
  ]
}
硬性要求：
- annotations 数组的长度必须等于输入单词数量，顺序必须与输入完全一致
- 每个元素都必须包含 word 字段（原样返回该单词），作为对齐依据
示例：
输入列表: ["murmur", "notorious"]
输出: {"annotations":[{"word":"murmur","emotion_color":"neutral","register":"literary","nuance_description":"低声、温柔或含糊不清地说话，强调声音极小。","usage_warning":"不能与 loudly、announce 等大声或正式场景搭配。","common_collocations":["murmur in agreement","murmur softly"]},{"word":"notorious","emotion_color":"negative","register":"written","nuance_description":"因坏事而广为人知，强调恶名昭著。","usage_warning":"不要用于褒义语境。","common_collocations":["notorious for","notorious criminal"]}]}`;

const activeControllers = new Set();
process.on('SIGINT', () => {
  log(null, '\n收到中断，正在保存进度并终止标注任务...');
  flushJsonl();
  flushState();
  for (const c of activeControllers) c.abort();
  process.exit(130);
});

function truncate(s, n) {
  return s.length > n ? s.slice(0, n) + '…' : s;
}

async function callChat(messages, useJsonMode, timeoutMs = 60000) {
  const controller = new AbortController();
  activeControllers.add(controller);
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const body = { model: CONFIG.model, messages, temperature: 0.1, stream: false };
    if (useJsonMode) body.response_format = { type: 'json_object' };
    if (VERBOSE) log(null, 'REQ> ' + JSON.stringify({ url: `${CONFIG.baseUrl}/chat/completions`, model: CONFIG.model }));
    const resp = await fetch(`${CONFIG.baseUrl}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${CONFIG.apiKey}`,
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    const status = resp.status;
    const text = await resp.text();
    if (status === 429 || status >= 500) {
      const err = new Error(`HTTP ${status}: ${truncate(text, 200)}`);
      err.retryable = true;
      throw err;
    }
    if (!resp.ok) {
      // 部分兼容网关不支持 response_format：降级为普通请求重试一次
      if (status === 400 && useJsonMode && /response_format|json\s?mode|format/i.test(text)) {
        const err = new Error('JSON 模式不受支持，降级为普通请求重试');
        err.retryable = true;
        err.retryWithoutJsonMode = true;
        throw err;
      }
      throw new Error(`HTTP ${status}: ${truncate(text, 300)}`);
    }
    const data = JSON.parse(text);
    const content = data && data.choices && data.choices[0] &&
      data.choices[0].message && data.choices[0].message.content;
    if (!content) throw new Error('响应缺少 choices[0].message.content');
    return content;
  } finally {
    clearTimeout(timer);
    activeControllers.delete(controller);
  }
}

// 单个词条标注：解析 + 校验 + 退避重试
async function annotateOne(word, tag) {
  const messages = [
    { role: 'system', content: SYSTEM_PROMPT },
    {
      role: 'user',
      content: `输入词: ${word.word}\n中文释义: ${word.translation || ''}\n请按 Schema 输出 JSON。`,
    },
  ];
  let lastError = null;
  let useJsonMode = true;
  for (let attempt = 0; attempt < CONFIG.maxRetries; attempt++) {
    try {
      const content = await callChat(messages, useJsonMode);
      let parsed;
      try {
        parsed = JSON.parse(content);
      } catch (e) {
        // 兼容被 ```json``` 代码块包裹的返回
        const m = content.match(/```(?:json)?\s*([\s\S]*?)```/);
        if (m) parsed = JSON.parse(m[1]); else throw new Error('AI 返回不是合法 JSON');
      }
      const ann = validateAnnotation(parsed);
      if (!ann) throw new Error('AI 返回字段非法或为空');
      return ann;
    } catch (e) {
      lastError = e;
      if (e.retryWithoutJsonMode) useJsonMode = false;
      if (attempt < CONFIG.maxRetries - 1) {
        const delay = 1500 * (2 ** attempt) + Math.round(Math.random() * 500);
        log(tag, `[重试 ${attempt + 1}/${CONFIG.maxRetries}] ${e.message}（${delay}ms 后）`);
        await new Promise((r) => setTimeout(r, delay));
      }
    }
  }
  throw lastError || new Error('未知失败');
}

// 批量标注：一次请求返回整批词的标注数组（顺序与 batch 对齐，元素为归一化后的 ann 或 null）
async function annotateBatch(batch, tag) {
  const wordsDesc = batch.map((w) => `- ${w.word}${w.translation ? `（中文释义: ${w.translation}）` : ''}`).join('\n');
  const messages = [
    { role: 'system', content: BATCH_PROMPT },
    { role: 'user', content: `输入单词列表:\n${wordsDesc}\n请按 Schema 输出 JSON 对象，annotations 数组与输入一一对应。` },
  ];
  // 批量返回 token 更多，超时按批量放大
  const timeoutMs = Math.min(300000, 60000 + batch.length * 15000);
  let lastError = null;
  let useJsonMode = true;
  for (let attempt = 0; attempt < CONFIG.maxRetries; attempt++) {
    try {
      const content = await callChat(messages, useJsonMode, timeoutMs);
      let parsed;
      try {
        parsed = JSON.parse(content);
      } catch (e) {
        // 兼容被 ```json``` 代码块包裹的返回
        const m = content.match(/```(?:json)?\s*([\s\S]*?)```/);
        if (m) parsed = JSON.parse(m[1]); else throw new Error('AI 返回不是合法 JSON');
      }
      const list = parsed && typeof parsed === 'object' && Array.isArray(parsed.annotations) ? parsed.annotations : null;
      if (!list) throw new Error('AI 返回缺少 annotations 数组');
      const byWord = new Map();
      for (const item of list) {
        if (item && typeof item === 'object' && typeof item.word === 'string') {
          byWord.set(item.word.trim().toLowerCase(), item);
        }
      }
      return batch.map((w, idx) => {
        const pos = list[idx] && typeof list[idx] === 'object' && typeof list[idx].word === 'string'
          && list[idx].word.trim().toLowerCase() === String(w.word).trim().toLowerCase()
          ? list[idx]
          : byWord.get(String(w.word).trim().toLowerCase());
        return pos ? validateAnnotation(pos) : null;
      });
    } catch (e) {
      lastError = e;
      if (e.retryWithoutJsonMode) useJsonMode = false;
      if (attempt < CONFIG.maxRetries - 1) {
        const delay = 1500 * (2 ** attempt) + Math.round(Math.random() * 500);
        log(tag, `[重试 ${attempt + 1}/${CONFIG.maxRetries}] ${e.message}（${delay}ms 后）`);
        await new Promise((r) => setTimeout(r, delay));
      }
    }
  }
  throw lastError || new Error('未知失败');
}

// ---------------------------------------------------------------------------
// 合并外部标注：--input（dict.db 或 JSONL），不调 API
// ---------------------------------------------------------------------------
function readMergeRecords(src) {
  const ext = path.extname(src).toLowerCase();
  if (ext === '.db' || ext === '.sqlite' || ext === '.sqlite3') {
    const sdb = openDatabase(src);
    try {
      ensureColumns(sdb);
      return sdb.prepare(`SELECT id, word, ${ANNOTATION_COLUMNS.join(', ')} FROM words`).all()
        .filter((w) => wordHasAnnotation(w));
    } finally {
      sdb.close();
    }
  }
  const records = [];
  for (const line of fs.readFileSync(src, 'utf8').split(/\r?\n/)) {
    const t = line.trim();
    if (!t) continue;
    try {
      const obj = JSON.parse(t);
      if (obj && typeof obj === 'object') records.push(obj);
    } catch (e) { /* 跳过坏行 */ }
  }
  return records;
}

async function runMerge(db, words) {
  const records = readMergeRecords(CONFIG.input);
  log(null, `读取外部标注 ${records.length} 条（${CONFIG.input}）`);
  const byWord = new Map();
  const byId = new Map();
  for (const r of records) {
    const w = r.word ?? r.term;
    if (typeof w === 'string') byWord.set(w.toLocaleLowerCase(), r);
    const id = Number(r.id);
    if (Number.isInteger(id) && id > 0) byId.set(id, r);
  }
  const pending = words.filter((w) => CONFIG.force || !wordHasAnnotation(w));
  let imported = 0;
  let missing = 0;
  for (const w of pending) {
    const rec = byId.get(w.id) || byWord.get(String(w.word).toLocaleLowerCase());
    const ann = rec && validateAnnotation(rec);
    if (!ann) { missing++; continue; }
    persistAnnotation(db, w, ann);
    recordSuccess(w.id, w.word);
    imported++;
  }
  flushState();
  return { imported, missing, pending: pending.length, skipped: words.length - pending.length };
}

// ---------------------------------------------------------------------------
// AI 模式：并发 worker 池（每 worker 领一批，批内个别词失败降级为单词请求兜底）
// ---------------------------------------------------------------------------
async function runAi(db, words) {
  let pending = words.filter((w) => {
    if (wordHasAnnotation(w)) return false;
    if (!CONFIG.force && state.completed[String(w.id)]) return false;
    return true;
  });
  if (CONFIG.groups.length) pending = pending.filter((w) => CONFIG.groups.includes(w.frequencyGroup));
  if (CONFIG.limit > 0) pending = pending.slice(0, CONFIG.limit);
  const skipped = words.length - pending.length;
  if (pending.length === 0) {
    log(null, '没有需要标注的词条。');
    return { ok: 0, fail: 0, pending: 0, skipped };
  }
  const batchSize = CONFIG.batch;
  const numBatches = Math.ceil(pending.length / batchSize);
  log(null, `\n待标注 ${pending.length} 项（并发 ${CONFIG.concurrency}，每批 ${batchSize} 词，共 ${numBatches} 批）:\n` +
    pending.slice(0, 5).map((w, i) => `  ${i + 1}. ${w.word}（组 ${w.frequencyGroup}）`).join('\n') +
    (pending.length > 5 ? `\n  …等 ${pending.length} 项` : '') + '\n');

  if (CONFIG.dryRun) {
    console.log(`（--dry-run）将标注 ${pending.length} 项，跳过 ${skipped} 项，未写库。`);
    return { ok: 0, fail: 0, pending: pending.length, skipped };
  }

  const results = new Array(pending.length);
  const limit = Math.min(CONFIG.concurrency, numBatches);
  let next = 0;
  const workers = Array.from({ length: limit }, async () => {
    while (true) {
      const bi = next++;
      if (bi >= numBatches) break;
      const start = bi * batchSize;
      const batch = pending.slice(start, start + batchSize);
      const end = Math.min(start + batchSize, pending.length);
      const tag = `[${start + 1}-${end}/${pending.length}]`;
      let anns;
      try {
        anns = await annotateBatch(batch, tag);
      } catch (e) {
        // 整批失败（重试耗尽）：整批记失败
        for (let j = 0; j < batch.length; j++) {
          const w = batch[j];
          const i = start + j;
          results[i] = { ok: false, word: w.word, error: e.message };
          recordFailure(w.id, w.word, e.message);
          console.error(`${tag} [失败] ${w.word}: ${e.message}`);
        }
        continue;
      }
      for (let j = 0; j < batch.length; j++) {
        const w = batch[j];
        const i = start + j;
        let ann = anns[j];
        if (!ann) {
          // 批内个别词字段非法/缺失：降级为该词单独发一次请求兜底，保住标注质量
          try { ann = await annotateOne(w, tag); } catch (e2) { ann = null; }
        }
        if (ann) {
          persistAnnotation(db, w, ann);
          recordSuccess(w.id, w.word);
          if (CONFIG.output) appendJsonl(ann, w.word);
          results[i] = { ok: true, word: w.word };
          log(tag, `✔ ${w.word} → ${ann.emotionColor || '-'} / ${ann.register || '-'}`);
        } else {
          results[i] = { ok: false, word: w.word, error: 'AI 返回字段非法或为空' };
          recordFailure(w.id, w.word, 'AI 返回字段非法或为空');
          console.error(`${tag} [失败] ${w.word}: AI 返回字段非法或为空`);
        }
      }
      flushJsonl();
    }
  });
  await Promise.all(workers);
  flushJsonl();
  flushState();
  const ok = results.filter((r) => r && r.ok).length;
  const fail = results.filter((r) => r && !r.ok).length;
  return { ok, fail, pending: pending.length, skipped };
}

// ---------------------------------------------------------------------------
// 主流程
// ---------------------------------------------------------------------------
async function main() {
  console.log('== CDict 词典 AI 语感标注 ==');
  if (!CONFIG.database) {
    console.error('用法: node annotate_dictionary.js <dict.db> [选项]\n  见文件头注释。');
    process.exit(1);
  }
  console.log(
    `模式: ${CONFIG.input ? '合并外部标注（不调 API）' : `AI 标注（${CONFIG.model} @ ${CONFIG.baseUrl}）`}` +
      ` | 并发: ${CONFIG.concurrency}（--parallel 调整）`,
  );
  if (CONFIG.dryRun) console.log('提示: --dry-run 模式，只统计不写库。');
  if (CONFIG.input && !fs.existsSync(CONFIG.input)) {
    console.error(`输入标注文件不存在: ${CONFIG.input}`);
    process.exit(1);
  }

  const db = openDatabase(CONFIG.database, { wal: true });
  ensureColumns(db);
  try {
    const words = loadWords(db);
    if (words.length === 0) {
      console.error('词典中没有词条。');
      process.exit(1);
    }
    const annotated = words.filter((w) => wordHasAnnotation(w)).length;
    console.log(`词条总数 ${words.length}，已标注 ${annotated}，断点续传: ${STATE_FILE}`);

    let summary;
    if (CONFIG.input) {
      summary = await runMerge(db, words);
      console.log(`\n\n================ 合并完成 ================`);
      console.log(`新增标注 ${summary.imported} 条，跳过已标注 ${summary.skipped} 条，未匹配 ${summary.missing} 条`);
    } else {
      if (!CONFIG.apiKey) {
        console.error('AI 模式需要 --api-key 或环境变量 AI_API_KEY。');
        process.exit(1);
      }
      summary = await runAi(db, words);
      console.log(`\n\n================ 批量标注完成 ================`);
      console.log(`成功 ${summary.ok} 项，失败 ${summary.fail} 项，跳过 ${summary.skipped} 项`);
      if (CONFIG.output) console.log(`JSONL 留档: ${CONFIG.output}`);
      if (CONFIG.dryRun) console.log('（--dry-run 模式，未写库）');
    }
    console.log('==========================================');
  } finally {
    db.close();
  }
}

main().catch((e) => {
  console.error('\n[失败] ' + e.message);
  process.exit(1);
});
