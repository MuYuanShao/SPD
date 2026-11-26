#!/usr/bin/env node
/**
 * 按钮一致性回归闸门（verify:button-consistency）
 * =====================================================================
 * 产品硬约束：**相同文本的按钮，全站必须像素级一致**。
 * 本脚本把这条约束变成可执行的检查：
 *
 *   1. 扫描 frontend/src 下所有 .vue 文件，抽取每个 <SpdButton ...> 起始标签；
 *   2. 解析它的「有效样式」：
 *        - 显式传了 tone / variant / size → 用显式值；
 *        - 没传 → 用 config/buttonStyle.ts 的注册表按 label 推导；
 *   3. 按按钮文本分组，同一文本必须只有一个 (tone, variant, size) 组合；
 *   4. 每个 <SpdButton> 必须有 label 或显式 tone（不允许静默回落）；
 *   5. 任何 .vue 文件都不允许在按钮相关选择器里写死 font-size: NNpx。
 *
 * 失败时打印详细报告（file:line）并以非 0 退出码结束。
 *
 * 用法：node scripts/verify-button-consistency.mjs
 */

import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = fileURLToPath(new URL('.', import.meta.url))
const ROOT = join(__dirname, '..')
const SRC = join(ROOT, 'frontend', 'src')
const REGISTRY_FILE = join(SRC, 'config', 'buttonStyle.ts')

/* ------------------------------------------------------------------ */
/* 读取注册表（直接解析 buttonStyle.ts，避免引入 TS 编译步骤）         */
/* ------------------------------------------------------------------ */

function readRegistry() {
  const raw = readFileSync(REGISTRY_FILE, 'utf8')
  const body = raw.slice(raw.indexOf('BUTTON_STYLE_BY_LABEL'))
  const start = body.indexOf('{')
  let depth = 0
  let end = -1
  for (let i = start; i < body.length; i += 1) {
    const ch = body[i]
    if (ch === '{') depth += 1
    else if (ch === '}') {
      depth -= 1
      if (depth === 0) {
        end = i
        break
      }
    }
  }
  if (end === -1) throw new Error('无法解析 buttonStyle.ts 的 BUTTON_STYLE_BY_LABEL')
  const mapText = body.slice(start, end + 1)

  const registry = new Map()
  const entryRe =
    /(["'])([^"']*)\1\s*:\s*\{\s*tone\s*:\s*'([a-z]+)'\s*,\s*variant\s*:\s*'([a-z]+)'\s*,\s*size\s*:\s*'([a-z]+)'\s*\}|([\w\u4e00-\u9fa5$]+)\s*:\s*\{\s*tone\s*:\s*'([a-z]+)'\s*,\s*variant\s*:\s*'([a-z]+)'\s*,\s*size\s*:\s*'([a-z]+)'\s*\}/g
  let m
  while ((m = entryRe.exec(mapText)) !== null) {
    if (m[2] !== undefined) {
      registry.set(m[2], { tone: m[3], variant: m[4], size: m[5] })
    } else {
      registry.set(m[6], { tone: m[7], variant: m[8], size: m[9] })
    }
  }
  if (registry.size === 0) throw new Error('buttonStyle.ts 注册表解析为空')
  return registry
}

const REGISTRY = readRegistry()
const DEFAULT_STYLE = { tone: 'primary', variant: 'solid', size: 'default' }

/* ------------------------------------------------------------------ */
/* 遍历 .vue 文件                                                      */
/* ------------------------------------------------------------------ */

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    const st = statSync(full)
    if (st.isDirectory()) {
      if (entry === 'node_modules' || entry === 'dist') continue
      walk(full, out)
    } else if (entry.endsWith('.vue')) {
      out.push(full)
    }
  }
  return out
}

const vueFiles = walk(SRC).sort()

/* ------------------------------------------------------------------ */
/* 抽取 <SpdButton ...> 标签                                            */
/* ------------------------------------------------------------------ */

/** 解析属性串为 Map（支持 :prop / v-bind:prop / prop） */
function parseAttrs(attrText) {
  const attrs = new Map()
  const re = /([:@]?[A-Za-z_][\w:.-]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+)))?/g
  let m
  while ((m = re.exec(attrText)) !== null) {
    attrs.set(m[1], m[2] ?? m[3] ?? m[4] ?? null)
  }
  return attrs
}

/** 静态属性值 → 字面量；动态绑定（:xxx / v-bind:xxx）→ null */
function staticValue(attrs, name) {
  if (attrs.has(name)) return attrs.get(name)
  const bound = attrs.get(`:${name}`) ?? attrs.get(`v-bind:${name}`)
  if (bound !== undefined && bound !== null) return null
  return undefined
}

function isDynamic(attrs, name) {
  return attrs.has(`:${name}`) || attrs.has(`v-bind:${name}`)
}

function extractButtons(source, file) {
  const results = []
  const openRe = /<SpdButton\b([^>]*?)(\/?)>/g
  let m
  while ((m = openRe.exec(source)) !== null) {
    const attrText = m[1] || ''
    const selfClosing = m[2] === '/'
    const startIndex = m.index
    const line = source.slice(0, startIndex).split('\n').length

    let innerText = null
    let endIndex = m.index + m[0].length
    if (!selfClosing) {
      const closeIdx = source.indexOf('</SpdButton>', endIndex)
      if (closeIdx !== -1) {
        innerText = source.slice(endIndex, closeIdx)
        endIndex = closeIdx + '</SpdButton>'.length
      }
    }

    const attrs = parseAttrs(attrText)

    results.push({
      file,
      line,
      attrs,
      labelProp: staticValue(attrs, 'label'),
      labelIsDynamic: isDynamic(attrs, 'label'),
      toneProp: staticValue(attrs, 'tone'),
      toneIsDynamic: isDynamic(attrs, 'tone'),
      variantProp: staticValue(attrs, 'variant'),
      variantIsDynamic: isDynamic(attrs, 'variant'),
      sizeProp: staticValue(attrs, 'size'),
      sizeIsDynamic: isDynamic(attrs, 'size'),
      iconOnly: staticValue(attrs, 'icon-only') ?? staticValue(attrs, ':icon-only'),
      ariaLabel: staticValue(attrs, 'aria-label'),
      innerText,
      selfClosing
    })
  }
  return results
}

/** 去掉插槽里的 HTML 标签与插值，得到「简单字面量文本」 */
function slotLiteralText(inner) {
  if (inner == null) return null
  const cleaned = inner.replace(/<!--[\s\S]*?-->/g, '')
  const stripped = cleaned.replace(/<[^>]*>/g, '')
  if (stripped.includes('{{') || stripped.includes('}}')) return null
  if (/<[A-Z]/.test(cleaned)) return null
  const text = stripped.replace(/\s+/g, ' ').trim()
  if (!text) return null
  if (!/^[\u4e00-\u9fa5A-Za-z0-9 \u3000-\u303f\uff00-\uffef()（）%+\-/·、，。：；！？,.:;!?[\]"'/_]+$/.test(text)) {
    return null
  }
  return text
}

/* ------------------------------------------------------------------ */
/* 解析有效样式                                                        */
/* ------------------------------------------------------------------ */

function effectiveStyle(btn) {
  const reg = REGISTRY.get((btn.labelProp ?? '').trim())
  const fallback = reg ?? DEFAULT_STYLE
  return {
    tone: btn.toneProp ?? fallback.tone,
    variant: btn.variantProp ?? fallback.variant,
    size: btn.sizeProp ?? fallback.size
  }
}

/* ------------------------------------------------------------------ */
/* 收集                                                               */
/* ------------------------------------------------------------------ */

const allButtons = []
for (const file of vueFiles) {
  const source = readFileSync(file, 'utf8')
  const relPath = relative(ROOT, file).split(sep).join('/')
  // 组件自身的文档示例不是真实调用点，跳过。
  if (relPath === 'frontend/src/components/common/SpdButton.vue') continue
  allButtons.push(...extractButtons(source, relPath))
}

/** @type {Map<string, Array<object>>} */
const groups = new Map()
const problems = []

for (const btn of allButtons) {
  const text = btn.labelIsDynamic ? null : (btn.labelProp ?? slotLiteralText(btn.innerText))
  const style = effectiveStyle(btn)
  const explicit = [
    btn.toneProp ? `tone=${btn.toneProp}` : null,
    btn.variantProp ? `variant=${btn.variantProp}` : null,
    btn.sizeProp ? `size=${btn.sizeProp}` : null
  ].filter(Boolean)

  if (text === null) continue

  if (!groups.has(text)) groups.set(text, [])
  groups.get(text).push({
    file: btn.file,
    line: btn.line,
    style,
    explicit
  })
}

for (const [text, items] of groups) {
  if (items.length < 2) continue
  const sigs = new Set(items.map((i) => `${i.style.tone}|${i.style.variant}|${i.style.size}`))
  if (sigs.size > 1) {
    problems.push({ kind: 'conflict', text, items, sigs: [...sigs] })
  }
}

/* ------------------------------------------------------------------ */
/* 检查 2：必须有 label 或显式 tone                                      */
/* ------------------------------------------------------------------ */

const missingIdentity = []
for (const btn of allButtons) {
  const hasLabel = btn.labelProp != null
  const hasSlotText = slotLiteralText(btn.innerText) != null
  const hasTone = btn.toneProp != null || btn.toneIsDynamic
  // 纯图标按钮没有文本可依据，也不需要 label —— 用 aria-label 承载语义即可。
  const isIconOnly = btn.iconOnly === true || btn.iconOnly === 'true'
  const hasAriaLabel = btn.ariaLabel != null
  if (!hasLabel && !hasTone) {
    // 无文本可推导 tone 的按钮，必须显式给出 tone 或（纯图标）aria-label，
    // 否则会静默回落到默认主色，破坏「相同文本同款」的可预期性。
    if (isIconOnly && hasAriaLabel) continue
    missingIdentity.push(btn)
  } else if (!hasLabel && !hasSlotText && btn.innerText != null && btn.innerText.trim() !== '') {
    // 有可见文本但无法解析成字面量（动态插值）→ 必须显式 tone
    if (!hasTone) missingIdentity.push(btn)
  }
}

/* ------------------------------------------------------------------ */
/* 检查 3：.vue 里按钮相关选择器不得写死 font-size                       */
/* ------------------------------------------------------------------ */

const FONT_SIZE_RE = /font-size\s*:\s*([0-9]+(?:\.[0-9]+)?)px/i
const BUTTON_SELECTOR_HINTS = [
  'btn',
  'button',
  'action',
  'close',
  'submit',
  'cancel',
  'confirm',
  'toggle',
  'pill',
  'chip',
  'toolbar'
]

const fontSizeViolations = []
for (const file of vueFiles) {
  const source = readFileSync(file, 'utf8')
  const relPath = relative(ROOT, file).split(sep).join('/')

  const styleStart = source.indexOf('<style')
  if (styleStart === -1) continue
  const styleEnd = source.lastIndexOf('</style>')
  const styleBlock = source.slice(styleStart, styleEnd === -1 ? source.length : styleEnd)
  const styleOffset = source.slice(0, styleStart).split('\n').length - 1
  const styleLines = styleBlock.split('\n')

  let selector = ''
  let depth = 0
  let inStyle = false
  for (let i = 0; i < styleLines.length; i += 1) {
    const line = styleLines[i]
    const absLine = styleOffset + i + 1

    if (!inStyle) {
      if (line.includes('{')) {
        selector = line.slice(0, line.indexOf('{')).trim()
        depth = 1
        inStyle = true
      }
      continue
    }

    if (line.includes('{')) depth += line.split('{').length - 1
    if (line.includes('}')) depth -= line.split('}').length - 1

    const fm = line.match(FONT_SIZE_RE)
    if (fm && depth === 1) {
      const sel = selector.toLowerCase()
      if (BUTTON_SELECTOR_HINTS.some((h) => sel.includes(h))) {
        fontSizeViolations.push({
          file: relPath,
          line: absLine,
          selector: selector.replace(/\s+/g, ' '),
          value: fm[1]
        })
      }
    }

    if (depth <= 0) {
      inStyle = false
      selector = ''
    }
  }
}

/* ------------------------------------------------------------------ */
/* 报告                                                               */
/* ------------------------------------------------------------------ */

let exitCode = 0

console.log('='.repeat(72))
console.log('按钮一致性回归检查 (verify:button-consistency)')
console.log('='.repeat(72))
console.log(`扫描目录      : frontend/src`)
console.log(`.vue 文件数   : ${vueFiles.length}`)
console.log(`<SpdButton> 数: ${allButtons.length}`)
console.log(`注册表条目数  : ${REGISTRY.size}`)
console.log('')

if (problems.length > 0) {
  exitCode = 1
  console.log(`❌ 冲突：${problems.length} 组同文本按钮使用了不同的样式组合`)
  console.log('')
  for (const p of problems) {
    console.log(`  文本：「${p.text}」`)
    console.log(`  冲突组合：${p.sigs.join('  /  ')}`)
    for (const item of p.items) {
      const explicitNote = item.explicit.length ? `  [显式 ${item.explicit.join(' ')}]` : '  [注册表推导]'
      console.log(
        `    - ${item.file}:${item.line}  ${item.style.tone}/${item.style.variant}/${item.style.size}${explicitNote}`
      )
    }
    console.log('')
  }
} else {
  console.log('✅ 无冲突：同文本按钮的 (tone, variant, size) 完全一致')
}

if (missingIdentity.length > 0) {
  exitCode = 1
  console.log(`❌ ${missingIdentity.length} 个 <SpdButton> 既没有 label 也没有显式 tone（静默回落）`)
  for (const btn of missingIdentity) {
    console.log(`    - ${btn.file}:${btn.line}  attrs=${JSON.stringify(Object.fromEntries(btn.attrs))}`)
  }
  console.log('')
} else {
  console.log('✅ 每个 <SpdButton> 都有 label 或显式 tone')
}

if (fontSizeViolations.length > 0) {
  exitCode = 1
  console.log(`❌ ${fontSizeViolations.length} 处按钮相关选择器写死了 font-size（应使用字号层级令牌）`)
  for (const v of fontSizeViolations) {
    console.log(`    - ${v.file}:${v.line}  ${v.selector}  →  font-size: ${v.value}px`)
  }
  console.log('')
} else {
  console.log('✅ 按钮相关选择器没有写死 font-size')
}

console.log('')
console.log('-'.repeat(72))
if (exitCode === 0) {
  console.log('✅ 通过：按钮样式注册表与全部调用点一致')
} else {
  console.log('❌ 未通过：请修复上述问题后重跑')
}
console.log('-'.repeat(72))

process.exit(exitCode)
