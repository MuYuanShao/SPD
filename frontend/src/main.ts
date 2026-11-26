import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { integerQuantity } from './directives/integerQuantity'
import { browserSessionAdapter, setSessionAdapter } from './api/session'
import { useAuthStore } from './stores/auth'
import { router } from './router'
import SpdButton from './components/common/SpdButton.vue'
import SpdFilterBar from './components/common/SpdFilterBar.vue'
import SpdStatusChip from './components/common/SpdStatusChip.vue'
import { ElCheckbox } from 'element-plus/es/components/checkbox/index.mjs'
import { ElPopover } from 'element-plus/es/components/popover/index.mjs'
import { ElTable, ElTableColumn } from 'element-plus/es/components/table/index.mjs'
import { ElContainer, ElHeader, ElMain } from 'element-plus/es/components/container/index.mjs'
import { ElMenu, ElMenuItem, ElSubMenu } from 'element-plus/es/components/menu/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus/es/components/dropdown/index.mjs'
import { ElBreadcrumb, ElBreadcrumbItem } from 'element-plus/es/components/breadcrumb/index.mjs'
import { ElCard } from 'element-plus/es/components/card/index.mjs'
import { ElRow } from 'element-plus/es/components/row/index.mjs'
import { ElCol } from 'element-plus/es/components/col/index.mjs'
import { ElTag } from 'element-plus/es/components/tag/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ElProgress } from 'element-plus/es/components/progress/index.mjs'
import { ElTimeline, ElTimelineItem } from 'element-plus/es/components/timeline/index.mjs'
import { ElPagination } from 'element-plus/es/components/pagination/index.mjs'
import { ElDialog } from 'element-plus/es/components/dialog/index.mjs'
import { ElCheckboxGroup } from 'element-plus/es/components/checkbox/index.mjs'
import { ElEmpty } from 'element-plus/es/components/empty/index.mjs'
import { ElSelect, ElOption } from 'element-plus/es/components/select/index.mjs'
import 'element-plus/theme-chalk/el-container.css'
import 'element-plus/theme-chalk/el-header.css'
import 'element-plus/theme-chalk/el-main.css'
import 'element-plus/theme-chalk/el-menu.css'
import 'element-plus/theme-chalk/el-menu-item.css'
import 'element-plus/theme-chalk/el-sub-menu.css'
import 'element-plus/theme-chalk/el-icon.css'
import 'element-plus/theme-chalk/el-dropdown.css'
import 'element-plus/theme-chalk/el-dropdown-menu.css'
import 'element-plus/theme-chalk/el-dropdown-item.css'
import 'element-plus/theme-chalk/el-breadcrumb.css'
import 'element-plus/theme-chalk/el-breadcrumb-item.css'
import 'element-plus/theme-chalk/el-card.css'
import 'element-plus/theme-chalk/el-row.css'
import 'element-plus/theme-chalk/el-col.css'
import 'element-plus/theme-chalk/el-tag.css'
import 'element-plus/theme-chalk/el-button.css'
import 'element-plus/theme-chalk/el-input.css'
import 'element-plus/theme-chalk/el-progress.css'
import 'element-plus/theme-chalk/el-timeline.css'
import 'element-plus/theme-chalk/el-timeline-item.css'
import 'element-plus/theme-chalk/el-pagination.css'
import 'element-plus/theme-chalk/el-dialog.css'
import 'element-plus/theme-chalk/el-checkbox-group.css'
import 'element-plus/theme-chalk/el-empty.css'
import 'element-plus/theme-chalk/el-select.css'
import 'element-plus/theme-chalk/el-option.css'
import 'element-plus/theme-chalk/el-input-number.css'
import 'element-plus/theme-chalk/el-scrollbar.css'
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-overlay.css'
import 'element-plus/theme-chalk/el-checkbox.css'
import 'element-plus/theme-chalk/el-popper.css'
import 'element-plus/theme-chalk/el-popover.css'
import 'element-plus/theme-chalk/el-table.css'
import 'element-plus/theme-chalk/el-table-column.css'
import 'element-plus/theme-chalk/el-message.css'
import './styles/main.css'
import './styles/responsive-medical.css'
/* 全站输入控件统一规格（唯一视觉来源）
   必须排在 styles/responsive-medical.css **之后**：
   responsive-medical.css 有一条 .main-area 内 input/select 的
   `min-height: 36px !important` 全局规则，本文件用更高特指度的
   同款 !important 把控件高度锁回 34px（--spd-p-field-height），
   保证「输入框现在也要统一成相同高度」真正生效。 */
import './styles/components/spd-input.css'
import './styles/tokens.scss'
import './styles/components/fli-layout.scss'

const app = createApp(App)
  .directive('integer-quantity', integerQuantity)
  .use(createPinia())
  .use(router)
  .use(ElCheckbox)
  .use(ElPopover)
  .use(ElTable)
  .use(ElTableColumn)

/* 全站通用按钮组件 SpdButton（样式：components/spd-button.css）
   颜色 / 字号全部取自令牌，本组件全局注册一次即可在任意模板中使用，
   无需逐文件 import。 */
app.component('SpdButton', SpdButton)

/* 全站通用筛选栏组件 SpdFilterBar（样式：components/spd-filter-bar.css）
   统一筛选栏的标准规则（栅格换行 / 标签排版 / 控件高度 / 操作区位置）
   全部由该组件与样式表承担，页面只写字段内容。 */
app.component('SpdFilterBar', SpdFilterBar)

/* 全站通用状态条组件 SpdStatusChip（样式：components/spd-status-chip.css）
   列表里的状态标签统一走这一个组件：几何（内边距 / 圆角 / 高度）与
   配色（浅色底 + 深色字）全部取自令牌，三套配色方案自动适配。 */
app.component('SpdStatusChip', SpdStatusChip)

for (const component of [ElContainer, ElHeader, ElMain, ElMenu, ElMenuItem, ElSubMenu, ElIcon, ElDropdown, ElDropdownMenu, ElDropdownItem, ElBreadcrumb, ElBreadcrumbItem, ElCard, ElRow, ElCol, ElTag, ElButton, ElInput, ElProgress, ElTimeline, ElTimelineItem, ElPagination, ElDialog, ElCheckboxGroup, ElEmpty, ElSelect, ElOption]) {
  app.use(component)
}
setSessionAdapter({
  ...browserSessionAdapter,
  handleUnauthorized() {
    const auth = useAuthStore()
    auth.logout()
    auth.initializationError = '登录已失效，请重新登录'
    if (router.currentRoute.value.matched.length && router.currentRoute.value.name !== 'login') {
      void router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    }
  },
})
app.mount('#app')
