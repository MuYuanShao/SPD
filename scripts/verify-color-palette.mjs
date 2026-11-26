#!/usr/bin/env node
/**
 * verify:color-palette — 全站颜色唯一来源校验
 * ---------------------------------------------------------------------
 * 背景：需求「需要为整个系统建立颜色文件，颜色只能取对应的，
 *       把其他杂色去掉，保持系统样式统一」。
 *
 * 规则：
 *   1. 全站样式（frontend/src/styles/**\/*.css 与 frontend/src/**\/*.vue 的
 *      <style> 块）里的颜色，只能来自：
 *        - tokens/colors.css 登记的 --spd-c-* 基础色；
 *        - tokens/palette.css 登记的 --spd-p-* 语义色；
 *        - 本脚本下方明确列出的白名单。
 *   2. 任何其它写死的 hex / rgb() / hsl() / 具名颜色都算违规。
 *   3. 三套配色方案文件（styles/theme/*.css）本身就是「基础色板的定义处」，
 *      它们定义 --spd-c-* 时必须写具体色值，因此按文件整体豁免，
 *      但仍然禁止它们引入 Element Plus 默认蓝等「第二套品牌色」。
 *
 * 用法：node scripts/verify-color-palette.mjs [--verbose]
 * 退出码：0 = 通过；1 = 存在违规。
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '..');
const FRONTEND_SRC = path.join(ROOT, 'frontend', 'src');
const STYLES_DIR = path.join(FRONTEND_SRC, 'styles');
const TOKENS_DIR = path.join(STYLES_DIR, 'tokens');
const VERBOSE = process.argv.includes('--verbose');

/* ------------------------------------------------------------------ *
 * 白名单（显式、小范围、有理由）
 * ------------------------------------------------------------------ */
const ALLOWED_KEYWORDS = new Set([
  // 语义关键字：随上下文继承，不属于「写死的颜色」
  'transparent',
  'currentcolor',
  'inherit',
  'initial',
  'unset',
  'none',
  // 基础色板文件本身登记的品牌色（三套方案与标准白共用）
  '#1f6f6d',
  '#175a58',
  '#2b827f',
  '#e8f1f1',
  '#ffffff',
  '#101820',
  '#1f2933',
  '#4a5a66',
  '#6b7c88',
  '#f5f8f9',
  '#eef3f5',
  '#f7fafb',
  '#d5e0e5',
  '#b9c8ce',
  '#e8eef1',
  '#2f7d5b',
  '#e6f1ec',
  '#b6d6c6',
  '#5b6b76',
  '#eef1f3',
  '#cdd7dc',
  '#9a6114',
  '#fbf1e2',
  '#e6cfa4',
  '#a33a30',
  '#f8ebe9',
  '#e0b8b2',
  '#1f5f7d',
  '#e9f1f5',
  '#b5ccd9',
]);

/* ------------------------------------------------------------------ *
 * 配色方案文件：这些文件负责「定义」--spd-c-*，
 * 因此其中的具体色值是合法的（它们是色板本身，不是业务杂色）。
 * 但仍禁止出现 EP 默认蓝 / 第二套品牌色。
 * ------------------------------------------------------------------ */
const SCHEME_FILES = new Set([
  'theme/calm-paper.css',
  'theme/night-black.css',
  'theme/standard.css',
]);

/**
 * 基础色板文件：tokens/colors.css 是「色板的定义处」，
 * 其中的 hex 就是登记动作本身，豁免。
 */
const PALETTE_FILES = new Set([
  'tokens/colors.css',
  'tokens/palette.css',
]);

const FORBIDDEN_ANYWHERE = [
  // Element Plus 默认主色 / 常见第二套品牌蓝
  '#409eff',
  '#409EFF',
  '#2563eb',
  '#2563EB',
  '#1677ff',
  '#0958d9',
  '#0b5ed7',
  '#3b82f6',
  '#1d4ed8',
  '#1e40af',
  // 靛 / 紫 / 粉 / 青 渐变（旧的第二套品牌色）
  '#6366f1',
  '#4f46e5',
  '#4338ca',
  '#3730a3',
  '#8b5cf6',
  '#7c3aed',
  '#ec4899',
  '#e11d48',
  '#be123c',
  '#9f1239',
  '#06b6d4',
  '#0891b2',
  '#0d9488',
  '#059669',
  '#047857',
];

/* ------------------------------------------------------------------ *
 * 采集「已登记」的颜色
 * ------------------------------------------------------------------ */
function readFileSafe(file) {
  try {
    return fs.readFileSync(file, 'utf8');
  } catch {
    return '';
  }
}

function collectDeclaredColors() {
  const declared = new Set();
  for (const name of ['colors.css', 'palette.css']) {
    const file = path.join(TOKENS_DIR, name);
    const text = readFileSafe(file);
    for (const m of text.matchAll(/#[0-9a-fA-F]{3,8}\b/g)) {
      declared.add(m[0].toLowerCase());
    }
    for (const m of text.matchAll(/rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)/g)) {
      declared.add(`rgb(${m[1]}, ${m[2]}, ${m[3]})`);
    }
  }
  return declared;
}

/* ------------------------------------------------------------------ *
 * 违规检测
 * ------------------------------------------------------------------ */
const NAMED_COLORS = new Set([
  'aliceblue', 'antiquewhite', 'aqua', 'aquamarine', 'azure', 'beige', 'bisque',
  'black', 'blanchedalmond', 'blue', 'blueviolet', 'brown', 'burlywood',
  'cadetblue', 'chartreuse', 'chocolate', 'coral', 'cornflowerblue', 'cornsilk',
  'crimson', 'cyan', 'darkblue', 'darkcyan', 'darkgoldenrod', 'darkgray',
  'darkgreen', 'darkgrey', 'darkkhaki', 'darkmagenta', 'darkolivegreen',
  'darkorange', 'darkorchid', 'darkred', 'darksalmon', 'darkseagreen',
  'darkslateblue', 'darkslategray', 'darkturquoise', 'darkviolet', 'deeppink',
  'deepskyblue', 'dimgray', 'dimgrey', 'dodgerblue', 'firebrick', 'floralwhite',
  'forestgreen', 'fuchsia', 'gainsboro', 'ghostwhite', 'gold', 'goldenrod',
  'gray', 'green', 'greenyellow', 'grey', 'honeydew', 'hotpink', 'indianred',
  'indigo', 'ivory', 'khaki', 'lavender', 'lavenderblush', 'lawngreen',
  'lemonchiffon', 'lightblue', 'lightcoral', 'lightcyan', 'lightgoldenrodyellow',
  'lightgray', 'lightgreen', 'lightgrey', 'lightpink', 'lightsalmon',
  'lightseagreen', 'lightskyblue', 'lightslategray', 'lightsteelblue',
  'lightyellow', 'lime', 'limegreen', 'linen', 'magenta', 'maroon',
  'mediumaquamarine', 'mediumblue', 'mediumorchid', 'mediumpurple',
  'mediumseagreen', 'mediumslateblue', 'mediumspringgreen', 'mediumturquoise',
  'mediumvioletred', 'midnightblue', 'mintcream', 'mistyrose', 'moccasin',
  'navajowhite', 'navy', 'oldlace', 'olive', 'olivedrab', 'orange',
  'orangered', 'orchid', 'palegoldenrod', 'palegreen', 'paleturquoise',
  'palevioletred', 'papayawhip', 'peachpuff', 'peru', 'pink', 'plum',
  'powderblue', 'purple', 'rebeccapurple', 'red', 'rosybrown', 'royalblue',
  'saddlebrown', 'salmon', 'sandybrown', 'seagreen', 'seashell', 'sienna',
  'silver', 'skyblue', 'slateblue', 'slategray', 'snow', 'springgreen',
  'steelblue', 'tan', 'teal', 'thistle', 'tomato', 'turquoise', 'violet',
  'wheat', 'white', 'whitesmoke', 'yellow', 'yellowgreen',
]);

const HEX_RE = /#[0-9a-fA-F]{3,8}\b/g;
const FUNC_RE = /\b(rgba?|hsla?|hwb|lab|lch|oklab|oklch)\s*\(/g;

/**
 * 具名颜色：只在「颜色属性值」里匹配，
 * 避免把 .icn.blue / .tag-red 这类类名误判为颜色。
 */
const COLOR_VALUE_RE = new RegExp(
  `(?:^|[;{\\s])(?:color|background|background-color|border-color|border(?:-\\w+)?-color|outline-color|fill|stroke|text-decoration-color|caret-color|column-rule-color|box-shadow|text-shadow)\\s*:([^;{}]+)`,
  'gmi',
);

function stripComments(source) {
  // 保留换行，确保行号与原文件一致
  return source.replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ' '));
}

function normalizeHex(hex) {
  let h = hex.slice(1).toLowerCase();
  if (h.length === 3) {
    h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
  } else if (h.length === 4) {
    h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2] + h[3] + h[3];
  } else if (h.length === 8) {
    h = h.slice(0, 7);
  }
  return `#${h}`;
}

/**
 * 判断一段颜色是否在「已登记」集合中。
 * - 纯白 #fff / #ffffff 在色板里登记过 → 允许。
 * - 透明通道写法 rgba(255,255,255,0.x) → 视为「白 + 透明度」，
 *   仅当白色已登记时允许。
 */
function isRegistered(colorValue, declared, allowlist) {
  const value = colorValue.trim().toLowerCase();
  if (allowlist.has(value)) return true;
  if (value.startsWith('#')) {
    return declared.has(normalizeHex(value)) || declared.has(value);
  }
  const fnMatch = value.match(/^([a-z]+)\((.*)\)$/);
  if (fnMatch) {
    const fn = fnMatch[1];
    const args = fnMatch[2].split(',').map((a) => a.trim());
    if (fn === 'rgba' || fn === 'rgb') {
      if (args.length < 3) return false;
      const key = `rgb(${args[0]}, ${args[1]}, ${args[2]})`;
      if (declared.has(key)) return true;
      // rgba(255,255,255,x) → 白 + 透明
      const rgbHex = `#${[args[0], args[1], args[2]]
        .map((n) => Number(n).toString(16).padStart(2, '0'))
        .join('')}`;
      return declared.has(rgbHex) || declared.has(normalizeHex(rgbHex));
    }
    // hsla / hsl：色板未登记具体 hsl，视为违规（统一要求用 hex/rgb）
    return false;
  }
  return false;
}

/**
 * 配色方案文件里，只有「定义基础色板」的那些行允许写具体色值。
 * 基础色板包括 --spd-c-*（标准令牌）与方案自己的别名
 * （--paper-* / --ink-* / --night-* 等）。
 */
const SCHEME_PALETTE_VAR_RE =
  /^\s*--(spd-c-[a-z0-9-]+|spd-(bg|border|surface|color|text|gray|primary|success|warning|danger|info|shadow)[a-z0-9-]*|el-[a-z0-9-]+|md-[a-z0-9-]+|paper-[a-z0-9-]+|ink-[a-z0-9-]+|night-[a-z0-9-]+|sidebar-bg|topbar-bg|card-bg|header-bg|row-hover|row-stripe|shadow-soft)\s*:/i;

/**
 * 多行值（渐变 / 阴影）会跨行，
 * 这里从「定义行」开始把后续的续行一并纳入豁免。
 */
const SCHEME_PALETTE_MULTILINE_RE = /^\s*--[a-z0-9-]+\s*:\s*(linear-gradient|radial-gradient|conic-gradient)\(/i;

function schemePaletteLines(source) {
  const lines = source.split('\n');
  const allowed = new Set();
  for (let i = 0; i < lines.length; i++) {
    if (SCHEME_PALETTE_VAR_RE.test(lines[i])) {
      allowed.add(i);
      // 多行值：一直豁免到分号结束
      if (SCHEME_PALETTE_MULTILINE_RE.test(lines[i]) && !lines[i].includes(';')) {
        let j = i + 1;
        while (j < lines.length && !lines[j].includes(';')) {
          allowed.add(j);
          j++;
        }
        allowed.add(j);
      }
    }
  }
  return { lines, allowed };
}

/* ------------------------------------------------------------------ *
 * 文件收集
 * ------------------------------------------------------------------ */
function walk(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(full, out);
    } else if (entry.isFile()) {
      out.push(full);
    }
  }
  return out;
}

function rel(p) {
  return path.relative(ROOT, p).replace(/\\/g, '/');
}

function collectStyleSources() {
  /** @type {{file: string, rel: string, source: string, scheme: boolean}[]} */
  const sources = [];

  for (const file of walk(STYLES_DIR)) {
    if (!file.endsWith('.css')) continue;
    const r = rel(file);
    const relToStyles = path.relative(STYLES_DIR, file).replace(/\\/g, '/');
    sources.push({
      file,
      rel: r,
      source: stripComments(fs.readFileSync(file, 'utf8')),
      scheme: SCHEME_FILES.has(relToStyles),
      palette: PALETTE_FILES.has(relToStyles),
    });
  }

  for (const file of walk(FRONTEND_SRC)) {
    if (!file.endsWith('.vue')) continue;
    const raw = fs.readFileSync(file, 'utf8');
    const styleBlocks = [];
    const re = /<style[^>]*>([\s\S]*?)<\/style>/g;
    let m;
    while ((m = re.exec(raw)) !== null) {
      styleBlocks.push(m[1]);
    }
    if (styleBlocks.length === 0) continue;
    sources.push({
      file,
      rel: rel(file),
      source: stripComments(styleBlocks.join('\n')),
      scheme: false,
      palette: false,
    });
  }

  return sources;
}

/* ------------------------------------------------------------------ *
 * 主流程
 * ------------------------------------------------------------------ */
const declared = collectDeclaredColors();
const allowlist = new Set([...ALLOWED_KEYWORDS].map((k) => k.toLowerCase()));

const sources = collectStyleSources();
const violations = [];
const perFile = [];

for (const src of sources) {
  const fileViolations = [];
  const { lines, allowed } = schemePaletteLines(src.source);

  lines.forEach((line, idx) => {
    const lineNo = idx + 1;
    // 配色方案文件里，只有定义 --spd-c-* 基础色板的那一行允许写具体色值
    const isPaletteDefinition = src.scheme && allowed.has(idx);

    if (src.palette) {
      // 基础色板文件本身就是「登记处」，仅检查禁止色（第二套品牌色）
      for (const bad of FORBIDDEN_ANYWHERE) {
        if (line.toLowerCase().includes(bad.toLowerCase())) {
          fileViolations.push({ line: lineNo, value: bad, kind: 'forbidden' });
        }
      }
      return;
    }

    // 1) 任何位置都禁止的颜色（EP 默认蓝 / 第二套品牌色）
    for (const bad of FORBIDDEN_ANYWHERE) {
      if (line.toLowerCase().includes(bad.toLowerCase())) {
        fileViolations.push({ line: lineNo, value: bad, kind: 'forbidden' });
      }
    }

    if (isPaletteDefinition) return;

    // 2) 未登记的 hex
    for (const m of line.matchAll(HEX_RE)) {
      const hex = m[0];
      if (!isRegistered(hex, declared, allowlist)) {
        fileViolations.push({ line: lineNo, value: hex, kind: 'unregistered-hex' });
      }
    }

    // 3) 未登记的 rgb() / hsl() 等函数色
    for (const m of line.matchAll(FUNC_RE)) {
      const start = m.index;
      let depth = 0;
      let end = start;
      for (let i = start; i < line.length; i++) {
        if (line[i] === '(') depth++;
        else if (line[i] === ')') {
          depth--;
          if (depth === 0) {
            end = i + 1;
            break;
          }
        }
      }
      const call = line.slice(start, end);
      if (!isRegistered(call, declared, allowlist)) {
        fileViolations.push({ line: lineNo, value: call, kind: 'unregistered-color' });
      }
    }

    // 4) 具名颜色（仅在颜色属性值里匹配）
    for (const m of line.matchAll(COLOR_VALUE_RE)) {
      const value = m[1];
      for (const named of NAMED_COLORS) {
        // 词边界：前后都不能是字母数字 / 连字符（避免 min-width 命中 white）
        const re = new RegExp(`(?<![-\\w])${named}(?![-\\w])`, 'i');
        if (re.test(value)) {
          fileViolations.push({ line: lineNo, value: named, kind: 'named-color' });
          break;
        }
      }
    }
  });

  perFile.push({ rel: src.rel, count: fileViolations.length, scheme: src.scheme });
  if (fileViolations.length > 0) {
    violations.push({ rel: src.rel, items: fileViolations });
  }
}

perFile.sort((a, b) => b.count - a.count || a.rel.localeCompare(b.rel));

console.log('=== 全站颜色唯一来源校验 (verify:color-palette) ===\n');
console.log(`已登记颜色：tokens/colors.css + tokens/palette.css（${declared.size} 个）`);
console.log(`扫描文件：${sources.length} 个（styles/** + **/*.vue 的 <style> 块）`);
console.log(`白名单：transparent / currentColor / inherit / initial / unset / none + 基础色板已登记色\n`);

const top = perFile.filter((f) => f.count > 0).slice(0, 40);
if (top.length === 0) {
  console.log('✅ 所有文件均未使用未登记颜色。');
} else {
  console.log('违规文件（按违规数排序）：');
  for (const f of top) {
    const suffix = f.scheme ? '  [配色方案文件：定义 --spd-c-*，允许具体色值]' : '';
    console.log(`  ${String(f.count).padStart(4)}  ${f.rel}${suffix}`);
  }
  const others = perFile.filter((f) => f.count > 0).length - top.length;
  if (others > 0) console.log(`  ... 另有 ${others} 个文件存在违规`);
  console.log('');
  for (const v of violations) {
    console.log(`\n${v.rel}`);
    for (const item of v.items.slice(0, 12)) {
      console.log(`   L${item.line}  ${item.value}   [${item.kind}]`);
    }
    if (v.items.length > 12) {
      console.log(`   ... 另有 ${v.items.length - 12} 处`);
    }
  }
}

const total = violations.reduce((sum, v) => sum + v.items.length, 0);
console.log(`\n合计违规：${total}`);

if (total > 0) {
  console.error('\n❌ 存在未登记颜色。请先在 tokens/palette.css 登记语义角色，再引用令牌。');
  process.exit(1);
}
console.log('\n✅ 通过：全站颜色均来自已登记令牌。');
