# SPD 平台字体与字号标准规范

> 版本：v2.0（完整版，覆盖全站所有 UI 区域）
> 适用仓库：`D:\SPD`（分支 `feature/ui-calm-paper` 方案 E「静墨宣纸」）
> 适用范围：院内 SPD 平台全部页面 —— 基础数据、供应链、库存、结算、系统管理、操作驾驶舱、登录页、AI 助手
> 面向人群：30 岁以上中长期使用者，含轻度老花与视力偏弱用户
> 状态：**待产品评审**（本文档只定义标准，不含代码改动）

---

## 1. 文档说明

### 1.1 目标

| 目标 | 落地方式 |
| --- | --- |
| 全站每一个 UI 区域都有唯一确定的字号 | 第 3 章给出 40+ 条分类，每条有标准字号、字重、行高、参考实现 |
| 同一语义在全站用同一字号 | 第 7 章列出全部不一致项，标明影响面与优先级 |
| 支持 30+ 用户视力调节 | 三档缩放机制（标准 / 大 / 特大）由 `--spd-fs-base` 统一驱动 |
| 图表与正文可分开治理 | 第 5 章把 ECharts 列为独立体系（原图表写死 10/11px，不参与档位） |
| 可验收 | 每条分类都给出「具体页面 + 具体元素」引用，评审时可直接对屏核对 |

### 1.2 读者

- 产品经理：定义标准、评审取舍、排定整改优先级（第 3、6、7 章）
- 前端：按令牌收敛样式（第 4、8、9 章）
- QA：按分类对屏验收（第 3 章表格 + 第 4 章分区详述）

### 1.3 使用方式

1. 新增 UI 时，先在第 3 章总表里找到对应**分类**，直接引用其标准字号，不自造数值。
2. 自造数值时，必须在第 7 章登记为「待收敛项」，并给出对应的标准分类。
3. 三档切换验证方法见第 9 章。

### 1.4 与其他规范的关系

本规范与 `frontend/src/styles/tokens/colors.css`（颜色唯一来源）、
`frontend/src/styles/layout/content-surface.css`（12px 内容区圆角）共同构成 SPD 视觉标准三件套：

| 维度 | 唯一来源 | 本文档覆盖 |
| --- | --- | --- |
| 字号 | `styles/tokens/typography.css` | ✅ 全部 |
| 颜色 | `styles/tokens/colors.css` | ❌ 另见配色规范 |
| 圆角 / 边框 | `styles/tokens/colors.css` + `layout/content-surface.css` | ❌ 另见圆角规范 |

**关键原则**：本规范管「多大」，不管「什么颜色」与「什么圆角」。

---

## 2. 字体基础规范

### 2.1 字体族

| 用途 | 字体栈 | 定义位置 |
| --- | --- | --- |
| 正文 / UI | `Inter, "Microsoft YaHei", "PingFang SC", system-ui, sans-serif` | `styles/main.css` `:root` |
| 标题（h1–h6 / 页面主标题） | `--spd-font-family-display`：HarmonyOS Sans SC → Alibaba PuHuiTi → Source Han Sans SC → Noto Sans SC → Inter → PingFang SC → Microsoft YaHei | `styles/spd-theme.css`，`spd-override.css` 强制应用到 h1–h6 |
| 业务页面正文 | `--spd-font-family`：`-apple-system → Segoe UI → PingFang SC → Hiragino Sans GB → Microsoft YaHei → Helvetica Neue → Arial` | `styles/spd-theme.css`，`spd-override.css` `body` 强制 |
| 等宽（编码 / 单号 / 金额） | `--spd-font-family-mono`：`SF Mono → Monaco → Cascadia Mono → Roboto Mono → Consolas` | `styles/spd-theme.css` |
| 医疗 SaaS 层 | `"Segoe UI Variable", "HarmonyOS Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif` | `styles/medical-saas.css` `:root` |
| 登录页（独立体系） | `"PingFang SC", "Microsoft YaHei", system-ui, sans-serif` | `styles/views/login.css` `.standalone-login` |

> **说明**：目前存在 4 套并行字体栈（main.css 的 Inter 系、spd-theme.css 的 Ant 系、medical-saas.css 的 Segoe 系、login.css 的独立系）。CSS 加载顺序决定最终生效：`spd-override.css` 用 `!important` 把 body 锁定为 `--spd-font-family`，`medical-saas.css` 的 `:root` 字体栈因此对 body 无效。**属第 7 章 D-14 项，建议收敛为 2 套（业务 + 登录）**。

### 2.2 字重梯度

只允许使用 5 档字重，禁止 650/750 等非标准值（注：`medical-saas.css` 存在 650，属待整改项）。

| 字重 | 用途 | 出现位置 |
| --- | --- | --- |
| 400 / normal | 正文、表格数据、说明文字 | 表格数据单元格、弹窗正文 |
| 500 | 次级强调、辅助标签 | `.ulc-sub`、`.md-query label > span` |
| 600 | 强调、字段标签、状态条 | `.ulc-chip`、`.md-status`、`.ulc-strong` |
| 700 | 标题、主字段、按钮 | `.md-btn`、`.md-dialog-header h3`、`.ulc-strong` |
| 800 / 900 | 展示型数值、品牌标识 | `.md-stat-value`、`.spd-brand-mark` |

### 2.3 数字等宽（tabular-nums）

**必须**加 `font-variant-numeric: tabular-nums` 的位置：

| 位置 | 类名 | 参考元素 |
| --- | --- | --- |
| 表格等宽单元格 | `.ulc-mono` | 『科室库房目录』页表格『商品编码』列 |
| 表格时间列 | `.ulc-time` | 任意列表页『更新时间』列 |
| 结算金额列 | `.settlement-detail-table .numeric-cell` | 『结算对账』页结算明细表『结算金额』列 |
| 结算编码列表 | `.settlement-code-list` | 『结算对账』页结算明细表『UID / 唯一码』列 |
| 统计数值 | `.md-stat-value` | 基础数据页 4 联统计卡『医院商品总数』 |
| 分页页码 | `.hospital-page-indicator` / `.md-page-indicator` | 分页条当前页号 |
| AI 助手消息时间 | `.ai-medical-message time` | AI 助手气泡下方时间戳 |
| 驾驶舱 KPI | `.kpi-body strong` | 『操作驾驶舱』页 KPI 数值 |

**判定规则**：任何需要**跨行对齐**或**跨列比较**的数字，一律等宽。金额、数量、日期、编码、页码无一例外。

### 2.4 三档缩放机制

字号体系由 `<html data-font-size="standard|large|xlarge">` 驱动。

| 档位 | 基准 `--spd-fs-base` | 缩放比 | 适用场景 | 提示文案（`stores/theme.ts`） |
| --- | --- | --- | --- | --- |
| **standard**（默认） | 14px | 100% | 常规显示器 · 密度优先 | 密度最高，表格一屏显示行数最多 |
| **large** | 16px | 114% | 27 寸以上屏幕 · 轻度老花 | 平衡档，绝大多数用户推荐 |
| **xlarge** | 18px | 129% | 视力偏弱 · 高分辨率远距使用 | 一屏行数减少，需配合横向滚动 |

**机制细节**

1. `html { font-size: var(--spd-fs-base) }` —— 整站 `rem` 体系等比缩放。
2. `body { font-size: var(--spd-fs-base); line-height: var(--spd-lh-base) }`。
3. 表格单元格上下留白同步微调，避免大字号下版面松散：

| 档位 | `--spd-table-cell-padding-y` |
| --- | --- |
| standard | 10px |
| large | 12px |
| xlarge | 13px |

4. 字号档位持久化于 `localStorage['spd.theme.fontSize']`，由 `stores/theme.ts` 的 `applyFontSize()` 写入 `<html>`。
5. 用户入口：顶栏主题切换器 → 字号档位（`stores/theme.ts` `FONT_SIZES`）。

### 2.5 行高令牌

| 令牌 | 值 | 用途 | 已使用位置 |
| --- | --- | --- | --- |
| `--spd-lh-tight` | 1.35 | 按钮、状态条、紧凑标签 | `.ulc-chip`（实际用 1.2）、`.ulc-action`（1） |
| `--spd-lh-base` | 1.5 | 正文默认 | `body` |
| `--spd-lh-loose` | 1.65 | 长段落、说明文字 | 登录页左侧说明段落（实测 1.85）、`.module-card p`（1.6） |

> **现状缺口**：行高令牌定义了但**几乎没被使用**。实际行高散落在各处（1、1.2、1.3、1.35、1.4、1.45、1.5、1.55、1.6、1.65、1.7、1.85）。属第 7 章 D-13 项。

### 2.6 字号令牌速查表

`styles/tokens/typography.css` 定义 10 个尺寸令牌，全部由基准推导：

| 令牌 | 表达式 | standard(14) | large(16) | xlarge(18) | 定位 |
| --- | --- | --- | --- | --- | --- |
| `--spd-font-size-xs` | base − 3px | 11px | 13px | 15px | 微型标记、角标 |
| `--spd-font-size-sm` | base − 1px | 13px | 15px | 17px | 次级说明、辅助文本 |
| `--spd-font-size-base` | base | 14px | 16px | 18px | **表单标签、正文基准** |
| `--spd-font-size-md` | base + 1px | 15px | 17px | 19px | **表格数据单元格** |
| `--spd-font-size-lg` | base + 2px | 16px | 18px | 20px | 强调文本、表格主字段 |
| `--spd-font-size-xl` | base + 4px | 18px | 20px | 22px | 区块小标题 |
| `--spd-font-size-2xl` | base + 6px | 20px | 22px | 24px | 弹窗标题、统计数值 |
| `--spd-font-size-3xl` | base + 10px | 24px | 26px | 28px | 页面主标题 |
| `--spd-font-size-4xl` | base + 16px | 30px | 32px | 34px | 展示型大标题 |
| `--spd-font-size-5xl` | base + 24px | 38px | 40px | 42px | 统计数值（超大） |

> **重要**：`spd-theme.css` 里另有一份同名固定值（xs 12 / sm 13 / base 14 / md 15 / lg 16 / xl 18 / 2xl 20 / 3xl 24 / 4xl 30 / 5xl 38）。
> `typography.css` 在 `main.css` 中**后于** `spd-theme.css` 引入，因此令牌跟随档位生效；
> 但 `spd-theme.css` 内部的**工具类**（`.text-tertiary` 写死 12px、`.spd-tag` 写死 12px 等）仍为固定值。

---

## 3. 字体分类总表（核心章节）

> **数值来源标注**：
> - **实测** = 浏览器 `getComputedStyle` 确认；
> - **CSS** = 从样式表规则推断（未在浏览器中逐一验证）；
> - **令牌** = 由 `--spd-font-size-*` 推导，三档值必然成立。
>
> **三档列**：`—` 表示该元素当前**不跟随字号档位**（写死 px），属待整改项。

### 3.1 一级分类：全局框架（Global Chrome）

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置（页面 · 元素） |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| G-01 | 顶栏品牌文字 | 16px | 800 | 1.2 | 16px | — | — | `.spd-topnav-brand-text` | 全站顶栏左侧『SPD 院内供应链』 |
| G-02 | 顶栏主菜单项 | 14px | 500 | 1.4 | 14px | — | — | `.spd-menu-label` | 全站顶栏『工作台』『基础数据管理』『供应链业务』 |
| G-03 | 顶栏下拉分组标题 | 12px | 700 | 1.4 | 12px | — | — | `.spd-dropdown-group-title` | 顶栏『供应链业务』展开后的『库存与仓储』『采购与结算』分组标题 |
| G-04 | 顶栏下拉叶子链接 | 13.5px | 500 | 1.4 | 13.5px | — | — | `.spd-dropdown-link-name` | 顶栏下拉『科室库房目录』『库存管理』 |
| G-05 | 顶栏图标按钮 | 图标 16px | — | — | 16px | — | — | `.spd-icon-btn` | 顶栏右侧『刷新数据』『消息通知』 |
| G-06 | 顶栏用户姓名 | 13px | 600 | 1.4 | 13px | — | — | `.spd-user-name` | 顶栏右侧用户胶囊『张伟』 |
| G-07 | 顶栏用户头像文字 | 13px | 700 | 1 | 13px | — | — | `.spd-user-avatar` | 顶栏右侧圆形头像内的首字 |
| G-08 | 用户下拉菜单项 | 13px | 400 | 1.4 | 13px | — | — | `:deep(.el-dropdown-menu__item)` | 用户菜单『修改密码』『退出登录』 |
| G-09 | 页签栏页签文字 | 14px | 400 / 激活 700 | 1.4 | 14px | — | — | `.spd-tabbar-tab` | 页签栏『工作台』『科室库房目录』 |
| G-10 | 迷你侧栏分组标题 | 12px | 600 | 1.4 | 12px | — | — | `.spd-mini-sidebar-title` | 迷你侧栏顶部『业务模块』大写标题 |
| G-11 | 迷你侧栏一级项 | 13px | 500 / 激活 600 | 1.4 | 13px | — | — | `.spd-mini-nav-item` | 迷你侧栏『库存管理』 |
| G-12 | 迷你侧栏二级分组 | 12.5px | 600 | 1.4 | 12.5px | — | — | `.spd-mini-nav-group-title` | 迷你侧栏『采购与结算』分组 |
| G-13 | 迷你侧栏计数徽标 | 11px | 500 | 1 | 11px | — | — | `.spd-mini-nav-group-count` | 迷你侧栏分组右侧数字角标 |
| G-14 | 左侧主菜单（备选布局） | 14px | 400 | 1.4 | 14px | — | — | `.nav-item.child` / `.nav-item.grandchild`(13px) | 左菜单『科室库房目录』项 |
| G-15 | 面包屑 | 13px | 400 / 当前 550 | 1.5 | 13px | — | — | `.breadcrumb-line` | 页面顶部『基础数据管理 / 科室库房目录』 |
| G-16 | 页脚 | 11px | 400 | 1.5 | 11px | — | — | `.fli-footer` | 页面底部版权行 |

### 3.2 一级分类：页面标题区（Page Heading）

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置（页面 · 元素） |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P-01 | 页面主标题（标准页） | 22px | 600 | 1.3 | 22px | — | — | `.master-page-title h2` | 任意基础数据页顶部的页面名 |
| P-02 | 页面主标题（Ant 覆盖层） | 20px | 600 | 1.4 | 20px | — | — | `.standard-page-heading h2` | 『DataTable 通用表格演示』页『DataTable 通用表格演示』标题 |
| P-03 | 页面主标题（响应式 clamp） | 22–26px | 650 | 1.3 | clamp(22px,1.55vw,26px) | — | — | `.master-page-title h2` 组（medical-saas） | 宽屏下的基础数据页标题 |
| P-04 | 页面副标题 / 描述 | 13px | 400 | 1.5 | 13px | — | — | `.master-page-title p` | 页面标题下的说明文字 |
| P-05 | 区块小标题（面板） | 15px | 600 | 1.4 | 15px | — | — | `.md-panel-title` | 面板标题条『科室库房目录』 |
| P-06 | 区块小标题（section） | 18px | 700 | 1.3 | 18px | — | — | `.section-title h2` | 审批详情页各流程区块标题 |
| P-07 | 区块小标题（紧凑） | 15px | 700 | 1.4 | 15px | — | — | `.section-title.compact h3` | 拣配工作台左侧『拣配任务』 |
| P-08 | 弹窗章节标题 | 13px | 600 | 1.4 | 13px | — | — | `.md-form-section h4` | 弹窗内『基本信息』『资质信息』小节 |
| P-09 | 段标题（列表卡片） | 15px | 600 | 1.4 | 15px | — | — | `.dept-req-section-title h3` | 『科室申领』页『历史申领单』 |
| P-10 | 段标题右侧元信息 | 12px | 400 | 1.4 | 12px | — | — | `.dept-req-section-title > span` | 同上右侧的计数 |

> **重要约束**：`main.css` 第 128–140 行有一组 `display: none !important` 规则
> （注释写明「Hide explanatory helper copy across feature pages」），隐藏了
> `.standard-page-heading > p`、`.workbench-hero p`、`.detail-heading > div > p`、
> `.section-title small`、`.subnav-panel small` 等**说明性文案**。
> 这意味着 P-04 类副标题在多数页面**当前不可见**。这是产品方向（去掉顶部概览），不是缺陷；
> 但 `MdPageHeader` 组件仍接收 `description` prop，属于**接口与实际展示不一致**，见 D-11。

### 3.3 一级分类：数据呈现 —— 表格（Table）

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置（页面 · 元素） |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T-01 | 统一列表表头 | 14px | 700 | 1.3 | 14px | 14px | 14px | `.ulc-table .el-table__header th .cell` | 『科室库房目录』页表头『商品名称』 |
| T-02 | 统一列表数据单元格 | 15px | 400 | 1.45 | 15px | 17px | 19px | `.ulc-table td.el-table__cell` | 同上『商品名称』列的『聚维酮碘消毒剂皮肤外用消毒液 500ml 棕色瓶装』 |
| T-03 | 单元格主字段（加粗） | 16px | 700 | 1.4 | 16px | 18px | 20px | `.ulc-strong` | 同上商品名称列的主标题行 |
| T-04 | 单元格次级说明 | 13px | 500 | 1.4 | 13px | 15px | 17px | `.ulc-sub` / `.ulc-wrap-text.is-sub` | 同上商品名称列下的『科室可申领商品』 |
| T-05 | 单元格等宽（编码） | 15px | 400 | 1.45 | 15px | 17px | 19px | `.ulc-mono` | 任意列表页『商品编码』列 |
| T-06 | 单元格时间 | 15px | 400 | 1.45 | 15px | 17px | 19px | `.ulc-time` | 任意列表页『更新时间』列 |
| T-07 | 单元格长文本（换行） | 15px | 400 | 1.45 | 15px | 17px | 19px | `.ulc-wrap-text` | 『科室库房目录』页『生产厂家』列 |
| T-08 | md-* 表格数据 | 13px | 400 | 1.5 | 13px | — | — | `.md-table` | 基础数据 md-* 列表的数据行 |
| T-09 | md-* 表格表头 | 12px | 600 | 1.4 | 12px | — | — | `.md-table thead th` | 同上表头行 |
| T-10 | md-* 单元格主名 | 13px | 600 | 1.4 | 13px | — | — | `.md-cell-name strong` | 同上『商品名称』列的主名 |
| T-11 | md-* 单元格副名 | 11px | 400 | 1.4 | 11px | — | — | `.md-cell-name small` | 同上主名下的规格说明 |
| T-12 | md-* 单元格等宽 | 12px | 400 | 1.4 | 12px | — | — | `.md-cell-mono` | 同上编码列 |
| T-13 | 原生 el-table 数据 | 14px | 400 | 1.5 | 14px | — | — | `.el-table` / `.spd-data-table__el` | 『DataTable 通用表格演示』页表格数据行 |
| T-14 | 原生 el-table 表头 | 14px | 600 | 1.5 | 14px | — | — | `.el-table th.el-table__cell` | 同上表头『商品编码』『规格型号』 |
| T-15 | `.master-table` 数据 | 14px | 400 | 1.5 | 14px | — | — | `.master-table` | 基础数据页原生表格 |
| T-16 | 科室申领目录表数据 | 12px | 400 | 1.45 | 12px | — | — | `.dept-req-catalog-body tbody td` | 『科室申领』弹窗内『科室申领目录』表数据行 |
| T-17 | 科室申领目录表头 | 12px | 600 | 1.4 | 12px | — | — | `.dept-req-catalog-body thead th` | 同上表头『商品编码』『散货可用』 |
| T-18 | 科室申领历史表数据 | 13px | 400 | 1.5 | 13px | — | — | `.dept-req-table` | 『科室申领』页『历史申领单』表 |
| T-19 | 科室申领历史表头 | 12px | 600 | 1.4 | 12px | — | — | `.dept-req-table th` | 同上表头『单据号』『请购人』 |
| T-20 | 打印模板字段表 | 13px | 400 | 1.5 | 13px | — | — | `.md-print-template-table` | 『打印模板设置』页字段列表 |
| T-21 | 打印模板表头 | 12px | 600 | 1.4 | 12px | — | — | `.md-print-template-table thead th` | 同上表头『字段名称』 |

### 3.4 一级分类：数据呈现 —— 状态与标签（Status & Tags）

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置（页面 · 元素） |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S-01 | 统一列表状态条 | 14px | 600 | 1.2 | 14px | 16px | 18px | `.ulc-chip` | 『科室库房目录』页『状态』列『启用』/『停用』 |
| S-02 | 统一列表文本标签 | 14px | 600 | 1.2 | 14px | 16px | 18px | `.ulc-tag` | 同页『集采』『重点监控』标签 |
| S-03 | 状态条圆点 | 7px 直径 | — | — | 固定 | 固定 | 固定 | `.ulc-chip::before` | 状态条左侧小圆点 |
| S-04 | md-* 状态徽章 | 12px | 600 | 1.4 | 12px | — | — | `.md-status` | 基础数据 md-* 列表『状态』列 |
| S-05 | md-* 通用 chip | 12px | 600 | 1.4 | 12px | — | — | `.md-chip` | 工具栏右侧『已选 3 条』信息 chip |
| S-06 | 工具栏计数 chip | 12px | 600 | 1.4 | 12px | — | — | `.md-table-toolbar-info .md-chip` | 同上 |
| S-07 | Tab 计数徽标 | 11px | 600 | 1 | 11px | — | — | `.md-tab .md-tab-count` | Tab 上的数字角标 |
| S-08 | `spd-tag` 胶囊标签 | 12px | 500 | 1.4 | 12px | — | — | `.spd-tag` | spd-theme 组件库标签 |
| S-09 | `status-badge` / `status-pill` | 继承 body 14px | 400 | 1.5 | 14px | — | — | `.status-badge` | 『科室申领』页『历史申领单』『状态』列 |
| S-10 | 包标签 chip | 12px | 400 | 1.4 | 12px | — | — | `.package-label-chip` | 定数包页商品行下的包规格 chip |
| S-11 | 盘点范围 chip | 12px | 400 | 1.4 | 12px | — | — | `.stocktaking-scope-chip` | 『库存盘点』弹窗『盘点范围』选择 chip |
| S-12 | 库房等级 / 库位标签 | 12px | 400 | 1.4 | 12px | — | — | `.md-cell-name small` / `.spd-storage-name` | 库存页库房条目名 |
| S-13 | 驾驶舱风险等级标记 | 10px | 600 | 1 | 10px | — | — | `.spd-warning-level-text` | 『操作驾驶舱』页预警条目左侧等级标 |
| S-14 | 结算策略标记 | 10.5px | 600 | 1.4 | 10.5px | — | — | `.settlement-policy-list b` | 『结算对账』页结算明细表『带量』『医保』前缀标 |

### 3.5 一级分类：数据录入 —— 筛选区（Filter）

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置（页面 · 元素） |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F-01 | 统一列表筛选标签 | 14px | 700 | 1.4 | 14px | 14px | 14px | `.ulc-filter label > span` | 任意统一列表页筛选区『商品编码』『规格型号』 |
| F-02 | 统一列表输入框 / 下拉文字 | 13px | 400 | 1.4 | 13px | 13px | 13px | `.ulc-filter .el-input__wrapper` / `.md-input` | 同上筛选框内输入文字 |
| F-03 | 统一列表筛选按钮 | 14px | 700 | 1 | 14px | 14px | 14px | `.ulc-filter-actions .md-btn` | 同上『查询』『重置』按钮 |
| F-04 | md-* 查询区标签 | 12px | 500 | 1.4 | 12px | — | — | `.md-query label > span` | md-* 列表页筛选区字段标签 |
| F-05 | md-* 输入框文字 | 13px | 400 | 1.4 | 13px | — | — | `.md-input` / `.md-select` / `.md-textarea` | 同上输入框 |
| F-06 | 科室申领筛选标签 | 11px | 500 | 1.4 | 11px | — | — | `.dept-req-filterbar-item > span` | 『科室申领』弹窗筛选条『科室』『收货库房』 |
| F-07 | 科室申领筛选控件 | 12px | 400 | 1.4 | 12px | — | — | `.dept-req-filterbar-item > input/select` | 同上筛选下拉框内文字 |
| F-08 | 科室申领筛选按钮 | 12px | 600 | 1 | 12px | — | — | `.dept-req-filterbar-actions .btn` | 同上『查询』『重置』 |
| F-09 | 主搜索框文字 | 14px | 400 | 1.4 | 14px | — | — | `.master-search input` | 基础数据页顶部搜索框 |
| F-10 | 搜索条汇总提示 | 13px | 500 | 1.4 | 13px | — | — | `.spd-data-table__hint` | 『DataTable 通用表格演示』页工具栏『已选 3 条 · 共 168 条』 |
| F-11 | 输入框占位符 | 继承输入框字号 | 400 | — | 随控件 | 随控件 | 随控件 | `::placeholder` | 任意搜索框灰色提示文字 |

### 3.6 一级分类：数据录入 —— 表单（Form）

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置（页面 · 元素） |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| R-01 | md-* 表单字段标签 | 12px | 500 | 1.4 | 12px | — | — | `.md-form-grid label > span` | 基础数据弹窗『商品名称』『规格型号』字段标签 |
| R-02 | md-* 字段辅助说明 | 12px | 400 | 1.4 | 12px | — | — | `.md-form-grid small` / `.md-form-hint` | 同上字段下的提示文字 |
| R-03 | 必填星号 | 继承标签 | 600 | — | 随标签 | 随标签 | 随标签 | `.md-required` | 同上标签前的红色 `*` |
| R-04 | Element Plus 表单标签 | 14px | 400 | 1.5 | 14px | — | — | `.el-form-item__label` | 弹窗内 el-form 标签 |
| R-05 | 自绘弹窗表单标签 | 13px | 700 | 1.4 | 13px | — | — | `.supplier-form-grid span` / `.hospital-query-grid span` | 供应商弹窗字段标签 |
| R-06 | 弹窗正文基准 | 13px | 400 | 1.5 | 13px | — | — | `.modal-body` / `.el-dialog__body` | 弹窗 body 区域 |
| R-07 | 表单校验错误提示 | 13px | 400 | 1.5 | 13px | — | — | `.md-error` | 表单校验失败红条 |
| R-08 | 字数 / 计数提示 | 12px | 400 | 1.4 | 12px | — | — | `.md-form-hint` | 文本域下方『12/200』 |
| R-09 | 复选 / 单选标签 | 14px | 400 | 1.5 | 14px | — | — | 继承 body | 弹窗内复选框旁文字 |
| R-10 | 数量步进器数字 | 13px | 400 | 1.4 | 13px | — | — | `.qty-stepper input` | 『科室申领』已选清单『申领数量』输入框 |

### 3.7 一级分类：操作 —— 按钮（Button）

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置（页面 · 元素） |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| B-01 | 主操作按钮 | 14px | 600 | 1 | 14px | 16px | 18px | `--btn-font-size`（`.main-area .btn` / `.el-button`） | 任意页面主按钮『新增商品』 |
| B-02 | md-* 工具栏按钮 | 14px | 600 | 1 | 14px | 16px | 18px | `.md-btn`（unified-list 覆盖） | 列表工具栏『新增』『批量导入』 |
| B-03 | md-* 按钮（独立体系） | 13px | 600 | 1 | 13px | — | — | `.md-btn`（master-data.theme） | md-* 页面工具栏按钮 |
| B-04 | 统一列表行内操作按钮 | 14px | 600 | 1 | 14px | 16px | 18px | `.ulc-action` | 『科室库房目录』行内『编辑』『删除』 |
| B-05 | 文字按钮 | 14px | 600 | 1 | 14px | — | — | `.btn-text` / `.md-btn-text` | 行内『移除』『查看明细』 |
| B-06 | 小号按钮 | 12px | 600 | 1 | 12px | — | — | `.btn-sm` / `.md-btn-sm` / `.el-button--small` | 分页『上一页』『下一页』 |
| B-07 | 图标按钮 | 图标 16–18px | — | — | 固定 | 固定 | 固定 | `.btn-icon` / `.md-btn-icon` | 弹窗右上角关闭 `✕` |
| B-08 | 链接式按钮 | 13px | 600 | 1.4 | 13px | — | — | `.btn-link` / `.spd-row-action` | 『DataTable 通用表格演示』页行内『查看』『编辑』 |
| B-09 | 批次 / 下拉菜单按钮 | 13px | 600 | 1 | 13px | — | — | `.batch-edit-menu` 内 `.btn` | 基础数据页『批量维护』下拉 |
| B-10 | 弹窗底部按钮 | 14px | 600 | 1 | 14px | — | — | `.md-dialog-footer .md-btn` | 弹窗底部『取消』『保存』 |
| B-11 | 驾驶舱面板按钮 | 12px | 700 | 1 | 12px | — | — | `.alert-panel > header button` | 『操作驾驶舱』页『查看全部』 |

### 3.8 一级分类：浮层 —— 弹窗与提示（Overlay）

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置（页面 · 元素） |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| O-01 | Element Plus 弹窗标题 | 16px | 600 | 1.3 | 16px | — | — | `.el-dialog__title` | 任意 el-dialog 标题 |
| O-02 | 自绘弹窗标题 | 16px | 600 | 1.3 | 16px | — | — | `.modal-heading h3` | 供应商 / 厂家自绘弹窗标题 |
| O-03 | md-* 弹窗标题 | 16px | 600 | 1.4 | 16px | — | — | `.md-dialog-header h3` | md-* 弹窗标题 |
| O-04 | 弹窗副标题 | 12px / 12.5px | 400 | 1.4 | 12.5px | — | — | `.modal-heading p` / `.md-dialog-header p` | 弹窗标题下的『待审批目录』 |
| O-05 | 确认框标题 | 16px | 600 | 1.5 | 16px | — | — | `.el-message-box__title` | 删除确认弹窗标题 |
| O-06 | 确认框正文 | 14px | 400 | 1.7 | 14px | — | — | `.el-message-box__message` | 确认框正文段落 |
| O-07 | 弹窗内表格 | 12.5px | 400 | 1.5 | 12.5px | — | — | `.el-dialog .el-table` | 弹窗内嵌表格数据 |
| O-08 | 下拉选项 | 13px | 400 | 1 | 13px | — | — | `.el-select-dropdown__item` | 任意下拉菜单项 |
| O-09 | Tooltip | 12px | 400 | 1.5 | 12px | — | — | Element Plus 默认 | 表格单元格悬浮提示 |
| O-10 | 列设置浮层标题 | 14px | 700 | 1.4 | 14px | — | — | `.ulc-col-settings-pop header strong` | 列设置浮层『显示列设置』 |
| O-11 | 列设置浮层链接 | 13px | 700 | 1.4 | 13px | — | — | `.ulc-col-settings-pop .ulc-link` | 浮层右上『重置』 |
| O-12 | 列设置浮层列项 | 14px | 400 | 1.4 | 14px | — | — | `.ulc-col-settings-item` | 浮层内可勾选列名 |
| O-13 | 列设置浮层宽度标注 | 12px | 400 | 1.4 | 12px | — | — | `.ulc-col-settings-item small` | 列名右侧『120px』 |
| O-14 | 全局消息提示 | 13px | 400 | 1.5 | 13px | — | — | `.md-message` / `.el-message` | 顶部『保存成功』提示条 |
| O-15 | 错误提示条 | 13px | 400 | 1.5 | 13px | — | — | `.md-error` | 表单校验失败条 |
| O-16 | 科室申领弹窗标题 | 17px | 600 | 1.3 | 17px | — | — | `.dept-req-dialog-title > span` | 『新增科室申领』弹窗标题 |

### 3.9 一级分类：分页（Pagination）

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置（页面 · 元素） |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| N-01 | 分页汇总文案 | 13px | 400 | 1.5 | 13px | 13px | 13px | `.hospital-pagination` | 任意列表底『显示 1-20 条，共 168 条』 |
| N-02 | 分页页码 / 上下页按钮 | 13px | 500 | 1 | 13px | — | — | `.hospital-pagination .btn` | 分页按钮『上一页』『1』『2』 |
| N-03 | 分页输入框 | 13px | 400 | 1 | 13px | — | — | `.hospital-pagination input` | 分页『每页 [20] 条』输入框 |
| N-04 | 分页当前页指示 | 13px | 600 | 1 | 13px | — | — | `.hospital-page-indicator` | 分页条『第 1 / 9 页』 |
| N-05 | md-* 分页容器 | 13px | 400 | 1.5 | 13px | — | — | `.md-pagination` | md-* 列表分页条 |
| N-06 | md-* 分页按钮 | 12px | 500 | 1 | 12px | — | — | `.md-pagination .md-btn` | md-* 分页页码按钮 |

### 3.10 一级分类：统计与看板（Metrics & Dashboard）

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置（页面 · 元素） |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M-01 | md-* 统计数值 | 30px | 800 | 1.1 | 30px | — | — | `.md-stat-value` | 基础数据页统计卡『医院商品总数』数值 |
| M-02 | md-* 统计标签 | 13px | 500 | 1.4 | 13px | — | — | `.md-stat-label` | 同上数值下方标签 |
| M-03 | md-* 统计单位 / 副标 | 12px | 500 | 1.4 | 12px | — | — | `.md-stat-value small` | 同上数值右侧单位 |
| M-04 | `spd-stat-value`（Ant 体系） | 38px | 800 | 1 | 38px | — | — | `.spd-stat-value` | spd-theme 统计卡数值 |
| M-05 | `metric-panel` 数值 | 24px | 700 | 1.2 | 24px | — | — | `.metric-panel .metric-value` | 审批页指标面板数值 |
| M-06 | `metric-panel` 标签 | 13px | 400 | 1.4 | 13px | — | — | `.metric-panel .metric-label` | 同上标签 |
| M-07 | 驾驶舱 KPI 数值 | 32px | 800 | 1.1 | 32px | — | — | `.kpi-body strong` | 『操作驾驶舱』页 KPI 数值 |
| M-08 | 驾驶舱 KPI 标签 | 12px | 400 | 1.4 | 12px | — | — | `.kpi-body p` | 同上 KPI 名称 |
| M-09 | 驾驶舱 KPI 环比 | 12px | 400 | 1.4 | 12px | — | — | `.kpi-body small` | 同上环比文字 |
| M-10 | 驾驶舱 6 宫格面板标题 | 15px | 700 | 1.4 | 15px | — | — | `.cockpit-panel h2` | 『操作驾驶舱』页『耗材消费构成』 |
| M-11 | 驾驶舱环形图中心值 | 22px | 800 | 1 | 22px | — | — | `.category-donut strong` | 同上环形图中心百分比 |
| M-12 | 驾驶舱图例标签 | 13px | 400 | 1.4 | 13px | — | — | `.category-legend .legend-label` | 同上右侧图例『高值耗材』 |
| M-13 | 驾驶舱图例数值 | 15px | 700 | 1.2 | 15px | — | — | `.category-legend strong` | 同上图例右侧数值 |
| M-14 | 驾驶舱时钟 | 30px | 700 | 1 | 30px | — | — | `.cockpit-clock strong` | 『操作驾驶舱』页右上角时钟 |
| M-15 | 驾驶舱标题 | clamp(26px, 2.4vw, 38px) | 800 | 1.1 | clamp | clamp | clamp | `.cockpit-title h1` | 同上页主标题 |
| M-16 | 待审批 KPI 卡数值 | 24px | 800 | 1.2 | 24px | — | — | `.approval-metric-card` | 『待审批目录』页 KPI 数值 |
| M-17 | 进度条文字 | 继承 14px | 400 | 1.4 | 14px | — | — | `.el-progress-text` | 审批进度条内百分比 |

### 3.11 一级分类：看板 / 工作台模块（Workbench）

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置（页面 · 元素） |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| W-01 | 模块卡片标题 | 17px | 700 | 1.3 | 17px | — | — | `.module-card h3` | 工作台模块卡标题 |
| W-02 | 模块卡片描述 | 14px | 400 | 1.6 | 14px | — | — | `.module-card p` | 同上描述 |
| W-03 | 流程条节点 | 14px | 400 | 1.4 | 14px | — | — | `.flow-line span` | 审批详情页流程条节点 |
| W-04 | 特性行文字 | 14px | 400 | 1.5 | 14px | — | — | `.feature-row` | 工作台功能入口行 |
| W-05 | 待办列表标题 | 12px | 500 | 1.4 | 12px | — | — | `.fli-todo-title` | 工作台待办列表 |
| W-06 | 待办单号 | 11px | 400 | 1.4 | 11px | — | — | `.fli-todo-id` | 同上单号 |
| W-07 | 审批流节点标题 | 16px | 600 | 1.4 | 16px | — | — | `.spd-approval-flow-node` | 『审批流设置』页节点名 |
| W-08 | 审批流节点说明 | 12px | 400 | 1.4 | 12px | — | — | 同上 | 同上说明文字 |

### 3.12 一级分类：结算 / 对账专用（Settlement）

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置（页面 · 元素） |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| L-01 | 结算汇总条 | 13px | 800 | 1.4 | 13px | — | — | `.settlement-table-summary` | 『结算对账』页明细表上方汇总条 |
| L-02 | 结算汇总提示 | 12px | 500 | 1.4 | 12px | — | — | `.settlement-table-hint` | 同上汇总条右侧说明 |
| L-03 | 结算明细表头 | 12px | 600 | 1.4 | 12px | — | — | `.settlement-detail-table thead th` | 同上表头『结算单号』『科室』 |
| L-04 | 结算明细主字段 | 14px | 800 | 1.4 | 14px | — | — | `.settlement-cell-stack > strong` | 同上『聚维酮碘消毒剂…』 |
| L-05 | 结算明细元信息 | 11.5px | 400 | 1.4 | 11.5px | — | — | `.settlement-cell-meta` | 同上『编码：』『注册证：』 |
| L-06 | 结算金额 | 14px | 800 | 1.4 | 14px | — | — | `.settlement-detail-table .amount-cell` | 同上『结算金额』列 |
| L-07 | 结算编码列表（等宽） | 11.5px | 400 | 1.4 | 11.5px | — | — | `.settlement-code-list` | 同上『UID / 唯一码』列 |
| L-08 | 结算确认按钮 | 14px | 800 | 1 | 14px | — | — | `.settlement-confirm-btn` | 同上『确认结算』按钮 |
| L-09 | 结算已确认标记 | 12px | 700 | 1.4 | 12px | — | — | `.settlement-confirmed-text` | 同上『已确认』文字 |

### 3.13 一级分类：库存专用（Inventory）

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置（页面 · 元素） |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| I-01 | 盘点范围标题 | 14px | 700 | 1.4 | 14px | — | — | `.stocktaking-scope-head strong` | 『库存盘点』弹窗『盘点范围』标题 |
| I-02 | 盘点范围说明 | 12px | 400 | 1.4 | 12px | — | — | `.stocktaking-scope-head span` | 同上说明 |
| I-03 | 盘点已选提示 | 13px | 400 | 1.4 | 13px | — | — | `.stocktaking-scope-selected` | 同上已选范围 |
| I-04 | 盘点数量输入 | 13px | 400 | 1.4 | 13px | — | — | `.stocktaking-qty-input` | 『盘点明细』弹窗实盘数量输入框 |
| I-05 | 库位层级标签 | 12px | 400 | 1.4 | 12px | — | — | `.md-cell-name small` | 库存页库房 / 库位层级路径 |
| I-06 | 补货分析汇总 | 14px | 700 | 1.4 | 14px | — | — | `.analysis-summary` | 『补货分析』弹窗汇总条 |
| I-07 | 拣配工作台紧凑标题 | 15px | 700 | 1.4 | 15px | — | — | `.picking-panel .section-title.compact h3` | 『拣配记录』页左侧『拣配任务』 |
| I-08 | 拣配数量输入 | 14px | 800 | 1.4 | 14px | — | — | `.analysis-qty-input` | 拣配弹窗数量输入框 |

### 3.14 一级分类：登录页（Login，独立体系，不参与档位）

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
| Lg-12 | 表单标签 | 12px | 600 | 1.4 | `.login-panel label > .lbl` | 『用户名』『密码』 |
| Lg-13 | 表单输入框 | 14px | 400 | 1.4 | `.login-field input` | 用户名 / 密码输入框 |
| Lg-14 | 记住账号 / 忘记密码 | 12px | 500 / 600 | 1.4 | `.login-panel-row` / `.forgot` | 『记住账号』勾选项、『忘记密码？』 |
| Lg-15 | 登录错误提示 | 12px | 400 | 1.55 | `.login-error` | 『用户名或密码错误』 |
| Lg-16 | 登录按钮 | 14px | 600 | 1 | `.login-submit` | 『登 录』按钮 |
| Lg-17 | 面板底部说明 | 11px | 400 | 1.4 | `.login-panel-foot` | 右侧面板最底部版权 |

### 3.15 一级分类：AI 助手（AI Assistant）

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A-01 | 悬浮球文字 | 17px | 800 | 1 | 17px | — | — | `.ai-medical-fab` | 右下角『AI医护助手』悬浮球 |
| A-02 | 助手窗口标题 | 21px | 800 | 1.1 | 21px | — | — | `.ai-medical-drag-title strong` | 助手面板左上『AI医护助手』 |
| A-03 | 助手窗口副标 | 13px | 400 | 1.4 | 13px | — | — | `.ai-medical-drag-title span` | 同上『院内SPD智能协作』 |
| A-04 | 欢迎语 | 15px | 700 | 1.4 | 15px | — | — | `.ai-medical-welcome strong` | 『您好，我是您的AI医护助手』 |
| A-05 | 快捷问题按钮 | 13px | 700 | 1.4 | 13px | — | — | `.ai-medical-quick-grid button` | 『查库存』『查消耗』等快捷入口 |
| A-06 | 用户气泡文字 | 13px | 400 | 1.5 | 13px | — | — | `.ai-medical-user-bubble p` | 右侧用户提问气泡 |
| A-07 | 助手回复正文 | 13px | 400 | 1.65 | 13px | — | — | `.ai-medical-answer p` / `.ai-response-text` | 左侧助手回复段落 |
| A-08 | 回复列表项 | 12px | 400 | 1.55 | 12px | — | — | `.ai-medical-answer li` | 回复中的分点说明 |
| A-09 | 消息时间戳 | 11px | 400 | 1.4 | 11px | — | — | `.ai-medical-message time` | 气泡下方时间 |
| A-10 | 输入框文字 | 14px | 400 | 1.55 | 14px | — | — | `.ai-medical-input-wrap textarea` | 助手底部输入框 |
| A-11 | 输入框占位符 | 14px | 400 | 1.55 | 14px | — | — | `::placeholder` | 『输入SPD业务问题，或上传耗材图片进行识别…』 |
| A-12 | 输入框下方免责说明 | 10px | 400 | 1.4 | 10px | — | — | `.ai-medical-composer small` | 『AI可识别图片及解答流程；实时业务数据请通过业务页面查询』 |

### 3.16 一级分类：图表（Chart，独立体系，不参与档位）

| # | 分类 | 标准字号 | 字重 | 行高 | CSS / 配置 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- |
| C-01 | X 轴刻度标签 | **10px** | 400 | 1 | `xAxis.axisLabel.fontSize` | 工作台『采购趋势』图表 X 轴月份 |
| C-02 | Y 轴刻度标签 | **10px** | 400 | 1 | `yAxis.axisLabel.fontSize` | 同上 Y 轴数值刻度 |
| C-03 | Y 轴名称（单位） | **10px** | 400 | 1 | `yAxis.nameTextStyle.fontSize` | 同上『万元』 |
| C-04 | 图例文字 | **11px** | 400 | 1 | `legend.textStyle.fontSize` | 同上右上角图例 |
| C-05 | 坐标轴名称 | 12px | 600 | 1 | 建议值 | 同上轴标题 |
| C-06 | 数据标签 | 12px | 600 | 1 | 建议值（当前未启用） | 建议用于柱顶数值 |
| C-07 | Tooltip 正文 | 12px | 400 | 1.5 | 建议值（当前未显式设置，ECharts 默认 12px） | 图表悬浮提示 |

> **图表是全站唯一的「完全脱离字号档位」的独立体系**，详见第 5 章。

### 3.17 一级分类：空态 / 加载 / 错误（Empty / Loading / Error）

| # | 分类 | 标准字号 | 字重 | 行高 | standard | large | xlarge | CSS 类名 | 参考实现位置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| E-01 | 统一列表空态 | 14px | 400 | 1.5 | 14px | 14px | 14px | `.ulc-table .el-table__empty-text` | 任意列表『暂无数据』 |
| E-02 | md-* 空态标题 | 14px | 600 | 1.4 | 14px | — | — | `.md-empty strong` | md-* 空态组件标题 |
| E-03 | md-* 空态说明 | 12px | 400 | 1.4 | 12px | — | — | `.md-empty p` | 同上说明文字 |
| E-04 | md-* 加载提示 | 14px | 400 | 1.5 | 14px | — | — | `.md-loading` | 加载中提示 |
| E-05 | 原生空态 | 14px | 400 | 1.5 | 14px | — | — | `.spd-data-table__empty-text` | 『DataTable 通用表格演示』页空态 |
| E-06 | 标准空态文案 | 14px | 400 | 1.5 | 14px | — | — | `.standard-empty-state` | 页面级空态 |
| E-07 | 打印模板空态 | 13px | 400 | 1.5 | 13px | — | — | `.md-print-template-field-empty` | 『打印模板设置』页字段区空态 |

---

## 4. 分区详述

> 每个分区给出「现状 → 标准 → 元素引用」。

### 4.1 全局框架

**顶栏（`.spd-topnav`）**
当前是一套自成一体的 14px 体系（菜单 14px / 下拉 13.5px / 用户 13px / 图标 16px），但全部写死，**不跟随字号档位**。
标准：菜单项 14px/500、下拉叶子 13.5px/500、分组标题 12px/700、用户姓名 13px/600。
元素引用：顶栏『供应链业务』为 14px；展开后『库存与仓储』分组标题 12px；分组下『科室库房目录』13.5px；右上角『张伟』13px。

**左侧主菜单（`.nav-item`）**（备选布局，当前 SPD 已收敛到顶部导航）
`layout-shell.css` 定义三级：`.nav-item` 继承 body 14px、`.nav-item.child` 14px、`.nav-item.grandchild` 13px + muted 色。
标准：保持 14 / 14 / 13 三级递减，但**跟随档位**（大档 16/16/15，特大档 18/18/17）。
元素引用：『科室库房目录』页左侧菜单『科室库房目录』项为 14px，三级项如『商品目录』13px。

**迷你侧栏（`.spd-mini-sidebar`）**
标题 12px 大写（`spd-override.css` 强制 `11px !important`）、一级项 13px、二级分组 12.5px、计数 11px。
标准：标题 12px（取消 11px 强制）、一级项 13px、二级 12.5px、计数 11px 保留。
元素引用：迷你侧栏『库存管理』13px、其下分组『采购与结算』12.5px。

**页签栏（`.spd-tabbar`）**
`.spd-tabbar-tab` 未显式设 `font-size`，继承 body → 跟随档位（14/16/18）。
标准：14px/400，激活态 700。
元素引用：页签『工作台』『科室库房目录』。

**面包屑（`.breadcrumb-line`）**
13px / 400，当前项 550（`medical-saas.css` 覆盖）。写死不跟随档位。
标准：13px/400。

### 4.2 页面标题区

三套并存的页面标题方案：

| 方案 | 类名 | 标题 | 副标题 | 使用页面 |
| --- | --- | --- | --- | --- |
| A（基础数据 / md-*） | `.master-page-title h2` | 22px/600 | 13px | 基础数据各页 |
| B（Ant 覆盖） | `.standard-page-heading h2` | 20px/600 | 13px | 『DataTable 通用表格演示』等 |
| C（responsive clamp） | `medical-saas.css` 组 | clamp(22px,1.55vw,26px)/650 | 13px | 宽屏基础数据页 |

**标准：统一为 22px / 600 / 行高 1.3，副标题 13px / 400 / 行高 1.5。**
元素引用：『科室库房目录』页顶部页面名（22px）与下方说明（13px）。

**注意**：`main.css` 的 `display:none !important` 已隐藏 `.standard-page-heading > p` 等说明文字。
产品决策：说明文案是**永久隐藏**还是**保留但缩小**？见第 10 章 Q-01。

### 4.3 筛选 / 查询区

三套并存：

| 方案 | 标签 | 控件 | 按钮 | 使用页面 |
| --- | --- | --- | --- | --- |
| unified-list | 14px/700 | 13px | 14px/700 | 统一列表页 |
| md-* | 12px/500 | 13px | 13px/600 | md-* 基础数据页 |
| 科室申领 | **11px**/500 | 12px | 12px/600 | 『科室申领』弹窗 |

**标准：筛选标签 13px / 600，控件 13px / 400，按钮 13px / 600。**
理由：标签与控件字面接近会造成层级断裂，13px 差值是密度与可读性的平衡点。
元素引用：『科室申领』弹窗筛选条『科室』标签现为 11px —— **这是全站最需要用户读取判断却最小的字号之一**，属 P0 整改项。

### 4.4 表格

**统一列表（`.spd-unified-list`）—— 唯一使用字号令牌、真正跟随档位的体系**
表头 14px/700（写死）、数据 15px/400（`--spd-font-size-md`）、主字段 16px/700、次级 13px/500。
元素引用：『科室库房目录』页表头『商品名称』14px；该列『聚维酮碘消毒剂皮肤外用消毒液 500ml 棕色瓶装』15px；其下『科室可申领商品』13px。

**md-* 体系（`.md-table`）** —— 数据 13px、表头 12px、主名 13px/600、副名 11px。
标准：数据 13px、表头 13px（与数据同档，仅靠字重与底色区分）、副名 12px。

**科室申领目录（`.dept-req-catalog-body`）** —— 数据 12px、表头 12px，两者**完全同字**，仅靠底色与字重区分。
标准：表头 13px、数据 13px。

**原生 el-table** —— 全局 14px、表头 14px/600。

**核心问题：同一个「商品名称」列，全站有 15px / 14px / 13px / 12px 四种数据字号。**

### 4.5 表单

| 方案 | 标签 | 控件 | 必填 | 错误提示 |
| --- | --- | --- | --- | --- |
| md-* | 12px/500 | 13px | 继承 | 13px |
| 自绘弹窗（`.supplier-form-grid`） | 13px/700 | inherit | — | — |
| el-form | 14px/400 | inherit | — | — |
| 科室申领已选清单 | 11px/500 | 11px | — | — |

**标准：标签 13px / 600，控件 13px / 400，必填继承标签字号，错误提示 13px / 400。**
元素引用：基础数据弹窗『商品名称』字段标签现为 12px；『科室申领』已选清单里『默认申领』下拉现为 11px —— 应升到 13px。

### 4.6 弹窗与浮层

| 分类 | 当前 | 标准 | 元素引用 |
| --- | --- | --- | --- |
| el-dialog 标题 | 16px/600（`!important` 锁定） | 16px/600 | 任意弹窗标题 |
| 自绘弹窗标题 | 16px/600 | 16px/600 | 供应商弹窗 |
| md-* 弹窗标题 | 16px/600 | 16px/600 | md-* 弹窗 |
| 弹窗副标题 | 12.5px / 12px（两套） | 13px | 弹窗标题下说明 |
| 弹窗正文 | 13px | 13px | 弹窗 body |
| 弹窗内表格 | 12.5px | 13px | 弹窗内嵌表格 |
| 确认框标题 / 正文 | 16px / 14px | 16px / 14px | 删除确认框 |
| 下拉选项 | 13px | 13px | 任意下拉菜单项 |
| 列设置浮层 | 标题 14px / 链接 13px / 列项 14px / 宽度 12px | 同 | 列设置浮层 |

**弹窗内表格 12.5px 是唯一的非整数字号**，且与主列表 15px 差 2.5px —— 弹窗与列表视觉密度割裂。

### 4.7 按钮

按钮体系有三套字号并存：

| 体系 | 字号 | 定义 |
| --- | --- | --- |
| 全局 `--btn-font-size` | 14px（跟随档位 → 16/18） | `main.css` `:root`，`medical-saas.css` 覆盖 weight |
| `.md-btn`（unified-list 内） | 14px（跟随档位） | `list/unified-list.css` |
| `.md-btn`（master-data.theme） | 13px（写死） | `master-data.theme.css` |
| `.el-button` | 14px（`!important`） | `spd-override.css` |
| `.el-button--small` | 12px（`!important`） | `spd-override.css` |
| `.btn-sm` | 12px | `components/buttons.css` |

**标准：主按钮 14px/600，小按钮 12px/600，图标按钮仅图标。**
元素引用：列表工具栏『新增商品』14px；分页『上一页』12px。

### 4.8 状态与标签

| 体系 | 状态条 | 文本标签 | 徽章 |
| --- | --- | --- | --- |
| unified-list | 14px/600（跟随档位） | 14px/600 | — |
| md-* | 12px/600（写死） | 12px/600（chip） | 11px/600（tab count） |
| spd-theme | 12px/500（`.spd-tag`） | — | — |
| status-badge | 继承 14px | — | — |

**标准：状态条统一 14px / 600。**
理由：状态条是表格中的**语义关键信息**（「启用」与「停用」必须一眼可辨），不应比数据文字更小。
元素引用：『科室库房目录』页『状态』列『启用』（unified-list，14px，已符合）vs md-* 页『状态』列（12px，需升）。

### 4.9 图表（重点）

**全站只有一个 ECharts 组件**：`frontend/src/components/business/TrendChart.vue`。
搜索 `echarts` / `setOption` / `vue-echarts` 全仓库仅命中该文件与 `stores/workspace.ts`（数据，无样式）。

其字号配置**全部写死在 JS 里**，且**完全不参与字号档位**：

```js
legend: { textStyle: { fontSize: 11, color: '#73838c' } },
xAxis: { axisLabel: { color: '#87969e', fontSize: 10 } },
yAxis: { nameTextStyle: { color: '#87969e', fontSize: 10 },
         axisLabel: { color: '#87969e', fontSize: 10 } },
```

**结论**：图表坐标轴标签 10px 是全站**最小的、需要用户读取判断**的字号 —— 低于 11px 的下限，且用户切到「大」「特大」档位后仍保持 10px。这是本次盘点中**最严重的可读性缺陷**。详见第 5 章。

### 4.10 卡片与统计

统计数值有 5 套字号：30px（md-*）/ 38px（spd-stat）/ 24px（metric-panel）/ 32px（驾驶舱 KPI）/ 22px（驾驶舱环形图）。
标准：统计数值统一 `--spd-font-size-3xl`（24px），仅驾驶舱等沉浸式页面允许 30–32px。
元素引用：基础数据页 4 联统计卡『医院商品总数』现为 30px。

### 4.11 看板 / 工作台

模块卡标题 17px、描述 14px、流程条节点 14px、特性行 14px。
标准：模块卡标题 16px/700，描述 13px/400，流程节点 14px。
元素引用：工作台模块卡标题。

### 4.12 结算 / 对账专用

结算明细表是全站**最密集**的表格：表头 12px、主字段 14px/800、元信息 11.5px、策略标记 10.5px、编码列表 11.5px。
标准：表头 13px、主字段 14px、元信息 12px、策略标记 12px/600、编码列表 12px。
元素引用：『结算对账』页结算明细表『带量』『医保』策略标记现为 10.5px —— 是结算核对的关键依据，不应小于 12px。

### 4.13 库存专用

盘点范围标题 14px、说明 12px、已选提示 13px、数量输入 13px、库位层级 12px、包标签 12px。
标准：数量输入 13px → 14px（与其它输入框统一）；其余保持。
元素引用：『库存盘点』弹窗『实盘数量』输入框。

### 4.14 登录页

登录页是**完全独立的视觉体系**，明确不参与字号档位（`standalone-login` 自带字体栈与全套写死字号），这符合「独立登录页不跟随业务设置」的产品惯例，**保留现状**。
但内部有两处待整改：
- Lg-05 统计标签 11px、Lg-17 底部说明 11px —— 属于说明性文字，可接受；
- Lg-12 表单标签 12px / Lg-13 输入框 14px —— 标签比控件小 2px，与全站「标签 ≤ 控件」的层级相反，**应把标签提到 13px**。

元素引用：登录页『用户名』标签 12px、输入框 14px。

### 4.15 AI 助手 / 聊天

气泡文字 13px、回复列表项 12px、时间戳 11px、输入框 14px、免责声明 10px。
标准：气泡 14px、列表项 13px、时间戳 12px、免责声明 11px。
元素引用：助手回复中的分点说明现为 12px。

**免责声明 10px 是全站最小字号**，但它是免责声明 —— 属「可接受但应提升至 11px」。

### 4.16 打印 / 导出

| 分类 | 当前 | 标准 |
| --- | --- | --- |
| 打印模板字段表 | 13px | 13px |
| 打印模板表头 | 12px | 13px |
| 字段值 | 12px | 13px |
| 字段区空态 | 13px | 13px |

**打印模板本身**（真正打印到纸上的内容）字号不在前端样式内，由后端模板定义，前端只配置字段与模板结构 —— 这部分**不受本规范约束**，需另立「打印排版规范」（见 Q-04）。
元素引用：『打印模板设置』页字段列表『单据号』『商品名称』。

### 4.17 空态 / 加载 / 错误

| 分类 | 当前 | 标准 |
| --- | --- | --- |
| unified-list 空态 | 14px（跟随档位失效，写死） | 14px 且跟随档位 |
| md-* 空态标题 / 说明 | 14px / 12px | 14px / 13px |
| 原生空态 | 14px | 14px |
| 加载提示 | 14px | 14px |
| 错误提示条 | 13px | 13px |

元素引用：任意列表页空态『暂无符合条件的数据』。

---

## 5. 图表字体专项规范

### 5.1 现状

| 图表元素 | 当前字号 | 是否跟随档位 | 定义位置 |
| --- | --- | --- | --- |
| X 轴刻度 | 10px | ❌ 写死在 JS | `TrendChart.vue` `xAxis.axisLabel.fontSize` |
| Y 轴刻度 | 10px | ❌ 写死在 JS | `TrendChart.vue` `yAxis.axisLabel.fontSize` |
| Y 轴名称 | 10px | ❌ 写死在 JS | `TrendChart.vue` `yAxis.nameTextStyle.fontSize` |
| 图例 | 11px | ❌ 写死在 JS | `TrendChart.vue` `legend.textStyle.fontSize` |
| Tooltip | 12px（ECharts 默认） | ❌ | 未显式设置 |
| 数据标签 | 未启用 | — | — |
| 图表标题 | 未启用（靠外部 HTML 标题） | — | — |

### 5.2 问题

1. **10px 低于全站可读性下限**。坐标轴刻度是用户判断数值趋势的依据，10px 在 27" 显示器上几乎不可辨。
2. **完全不参与三档缩放**。用户切到「特大」档位后，图表仍是 10px，而周边表格已放大到 19px —— 图表成为页面中**唯一不变的区域**，形成严重的尺度断裂。
3. **只有一个组件、只有一处配置**，整改成本极低、收益极高。

### 5.3 标准

图表建立**独立于 `--spd-font-size-*` 的第二套令牌**，因为 ECharts 的 `fontSize` 是 canvas 像素值，不接受 CSS `var()`：

| 令牌 | standard | large | xlarge | 用途 |
| --- | --- | --- | --- | --- |
| `--spd-chart-font-xs` | 11px | 12px | 13px | 微型角标（暂不使用） |
| `--spd-chart-font-sm` | 12px | 13px | 14px | **坐标轴刻度**、轴名称 |
| `--spd-chart-font-base` | 13px | 14px | 15px | **图例文字** |
| `--spd-chart-font-lg` | 14px | 15px | 16px | 数据标签、Tooltip 标题 |
| `--spd-chart-font-xl` | 15px | 16px | 17px | Tooltip 正文 |

**图表字号 = 页面字号 − 2px**（standard 档），保证图表始终比同页正文轻一档。

### 5.4 落地要求

1. `TrendChart.vue` 需读取 `<html data-font-size>` 或订阅主题 store，动态计算 `fontSize`；
2. 表格与图表的字号差保持 ≥ 2px，避免图表文字与表格文字混淆；
3. **严禁**在图表 JS 里继续写死 `fontSize: 10` / `11`；
4. 若未来新增图表组件，必须复用同一套 `--spd-chart-font-*`；
5. 图表必须开启 `aria.enabled`（当前已开启），保证读屏可用。

---

## 6. 字号层级与视觉优先级模型

### 6.1 层级阶梯（标准档）

```
展示层  30 – 38px   统计数值、驾驶舱 KPI          ┐  极少量，每屏 ≤ 3 处
标题层  22 – 26px   页面主标题、弹窗标题           ┘

强调层  16px        表格主字段、卡片标题           ┐
操作层  14px        状态条、按钮、表头、筛选标签   │  高频区，等大降噪

数据层  13 – 15px   表格数据、输入框、说明         ┘
辅助层  12px        次级说明、chip、元信息
微型层  10 – 11px   角标、免责、图例（仅限纯装饰性）
```

### 6.2 约束规则

| # | 规则 |
| --- | --- |
| L-1 | 同一区域内的字号**不超过 3 档**（含微型层） |
| L-2 | **表格数据字号 ≤ 表头字号**。数据不得大于表头（表头是列名，数据是内容） |
| L-3 | **筛选标签字号 ≥ 输入控件字号 − 1px**，且**不得大于**控件字号 + 1px |
| L-4 | 同一页面内，**标题类字号与正文字号的比值不超过 2 倍**（22px / 13px ≈ 1.7，合法） |
| L-5 | **相邻层级最小差值 1px**；正文与次级说明最小差值 2px（保证可辨） |
| L-6 | **11px 及以下**仅允许用于：角标、计数徽标、免责/版权、纯装饰标签。**禁止**用于需要用户读取判断的业务信息 |
| L-7 | 状态语义信息（启用/停用、待审核、通过/驳回）**不得小于 12px** |
| L-8 | 数值型单元格与相邻数值列必须使用同一字号 + `tabular-nums` |
| L-9 | 图表字号比同页正文**轻一档**（≥ 2px 差值） |
| L-10 | 同一语义（如「商品名称」列）**跨页面字号必须一致** |

### 6.3 违反示例（来自本次盘点）

| 违反 | 实例 | 规则 |
| --- | --- | --- |
| L-2 | md-* 表格数据 13px / 表头 12px → 数据大于表头 | L-2 |
| L-3 | 科室申领筛选标签 11px / 控件 12px → 标签小于控件 1px 但整体过小 | L-3、L-6 |
| L-6 | 结算策略标记 10.5px、图表轴标签 10px、AI 免责 10px | L-6 |
| L-7 | md-* 状态徽章 12px 达标；`.status-badge` 继承 14px 达标 | — |
| L-10 | 「商品名称」列有 15 / 14 / 13 / 12px 四种 | L-10 |

---

## 7. 不一致项清单

> 优先级：**P0** = 影响核心信息可读性，必须整改；**P1** = 影响跨页面一致性，应整改；**P2** = 体验优化，可排期。

| # | 不一致项 | 现状 | 建议 | 影响面 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| D-01 | **图表坐标轴字号写死 10px 且不参与档位** | 轴标签 10px、图例 11px，恒定 | 建立 `--spd-chart-font-*` 令牌，轴 12px / 图例 13px，随档位缩放 | 全站唯一图表组件，影响所有趋势图 | **P0** |
| D-02 | **科室申领筛选标签 11px** | `.dept-req-filterbar-item > span` = 11px | 13px | 『科室申领』弹窗筛选条 | **P0** |
| D-03 | **科室申领已选清单全线 11px** | `.dept-req-selected-list` 下 label/select/input/code/spec 全为 11px | 13px | 『科室申领』已选清单 | **P0** |
| D-04 | **结算策略标记 10.5px** | `.settlement-policy-list b/small` = 10.5px | 12px | 『结算对账』页「带量」「医保」依据 | **P0** |
| D-05 | **结算元信息 11.5px（非整数）** | `.settlement-cell-meta` = 11.5px | 12px | 结算明细表全部元信息行 | **P1** |
| D-06 | **同一「商品名称」列四种字号** | 15px（unified-list）/ 14px（el-table、master-table）/ 13px（md-table）/ 12px（科室申领目录） | 统一 14px | 全部列表页 | **P1** |
| D-07 | **表头不跟随字号档位** | unified-list 表头恒 14px | 跟随 `--spd-font-size-base` | 全部统一列表页 | **P1** |
| D-08 | **筛选标签 / 输入框 / 分页不跟随档位** | 恒 14 / 13 / 14px | 跟随 `--spd-font-size-base` | 全部统一列表页 | **P1** |
| D-09 | **筛选标签 14px vs md-* 12px 两套** | unified-list 14 / md-* 12 | 统一 13px | 列表页筛选区 | **P1** |
| D-10 | **弹窗内表格 12.5px（非整数）** | `.el-dialog .el-table` = 12.5px | 13px | 所有弹窗内嵌表格 | **P1** |
| D-11 | **`MdPageHeader` 接收 `description` 但全局规则隐藏它** | `main.css` `display:none !important` 隐藏 `.standard-page-heading > p` | 产品决策：保留并显示 13px，或移除 prop | 全部 md-* 页面 | **P1** |
| D-12 | **状态条 14px（unified-list）vs 12px（md-*）** | 两套 | 统一 14px | 全部列表页 | **P1** |
| D-13 | **行高令牌几乎未使用** | `--spd-lh-tight/base/loose` 定义了但实际散落 1–1.85 | 收敛到 3 档令牌 | 全站 | **P1** |
| D-14 | **4 套并行字体栈** | main.css / spd-theme / medical-saas / login | 收敛为 2 套（业务 + 登录） | 全站 | **P1** |
| D-15 | **`.btn` 字号三套** | 14px（--btn-font-size）/ 13px（.md-btn）/ 12px（.btn-sm） | 14 / 14 / 12 | 全站按钮 | **P1** |
| D-16 | **迷你侧栏标题被强制 11px** | `spd-override.css` `.spd-mini-sidebar-title` `11px !important` | 12px | 迷你侧栏 | **P2** |
| D-17 | **驾驶舱大量 10–11px** | `.trend-labels` 10px、`.spd-warning-level-text` 10px、`.caliber-note` 11px 等 | 提升 1–2px | 『操作驾驶舱』页 | **P2** |
| D-18 | **AI 助手免责声明 10px** | `.ai-medical-composer small` = 10px | 11px | AI 助手 | **P2** |
| D-19 | **登录页表单标签 12px 小于输入框 14px** | `.login-panel label > .lbl` = 12px | 13px | 登录页 | **P2** |
| D-20 | **字重 650 非标准值** | `medical-saas.css` 多处 `font-weight: 650` | 改 600 | 页面标题、导航、卡片 | **P2** |
| D-21 | **`.md-cell-name small` 11px 承载商品规格** | 11px | 12px | md-* 列表 | **P2** |
| D-22 | **`.spd-tag` / `.spd-tab-count` / `.text-tertiary` 写死 12px** | `spd-theme.css` 工具类未跟随档位 | 改令牌 | spd-theme 组件 | **P2** |
| D-23 | **打印模板表头 12px / 字段值 12px** | 打印配置页 | 表头 13px / 值 13px | 『打印模板设置』页 | **P2** |
| D-24 | **驾驶舱图表用原生 SVG 而非 ECharts** | `.trend-chart svg` + `.trend-labels` 10px 手写 | 若保留 SVG，需同步图表字号规范 | 『操作驾驶舱』页 | **P2** |

---

## 8. 实施建议

> 本次不改代码。以下为后续收敛路径。

### 阶段 0：产品决策（先做，决定后续）

在动代码前，先由产品确认第 10 章的 5 个问题，尤其是 **Q-02（表头是否随档位）** 与 **Q-03（表格数据统一到几 px）**。

### 阶段 1：P0 可读性修复（低风险、高收益）

1. `TrendChart.vue` 引入 `--spd-chart-font-*`（新增 5 个令牌在 `typography.css`），轴标签 10 → 12px，图例 11 → 13px，并订阅字号档位。
2. `DepartmentRequisitionView.vue`：筛选标签 11 → 13px；已选清单全线 11 → 13px。
3. `main.css`：`.settlement-policy-list b/small` 10.5 → 12px；`.settlement-cell-meta` 11.5 → 12px。

### 阶段 2：令牌化（P1）

1. 新增令牌：`--spd-font-size-table-head`（表头）、`--spd-font-size-filter-label`（筛选标签）、`--spd-font-size-label`（表单标签）。
2. `list/unified-list.css`：表头、筛选标签、筛选按钮、空态、分页由写死 px 改为令牌。
3. `components/pagination.css`：分页容器与按钮改为令牌。
4. `master-data.theme.css`：`.md-table` 表头 12 → 13px（与数据同档），`.md-status` 12 → 14px，`.md-btn` 13 → 14px。
5. `spd-override.css` / `medical-saas.css`：表头、筛选区、`.master-table` 改为令牌。

### 阶段 3：跨页面对齐（P1）

1. 统一「商品名称 / 规格型号 / 商品编码」三列字号为 14px（表格数据）。
2. 统一状态条字号为 14px。
3. 统一筛选标签字号为 13px。
4. 统一弹窗内表格字号为 13px。

### 阶段 4：行高与字重收敛（P1/P2）

1. 行高统一到 `--spd-lh-tight / base / loose` 三档。
2. `font-weight: 650` 全部改 600。
3. 字体栈收敛为 2 套。

### 阶段 5：长尾清理（P2）

D-16 ~ D-24 逐项清理。

### 验收方式

每个阶段结束后：

```powershell
npm --prefix frontend run build
npm run test:smoke:business-ui   # 需要 MySQL
```

并按第 9 章的三档验证清单对屏核对。

---

## 9. 附录

### 9.1 全量字号令牌表

**`frontend/src/styles/tokens/typography.css`（唯一来源）**

```
:root {
  --spd-fs-base: 14px;
  --spd-lh-tight: 1.35;
  --spd-lh-base: 1.5;
  --spd-lh-loose: 1.65;
  --spd-table-cell-padding-y: 10px;
}
:root[data-font-size='large']  { --spd-fs-base: 16px; --spd-table-cell-padding-y: 12px; }
:root[data-font-size='xlarge'] { --spd-fs-base: 18px; --spd-table-cell-padding-y: 13px; }

:root {
  --spd-font-size-xs:   calc(var(--spd-fs-base) - 3px);
  --spd-font-size-sm:   calc(var(--spd-fs-base) - 1px);
  --spd-font-size-base: var(--spd-fs-base);
  --spd-font-size-md:   calc(var(--spd-fs-base) + 1px);
  --spd-font-size-lg:   calc(var(--spd-fs-base) + 2px);
  --spd-font-size-xl:   calc(var(--spd-fs-base) + 4px);
  --spd-font-size-2xl:  calc(var(--spd-fs-base) + 6px);
  --spd-font-size-3xl:  calc(var(--spd-fs-base) + 10px);
  --spd-font-size-4xl:  calc(var(--spd-fs-base) + 16px);
  --spd-font-size-5xl:  calc(var(--spd-fs-base) + 24px);
}
```

**建议新增（P0/P1 阶段）**

```
--spd-font-size-table-head    = var(--spd-font-size-base)
--spd-font-size-filter-label  = var(--spd-font-size-sm)
--spd-font-size-form-label    = var(--spd-font-size-sm)
--spd-chart-font-xs / -sm / -base / -lg / -xl   （图表专用，见第 5 章）
```

### 9.2 逐档速查

```
standard（14px 基准）
  xs 11px · sm 13px · base 14px · md 15px · lg 16px
  xl 18px · 2xl 20px · 3xl 24px · 4xl 30px · 5xl 38px

large（16px 基准）
  xs 13px · sm 15px · base 16px · md 17px · lg 18px
  xl 20px · 2xl 22px · 3xl 26px · 4xl 32px · 5xl 40px

xlarge（18px 基准）
  xs 15px · sm 17px · base 18px · md 19px · lg 20px
  xl 22px · 2xl 24px · 3xl 28px · 4xl 34px · 5xl 42px
```

### 9.3 关键文件索引

| 用途 | 路径 |
| --- | --- |
| 字号令牌唯一来源 | `frontend/src/styles/tokens/typography.css` |
| 颜色令牌 | `frontend/src/styles/tokens/colors.css` |
| 全局基础 + 按钮令牌 | `frontend/src/styles/main.css` |
| 字号档位状态管理 | `frontend/src/stores/theme.ts` |
| 统一列表（跟随档位） | `frontend/src/styles/list/unified-list.css` |
| md-* 设计体系 | `frontend/src/styles/master-data.theme.css` |
| 顶栏 / 迷你侧栏 / 页签栏 | `frontend/src/styles/spd-layout.css` |
| 弹窗 | `frontend/src/styles/components/modal.css` |
| 按钮 | `frontend/src/styles/components/buttons.css` |
| 分页 | `frontend/src/styles/components/pagination.css` |
| 登录页 | `frontend/src/styles/views/login.css` |
| 图表（唯一 ECharts） | `frontend/src/components/business/TrendChart.vue` |
| 通用表格组件 | `frontend/src/components/common/DataTable.vue` |
| 通用分页组件 | `frontend/src/components/common/PaginationControls.vue` |

### 9.4 三档验证清单

**切换方式**

```javascript
document.documentElement.setAttribute('data-font-size', 'large')
document.documentElement.setAttribute('data-font-size', 'xlarge')
document.documentElement.removeAttribute('data-font-size')  // 回到 standard
```

或：顶栏主题切换器 → 字号档位。

**逐项检查**

| # | 检查项 | 期望 |
| --- | --- | --- |
| V-01 | 表格数据单元格 | standard 15 → large 17 → xlarge 19 |
| V-02 | 表格主字段 `.ulc-strong` | 16 → 18 → 20 |
| V-03 | 表格次级说明 `.ulc-sub` | 13 → 15 → 17 |
| V-04 | 状态条 `.ulc-chip` | 14 → 16 → 18 |
| V-05 | 行内操作按钮 `.ulc-action` | 14 → 16 → 18 |
| V-06 | 工具栏按钮 `.md-btn` | 14 → 16 → 18 |
| V-07 | 表格表头 | **当前恒 14px**（整改后应 14 → 16 → 18） |
| V-08 | 筛选标签 | **当前恒 14px**（整改后应 13 → 15 → 17） |
| V-09 | 筛选输入框 | **当前恒 13px**（整改后应 13 → 15 → 17） |
| V-10 | 分页 | **当前恒 13–14px**（整改后应跟随） |
| V-11 | ECharts 坐标轴 | **当前恒 10px**（整改后应 12 → 13 → 14） |
| V-12 | ECharts 图例 | **当前恒 11px**（整改后应 13 → 14 → 15） |
| V-13 | 长文本列换行 | 特大档不溢出、不截断 |
| V-14 | 页面无横向溢出 | 除非列数确实超宽 |

---

## 10. 需要产品决策的问题

| # | 问题 | 选项 | 影响 |
| --- | --- | --- | --- |
| Q-01 | 页面副标题（描述文案）是否永久隐藏？ | A. 保持隐藏，移除 `MdPageHeader` 的 `description` prop；B. 恢复显示，统一 13px | 决定 D-11 的整改方向；影响所有页面的信息密度 |
| Q-02 | 表格表头是否随字号档位放大？ | A. 跟随（表头随数据同步放大，推荐）；B. 恒定 14px（保持表头紧凑） | 决定 D-07；A 会让大档位表格更宽 |
| Q-03 | 表格数据统一到 14px 还是 15px？ | A. 14px（与 el-table / master-table 一致，密度高，推荐）；B. 15px（unified-list 现状，可读性优先） | 决定 D-06；影响所有列表页横向宽度 |
| Q-04 | 是否需要独立的「打印排版规范」？ | A. 需要（纸张 A4 字号体系与屏幕不同，另立规范）；B. 不需要 | 打印模板的真实字号由后端模板定义，目前完全无约束 |
| Q-05 | 登录页是否继续独立于字号档位？ | A. 继续独立（推荐，惯例如此）；B. 跟随档位 | 决定 Lg 系列是否需要令牌化 |
| Q-06 | 驾驶舱（暗色沉浸式页面）是否允许突破常规层级？ | A. 允许（该页为演示/监控场景，保留 30–38px 展示型字号）；B. 不允许 | 决定 D-17 的整改范围 |

---

## 11. 复现与验证

```powershell
# 前端构建
npm --prefix frontend run build

# 本地启动
npm run dev:frontend

# 关键业务页烟囱测试（需 MySQL）
npm run test:smoke:business-ui
```

**三档切换**：顶栏主题切换器 → 字号；或控制台

```javascript
document.documentElement.setAttribute('data-font-size', 'xlarge')
```

**样式来源核对**：浏览器 DevTools → Computed → 查 `font-size` 的最终值与来源规则；或在 `styles/tokens/typography.css` 中检索 `var(--spd-font-size-*)` 的使用情况。

---

## 12. 版本记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1.0 | 2026-06 | 仅覆盖统一列表页与弹窗，浅层 |
| **v2.0** | **2026-11-23** | **重写为完整版：覆盖 17 类、110+ 条分类；新增图表专项、层级模型、24 项不一致清单、6 项产品决策问题** |
