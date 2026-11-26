#!/usr/bin/env node
/**
 * apply-color-token-edits.mjs
 * -----------------------------------------------------------------------
 * 本脚本是**一次性辅助工具**：把「颜色杂色 → 调色板令牌」的替换集中执行，
 * 避免在 PowerShell 里手工改 UTF-8 中文文件时把编码改坏。
 *
 * 做法：
 *   1. 读取目标 CSS（UTF-8，保留 BOM 状态不变）；
 *   2. 按 RULES 里的有序规则做字面量替换；
 *   3. 写回（UTF-8，无 BOM）。
 *
 * 用法：node scripts/apply-color-token-edits.mjs
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const ROOT = resolve(process.cwd())
const STYLE_DIR = resolve(ROOT, 'frontend/src/styles')

/** @type {Array<{file: string, rules: Array<[string, string]>, optional?: boolean}>} */
const RULES = JSON.parse(readFileSync(resolve(ROOT, '.color-token-edits.json'), 'utf8')).entries

let totalReplaced = 0

for (const entry of RULES) {
  const file = resolve(STYLE_DIR, entry.file)
  let text = readFileSync(file, 'utf8')
  let fileReplaced = 0
  for (const [from, to] of entry.rules) {
    if (!text.includes(from)) {
      if (!entry.optional) {
        console.error(`[MISS] ${entry.file}: ${JSON.stringify(from).slice(0, 90)}`)
      }
      continue
    }
    const parts = text.split(from)
    fileReplaced += parts.length - 1
    text = parts.join(to)
  }
  if (fileReplaced > 0) {
    writeFileSync(file, text, 'utf8')
    console.log(`[OK]   ${entry.file}: ${fileReplaced} replacement(s)`)
    totalReplaced += fileReplaced
  } else {
    console.log(`[SKIP] ${entry.file}: no change`)
  }
}

console.log(`\nTotal replacements: ${totalReplaced}`)
