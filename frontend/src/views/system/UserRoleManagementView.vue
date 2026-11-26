<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Teleport } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Database,
  KeyRound,
  LayoutList,
  Pencil,
  Plus,
  RefreshCw,
  Save,
  Search,
  Shield,
  ShieldCheck,
  Trash2,
  UserCheck,
  UserCog,
  UserPlus,
  Users,
  X
} from '@lucide/vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
import MdPagination from '../../components/master-data/MdPagination.vue'
import {
  assignRolePermissions,
  assignUserRoles,
  createRole,
  createUser,
  deleteRoles,
  deleteUsers,
  fetchDepartmentOptions,
  fetchPermissions,
  fetchRoles,
  fetchUsers,
  resetUserPasswords,
  updateDataPermission,
  updateRole,
  updateUser,
  type DepartmentOption,
  type PermissionRow,
  type RoleRow,
  type UserRow,
  type UserPayload
} from '../../api/foundation'
import { useAuthStore } from '../../stores/auth'
import { useListColumns, type ListColumnConfig } from '../../composables/useListColumns'
import { useActionColumnFix } from '../../composables/useActionColumnFix'
import { useColSettingsPopover } from '../../composables/useColSettingsPopover'
import MdDataTableCard from '../../components/master-data/MdDataTableCard.vue'

import {
  Columns3,
  ChevronDown
} from '@lucide/vue'
import { useNotify } from '../../composables/useNotify'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const notify = useNotify()
const pageMode = computed(() => (String(route.params.code) === 'role-permission' ? 'role' : 'user'))
const users = ref<UserRow[]>([])
const roles = ref<RoleRow[]>([])
const permissions = ref<PermissionRow[]>([])
const departments = ref<DepartmentOption[]>([])
const selectedUserIds = ref<number[]>([])
const selectedRoleIds = ref<number[]>([])
const loading = ref(false)
const userFormError = ref('')
const userPagination = reactive({ page: 1, size: 20, total: 0 })
const rolePagination = reactive({ page: 1, size: 20, total: 0 })
const modal = ref<'user' | 'role' | 'user-role' | 'role-permission' | 'data-permission' | null>(null)
const editingUserId = ref<number | null>(null)
const editingRoleId = ref<number | null>(null)

const userQuery = reactive({ username: '', realName: '', status: '', deptName: '' })
const roleQuery = reactive({ roleName: '', roleCode: '', status: '' })
const userForm = reactive<UserPayload & { password: string }>({
  username: '',
  password: '',
  realName: '',
  phone: '',
  email: '',
  gender: 0,
  deptId: 0,
  status: 1,
  roleIds: []
} as UserPayload & { password: string })
const roleForm = reactive({
  roleName: '', roleCode: '', description: '',
  dataScope: 1, status: 1, sortOrder: 99
})
const permissionForm = reactive({
  roleId: '', permissionIds: [] as number[], dataScope: 1, deptIds: [] as number[]
})

const selectedUser = computed(() => users.value.find(item => item.userId === selectedUserIds.value[0]))
const selectedRole = computed(() => roles.value.find(item => item.roleId === selectedRoleIds.value[0]))
const title = computed(() => pageMode.value === 'role' ? '角色权限' : '用户管理')

function roleIdsFrom(row?: UserRow) {
  return row?.roleIds ? row.roleIds.split(',').map(Number).filter(Boolean) : []
}
function permissionIdsFrom(row?: RoleRow) {
  return row?.permissionIds ? row.permissionIds.split(',').map(Number).filter(Boolean) : []
}

async function loadAll() {
  loading.value = true
  userFormError.value = ''
  try {
    const [u, r, p, d] = await Promise.all([
      fetchUsers({ ...userQuery, page: String(userPagination.page), size: String(userPagination.size) }),
      fetchRoles({ ...roleQuery, page: String(rolePagination.page), size: String(rolePagination.size) }),
      fetchPermissions(),
      fetchDepartmentOptions()
    ])
    users.value = u.rows
    userPagination.total = u.total
    roles.value = r.rows
    rolePagination.total = r.total
    permissions.value = p
    departments.value = d
  } catch (err: any) { notify.error(err?.message || '数据加载失败') }
  finally { loading.value = false }
}

function changeUserPage(p: number, s?: number) { if (s !== undefined) userPagination.size = s; userPagination.page = p; loadAll() }
function changeRolePage(p: number, s?: number) { if (s !== undefined) rolePagination.size = s; rolePagination.page = p; loadAll() }
function changeUserPageSize(s: number) { changeUserPage(1, s) }
function changeRolePageSize(s: number) { changeRolePage(1, s) }

function openUserCreate() {
  userFormError.value = ''
  editingUserId.value = null
  Object.assign(userForm, { username: '', password: '', realName: '', phone: '', email: '', gender: 0, deptId: 0, status: 1, roleIds: [] })
  modal.value = 'user'
}
function openUserEdit(row?: UserRow) {
  userFormError.value = ''
  if (row) { selectedUserIds.value = [row.userId]; editingUserId.value = row.userId }
  else if (!selectedUserIds.value.length) { notify.warning('请先勾选要编辑的用户'); return }
  const u = selectedUser.value
  if (!u) return
  Object.assign(userForm, { username: u.username, password: '', realName: u.realName, phone: u.phone || '', email: u.email || '', gender: u.gender || 0, deptId: Number(u.deptId) || 0, status: u.status, roleIds: roleIdsFrom(u) })
  modal.value = 'user'
}
function openRoleCreate() {
  editingRoleId.value = null
  Object.assign(roleForm, { roleName: '', roleCode: '', description: '', dataScope: 1, status: 1, sortOrder: 99 })
  modal.value = 'role'
}
function openRoleEdit() {
  if (!selectedRoleIds.value.length) { notify.warning('请先勾选要编辑的角色'); return }
  const r = selectedRole.value
  if (!r) return
  editingRoleId.value = r.roleId
  Object.assign(roleForm, { roleName: r.roleName, roleCode: r.roleCode, description: r.description || '', dataScope: r.dataScope || 1, status: r.status, sortOrder: r.sortOrder || 99 })
  modal.value = 'role'
}
function openPermissionAssign() {
  if (!selectedRoleIds.value.length) { notify.warning('请先勾选要分配权限的角色'); return }
  const r = selectedRole.value
  if (!r) return
  permissionForm.roleId = String(r.roleId)
  permissionForm.permissionIds = permissionIdsFrom(r)
  modal.value = 'role-permission'
}
function openDataPermission() {
  if (!selectedRoleIds.value.length) { notify.warning('请先勾选角色'); return }
  const r = selectedRole.value
  if (!r) return
  permissionForm.roleId = String(r.roleId)
  permissionForm.dataScope = r.dataScope || 1
  permissionForm.deptIds = []
  modal.value = 'data-permission'
}
function openUserRoleAssign() {
  if (!selectedUserIds.value.length) { notify.warning('请先勾选要分配角色的用户'); return }
  userForm.roleIds = roleIdsFrom(selectedUser.value)
  modal.value = 'user-role'
}

async function saveUser() {
  if (!userForm.username?.trim()) { userFormError.value = '用户名为必填项'; return }
  try {
    if (editingUserId.value === null) {
      if (!userForm.password?.trim()) { userFormError.value = '新增用户需填写初始密码'; return }
      await createUser(userForm)
    } else { await updateUser(editingUserId.value, userForm) }
    modal.value = null
    notify.success('用户已保存')
    await loadAll()
  } catch (err: any) { userFormError.value = err?.message || '保存失败' }
}

async function removeUsers() {
  if (!selectedUserIds.value.length) { notify.warning('请勾选要删除的用户'); return }
  if (!confirm(`确认删除选中的 ${selectedUserIds.value.length} 个用户？`)) return
  try {
    await deleteUsers(selectedUserIds.value)
    notify.success('用户已删除')
    selectedUserIds.value = []
    await loadAll()
  } catch (err: any) { notify.error(err?.message || '删除失败') }
}

async function resetPasswords() {
  if (!selectedUserIds.value.length) { notify.warning('请勾选要重置密码的用户'); return }
  if (!confirm(`确认重置 ${selectedUserIds.value.length} 个用户的密码？`)) return
  try {
    await resetUserPasswords(selectedUserIds.value)
    notify.success('密码已重置为默认密码')
  } catch (err: any) { notify.error(err?.message || '重置失败') }
}

async function saveUserRoles() {
  if (!selectedUserIds.value.length) return
  try {
    await assignUserRoles(selectedUserIds.value[0], userForm.roleIds)
    modal.value = null
    notify.success('角色分配已保存')
    await loadAll()
  } catch (err: any) { notify.error(err?.message || '保存失败') }
}

async function saveRole() {
  if (!roleForm.roleName.trim()) { notify.warning('角色名称为必填项'); return }
  if (!roleForm.roleCode.trim()) { notify.warning('角色编码为必填项'); return }
  try {
    if (editingRoleId.value === null) { await createRole(roleForm) }
    else { await updateRole(editingRoleId.value, roleForm) }
    modal.value = null
    notify.success('角色已保存')
    await loadAll()
  } catch (err: any) { notify.error(err?.message || '保存失败') }
}

async function removeRoles() {
  if (!selectedRoleIds.value.length) { notify.warning('请勾选要删除的角色'); return }
  if (!confirm(`确认删除选中的 ${selectedRoleIds.value.length} 个角色？`)) return
  try {
    await deleteRoles(selectedRoleIds.value)
    notify.success('角色已删除')
    selectedRoleIds.value = []
    await loadAll()
  } catch (err: any) { notify.error(err?.message || '删除失败') }
}

async function saveRolePermissions() {
  try {
    await assignRolePermissions(Number(permissionForm.roleId), permissionForm.permissionIds)
    modal.value = null
    notify.success('权限分配已保存')
    await loadAll()
  } catch (err: any) { notify.error(err?.message || '保存失败') }
}

async function saveDataPermission() {
  try {
    await updateDataPermission(Number(permissionForm.roleId), permissionForm.dataScope, permissionForm.deptIds)
    modal.value = null
    notify.success('数据权限已保存')
    await loadAll()
  } catch (err: any) { notify.error(err?.message || '保存失败') }
}

function dataScopeName(scope: number) {
  return ['-', '全部数据', '本部门数据', '本部门及子部门', '仅本人数据', '自定义部门'][scope] ?? '全部数据'
}
function onUserCheckbox(id: number, e: Event) {
  const checked = (e.target as HTMLInputElement).checked
  selectedUserIds.value = checked ? [...selectedUserIds.value, id] : selectedUserIds.value.filter(x => x !== id)
}
function onRoleCheckbox(id: number, e: Event) {
  const checked = (e.target as HTMLInputElement).checked
  selectedRoleIds.value = checked ? [...selectedRoleIds.value, id] : selectedRoleIds.value.filter(x => x !== id)
}

function switchToUser() { router.push('/features/user-management') }
function switchToRole() { router.push('/features/role-permission') }

onMounted(loadAll)
watch(pageMode, () => {
  selectedUserIds.value = []
  selectedRoleIds.value = []
  userPagination.page = 1
  rolePagination.page = 1
  loadAll()
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 默认 'fixed'：每列宽度互相独立，拖拽某一列不影响其它列；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const USERLIST_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'selection', label: '', width: 48, visible: true, weight: 0, minWidth: 48, maxWidth: 48 },
  { key: 'username', label: '用户名', width: 140, visible: true, weight: 4, minWidth: 110, maxWidth: 210 },
  { key: 'realName', label: '真实姓名', width: 120, visible: true, weight: 3, minWidth: 100, maxWidth: 180 },
  { key: 'deptName', label: '所属科室', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 210 },
  { key: 'roleNames', label: '角色', width: 190, visible: true, weight: 5, minWidth: 130, maxWidth: 280 },
  { key: 'phone', label: '手机', width: 130, visible: true, weight: 3, minWidth: 110, maxWidth: 190 },
  { key: 'email', label: '邮箱', width: 190, visible: true, weight: 4, minWidth: 140, maxWidth: 280 },
  { key: 'status', label: '状态', width: 90, visible: true, weight: 2, minWidth: 80, maxWidth: 130 },
  { key: 'createTime', label: '创建时间', width: 170, visible: true, weight: 3, minWidth: 140, maxWidth: 220 },
  { key: 'updateTime', label: '更新时间', width: 170, visible: true, weight: 3, minWidth: 140, maxWidth: 220 },
  // 「操作」列必须出现在 defaults 里：isColumnVisible() 只查 defaults，
  // 缺少该键时 v-if="isUserListColVisible('actions')" 恒为 false，
  // 整列不会渲染，「编辑」等按钮将完全无法触达。
  { key: 'actions', label: '操作', width: 150, visible: true, weight: 3, minWidth: 130, maxWidth: 220, fill: true }
]

const userListCols = useListColumns({
  storageKey: 'spd-user-list-cols-v1',
  defaults: USERLIST_COL_DEFAULTS,
  // 混合策略：按权重把内容区宽度铺满给所有列，
  // 角色 / 邮箱等长文本列额外享有「理想宽度优先」调剂。
  fitMode: 'hybrid',
  reservedWidth: 0
})

const userListTableRef = userListCols.tableRef
const isUserListColVisible = (key: string) => userListCols.isColumnVisible(key)
const userListColBinding = (key: string) => userListCols.columnBinding(key)

/**
 * 操作列固定规则：出现横向滚动条才固定右侧，没有则不固定。
 * 必须在 setup 顶层展平成 computed —— 模板不会自动解包普通对象里的 computed。
 */
const userListActionFix = useActionColumnFix()
const userListActionFixed = computed(() => userListActionFix.actionFixedBinding.value)

watch(
  [userListTableRef],
  () => {
    userListActionFix.observe(userListTableRef.value)
  },
  { flush: 'post' }
)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const userListColSettingsAnchorRef = ref<HTMLElement | null>(null)
const userListColSettingsPopRef = ref<HTMLElement | null>(null)
const userListColSettingsPop = useColSettingsPopover({
  open: userListCols.settingsOpen,
  anchorRef: userListColSettingsAnchorRef,
  popRef: userListColSettingsPopRef
})

/* ===== 统一列表：列宽拖拽 + 列设置 + localStorage 持久化 =====
   ---------------------------------------------------------------------
   与其它统一列表页共用同一套列宽机制（useListColumns）：
     - fitMode 默认 'fixed'：每列宽度互相独立，拖拽某一列不影响其它列；
     - 列宽与列显隐持久化到 localStorage；
     - max-height 让表体成为独立滚动区，表头天然固定。
   ===================================================================== */
const ROLELIST_COL_DEFAULTS: ListColumnConfig[] = [
  { key: 'selection', label: '', width: 48, visible: true, weight: 0, minWidth: 48, maxWidth: 48 },
  { key: 'roleName', label: '角色名称', width: 150, visible: true, weight: 5, minWidth: 110, maxWidth: 220 },
  { key: 'roleCode', label: '角色编码', width: 160, visible: true, weight: 4, minWidth: 120, maxWidth: 230 },
  { key: 'dataScope', label: '数据权限', width: 140, visible: true, weight: 3, minWidth: 110, maxWidth: 200 },
  { key: 'userCount', label: '用户数', width: 90, visible: true, weight: 2, minWidth: 80, maxWidth: 130 },
  { key: 'permissionCount', label: '权限点', width: 90, visible: true, weight: 2, minWidth: 80, maxWidth: 130 },
  { key: 'status', label: '状态', width: 90, visible: true, weight: 2, minWidth: 80, maxWidth: 130 },
  { key: 'description', label: '说明', width: 220, visible: true, flexible: true, idealWidth: 280, weight: 6, minWidth: 130, maxWidth: 380 },
  { key: 'createTime', label: '创建时间', width: 170, visible: true, weight: 3, minWidth: 140, maxWidth: 220 },
  { key: 'updateTime', label: '修改时间', width: 170, visible: true, weight: 3, minWidth: 140, maxWidth: 220, fill: true }
]

const roleListCols = useListColumns({
  storageKey: 'spd-role-list-cols-v1',
  defaults: ROLELIST_COL_DEFAULTS,
  // 混合策略：按权重把内容区宽度铺满给所有列，
  // 「说明」列额外享有「理想宽度优先」调剂。
  fitMode: 'hybrid',
  reservedWidth: 0
})

const roleListTableRef = roleListCols.tableRef
const isRoleListColVisible = (key: string) => roleListCols.isColumnVisible(key)
const roleListColBinding = (key: string) => roleListCols.columnBinding(key)

/** 「列设置」浮层的视口定位（右侧空间不足时自动改为右对齐） */
const roleListColSettingsAnchorRef = ref<HTMLElement | null>(null)
const roleListColSettingsPopRef = ref<HTMLElement | null>(null)
const roleListColSettingsPop = useColSettingsPopover({
  open: roleListCols.settingsOpen,
  anchorRef: roleListColSettingsAnchorRef,
  popRef: roleListColSettingsPopRef
})

</script>

<template>
  <div class="sys-page spd-unified-list">

    <MdPageHeader
      :title="title"
      :loading="loading"
      :show-refresh="false"
      @refresh="loadAll"
    />

    <!-- Tab -->
    <div class="sys-tabs">
      <button type="button" :class="['sys-tab', { active: pageMode === 'user' }]" @click="switchToUser">
        <LayoutList :size="14" /> 用户管理
      </button>
      <button type="button" :class="['sys-tab', { active: pageMode === 'role' }]" @click="switchToRole">
        <ShieldCheck :size="14" /> 角色权限
      </button>
    </div>

    <!-- 工具栏 -->
    <div class="sys-toolbar-panel">
      <div class="sys-toolbar-search">
        <i class="sys-toolbar-search-icon"><Search :size="15" /></i>
        <input
          v-if="pageMode === 'user'"
          v-model="userQuery.username"
          placeholder="用户名 / 姓名 / 科室"
          @keyup.enter="loadAll"
        />
        <input
          v-else
          v-model="roleQuery.roleName"
          placeholder="角色名称 / 编码"
          @keyup.enter="loadAll"
        />
      </div>
      <div class="sys-toolbar-actions">
        <button class="btn btn-primary" type="button" @click="loadAll"><Search :size="15" /> 查询</button>
        <button class="btn" type="button" @click="loadAll"><RefreshCw :size="15" /> 重置</button>
        <i class="sys-toolbar-sep"></i>
        <template v-if="pageMode === 'user'">
          <button v-if="authStore.hasPermission('user:create')" class="btn" type="button" @click="openUserCreate"><Plus :size="15" /> 新增用户</button>
          <button v-if="authStore.hasPermission('user:update')" class="btn" type="button" @click="openUserEdit()"><Pencil :size="15" /> 编辑</button>
          <button v-if="authStore.hasPermission('user:delete')" class="btn btn-danger" type="button" @click="removeUsers"><Trash2 :size="15" /> 删除</button>
          <button v-if="authStore.hasPermission('user:reset-password')" class="btn" type="button" @click="resetPasswords"><KeyRound :size="15" /> 重置密码</button>
          <button v-if="authStore.hasPermission('user:assign-role')" class="btn" type="button" @click="openUserRoleAssign"><UserCheck :size="15" /> 分配角色</button>
        </template>
        <template v-else>
          <button v-if="authStore.hasPermission('role:create')" class="btn" type="button" @click="openRoleCreate"><Plus :size="15" /> 新增角色</button>
          <button v-if="authStore.hasPermission('role:update')" class="btn" type="button" @click="openRoleEdit()"><Pencil :size="15" /> 编辑</button>
          <button v-if="authStore.hasPermission('role:delete')" class="btn btn-danger" type="button" @click="removeRoles"><Trash2 :size="15" /> 删除</button>
          <button v-if="authStore.hasPermission('role:assign-permission')" class="btn" type="button" @click="openPermissionAssign"><ShieldCheck :size="15" /> 分配权限</button>
          <button v-if="authStore.hasPermission('role:data-permission')" class="btn" type="button" @click="openDataPermission"><Database :size="15" /> 数据权限</button>
        </template>
      </div>
    </div>

    <!-- 消息：保存 / 操作反馈统一走顶部 ElMessage 弹层，不再占用页面空间 -->

    <!-- 内容 -->
    <div class="sys-content">

      <!-- 用户列表 -->
      <MdDataTableCard v-if="pageMode === 'user'" class="master-table-card">
          <template #toolbar>
            <div class="ulc-col-settings">
              <button
                ref="userListColSettingsAnchorRef"
                type="button"
                class="md-btn md-btn-primary"
                @click="userListCols.settingsOpen.value = !userListCols.settingsOpen.value"
              >
                <Columns3 :size="16" />
                <span>列设置</span>
                <ChevronDown :size="14" :class="{ rotated: userListCols.settingsOpen.value }" />
              </button>
              <div
                v-if="userListCols.settingsOpen.value"
                ref="userListColSettingsPopRef"
                class="ulc-col-settings-pop"
                :class="{ 'is-align-right': userListColSettingsPop.align.value === 'right', 'is-align-up': userListColSettingsPop.vertical.value === 'up' }"
                :style="{ '--ulc-pop-offset-y': userListColSettingsPop.offsetY.value + 'px', '--ulc-pop-max-h': userListColSettingsPop.maxHeight.value + 'px' }"
              >
                <header>
                  <strong>列展示设置</strong>
                  <button type="button" class="ulc-link" @click="userListCols.resetColumns()">恢复默认</button>
                </header>
                <div class="ulc-col-settings-list">
                  <label v-for="col in userListCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                    <input
                      type="checkbox"
                      :checked="col.visible"
                      :disabled="col.visible && userListCols.visibleColumnCount.value <= 1"
                      @change="userListCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                    />
                    <span>{{ col.label }}</span>
                    <small>{{ col.width }}px</small>
                  </label>
                </div>
                <footer>
                  <button type="button" class="ulc-action" @click="userListCols.settingsOpen.value = false">确定</button>
                </footer>
              </div>
            </div>
          </template>
          <template #meta-extra>
            <span class="md-chip primary">共 {{ userPagination.total }} 条</span>
          </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="userListTableRef"
            v-loading="loading"
            :data="users"
            row-key="userId"
            border
            stripe
            class="ulc-table is-sticky"
            max-height="560"
            style="width: 100%"
            :empty-text="'暂无用户数据'"
            @header-dragend="(width: number, oldWidth: number, column: any) => userListCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="selection"
              v-if="isUserListColVisible('selection')"
              label=""
              v-bind="userListColBinding('selection')"
            >
              <template #header>
                <span class="ulc-th-text"></span>
              </template>
              <template #default="{ row }">
                <input type="checkbox" :checked="selectedUserIds.includes(row.userId)" @change="onUserCheckbox(row.userId, $event)" />
              </template>
            </el-table-column>
            <el-table-column
              column-key="username"
              v-if="isUserListColVisible('username')"
              label="用户名"
              v-bind="userListColBinding('username')"
            >
              <template #header>
                <span class="ulc-th-text">用户名</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-mono ulc-wrap-text">{{ row.username }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="realName"
              v-if="isUserListColVisible('realName')"
              label="真实姓名"
              v-bind="userListColBinding('realName')"
            >
              <template #header>
                <span class="ulc-th-text">真实姓名</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ row.realName }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="deptName"
              v-if="isUserListColVisible('deptName')"
              label="所属科室"
              v-bind="userListColBinding('deptName')"
            >
              <template #header>
                <span class="ulc-th-text">所属科室</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.deptName || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="roleNames"
              v-if="isUserListColVisible('roleNames')"
              label="角色"
              v-bind="userListColBinding('roleNames')"
            >
              <template #header>
                <span class="ulc-th-text">角色</span>
              </template>
              <template #default="{ row }">
                <span class="sys-tag is-role ulc-wrap-text">{{ row.roleNames || '未分配' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="phone"
              v-if="isUserListColVisible('phone')"
              label="手机"
              v-bind="userListColBinding('phone')"
            >
              <template #header>
                <span class="ulc-th-text">手机</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.phone || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="email"
              v-if="isUserListColVisible('email')"
              label="邮箱"
              v-bind="userListColBinding('email')"
            >
              <template #header>
                <span class="ulc-th-text">邮箱</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.email || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="status"
              v-if="isUserListColVisible('status')"
              label="状态"
              v-bind="userListColBinding('status')"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <span :class="['sys-badge', row.status === 1 ? 'is-active' : 'is-stopped']">{{ row.status === 1 ? '启用' : '停用' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="createTime"
              v-if="isUserListColVisible('createTime')"
              label="创建时间"
              v-bind="userListColBinding('createTime')"
            >
              <template #header>
                <span class="ulc-th-text">创建时间</span>
              </template>
              <template #default="{ row }">
                <span class="md-audit-time">
                  {{ row.createTime || '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="updateTime"
              v-if="isUserListColVisible('updateTime')"
              label="更新时间"
              v-bind="userListColBinding('updateTime')"
            >
              <template #header>
                <span class="ulc-th-text">更新时间</span>
              </template>
              <template #default="{ row }">
                {{ row.updateTime || '-' }}
              </template>
            </el-table-column>
            <el-table-column
              column-key="actions"

              v-if="isUserListColVisible('actions')"

              label="操作"

              v-bind="userListColBinding('actions')"
              
              :fixed="userListActionFixed"
              align="center"
            >
              <template #header>
                <span class="ulc-th-text">操作</span>
              </template>
              <template #default="{ row }">
                <div class="ulc-actions">
                <button class="ulc-action" type="button" @click="openUserEdit(row)"><Pencil :size="13" /> 编辑</button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <template #pagination>
          <MdPagination
            :page="userPagination.page"
            :total-pages="Math.max(1, Math.ceil(userPagination.total / userPagination.size))"
            :total="userPagination.total"
            :size="userPagination.size"
            :loading="loading"
            @update:page="changeUserPage"
            @update:size="changeUserPageSize"
          />
        </template>
      </MdDataTableCard>

      <!-- 角色列表 -->
      <MdDataTableCard v-else class="master-table-card">
          <template #toolbar>
            <div class="ulc-col-settings">
              <button
                ref="roleListColSettingsAnchorRef"
                type="button"
                class="md-btn md-btn-primary"
                @click="roleListCols.settingsOpen.value = !roleListCols.settingsOpen.value"
              >
                <Columns3 :size="16" />
                <span>列设置</span>
                <ChevronDown :size="14" :class="{ rotated: roleListCols.settingsOpen.value }" />
              </button>
              <div
                v-if="roleListCols.settingsOpen.value"
                ref="roleListColSettingsPopRef"
                class="ulc-col-settings-pop"
                :class="{ 'is-align-right': roleListColSettingsPop.align.value === 'right', 'is-align-up': roleListColSettingsPop.vertical.value === 'up' }"
                :style="{ '--ulc-pop-offset-y': roleListColSettingsPop.offsetY.value + 'px', '--ulc-pop-max-h': roleListColSettingsPop.maxHeight.value + 'px' }"
              >
                <header>
                  <strong>列展示设置</strong>
                  <button type="button" class="ulc-link" @click="roleListCols.resetColumns()">恢复默认</button>
                </header>
                <div class="ulc-col-settings-list">
                  <label v-for="col in roleListCols.columns.value" :key="col.key" class="ulc-col-settings-item">
                    <input
                      type="checkbox"
                      :checked="col.visible"
                      :disabled="col.visible && roleListCols.visibleColumnCount.value <= 1"
                      @change="roleListCols.setColumnVisible(col.key, ($event.target as HTMLInputElement).checked)"
                    />
                    <span>{{ col.label }}</span>
                    <small>{{ col.width }}px</small>
                  </label>
                </div>
                <footer>
                  <button type="button" class="ulc-action" @click="roleListCols.settingsOpen.value = false">确定</button>
                </footer>
              </div>
            </div>
          </template>
          <template #meta-extra>
            <span class="md-chip primary">共 {{ rolePagination.total }} 条</span>
          </template>
        <div class="ulc-table-wrap">
          <el-table
            ref="roleListTableRef"
            v-loading="loading"
            :data="roles"
            row-key="roleId"
            border
            stripe
            class="ulc-table is-sticky"
            max-height="560"
            style="width: 100%"
            :empty-text="'暂无角色数据'"
            @header-dragend="(width: number, oldWidth: number, column: any) => roleListCols.handleHeaderDragEnd(width, oldWidth, column)"
          >
            <el-table-column
              column-key="selection"
              v-if="isRoleListColVisible('selection')"
              label=""
              v-bind="roleListColBinding('selection')"
            >
              <template #header>
                <span class="ulc-th-text"></span>
              </template>
              <template #default="{ row }">
                <input type="checkbox" :checked="selectedRoleIds.includes(row.roleId)" @change="onRoleCheckbox(row.roleId, $event)" />
              </template>
            </el-table-column>
            <el-table-column
              column-key="roleName"
              v-if="isRoleListColVisible('roleName')"
              label="角色名称"
              v-bind="roleListColBinding('roleName')"
            >
              <template #header>
                <span class="ulc-th-text">角色名称</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-strong ulc-wrap-text">{{ row.roleName }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="roleCode"
              v-if="isRoleListColVisible('roleCode')"
              label="角色编码"
              v-bind="roleListColBinding('roleCode')"
            >
              <template #header>
                <span class="ulc-th-text">角色编码</span>
              </template>
              <template #default="{ row }">
                <span class="sys-tag is-code ulc-wrap-text">{{ row.roleCode }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="dataScope"
              v-if="isRoleListColVisible('dataScope')"
              label="数据权限"
              v-bind="roleListColBinding('dataScope')"
            >
              <template #header>
                <span class="ulc-th-text">数据权限</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ dataScopeName(row.dataScope) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="userCount"
              v-if="isRoleListColVisible('userCount')"
              label="用户数"
              v-bind="roleListColBinding('userCount')"
            >
              <template #header>
                <span class="ulc-th-text">用户数</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">{{ row.userCount }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="permissionCount"
              v-if="isRoleListColVisible('permissionCount')"
              label="权限点"
              v-bind="roleListColBinding('permissionCount')"
            >
              <template #header>
                <span class="ulc-th-text">权限点</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text ulc-number">{{ row.permissionCount }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="status"
              v-if="isRoleListColVisible('status')"
              label="状态"
              v-bind="roleListColBinding('status')"
            >
              <template #header>
                <span class="ulc-th-text">状态</span>
              </template>
              <template #default="{ row }">
                <span :class="['sys-badge', row.status === 1 ? 'is-active' : 'is-stopped']">{{ row.status === 1 ? '启用' : '停用' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="description"
              v-if="isRoleListColVisible('description')"
              label="说明"
              v-bind="roleListColBinding('description')"
            >
              <template #header>
                <span class="ulc-th-text">说明</span>
              </template>
              <template #default="{ row }">
                <span class="ulc-wrap-text">{{ row.description || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="createTime"
              v-if="isRoleListColVisible('createTime')"
              label="创建时间"
              v-bind="roleListColBinding('createTime')"
            >
              <template #header>
                <span class="ulc-th-text">创建时间</span>
              </template>
              <template #default="{ row }">
                <span class="md-audit-time">
                  {{ row.createTime || '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              column-key="updateTime"
              v-if="isRoleListColVisible('updateTime')"
              label="修改时间"
              v-bind="roleListColBinding('updateTime')"
            >
              <template #header>
                <span class="ulc-th-text">修改时间</span>
              </template>
              <template #default="{ row }">
                <span class="md-audit-time">
                  {{ row.updateTime || '-' }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <template #pagination>
          <MdPagination
            :page="rolePagination.page"
            :total-pages="Math.max(1, Math.ceil(rolePagination.total / rolePagination.size))"
            :total="rolePagination.total"
            :size="rolePagination.size"
            :loading="loading"
            @update:page="changeRolePage"
            @update:size="changeRolePageSize"
          />
        </template>
      </MdDataTableCard>
    </div>

    <!-- 弹窗 -->
    <Teleport to="body">
    <div v-if="modal" class="sys-dialog-mask" @click.self="modal = null">
      <div class="sys-dialog">
        <div class="sys-dialog-header">
          <h3>
            {{
              modal === 'user' ? (editingUserId ? '编辑用户' : '新增用户')
              : modal === 'role' ? (editingRoleId ? '编辑角色' : '新增角色')
              : modal === 'user-role' ? `为 ${selectedUser?.realName} 分配角色`
              : modal === 'role-permission' ? `为 ${selectedRole?.roleName} 分配菜单与按钮权限`
              : '数据权限'
            }}
          </h3>
          <button class="btn-icon" type="button" @click="modal = null"><X :size="16" /></button>
        </div>
        <div class="sys-dialog-body">

          <!-- 用户表单 -->
          <div v-if="modal === 'user'" class="sys-form-grid">
            <label><span>用户名</span><input v-model="userForm.username" class="sys-input" /></label>
            <label v-if="!editingUserId"><span>初始密码</span><input v-model="userForm.password" type="password" class="sys-input" /></label>
            <label><span>真实姓名</span><input v-model="userForm.realName" class="sys-input" /></label>
            <label><span>手机号</span><input v-model="userForm.phone" class="sys-input" /></label>
            <label><span>邮箱</span><input v-model="userForm.email" class="sys-input" /></label>
            <label><span>性别</span><select v-model.number="userForm.gender" class="sys-select"><option :value="0">未知</option><option :value="1">男</option><option :value="2">女</option></select></label>
            <label><span>所属科室</span><select v-model="userForm.deptId" class="sys-select"><option :value="0">未选择</option><option v-for="d in departments" :key="d.deptId" :value="d.deptId">{{ d.deptName }}</option></select></label>
            <label><span>状态</span><select v-model.number="userForm.status" class="sys-select"><option :value="1">启用</option><option :value="0">停用</option></select></label>
            <label class="wide"><span>角色分配</span></label>
            <div class="sys-perm-checkboxes">
              <label v-for="r in roles" :key="r.roleId"><input v-model="userForm.roleIds" type="checkbox" :value="r.roleId" /> {{ r.roleName }}</label>
            </div>
            <p v-if="userFormError" class="sys-error wide">{{ userFormError }}</p>
          </div>

          <!-- 角色表单 -->
          <div v-else-if="modal === 'role'" class="sys-form-grid">
            <label><span>角色名称</span><input v-model="roleForm.roleName" class="sys-input" /></label>
            <label><span>角色编码</span><input v-model="roleForm.roleCode" class="sys-input" :disabled="Boolean(editingRoleId)" /></label>
            <label><span>数据权限</span><select v-model.number="roleForm.dataScope" class="sys-select"><option :value="1">全部数据</option><option :value="2">本部门数据</option><option :value="3">本部门及子部门</option><option :value="4">仅本人数据</option><option :value="5">自定义部门</option></select></label>
            <label><span>状态</span><select v-model.number="roleForm.status" class="sys-select"><option :value="1">启用</option><option :value="0">停用</option></select></label>
            <label><span>排序</span><input v-model.number="roleForm.sortOrder" type="number" class="sys-input" /></label>
            <label class="wide"><span>角色说明</span><textarea v-model="roleForm.description" class="sys-textarea" rows="2" /></label>
          </div>

          <!-- 分配角色 -->
          <div v-else-if="modal === 'user-role'" class="sys-perm-checkboxes wide">
            <label v-for="r in roles" :key="r.roleId"><input v-model="userForm.roleIds" type="checkbox" :value="r.roleId" /> {{ r.roleName }}</label>
          </div>

          <!-- 分配权限 -->
          <div v-else-if="modal === 'role-permission'" class="sys-perm-checkboxes wide">
            <label v-for="p in permissions" :key="p.permissionId"><input v-model="permissionForm.permissionIds" type="checkbox" :value="p.permissionId" /> {{ p.permissionType === 1 ? '菜单' : '按钮' }} · {{ p.permissionName }}</label>
          </div>

          <!-- 数据权限 -->
          <div v-else class="sys-form-grid">
            <label><span>数据权限范围</span><select v-model.number="permissionForm.dataScope" class="sys-select"><option :value="1">全部数据</option><option :value="2">本部门数据</option><option :value="3">本部门及子部门</option><option :value="4">仅本人数据</option><option :value="5">自定义部门</option></select></label>
            <div v-if="permissionForm.dataScope === 5" class="sys-perm-checkboxes wide">
              <label v-for="d in departments" :key="d.deptId"><input v-model="permissionForm.deptIds" type="checkbox" :value="d.deptId" /> {{ d.deptName }}</label>
            </div>
          </div>

        </div>
        <div class="sys-dialog-footer">
          <button class="btn" type="button" @click="modal = null">取消</button>
          <button v-if="modal === 'user'" class="btn btn-primary" type="button" @click="saveUser"><Save :size="15" /> 保存</button>
          <button v-else-if="modal === 'role'" class="btn btn-primary" type="button" @click="saveRole"><Save :size="15" /> 保存</button>
          <button v-else-if="modal === 'user-role'" class="btn btn-primary" type="button" @click="saveUserRoles"><Save :size="15" /> 保存</button>
          <button v-else-if="modal === 'role-permission'" class="btn btn-primary" type="button" @click="saveRolePermissions"><Save :size="15" /> 保存</button>
          <button v-else class="btn btn-primary" type="button" @click="saveDataPermission"><Save :size="15" /> 保存</button>
        </div>
      </div>
    </div>
    </Teleport>

  </div>
</template>

<style scoped>
/* =====================================================================
   系统管理 · 用户管理 / 角色权限
   ---------------------------------------------------------------------
   设计目标：
   1. 顶部 4 张主题色 KPI 卡（用户/角色分主题色系）。
   2. Tab：文字 underline 激活态。
   3. 工具栏：搜索框 + 动作按钮组，同色系按钮。
   4. 表格：蓝绿主题色表头，交替行，hover。
   5. 弹窗：居中浅色卡片 + 统一表单控件。
   6. 内容区 padding：16px 20px 24px。
   ===================================================================== */
.sys-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  min-width: 0;
  padding: 16px 20px 24px;
  box-sizing: border-box;
}

/* ---------- 顶部 4 项 KPI 摘要（不同主题色卡） ---------- */
.sys-kpi-card {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.sys-kpi-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border: 1px solid;
  border-radius: var(--spd-radius-md, 10px);
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.sys-kpi-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.08);
}

/* ① 蓝色：总用户数 / 总角色数 */
.sys-kpi-item.is-blue {
  background: linear-gradient(180deg, #dbeafe 0%, #bfdbfe 100%);
  border-color: #93c5fd;
}

/* ② 绿色：启用用户 / 启用角色 */
.sys-kpi-item.is-green {
  background: linear-gradient(180deg, #d1fae5 0%, #a7f3d0 100%);
  border-color: #6ee7b7;
}

/* ③ 红色：停用用户 */
.sys-kpi-item.is-red {
  background: linear-gradient(180deg, #fee2e2 0%, #fecaca 100%);
  border-color: #fca5a5;
}

/* ④ 紫色：已分配角色 / 关联用户 */
.sys-kpi-item.is-purple {
  background: linear-gradient(180deg, #ede9fe 0%, #ddd6fe 100%);
  border-color: #c4b5fd;
}

/* 橙色：权限点 */
.sys-kpi-item.is-orange {
  background: linear-gradient(180deg, #fef3c7 0%, #fde68a 100%);
  border-color: #fcd34d;
}

.sys-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.65);
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.9);
}

.sys-kpi-item.is-blue   .sys-kpi-icon { color: #1d4ed8; }
.sys-kpi-item.is-green  .sys-kpi-icon { color: #047857; }
.sys-kpi-item.is-red    .sys-kpi-icon { color: #b91c1c; }
.sys-kpi-item.is-purple .sys-kpi-icon { color: #6d28d9; }
.sys-kpi-item.is-orange .sys-kpi-icon { color: #b45309; }

.sys-kpi-item > div {
  display: grid;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.sys-kpi-label {
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
  opacity: 0.85;
}

.sys-kpi-value {
  font-size: var(--spd-font-size-2xl);
  font-weight: 700;
  line-height: var(--spd-lh-tight);
  font-variant-numeric: tabular-nums;
}

.sys-kpi-hint {
  font-size: var(--spd-font-size-xs);
  font-weight: 400;
  line-height: var(--spd-lh-tight);
  opacity: 0.75;
}

/* 文字颜色 */
.sys-kpi-item.is-blue   .sys-kpi-label,
.sys-kpi-item.is-blue   .sys-kpi-value,
.sys-kpi-item.is-blue   .sys-kpi-hint { color: #1e3a8a; }
.sys-kpi-item.is-green  .sys-kpi-label,
.sys-kpi-item.is-green  .sys-kpi-value,
.sys-kpi-item.is-green  .sys-kpi-hint { color: #065f46; }
.sys-kpi-item.is-red    .sys-kpi-label,
.sys-kpi-item.is-red    .sys-kpi-value,
.sys-kpi-item.is-red    .sys-kpi-hint { color: #7f1d1d; }
.sys-kpi-item.is-purple .sys-kpi-label,
.sys-kpi-item.is-purple .sys-kpi-value,
.sys-kpi-item.is-purple .sys-kpi-hint { color: #4c1d95; }
.sys-kpi-item.is-orange .sys-kpi-label,
.sys-kpi-item.is-orange .sys-kpi-value,
.sys-kpi-item.is-orange .sys-kpi-hint { color: #78350f; }

/* ---------- Tab ---------- */
.sys-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 4px;
}

.sys-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--spd-text-secondary);
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
  border-radius: 6px 6px 0 0;
}

.sys-tab:hover {
  color: var(--spd-primary);
  background: #f0fdf9;
}

.sys-tab.active {
  color: var(--spd-primary);
  border-bottom-color: var(--spd-primary);
  background: #f0fdf9;
  font-weight: 600;
}

/* ---------- 工具栏 ---------- */
.sys-toolbar-panel {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: var(--spd-radius-md, 10px);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  flex-wrap: wrap;
}

.sys-toolbar-search {
  display: flex;
  align-items: center;
  gap: 0;
  border: 1px solid #cbd5e1;
  border-radius: 7px;
  background: #fff;
  overflow: hidden;
  flex: 0 0 280px;
  min-width: 0;
}

.sys-toolbar-search-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: #64748b;
  background: #f8fafc;
  border-right: 1px solid #e2e8f0;
  flex-shrink: 0;
}

.sys-toolbar-search input {
  flex: 1;
  min-width: 0;
  height: 34px;
  border: none;
  background: transparent;
  color: #102033;
  padding: 0 10px;
  font-size: var(--spd-font-size-base);
  font-weight: 500;
}

.sys-toolbar-search input::placeholder { color: #94a3b8; }
.sys-toolbar-search input:focus { outline: none; }

.sys-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.sys-toolbar-sep {
  width: 1px;
  height: 22px;
  background: #e2e8f0;
  margin: 0 4px;
}

/* ---------- 按钮 ---------- */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 32px;
  height: 32px;
  padding: 0 14px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  color: #334155;
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.btn:hover:not(:disabled) {
  background: #f8fafc;
  border-color: #94a3b8;
  color: #1e293b;
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-primary {
  background: linear-gradient(180deg, #10b981 0%, #059669 100%);
  border-color: #047857;
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(180deg, #059669 0%, #047857 100%);
  border-color: #065f46;
  color: #fff;
}

.btn-danger {
  color: #b91c1c;
  border-color: #fca5a5;
  background: #fff;
}

.btn-danger:hover:not(:disabled) {
  background: #fee2e2;
  border-color: #f87171;
  color: #991b1b;
}

.btn-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.btn-icon:hover {
  background: #f1f5f9;
  color: #334155;
}

.btn-text {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: transparent;
  color: #10b981;
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  transition: background 0.15s;
}

.btn-text:hover { background: #d1fae5; }

/* ---------- 消息条 ---------- */
.sys-msg {
  display: flex;
  align-items: center;
  padding: 8px 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  color: #334155;
  font-size: var(--spd-font-size-sm);
  box-shadow: 0 1px 2px rgba(0,0,0,0.04);
}

/* ---------- 内容区 ---------- */
.sys-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ---------- 表格卡片 ---------- */
.sys-table-card {
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: var(--spd-radius-md, 10px);
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.sys-table-scroll { overflow-x: auto; }

.sys-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--spd-font-size-base);
}

.sys-table thead tr {
  background: linear-gradient(180deg, #ecfdf5 0%, #d1fae5 100%);
  border-bottom: 1px solid #6ee7b7;
}

.sys-table th {
  color: #065f46;
  font-weight: 700;
  font-size: var(--spd-font-size-table-head);
  text-align: left;
  padding: 10px 12px;
  white-space: nowrap;
  letter-spacing: 0.3px;
}

.sys-table td {
  padding: 9px 12px;
  border-bottom: 1px solid #f1f5f9;
  color: #334155;
  vertical-align: middle;
}

.sys-table tbody tr:nth-child(even) td { background: #fafdfb; }
.sys-table tbody tr:hover td { background: #ecfdf5 !important; }
.sys-table tbody tr:last-child td { border-bottom: none; }

.text-center { text-align: center; }
.muted { color: #94a3b8; padding: 32px; font-size: var(--spd-font-size-base); }

/* 角色 / 编码 tag */
.sys-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  /* 文字居中于色块内；宽度不足时换行而不是横向溢出侵占相邻列 */
  text-align: center;
  max-width: 100%;
  min-width: 0;
  white-space: normal;
  overflow-wrap: anywhere;
}

.sys-tag.is-role {
  color: #6d28d9;
  background: #ede9fe;
  border-color: #c4b5fd;
}

.sys-tag.is-code {
  color: #334155;
  background: #f1f5f9;
  border-color: #e2e8f0;
  font-family: var(--spd-font-family-mono);
  font-size: var(--spd-font-size-sm);
  /* 角色编码是长英文标识，需要在断点处折行 */
  word-break: break-word;
}

/* 状态 badge */
.sys-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: var(--spd-font-size-base);
  font-weight: 600;
}

.sys-badge.is-active {
  color: #065f46;
  background: #d1fae5;
  border: 1px solid #6ee7b7;
}

.sys-badge.is-stopped {
  color: #991b1b;
  background: #fee2e2;
  border: 1px solid #fca5a5;
}

/* 分页 */
.sys-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-top: 1px solid #f1f5f9;
  font-size: var(--spd-font-size-sm);
  color: #64748b;
}

.sys-pagination strong { color: #102033; font-weight: 700; }

/* ---------- 弹窗 ---------- */
.sys-dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.5);
  backdrop-filter: blur(2px);
}

.sys-dialog {
  background: #fff;
  border-radius: var(--spd-radius-lg, 14px);
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.2);
  width: 100%;
  max-width: 520px;
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: dialog-in 0.2s ease;
}

@keyframes dialog-in {
  from { opacity: 0; transform: scale(0.95) translateY(-8px); }
  to   { opacity: 1; transform: scale(1) translateY(0); }
}

.sys-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #e2e8f0;
}

.sys-dialog-header h3 {
  margin: 0;
  font-size: var(--spd-font-size-lg);
  font-weight: 700;
  color: var(--spd-text-heading);
}

.sys-dialog-body {
  padding: 18px 20px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.sys-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 14px 20px;
  border-top: 1px solid #e2e8f0;
  background: #fafdfb;
}

/* ---------- 表单 ---------- */
.sys-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
}

.sys-form-grid label {
  display: grid;
  gap: 5px;
  font-size: var(--spd-font-size-sm);
  font-weight: 600;
  color: #4b5b66;
}

.sys-form-grid label.wide { grid-column: 1 / -1; }

.sys-input,
.sys-select {
  width: 100%;
  min-height: 34px;
  height: 34px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  color: #102033;
  padding: 0 10px;
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  transition: border-color 0.15s;
}

.sys-input:focus,
.sys-select:focus {
  outline: none;
  border-color: #cbd5e1;
}

.sys-input:disabled {
  background: #f1f5f9;
  color: #94a3b8;
  cursor: not-allowed;
}

.sys-textarea {
  width: 100%;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #fff;
  color: #102033;
  padding: 8px 10px;
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  resize: vertical;
  min-height: 60px;
}

.sys-textarea:focus { outline: none; border-color: #cbd5e1; }

.sys-error {
  color: #b91c1c;
  font-size: var(--spd-font-size-sm);
  font-weight: 500;
  margin: 0;
}

/* 权限 checkbox 列表 */
.sys-perm-checkboxes {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 220px;
  overflow-y: auto;
  padding: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.sys-perm-checkboxes.wide { grid-column: 1 / -1; }

.sys-perm-checkboxes label {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 5px 8px;
  border-radius: 5px;
  font-size: var(--spd-font-size-base);
  color: #334155;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.12s;
}

.sys-perm-checkboxes label:hover { background: #d1fae5; color: #047857; }
.sys-perm-checkboxes label input[type="checkbox"] { accent-color: #10b981; width: 15px; height: 15px; }

/* ---------- 响应式 ---------- */
@media (max-width: 1200px) {
  .sys-kpi-card { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .sys-toolbar-panel { gap: 10px; }
}
@media (max-width: 900px) {
  .sys-toolbar-search { flex: 1; min-width: 0; }
  .sys-form-grid { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .sys-kpi-card { grid-template-columns: 1fr; }
  .sys-toolbar-actions { gap: 6px; }
  .sys-page { padding: 12px; }
}
</style>
