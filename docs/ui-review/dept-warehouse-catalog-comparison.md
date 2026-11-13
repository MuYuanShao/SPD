# 科室库房管理页面 — 三方案 UI 对比说明

> 面向 30 岁以上中长期使用者的可读性改造。
> 本次在独立分支上做 A/B/C 三套列宽方案测试，当前分支不改动 `main`。

## 一、分支说明

| 分支 | 角色 | 说明 |
| --- | --- | --- |
| `feature/ui-readability-base` | 基准（含共用改造） | 色彩令牌、三档字号、状态条/按钮规范、长文本列宽标准；列表列宽采用方案 B |
| `feature/ui-col-a-fixed-wide` | 方案 A | 固定宽度列 + 长文本换行 |
| `feature/ui-col-b-auto-fit` | 方案 B | 全部列自适应填充容器 |
| `feature/ui-col-c-hybrid` | 方案 C | 短字段固定 + 长文本弹性 + Tooltip 兜底 |

切换分支后需要重新构建前端：`npm --prefix frontend run build`。

## 二、共用改造（三方案一致）

### 1. 统一色彩令牌（唯一颜色来源）

文件：`frontend/src/styles/tokens/colors.css`

- 全站只允许使用 **1 个主色 + 4 个语义色 + 中性灰阶**，不再新增其他颜色。
- 文字恒为深色：主文字 `#1f2933`、强调 `#101820`、次级 `#4a5a66`。
- 背景恒为浅色：卡片 `#ffffff`、次级区块 `#f5f8f9`、页面底 `#f7fafb`。
- 主色在原青玉 `rgb(3,179,178)` 基础上继续降低明艳度 → **`rgb(31,111,109)`**（明度约 43%），长时间注视不刺眼。
- 语义色固定不变：

| 语义 | 令牌 | 颜色 | 用途 |
| --- | --- | --- | --- |
| 成功 / 启用 | `--spd-c-success` | `#2f7d5b` | 低饱和绿 |
| 停用 / 禁用 | `--spd-c-disabled` | `#5b6b76` | 中性灰蓝，明确区别于危险 |
| 警告 | `--spd-c-warning` | `#9a6114` | 低饱和橙 |
| 危险 | `--spd-c-danger` | `#a33a30` | 低饱和红 |
| 信息 | `--spd-c-info` | `#1f5f7d` | 低饱和蓝 |

### 2. 状态条 / 按钮统一规格

统一为：**4px 圆角 + 1px 边框 + 0.4 透明度底色 + 深色文字**。

```css
border-radius: 4px;
border: 1px solid var(--spd-c-success-border);
background: color-mix(in srgb, var(--spd-c-success) 40%, #ffffff);
color: var(--spd-c-success);
```

实测（浏览器 computed style，三方案一致）：

```
chipRadius = 4px    chipBorder = 1px
chipBg     = color(srgb 0.648627 0.774118 0.77098)   /* 0.4 透明度底 */
```

### 3. 三档字号系统

文件：`frontend/src/styles/tokens/typography.css`、`frontend/src/stores/theme.ts`

| 档位 | 基准 | 适用人群 |
| --- | --- | --- |
| 标准 | 14px | 常规显示器、密度优先 |
| 大 | 16px | 27 寸以上屏幕、轻度老花 |
| 特大 | 18px | 视力偏弱、高分辨率远距使用 |

- 通过 `<html data-font-size="standard|large|xlarge">` 驱动，`html { font-size }` 跟随基准，整站等比缩放。
- 用户在顶栏「主题面板 → 界面字号」中切换，选择结果持久化到 `localStorage`（键 `spd.theme.fontSize`）。
- 实测点击链路验证：

```
click 大    → attr=large   stored=large   htmlFont=16px
click 特大  → attr=xlarge  stored=xlarge  htmlFont=18px
click 标准  → attr=null     stored=standard htmlFont=14px
```

### 4. 长名称测试数据

迁移：`backend/src/main/resources/db/migration/V109__catalog_long_name_test_data.sql`

| 商品编码 | 商品名称长度 | 厂家名称长度 |
| --- | --- | --- |
| TEST_LONG_P001 | 35 字符 | 20 字符 |
| TEST_LONG_P002 | 40 字符 | 20 字符 |
| TEST_LONG_P003 | 34 字符 | 17 字符 |

均超过 20 字符，符合实际医院耗材名称长度。所有数据以 `TEST_LONG_` 前缀标记，便于清理。

## 三、三套列宽方案实测数据

### 宽屏（1600px，内容区 1564px）

| 方案 | 表格总宽 | 是否横向滚动 | 标准行高 | 大行高 | 特大行高 |
| --- | --- | --- | --- | --- | --- |
| A 固定宽度 | 1838 | **是** | 90 | 98 | 107 |
| B 自适应 | 1564 | 否 | 90 | 140 | 154 |
| C 混合 | 1564 | 否 | 90 | 140 | 154 |

### 窄屏（1366px，内容区 1330px）

| 方案 | 表格总宽 | 是否横向滚动 | 标准行高 | 特大行高 | 特大时长文本行数 |
| --- | --- | --- | --- | --- | --- |
| A 固定宽度 | 1838 | **是**（需横向滚动） | 90 | 107 | 3 行 |
| B 自适应 | 1330 | 否 | 126 | 154 | 4 行 |
| C 混合 | 1330 | 否 | 90 | 154 | 4 行 |

关键差异：

- **方案 A**：列宽完全由用户决定，横向滚动位置可预期；但窄屏必须横向滚动，且操作列靠 `fixed="right"` 固定才可见。
- **方案 B**：始终铺满容器、无横向滚动；但列宽随视口变化，长文本行高在窄屏下明显增高（标准档 90 → 126），信息密度下降。
- **方案 C**：短字段列位稳定（窄屏行高仍是 90），长文本列弹性伸缩，兼顾对齐稳定与空间利用；超出部分用 Tooltip 兜底。

## 四、建议

推荐 **方案 C** 作为最终方案，理由：

1. 短字段（编码 / 状态 / 来源 / 时间）列位稳定，扫读时视线不会因视口宽度变化而跳动 —— 这对 30+ 用户的持续扫读体验最关键。
2. 长文本列在宽屏充分利用空间，在窄屏压缩后换行，信息不丢失。
3. Tooltip + `title` 双兜底，保证极端窄屏下也能看到完整名称。
4. 基准分支当前采用方案 B，若决定转向 C，只需把 `feature/ui-col-c-hybrid` 的列宽部分合并进基准分支即可。

## 五、验证方式

```powershell
# 1. 切换分支并构建
git checkout feature/ui-col-c-hybrid
npm --prefix frontend run build

# 2. 启动（后端需先执行 V109 迁移）
npm run backend:restart
npm run dev:frontend

# 3. 打开 http://localhost:1820/features/department-warehouse-catalog
#    - 顶栏主题面板切换「标准 / 大 / 特大」
#    - 表头分隔线拖拽调整列宽（持久化）
#    - 「列设置」勾选显隐、「恢复默认」
```

对比截图见 `output/cmp-{a,b,c}-{standard,large,xlarge}.png`（1600px）
与 `output/cmp-{a,b,c}-{standard,large,xlarge}.png`（1366px，覆盖同名文件）。
