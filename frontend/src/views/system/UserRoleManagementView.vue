<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
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
  ShieldCheck,
  Trash2,
  UserCheck,
  X
} from '@lucide/vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
import MdPageHeader from '../../components/master-data/MdPageHeader.vue'
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

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const pageMode = computed(() => (String(route.params.code) === 'role-permission' ? 'role' : 'user'))
const users = ref<UserRow[]>([])
const roles = ref<RoleRow[]>([])
const permissions = ref<PermissionRow[]>([])
const departments = ref<DepartmentOption[]>([])
const selectedUserIds = ref<number[]>([])
const selectedRoleIds = ref<number[]>([])
const loading = ref(false)
const message = ref('')
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
const subtitle = computed(() =>
  pageMode.value === 'role'
    ? '维护角色、菜单按钮权限与数据权限范围。'
    : '维护系统账号、所属科室、角色分配与密码重置。'
)
const userStats = computed(() => [
  { label: '启用用户', value: users.value.filter(item => item.status === 1).length },
  { label: '停用用户', value: users.value.filter(item => item.status === 0).length },
  { label: '已分配角色', value: users.value.filter(item => item.roleNames).length }
])
const roleStats = computed(() => [
  { label: '启用角色', value: roles.value.filter(item => item.status === 1).length },
  { label: '权限点', value: permissions.value.length },
  { label: '已关联用户', value: roles.value.reduce((sum, item) => sum + Number(item.userCount || 0), 0) }
])

function roleIdsFrom(row?: UserRow) {
  return row?.roleIds ? row.roleIds.split(',').map(Number).filter(Boolean) : []
}
function permissionIdsFrom(row?: RoleRow) {
  return row?.permissionIds ? row.permissionIds.split(',').map(Number).filter(Boolean) : []
}

async function loadAll() {
  loading.value = true
  message.value = ''
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
  } catch (err: any) { message.value = err?.message || '数据加载失败' }
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
  else if (!selectedUserIds.value.length) { message.value = '请先勾选要编辑的用户'; return }
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
  if (!selectedRoleIds.value.length) { message.value = '请先勾选要编辑的角色'; return }
  const r = selectedRole.value
  if (!r) return
  editingRoleId.value = r.roleId
  Object.assign(roleForm, { roleName: r.roleName, roleCode: r.roleCode, description: r.description || '', dataScope: r.dataScope || 1, status: r.status, sortOrder: r.sortOrder || 99 })
  modal.value = 'role'
}
function openPermissionAssign() {
  if (!selectedRoleIds.value.length) { message.value = '请先勾选要分配权限的角色'; return }
  const r = selectedRole.value
  if (!r) return
  permissionForm.roleId = String(r.roleId)
  permissionForm.permissionIds = permissionIdsFrom(r)
  modal.value = 'role-permission'
}
function openDataPermission() {
  if (!selectedRoleIds.value.length) { message.value = '请先勾选角色'; return }
  const r = selectedRole.value
  if (!r) return
  permissionForm.roleId = String(r.roleId)
  permissionForm.dataScope = r.dataScope || 1
  permissionForm.deptIds = []
  modal.value = 'data-permission'
}
function openUserRoleAssign() {
  if (!selectedUserIds.value.length) { message.value = '请先勾选要分配角色的用户'; return }
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
    message.value = '用户已保存'
    await loadAll()
  } catch (err: any) { userFormError.value = err?.message || '保存失败' }
}

async function removeUsers() {
  if (!selectedUserIds.value.length) { message.value = '请勾选要删除的用户'; return }
  if (!confirm(`确认删除选中的 ${selectedUserIds.value.length} 个用户？`)) return
  try {
    await deleteUsers(selectedUserIds.value)
    message.value = '用户已删除'
    selectedUserIds.value = []
    await loadAll()
  } catch (err: any) { message.value = err?.message || '删除失败' }
}

async function resetPasswords() {
  if (!selectedUserIds.value.length) { message.value = '请勾选要重置密码的用户'; return }
  if (!confirm(`确认重置 ${selectedUserIds.value.length} 个用户的密码？`)) return
  try {
    await resetUserPasswords(selectedUserIds.value)
    message.value = '密码已重置为默认密码'
  } catch (err: any) { message.value = err?.message || '重置失败' }
}

async function saveUserRoles() {
  if (!selectedUserIds.value.length) return
  try {
    await assignUserRoles(selectedUserIds.value[0], userForm.roleIds)
    modal.value = null
    message.value = '角色分配已保存'
    await loadAll()
  } catch (err: any) { message.value = err?.message || '保存失败' }
}

async function saveRole() {
  if (!roleForm.roleName.trim()) { message.value = '角色名称为必填项'; return }
  if (!roleForm.roleCode.trim()) { message.value = '角色编码为必填项'; return }
  try {
    if (editingRoleId.value === null) { await createRole(roleForm) }
    else { await updateRole(editingRoleId.value, roleForm) }
    modal.value = null
    message.value = '角色已保存'
    await loadAll()
  } catch (err: any) { message.value = err?.message || '保存失败' }
}

async function removeRoles() {
  if (!selectedRoleIds.value.length) { message.value = '请勾选要删除的角色'; return }
  if (!confirm(`确认删除选中的 ${selectedRoleIds.value.length} 个角色？`)) return
  try {
    await deleteRoles(selectedRoleIds.value)
    message.value = '角色已删除'
    selectedRoleIds.value = []
    await loadAll()
  } catch (err: any) { message.value = err?.message || '删除失败' }
}

async function saveRolePermissions() {
  try {
    await assignRolePermissions(Number(permissionForm.roleId), permissionForm.permissionIds)
    modal.value = null
    message.value = '权限分配已保存'
    await loadAll()
  } catch (err: any) { message.value = err?.message || '保存失败' }
}

async function saveDataPermission() {
  try {
    await updateDataPermission(Number(permissionForm.roleId), permissionForm.dataScope, permissionForm.deptIds)
    modal.value = null
    message.value = '数据权限已保存'
    await loadAll()
  } catch (err: any) { message.value = err?.message || '保存失败' }
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
  message.value = ''
  selectedUserIds.value = []
  selectedRoleIds.value = []
  userPagination.page = 1
  rolePagination.page = 1
  loadAll()
})
</script>

<template>
  <div class="md-page sys-page">
    <!-- 顶栏（与采购管理 / 证照管理一致：MdPageHeader 渐变） -->
    <MdPageHeader
      crumb="系统管理"
      :title="title"
      :description="subtitle"
      :loading="loading"
      @refresh="loadAll"
    />

    <!-- Tab -->
    <div class="md-tabs sys-tabs">
      <button type="button" :class="['md-tab', { active: pageMode === 'user' }]" @click="switchToUser">
        <LayoutList :size="14" /> 用户管理
      </button>
      <button type="button" :class="['md-tab', { active: pageMode === 'role' }]" @click="switchToRole">
        <ShieldCheck :size="14" /> 角色权限
      </button>
    </div>

    <!-- 工具栏 -->
    <div class="md-toolbar sys-toolbar">
      <div class="sys-search-wrap">
        <span class="sys-search-label">关键词</span>
        <div class="sys-search-input-wrap">
          <input
            v-if="pageMode === 'user'"
            v-model="userQuery.username"
            class="sys-search-input"
            placeholder="用户名 / 姓名 / 科室"
            @keyup.enter="loadAll"
          />
          <input
            v-else
            v-model="roleQuery.roleName"
            class="sys-search-input"
            placeholder="角色名称 / 编码"
            @keyup.enter="loadAll"
          />
          <button class="sys-search-btn" type="button" @click="loadAll"><Search :size="15" /></button>
        </div>
      </div>
      <div class="sys-toolbar-right">
        <template v-if="pageMode === 'user'">
          <button v-if="authStore.hasPermission('user:create')" class="md-btn md-btn-primary" type="button" @click="openUserCreate"><Plus :size="15" /> 新增</button>
          <button v-if="authStore.hasPermission('user:update')" class="md-btn" type="button" @click="openUserEdit()"><Pencil :size="15" /> 编辑</button>
          <button v-if="authStore.hasPermission('user:delete')" class="md-btn md-btn-danger" type="button" @click="removeUsers"><Trash2 :size="15" /> 删除</button>
          <button v-if="authStore.hasPermission('user:reset-password')" class="md-btn" type="button" @click="resetPasswords"><KeyRound :size="15" /> 重置密码</button>
          <button v-if="authStore.hasPermission('user:assign-role')" class="md-btn" type="button" @click="openUserRoleAssign"><UserCheck :size="15" /> 分配角色</button>
        </template>
        <template v-else>
          <button v-if="authStore.hasPermission('role:create')" class="md-btn md-btn-primary" type="button" @click="openRoleCreate"><Plus :size="15" /> 新增</button>
          <button v-if="authStore.hasPermission('role:update')" class="md-btn" type="button" @click="openRoleEdit()"><Pencil :size="15" /> 编辑</button>
          <button v-if="authStore.hasPermission('role:delete')" class="md-btn md-btn-danger" type="button" @click="removeRoles"><Trash2 :size="15" /> 删除</button>
          <button v-if="authStore.hasPermission('role:assign-permission')" class="md-btn" type="button" @click="openPermissionAssign"><ShieldCheck :size="15" /> 分配权限</button>
          <button v-if="authStore.hasPermission('role:data-permission')" class="md-btn" type="button" @click="openDataPermission"><Database :size="15" /> 数据权限</button>
        </template>
      </div>
    </div>

    <!-- 内容 -->
    <div class="md-content sys-content">

      <!-- 汇总卡片 -->
      <div class="sys-stat-grid">
        <article v-for="item in (pageMode === 'role' ? roleStats : userStats)" :key="item.label">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </div>

      <!-- 消息 -->
      <div v-if="message" class="md-message">{{ message }}</div>

      <!-- 用户列表 -->
      <div v-if="pageMode === 'user'" class="md-table-card">
        <div class="table-scroll">
          <table class="md-table">
            <thead>
              <tr>
                <th style="width:40px"></th>
                <th>用户名</th>
                <th>真实姓名</th>
                <th>所属科室</th>
                <th>角色</th>
                <th>手机</th>
                <th>邮箱</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td colspan="10" class="text-center muted">正在加载...</td></tr>
              <tr v-else-if="!users.length"><td colspan="10" class="text-center muted">暂无数据</td></tr>
              <tr v-else v-for="row in users" :key="row.userId">
                <td><input type="checkbox" :checked="selectedUserIds.includes(row.userId)" @change="onUserCheckbox(row.userId, $event)" /></td>
                <td>{{ row.username }}</td>
                <td>{{ row.realName }}</td>
                <td>{{ row.deptName || '-' }}</td>
                <td>{{ row.roleNames || '未分配' }}</td>
                <td>{{ row.phone || '-' }}</td>
                <td>{{ row.email || '-' }}</td>
                <td><span :class="['md-status', row.status === 1 ? 'success' : 'danger']">{{ row.status === 1 ? '启用' : '停用' }}</span></td>
                <td>{{ row.updateTime || '-' }}</td>
                <td><button class="md-btn-text" type="button" @click="openUserEdit(row)"><Pencil :size="13" /> 编辑</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="md-pagination">
          <span>共 <strong>{{ userPagination.total }}</strong> 条</span>
          <PaginationControls :page="userPagination.page" :size="userPagination.size" :total="userPagination.total" :loading="loading" @change-page="changeUserPage" @change-size="changeUserPageSize" />
        </div>
      </div>

      <!-- 角色列表 -->
      <div v-else class="md-table-card">
        <div class="table-scroll">
          <table class="md-table">
            <thead>
              <tr>
                <th style="width:40px"></th>
                <th>角色名称</th>
                <th>角色编码</th>
                <th>数据权限</th>
                <th>用户数</th>
                <th>权限点</th>
                <th>状态</th>
                <th>说明</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td colspan="8" class="text-center muted">正在加载...</td></tr>
              <tr v-else-if="!roles.length"><td colspan="8" class="text-center muted">暂无数据</td></tr>
              <tr v-else v-for="row in roles" :key="row.roleId">
                <td><input type="checkbox" :checked="selectedRoleIds.includes(row.roleId)" @change="onRoleCheckbox(row.roleId, $event)" /></td>
                <td>{{ row.roleName }}</td>
                <td>{{ row.roleCode }}</td>
                <td>{{ dataScopeName(row.dataScope) }}</td>
                <td>{{ row.userCount }}</td>
                <td>{{ row.permissionCount }}</td>
                <td><span :class="['md-status', row.status === 1 ? 'success' : 'danger']">{{ row.status === 1 ? '启用' : '停用' }}</span></td>
                <td>{{ row.description || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="md-pagination">
          <span>共 <strong>{{ rolePagination.total }}</strong> 条</span>
          <PaginationControls :page="rolePagination.page" :size="rolePagination.size" :total="rolePagination.total" :loading="loading" @change-page="changeRolePage" @change-size="changeRolePageSize" />
        </div>
      </div>
    </div>

    <!-- 弹窗 -->
    <div v-if="modal" class="md-dialog-mask" @click.self="modal = null">
      <div class="md-dialog">
        <div class="md-dialog-header">
          <h3>
            {{
              modal === 'user' ? (editingUserId ? '编辑用户' : '新增用户')
              : modal === 'role' ? (editingRoleId ? '编辑角色' : '新增角色')
              : modal === 'user-role' ? `为 ${selectedUser?.realName} 分配角色`
              : modal === 'role-permission' ? `为 ${selectedRole?.roleName} 分配菜单与按钮权限`
              : '数据权限'
            }}
          </h3>
          <button class="md-btn-icon" type="button" @click="modal = null"><X :size="16" /></button>
        </div>
        <div class="md-dialog-body">

          <!-- 用户表单 -->
          <div v-if="modal === 'user'" class="md-form-grid">
            <label><span>用户名</span><input v-model="userForm.username" class="md-input" /></label>
            <label v-if="!editingUserId"><span>初始密码</span><input v-model="userForm.password" type="password" class="md-input" /></label>
            <label><span>真实姓名</span><input v-model="userForm.realName" class="md-input" /></label>
            <label><span>手机号</span><input v-model="userForm.phone" class="md-input" /></label>
            <label><span>邮箱</span><input v-model="userForm.email" class="md-input" /></label>
            <label><span>性别</span><select v-model.number="userForm.gender" class="md-select"><option :value="0">未知</option><option :value="1">男</option><option :value="2">女</option></select></label>
            <label><span>所属科室</span><select v-model="userForm.deptId" class="md-select"><option :value="0">未选择</option><option v-for="d in departments" :key="d.deptId" :value="d.deptId">{{ d.deptName }}</option></select></label>
            <label><span>状态</span><select v-model.number="userForm.status" class="md-select"><option :value="1">启用</option><option :value="0">停用</option></select></label>
            <label class="wide"><span>角色分配</span></label>
            <div class="sys-perm-checkboxes">
              <label v-for="r in roles" :key="r.roleId"><input v-model="userForm.roleIds" type="checkbox" :value="r.roleId" /> {{ r.roleName }}</label>
            </div>
            <p v-if="userFormError" class="md-error wide">{{ userFormError }}</p>
          </div>

          <!-- 角色表单 -->
          <div v-else-if="modal === 'role'" class="md-form-grid">
            <label><span>角色名称</span><input v-model="roleForm.roleName" class="md-input" /></label>
            <label><span>角色编码</span><input v-model="roleForm.roleCode" class="md-input" :disabled="Boolean(editingRoleId)" /></label>
            <label><span>数据权限</span><select v-model.number="roleForm.dataScope" class="md-select"><option :value="1">全部数据</option><option :value="2">本部门数据</option><option :value="3">本部门及子部门</option><option :value="4">仅本人数据</option><option :value="5">自定义部门</option></select></label>
            <label><span>状态</span><select v-model.number="roleForm.status" class="md-select"><option :value="1">启用</option><option :value="0">停用</option></select></label>
            <label><span>排序</span><input v-model.number="roleForm.sortOrder" type="number" class="md-input" /></label>
            <label class="wide"><span>角色说明</span><textarea v-model="roleForm.description" class="md-textarea" rows="2" /></label>
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
          <div v-else class="md-form-grid">
            <label><span>数据权限范围</span><select v-model.number="permissionForm.dataScope" class="md-select"><option :value="1">全部数据</option><option :value="2">本部门数据</option><option :value="3">本部门及子部门</option><option :value="4">仅本人数据</option><option :value="5">自定义部门</option></select></label>
            <div v-if="permissionForm.dataScope === 5" class="sys-perm-checkboxes wide">
              <label v-for="d in departments" :key="d.deptId"><input v-model="permissionForm.deptIds" type="checkbox" :value="d.deptId" /> {{ d.deptName }}</label>
            </div>
          </div>

        </div>
        <div class="md-dialog-footer">
          <button class="md-btn" type="button" @click="modal = null">取消</button>
          <button v-if="modal === 'user'" class="md-btn md-btn-primary" type="button" @click="saveUser"><Save :size="15" /> 保存</button>
          <button v-else-if="modal === 'role'" class="md-btn md-btn-primary" type="button" @click="saveRole"><Save :size="15" /> 保存</button>
          <button v-else-if="modal === 'user-role'" class="md-btn md-btn-primary" type="button" @click="saveUserRoles"><Save :size="15" /> 保存</button>
          <button v-else-if="modal === 'role-permission'" class="md-btn md-btn-primary" type="button" @click="saveRolePermissions"><Save :size="15" /> 保存</button>
          <button v-else class="md-btn md-btn-primary" type="button" @click="saveDataPermission"><Save :size="15" /> 保存</button>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
.sys-page { display: flex; flex-direction: column; }
.sys-tabs { margin: 12px 0 0; }
.sys-toolbar { margin: 0; }
.sys-content { padding: 12px 0 24px; display: flex; flex-direction: column; gap: 12px; }
.sys-stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}
.sys-stat-grid article {
  background: var(--md-bg-card);
  border: 1px solid var(--md-border);
  border-radius: var(--md-radius-md);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.sys-stat-grid article span { font-size: 12px; color: var(--md-text-secondary); font-weight: 500; }
.sys-stat-grid article strong { font-size: 24px; color: var(--md-text-heading); font-weight: 700; font-family: var(--md-font-mono); }
.table-scroll { overflow-x: auto; }
.sys-perm-checkboxes {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 240px;
  overflow-y: auto;
  padding: 8px;
  background: var(--md-bg-muted);
  border: 1px solid var(--md-border);
  border-radius: var(--md-radius);
  grid-column: 1 / -1;
}
.sys-perm-checkboxes label {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
  color: var(--md-text);
  padding: 4px 6px;
  border-radius: var(--md-radius-sm);
  transition: background .15s;
}
.sys-perm-checkboxes label:hover { background: var(--md-primary-soft); color: var(--md-primary-strong); }
.text-center { text-align: center; }
.muted { color: var(--md-text-muted); padding: 32px; }
</style>
