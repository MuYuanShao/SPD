# 院内 SPD 系统开发规范

---

## 目录

1. [前端规范](#1-前端规范)
   - 1.1 组件组织
   - 1.2 UI 设计规范
   - 1.3 样式规范
   - 1.4 命名规范
   - 1.5 代码注释
2. [API 调用规范](#2-api-调用规范)
   - 2.1 请求封装
   - 2.2 响应格式
   - 2.3 接口定义
   - 2.4 错误处理
3. [后端规范](#3-后端规范)
   - 3.1 包结构与分层
   - 3.2 Controller 规范
   - 3.3 数据访问规范
   - 3.4 异常处理
   - 3.5 代码注释
4. [SQL 规范](#4-sql-规范)
   - 4.1 DDL 规范
   - 4.2 DML 规范
   - 4.3 Java 嵌入式 SQL 规范
   - 4.4 备注规范
5. [Git 提交规范](#5-git-提交规范)

---

## 1. 前端规范

### 1.1 组件组织

#### 目录结构

```
src/
├── api/                    # 接口请求层（按业务模块拆分）
│   ├── http.ts             # Axios 实例 + 通用响应类型
│   ├── foundation.ts       # 基础管理 API
│   ├── masterData.ts       # 主数据 API
│   └── ...
├── components/             # 公共组件
│   ├── common/             # 通用组件（按钮、表格、弹窗、表单等）
│   └── business/           # 业务组件（采购卡片、库存面板等）
├── config/                 # 前端配置（菜单、常量）
├── stores/                 # Pinia 状态管理
├── router/                 # 路由配置
├── styles/                 # 全局样式
│   └── main.css            # 主样式文件（CSS 变量 + 全局样式）
└── views/                  # 页面级组件（按业务模块分目录）
    ├── master-data/
    ├── supply-chain/
    └── system/
```

#### 组件拆分原则

1. **页面组件** (`views/`) 只负责布局编排和数据获取，不写复杂业务逻辑
2. **公共组件** (`components/common/`) 提取复用 ≥2 次的 UI 片段：
   - 通用表格组件（含分页、排序、多选）
   - 通用表单弹窗（新建/编辑）
   - 通用搜索栏
   - 状态标签/徽章
   - 确认对话框
   - 导入导出按钮组
3. **业务组件** (`components/business/`) 提取领域相关的可复用卡片/面板
4. 组件使用 `<script setup lang="ts">` 语法，使用 Vue 3 Composition API
5. 组件文件名用 PascalCase（如 `SupplierFormDialog.vue`）

#### 数据流

```
View（页面） → 调用 api/ 层函数 → 获取数据 → 存入 Pinia store（跨页面共享）或组件 local ref（页面内）
```

- **跨页面共享的状态**（如用户信息、模块列表）→ Pinia store
- **页面内部状态**（如当前列表数据、表单输入）→ `ref()` / `reactive()`
- 避免在子组件中直接调用 API，数据由父组件通过 props 传递

---

### 1.2 UI 设计规范

#### 布局

- **整体布局**：左侧导航 + 顶部栏 + 内容区（`App.vue` 已定义）
- **内容区宽度**：自适应，最大宽度 `1400px` 居中
- **页面标题**：每个页面顶部使用 `.section-title` 样式块（图标 + 标题文字）

```vue
<div class="section-title">
  <PackageSearch :size="22" />
  <h2>采购订单管理</h2>
</div>
```

#### 色彩系统

使用 CSS 自定义属性统一管理（已定义在 `main.css`）：

| 用途 | 变量示例 | 说明 |
|------|---------|------|
| 主色 | `--primary` | 品牌色（按钮、链接、选中态） |
| 成功 | `--success` | 启用/正常状态 |
| 警告 | `--warning` | 待处理/提醒 |
| 危险 | `--danger` | 禁用/删除/异常 |
| 背景 | `--bg-primary`, `--bg-secondary` | 页面/卡片背景 |
| 文字 | `--text-primary`, `--text-secondary` | 主/次要文字 |
| 边框 | `--border-color` | 表格/卡片边框 |

**规则**：
- 禁止在组件内硬编码颜色值（如 `#1890ff`），必须引用 CSS 变量
- 状态色语义固定：成功=绿色、警告=橙色、危险=红色、信息=蓝色

#### 字体

- 主字体：系统默认（`-apple-system, BlinkMacSystemFont, 'Segoe UI', ...`）
- 等宽字体（数字/编码场景）：`'SF Mono', 'Cascadia Code', monospace`
- 字号层级：`12px / 13px / 14px / 16px / 18px / 22px`，不得使用其他字号

#### 间距

- 基础间距单位：`4px`
- 常用间距：`4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48`
- 组件内部使用 `gap` + `padding`，禁止使用 `margin` 做兄弟间距（用 `gap` 替代）

#### 图标

- 统一使用 `@lucide/vue` 图标库，禁止引入其他图标库
- 图标尺寸统一：页面标题 `22px`，按钮内 `16px/18px`，表格操作列 `16px`

#### 表格

- 必须包含：表头、斑马纹、hover 高亮、水平滚动、空状态提示
- 状态列使用彩色标签（`.status-tag`）
- 操作列固定在右侧（`position: sticky; right: 0`）
- 数值列右对齐，文字列左对齐

#### 表单

- 标签在输入框上方（垂直排列），宽度统一
- 必填项加红色星号
- 验证错误信息显示在输入框下方，红色
- 提交按钮右对齐，取消/重置按钮在左侧

#### 响应式

- 最小支持屏幕宽度：`1280px`
- 表格/卡片在小屏时可水平滚动

---

### 1.3 样式规范

1. 全局样式写在 `src/styles/main.css`，组件样式用 `<style scoped>`
2. 禁止使用 `!important`（除非覆盖第三方库样式）
3. CSS 类名用 kebab-case（如 `.supplier-form .form-row`）
4. 选择器嵌套不超过 3 层
5. 动画/过渡统一用 `transition: all 0.2s ease`

```vue
<style scoped>
.supplier-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 680px;
}

.form-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
</style>
```

---

### 1.4 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| Vue 组件文件名 | PascalCase | `PurchaseOrderView.vue` |
| TypeScript 文件名 | camelCase | `masterData.ts` |
| TS 接口/类型 | PascalCase | `ProductDetail`, `SupplierPayload` |
| TS 函数 | camelCase | `fetchMasterDataPage`, `createSupplier` |
| TS 变量/常量 | camelCase (变量), UPPER_SNAKE (常量) | `deptList`, `MAX_PAGE_SIZE` |
| CSS 类名 | kebab-case | `.section-title`, `.login-panel` |
| Vue 路由路径 | kebab-case | `/master-data/hospital-products` |

#### TypeScript 类型定义优先级

1. 能用 `interface` 不用 `type`（对象结构）
2. 联合类型/函数签名用 `type`
3. 所有 API 请求/响应的数据结构必须定义类型
4. 禁止使用 `any`，未知类型用 `unknown`

---

### 1.5 代码注释

#### 必须写注释的场景：

1. **每个 `.ts` 文件顶部**：文件用途说明
```typescript
// 主数据管理 API——商品目录、供应商、厂家、科室、库房的 CRUD 与导入导出
```

2. **每个 API 函数**：JSDoc 风格
```typescript
/**
 * 查询商品详情（含证照附件列表）
 * @param productCode - 商品编码
 * @returns 商品完整信息
 */
export async function fetchHospitalProductDetail(productCode: string) { ... }
```

3. **复杂业务逻辑**：说明"为什么这么做"，不是"做了什么"
```typescript
// 采购计划生成时需校验供应商是否存在且启用，无绑定则取首个启用供应商兜底
```

4. **Pinia store 的 action**：说明副作用（调用了哪些 API、修改了哪些 state）
```typescript
// 登录：验证用户名密码 → 写入 localStorage → 更新 currentUser
login(username: string, password: string) { ... }
```

5. **组件 props / emits**：复杂参数必须注释
```vue
<script setup lang="ts">
// 显示模式：'list' 表格视图 | 'card' 卡片视图
const props = defineProps<{ mode: 'list' | 'card' }>()
</script>
```

#### 不需要注释的场景（代码即文档）：
- 简单的 getter/setter
- 一眼能看懂的变量赋值
- 标准 CRUD 调用

---

## 2. API 调用规范

### 2.1 请求封装

所有 HTTP 请求统一通过 `src/api/http.ts` 导出的 `http` 实例发送：

```typescript
import axios from 'axios'

export const http = axios.create({
  baseURL: '/api',          // Vite proxy 代理到后端 :1818
  timeout: 10000            // 10 秒超时
})
```

**规则**：
1. 禁止在组件中直接 `import axios` 或裸写 `fetch`
2. 禁止在组件中拼接请求 URL，所有 URL 必须在 `api/` 层定义为常量
3. 请求参数通过 `{ params }` 传递 query string，通过第二个参数传 request body

### 2.2 响应格式

后端统一返回结构：

```typescript
interface ApiResponse<T> {
  code: number       // 0 = 成功，非 0 = 业务错误
  message: string    // 提示信息
  data: T            // 业务数据
  timestamp: string  // 响应时间戳
}
```

**前端处理规则**：
- 成功：`response.data.data` 获取业务数据
- 失败：由拦截器统一 toast 提示，业务代码无需手动处理 `code !== 0`

### 2.3 API 函数定义规范

每个 `api/xxx.ts` 文件遵循以下格式：

```typescript
import { http, type ApiResponse } from './http'

// ========== 类型定义 ==========
export interface XxxPayload { ... }
export interface XxxDetail { ... }

// ========== API 函数 ==========

/** 查询列表 */
export async function fetchXxxList(query: Record<string, string>) {
  const res = await http.get<ApiResponse<XxxDetail[]>>('/xxx', { params: query })
  return res.data.data
}

/** 创建 */
export async function createXxx(payload: XxxPayload) {
  const res = await http.post<ApiResponse<{ id: number }>>('/xxx', payload)
  return res.data.data
}

/** 更新 */
export async function updateXxx(id: number, payload: XxxPayload) {
  const res = await http.put<ApiResponse<null>>(`/xxx/${id}`, payload)
  return res.data.data
}

/** 删除（软删除） */
export async function deleteXxx(ids: number[]) {
  const res = await http.put<ApiResponse<{ deletedRows: number }>>('/xxx/delete', { ids })
  return res.data.data
}
```

**URL 设计规范**：
- 资源名用 kebab-case 复数形式：`/purchase-orders`、`/master-data/suppliers`
- 单资源操作用路径参数：`GET /purchase-orders/:orderNo`
- 动作类操作用子路径：`PUT /purchase-orders/:orderNo/action`
- 分页参数：`?page=1&size=20&keyword=xxx`
- 筛选参数：`?status=draft&supplierName=xxx`

### 2.4 错误处理

1. **网络错误**（超时、断网）→ axios 拦截器统一弹 toast "网络异常，请稍后重试"
2. **HTTP 状态码错误**（4xx/5xx）→ 拦截器统一处理，401 跳转登录页
3. **业务错误**（`code !== 0`）→ 拦截器统一弹 `message` 提示
4. **组件中的 try/catch**：仅用于需要特殊降级逻辑的场景（如导入预览失败不阻塞页面），一般不要写

```typescript
// 拦截器示例（应在 http.ts 中配置）
http.interceptors.response.use(
  (response) => {
    const { code, message } = response.data
    if (code !== 0) {
      // 统一错误提示
      return Promise.reject(new Error(message || '请求失败'))
    }
    return response
  },
  (error) => {
    // 网络/HTTP 错误
    return Promise.reject(error)
  }
)
```

---

## 3. 后端规范

### 3.1 包结构与分层

```
com.hospital.spd
├── common/                  # 通用基础设施
│   ├── ApiResponse.java     # 统一响应体
│   ├── CorsConfig.java      # CORS 配置
│   ├── GlobalExceptionHandler.java  # 全局异常处理
│   └── WebMvcConfig.java
├── system/                  # 系统模块（健康检查、模块目录、系统配置）
├── foundation/              # 基础管理（用户、角色、权限、科室）
├── masterdata/              # 主数据管理（商品、供应商、厂家、审批）
├── supplychain/             # 供应链业务（采购、库存、申领、结算）
├── specialty/               # 专项管理（UDI、高值、冷链）
├── operations/              # 运营支撑（报表、配置）
└── extension/               # 业务扩展（院内扩展、合规扩展）
```

**分层规则**（当前项目采用 Controller 直连 JdbcTemplate 模式）：
- Controller：接收请求 + 参数校验 + 调用 JdbcTemplate + 返回 `ApiResponse`
- 每个模块允许有一个工具类（如 `SupplyChainSupport`），封装跨 Controller 共用的 SQL 片段、编码生成、审计写入
- 请求/响应数据结构用 Java `record` 定义，放在对应 Controller 同包下

**如果后续引入 Service 层**：
- Controller 只做参数绑定和响应封装
- Service 包含业务逻辑和事务管理
- Repository 封装数据访问（如果引入 MyBatis/JPA）

### 3.2 Controller 规范

```java
@RestController
@RequestMapping("/purchase-orders")  // kebab-case 复数
public class PurchaseOrderController {

    private final JdbcTemplate jdbcTemplate;
    private final SupplyChainSupport support;

    // 构造器注入（不用 @Autowired 字段注入）
    public PurchaseOrderController(JdbcTemplate jdbcTemplate, SupplyChainSupport support) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
    }

    // ===== 查询 =====

    /**
     * 分页查询采购订单列表，支持按单号、供应商、状态、商品关键字筛选
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam Map<String, String> params) { ... }

    /**
     * 查询采购订单详情，含订单头、明细行、操作记录
     */
    @GetMapping("/{orderNo}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String orderNo) { ... }

    // ===== 新增 =====

    /**
     * 创建采购订单（草稿状态），自动生成订单编号，写入审计日志
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody PurchaseOrderRequest request) { ... }

    // ===== 更新（动作驱动） =====

    /**
     * 订单状态流转：submit/approve/send/close/reject
     */
    @PutMapping("/{orderNo}/action")
    public ApiResponse<Map<String, Object>> action(@PathVariable String orderNo,
                                                   @RequestBody PurchaseOrderActionRequest request) { ... }

    // ===== 辅助端点 =====

    /** 获取订单下拉选项（供应商列表、商品列表） */
    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> options() { ... }
}
```

**规范要点**：

1. RESTful 风格：
   - `GET` 查询
   - `POST` 新增
   - `PUT` 更新/状态变更/软删除
   - 不使用 `DELETE`（所有删除均为软删除，用 `PUT .../delete`）

2. URL 命名：
   - 资源名 kebab-case 复数
   - 路径参数用 `@PathVariable`
   - 查询参数用 `@RequestParam`
   - 请求体用 `@RequestBody`

3. 返回值：
   - 所有方法返回 `ApiResponse<T>`
   - 查询返回 `ApiResponse.ok(data)`
   - 操作成功返回 `ApiResponse.ok(结果摘要 Map)`
   - 不允许返回 `void` 或裸数据

4. 依赖注入：
   - 使用构造器注入，不使用 `@Autowired` 字段注入
   - 便于单元测试和明确依赖关系

5. 参数校验：
   - 简单校验在 Controller 私有方法中做（抛 `IllegalArgumentException`）
   - 全局异常处理器将 `IllegalArgumentException` 转为 `ApiResponse.error(400, message)`

### 3.3 数据访问规范

当前项目使用 Spring JdbcTemplate，所有 SQL 以字符串形式写在 Controller 中。

```java
// 查询 —— 使用 text block (Java 17+)
List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
        SELECT po.order_no AS orderNo, s.supplier_name AS supplierName,
               po.order_status AS orderStatus, po.total_amount AS totalAmount
          FROM purchase_order po
          JOIN supplier s ON s.supplier_id = po.supplier_id
         WHERE 1 = 1
         ORDER BY po.create_time DESC
         LIMIT 100
        """);

// 插入 —— 使用 GeneratedKeyHolder 获取自增 ID
KeyHolder keyHolder = new GeneratedKeyHolder();
jdbcTemplate.update(connection -> {
    PreparedStatement ps = connection.prepareStatement(
        "INSERT INTO purchase_order (order_no, supplier_id, order_status, total_amount) VALUES (?, ?, ?, ?)",
        Statement.RETURN_GENERATED_KEYS
    );
    ps.setString(1, orderNo);
    ps.setLong(2, supplierId);
    ps.setString(3, "draft");
    ps.setBigDecimal(4, totalAmount);
    return ps;
}, keyHolder);
Long orderId = Objects.requireNonNull(keyHolder.getKey()).longValue();

// 更新
jdbcTemplate.update("""
        UPDATE purchase_order SET order_status = ?, approve_time = NOW() WHERE order_no = ?
        """, newStatus, orderNo);
```

**规则**：

1. **SQL 格式化**：必须使用 Java Text Block (`"""..."""`)，SQL 关键字大写，字段小写，缩进对齐
2. **SELECT 列别名**：使用 camelCase（与前端字段名一致），如 `order_no AS orderNo`
3. **参数绑定**：使用 `?` 占位符，通过可变参数传递，禁止字符串拼接 SQL（防注入）
4. **动态 SQL**：用 `StringBuilder` 拼接 WHERE 条件，配合 `List<Object> args`（参考 `PurchaseOrderController` 模式）
5. **NULL 处理**：用 `COALESCE` 处理聚合字段的空值（如 `COALESCE(SUM(poi.quantity), 0)`）
6. **日期格式化**：SQL 层用 `DATE_FORMAT(create_time, '%Y-%m-%d %H:%i')`，不在 Java 层二次格式化
7. **软删除**：所有查询必须加 `AND deleted = 0`
8. **记录审计**：创建/修改操作必须调用 `writeAudit()` 写入 `audit_log` 表

### 3.4 异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleBadRequest(IllegalArgumentException ex) {
        return ApiResponse.error(400, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        // 记录日志
        return ApiResponse.error(500, "服务器内部错误");
    }
}
```

**规则**：
- 业务校验不通过 → `throw new IllegalArgumentException("具体错误信息")` → 全局处理器返回 400
- 资源不存在 → `throw new IllegalArgumentException("xxx 不存在")`（当前项目简化处理）
- 未预期的异常 → 全局兜底返回 500 "服务器内部错误"
- 所有异常信息用中文，直接面向用户

### 3.5 代码注释

#### 必须写注释：

1. **每个 Java 文件顶部**：Javadoc 类说明
```java
/**
 * 采购订单控制器——订单的创建、查询、状态流转（提交/审批/发送/关闭/驳回），
 * 以及采购需求池、采购计划的生成与管理。
 */
```

2. **每个 public 方法**：Javadoc 说明功能、入参、返回值
```java
/**
 * 创建采购订单，状态为 draft。
 * 自动生成订单编号（CG 前缀），计算总金额，写入审计日志。
 *
 * @param request 订单创建请求（供应商 + 明细行）
 * @return orderNo 和 totalAmount
 */
@PostMapping
public ApiResponse<Map<String, Object>> create(@RequestBody PurchaseOrderRequest request) { ... }
```

3. **复杂 SQL 逻辑**：行内注释说明业务含义
```java
// 采购计划审批通过后转为正式采购订单，状态为 draft
String orderNo = support.nextNo("CG", "purchase_order", "order_no", 3);
```

4. **常量/魔法数字**：必须注释含义
```java
private static final int MAX_PAGE_SIZE = 100;  // 列表查询最大返回行数
```

5. **Record 类型**：注释每个字段含义
```java
public record PurchaseOrderRequest(
        /** 供应商名称（必填） */
        String supplierName,
        /** 订单来源：manual=手工, plan=采购计划, demand=需求池 */
        String orderSource,
        /** 预计到货日期（yyyy-MM-dd） */
        String expectedArrivalDate,
        /** 订单明细行（至少一行） */
        List<PurchaseOrderItemRequest> items
) {}
```

#### 不需要注释：
- 标准 getter/setter（本项目使用 Record 无此问题）
- 一眼能看懂的 Spring 注解
- 简单的赋值语句

---

## 4. SQL 规范

### 4.1 DDL 规范（`db/init/` 目录下的建表脚本）

```sql
CREATE TABLE IF NOT EXISTS `purchase_order` (
  `purchase_order_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '采购订单ID',
  `order_no` VARCHAR(50) NOT NULL COMMENT '订单编号（CG+日期+序号）',
  `supplier_id` BIGINT UNSIGNED NOT NULL COMMENT '供应商ID',
  `order_source` VARCHAR(30) DEFAULT NULL COMMENT '订单来源：manual-手工/plan-采购计划/demand-需求池',
  `order_status` VARCHAR(30) NOT NULL DEFAULT 'draft' COMMENT '订单状态：draft-草稿/pending_approval-待批/approved-已审/sent-已发送/closed-已关闭/rejected-已驳回',
  `purchase_type` VARCHAR(30) DEFAULT NULL COMMENT '采购类型：regular-常规/temporary-临时/urgent-紧急',
  `total_amount` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '订单总金额',
  `expected_arrival_date` DATE DEFAULT NULL COMMENT '预计到货日期',
  `create_by` BIGINT UNSIGNED NOT NULL COMMENT '创建人ID',
  `approve_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '审批人ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `approve_time` DATETIME DEFAULT NULL COMMENT '审批时间',
  `send_time` DATETIME DEFAULT NULL COMMENT '发送时间',
  `close_time` DATETIME DEFAULT NULL COMMENT '关闭时间',
  `close_reason` VARCHAR(500) DEFAULT NULL COMMENT '关闭原因（关闭时必填）',
  PRIMARY KEY (`purchase_order_id`),
  UNIQUE KEY `uk_purchase_order_no` (`order_no`) COMMENT '订单编号唯一',
  KEY `idx_purchase_order_supplier` (`supplier_id`) COMMENT '供应商查询',
  KEY `idx_purchase_order_status` (`order_status`) COMMENT '状态筛选',
  KEY `idx_purchase_order_create_time` (`create_time`) COMMENT '时间排序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购订单表';
```

**规范要点**：

1. **字段注释**：每个字段必须有 `COMMENT`，内容包括：
   - 字段含义
   - 枚举值说明（如状态、类型字段）
   - 格式说明（如日期格式、编码规则）
   - 单位说明（如金额单位、数量单位）

2. **表注释**：`COMMENT='表用途说明'`

3. **索引注释**：每个索引必须 `COMMENT` 说明用途

4. **命名规范**：
   - 表名：小写 + 下划线（`snake_case`）
   - 字段名：小写 + 下划线
   - 主键：`表名_id`（如 `purchase_order_id`）
   - 唯一索引：`uk_表名_字段名`
   - 普通索引：`idx_表名_字段名`

5. **数据类型约定**：
   - 主键/外键：`BIGINT UNSIGNED`
   - 状态/标记：`TINYINT`，注释中说明 0/1 含义
   - 编码/名称：`VARCHAR(50)`
   - 金额/数量：`DECIMAL(18,4)`
   - 长文本：`VARCHAR(500)` 或 `TEXT`
   - 时间戳：`DATETIME`
   - 日期：`DATE`

6. **必需字段**：每张业务表必须包含：
   - `create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'`
   - `deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记'`（如果需要软删除）

7. **引擎和字符集**：
   - 引擎：`InnoDB`
   - 字符集：`utf8mb4`，排序规则：`utf8mb4_0900_ai_ci`

8. **SQL 文件管理**：
   - 按编号前缀排序：`01-`, `02-`, `03-`, ...
   - 文件名描述性：`02-core-schema.sql`、`03-pending-product-application-fields.sql`
   - ALTER 扩展字段 → 单独文件，不修改原有 CREATE TABLE

### 4.2 DML 规范（种子数据）

```sql
-- ============================================================
-- 系统配置种子数据
-- ============================================================
INSERT INTO `system_config` (`config_key`, `config_value`, `config_group`, `description`, `status`)
VALUES
('inventory.low_stock_threshold', '10', '库存配置', '低库存预警阈值（最小库存量）', 1),
('order.auto_close_days', '30', '订单配置', '已发送订单自动关闭天数', 1);
```

**规范**：
- 种子数据 SQL 必须带注释说明数据用途
- INSERT 语句必须指定列名（不依赖列顺序）
- 批量插入用一条 INSERT 多行 VALUES

### 4.3 Java 嵌入式 SQL 规范

与 DDL 规范一致的命名和格式，额外要求：

1. SQL 使用 Java Text Block，保持 SQL 本身的缩进风格
2. SELECT 列使用 camelCase 别名（直接映射前端的 JSON key）
3. 动态 WHERE 条件拼接参考 `SupplyChainSupport` 工具类，禁止直接 `+` 拼接用户输入
4. 每个 SQL 语句块上方加单行注释说明业务目的

```java
// 查询供应商下拉选项（仅启用且未删除）
List<Map<String, Object>> suppliers = jdbcTemplate.queryForList("""
        SELECT supplier_name AS supplierName
          FROM supplier
         WHERE deleted = 0 AND status = 1
         ORDER BY supplier_id DESC
         LIMIT 100
        """);
```

### 4.4 备注规范总结

| 内容 | 是否必须 | 示例 |
|------|---------|------|
| 表 COMMENT | 必须 | `COMMENT='采购订单表'` |
| 字段 COMMENT | 必须 | `COMMENT '订单状态：draft-草稿/pending_approval-待批/...'` |
| 索引 COMMENT | 必须 | `COMMENT '供应商查询'` |
| 枚举值说明 | 必须（状态/类型字段） | `'订单来源：manual-手工/plan-计划/demand-需求'` |
| 金额/数量单位 | 必须（金额/数量字段） | `'订单总金额（元）'` |
| 编码规则 | 建议（编码字段） | `'订单编号（CG+YYYYMMDD+3位序号）'` |
| 默认值说明 | 建议 | `'创建时间（自动填充）'` |

---

## 5. Git 提交规范

### 提交信息格式

```
<type>(<scope>): <subject>

<body>
```

**type**：
- `feat`：新功能
- `fix`：Bug 修复
- `refactor`：重构（不改变功能）
- `style`：样式调整
- `docs`：文档更新
- `chore`：构建/配置/依赖变更

**scope**：模块名（如 `foundation`, `masterdata`, `supplychain`, `frontend`）

**示例**：
```
feat(supplychain): add purchase order creation with audit log

- 新增采购订单创建接口 POST /purchase-orders
- 自动生成 CG 前缀订单编号
- 写入 audit_log 审计记录
- 前端增加采购订单新建表单页面
```

### 禁止提交的内容

- `node_modules/`、`dist/`、`target/`
- IDE 配置文件（`.idea/`, `.vscode/`）
- 日志文件（`*.log`）
- 数据库密码、API Key 等敏感信息

---

*本规范适用于院内 SPD 系统全部前端和后端开发工作。如有异议或补充，请通过 Issue/PR 讨论修订。*
