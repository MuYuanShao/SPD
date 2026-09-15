import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { browserSessionAdapter, setSessionAdapter } from './api/session'
import { useAuthStore } from './stores/auth'
import { router } from './router'
import { ElCheckbox } from 'element-plus/es/components/checkbox/index.mjs'
import { ElPopover } from 'element-plus/es/components/popover/index.mjs'
import { ElTable, ElTableColumn } from 'element-plus/es/components/table/index.mjs'
import { ElContainer, ElHeader, ElMain } from 'element-plus/es/components/container/index.mjs'
import { ElMenu, ElMenuItem, ElSubMenu } from 'element-plus/es/components/menu/index.mjs'
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
import 'element-plus/theme-chalk/el-container.css'
import 'element-plus/theme-chalk/el-header.css'
import 'element-plus/theme-chalk/el-main.css'
import 'element-plus/theme-chalk/el-menu.css'
import 'element-plus/theme-chalk/el-menu-item.css'
import 'element-plus/theme-chalk/el-sub-menu.css'
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
import './styles/main.css'
import './styles/responsive-medical.css'
import './styles/tokens.scss'
import './styles/components/fli-layout.scss'

const app = createApp(App)
  .use(createPinia())
  .use(router)
  .use(ElCheckbox)
  .use(ElPopover)
  .use(ElTable)
  .use(ElTableColumn)

for (const component of [ElContainer, ElHeader, ElMain, ElMenu, ElMenuItem, ElSubMenu, ElDropdown, ElDropdownMenu, ElDropdownItem, ElBreadcrumb, ElBreadcrumbItem, ElCard, ElRow, ElCol, ElTag, ElButton, ElInput, ElProgress, ElTimeline, ElTimelineItem, ElPagination, ElDialog, ElCheckboxGroup, ElEmpty]) {
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
