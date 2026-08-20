# Button Unification Implementation Plan

## Overview

Replace the current fragmented button CSS (10+ duplicated rule sets across `main.css` and scoped styles in 5 views) with a single unified `.btn` system, then migrate each view to use the new classes.

---

## Phase 1: Add `.btn` CSS System to `main.css`

Add the following block after line 2633 (after the existing `.system-action-button.danger` rule), **before** `.architecture-note`.

### 1.1 CSS Custom Properties (add to `:root` block at top of file)

```css
/* Button system tokens */
--btn-font-weight: 700;
--btn-radius: 8px;
--btn-min-h: 38px;
--btn-min-h-sm: 32px;
--btn-gap: 8px;
--btn-padding-x: 12px;
--btn-padding-x-sm: 10px;
--btn-text-color: #334652;
--btn-primary-text: #ffffff;
--btn-danger-text: var(--danger);
```

### 1.2 Base `.btn` class

```css
/* ===== Button system ===== */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--btn-gap);
  min-height: var(--btn-min-h);
  border: 1px solid var(--line);
  border-radius: var(--btn-radius);
  background: #ffffff;
  color: var(--btn-text-color);
  padding: 0 var(--btn-padding-x);
  font: inherit;
  font-weight: var(--btn-font-weight);
  cursor: pointer;
  text-decoration: none;
  white-space: nowrap;
  transition: opacity 0.15s, border-color 0.15s, background 0.15s;
}

.btn:hover {
  opacity: 0.85;
}
```

### 1.3 Variants

```css
/* Primary (filled) */
.btn-primary {
  border-color: var(--primary);
  background: var(--primary);
  color: var(--btn-primary-text);
}

/* Outline (matches existing system-action-button look) — alias for bare .btn */
/* .btn-outline is the DEFAULT look; this alias is for explicitness */
.btn-outline {
  /* inherits all from .btn */
}

/* Danger text variant (outlined with danger color) */
.btn-danger {
  color: var(--danger-text);
  border-color: var(--line);
  background: #ffffff;
}

/* Danger filled variant (for primary destructive actions) */
.btn-danger-filled {
  border-color: var(--danger-text);
  background: var(--danger-text);
  color: #ffffff;
}

/* Text-only button (no border/background, like table row actions) */
.btn-text {
  border: 0;
  background: transparent;
  color: #0f766e;
  font-weight: var(--btn-font-weight);
  gap: 4px;
  min-height: auto;
  padding: 0;
}

.btn-text:hover {
  opacity: 0.8;
}

/* Link-style button (underlined) */
.btn-link {
  display: inline;
  background: none;
  border: none;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  text-decoration: underline;
  text-underline-offset: 3px;
  min-height: auto;
}

.btn-link:hover {
  color: #1d4ed8;
}

/* Icon-only button (close buttons, etc.) */
.btn-icon {
  border: 0;
  background: transparent;
  color: inherit;
  padding: 0;
  min-height: var(--btn-min-h-sm);
}
```

### 1.4 Size variant

```css
.btn-sm {
  min-height: var(--btn-min-h-sm);
  padding: 0 var(--btn-padding-x-sm);
  gap: 6px;
}
```

### 1.5 State

```css
.btn:disabled,
.btn.disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
/* Ensure button text is always visible when disabled */
.btn:disabled {
  pointer-events: none;
}
/* Allow text-only buttons to remain legible when disabled */
.btn-text:disabled {
  opacity: 0.45;
}
```

### 1.6 Backward compatibility alias

```css
/* Backward compatibility: system-action-button maps to .btn-outline */
.system-action-button {
  composes: btn btn-outline; /* SCSS syntax — NOT valid CSS */
}
```

Since this is plain CSS, do not use `composes`. Instead, keep the existing `.system-action-button` block as-is — it already matches `.btn-outline` visually. After all views are migrated, remove `.system-action-button` in a cleanup pass. Alternatively, add a one-line alias:

```css
.system-action-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--btn-gap);
  min-height: var(--btn-min-h);
  border: 1px solid var(--line);
  border-radius: var(--btn-radius);
  background: #ffffff;
  color: var(--btn-text-color);
  padding: 0 var(--btn-padding-x);
  font: inherit;
  font-weight: var(--btn-font-weight);
  cursor: pointer;
}
/* Keep .system-action-button.primary and .system-action-button.danger as-is */
```

### 1.7 Deduplicate existing CSS

After adding the `.btn` system, the following existing rules in `main.css` can be REPLACED with `.btn` usage in the views and then REMOVED from the global stylesheet:

| Rule set | Lines | Replace with |
|---|---|---|
| `.hospital-action-row > button, .hospital-action-link, .batch-edit-menu summary, .batch-edit-menu button, .hospital-query-actions button` | 1563-1582 | `.btn` or `.btn-outline` |
| `.hospital-action-row .primary-action, .hospital-action-link.primary-action, .hospital-query-actions button[type='submit']` | 1584-1590 | `.btn-primary` |
| `.hospital-action-row .danger-action` | 1592-1594 | `.btn-danger` |
| `.master-page-title button, .master-toolbar button` | 1499-1513 | `.btn` |
| `.approval-action-row > button, .import-menu summary` | 1262-1276 | `.btn` |
| `.approval-action-row .primary-action` | 1278-1283 | `.btn-primary` |
| `.product-form-actions button` | 2396-2410 | `.btn` |
| `.product-form-actions .primary-action` | 2412-2416 | `.btn-primary` |
| `.approval-actions button` | 1961-1973 | `.btn` |
| `.approval-actions .approve` | 1975-1979 | `.btn-primary` |

**However**, this de-duplication should happen AFTER all views are migrated (Phase 3), to avoid breaking anything mid-migration.

---

## Phase 2: View-by-View Migration

### 2.1 PurchaseManagementView

**Changes to scoped CSS (`<style scoped>`):**

Remove these rules completely:
```css
.row-actions button,
.danger-text {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: transparent;
  color: #0f766e;
  font-weight: 700;
  cursor: pointer;
}

.danger-text {
  color: #dc2626;
}

.row-actions .muted-action {
  color: #66788a;
}

.text-link-button { ... }

.primary-action:disabled { ... }
```

Keep these (non-button styles):
```css
.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
```

**Template changes:**

| Current class | New class | Occurrences |
|---|---|---|
| `class="system-action-button"` | `class="btn"` | Lines: 467, 506, 514, 518, 778, 830, 837, 902, 933 |
| `class="primary-action"` | `class="btn btn-primary"` | Lines: 496, 510, 838, 903, 934 |
| `class="danger-text"` (standalone) | `class="btn-text btn-danger-text"` — or just `class="btn-text"` with a `style="color:#dc2626"` — actually add a `btn-text-danger` variant or simply use `class="btn-text"` and let the red come from `color: var(--danger-text)` ... | Lines: 577, 611, 678, 828, 893 |
| `class="muted-action"` | `class="btn-text muted-action"` — but better: inline style or add a utility. Simplest: just style the element directly since it's conditional via `:class` | Line 672 |
| `class="text-link-button"` | `class="btn-link"` | Line 568 |
| `class="icon-button"` | `class="btn-icon"` | Lines: 695, 743, 787, 912 |

**Icon-button note:** The `.icon-button` class has no scoped CSS definition in this view — it inherits from modal header styles. The tracking header at line 695 shows `<button class="icon-button" type="button" @click="detail = null">`. Replace with `class="btn-icon"`.

**Bare `<button>` elements in `.row-actions`:**

For all bare buttons inside `.row-actions` (lines 575, 576, 577, 609, 610, 611, 662, 663, 664, 667, 672, 678), add `class="btn-text"`. For the `.danger-text` buttons, add `class="btn-text btn-text-danger"`.

The `.row-actions button` selector in scoped CSS currently provides the text-button styling. After adding `.btn-text` to all these buttons, that scoped rule can be removed.

For the conditional class on line 672:
```html
:class="{ 'muted-action': !canCloseOrder(row) }"
```
Change to:
```html
:class="{ 'btn-text-muted': !canCloseOrder(row) }"
```
And add to scoped CSS (or better, add a `.btn-text-muted` class to main.css):
```css
.btn-text-muted { color: #66788a; }
/* Or just use the existing --text-muted var */
.btn-text-muted { color: var(--text-muted); }
```

**Special case — `.primary-action:disabled`:** Move this to the global `.btn:disabled` rule. The scoped version can be removed.

### 2.2 MasterDataListView

**This view has the most button patterns.** None of its buttons use `system-action-button` — they all rely on the global `.hospital-action-row > button` and `.approval-action` selectors.

**Template changes:**

| Location | Current pattern | New class |
|---|---|---|
| Page header refresh button (line 1136) | bare `<button>` | `class="btn"` |
| Action row primary buttons (lines 1144, 1247, 1313, 1385, 1473) | `class="primary-action"` | `class="btn btn-primary"` |
| Action row danger buttons (lines 1160, 1255, 1321, 1393, 1481) | `class="danger-action"` | `class="btn btn-danger"` |
| Action row neutral buttons (lines 1148, 1152, 1156, 1164, 1170, 1251, 1275, 1317, 1341, 1389, 1413, 1417, 1421, 1477, 1501, 1505, 1509) | bare `<button>` within `.hospital-action-row` | `class="btn"` |
| Import summary buttons (lines 1262, 1327, 1399, 1486) | `<summary>` in `.batch-edit-menu` | `class="btn"` |
| Import menu buttons (lines 1265, 1269, 1331, 1335, 1403, 1407, 1491, 1495) | bare `<button>` in `.supplier-import-menu` | `class="btn btn-sm"` |
| Query action buttons (lines 1234, 1238, 1300, 1304, 1372, 1376, 1460, 1464, 1548, 1552) | bare `<button>` in `.hospital-query-actions` | Submit: `class="btn btn-primary"`, Reset: `class="btn"` |
| Table row action "approval-action" buttons (lines 1602-1606, 1631-1688) | `class="approval-action"` | `class="btn-text"` |
| Table row danger-text buttons (lines 1606, 1647, 1654, 1660, 1670, 1674, 1680, 1685) | `class="approval-action danger-text"` | `class="btn-text"` with `style="color:var(--danger)"` OR add a `btn-text-danger` variant |
| Dialog close buttons (lines 1705, 1826, 1898, 1960, 2012, 2075) | bare `<button>` with X icon | `class="btn-icon"` |
| Dialog cancel buttons (lines 1812, 1884, 1946, 1998, 2061, 2119) | bare `<button>` in `.dialog-actions` or `.product-form-actions` | `class="btn"` |
| Dialog save buttons (lines 1813, 1885, 1947, 1999, 2062, 2120) | `class="primary-action"` | `class="btn btn-primary"` |
| Master toolbar button (line 1564) | bare `<button>` | `class="btn"` |

**Bare buttons at lines 1564 (master toolbar)** — this is in a fallback `v-else` section. Add appropriate class.

### 2.3 SystemConfigView

**Template changes:**

| Location | Current pattern | New class |
|---|---|---|
| Page header refresh (line 174) | bare `<button>` in `.master-page-title` | `class="btn"` |
| Action row primary (line 207) | `class="primary-action"` | `class="btn btn-primary"` |
| Action row neutral (line 211) | bare `<button>` | `class="btn"` |
| Query submit (line 231) | bare `<button>` | `class="btn btn-primary"` |
| Query reset (line 235) | bare `<button>` | `class="btn"` |
| Table row actions (lines 279, 280) | `class="approval-action"` | `class="btn-text"` |
| Dialog close (line 301) | bare `<button>` with X icon | `class="btn-icon"` |
| Dialog cancel (line 369) | bare `<button>` | `class="btn"` |
| Dialog save (line 370) | `class="primary-action"` | `class="btn btn-primary"` |

### 2.4 ReceivingAcceptanceView

**Template changes:**

| Location | Current pattern | New class |
|---|---|---|
| Page refresh (line 155) | `class="system-action-button"` | `class="btn"` |
| New receiving (line 172) | `class="primary-action"` | `class="btn btn-primary"` |
| Search button (line 176) | `class="system-action-button"` | `class="btn"` |
| Query button in grid (line 192) | bare `class="primary-action"` | `class="btn btn-primary"` |
| Table row actions (lines 230, 231, 235) | bare `<button>` in `.row-actions` | `class="btn-text"` |
| Modal close heading (lines 251, 308) | bare `<button>` with X + text | `class="btn-icon"` + add `btn btn-sm`-like; actually this is `<button type="button" @click="..."><X :size="18" /> 关闭</button>` — it has text "关闭", so use `class="btn"` |
| Add item (line 278) | bare `<button>` | `class="btn btn-sm"` |
| Delete item (line 292) | bare `<button>` in receiving item rows | `class="btn-text"` with danger color inline |
| Save receiving (line 296) | `class="primary-action"` | `class="btn btn-primary"` |

**Note for modal close buttons with text:** Lines 251 and 308 show `<button type="button" @click="..."><X :size="18" /> 关闭</button>`. The previous icon-button pattern in PurchaseManagementView used `.icon-button` for close buttons without text. Since these have text "关闭", use `class="btn btn-sm"` or just `class="btn"`.

### 2.5 InventoryWorkbenchView

**Template changes:**

| Location | Current pattern | New class |
|---|---|---|
| Page refresh (line 138) | `class="system-action-button"` | `class="btn"` |
| Query button in action row (line 155) | `class="system-action-button"` | `class="btn"` |
| Query button in grid (line 165) | `class="primary-action"` | `class="btn btn-primary"` |
| Table row actions (line 205) | bare `<button>` in `.row-actions` | `class="btn-text"` |
| Save stocktaking (line 262) | `class="primary-action"` | `class="btn btn-primary"` |
| Save price adjustment (line 293) | `class="primary-action"` | `class="btn btn-primary"` |
| Approve stocktaking (line 277) | `class="primary-action"` (in table cell) | `class="btn btn-primary btn-sm"` (since it's in a table cell) |
| Approve price (line 310) | `class="primary-action"` (in table cell) | `class="btn btn-primary btn-sm"` |

---

## Phase 3: Remove Duplicated CSS from `main.css`

After all 5 views have been migrated (in Phase 2), remove these rule sets from `main.css` — they are no longer needed because all buttons use `.btn` variants:

1. `.hospital-action-row > button, .hospital-action-link, .batch-edit-menu summary, .batch-edit-menu button, .hospital-query-actions button` (lines 1563-1582)
2. `.hospital-action-row .primary-action, .hospital-action-link.primary-action, .hospital-query-actions button[type='submit']` (lines 1584-1590)
3. `.hospital-action-row .danger-action` (lines 1592-1594)
4. `.master-page-title button, .master-toolbar button` (lines 1499-1513)
5. `.approval-action-row > button, .import-menu summary` (lines 1262-1276)
6. `.approval-action-row .primary-action` (lines 1278-1283)
7. `.product-form-actions button` (lines 2396-2410)
8. `.product-form-actions .primary-action` (lines 2412-2416)
9. `.approval-actions button` (lines 1961-1973)
10. `.approval-actions .approve` (lines 1975-1979)
11. `.approval-actions .reject` (lines 1980-1982)
12. `.approval-actions .return` (lines 1984-1986)

Also remove `danger-text` from `.approval-action.danger-text` patterns or keep `.danger-text` as a utility class since it's used in multiple places.

**Keep** these as they serve non-button purposes or are still needed:
- `.system-action-button` (keep temporarily for backward compatibility, remove in a future cleanup)
- `.approval-action` (this has a different visual style — `border: 0; background: transparent; color: #06152b;` — it's used for table row links/buttons. After migration to `.btn-text`, it can be removed.)

---

## Phase 4: Clean Up

1. Remove `.system-action-button` from `main.css` (once all usages across ALL views — not just these 5 — have been migrated)
2. Verify no regressions in other views (DashboardView, ProductDetailView, etc.)
3. Remove the following from PurchaseManagementView's scoped CSS:
   - `.row-actions button` base styling (replaced by `.btn-text`)
   - `.danger-text` global styling (replaced by `.btn-text` with inline or local color)
   - `.muted-action` (replaced by `.btn-text-muted` or var-based color)
   - `.text-link-button` (replaced by `.btn-link`)
   - `.primary-action:disabled` (replaced by global `.btn:disabled`)

---

## Order of Changes (Recommended)

1. **Add the `.btn` system to `main.css`** (Phase 1) — this is additive, zero risk
2. **Migrate 2 small views first** to validate: `SystemConfigView` then `InventoryWorkbenchView` — minimal button count, quick wins
3. **Migrate `ReceivingAcceptanceView`** — moderate size
4. **Migrate `PurchaseManagementView`** — has scoped CSS to remove
5. **Migrate `MasterDataListView`** — largest file, most buttons, most risk
6. **Remove duplicated global CSS** (Phase 3)
7. **Final cleanup** (Phase 4)

---

## Potential Risks

1. **Specificity conflicts:** Scoped CSS (`<style scoped>`) has higher specificity than global CSS due to the `data-v-xxxx` attribute selector. After removing scoped button rules, the global `.btn` class must match exactly. Test each view after migration.

2. **`.hospital-action-row > button` selector** currently applies to all `button` children of `.hospital-action-row` without needing a class. Adding explicit `.btn` classes is safer but requires touching each template.

3. **`.approval-action` removal risk:** This class is used in views beyond the 5 targeted (e.g., potentially in `PendingProductCatalogView`, `ProductDetailView`). Do NOT remove `.approval-action` until confirming it's unused elsewhere.

4. **Button height consistency:** `.hospital-action-row > button` uses `min-height: 36px` while `.system-action-button` uses `min-height: 38px`. The `.btn` base class uses 38px. This is a 2px difference — verify visually that action row buttons don't look out of place.

5. **Icon-button in modals:** The `.icon-button` class in PurchaseManagementView's modals currently inherits styles from `.edit-modal header button` (or similar). After switching to `.btn-icon`, verify alignment and sizing are consistent.

6. **Batch edit menu buttons:** In MasterDataListView, the `<summary>` elements in `.batch-edit-menu` look like buttons via CSS. Replacing with `<button class="btn">` wrapped inside `<summary>` changes semantics — `<summary>` must remain for the `<details>` disclosure to work. The better approach is to keep `<summary>` but add `class="btn"` to it, or restructure to use `<button>` outside `<details>`.

7. **The `<summary>` in `.batch-edit-menu`:** This is the "导入" trigger. It gets its button styling from `.hospital-action-row > button, .batch-edit-menu summary`. If we remove that rule, the `<summary>` loses its styling. Solution: add `class="btn"` to the `<summary>` element.

8. **Dialog close buttons in MasterDataListView** are bare `<button>` with only an `aria-label`. They get their icon styling from... where? Let me check — the header layout in `.supplier-dialog`:
   Looking at the supplier-dialog headers, they use `<header>` with flex layout. The close buttons are bare `<button type="button" aria-label="关闭">`. They currently don't have explicit button classes and rely on the default button styling... actually they rely on the `header button` styles or similar. This is risky — without `.btn-icon`, they may lose styling after removing the global button catch-all rules.

---

## Verification Steps

1. **After each view migration:**
   - Open the view in browser
   - Verify all buttons render with correct visual style
   - Verify hover states work
   - Verify disabled states work (where applicable)
   - Verify modal close buttons function
   - Verify table row action buttons look correct

2. **After Phase 3:**
   - Run full visual regression on all 5 views
   - Test all modal open/close flows
   - Test all CRUD action flows
   - Verify responsive layout isn't broken (check mobile viewport)

3. **Build check:**
   - Run `npm run build` (or the project's build command) to catch any template syntax errors

4. **Cross-view check:**
   - Verify DashboardView, ProductDetailView, and other views not in scope aren't broken by the global CSS cleanup
