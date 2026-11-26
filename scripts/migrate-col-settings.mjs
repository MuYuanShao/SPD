#!/usr/bin/env node
/**
 * 一次性迁移脚本：把各列表页里重复的「列设置浮层」标记
 * 换成共享组件 <SpdColumnSettings>。
 *
 * 迁移前（每个页面各写一份）：
 *   <div v-if="xxx.settingsOpen.value" ref="xxxPopRef"
 *        class="ulc-col-settings-pop"
 *        :class="{ 'is-align-right': ..., 'is-align-up': ... }"
 *        :style="xxxPop.popStyle.value">
 *     <header>…</header>
 *     <div class="ulc-col-settings-list">…</div>
 *     <footer>…</footer>
 *   </div>
 *
 * 迁移后：
 *   <SpdColumnSettings v-if="…" ref="…" :class="…" :style="…"
 *     :columns="…" :visible-column-count="…"
 *     @toggle="…" @move="…" @reset="…" @confirm="…" />
 *
 * 用法：node scripts/tmp-migrate-col-settings.mjs
 */
import { readdirSync, readFileSync, writeFileSync, statSync } from 'node:fs'
import { join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = fileURLToPath(new URL('.', import.meta.url))
const ROOT = join(__dirname, '..')
const SRC = join(ROOT, 'frontend', 'src')

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) walk(full, out)
    else if (full.endsWith('.vue')) out.push(full)
  }
  return out
}

/**
 * 匹配一个完整的列设置浮层块。
 * 结构固定：<div …class="ulc-col-settings-pop"…> header / list / footer </div>
 */
const BLOCK_RE =
  /<div\s[^>]*?class="ulc-col-settings-pop"[\s\S]*?<\/header>[\s\S]*?<\/footer>\s*<\/div>/g

/** 从块里取出 cols 变量名（如 quotaTemplateCols） */
function extractColsVar(block) {
  const m = block.match(/v-for="col in ([A-Za-z0-9_]+)\.columns\.value"/)
  return m ? m[1] : null
}

/** 从块里取出弹层定位 composable 变量名（如 quotaTemplateColSettingsPop） */
function extractPopVar(block) {
  const m = block.match(/:style="([A-Za-z0-9_]+)\.popStyle\.value"/)
  return m ? m[1] : null
}

/** 从块里取出 v-if 的表达式 */
function extractVIf(block) {
  const m = block.match(/v-if="([^"]+)"/)
  return m ? m[1] : null
}

/** 从块里取出 ref 名 */
function extractRef(block) {
  const m = block.match(/ref="([A-Za-z0-9_]+)"/)
  return m ? m[1] : null
}

/** 从块里取出 :class 表达式（去掉外层大括号） */
function extractClassExpr(block) {
  const m = block.match(/:class="\{([^}]*)\}"/)
  return m ? m[1].trim() : null
}

let changed = 0
let replaced = 0
const skipped = []

for (const file of walk(SRC)) {
  const original = readFileSync(file, 'utf8')
  if (!original.includes('ulc-col-settings-pop')) continue
  // 组件自身跳过
  if (file.endsWith('SpdColumnSettings.vue')) continue

  const blocks = original.match(BLOCK_RE)
  if (!blocks || !blocks.length) {
    skipped.push([relative(ROOT, file).split(sep).join('/'), 'no block matched'])
    continue
  }

  let text = original
  let fileReplaced = 0

  for (const block of blocks) {
    const colsVar = extractColsVar(block)
    const popVar = extractPopVar(block)
    const vIf = extractVIf(block)
    const refName = extractRef(block)
    const classExpr = extractClassExpr(block)

    if (!colsVar || !popVar || !vIf || !refName) {
      skipped.push([
        relative(ROOT, file).split(sep).join('/'),
        `cannot parse (cols=${colsVar} pop=${popVar} vIf=${vIf} ref=${refName})`
      ])
      continue
    }

    const attrs = [
      `v-if="${vIf}"`,
      `ref="${refName}"`,
      classExpr ? `:class="{ ${classExpr} }"` : null,
      `:style="${popVar}.popStyle.value"`,
      `:columns="${colsVar}.columns.value"`,
      `:visible-column-count="${colsVar}.visibleColumnCount.value"`,
      `@toggle="${colsVar}.setColumnVisible"`,
      `@move="${colsVar}.moveColumn"`,
      `@reset="${colsVar}.resetColumns()"`,
      `@confirm="${colsVar}.settingsOpen.value = false"`
    ]
      .filter(Boolean)
      .join('\n')

    const replacement = `<SpdColumnSettings\n${attrs}\n          />`
    text = text.replace(block, replacement)
    fileReplaced++
  }

  if (fileReplaced > 0 && text !== original) {
    writeFileSync(file, text, 'utf8')
    changed++
    replaced += fileReplaced
    console.log(`  ${relative(ROOT, file).split(sep).join('/')}  → ${fileReplaced} block(s)`)
  }
}

console.log(`\n替换 ${replaced} 个浮层块，涉及 ${changed} 个文件。`)
if (skipped.length) {
  console.log('\n未能自动解析的块：')
  for (const [f, why] of skipped) console.log(`  ${f}: ${why}`)
}
