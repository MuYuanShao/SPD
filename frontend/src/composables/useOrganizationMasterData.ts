import { ref, type ComputedRef, type Ref } from 'vue'
import {
  createCampus,
  createDepartment,
  createDepartmentWarehouseCatalog,
  batchMaintainDepartmentWarehouseCatalogs,
  createWarehouse,
  createWarehouseLocation,
  deleteDepartmentWarehouseCatalogs,
  deleteWarehouseLocation,
  deleteCampuses,
  deleteDepartments,
  deleteWarehouses,
  fetchCampusOptions,
  fetchDepartmentWarehouseCatalogProductOptions,
  fetchDepartmentWarehouses,
  fetchWarehouseLocations,
  fetchWarehouseProductOptions,
  fetchWarehouseProducts,
  updateCampus,
  updateDepartment,
  updateDepartmentWarehouseCatalog,
  updateDepartmentWarehouseCatalogStatus,
  updateDepartmentWarehouses,
  updateWarehouse,
  updateWarehouseLocation,
  type CampusOption,
  type CampusPayload,
  type DepartmentWarehouseCatalogBatchPayload,
  type DepartmentWarehouseCatalogPayload,
  type DepartmentWarehouseRelation,
  type DepartmentPayload,
  type WarehouseLocation,
  type WarehouseLocationPayload,
  type WarehousePayload,
  type WarehouseProductOption
} from '../api/masterData'
import { fetchClosureOptions, type ClosureOptions } from '../api/operationalClosure'

interface OrganizationMasterDataDeps {
  selectedCodes: Ref<string[]>
  selectedCampus: ComputedRef<Record<string, unknown> | undefined>
  selectedDepartment: ComputedRef<Record<string, unknown> | undefined>
  selectedDepartmentWarehouseCatalog: ComputedRef<Record<string, unknown> | undefined>
  selectedWarehouse: ComputedRef<Record<string, unknown> | undefined>
  campusDialogOpen: Ref<boolean>
  campusDialogMode: Ref<'create' | 'edit'>
  departmentDialogOpen: Ref<boolean>
  departmentDialogMode: Ref<'create' | 'edit'>
  departmentWarehouseCatalogDialogOpen: Ref<boolean>
  departmentWarehouseCatalogDialogMode: Ref<'create' | 'edit'>
  warehouseDialogOpen: Ref<boolean>
  warehouseDialogMode: Ref<'create' | 'edit'>
  actionError: Ref<string>
  actionMessage: Ref<string>
  clearActionState: () => void
  loadPage: () => Promise<void>
}

export function useOrganizationMasterData(deps: OrganizationMasterDataDeps) {
  const campusForm = ref<CampusPayload>(emptyCampusForm())
  const campusOptions = ref<CampusOption[]>([])
  const departmentForm = ref<DepartmentPayload>(emptyDepartmentForm())
  const warehouseForm = ref<WarehousePayload>(emptyWarehouseForm())
  const warehouseProductOptions = ref<WarehouseProductOption[]>([])
  const warehouseProductOptionsLoading = ref(false)
  const departmentOptions = ref<Array<{ deptCode: string; deptName: string }>>([])
  const departmentWarehouseDialogOpen = ref(false)
  const departmentWarehouseLoading = ref(false)
  const departmentWarehouseSaving = ref(false)
  const departmentWarehouseRows = ref<DepartmentWarehouseRelation[]>([])
  const departmentWarehouseSelectedCodes = ref<string[]>([])
  const activeDepartmentForWarehouses = ref<Record<string, unknown> | null>(null)
  const departmentWarehouseCatalogForm = ref<DepartmentWarehouseCatalogPayload>(emptyDepartmentWarehouseCatalogForm())
  const departmentWarehouseCatalogBatchForm = ref<DepartmentWarehouseCatalogBatchPayload>(emptyDepartmentWarehouseCatalogBatchForm())
  const departmentWarehouseCatalogBatchDialogOpen = ref(false)
  const departmentWarehouseCatalogBatchSaving = ref(false)
  const departmentWarehouseCatalogBatchWarehouses = ref<DepartmentWarehouseRelation[]>([])
  const departmentWarehouseCatalogOptions = ref<ClosureOptions>({
    departments: [],
    warehouses: [],
    products: [],
    balances: []
  })
  const departmentWarehouseCatalogWarehouses = ref<DepartmentWarehouseRelation[]>([])
  const warehouseLocationDialogOpen = ref(false)
  const warehouseLocationLoading = ref(false)
  const warehouseLocationSaving = ref(false)
  const warehouseLocationRows = ref<WarehouseLocation[]>([])
  const warehouseLocationForm = ref<WarehouseLocationPayload>(emptyWarehouseLocationForm())
  const warehouseLocationEditingId = ref<number | null>(null)
  const activeWarehouseForLocations = ref<Record<string, unknown> | null>(null)

  async function loadCampusOptions() {
    try {
      campusOptions.value = await fetchCampusOptions()
    } catch {
      campusOptions.value = []
    }
  }

  function openCreateCampus() {
    deps.clearActionState()
    deps.campusDialogMode.value = 'create'
    campusForm.value = emptyCampusForm()
    deps.campusDialogOpen.value = true
  }

  function openEditCampus(row = deps.selectedCampus.value, requireSelected = true) {
    deps.clearActionState()
    if (!row || (requireSelected && deps.selectedCodes.value.length !== 1)) {
      deps.actionError.value = '请选择一条院区记录后再编辑'
      return
    }
    deps.campusDialogMode.value = 'edit'
    campusForm.value = {
      campusCode: String(row.code ?? ''),
      campusName: String(row.name ?? ''),
      address: String(row.address === '-' ? '' : row.address ?? ''),
      managerName: String(row.manager === '-' ? '' : row.manager ?? ''),
      phone: String(row.phone === '-' ? '' : row.phone ?? ''),
      sortOrder: Number(row.sortOrder ?? 0),
      status: String(row.status ?? '') === '停用' ? 0 : 1
    }
    deps.campusDialogOpen.value = true
  }

  async function saveCampus() {
    deps.clearActionState()
    const payload = normalizeCampusPayload(campusForm.value)
    if (!payload.campusCode || !payload.campusName) {
      deps.actionError.value = '院区编码、院区名称为必填项'
      return
    }
    campusForm.value = payload
    try {
      if (deps.campusDialogMode.value === 'create') {
        await createCampus(payload)
        deps.actionMessage.value = '院区已新增'
      } else {
        await updateCampus(payload.campusCode, payload)
        deps.actionMessage.value = '院区已编辑'
      }
      await loadCampusOptions()
      await deps.loadPage()
      deps.campusDialogOpen.value = false
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '院区保存失败'
    }
  }

  async function deleteSelectedCampuses(campusCodes = deps.selectedCodes.value) {
    deps.clearActionState()
    if (!campusCodes.length) {
      deps.actionError.value = '请选择需要删除的院区'
      return
    }
    if (!window.confirm(`确认删除已选择的 ${campusCodes.length} 条院区吗？`)) {
      return
    }
    try {
      const result = await deleteCampuses({ campusCodes })
      deps.actionMessage.value = `已删除 ${result.deletedRows} 条院区`
      await loadCampusOptions()
      await deps.loadPage()
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '院区删除失败'
    }
  }

  function openCreateDepartment() {
    void loadCampusOptions()
    deps.clearActionState()
    deps.departmentDialogMode.value = 'create'
    departmentForm.value = emptyDepartmentForm()
    deps.departmentDialogOpen.value = true
  }

  function openEditDepartment(row = deps.selectedDepartment.value, requireSelected = true) {
    void loadCampusOptions()
    deps.clearActionState()
    if (!row || (requireSelected && deps.selectedCodes.value.length !== 1)) {
      deps.actionError.value = '请选择一条科室记录后再编辑'
      return
    }

    deps.departmentDialogMode.value = 'edit'
    departmentForm.value = {
      deptCode: String(row.code ?? ''),
      deptName: String(row.name ?? ''),
      financeDeptCode: String(row.financeDeptCode === '-' ? '' : row.financeDeptCode ?? ''),
      financeDeptName: String(row.financeDept === '-' ? '' : row.financeDept ?? ''),
      campusName: String(row.campus === '-' ? '' : row.campus ?? ''),
      address: String(row.address === '-' ? '' : row.address ?? ''),
      managerName: String(row.manager === '-' ? '' : row.manager ?? ''),
      phone: String(row.phone === '-' ? '' : row.phone ?? ''),
      sortOrder: 0,
      status: String(row.status ?? '') === '停用' ? 0 : 1
    }
    deps.departmentDialogOpen.value = true
  }

  async function saveDepartment() {
    deps.clearActionState()
    const payload = normalizeDepartmentPayload(departmentForm.value)
    if (!payload.deptCode || !payload.deptName) {
      deps.actionError.value = '科室编码、科室名称为必填项'
      return
    }
    departmentForm.value = payload

    try {
      if (deps.departmentDialogMode.value === 'create') {
        await createDepartment(payload)
        deps.actionMessage.value = '科室已新增'
      } else {
        await updateDepartment(payload.deptCode, payload)
        deps.actionMessage.value = '科室已编辑'
      }
      await deps.loadPage()
      deps.departmentDialogOpen.value = false
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '科室保存失败'
    }
  }

  async function deleteSelectedDepartments(deptCodes = deps.selectedCodes.value) {
    deps.clearActionState()
    if (!deptCodes.length) {
      deps.actionError.value = '请选择需要删除的科室'
      return
    }
    if (!window.confirm(`确认删除已选择的 ${deptCodes.length} 条科室吗？`)) {
      return
    }

    try {
      const result = await deleteDepartments({ deptCodes })
      deps.actionMessage.value = `已删除 ${result.deletedRows} 条科室`
      await deps.loadPage()
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '科室删除失败'
    }
  }

  function showDepartmentRelationMessage() {
    deps.clearActionState()
    deps.actionMessage.value = deps.selectedCodes.value.length
      ? `已选择 ${deps.selectedCodes.value.length} 条科室，可在库房 / 货位管理中维护关联库房`
      : '请选择科室后查看或维护关联库房'
  }

  async function openDepartmentWarehouseDialog(row = deps.selectedDepartment.value, requireSelected = true) {
    deps.clearActionState()
    if (!row || (requireSelected && deps.selectedCodes.value.length !== 1)) {
      deps.actionError.value = '请选择一条科室记录后维护关联库房'
      return
    }
    activeDepartmentForWarehouses.value = row
    departmentWarehouseDialogOpen.value = true
    await loadDepartmentWarehouses()
  }

  async function loadDepartmentWarehouses() {
    const deptCode = activeDepartmentCode()
    if (!deptCode) {
      return
    }
    departmentWarehouseLoading.value = true
    try {
      const rows = await fetchDepartmentWarehouses(deptCode)
      departmentWarehouseRows.value = rows
      departmentWarehouseSelectedCodes.value = rows
        .filter((row) => row.selected === true || row.selected === 1)
        .map((row) => row.code)
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '关联库房加载失败'
    } finally {
      departmentWarehouseLoading.value = false
    }
  }

  function toggleDepartmentWarehouse(warehouseCode: string, checked: boolean) {
    const next = new Set(departmentWarehouseSelectedCodes.value)
    if (checked) {
      next.add(warehouseCode)
    } else {
      next.delete(warehouseCode)
    }
    departmentWarehouseSelectedCodes.value = [...next]
  }

  async function saveDepartmentWarehouses() {
    deps.clearActionState()
    const deptCode = activeDepartmentCode()
    if (!deptCode) {
      deps.actionError.value = '请选择科室后维护关联库房'
      return
    }
    departmentWarehouseSaving.value = true
    try {
      const result = await updateDepartmentWarehouses(deptCode, {
        warehouseCodes: departmentWarehouseSelectedCodes.value
      })
      deps.actionMessage.value = `已关联 ${result.warehouseCount} 个库房`
      await loadDepartmentWarehouses()
      await deps.loadPage()
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '关联库房保存失败'
    } finally {
      departmentWarehouseSaving.value = false
    }
  }

  function closeDepartmentWarehouseDialog() {
    departmentWarehouseDialogOpen.value = false
    activeDepartmentForWarehouses.value = null
    departmentWarehouseRows.value = []
    departmentWarehouseSelectedCodes.value = []
  }

  async function loadDepartmentWarehouseCatalogOptions() {
    try {
      departmentWarehouseCatalogOptions.value = await fetchClosureOptions()
    } catch {
      departmentWarehouseCatalogOptions.value = {
        departments: [],
        warehouses: [],
        products: [],
        balances: []
      }
    }
  }

  function clearDepartmentWarehouseCatalogProducts() {
    departmentWarehouseCatalogOptions.value = {
      ...departmentWarehouseCatalogOptions.value,
      products: []
    }
  }

  async function loadCatalogProductsForWarehouse(
    warehouseName: string,
    warehouses: DepartmentWarehouseRelation[],
    retainedProduct?: ClosureOptions['products'][number],
    keyword = ''
  ) {
    const warehouse = warehouses.find((item) => item.name === warehouseName)
    const deptName = departmentWarehouseCatalogForm.value.deptName || departmentWarehouseCatalogBatchForm.value.deptName
    if (!warehouse?.code || !deptName) {
      clearDepartmentWarehouseCatalogProducts()
      return
    }
    try {
      // 商品范围：医院目录内已绑定该库房、且未在该科室库房维护的商品（支持关键词搜索）
      const products = (await fetchDepartmentWarehouseCatalogProductOptions(deptName, warehouseName, keyword)).map(
        (product) => ({
          ...product,
          purchasePrice: 0
        })
      )
      if (retainedProduct && !products.some((product) => product.productCode === retainedProduct.productCode)) {
        products.push(retainedProduct)
      }
      departmentWarehouseCatalogOptions.value = {
        ...departmentWarehouseCatalogOptions.value,
        products
      }
    } catch (err) {
      clearDepartmentWarehouseCatalogProducts()
      deps.actionError.value = err instanceof Error ? err.message : '库房绑定商品加载失败'
    }
  }

  async function searchDepartmentWarehouseCatalogProducts(keyword = '') {
    await loadCatalogProductsForWarehouse(
      departmentWarehouseCatalogForm.value.warehouseName || departmentWarehouseCatalogBatchForm.value.warehouseName,
      departmentWarehouseCatalogBatchDialogOpen.value
        ? departmentWarehouseCatalogBatchWarehouses.value
        : departmentWarehouseCatalogWarehouses.value,
      undefined,
      keyword
    )
  }

  async function loadDepartmentWarehouseCatalogProducts(
    warehouseName = departmentWarehouseCatalogForm.value.warehouseName
  ) {
    departmentWarehouseCatalogForm.value.productCode = ''
    await loadCatalogProductsForWarehouse(warehouseName, departmentWarehouseCatalogWarehouses.value)
  }

  async function loadDepartmentWarehouseCatalogBatchProducts(
    warehouseName = departmentWarehouseCatalogBatchForm.value.warehouseName
  ) {
    departmentWarehouseCatalogBatchForm.value.productCodes = []
    await loadCatalogProductsForWarehouse(warehouseName, departmentWarehouseCatalogBatchWarehouses.value)
  }

  async function loadDepartmentWarehouseCatalogWarehouses(deptName = departmentWarehouseCatalogForm.value.deptName, resetProduct = true) {
    clearDepartmentWarehouseCatalogProducts()
    if (resetProduct) {
      departmentWarehouseCatalogForm.value.productCode = ''
    }
    const dept = departmentWarehouseCatalogOptions.value.departments.find((item) => item.deptName === deptName)
    if (!dept?.deptCode) {
      departmentWarehouseCatalogWarehouses.value = []
      departmentWarehouseCatalogForm.value.warehouseName = ''
      return
    }
    try {
      const rows = await fetchDepartmentWarehouses(dept.deptCode)
      departmentWarehouseCatalogWarehouses.value = rows.filter((row) => row.selected === true || row.selected === 1)
      if (
        departmentWarehouseCatalogForm.value.warehouseName &&
        !departmentWarehouseCatalogWarehouses.value.some((row) => row.name === departmentWarehouseCatalogForm.value.warehouseName)
      ) {
        departmentWarehouseCatalogForm.value.warehouseName = ''
      }
      // 关联库房直接带出科室库房关联关系：仅一个关联库房时自动选中并加载商品
      if (
        departmentWarehouseCatalogWarehouses.value.length === 1 &&
        !departmentWarehouseCatalogForm.value.warehouseName
      ) {
        departmentWarehouseCatalogForm.value.warehouseName = departmentWarehouseCatalogWarehouses.value[0].name
        await loadDepartmentWarehouseCatalogProducts()
      }
    } catch (err) {
      departmentWarehouseCatalogWarehouses.value = []
      deps.actionError.value = err instanceof Error ? err.message : '科室关联库房加载失败'
    }
  }

  async function openCreateDepartmentWarehouseCatalog() {
    deps.clearActionState()
    deps.departmentWarehouseCatalogDialogMode.value = 'create'
    departmentWarehouseCatalogForm.value = emptyDepartmentWarehouseCatalogForm()
    await loadDepartmentWarehouseCatalogOptions()
    clearDepartmentWarehouseCatalogProducts()
    departmentWarehouseCatalogWarehouses.value = []
    deps.departmentWarehouseCatalogDialogOpen.value = true
  }

  async function loadDepartmentWarehouseCatalogBatchWarehouses(
    deptName = departmentWarehouseCatalogBatchForm.value.deptName
  ) {
    clearDepartmentWarehouseCatalogProducts()
    departmentWarehouseCatalogBatchForm.value.productCodes = []
    const dept = departmentWarehouseCatalogOptions.value.departments.find((item) => item.deptName === deptName)
    if (!dept?.deptCode) {
      departmentWarehouseCatalogBatchWarehouses.value = []
      departmentWarehouseCatalogBatchForm.value.warehouseName = ''
      return
    }
    try {
      const rows = await fetchDepartmentWarehouses(dept.deptCode)
      departmentWarehouseCatalogBatchWarehouses.value = rows.filter((row) => row.selected === true || row.selected === 1)
      if (
        departmentWarehouseCatalogBatchForm.value.warehouseName &&
        !departmentWarehouseCatalogBatchWarehouses.value.some(
          (row) => row.name === departmentWarehouseCatalogBatchForm.value.warehouseName
        )
      ) {
        departmentWarehouseCatalogBatchForm.value.warehouseName = ''
      }
      // 关联库房直接带出：仅一个关联库房时自动选中并加载商品
      if (
        departmentWarehouseCatalogBatchWarehouses.value.length === 1 &&
        !departmentWarehouseCatalogBatchForm.value.warehouseName
      ) {
        departmentWarehouseCatalogBatchForm.value.warehouseName = departmentWarehouseCatalogBatchWarehouses.value[0].name
        await loadDepartmentWarehouseCatalogBatchProducts()
      }
    } catch (err) {
      departmentWarehouseCatalogBatchWarehouses.value = []
      deps.actionError.value = err instanceof Error ? err.message : '科室关联库房加载失败'
    }
  }

  async function openBatchDepartmentWarehouseCatalog() {
    deps.clearActionState()
    departmentWarehouseCatalogBatchForm.value = emptyDepartmentWarehouseCatalogBatchForm()
    departmentWarehouseCatalogBatchWarehouses.value = []
    await loadDepartmentWarehouseCatalogOptions()
    clearDepartmentWarehouseCatalogProducts()
    departmentWarehouseCatalogBatchDialogOpen.value = true
  }

  async function saveBatchDepartmentWarehouseCatalog() {
    deps.clearActionState()
    const form = departmentWarehouseCatalogBatchForm.value
    const payload: DepartmentWarehouseCatalogBatchPayload = {
      deptName: String(form.deptName ?? '').trim(),
      warehouseName: String(form.warehouseName ?? '').trim(),
      productCodes: [...new Set(form.productCodes.map((code) => String(code).trim()).filter(Boolean))],
      status: Number(form.status ?? 1) === 0 ? 0 : 1
    }
    if (!payload.deptName || !payload.warehouseName || !payload.productCodes.length) {
      deps.actionError.value = '科室、库房和至少一个商品为必填项'
      return
    }
    departmentWarehouseCatalogBatchSaving.value = true
    try {
      const result = await batchMaintainDepartmentWarehouseCatalogs(payload)
      deps.actionMessage.value = '批量维护完成：新增 ' + result.createdRows + ' 条，更新 ' + result.updatedRows + ' 条，恢复 ' + result.restoredRows + ' 条'
      departmentWarehouseCatalogBatchDialogOpen.value = false
      await deps.loadPage()
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '科室库房目录批量维护失败'
    } finally {
      departmentWarehouseCatalogBatchSaving.value = false
    }
  }

  async function openEditDepartmentWarehouseCatalog(
    row = deps.selectedDepartmentWarehouseCatalog.value,
    requireSelected = true
  ) {
    deps.clearActionState()
    if (!row || (requireSelected && deps.selectedCodes.value.length !== 1)) {
      deps.actionError.value = '请选择一条科室库房目录后再编辑'
      return
    }
    deps.departmentWarehouseCatalogDialogMode.value = 'edit'
    departmentWarehouseCatalogForm.value = {
      deptName: String(row.deptName ?? ''),
      warehouseName: String(row.warehouseName ?? ''),
      productCode: String(row.productCode ?? ''),
      status: String(row.status ?? '') === '停用' ? 0 : 1
    }
    await loadDepartmentWarehouseCatalogOptions()
    const retainedProduct = departmentWarehouseCatalogOptions.value.products.find(
      (product) => product.productCode === departmentWarehouseCatalogForm.value.productCode
    )
    await loadDepartmentWarehouseCatalogWarehouses(departmentWarehouseCatalogForm.value.deptName, false)
    await loadCatalogProductsForWarehouse(
      departmentWarehouseCatalogForm.value.warehouseName, departmentWarehouseCatalogWarehouses.value, retainedProduct)
    deps.departmentWarehouseCatalogDialogOpen.value = true
  }

  async function saveDepartmentWarehouseCatalog() {
    deps.clearActionState()
    const payload = normalizeDepartmentWarehouseCatalogPayload(departmentWarehouseCatalogForm.value)
    if (!payload.deptName || !payload.warehouseName || !payload.productCode) {
      deps.actionError.value = '科室、库房、商品为必填项'
      return
    }
    departmentWarehouseCatalogForm.value = payload
    try {
      if (deps.departmentWarehouseCatalogDialogMode.value === 'create') {
        await createDepartmentWarehouseCatalog(payload)
        deps.actionMessage.value = '科室库房目录已新增'
      } else {
        const catalogId = Number(deps.selectedDepartmentWarehouseCatalog.value?.code)
        await updateDepartmentWarehouseCatalog(catalogId, payload)
        deps.actionMessage.value = '科室库房目录已保存'
      }
      await deps.loadPage()
      deps.departmentWarehouseCatalogDialogOpen.value = false
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '科室库房目录保存失败'
    }
  }

  async function deleteSelectedDepartmentWarehouseCatalogs(catalogCodes = deps.selectedCodes.value) {
    deps.clearActionState()
    const catalogIds = catalogCodes.map((code) => Number(code)).filter((id) => Number.isFinite(id) && id > 0)
    if (!catalogIds.length) {
      deps.actionError.value = '请选择需要删除的科室库房目录'
      return
    }
    if (!window.confirm(`确认删除已选择的 ${catalogIds.length} 条科室库房目录吗？`)) {
      return
    }
    try {
      const result = await deleteDepartmentWarehouseCatalogs({ catalogIds })
      deps.actionMessage.value = `已删除 ${result.deletedRows} 条科室库房目录`
      await deps.loadPage()
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '科室库房目录删除失败'
    }
  }

  async function updateSelectedDepartmentWarehouseCatalogStatus(status: number, catalogCodes = deps.selectedCodes.value) {
    deps.clearActionState()
    const catalogIds = catalogCodes.map((code) => Number(code)).filter((id) => Number.isFinite(id) && id > 0)
    if (!catalogIds.length) {
      deps.actionError.value = '请选择需要操作的科室库房目录'
      return
    }
    try {
      const result = await updateDepartmentWarehouseCatalogStatus({ catalogIds, status })
      deps.actionMessage.value = `已${status === 1 ? '启用' : '停用'} ${result.updatedRows} 条科室库房目录`
      await deps.loadPage()
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '科室库房目录状态更新失败'
    }
  }

  async function loadWarehouseProductOptions() {
    warehouseProductOptionsLoading.value = true
    try {
      warehouseProductOptions.value = await fetchWarehouseProductOptions()
    } catch (err) {
      warehouseProductOptions.value = []
      deps.actionError.value = err instanceof Error ? err.message : '商品选项加载失败'
    } finally {
      warehouseProductOptionsLoading.value = false
    }
  }

  async function loadDepartmentOptions() {
    try {
      const options = await fetchClosureOptions()
      departmentOptions.value = options.departments
    } catch {
      departmentOptions.value = []
    }
  }

  async function openCreateWarehouse() {
    deps.clearActionState()
    deps.warehouseDialogMode.value = 'create'
    warehouseForm.value = emptyWarehouseForm()
    deps.warehouseDialogOpen.value = true
    await Promise.all([loadCampusOptions(), loadWarehouseProductOptions(), loadDepartmentOptions()])
  }

  async function openEditWarehouse(row = deps.selectedWarehouse.value, requireSelected = true) {
    deps.clearActionState()
    if (!row || (requireSelected && deps.selectedCodes.value.length !== 1)) {
      deps.actionError.value = '请选择一条库房记录后再编辑'
      return
    }

    deps.warehouseDialogMode.value = 'edit'
    const warehouseCode = String(row.code ?? '')
    warehouseForm.value = {
      warehouseCode,
      warehouseName: String(row.name ?? ''),
      warehouseType: String(row.type ?? ''),
      campusName: String(row.campus ?? ''),
      deptName: String(row.dept === '-' ? '' : row.dept ?? ''),
      deptCode: String(row.deptCode ?? ''),
      participateStats: String(row.participateStats ?? '') !== '不参与',
      receivingEnabled: Boolean(row.receivingEnabled),
      statsCategories: String(row.statsCategories === '-' ? '' : row.statsCategories ?? '').replace(/^\["|"\]$/g, ''),
      status: String(row.status ?? '') === '停用' ? 0 : 1,
      productCodes: []
    }
    deps.warehouseDialogOpen.value = true
    try {
      const [, products] = await Promise.all([
        Promise.all([loadCampusOptions(), loadWarehouseProductOptions(), loadDepartmentOptions()]),
        fetchWarehouseProducts(warehouseCode)
      ])
      warehouseForm.value.productCodes = products.map((product) => product.productCode)
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '已绑定商品加载失败'
    }
  }

  async function saveWarehouse() {
    deps.clearActionState()
    warehouseForm.value.productCodes = [...new Set(
      warehouseForm.value.productCodes.map((code) => String(code).trim()).filter(Boolean)
    )]
    const requiresDepartment = ['二级库', '三级库', '科室库']
      .some((type) => warehouseForm.value.warehouseType.includes(type))
    if (requiresDepartment && !warehouseForm.value.deptCode) {
      deps.actionError.value = '二级库、三级库和科室库必须从有效科室列表中选择关联科室'
      return
    }
    warehouseForm.value.deptName = String(warehouseForm.value.deptName ?? '').trim()
    warehouseForm.value.deptCode = String(warehouseForm.value.deptCode ?? '').trim()
    try {
      if (deps.warehouseDialogMode.value === 'create') {
        await createWarehouse(warehouseForm.value)
        deps.actionMessage.value = '库房已新增'
      } else {
        await updateWarehouse(warehouseForm.value.warehouseCode, warehouseForm.value)
        deps.actionMessage.value = '库房已编辑'
      }
      await deps.loadPage()
      deps.warehouseDialogOpen.value = false
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '库房保存失败'
    }
  }

  async function deleteSelectedWarehouses(warehouseCodes = deps.selectedCodes.value) {
    deps.clearActionState()
    if (!warehouseCodes.length) {
      deps.actionError.value = '请选择需要删除的库房'
      return
    }
    if (!window.confirm(`确认删除已选择的 ${warehouseCodes.length} 条库房吗？`)) {
      return
    }

    try {
      const result = await deleteWarehouses({ warehouseCodes })
      deps.actionMessage.value = `已删除 ${result.deletedRows} 条库房`
      await deps.loadPage()
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '库房删除失败'
    }
  }

  async function openWarehouseLocationDialog(row = deps.selectedWarehouse.value, requireSelected = true) {
    deps.clearActionState()
    if (!row || (requireSelected && deps.selectedCodes.value.length !== 1)) {
      deps.actionError.value = '请选择一条库房记录后维护货位'
      return
    }
    activeWarehouseForLocations.value = row
    warehouseLocationDialogOpen.value = true
    resetWarehouseLocationForm()
    // 固定商品编码搜索范围为医院目录：打开弹窗时确保商品选项已加载
    await Promise.all([loadWarehouseLocations(), loadWarehouseProductOptions()])
  }

  async function loadWarehouseLocations() {
    const warehouseCode = activeWarehouseCode()
    if (!warehouseCode) {
      return
    }
    warehouseLocationLoading.value = true
    try {
      warehouseLocationRows.value = await fetchWarehouseLocations(warehouseCode)
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '货位加载失败'
    } finally {
      warehouseLocationLoading.value = false
    }
  }

  function startCreateWarehouseLocation() {
    deps.clearActionState()
    resetWarehouseLocationForm()
  }

  function startEditWarehouseLocation(row: WarehouseLocation) {
    deps.clearActionState()
    warehouseLocationEditingId.value = Number(row.locationId)
    warehouseLocationForm.value = {
      locationCode: String(row.locationCode ?? ''),
      locationType: String(row.locationType ?? ''),
      capacityLimit: row.capacityLimit ?? '',
      productCode: String(row.productCode ?? ''),
      status: Number(row.status ?? 1)
    }
  }

  async function saveWarehouseLocation() {
    deps.clearActionState()
    const warehouseCode = activeWarehouseCode()
    const payload = normalizeWarehouseLocationPayload(warehouseLocationForm.value)
    if (!warehouseCode) {
      deps.actionError.value = '请选择库房后维护货位'
      return
    }
    if (!payload.locationCode || !payload.locationType) {
      deps.actionError.value = '货位编码、货位类型为必填项'
      return
    }
    warehouseLocationSaving.value = true
    try {
      if (warehouseLocationEditingId.value == null) {
        await createWarehouseLocation(warehouseCode, payload)
        deps.actionMessage.value = '货位已新增'
      } else {
        await updateWarehouseLocation(warehouseCode, warehouseLocationEditingId.value, payload)
        deps.actionMessage.value = '货位已保存'
      }
      resetWarehouseLocationForm()
      await loadWarehouseLocations()
      await deps.loadPage()
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '货位保存失败'
    } finally {
      warehouseLocationSaving.value = false
    }
  }

  async function removeWarehouseLocation(row: WarehouseLocation) {
    deps.clearActionState()
    const warehouseCode = activeWarehouseCode()
    if (!warehouseCode) {
      deps.actionError.value = '请选择库房后维护货位'
      return
    }
    if (!window.confirm(`确认删除货位 ${row.locationCode} 吗？`)) {
      return
    }
    try {
      const result = await deleteWarehouseLocation(warehouseCode, Number(row.locationId))
      deps.actionMessage.value = `已删除 ${result.deletedRows} 条货位`
      await loadWarehouseLocations()
      await deps.loadPage()
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '货位删除失败'
    }
  }

  function closeWarehouseLocationDialog() {
    warehouseLocationDialogOpen.value = false
    activeWarehouseForLocations.value = null
    warehouseLocationRows.value = []
    resetWarehouseLocationForm()
  }



  function activeWarehouseCode() {
    return String(activeWarehouseForLocations.value?.code ?? '').trim()
  }

  function activeDepartmentCode() {
    return String(activeDepartmentForWarehouses.value?.code ?? '').trim()
  }

  function resetWarehouseLocationForm() {
    warehouseLocationEditingId.value = null
    warehouseLocationForm.value = emptyWarehouseLocationForm()
  }

  function showWarehouseLocationMessage() {
    deps.clearActionState()
    deps.actionMessage.value = deps.selectedCodes.value.length
      ? `已选择 ${deps.selectedCodes.value.length} 条库房，可继续扩展货位维护明细`
      : '请选择库房后维护货位'
  }

  return {
    campusForm,
    campusOptions,
    departmentForm,
    warehouseForm,
    departmentWarehouseDialogOpen,
    departmentWarehouseLoading,
    departmentWarehouseSaving,
    departmentWarehouseRows,
    departmentWarehouseSelectedCodes,
    activeDepartmentForWarehouses,
    departmentWarehouseCatalogForm,
    departmentWarehouseCatalogBatchForm,
    departmentWarehouseCatalogBatchDialogOpen,
    departmentWarehouseCatalogBatchSaving,
    departmentWarehouseCatalogBatchWarehouses,
    departmentWarehouseCatalogOptions,
    departmentWarehouseCatalogWarehouses,
    warehouseLocationDialogOpen,
    warehouseLocationLoading,
    warehouseLocationSaving,
    warehouseLocationRows,
    warehouseLocationForm,
    warehouseLocationEditingId,
    warehouseProductOptions,
    warehouseProductOptionsLoading,
    departmentOptions,
    activeWarehouseForLocations,
    loadCampusOptions,
    openCreateCampus,
    openEditCampus,
    saveCampus,
    deleteSelectedCampuses,
    openCreateDepartment,
    openEditDepartment,
    saveDepartment,
    deleteSelectedDepartments,
    openDepartmentWarehouseDialog,
    closeDepartmentWarehouseDialog,
    loadDepartmentWarehouses,
    toggleDepartmentWarehouse,
    saveDepartmentWarehouses,
    loadDepartmentWarehouseCatalogWarehouses,
    loadDepartmentWarehouseCatalogProducts,
    searchDepartmentWarehouseCatalogProducts,
    loadDepartmentWarehouseCatalogBatchProducts,
    loadDepartmentWarehouseCatalogBatchWarehouses,
    openCreateDepartmentWarehouseCatalog,
    openBatchDepartmentWarehouseCatalog,
    saveBatchDepartmentWarehouseCatalog,
    openEditDepartmentWarehouseCatalog,
    saveDepartmentWarehouseCatalog,
    deleteSelectedDepartmentWarehouseCatalogs,
    updateSelectedDepartmentWarehouseCatalogStatus,
    openCreateWarehouse,
    openEditWarehouse,
    saveWarehouse,
    deleteSelectedWarehouses,
    openWarehouseLocationDialog,
    closeWarehouseLocationDialog,
    loadWarehouseLocations,
    startCreateWarehouseLocation,
    startEditWarehouseLocation,
    saveWarehouseLocation,
    removeWarehouseLocation
  }
}

function emptyCampusForm(): CampusPayload {
  return {
    campusCode: '',
    campusName: '',
    address: '',
    managerName: '',
    phone: '',
    sortOrder: 0,
    status: 1
  }
}

function normalizeCampusPayload(form: CampusPayload): CampusPayload {
  return {
    campusCode: String(form.campusCode ?? '').trim(),
    campusName: String(form.campusName ?? '').trim(),
    address: String(form.address ?? '').trim(),
    managerName: String(form.managerName ?? '').trim(),
    phone: String(form.phone ?? '').trim(),
    sortOrder: Number(form.sortOrder) || 0,
    status: Number(form.status ?? 1)
  }
}

function emptyDepartmentForm(): DepartmentPayload {
  return {
    deptCode: '',
    deptName: '',
    financeDeptCode: '',
    financeDeptName: '',
    campusName: '',
    address: '',
    managerName: '',
    phone: '',
    sortOrder: 0,
    status: 1
  }
}

function normalizeDepartmentPayload(form: DepartmentPayload): DepartmentPayload {
  return {
    ...form,
    deptCode: String(form.deptCode ?? '').trim(),
    deptName: String(form.deptName ?? '').trim(),
    financeDeptCode: String(form.financeDeptCode ?? '').trim(),
    financeDeptName: String(form.financeDeptName ?? '').trim(),
    campusName: String(form.campusName ?? '').trim(),
    address: String(form.address ?? '').trim(),
    managerName: String(form.managerName ?? '').trim(),
    phone: String(form.phone ?? '').trim(),
    sortOrder: Number(form.sortOrder) || 0,
    status: Number(form.status ?? 1)
  }
}

function emptyWarehouseForm(): WarehousePayload {
  return {
    warehouseCode: '',
    warehouseName: '',
    warehouseType: '',
    campusName: '',
    deptName: '',
    participateStats: true,
    receivingEnabled: false,
    deptCode: '',
    statsCategories: '',
    status: 1,
    productCodes: []
  }
}

function emptyDepartmentWarehouseCatalogBatchForm(): DepartmentWarehouseCatalogBatchPayload {
  return {
    deptName: '',
    warehouseName: '',
    productCodes: [],
    status: 1
  }
}

function emptyDepartmentWarehouseCatalogForm(): DepartmentWarehouseCatalogPayload {
  return {
    deptName: '',
    warehouseName: '',
    productCode: '',
    status: 1
  }
}

function normalizeDepartmentWarehouseCatalogPayload(
  form: DepartmentWarehouseCatalogPayload
): DepartmentWarehouseCatalogPayload {
  return {
    deptName: String(form.deptName ?? '').trim(),
    warehouseName: String(form.warehouseName ?? '').trim(),
    productCode: String(form.productCode ?? '').trim(),
    status: Number(form.status ?? 1) === 0 ? 0 : 1
  }
}

function emptyWarehouseLocationForm(): WarehouseLocationPayload {
  return {
    locationCode: '',
    locationType: '',
    capacityLimit: '',
    productCode: '',
    status: 1
  }
}

function normalizeWarehouseLocationPayload(form: WarehouseLocationPayload): WarehouseLocationPayload {
  const capacityText = String(form.capacityLimit ?? '').trim()
  return {
    locationCode: String(form.locationCode ?? '').trim(),
    locationType: String(form.locationType ?? '').trim(),
    capacityLimit: capacityText ? Number(capacityText) : null,
    productCode: String(form.productCode ?? '').trim(),
    status: Number(form.status ?? 1)
  }
}
