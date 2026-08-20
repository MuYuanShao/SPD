// 用户工作区上下文 Store——管理当前科室、当前库房等上下文状态
import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface DepartmentOption {
  id: number
  name: string
  code: string
}

export interface WarehouseOption {
  id: number
  name: string
  code: string
  campusName: string
}

export const useWorkspaceStore = defineStore('workspace', () => {
  // ===== State =====
  /** 当前用户选中的科室 */
  const currentDepartment = ref<DepartmentOption | null>(null)
  /** 当前用户选中的库房 */
  const currentWarehouse = ref<WarehouseOption | null>(null)
  /** 可选科室列表 */
  const departments = ref<DepartmentOption[]>([])
  /** 可选库房列表（根据当前科室过滤） */
  const warehouses = ref<WarehouseOption[]>([])

  // ===== Actions =====
  /**
   * 设置当前科室
   * 切换科室时同时清空当前库房选择
   */
  function setDepartment(dept: DepartmentOption | null) {
    currentDepartment.value = dept
    currentWarehouse.value = null
  }

  /**
   * 设置当前库房
   */
  function setWarehouse(warehouse: WarehouseOption | null) {
    currentWarehouse.value = warehouse
  }

  /**
   * 初始化可选科室和库房列表
   */
  function setOptions(depts: DepartmentOption[], whs: WarehouseOption[]) {
    departments.value = depts
    warehouses.value = whs
  }

  /** 重置所有状态 */
  function $reset() {
    currentDepartment.value = null
    currentWarehouse.value = null
    departments.value = []
    warehouses.value = []
  }

  return {
    currentDepartment,
    currentWarehouse,
    departments,
    warehouses,
    setDepartment,
    setWarehouse,
    setOptions,
    $reset,
  }
})
