/**
 * 按钮几何覆盖回归检查 (verify:button-geometry)
 * =============================================================================
 * 目标：
 *   SpdButton 是全站唯一的按钮组件，其几何（高度 / 内边距 / 字号 / 圆角）
 *   由 styles/tokens/palette.css 的控件令牌统一决定。
 *   **页面 scoped 样式不得再覆盖这些几何属性**，否则会出现
 *   「同页按钮一个 34px 一个 40px」「同功能按钮字号不同」等漂移。
 *
 * 允许的例外（白名单，需在下方显式登记）：
 *   - 只覆盖布局属性（margin / justify-self / gap / flex / position 等）
 *   - 覆盖 width / min-width 但仅用于「与输入框贴合的组合按钮」形态
 *   - 覆盖 height: 100%（同样只用于组合按钮）
 *
 * 检查项：
 *   1. 扫描所有 frontend/src 下的 .vue 文件的 <style> 块；
 *   2. 找出所有 <SpdButton class="..."> 上用到的自定义 class；
 *   3. 若这些 class 的规则里出现几何属性且不在白名单 → 报错。
 * =============================================================================
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative, sep } from 'node:path'

const ROOT = process.cwd()
const SRC = join(ROOT, 'frontend/src')

/* ------------------------------------------------------------------ */
/* 白名单：class → 允许覆盖的属性集合                                 */
/* ------------------------------------------------------------------ */
const ALLOWED_OVERRIDES = {
  // 与输入框贴合的「组合按钮」：宽度跟随输入框高度，需要覆盖 width / height / 圆角
  'search-trigger': ['width', 'height', 'border-radius', 'border-top-right-radius', 'border-bottom-right-radius'],
  // 定数包操作按钮：需要更宽的点击区域
  'quota-action-sign': ['min-width'],
  'quota-action-consume': ['min-width'],
  // 库存查询按钮：作为 grid item 不能被拉伸到整列
  'inventory-query-button': ['width', 'min-width']
}

/** 被视为「几何漂移」的属性 */
const GEOMETRY_PROPS = [
  'font-size',
  'font-weight',
  'line-height',
  'height',
  'min-height',
  'max-height',
  'padding',
  'padding-top',
  'padding-right',
  'padding-bottom',
  'padding-left',
  'padding-block',
  'padding-inline',
  'border-width',
  'border-radius'
]

function collectVueFiles(dir, acc = []) {
  for (const entry of readdirSync(dir)) {
    const p = join(dir, entry)
    if (statSync(p).isDirectory()) collectVueFiles(p, acc)
    else if (p.endsWith('.vue')) acc.push(p)
  }
  return acc
}

const vueFiles = collectVueFiles(SRC)
const problems = []

for (const file of vueFiles) {
  const source = readFileSync(file, 'utf8')
  const relPath = relative(ROOT, file).split(sep).join('/')

  /* 1. 收集 SpdButton 上用到的自定义 class */
  const buttonClasses = new Set()
  const tagRe = /<SpdButton\b([^>]*)>/g
  let tagMatch
  while ((tagMatch = tagRe.exec(source)) !== null) {
    const classMatch = /\bclass="([^"]*)"/.exec(tagMatch[1])
    if (!classMatch) continue
    for (const cls of classMatch[1].split(/\s+/)) {
      if (!cls) continue
      // 组件自身的类与状态类不算「页面自定义覆盖」
      if (cls.startsWith('spd-') || cls.startsWith('is-')) continue
      buttonClasses.add(cls)
    }
  }
  if (buttonClasses.size === 0) continue

  /* 2. 只看 <style> 块 */
  const styleStart = source.indexOf('<style')
  if (styleStart === -1) continue
  const style = source.slice(styleStart)

  for (const cls of buttonClasses) {
    const allowed = ALLOWED_OVERRIDES[cls] ?? []
    const ruleRe = new RegExp(`\\.${cls}(?![\\w-])[^{}]*\\{([^}]*)\\}`, 'g')
    let ruleMatch
    while ((ruleMatch = ruleRe.exec(style)) !== null) {
      // 只检查「作用在该 class 自身」的规则；
      // `.cls svg` / `.cls .child` / `.ancestor .cls svg` 这类选择器
      // 作用在子元素上，不算覆盖按钮几何。
      const selector = ruleMatch[0].slice(0, ruleMatch[0].indexOf('{')).trim()
      const baseSelector = selector.split(/[\s,>]+/).filter(Boolean).pop() ?? ''
      if (baseSelector !== `.${cls}`) continue

      const body = ruleMatch[1]
      const line = style.slice(0, ruleMatch.index).split('\n').length
      for (const prop of GEOMETRY_PROPS) {
        const propRe = new RegExp(`(^|[;{\\s])${prop}\\s*:`, 'm')
        if (!propRe.test(body)) continue
        if (allowed.includes(prop)) continue
        problems.push({
          file: relPath,
          line,
          cls,
          prop,
          snippet: body.trim().split('\n')[0].slice(0, 90)
        })
      }
    }
  }
}

/* ------------------------------------------------------------------ */
/* 输出                                                                */
/* ------------------------------------------------------------------ */
console.log('='.repeat(72))
console.log('按钮几何覆盖回归检查 (verify:button-geometry)')
console.log('='.repeat(72))
console.log('扫描目录      : frontend/src')
console.log('.vue 文件数   :', vueFiles.length)
console.log('')

if (problems.length > 0) {
  console.log(`❌ ${problems.length} 处页面样式覆盖了 SpdButton 的统一几何`)
  for (const p of problems) {
    console.log(`    - ${p.file}:${p.line}  .${p.cls} → ${p.prop}`)
    console.log(`        ${p.snippet}`)
  }
  console.log('')
  console.log('修复方式：删除该覆盖（几何由 SpdButton + palette.css 统一提供），')
  console.log('或把确实必要的项登记到 scripts/verify-button-geometry.mjs 的 ALLOWED_OVERRIDES。')
  console.log('')
  console.log('-'.repeat(72))
  console.log('❌ 未通过：存在按钮几何漂移')
  process.exit(1)
}

console.log('✅ 页面样式未覆盖 SpdButton 的统一几何（高度 / 内边距 / 字号 / 圆角）')
console.log('')
console.log('-'.repeat(72))
console.log('✅ 通过')
