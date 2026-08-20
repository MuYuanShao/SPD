import { readdir, readFile } from 'node:fs/promises';
import { extname, relative, resolve } from 'node:path';

const roots = ['backend/src', 'frontend/src', 'frontend/public', 'docs', 'scripts', 'tests'];
const rootFiles = ['AGENTS.md', 'CLAUDE.md', 'CONTEXT.md', 'README.md', 'package.json', 'playwright.config.ts'];
const extensions = new Set(['.java', '.ts', '.vue', '.css', '.sql', '.yml', '.yaml', '.json', '.md', '.mjs', '.ps1', '.html']);
const strictUtf8 = new TextDecoder('utf-8', { fatal: true });
const replacementCharacter = String.fromCodePoint(0xfffd);
const privateUsePattern = /[\uE000-\uF8FF]/u;
const mojibakeTokens = [
  [0x9359, 0xe044, 0x5ba8], [0x7ec9, 0x4f7a, 0x6e41], [0x9354, 0x3126],
  [0x9429, 0x6a3c], [0x748b, 0x51af], [0x9350, 0x70fd], [0x6d93, 0x7528],
  [0x6dc7, 0x6b3d], [0x6960, 0x5c7e], [0x6e1a, 0x6dac], [0x93cc, 0x30e8],
  [0x93c2, 0x677f], [0x7f02, 0x682c], [0x9352, 0x72b3], [0x5a23, 0x8bf2],
  [0x74d0, 0x4e50], [0x7f02, 0x54c4],
].map((points) => String.fromCodePoint(...points));

const allowedLinePredicates = new Map([
  ['frontend/src/views/system/ApprovalFlowSettingsView.vue', (line) => line.includes('if (!value || /') && line.includes('.test(value))')],
]);

const failures = [];
for (const root of roots) {
  await walk(resolve(root));
}
for (const file of rootFiles) {
  await inspect(resolve(file));
}

if (failures.length > 0) {
  console.error(`Text encoding verification failed with ${failures.length} finding(s):`);
  for (const failure of failures) {
    console.error(`- ${failure.file}:${failure.line} ${failure.reason}`);
  }
  process.exitCode = 1;
} else {
  console.log('Text encoding verification passed.');
}

async function walk(directory) {
  let entries;
  try {
    entries = await readdir(directory, { withFileTypes: true });
  } catch (error) {
    if (error?.code === 'ENOENT') return;
    throw error;
  }
  for (const entry of entries) {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) {
      await walk(path);
    } else if (extensions.has(extname(entry.name))) {
      await inspect(path);
    }
  }
}

async function inspect(path) {
  const file = relative(process.cwd(), path).replaceAll('\\', '/');
  let text;
  try {
    text = strictUtf8.decode(await readFile(path));
  } catch {
    failures.push({ file, line: 1, reason: '文件不是有效 UTF-8' });
    return;
  }
  const allowedLine = allowedLinePredicates.get(file);
  for (const [index, line] of text.split(/\r?\n/u).entries()) {
    const lineNumber = index + 1;
    if (allowedLine?.(line)) continue;
    if (line.includes(replacementCharacter)) {
      failures.push({ file, line: lineNumber, reason: '包含 Unicode 替换字符' });
    } else if (privateUsePattern.test(line)) {
      failures.push({ file, line: lineNumber, reason: '包含 Unicode 私用区字符' });
    } else if (mojibakeTokens.some((token) => line.includes(token))) {
      failures.push({ file, line: lineNumber, reason: '包含典型 GBK/UTF-8 误转码文本' });
    }
  }
}
