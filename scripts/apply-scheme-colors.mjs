import fs from 'node:fs';

const edits = {
  'frontend/src/styles/theme/calm-paper.css': [
    ['  color: #fdfbf7 !important;', '  color: var(--spd-c-text-on-primary) !important;'],
    ['  box-shadow: 0 8px 24px rgba(60, 52, 38, 0.1) !important;', '  box-shadow: var(--spd-p-shadow-3) !important;'],
    ['  box-shadow: 0 1px 2px rgba(60, 52, 38, 0.04);', '  box-shadow: var(--spd-p-shadow-1);'],
    ['  background: #edeae1 !important;', '  background: var(--spd-c-bg-muted) !important;'],
    ['  background: color-mix(in srgb, var(--spd-c-primary) 20%, #fdfbf7) !important;', '  background: var(--spd-p-primary-bg) !important;'],
    ['  box-shadow: 0 8px 24px rgba(60, 52, 38, 0.12) !important;', '  box-shadow: var(--spd-p-shadow-3) !important;'],
    ['  box-shadow: 0 24px 70px rgba(60, 52, 38, 0.16) !important;', '  box-shadow: var(--spd-p-shadow-dialog) !important;'],
    ['  background: #d5cfc2;', '  background: var(--spd-c-border-strong);'],
    // 色板定义块内部：chip 底色混用的纸白 → 表面令牌
    ['  --spd-c-chip-bg-success: color-mix(in srgb, var(--spd-c-success) 14%, #fdfbf7);\n  --spd-c-chip-bg-disabled: color-mix(in srgb, var(--spd-c-disabled) 14%, #fdfbf7);\n  --spd-c-chip-bg-warning: color-mix(in srgb, var(--spd-c-warning) 14%, #fdfbf7);\n  --spd-c-chip-bg-danger: color-mix(in srgb, var(--spd-c-danger) 14%, #fdfbf7);\n  --spd-c-chip-bg-info: color-mix(in srgb, var(--spd-c-info) 14%, #fdfbf7);\n  --spd-c-chip-bg-neutral: color-mix(in srgb, var(--spd-c-text-muted) 14%, #fdfbf7);\n  --spd-c-chip-bg-primary: color-mix(in srgb, var(--spd-c-primary) 14%, #fdfbf7);',
     '  --spd-c-chip-bg-success: color-mix(in srgb, var(--spd-c-success) 14%, var(--spd-c-bg));\n  --spd-c-chip-bg-disabled: color-mix(in srgb, var(--spd-c-disabled) 14%, var(--spd-c-bg));\n  --spd-c-chip-bg-warning: color-mix(in srgb, var(--spd-c-warning) 14%, var(--spd-c-bg));\n  --spd-c-chip-bg-danger: color-mix(in srgb, var(--spd-c-danger) 14%, var(--spd-c-bg));\n  --spd-c-chip-bg-info: color-mix(in srgb, var(--spd-c-info) 14%, var(--spd-c-bg));\n  --spd-c-chip-bg-neutral: color-mix(in srgb, var(--spd-c-text-muted) 14%, var(--spd-c-bg));\n  --spd-c-chip-bg-primary: color-mix(in srgb, var(--spd-c-primary) 14%, var(--spd-c-bg));'],
  ],
  'frontend/src/styles/theme/night-black.css': [
    ['  color: #16110a !important;', '  color: var(--spd-c-text-on-primary) !important;'],
    ['  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.6), 0 2px 8px rgba(0, 0, 0, 0.4) !important;', '  box-shadow: var(--spd-p-shadow-3) !important;'],
    ['  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.45);', '  box-shadow: var(--spd-p-shadow-1);'],
    ['  color: var(--spd-text-regular, #c3ccd6) !important;', '  color: var(--spd-text-regular, var(--spd-c-text-secondary)) !important;'],
    ['  background: color-mix(in srgb, var(--spd-c-primary) 26%, #131920) !important;', '  background: var(--spd-p-primary-bg) !important;'],
    ['  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.6) !important;', '  box-shadow: var(--spd-p-shadow-3) !important;'],
    ['  background: #303b47;', '  background: var(--spd-c-border-strong);'],
    ['  --spd-c-chip-bg-success: color-mix(in srgb, var(--spd-c-success) 40%, #131920);\n  --spd-c-chip-bg-disabled: color-mix(in srgb, var(--spd-c-disabled) 40%, #131920);\n  --spd-c-chip-bg-warning: color-mix(in srgb, var(--spd-c-warning) 40%, #131920);\n  --spd-c-chip-bg-danger: color-mix(in srgb, var(--spd-c-danger) 40%, #131920);\n  --spd-c-chip-bg-info: color-mix(in srgb, var(--spd-c-info) 40%, #131920);\n  --spd-c-chip-bg-neutral: color-mix(in srgb, var(--spd-c-text-muted) 40%, #131920);\n  --spd-c-chip-bg-primary: color-mix(in srgb, var(--spd-c-primary) 40%, #131920);',
     '  --spd-c-chip-bg-success: color-mix(in srgb, var(--spd-c-success) 40%, var(--spd-c-bg));\n  --spd-c-chip-bg-disabled: color-mix(in srgb, var(--spd-c-disabled) 40%, var(--spd-c-bg));\n  --spd-c-chip-bg-warning: color-mix(in srgb, var(--spd-c-warning) 40%, var(--spd-c-bg));\n  --spd-c-chip-bg-danger: color-mix(in srgb, var(--spd-c-danger) 40%, var(--spd-c-bg));\n  --spd-c-chip-bg-info: color-mix(in srgb, var(--spd-c-info) 40%, var(--spd-c-bg));\n  --spd-c-chip-bg-neutral: color-mix(in srgb, var(--spd-c-text-muted) 40%, var(--spd-c-bg));\n  --spd-c-chip-bg-primary: color-mix(in srgb, var(--spd-c-primary) 40%, var(--spd-c-bg));'],
  ],
};

for (const [file, rules] of Object.entries(edits)) {
  let text = fs.readFileSync(file, 'utf8');
  let applied = 0;
  const missed = [];
  for (const [from, to] of rules) {
    if (text.includes(from)) {
      text = text.split(from).join(to);
      applied++;
    } else {
      missed.push(from.split('\n')[0]);
    }
  }
  fs.writeFileSync(file, text, 'utf8');
  console.log(`${file}: applied ${applied}/${rules.length}`);
  for (const m of missed) console.log('   MISS:', m);
}
