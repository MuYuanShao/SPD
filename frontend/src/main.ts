import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { ElCheckbox } from 'element-plus/es/components/checkbox/index.mjs'
import { ElPopover } from 'element-plus/es/components/popover/index.mjs'
import { ElTable, ElTableColumn } from 'element-plus/es/components/table/index.mjs'
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-overlay.css'
import 'element-plus/theme-chalk/el-checkbox.css'
import 'element-plus/theme-chalk/el-popper.css'
import 'element-plus/theme-chalk/el-popover.css'
import 'element-plus/theme-chalk/el-table.css'
import 'element-plus/theme-chalk/el-table-column.css'
import './styles/main.css'
import './styles/responsive-medical.css'

createApp(App)
  .use(createPinia())
  .use(router)
  .use(ElCheckbox)
  .use(ElPopover)
  .use(ElTable)
  .use(ElTableColumn)
  .mount('#app')
