# SPD 字体与字号标准规范

> 版本：v1.0
> 适用分支：`feature/ui-calm-paper`（方案 E 静墨宣纸）
> 适用范围：院内 SPD 平台全部页面（基础数据、供应链、库存、结算、系统管理、登录页）
> 面向人群：30 岁以上中长期使用者，含轻度老花与视力偏弱用户

---

## 一、总则

### 1.1 设计目标

| 目标 | 落地方式 |
| --- | --- |
| 长时间注视不疲劳 | 基准 14px 起跳，正文行高 1.5，字号档位可整站等比放大 |
| 不同视力用户都能用 | 提供标准 / 大 / 特大三档，用户自行切换，整站等比缩放 |
| 全站字号统一 | 全部字号由 `tokens/typography.css` 的 11 个令牌推导，禁止业务样式写死 |
| 表格信息密度可控 | 表头与正文分档，数值使用等宽数字（tabular-nums） |

### 1.2 三条铁律

1. **禁止在业务样式里写死 `font-size: 14px`**，必须使用 `var(--spd-font-size-*)` 令牌。
2. **禁止用 `<small>` 承载关键信息**，次级信息用 `.ulc-sub` / `--spd-font-size-sm`。
3. **字号档位是「基准」而非「逐个元素」**，切换档位后全站等比缩放，不允许出现「表格放大了、筛选框没放大」的割裂。

### 1.3 令牌定义位置

```
frontend/src/styles/tokens/typography.css   ← 字号体系唯一来源
```

CSS 变量通过 `<html data-font-size="standard|large|xlarge">` 驱动：

| 令牌 | 表达式 | 用途 |
| --- | --- | --- |
| `--spd-font-size-xs` | `base - 3px` | 微型标记、图标旁计数 |
| `--spd-font-size-sm` | `base - 1px` | 次级说明、辅助文本 |
| `--spd-font-size-base` | `base` | **表单标签、正文基准** |
| `--spd-font-size-md` | `base + 1px` | **表格数据单元格** |
| `--spd-font-size-lg` | `base + 2px` | 强调文本、表格主字段 |
| `--spd-font-size-xl` | `base + 4px` | 区块小标题 |
| `--spd-font-size-2xl` | `base + 6px` | 弹窗标题 |
| `--spd-font-size-3xl` | `base + 10px` | 页面主标题 |
| `--spd-font-size-4xl` | `base + 16px` | 展示型大标题 |
| `--spd-font-size-5xl` | `base + 24px` | 统计数值 |

---

## 二、三档字号体系（标准 / 大 / 特大）

用户通过顶栏主题切换器选择，系统写入 `<html data-font-size="...">`。

| 档位 | 基准字号 | 缩放比 | 适用场景 | 提示文案 |
| --- | --- | --- | --- | --- |
| **标准** | 14px | 100% | 常规显示器 · 密度优先 | 密度最高，表格一屏显示行数最多 |
| **大** | 16px | 114% | 27 寸以上屏幕 · 轻度老花 | 平衡档，绝大多数用户推荐 |
| **特大** | 18px | 129% | 视力偏弱 · 高分辨率远距使用 | 一屏行数减少，需配合横向滚动 |

### 2.1 切换行为

- 基准字号写入 `<html>`，`html { font-size: var(--spd-fs-base) }`，整站 `rem` 体系等比缩放。
- 表格单元格上下留白同步微调，避免大字号下版面松散：

| 档位 | 基准 | 单元格上下留白 `--spd-table-cell-padding-y` |
| --- | --- | --- |
| 标准 | 14px | 10px |
| 大 | 16px | 12px |
| 特大 | 18px | 13px |

- 行高随字号放大略微收紧，防止大字号下表格行过高：

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `--spd-lh-tight` | 1.35 | 按钮、状态条、紧凑标签 |
| `--spd-lh-base` | 1.5 | 正文默认 |
| `--spd-lh-loose` | 1.65 | 长段落、说明文字 |

---

## 三、各页面部分字号对照表（标准档位实测值）

> 下表为浏览器 `getComputedStyle` 实测结果，非设计稿推算。
> 括号内为「大 / 特大」档位的实际生效值；标注 `—` 表示该元素当前**不跟随档位**（见第五节）。

### 3.1 页面框架（所有页面共用）

| 部位 | 标准 14px | 大 16px | 特大 18px | 字重 | 说明 |
| --- | --- | --- | --- | --- | --- |
| 顶栏品牌文字 | **16px** | — | — | 700 | `.spd-topnav-brand-text` |
| 顶栏页面标题 | **24px** | — | — | 700 | `.topbar h1`，行高 1.25 |
| 左侧菜单项 | **14px** | — | — | 500 | `.spd-menu-label` |
| 账号姓名 | **13px** | — | — | 400 | `.account-info span` |
| 内容区页面主标题 | **28px** | — | — | 700 | `.master-page-title h2` |
| 面包屑 | **13px** | — | — | 400 | `.breadcrumb-line` |

### 3.2 列表页 —— 筛选区

| 部位 | 标准 14px | 大 16px | 特大 18px | 字重 | 说明 |
| --- | --- | --- | --- | --- | --- |
| 筛选字段标签 | **14px** | 14px | 14px | 700 | `.ulc-filter label > span` — **不跟随档位** |
| 输入框 / 下拉文字 | **13px** | 13px | 13px | 400 | `.ulc-filter input` — **不跟随档位** |
| 输入框占位符 | 13px | 13px | 13px | 400 | `--spd-text-placeholder` |
| 筛选按钮「查询 / 重置」 | **14px** | 14px | 14px | 700 | — **不跟随档位** |

### 3.3 列表页 —— 表格（核心）

| 部位 | 标准 14px | 大 16px | 特大 18px | 字重 | 说明 |
| --- | --- | --- | --- | --- | --- |
| **表头** | **14px** | 14px | 14px | 700 | `.el-table__header th .cell` — **不跟随档位** |
| **数据单元格** | **15px** | 17px | 19px | 400 | `--spd-font-size-md`，行高 1.45 |
| 单元格主字段（加粗） | **16px** | 18px | 20px | 700 | `.ulc-strong` |
| 单元格次级说明 | **13px** | 15px | 17px | 500 | `.ulc-sub` |
| 编码 / 单号（等宽） | **15px** | 17px | 19px | 400 | `.ulc-mono`，`tabular-nums` |
| 时间列 | **15px** | 17px | 19px | 400 | `.ulc-time`，`tabular-nums` |
| 长文本（自动换行） | 15px | 17px | 19px | 400 | `.ulc-wrap-text`，`white-space: normal` |
| 序号 / 计数 | 15px | 17px | 19px | 700 | — |

### 3.4 列表页 —— 状态条与按钮

| 部位 | 标准 14px | 大 16px | 特大 18px | 字重 | 说明 |
| --- | --- | --- | --- | --- | --- |
| **状态条** | **14px** | 16px | 18px | 600 | `.ulc-chip`，`--spd-font-size-base` |
| 状态条圆点 | 7px | 7px | 7px | — | 固定尺寸，不随档位缩放 |
| **行内操作按钮** | **14px** | 16px | 18px | 600 | `.ulc-action` |
| **工具栏按钮** | **14px** | 16px | 18px | 600 | `.md-btn` |
| 工具栏按钮高度 | 34px | 34px | 34px | — | 固定，不随档位缩放 |
| 筛选按钮高度 | 38px | 38px | 38px | — | 固定 |

### 3.5 列表页 —— 分页与空态

| 部位 | 标准 14px | 大 16px | 特大 18px | 字重 | 说明 |
| --- | --- | --- | --- | --- | --- |
| 分页页码 | **14px** | 14px | 14px | 400 | `.el-pagination` — **不跟随档位** |
| 分页汇总文案 | **13px** | 13px | 13px | 400 | `.hospital-pagination-summary` — **不跟随档位** |
| 空态主文案 | **13px** | 13px | 13px | 400 | `.el-table__empty-text` — **不跟随档位** |
| 空态提示 | 12px | 12px | 12px | 400 | `.spd-calm-empty small` |

### 3.6 弹窗

| 部位 | 标准 14px | 大 16px | 特大 18px | 字重 | 说明 |
| --- | --- | --- | --- | --- | --- |
| 弹窗标题 | **16px** | — | — | 600 | `.el-dialog__title`（`!important` 锁定） |
| 弹窗副标题 | **12.5px** | — | — | 400 | `.modal-subtitle` |
| 弹窗表单标签 | **13px** | — | — | 400 | `.catalog-dialog-form label > span` |
| 弹窗输入框 | **13px** | — | — | 400 | `.catalog-dialog-form input` |
| 弹窗表格 | **12.5px** | — | — | 400 | `.el-dialog .el-table` |
| 弹窗按钮 | **14px** | — | — | 600 | `.catalog-dialog-actions .btn` |
| 确认框标题 | **16px** | — | — | 600 | `.el-message-box__title` |
| 确认框正文 | **14px** | — | — | 400 | `.el-message-box__message`，行高 1.7 |

### 3.7 登录页（独立页面，不属于业务列表）

| 部位 | 字号 | 字重 | 说明 |
| --- | --- | --- | --- |
| 左侧主标题 | **30px** | 700 | `.login-aside-hero h1`，行高 1.32 |
| 左侧说明段落 | **14px** | 400 | 行高 1.85 |
| 左侧特性条目 | **13px** | 500 | — |
| 统计数值 | **20px** | 700 | `.login-aside-stats .stat strong` |
| 统计标签 | **11px** | 400 | — |
| 底部版权 | **11px** | 400 | — |
| 右侧面板标题 | **20px** | 700 | `.login-panel-head h2` |
| 右侧面板说明 | **13px** | 400 | 行高 1.7 |
| 表单标签 | **13px** | 500 | — |
| 表单输入框 | **14px** | 400 | — |
| 记住账号 / 忘记密码 | **12px** | 500–600 | — |
| 错误提示 | **12px** | 400 | 行高 1.55 |
| 登录按钮 | **14px** | 600 | 字距 0.12em |
| 品牌标识 | **18px** | 700 | 字距 0.05em |
| 品牌副标 | **12px** | 400 | — |

> 登录页为独立视觉体系，**不参与字号档位缩放**，固定使用上表数值。

### 3.8 其它业务区块

| 部位 | 字号 | 字重 | 说明 |
| --- | --- | --- | --- |
| 区块小标题 | **18px** | 700 | `.section-title h2` |
| 卡片标题 | **17px** | 700 | `.module-card h3` |
| 区块小标题（紧凑） | **15px** | 700 | `.section-title.compact h3` |
| 统计面板数值 | **20px** | 700 | `.metric-panel strong` |
| 结算表头 | **12px** | 700 | `.settlement-detail-table thead th`，字距 0.02em |
| 结算表元信息 | **12px** | 400 | `.settlement-cell-meta` |
| 结算策略标记 | **11px** | 800 | `.settlement-policy-list b` |
| 结算编码列表 | **12px** | 400 | 等宽字体 |
| 包标签 chip | **12px** | 400 | `.package-label-chip` |
| 库存标签 chip | **12px** | 400 | `.stocktaking-scope-chip` |
| 面包屑 | **13px** | 400 | — |
| 分区小标题 | **13px** | 800 | 字距 0.06em |

---

## 四、字号层级模型（视觉优先级）

同一页面内，字号应形成清晰的层级递进，而非无序跳跃：

```
页面主标题      28px  ┐
弹窗标题        16px  │  极少量使用
主字段 / 统计值  16-20px ┘

表头            14px  ┐
工具栏按钮      14px  │
状态条          14px  │
行内操作        14px  │  高频操作区，等大以降低视觉噪声
筛选标签        14px  ┘

表格数据        15px  ┐
强调字段        16px  │  数据区
次级说明        13px  ┘

辅助 / 提示     12px  ┐  少量使用
微型标记        11px  ┘
```

**规则**：
1. 同一区域内字号档位不超过 **2 档**。
2. 数据区字号（15px）与操作区字号（14px）**必须小于等于**表头，不得反超。
3. `11px` 仅用于角标类微型标记，**不得**用于任何需要用户读取判断的信息。

---

## 五、当前已知的不一致项（需整改）

实测发现以下元素**未跟随字号档位**，属于实现缺陷而非规范要求：

| 部位 | 当前表现 | 期望 | 涉及文件 |
| --- | --- | --- | --- |
| 表格表头 | 恒定 14px | 跟随 `--spd-font-size-base` | `list/unified-list.css`（`.ulc-table .el-table__header th .cell`） |
| 筛选字段标签 | 恒定 14px | 跟随 `--spd-font-size-base` | `list/unified-list.css`（`.ulc-filter label > span` 写死 `#405564` + `14px`） |
| 筛选输入框 | 恒定 13px | 跟随 `--spd-font-size-base` | `list/unified-list.css`（`.el-input__wrapper`） |
| 分页页码 | 恒定 14px | 跟随 `--spd-font-size-base` | `components/pagination.css` / Element Plus 默认值 |
| 空态文案 | 恒定 13px | 跟随 `--spd-font-size-base` | `list/unified-list.css`（`.el-table__empty-text`） |
| 筛选区按钮 | 恒定 14px | 跟随 `--spd-font-size-base` | `list/unified-list.css`（`.ulc-filter-actions .md-btn` 写死 `14px`） |

**整改方向**：将上述规则中的写死字号替换为 `var(--spd-font-size-base)`，
并在 `typography.css` 中补充 `--spd-font-size-table-head` 令牌（用于表头），
使表头与正文形成 **base / md** 的稳定两级关系。

---

## 六、与配色标准的配套关系

本规范与 `tokens/colors.css` 共同构成「统一标准」，三者不可分割：

| 维度 | 令牌文件 | 规范要点 |
| --- | --- | --- |
| 字号 | `tokens/typography.css` | 11 个令牌 × 3 个档位 |
| 颜色 | `tokens/colors.css` | 1 主色 + 4 语义色 + 中性灰阶 |
| 圆角 / 边框 | `tokens/colors.css` | 控件 4px · 卡片 12px · 边框 1px |

配套执行要点：

- 文字**恒为深色**（`--spd-c-text` ≈ `#1f2933`），背景**恒为浅色**（`--spd-c-bg` ≈ `#ffffff`）；
- 状态条统一为 **4px 圆角 + 1px 边框 + 语义色透明底 + 深色文字 + 7px 圆点**；
- 数值类字段（编码、金额、时间）统一使用 `font-variant-numeric: tabular-nums`，保证多行对齐；
- 等宽字体仅用于编码 / 单号 / 追踪码，正文一律使用系统无衬线字体。

---

## 七、复现与验证

```powershell
npm --prefix frontend run build
npm run dev:frontend
```

切换字号档位（顶栏主题切换器 → 字号），或直接在控制台：

```javascript
document.documentElement.setAttribute('data-font-size', 'xlarge')
```

验证要点：

1. 表格数据、状态条、工具栏按钮应随档位等比放大；
2. 表头与筛选标签在整改前保持不变（见第五节），整改后应同步；
3. 长文本列（商品名称 / 生产厂家）在特大档位下应正常换行，不得溢出或截断；
4. 表格宽度应始终铺满内容区，无横向留白；
5. 切换档位后页面不应出现横向滚动条溢出（除非列数确实超宽）。

---

## 八、附录：字号令牌速查

```
标准 14px 档位：
  xs 11px · sm 13px · base 14px · md 15px · lg 16px
  xl 18px · 2xl 20px · 3xl 24px · 4xl 30px · 5xl 38px

大 16px 档位：
  xs 13px · sm 15px · base 16px · md 17px · lg 18px
  xl 20px · 2xl 22px · 3xl 26px · 4xl 32px · 5xl 40px

特大 18px 档位：
  xs 15px · sm 17px · base 18px · md 19px · lg 20px
  xl 22px · 2xl 24px · 3xl 28px · 4xl 34px · 5xl 42px
```
