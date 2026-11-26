#!/usr/bin/env node
/**
 * 统一筛选栏回归闸门（verify:filter-bar）
 * =====================================================================
 * 产品硬约束（用户诉求）：
 *   「筛选框也写一个组件，保持完全一致，筛选框的高度也要一样，
 *     筛选组件要有标准规则，如多宽需要换行，对应的按钮要放在哪里，
 *     都写一个统一的组件」
 *
 * 本脚本把这条约束变成可执行的检查：
 *
 *   1. **统一性**：凡是带筛选区的 .vue 文件，必须使用统一筛选栏组件
 *      <SpdFilterBar>，或至少挂上统一筛选栏的 CSS 类
 *      （.spd-filter-bar / .spd-filter-bar__fields / .spd-filter-bar__actions）。
 *      不允许残留自造的筛选栅格类名（如 .ulc-filter / .hospital-query-grid
 *      等历史类）。
 *   2. **字号**：筛选控件相关样式不得写死 `font-size: NNpx`，
 *      必须使用排版令牌（var(--spd-font-size-*)）。
 *   3. **高度**：筛选控件不得写死与 --spd-p-field-height 不同的 px 高度，
 *      允许引用该令牌本身。
 *   4. **按钮**：筛选栏里的操作按钮必须是 <SpdButton>，
 *      不允许出现裸 <button> / .btn / .el-button；
 *      且按钮顺序必须是「重置」→「查询」（查询在右，为主动作）。
 *   5. **换行规则唯一**：不允许任何页面在 scoped 样式里覆写
 *      `.spd-filter-bar__fields` 的 `grid-template-columns`，
 *      列数 / 换行规则只能来自 styles/components/spd-filter-bar.css。
 *
 * 失败时打印详细报告（file:line）并以非 0 退出码结束。
 *
 * 用法：node scripts/verify-filter-bar.mjs
 */

import { readdirSync, readFileSync, statSync, existsSync } from 'node:fs'
import { join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = fileURLToPath(new URL('.', import.meta.url))
const ROOT = join(__dirname, '..')
const SRC = join(ROOT, 'frontend', 'src')
const STYLES = join(SRC, 'styles')

/** 统一筛选栏的组件标记（.vue 里出现即视为使用统一筛选栏） */
const COMPONENT_TAG = '<SpdFilterBar'

/** 统一筛选栏的 CSS 类（作为未迁移页面的过渡出口） */
const UNIFIED_CLASSES = ['spd-filter-bar', 'spd-filter-bar__fields', 'spd-filter-bar__actions']

/**
 * 已废弃的历史筛选栅格类名。
 * 出现即视为「仍在自造筛选区」，必须改用 SpdFilterBar。
 */
const LEGACY_FILTER_CLASSES = [
  'ulc-filter',
  'ulc-filter-fields',
  'ulc-filter-actions',
  'md-hospital-filter',
  'md-hospital-filter-actions',
  'hospital-query-grid',
  'hospital-query-actions'
]

/** 允许出现的过渡性辅助类（不是筛选栅格本身） */
const ALLOWED_LEGACY_SELECTORS = new Set(['ulc-filter-select'])

/** 筛选控件高度令牌 */
const FIELD_HEIGHT_TOKEN = '--spd-p-field-height'

/** 允许在筛选样式里出现的 px 高度（标准高度 34px 本身） */
const FIELD_HEIGHT_PX = 34

/* ------------------------------------------------------------------ */
/* 文件遍历                                                            */
/* ------------------------------------------------------------------ */

function walk(dir, out = [], filter = () => true) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    const st = statSync(full)
    if (st.isDirectory()) {
      walk(full, out, filter)
    } else if (filter(full)) {
      out.push(full)
    }
  }
  return out
}

const VUE_FILES = walk(SRC, [], (f) => f.endsWith('.vue'))
const CSS_FILES = walk(STYLES, [], (f) => f.endsWith('.css'))

/* ------------------------------------------------------------------ */
/* 通用工具                                                            */
/* ------------------------------------------------------------------ */

/** 全部问题收集（检查 1 / 2 / 3 共用） */
const errors = []

function lineOf(text, index) {
  return text.slice(0, index).split('\n').length
}

function rel(file) {
  return relative(ROOT, file).split(sep).join('/')
}

/* ------------------------------------------------------------------ */
/* 检查 1：筛选区必须使用统一筛选栏                                    */
/* ------------------------------------------------------------------ */

/**
 * 判断一个 .vue 文件是否含筛选区。
 * 判据：出现历史筛选类名，或出现 <SpdFilterBar，或出现统一筛选栏 CSS 类。
 */
function detectFilterUsage(text) {
  const legacyHits = []
  for (const cls of LEGACY_FILTER_CLASSES) {
    // 只匹配「作为 class 使用」的位置，排除纯 CSS 选择器文本
    const re = new RegExp(`class\\s*=\\s*"[^"]*\\b${cls.replace(/[-]/g, '\\-')}\\b[^"]*"`, 'g')
    let m
    while ((m = re.exec(text)) !== null) {
      legacyHits.push({ cls, index: m.index })
    }
  }
  const componentIndex = text.indexOf(COMPONENT_TAG)
  const unifiedClassIndex = UNIFIED_CLASSES.reduce(
    (acc, cls) => (acc === -1 ? text.indexOf(cls) : acc),
    -1
  )
  return { legacyHits, componentIndex, unifiedClassIndex }
}

/** 提取筛选栏内的操作按钮，判断是否都是 SpdButton */
function checkFilterButtons(text, file, errors) {
  // 找出所有 <SpdFilterBar ...> ... </SpdFilterBar> 区块
  const openRe = /<SpdFilterBar\b[\s\S]*?>/g
  let m
  while ((m = openRe.exec(text)) !== null) {
    const start = m.index
    // 找到对应的结束标签
    const closeIdx = text.indexOf('</SpdFilterBar>', start)
    const end = closeIdx === -1 ? text.length : closeIdx
    const block = text.slice(start, end)

    // 允许裸 <button>？不允许。找出所有按钮标签
    const btnRe = /<button\b/g
    let b
    while ((b = btnRe.exec(block)) !== null) {
      errors.push({
        file: rel(file),
        line: lineOf(text, start + b.index),
        rule: 'filter-buttons',
        message: '筛选栏内出现裸 <button>，必须使用 <SpdButton>',
      })
    }
    // .btn / .el-button 类名
    const legacyBtnRe = /class\s*=\s*"[^"]*\b(btn|el-button)\b[^"]*"/g
    let l
    while ((l = legacyBtnRe.exec(block)) !== null) {
      errors.push({
        file: rel(file),
        line: lineOf(text, start + l.index),
        rule: 'filter-buttons',
        message: '筛选栏内出现 .btn / .el-button，按钮必须使用 <SpdButton>',
      })
    }

    /* ---- 检查 4b：按钮顺序必须是「重置」→「查询」 ---- */
    // 取出操作区插槽内的按钮 label，按出现顺序排列
    const actionsMatch = block.match(/<template\s+#actions\s*>([\s\S]*?)<\/template>/)
    if (actionsMatch) {
      const labels = []
      const labelRe = /<SpdButton\b[^>]*?\blabel\s*=\s*"([^"]*)"/g
      let lb
      while ((lb = labelRe.exec(actionsMatch[1])) !== null) {
        labels.push({ text: lb[1], index: lb.index })
      }
      const resetIdx = labels.findIndex((x) => x.text === '重置')
      const submitIdx = labels.findIndex((x) => x.text === '查询' || x.text.endsWith('查询'))
      if (resetIdx !== -1 && submitIdx !== -1 && resetIdx > submitIdx) {
        errors.push({
          file: rel(file),
          line: lineOf(text, start + actionsMatch.index),
          rule: 'filter-button-order',
          message:
            '筛选栏操作区按钮顺序必须是「重置」→「查询」（查询在右为主动作），' +
            `当前为「${labels.map((x) => x.text).join(' → ')}」`,
        })
      }
    }
  }
}

/* ------------------------------------------------------------------ */
/* 检查 5：页面不得覆写统一筛选栏的列数 / 换行规则                      */
/* ------------------------------------------------------------------ */

/**
 * 统一筛选栏的列数规则只能来自 styles/components/spd-filter-bar.css。
 * 任何页面在 scoped 样式里写 `.spd-filter-bar__fields { grid-template-columns: ... }`
 * 都会让「多宽换行」重新分叉，必须禁止。
 */
function checkGridOverrides(text, file, errors) {
  const ruleRe = /([^{}]*)\{([^{}]*)\}/g
  let m
  while ((m = ruleRe.exec(text)) !== null) {
    const selector = m[1].trim()
    if (!selector.includes('spd-filter-bar__fields')) continue
    const body = m[2]
    if (!/grid-template-columns\s*:/.test(body)) continue
    errors.push({
      file: rel(file),
      line: lineOf(text, m.index + m[1].length),
      rule: 'filter-grid-override',
      message:
        '页面覆写了 .spd-filter-bar__fields 的 grid-template-columns，' +
        '列数 / 换行规则必须统一由 styles/components/spd-filter-bar.css 提供',
    })
  }
}

/* ------------------------------------------------------------------ */
/* 检查 2 / 3：筛选样式的字号与高度                                    */
/* ------------------------------------------------------------------ */

/** 判断一个 CSS 选择器块是否与「筛选控件」相关 */function isFilterSelector(selector) {
  const s = selector.toLowerCase()
  if (s.includes('spd-filter-bar')) return true
  if (s.includes('ulc-filter')) return true
  if (s.includes('hospital-query-grid')) return true
  if (s.includes('md-hospital-filter')) return true
  if (s.includes('md-hospital-filter-actions')) return true
  return false
}

/** 取出 CSS 规则块里的声明 */
function parseDeclarations(body) {
  return body
    .split(';')
    .map((d) => d.trim())
    .filter(Boolean)
}

for (const file of CSS_FILES) {
  const text = readFileSync(file, 'utf8')

  // 逐个规则块扫描（简易 CSS 解析，足够覆盖本仓库的写法）
  const ruleRe = /([^{}]+)\{([^{}]*)\}/g
  let m
  while ((m = ruleRe.exec(text)) !== null) {
    const selector = m[1].trim()
    const body = m[2]
    if (!isFilterSelector(selector)) continue

    // 嵌套 @media 的规则其选择器里可能带前缀，仍按整块判断
    for (const decl of parseDeclarations(body)) {
      const idx = decl.indexOf(':')
      if (idx === -1) continue
      const prop = decl.slice(0, idx).trim().toLowerCase()
      const value = decl.slice(idx + 1).trim()

      /* ---- 检查 2：不得写死 font-size: NNpx ---- */
      if (prop === 'font-size') {
        const pxMatch = value.match(/^(\d+(?:\.\d+)?)px$/)
        if (pxMatch) {
          errors.push({
            file: rel(file),
            line: lineOf(text, m.index + m[1].length),
            rule: 'filter-font-size',
            message: `筛选样式写死了 font-size: ${pxMatch[0]}，必须使用 var(--spd-font-size-*) 令牌`,
          })
        }
      }

      /* ---- 检查 3：不得写死与标准高度不同的 px 高度 ---- */
      if (prop === 'height' || prop === 'min-height' || prop === 'max-height') {
        const pxMatch = value.match(/^(\d+(?:\.\d+)?)px$/)
        if (pxMatch) {
          const px = Number(pxMatch[1])
          // 允许引用令牌本身
          if (value.includes(FIELD_HEIGHT_TOKEN)) {
            // 已是令牌引用，放行
          } else if (px !== FIELD_HEIGHT_PX) {
            errors.push({
              file: rel(file),
              line: lineOf(text, m.index + m[1].length),
              rule: 'filter-height',
              message:
                `筛选样式写死了 ${prop}: ${pxMatch[0]}，与标准 ${FIELD_HEIGHT_TOKEN}` +
                `（${FIELD_HEIGHT_PX}px）不一致，必须引用令牌`,
            })
          }
        }
      }
    }
  }
}

/* ------------------------------------------------------------------ */
/* 汇总                                                                */
/* ------------------------------------------------------------------ */

for (const file of VUE_FILES) {
  const text = readFileSync(file, 'utf8')
  const usage = detectFilterUsage(text)

  if (usage.legacyHits.length > 0) {
    for (const hit of usage.legacyHits) {
      if (ALLOWED_LEGACY_SELECTORS.has(hit.cls)) continue
      errors.push({
        file: rel(file),
        line: lineOf(text, hit.index),
        rule: 'filter-component',
        message:
          `仍使用历史筛选栅格类「${hit.cls}」，必须改用统一筛选栏组件 <SpdFilterBar>` +
          `（或统一 CSS 类 .spd-filter-bar）`,
      })
    }
  }

  // 有筛选区但既没用组件也没用统一类 → 报错
  const hasFilterArea =
    usage.legacyHits.length > 0 ||
    usage.componentIndex !== -1 ||
    usage.unifiedClassIndex !== -1
  if (hasFilterArea && usage.componentIndex === -1 && usage.unifiedClassIndex === -1) {
    errors.push({
      file: rel(file),
      line: 1,
      rule: 'filter-component',
      message: '筛选区未使用统一筛选栏组件 <SpdFilterBar>，也未挂统一 CSS 类 .spd-filter-bar',
    })
  }

  if (usage.componentIndex !== -1) {
    checkFilterButtons(text, file, errors)
  }

  // 检查 5：页面不得覆写统一筛选栏的列数 / 换行规则
  checkGridOverrides(text, file, errors)
}

/* ------------------------------------------------------------------ */
/* 检查 6：筛选栏栅格参数必须与调色板令牌同步                              */
/* ------------------------------------------------------------------ */
/**
 * 断点值写在 @media 条件里（CSS 不支持 var()），
 * 因此必须与 tokens/palette.css 的 --spd-filter-narrow-breakpoint 保持一致。
 * 同时，筛选栏的栅格参数只能在 palette.css 声明一次，
 * 组件样式表不得重复定义（否则两处会各自漂移）。
 */
const PALETTE_FILE = join(ROOT, 'frontend/src/styles/tokens/palette.css')
const FILTER_CSS_FILE = join(ROOT, 'frontend/src/styles/components/spd-filter-bar.css')

if (existsSync(PALETTE_FILE) && existsSync(FILTER_CSS_FILE)) {
  const paletteText = readFileSync(PALETTE_FILE, 'utf8')
  const filterCssText = readFileSync(FILTER_CSS_FILE, 'utf8')

  /* 6a. 令牌必须在 palette.css 中声明 */
  const bpMatch = /--spd-filter-narrow-breakpoint\s*:\s*(\d+(?:\.\d+)?)px/.exec(paletteText)
  if (!bpMatch) {
    errors.push({
      file: rel(PALETTE_FILE),
      line: 1,
      rule: 'filter-token-sync',
      message:
        'tokens/palette.css 未声明 --spd-filter-narrow-breakpoint，' +
        '筛选栏栅格参数必须集中在调色板中',
    })
  } else {
    /* 6b. 媒体查询里的字面量必须与令牌一致 */
    const mediaRe = /@media\s*\(max-width:\s*(\d+(?:\.\d+)?)px\)/g
    let m
    while ((m = mediaRe.exec(filterCssText)) !== null) {
      if (m[1] !== bpMatch[1]) {
        errors.push({
          file: rel(FILTER_CSS_FILE),
          line: lineOf(filterCssText, m.index),
          rule: 'filter-token-sync',
          message:
            `媒体查询断点 ${m[1]}px 与调色板令牌 ` +
            `--spd-filter-narrow-breakpoint (${bpMatch[1]}px) 不一致`,
        })
      }
    }
  }

  /* 6c. 组件样式表不得重复声明这些令牌 */
  const dupRe = /--spd-filter-(min-width|gap-x|gap-y|actions-gap|narrow-breakpoint)\s*:/g
  let dup
  while ((dup = dupRe.exec(filterCssText)) !== null) {
    errors.push({
      file: rel(FILTER_CSS_FILE),
      line: lineOf(filterCssText, dup.index),
      rule: 'filter-token-sync',
      message:
        `筛选栏栅格参数 --spd-filter-${dup[1]} 在组件样式表中重复声明，` +
        '唯一出处应为 tokens/palette.css',
    })
  }
}

/* ------------------------------------------------------------------ */
/* 输出                                                                */
/* ------------------------------------------------------------------ */

if (errors.length > 0) {
  console.error('\n❌ 统一筛选栏回归检查未通过：\n')
  const byFile = new Map()
  for (const e of errors) {
    if (!byFile.has(e.file)) byFile.set(e.file, [])
    byFile.get(e.file).push(e)
  }
  for (const [file, list] of byFile) {
    console.error(`  ${file}`)
    for (const e of list) {
      console.error(`    :${e.line}  [${e.rule}] ${e.message}`)
    }
  }
  console.error(`\n共 ${errors.length} 处问题。\n`)
  process.exit(1)
}

console.log('\n✅ 统一筛选栏回归检查通过')
console.log(`   扫描 .vue 文件：${VUE_FILES.length} 个`)
console.log(`   扫描样式文件：${CSS_FILES.length} 个`)
console.log('   规则：统一组件 / 字号令牌 / 高度令牌 / SpdButton 按钮 / 按钮顺序 / 换行规则唯一\n')
