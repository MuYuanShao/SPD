# SPD 平台字体与字号标准 —— 最终定稿执行文档

> **版本**：v3.0（最终定稿 · 可执行标准）
> **状态**：**已定稿**。本文档中的全部数值、字重、行高、令牌名均为结论，不含待定项。
> **前置依据**：`docs/ui-review/spd-typography-standard.md`（v2.0 现状调研版，995 行，17 个一级分类，24 项不一致清单）
> **风格参考**：`docs/ui-review/dept-warehouse-catalog-comparison.md`（配色方案对比，三档字号系统实测）
> **适用仓库**：`D:\SPD`（分支 `feature/ui-calm-paper` 方案 E「静墨宣纸」）
> **适用范围**：院内 SPD 平台全部页面 —— 基础数据、供应链、库存、结算、系统管理、操作驾驶舱、AI 助手
> **不含范围**：打印到纸面的真实排版（另立《打印排版规范》）、登录页（独立体系，见 4.17）

---

## 目录

| 章 | 标题 | 内容 |
| --- | --- | --- |
| 1 | 文档定位与使用方式 | 谁读、怎么用、与调研稿的关系 |
| 2 | 字体基础规范 | 字体族、字重梯度、数字等宽、行高 |
| 3 | 字号档位与令牌体系 | 三档机制、完整令牌表、新增令牌 |
| 4 | 完整执行标准 | **核心章节**，按 UI 区域逐条给出标准值 |
| 5 | 图表字体专项 | ECharts canvas 限制与 `--spd-chart-font-*` |
| 6 | 字号层级与视觉优先级模型 | 层级阶梯与硬约束规则 |
| 7 | 整改清单 | 15 项不一致的逐条执行表 |
| 8 | 实施阶段与验收 | 4 阶段、验收标准、回归命令 |
| 9 | 新增页面 / 组件字体检查清单 | 一页 checklist |
| 10 | 附录 | 令牌速查、术语表、变更记录 |

---

## 1. 文档定位与使用方式

### 1.1 本文档的性质

本文档是 SPD 平台**字体与字号的唯一可执行标准**。文档中出现的每一个数值都是结论，不是选项。

与前置文档的关系：

| 文档 | 角色 | 读者动作 |
| --- | --- | --- |
| `spd-typography-standard.md`（v2.0） | **依据** —— 现状调研、证据采集、不一致项发现 | 已读完，无需再读 |
| `spd-typography-spec-final.md`（本文档 v3.0） | **结论** —— 标准值、令牌、执行顺序、验收口径 | **照此执行** |

调研稿中的「建议改成什么」在本文档中一律升级为「规定为」。调研稿第 10 章的 6 个待决策问题（Q-01 ~ Q-06）已在本文档第 1.3 节给出最终裁定，**不再存在待定项**。

### 1.2 读者

| 角色 | 必读章节 | 使用方式 |
| --- | --- | --- |
| 产品经理 | 1、6、7、8 | 评审验收标准、裁定新争议、排定整改优先级 |
| 前端开发 | 2、3、4、5、7、8、9 | 按第 4 章逐区域改样式，按第 7 章逐条销项 |
| QA | 4、6、8 | 按第 4 章的「标准执行表」对屏核对，按第 8 章的 computed style 数值验收 |
| 新人 / AI 编码助手 | 3、4、9 | 新增页面时直接查第 4 章分类并抄值，走第 9 章 checklist |

### 1.3 产品决策最终裁定

| 决策 | 结论 | 落地影响 |
| --- | --- | --- |
| **Q-02** 表格表头 | **跟随字号档位放大** | 新增令牌 `--spd-font-size-table-head` |
| **Q-03** 表格数据单元格 | **全站统一 14px** | 表格数据统一用 `--spd-font-size-base` |
| **Q 下限** 最小字号 | **12px 为全站最小字号下限** | 11px 及以下仅限角标/徽标/图例等辅助信息；**禁止**用于需要用户判断的信息 |
| **Q-01** 图表字体 | **纳入三档体系** | 新建图表专用令牌 `--spd-chart-font-*` |
| **Q-05** 登录页 | **维持独立体系，不参与档位缩放** | 登录页内部只做标签层级纠正，不引入令牌 |
| **Q-06** 驾驶舱 | **允许突破常规层级**（演示/监控场景保留展示型大字号），但 **10px 类微型标签仍须提升到 12px** | 驾驶舱数值保留 30–38px；所有 ≤11px 标签升到 12px |
| **Q-04** 打印模板 | **独立排版体系，另立规范** | 本文档只给边界原则（见 4.19），不展开 |

### 1.4 怎么用

**新增页面**：在第 4 章找到对应 UI 区域，直接复制该区域表格里的「标准字号 / 字重 / 行高 / CSS 类名 / 令牌」五列，不自造数值。若该区域在第 4 章中不存在，先在第 4 章登记一条新分类并给出参考元素，再实现。

**整改现有页面**：按第 7 章整改清单逐条执行。清单中每一行都写明「现状值 → 目标值 → 精确到 CSS 类名的改动点 → 优先级」，不需要重新分析现状。

**自造数值**：禁止。任何新出现的字号必须先在第 3 章找到归属令牌；找不到则新增令牌并登记，不得在业务样式文件里写裸 `px` 字号。

### 1.5 与其它规范的关系

本规范只管「多大」，不管「什么颜色」「什么圆角」。

| 维度 | 唯一来源 | 本文档覆盖 |
| --- | --- | --- |
| 字号 | `frontend/src/styles/tokens/typography.css` | ✅ 全部 |
| 颜色 | `frontend/src/styles/tokens/colors.css` | ❌ 另见配色规范 |
| 圆角 / 边框 | `colors.css` + `layout/content-surface.css` | ❌ 另见圆角规范 |
| 图表字号 | `tokens/typography.css`（图表段）+ `TrendChart.vue` 读取 | ✅ 全部 |

### 1.6 数值来源标注约定

本文档每条数值都带来源标注：

| 标注 | 含义 |
| --- | --- |
| **实测** | 浏览器 `getComputedStyle` 确认，或直接读取源文件确认 |
| **CSS 推断** | 从样式表规则推断，未在浏览器中逐一验证 |
| **令牌推导** | 由 `--spd-font-size-*` 推导，三档值必然成立 |

### 1.7 数值来源说明

调研稿写作期间，仓库中部分数值已发生变化（分支演进）。本文档以**当前工作区实际代码**为准，差异在第 7 章整改清单中标注。

---

## 2. 字体基础规范

### 2.1 字体族（收敛为 2 套）

全站只允许 **2 套字体栈**。任何第三套字体栈一律视为缺陷。

| 套 | 用途 | 字体栈 | 定义位置 |
| --- | --- | --- | --- |
| **业务系统** | 除登录页外的全部界面 | `-apple-system, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif` | `styles/spd-theme.css` 的 `--spd-font-family`；由 `spd-override.css` 强制应用到 `body` |
| **登录页** | 登录页独立体系 | `"PingFang SC", "Microsoft YaHei", system-ui, sans-serif` | `styles/views/login.css` 的 `.standalone-login` |

**规定**：

1. 业务系统字体栈以 `--spd-font-family` 为**唯一入口**。`main.css` 的 `Inter` 系、`medical-saas.css` 的 `"Segoe UI Variable"` 系一律删除或改为 `var(--spd-font-family)` 引用。
2. `--spd-font-family-display`（HarmonyOS Sans SC → Alibaba PuHuiTi → …）保留，用于 `h1`–`h6` 与页面主标题；其 fallback 链末端必须包含 `--spd-font-family` 的同一批字体。
3. 登录页字体栈**不参与**字号档位缩放，与 `--spd-font-family` 完全隔离。

### 2.2 等宽字体

| 用途 | 令牌 | 字体栈 |
| --- | --- | --- |
| 编码 / 单号 / 金额 / UID | `--spd-font-family-mono` | `"SF Mono", Monaco, "Cascadia Mono", "Roboto Mono", Consolas, monospace` |

**规定**：

1. 等宽字体**只用于**编码、单号、UID/唯一码、金额、时间戳。**禁止**用于正文段落、商品名称、按钮文字。
2. 等宽字体必须与 `font-variant-numeric: tabular-nums` 同时使用，保证跨行数字对齐。
3. 结算页的 `.settlement-code-list`、科室申领页的 `.dept-req-selected-list .selected-code`、统一列表的 `.ulc-mono` 是全站仅有的三处等宽应用点，均保留。

### 2.3 字重梯度

全站只允许 **5 档字重**：`400 / 500 / 600 / 700 / 800`。

| 字重 | 语义 | 参考元素 |
| --- | --- | --- |
| 400 / normal | 正文、表格数据、说明文字、弹窗正文 | 统一列表数据单元格、`.modal-body` |
| 500 | 次级强调、辅助标签、页签未激活 | `.ulc-sub`、`.md-query label > span` |
| 600 | 强调、字段标签、状态条、次级标题 | `.ulc-chip`、`.md-status`、`.ulc-action` |
| 700 | 区块标题、表格表头、主按钮 | `.ulc-table th .cell`、`.md-btn`、`.ulc-strong` |
| 800 | 展示型数值、金额、品牌标识 | `.md-stat-value`、`.settlement-detail-table .amount-cell`、`.spd-brand-mark` |

**规定**：

1. **`font-weight: 650` / `750` / `850` 一律非法**。650 一律改为 **600**，750 改为 **700**，850 改为 **800**。当前 `medical-saas.css` 第 107、120、142、229、352、546 行共 6 处 `650`（其中 2 处带 `!important`）必须全部整改。
2. 字重 **900 及以上**禁止使用。`.spd-brand-mark` 的 800 是上限。
3. 字重 **300 / 200 / 100** 禁止使用。
4. 字重必须与字号配合表达层级，**禁止**用字重单独制造层级（同字号下字重跨度不超过 2 档）。

### 2.4 数字等宽（tabular-nums）

**必须**加 `font-variant-numeric: tabular-nums` 的位置：

| 位置 | 类名 | 参考元素 |
| --- | --- | --- |
| 表格等宽单元格 | `.ulc-mono` | 『科室库房目录』页表格『商品编码』列的 `SP0000123` |
| 表格时间列 | `.ulc-time` | 任意统一列表页『更新时间』列的 `2026-11-23 09:41` |
| 结算金额列 | `.settlement-detail-table .numeric-cell` / `.amount-cell` | 『结算对账』页结算明细表『结算金额』列的 `¥ 12,480.00` |
| 结算编码列表 | `.settlement-code-list` | 同页『UID / 唯一码』列 |
| 统计数值 | `.md-stat-value` | 基础数据页统计卡『医院商品总数』的 `12,480` |
| 分页页码 | `.hospital-page-indicator` | 分页条『第 1 / 9 页』 |
| AI 助手消息时间 | `.ai-medical-message time` | AI 助手气泡下方时间戳 |
| 驾驶舱 KPI | `.kpi-body strong` | 『操作驾驶舱』页 KPI 数值 |
| 拣配数量 | `.analysis-qty-input` | 『拣配记录』页拣配弹窗数量输入框 |

**判定规则**：任何需要跨行对齐或跨列比较的数字，一律等宽。金额、数量、日期、编码、页码无一例外。

### 2.5 行高三档

行高收敛为 **3 档令牌**，禁止出现其它数值。

| 令牌 | 值 | 用途 | 参考元素 |
| --- | --- | --- | --- |
| `--spd-lh-tight` | 1.35 | 按钮、状态条、徽标、表格单元格 | `.ulc-chip`、`.ulc-action`、`.md-btn`、统一列表数据单元格 |
| `--spd-lh-base` | 1.5 | 正文默认、表格数据、输入控件 | `body`、`.md-table tbody td`、`.el-input__wrapper` |
| `--spd-lh-loose` | 1.65 | 长段落、说明文字、弹窗正文 | `.ai-medical-answer p`、`.login-aside-hero p` |

**规定**：

1. 任何 `line-height` 必须写成 `var(--spd-lh-tight | --spd-lh-base | --spd-lh-loose)`，或写为无单位倍数 `1 / 1.2 / 1.35 / 1.4 / 1.5 / 1.65` 中与上述令牌等值者。**禁止** 1.85、1.55、1.3、1.45、1.6、1.7、1.75、1.8 等散落值。
2. 表格数据单元格行高固定 `--spd-lh-tight`（1.35），与 `--spd-table-cell-padding-y` 配合保证大档位下版面不松散。
3. 按钮、状态条、徽标行高固定 `--spd-lh-tight`；单行控件（`height: 26–30px`）可写 `line-height: 1`。
4. 登录页独立体系，其 `1.85` 行高**保持不变**（见 4.17）。

---

## 3. 字号档位与令牌体系

### 3.1 三档机制

字号体系由 `<html data-font-size="standard|large|xlarge">` 驱动，全部令牌由基准 `--spd-fs-base` 推导。

| 档位 | `--spd-fs-base` | 缩放比 | 适用场景 | 持久化键 |
| --- | --- | --- | --- | --- |
| **standard**（默认） | 14px | 100% | 常规显示器 · 密度优先 | `localStorage['spd.theme.fontSize']` |
| **large** | 16px | 114% | 27 寸以上屏幕 · 轻度老花 | 同上 |
| **xlarge** | 18px | 129% | 视力偏弱 · 高分辨率远距使用 | 同上 |

**机制细节**（**实测**）：

```css
html { font-size: var(--spd-fs-base); }            /* 整站 rem 等比缩放 */
body { font-size: var(--spd-fs-base); line-height: var(--spd-lh-base); }
```

| 档位 | `--spd-table-cell-padding-y` |
| --- | --- |
| standard | 10px |
| large | 12px |
| xlarge | 13px |

用户入口：顶栏主题切换器 → 字号档位（`frontend/src/stores/theme.ts` 的 `FONT_SIZES` / `applyFontSize()`）。

### 3.2 基础字号令牌表（10 个，全部已存在）

定义位置：`frontend/src/styles/tokens/typography.css`（**令牌推导**，三档值必然成立）。

| 令牌 | 表达式 | standard (14) | large (16) | xlarge (18) | 新语义定位 |
| --- | --- | --- | --- | --- | --- |
| `--spd-font-size-xs` | base − 3px | 11px | 13px | 15px | **角标 / 计数徽标 / 纯装饰标签**（仅限不需判断的信息） |
| `--spd-font-size-sm` | base − 1px | 13px | 15px | 17px | **次级说明、筛选标签、表单标签、辅助文本** |
| `--spd-font-size-base` | base | 14px | 16px | 18px | **表格数据单元格、正文基准、输入控件文字** |
| `--spd-font-size-md` | base + 1px | 15px | 17px | 19px | **正文强调文本**（见 3.5 用途变更） |
| `--spd-font-size-lg` | base + 2px | 16px | 18px | 20px | **区块小标题、卡片标题** |
| `--spd-font-size-xl` | base + 4px | 18px | 20px | 22px | **弹窗内区块标题 / 二级标题** |
| `--spd-font-size-2xl` | base + 6px | 20px | 22px | 24px | **弹窗标题、统计数值（标准）** |
| `--spd-font-size-3xl` | base + 10px | 24px | 26px | 28px | **页面主标题** |
| `--spd-font-size-4xl` | base + 16px | 30px | 32px | 34px | **展示型大数值（驾驶舱）** |
| `--spd-font-size-5xl` | base + 24px | 38px | 40px | 42px | **超大展示数值（驾驶舱 KPI）** |

### 3.3 新增令牌（本次定稿必须新增）

新增位置：`frontend/src/styles/tokens/typography.css`，紧跟基础字号令牌块。

| 令牌 | 表达式 | standard | large | xlarge | 用途 | 替代的写死值 |
| --- | --- | --- | --- | --- | --- | --- |
| `--spd-font-size-table-head` | `var(--spd-font-size-base)` | 14px | 16px | 18px | **表格表头** | 统一列表表头 14px 写死、md-* 表头 12px 写死、科室申领目录表头 12px 写死、结算明细表头 12px 写死 |
| `--spd-chart-font-xs` | `calc(var(--spd-fs-base) - 3px)` | 11px | 13px | 15px | 图表微型角标（预留） | — |
| `--spd-chart-font-sm` | `calc(var(--spd-fs-base) - 2px)` | 12px | 14px | 16px | **坐标轴刻度、轴名称** | 轴标签 10px 写死 |
| `--spd-chart-font-base` | `calc(var(--spd-fs-base) - 1px)` | 13px | 15px | 17px | **图例文字** | 图例 11px 写死 |
| `--spd-chart-font-lg` | `var(--spd-font-size-base)` | 14px | 16px | 18px | **数据标签、Tooltip 标题** | — |
| `--spd-chart-font-xl` | `calc(var(--spd-fs-base) + 1px)` | 15px | 17px | 19px | **Tooltip 正文** | — |

**规定**：图表令牌使用 `-2px / -1px / 0 / +1px` 的相对基准，**不**复用 `--spd-font-size-*` 的 `-3 / -1 / 0 / +1 / +2` 偏移。原因是图表 canvas 需要独立的像素值粒度，且图表字号整体比同页正文轻一档（见第 5 章）。

### 3.4 令牌与语义对应表

| 语义 | 令牌 | standard | large | xlarge | 参考元素 |
| --- | --- | --- | --- | --- | --- |
| 表格表头 | `--spd-font-size-table-head` | 14px | 16px | 18px | 『科室库房目录』页表头『商品名称』 |
| 表格数据 | `--spd-font-size-base` | 14px | 16px | 18px | 同页『商品名称』列的『聚维酮碘消毒剂皮肤外用消毒液 500ml 棕色瓶装』 |
| 表格主字段 | `--spd-font-size-base` + 700 | 14px | 16px | 18px | 同列的主标题行（用字重区分，不加字号） |
| 表格次级说明 | `--spd-font-size-sm` | 13px | 15px | 17px | 同列下的『科室可申领商品』 |
| 筛选标签 | `--spd-font-size-sm` + 600 | 13px | 15px | 17px | 筛选区『商品编码』 |
| 输入控件文字 | `--spd-font-size-base` | 14px | 16px | 18px | 筛选输入框内输入文字 |
| 表单标签 | `--spd-font-size-sm` + 600 | 13px | 15px | 17px | 弹窗内『商品名称』字段标签 |
| 状态条 | `--spd-font-size-base` + 600 | 14px | 16px | 18px | 表格『状态』列的『启用』 |
| 按钮（主/次） | `--spd-font-size-base` + 600 | 14px | 16px | 18px | 工具栏『新增』 |
| 按钮（小号） | `--spd-font-size-sm` + 600 | 13px | 15px | 17px | 分页『上一页』 |
| 徽标 / 计数 | `--spd-font-size-xs` | 11px | 13px | 15px | 迷你侧栏计数角标 |
| 区块标题 | `--spd-font-size-lg` + 700 | 16px | 18px | 20px | 面板标题条 |
| 弹窗标题 | `--spd-font-size-2xl` + 600 | 20px | 22px | 24px | 弹窗标题 |
| 页面主标题 | `--spd-font-size-3xl` + 600 | 24px | 26px | 28px | 页面顶部页面名 |
| 统计数值 | `--spd-font-size-2xl` + 800 | 20px | 22px | 24px | 统计卡『医院商品总数』 |
| 展示型数值 | `--spd-font-size-4xl` / `-5xl` | 30 / 38px | 32 / 40px | 34 / 42px | 驾驶舱 KPI（允许突破） |
| 图表轴刻度 | `--spd-chart-font-sm` | 12px | 14px | 16px | 『采购趋势』图 X 轴月份 |
| 图表图例 | `--spd-chart-font-base` | 13px | 15px | 17px | 同图右上角图例 |

### 3.5 `--spd-font-size-md` 的新用途

`--spd-font-size-md`（base + 1px）在本次定稿后**不再用于表格数据**。

| 阶段 | `--spd-font-size-md` 的用途 | 状态 |
| --- | --- | --- |
| 整改前 | 统一列表表格数据单元格（`unified-list.css` 第 59、128、228 行） | 废弃 |
| **整改后** | **正文强调文本** —— 需要在段落中略微提亮的句子、摘要中的关键结论、卡片描述中的重点词 | **新用途** |

**规定**：

1. `--spd-font-size-md` 的新语义是「正文强调」。典型用法是弹窗正文、卡片描述、说明段落中的**局部强调**（`<span class="md-emphasis">`），**不是**整段字号。
2. **禁止**用 `--spd-font-size-md` 表达「表格数据」。表格数据一律 `--spd-font-size-base`。
3. **禁止**用 `--spd-font-size-md` 表达「比正文小的次级信息」—— 那是 `--spd-font-size-sm` 的职责。
4. 令牌本身**保留不删除**，保持体系连续（`md` 位于 `base` 与 `lg` 之间，档位序列不断裂）。

### 3.6 令牌使用纪律

1. 业务样式文件（`styles/**` 之外的 view / component 内联 `<style>`）**禁止**写裸 `font-size: Npx`，必须写 `var(--spd-font-size-*)`。
2. 允许裸 `px` 的例外，仅限以下四类，且必须在评审时说明理由：
   - 图标尺寸（`.spd-icon-btn` 的 16px）
   - 固定尺寸的圆点 / 徽标装饰（`.ulc-chip::before` 的 7px）
   - 打印模板纸张相关尺寸
   - 登录页独立体系
3. `spd-theme.css` 中另有一份同名固定值（xs 12 / sm 13 / base 14 / md 15 / lg 16 / xl 18 / 2xl 20 / 3xl 24 / 4xl 30 / 5xl 38）。因 `typography.css` 在 `main.css` 中**后于** `spd-theme.css` 引入，令牌跟随档位生效；但 `spd-theme.css` **内部工具类**（`.text-tertiary` 12px、`.spd-tag` 12px 等）仍为固定值，必须整改为令牌引用（见整改清单 D-13）。

---

## 4. 完整执行标准

### 4.0 阅读方式

每张表包含：标准值 / 三档实际值 / CSS 类名 / **参考实现位置（页面 · 具体元素）**。参考元素是验收锚点 —— 评审时直接对屏核对。

**通用规则（覆盖所有区域）**：

| # | 规则 |
| --- | --- |
| G-1 | 表格数据单元格 = `--spd-font-size-base` / 400 / `--spd-lh-tight` |
| G-2 | 表格表头 = `--spd-font-size-table-head` / 700 / `--spd-lh-tight` |
| G-3 | 状态条 = `--spd-font-size-base` / 600 / `--spd-lh-tight` |
| G-4 | 按钮（主/次/行内）= `--spd-font-size-base` / 600 / `--spd-lh-tight` |
| G-5 | 筛选标签 = `--spd-font-size-sm` / 600 |
| G-6 | 输入控件文字 = `--spd-font-size-base` / 400 |
| G-7 | 表格次级说明 = `--spd-font-size-sm` / 400 |
| G-8 | 徽标 / 计数 = `--spd-font-size-xs` / 600 |
| G-9 | 所有需要用户读取判断的信息 **≥ 12px** |
| G-10 | 12px 只允许出现在：角标、计数徽标、图例、免责/版权、辅助计数、纯装饰标签 |

---

### 4.1 全局框架（顶栏 / 页签 / 迷你侧栏 / 面包屑 / 页脚）

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置（页面 · 元素） |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| G-01 | 顶栏品牌文字 | `--spd-font-size-lg` | 800 | 1.2 | 16px | 18px | 20px | `.spd-topnav-brand-text` | 全站顶栏左侧『SPD 院内供应链』 |
| G-02 | 顶栏主菜单项 | `--spd-font-size-base` | 500 | 1.4 | 14px | 16px | 18px | `.spd-menu-label` | 顶栏『工作台』『基础数据管理』『供应链业务』 |
| G-03 | 顶栏下拉分组标题 | `--spd-font-size-xs` | 700 | 1.4 | 11px | 13px | 15px | `.spd-dropdown-group-title` | 顶栏『供应链业务』展开后『库存与仓储』『采购与结算』分组标题 |
| G-04 | 顶栏下拉叶子链接 | `--spd-font-size-sm` | 500 | 1.4 | 13px | 15px | 17px | `.spd-dropdown-link-name` | 顶栏下拉『科室库房目录』『库存管理』 |
| G-05 | 顶栏图标按钮 | 图标 16px（固定） | — | — | 固定 | 固定 | 固定 | `.spd-icon-btn` | 顶栏右侧『刷新数据』『消息通知』 |
| G-06 | 顶栏用户姓名 | `--spd-font-size-sm` | 600 | 1.4 | 13px | 15px | 17px | `.spd-user-name` | 顶栏右侧用户胶囊『张伟』 |
| G-07 | 顶栏用户头像文字 | `--spd-font-size-sm` | 700 | 1 | 13px | 15px | 17px | `.spd-user-avatar` | 顶栏右侧圆形头像内的『张』 |
| G-08 | 用户下拉菜单项 | `--spd-font-size-sm` | 400 | 1.4 | 13px | 15px | 17px | `:deep(.el-dropdown-menu__item)` | 用户菜单『修改密码』『退出登录』 |
| G-09 | 页签栏页签文字 | `--spd-font-size-base` | 400 / 激活 700 | 1.4 | 14px | 16px | 18px | `.spd-tabbar-tab` | 页签栏『工作台』『科室库房目录』 |
| G-10 | 迷你侧栏分组标题 | `--spd-font-size-xs` | 700 | 1.4 | 11px | 13px | 15px | `.spd-mini-sidebar-title` | 迷你侧栏顶部『业务模块』大写标题 |
| G-11 | 迷你侧栏一级项 | `--spd-font-size-sm` | 500 / 激活 600 | 1.4 | 13px | 15px | 17px | `.spd-mini-nav-item` | 迷你侧栏『库存管理』 |
| G-12 | 迷你侧栏二级分组 | `--spd-font-size-xs` | 600 | 1.4 | 11px | 13px | 15px | `.spd-mini-nav-group-title` | 迷你侧栏『采购与结算』分组 |
| G-13 | 迷你侧栏计数徽标 | `--spd-font-size-xs` | 600 | 1 | 11px | 13px | 15px | `.spd-mini-nav-group-count` | 迷你侧栏分组右侧数字角标 |
| G-14 | 左侧主菜单一级项 | `--spd-font-size-base` | 400 | 1.4 | 14px | 16px | 18px | `.nav-item` | 左侧菜单『科室库房目录』 |
| G-15 | 左侧主菜单三级项 | `--spd-font-size-sm` | 400 muted | 1.4 | 13px | 15px | 17px | `.nav-item.grandchild` | 左侧菜单『商品目录』 |
| G-16 | 面包屑 | `--spd-font-size-sm` | 400 / 当前 600 | 1.5 | 13px | 15px | 17px | `.breadcrumb-line` | 页面顶部『基础数据管理 / 科室库房目录』 |
| G-17 | 页脚 | `--spd-font-size-xs` | 400 | 1.5 | 11px | 13px | 15px | `.fli-footer` | 页面底部版权行 |

**整改说明**：本区整改对应 D-18（迷你侧栏标题 `11px !important` 强制）与 D-16（面包屑当前项非法字重 550）。G-03（12px）、G-04（13.5px）、G-06 / G-07（13px）、G-11（13px）、G-12（12.5px）、G-16（13px）、G-17（11px）均为写死值，统一在阶段 2 令牌化为对应令牌；G-04 与 G-12 的非整数字号按 D-19 整数化（13.5px → 13px，12.5px → 11px）。改动文件：`styles/spd-layout.css`、`styles/spd-override.css`（第 165–172 行）、`styles/theme.css`（第 515 行）、`styles/theme/calm-paper.css`（第 388 行）、`styles/medical-saas.css`、`styles/main.css`。

---

### 4.2 页面标题区

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P-01 | 页面主标题（标准页） | `--spd-font-size-3xl` | 600 | 1.3 | 24px | 26px | 28px | `.master-page-title h2` | 基础数据各页顶部的页面名 |
| P-02 | 页面主标题（Ant 覆盖层） | `--spd-font-size-3xl` | 600 | 1.3 | 24px | 26px | 28px | `.standard-page-heading h2` | 『DataTable 通用表格演示』页标题 |
| P-03 | 页面副标题 / 描述 | `--spd-font-size-sm` | 400 | 1.5 | 13px | 15px | 17px | `.master-page-title p` | 页面标题下的说明文字 |
| P-04 | 区块小标题（面板） | `--spd-font-size-lg` | 600 | 1.4 | 16px | 18px | 20px | `.md-panel-title` | 面板标题条『科室库房目录』 |
| P-05 | 区块小标题（section） | `--spd-font-size-xl` | 700 | 1.3 | 18px | 20px | 22px | `.section-title h2` | 审批详情页各流程区块标题 |
| P-06 | 区块小标题（紧凑） | `--spd-font-size-lg` | 700 | 1.4 | 16px | 18px | 20px | `.section-title.compact h3` | 『拣配记录』页左侧『拣配任务』 |
| P-07 | 弹窗章节标题 | `--spd-font-size-sm` | 600 | 1.4 | 13px | 15px | 17px | `.md-form-section h4` | 弹窗内『基本信息』『资质信息』小节 |
| P-08 | 段标题（列表卡片） | `--spd-font-size-lg` | 600 | 1.4 | 16px | 18px | 20px | `.dept-req-section-title h3` | 『科室申领』页『历史申领单』 |
| P-09 | 段标题右侧元信息 | `--spd-font-size-xs` | 400 | 1.4 | 11px | 13px | 15px | `.dept-req-section-title > span` | 同上右侧的计数 |

**整改说明**：P-01（22px）、P-02（20px）、P-03（13px）、P-04（15px）、P-05（18px）、P-06（15px）、P-08（15px）均为写死值，统一令牌化。**P-03 全局隐藏**：`main.css` 第 128–140 行 `display: none !important` 隐藏 `.standard-page-heading > p`、`.workbench-hero p`、`.detail-heading > div > p`、`.section-title small`、`.subnav-panel small` —— **保持隐藏**（产品方向：去顶部概览），同时**移除 `MdPageHeader` 的 `description` prop** 与调用点传参，属 **D-20**。改动文件：`styles/master-data.theme.css`、`styles/spd-override.css`、`styles/medical-saas.css`、`styles/main.css`（第 128–140 行）、`components/common/MdPageHeader.vue`、`views/supply-chain/DepartmentRequisitionView.vue`。

> **说明**：`MdPageHeader` 的 `description` prop 属于历史遗留。产品已定方向为「去掉顶部概览」，因此该 prop 的正确终态是**删除**，而不是「保留并显示 13px」。这是本轮唯一「删代码」而非「改数值」的条目。

---

### 4.3 筛选区

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F-01 | 筛选标签 | `--spd-font-size-sm` | 600 | 1.4 | 13px | 15px | 17px | `.ulc-filter label > span` | 统一列表筛选区『商品编码』『规格型号』 |
| F-02 | 筛选输入框 / 下拉文字 | `--spd-font-size-base` | 400 | 1.4 | 14px | 16px | 18px | `.ulc-filter .el-input__wrapper` / `.md-input` | 筛选框内输入文字 |
| F-03 | 筛选按钮 | `--spd-font-size-base` | 700 | 1 | 14px | 16px | 18px | `.ulc-filter-actions .md-btn` | 筛选区『查询』『重置』 |
| F-04 | md-* 查询区标签 | `--spd-font-size-sm` | 600 | 1.4 | 13px | 15px | 17px | `.md-query label > span` | md-* 列表页筛选区字段标签 |
| F-05 | md-* 输入框文字 | `--spd-font-size-base` | 400 | 1.4 | 14px | 16px | 18px | `.md-input` / `.md-select` / `.md-textarea` | md-* 筛选输入框 |
| F-06 | 科室申领筛选标签 | `--spd-font-size-sm` | 600 | 1.4 | 13px | 15px | 17px | `.dept-req-filterbar-item > span` | 『科室申领』弹窗筛选条『科室』『收货库房』 |
| F-07 | 科室申领筛选控件 | `--spd-font-size-base` | 400 | 1.4 | 14px | 16px | 18px | `.dept-req-filterbar-item > input/select` | 同上筛选下拉框内文字 |
| F-08 | 科室申领筛选按钮 | `--spd-font-size-base` | 600 | 1 | 14px | 16px | 18px | `.dept-req-filterbar-actions .btn` | 同上『查询』『重置』 |
| F-09 | 主搜索框文字 | `--spd-font-size-base` | 400 | 1.4 | 14px | 16px | 18px | `.master-search input` | 基础数据页顶部搜索框 |
| F-10 | 搜索条汇总提示 | `--spd-font-size-sm` | 500 | 1.4 | 13px | 15px | 17px | `.spd-data-table__hint` | 『DataTable 通用表格演示』页工具栏『已选 3 条 · 共 168 条』 |
| F-11 | 输入框占位符 | 继承控件字号 | 400 | — | 随控件 | 随控件 | 随控件 | `::placeholder` | 任意搜索框灰色提示文字 |

**整改说明**：F-01（14px 写死，`unified-list.css` 第 654–656 行）、F-02（13px 写死，第 571 行）、F-03（14px 写死，第 702 行）属 D-10；F-04（12px）、F-05（13px）属阶段 2 令牌化；**F-06（11px）、F-07（12px）、F-08（12px）属 D-02，是全站最需要用户读取判断却字号最小的筛选标签，列为 P0**。改动文件：`styles/list/unified-list.css`、`styles/master-data.theme.css`、`views/supply-chain/DepartmentRequisitionView.vue`（第 1830 行）。

> F-06 是**全站最需要用户读取判断却字号最小的筛选标签**。11px 低于全站下限，列为 P0。

---

### 4.4 表格（表头 / 数据 / 主字段 / 次级说明 / 长文本 / 编码 / 时间 / 空态）

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T-01 | **表格表头（全站统一）** | `--spd-font-size-table-head` | 700 | 1.35 | 14px | 16px | 18px | `.ulc-table .el-table__header th .cell` | 『科室库房目录』页表头『商品名称』 |
| T-02 | **表格数据单元格（全站统一）** | `--spd-font-size-base` | 400 | 1.35 | 14px | 16px | 18px | `.ulc-table td.el-table__cell` | 同页『商品名称』列的『聚维酮碘消毒剂皮肤外用消毒液 500ml 棕色瓶装』 |
| T-03 | 单元格主字段 | `--spd-font-size-base` | 700 | 1.35 | 14px | 16px | 18px | `.ulc-strong` | 同列主标题行 |
| T-04 | 单元格次级说明 | `--spd-font-size-sm` | 500 | 1.35 | 13px | 15px | 17px | `.ulc-sub` / `.ulc-wrap-text.is-sub` | 同列下的『科室可申领商品』 |
| T-05 | 单元格等宽（编码） | `--spd-font-size-base` | 400 | 1.35 | 14px | 16px | 18px | `.ulc-mono` | 任意列表页『商品编码』列 |
| T-06 | 单元格时间 | `--spd-font-size-base` | 400 | 1.35 | 14px | 16px | 18px | `.ulc-time` | 任意列表页『更新时间』列 |
| T-07 | 单元格长文本（换行） | `--spd-font-size-base` | 400 | 1.35 | 14px | 16px | 18px | `.ulc-wrap-text` | 『科室库房目录』页『生产厂家』列 |
| T-08 | md-* 表格数据 | `--spd-font-size-base` | 400 | 1.35 | 14px | 16px | 18px | `.md-table tbody td` | 基础数据 md-* 列表数据行 |
| T-09 | md-* 表格表头 | `--spd-font-size-table-head` | 700 | 1.35 | 14px | 16px | 18px | `.md-table thead th` | 同上表头行 |
| T-10 | md-* 单元格主名 | `--spd-font-size-base` | 700 | 1.35 | 14px | 16px | 18px | `.md-cell-name strong` | 同上『商品名称』列主名 |
| T-11 | md-* 单元格副名 | `--spd-font-size-sm` | 400 | 1.35 | 13px | 15px | 17px | `.md-cell-name small` | 同上主名下的规格说明 |
| T-12 | md-* 单元格等宽 | `--spd-font-size-base` | 400 | 1.35 | 14px | 16px | 18px | `.md-cell-mono` | 同上编码列 |
| T-13 | 原生 el-table 数据 | `--spd-font-size-base` | 400 | 1.35 | 14px | 16px | 18px | `.el-table` / `.spd-data-table__el` | 『DataTable 通用表格演示』页表格数据行 |
| T-14 | 原生 el-table 表头 | `--spd-font-size-table-head` | 700 | 1.35 | 14px | 16px | 18px | `.el-table th.el-table__cell` | 同上表头『商品编码』『规格型号』 |
| T-15 | `.master-table` 数据 | `--spd-font-size-base` | 400 | 1.35 | 14px | 16px | 18px | `.master-table` | 基础数据页原生表格 |
| T-16 | 科室申领目录表数据 | `--spd-font-size-base` | 400 | 1.35 | 14px | 16px | 18px | `.dept-req-catalog-body tbody td` | 『科室申领』弹窗内『科室申领目录』表数据行 |
| T-17 | 科室申领目录表头 | `--spd-font-size-table-head` | 700 | 1.35 | 14px | 16px | 18px | `.dept-req-catalog-body thead th` | 同上表头『商品编码』『散货可用』 |
| T-18 | 科室申领历史表数据 | `--spd-font-size-base` | 400 | 1.35 | 14px | 16px | 18px | `.dept-req-table tbody td` | 『科室申领』页『历史申领单』表 |
| T-19 | 科室申领历史表头 | `--spd-font-size-table-head` | 700 | 1.35 | 14px | 16px | 18px | `.dept-req-table th` | 同上表头『单据号』『请购人』 |
| T-20 | 弹窗内嵌表格数据 | `--spd-font-size-base` | 400 | 1.35 | 14px | 16px | 18px | `.el-dialog .el-table` | 弹窗内嵌表格数据行 |
| T-21 | 打印模板字段表 | `--spd-font-size-base` | 400 | 1.5 | 14px | 16px | 18px | `.md-print-template-table tbody td` | 『打印模板设置』页字段列表 |
| T-22 | 打印模板表头 | `--spd-font-size-table-head` | 700 | 1.35 | 14px | 16px | 18px | `.md-print-template-table thead th` | 同上表头『字段名称』 |

**整改说明**：**T-01（14px 写死不跟随档位）属 D-09**；**T-02（15px `--spd-font-size-md`）、T-03（16px `--spd-font-size-lg`）、T-05（15px）、T-06（15px 写死）属 D-08 —— 同一个「商品名称」列全站有 15/14/13/12 四种数据字号，统一到 14px，主字段靠 700 字重区分**；T-08 / T-09（13px / 12px）、T-11（11px）、T-21 / T-22（13px / 12px）同属 D-08；T-13 / T-14（14px 写死）需令牌化；**T-16 / T-17（12px / 12px）属 D-08，且是全站层级最弱的一处表格**；T-18 / T-19（13px / 12px）属 D-08；**T-20（12.5px）是全站唯一非整数字号，属 D-19**。

改动文件：`styles/list/unified-list.css`（第 59、87、128、211–213、225–228、728–730 行）、`styles/master-data.theme.css`、`styles/spd-override.css`、`styles/components/modal.css`、`views/supply-chain/DepartmentRequisitionView.vue`（第 2090、2093–2100、2108–2111 行）。

> **T-16/T-17 的现状说明**：`DepartmentRequisitionView.vue` 第 2090 行 `font-size: 12px`、第 2100 行表头 `font-size: 12px`、第 2111 行数据 `font-size: 12px`。表头与数据**完全同字号**，仅靠字重 600 与白色底区分。

---

### 4.5 状态与标签

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S-01 | **状态条（全站统一）** | `--spd-font-size-base` | 600 | 1.35 | 14px | 16px | 18px | `.ulc-chip` | 『科室库房目录』页『状态』列『启用』 |
| S-02 | 统一列表文本标签 | `--spd-font-size-base` | 600 | 1.35 | 14px | 16px | 18px | `.ulc-tag` | 同页『集采』『重点监控』标签 |
| S-03 | 状态条圆点 | 7px 直径（固定） | — | — | 固定 | 固定 | 固定 | `.ulc-chip::before` | 状态条左侧小圆点 |
| S-04 | md-* 状态徽章 | `--spd-font-size-base` | 600 | 1.35 | 14px | 16px | 18px | `.md-status` | 基础数据 md-* 列表『状态』列 |
| S-05 | md-* 通用 chip | `--spd-font-size-base` | 600 | 1.35 | 14px | 16px | 18px | `.md-chip` | 工具栏右侧『已选 3 条』信息 chip |
| S-06 | Tab 计数徽标 | `--spd-font-size-xs` | 600 | 1 | 11px | 13px | 15px | `.md-tab .md-tab-count` | Tab 上的数字角标 |
| S-07 | `spd-tag` 胶囊标签 | `--spd-font-size-sm` | 500 | 1.4 | 13px | 15px | 17px | `.spd-tag` | spd-theme 组件库标签 |
| S-08 | `status-badge` / `status-pill` | `--spd-font-size-base` | 400 | 1.35 | 14px | 16px | 18px | `.status-badge` | 『科室申领』页『历史申领单』『状态』列 |
| S-09 | 包标签 chip | `--spd-font-size-sm` | 400 | 1.4 | 13px | 15px | 17px | `.package-label-chip` | 定数包页商品行下的包规格 chip |
| S-10 | 盘点范围 chip | `--spd-font-size-sm` | 400 | 1.4 | 13px | 15px | 17px | `.stocktaking-scope-chip` | 『库存盘点』弹窗『盘点范围』选择 chip |
| S-11 | 库房等级 / 库位标签 | `--spd-font-size-sm` | 400 | 1.4 | 13px | 15px | 17px | `.spd-storage-name` / `.md-cell-name small` | 库存页库房条目名 |
| S-12 | 驾驶舱风险等级标记 | `--spd-font-size-xs` | 600 | 1.35 | 11px | 13px | 15px | `.spd-warning-level-text` | 『操作驾驶舱』页预警条目左侧等级标 |
| S-13 | 结算策略标记 | `--spd-font-size-xs` | 600 | 1.35 | 11px | 13px | 15px | `.settlement-policy-list b` | 『结算对账』页结算明细表『带量』『医保』前缀标 |
| S-14 | 结算已确认标记 | `--spd-font-size-sm` | 700 | 1.35 | 13px | 15px | 17px | `.settlement-confirmed-text` | 同上『已确认』文字 |

**整改说明**：S-01 / S-02 已按令牌跟随档位 ✅ **符合标准**，仅需令牌化（`styles/list/unified-list.css` 第 244、306 行）；**S-04 / S-05（12px 写死）属 D-12**，统一到 14px；S-07（12px 写死）按 D-19 令牌化；**S-12（10px）属 D-06**；**S-13（11px，`main.css` 第 1727–1731 行）属 D-15**，改为 `--spd-font-size-xs` 令牌引用。改动文件：`styles/list/unified-list.css`、`styles/master-data.theme.css`、`styles/spd-theme.css`、`styles/main.css`、`views/operations/OperationCockpitView.vue`。

> **S-13 的现状值修正**：调研稿记为 10.5px。**实测源文件 `main.css` 第 1727–1731 行为 11px**。本整改项按 11px 记录。

---

### 4.6 分页

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| N-01 | 分页汇总文案 | `--spd-font-size-sm` | 400 | 1.5 | 13px | 15px | 17px | `.hospital-pagination` | 任意列表底『显示 1-20 条，共 168 条』 |
| N-02 | 分页页码 / 上下页按钮 | `--spd-font-size-base` | 500 | 1 | 14px | 16px | 18px | `.hospital-pagination .btn` | 分页按钮『上一页』『1』『2』 |
| N-03 | 分页输入框 | `--spd-font-size-sm` | 400 | 1 | 13px | 15px | 17px | `.hospital-pagination input` | 分页『每页 [20] 条』输入框 |
| N-04 | 分页当前页指示 | `--spd-font-size-sm` | 600 | 1.4 | 13px | 15px | 17px | `.hospital-page-indicator` | 分页条『第 1 / 9 页』 |
| N-05 | md-* 分页容器 | `--spd-font-size-sm` | 400 | 1.5 | 13px | 15px | 17px | `.md-pagination` | md-* 列表分页条 |
| N-06 | md-* 分页按钮 | `--spd-font-size-sm` | 500 | 1 | 13px | 15px | 17px | `.md-pagination .md-btn` | md-* 分页页码按钮 |

**整改说明**：N-01（13px 写死，`pagination.css` 第 9 行）、N-02（写死 13–14px）、N-04（写死 14px）均属 **D-10**，统一令牌化。改动文件：`styles/components/pagination.css`。
---

### 4.7 表单

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| R-01 | 表单字段标签 | `--spd-font-size-sm` | 600 | 1.4 | 13px | 15px | 17px | `.md-form-grid label > span` | 基础数据弹窗『商品名称』『规格型号』字段标签 |
| R-02 | 字段辅助说明 | `--spd-font-size-sm` | 400 | 1.4 | 13px | 15px | 17px | `.md-form-grid small` / `.md-form-hint` | 同上字段下的提示文字 |
| R-03 | 必填星号 | 继承标签 | 600 | — | 随标签 | 随标签 | 随标签 | `.md-required` | 同上标签前的红色 `*` |
| R-04 | Element Plus 表单标签 | `--spd-font-size-sm` | 600 | 1.4 | 13px | 15px | 17px | `.el-form-item__label` | 弹窗内 el-form 标签 |
| R-05 | 自绘弹窗表单标签 | `--spd-font-size-sm` | 700 | 1.4 | 13px | 15px | 17px | `.supplier-form-grid span` / `.hospital-query-grid span` | 供应商弹窗字段标签 |
| R-06 | 弹窗正文基准 | `--spd-font-size-base` | 400 | 1.5 | 14px | 16px | 18px | `.modal-body` / `.el-dialog__body` | 弹窗 body 区域 |
| R-07 | 表单校验错误提示 | `--spd-font-size-sm` | 400 | 1.5 | 13px | 15px | 17px | `.md-error` | 表单校验失败红条 |
| R-08 | 字数 / 计数提示 | `--spd-font-size-xs` | 400 | 1.4 | 11px | 13px | 15px | `.md-form-hint` | 文本域下方『12/200』 |
| R-09 | 复选 / 单选标签 | `--spd-font-size-base` | 400 | 1.5 | 14px | 16px | 18px | 继承 body | 弹窗内复选框旁文字 |
| R-10 | 数量步进器数字 | `--spd-font-size-base` | 400 | 1.35 | 14px | 16px | 18px | `.qty-stepper input` | 『科室申领』已选清单『申领数量』输入框 |
| R-11 | 计数提示（辅助信息） | `--spd-font-size-xs` | 400 | 1.4 | 11px | 13px | 15px | `.dept-req-selected-head span` | 『科室申领』已选清单标题右侧计数 |

**整改说明**：R-01（12px）、R-02（12px）、R-07（13px）属阶段 2 令牌化；R-04（14px 写死）统一为 `--spd-font-size-sm`；R-06（13px 写死）统一为 `--spd-font-size-base`；**R-10（11px 写死，`DepartmentRequisitionView.vue` 第 2009 行起）属 D-03，是数量输入框，统一为 14px**；R-11（11px）属辅助计数，令牌化。改动文件：`styles/master-data.theme.css`、`styles/spd-override.css`、`styles/components/modal.css`、`views/supply-chain/DepartmentRequisitionView.vue`。
---

### 4.8 按钮

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| B-01 | 主操作按钮 | `--spd-font-size-base` | 600 | 1 | 14px | 16px | 18px | `--btn-font-size`（`.main-area .btn` / `.el-button`） | 任意页面主按钮『新增商品』 |
| B-02 | md-* 工具栏按钮 | `--spd-font-size-base` | 600 | 1 | 14px | 16px | 18px | `.md-btn`（unified-list 覆盖） | 列表工具栏『新增』『批量导入』 |
| B-03 | md-* 按钮（独立体系） | `--spd-font-size-base` | 600 | 1 | 14px | 16px | 18px | `.md-btn`（master-data.theme） | md-* 页面工具栏按钮 |
| B-04 | 行内操作按钮 | `--spd-font-size-base` | 600 | 1 | 14px | 16px | 18px | `.ulc-action` | 『科室库房目录』行内『编辑』『删除』 |
| B-05 | 文字按钮 | `--spd-font-size-base` | 600 | 1 | 14px | 16px | 18px | `.btn-text` / `.md-btn-text` | 行内『移除』『查看明细』 |
| B-06 | 小号按钮 | `--spd-font-size-sm` | 600 | 1 | 13px | 15px | 17px | `.btn-sm` / `.md-btn-sm` / `.el-button--small` | 分页『上一页』『下一页』 |
| B-07 | 图标按钮 | 图标 16–18px（固定） | — | — | 固定 | 固定 | 固定 | `.btn-icon` / `.md-btn-icon` | 弹窗右上角关闭 `✕` |
| B-08 | 链接式按钮 | `--spd-font-size-sm` | 600 | 1.35 | 13px | 15px | 17px | `.btn-link` / `.spd-row-action` | 『DataTable 通用表格演示』页行内『查看』『编辑』 |
| B-09 | 批次 / 下拉菜单按钮 | `--spd-font-size-sm` | 600 | 1 | 13px | 15px | 17px | `.batch-edit-menu` 内 `.btn` | 基础数据页『批量维护』下拉 |
| B-10 | 弹窗底部按钮 | `--spd-font-size-base` | 600 | 1 | 14px | 16px | 18px | `.md-dialog-footer .md-btn` | 弹窗底部『取消』『保存』 |
| B-11 | 驾驶舱面板按钮 | `--spd-font-size-sm` | 700 | 1 | 13px | 15px | 17px | `.alert-panel > header button` | 『操作驾驶舱』页『查看全部』 |

**整改说明**：B-01 与 B-04 已跟随档位 ✅ **符合标准**，仅需令牌化（`styles/main.css` 的 `--btn-font-size` 改为 `var(--spd-font-size-base)`；`styles/list/unified-list.css` 第 331–343 行）；**B-03（13px 写死）与 B-06（12px 写死）属 D-13 —— `.btn` 字号三套**；B-09（13px）与 B-11（12px）统一到 `--spd-font-size-sm`。改动文件：`styles/main.css`、`styles/master-data.theme.css`、`styles/list/unified-list.css`、`styles/components/buttons.css`、`styles/spd-override.css`、`views/operations/OperationCockpitView.vue`。
> **按钮三套字号的最终裁定**：主按钮族 **14px**（令牌 `--spd-font-size-base`），小号按钮族 **13px**（令牌 `--spd-font-size-sm`）。**12px 从按钮体系中彻底移除** —— 12px 只保留给角标与徽标（G-8 / G-10 规则）。

---

### 4.9 浮层（弹窗 / 消息 / tooltip / 列设置）

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| O-01 | Element Plus 弹窗标题 | `--spd-font-size-2xl` | 600 | 1.3 | 20px | 22px | 24px | `.el-dialog__title` | 任意 el-dialog 标题 |
| O-02 | 自绘弹窗标题 | `--spd-font-size-2xl` | 600 | 1.3 | 20px | 22px | 24px | `.modal-heading h3` | 供应商 / 厂家自绘弹窗标题 |
| O-03 | md-* 弹窗标题 | `--spd-font-size-2xl` | 600 | 1.4 | 20px | 22px | 24px | `.md-dialog-header h3` | md-* 弹窗标题 |
| O-04 | 弹窗副标题 | `--spd-font-size-sm` | 400 | 1.4 | 13px | 15px | 17px | `.modal-heading p` / `.md-dialog-header p` | 弹窗标题下的『待审批目录』 |
| O-05 | 确认框标题 | `--spd-font-size-2xl` | 600 | 1.5 | 20px | 22px | 24px | `.el-message-box__title` | 删除确认弹窗标题 |
| O-06 | 确认框正文 | `--spd-font-size-base` | 400 | 1.65 | 14px | 16px | 18px | `.el-message-box__message` | 确认框正文段落 |
| O-07 | 下拉选项 | `--spd-font-size-base` | 400 | 1 | 14px | 16px | 18px | `.el-select-dropdown__item` | 任意下拉菜单项 |
| O-08 | Tooltip | `--spd-font-size-sm` | 400 | 1.5 | 13px | 15px | 17px | Element Plus 默认 | 表格单元格悬浮提示 |
| O-09 | 列设置浮层标题 | `--spd-font-size-base` | 700 | 1.4 | 14px | 16px | 18px | `.ulc-col-settings-pop header strong` | 列设置浮层『显示列设置』 |
| O-10 | 列设置浮层链接 | `--spd-font-size-sm` | 700 | 1.4 | 13px | 15px | 17px | `.ulc-col-settings-pop .ulc-link` | 浮层右上『重置』 |
| O-11 | 列设置浮层列项 | `--spd-font-size-base` | 400 | 1.4 | 14px | 16px | 18px | `.ulc-col-settings-item` | 浮层内可勾选列名 |
| O-12 | 列设置浮层宽度标注 | `--spd-font-size-xs` | 400 | 1.4 | 11px | 13px | 15px | `.ulc-col-settings-item small` | 列名右侧『120px』 |
| O-13 | 全局消息提示 | `--spd-font-size-sm` | 400 | 1.5 | 13px | 15px | 17px | `.md-message` / `.el-message` | 顶部『保存成功』提示条 |
| O-14 | 错误提示条 | `--spd-font-size-sm` | 400 | 1.5 | 13px | 15px | 17px | `.md-error` | 表单校验失败条 |
| O-15 | 科室申领弹窗标题 | `--spd-font-size-2xl` | 600 | 1.3 | 20px | 22px | 24px | `.dept-req-dialog-title > span` | 『新增科室申领』弹窗标题 |
| O-16 | 列设置浮层底部按钮 | `--spd-font-size-sm` | 600 | 1 | 13px | 15px | 17px | `.ulc-col-settings-pop footer .ulc-action` | 浮层底部『全选』『取消』 |

**整改说明**：O-01（16px `!important` 写死）、O-02 / O-03（16px 写死）统一为 `--spd-font-size-2xl`；O-04（12px / 12.5px 两套）统一为 `--spd-font-size-sm`；O-06（14px 写死，行高 1.7）统一为 `--spd-font-size-base` + `--spd-lh-loose`；O-07（13px 写死）统一为 `--spd-font-size-base`；O-08（12px 默认）统一为 `--spd-font-size-sm`；O-12（12px 写死）统一为 `--spd-font-size-xs`；O-15（17px 写死）统一为 `--spd-font-size-2xl`。改动文件：`styles/spd-override.css`、`styles/components/modal.css`、`styles/master-data.theme.css`、`styles/list/unified-list.css`（第 605 行）、`views/supply-chain/DepartmentRequisitionView.vue`。
---

### 4.10 统计与看板

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M-01 | 统计数值（标准页） | `--spd-font-size-2xl` | 800 | 1.1 | 20px | 22px | 24px | `.md-stat-value` | 基础数据页统计卡『医院商品总数』数值 |
| M-02 | 统计标签 | `--spd-font-size-sm` | 500 | 1.4 | 13px | 15px | 17px | `.md-stat-label` | 同上数值下方标签 |
| M-03 | 统计单位 / 副标 | `--spd-font-size-xs` | 500 | 1.4 | 11px | 13px | 15px | `.md-stat-value small` | 同上数值右侧单位 |
| M-04 | `spd-stat-value`（Ant 体系） | `--spd-font-size-3xl` | 800 | 1 | 24px | 26px | 28px | `.spd-stat-value` | spd-theme 统计卡数值 |
| M-05 | `metric-panel` 数值 | `--spd-font-size-2xl` | 700 | 1.2 | 20px | 22px | 24px | `.metric-panel .metric-value` | 审批页指标面板数值 |
| M-06 | `metric-panel` 标签 | `--spd-font-size-sm` | 400 | 1.4 | 13px | 15px | 17px | `.metric-panel .metric-label` | 同上标签 |
| M-07 | 驾驶舱 KPI 数值（允许突破） | `--spd-font-size-5xl` | 800 | 1.1 | 38px | 40px | 42px | `.kpi-body strong` | 『操作驾驶舱』页 KPI 数值 |
| M-08 | 驾驶舱 KPI 标签 | `--spd-font-size-sm` | 400 | 1.4 | 13px | 15px | 17px | `.kpi-body p` | 同上 KPI 名称 |
| M-09 | 驾驶舱 KPI 环比 | `--spd-font-size-xs` | 400 | 1.4 | 11px | 13px | 15px | `.kpi-body small` | 同上环比文字 |
| M-10 | 驾驶舱 6 宫格面板标题 | `--spd-font-size-lg` | 700 | 1.4 | 16px | 18px | 20px | `.cockpit-panel h2` | 『操作驾驶舱』页『耗材消费构成』 |
| M-11 | 驾驶舱环形图中心值 | `--spd-font-size-2xl` | 800 | 1 | 20px | 22px | 24px | `.category-donut strong` | 同上环形图中心百分比 |
| M-12 | 驾驶舱图例标签 | `--spd-font-size-sm` | 400 | 1.4 | 13px | 15px | 17px | `.category-legend .legend-label` | 同上右侧图例『高值耗材』 |
| M-13 | 驾驶舱图例数值 | `--spd-font-size-base` | 700 | 1.2 | 14px | 16px | 18px | `.category-legend strong` | 同上图例右侧数值 |
| M-14 | 驾驶舱时钟 | `--spd-font-size-4xl` | 700 | 1 | 30px | 32px | 34px | `.cockpit-clock strong` | 『操作驾驶舱』页右上角时钟 |
| M-15 | 驾驶舱标题（允许突破） | `--spd-font-size-5xl` | 800 | 1.1 | 38px | 40px | 42px | `.cockpit-title h1` | 同上页主标题 |
| M-16 | 待审批 KPI 卡数值 | `--spd-font-size-2xl` | 800 | 1.2 | 20px | 22px | 24px | `.approval-metric-card` | 『待审批目录』页 KPI 数值 |
| M-17 | 进度条文字 | `--spd-font-size-sm` | 400 | 1.4 | 13px | 15px | 17px | `.el-progress-text` | 审批进度条内百分比 |
| M-18 | 驾驶舱趋势轴标签 | `--spd-font-size-xs` | 400 | 1 | 11px | 13px | 15px | `.trend-labels` | 驾驶舱手写 SVG 趋势图下方月份标签 |
| M-19 | 驾驶舱口径说明 | `--spd-font-size-xs` | 400 | 1.4 | 11px | 13px | 15px | `.caliber-note` | 驾驶舱面板底部『口径：高值耗材…』 |

**整改说明**：M-01（30px 写死）、M-05（24px 写死）、M-11（22px 写死）、M-16（24px 写死）统一为 `--spd-font-size-2xl`（20px）；M-04（38px 写死）统一为 `--spd-font-size-3xl`（24px）；M-07（32px 写死）、M-14（30px 写死）、M-15 属驾驶舱展示型字号，**允许突破**并统一为 `--spd-font-size-5xl` / `-4xl`；M-13（15px 写死）统一为 `--spd-font-size-base`；**M-18（10px，`OperationCockpitView.vue` 第 722 行）属 D-05，改为 `--spd-font-size-xs`（11px）**；M-19（11px，第 880 行）令牌化。改动文件：`styles/master-data.theme.css`、`styles/spd-theme.css`、`styles/medical-saas.css`、`views/operations/OperationCockpitView.vue`。
> **Q-06 落地说明**：驾驶舱的**展示型大字号**（M-07 38px、M-15 38px、M-14 30px）**保持并纳入三档**，因为该页是演示/监控场景。但 **10px 类微型标签必须提升到 12px 下限**。M-18 的 `.trend-labels` 当前 10px —— 提升到 `--spd-font-size-xs`（11px）。**11px 是下限允许的角标层级**；若该标签承载需要用户判断的数值口径含义，则进一步提升到 `--spd-font-size-sm`（13px）。本裁定取前者：`.trend-labels` 与 `.caliber-note` 属于刻度与口径注释，11px 合法。

---

### 4.11 工作台模块

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| W-01 | 模块卡片标题 | `--spd-font-size-lg` | 700 | 1.3 | 16px | 18px | 20px | `.module-card h3` | 工作台模块卡标题 |
| W-02 | 模块卡片描述 | `--spd-font-size-sm` | 400 | 1.65 | 13px | 15px | 17px | `.module-card p` | 同上描述 |
| W-03 | 流程条节点 | `--spd-font-size-base` | 400 | 1.35 | 14px | 16px | 18px | `.flow-line span` | 审批详情页流程条节点 |
| W-04 | 特性行文字 | `--spd-font-size-base` | 400 | 1.5 | 14px | 16px | 18px | `.feature-row` | 工作台功能入口行 |
| W-05 | 待办列表标题 | `--spd-font-size-sm` | 500 | 1.4 | 13px | 15px | 17px | `.fli-todo-title` | 工作台待办列表标题 |
| W-06 | 待办单号 | `--spd-font-size-xs` | 400 | 1.4 | 11px | 13px | 15px | `.fli-todo-id` | 同上单号 |
| W-07 | 审批流节点标题 | `--spd-font-size-lg` | 600 | 1.4 | 16px | 18px | 20px | `.spd-approval-flow-node` | 『审批流设置』页节点名 |
| W-08 | 审批流节点说明 | `--spd-font-size-sm` | 400 | 1.4 | 13px | 15px | 17px | 同上 | 同上说明文字 |
| W-09 | 工作台待办计数 | `--spd-font-size-xs` | 600 | 1 | 11px | 13px | 15px | `.md-tab .md-tab-count` | 工作台待办 Tab 数字角标 |

**整改说明**：W-01（17px 写死）统一为 `--spd-font-size-lg`；W-02（14px 写死，行高 1.6）统一为 `--spd-font-size-sm` + `--spd-lh-loose`（按 **D-14** 收敛行高）；W-06（11px 写死）令牌化。改动文件：`styles/main.css`。
---

### 4.12 结算对账专用

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| L-01 | 结算汇总条 | `--spd-font-size-base` | 800 | 1.4 | 14px | 16px | 18px | `.settlement-table-summary` | 『结算对账』页明细表上方汇总条 |
| L-02 | 结算汇总提示 | `--spd-font-size-sm` | 500 | 1.4 | 13px | 15px | 17px | `.settlement-table-hint` | 同上汇总条右侧说明 |
| L-03 | 结算明细表头 | `--spd-font-size-table-head` | 700 | 1.35 | 14px | 16px | 18px | `.settlement-detail-table thead th` | 同上表头『结算单号』『科室』 |
| L-04 | 结算明细主字段 | `--spd-font-size-base` | 800 | 1.4 | 14px | 16px | 18px | `.settlement-cell-stack > strong` | 同上『聚维酮碘消毒剂…』 |
| L-05 | 结算明细元信息 | `--spd-font-size-xs` | 400 | 1.4 | 11px | 13px | 15px | `.settlement-cell-meta` | 同上『编码：』『注册证：』 |
| L-06 | 结算金额 | `--spd-font-size-base` | 800 | 1.4 | 14px | 16px | 18px | `.settlement-detail-table .amount-cell` | 同上『结算金额』列 |
| L-07 | 结算编码列表（等宽） | `--spd-font-size-xs` | 400 | 1.4 | 11px | 13px | 15px | `.settlement-code-list` | 同上『UID / 唯一码』列 |
| L-08 | 结算策略标记 | `--spd-font-size-xs` | 600 | 1.35 | 11px | 13px | 15px | `.settlement-policy-list b` | 同上『带量』『医保』前缀标 |
| L-09 | 结算策略说明 | `--spd-font-size-xs` | 400 | 1.4 | 11px | 13px | 15px | `.settlement-policy-list small` | 同上前缀标右侧说明 |
| L-10 | 结算确认按钮 | `--spd-font-size-base` | 800 | 1 | 14px | 16px | 18px | `.settlement-confirm-btn` | 同上『确认结算』按钮 |
| L-11 | 结算已确认标记 | `--spd-font-size-sm` | 700 | 1.35 | 13px | 15px | 17px | `.settlement-confirmed-text` | 同上『已确认』文字 |
| L-12 | 结算数值单元格 | `--spd-font-size-base` | 400 | 1.35 | 14px | 16px | 18px | `.settlement-detail-table .numeric-cell` | 同上数量列 |

**整改说明**：L-01（13px 写死）统一为 `--spd-font-size-base`；**L-03（12px 写死）属 D-09，统一为 `--spd-font-size-table-head`**；**L-05（12px，调研稿记 11.5px，实测 `main.css` 第 1715–1718 行为 12px）、L-07（12px）、L-08（11px）、L-09（11px）属 D-15，统一为 `--spd-font-size-xs`（11px）**；L-11（12px 写死）统一为 `--spd-font-size-sm`。改动文件：`styles/main.css`（第 1496、1546–1552、1715、1727–1731、1734–1738、1742、1781 行）。
> **结算明细表是全站最密集的表格**（`min-width: 2780px`，横向滚动）。整改后表头 14px、数据 14px，密度仍在可接受范围；元信息与编码列表降到 11px 属于角标层级，合法。

---

### 4.13 库存专用

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| I-01 | 盘点范围标题 | `--spd-font-size-base` | 700 | 1.35 | 14px | 16px | 18px | `.stocktaking-scope-head strong` | 『库存盘点』弹窗『盘点范围』标题 |
| I-02 | 盘点范围说明 | `--spd-font-size-sm` | 400 | 1.4 | 13px | 15px | 17px | `.stocktaking-scope-head span` | 同上说明 |
| I-03 | 盘点已选提示 | `--spd-font-size-sm` | 400 | 1.4 | 13px | 15px | 17px | `.stocktaking-scope-selected` | 同上已选范围 |
| I-04 | 盘点数量输入 | `--spd-font-size-base` | 400 | 1.35 | 14px | 16px | 18px | `.stocktaking-qty-input` | 『盘点明细』弹窗实盘数量输入框 |
| I-05 | 库位层级标签 | `--spd-font-size-sm` | 400 | 1.4 | 13px | 15px | 17px | `.md-cell-name small` | 库存页库房 / 库位层级路径 |
| I-06 | 补货分析汇总 | `--spd-font-size-base` | 700 | 1.35 | 14px | 16px | 18px | `.analysis-summary` | 『补货分析』弹窗汇总条 |
| I-07 | 拣配工作台紧凑标题 | `--spd-font-size-lg` | 700 | 1.4 | 16px | 18px | 20px | `.picking-panel .section-title.compact h3` | 『拣配记录』页左侧『拣配任务』 |
| I-08 | 拣配数量输入 | `--spd-font-size-base` | 800 | 1.35 | 14px | 16px | 18px | `.analysis-qty-input` | 拣配弹窗数量输入框 |

**整改说明**：**I-04（13px 写死）统一为 `--spd-font-size-base`（14px）**；I-05（12px）统一为 `--spd-font-size-sm`；I-08（14px ✅）令牌化。改动文件：库存盘点相关 view、拣配相关 view、`styles/master-data.theme.css`。
> I-04 数量输入框是**用户需要读取并输入判断**的元素，必须与其他输入框统一为 14px。13px 输入框在全站仅此一处，属孤例。

---

### 4.14 科室申领专用

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| D-01 | 已选清单行容器 | `--spd-font-size-base` | 400 | 1.35 | 14px | 16px | 18px | `.dept-req-selected-list li` | 『科室申领』已选清单每一行 |
| D-02 | 已选清单商品编码 | `--spd-font-size-xs` | 400 | 1.35 | 11px | 13px | 15px | `.dept-req-selected-list .selected-code` | 同上行的编码 chip |
| D-03 | 已选清单商品名称 | `--spd-font-size-base` | 600 | 1.35 | 14px | 16px | 18px | `.dept-req-selected-list .selected-name` | 同上行的商品名 |
| D-04 | 已选清单规格说明 | `--spd-font-size-sm` | 400 | 1.35 | 13px | 15px | 17px | `.dept-req-selected-list .selected-spec` | 同上行的规格 |
| D-05 | 已选清单字段标签 | `--spd-font-size-sm` | 400 | 1.35 | 13px | 15px | 17px | `.dept-req-selected-list label` | 同上行的『默认申领』标签 |
| D-06 | 已选清单下拉 | `--spd-font-size-sm` | 400 | 1.35 | 13px | 15px | 17px | `.dept-req-selected-list label select` | 同上行的『默认申领』下拉 |
| D-07 | 已选清单数量输入 | `--spd-font-size-base` | 400 | 1.35 | 14px | 16px | 18px | `.dept-req-selected-list .qty-stepper input` | 同上行的『申领数量』 |
| D-08 | 已选清单合计 | `--spd-font-size-base` | 700 | 1.35 | 14px | 16px | 18px | `.dept-req-selected-list .selected-total` | 同上行的合计金额 |

**整改说明**：**本区整体属 D-03（P0）**。D-01（容器 12px）、D-03（商品名称 12px）、D-07（数量输入 11px）统一为 `--spd-font-size-base`（14px）；D-04（规格 11px）、D-05（标签 11px）、D-06（下拉 11px）统一为 `--spd-font-size-sm`（13px）；D-02（编码 chip 11px）保留 `--spd-font-size-xs`（11px）并令牌化；D-08（合计 12px）统一为 `--spd-font-size-base` + 700。改动文件：`views/supply-chain/DepartmentRequisitionView.vue`（第 1927–1930、1941–1945、1951–1957、1960–1964、1967–1971、1976–1979、1984–1990、2009、2019 行）。
> **现状值修正**：调研稿记「已选清单全线 11px」。**实测为 11px / 12px 混合**（`li` 容器 12px、`selected-name` 12px、`selected-code` 11px、`selected-spec` 11px、`label` 11px、`label select` 11px）。整改清单按实测值记录。

---

### 4.15 登录页（独立体系，不参与档位）

登录页**维持独立体系**（Q-05），全部字号不随字号档位变化。内部只做一处层级纠正。

| # | 分类 | 标准字号 | 字重 | 行高 | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- |
| Lg-01 | 左侧主标题 | 30px | 700 | 1.32 | `.login-aside-hero h1` | 登录页左侧『让每一次耗材流转都可追溯』 |
| Lg-02 | 左侧说明段落 | 14px | 400 | 1.85 | `.login-aside-hero p` | 主标题下方说明段落 |
| Lg-03 | 左侧特性条目 | 13px | 400 | 1.4 | `.login-aside-features li` | 『高值耗材全流程追溯』等特性条目 |
| Lg-04 | 左侧统计数值 | 20px | 700 | 1.1 | `.login-aside-stats .stat strong` | 『12 万+』等统计数字 |
| Lg-05 | 左侧统计标签 | 11px | 400 | 1.4 | `.login-aside-stats .stat span` | 同上数字下方标签 |
| Lg-06 | 左侧底部版权 | 12px | 400 | 1.4 | `.login-aside-foot` | 左下角版权行 |
| Lg-07 | 品牌标识文字 | 17px | 800 | 1.2 | `.login-brand .brand-mark` | 左上角品牌方块内文字 |
| Lg-08 | 品牌名称 | 18px | 700 | 1.3 | `.login-brand .brand-text strong` | 左上角『SPD 院内供应链』 |
| Lg-09 | 品牌副标 | 12px | 400 | 1.4 | `.login-brand .brand-text small` | 品牌名下方副标题 |
| Lg-10 | 右侧面板标题 | 20px | 700 | 1.3 | `.login-panel-head h2` | 『SPD 院内供应链管理平台』 |
| Lg-11 | 右侧面板说明 | 13px | 400 | 1.7 | `.login-panel-head p` | 标题下说明 |
| **Lg-12** | **表单标签** | **13px** | 600 | 1.4 | `.login-panel label > .lbl` | 『用户名』『密码』 |
| Lg-13 | 表单输入框 | 14px | 400 | 1.4 | `.login-field input` | 用户名 / 密码输入框 |
| Lg-14 | 记住账号 / 忘记密码 | 12px | 500 / 600 | 1.4 | `.login-panel-row` / `.forgot` | 『记住账号』勾选项、『忘记密码？』 |
| Lg-15 | 登录错误提示 | 12px | 400 | 1.55 | `.login-error` | 『用户名或密码错误』 |
| Lg-16 | 登录按钮 | 14px | 600 | 1 | `.login-submit` | 『登 录』按钮 |
| Lg-17 | 面板底部说明 | 11px | 400 | 1.4 | `.login-panel-foot` | 右侧面板最底部版权 |

**整改说明**：**Lg-12（12px，小于输入框 14px）属 D-07（P0），改为 13px**。其余全部保持现状 —— 登录页不引入任何 `--spd-font-size-*` 令牌，不参与档位缩放。改动文件：`styles/views/login.css`（第 450–465 行）。
**规定**：

1. 登录页**唯一**必须整改项是 Lg-12。其余全部保持现状。
2. 登录页**不引入**任何 `--spd-font-size-*` 令牌，**不参与**档位缩放。
3. 登录页的 11px（Lg-05、Lg-17）属于统计标签与版权说明，属 G-10 允许的辅助信息，合法。

---

### 4.16 AI 助手

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A-01 | 悬浮球文字 | `--spd-font-size-lg` | 800 | 1 | 16px | 18px | 20px | `.ai-medical-fab` | 右下角『AI医护助手』悬浮球 |
| A-02 | 助手窗口标题 | `--spd-font-size-xl` | 800 | 1.1 | 18px | 20px | 22px | `.ai-medical-drag-title strong` | 助手面板左上『AI医护助手』 |
| A-03 | 助手窗口副标 | `--spd-font-size-sm` | 400 | 1.4 | 13px | 15px | 17px | `.ai-medical-drag-title span` | 同上『院内SPD智能协作』 |
| A-04 | 欢迎语 | `--spd-font-size-base` | 700 | 1.4 | 14px | 16px | 18px | `.ai-medical-welcome strong` | 『您好，我是您的AI医护助手』 |
| A-05 | 快捷问题按钮 | `--spd-font-size-sm` | 700 | 1.4 | 13px | 15px | 17px | `.ai-medical-quick-grid button` | 『查库存』『查消耗』等快捷入口 |
| A-06 | 用户气泡文字 | `--spd-font-size-base` | 400 | 1.5 | 14px | 16px | 18px | `.ai-medical-user-bubble p` | 右侧用户提问气泡 |
| A-07 | 助手回复正文 | `--spd-font-size-base` | 400 | 1.65 | 14px | 16px | 18px | `.ai-medical-answer p` / `.ai-response-text` | 左侧助手回复段落 |
| A-08 | 回复列表项 | `--spd-font-size-sm` | 400 | 1.5 | 13px | 15px | 17px | `.ai-medical-answer li` | 回复中的分点说明 |
| A-09 | 消息时间戳 | `--spd-font-size-xs` | 400 | 1.4 | 11px | 13px | 15px | `.ai-medical-message time` | 气泡下方时间 |
| A-10 | 输入框文字 | `--spd-font-size-base` | 400 | 1.55 | 14px | 16px | 18px | `.ai-medical-input-wrap textarea` | 助手底部输入框 |
| A-11 | 输入框占位符 | 继承控件字号 | 400 | 1.55 | 随控件 | 随控件 | 随控件 | `::placeholder` | 『输入SPD业务问题，或上传耗材图片进行识别…』 |
| **A-12** | **输入框下方免责说明** | `--spd-font-size-xs` | 400 | 1.4 | 11px | 13px | 15px | `.ai-medical-composer small` | 『AI可识别图片及解答流程；实时业务数据请通过业务页面查询』 |

**整改说明**：A-01（17px 写死）统一为 `--spd-font-size-lg`；A-02（21px 写死）统一为 `--spd-font-size-xl`；**A-04（15px 写死）与 A-06（13px 写死）统一为 `--spd-font-size-base`（14px）**；A-07（13px 写死，行高 1.65 ✅）统一为 `--spd-font-size-base`；A-08（12px 写死）统一为 `--spd-font-size-sm`；A-10（14px 写死）令牌化；**A-12（10px，第 1258–1265 行）属 D-04（P0），改为 `--spd-font-size-xs`（11px）—— 它是全站唯一低于下限的字号**。改动文件：`components/common/AiMedicalAssistant.vue`。
> A-12 免责声明属 G-10 允许的辅助信息类别，11px 合法。但 **10px 必须在本次整改中消除**，它是全站唯一低于下限的字号。

---

### 4.17 空态 / 加载 / 错误

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| E-01 | 统一列表空态 | `--spd-font-size-base` | 400 | 1.5 | 14px | 16px | 18px | `.ulc-table .el-table__empty-text` | 任意列表『暂无数据』 |
| E-02 | 空态标题 | `--spd-font-size-base` | 600 | 1.4 | 14px | 16px | 18px | `.md-empty strong` | md-* 空态组件标题 |
| E-03 | 空态说明 | `--spd-font-size-sm` | 400 | 1.4 | 13px | 15px | 17px | `.md-empty p` | 同上说明文字 |
| E-04 | 加载提示 | `--spd-font-size-base` | 400 | 1.5 | 14px | 16px | 18px | `.md-loading` | 加载中提示 |
| E-05 | 原生空态 | `--spd-font-size-base` | 400 | 1.5 | 14px | 16px | 18px | `.spd-data-table__empty-text` | 『DataTable 通用表格演示』页空态 |
| E-06 | 标准空态文案 | `--spd-font-size-base` | 400 | 1.5 | 14px | 16px | 18px | `.standard-empty-state` | 页面级空态 |
| E-07 | 打印模板空态 | `--spd-font-size-base` | 400 | 1.5 | 14px | 16px | 18px | `.md-print-template-field-empty` | 『打印模板设置』页字段区空态 |

**整改说明**：E-01（14px 写死，不跟随档位）统一为 `--spd-font-size-base`；E-03（12px 写死）统一为 `--spd-font-size-sm`；E-07（13px 写死）统一为 `--spd-font-size-base`。改动文件：`styles/list/unified-list.css`、`styles/master-data.theme.css`。
---

### 4.18 打印模板（边界原则，不展开）

打印到纸面的真实排版由后端模板定义，**不受本规范约束**。本节只规定**边界**：

| 规则 | 内容 |
| --- | --- |
| PR-1 | 『打印模板设置』页的**配置界面**（字段列表、模板结构预览）**受本规范约束**，按 4.4 的 T-21 / T-22 执行 |
| PR-2 | 真正打印到纸上的**内容排版**（纸张尺寸、字号、页边距、分页规则）**另立《打印排版规范》**，在下一份文档中定义 |
| PR-3 | 在《打印排版规范》发布前，打印模板内容的字号**保持现状**，不做任何改动 |
| PR-4 | 本规范的任何条款**不得**被用于解释或修改后端打印模板的字号 |
| PR-5 | 打印模板配置的**字段值**（在配置界面中预览的模板字段文本）跟随屏幕规范，**不参与**档位缩放（预览态固定 standard） |

**当前打印模板配置界面的现状值**（作为 PR-1 的整改基线）：

| 项 | 现状 | 目标 | 类名 |
| --- | --- | --- | --- |
| 字段表数据 | 13px | 14px | `.md-print-template-table tbody td` |
| 字段表表头 | 12px | 14px | `.md-print-template-table thead th` |
| 字段值 | 12px | 14px | `.md-print-template-field-value` |
| 字段区空态 | 13px | 14px | `.md-print-template-field-empty` |

---

### 4.19 全局框架中的其它固定元素

| # | 分类 | 标准 | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- |
| X-01 | 加载动画 / 骨架屏 | 不使用文字；如需文字用 `--spd-font-size-sm` | `.el-loading-text` | 表格加载态 |
| X-02 | 分隔线 / 装饰条 | 不含文字 | — | — |
| X-03 | 滚动条 | 不含文字 | — | — |
| X-04 | Tooltip 箭头 | 不含文字 | — | — |

---

## 5. 图表字体专项

### 5.1 问题定性

ECharts 的 `fontSize` 是 **canvas 像素值**，**不接受 CSS `var()` 表达式**。因此图表无法直接消费 `--spd-font-size-*`，必须：

1. 建立一组**独立的图表字号令牌**，其取值用 `calc()` 从 `--spd-fs-base` 推导（在 CSS 中有效，可被 JS 读取）；
2. 在 JS 中通过 `getComputedStyle` 读取这些令牌的实际像素值，喂给 ECharts；
3. 在字号档位变化时**重新读取并重绘**。

这是全站**唯一**必须走 JS 通道的字号体系。

### 5.2 现状（实测源文件）

`frontend/src/components/business/TrendChart.vue` 第 17–21 行：

```js
tooltip: { trigger: 'axis', renderMode: 'richText' },
legend: { top: 0, right: 0, icon: 'roundRect', itemWidth: 9, itemHeight: 5, textStyle: { fontSize: 11, color: '#73838c' } },
xAxis: { type: 'category', data: props.labels, axisTick: { show: false }, axisLine: { lineStyle: { color: '#e6ecef' } }, axisLabel: { color: '#87969e', fontSize: 10 } },
yAxis: { type: 'value', name: props.unit, nameTextStyle: { color: '#87969e', fontSize: 10 }, splitLine: { lineStyle: { color: '#edf1f3', type: 'dashed' } }, axisLabel: { color: '#87969e', fontSize: 10 } },
```

| 图表元素 | 现状 | 是否跟随档位 | 定义位置 |
| --- | --- | --- | --- |
| X 轴刻度 | **10px** | ❌ 写死在 JS | `TrendChart.vue` `xAxis.axisLabel.fontSize` |
| Y 轴刻度 | **10px** | ❌ 写死在 JS | `TrendChart.vue` `yAxis.axisLabel.fontSize` |
| Y 轴名称 | **10px** | ❌ 写死在 JS | `TrendChart.vue` `yAxis.nameTextStyle.fontSize` |
| 图例 | **11px** | ❌ 写死在 JS | `TrendChart.vue` `legend.textStyle.fontSize` |
| Tooltip | 12px（ECharts 默认） | ❌ 未显式设置 | 未显式设置 |
| 数据标签 | 未启用 | — | — |
| 图表标题 | 未启用（靠外部 HTML 标题） | — | — |

**缺陷定性**：

1. **10px 低于全站 12px 下限**，且坐标轴刻度是用户判断数值趋势的**依据**，属 G-9 违规。
2. **完全不参与三档缩放**。用户切到「特大」档位后，图表仍是 10px，而周边表格已放大到 18px —— 图表成为页面中唯一不变的区域，形成严重尺度断裂。
3. 全站只有一个 ECharts 组件、一处配置，**整改成本极低、收益极高**。

### 5.3 `--spd-chart-font-*` 完整取值表

定义位置：`frontend/src/styles/tokens/typography.css`。

| 令牌 | 表达式 | standard (14) | large (16) | xlarge (18) | 用途 |
| --- | --- | --- | --- | --- | --- |
| `--spd-chart-font-xs` | `calc(var(--spd-fs-base) - 3px)` | 11px | 13px | 15px | 图表微型角标（预留，当前不使用） |
| `--spd-chart-font-sm` | `calc(var(--spd-fs-base) - 2px)` | 12px | 14px | 16px | **坐标轴刻度、X/Y 轴名称** |
| `--spd-chart-font-base` | `calc(var(--spd-fs-base) - 1px)` | 13px | 15px | 17px | **图例文字** |
| `--spd-chart-font-lg` | `var(--spd-font-size-base)` | 14px | 16px | 18px | **数据标签、Tooltip 标题** |
| `--spd-chart-font-xl` | `calc(var(--spd-fs-base) + 1px)` | 15px | 17px | 19px | **Tooltip 正文** |

### 5.4 各图表元素标准值

| 图表元素 | 标准令牌 | standard | large | xlarge | 参考元素 |
| --- | --- | --- | --- | --- | --- |
| X 轴刻度标签 | `--spd-chart-font-sm` | 12px | 14px | 16px | 工作台『采购趋势』图 X 轴月份标签 |
| Y 轴刻度标签 | `--spd-chart-font-sm` | 12px | 14px | 16px | 同图 Y 轴数值刻度 |
| Y 轴名称（单位） | `--spd-chart-font-sm` | 12px | 14px | 16px | 同图 Y 轴右上角『万元』 |
| X 轴名称 | `--spd-chart-font-sm` | 12px | 14px | 16px | 同图 X 轴标题（若启用） |
| 图例文字 | `--spd-chart-font-base` | 13px | 15px | 17px | 同图右上角图例『采购金额』 |
| 数据标签 | `--spd-chart-font-lg` | 14px | 16px | 18px | 柱顶 / 点旁的数值标注（若启用） |
| Tooltip 标题 | `--spd-chart-font-lg` | 14px | 16px | 18px | 悬浮提示的轴名行 |
| Tooltip 正文 | `--spd-chart-font-xl` | 15px | 17px | 19px | 悬浮提示的数值行 |
| 图表标题 | 不使用（由外部 HTML 标题承担） | — | — | — | 图表卡片标题按 P-04 |

### 5.5 与正文的层级关系

**规定**：图表字号比同页正文**轻一档**，差值 **≥ 1px**（standard 档：正文 14px，图表轴 12px，差 2px）。

| 层级 | standard | 说明 |
| --- | --- | --- |
| 同页正文 / 表格数据 | 14px | — |
| 图表图例 | 13px | 轻 1 档 |
| 图表轴刻度 / 轴名称 | 12px | 轻 2 档 |
| 图表 Tooltip 正文 | 15px | 重 1 档（浮层需要更高可读性） |

图表 Tooltip 是浮层，其字号**可以**大于正文，但**不得**大于同页弹窗标题（`--spd-font-size-2xl`，20px）。

### 5.6 `TrendChart.vue` 落地要求

**规定**：

1. **严禁**在图表 JS 中继续写死 `fontSize: 10` / `11` / 任何裸数字。
2. 组件必须在 `<script setup>` 中通过 `getComputedStyle(document.documentElement)` 读取六个图表令牌的实际像素值（`parseFloat`）。
3. 必须**订阅字号档位变化**并重绘。订阅方式二选一：
   - 读取 `stores/theme.ts` 的字号档位状态（`watch`），或
   - 使用 `MutationObserver` 监听 `<html>` 的 `data-font-size` 属性变化。
4. `setOption(option, true)` 必须在档位变化后**重新调用**，用新的 `fontSize` 覆盖。
5. `aria.enabled` 保持开启，保证读屏可用。

**参考实现骨架**（**仅示意，实际实现由前端按仓库既有模式编写**）：

```js
// 伪代码 —— 说明读取与订阅机制，不是可直接粘贴的代码
const CHART_TOKENS = [
  '--spd-chart-font-xs',
  '--spd-chart-font-sm',
  '--spd-chart-font-base',
  '--spd-chart-font-lg',
  '--spd-chart-font-xl'
]

function readChartFonts() {
  const cs = getComputedStyle(document.documentElement)
  const out = {}
  for (const t of CHART_TOKENS) out[t] = parseFloat(cs.getPropertyValue(t)) || 12
  return out
}

// 订阅档位变化 → readChartFonts() → setOption(option, true)
```

### 5.7 新增图表组件的规定

1. 任何新增 ECharts 组件**必须**复用 `--spd-chart-font-*`，**禁止**自建数字。
2. 若未来新增 SVG 手写图表（如驾驶舱 `.trend-chart svg`），其刻度文字**必须**按 HTML 文字处理，使用 `--spd-font-size-xs`（11px），并遵守 G-9（≥ 12px 时改用 `--spd-font-size-sm`）。
3. 图表相关的所有 JS 字号常量必须集中在一个模块内，禁止分散在各个组件的 `setOption` 里。

---

## 6. 字号层级与视觉优先级模型

### 6.1 层级阶梯（standard 档）

```
展示层  38px        驾驶舱 KPI、驾驶舱主标题        ┐ 极少量，每屏 ≤ 3 处
                                                          ┘
标题层  20 – 24px   弹窗标题、页面主标题、统计数值   ┐
                                                          ┘
强调层  16px        区块标题、卡片标题               ┐
                                                          │ 高频区，等大降噪
操作层  14px        表格数据、状态条、按钮、表头     │
                                                          ┘
数据层  13px        次级说明、筛选标签、表单标签     ┐
                                                          ┘
辅助层  11px        角标、计数徽标、免责说明         ┐  仅限纯辅助信息
                                                          ┘
```

**12px 是硬下限**：12px **不是**一个独立的层级，而是「辅助层」在 standard 档的**绝对下限值**。任何承载业务判断的语义信息，最小字号是 12px。

### 6.2 硬约束规则

| # | 规则 | 判定方法 |
| --- | --- | --- |
| L-1 | 同一区域内的字号**不超过 2 档** | 区域内所有可见文字的字号去重后，档位跨度 ≤ 2 |
| L-2 | **表格数据字号 = 表格表头字号**（同档），靠字重 700/400 + 底色区分 | 见 6.3 说明 |
| L-3 | **筛选标签字号 ≥ 输入控件字号 − 1px**，且 ≤ 控件字号 + 1px | 标签 13px / 控件 14px ✅ |
| L-4 | 同一页面内，标题类字号与正文字号的比值 **≤ 2 倍** | 页面主标题 24px / 正文 13px ≈ 1.85 ✅ |
| L-5 | 相邻层级最小差值 **1px**；正文与次级说明最小差值 **2px**（standard 档 14px / 13px 为 1px，因两者靠字重与颜色区分，不属「正文与次级说明」对） | 见 6.4 |
| L-6 | **11px 及以下**仅允许用于：角标、计数徽标、免责/版权、纯装饰标签、刻度标注、编码 chip。**禁止**用于需要用户读取判断的业务信息 | 白名单制，超出白名单即违规 |
| L-7 | 状态语义信息（启用/停用、待审核、通过/驳回）**不得小于 12px**；全站统一为 14px | S-01 / S-04 |
| L-8 | 数值型单元格与相邻数值列必须使用**同一字号 + `tabular-nums`** | 2.4 节 |
| L-9 | 图表字号比同页正文**轻一档**（≥ 1px 差值） | 5.5 节 |
| L-10 | 同一语义（如「商品名称」列、状态条）**跨页面字号必须一致** | 4.4 / 4.5 节 |
| L-11 | 标签字号**不得小于**其对应控件的字号 | 登录页 Lg-12 是唯一违规项 |
| L-12 | 字重跨度在同一字号内**不超过 2 档** | 例：表头 700 / 数据 400 = 3 档但属不同字号层级，不受本条约束 |

### 6.3 「表格数据 14px 与表头 14px 同档」不违反 L-1 / L-2 的说明

本规范将表格表头与表格数据定为**同一字号档位**（14px），这**不**违反 L-1「同一区域内字号不超过 2 档」与 L-2「表格数据字号 = 表头字号」，原因如下：

| 论点 | 说明 |
| --- | --- |
| L-1 的档位含义 | L-1 限制的是**视觉层级数量**，目的是避免一屏内出现 5–6 种字号造成杂乱。表头与数据**同档**意味着该区域的字号种类**减少**而非增加。表格区域实际字号集合 = { 14px（表头+数据+主字段+编码+时间）, 13px（次级说明）, 11px（角标） } = **3 档**，其中主档占绝对多数，视觉重心唯一。 |
| L-2 的本意 | L-2 的本意是「数据不得大于表头」（列名与内容的层级关系）。本规范取**数据 = 表头**，是最保守的取值，严格满足「数据不大于表头」。 |
| 层级如何表达 | 表头与数据的区分**不依赖字号**，而依赖三个正交维度：<br>① **字重**：表头 700 / 数据 400<br>② **底色**：表头带底色（统一列表为主色实底或浅墨底 + 加重横线，取决于主题方案）/ 数据行透明或斑马纹<br>③ **对齐与字距**：表头独立 sticky 行、字距 `0.02em` |
| 语义后果 | 表头是**列名**，属于「结构性信息」，不需要比内容更大；数据是**内容**，是需要用户实际读取的信息。两者等大但表头更重、更实，视觉层级依然清晰。 |
| 视力可达性 | 目标用户含轻度老花用户。若表头保持 14px、数据放大到 15px，数据虽大但列名不变小，扫读时列名反而成为瓶颈。等大 + 加重是更优解。 |
| 与 Q-02 的一致性 | Q-02 裁定「表头跟随档位放大」。本表头用 `--spd-font-size-table-head`（= base），数据用 `--spd-font-size-base`。两者三档同步变化，保持等大关系在每一档都成立。 |

### 6.4 相邻层级最小差值

| 相邻对 | standard 档取值 | 差值 | 是否合规 |
| --- | --- | --- | --- |
| 页面主标题 24px → 区块标题 16px | 8px | ✅ 远大于 1px |
| 区块标题 16px → 表格表头 14px | 2px | ✅ |
| 表格表头 14px → 表格数据 14px | 0px | ✅ 同档，靠字重+底色区分（见 6.3） |
| 表格数据 14px → 次级说明 13px | 1px | ✅ 同区域相邻层级，靠字重 400 vs 500 与颜色区分 |
| 表格数据 14px → 角标 11px | 3px | ✅ |
| 输入控件 14px → 筛选标签 13px | 1px | ✅（L-3 满足） |
| 弹窗标题 20px → 弹窗正文 14px | 6px | ✅ |

**规定**：当两个相邻层级差值为 0 或 1px 时，**必须**用字重（≥ 100 的差）或颜色（≥ 一个色阶的明度差）提供额外区分，否则视为层级不可辨。

### 6.5 字号与颜色的搭配

| 场景 | 字号 | 字重 | 颜色 | 底色 |
| --- | --- | --- | --- | --- |
| 表格表头 | 14px | 700 | 主色深或墨色 | 有底色（统一列表主色实底 / 静墨宣纸浅墨底 + 加重横线） |
| 表格数据 | 14px | 400 | 正文色 | 无底色或极淡斑马纹 |
| 表格主字段 | 14px | 700 | 标题色 | 同数据行 |
| 表格次级说明 | 13px | 500 | 次级文字色 | 同数据行 |
| 状态条 | 14px | 600 | 语义深色 | 语义色 0.14–0.4 透明度底 + 1px 边框 |
| 筛选标签 | 13px | 600 | 标签色 | 无 |
| 输入控件文字 | 14px | 400 | 正文色 | 控件底 |
| 辅助角标 | 11px | 600 | 弱化色 | 无 / chip 底 |

**规定**：**禁止**出现「小字号 + 低对比度 + 细字重」的组合。任何 11px 文字必须至少满足 400 字重与正文色系可达对比度。

### 6.6 12px 下限的使用边界

**允许使用 12px 的位置（白名单）**：

| 类别 | 实例 |
| --- | --- |
| 纯计数徽标 | `.md-tab-count`、`.spd-mini-nav-group-count` |
| 编码 chip | `.dept-req-selected-list .selected-code` |
| 免责 / 版权 | `.login-panel-foot`、`.fli-footer` |
| 图表刻度（若不使用 `--spd-chart-font-*` 的 HTML 方案） | SVG 手写图表刻度 |
| 装饰性标签 | `.spd-tag` 的装饰变体 |

**禁止使用 12px（或更小）的位置**：

| 禁止对象 | 原因 |
| --- | --- |
| 表格数据 | 必须 14px |
| 表格表头 | 必须 14px（`--spd-font-size-table-head`） |
| 筛选标签 | 必须 13px，且 ≥ 控件 − 1px |
| 输入框内文字 | 必须 14px |
| 表单字段标签 | 必须 13px |
| 状态条 / 状态徽章 | 必须 14px |
| 按钮文字 | 必须 13px 或 14px |
| 结算策略标记、结算金额、结算单号 | 属核对依据，必须 ≥ 11px 且金额 14px |
| 图表坐标轴刻度 | 必须 12px（`--spd-chart-font-sm`） |
| 需要用户据此做业务判断的任何文字 | 违反 G-9 |

---

## 7. 整改清单

> **优先级**：**P0** = 违反 12px 硬下限或核心信息可读性受损，必须整改；**P1** = 跨页面一致性，影响「同一语义同一字号」，应整改；**P2** = 令牌化与长尾清理，可排期。
>
> 共 **20 条**（原调研稿 15 项不一致 D-01 ~ D-15，本次定稿新增 5 条 D-16 ~ D-20，覆盖字重、字体栈、迷你侧栏、非整数字号与 `MdPageHeader` 接口清理）。

| # | 问题 | 现状值 | 目标值 | 涉及文件（精确到类名） | 优先级 | 预估影响面 |
| --- | --- | --- | --- | --- | --- | --- |
| **D-01** | **图表坐标轴字号写死 10px 且不参与档位** | 轴标签 10px、图例 11px（`TrendChart.vue` 第 18、20、21 行） | 轴刻度 / 轴名称 `--spd-chart-font-sm`（12/14/16px）；图例 `--spd-chart-font-base`（13/15/17px）；Tooltip `--spd-chart-font-lg` / `-xl` | `components/business/TrendChart.vue`；新增令牌 `styles/tokens/typography.css` | **P0** | 全站唯一图表组件，影响所有趋势图 |
| **D-02** | **科室申领筛选标签 11px** | `.dept-req-filterbar-item > span` = 11px | `--spd-font-size-sm`（13/15/17px） | `views/supply-chain/DepartmentRequisitionView.vue` 第 1830 行 | **P0** | 『科室申领』弹窗筛选条全部字段 |
| **D-03** | **科室申领已选清单全线 11–12px** | `li` 12px、`selected-name` 12px、`selected-code` 11px、`selected-spec` 11px、`label` 11px、`label select` 11px、`qty-stepper input` 11px | 容器 / 名称 / 数量输入 `--spd-font-size-base`（14px）；规格 / 标签 / 下拉 `--spd-font-size-sm`（13px）；编码 chip `--spd-font-size-xs`（11px） | `views/supply-chain/DepartmentRequisitionView.vue` 第 1941、1951、1960、1967、1976、1984、2009 行 | **P0** | 『科室申领』已选清单全部字段 |
| **D-04** | **AI 助手免责声明 10px —— 全站唯一低于下限的字号** | `.ai-medical-composer small` = 10px（第 1258–1265 行） | `--spd-font-size-xs`（11/13/15px） | `components/common/AiMedicalAssistant.vue` | **P0** | AI 助手输入区底部 |
| **D-05** | **驾驶舱趋势轴标签 10px** | `.trend-labels` = 10px（`OperationCockpitView.vue` 第 722 行） | `--spd-font-size-xs`（11/13/15px） | `views/operations/OperationCockpitView.vue` | **P0** | 驾驶舱手写 SVG 趋势图 12 个月份标签 |
| **D-06** | **驾驶舱风险等级标记 10px** | `.spd-warning-level-text` = 10px | `--spd-font-size-xs`（11px） | `views/operations/OperationCockpitView.vue` | **P0** | 驾驶舱预警条目等级标 |
| **D-07** | **登录页表单标签 12px 小于输入框 14px** | `.login-panel label > .lbl` = 12px | 13px（标签不得小于控件） | `styles/views/login.css` 第 450–465 行 | **P0** | 登录页用户名 / 密码标签 |
| **D-08** | **同一「商品名称」列四种字号** | 15px（unified-list，`.ulc-table td`）/ 15px（`.ulc-strong`）/ 14px（el-table、master-table）/ 13px（md-table）/ 12px（科室申领目录） | 统一 **14px**（`--spd-font-size-base`），主字段靠 700 字重区分 | `styles/list/unified-list.css`（第 59、128、211、228 行）、`styles/master-data.theme.css`、`styles/spd-override.css`、`views/supply-chain/DepartmentRequisitionView.vue`（第 2090、2100、2111 行） | **P1** | 全部列表页 |
| **D-09** | **表头不跟随字号档位** | 统一列表表头恒 14px 写死 | `--spd-font-size-table-head`（14/16/18px） | `styles/list/unified-list.css` 第 87 行；`styles/master-data.theme.css`；`styles/spd-override.css`；`styles/main.css`（结算明细表头第 1546 行）；`views/supply-chain/DepartmentRequisitionView.vue`（第 2100 行） | **P1** | 全部表格 |
| **D-10** | **筛选标签 / 输入框 / 分页不跟随档位** | 筛选标签恒 14px、输入框恒 13px、分页恒 13–14px | 标签 `--spd-font-size-sm`；控件 `--spd-font-size-base`；分页 `--spd-font-size-sm` / `--spd-font-size-base` | `styles/list/unified-list.css`（第 571、654、702 行）、`styles/components/pagination.css`（第 9 行） | **P1** | 全部列表页筛选区与分页条 |
| **D-11** | **筛选标签三套字号** | 14px（unified-list）/ 12px（md-*）/ 11px（科室申领） | 统一 **13px**（`--spd-font-size-sm`） | `styles/list/unified-list.css` 第 654 行、`styles/master-data.theme.css`、`views/supply-chain/DepartmentRequisitionView.vue` 第 1830 行 | **P1** | 全部列表页筛选区 |
| **D-12** | **状态条两套字号** | 14px（unified-list）/ 12px（md-*） | 统一 **14px**（`--spd-font-size-base`） | `styles/list/unified-list.css` 第 244 行（已符合，仅令牌化）、`styles/master-data.theme.css` | **P1** | 全部列表页状态列 |
| **D-13** | **`.btn` 字号三套** | 14px（`--btn-font-size`）/ 13px（`.md-btn` master-data 体系）/ 12px（`.btn-sm`、`.el-button--small`） | 主按钮族 **14px**；小号按钮族 **13px**；**12px 从按钮体系移除** | `styles/main.css`（`:root` 的 `--btn-font-size`）、`styles/components/buttons.css`、`.md-btn`（`styles/master-data.theme.css`）、`.el-button--small`（`styles/spd-override.css`） | **P1** | 全站按钮 |
| **D-14** | **行高令牌定义了但实际散落 1–1.85** | 实际出现 1 / 1.2 / 1.3 / 1.35 / 1.4 / 1.45 / 1.5 / 1.55 / 1.6 / 1.65 / 1.7 / 1.85 | 收敛到 `--spd-lh-tight`（1.35）/ `--spd-lh-base`（1.5）/ `--spd-lh-loose`（1.65）；单行控件可用 `1` | 全站 `styles/**` | **P1** | 全站 |
| **D-15** | **结算策略标记 / 元信息 / 编码列表过小** | `.settlement-policy-list b` 11px、`.settlement-policy-list small` 11px、`.settlement-cell-meta` 12px、`.settlement-code-list` 12px | 前三者 `--spd-font-size-xs`（11px，保持 11 但令牌化）；`settlement-cell-meta` 12px → 11px | `styles/main.css` 第 1715、1727、1734、1742 行 | **P1** | 『结算对账』页结算明细表 |
| **D-16** | **`font-weight: 650` 非标准值** | `medical-saas.css` 第 107、120、142、229、352、546 行共 6 处 `650`（其中 2 处带 `!important`） | 全部改为 **600** | `styles/medical-saas.css` | **P2** | 页面标题、导航、卡片 |
| **D-17** | **4 套并行字体栈** | `main.css`（Inter 系）、`spd-theme.css`（Ant 系）、`medical-saas.css`（Segoe 系）、`login.css`（独立系） | 收敛为 **2 套**：业务系统 `--spd-font-family` + 登录页独立栈 | `styles/main.css`、`styles/spd-theme.css`、`styles/medical-saas.css` | **P2** | 全站 |
| **D-18** | **迷你侧栏标题被 `11px !important` 强制** | `spd-override.css` 第 165–172 行 `font-size: 11px !important` | 保留 11px，改为 `var(--spd-font-size-xs) !important`，取消裸 px | `styles/spd-override.css`、`styles/spd-layout.css`（第 442 行）、`styles/theme.css`（第 515 行）、`styles/theme/calm-paper.css`（第 388 行） | **P2** | 迷你侧栏 |
| **D-19** | **行高 / 字重 / 弹窗内表格 12.5px 非整数** | `.el-dialog .el-table` = 12.5px；`.modal-heading p` = 12.5px；`.dept-req-section-title > span` 等多处 12.5px | 全部整数化：12.5 → 13 或 14 | `styles/spd-override.css`、`styles/components/modal.css`、`views/supply-chain/DepartmentRequisitionView.vue` | **P2** | 弹窗内表格与副标题 |
| **D-20** | **`MdPageHeader` 接收 `description` prop 但被全局 `display:none` 隐藏** | `main.css` 第 128–140 行隐藏 `.standard-page-heading > p` 等；`MdPageHeader` 仍接收并渲染 `description` | **删除 `description` prop 及其调用点传参**（产品方向：去顶部概览） | `styles/main.css` 第 128–140 行、`components/common/MdPageHeader.vue`、全部调用点 | **P2** | 全部 md-* 页面 |

### 7.1 优先级分布

| 优先级 | 条数 | 条目 |
| --- | --- | --- |
| **P0** | **7** | D-01 ~ D-07 |
| **P1** | **8** | D-08 ~ D-15 |
| **P2** | **5** | D-16 ~ D-20 |
| 合计 | **20** | — |

> 上表「合计 20」为 D-01 ~ D-20 全部条目。任务描述中「15 项不一致」对应调研稿原始清单（D-01 ~ D-15）；本次定稿新增 D-16 ~ D-20 共 5 条，覆盖字重、字体栈、迷你侧栏、非整数字号与 `MdPageHeader` 接口清理。

### 7.2 关键不一致项的现状值修正说明

| 项 | 调研稿记录 | **实测修正值** | 依据 |
| --- | --- | --- | --- |
| 结算策略标记 | 10.5px | **11px** | `styles/main.css` 第 1727–1731 行 |
| 结算明细 meta | 11.5px | **12px** | `styles/main.css` 第 1715–1718 行 |
| 结算编码列表 | 11.5px | **12px** | `styles/main.css` 第 1742 行 |
| 科室申领已选清单 | 全线 11px | **11px / 12px 混合** | `DepartmentRequisitionView.vue` 第 1941–2019 行 |
| 科室申领目录表 | 12px | **12px 确认** | `DepartmentRequisitionView.vue` 第 2090、2100、2111 行 |

---

## 8. 实施阶段与验收

### 8.0 阶段总览

| 阶段 | 名称 | 目标 | 条目 | 风险 |
| --- | --- | --- | --- | --- |
| **阶段 0** | 产品决策 | **已完成** —— Q-01 ~ Q-06 全部裁定 | — | 无 |
| **阶段 1** | P0 可读性修复 | 消除全部 < 12px 的判断型信息 | D-01 ~ D-07 | 低 |
| **阶段 2** | 令牌化 | 建立令牌体系，写死值全部令牌化 | D-09、D-10、D-13、D-14 | 中 |
| **阶段 3** | 跨页面对齐 | 统一「商品名称」等核心列与状态条、筛选标签 | D-08、D-11、D-12、D-15 | 中 |
| **阶段 4** | 行高字重收敛 | 行高 3 档、字重 5 档、字体栈 2 套、长尾清理 | D-16 ~ D-20 | 低 |

---

### 8.1 阶段 0：产品决策（已完成）

**内容**：Q-01 ~ Q-06 已在本文档 1.3 节裁定。本阶段无代码改动。

**验收**：产品经理确认 1.3 节表格无「待定」字样。

---

### 8.2 阶段 1：P0 可读性修复

**目标**：消除所有低于 12px 下限且承载业务判断的信息；图表纳入三档。

**改动文件清单**（7 个文件）：

| 文件 | 改动 |
| --- | --- |
| `styles/tokens/typography.css` | 新增 6 个令牌：`--spd-font-size-table-head`、`--spd-chart-font-xs/sm/base/lg/xl` |
| `components/business/TrendChart.vue` | 读取图表令牌；订阅档位；替换第 18、20、21 行的写死 `fontSize` |
| `views/supply-chain/DepartmentRequisitionView.vue` | 第 1830 行筛选标签 11 → 13；第 1941/1960/2009 行 12/11 → 14；第 1967/1976/1984 行 11 → 13 |
| `components/common/AiMedicalAssistant.vue` | 第 1258 行起免责声明 10 → 11 |
| `views/operations/OperationCockpitView.vue` | 第 722 行 `.trend-labels` 10 → 11；`.spd-warning-level-text` 10 → 11 |
| `styles/views/login.css` | 第 455 行表单标签 12 → 13 |
| `styles/main.css` | 第 1715、1727–1731 行结算区令牌化 |
**验收标准（computed style 可验证）**：

| 检查项 | standard | large | xlarge |
| --- | --- | --- | --- |
| `TrendChart` X 轴 `axisLabel.fontSize` | 12 | 14 | 16 |
| `TrendChart` Y 轴 `axisLabel.fontSize` | 12 | 14 | 16 |
| `TrendChart` `legend.textStyle.fontSize` | 13 | 15 | 17 |
| `.dept-req-filterbar-item > span` | 13 | 15 | 17 |
| `.dept-req-selected-list .selected-name` | 14 | 16 | 18 |
| `.dept-req-selected-list .qty-stepper input` | 14 | 16 | 18 |
| `.ai-medical-composer small` | 11 | 13 | 15 |
| `.trend-labels` | 11 | 13 | 15 |
| `.spd-warning-level-text` | 11 | 13 | 15 |
| `.login-panel label > .lbl` | 13 | 13 | 13（不参与档位） |

**回归验证命令**：

```powershell
npm --prefix frontend run build
npm run test:smoke:business-ui   # 需要 MySQL
```

---

### 8.3 阶段 2：令牌化

**目标**：写死字号全部改为令牌引用；三档机制覆盖全站。

**改动文件清单**（11 个文件）：

| 文件 | 改动 |
| --- | --- |
| `styles/tokens/typography.css` | 令牌定义与注释补齐 |
| `styles/list/unified-list.css` | 表头（第 87 行）、筛选标签（第 654 行）、筛选控件（第 571 行）、筛选按钮（第 702 行）、空态（第 563 行）、`.ulc-time`（第 730 行）令牌化 |
| `styles/components/pagination.css` | 容器（第 9 行）、按钮、输入框令牌化 |
| `styles/master-data.theme.css` | 表头、数据、`.md-status`、`.md-chip`、`.md-btn`、`.md-empty`、`.md-print-template-table` 令牌化 |
| `styles/spd-override.css` | `.el-table`、`.el-button`、`.el-dialog`、`.el-message-box`、`.el-select-dropdown__item`、`.el-tooltip` 令牌化 |
| `styles/main.css` | `--btn-font-size` 改为 `var(--spd-font-size-base)`；结算区令牌化 |
| `styles/components/buttons.css` | `.btn` / `.btn-sm` 令牌化 |
| `styles/components/modal.css` | `.modal-heading`、`.modal-body` 令牌化 |
| `styles/spd-layout.css` | 顶栏 / 迷你侧栏 / 页签栏令牌化 |
| `styles/spd-theme.css` | `.spd-tag`、`.spd-stat-value`、`.text-tertiary` 等工具类令牌化 |
| `styles/views/login.css` | **不令牌化**（独立体系），仅第 455 行改 13px |
**验收标准**：

| 检查项 | standard | large | xlarge |
| --- | --- | --- | --- |
| `.ulc-table .el-table__header th .cell` | 14 | 16 | 18 |
| `.ulc-filter label > span` | 13 | 15 | 17 |
| `.ulc-filter .el-input__wrapper` | 14 | 16 | 18 |
| `.hospital-pagination` | 13 | 15 | 17 |
| `.ulc-table .el-table__empty-text` | 14 | 16 | 18 |
| `.md-table thead th` | 14 | 16 | 18 |
| `.el-button` | 14 | 16 | 18 |

**验证脚本**（在浏览器控制台执行）：

```javascript
// 逐档切换并打印关键元素的 computed font-size
const targets = [
  '.ulc-table .el-table__header th .cell',
  '.ulc-table td.el-table__cell',
  '.ulc-filter label > span',
  '.hospital-pagination',
  '.md-table thead th'
]
for (const t of targets) {
  const el = document.querySelector(t)
  if (el) console.log(t, getComputedStyle(el).fontSize)
}
```

**回归验证命令**：

```powershell
npm --prefix frontend run build
npm run test:smoke:business-ui
```

---

### 8.4 阶段 3：跨页面对齐

**目标**：同一语义跨页面字号一致。

**改动文件清单**（6 个文件）：

| 文件 | 改动 |
| --- | --- |
| `styles/list/unified-list.css` | `.ulc-table td` 15px → base；`.ulc-strong` 16px → base + 700；`.ulc-mono` 15px → base |
| `styles/master-data.theme.css` | `.md-table tbody td` 13 → base；`.md-cell-name strong` 13 → base + 700；`.md-cell-name small` 11 → sm；`.md-status` 12 → base；`.md-chip` 12 → base；`.md-btn` 13 → base；`.md-query label > span` 12 → sm；`.md-input` 13 → base；`.md-empty p` 12 → sm；`.md-pagination` 13 → sm；`.md-print-template-table` 令牌化 |
| `styles/spd-override.css` | `.el-dialog .el-table` 12.5 → base；`.el-form-item__label` 14 → sm |
| `styles/main.css` | 结算明细表头 12 → table-head；`.settlement-table-summary` 13 → base；`.settlement-confirmed-text` 12 → sm |
| `views/supply-chain/DepartmentRequisitionView.vue` | 目录表 12 → base / table-head；历史表 13 → base / table-head；`.dept-req-section-title h3` 15 → lg |
| `styles/components/pagination.css` | `.md-pagination .md-btn` 12 → sm |
**验收标准**：

| 页面 · 元素 | standard | large | xlarge |
| --- | --- | --- | --- |
| 『科室库房目录』表头『商品名称』 | 14 | 16 | 18 |
| 同页『商品名称』列数据（聚维酮碘消毒剂…） | 14 | 16 | 18 |
| 同页『商品名称』列主字段 `.ulc-strong` | 14 | 16 | 18 |
| 同页该列次级说明『科室可申领商品』 | 13 | 15 | 17 |
| md-* 页『商品名称』列数据 | 14 | 16 | 18 |
| 『科室申领』目录表数据 | 14 | 16 | 18 |
| md-* 页『状态』列徽章 | 14 | 16 | 18 |
| 『科室库房目录』『状态』列『启用』 | 14 | 16 | 18 |
| 全部筛选标签 | 13 | 15 | 17 |
| 『结算对账』结算明细表头 | 14 | 16 | 18 |

**回归验证命令**：

```powershell
npm --prefix frontend run build
npm run test:smoke:business-ui
```

---

### 8.5 阶段 4：行高与字重收敛

**目标**：行高 3 档、字重 5 档、字体栈 2 套、剩余长尾清理。

**改动文件清单**（9 个文件）：

| 文件 | 改动 |
| --- | --- |
| `styles/medical-saas.css` | 第 107、120、142、229、352、546 行 `font-weight: 650` → `600`；面包屑当前项 550 → 600；散落行高归入 3 档 |
| `styles/main.css` | 字体栈统一为 `var(--spd-font-family)`；散落行高归入 3 档；第 128–140 行保留隐藏规则 |
| `styles/spd-theme.css` | 字体栈统一为 `var(--spd-font-family)` |
| `styles/spd-override.css` | 第 165–172 行 `.spd-mini-sidebar-title` 改 `var(--spd-font-size-xs) !important` |
| `styles/spd-layout.css` | 顶栏 / 迷你侧栏令牌化；12.5px 整数化 |
| `styles/theme.css`、`styles/theme/calm-paper.css` | 迷你侧栏标题令牌化 |
| `styles/components/modal.css` | 12.5px 整数化；行高归档 |
| `components/common/MdPageHeader.vue` + 调用点 | 删除 `description` prop 与传参 |
| `styles/views/login.css` | 不动（独立体系） |
**验收标准**：

| 检查项 | 期望 |
| --- | --- |
| 全仓库 `font-weight: 650` 命中数 | **0** |
| 全仓库 `font-weight` 非 400/500/600/700/800 命中数 | **0**（900+ 与 < 400 均为 0） |
| 全仓库 `line-height` 非 1 / 1.35 / 1.5 / 1.65 命中数 | **0**（登录页独立体系除外） |
| 全仓库裸 `font-size: Npx` 命中数 | 收敛至图标尺寸 / 装饰圆点 / 打印模板 / 登录页四类白名单 |
| 字体栈定义处 | 恰好 2 处（业务 `--spd-font-family` + 登录页） |
| `MdPageHeader` 的 `description` prop | **不存在** |

**扫描命令**：

```powershell
rg -n "font-weight:\s*(650|750|850|900|[1-3]00)" frontend/src
rg -n "line-height:\s*(1\.?[0-9]*)" frontend/src/styles | rg -v "1\.35|1\.5|1\.65|line-height:\s*1;"
rg -n "font-size:\s*[0-9]+px" frontend/src --glob "*.vue" --glob "*.css"
```

**回归验证命令**：

```powershell
npm --prefix frontend run build
npm run test:smoke:business-ui
npm run verify:release
```

---

### 8.6 全阶段通用验收

| # | 检查项 | 方法 | 通过标准 |
| --- | --- | --- | --- |
| G-T-1 | 三档缩放全链路生效 | 顶栏切换 standard / large / xlarge | 表格、筛选、分页、图表、按钮、状态条全部同步变化 |
| G-T-2 | 无横向溢出 | 1280 / 1366 / 1920 / 2560 四档视口 | 除结算明细表（`min-width: 2780px`，设计如此）外无横向滚动 |
| G-T-3 | 长文本不截断 | 『科室库房目录』65 字符商品名 | 换行完整显示，Tooltip 可看全文 |
| G-T-4 | 最小字号合规 | DevTools 全页扫描 | 除白名单外无 < 12px 文字 |
| G-T-5 | 无生成文件污染 | `git status` | `frontend/src` 下无新增 `*.js` / `*.vue.js` |
| G-T-6 | 空态 / 加载 / 错误态字号一致 | 触发各页空态 | 符合 4.17 节 |

---

## 9. 新增页面 / 组件的字体检查清单

> 开发新页面或新组件时，逐项确认后再提交。任一项不满足即退回。

### 9.1 字号与令牌

| # | 检查项 | 期望 |
| --- | --- | --- |
| 1 | 裸 `font-size: Npx` | **零**，全部 `var(--spd-font-size-*)` |
| 2 | 表格数据单元格 | `--spd-font-size-base`（14/16/18px） |
| 3 | 表格表头 | `--spd-font-size-table-head`（14/16/18px），字重 700 |
| 4 | 表格主字段 | `--spd-font-size-base` + 字重 700（**不**靠放大字号制造层级） |
| 5 | 表格次级说明 | `--spd-font-size-sm`（13/15/17px） |
| 6 | 筛选标签 / 表单标签 | `--spd-font-size-sm` + 字重 600 |
| 7 | 输入控件内文字 | `--spd-font-size-base` |
| 8 | 状态条 / 状态徽章 | `--spd-font-size-base` + 字重 600 |
| 9 | 按钮文字 | `--spd-font-size-base`（主/次）或 `--spd-font-size-sm`（小号），**不出现 12px** |
| 10 | 角标 / 计数 / 编码 chip | `--spd-font-size-xs`（11/13/15px） |
| 11 | 标题层级 | 页面主标题 `-3xl`；区块标题 `-lg` / `-xl`；弹窗标题 `-2xl` |
| 12 | 页面内字号种类 | **≤ 3 档**，主档占绝对多数 |

### 9.2 字重与行高

| # | 检查项 | 期望 |
| --- | --- | --- |
| 13 | 字重取值 | 只用 400 / 500 / 600 / 700 / 800，**没有** 650 / 750 / 900 |
| 14 | 表格单元格 / 按钮 / 状态条行高 | `--spd-lh-tight` |
| 15 | 正文 / 输入控件行高 | `--spd-lh-base` |
| 16 | 长段落 / 说明文字行高 | `--spd-lh-loose` |
| 17 | 散落行高 | **没有** 1.2 / 1.4 / 1.45 / 1.55 / 1.6 / 1.7 / 1.85 |

### 9.3 语义与可读性

| # | 检查项 | 期望 |
| --- | --- | --- |
| 18 | 承载业务判断的文字 | **没有任何**小于 12px 的 |
| 19 | 标签字号 | **不小于**其对应控件的字号 |
| 20 | 筛选标签 vs 输入控件 | 字号差在 −1px ~ +1px 之间 |
| 21 | 状态语义信息（启用/停用/待审核/通过/驳回） | 14px |
| 22 | 金额 / 数量 / 编码 / 日期 / 页码 | 加 `font-variant-numeric: tabular-nums` |
| 23 | 跨行对齐的数字列 | 同一字号 + 等宽字体 |
| 24 | 跨页面一致性 | 同一语义在其它页面也是同一字号（对照第 4 章） |

### 9.4 图表

| # | 检查项 | 期望 |
| --- | --- | --- |
| 25 | ECharts 字号来源 | **全部**来自 `--spd-chart-font-*`，**没有**裸 `fontSize: N` |
| 26 | 档位订阅 | 组件订阅了字号档位变化并重绘 |
| 27 | 坐标轴刻度 | ≥ 12px（standard 档），且比同页正文轻一档 |
| 28 | Tooltip 字号 | ≤ 同页弹窗标题字号 |
| 29 | 无障碍 | `aria.enabled` 保持开启 |

### 9.5 三档验证

| # | 检查项 | 期望 |
| --- | --- | --- |
| 30 | 三档走查 | standard / large / xlarge 下**逐一**走查，无文字溢出、无裁切、无重叠 |
| 31 | 表格横向滚动 | xlarge 档下不出现（除非列数确实超宽） |
| 32 | 长文本列 | xlarge 档下换行正常，不截断关键信息 |
| 33 | 控件高度 | 足够容纳 xlarge 档字号（输入框、下拉、按钮、chip 均无文字被切） |
| 34 | 空态 / 加载 / 错误态 | 与正常态一致或符合 4.17 节 |
| 35 | 弹窗内嵌表格 | 与主列表一致（14px） |

### 9.6 提交前

| # | 检查项 | 期望 |
| --- | --- | --- |
| 36 | 构建 | `npm --prefix frontend run build` 通过 |
| 37 | 生成文件 | `git status` 中 `frontend/src` 下**没有**新增 `*.js` / `*.vue.js` |
| 38 | 截图留证 | 页面在标准档与特大档各截一张图，附在 PR 描述中 |

---

## 10. 附录

### 10.1 全量令牌速查表（三档 × 全部字号）

| 令牌 | standard (14) | large (16) | xlarge (18) | 语义 |
| --- | --- | --- | --- | --- |
| `--spd-fs-base` | 14px | 16px | 18px | 基准 |
| `--spd-lh-tight` | 1.35 | 1.35 | 1.35 | 紧凑行高 |
| `--spd-lh-base` | 1.5 | 1.5 | 1.5 | 基准行高 |
| `--spd-lh-loose` | 1.65 | 1.65 | 1.65 | 松散行高 |
| `--spd-table-cell-padding-y` | 10px | 12px | 13px | 表格单元格上下留白 |
| `--spd-font-size-xs` | 11px | 13px | 15px | 角标 / 计数 / 装饰标签 |
| `--spd-font-size-sm` | 13px | 15px | 17px | 次级说明 / 筛选标签 / 表单标签 |
| `--spd-font-size-base` | 14px | 16px | 18px | 表格数据 / 正文基准 / 输入控件 |
| `--spd-font-size-md` | 15px | 17px | 19px | **正文强调文本**（不再用于表格数据） |
| `--spd-font-size-lg` | 16px | 18px | 20px | 区块标题 / 卡片标题 |
| `--spd-font-size-xl` | 18px | 20px | 22px | 二级标题 |
| `--spd-font-size-2xl` | 20px | 22px | 24px | 弹窗标题 / 统计数值 |
| `--spd-font-size-3xl` | 24px | 26px | 28px | 页面主标题 |
| `--spd-font-size-4xl` | 30px | 32px | 34px | 展示型大数值（驾驶舱时钟） |
| `--spd-font-size-5xl` | 38px | 40px | 42px | 超大展示数值（驾驶舱 KPI） |
| **`--spd-font-size-table-head`** | **14px** | **16px** | **18px** | **表格表头** |
| `--spd-chart-font-xs` | 11px | 13px | 15px | 图表微型角标（预留） |
| **`--spd-chart-font-sm`** | **12px** | **14px** | **16px** | **图表轴刻度 / 轴名称** |
| **`--spd-chart-font-base`** | **13px** | **15px** | **17px** | **图表图例** |
| `--spd-chart-font-lg` | 14px | 16px | 18px | 图表数据标签 / Tooltip 标题 |
| `--spd-chart-font-xl` | 15px | 17px | 19px | 图表 Tooltip 正文 |

### 10.2 逐档速查

```
standard（14px 基准）
  xs 11px · sm 13px · base 14px · md 15px · lg 16px
  xl 18px · 2xl 20px · 3xl 24px · 4xl 30px · 5xl 38px
  table-head 14px
  chart: xs 11px · sm 12px · base 13px · lg 14px · xl 15px

large（16px 基准）
  xs 13px · sm 15px · base 16px · md 17px · lg 18px
  xl 20px · 2xl 22px · 3xl 26px · 4xl 32px · 5xl 40px
  table-head 16px
  chart: xs 13px · sm 14px · base 15px · lg 16px · xl 17px

xlarge（18px 基准）
  xs 15px · sm 17px · base 18px · md 19px · lg 20px
  xl 22px · 2xl 24px · 3xl 28px · 4xl 34px · 5xl 42px
  table-head 18px
  chart: xs 15px · sm 16px · base 17px · lg 18px · xl 19px
```

### 10.3 关键文件索引

| 用途 | 路径 |
| --- | --- |
| **字号令牌唯一来源** | `frontend/src/styles/tokens/typography.css` |
| 颜色令牌 | `frontend/src/styles/tokens/colors.css` |
| 全局基础 + 按钮令牌 | `frontend/src/styles/main.css` |
| 字号档位状态管理 | `frontend/src/stores/theme.ts` |
| 统一列表（跟随档位） | `frontend/src/styles/list/unified-list.css` |
| md-* 设计体系 | `frontend/src/styles/master-data.theme.css` |
| 顶栏 / 迷你侧栏 / 页签栏 | `frontend/src/styles/spd-layout.css` |
| 强制覆盖层 | `frontend/src/styles/spd-override.css` |
| 医疗 SaaS 层（含 650 字重） | `frontend/src/styles/medical-saas.css` |
| 弹窗 | `frontend/src/styles/components/modal.css` |
| 按钮 | `frontend/src/styles/components/buttons.css` |
| 分页 | `frontend/src/styles/components/pagination.css` |
| 登录页（独立体系） | `frontend/src/styles/views/login.css` |
| **图表（唯一 ECharts）** | `frontend/src/components/business/TrendChart.vue` |
| 通用表格组件 | `frontend/src/components/common/DataTable.vue` |
| 通用分页组件 | `frontend/src/components/common/PaginationControls.vue` |
| 页面标题组件 | `frontend/src/components/common/MdPageHeader.vue` |
| 科室申领页 | `frontend/src/views/supply-chain/DepartmentRequisitionView.vue` |
| 操作驾驶舱页 | `frontend/src/views/operations/OperationCockpitView.vue` |
| AI 助手组件 | `frontend/src/components/common/AiMedicalAssistant.vue` |

### 10.4 三档切换与验证

**切换方式**：

```javascript
document.documentElement.setAttribute('data-font-size', 'large')
document.documentElement.setAttribute('data-font-size', 'xlarge')
document.documentElement.removeAttribute('data-font-size')  // 回到 standard
```

或：顶栏主题切换器 → 主题面板 → 界面字号。

**逐项检查（整改后目标值）**：

| # | 检查项 | standard | large | xlarge | 状态 |
| --- | --- | --- | --- | --- | --- |
| V-01 | 表格数据单元格 | 14 | 16 | 18 | 待整改（现状 15/17/19） |
| V-02 | 表格表头 | 14 | 16 | 18 | 待整改（现状恒 14） |
| V-03 | 表格主字段 `.ulc-strong` | 14 | 16 | 18 | 待整改（现状 16/18/20） |
| V-04 | 表格次级说明 `.ulc-sub` | 13 | 15 | 17 | ✅ 已符合 |
| V-05 | 状态条 `.ulc-chip` | 14 | 16 | 18 | ✅ 已符合 |
| V-06 | 行内操作按钮 `.ulc-action` | 14 | 16 | 18 | ✅ 已符合 |
| V-07 | 工具栏按钮 `.md-btn` | 14 | 16 | 18 | 部分符合（master-data 体系 13px 写死） |
| V-08 | 筛选标签 | 13 | 15 | 17 | 待整改（现状恒 14 / 12 / 11） |
| V-09 | 筛选输入框 | 14 | 16 | 18 | 待整改（现状恒 13） |
| V-10 | 分页 | 13 | 15 | 17 | 待整改（现状恒 13–14） |
| V-11 | ECharts 坐标轴 | 12 | 14 | 16 | 待整改（现状恒 10） |
| V-12 | ECharts 图例 | 13 | 15 | 17 | 待整改（现状恒 11） |
| V-13 | md-* 表格数据 | 14 | 16 | 18 | 待整改（现状 13px 写死） |
| V-14 | 科室申领目录表数据 | 14 | 16 | 18 | 待整改（现状 12px 写死） |
| V-15 | 结算明细表头 | 14 | 16 | 18 | 待整改（现状 12px 写死） |
| V-16 | 长文本列换行 | xlarge 档不溢出、不截断 | — | — | 回归项 |
| V-17 | 页面无横向溢出 | 除结算明细表外无横向滚动 | — | — | 回归项 |

### 10.5 术语表

| 术语 | 定义 |
| --- | --- |
| **字号档位** | standard / large / xlarge 三档，由 `<html data-font-size>` 驱动 |
| **基准字号 `--spd-fs-base`** | 三档的推导原点，14 / 16 / 18px |
| **表格表头** | 表格中列名所在的 `th` 行 |
| **表格数据单元格** | 表格中内容所在的 `td` 单元格 |
| **主字段** | 单元格内的主标题文本，如商品名称主行 |
| **次级说明** | 单元格内主字段下方的补充说明文字 |
| **状态条** | `.ulc-chip`，带圆点的语义状态标签 |
| **状态徽章** | `.md-status`，无圆点的语义状态标签 |
| **行内操作按钮** | `.ulc-action`，表格行内的编辑/删除等操作 |
| **工具栏按钮** | `.md-btn`，表格上方工具栏的页面级操作 |
| **列设置** | 表格列显隐与宽度配置的浮层面板 |
| **筛选区** | 表格上方的查询条件区域 |
| **次级说明** | 辅助解释性文字，13px 档 |
| **辅助信息** | 角标、计数、免责、版权等不承载业务判断的信息 |
| **展示型数值** | 统计卡、驾驶舱 KPI 这类允许突破常规层级的字号（30–42px） |
| **图表令牌** | `--spd-chart-font-*`，供 ECharts canvas 读取的独立字号体系 |
| **静墨宣纸** | 方案 E 主题分支 `feature/ui-calm-paper` |
| **四套字体栈** | 整改前并存的 4 套字体定义，目标是收敛为 2 套 |

### 10.6 变更记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1.0 | 2026-06 | 仅覆盖统一列表页与弹窗，浅层 |
| v2.0 | 2026-11-23 | 重写为完整版：覆盖 17 类、110+ 条分类；新增图表专项、层级模型、24 项不一致清单、6 项产品决策问题 |
| **v3.0** | **2026-11-23** | **最终定稿执行文档：产品决策 Q-01 ~ Q-06 全部裁定；确立「12px 硬下限」；新增 `--spd-font-size-table-head` 与 `--spd-chart-font-*` 共 6 个令牌；`--spd-font-size-md` 重新定位为正文强调；补充表格主字段同档不违反层级规则的论证；整改清单扩至 20 条并修正 5 项现状实测值；新增图表专项与三档验收数值；新增 38 项开发检查清单** |

---

**文档结束。**

> 本文档为 SPD 平台字体与字号的**唯一可执行标准**。所有数值均为结论。
> 后续新增 UI 区域必须先在第 4 章登记分类再实现；后续调整字号必须先修改第 3 章令牌再改样式。
