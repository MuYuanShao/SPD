import fs from 'node:fs';

const file = 'frontend/src/styles/master-data.theme.css';
let text = fs.readFileSync(file, 'utf8');

const rules = [
  // ---------- :root token block ----------
  [`  --md-bg-page: #f1f5f4;
  --md-bg-card: #ffffff;
  --md-bg-elevated: #ffffff;
  --md-bg-soft: #f5f9f8;
  --md-bg-muted: #f9fbfb;`,
`  --md-bg-page: var(--spd-p-bg-page);
  --md-bg-card: var(--spd-p-bg);
  --md-bg-elevated: var(--spd-p-bg);
  --md-bg-soft: var(--spd-p-bg-subtle);
  --md-bg-muted: var(--spd-p-bg-muted);`],

  [`  --md-bg-header: linear-gradient(135deg,
    var(--primary, var(--md-primary)) 0%,
    color-mix(in srgb, var(--primary, var(--md-primary)) 70%, #14b8a6) 100%);
  --md-bg-header-overlay: linear-gradient(180deg,
    var(--primary-strong, var(--md-primary-strong)) 0%,
    color-mix(in srgb, var(--primary-strong, var(--md-primary-strong)) 50%, #0a2f2a) 100%);
  --md-bg-accent: linear-gradient(135deg,
    var(--primary, var(--md-primary)) 0%,
    color-mix(in srgb, var(--primary, var(--md-primary)) 50%, #14b8a6) 100%);`,
`  --md-bg-header: var(--spd-p-header-gradient);
  --md-bg-header-overlay: var(--spd-p-header-overlay-gradient);
  --md-bg-accent: var(--spd-p-accent-bar-gradient);`],

  [`  --md-border: #d6e4e0;
  --md-border-strong: #b7d3cc;
  --md-border-soft: #e8f0ed;`,
`  --md-border: var(--spd-p-border);
  --md-border-strong: var(--spd-p-border-strong);
  --md-border-soft: var(--spd-p-border-subtle);`],

  [`  --md-text: #1d2a35;
  --md-text-secondary: #4a5d68;
  --md-text-muted: #6b7f8e;
  --md-text-inverse: #ffffff;
  --md-text-heading: color-mix(in srgb, var(--md-primary-strong, #0a554e) 60%, #0a2f2a);`,
`  --md-text: var(--spd-p-text);
  --md-text-secondary: var(--spd-p-text-secondary);
  --md-text-muted: var(--spd-p-text-muted);
  --md-text-inverse: var(--spd-p-text-on-primary);
  --md-text-heading: var(--spd-p-text-strong);`],

  [`  --md-primary: var(--primary, #0d6b63);
  --md-primary-strong: var(--primary-strong, #0a554e);
  --md-primary-soft: color-mix(in srgb, var(--md-primary) 12%, #ffffff);
  --md-primary-hover: color-mix(in srgb, var(--md-primary) 85%, #000000);
  --md-primary-light: color-mix(in srgb, var(--md-primary) 55%, #14b8a6);`,
`  --md-primary: var(--spd-p-primary);
  --md-primary-strong: var(--spd-p-primary-strong);
  --md-primary-soft: var(--spd-p-primary-bg);
  --md-primary-hover: var(--spd-p-primary-hover);
  --md-primary-light: var(--spd-p-accent-soft);`],

  [`  --md-success: #16a34a;
  --md-success-soft: #ecfdf5;
  --md-success-border: #bbf7d0;
  --md-success-text: #15803d;`,
`  --md-success: var(--spd-p-success);
  --md-success-soft: var(--spd-p-success-bg);
  --md-success-border: var(--spd-p-success-border);
  --md-success-text: var(--spd-p-success-text);`],

  [`  --md-warning: #d97706;
  --md-warning-soft: #fffbeb;
  --md-warning-border: #fde68a;
  --md-warning-text: #b45309;`,
`  --md-warning: var(--spd-p-warning);
  --md-warning-soft: var(--spd-p-warning-bg);
  --md-warning-border: var(--spd-p-warning-border);
  --md-warning-text: var(--spd-p-warning-text);`],

  [`  --md-danger: #dc2626;
  --md-danger-soft: #fef2f2;
  --md-danger-border: #fecaca;
  --md-danger-text: #b91c1c;`,
`  --md-danger: var(--spd-p-danger);
  --md-danger-soft: var(--spd-p-danger-bg);
  --md-danger-border: var(--spd-p-danger-border);
  --md-danger-text: var(--spd-p-danger-text);`],

  [`  --inventory-table-heading-bg: linear-gradient(180deg, #f5f9f8 0%, #e8f0ed 100%);
  --md-info: #0284c7;
  --md-info-soft: #f0f9ff;
  --md-info-border: #bae6fd;
  --md-info-text: #0369a1;`,
`  --inventory-table-heading-bg: var(--spd-p-row-header-bg);
  --md-info: var(--spd-p-info);
  --md-info-soft: var(--spd-p-info-bg);
  --md-info-border: var(--spd-p-info-border);
  --md-info-text: var(--spd-p-info-text);`],

  [`  --md-shadow-1: 0 1px 2px 0 rgba(13, 93, 74, 0.04), 0 1px 4px -1px rgba(13, 93, 74, 0.02);
  --md-shadow-2: 0 4px 12px -2px rgba(13, 93, 74, 0.08), 0 2px 6px -2px rgba(13, 93, 74, 0.04);
  --md-shadow-3: 0 12px 32px -4px rgba(13, 93, 74, 0.16), 0 4px 12px -4px rgba(13, 93, 74, 0.08);`,
`  --md-shadow-1: var(--spd-p-shadow-1);
  --md-shadow-2: var(--spd-p-shadow-2);
  --md-shadow-3: var(--spd-p-shadow-3);`],

  // ---------- header decoration ----------
  [`    radial-gradient(circle at 8% 0%, rgba(255, 255, 255, 0.18) 0%, transparent 36%),`,
`    radial-gradient(circle at 8% 0%, var(--spd-p-header-sheen) 0%, transparent 36%),`],

  [`  height: 1px;
  background: rgba(255, 255, 255, 0.18);`,
`  height: 1px;
  background: var(--spd-p-header-sheen);`],

  [`  color: rgba(255, 255, 255, 0.72);`, `  color: var(--spd-p-header-text-secondary);`],
  [`  color: rgba(255, 255, 255, 0.92);`, `  color: var(--spd-p-header-text);`],
  [`  color: rgba(255, 255, 255, 0.66);`, `  color: var(--spd-p-header-text-secondary);`],
  [`  color: rgba(255, 255, 255, 0.78);`, `  color: var(--spd-p-header-text-secondary);`],
  [`  background: color-mix(in srgb, var(--md-primary) 30%, #ffffff);`,
   `  background: color-mix(in srgb, var(--md-primary) 30%, var(--spd-c-bg));`],

  // ---------- .md-btn family: brand-consolidated ----------
  [`  background: linear-gradient(135deg, #06b6d4 0%, #6366f1 50%, #8b5cf6 100%);
  color: #ffffff;`,
`  background: var(--spd-p-header-gradient);
  color: var(--spd-p-text-on-primary);`],

  [`  box-shadow: 0 2px 6px -2px rgba(99, 102, 241, 0.45);`,
   `  box-shadow: var(--spd-p-shadow-btn-primary);`],

  [`  background: linear-gradient(135deg, #0891b2 0%, #4f46e5 50%, #7c3aed 100%);
  color: #ffffff;
  box-shadow: 0 6px 14px -4px rgba(99, 102, 241, 0.55);`,
`  background: var(--spd-p-primary-hover-solid);
  color: var(--spd-p-text-on-primary);
  box-shadow: var(--spd-p-shadow-3);`],

  [`  box-shadow: 0 1px 3px -1px rgba(99, 102, 241, 0.4);`,
   `  box-shadow: var(--spd-p-shadow-1);`],

  [`  background: linear-gradient(135deg, #2563eb 0%, #4f46e5 100%);
  border-color: transparent;
  color: #ffffff;
  box-shadow: 0 4px 12px -4px rgba(37, 99, 235, 0.45);`,
`  background: var(--spd-p-header-gradient);
  border-color: transparent;
  color: var(--spd-p-text-on-primary);
  box-shadow: var(--spd-p-shadow-btn-primary);`],

  [`  background: linear-gradient(135deg, #1d4ed8 0%, #4338ca 100%);
  color: #ffffff;
  box-shadow: 0 8px 20px -4px rgba(37, 99, 235, 0.55);`,
`  background: var(--spd-p-primary-hover-solid);
  color: var(--spd-p-text-on-primary);
  box-shadow: var(--spd-p-shadow-3);`],

  [`  background: linear-gradient(135deg, #1e40af 0%, #3730a3 100%);`,
   `  background: var(--spd-p-primary-active-solid);`],

  [`  background: rgba(255, 255, 255, 0.16);
  border-color: rgba(255, 255, 255, 0.28);
  color: #ffffff;
  box-shadow: none;
  backdrop-filter: blur(2px);`,
`  background: var(--spd-p-btn-ghost-on-solid-bg);
  border-color: var(--spd-p-btn-ghost-on-solid-border);
  color: var(--spd-p-text-on-primary);
  box-shadow: none;
  backdrop-filter: blur(2px);`],

  [`  background: rgba(255, 255, 255, 0.28);
  border-color: rgba(255, 255, 255, 0.4);
  color: #ffffff;
  box-shadow: 0 4px 12px -4px rgba(0, 0, 0, 0.18);`,
`  background: var(--spd-p-btn-ghost-on-solid-hover-bg);
  border-color: var(--spd-p-btn-ghost-on-solid-hover-border);
  color: var(--spd-p-text-on-primary);
  box-shadow: var(--spd-p-shadow-2);`],

  [`  background: linear-gradient(135deg, #dc2626 0%, #e11d48 100%);
  border-color: transparent;
  color: #ffffff;
  box-shadow: 0 4px 12px -4px rgba(220, 38, 38, 0.45);`,
`  background: var(--spd-p-danger);
  border-color: transparent;
  color: var(--spd-p-text-on-primary);
  box-shadow: var(--spd-p-shadow-btn-danger);`],

  [`  background: linear-gradient(135deg, #b91c1c 0%, #be123c 100%);
  color: #ffffff;
  box-shadow: 0 8px 20px -4px rgba(220, 38, 38, 0.55);`,
`  background: var(--spd-p-danger-hover);
  color: var(--spd-p-text-on-primary);
  box-shadow: var(--spd-p-shadow-3);`],

  [`  background: linear-gradient(135deg, #991b1b 0%, #9f1239 100%);`,
   `  background: var(--spd-p-danger-active);`],

  [`  background: linear-gradient(135deg, #f97316 0%, #ea580c 100%);
  border-color: transparent;
  color: #ffffff;
  box-shadow: 0 4px 12px -4px rgba(249, 115, 22, 0.45);`,
`  background: var(--spd-p-warning);
  border-color: transparent;
  color: var(--spd-p-text-on-primary);
  box-shadow: var(--spd-p-shadow-btn-warning);`],

  [`  background: linear-gradient(135deg, #ea580c 0%, #c2410c 100%);
  color: #ffffff;
  box-shadow: 0 8px 20px -4px rgba(249, 115, 22, 0.55);`,
`  background: var(--spd-p-warning-hover);
  color: var(--spd-p-text-on-primary);
  box-shadow: var(--spd-p-shadow-3);`],

  [`  background: linear-gradient(135deg, #059669 0%, #0d9488 100%);
  border-color: transparent;
  color: #ffffff;
  box-shadow: 0 4px 12px -4px rgba(5, 150, 105, 0.45);`,
`  background: var(--spd-p-success);
  border-color: transparent;
  color: var(--spd-p-text-on-primary);
  box-shadow: var(--spd-p-shadow-btn-success);`],

  [`  background: linear-gradient(135deg, #047857 0%, #0f766e 100%);
  color: #ffffff;
  box-shadow: 0 8px 20px -4px rgba(5, 150, 105, 0.55);`,
`  background: var(--spd-p-success-hover);
  color: var(--spd-p-text-on-primary);
  box-shadow: var(--spd-p-shadow-3);`],

  [`  color: #2563eb;`, `  color: var(--spd-p-primary-text);`],
  [`  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;`,
`  background: var(--spd-p-primary-bg);
  color: var(--spd-p-primary-strong);`],
  [`  color: #dc2626;`, `  color: var(--spd-p-danger-text);`],
  [`  background: rgba(220, 38, 38, 0.1);
  color: #b91c1c;`,
`  background: var(--spd-p-danger-bg);
  color: var(--spd-p-danger-strong);`],
  [`  color: #059669;`, `  color: var(--spd-p-success-text);`],
  [`  background: rgba(5, 150, 105, 0.1);
  color: #047857;`,
`  background: var(--spd-p-success-bg);
  color: var(--spd-p-success-strong);`],

  [`  background: rgba(255, 255, 255, 0.16);
  color: #ffffff;
  cursor: pointer;`,
`  background: var(--spd-p-btn-ghost-on-solid-bg);
  color: var(--spd-p-text-on-primary);
  cursor: pointer;`],

  [`  background: rgba(255, 255, 255, 0.3);
  color: #ffffff;`,
`  background: var(--spd-p-btn-ghost-on-solid-hover-bg);
  color: var(--spd-p-text-on-primary);`],

  [`  box-shadow: 0 4px 10px -4px rgba(0, 0, 0, 0.18);`,
   `  box-shadow: var(--spd-p-shadow-2);`],

  // ---------- tab / chip ----------
  [`  border-color: var(--md-primary-border, rgba(13, 107, 99, 0.24));
  box-shadow: 0 0 0 1px rgba(13, 107, 99, 0.16) inset;`,
`  border-color: var(--md-primary-border, var(--spd-p-primary-border));
  box-shadow: 0 0 0 1px var(--spd-p-focus-ring) inset;`],

  [`.md-tab.active .md-tab-count {
  background: var(--md-primary);
  color: #ffffff;
}`,
`.md-tab.active .md-tab-count {
  background: var(--md-primary);
  color: var(--spd-p-text-on-primary);
}`],

  [`  background: var(--md-primary-soft);
  color: var(--md-primary-strong);
  border: 1px solid rgba(13, 107, 99, 0.24);`,
`  background: var(--md-primary-soft);
  color: var(--md-primary-strong);
  border: 1px solid var(--spd-p-primary-border);`],

  [`.md-chip.primary {
  background: var(--md-primary-soft);
  color: var(--md-primary-strong);
  border-color: rgba(13, 107, 99, 0.24);
}`,
`.md-chip.primary {
  background: var(--md-primary-soft);
  color: var(--md-primary-strong);
  border-color: var(--spd-p-primary-border);
}`],

  // ---------- table header gradients ----------
  [`  background: linear-gradient(180deg, #f5f9f8 0%, #e8f0ed 100%);`,
   `  background: var(--spd-p-row-header-bg);`],

  [`  background: linear-gradient(180deg, #f9fbfb 0%, #ffffff 100%);`,
   `  background: var(--spd-p-bg-subtle);`],

  [`  background: linear-gradient(180deg, #ffffff 0%, #f9fbfb 100%);`,
   `  background: var(--spd-p-bg-subtle);`],

  // ---------- misc ----------
  [`  box-shadow: -8px 0 12px -10px rgba(13, 93, 74, 0.2);`,
   `  box-shadow: var(--spd-p-shadow-2);`],
  [`  border: 1px solid rgba(13, 107, 99, 0.24);`,
   `  border: 1px solid var(--spd-p-primary-border);`],
  [`  background: rgba(13, 30, 26, 0.45);`, `  background: var(--spd-p-overlay);`],
  [`  color: #ffffff;
  border-radius: 8px;`, `  color: var(--spd-p-text-on-primary);
  border-radius: 8px;`],
];

let applied = 0;
const missed = [];
for (const [from, to] of rules) {
  if (!from || from.startsWith('  --md-md-warning-placeholder')) continue;
  if (text.includes(from)) {
    text = text.split(from).join(to);
    applied += 1;
  } else {
    missed.push(from.split('\n')[0]);
  }
}

fs.writeFileSync(file, text, 'utf8');
console.log(`applied ${applied}/${rules.length}`);
for (const m of missed) console.log('  MISS:', m);
