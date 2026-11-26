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
        <SpdButton label="查询" tone="primary" @click="loadAll">
          <template #icon><Search :size="15" /></template>
        </SpdButton>
        <SpdButton label="重置" tone="neutral" @click="loadAll">
          <template #icon><RefreshCw :size="15" /></template>
        </SpdButton>
        <i class="sys-toolbar-sep"></i>
        <template v-if="pageMode === 'user'">
          <SpdButton label="新增用户" tone="primary" v-if="authStore.hasPermission('user:create')" @click="openUserCreate">
          <template #icon><Plus :size="15" /></template>
        </SpdButton>
          <SpdButton label="编辑" tone="primary" v-if="authStore.hasPermission('user:update')" @click="openUserEdit()">
          <template #icon><Pencil :size="15" /></template>
        </SpdButton>
          <SpdButton label="删除" tone="danger" v-if="authStore.hasPermission('user:delete')" @click="removeUsers">
          <template #icon><Trash2 :size="15" /></template>
        </SpdButton>
          <SpdButton label="重置密码" tone="warning" v-if="authStore.hasPermission('user:reset-password')" @click="resetPasswords">
          <template #icon><KeyRound :size="15" /></template>
        </SpdButton>
          <SpdButton label="分配角色" tone="primary" v-if="authStore.hasPermission('user:assign-role')" @click="openUserRoleAssign">
          <template #icon><UserCheck :size="15" /></template>
        </SpdButton>
        </template>
        <template v-else>
          <SpdButton label="新增角色" tone="primary" v-if="authStore.hasPermission('role:create')" @click="openRoleCreate">
          <template #icon><Plus :size="15" /></template>
        </SpdButton>
          <SpdButton label="编辑" tone="primary" v-if="authStore.hasPermission('role:update')" @click="openRoleEdit()">
          <template #icon><Pencil :size="15" /></template>
        </SpdButton>
          <SpdButton label="删除" tone="danger" v-if="authStore.hasPermission('role:delete')" @click="removeRoles">
          <template #icon><Trash2 :size="15" /></template>
        </SpdButton>
          <SpdButton label="分配权限" tone="primary" v-if="authStore.hasPermission('role:assign-permission')" @click="openPermissionAssign">
          <template #icon><ShieldCheck :size="15" /></template>
        </SpdButton>
          <SpdButton label="数据权限" tone="primary" v-if="authStore.hasPermission('role:data-permission')" @click="openDataPermission">
          <template #icon><Database :size="15" /></template>
        </SpdButton>
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
              <SpdButton
                ref="userListColSettingsAnchorRef"
                label="列设置"
                @click="userListCols.settingsOpen.value = !userListCols.settingsOpen.value">
                <template #icon><Columns3 :size="16" /></template>
              </SpdButton>
              <SpdColumnSettings
v-if="userListCols.settingsOpen.value"
ref="userListColSettingsPopRef"
:class="{ 'is-align-right': userListColSettingsPop.align.value === 'right', 'is-align-up': userListColSettingsPop.vertical.value === 'up' }"
:style="userListColSettingsPop.popStyle.value"
:columns="userListCols.columns.value"
:visible-column-count="userListCols.visibleColumnCount.value"
@toggle="userListCols.setColumnVisible"
@move="userListCols.moveColumn"
@reset="userListCols.resetColumns()"
@confirm="userListCols.settingsOpen.value = false"
          />
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
              <SpdButton
                ref="roleListColSettingsAnchorRef"
                label="列设置"
                @click="roleListCols.settingsOpen.value = !roleListCols.settingsOpen.value">
                <template #icon><Columns3 :size="16" /></template>
              </SpdButton>
              <SpdColumnSettings
v-if="roleListCols.settingsOpen.value"
ref="roleListColSettingsPopRef"
:class="{ 'is-align-right': roleListColSettingsPop.align.value === 'right', 'is-align-up': roleListColSettingsPop.vertical.value === 'up' }"
:style="roleListColSettingsPop.popStyle.value"
:columns="roleListCols.columns.value"
:visible-column-count="roleListCols.visibleColumnCount.value"
@toggle="roleListCols.setColumnVisible"
@move="roleListCols.moveColumn"
@reset="roleListCols.resetColumns()"
@confirm="roleListCols.settingsOpen.value = false"
          />
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
          <SpdButton icon-only variant="ghost" tone="neutral" type="button" @click="modal = null">
          <template #icon><X :size="16" /></template>
        </SpdButton>
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
          <SpdButton label="取消" tone="neutral" @click="modal = null"></SpdButton>
          <SpdButton label="保存" tone="success" v-if="modal === 'user'" @click="saveUser">
          <template #icon><Save :size="15" /></template>
        </SpdButton>
          <SpdButton label="保存" tone="success" v-else-if="modal === 'role'" @click="saveRole">
          <template #icon><Save :size="15" /></template>
        </SpdButton>
          <SpdButton label="保存" tone="success" v-else-if="modal === 'user-role'" @click="saveUserRoles">
          <template #icon><Save :size="15" /></template>
        </SpdButton>
          <SpdButton label="保存" tone="success" v-else-if="modal === 'role-permission'" @click="saveRolePermissions">
          <template #icon><Save :size="15" /></template>
        </SpdButton>
          <SpdButton label="保存" tone="success" v-else @click="saveDataPermission">
          <template #icon><Save :size="15" /></template>
        </SpdButton>
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
  background: var(--spd-p-bg);
  box-shadow: var(--spd-p-shadow-1);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.sys-kpi-item:hover {
  transform: translateY(-1px);
  box-shadow: var(--spd-p-shadow-2);
}

/* ① 蓝色：总用户数 / 总角色数 */
.sys-kpi-item.is-blue {
  background: linear-gradient(180deg, var(--spd-p-border) 0%, var(--spd-p-info-border) 100%);
  border-color: var(--spd-p-info-border);
}

/* ② 绿色：启用用户 / 启用角色 */
.sys-kpi-item.is-green {
  background: linear-gradient(180deg, var(--spd-p-success-bg) 0%, var(--spd-p-success-border) 100%);
  border-color: var(--spd-p-success);
}

/* ③ 红色：停用用户 */
.sys-kpi-item.is-red {
  background: linear-gradient(180deg, var(--spd-p-danger-bg) 0%, var(--spd-p-danger-border) 100%);
  border-color: var(--spd-p-danger-border);
}

/* ④ 紫色：已分配角色 / 关联用户 */
.sys-kpi-item.is-purple {
  background: linear-gradient(180deg, var(--spd-p-info-bg) 0%, var(--spd-p-info-bg) 100%);
  border-color: var(--spd-p-info-border);
}

/* 橙色：权限点 */
.sys-kpi-item.is-orange {
  background: linear-gradient(180deg, var(--spd-p-warning-bg) 0%, var(--spd-p-warning-border) 100%);
  border-color: var(--spd-p-warning-border);
}

.sys-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--spd-p-bg) 65%, transparent);
  flex-shrink: 0;
  border: 1px solid color-mix(in srgb, var(--spd-p-bg) 90%, transparent);
}

.sys-kpi-item.is-blue   .sys-kpi-icon { color: var(--spd-p-info-strong); }
.sys-kpi-item.is-green  .sys-kpi-icon { color: var(--spd-p-success-strong); }
.sys-kpi-item.is-red    .sys-kpi-icon { color: var(--spd-p-danger-strong); }
.sys-kpi-item.is-purple .sys-kpi-icon { color: var(--spd-p-primary); }
.sys-kpi-item.is-orange .sys-kpi-icon { color: var(--spd-p-warning-text); }

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
.sys-kpi-item.is-blue   .sys-kpi-hint { color: var(--spd-p-info-strong); }
.sys-kpi-item.is-green  .sys-kpi-label,
.sys-kpi-item.is-green  .sys-kpi-value,
.sys-kpi-item.is-green  .sys-kpi-hint { color: var(--spd-p-primary-strong); }
.sys-kpi-item.is-red    .sys-kpi-label,
.sys-kpi-item.is-red    .sys-kpi-value,
.sys-kpi-item.is-red    .sys-kpi-hint { color: var(--spd-p-danger-strong); }
.sys-kpi-item.is-purple .sys-kpi-label,
.sys-kpi-item.is-purple .sys-kpi-value,
.sys-kpi-item.is-purple .sys-kpi-hint { color: var(--spd-p-primary-strong); }
.sys-kpi-item.is-orange .sys-kpi-label,
.sys-kpi-item.is-orange .sys-kpi-value,
.sys-kpi-item.is-orange .sys-kpi-hint { color: var(--spd-p-warning-strong); }

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
  background: var(--spd-p-primary-bg);
}

.sys-tab.active {
  color: var(--spd-primary);
  border-bottom-color: var(--spd-primary);
  background: var(--spd-p-primary-bg);
  font-weight: 600;
}

/* ---------- 工具栏 ---------- */
.sys-toolbar-panel {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--spd-p-bg);
  border: 1px solid var(--spd-p-border);
  border-radius: var(--spd-radius-md, 10px);
  box-shadow: var(--spd-p-shadow-1);
  flex-wrap: wrap;
}

.sys-toolbar-search {
  display: flex;
  align-items: center;
  gap: 0;
  border: 1px solid var(--spd-p-border-strong);
  border-radius: 7px;
  background: var(--spd-p-bg);
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
  color: var(--spd-p-text-secondary);
  background: var(--spd-p-bg-muted);
  border-right: 1px solid var(--spd-p-border);
  flex-shrink: 0;
}

.sys-toolbar-search input {
  flex: 1;
  min-width: 0;
  height: 34px;
  border: none;
  background: transparent;
  color: var(--spd-p-text);
  padding: 0 10px;
  font-size: var(--spd-font-size-base);
  font-weight: 500;
}

.sys-toolbar-search input::placeholder { color: var(--spd-p-text-muted); }
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
  background: var(--spd-p-border);
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
  border: 1px solid var(--spd-p-border-strong);
  border-radius: 6px;
  background: var(--spd-p-bg);
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.btn:hover:not(:disabled) {
  background: var(--spd-p-bg-muted);
  border-color: var(--spd-p-text-muted);
  /* 悬停文字：深色（此前误用与背景同色，悬停时文字完全不可见） */
  color: var(--spd-p-text-strong);
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-primary {
  background: linear-gradient(180deg, var(--spd-p-success) 0%, var(--spd-p-success) 100%);
  border-color: var(--spd-p-success-strong);
  color: var(--spd-p-bg);
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(180deg, var(--spd-p-success) 0%, var(--spd-p-success-strong) 100%);
  border-color: var(--spd-p-primary-strong);
  color: var(--spd-p-bg);
}

.btn-danger {
  color: var(--spd-p-danger-strong);
  border-color: var(--spd-p-danger-border);
  background: var(--spd-p-bg);
}

.btn-danger:hover:not(:disabled) {
  background: var(--spd-p-danger-bg);
  border-color: var(--spd-p-danger);
  color: var(--spd-p-danger-strong);
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
  color: var(--spd-p-text-secondary);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.btn-icon:hover {
  /* 浅底深字（此前误用近黑底配深灰字，几乎不可见） */
  background: var(--spd-p-bg-muted);
  color: var(--spd-p-text-strong);
}

.btn-text {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: transparent;
  color: var(--spd-p-success);
  font-size: var(--spd-font-size-base);
  font-weight: 600;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  transition: background 0.15s;
}

.btn-text:hover { background: var(--spd-p-success-bg); }

/* ---------- 消息条 ---------- */
.sys-msg {
  display: flex;
  align-items: center;
  padding: 8px 14px;
  background: var(--spd-p-bg);
  border: 1px solid var(--spd-p-border);
  border-radius: 8px;
  color: var(--spd-p-text-secondary);
  font-size: var(--spd-font-size-sm);
  box-shadow: var(--spd-p-shadow-1);
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
  background: var(--spd-p-bg);
  border: 1px solid var(--spd-p-border);
  border-radius: var(--spd-radius-md, 10px);
  overflow: hidden;
  box-shadow: var(--spd-p-shadow-1);
}

.sys-table-scroll { overflow-x: auto; }

.sys-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--spd-font-size-base);
}

.sys-table thead tr {
  background: linear-gradient(180deg, var(--spd-p-primary-bg) 0%, var(--spd-p-success-bg) 100%);
  border-bottom: 1px solid var(--spd-p-success);
}

.sys-table th {
  color: var(--spd-p-primary-strong);
  font-weight: 700;
  font-size: var(--spd-font-size-table-head);
  text-align: left;
  padding: 10px 12px;
  white-space: nowrap;
  letter-spacing: 0.3px;
}

.sys-table td {
  padding: 9px 12px;
  border-bottom: 1px solid var(--spd-p-text-strong);
  color: var(--spd-p-text-secondary);
  vertical-align: middle;
}

.sys-table tbody tr:nth-child(even) td { background: var(--spd-p-bg); }
.sys-table tbody tr:hover td { background: var(--spd-p-primary-bg) !important; }
.sys-table tbody tr:last-child td { border-bottom: none; }

.text-center { text-align: center; }
.muted { color: var(--spd-p-text-muted); padding: 32px; font-size: var(--spd-font-size-base); }

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
  color: var(--spd-p-primary);
  background: var(--spd-p-info-bg);
  border-color: var(--spd-p-info-border);
}

.sys-tag.is-code {
  color: var(--spd-p-text-secondary);
  /* 浅底深字（此前误用近黑底配深灰字，几乎不可见） */
  background: var(--spd-p-bg-muted);
  border-color: var(--spd-p-border);
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
  color: var(--spd-p-primary-strong);
  background: var(--spd-p-success-bg);
  border: 1px solid var(--spd-p-success);
}

.sys-badge.is-stopped {
  color: var(--spd-p-danger-strong);
  background: var(--spd-p-danger-bg);
  border: 1px solid var(--spd-p-danger-border);
}

/* 分页 */
.sys-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-top: 1px solid var(--spd-p-text-strong);
  font-size: var(--spd-font-size-sm);
  color: var(--spd-p-text-secondary);
}

.sys-pagination strong { color: var(--spd-p-text); font-weight: 700; }

/* ---------- 弹窗 ---------- */
.sys-dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--spd-p-text-strong) 50%, transparent);
  backdrop-filter: blur(2px);
}

.sys-dialog {
  background: var(--spd-p-bg);
  border-radius: var(--spd-radius-lg, 14px);
  box-shadow: var(--spd-p-shadow-dialog);
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
  border-bottom: 1px solid var(--spd-p-border);
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
  border-top: 1px solid var(--spd-p-border);
  background: var(--spd-p-bg);
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
  color: var(--spd-p-text-secondary);
}

.sys-form-grid label.wide { grid-column: 1 / -1; }

.sys-input,
.sys-select {
  width: 100%;
  min-height: 34px;
  height: 34px;
  border: 1px solid var(--spd-p-border-strong);
  border-radius: 6px;
  background: var(--spd-p-bg);
  color: var(--spd-p-text);
  padding: 0 10px;
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  transition: border-color 0.15s;
}

.sys-input:focus,
.sys-select:focus {
  outline: none;
  border-color: var(--spd-p-border-strong);
}

.sys-input:disabled {
  /* 禁用态：浅底 + 弱化文字（此前误用近黑底，几乎不可见） */
  background: var(--spd-p-bg-muted);
  color: var(--spd-p-text-muted);
  cursor: not-allowed;
}

.sys-textarea {
  width: 100%;
  border: 1px solid var(--spd-p-border-strong);
  border-radius: 6px;
  background: var(--spd-p-bg);
  color: var(--spd-p-text);
  padding: 8px 10px;
  font-size: var(--spd-font-size-base);
  font-weight: 500;
  resize: vertical;
  min-height: 60px;
}

.sys-textarea:focus { outline: none; border-color: var(--spd-p-border-strong); }

.sys-error {
  color: var(--spd-p-danger-strong);
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
  background: var(--spd-p-bg-muted);
  border: 1px solid var(--spd-p-border);
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
  color: var(--spd-p-text-secondary);
  font-weight: 500;
  cursor: pointer;
  transition: background 0.12s;
}

.sys-perm-checkboxes label:hover { background: var(--spd-p-success-bg); color: var(--spd-p-success-strong); }
.sys-perm-checkboxes label input[type="checkbox"] { accent-color: var(--spd-p-success); width: 15px; height: 15px; }

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
