#!/usr/bin/env node
/**
 * apply-regex-color-edits.mjs
 * -----------------------------------------------------------------------
 * 用正则整块替换 CSS 规则（用于 el-button 等多行变体块）。
 * 读取 .color-token-regex.json 中的规则，逐条应用并写回 UTF-8。
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const ROOT = resolve(process.cwd())
const STYLE_DIR = resolve(ROOT, 'frontend/src/styles')
const spec = JSON.parse(readFileSync(resolve(ROOT, '.color-token-regex.json'), 'utf8'))

for (const entry of spec.entries) {
  const file = resolve(STYLE_DIR, entry.file)
  let text = readFileSync(file, 'utf8')
  let count = 0
  for (const rule of entry.rules) {
    const re = new RegExp(rule.re, rule.flags ?? 's')
    if (!re.test(text)) {
      console.error(`[MISS] ${entry.file}: ${rule.re}`)
      continue
    }
    text = text.replace(re, rule.rep)
    count += 1
  }
  if (count > 0) {
    writeFileSync(file, text, 'utf8')
    console.log(`[OK]   ${entry.file}: ${count} rule(s)`)
  }
}
