# 列表页标准化改造规范（列宽 / 铺满 / 换行 / 分页 / 记忆）

本文件是各子任务共同的**唯一标准**。所有改造后的列表必须与标准页
**科室库房目录**（`frontend/src/views/master-data/MasterDataListView.vue` 的 `catalogCols`）完全一致。

---

## 0. 标准参考位置

| 内容 | 文件 | 位置 |
|---|---|---|
| 标准列配置 | `frontend/src/views/master-data/MasterDataListView.vue` | `CATALOG_COL_DEFAULTS`（约 472 行） |
| 标准 `useListColumns` 调用 | 同上 | `catalogCols`（约 548 行） |
| 标准分页器 | `frontend/src/components/master-data/MdPagination.vue` | 整体 |
| 标准表格结构 | 同上 | `el-table` + `<template #pagination>` |
| 换行样式 | `frontend/src/styles/list/unified-list.css` | `.ulc-wrap-text` / `.ulc-cell-stack` / `.ulc-joined` |

---

## 1. 四项硬性要求

### 1.1 默认占满 100%（铺满内容区）

`useListColumns` 调用必须设置：

```ts
fitMode: 'hybrid',
reservedWidth: 0,   // 或 48，见下方「勾选列」说明
```

- `fitMode: 'hybrid'` —— 按权重把容器宽度分配给所有列，表格铺满内容区。
  **不要用 `'fixed'`**（列宽恒定，右侧留白不填满）。

### ⚠️ 勾选列必须预扣宽度（最容易漏、后果最严重）

表格里的 `<el-table-column type="selection" width="48" />`（勾选列）
**不在列配置里**，但它实实在在占用了 48px 表格宽度。
如果 `reservedWidth` 仍写 0，数据列会按**完整容器宽度**分配，
把表格顶出横向滚动条 —— 表现为「表格比容器宽、右侧被切掉」。

规则：

| 表格是否有 `type="selection"` 勾选列 | `reservedWidth` |
|---|---|
| 有 | `48` |
| 没有 | `0` |

判断方法：在同一个 `<el-table>` 里搜 `type="selection"`。
**同一文件里不同的表格要分别判断**（有的表格有勾选列、有的没有）。

```vue
<!-- 有勾选列 -->
<el-table-column type="selection" width="48" align="center" :reserve-selection="false" />
```
```ts
useListColumns({
  storageKey: 'spd-xxx-cols-v1',
  defaults: XXX_COL_DEFAULTS,
  fitMode: 'hybrid',
  // 预留勾选列宽度：该列不在列配置里，但会占用表格宽度，必须先扣掉，
  // 否则数据列按完整容器宽度分配，把表格顶出横向滚动条。
  reservedWidth: 48
})
```

**自检**：表格所有列（含勾选列）宽度之和 **必须等于** 容器宽度。
若大于 → 溢出（检查 `reservedWidth`）；若小于 → 右侧留白（检查 `fill` 列）。

操作列若需要参与分配，用 `fixedColumns`（见 1.3）。

### 1.2 按字段长度给默认列宽

每个 `*_COL_DEFAULTS` 的每一列都必须有：

```ts
{ key: 'xxx', label: '中文标签', width: <基准宽>, visible: true, weight: <权重>, minWidth: <下限>, maxWidth: <上限> }
```

**基准宽度的估算规则**（中文按 14px/字，英文数字按 7.5px/字，含内边距 24px）：

| 字段类型 | 基准宽度 | 权重 | minWidth | maxWidth |
|---|---|---|---|---|
| 短状态/类型/数量（≤4 字） | 80 | 2 | 70 | 130 |
| 单号/编码（8～16 位） | 130 | 3 | 110 | 200 |
| 名称（≤10 字） | 160 | 4 | 120 | 260 |
| 名称 + 副信息（10～20 字） | 190 | 5 | 130 | 300 |
| 长文本/备注/说明 | 220 | 6 | 140 | 380 |
| 超长文本（>20 字，如地址、说明） | 240 | 6 | 150 | 420 |

**权重规则**：字越多权重越大。字少的列**不能**给大权重，
否则会把字多的列挤窄。

### 1.3 操作列（`actions`）

**操作列必须出现在 `*_COL_DEFAULTS` 里**（这是硬性要求，
`isColumnVisible()` 只查 defaults，漏了会导致整列不渲染、按钮无法触达）：

```ts
{ key: 'actions', label: '操作', width: <按按钮数>, visible: true, fill: true, minWidth: <下限>, maxWidth: <上限> }
```

- `fill: true` —— 吃掉右侧剩余空白，保证表格 100% 铺满。
- 宽度按按钮数量估算：1 个按钮 110px，2 个 140px，3 个 170px，4 个 200px。

如果页面已有 `fixedColumns`（含 `key: 'actions'`），
则**保持二选一，不要重复定义**：
- 方案 A（推荐，与标准页一致）：`actions` 放 defaults 里并带 `fill: true`，**不加** `fixedColumns`。
- 方案 B：保留 `fixedColumns`，此时**不要**在 defaults 里放 `actions`。

> **重要**：`useListColumns` 里 `fixedColumns` 的列**不提供显隐开关**，
> 若已在 `fixedColumns` 里，又想在列设置面板里看到「操作」，
> 必须改用方案 A。
>
> 方案 A 的机制：`columnBinding()` 对 `fill: true` 的列只返回 `minWidth`、
> 不返回固定 `width`，由 Element Plus 原生 `fit` 把剩余空间全给它，
> 表格因此 100% 铺满。
>
> **推荐统一用方案 A**：把原来 `fixedColumns` 里的 `actions` 移到 defaults，
> 并加 `fill: true` + `minWidth`/`maxWidth`。这样：
> - 列设置面板能看到「操作」（方案 B 看不到）
> - 操作列宽度可拖拽调整并持久化
> - 表格仍然 100% 铺满

### 1.4 超过列宽自动换行

单元格内容必须使用 `ulc-wrap-text`：

```vue
<template #default="{ row }">
  <span class="ulc-wrap-text">{{ row.someField }}</span>
</template>
```

规则：
- **单值字段** → `<span class="ulc-wrap-text">值</span>`
- **多值拼接**（如「单价 × 数量 = 金额」）→ 用 `ulc-cell-stack` 或 `ulc-joined`，
  **不要**每段都单独 `ulc-wrap-text`（会导致整体超宽才换行时仍被拆开）。
  标准写法：
  ```vue
  <span class="ulc-joined">
    <span class="ulc-joined-part">{{ row.price }}</span>
    <span class="ulc-joined-part">×</span>
    <span class="ulc-joined-part">{{ row.qty }}</span>
  </span>
  ```
- **数字列** → `<span class="ulc-wrap-text ulc-number">值</span>`（右对齐）
- **不允许** `text-overflow: ellipsis` 截断。文字超宽就换行，行高自适应。

---

## 2. 分页器统一

**全部列表页统一用 `MdPagination`**（不是 `PaginationControls`）。

`MdPagination` 位于 `frontend/src/components/master-data/MdPagination.vue`，
基于 Element Plus `el-pagination`，外观与标准页一致。

用法（放在 `MdDataTableCard` 的 `#pagination` slot 里）：

```vue
<template #pagination>
  <MdPagination
    :page="currentPage"
    :total-pages="totalPages"
    :total="total"
    :size="pageSize"
    :loading="loading"
    @update:page="onChangePage"
    @update:size="onChangeSize"
  />
</template>
```

改造要求：
1. 删掉原有的 `<PaginationControls ... />`。
2. 改为上面的 `<MdPagination>`，放进 `MdDataTableCard` 的 `<template #pagination>`。
3. `total-pages` 传 `Math.max(1, Math.ceil(total / size))`。
4. 事件处理函数沿用页面原有的翻页/改页长逻辑，**不要改业务行为**。
5. 若页面分页逻辑分散在多个表格，每个表格各自放一个 `MdPagination`。

> `PaginationControls` 组件本身**保留不动**（其它非列表场景可能在用），
> 只是列表页不再使用它。

---

## 3. 列宽记忆（刷新 / 重新登录后保持）

`useListColumns` 已经内置了 `localStorage` 持久化，**关键是每个列表必须传唯一的 `storageKey`**：

```ts
const xxxCols = useListColumns({
  storageKey: 'spd-<业务名>-cols-v1',   // 全站唯一
  defaults: XXX_COL_DEFAULTS,
  fitMode: 'hybrid',
  reservedWidth: 0,
})
```

要求：
- `storageKey` **全站唯一**，不得与其它列表重复。
  推荐命名：`spd-<视图/业务>-<表格用途>-cols-v1`
  （例：`spd-purchase-order-cols-v1`、`spd-audit-log-cols-v1`）。
- 已有 `storageKey` 的**不要改**（改了会让用户丢失已保存的列宽）。
- 用户拖拽列宽 → `manual: true` → 持久化到 localStorage → 刷新/重登后保持。
- `normalize()` 已兼容旧数据（无 `manual` 字段视为未拖拽）。

**验证方式**：改列宽 → 刷新页面 → 列宽不变；退出重登 → 列宽不变。

---

## 4. 表格结构（保持现状，不要破坏）

```vue
<MdDataTableCard>
  <template #toolbar>
    <!-- 列设置按钮放这里（已有则保持） -->
  </template>
  <div class="ulc-table-wrap">
    <el-table
      class="ulc-table is-sticky"
      border stripe
      max-height="560"
      style="width: 100%"
      @header-dragend="(w, ow, col) => xxxCols.handleHeaderDragEnd(w, ow, col)"
    >
      <!-- 列：v-if="isXxxColVisible('key')" + v-bind="xxxColBinding('key')" -->
      <template #empty>
        <MdEmptyState text="..." />
      </template>
    </el-table>
  </div>
  <template #pagination>
    <MdPagination ... />
  </template>
</MdDataTableCard>
```

---

## 5. 禁止事项

- ❌ 不要修改 `frontend/src/composables/useListColumns.ts`（共享基础设施，由主任务统一维护）
- ❌ 不要修改 `frontend/src/composables/useColSettingsPopover.ts`
- ❌ 不要修改 `frontend/src/styles/list/unified-list.css`（共享样式）
- ❌ 不要修改 `frontend/src/components/master-data/MdPagination.vue` / `MdDataTableCard.vue`
- ❌ 不要改业务逻辑、接口调用、权限判断、筛选条件、弹窗
- ❌ 不要删除已有的 `v-if="isXxxColVisible(...)"` 守卫
- ❌ 不要动其它子任务负责的文件

---

## 6. 自检清单

改完每个列表后逐条确认：

- [ ] `useListColumns` 有 `fitMode: 'hybrid'` 和 `reservedWidth: 0`
- [ ] `*_COL_DEFAULTS` 每列都有 `width / visible / weight / minWidth / maxWidth`
- [ ] `actions` 列在 defaults 里且带 `fill: true`（或保留原有 `fixedColumns` 且 defaults 里没有 `actions`）
- [ ] `storageKey` 全站唯一
- [ ] 长文本单元格用了 `ulc-wrap-text`（多值拼接用 `ulc-joined` / `ulc-cell-stack`）
- [ ] 分页器换成 `MdPagination`，放在 `#pagination` slot
- [ ] 构建通过：`npm --prefix frontend run build`（0 错误）

**浏览器验证**（前端已跑在 `http://localhost:1820`，账号 `admin` / `admin123`）：
- [ ] 表格右侧无空白，铺满内容区
- [ ] 拖动某列表头边界，**其它列宽度不变**
- [ ] 长文字在列宽内自动换行，不被省略号截断
- [ ] 刷新页面后列宽保持
- [ ] 分页器外观与科室库房目录一致

---

## 7. 报告要求

完成后报告：
- 改了哪些文件、哪些列配置
- 每个列表的 `storageKey`
- 构建结果
- 浏览器验证结果（铺满 / 拖拽互不影响 / 换行 / 刷新记忆 / 分页器）
- 遇到的异常情况
