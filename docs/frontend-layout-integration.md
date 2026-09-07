# 顶部导航与示例首页接入

## 范围

保留 Vue、Vite 6、Pinia、Vue Router、现有业务 API 和后端架构。新增 ECharts 与 Sass，沿用 Element Plus 按需组件注册。原首页实时页面保留在 /dashboard/realtime，使用相同 dashboard 权限；/ 展示 mock Dashboard，并明确标记“演示数据”。其他业务页继续使用真实 API。

本次以需求描述的分区和配色为视觉基准。参考站点登录需要验证码，未取得登录后页面截图，因此尚未完成参考首页的逐像素比对。

## 布局和菜单

- App.vue：登录页分流、Element Plus 中文配置。
- layout/AppMain.vue：顶部、二级入口、面包屑、内容区和页脚；保留登录会话刷新与 AI 医护助手。
- layout/TopBar.vue、MenuBranch.vue：横向递归多级菜单，窄屏由 Element Plus 收纳溢出项。
- layout/SubMenu.vue：当前业务模块的叶子页面入口，横向滚动。
- layout/useLayoutNavigation.ts：复用 config/menu.ts 和 featureCatalog.ts，将原权限过滤和路由信息适配为布局数据。

菜单名称沿用实际业务目录，不添加没有业务实现的设备管理等参考站点专属模块。供应商厂家合并权限和拣配记录权限别名保持原规则。

新增页面时：

1. 在 views 对应业务目录创建页面，继续使用原 API 客户端和公共组件。
2. 在 config/featureCatalog.ts 注册功能、所属菜单组与原有权限码；按既有规则配置页面族或专用路由。
3. 在 router/index.ts 添加必要路由；已有动态页面族无需重复添加。设置 meta.requiresAuth，并可设置 meta.title、meta.featureCode。
4. 独立于 /features/:code 的新路由，需要同时在 permissionCodeForRoute 中明确权限映射。
5. 导航自动从功能目录生成；面包屑优先使用 meta.title，当前菜单优先使用 meta.featureCode，再使用动态参数和业务路径。

示例路由：

```ts
{
  path: '/features/example',
  name: 'example',
  component: () => import('../views/example/ExampleView.vue'),
  meta: { requiresAuth: true, title: '示例页面', featureCode: 'example' }
}
```

## 主题

styles/tokens.scss 提供品牌色、背景、边框、间距、圆角和阴影，同时映射旧 --primary 等变量和 Element Plus 主题变量。styles/components/fli-layout.scss 管理新布局和首页样式，通过 fli 前缀隔离。

首页最大宽度 1200px，桌面三栏比例为 7/10/7；业务页使用可用全宽。1024px 保留三栏，低于 992px 调整右栏，手机单列。新增业务页面不要添加顶部统计卡，除非另有明确要求。

## 首页组件

| 组件 | 输入 | 交互 |
| --- | --- | --- |
| StatisticCard | title、value、unit、tone、icon | 展示指标 |
| AlertCard | title、value、unit、tone | 展示预警数量 |
| TodoList | items：id、title、status、progress | select 事件交给页面处理 |
| NoticeList | items：id、title、date、content | select 事件交给页面处理 |
| TrendChart | labels、series、title、height、unit | ECharts 提示、图例切换；自动缩放和释放 |

mock 数据集中在 mocks/dashboard.ts，不发起业务请求。筛选、分页、公告和待办详情均为本地交互。常用功能选择仅对当前页面会话生效，刷新后恢复默认；候选项遵循当前用户权限，点击进入真实业务页。

替换 mock 时在页面或数据 composable 中接入实际接口，不在展示组件内请求数据；明确金额、数量和时间范围口径后映射 props。销售指标仅是演示文案，不改变现有 HIS 收费后扣库存规则。

## 验证

```powershell
npm --prefix frontend run build
npm run dev:frontend
```

打开 http://localhost:1820，使用本地系统账号登录。参考站点账号不用于本地系统。检查 1280px 与 1024px、菜单溢出、二级导航、受限账号、搜索空状态、分页、公告弹窗和退出登录。

真实业务冒烟测试另需本地 MySQL、后端和 SPD_E2E_USERNAME/SPD_E2E_PASSWORD：

```powershell
npm run test:smoke:business-ui
```

## 本次验收记录（2026-09-07）

- vue-tsc 与 Vite 生产构建通过；无循环分包警告。Element Plus 和 ECharts 仍有超过 500kB 的包体提示，图表独立分包。
- 生产预览浏览器验收通过：两张图表、商品搜索、空状态、翻页、公告详情、待办详情、常用功能勾选。
- 1280×1000 与 1024×1000 截图已核对；页面及主内容区无横向溢出。截图位于 output/playwright/fli-dashboard-1280.png 和 fli-dashboard-1024.png。
- 独立浏览器会话以拦截的 auth/me 返回演示账号，验证受限用户仅见首页菜单、无权业务路由跳转 forbidden、退出后清除 token 并返回登录页。未改动真实账号或权限数据。
- 生产首页验收未捕获 pageerror；git diff --check 通过，frontend/src 未生成 JavaScript 编译副本。
- 真实业务冒烟未执行：本次环境没有运行后端，也未配置本地业务测试账号。参考系统的验证码阻止了登录后视觉比对。
