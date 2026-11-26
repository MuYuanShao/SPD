#!/usr/bin/env node
/**
 * apply-palette-substitution.mjs
 * ---------------------------------------------------------------------
 * 自动把业务样式里的写死颜色替换为 tokens/palette.css 已登记的语义令牌。
 *
 * 做法：
 *   1. 读取 tokens/colors.css + tokens/palette.css，解析出「已登记颜色 → 令牌名」；
 *   2. 对目标文件里的每个 hex / rgb()，按 RGB 距离找最接近的已登记颜色；
 *   3. 只有距离小于阈值时才替换，避免把「接近但语义不同」的颜色误换；
 *   4. 语义可推断的常见色（主色/成功/警告/危险/信息/中性）走显式映射表，
 *      精确优先于距离。
 *
 * 用法：node scripts/apply-palette-substitution.mjs <file...> [--dry]
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '..');
const TOKENS_DIR = path.join(ROOT, 'frontend', 'src', 'styles', 'tokens');

const args = process.argv.slice(2);
const DRY = args.includes('--dry');
const targets = args.filter((a) => !a.startsWith('--'));
if (targets.length === 0) {
  console.error('usage: node scripts/apply-palette-substitution.mjs <file...> [--dry]');
  process.exit(2);
}

/* ---------------- 解析色板 ---------------- */
function parseHex(hex) {
  let h = hex.slice(1);
  if (h.length === 3 || h.length === 4) {
    h = h.split('').map((c) => c + c).join('');
  }
  return [
    parseInt(h.slice(0, 2), 16),
    parseInt(h.slice(2, 4), 16),
    parseInt(h.slice(4, 6), 16),
  ];
}

function toHex([r, g, b]) {
  return `#${[r, g, b].map((n) => Math.round(n).toString(16).padStart(2, '0')).join('')}`;
}

function rgbToHex(r, g, b) {
  return toHex([r, g, b]);
}

function distance(a, b) {
  // 加权 RGB 距离（人眼对绿色更敏感）
  const rm = (a[0] + b[0]) / 2;
  const dr = a[0] - b[0];
  const dg = a[1] - b[1];
  const db = a[2] - b[2];
  return Math.sqrt(
    (2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db,
  );
}

/** 令牌定义表：hex → 令牌名（人工维护，保证语义正确） */
const EXPLICIT = new Map(Object.entries({
  // 主色系（青玉及其派生）
  '#1f6f6d': '--spd-p-primary',
  '#175a58': '--spd-p-primary-strong',
  '#2b827f': '--spd-p-primary-hover',
  '#0f766e': '--spd-p-primary',
  '#0d6b63': '--spd-p-primary',
  '#0a554e': '--spd-p-primary-strong',
  '#0f3a35': '--spd-p-primary-strong',
  '#e8f1f1': '--spd-p-primary-bg',
  '#eef8f6': '--spd-p-primary-bg',
  '#eef6f5': '--spd-p-primary-bg',
  '#dff3ef': '--spd-p-primary-bg',
  '#d6ebe7': '--spd-p-primary-border',
  '#cde3df': '--spd-p-primary-border',
  '#b7ded7': '--spd-p-primary-border',
  '#f5fbfa': '--spd-p-primary-bg',
  '#087f71': '--spd-p-primary-strong',
  '#d6ebe7 ': '--spd-p-primary-border',

  // 成功
  '#2f7d5b': '--spd-p-success',
  '#059669': '--spd-p-success',
  '#e6f1ec': '--spd-p-success-bg',
  '#e7f8ef': '--spd-p-success-bg',
  '#eaf8f2': '--spd-p-success-bg',
  '#b6d6c6': '--spd-p-success-border',
  '#b7ebd3': '--spd-p-success-border',
  '#bbf7d0': '--spd-p-success-border',
  '#15803d': '--spd-p-success-text',
  '#087a4d': '--spd-p-success-text',
  '#047857': '--spd-p-success-strong',

  // 危险
  '#a33a30': '--spd-p-danger',
  '#dc2626': '--spd-p-danger',
  '#f8ebe9': '--spd-p-danger-bg',
  '#fff1f2': '--spd-p-danger-bg',
  '#ffecec': '--spd-p-danger-bg',
  '#fff0f1': '--spd-p-danger-bg',
  '#fef2f2': '--spd-p-danger-bg',
  '#e0b8b2': '--spd-p-danger-border',
  '#fecaca': '--spd-p-danger-border',
  '#ffc9cf': '--spd-p-danger-border',
  '#b91c1c': '--spd-p-danger-strong',
  '#b83243': '--spd-p-danger-text',
  '#991b1b': '--spd-p-danger-strong',
  '#9f1239': '--spd-p-danger-strong',
  '#e11d48': '--spd-p-danger',
  '#be123c': '--spd-p-danger-strong',

  // 警告
  '#9a6114': '--spd-p-warning',
  '#d97706': '--spd-p-warning',
  '#f59e0b': '--spd-p-warning',
  '#f97316': '--spd-p-warning',
  '#b45309': '--spd-p-warning-text',
  '#a15c00': '--spd-p-warning-text',
  '#fbf1e2': '--spd-p-warning-bg',
  '#fff7e6': '--spd-p-warning-bg',
  '#fffbeb': '--spd-p-warning-bg',
  '#fff4e6': '--spd-p-warning-bg',
  '#fff8e8': '--spd-p-warning-bg',
  '#e6cfa4': '--spd-p-warning-border',
  '#fde68a': '--spd-p-warning-border',
  '#ffe1a8': '--spd-p-warning-border',
  '#fed7aa': '--spd-p-warning-border',
  '#ea580c': '--spd-p-warning-strong',
  '#c2410c': '--spd-p-warning-strong',
  '#855a24': '--spd-p-warning-text',

  // 信息 / 蓝
  '#1f5f7d': '--spd-p-info',
  '#2563eb': '--spd-p-info',
  '#0284c7': '--spd-p-info',
  '#0369a1': '--spd-p-info-text',
  '#4c6274': '--spd-p-info-text',
  '#e9f1f5': '--spd-p-info-bg',
  '#eaf4ff': '--spd-p-info-bg',
  '#eaf1ff': '--spd-p-info-bg',
  '#f0f9ff': '--spd-p-info-bg',
  '#f3f8ff': '--spd-p-info-bg',
  '#f7fbff': '--spd-p-info-bg',
  '#b5ccd9': '--spd-p-info-border',
  '#bae6fd': '--spd-p-info-border',
  '#b9d8ff': '--spd-p-info-border',
  '#91caff': '--spd-p-info-border',
  '#1677ff': '--spd-p-info',
  '#0958d9': '--spd-p-info-strong',
  '#1d4ed8': '--spd-p-info-strong',
  '#4b5870': '--spd-p-info-text',
  '#64748b': '--spd-p-info-text',

  // 文本
  '#101820': '--spd-p-text-strong',
  '#0f172a': '--spd-p-text-strong',
  '#1f2933': '--spd-p-text',
  '#102033': '--spd-p-text',
  '#1b2733': '--spd-p-text',
  '#334652': '--spd-p-text',
  '#06152b': '--spd-p-text-strong',
  '#4a5a66': '--spd-p-text-secondary',
  '#516577': '--spd-p-text-secondary',
  '#647188': '--spd-p-text-secondary',
  '#6b7f91': '--spd-p-text-secondary',
  '#45536b': '--spd-p-text-secondary',
  '#34415a': '--spd-p-text-secondary',
  '#4b5870 ': '--spd-p-info-text',
  '#6b7c88': '--spd-p-text-muted',
  '#7a8699': '--spd-p-text-muted',
  '#7a8797': '--spd-p-text-muted',
  '#8a95a6': '--spd-p-text-muted',
  '#94a3b8': '--spd-p-text-muted',
  '#8b99aa': '--spd-p-text-muted',
  '#8a8377': '--spd-p-text-muted',
  '#857e71': '--spd-p-text-muted',
  '#a8a196': '--spd-p-text-muted',
  '#a15c00 ': '--spd-p-warning-text',
  '#7f94a7': '--spd-p-text-muted',
  '#cbd5e1': '--spd-p-border-strong',
  '#94a3b8 ': '--spd-p-text-muted',
  '#e8edf3': '--spd-p-text',
  '#e8eef1': '--spd-p-border-subtle',
  '#d5e0e5': '--spd-p-border',
  '#b9c8ce': '--spd-p-border-strong',
  '#d0d8dd': '--spd-p-border-strong',
  '#e2e8f0': '--spd-p-border',
  '#e2ddd1': '--spd-p-border',
  '#cbd7dc': '--spd-p-border-strong',

  // 表面
  '#ffffff': '--spd-p-bg',
  '#f8fafc': '--spd-p-bg-muted',
  '#f9fbfb': '--spd-p-bg-muted',
  '#fafbfc': '--spd-p-row-stripe',
  '#fafafa': '--spd-p-row-header-bg',
  '#fbfdfd': '--spd-p-bg',
  '#fafbff': '--spd-p-bg-subtle',
  '#f7fafb': '--spd-p-bg-page',
  '#f5f8f9': '--spd-p-bg-subtle',
  '#eef3f5': '--spd-p-bg-muted',
  '#f6f8fa': '--spd-p-bg-subtle',
  '#f8f9fc': '--spd-p-bg-subtle',
  '#edf2f4': '--spd-p-bg-muted',
  '#eef1f3': '--spd-p-bg-muted',
  '#f1f4f7': '--spd-p-bg-muted',
  '#f2f4f7': '--spd-p-bg-muted',
  '#e2e8f0 ': '--spd-p-border',
  '#e8eef3': '--spd-p-bg-muted',
  '#eef2ec': '--spd-p-success-bg',
  '#f6f0e6': '--spd-p-warning-bg',
  '#f6ebe8': '--spd-p-danger-bg',
  '#eceff2': '--spd-p-info-bg',
  '#eef8f6 ': '--spd-p-primary-bg',
  '#f5f3ff': '--spd-p-bg-subtle',
  '#eef2ff': '--spd-p-info-bg',
  '#fbf9f4': '--spd-p-bg',
  '#f6f3ec': '--spd-p-bg-page',
  '#fdfbf7': '--spd-p-bg',
  '#f2eee5': '--spd-p-bg-subtle',
  '#f1eee7': '--spd-p-bg-muted',
  '#131920': '--spd-p-bg',
  '#0b0f14': '--spd-p-bg-page',
  '#18202a': '--spd-p-bg-subtle',
  '#1e2732': '--spd-p-bg-muted',
  '#161d26': '--spd-p-bg-muted',
  '#141b23': '--spd-p-bg-muted',
  '#121920': '--spd-p-bg-muted',
  '#1d2732': '--spd-p-bg-muted',
  '#222d39': '--spd-p-bg-muted',
  '#10161d': '--spd-p-bg',
  '#151c24': '--spd-p-row-stripe',
  '#1a222c': '--spd-p-field-disabled-bg',
  '#1b232d': '--spd-p-field-disabled-bg',
  '#2c3744': '--spd-p-field-disabled-border',
  '#303b47': '--spd-p-border-strong',
  '#26303c': '--spd-p-border',
  '#202934': '--spd-p-border',
  '#1b232d ': '--spd-p-field-disabled-bg',
  '#33404e': '--spd-p-border-strong',
  '#3d4b5a': '--spd-p-border-strong',
  '#3a4756': '--spd-p-border-strong',
  '#475463': '--spd-p-text-muted',
  '#e8edf3 ': '--spd-p-text',
  '#5d6875': '--spd-p-text-muted',
  '#4a5561': '--spd-p-text-muted',
  '#7a8794': '--spd-p-neutral',
  '#78838f': '--spd-p-text-muted',
  '#a7b3c0': '--spd-p-text-secondary',
  '#c3ccd6': '--spd-p-text-secondary',
  '#5a6572': '--spd-p-text-muted',
  '#f2f6fa': '--spd-p-text-strong',
  '#2b2823': '--spd-p-text',
  '#23201c': '--spd-p-text-strong',
  '#4a453d': '--spd-p-text-secondary',
  '#7c7568': '--spd-p-text-secondary',
  '#5d574d': '--spd-p-text-secondary',
  '#423e37': '--spd-p-text-secondary',
  '#857e71 ': '--spd-p-text-muted',
  '#3f5c56': '--spd-p-primary',
  '#2f4742': '--spd-p-primary-strong',
  '#35504a': '--spd-p-primary-hover',
  '#e7edeb': '--spd-p-primary-bg',
  '#4c7a5f': '--spd-p-success',
  '#3d6650': '--spd-p-success-text',
  '#9a6b2f': '--spd-p-warning',
  '#855a24 ': '--spd-p-warning-text',
  '#9c4a3c': '--spd-p-danger',
  '#8a3f33': '--spd-p-danger-text',
  '#5b7285': '--spd-p-info',
  '#4c6274 ': '--spd-p-info-text',
  '#8d867a': '--spd-p-neutral',
  '#ddd8cc': '--spd-p-field-disabled-border',
  '#cfdccf': '--spd-p-success-border',
  '#e3d5ba': '--spd-p-warning-border',
  '#e3c8c2': '--spd-p-danger-border',
  '#cdd6de': '--spd-p-info-border',
  '#4ec08c': '--spd-p-success',
  '#7ed4ac': '--spd-p-success-text',
  '#e0a951': '--spd-p-warning',
  '#eec27e': '--spd-p-warning-text',
  '#e06c5c': '--spd-p-danger',
  '#f09384': '--spd-p-danger-text',
  '#6ba7d4': '--spd-p-info',
  '#93c3e4': '--spd-p-info-text',
  '#14251d': '--spd-p-success-bg',
  '#2a4a3a': '--spd-p-success-border',
  '#251e12': '--spd-p-warning-bg',
  '#4a3a1e': '--spd-p-warning-border',
  '#261614': '--spd-p-danger-bg',
  '#4d2a26': '--spd-p-danger-border',
  '#14212c': '--spd-p-info-bg',
  '#26404f': '--spd-p-info-border',
  '#d8a24a': '--spd-p-primary',
  '#e8bc6b': '--spd-p-primary-hover',
  '#b8862f': '--spd-p-primary-strong',
  '#241d12': '--spd-p-primary-bg',
  '#16110a': '--spd-p-text-on-primary',
  '#6b7885': '--spd-p-text-muted',
  '#a8a196 ': '--spd-p-text-muted',
  '#cde3df ': '--spd-p-primary-border',
  // 驾驶舱（Operation Cockpit）遗留的霓虹色板 —— 全部收敛到品牌主色与语义色
  '#00f0ff': '--spd-p-primary',
  '#3a8bff': '--spd-p-info',
  '#8a5cff': '--spd-p-primary-strong',
  '#ff4d8d': '--spd-p-danger',
  '#ffb84d': '--spd-p-warning',
  '#2bff9d': '--spd-p-success',
  '#ff4d6d': '--spd-p-danger',
  '#d8f4ff': '--spd-p-info-bg',
  '#6a8aa0': '--spd-p-text-muted',
  '#1f8d7a': '--spd-p-primary',
  '#16867d': '--spd-p-primary-hover',
  '#d1fae5': '--spd-p-success-bg',
  '#00e5ff': '--spd-p-primary',
  '#7c4dff': '--spd-p-primary-strong',
  '#ffd166': '--spd-p-warning',
  '#06b6d4 ': '--spd-p-primary',
  '#14b8a6': '--spd-p-primary',
  '#0d9488 ': '--spd-p-primary-hover',
  '#f43f5e': '--spd-p-danger',
  '#fb7185': '--spd-p-danger',
  '#fbbf24': '--spd-p-warning',
  '#f59e0b ': '--spd-p-warning',
  '#34d399': '--spd-p-success',
  '#10b981': '--spd-p-success',
  '#a78bfa': '--spd-p-primary-strong',
  '#818cf8': '--spd-p-info',
  '#c084fc': '--spd-p-primary-strong',
  '#f472b6': '--spd-p-danger',
  '#fcd34d': '--spd-p-warning',
  '#2dd4bf': '--spd-p-primary-hover',
  '#22d3ee': '--spd-p-info',
  '#38bdf8': '--spd-p-info',
  '#0ea5e9': '--spd-p-info',
  '#0284c7 ': '--spd-p-info',
  '#0369a1 ': '--spd-p-info-text',
  '#075985': '--spd-p-info-strong',
  '#e0f2fe': '--spd-p-info-bg',
  '#f0f9ff ': '--spd-p-info-bg',
  '#cffafe': '--spd-p-info-bg',
  '#ccfbf1': '--spd-p-primary-bg',
  '#a7f3d0': '--spd-p-success-border',
  '#bbf7d0 ': '--spd-p-success-border',
  '#fde68a ': '--spd-p-warning-border',
  '#fecaca ': '--spd-p-danger-border',
  '#bfdbfe': '--spd-p-info-border',
  '#99f6e4': '--spd-p-primary-border',
  '#5eead4': '--spd-p-primary-hover',
  '#93c5fd': '--spd-p-info-border',
  '#fca5a5': '--spd-p-danger-border',
  '#fcd34d ': '--spd-p-warning-border',
  '#86efac': '--spd-p-success-border',
  '#e879f9': '--spd-p-danger',
  '#c084fc ': '--spd-p-primary-strong',
  '#22c55e ': '--spd-p-success',
  '#4ade80': '--spd-p-success',
  '#16a34a ': '--spd-p-success',
  '#15803d ': '--spd-p-success-text',
  '#b91c1c ': '--spd-p-danger-strong',
  '#ef4444': '--spd-p-danger',
  '#e11d48 ': '--spd-p-danger',
  '#f97316 ': '--spd-p-warning',
  '#f59e0b ': '--spd-p-warning',
  '#fb923c': '--spd-p-warning',
  '#ea580c ': '--spd-p-warning-strong',
  '#3b82f6 ': '--spd-p-info',
  '#60a5fa': '--spd-p-info',
  '#93c5fd ': '--spd-p-info-border',
  '#1d4ed8 ': '--spd-p-info-strong',
  '#2563eb ': '--spd-p-info',
  '#7c3aed ': '--spd-p-primary-strong',
  '#4f46e5 ': '--spd-p-primary',
  '#4338ca ': '--spd-p-primary-strong',
  '#6366f1 ': '--spd-p-primary',
  '#8b5cf6 ': '--spd-p-primary-strong',
  '#a855f7': '--spd-p-primary-strong',
  '#d946ef': '--spd-p-primary-strong',
  '#ec4899 ': '--spd-p-danger',
  '#db2777': '--spd-p-danger',
  '#be185d': '--spd-p-danger-strong',
  '#831843': '--spd-p-danger-strong',
  '#701a75': '--spd-p-danger-strong',
  '#86198f': '--spd-p-danger-strong',
  '#9d174d': '--spd-p-danger-strong',
  '#065f46': '--spd-p-success-strong',
  '#064e3b': '--spd-p-success-strong',
  '#166534': '--spd-p-success-strong',
  '#14532d': '--spd-p-success-strong',
  '#854d0e': '--spd-p-warning-strong',
  '#78350f': '--spd-p-warning-strong',
  '#7c2d12': '--spd-p-danger-strong',
  '#7f1d1d': '--spd-p-danger-strong',
  '#1e3a8a': '--spd-p-info-strong',
  '#1e40af ': '--spd-p-info-strong',
  '#172554': '--spd-p-info-strong',
  '#0c4a6e': '--spd-p-info-strong',
  '#164e63': '--spd-p-info-strong',
  '#134e4a': '--spd-p-primary-strong',
  '#134e4a ': '--spd-p-primary-strong',
  '#0f766e ': '--spd-p-primary',
  '#115e59': '--spd-p-primary-strong',
  '#042f2e': '--spd-p-primary-strong',
  '#022c22': '--spd-p-primary-strong',
  '#052e16': '--spd-p-success-strong',
  '#1a2e05': '--spd-p-success-strong',
  '#1c1917': '--spd-p-text-strong',
  '#292524': '--spd-p-text-strong',
  '#44403c': '--spd-p-text-secondary',
  '#57534e': '--spd-p-text-secondary',
  '#78716c': '--spd-p-text-muted',
  '#a8a29e': '--spd-p-text-muted',
  '#d6d3d1': '--spd-p-border',
  '#e7e5e4': '--spd-p-border-subtle',
  '#f5f5f4': '--spd-p-bg-subtle',
  '#fafaf9': '--spd-p-bg-muted',
  '#f5f5f4 ': '--spd-p-bg-subtle',
  '#292524 ': '--spd-p-text-strong',
  '#e7e5e4 ': '--spd-p-border-subtle',
  '#78716c ': '--spd-p-text-muted',
  '#57534e ': '--spd-p-text-secondary',
  '#44403c ': '--spd-p-text-secondary',
  '#d6d3d1 ': '--spd-p-border',
  '#94a3b8 ': '--spd-p-text-muted',
  '#64748b ': '--spd-p-info-text',
  '#475569': '--spd-p-text-secondary',
  '#334155': '--spd-p-text-secondary',
  '#1e293b': '--spd-p-text-strong',
  '#0f172a ': '--spd-p-text-strong',
  '#f1f5f9': '--spd-p-bg-subtle',
  '#f8fafc ': '--spd-p-bg-muted',
  '#e2e8f0 ': '--spd-p-border',
  '#f8fafc': '--spd-p-bg-muted',
  '#f1f5f9 ': '--spd-p-bg-subtle',
  '#e2e8f0': '--spd-p-border',
  '#cbd5e1 ': '--spd-p-border-strong',
  '#94a3b8': '--spd-p-text-muted',
  '#334155 ': '--spd-p-text-secondary',
  '#1e293b ': '--spd-p-text-strong',
  '#0f172a': '--spd-p-text-strong',
  '#64748b': '--spd-p-info-text',
  '#475569 ': '--spd-p-text-secondary',
  '#94a3b8': '--spd-p-text-muted',
  '#64748b ': '--spd-p-info-text',
  '#f97316 ': '--spd-p-warning',
  '#fbbf24 ': '--spd-p-warning',
  '#fde047': '--spd-p-warning',
  '#fef08a': '--spd-p-warning-bg',
  '#fef9c3': '--spd-p-warning-bg',
  '#fffbeb ': '--spd-p-warning-bg',
  '#f0fdf4': '--spd-p-success-bg',
  '#f0fdfa': '--spd-p-primary-bg',
  '#ecfdf5': '--spd-p-success-bg',
  '#ecfdfb': '--spd-p-primary-bg',
  '#eff6ff': '--spd-p-info-bg',
  '#f8fafc': '--spd-p-bg-muted',
  '#faf5ff': '--spd-p-bg-subtle',
  '#fdf4ff': '--spd-p-bg-subtle',
  '#fdf2f8': '--spd-p-bg-subtle',
  '#f0f9ff': '--spd-p-info-bg',
  '#f5f3ff': '--spd-p-bg-subtle',
  '#fafafa': '--spd-p-row-header-bg',
  '#fcfcfd': '--spd-p-bg',
  '#f7f7f8': '--spd-p-bg-muted',
  '#f6f7f9': '--spd-p-bg-muted',
  '#f4f5f7': '--spd-p-bg-muted',
  '#f2f3f5': '--spd-p-bg-muted',
  '#f0f1f3': '--spd-p-bg-muted',
  '#eceef0': '--spd-p-bg-muted',
  '#e9ebee': '--spd-p-bg-muted',
  '#e6e8eb': '--spd-p-bg-muted',
  '#e3e5e8': '--spd-p-bg-muted',
  '#e0e2e5': '--spd-p-bg-muted',
  '#dddfe2': '--spd-p-bg-muted',
  '#d9dbde': '--spd-p-bg-muted',
  '#d5d7da': '--spd-p-bg-muted',
  '#d1d3d6': '--spd-p-bg-muted',
  '#cdcfd2': '--spd-p-bg-muted',
  '#c9cbce': '--spd-p-bg-muted',
  '#c5c7ca': '--spd-p-bg-muted',
  '#c1c3c6': '--spd-p-bg-muted',
  '#bdc0c3': '--spd-p-bg-muted',
  '#b9bcc0': '--spd-p-bg-muted',
  '#b5b8bc': '--spd-p-bg-muted',
  '#b1b4b8': '--spd-p-bg-muted',
  '#adb0b4': '--spd-p-bg-muted',
  '#a9acb0': '--spd-p-bg-muted',
  '#a5a8ac': '--spd-p-bg-muted',
  '#a1a4a8': '--spd-p-bg-muted',
  '#9da0a4': '--spd-p-bg-muted',
  '#999ca0': '--spd-p-bg-muted',
  '#95989c': '--spd-p-bg-muted',
  '#919499': '--spd-p-bg-muted',
  '#8d9095': '--spd-p-bg-muted',
  '#898d92': '--spd-p-bg-muted',
  '#85898e': '--spd-p-bg-muted',
  '#81858a': '--spd-p-bg-muted',
  '#7d8186': '--spd-p-bg-muted',
  '#797d82': '--spd-p-bg-muted',
  '#75797e': '--spd-p-bg-muted',
  '#71757a': '--spd-p-bg-muted',
  '#6d7176': '--spd-p-bg-muted',
  '#696d72': '--spd-p-bg-muted',
  '#65696e': '--spd-p-bg-muted',
  '#61656a': '--spd-p-bg-muted',
  '#5d6166': '--spd-p-bg-muted',
  '#595d62': '--spd-p-bg-muted',
  '#55595e': '--spd-p-bg-muted',
  '#51555a': '--spd-p-bg-muted',
  '#4d5156': '--spd-p-bg-muted',
  '#494d52': '--spd-p-bg-muted',
  '#45494e': '--spd-p-bg-muted',
  '#41454a': '--spd-p-bg-muted',
  '#3d4146': '--spd-p-bg-muted',
  '#393d42': '--spd-p-bg-muted',
  '#35393e': '--spd-p-bg-muted',
  '#31353a': '--spd-p-bg-muted',
  '#2d3136': '--spd-p-bg-muted',
  '#292d32': '--spd-p-bg-muted',
  '#25292e': '--spd-p-bg-muted',
  '#212529': '--spd-p-bg-muted',
  '#1d2126': '--spd-p-bg-muted',
  '#191d22': '--spd-p-bg-muted',
  '#15191e': '--spd-p-bg-muted',
  '#11151a': '--spd-p-bg-muted',
  '#0d1116': '--spd-p-bg-muted',
  '#090c10': '--spd-p-bg-muted',
  '#05070a': '--spd-p-bg-muted',
  '#fcfcfc': '--spd-p-bg',
  '#f9f9fa': '--spd-p-bg',
  '#f7f7f7': '--spd-p-bg-muted',
  '#f4f4f5': '--spd-p-bg-muted',
  '#f1f1f2': '--spd-p-bg-muted',
  '#eeeef0': '--spd-p-bg-muted',
  '#ebebed': '--spd-p-bg-muted',
  '#e8e8ea': '--spd-p-bg-muted',
  '#e5e5e7': '--spd-p-bg-muted',
  '#e2e2e4': '--spd-p-bg-muted',
  '#dfdfE1': '--spd-p-bg-muted',
  // 具名颜色
  '#white': '--spd-p-text-on-primary',
  '#black': '--spd-p-text-strong',
}));

/** 已知色 → 令牌（去掉空格 key 的清洗） */
const CLEAN_EXPLICIT = new Map();
for (const [k, v] of EXPLICIT) {
  CLEAN_EXPLICIT.set(k.trim().toLowerCase(), v);
}

/** 明确禁止替换的色（EP 默认蓝 / 第二套品牌色渐变端点） */
const NEVER = new Set([
  '#409eff', '#2563eb', '#1677ff', '#0958d9', '#0b5ed7', '#3b82f6',
  '#6366f1', '#4f46e5', '#4338ca', '#3730a3', '#8b5cf6', '#7c3aed',
  '#ec4899', '#e11d48', '#be123c', '#9f1239', '#06b6d4', '#0891b2',
  '#0d9488', '#059669', '#047857', '#1d4ed8', '#1e40af',
]);

/* ---------------- 第二套品牌色的显式收敛 ---------------- */
/**
 * 旧样式里的蓝 / 靛 / 紫 / 青渐变与 EP 默认蓝，与固定品牌色「青玉」冲突。
 * 这里按语义收敛：
 *   - 蓝色系   → 信息色（info）
 *   - 绿色系   → 成功色（success）
 *   - 紫 / 靛  → 品牌主色（primary）
 *   - 青 / 粉  → 品牌主色 / 危险色
 *   - 渐变端点 → 收敛成「主色 → 主色深色」两段式渐变
 */
const LEGACY_BRAND_MAP = new Map(Object.entries({
  '#409eff': '--spd-p-info',
  '#2563eb': '--spd-p-info',
  '#1677ff': '--spd-p-info',
  '#0958d9': '--spd-p-info-strong',
  '#0b5ed7': '--spd-p-info-strong',
  '#3b82f6': '--spd-p-info',
  '#1d4ed8': '--spd-p-info-strong',
  '#1e40af': '--spd-p-info-strong',
  '#6366f1': '--spd-p-primary',
  '#4f46e5': '--spd-p-primary',
  '#4338ca': '--spd-p-primary-strong',
  '#3730a3': '--spd-p-primary-strong',
  '#8b5cf6': '--spd-p-primary-strong',
  '#7c3aed': '--spd-p-primary-strong',
  '#06b6d4': '--spd-p-primary',
  '#0891b2': '--spd-p-primary-hover',
  '#0d9488': '--spd-p-primary-hover',
  '#059669': '--spd-p-success',
  '#047857': '--spd-p-success-strong',
  '#ec4899': '--spd-p-danger',
  '#e11d48': '--spd-p-danger',
  '#be123c': '--spd-p-danger-strong',
  '#9f1239': '--spd-p-danger-strong',
}));

/**
 * 渐变收敛表：旧的多段彩色渐变 → 调色板里的两段式主色渐变。
 * key 为归一化后的渐变文本片段。
 */
const GRADIENT_REPLACEMENTS = [
  [/linear-gradient\(135deg,\s*#06b6d4\s*0%,\s*#6366f1\s*50%,\s*#8b5cf6\s*100%\)/gi, 'var(--spd-p-header-gradient)'],
  [/linear-gradient\(135deg,\s*#0891b2\s*0%,\s*#4f46e5\s*50%,\s*#7c3aed\s*100%\)/gi, 'var(--spd-p-primary-hover-solid)'],
  [/linear-gradient\(135deg,\s*#2563eb\s*0%,\s*#4f46e5\s*100%\)/gi, 'var(--spd-p-header-gradient)'],
  [/linear-gradient\(135deg,\s*#1d4ed8\s*0%,\s*#4338ca\s*100%\)/gi, 'var(--spd-p-primary-hover-solid)'],
  [/linear-gradient\(135deg,\s*#1e40af\s*0%,\s*#3730a3\s*100%\)/gi, 'var(--spd-p-primary-active-solid)'],
  [/linear-gradient\(135deg,\s*#dc2626\s*0%,\s*#e11d48\s*100%\)/gi, 'var(--spd-p-danger)'],
  [/linear-gradient\(135deg,\s*#b91c1c\s*0%,\s*#be123c\s*100%\)/gi, 'var(--spd-p-danger-hover)'],
  [/linear-gradient\(135deg,\s*#991b1b\s*0%,\s*#9f1239\s*100%\)/gi, 'var(--spd-p-danger-active)'],
  [/linear-gradient\(135deg,\s*#f97316\s*0%,\s*#ea580c\s*100%\)/gi, 'var(--spd-p-warning)'],
  [/linear-gradient\(135deg,\s*#ea580c\s*0%,\s*#c2410c\s*100%\)/gi, 'var(--spd-p-warning-hover)'],
  [/linear-gradient\(135deg,\s*#059669\s*0%,\s*#0d9488\s*100%\)/gi, 'var(--spd-p-success)'],
  [/linear-gradient\(135deg,\s*#047857\s*0%,\s*#0f766e\s*100%\)/gi, 'var(--spd-p-success-hover)'],
  [/linear-gradient\(135deg,\s*#667eea\s*0%,\s*#764ba2\s*100%\)/gi, 'var(--spd-p-header-gradient)'],
  [/linear-gradient\(135deg,\s*#f093fb\s*0%,\s*#f5576c\s*100%\)/gi, 'var(--spd-p-header-gradient)'],
  [/linear-gradient\(135deg,\s*#4facfe\s*0%,\s*#00f2fe\s*100%\)/gi, 'var(--spd-p-header-gradient)'],
  [/linear-gradient\(135deg,\s*#43e97b\s*0%,\s*#38f9d7\s*100%\)/gi, 'var(--spd-p-header-gradient)'],
  [/linear-gradient\(135deg,\s*#fa709a\s*0%,\s*#fee140\s*100%\)/gi, 'var(--spd-p-header-gradient)'],
  [/linear-gradient\(135deg,\s*#a18cd1\s*0%,\s*#fbc2eb\s*100%\)/gi, 'var(--spd-p-header-gradient)'],
  [/linear-gradient\(135deg,\s*#fccb90\s*0%,\s*#d57eeb\s*100%\)/gi, 'var(--spd-p-header-gradient)'],
  [/linear-gradient\(135deg,\s*#30cfd0\s*0%,\s*#330867\s*100%\)/gi, 'var(--spd-p-header-gradient)'],
];


/** 归一化后的「可替换候选表」 */
const CANDIDATES = [];
for (const [hex, token] of CLEAN_EXPLICIT) {
  if (NEVER.has(hex)) continue;
  const rgb = parseHex(hex);
  CANDIDATES.push({ hex, token, rgb });
}
// 基础色板（--spd-c-*）里的中性灰
for (const hex of ['#d5e0e5', '#b9c8ce', '#e8eef1', '#f5f8f9', '#eef3f5', '#f7fafb', '#ffffff', '#101820', '#1f2933', '#4a5a66', '#6b7c88']) {
  const idx = CANDIDATES.findIndex((c) => c.hex === hex);
  if (idx >= 0) continue;
  CANDIDATES.push({ hex, token: null, rgb: parseHex(hex) });
}

/* ---------------- 上下文感知替换 ---------------- */
/**
 * 阴影是「整条 box-shadow 值」而不是单个颜色。
 * 这里先把整条 box-shadow 换成调色板里的阴影令牌，避免出现
 * `color-mix(...) 0 8px 24px ...` 这类拼装出来的无效阴影。
 */
const SHADOW_TOKEN_BY_BLUR = [
  // 大模糊 → 对话框级阴影
  { blur: 48, token: '--spd-p-shadow-dialog' },
  { blur: 64, token: '--spd-p-shadow-dialog' },
  { blur: 70, token: '--spd-p-shadow-dialog' },
  { blur: 32, token: '--spd-p-shadow-3' },
  { blur: 28, token: '--spd-p-shadow-3' },
  { blur: 24, token: '--spd-p-shadow-3' },
  { blur: 20, token: '--spd-p-shadow-3' },
  { blur: 16, token: '--spd-p-shadow-2' },
  { blur: 12, token: '--spd-p-shadow-2' },
  { blur: 10, token: '--spd-p-shadow-2' },
  { blur: 8, token: '--spd-p-shadow-1' },
  { blur: 6, token: '--spd-p-shadow-1' },
  { blur: 4, token: '--spd-p-shadow-1' },
  { blur: 3, token: '--spd-p-shadow-1' },
  { blur: 2, token: '--spd-p-shadow-1' },
];

const BOX_SHADOW_RE = /box-shadow:\s*([^;]+);/g;

function shadowTokenFor(value) {
  // 取最大的模糊半径
  const blurs = [...value.matchAll(/(-?\d+(?:\.\d+)?)px/g)].map((m) => Number(m[1]));
  if (blurs.length === 0) return null;
  const maxBlur = Math.max(...blurs);
  for (const entry of SHADOW_TOKEN_BY_BLUR) {
    if (maxBlur >= entry.blur) return entry.token;
  }
  return '--spd-p-shadow-1';
}


/* ---------------- 处理文件 ---------------- */
const HEX_RE = /#[0-9a-fA-F]{3,8}\b/g;
const RGB_RE = /rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*(?:,\s*([\d.]+)\s*)?\)/g;

/**
 * 具名颜色：只在颜色属性值里出现时替换，
 * 避免误伤 .btn-white 这类类名或 min-width 这类属性名。
 */
const NAMED_COLOR_PROP_RE =
  /((?:^|[;{])\s*(?:color|background|background-color|border(?:-\w+)?-color|outline-color|fill|stroke|text-decoration-color|caret-color|column-rule-color)\s*:\s*)([^;{}]+)/gim;
const NAMED_COLOR_MAP = {
  white: '--spd-p-text-on-primary',
  black: '--spd-p-text-strong',
  red: '--spd-p-danger',
  green: '--spd-p-success',
  blue: '--spd-p-info',
  yellow: '--spd-p-warning',
  orange: '--spd-p-warning',
  purple: '--spd-p-primary-strong',
  magenta: '--spd-p-danger',
  pink: '--spd-p-danger',
  cyan: '--spd-p-primary',
  teal: '--spd-p-primary',
  lime: '--spd-p-success',
  indigo: '--spd-p-primary-strong',
  violet: '--spd-p-primary-strong',
  gold: '--spd-p-warning',
  gray: '--spd-p-text-secondary',
  grey: '--spd-p-text-secondary',
  silver: '--spd-p-border-strong',
  maroon: '--spd-p-danger-strong',
  navy: '--spd-p-info-strong',
  olive: '--spd-p-success',
  limegreen: '--spd-p-success',
  darkblue: '--spd-p-info-strong',
  darkgreen: '--spd-p-success-strong',
  darkred: '--spd-p-danger-strong',
  lightblue: '--spd-p-info-bg',
  lightgreen: '--spd-p-success-bg',
  lightgray: '--spd-p-bg-subtle',
  lightgrey: '--spd-p-bg-subtle',
  lightyellow: '--spd-p-warning-bg',
  lightpink: '--spd-p-danger-bg',
  whitesmoke: '--spd-p-bg',
  snow: '--spd-p-bg',
  ivory: '--spd-p-bg',
  azure: '--spd-p-info-bg',
  beige: '--spd-p-warning-bg',
  coral: '--spd-p-danger',
  crimson: '--spd-p-danger',
  tomato: '--spd-p-danger',
  salmon: '--spd-p-danger',
  khaki: '--spd-p-warning-bg',
  lavender: '--spd-p-primary-bg',
  plum: '--spd-p-primary-strong',
  orchid: '--spd-p-primary-strong',
  turquoise: '--spd-p-primary-hover',
  springgreen: '--spd-p-success',
  seagreen: '--spd-p-success',
  steelblue: '--spd-p-info',
  slategray: '--spd-p-text-secondary',
  slategrey: '--spd-p-text-secondary',
  darkslategray: '--spd-p-text-secondary',
  dimgray: '--spd-p-text-secondary',
  dimgrey: '--spd-p-text-secondary',
  whitesmoke_: '--spd-p-bg',
};

function nearest(rgb) {
  let best = null;
  let bestDist = Infinity;
  for (const c of CANDIDATES) {
    const d = distance(rgb, c.rgb);
    if (d < bestDist) {
      bestDist = d;
      best = c;
    }
  }
  return { best, bestDist };
}

/**
 * 语义兜底：按 HSL 色相 + 饱和度 + 明度判断这个颜色「本质上」是
 * 成功 / 危险 / 警告 / 信息 / 主色 / 中性，然后映射到对应令牌。
 * 用于那些没有逐个登记过的近色（同一色系的浅色底 / 深色字）。
 */
function semanticFallback(rgb) {
  const [r, g, b] = rgb.map((v) => v / 255);
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const l = (max + min) / 2;
  const d = max - min;
  let h = 0;
  let s = 0;
  if (d !== 0) {
    s = d / (1 - Math.abs(2 * l - 1));
    if (max === r) h = ((g - b) / d) % 6;
    else if (max === g) h = (b - r) / d + 2;
    else h = (r - g) / d + 4;
    h *= 60;
    if (h < 0) h += 360;
  }

  // 近中性（低饱和）→ 中性色角色
  if (s < 0.12) {
    if (l > 0.92) return '--spd-p-bg';
    if (l > 0.8) return '--spd-p-bg-subtle';
    if (l > 0.65) return '--spd-p-bg-muted';
    if (l > 0.5) return '--spd-p-border';
    if (l > 0.35) return '--spd-p-border-strong';
    if (l > 0.2) return '--spd-p-text-secondary';
    return '--spd-p-text-strong';
  }

  if (h >= 140 && h < 200) {
    // 青 / 绿 → 成功
    if (l > 0.88) return '--spd-p-success-bg';
    if (l > 0.72) return '--spd-p-success-border';
    if (l > 0.45) return '--spd-p-success';
    return '--spd-p-success-text';
  }
  if (h >= 200 && h < 260) {
    // 蓝 → 信息
    if (l > 0.9) return '--spd-p-info-bg';
    if (l > 0.75) return '--spd-p-info-border';
    if (l > 0.45) return '--spd-p-info';
    return '--spd-p-info-text';
  }
  if (h >= 260 && h < 340) {
    // 紫 / 洋红 → 主色
    if (l > 0.9) return '--spd-p-primary-bg';
    if (l > 0.75) return '--spd-p-primary-border';
    if (l > 0.45) return '--spd-p-primary';
    return '--spd-p-primary-strong';
  }
  if (h >= 340 || h < 20) {
    // 红 → 危险
    if (l > 0.9) return '--spd-p-danger-bg';
    if (l > 0.75) return '--spd-p-danger-border';
    if (l > 0.45) return '--spd-p-danger';
    return '--spd-p-danger-text';
  }
  if (h >= 20 && h < 60) {
    // 橙 / 黄 → 警告
    if (l > 0.9) return '--spd-p-warning-bg';
    if (l > 0.75) return '--spd-p-warning-border';
    if (l > 0.45) return '--spd-p-warning';
    return '--spd-p-warning-text';
  }
  // 黄绿 / 橄榄
  if (l > 0.9) return '--spd-p-bg-subtle';
  if (l > 0.45) return '--spd-p-success';
  return '--spd-p-text-secondary';
}

const MAX_DIST = 26; // 允许的最大 RGB 距离

function processFile(file) {
  const abs = path.resolve(ROOT, file);
  let text = fs.readFileSync(abs, 'utf8');
  const original = text;
  const changes = [];

  // 0) box-shadow 整条替换为阴影令牌（先做，避免逐色拼装出无效阴影）
  text = text.replace(BOX_SHADOW_RE, (match, value) => {
    if (!/rgba?\(|#[0-9a-fA-F]{3,8}/.test(value)) return match;
    const token = shadowTokenFor(value);
    if (!token) return match;
    const to = `box-shadow: var(${token});`;
    changes.push({ from: match.trim(), to });
    return to;
  });

  // 0.5) 旧的彩色渐变 → 调色板两段式主色渐变
  for (const [re, to] of GRADIENT_REPLACEMENTS) {
    text = text.replace(re, (match) => {
      changes.push({ from: match, to });
      return to;
    });
  }

  // 1) rgba(...) —— 半透明色：改为 color-mix(令牌, 透明, alpha)，语义不变
  text = text.replace(RGB_RE, (match, r, g, b, a) => {
    const rgb = [Number(r), Number(g), Number(b)];
    const { best, bestDist } = nearest(rgb);
    let token = null;
    if (best && bestDist <= MAX_DIST && best.token) {
      token = best.token;
    } else {
      token = semanticFallback(rgb);
    }
    if (!token) return match;
    const alpha = a === undefined ? '1' : a;
    const to = `color-mix(in srgb, var(${token}) ${Math.round(Number(alpha) * 100)}%, transparent)`;
    changes.push({ from: match, to });
    return to;
  });

  // 2.5) 具名颜色 → 语义令牌
  text = text.replace(NAMED_COLOR_PROP_RE, (match, prefix, value) => {
    const trimmed = value.trim();
    const key = trimmed.toLowerCase();
    const token = NAMED_COLOR_MAP[key];
    if (!token) return match;
    changes.push({ from: match.trim(), to: `${prefix}var(${token})` });
    return `${prefix}var(${token})`;
  });

  // 2.6) color-mix(..., white) → 混表面令牌（暗夜黑下不会出亮斑）
  text = text.replace(
    /color-mix\(in srgb,\s*(.+?),\s*white\)/g,
    (match, mixToken) => {
      const to = `color-mix(in srgb, ${mixToken.trim()}, var(--spd-p-bg))`;
      changes.push({ from: match, to });
      return to;
    },
  );

  // 2.7) background: white !important → 表面令牌
  text = text.replace(
    /(:\s*)white(\s*!important)/g,
    (match, prefix, suffix) => {
      const to = `${prefix}var(--spd-p-bg)${suffix}`;
      changes.push({ from: match, to });
      return to;
    },
  );

  // 3) hex
  text = text.replace(HEX_RE, (match) => {
    const lower = match.toLowerCase();
    if (lower.length === 8) return match; // 带 alpha 的 hex 保持原样（少见）
    const legacy = LEGACY_BRAND_MAP.get(lower);
    if (legacy) {
      changes.push({ from: match, to: `var(${legacy})` });
      return `var(${legacy})`;
    }
    if (NEVER.has(lower)) return match;
    const explicit = CLEAN_EXPLICIT.get(lower);
    if (explicit) {
      changes.push({ from: match, to: `var(${explicit})` });
      return `var(${explicit})`;
    }
    const rgb = parseHex(lower);
    const { best, bestDist } = nearest(rgb);
    if (best && bestDist <= MAX_DIST && best.token) {
      changes.push({ from: match, to: `var(${best.token})` });
      return `var(${best.token})`;
    }
    // 兜底：按色相语义映射到调色板角色
    const fallback = semanticFallback(rgb);
    if (fallback) {
      changes.push({ from: match, to: `var(${fallback})` });
      return `var(${fallback})`;
    }
    return match;
  });

  if (text !== original && !DRY) {
    fs.writeFileSync(abs, text, 'utf8');
  }
  return changes;
}

let total = 0;
for (const file of targets) {
  const changes = processFile(file);
  total += changes.length;
  console.log(`${file}: ${changes.length} 处`);
  if (DRY) {
    for (const c of changes.slice(0, 20)) console.log(`   ${c.from} -> ${c.to}`);
  }
}
console.log(`\n合计替换：${total}`);
