<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
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
  UserCheck
} from '@lucide/vue'
import PaginationControls from '../../components/common/PaginationControls.vue'
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
  type UserRow
} from '../../api/foundation'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
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

const userQuery = reactive({
  username: '',
  realName: '',
  status: '',
  deptName: ''
})
const roleQuery = reactive({
  roleName: '',
  roleCode: '',
  status: ''
})
const userForm = reactive({
  username: '',
  password: '',
  realName: '',
  phone: '',
  email: '',
  gender: 0,
  deptId: '',
  status: 1,
  roleIds: [] as number[]
})
const roleForm = reactive({
  roleName: '',
  roleCode: '',
  description: '',
  dataScope: 1,
  status: 1,
  sortOrder: 99
})
const permissionForm = reactive({
  roleId: '',
  permissionIds: [] as number[],
  dataScope: 1,
  deptIds: [] as number[]
})

const selectedUser = computed(() => users.value.find((item) => item.userId === selectedUserIds.value[0]))
const selectedRole = computed(() => roles.value.find((item) => item.roleId === selectedRoleIds.value[0]))
const title = computed(() => (pageMode.value === 'role' ? '角色权限' : '用户管理'))
const subtitle = computed(() =>
  pageMode.value === 'role'
    ? '维护角色、菜单按钮权限与数据权限范围。'
    : '维护系统账号、所属科室、角色分配与密码重置。'
)
const userStats = computed(() => [
  { label: '启用用户', value: users.value.filter((item) => item.status === 1).length },
  { label: '停用用户', value: users.value.filter((item) => item.status === 0).length },
  { label: '已分配角色', value: users.value.filter((item) => item.roleNames).length }
])
const roleStats = computed(() => [
  { label: '启用角色', value: roles.value.filter((item) => item.status === 1).length },
  { label: '权限点', value: permissions.value.length },
  { label: '已关联用户', value: roles.value.reduce((sum, item) => sum + Number(item.userCount || 0), 0) }
])

function roleIdsFrom(row?: UserRow) {
  return row?.roleIds ? row.roleIds.split(',').map(Number).filter(Boolean) : []
}

function permissionIdsFrom(row?: RoleRow) {
  return row?.permissionIds ? row.permissionIds.split(',').map(Number).filter(Boolean) : []
}
function customDeptIdsFrom(row?: RoleRow) {
  return row?.customDeptIds ? row.customDeptIds.split(',').map(Number).filter(Boolean) : []
}


async function loadAll() {
  loading.value = true
  try {
    const [userRows, roleRows, permissionRows, deptRows] = await Promise.all([
      fetchUsers({ ...userQuery, page: String(userPagination.page), size: String(userPagination.size) }),
      fetchRoles({ ...roleQuery, page: String(rolePagination.page), size: String(rolePagination.size) }),
      fetchPermissions(),
      fetchDepartmentOptions()
    ])
    users.value = userRows.rows
    userPagination.total = userRows.total
    roles.value = roleRows.rows
    rolePagination.total = roleRows.total
    permissions.value = permissionRows
    departments.value = deptRows
  } finally {
    loading.value = false
  }
}

async function changeUserPage(page: number) {
  const totalPages = Math.max(Math.ceil(userPagination.total / Math.max(userPagination.size, 1)), 1)
  const nextPage = Math.min(Math.max(page, 1), totalPages)
  if (nextPage === userPagination.page) return
  userPagination.page = nextPage
  await loadAll()
}

async function changeUserPageSize(size: number) {
  if (size === userPagination.size) return
  userPagination.size = size
  userPagination.page = 1
  await loadAll()
}

async function changeRolePage(page: number) {
  const totalPages = Math.max(Math.ceil(rolePagination.total / Math.max(rolePagination.size, 1)), 1)
  const nextPage = Math.min(Math.max(page, 1), totalPages)
  if (nextPage === rolePagination.page) return
  rolePagination.page = nextPage
  await loadAll()
}

async function changeRolePageSize(size: number) {
  if (size === rolePagination.size) return
  rolePagination.size = size
  rolePagination.page = 1
  await loadAll()
}

function resetUserForm() {
  editingUserId.value = null
  userFormError.value = ''
  Object.assign(userForm, {
    username: '',
    password: '',
    realName: '',
    phone: '',
    email: '',
    gender: 0,
    deptId: '',
    status: 1,
    roleIds: []
  })
}

function resetRoleForm() {
  editingRoleId.value = null
  Object.assign(roleForm, {
    roleName: '',
    roleCode: '',
    description: '',
    dataScope: 1,
    status: 1,
    sortOrder: 99
  })
}

function openUserCreate() {
  resetUserForm()
  modal.value = 'user'
}

function openUserEdit(row?: UserRow) {
  const target = row || selectedUser.value
  if (!target) {
    message.value = '请先勾选一个用户'
    return
  }
  editingUserId.value = target.userId
  userFormError.value = ''
  selectedUserIds.value = [target.userId]
  Object.assign(userForm, {
    username: target.username,
    password: '',
    realName: target.realName,
    phone: target.phone ?? '',
    email: target.email ?? '',
    gender: target.gender ?? 0,
    deptId: target.deptId ? String(target.deptId) : '',
    status: target.status,
    roleIds: roleIdsFrom(target)
  })
  modal.value = 'user'
}

function openRoleCreate() {
  resetRoleForm()
  modal.value = 'role'
}

function openRoleEdit() {
  const row = selectedRole.value
  if (!row) {
    message.value = '请先勾选一个角色'
    return
  }
  editingRoleId.value = row.roleId
  Object.assign(roleForm, {
    roleName: row.roleName,
    roleCode: row.roleCode,
    description: row.description ?? '',
    dataScope: row.dataScope,
    status: row.status,
    sortOrder: row.sortOrder
  })
  modal.value = 'role'
}

function openUserRoleAssign() {
  const row = selectedUser.value
  if (!row) {
    message.value = '请先勾选一个用户'
    return
  }
  editingUserId.value = row.userId
  userForm.roleIds = roleIdsFrom(row)
  modal.value = 'user-role'
}

function openPermissionAssign() {
  const row = selectedRole.value
  if (!row) {
    message.value = '请先勾选一个角色'
    return
  }
  permissionForm.roleId = String(row.roleId)
  permissionForm.permissionIds = permissionIdsFrom(row)
  modal.value = 'role-permission'
}

function openDataPermission() {
  const row = selectedRole.value
  if (!row) {
    message.value = '请先勾选一个角色'
    return
  }
  permissionForm.roleId = String(row.roleId)
  permissionForm.dataScope = row.dataScope
  modal.value = 'data-permission'
  permissionForm.deptIds = customDeptIdsFrom(row)
}

async function saveUser() {
  userFormError.value = ''
  const payload = {
    username: userForm.username.trim(),
    password: userForm.password,
    realName: userForm.realName.trim(),
    phone: userForm.phone.trim(),
    email: userForm.email.trim(),
    gender: Number(userForm.gender),
    deptId: userForm.deptId ? Number(userForm.deptId) : undefined,
    status: Number(userForm.status),
    roleIds: userForm.roleIds
  }
  try {
    if (editingUserId.value) {
      await updateUser(editingUserId.value, payload)
      message.value = '用户已保存'
    } else {
      await createUser(payload)
      message.value = '用户已新增'
    }
    modal.value = null
    await loadAll()
  } catch (error: any) {
    const errorMessage =
      error?.response?.data?.message || error?.message || '用户保存失败，请检查用户信息'
    userFormError.value = errorMessage
    message.value = errorMessage
  }
}

async function saveRole() {
  const payload = {
    roleName: roleForm.roleName.trim(),
    roleCode: roleForm.roleCode.trim() || undefined,
    description: roleForm.description.trim(),
    dataScope: Number(roleForm.dataScope),
    status: Number(roleForm.status),
    sortOrder: Number(roleForm.sortOrder)
  }
  try {
    if (editingRoleId.value) {
      await updateRole(editingRoleId.value, payload)
      message.value = '角色已保存'
    } else {
      await createRole(payload)
      message.value = '角色已新增'
    }
    modal.value = null
    await loadAll()
  } catch (error: any) {
    message.value = error?.response?.data?.message || error?.message || '角色保存失败，请检查角色信息'
  }
}

async function removeUsers() {
  if (!selectedUserIds.value.length) {
    message.value = '请先勾选用户'
    return
  }
  await deleteUsers(selectedUserIds.value)
  selectedUserIds.value = []
  message.value = '用户已删除'
  await loadAll()
}

async function removeRoles() {
  if (!selectedRoleIds.value.length) {
    message.value = '请先勾选角色'
    return
  }
  await deleteRoles(selectedRoleIds.value)
  selectedRoleIds.value = []
  message.value = '角色已删除'
  await loadAll()
}

async function resetPasswords() {
  if (!selectedUserIds.value.length) {
    message.value = '请先勾选用户'
    return
  }
  const result = await resetUserPasswords(selectedUserIds.value)
  message.value = `已重置为临时密码：${result.temporaryPassword}`
  await loadAll()
}

async function saveUserRoles() {
  if (!editingUserId.value) return
  await assignUserRoles(editingUserId.value, userForm.roleIds)
  modal.value = null
  message.value = '角色分配已保存'
  await loadAll()
}

async function saveRolePermissions() {
  await assignRolePermissions(Number(permissionForm.roleId), permissionForm.permissionIds)
  modal.value = null
  message.value = '权限分配已保存'
  await loadAll()
}

async function saveDataPermission() {
  await updateDataPermission(
    Number(permissionForm.roleId),
    Number(permissionForm.dataScope),
    permissionForm.deptIds
  )
  modal.value = null
  message.value = '数据权限已保存'
  await loadAll()
}

function toggleUser(id: number) {
  selectedUserIds.value = selectedUserIds.value.includes(id)
    ? selectedUserIds.value.filter((item) => item !== id)
    : [...selectedUserIds.value, id]
}

function toggleRole(id: number) {
  selectedRoleIds.value = selectedRoleIds.value.includes(id)
    ? selectedRoleIds.value.filter((item) => item !== id)
    : [...selectedRoleIds.value, id]
}

function dataScopeName(scope: number) {
  return ['-', '全部数据', '本部门数据', '本部门及子部门', '仅本人数据', '自定义部门'][scope] ?? '全部数据'
}

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
  <section class="foundation-page">
    <div class="breadcrumb-line">
      <span>系统管理</span>
      <strong>{{ title }}</strong>
    </div>

    <div class="detail-heading">
      <div>
        <p>基础权限</p>
        <h2>{{ title }}</h2>
        <small>{{ subtitle }}</small>
      </div>
      <button class="btn" type="button" @click="loadAll">
        <RefreshCw :size="17" />
        刷新
      </button>
    </div>

    <div class="foundation-stat-grid">
      <article v-for="item in pageMode === 'role' ? roleStats : userStats" :key="item.label">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
    </div>

    <p v-if="message" class="inline-message">{{ message }}</p>

    <section v-if="pageMode === 'user'" class="hospital-catalog-panel">
      <div class="hospital-action-row">
        <button class="btn" type="button" @click="loadAll"><LayoutList :size="17" /> 用户列表查询</button>
        <button v-if="authStore.hasPermission('user:create')" class="btn btn-primary" type="button" @click="openUserCreate"><Plus :size="18" /> 新增</button>
        <button v-if="authStore.hasPermission('user:update')" class="btn" type="button" @click="openUserEdit()"><Pencil :size="17" /> 编辑</button>
        <button v-if="authStore.hasPermission('user:delete')" class="btn btn-danger" type="button" @click="removeUsers"><Trash2 :size="17" /> 删除</button>
        <button v-if="authStore.hasPermission('user:reset-password')" class="btn" type="button" @click="resetPasswords"><KeyRound :size="17" /> 重置密码</button>
        <button v-if="authStore.hasPermission('user:assign-role')" class="btn" type="button" @click="openUserRoleAssign"><UserCheck :size="17" /> 分配角色</button>
      </div>

      <div class="hospital-query-grid foundation-query-grid">
        <label><span>用户名</span><input v-model="userQuery.username" placeholder="模糊查询用户名" /></label>
        <label><span>真实姓名</span><input v-model="userQuery.realName" placeholder="模糊查询真实姓名" /></label>
        <label><span>科室</span><input v-model="userQuery.deptName" placeholder="模糊查询科室" /></label>
        <label>
          <span>状态</span>
          <select v-model="userQuery.status">
            <option value="">全部</option>
            <option value="1">启用</option>
            <option value="0">停用</option>
          </select>
        </label>
        <button class="btn btn-primary" type="button" @click="loadAll"><Search :size="18" /> 查询</button>
      </div>

      <div class="table-scroll">
        <table class="master-table foundation-table">
          <thead>
            <tr>
              <th></th>
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
            <tr v-for="row in users" :key="row.userId" @dblclick="openUserEdit(row)">
              <td><input type="checkbox" :checked="selectedUserIds.includes(row.userId)" @change="toggleUser(row.userId)" /></td>
              <td>{{ row.username }}</td>
              <td>{{ row.realName }}</td>
              <td>{{ row.deptName || '-' }}</td>
              <td>{{ row.roleNames || '未分配' }}</td>
              <td>{{ row.phone || '-' }}</td>
              <td>{{ row.email || '-' }}</td>
              <td><span :class="['status-badge', row.status === 1 ? 'enabled' : 'disabled']">{{ row.status === 1 ? '启用' : '停用' }}</span></td>
              <td>{{ row.updateTime || '-' }}</td>
              <td>
                <div class="row-actions">
                  <button v-if="authStore.hasPermission('user:update')" class="btn-text" type="button" @click.stop="openUserEdit(row)">
                    <Pencil :size="15" />
                    编辑
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationControls
        :page="userPagination.page"
        :size="userPagination.size"
        :total="userPagination.total"
        :loading="loading"
        @change-page="changeUserPage"
        @change-size="changeUserPageSize"
      />
    </section>

    <section v-else class="hospital-catalog-panel">
      <div class="hospital-action-row">
        <button class="btn" type="button" @click="loadAll"><LayoutList :size="17" /> 角色列表查询</button>
        <button v-if="authStore.hasPermission('role:create')" class="btn btn-primary" type="button" @click="openRoleCreate"><Plus :size="18" /> 新增</button>
        <button v-if="authStore.hasPermission('role:update')" class="btn" type="button" @click="openRoleEdit"><Pencil :size="17" /> 编辑</button>
        <button v-if="authStore.hasPermission('role:delete')" class="btn btn-danger" type="button" @click="removeRoles"><Trash2 :size="17" /> 删除</button>
        <button v-if="authStore.hasPermission('role:assign-permission')" class="btn" type="button" @click="openPermissionAssign"><ShieldCheck :size="17" /> 分配权限</button>
        <button v-if="authStore.hasPermission('role:data-permission')" class="btn" type="button" @click="openDataPermission"><Database :size="17" /> 数据权限</button>
      </div>

      <div class="hospital-query-grid foundation-query-grid">
        <label><span>角色名称</span><input v-model="roleQuery.roleName" placeholder="模糊查询角色名称" /></label>
        <label><span>角色编码</span><input v-model="roleQuery.roleCode" placeholder="模糊查询角色编码" /></label>
        <label>
          <span>状态</span>
          <select v-model="roleQuery.status">
            <option value="">全部</option>
            <option value="1">启用</option>
            <option value="0">停用</option>
          </select>
        </label>
        <button class="btn btn-primary" type="button" @click="loadAll"><Search :size="18" /> 查询</button>
      </div>

      <div class="table-scroll">
        <table class="master-table foundation-table">
          <thead>
            <tr>
              <th></th>
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
            <tr v-for="row in roles" :key="row.roleId">
              <td><input type="checkbox" :checked="selectedRoleIds.includes(row.roleId)" @change="toggleRole(row.roleId)" /></td>
              <td>{{ row.roleName }}</td>
              <td>{{ row.roleCode }}</td>
              <td>{{ dataScopeName(row.dataScope) }}</td>
              <td>{{ row.userCount }}</td>
              <td>{{ row.permissionCount }}</td>
              <td><span :class="['status-badge', row.status === 1 ? 'enabled' : 'disabled']">{{ row.status === 1 ? '启用' : '停用' }}</span></td>
              <td>{{ row.description || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <PaginationControls
        :page="rolePagination.page"
        :size="rolePagination.size"
        :total="rolePagination.total"
        :loading="loading"
        @change-page="changeRolePage"
        @change-size="changeRolePageSize"
      />
    </section>

    <div v-if="modal" class="modal-mask">
      <section class="supplier-modal foundation-modal">
        <div class="modal-heading">
          <h3>
            {{
              modal === 'user'
                ? editingUserId
                  ? '编辑用户'
                  : '新增用户'
                : modal === 'role'
                  ? editingRoleId
                    ? '编辑角色'
                    : '新增角色'
                  : modal === 'user-role'
                    ? '分配角色'
                    : modal === 'role-permission'
                      ? '分配权限'
                      : '数据权限'
            }}
          </h3>
          <button class="btn" type="button" @click="modal = null">关闭</button>
        </div>

        <div v-if="modal === 'user'" class="supplier-form-grid">
          <label><span>用户名</span><input v-model="userForm.username" /></label>
          <label v-if="!editingUserId"><span>初始密码</span><input v-model="userForm.password" /></label>
          <label><span>真实姓名</span><input v-model="userForm.realName" /></label>
          <label><span>手机号</span><input v-model="userForm.phone" /></label>
          <label><span>邮箱</span><input v-model="userForm.email" /></label>
          <label>
            <span>性别</span>
            <select v-model.number="userForm.gender">
              <option :value="0">未知</option>
              <option :value="1">男</option>
              <option :value="2">女</option>
            </select>
          </label>
          <label>
            <span>所属科室</span>
            <select v-model="userForm.deptId">
              <option value="">未选择</option>
              <option v-for="dept in departments" :key="dept.deptId" :value="String(dept.deptId)">{{ dept.deptName }}</option>
            </select>
          </label>
          <label>
            <span>状态</span>
            <select v-model.number="userForm.status">
              <option :value="1">启用</option>
              <option :value="0">停用</option>
            </select>
          </label>
          <div class="checkbox-cloud">
            <span>角色</span>
            <label v-for="role in roles" :key="role.roleId">
              <input v-model="userForm.roleIds" type="checkbox" :value="role.roleId" />
              {{ role.roleName }}
            </label>
          </div>
          <p v-if="userFormError" class="inline-message full-line">{{ userFormError }}</p>
          <button class="btn btn-primary" type="button" @click="saveUser"><Save :size="18" /> 保存</button>
        </div>

        <div v-else-if="modal === 'role'" class="supplier-form-grid">
          <label><span>角色名称</span><input v-model="roleForm.roleName" /></label>
          <label><span>角色编码</span><input v-model="roleForm.roleCode" :disabled="Boolean(editingRoleId)" /></label>
          <label>
            <span>数据权限</span>
            <select v-model.number="roleForm.dataScope">
              <option :value="1">全部数据</option>
              <option :value="2">本部门数据</option>
              <option :value="3">本部门及子部门</option>
              <option :value="4">仅本人数据</option>
              <option :value="5">自定义部门</option>
            </select>
          </label>
          <label>
            <span>状态</span>
            <select v-model.number="roleForm.status">
              <option :value="1">启用</option>
              <option :value="0">停用</option>
            </select>
          </label>
          <label><span>排序</span><input v-model.number="roleForm.sortOrder" type="number" /></label>
          <label class="full-line"><span>角色说明</span><textarea v-model="roleForm.description" /></label>
          <button class="btn btn-primary" type="button" @click="saveRole"><Save :size="18" /> 保存</button>
        </div>

        <div v-else-if="modal === 'user-role'" class="checkbox-cloud modal-cloud">
          <span>为 {{ selectedUser?.realName }} 分配角色</span>
          <label v-for="role in roles" :key="role.roleId">
            <input v-model="userForm.roleIds" type="checkbox" :value="role.roleId" />
            {{ role.roleName }}
          </label>
          <button class="btn btn-primary" type="button" @click="saveUserRoles"><Save :size="18" /> 保存</button>
        </div>

        <div v-else-if="modal === 'role-permission'" class="checkbox-cloud modal-cloud">
          <span>为 {{ selectedRole?.roleName }} 分配菜单与按钮权限</span>
          <label v-for="permission in permissions" :key="permission.permissionId">
            <input v-model="permissionForm.permissionIds" type="checkbox" :value="permission.permissionId" />
            {{ permission.permissionType === 1 ? '菜单' : '按钮' }} · {{ permission.permissionName }}
          </label>
          <button class="btn btn-primary" type="button" @click="saveRolePermissions"><Save :size="18" /> 保存</button>
        </div>

        <div v-else class="supplier-form-grid">
          <label>
            <span>数据权限范围</span>
            <select v-model.number="permissionForm.dataScope">
              <option :value="1">全部数据</option>
              <option :value="2">本部门数据</option>
              <option :value="3">本部门及子部门</option>
              <option :value="4">仅本人数据</option>
              <option :value="5">自定义部门</option>
            </select>
          </label>
          <button class="btn btn-primary" type="button" @click="saveDataPermission"><Save :size="18" /> 保存</button>
          <div v-if="permissionForm.dataScope === 5" class="checkbox-cloud full-line">
            <span>授权部门</span>
            <label v-for="dept in departments" :key="dept.deptId">
              <input v-model="permissionForm.deptIds" type="checkbox" :value="dept.deptId" />
              {{ dept.deptName }}
            </label>
          </div>
        </div>
      </section>
    </div>
  </section>
</template>
