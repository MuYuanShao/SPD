# 科室库房目录页 字体规范参考文档

> **文档类型**：单页字体规格参考（Reference Spec）
> **参考页**：科室库房目录页，路由 `/features/department-warehouse-catalog`
> **唯一权威标准**：`docs/ui-review/spd-typography-spec-final.md`（v3.0 最终定稿执行文档）
> **本文档性质**：对上述标准在**科室库房目录页**上的**落地展开**，不新增、不改写任何标准值
> **本文字档不含截图，截图由使用者自行补充**

---

## 目录

| 章 | 标题 | 内容 |
| --- | --- | --- |
| 1 | 文档说明 | 本页是什么、给谁看、怎么用 |
| 2 | 三档字号机制 | 切换方式、基准值、令牌速查表 |
| 3 | 页面区域拆解 | **核心章节**，从上到下逐区域元素清单 |
| 4 | 弹窗与浮层 | **核心章节**，本页全部弹窗 / 选择框 / 浮层 |
| 5 | 字体与字重基础规范 | 字体族、字重梯度、数字等宽、行高 |
| 6 | 状态条与配色 | 状态条规格、配色取用纪律 |
| 7 | 按钮规范 | 主 / 次 / 文字 / 小号 / 行内操作按钮 |
| 8 | 响应式表现 | 断点下的字号与列宽变化 |
| 9 | 新增同类页面的照抄清单 | 开发者 checklist |
| 10 | 附录 | 全量令牌表、术语表、变更记录 |

---

## 1. 文档说明

### 1.1 本页是什么

科室库房目录页是 SPD 平台**基础数据管理**模块下的一个列表维护页，功能码 `department-warehouse-catalog`，用于维护「科室 × 库房 × 商品」三者的目录关系，并支持按科室、库房、商品、状态检索。

页面技术构成（**实测**）：

| 项 | 值 | 来源 |
| --- | --- | --- |
| 路由 | `/features/department-warehouse-catalog` | `frontend/src/router/index.ts` 第 242 行通配路由 |
| 功能码 | `department-warehouse-catalog` | `frontend/src/config/featureCatalog.ts` 第 263–269 行 |
| 视图族 | `master-data-list` → `MasterDataListView.vue` | 同上，第 267 行 |
| 页面分支 | `isDepartmentWarehouseCatalog` | `MasterDataListView.vue` 第 1285 行（筛选区）、第 2521 行（表格区） |
| 样式体系 | `.spd-unified-list` + `.md-*` + 页面专属 CSS | `styles/list/unified-list.css`、`styles/master-data.theme.css`、`styles/master-data/department-warehouse-catalog.css` |
| 表格 | Element Plus `el-table`（`ulc-table`） | `MasterDataListView.vue` 第 2617 行 |
| 分页 | `MdPagination.vue` → `el-pagination` | `MasterDataListView.vue` 第 2771 行 |
| 弹窗 | `DepartmentWarehouseCatalogDialog.vue`、`DepartmentWarehouseCatalogBatchDialog.vue` | `MasterDataListView.vue` 第 42–43 行 |
| 确认框 | `ConfirmDialog.vue`（经 `useSpdConfirm`） | `MasterDataListView.vue` 第 47 行 |

> **配色变体页**：另有 `department-warehouse-catalog-night`（暗夜黑）与 `department-warehouse-catalog-calm`（静墨宣纸）两个独立路由，视觉表现不同但**字体规格完全一致**。本文档以正式路由 `/features/department-warehouse-catalog` 为准，另两个变体页直接套用本文档全部数值。

### 1.2 给谁看

| 角色 | 必读章节 | 使用方式 |
| --- | --- | --- |
| 产品经理 | 1、2、6、7、8 | 评审页面字号是否达标，裁定新争议 |
| 前端开发 | 2、3、4、5、7、9 | 按第 3、4 章逐元素照抄类名与令牌 |
| QA | 2、3、4、8 | 按「标准档 / 大档 / 特大档」三档逐元素对屏核对 |
| 新人 / AI 编码助手 | 2、3、9 | 新增同款列表页时直接查第 3 章类名并抄值 |

### 1.3 一句话原则

> **深色文字 + 浅色背景 + 三档可调。**

拆解为三条硬性要求：

1. **深色文字 + 浅色背景** —— 本页所有正文、表头、状态条、按钮文字统一使用深色字（正文墨色 `--spd-c-text`、次级灰蓝 `--spd-c-text-muted`、语义深色），背景统一为浅色（卡片白 `--spd-c-bg`、筛选区白、表格斑马纹 `--spd-c-bg-page`、状态条为**语义色 40% 透明度底**）。禁止出现浅底浅字或深底浅字造成对比度塌陷的组合。
2. **三档可调** —— 全页字号**必须**由 `--spd-font-size-*` 令牌驱动，禁止任何裸 `font-size: Npx`。用户在顶栏切换标准 / 大 / 特大档时，本页**所有**文字（含表头、状态条、弹窗、按钮、分页、提示条）**同步缩放**，不得出现局部不变的「孤岛」。
3. **字号只表达语义层级，不表达强调** —— 同一语义元素在本页与全站其它页面必须字号一致（例：`商品名称`列数据在任何列表页都是 `--spd-font-size-base`）。

### 1.4 怎么用

- **新增同类列表页**：第 9 章 checklist 是照抄清单，逐项确认后再提交。
- **本页改造**：第 3 章按区域给出「元素 | CSS 类名 | 字号令牌 | 三档值 | 字重 | 行高」，第 4 章给出全部弹窗的同结构表。**禁止自造数值**，任何新出现的字号必须先在 `docs/ui-review/spd-typography-spec-final.md` 第 3 章找到归属令牌。
- **验收**：按第 2.5 节的验证脚本在浏览器控制台逐档切换并打印 computed style。

### 1.5 数值来源标注约定

本文档每条数值都带来源标注（沿用标准文档第 1.6 节约定）：

| 标注 | 含义 |
| --- | --- |
| **实测** | 直接读取源文件确认，或浏览器 `getComputedStyle` 确认 |
| **CSS 推断** | 从样式表规则推断，未在浏览器中逐一验证 |
| **令牌推导** | 由 `--spd-font-size-*` 推导，三档值必然成立 |

**本文档中绝大多数条目的来源组合为「实测 + 令牌推导」**：类名与 `font-size` 表达式来自源码实读，三档 px 值由 `tokens/typography.css` 的 `calc()` 表达式推导。三档值在本页**必然成立**，因为本页所有相关类名都已令牌化（这一点经源码逐条核对确认，见第 3、4 章）。

### 1.6 本文档的范围边界

| 范围 | 是否覆盖 |
| --- | --- |
| 科室库房目录页的页面主体（标题区 / 筛选区 / 工具栏 / 表格 / 分页 / 空态） | ✅ 第 3 章 |
| 该页面出现的全部弹窗、浮层、选择器、确认框、提示条 | ✅ 第 4 章 |
| 该页面使用的全局框架（顶栏 / 迷你侧栏 / 页签栏 / 面包屑） | ⚠️ 只在第 3.1 节列出锚点，细则见标准文档第 4.1 节 |
| 登录页 | ❌ 独立体系，不参与档位（标准文档第 4.15 节） |
| 打印排版 | ❌ 另立规范（标准文档第 4.18 节） |
| 颜色 / 圆角 / 间距的完整规范 | ❌ 另见配色与布局规范，本文档只标注与字号耦合的部分 |

---

## 2. 三档字号机制

### 2.1 如何切换

| 入口 | 说明 | 来源（**实测**） |
| --- | --- | --- |
| 顶栏主题切换器 → 「界面字号」 | 面板内三张卡片：`标准 14px` / `大 16px` / `特大 18px`，每张卡片带真实基准字号的「Aa」样张，可直接肉眼比较 | `frontend/src/components/common/ThemeSwitcher.vue` 第 149–181 行 |
| 系统设置 | 同一入口的表述变体，本仓当前实现在顶栏主题面板内 | — |
| 持久化键 | `localStorage['spd.theme.fontSize']`，取值 `standard` / `large` / `xlarge` | `frontend/src/stores/theme.ts` 第 65 行 |
| 状态管理 | `useThemeStore()` 的 `fontSize` state、`currentFontSize` getter、`setFontSize(id)` action | `frontend/src/stores/theme.ts` 第 192–248 行 |

**落地机制**（**实测**，`stores/theme.ts` 第 128–137 行）：

```js
export function applyFontSize(id: FontSizeId) {
  const root = document.documentElement
  if (id === DEFAULT_FONT_SIZE) {
    root.removeAttribute('data-font-size')   // standard 档：不写属性
  } else {
    root.setAttribute('data-font-size', id)  // large / xlarge 档
  }
}
```

**驱动链路**（**实测**，`styles/tokens/typography.css` 第 145–150 行）：

```css
html { font-size: var(--spd-fs-base); }   /* 整站 rem 体系等比缩放 */
body { font-size: var(--spd-fs-base); line-height: var(--spd-lh-base); }
```

**规定**：

1. 字号档位是**全局**设置，本页**没有**页面级字号覆盖入口。不得在 `.master-page` 或本页专属 CSS 中重定义 `--spd-font-size-*`。
2. 本页的 `data-spd-theme="calm-paper"`（静墨宣纸变体页使用）**与** `data-font-size` **完全隔离**，主题切换不得影响字号档位，反之亦然。

### 2.2 基准值表

| 档位 | `data-font-size` | `--spd-fs-base` | 缩放比 | `--spd-table-cell-padding-y` | 适用场景 |
| --- | --- | --- | --- | --- | --- |
| **standard**（默认） | 不写属性（默认） | **14px** | 100% | 10px | 常规显示器 · 密度优先 |
| **large** | `large` | **16px** | 114% | 12px | 27 寸以上屏幕 · 轻度老花 |
| **xlarge** | `xlarge` | **18px** | 129% | 13px | 视力偏弱 · 高分辨率远距使用 |

> **来源**：**实测** —— `styles/tokens/typography.css` 第 19–47 行；`stores/theme.ts` 第 81–85 行 `FONT_SIZES` 定义。三档基准与 `--spd-table-cell-padding-y` 均为源码写死值，非推导值。

### 2.3 字号令牌速查表（三档实际 px）

**来源**：**令牌推导** —— 全部取自 `styles/tokens/typography.css` 第 56–106 行的 `calc()` 表达式，三档值必然成立。

| 令牌 | 表达式 | standard (14) | large (16) | xlarge (18) | 本页用途 |
| --- | --- | --- | --- | --- | --- |
| `--spd-font-size-xs` | `base − 3px` | **11px** | **13px** | **15px** | 列设置浮层的列宽标注（`120px`） |
| `--spd-font-size-sm` | `base − 1px` | **13px** | **15px** | **17px** | 筛选标签、表格次级说明、弹窗字段标签、弹窗副标题、章节标题、状态说明 |
| `--spd-font-size-base` | `base` | **14px** | **16px** | **18px** | 表格数据、表格主字段、编码列、时间列、状态条、按钮、输入控件文字、空态标题 |
| `--spd-font-size-md` | `base + 1px` | **15px** | **17px** | **19px** | **正文强调文本**（本页未使用） |
| `--spd-font-size-lg` | `base + 2px` | **16px** | **18px** | **20px** | 确认框标题、批量弹窗申领范围标题 |
| `--spd-font-size-xl` | `base + 4px` | **18px** | **20px** | **22px** | 新增/编辑弹窗主标题 |
| `--spd-font-size-2xl` | `base + 6px` | **20px** | **22px** | **24px** | Element Plus 弹窗/确认框标题（备选实现） |
| `--spd-font-size-3xl` | `base + 10px` | **24px** | **26px** | **28px** | 页面主标题 |
| `--spd-font-size-4xl` | `base + 16px` | **30px** | **32px** | **34px** | 展示型大数值（**本页不使用**） |
| `--spd-font-size-5xl` | `base + 24px` | **38px** | **40px** | **42px** | 超大展示数值（**本页不使用**） |
| **`--spd-font-size-table-head`** | `= var(--spd-font-size-base)` | **14px** | **16px** | **18px** | **表格表头专用**，随档位缩放 |
| `--spd-lh-tight` | 固定 | 1.35 | 1.35 | 1.35 | 按钮、状态条、徽标、表格单元格 |
| `--spd-lh-base` | 固定 | 1.5 | 1.5 | 1.5 | 正文默认、输入控件、筛选标签、说明文字 |
| `--spd-lh-loose` | 固定 | 1.65 | 1.65 | 1.65 | 长段落、确认框正文 |

### 2.4 行高令牌

**来源**：**实测** —— `styles/tokens/typography.css` 第 33–35 行。

| 令牌 | 值 | 本页用途 |
| --- | --- | --- |
| `--spd-lh-tight` | **1.35** | 表格单元格、状态条、工具栏按钮、行内操作按钮、弹窗主标题 |
| `--spd-lh-base` | **1.5** | 筛选标签、输入控件文字、筛选按钮、空态、分页、列设置浮层 |
| `--spd-lh-loose` | **1.65** | 确认框正文段落 |

**规定**：本页任何 `line-height` 必须写成上述三个令牌之一，或与令牌等值的无单位倍数（`1` / `1.35` / `1.5` / `1.65`）。禁止 1.2 / 1.3 / 1.4 / 1.55 / 1.6 / 1.7 / 1.85 等散落值。

### 2.5 三档验证脚本

在浏览器控制台执行，逐档切换并打印关键元素的 computed `font-size`：

```javascript
const targets = [
  '.ulc-table .el-table__header th .cell',       // 表头
  '.ulc-table td.el-table__cell',                  // 数据单元格
  '.ulc-strong',                                   // 主字段
  '.ulc-sub',                                      // 次级说明
  '.ulc-chip',                                     // 状态条
  '.ulc-action',                                   // 行内操作按钮
  '.ulc-filter label > span',                      // 筛选标签
  '.ulc-filter .el-input__wrapper',                // 筛选输入框
  '.ulc-filter-actions .md-btn',                   // 筛选按钮
  '.md-table-toolbar .md-btn',                     // 工具栏按钮
  '.md-pagination',                                // 分页容器
  '.ulc-col-settings-pop header strong',           // 列设置浮层标题
  '.ulc-col-settings-item small',                  // 列设置宽度标注
  '.catalog-dialog > header > div > h3',           // 新增/编辑弹窗标题
  '.catalog-dialog-form label > span',             // 弹窗字段标签
  '.catalog-dialog-form input',                    // 弹窗输入框
  '.catalog-dialog-actions .btn',                  // 弹窗底部按钮
  '.spd-confirm-title',                            // 确认框标题
  '.spd-confirm-desc'                              // 确认框正文
]
for (const t of targets) {
  const el = document.querySelector(t)
  if (el) console.log(t, getComputedStyle(el).fontSize)
}
```

切换档位：

```javascript
document.documentElement.setAttribute('data-font-size', 'large')
document.documentElement.setAttribute('data-font-size', 'xlarge')
document.documentElement.removeAttribute('data-font-size')  // 回到 standard
```

**预期结果**（standard / large / xlarge）：

| 选择器 | standard | large | xlarge |
| --- | --- | --- | --- |
| `.ulc-table .el-table__header th .cell` | 14 | 16 | 18 |
| `.ulc-table td.el-table__cell` | 14 | 16 | 18 |
| `.ulc-strong` | 14 | 16 | 18 |
| `.ulc-sub` | 13 | 15 | 17 |
| `.ulc-chip` | 14 | 16 | 18 |
| `.ulc-action` | 14 | 16 | 18 |
| `.ulc-filter label > span` | 13 | 15 | 17 |
| `.ulc-filter .el-input__wrapper` | 14 | 16 | 18 |
| `.ulc-filter-actions .md-btn` | 14 | 16 | 18 |
| `.md-table-toolbar .md-btn` | 14 | 16 | 18 |
| `.md-pagination` | 13 | 15 | 17 |
| `.ulc-col-settings-pop header strong` | 14 | 16 | 18 |
| `.ulc-col-settings-item small` | 11 | 13 | 15 |
| `.catalog-dialog > header > div > h3` | 18 | 20 | 22 |
| `.catalog-dialog-form label > span` | 13 | 15 | 17 |
| `.catalog-dialog-form input` | 14 | 16 | 18 |
| `.catalog-dialog-actions .btn` | 14 | 16 | 18 |
| `.spd-confirm-title` | 16 | 18 | 20 |
| `.spd-confirm-desc` | 14 | 16 | 18 |

---

## 3. 页面区域拆解

> **本章是本文档核心。** 按页面**从上到下**逐区域拆解。元素清单中的类名全部来自源码实读（**实测**），字号令牌为实读的 `font-size` 表达式，三档 px 值为**令牌推导**。

### 3.0 页面整体结构

科室库房目录页从上到下共 5 个区域 + 1 个空态分支：

```
┌─────────────────────────────────────────────┐
│ 区域 A · 页面标题区                          │  MdPageHeader
├─────────────────────────────────────────────┤
│ 区域 B · 筛选区                              │  form.ulc-filter
├─────────────────────────────────────────────┤
│ 区域 C · 工具栏                              │  MdDataTableCard #toolbar
├─────────────────────────────────────────────┤
│ 区域 D · 数据表格                            │  el-table.ulc-table
├─────────────────────────────────────────────┤
│ 区域 E · 分页区                              │  MdPagination
└─────────────────────────────────────────────┘
```

**页面主体实际 DOM 骨架**（**实测**，`MasterDataListView.vue`）：

| 区域 | 行号 | 容器类名 | 说明 |
| --- | --- | --- | --- |
| 页面容器 | 636–641 | `.master-page` + `.master-page--aligned` | `isDepartmentWarehouseCatalog` 时加 `--aligned` |
| A 标题区 | 1286–1291 | `MdPageHeader` 组件 | 位于 `section.md-partner-page.spd-unified-list` 内 |
| B 筛选区 | 1292–1343 | `form.md-hospital-filter.ulc-filter.spd-unified-list` | 4 个筛选字段 + 2 个按钮 |
| C 工具栏 | 2526–2614 | `MdDataTableCard` 的 `#toolbar` slot | 落在 `.md-table-toolbar-main` 内 |
| D 表格 | 2615–2768 | `div.ulc-table-wrap` → `el-table.ulc-table` | 8 个业务列 + 操作列 |
| E 分页 | 2770–2780 | `MdPagination` → `div.md-pagination` | 位于 `#pagination` slot |

---

### 3.1 区域 A · 页面标题区

**DOM 结构**（**实测**，`MasterDataListView.vue` 第 1286–1291 行）：

```vue
<MdPageHeader
  title="科室库房目录"
  description="维护科室与库房的商品目录关系，按科室、库房、商品、状态快速检索。"
  :loading="loading"
  @refresh="loadPage"
/>
```

**组件内部**（**实测**，`components/master-data/MdPageHeader.vue` 第 23–37 行）：

```vue
<h2 class="md-page-accessible-title">{{ title }}</h2>
<div class="md-page-actions">
  <button type="button" class="md-btn" :disabled="loading" aria-label="刷新">
    <RefreshCw :size="16" :class="{ 'md-spin': loading }" />
    <span>刷新</span>
  </button>
  <slot name="actions" />
</div>
```

#### 元素清单表

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A-01 | 页面主标题（无障碍标题） | `.md-page-accessible-title` | 视觉隐藏 | — | — | — | — | — | 实测 | `width:1px; height:1px; clip-path:inset(50%)` 视觉隐藏，仅供读屏。**不占用版面**，不产生可见字号 |
| A-02 | 页面可见主标题 | `.master-page-title h2` | `--spd-font-size-3xl` | 24px | 26px | 28px | 600 | `--spd-lh-tight` | 实测 + 令牌推导 | `styles/master-data/master-data-list.override.css` 第 75–83 行。字体族 `--spd-font-family-display` |
| A-03 | 页面副标题 / 描述 | `.master-page-title p`、`.master-page-title span` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | 同文件第 86–95 行。`MdPageHeader` 的 `description` prop 当前**全局隐藏**，属标准文档 D-20 待清理项 |
| A-04 | 刷新按钮文字 | `.md-page-actions .md-btn` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 实测 + 令牌推导 | `styles/master-data.theme.css` 第 327–350 行 |
| A-05 | 刷新按钮图标 | `.md-page-actions .md-btn svg` | 图标 16px（固定） | 16px | 16px | 16px | — | — | 实测 | Lucide `RefreshCw :size="16"`，**固定不随档位缩放**。属标准文档第 3.6 节图标白名单 |
| A-06 | 刷新按钮加载态文字 | `.md-page-actions .md-btn`（`loading` 时） | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 实测 | 文字不变，仅图标加 `.md-spin` 旋转动画 |

#### 全局框架锚点（同一页面内可见）

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 来源 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A-07 | 面包屑（当前项） | `.breadcrumb-line` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 / 当前 600 | 实测（标准文档 4.1 G-16） |
| A-08 | 左侧主菜单一级项 | `.nav-item` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | 实测（标准文档 4.1 G-14） |
| A-09 | 迷你侧栏一级项 | `.spd-mini-nav-item` | `--spd-font-size-sm` | 13px | 15px | 17px | 500 / 激活 600 | 实测（标准文档 4.1 G-11） |
| A-10 | 顶栏用户姓名 | `.spd-user-name` | `--spd-font-size-sm` | 13px | 15px | 17px | 600 | 实测（标准文档 4.1 G-06） |
| A-11 | 顶栏主题/字号切换入口 | `.spd-icon-btn` | 图标 16px（固定） | 16px | 16px | 16px | — | 实测（标准文档 4.1 G-05） |

---

### 3.2 区域 B · 筛选区

**DOM 结构**（**实测**，`MasterDataListView.vue` 第 1292–1343 行）：

```vue
<form class="md-hospital-filter ulc-filter spd-unified-list" @submit.prevent="submitDepartmentWarehouseCatalogQuery">
  <div class="ulc-filter-fields">
    <label><span>科室</span><input class="md-input" type="search" placeholder="输入科室名称" ... /></label>
    <label><span>库房</span><input class="md-input" type="search" placeholder="输入关联库房" ... /></label>
    <label><span>商品</span><input class="md-input" type="search" placeholder="商品编码或名称" ... /></label>
    <label><span>目录状态</span><el-select ...><el-option label="全部状态" ... /></el-select></label>
  </div>
  <div class="md-hospital-filter-actions ulc-filter-actions">
    <button type="button" class="md-btn" @click="resetDepartmentWarehouseCatalogQuery">
      <RefreshCw :size="16" /><span>重置</span>
    </button>
    <button type="submit" class="md-btn md-btn-primary">
      <Search :size="16" /><span>查询</span>
    </button>
  </div>
</form>
```

#### 元素清单表

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| B-01 | 筛选标签「科室」 | `.ulc-filter label > span` | `--spd-font-size-sm` | 13px | 15px | 17px | 600 | `--spd-lh-base` | 实测 + 令牌推导 | `unified-list.css` 第 688–693 行 |
| B-02 | 筛选标签「库房」 | 同上 | `--spd-font-size-sm` | 13px | 15px | 17px | 600 | `--spd-lh-base` | 实测 + 令牌推导 | 4 个标签类名一致 |
| B-03 | 筛选标签「商品」 | 同上 | `--spd-font-size-sm` | 13px | 15px | 17px | 600 | `--spd-lh-base` | 实测 + 令牌推导 | — |
| B-04 | 筛选标签「目录状态」 | 同上 | `--spd-font-size-sm` | 13px | 15px | 17px | 600 | `--spd-lh-base` | 实测 + 令牌推导 | — |
| B-05 | 「科室」输入框文字 | `.ulc-filter .md-input` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | `unified-list.css` 第 697–703 行。控件高 `min-height: 38px` |
| B-06 | 「库房」输入框文字 | 同上 | `--spd-font-size-base` | 14px | 16px | 18px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | 占位符「输入关联库房」 |
| B-07 | 「商品」输入框文字 | 同上 | `--spd-font-size-base` | 14px | 16px | 18px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | 占位符「商品编码或名称」 |
| B-08 | 「目录状态」下拉框文字 | `.ulc-filter .el-select__wrapper` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | `unified-list.css` 第 697–703 行。控件高 `min-height: 38px` |
| B-09 | 「目录状态」下拉选项 | `.el-select-dropdown__item` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | `--spd-lh-tight` | 实测 + 令牌推导 | `spd-override.css` 第 1167–1170 行。选项「全部状态」「启用」「停用」 |
| B-10 | 输入框占位符 | `::placeholder` | 继承控件字号（`--spd-font-size-base`） | 14px | 16px | 18px | 400 | 随控件 | 实测 + 令牌推导 | `unified-list.css` 第 707–712 行；颜色 `opacity: 0.7` |
| B-11 | 「重置」按钮文字 | `.ulc-filter-actions .md-btn` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 实测 + 令牌推导 | `unified-list.css` 第 748–761 行。控件高 `min-height: 38px` |
| B-12 | 「重置」按钮图标 | `.ulc-filter-actions .md-btn svg` | 图标 16px（固定） | 16px | 16px | 16px | — | — | 实测 | Lucide `RefreshCw :size="16"` |
| B-13 | 「查询」按钮文字 | `.ulc-filter-actions .md-btn.md-btn-primary` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 实测 + 令牌推导 | 同上；背景 `--ulc-primary`，白字 |
| B-14 | 「查询」按钮图标 | `.ulc-filter-actions .md-btn.md-btn-primary svg` | 图标 16px（固定） | 16px | 16px | 16px | — | — | 实测 | Lucide `Search :size="16"` |
| B-15 | 筛选区容器 | `.ulc-filter` | 继承 | — | — | — | — | — | 实测 | `padding: 16px 18px`，`border-radius: 12px`，白底。**无文字** |
| B-16 | 筛选字段栅格容器 | `.ulc-filter-fields` | 继承 | — | — | — | — | — | 实测 | `grid-template-columns: repeat(auto-fit, minmax(150px, 1fr))` |

> **本页筛选区无「展开 / 收起」按钮**。`unified-list.css` 中 `.ulc-filter-actions .rotated` 的箭头旋转规则是为「医院商品目录」页的「更多 / 收起」按钮准备的，本页未使用（**实测** `MasterDataListView.vue` 第 1333–1343 行，`isDepartmentWarehouseCatalog` 的筛选区只有「重置」「查询」两个按钮）。

---

### 3.3 区域 C · 工具栏

**DOM 结构**（**实测**，`MasterDataListView.vue` 第 2526–2614 行，落在 `MdDataTableCard` 的 `#toolbar` slot 内，容器为 `.md-table-toolbar` → `.md-table-toolbar-main`）。

#### 元素清单表

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| C-01 | 「新增目录」按钮文字 | `.md-table-toolbar .md-btn` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 实测 + 令牌推导 | `unified-list.css` 第 436–444 行。控件高 `min-height: 34px`，圆角 4px，边框 1px |
| C-02 | 「新增目录」按钮图标 | `.md-btn svg` | 图标 16px（固定） | 16px | 16px | 16px | — | — | 实测 | Lucide `Plus :size="16"` |
| C-03 | 「批量维护」按钮文字 | `.md-table-toolbar .md-btn` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 实测 + 令牌推导 | 未选中任何行时 `disabled`（`opacity: 0.5`） |
| C-04 | 「批量维护」按钮图标 | `.md-btn svg` | 图标 16px（固定） | 16px | 16px | 16px | — | — | 实测 | Lucide `ListPlus :size="16"` |
| C-05 | 「编辑」按钮文字 | `.md-table-toolbar .md-btn` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 实测 + 令牌推导 | 仅选中**恰好 1 行**时可用 |
| C-06 | 「编辑」按钮图标 | `.md-btn svg` | 图标 16px（固定） | 16px | 16px | 16px | — | — | 实测 | Lucide `Pencil :size="16"` |
| C-07 | 「启用」按钮文字 | `.md-table-toolbar .md-btn` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 实测 + 令牌推导 | 未选中行时 `disabled` |
| C-08 | 「启用」按钮图标 | `.md-btn svg` | 图标 16px（固定） | 16px | 16px | 16px | — | — | 实测 | Lucide `RefreshCw :size="16"` |
| C-09 | 「停用」按钮文字 | `.md-table-toolbar .md-btn.md-btn-warning` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 实测 + 令牌推导 | 语义色 `--spd-c-warning` |
| C-10 | 「停用」按钮图标 | `.md-btn svg` | 图标 16px（固定） | 16px | 16px | 16px | — | — | 实测 | Lucide `Ban :size="16"` |
| C-11 | 「删除」按钮文字 | `.md-table-toolbar .md-btn.md-btn-danger` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 实测 + 令牌推导 | 语义色 `--spd-c-danger` |
| C-12 | 「删除」按钮图标 | `.md-btn svg` | 图标 16px（固定） | 16px | 16px | 16px | — | — | 实测 | Lucide `Trash2 :size="16"` |
| C-13 | 「列设置」按钮文字 | `.md-table-toolbar .md-btn` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 实测 + 令牌推导 | 点击展开列设置浮层（见第 4.3 节） |
| C-14 | 「列设置」按钮图标 | `.md-btn svg` | 图标 16px（固定） | 16px | 16px | 16px | — | — | 实测 | Lucide `Columns3 :size="16"` |
| C-15 | 「列设置」展开箭头 | `.md-btn svg`（`ChevronDown`） | 图标 14px（固定） | 14px | 14px | 14px | — | — | 实测 | Lucide `ChevronDown :size="14"`。展开时加 `.rotated` 旋转 180° |
| C-16 | 工具栏已选计数 chip | `.md-chip.primary` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `--spd-lh-tight` | 实测 + 令牌推导 | `master-data.theme.css` 第 760–770 行。**本页未使用**（`isDepartmentWarehouseCatalog` 的表格卡未传 `metaCount` / `#meta-extra`），此处登记以备同类页参照 |
| C-17 | 工具栏容器 | `.md-table-toolbar` / `.md-table-toolbar-main` | 继承 | — | — | — | — | — | 实测 | `master-data.theme.css` 第 729–750 行。`padding: 10px 16px`，`min-height: 48px`。**无自身文字** |

---

### 3.4 区域 D · 数据表格

**DOM 结构**（**实测**，`MasterDataListView.vue` 第 2615–2768 行）：

```vue
<div class="ulc-table-wrap">
  <el-table ref="catalogTableRef" v-loading="loading" class="ulc-table"
            :data="page.rows" row-key="code" border stripe
            style="width: 100%" empty-text="暂无符合条件的科室库房目录" ...>
```

**列结构**（**实测**，共 8 个业务列 + 1 个操作列 + 1 个选择列）：

| 列序 | column-key | 列名 | 默认宽 | 可见性 | 可拖拽调宽 |
| --- | --- | --- | --- | --- | --- |
| 0 | `type="selection"` | （复选框列） | 48px | 固定 | 否 |
| 1 | `scope` | 科室 / 库房 | 141px | 可列设置控制 | 是 |
| 2 | `productCode` | 商品编码 | 119px | 可列设置控制 | 是 |
| 3 | `productName` | 商品名称 | 171px（`flexible`，理想 300px） | 可列设置控制 | 是 |
| 4 | `spec` | 规格型号 | 141px（`flexible`，理想 190px） | 可列设置控制 | 是 |
| 5 | `manufacturer` | 生产厂家 | 156px（`flexible`，理想 280px） | 可列设置控制 | 是 |
| 6 | `source` | 维护来源 | 71px | 可列设置控制 | 是 |
| 7 | `status` | 状态 | 71px | 可列设置控制 | 是 |
| 8 | `updateTime` | 更新时间 | 126px | 可列设置控制 | 是 |
| 9 | （固定右侧） | 操作 | 170px | 固定 | 是 |

> 来源：**实测** —— `MasterDataListView.vue` 第 2615–2629 行的 `el-table-column` 定义与 `CATALOG_COL_DEFAULTS`（第 163–170 行附近）。

#### D-1 表头行

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| D-01 | 表头文字「科室 / 库房」 | `.ulc-table .el-table__header th.el-table__cell .cell` | `--spd-font-size-table-head` | 14px | 16px | 18px | 700 | `--spd-lh-tight` | 实测 + 令牌推导 | `unified-list.css` 第 92–101 行。白字，主色实底 |
| D-02 | 表头文字「商品编码」 | 同上 | `--spd-font-size-table-head` | 14px | 16px | 18px | 700 | `--spd-lh-tight` | 实测 + 令牌推导 | 每列表头均包 `<span class="ulc-th-text">` |
| D-03 | 表头文字「商品名称」 | 同上 | `--spd-font-size-table-head` | 14px | 16px | 18px | 700 | `--spd-lh-tight` | 实测 + 令牌推导 | — |
| D-04 | 表头文字「规格型号」 | 同上 | `--spd-font-size-table-head` | 14px | 16px | 18px | 700 | `--spd-lh-tight` | 实测 + 令牌推导 | — |
| D-05 | 表头文字「生产厂家」 | 同上 | `--spd-font-size-table-head` | 14px | 16px | 18px | 700 | `--spd-lh-tight` | 实测 + 令牌推导 | — |
| D-06 | 表头文字「维护来源」 | 同上 | `--spd-font-size-table-head` | 14px | 16px | 18px | 700 | `--spd-lh-tight` | 实测 + 令牌推导 | 居中对齐 |
| D-07 | 表头文字「状态」 | 同上 | `--spd-font-size-table-head` | 14px | 16px | 18px | 700 | `--spd-lh-tight` | 实测 + 令牌推导 | 居中对齐 |
| D-08 | 表头文字「更新时间」 | 同上 | `--spd-font-size-table-head` | 14px | 16px | 18px | 700 | `--spd-lh-tight` | 实测 + 令牌推导 | 居中对齐 |
| D-09 | 表头文字「操作」 | 同上 | `--spd-font-size-table-head` | 14px | 16px | 18px | 700 | `--spd-lh-tight` | 实测 + 令牌推导 | 固定右侧列 |
| D-10 | 表头文本包装层 | `.ulc-th-text` | 继承表头 | 14px | 16px | 18px | 继承 700 | 继承 | 实测 | `unified-list.css` 第 177–185 行。`text-overflow: ellipsis`，单行不换行 |
| D-11 | 表头单元格上下留白 | `.el-table__header th.el-table__cell` | — | — | — | — | — | — | 实测 | `padding: var(--spd-table-cell-padding-y) 6px` → 10 / 12 / 13px 垂直、6px 水平 |

#### D-2 数据单元格（按列）

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| D-12 | 数据单元格通用基准 | `.ulc-table td.el-table__cell` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | `--spd-lh-tight` | 实测 + 令牌推导 | `unified-list.css` 第 128–138 行。全部居中 |
| D-13 | 科室名（主字段） | `.ulc-strong` | `--spd-font-size-base` | 14px | 16px | 18px | 700 | `--spd-lh-tight` | 实测 + 令牌推导 | 第 229–234 行 |
| D-14 | 库房名称（次级说明） | `.ulc-sub` | `--spd-font-size-sm` | 13px | 15px | 17px | 500 | `--spd-lh-tight` | 实测 + 令牌推导 | 第 237–242 行。文案「库房名称：XXX」/「未关联库房」 |
| D-15 | 库房等级（次级说明） | `.ulc-sub` | `--spd-font-size-sm` | 13px | 15px | 17px | 500 | `--spd-lh-tight` | 实测 + 令牌推导 | 文案「库房等级：XXX」 |
| D-16 | 商品编码 | `.ulc-mono` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | `--spd-lh-tight` | 实测 + 令牌推导 | 第 245–251 行。等宽字体 `"Cascadia Mono", "SFMono-Regular", Consolas, monospace` + `font-variant-numeric: tabular-nums` |
| D-17 | 商品名称（主字段） | `.ulc-strong.ulc-wrap-text` | `--spd-font-size-base` | 14px | 16px | 18px | 700 | `--spd-lh-tight` | 实测 + 令牌推导 | 长文本换行（`overflow-wrap: anywhere`），**不截断** |
| D-18 | 商品名称下的次级说明 | `.ulc-sub.ulc-wrap-text.is-sub` | `--spd-font-size-sm` | 13px | 15px | 17px | 500 | `--spd-lh-tight` | 实测 + 令牌推导 | 第 211–217 行。文案「科室可申领商品」 |
| D-19 | 规格型号 | `.ulc-strong.ulc-wrap-text` | `--spd-font-size-base` | 14px | 16px | 18px | 700 | `--spd-lh-tight` | 实测 + 令牌推导 | 换行显示 |
| D-20 | 生产厂家 | `.ulc-strong.ulc-wrap-text` | `--spd-font-size-base` | 14px | 16px | 18px | 700 | `--spd-lh-tight` | 实测 + 令牌推导 | 换行显示 |
| D-21 | 维护来源状态条 | `.ulc-chip`（`.is-primary` / `.is-info` / `.is-warning` / `.is-neutral`） | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `--spd-lh-tight` | 实测 + 令牌推导 | 第 255–270 行。取值映射见第 6.2 节 |
| D-22 | 状态列状态条 | `.ulc-chip`（`.is-success` / `.is-danger`） | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `--spd-lh-tight` | 实测 + 令牌推导 | 「启用」/「停用」。详见第 6 章 |
| D-23 | 更新时间 | `.ulc-time` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | `--spd-lh-tight` | 实测 + 令牌推导 | 第 785–792 行。`font-variant-numeric: tabular-nums`，`white-space: nowrap` |
| D-24 | 单元格多行堆叠容器 | `.ulc-cell-stack` | 继承 | — | — | — | — | — | 实测 | 第 187–194 行。`flex-direction: column; gap: 3px` |

#### D-3 行内操作按钮（操作列）

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| D-25 | 「编辑」按钮文字 | `.ulc-actions .ulc-action` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 实测 + 令牌推导 | `unified-list.css` 第 357–374 行。控件高 `min-height: 30px`，圆角 4px，边框 1px |
| D-26 | 「启用 / 停用」按钮文字 | `.ulc-actions .ulc-action.is-info` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 实测 + 令牌推导 | 按当前状态动态切换文案 |
| D-27 | 「删除」按钮文字 | `.ulc-actions .ulc-action.is-danger` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 实测 + 令牌推导 | 语义色 `--spd-c-danger` |
| D-28 | 操作按钮容器 | `.ulc-actions` | 继承 | — | — | — | — | — | 实测 | `flex-wrap: nowrap; gap: 6px`。**操作列强制单行**，宽度不足时压缩间距而非折行 |
| D-29 | 操作列单元格 | `.ulc-table td.el-table__cell:has(.ulc-actions) .cell` | 继承 | — | — | — | — | — | 实测 | 第 150–154 行。`white-space: nowrap` |

#### D-4 复选框 / 选择列

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| D-30 | 行选择复选框 | `.el-table-column--selection .el-checkbox` | 无文字 | — | — | — | — | — | 实测 | 列宽 48px，`align="center"`。复选框为纯图形控件，**无字号** |
| D-31 | 全选复选框（表头） | 同上 | 无文字 | — | — | — | — | — | 实测 | 选中态底色 `--primary`（`spd-override.css` 第 487–491 行） |

#### D-5 表格容器与滚动

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| D-32 | 表格滚动容器 | `.ulc-table-wrap` | 继承 | — | — | — | — | — | 实测 | `unified-list.css` 第 47–56 行。`border: 1px solid var(--ulc-border)`，`border-radius: 12px`，`overflow: hidden` |
| D-33 | 表格本体 | `.ulc-table` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | `--spd-lh-tight` | 实测 + 令牌推导 | 第 58–61 行。`border` + `stripe`（斑马纹） |
| D-34 | 数据单元格上下留白 | `.ulc-table td.el-table__cell` | — | — | — | — | — | — | 实测 | `padding: var(--spd-table-cell-padding-y) 6px` → 10 / 12 / 13px |

---

### 3.5 区域 E · 分页区

**DOM 结构**（**实测**，`MasterDataListView.vue` 第 2770–2780 行）：

```vue
<MdPagination
  :page="catalogCurrentPage"
  :total-pages="catalogPageTotal"
  :total="catalogTotal"
  :size="catalogPageSize"
  :loading="loading"
  @update:page="loadDepartmentWarehouseCatalogPage"
  @update:size="changeDepartmentWarehouseCatalogPageSize"
/>
```

**组件内部**（**实测**，`components/master-data/MdPagination.vue` 第 55–67 行）：

```vue
<div class="md-pagination">
  <el-pagination :current-page="page" :page-size="size" :page-sizes="pageSizes"
                 :total="total" :disabled="loading"
                 layout="total, sizes, prev, pager, next, jumper" background
                 @current-change="handleCurrentChange" @size-change="handleSizeChange" />
</div>
```

#### 元素清单表

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| E-01 | 分页容器 | `.md-pagination` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | `master-data.theme.css` 第 1060–1073 行。`padding: 12px 18px`，右对齐 |
| E-02 | 总条数汇总文案「共 N 条」 | `.el-pagination .el-pagination__total` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | `spd-override.css` 第 1086–1091 行 |
| E-03 | 每页条数下拉 | `.el-pagination .el-pagination__sizes` / `.el-pagination .el-select__wrapper` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | 同上第 1093–1098 行。选项 10 / 20 / 50 / 100 |
| E-04 | 每页条数下拉选项 | `.el-select-dropdown__item` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | `--spd-lh-tight` | 实测 + 令牌推导 | `spd-override.css` 第 1167–1170 行 |
| E-05 | 「上一页」按钮 | `.el-pagination .btn-prev` | `--spd-font-size-sm` | 13px | 15px | 17px | 500 | `--spd-lh-tight` | 实测 + 令牌推导 | `spd-override.css` 第 1079–1085 行。禁用时 `opacity` 降低 |
| E-06 | 「下一页」按钮 | `.el-pagination .btn-next` | `--spd-font-size-sm` | 13px | 15px | 17px | 500 | `--spd-lh-tight` | 实测 + 令牌推导 | 同上 |
| E-07 | 页码数字（1 / 2 / 3 …） | `.el-pagination .el-pager li` | `--spd-font-size-sm` | 13px | 15px | 17px | 500 | `--spd-lh-tight` | 实测 + 令牌推导 | 同上。当前页 `.is-active` 底色 `--primary`，白字 |
| E-08 | 跳页文案「前往 N 页」 | `.el-pagination .el-pagination__jump` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | `spd-override.css` 第 1086–1091 行 |
| E-09 | 跳页输入框 | `.el-pagination .el-input__wrapper` / `.el-input__inner` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | 同上第 1093–1098 行 |
| E-10 | 当前页指示（备用方案） | `.md-page-indicator` | `--spd-font-size-sm` | 13px | 15px | 17px | 600 | `--spd-lh-base` | 实测 + 令牌推导 | `master-data.theme.css` 第 1121–1130 行。**本页未使用**（`MdPagination` 走 Element Plus 原生），此处登记以备同类页参照 |

---

### 3.6 区域 F · 空态 / 加载态 / 错误态

#### 空态

**DOM 结构**（**实测**，`MasterDataListView.vue` 第 2764–2766 行）：

```vue
<template #empty>
  <MdEmptyState text="暂无符合条件的科室库房目录" hint="尝试调整筛选条件或新增目录" />
</template>
```

**组件内部**（**实测**，`components/master-data/MdEmptyState.vue` 第 14–25 行）：`loading` 为真时渲染 `.md-loading`，否则渲染 `.md-empty`。

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F-01 | 空态主文案 | `.md-empty strong` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `--spd-lh-base` | 实测 + 令牌推导 | `master-data.theme.css` 第 1154–1160 行。文案「暂无符合条件的科室库房目录」 |
| F-02 | 空态提示文案 | `.md-empty p` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | 第 1162–1169 行。文案「尝试调整筛选条件或新增目录」 |
| F-03 | 空态图标 | `.md-empty svg` | 图标 48px（固定） | 48px | 48px | 48px | — | — | 实测 | Lucide `Inbox :size="48"`，`stroke-width: 1.5`。**固定不随档位缩放** |
| F-04 | 空态容器 | `.md-empty` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | 第 1132–1147 行。`padding: 56px 20px`，虚线边框 |
| F-05 | 表格原生空态文字（备用） | `.ulc-table .el-table__empty-text` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | `unified-list.css` 第 168–173 行。`el-table` 的 `empty-text` 属性值与 `#empty` slot 一致；本页用 slot 覆盖，实际显示 F-01 / F-02 |

#### 加载态

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F-06 | 加载文案「正在加载...」 | `.md-loading` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | `master-data.theme.css` 第 1171–1183 行 |
| F-07 | 加载转圈 | `.md-loading-spinner` | 无文字 | — | — | — | — | — | 实测 | 32×32px 纯图形，`animation: md-spin 0.8s linear infinite` |
| F-08 | 表格遮罩加载指示 | `.el-loading-text` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | — | 实测（标准文档 4.19 X-01） | `el-table` 的 `v-loading` 遮罩。**默认不显示文字**；如需文字必须用 `--spd-font-size-sm` |

#### 错误态

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F-09 | 顶部错误提示条 | `.spd-top-notify.el-message` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | `useNotify.ts` 走 `ElMessage`，`customClass: 'spd-top-notify'`，`zIndex: 999999` |
| F-10 | 成功提示条 | 同上 | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | 保存 / 启用 / 停用 / 删除成功后的顶部消息 |
| F-11 | 表单校验错误条 | `.md-error` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | `master-data.theme.css` 第 1198 行起。**本页弹窗未使用**，登记以备同类页参照 |

---

### 3.7 区域 G · 悬浮 / 底部元素

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| G-01 | AI 助手悬浮球文字 | `.ai-medical-fab` | `--spd-font-size-lg` | 16px | 18px | 20px | 800 | `1` | 实测（标准文档 4.16 A-01） | 右下角固定悬浮球，全站通用 |
| G-02 | AI 助手窗口标题 | `.ai-medical-drag-title strong` | `--spd-font-size-xl` | 18px | 20px | 22px | 800 | `1.1` | 实测（标准文档 4.16 A-02） | 展开面板左上角 |
| G-03 | AI 助手窗口副标 | `.ai-medical-drag-title span` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | `--spd-lh-base` | 实测（标准文档 4.16 A-03） | 「院内SPD智能协作」 |
| G-04 | AI 助手输入框文字 | `.ai-medical-input-wrap textarea` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | `1.55` | 实测（标准文档 4.16 A-10） | 底部输入区 |
| G-05 | AI 助手免责声明 | `.ai-medical-composer small` | `--spd-font-size-xs` | 11px | 13px | 15px | 400 | `--spd-lh-base` | 实测（标准文档 4.16 A-12） | 全站最小字号白名单项（免责说明） |
| G-06 | 页脚版权 | `.fli-footer` | `--spd-font-size-xs` | 11px | 13px | 15px | 400 | `--spd-lh-base` | 实测（标准文档 4.1 G-17） | 页面底部版权行 |

---

## 4. 弹窗与浮层

> **本章单独成章**，覆盖科室库房目录页上出现的**全部**弹窗、浮层、选择器、确认框与提示条。

### 4.0 本页弹窗总清单

| # | 弹窗 / 浮层 | 触发方式 | 组件文件 | 是否使用 Element Plus 弹窗 | 章节 |
| --- | --- | --- | --- | --- | --- |
| M-1 | **新增 / 编辑目录弹窗** | 工具栏「新增目录」/「编辑」，或行内「编辑」 | `components/master-data/DepartmentWarehouseCatalogDialog.vue` | ❌ 自绘（`.catalog-dialog`） | 4.1 |
| M-2 | **批量目录维护弹窗** | 工具栏「批量维护」 | `components/master-data/DepartmentWarehouseCatalogBatchDialog.vue` | ❌ 自绘（`.batch-catalog-dialog`） | 4.2 |
| M-3 | **列设置浮层** | 工具栏「列设置」 | `MasterDataListView.vue` 内联 | ❌ 自绘浮层（`.ulc-col-settings-pop`） | 4.3 |
| M-4 | **科室搜索下拉**（在 M-1 / M-2 内） | M-1「科室」输入框聚焦 / 回车 / 点放大镜 | 同 M-1 / M-2 | ❌ 自绘下拉（`.catalog-search-results` / `.batch-dept-results`） | 4.4 |
| M-5 | **商品搜索下拉**（在 M-1 内） | M-1「商品」输入框聚焦 / 回车 / 点放大镜 | 同 M-1 | ❌ 自绘下拉（`.catalog-search-results`） | 4.4 |
| M-6 | **商品选择区**（在 M-2 内） | M-2 展开后的复选列表 | 同 M-2 | ❌ 自绘列表（`.product-options`） | 4.5 |
| M-7 | **确认框** | 停用 / 删除目录前二次确认 | `components/common/ConfirmDialog.vue`（经 `useSpdConfirm`） | ❌ 自绘（`.spd-confirm-panel`） | 4.6 |
| M-8 | **顶部消息提示** | 各类操作成功 / 失败 | `useNotify.ts` → `ElMessage` | ✅ Element Plus Message | 4.7 |
| M-9 | **状态条提示（Tooltip）** | 表格单元格悬浮 | Element Plus Tooltip | ✅ Element Plus Tooltip | 4.8 |

---

### 4.1 M-1 · 新增 / 编辑目录弹窗

**触发**：工具栏「新增目录」（`mode = 'create'`）、工具栏「编辑」（`mode = 'edit'`，需恰好选中 1 行）、行内「编辑」。

**宽度**：`min(1040px, calc(100vw - 32px))`；`max-height: calc(100vh - 32px)`。

**DOM 结构**（**实测**，`DepartmentWarehouseCatalogDialog.vue` 第 79–203 行）：

```vue
<div class="attachment-preview-mask">
  <section class="catalog-dialog" role="dialog" aria-modal="true">
    <header class="catalog-dialog-header">
      <div>
        <p class="catalog-dialog-kicker"><Boxes :size="15" /> 科室库房目录</p>
        <h3 id="catalog-dialog-title">{{ mode === 'create' ? '新增可申领商品' : '编辑目录关系' }}</h3>
        <span id="catalog-dialog-description">设置科室在指定关联库房下可以申领的商品</span>
      </div>
      <button class="btn-icon" type="button" aria-label="关闭"><X :size="18" /></button>
    </header>
    <form class="catalog-dialog-form">
      <section class="catalog-dialog-section">
        <div class="catalog-dialog-section-heading">
          <Building2 :size="19" />
          <div><strong id="catalog-scope-heading">申领范围</strong><span>输入科室名称回车或点击放大镜搜索，再选择该科室已经关联的库房</span></div>
        </div>
        <label class="catalog-search-field">
          <span>科室 *</span>
          <div class="catalog-search-box"><input ... /><button class="btn-icon search-trigger"><Search :size="16" /></button></div>
          <ul class="catalog-search-results">...</ul>
        </label>
        <label>
          <span>关联库房 *</span>
          <select ...><option value="">请选择关联库房</option>...</select>
          <small class="catalog-field-hint">库房列表直接来自科室管理中的科室库房关联关系</small>
        </label>
      </section>
      <section class="catalog-dialog-section">
        <div class="catalog-dialog-section-heading">
          <Boxes :size="19" />
          <div><strong id="catalog-product-heading">目录商品</strong><span>搜索医院目录；未绑定库房或已维护的商品仅供查看</span></div>
        </div>
        <label class="catalog-search-field">
          <span>商品 *</span>
          ...
          <ul class="catalog-search-results">...</ul>
        </label>
        <label>
          <span>目录状态</span>
          <select v-model.number="form.status"><option :value="1">启用</option><option :value="0">停用</option></select>
        </label>
      </section>
      <div class="catalog-dialog-actions">
        <button class="btn" type="button">取消</button>
        <button class="btn btn-primary" type="submit"><Save :size="16" />{{ mode === 'create' ? '创建目录' : '保存修改' }}</button>
      </div>
    </form>
  </section>
</div>
```

#### 元素清单表

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M1-01 | 弹窗主标题 | `.catalog-dialog > header > div > h3` | `--spd-font-size-xl` | 18px | 20px | 22px | 700 | `--spd-lh-tight` | 实测 + 令牌推导 | `styles/master-data/department-warehouse-catalog.css` 第 331–337 行。文案「新增可申领商品」/「编辑目录关系」 |
| M1-02 | 弹窗眉标（kicker） | `.catalog-dialog-kicker` | `--spd-font-size-sm` | 13px | 15px | 17px | 800 | — | 实测 + 令牌推导 | 同文件第 365–374 行。文案「科室库房目录」，`letter-spacing: 0.06em` |
| M1-03 | 弹窗副标题 | `.catalog-dialog > header > div > span` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | `--spd-lh-tight` | 实测 + 令牌推导 | 同文件第 339–343 行。文案「设置科室在指定关联库房下可以申领的商品」 |
| M1-04 | 关闭按钮 | `.catalog-dialog > header > .btn-icon` | 图标 18px（固定） | 18px | 18px | 18px | — | — | 实测 | 同文件第 345–359 行。32×32px，**无文字** |
| M1-05 | 章节标题「申领范围」 | `.catalog-dialog-section-heading strong` | `--spd-font-size-sm` | 13px | 15px | 17px | 700 | — | 实测 + 令牌推导 | 同文件第 415–419 行 |
| M1-06 | 章节说明文字 | `.catalog-dialog-section-heading span` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | — | 实测 + 令牌推导 | 同文件第 421–426 行 |
| M1-07 | 章节标题「目录商品」 | `.catalog-dialog-section-heading strong` | `--spd-font-size-sm` | 13px | 15px | 17px | 700 | — | 实测 + 令牌推导 | 同 M1-05 |
| M1-08 | 章节说明文字 | `.catalog-dialog-section-heading span` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | — | 实测 + 令牌推导 | 同 M1-06 |
| M1-09 | 字段标签「科室 *」 | `.catalog-dialog-form label > span` | `--spd-font-size-sm` | 13px | 15px | 17px | 500 | — | 实测 + 令牌推导 | 同文件第 438–443 行。必填星号为文字的一部分 |
| M1-10 | 字段标签「关联库房 *」 | 同上 | `--spd-font-size-sm` | 13px | 15px | 17px | 500 | — | 实测 + 令牌推导 | 同上 |
| M1-11 | 字段标签「商品 *」 | 同上 | `--spd-font-size-sm` | 13px | 15px | 17px | 500 | — | 实测 + 令牌推导 | 同上 |
| M1-12 | 字段标签「目录状态」 | 同上 | `--spd-font-size-sm` | 13px | 15px | 17px | 500 | — | 实测 + 令牌推导 | 同上 |
| M1-13 | 「科室」搜索输入框文字 | `.catalog-dialog-form input` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | 继承 | 实测 + 令牌推导 | 同文件第 446–459 行。`font-size: var(--spd-font-size-base) !important`。控件高 32px |
| M1-14 | 「科室」搜索占位符 | `::placeholder` | 继承控件（`--spd-font-size-base`） | 14px | 16px | 18px | 400 | 继承 | 实测 + 令牌推导 | 占位符「输入科室名称/编码搜索」 |
| M1-15 | 「关联库房」下拉文字 | `.catalog-dialog-form select` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | 继承 | 实测 + 令牌推导 | 同 M1-13。原生 `<select>`，非 Element Plus |
| M1-16 | 「关联库房」下拉选项 | `select option` | 继承控件（`--spd-font-size-base`） | 14px | 16px | 18px | 400 | 继承 | 实测 | 原生 select option 继承 select 字号 |
| M1-17 | 「目录状态」下拉文字 | `.catalog-dialog-form select` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | 继承 | 实测 + 令牌推导 | 同 M1-13。选项「启用」「停用」 |
| M1-18 | 「商品」搜索输入框文字 | `.catalog-dialog-form input` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | 继承 | 实测 + 令牌推导 | 同 M1-13 |
| M1-19 | 「商品」搜索占位符 | `::placeholder` | 继承控件 | 14px | 16px | 18px | 400 | 继承 | 实测 + 令牌推导 | 占位符「输入商品编码/名称/规格回车或点击放大镜搜索」 |
| M1-20 | 搜索放大镜按钮 | `.catalog-search-box .search-trigger` | 图标 16px（固定） | 16px | 16px | 16px | — | — | 实测 | 32×32px，**无文字** |
| M1-21 | 字段辅助说明 | `.catalog-field-hint` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | — | 实测 + 令牌推导 | 同文件第 478–483 行。文案「库房列表直接来自科室管理中的科室库房关联关系」 |
| M1-22 | 「取消」按钮文字 | `.catalog-dialog-actions .btn` | `--spd-font-size-base` | 14px | 16px | 18px | 500 | 继承 | 实测 + 令牌推导 | 同文件第 501–521 行。控件高 32px，圆角 6px，白底 1px 边框 |
| M1-23 | 「创建目录」/「保存修改」按钮文字 | `.catalog-dialog-actions .btn.btn-primary` | `--spd-font-size-base` | 14px | 16px | 18px | 700 | 继承 | 实测 + 令牌推导 | 同文件第 528–534 行。主色实底白字 |
| M1-24 | 「创建目录」/「保存修改」按钮图标 | `.btn-primary svg` | 图标 16px（固定） | 16px | 16px | 16px | — | — | 实测 | Lucide `Save :size="16"` |
| M1-25 | 弹窗遮罩 | `.attachment-preview-mask` | 无文字 | — | — | — | — | — | 实测 | 纯背景层，**无字号** |
| M1-26 | 弹窗表单区容器 | `.catalog-dialog-form` | 继承 | — | — | — | — | — | 实测 | `padding: 14px 18px 0` |
| M1-27 | 弹窗章节卡片 | `.catalog-dialog-section` | 继承 | — | — | — | — | — | 实测 | `border-radius: 8px`，`background: #fbfdfd` |
| M1-28 | 弹窗底部操作条 | `.catalog-dialog-actions` | 继承 | — | — | — | — | — | 实测 | `position: sticky; bottom: 0`，毛玻璃底 |

---

### 4.2 M-2 · 批量目录维护弹窗

**触发**：工具栏「批量维护」（需至少选中 1 行）。

**宽度**：`min(960px, calc(100vw - 32px))`；`max-height: calc(100vh - 48px)`。

**DOM 结构**（**实测**，`DepartmentWarehouseCatalogBatchDialog.vue` 第 70–214 行）：

```vue
<section class="batch-catalog-dialog" role="dialog" aria-modal="true">
  <header class="batch-catalog-header">
    <div>
      <p class="batch-catalog-kicker"><Layers3 :size="15" /> 科室库房目录</p>
      <h3 id="batch-catalog-title">批量目录维护</h3>
      <span id="batch-catalog-description">为一个科室库房一次维护多种可申领商品</span>
    </div>
    <button class="btn-icon" aria-label="关闭"><X :size="18" /></button>
  </header>
  <form class="batch-catalog-form">
    <div class="batch-catalog-fields">
      <div class="batch-catalog-scope-heading">
        <Building2 :size="19" />
        <div><strong>申领范围</strong><span>选择目标科室与其已关联库房，随后勾选该库房已绑定的商品</span></div>
      </div>
      <label class="batch-dept-search"><span>科室 *</span> ... <ul class="batch-dept-results">...</ul></label>
      <label><span>关联库房 *</span><select ...></select></label>
      <label><span>目录状态</span><select ...></select></label>
    </div>
    <section class="product-picker">
      <div class="product-picker-toolbar">
        <div class="catalog-filter-title"><Layers3 :size="17" /><span>选择目录商品</span></div>
        <label class="product-search"><input ... /><button class="btn-icon search-trigger"><Search :size="16" /></button></label>
        <button class="btn btn-primary"><Layers3 :size="16" />全选/取消当前结果</button>
        <strong class="product-picker-count">已选 {{ form.productCodes.length }} 项</strong>
      </div>
      <div class="product-options">
        <label class="product-option">
          <input type="checkbox" />
          <span class="product-code">{{ product.productCode }}</span>
          <span class="product-name">{{ product.productName }}<em class="product-flag is-muted">未绑定库房</em></span>
          <span class="product-spec">{{ product.specModel || '-' }}</span>
        </label>
        <p class="product-empty">该库房尚未绑定可维护的目录商品…</p>
      </div>
      <p class="product-hint">已存在目录会更新状态，已删除目录会恢复；单次最多维护 500 项。</p>
    </section>
    <div class="batch-catalog-actions">
      <button class="btn btn-secondary">取消</button>
      <button class="btn btn-primary"><Save :size="16" />保存 N 项目录</button>
    </div>
  </form>
</section>
```

#### 元素清单表

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M2-01 | 弹窗主标题 | `.batch-catalog-header h3` | `--spd-font-size-2xl` | 20px | 22px | 24px | 700 | — | 实测 + 令牌推导 | `DepartmentWarehouseCatalogBatchDialog.vue` 第 265–271 行（组件 scoped）。深色渐变头部上的白字，`letter-spacing: 0.3px` |
| M2-02 | 弹窗眉标（kicker） | `.batch-catalog-header p` | `--spd-font-size-base` | 14px | 16px | 18px | 700 | — | 实测 + 令牌推导 | 第 252–264 行。胶囊底（`rgba(255,255,255,0.18)`） |
| M2-03 | 弹窗副标题 | `.batch-catalog-header span` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | — | 实测 + 令牌推导 | 第 273–277 行。文案「为一个科室库房一次维护多种可申领商品」 |
| M2-04 | 关闭按钮 | `.batch-catalog-header .btn-icon` | 图标 18px（固定） | 18px | 18px | 18px | — | — | 实测 | 第 279–290 行。**无文字** |
| M2-05 | 章节标题「申领范围」 | `.batch-catalog-scope-heading strong` | `--spd-font-size-lg` | 16px | 18px | 20px | 700 | — | 实测 + 令牌推导 | 第 320–326 行 |
| M2-06 | 章节说明文字 | `.batch-catalog-scope-heading span` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | — | 实测 + 令牌推导 | 第 328–331 行 |
| M2-07 | 字段标签「科室 *」 | `.batch-catalog-fields label` | 继承 label 字号（`--spd-font-size-sm`） | 13px | 15px | 17px | 700 | — | 实测 + 令牌推导 | 第 333–340 行。label 本身 `font-weight: 700`、`font-size: var(--spd-font-size-sm)` |
| M2-08 | 字段标签「关联库房 *」 | 同上 | `--spd-font-size-sm` | 13px | 15px | 17px | 700 | — | 实测 + 令牌推导 | 同上 |
| M2-09 | 字段标签「目录状态」 | 同上 | `--spd-font-size-sm` | 13px | 15px | 17px | 700 | — | 实测 + 令牌推导 | 同上 |
| M2-10 | 「科室」搜索输入框文字 | `.batch-dept-box input` | 继承（`font: inherit` → label 的 `--spd-font-size-sm`） | 13px | 15px | 17px | 700 | — | 实测 + 令牌推导 | 第 455–465 行。**注意：本项字号为 13px（`sm`），与 M-2 的输入框（14px）不一致，见第 4.9 节差异登记** |
| M2-11 | 「关联库房」下拉文字 | `.batch-catalog-fields select` | 继承（`font: inherit`） | 13px | 15px | 17px | 700 | — | 实测 + 令牌推导 | 第 342–349 行。控件高 40px。**同 M2-10 的 13px 差异** |
| M2-12 | 「目录状态」下拉文字 | `.batch-catalog-fields select` | 继承 | 13px | 15px | 17px | 700 | — | 实测 + 令牌推导 | 同上 |
| M2-13 | 搜索放大镜按钮 | `.batch-dept-box .search-trigger` | 图标 16px（固定） | 16px | 16px | 16px | — | — | 实测 | 36×40px，主色实底。**无文字** |
| M2-14 | 商品选择区标题 | `.catalog-filter-title` | 继承（`font-weight: 700`，无 font-size） | 14px | 16px | 18px | 700 | 继承 | 实测 + 令牌推导 | 第 366–374 行。继承 body 的 `--spd-font-size-base` |
| M2-15 | 商品搜索输入框文字 | `.product-search input` | 继承（`font: inherit` → label 字号） | 13px | 15px | 17px | 400 | 继承 | 实测 + 令牌推导 | 第 395–404 行。控件高 38px |
| M2-16 | 商品搜索放大镜按钮 | `.product-search .search-trigger` | 图标 16px（固定） | 16px | 16px | 16px | — | — | 实测 | 第 410–421 行。**无文字** |
| M2-17 | 「全选/取消当前结果」按钮文字 | `.product-picker-toolbar .btn.btn-primary` | `--spd-font-size-base` | 14px | 16px | 18px | 700 | 继承 | 实测 + 令牌推导 | `department-warehouse-catalog.css` 第 586–599 行 |
| M2-18 | 「全选/取消当前结果」按钮图标 | `.btn-primary svg` | 图标 16px（固定） | 16px | 16px | 16px | — | — | 实测 | Lucide `Layers3 :size="16"` |
| M2-19 | 已选计数 | `.product-picker-count` | 继承（`font-weight: 700`，无 font-size） | 14px | 16px | 18px | 700 | 继承 | 实测 + 令牌推导 | `department-warehouse-catalog.css` 第 620–629 行。胶囊形（`border-radius: 999px`） |
| M2-20 | 商品编码 | `.product-code` | 继承 | 14px | 16px | 18px | 700 | 继承 | 实测 + 令牌推导 | `DepartmentWarehouseCatalogBatchDialog.vue` 第 564–567 行。**未使用等宽字体**，与表格编码列 `.ulc-mono` 的处理不一致，见第 4.9 节 |
| M2-21 | 商品名称 | `.product-name` | 继承 | 14px | 16px | 18px | 700 | 继承 | 实测 + 令牌推导 | 第 569–572 行 |
| M2-22 | 商品状态标记「未绑定库房」/「已维护」 | `.product-flag` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | — | 实测 + 令牌推导 | 第 583–591 行。胶囊形（`border-radius: 999px`），`font-style: normal` |
| M2-23 | 商品规格 | `.product-spec` | 继承 | 14px | 16px | 18px | 400 | 继承 | 实测 + 令牌推导 | 第 602–604 行 |
| M2-24 | 商品列表空态文案 | `.product-empty` | 继承 | 14px | 16px | 18px | 400 | 继承 | 实测 + 令牌推导 | 第 606–614 行。`padding: 36px 12px` |
| M2-25 | 底部操作提示 | `.product-hint` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | 继承 | 实测 + 令牌推导 | 第 616–621 行。文案「已存在目录会更新状态，已删除目录会恢复；单次最多维护 500 项。」 |
| M2-26 | 商品复选框 | `.product-option input` | 无文字 | — | — | — | — | — | 实测 | 16×16px 纯图形 |
| M2-27 | 「取消」按钮文字 | `.batch-catalog-actions .btn.btn-secondary` | `--spd-font-size-base` | 14px | 16px | 18px | 700 | 继承 | 实测 + 令牌推导 | `department-warehouse-catalog.css` 第 547–568 行。控件高 36px，圆角 6px |
| M2-28 | 「保存 N 项目录」按钮文字 | `.batch-catalog-actions .btn.btn-primary` | `--spd-font-size-base` | 14px | 16px | 18px | 700 | 继承 | 实测 + 令牌推导 | 同文件第 570–580 行 |
| M2-29 | 「保存 N 项目录」按钮图标 | `.btn-primary svg` | 图标 16px（固定） | 16px | 16px | 16px | — | — | 实测 | Lucide `Save :size="16"` |
| M2-30 | 保存中按钮文字「正在保存...」 | `.batch-catalog-actions .btn.btn-primary` | `--spd-font-size-base` | 14px | 16px | 18px | 700 | 继承 | 实测 + 令牌推导 | `saving` 为真时文案替换，字号不变 |

---

### 4.3 M-3 · 列设置浮层

**触发**：工具栏「列设置」按钮。浮层绝对定位于按钮下方右对齐，`width: 268px`，`max-height: min(60vh, 420px)`，`z-index: 60`。

**DOM 结构**（**实测**，`MasterDataListView.vue` 第 2592–2613 行）：

```vue
<div class="ulc-col-settings-pop">
  <header>
    <strong>列展示设置</strong>
    <button type="button" class="ulc-link" @click="catalogCols.resetColumns()">恢复默认</button>
  </header>
  <div class="ulc-col-settings-list">
    <label v-for="col in catalogCols.columns.value" class="ulc-col-settings-item">
      <input type="checkbox" :checked="col.visible" ... />
      <span>{{ col.label }}</span>
      <small>{{ col.width }}px</small>
    </label>
  </div>
  <footer>
    <button type="button" class="ulc-action" @click="catalogCols.settingsOpen.value = false">确定</button>
  </footer>
</div>
```

#### 元素清单表

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M3-01 | 浮层标题「列展示设置」 | `.ulc-col-settings-pop header strong` | `--spd-font-size-base` | 14px | 16px | 18px | 700 | `--spd-lh-base` | 实测 + 令牌推导 | `unified-list.css` 第 585–592 行 |
| M3-02 | 「恢复默认」链接 | `.ulc-col-settings-pop .ulc-link` | `--spd-font-size-sm` | 13px | 15px | 17px | 700 | — | 实测 + 令牌推导 | 同文件第 595–601 行。主色文字，无边框透明底 |
| M3-03 | 列项名称（8 个业务列 + 操作列） | `.ulc-col-settings-item span` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | 同文件第 609–620 行。单行省略号截断 |
| M3-04 | 列宽标注「141px」 | `.ulc-col-settings-item small` | `--spd-font-size-xs` | 11px | 13px | 15px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | 同文件第 634–641 行。`font-variant-numeric: tabular-nums`。**本页唯一使用 `-xs` 的位置**，属 G-10 白名单（纯辅助标注） |
| M3-05 | 列项复选框 | `.ulc-col-settings-item input[type=checkbox]` | 无文字 | — | — | — | — | — | 实测 | 纯图形控件 |
| M3-06 | 底部「确定」按钮文字 | `.ulc-col-settings-pop footer .ulc-action` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 实测 + 令牌推导 | 同文件第 357–374 行 + 第 651–653 行。控件高 30px，`padding: 0 16px` |
| M3-07 | 浮层容器 | `.ulc-col-settings-pop` | 继承 | — | — | — | — | — | 实测 | 同文件第 534–549 行。`border-radius: 8px`，白底 |
| M3-08 | 列项列表滚动区 | `.ulc-col-settings-list` | 继承 | — | — | — | — | — | 实测 | 同文件第 551–559 行。独立纵向滚动，滚动条 6px |

---

### 4.4 M-4 / M-5 · 科室搜索下拉 / 商品搜索下拉

**触发**：M-1 的「科室」「商品」输入框聚焦 / 回车 / 点击放大镜；M-2 的「科室」输入框同理。

**DOM 结构**（**实测**，`DepartmentWarehouseCatalogDialog.vue` 第 128–133 行 / 第 175–182 行；`DepartmentWarehouseCatalogBatchDialog.vue` 第 115–123 行）：

```vue
<ul v-if="deptDropdownOpen" class="catalog-search-results" role="listbox">
  <li v-for="dept in filteredDepartments" :key="dept.deptCode">
    <button type="button" role="option" @click="selectDept(dept.deptName)">
      {{ dept.deptName }}（{{ dept.deptCode }}）
    </button>
  </li>
  <li v-if="!filteredDepartments.length" class="catalog-search-empty">未找到匹配科室</li>
</ul>
```

#### 元素清单表

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M4-01 | 科室候选项文字 | `.catalog-search-results li button` | 继承（`font: inherit` → 弹窗表单 label 字号 `--spd-font-size-sm`） | 13px | 15px | 17px | 700 | 继承 | 实测 + 令牌推导 | `DepartmentWarehouseCatalogDialog.vue` 第 259–270 行。`font: inherit` 继承自 `.catalog-dialog-form label` 的 `--spd-font-size-sm` + 500 |
| M4-02 | 科室候选项编码 | 同上（同一文本节点内） | 同上 | 13px | 15px | 17px | 700 | 继承 | 实测 | 文案「科室名（科室编码）」，编码**未使用等宽字体**，见第 4.9 节 |
| M4-03 | 科室下拉空态「未找到匹配科室」 | `.catalog-search-empty` | 继承 | 13px | 15px | 17px | 400 | 继承 | 实测 | 第 276–279 行。`padding: 8px 10px` |
| M4-04 | 批量弹窗科室候选项 | `.batch-dept-results li button` | 继承 | 13px | 15px | 17px | 700 | 继承 | 实测 + 令牌推导 | `DepartmentWarehouseCatalogBatchDialog.vue` 第 504–512 行 |
| M4-05 | 批量弹窗下拉空态 | `.batch-dept-empty` | 继承 | 13px | 15px | 17px | 400 | 继承 | 实测 | 同文件第 523–526 行 |
| M5-01 | 商品候选项文字 | `.catalog-search-results li button` | 继承 | 13px | 15px | 17px | 700 | 继承 | 实测 + 令牌推导 | `DepartmentWarehouseCatalogDialog.vue` 第 176–181 行。文案「编码 - 名称（规格）」 |
| M5-02 | 商品候选项尾注「· 未绑定库房或已维护」 | 同上（同一文本节点内） | 同上 | 13px | 15px | 17px | 700 | 继承 | 实测 | 不可选商品附加说明 |
| M5-03 | 商品下拉空态「没有匹配的可用商品」 | `.catalog-search-empty` | 继承 | 13px | 15px | 17px | 400 | 继承 | 实测 | `DepartmentWarehouseCatalogDialog.vue` 第 182 行 |
| M5-04 | 下拉浮层容器 | `.catalog-search-results` | 继承 | — | — | — | — | — | 实测 | 同文件第 242–257 行。`max-height: 220px`，可滚动，`z-index: 30` |
| M5-05 | 批量弹窗下拉浮层容器 | `.batch-dept-results` | 继承 | — | — | — | — | — | 实测 | `DepartmentWarehouseCatalogBatchDialog.vue` 第 487–502 行 |

---

### 4.5 M-6 · 商品选择区（批量弹窗内）

**DOM 结构**：见第 4.2 节 M-2 结构（`.product-picker` → `.product-options` → `.product-option`）。

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M6-01 | 商品行容器 | `.product-option` | 继承 | 14px | 16px | 18px | 400 | 继承 | 实测 + 令牌推导 | `DepartmentWarehouseCatalogBatchDialog.vue` 第 543–556 行。`min-height: 42px`，4 列栅格 |
| M6-02 | 商品编码 | `.product-code` | 继承 | 14px | 16px | 18px | 700 | 继承 | 实测 | 见 M2-20 |
| M6-03 | 商品名称 | `.product-name` | 继承 | 14px | 16px | 18px | 700 | 继承 | 实测 | 见 M2-21 |
| M6-04 | 商品规格 | `.product-spec` | 继承 | 14px | 16px | 18px | 400 | 继承 | 实测 | 见 M2-23 |
| M6-05 | 状态标记 | `.product-flag` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | — | 实测 | 见 M2-22 |
| M6-06 | 不可选商品整行 | `.product-option.is-disabled` | 继承 | — | — | — | — | — | 实测 | `opacity: 0.55`，字号不变 |
| M6-07 | 已勾选行高亮 | `.product-option:has(input:checked)` | 继承 | — | — | — | — | — | 实测 | 背景色变化，字号不变 |

---

### 4.6 M-7 · 确认框

**触发**：删除目录、批量停用等危险操作前（经 `useSpdConfirm` 的 `ask()`）。

**DOM 结构**（**实测**，`components/common/ConfirmDialog.vue` 第 122–199 行）：

```vue
<section class="spd-confirm-panel" :class="`is-${tone}`">
  <header class="spd-confirm-head">
    <span class="spd-confirm-icon"><component :is="toneIcon" :size="22" /></span>
    <h3 class="spd-confirm-title">{{ title }}</h3>
    <button class="spd-confirm-close" aria-label="关闭"><X :size="18" /></button>
  </header>
  <div class="spd-confirm-body">
    <p v-for="line in descriptionLines" class="spd-confirm-desc">{{ line }}</p>
    <ul v-if="detailLines.length" class="spd-confirm-details"><li v-for="line in detailLines">{{ line }}</li></ul>
  </div>
  <footer class="spd-confirm-foot">
    <button class="spd-confirm-btn spd-confirm-btn-cancel">{{ cancelText }}</button>
    <button class="spd-confirm-btn" :class="[`spd-confirm-btn-${tone}`]">{{ confirmText }}</button>
  </footer>
</section>
```

**宽度**：默认 `460px`（`width` prop 可覆盖）。

#### 元素清单表

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M7-01 | 确认框标题 | `.spd-confirm-title` | `--spd-font-size-lg` | 16px | 18px | 20px | 700 | `--spd-lh-tight` | 实测 + 令牌推导 | `ConfirmDialog.vue` 第 245–252 行 |
| M7-02 | 确认框正文段落 | `.spd-confirm-desc` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | `--spd-lh-loose` | 实测 + 令牌推导 | 第 307–312 行 |
| M7-03 | 确认框明细条目 | `.spd-confirm-details li` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | `1.5` | 实测 + 令牌推导 | 第 324–330 行。带 `•` 前缀 |
| M7-04 | 「取消」按钮文字 | `.spd-confirm-btn-cancel` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 实测 + 令牌推导 | 第 441–450 行。控件高 38px，圆角 10px |
| M7-05 | 「确认」按钮文字 | `.spd-confirm-btn`（`.spd-confirm-btn-danger` / `-warning` / `-primary` 等） | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 实测 + 令牌推导 | 第 372–390 行。语义色变体 |
| M7-06 | 确认按钮加载态文字「处理中...」 | `.spd-confirm-btn.is-loading` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 实测 | 字号不变，仅插入 spinner |
| M7-07 | 关闭按钮 | `.spd-confirm-close` | 图标 18px（固定） | 18px | 18px | 18px | — | — | 实测 | 30×30px，**无文字** |
| M7-08 | 语义图标 | `.spd-confirm-icon` | 图标 22px（固定） | 22px | 22px | 22px | — | — | 实测 | 36×36px 圆角容器，**无文字** |
| M7-09 | 弹窗主体 | `.spd-confirm-panel` | `--spd-font-size-base` | 14px | 16px | 18px | 400 | — | 实测 + 令牌推导 | 第 218–234 行。`border-radius: 16px`，白底 |
| M7-10 | 遮罩 | `.spd-confirm-mask` | 无文字 | — | — | — | — | — | 实测 | `rgba(6,21,43,0.45)` + 模糊 |

> **说明**：本页确认框走的是自绘 `ConfirmDialog.vue`，**不是** Element Plus 的 `ElMessageBox`。因此标准文档第 4.9 节的 O-05 / O-06 条目（`.el-message-box__title` / `.el-message-box__message`）在本页**不生效**。若后续改用 `ElMessageBox`，则标题为 `--spd-font-size-2xl`（20/22/24px）、正文为 `--spd-font-size-base` + `--spd-lh-loose`。

---

### 4.7 M-8 · 顶部消息提示

**DOM 结构**：由 `useNotify()`（`composables/useNotify.ts`）调用 Element Plus `ElMessage`，`customClass: 'spd-top-notify'`，`appendTo: 'body'`，`zIndex: 999999`。

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M8-01 | 成功消息文字 | `.spd-top-notify.el-message` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | `spd-override.css` 第 1166–1182 行的 Element Plus 通用规则 + 标准文档 O-13 |
| M8-02 | 错误消息文字 | 同上 | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | 同上 |
| M8-03 | 警告消息文字 | 同上 | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | 同上 |
| M8-04 | 信息消息文字 | 同上 | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | 同上 |

---

### 4.8 M-9 · Tooltip（悬浮提示）

| # | 元素名 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 来源 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M9-01 | Tooltip 文字 | `.el-tooltip__popper` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | `--spd-lh-base` | 实测 + 令牌推导 | `spd-override.css` 第 1172–1182 行 |
| M9-02 | Popper 内容文字 | `.el-tooltip__popper .el-popper__content` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | — | 实测 + 令牌推导 | 同上 |
| M9-03 | 下拉浮层文字 | `.el-popper .el-popper__content` | `--spd-font-size-sm` | 13px | 15px | 17px | 400 | — | 实测 + 令牌推导 | 同上 |

---

### 4.9 弹窗区已登记的字号差异（现状登记）

以下差异是**源码实测的现状**，登记在此供后续整改与评审使用，**不属于标准值**。

| # | 位置 | 现状字号 | 现状令牌 | 与标准的差距 | 差距来源 |
| --- | --- | --- | --- | --- | --- |
| Δ-01 | M-2 批量弹窗「科室」输入框 `.batch-dept-box input` | 13px | 继承 label 的 `--spd-font-size-sm` | 输入控件文字应为 `--spd-font-size-base`（14px） | 组件 scoped 样式使用 `font: inherit`，继承自 label |
| Δ-02 | M-2 批量弹窗「关联库房」下拉 `.batch-catalog-fields select` | 13px | 继承 label | 同 Δ-01 | 同上 |
| Δ-03 | M-2 批量弹窗「目录状态」下拉 | 13px | 继承 label | 同 Δ-01 | 同上 |
| Δ-04 | M-2 商品搜索输入框 `.product-search input` | 13px | `font: inherit` | 同 Δ-01 | 同上 |
| Δ-05 | M-1 / M-2 科室候选项中的科室编码 | 无等宽字体 | 继承 | 编码类内容应使用 `--spd-font-family-mono` + `tabular-nums` | 未使用 `.ulc-mono` 或等宽字体族 |
| Δ-06 | M-2 商品编码 `.product-code` | 无等宽字体 | 继承 | 同 Δ-05 | 同上 |
| Δ-07 | M-2 商品列表行内规格 `.product-spec` | 14px（继承 body） | `--spd-font-size-base` | 属次级说明，参照表格「规格型号」列应为 `--spd-font-size-sm` | 未显式设定，继承 body |
| Δ-08 | M-1 弹窗章节标题 / 章节说明同为 13px | 13px | `--spd-font-size-sm` | 章节标题与说明**同档**，靠字重（700 vs 400）区分 | 两者均显式使用 `--spd-font-size-sm` |

**规定**：

1. 上表 Δ-01 ~ Δ-04 属于**输入控件文字小于标准**的类别，与标准文档 6.6 节「禁止使用 12px（或更小）的位置」中「输入框内文字 必须 14px」同类，虽然本项为 13px 未破 12px 下限，但仍低于标准值。
2. Δ-05 / Δ-06 属于标准文档第 2.2 节「等宽字体只用于编码、单号、UID/唯一码、金额、时间戳」的适用范围问题。
3. 上述差异**记录在案**，本页面的字体规格基线以本文档第 3、4 章表格为准；差异项在整改时向标准值收敛。

---

## 5. 字体与字重基础规范

### 5.1 字体族

**全站只允许 2 套字体栈**（标准文档第 2.1 节）。本页属于**业务系统**体系。

| 套 | 用途 | 字体栈 | 定义位置 |
| --- | --- | --- | --- |
| **业务系统** | 除登录页外的全部界面（**含本页**） | `-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Helvetica Neue", Helvetica, Arial, sans-serif` | `styles/spd-theme.css` 第 13 行 `--spd-font-family` |
| **登录页** | 登录页独立体系，不参与档位 | `"PingFang SC", "Microsoft YaHei", system-ui, sans-serif` | `styles/views/login.css` 的 `.standalone-login` |

**本页实际使用**：

| 用途 | 类名 / 令牌 | 字体栈 |
| --- | --- | --- |
| 全站正文 | `body` | `var(--spd-font-family)` |
| 页面主标题 | `.master-page-title h2` | `var(--spd-font-family-display)` —— `"HarmonyOS Sans SC", "Alibaba PuHuiTi", "Source Han Sans SC", "Noto Sans SC", -apple-system, …`（`spd-theme.css` 第 14 行） |
| 商品编码列 | `.ulc-mono` | `"Cascadia Mono", "SFMono-Regular", Consolas, monospace` |
| 分页页码 | `.hospital-page-indicator` / `.md-page-indicator` | `var(--md-font-mono)` |

> **规定**：等宽字体**只用于**编码、单号、UID/唯一码、金额、时间戳。**禁止**用于正文段落、商品名称、按钮文字。

### 5.2 字重梯度

**全站只允许 5 档字重**：`400 / 500 / 600 / 700 / 800`。

| 字重 | 语义 | 本页典型元素 |
| --- | --- | --- |
| **400** | 正文、表格数据、说明文字、弹窗正文 | 表格数据单元格、筛选输入框文字、`.ulc-time`、弹窗字段说明、`.spd-confirm-desc` |
| **500** | 次级强调、辅助标签 | `.ulc-sub`（表格次级说明）、筛选标签部分场景、弹窗字段标签、`.catalog-dialog-actions .btn`（取消） |
| **600** | 强调、字段标签、状态条、次级标题 | `.ulc-chip`（状态条）、`.ulc-action`（行内按钮）、工具栏按钮、筛选标签、`.ulc-col-settings-pop header strong` |
| **700** | 区块标题、表格表头、主按钮、表格主字段 | `.ulc-table th .cell`、`.ulc-strong`、`.md-btn`、章节标题、确认框标题 |
| **800** | 展示型数值、金额、品牌标识 | `.catalog-dialog-kicker`（弹窗眉标） |

**规定**（本页适用）：

1. `font-weight: 650` / `750` / `850` **一律非法**。650 → 600，750 → 700，850 → 800。
2. 字重 **900 及以上**禁止使用；**300 / 200 / 100** 禁止使用。
3. **禁止**用字重单独制造层级：同字号下字重跨度不超过 2 档。
4. 当两个相邻层级字号差值为 0 或 1px 时，**必须**用字重（≥ 100 差）或颜色（≥ 一个色阶的明度差）提供额外区分。

### 5.3 数字等宽（tabular-nums）

**必须**加 `font-variant-numeric: tabular-nums` 的位置（**实测**，本页范围）：

| 位置 | 类名 | 参考元素 |
| --- | --- | --- |
| 表格编码列 | `.ulc-mono` | 「商品编码」列的 `SP0000123` |
| 表格时间列 | `.ulc-time` | 「更新时间」列的 `2026-11-23 09:41` |
| 列设置宽度标注 | `.ulc-col-settings-item small` | 「141px」 |
| 分页页码 / 输入框 | `.hospital-pagination input`、`.md-page-indicator` | 「每页 [20] 条」 |

**判定规则**：任何需要跨行对齐或跨列比较的数字，一律等宽。金额、数量、日期、编码、页码无一例外。

### 5.4 行高三档

| 令牌 | 值 | 本页用途 |
| --- | --- | --- |
| `--spd-lh-tight` | **1.35** | 表格单元格、状态条、徽标、工具栏按钮、行内操作按钮、筛选按钮、弹窗主标题 |
| `--spd-lh-base` | **1.5** | 正文默认、输入控件、筛选标签、空态、分页、列设置浮层、确认框明细 |
| `--spd-lh-loose` | **1.65** | 确认框正文段落 |

---

## 6. 状态条与配色

### 6.1 状态条规格

**状态条 = `.ulc-chip`**（带圆点的语义状态标签），本页出现在「维护来源」与「状态」两列。

**通用规格**（**实测**，`unified-list.css` 第 255–278 行）：

| 属性 | 值 | 来源 |
| --- | --- | --- |
| 字号 | `--spd-font-size-base` | 实测 + 令牌推导 |
| 字重 | `var(--spd-c-chip-font-weight, 600)` | `tokens/colors.css` 第 74 行 |
| 行高 | `--spd-lh-tight`（1.35） | 实测 |
| 圆角 | `var(--spd-c-chip-radius, 4px)` | `tokens/colors.css` 第 71 行 |
| 边框 | `var(--spd-c-chip-border-width, 1px) solid <语义色>` | `tokens/colors.css` 第 72 行 |
| 背景 | `color-mix(in srgb, <语义色> 40%, #ffffff)` | `tokens/colors.css` 第 92–98 行 |
| 文字色 | 语义深色（`--spd-c-success` / `--spd-c-danger` / `--spd-c-info` / `--spd-c-warning` / `--spd-c-disabled` / `--spd-c-primary-strong`） | 实测 |
| 内边距 | `0 11px` | 实测 |
| 最小高度 | `28px` | 实测 |
| 圆点 | `::before` 7×7px，`border-radius: 50%`，`background: currentColor` | 实测，第 272–278 行。**固定尺寸，不随档位缩放** |
| 文字不换行 | `white-space: nowrap` | 实测 |

### 6.2 本页状态条取值映射

**状态列**（**实测**，`MasterDataListView.vue` 第 2728–2731 行）：

| 状态值 | 类名 | 背景 | 边框 | 文字色 |
| --- | --- | --- | --- | --- |
| 启用 | `.ulc-chip.is-success` | `--spd-c-chip-bg-success`（success 40% + 白） | `--spd-c-success-border` | `--spd-c-success` |
| 停用 | `.ulc-chip.is-danger` | `--spd-c-chip-bg-danger`（danger 40% + 白） | `--spd-c-danger-border` | `--spd-c-danger` |

**维护来源列**（**实测**，`MasterDataListView.vue` 第 190–200 行的 `CATALOG_SOURCE_CHIP_CLASS`）：

| 来源值 | 类名 | 背景 | 边框 | 文字色 |
| --- | --- | --- | --- | --- |
| 手工维护 / manual | `.ulc-chip.is-primary` | `--spd-c-chip-bg-primary`（primary 40% + 白） | `--spd-c-primary-strong` | `--spd-c-primary-strong` |
| 系统生成 / system | `.ulc-chip.is-info` | `--spd-c-chip-bg-info`（info 40% + 白） | `--spd-c-info-border` | `--spd-c-info` |
| 库房绑定 / warehouse_binding | `.ulc-chip.is-warning` | `--spd-c-chip-bg-warning`（warning 40% + 白） | `--spd-c-warning-border` | `--spd-c-warning` |
| batch（批量生成） | `.ulc-chip.is-neutral` | `--spd-c-chip-bg-neutral`（muted 40% + 白） | `--spd-c-disabled-border` | `--spd-c-disabled` |

> **对比**：本页表格内使用的是 **40% 透明度底**（`--spd-c-chip-bg-*`）。静墨宣纸变体页（`/features/department-warehouse-catalog-calm`）把状态条底色透明度**降到 0.14**，以降低满屏色块感。两者**字号、字重、行高、圆角、边框宽度完全一致**，只有背景透明度不同（见 `styles/theme/calm-paper.css`）。

### 6.3 配色取用纪律

**规定**：

1. 本页所有颜色**必须**取自 `frontend/src/styles/tokens/colors.css` 的 `--spd-c-*` 令牌，或由 `unified-list.css` 中定义的 `--ulc-*` 局部变量间接引用。
2. **禁止**在 `department-warehouse-catalog.css`、页面 Vue 文件的 `<style>` 块中写死新的 hex 值作为文字色 / 背景色 / 边框色。
3. 状态条、按钮的**语义色**（success / danger / info / warning / primary / neutral）由 `--spd-c-*` 令牌定义，**不随页面变化**。
4. 字号与颜色的搭配（标准文档第 6.5 节）在本页的落地：

| 场景 | 字号 | 字重 | 文字色 | 底色 |
| --- | --- | --- | --- | --- |
| 表格表头 | 14px | 700 | `--spd-c-text-on-primary`（白） | `--ulc-primary` 主色实底 |
| 表格数据 | 14px | 400 | `--ulc-text` | 无底色 / 斑马纹 `--ulc-row-stripe` |
| 表格主字段 | 14px | 700 | `--ulc-text` | 同数据行 |
| 表格次级说明 | 13px | 500 | `--ulc-muted` | 同数据行 |
| 状态条 | 14px | 600 | 语义深色 | 语义色 40% 透明度底 + 1px 边框 |
| 筛选标签 | 13px | 600 | `#405564` | 无 |
| 输入控件文字 | 14px | 400 | `--ulc-text` | 控件底（白） |
| 辅助角标 | 11px | 400 | `--ulc-muted` | 无 |

5. **禁止**出现「小字号 + 低对比度 + 细字重」的组合。任何 11px 文字必须至少满足 400 字重与正文色系可达对比度。

---

## 7. 按钮规范

### 7.1 按钮族总表

| 按钮族 | 本页实例 | CSS 类名 | 字号令牌 | 标准档 | 大档 | 特大档 | 字重 | 行高 | 圆角 | 边框 | 控件高 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **主按钮** | 工具栏「新增目录」「批量维护」「编辑」「启用」「列设置」 | `.md-table-toolbar .md-btn` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 4px | 1px | 34px |
| **次按钮（语义变体）** | 工具栏「停用」 | `.md-table-toolbar .md-btn.md-btn-warning` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 4px | 1px | 34px |
| **危险按钮** | 工具栏「删除」 | `.md-table-toolbar .md-btn.md-btn-danger` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 4px | 1px | 34px |
| **筛选按钮** | 「重置」 | `.ulc-filter-actions .md-btn` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 4px | — | 38px |
| **主筛选按钮** | 「查询」 | `.ulc-filter-actions .md-btn.md-btn-primary` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 4px | — | 38px |
| **行内操作按钮** | 「编辑」「启用/停用」「删除」 | `.ulc-actions .ulc-action` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 4px | 1px | 30px |
| **浮层底部按钮** | 列设置「确定」 | `.ulc-col-settings-pop footer .ulc-action` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 4px | 1px | 30px |
| **浮层文字链接** | 列设置「恢复默认」 | `.ulc-col-settings-pop .ulc-link` | `--spd-font-size-sm` | 13px | 15px | 17px | 700 | — | 无 | 无 | — |
| **弹窗次按钮** | 新增/编辑弹窗「取消」 | `.catalog-dialog-actions .btn` | `--spd-font-size-base` | 14px | 16px | 18px | 500 | 继承 | 6px | 1px | 32px |
| **弹窗主按钮** | 「创建目录」「保存修改」 | `.catalog-dialog-actions .btn.btn-primary` | `--spd-font-size-base` | 14px | 16px | 18px | 700 | 继承 | 6px | 1px | 32px |
| **批量弹窗按钮** | 「取消」「保存 N 项目录」「全选/取消当前结果」 | `.batch-catalog-actions .btn` | `--spd-font-size-base` | 14px | 16px | 18px | 700 | 继承 | 6px | 1px | 36px |
| **确认框按钮** | 「取消」「确认」 | `.spd-confirm-btn` | `--spd-font-size-base` | 14px | 16px | 18px | 600 | `1` | 10px | 1px | 38px |
| **图标按钮** | 弹窗关闭 `✕`、搜索放大镜 | `.btn-icon` | 图标 16–18px（固定） | 固定 | 固定 | 固定 | — | — | 6–8px | 1px | 30–32px |
| **分页按钮** | 「上一页」「下一页」、页码 | `.el-pagination .btn-prev` / `.btn-next` / `.el-pager li` | `--spd-font-size-sm` | 13px | 15px | 17px | 500 | `--spd-lh-tight` | — | — | — |

### 7.2 按钮通用规格

**规定**（本页全部按钮适用）：

1. **圆角 4px**（`.md-btn` / `.ulc-action` / `.ulc-filter-actions .md-btn`）为全站基准。弹窗内按钮因视觉层级需要可放宽到 6px，确认框可到 10px，但**必须**在样式表中显式声明，不依赖继承。
2. **边框 1px**（`--spd-c-chip-border-width`）。图标按钮同理。
3. **字重 600** 是按钮的默认字重（`.ulc-action`、`.md-btn`、`.spd-confirm-btn`）。弹窗「取消」类次按钮可用 500。
4. **行高 1** —— 单行控件统一 `line-height: 1`，由控件高度撑开垂直空间。
5. **按钮字号只有两档**：
   - 主按钮族 **14px**（`--spd-font-size-base`）
   - 小号按钮族 **13px**（`--spd-font-size-sm`）—— 本页仅用于分页按钮与列设置「恢复默认」链接
   - **12px 从按钮体系中彻底移除**
6. **禁用态**：`opacity: 0.5`（工具栏按钮、`.ulc-action`）或 `opacity: 0.55`（批量弹窗按钮），字号**不变**。
7. **图标尺寸固定**：工具栏与筛选按钮图标统一 16px，弹窗关闭 18px，搜索放大镜 16px，列设置箭头 14px。图标尺寸**不随字号档位缩放**（属标准文档第 3.6 节图标白名单）。
8. **图标与文字间距**：`.md-btn` gap 6px，`.ulc-action` gap 4px。

---

## 8. 响应式表现

### 8.1 断点与布局变化

**来源**：**实测** —— `unified-list.css` 第 793–814 行、`department-warehouse-catalog.css` 第 641–684 行、`DepartmentWarehouseCatalogBatchDialog.vue` 第 635–654 行。

| 断点 | 影响的元素 | 变化 | 字号是否变化 |
| --- | --- | --- | --- |
| **> 1400px** | `.ulc-filter-fields` | `grid-template-columns: repeat(auto-fit, minmax(150px, 1fr))`，4 个筛选项排一行 | ❌ 不变 |
| **≤ 1400px** | `.ulc-filter-fields` | 同上（保持 auto-fit 自适应） | ❌ 不变 |
| **≤ 1180px** | `.department-warehouse-catalog-query-grid` | 降为 2 列 | ❌ 不变 |
| **≤ 1100px** | `.ulc-filter-fields` | 同上（保持 auto-fit 自适应） | ❌ 不变 |
| **≤ 760px** | `.ulc-filter-fields` | 降为 **1 列**（`minmax(0, 1fr)`） | ❌ 不变 |
| **≤ 760px** | `.ulc-filter-actions` | `width: 100%`，按钮区独占一行右对齐 | ❌ 不变 |
| **≤ 760px** | `.catalog-dialog-section` | 降为 1 列 | ❌ 不变 |
| **≤ 760px** | `.catalog-dialog` | `width: min(100vw - 16px, 1040px)`，`max-height: calc(100dvh - 16px)` | ❌ 不变 |
| **≤ 760px** | `.batch-catalog-fields` | 降为 1 列 | ❌ 不变 |
| **≤ 760px** | `.product-picker-toolbar` | `flex-wrap: wrap`，搜索框 `flex-basis: 100%` 独占一行 | ❌ 不变 |
| **≤ 760px** | `.product-option` | 栅格降为 3 列（`24px 110px 1fr`），`.product-spec` **隐藏** | ❌ 不变 |

### 8.2 表格横向滚动

| 项 | 规格 | 来源 |
| --- | --- | --- |
| 表格容器 | `.ulc-table-wrap`，`overflow: hidden`，`border-radius: 12px` | 实测 |
| 列宽模式 | `useListColumns` 的 `fitMode: 'hybrid'`，`reservedWidth: 48`，弹性列 `idealWidth` / `minWidth` / `maxWidth` | 实测，`MasterDataListView.vue` 第 163–180 行 |
| 列宽拖拽 | 表头右侧 8px 透明热区，拖拽改变标准边框位置，`border-right-color` 提亮至 65% 白 | 实测，`unified-list.css` 第 105–125 行 |
| 操作列 | `fixed="right"`，默认宽 170px，`minWidth: 150px` / `maxWidth: 210px` | 实测 |
| 横向滚动 | 由 `responsive-medical.css` 注入垂直滚动约束；表格容器保留横向滚动条样式（6px） | 实测 |

**规定**：

1. **字号档位切换不得引起表格横向滚动**。`xlarge` 档（18px 基准）下，表格总宽应在 1920px 视口内完整显示；列宽不足时优先压缩弹性列的 `minWidth`，**不得**通过缩小字号来塞下内容。
2. 长文本列（商品名称、规格型号、生产厂家）**必须**换行完整显示，**禁止**截断关键信息。`.ulc-wrap-text` 使用 `white-space: normal` + `overflow-wrap: anywhere`。
3. 表格在 `xlarge` 档下不出现横向滚动（除非列数确实超宽）。

### 8.3 字号档位 × 视口的三档走查要求

按标准文档第 9.5 节，逐档走查必须确认：

| 检查项 | standard | large | xlarge |
| --- | --- | --- | --- |
| 筛选区 4 个筛选项排布 | 1 行 | 1 行 | 1 行（必要时 2 行） |
| 工具栏 7 个按钮不换行 | ✅ | ✅ | ✅（必要时折行，不截字） |
| 表格列宽不溢出 | ✅ | ✅ | ✅ |
| 弹窗字段标签不折行 | ✅ | ✅ | ✅ |
| 批量弹窗商品列表三列不重叠 | ✅ | ✅ | ✅ |
| 长商品名换行完整 | ✅ | ✅ | ✅ |
| 控件高度足够容纳当前字号 | ✅ | ✅ | ✅（输入框 38px、按钮 34px、行内按钮 30px 均满足） |

---

## 9. 新增同类页面的照抄清单

> 开发一个新的**同款列表页**（统一列表 + md-* 体系）时，逐项确认后再提交。任一项不满足即退回。

### 9.1 页面骨架

| # | 检查项 | 期望 |
| --- | --- | --- |
| 1 | 页面根容器 | `<section class="master-page master-page--aligned spd-unified-list">` |
| 2 | 标题区 | `<MdPageHeader title="…" :loading="loading" @refresh="loadPage" />` |
| 3 | 筛选区 | `<form class="md-hospital-filter ulc-filter spd-unified-list">` |
| 4 | 筛选字段容器 | `<div class="ulc-filter-fields">`，每个字段 `<label><span>标签</span><input class="md-input" /></label>` |
| 5 | 筛选按钮区 | `<div class="md-hospital-filter-actions ulc-filter-actions">`，按钮类 `md-btn` / `md-btn md-btn-primary` |
| 6 | 表格卡 | `<MdDataTableCard class="md-table-card-catalog" :loading="loading">` |
| 7 | 工具栏 slot | `#toolbar` 内放 `.md-btn` 按钮组 + `.ulc-col-settings` 列设置 |
| 8 | 表格 | `<div class="ulc-table-wrap">` → `<el-table class="ulc-table" border stripe>` |
| 9 | 表头 | 每列 `#header` slot 内包 `<span class="ulc-th-text">列名</span>` |
| 10 | 分页 | `#pagination` slot 内放 `<MdPagination :page :total-pages :total :size :loading />` |
| 11 | 弹窗挂载 | 弹窗组件置于 `.master-page` 内（便于 `.master-page .btn` 特异性规则生效） |
| 12 | 确认框 | `<SpdConfirmHost />` 置于页面末尾 |

### 9.2 字号与令牌

| # | 检查项 | 期望 |
| --- | --- | --- |
| 13 | 裸 `font-size: Npx` | **零**，全部 `var(--spd-font-size-*)` |
| 14 | 表格表头 | `--spd-font-size-table-head`（14/16/18px）+ 字重 700 |
| 15 | 表格数据单元格 | `--spd-font-size-base`（14/16/18px）+ 字重 400 |
| 16 | 表格主字段 | `.ulc-strong` → `--spd-font-size-base` + 字重 700（**不靠放大字号制造层级**） |
| 17 | 表格次级说明 | `.ulc-sub` → `--spd-font-size-sm`（13/15/17px）+ 字重 500 |
| 18 | 表格编码列 | `.ulc-mono` → 等宽字体 + `tabular-nums` |
| 19 | 表格时间列 | `.ulc-time` → `--spd-font-size-base` + `tabular-nums` |
| 20 | 筛选标签 | `.ulc-filter label > span` → `--spd-font-size-sm` + 字重 600 |
| 21 | 筛选输入框 / 下拉 | `--spd-font-size-base` + 字重 400 |
| 22 | 状态条 | `.ulc-chip` → `--spd-font-size-base` + 字重 600 + `--spd-lh-tight` |
| 23 | 工具栏按钮 | `.md-btn` → `--spd-font-size-base` + 字重 600 |
| 24 | 行内操作按钮 | `.ulc-action` → `--spd-font-size-base` + 字重 600 |
| 25 | 弹窗标题 | `--spd-font-size-xl`（自绘）或 `--spd-font-size-2xl`（Element Plus） |
| 26 | 弹窗字段标签 | `--spd-font-size-sm` + 字重 500/700 |
| 27 | 弹窗输入框文字 | `--spd-font-size-base`（**禁止** `font: inherit` 从 label 继承到 13px） |
| 28 | 确认框标题 | `--spd-font-size-lg`；确认框正文 `--spd-font-size-base` + `--spd-lh-loose` |
| 29 | 分页 | `--spd-font-size-sm`（页码、汇总文案、每页条数、跳页输入框） |
| 30 | 空态标题 / 说明 | `--spd-font-size-base` / `--spd-font-size-sm` |
| 31 | 角标 / 计数 | `--spd-font-size-xs`（仅限不需判断的辅助信息） |

### 9.3 字重与行高

| # | 检查项 | 期望 |
| --- | --- | --- |
| 32 | 字重取值 | 只用 400 / 500 / 600 / 700 / 800，**没有** 650 / 750 / 900 |
| 33 | 表格单元格 / 按钮 / 状态条行高 | `--spd-lh-tight` |
| 34 | 正文 / 输入控件 / 筛选标签行高 | `--spd-lh-base` |
| 35 | 长段落 / 确认框正文行高 | `--spd-lh-loose` |
| 36 | 散落行高 | **没有** 1.2 / 1.3 / 1.4 / 1.45 / 1.55 / 1.6 / 1.7 / 1.85 |

### 9.4 语义与可读性

| # | 检查项 | 期望 |
| --- | --- | --- |
| 37 | 承载业务判断的文字 | **没有任何**小于 12px 的 |
| 38 | 标签字号 | **不小于**其对应控件的字号 |
| 39 | 筛选标签 vs 输入控件 | 字号差在 −1px ~ +1px 之间（13px vs 14px） |
| 40 | 状态语义信息 | 14px（`.ulc-chip`） |
| 41 | 金额 / 数量 / 编码 / 日期 / 页码 | 加 `font-variant-numeric: tabular-nums` |
| 42 | 跨页面一致性 | 同一语义在其它页面也是同一字号（对照本文档第 3 章） |

### 9.5 弹窗专项

| # | 检查项 | 期望 |
| --- | --- | --- |
| 43 | 弹窗内嵌表格 | 与主列表一致（14px） |
| 44 | 弹窗输入控件字号 | 显式 `--spd-font-size-base`，**禁止** `font: inherit` 继承到 13px |
| 45 | 弹窗章节标题与说明 | 标题 `--spd-font-size-sm` + 700；说明 `--spd-font-size-sm` + 400（靠字重区分） |
| 46 | 弹窗底部按钮 | `--spd-font-size-base` + 字重 700（主）/ 500–700（次） |
| 47 | 弹窗编码类内容 | 使用等宽字体 + `tabular-nums` |
| 48 | 弹窗关闭 / 搜索图标 | 固定 16–18px，不随档位缩放 |
| 49 | 弹窗遮罩 | 无文字 |

### 9.6 提交前

| # | 检查项 | 期望 |
| --- | --- | --- |
| 50 | 构建 | `npm --prefix frontend run build` 通过 |
| 51 | 生成文件 | `git status` 中 `frontend/src` 下**没有**新增 `*.js` / `*.vue.js` |
| 52 | 三档走查 | standard / large / xlarge 下逐一走查，无文字溢出、无裁切、无重叠 |
| 53 | 截图留证 | 页面在标准档与特大档各截一张图，附在 PR 描述中 |

---

## 10. 附录

### 10.1 全量令牌表（三档 × 全部字号）

**来源**：**实测** —— `frontend/src/styles/tokens/typography.css` 第 19–134 行。三档值为 `calc()` 表达式的**令牌推导**结果。

| 令牌 | 表达式 | standard (14) | large (16) | xlarge (18) | 语义 |
| --- | --- | --- | --- | --- | --- |
| `--spd-fs-base` | 固定 / 覆盖 | **14px** | **16px** | **18px** | 基准 |
| `--spd-lh-tight` | 固定 | 1.35 | 1.35 | 1.35 | 紧凑行高 |
| `--spd-lh-base` | 固定 | 1.5 | 1.5 | 1.5 | 基准行高 |
| `--spd-lh-loose` | 固定 | 1.65 | 1.65 | 1.65 | 松散行高 |
| `--spd-table-cell-padding-y` | 固定 / 覆盖 | 10px | 12px | 13px | 表格单元格上下留白 |
| `--spd-font-size-xs` | `base − 3px` | 11px | 13px | 15px | 角标 / 计数 / 装饰标签 / 编码 chip |
| `--spd-font-size-sm` | `base − 1px` | 13px | 15px | 17px | 次级说明 / 筛选标签 / 表单标签 / 辅助文本 |
| `--spd-font-size-base` | `base` | 14px | 16px | 18px | 表格数据 / 正文基准 / 输入控件 / 按钮 |
| `--spd-font-size-md` | `base + 1px` | 15px | 17px | 19px | **正文强调文本**（本页不使用） |
| `--spd-font-size-lg` | `base + 2px` | 16px | 18px | 20px | 区块标题 / 卡片标题 / 确认框标题 |
| `--spd-font-size-xl` | `base + 4px` | 18px | 20px | 22px | 弹窗内区块标题 / 二级标题 |
| `--spd-font-size-2xl` | `base + 6px` | 20px | 22px | 24px | 弹窗标题 / 统计数值 |
| `--spd-font-size-3xl` | `base + 10px` | 24px | 26px | 28px | 页面主标题 |
| `--spd-font-size-4xl` | `base + 16px` | 30px | 32px | 34px | 展示型大数值（**本页不使用**） |
| `--spd-font-size-5xl` | `base + 24px` | 38px | 40px | 42px | 超大展示数值（**本页不使用**） |
| **`--spd-font-size-table-head`** | `= var(--spd-font-size-base)` | **14px** | **16px** | **18px** | **表格表头** |
| `--spd-chart-font-xs` | `base − 3px` | 11px | 13px | 15px | 图表微型角标（**本页无图表**） |
| `--spd-chart-font-sm` | `base − 2px` | 12px | 14px | 16px | 图表轴刻度（**本页无图表**） |
| `--spd-chart-font-base` | `base − 1px` | 13px | 15px | 17px | 图表图例（**本页无图表**） |
| `--spd-chart-font-lg` | `= var(--spd-font-size-base)` | 14px | 16px | 18px | 图表数据标签 / Tooltip 标题（**本页无图表**） |
| `--spd-chart-font-xl` | `base + 1px` | 15px | 17px | 19px | 图表 Tooltip 正文（**本页无图表**） |

> **本页无图表**。科室库房目录页不含任何 ECharts / SVG 图表组件，`--spd-chart-font-*` 在本页**不生效**，此处列出仅为完整性。

### 10.2 逐档速查

```
standard（14px 基准）
  xs 11px · sm 13px · base 14px · md 15px · lg 16px
  xl 18px · 2xl 20px · 3xl 24px · 4xl 30px · 5xl 38px
  table-head 14px
  lh: tight 1.35 · base 1.5 · loose 1.65

large（16px 基准）
  xs 13px · sm 15px · base 16px · md 17px · lg 18px
  xl 20px · 2xl 22px · 3xl 26px · 4xl 32px · 5xl 40px
  table-head 16px
  lh: tight 1.35 · base 1.5 · loose 1.65

xlarge（18px 基准）
  xs 15px · sm 17px · base 18px · md 19px · lg 20px
  xl 22px · 2xl 24px · 3xl 28px · 4xl 34px · 5xl 42px
  table-head 18px
  lh: tight 1.35 · base 1.5 · loose 1.65
```

### 10.3 本页字号分布统计（standard 档）

| 层级 | 字号 | 本页元素数（估） | 占比特征 |
| --- | --- | --- | --- |
| 展示层 | 24px | 1（页面主标题） | 极少量 |
| 标题层 | 20px / 18px / 16px | 3（批量弹窗标题、新增弹窗标题、确认框标题） | 弹窗内 |
| 操作层 | 14px | 表格数据、状态条、工具栏按钮、筛选按钮、行内按钮、输入控件、空态标题 | **绝对多数** |
| 数据层 | 13px | 筛选标签、表格次级说明、弹窗字段标签、章节标题、分页 | 次多数 |
| 辅助层 | 11px | 列设置宽度标注 | 极少量 |

> 符合标准文档第 6.1 节的层级模型：**操作层占绝对多数**，视觉重心唯一。

### 10.4 关键文件索引

| 用途 | 路径 |
| --- | --- |
| **字号令牌唯一来源** | `frontend/src/styles/tokens/typography.css` |
| **权威标准文档** | `docs/ui-review/spd-typography-spec-final.md` |
| 颜色令牌 | `frontend/src/styles/tokens/colors.css` |
| 统一列表体系（`.ulc-*`） | `frontend/src/styles/list/unified-list.css` |
| md-* 设计体系 | `frontend/src/styles/master-data.theme.css` |
| 本页专属样式 | `frontend/src/styles/master-data/department-warehouse-catalog.css` |
| md-* 列表页覆盖 | `frontend/src/styles/master-data/master-data-list.override.css` |
| Element Plus 强制覆盖 | `frontend/src/styles/spd-override.css` |
| 字体栈定义 | `frontend/src/styles/spd-theme.css` |
| 主题 / 字号档位状态 | `frontend/src/stores/theme.ts` |
| 顶栏字号切换器 | `frontend/src/components/common/ThemeSwitcher.vue` |
| **参考页视图** | `frontend/src/views/master-data/MasterDataListView.vue` |
| 页面标题组件 | `frontend/src/components/master-data/MdPageHeader.vue` |
| 表格卡组件 | `frontend/src/components/master-data/MdDataTableCard.vue` |
| 分页组件 | `frontend/src/components/master-data/MdPagination.vue` |
| 空态组件 | `frontend/src/components/master-data/MdEmptyState.vue` |
| 新增/编辑弹窗 | `frontend/src/components/master-data/DepartmentWarehouseCatalogDialog.vue` |
| 批量维护弹窗 | `frontend/src/components/master-data/DepartmentWarehouseCatalogBatchDialog.vue` |
| 确认框 | `frontend/src/components/common/ConfirmDialog.vue` |
| 消息通道 | `frontend/src/composables/useNotify.ts` |
| 确认框通道 | `frontend/src/composables/useSpdConfirm.ts` |
| 列宽配置 | `frontend/src/composables/useListColumns.ts` |

### 10.5 术语表

| 术语 | 定义 |
| --- | --- |
| **字号档位** | standard / large / xlarge 三档，由 `<html data-font-size>` 驱动 |
| **基准字号 `--spd-fs-base`** | 三档的推导原点，14 / 16 / 18px |
| **令牌推导** | 由 `--spd-fs-base` 通过 `calc()` 推导，三档值必然成立 |
| **表格表头** | 表格中列名所在的 `th` 行 |
| **表格数据单元格** | 表格中内容所在的 `td` 单元格 |
| **主字段** | 单元格内的主标题文本，如商品名称主行（`.ulc-strong`） |
| **次级说明** | 单元格内主字段下方的补充说明文字（`.ulc-sub`） |
| **状态条** | `.ulc-chip`，带 7px 圆点的语义状态标签 |
| **状态徽章** | `.md-status`，无圆点的语义状态标签（**本页未使用**） |
| **行内操作按钮** | `.ulc-action`，表格行内的编辑 / 启用 / 删除操作 |
| **工具栏按钮** | `.md-btn`，表格上方工具栏的页面级操作 |
| **列设置** | 表格列显隐与宽度配置的浮层面板（`.ulc-col-settings-pop`） |
| **筛选区** | 表格上方的查询条件区域（`.ulc-filter`） |
| **辅助信息** | 角标、计数、免责、版权等不承载业务判断的信息 |
| **展示型数值** | 允许突破常规层级的字号（30–42px），**本页不使用** |
| **图表令牌** | `--spd-chart-font-*`，供 ECharts canvas 读取的独立字号体系，**本页无图表** |
| **深色文字 + 浅色背景** | 本页配色原则：正文用深色字，背景用浅色底 |
| **静墨宣纸** | 配色方案 E，对应路由 `/features/department-warehouse-catalog-calm`，字体规格与本页完全一致 |

### 10.6 变更记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1.0 | 2026-11-23 | 首版。基于 `spd-typography-spec-final.md` v3.0，结合 `MasterDataListView.vue` 第 1285–1343 / 2521–2780 行的 `isDepartmentWarehouseCatalog` 分支、`unified-list.css`、`master-data.theme.css`、`department-warehouse-catalog.css`、`typography.css` 逐条实读核对，输出科室库房目录页完整字体规格。覆盖 7 个页面区域 + 9 类弹窗浮层，登记 8 项字号差异 |

---

**文档结束。**

> 本文档是 `docs/ui-review/spd-typography-spec-final.md` v3.0 在**科室库房目录页**上的落地展开。
> 一切数值以标准文档为准；本文档与标准文档冲突时，**以标准文档为准**。
> 本文档只写文字规格，**不含截图**，截图由使用者自行补充。
