import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';

const vueFiles = await collectVueFiles('frontend/src');
const failures = [];

for (const file of vueFiles) {
  const source = await readFile(file, 'utf8');
  const templateOpen = source.indexOf('<template');
  const templateStart = templateOpen < 0 ? -1 : source.indexOf('>', templateOpen) + 1;
  const templateEnd = source.lastIndexOf('</template>');

  if (templateStart <= 0 || templateEnd < templateStart) {
    failures.push(`${file}: template block is missing or not closed`);
    continue;
  }

  for (const match of source.matchAll(/<(script|style)\b/gi)) {
    const index = match.index ?? -1;
    if (index > templateStart && index < templateEnd) {
      failures.push(`${file}: <${match[1]}> must be a top-level SFC block, not template content`);
    }
  }
}

if (failures.length > 0) {
  throw new Error(`Vue SFC structure verification failed:\n${failures.join('\n')}`);
}

console.log(`Vue SFC structure verification passed (${vueFiles.length} files).`);

async function collectVueFiles(directory) {
  const files = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...await collectVueFiles(path));
    } else if (entry.isFile() && entry.name.endsWith('.vue')) {
      files.push(path);
    }
  }
  return files;
}
