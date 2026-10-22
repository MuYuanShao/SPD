import { computed, nextTick, onActivated, onDeactivated, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import Sortable from 'sortablejs'
import { useCatalogApprovalRefreshStore } from '../stores/catalogApprovalRefresh'
import {
  fetchMasterDataPage,
  type ColumnConfig,
  type MasterDataPage,
  type MasterDataQuery
} from '../api/masterData'

const PARTNER_COMBINED_CODE = 'supplier-manufacturer-management'

export function useMasterDataWorkbench() {
  const route = useRoute()
  const router = useRouter()
  const refreshStore = useCatalogApprovalRefreshStore()
  const active = ref(true)
  let pageRequestVersion = 0
  let needsReload = true
  let loadedHospitalVersion = -1
  let hospitalScrollTop = 0
  let hospitalScrollLeft = 0
  const page = ref<MasterDataPage | null>(null)
  const loading = ref(false)
  const error = ref('')
  const actionError = ref('')
  const actionMessage = ref('')
  const selectedCodes = ref<string[]>([])
  const hospitalColumnConfigs = ref<ColumnConfig[]>([])
  const hospitalCurrentPage = ref(1)
  const hospitalPageSize = ref(20)
  const hospitalPageJumpInput = ref('1')
  const catalogCurrentPage = ref(1)
  const catalogPageSize = ref(15)
  const catalogPageJumpInput = ref('1')
  const partnerCurrentPage = ref(1)
  const partnerPageSize = ref(20)
  // 院区 / 科室 / 库房货位列表分页状态（后端分页，前端只维护页码）
  const listCurrentPage = ref(1)
  const listPageSize = ref(20)
  const tableRef = ref<any>(null)
  const columnSettingsOpen = ref(false)
  const importInput = ref<HTMLInputElement | null>(null)
  const manufacturerImportInput = ref<HTMLInputElement | null>(null)
  const departmentImportInput = ref<HTMLInputElement | null>(null)
  const warehouseImportInput = ref<HTMLInputElement | null>(null)
  const supplierDialogOpen = ref(false)
  const supplierDialogMode = ref<'create' | 'edit'>('create')
  const productDialogOpen = ref(false)
  const productDialogMode = ref<'create' | 'edit'>('create')
  const batchProductDialogOpen = ref(false)
  const manufacturerDialogOpen = ref(false)
  const manufacturerDialogMode = ref<'create' | 'edit'>('create')
  const campusDialogOpen = ref(false)
  const campusDialogMode = ref<'create' | 'edit'>('create')
  const departmentDialogOpen = ref(false)
  const departmentDialogMode = ref<'create' | 'edit'>('create')
  const departmentWarehouseCatalogDialogOpen = ref(false)
  const departmentWarehouseCatalogDialogMode = ref<'create' | 'edit'>('create')
  const warehouseDialogOpen = ref(false)
  const warehouseDialogMode = ref<'create' | 'edit'>('create')

  const hospitalQuery = ref({
    productCode: '',
    productName: '',
    specModel: '',
    model: '',
    manufacturerName: '',
    supplierName: '',
    price: '',
    isCentralized: '',
    isDomestic: '',
    tenderCode: ''
  })

  const supplierQuery = ref({
    supplierName: '',
    supplierType: '',
    status: ''
  })

  const manufacturerQuery = ref({
    manufacturerName: '',
    licenseNo: '',
    status: ''
  })

  const campusQuery = ref({
    campusCode: '',
    campusName: '',
    status: ''
  })

  const departmentQuery = ref({
    deptKeyword: '',
    deptType: '',
    status: '',
    sourceFlag: '',
    warehouseName: ''
  })

  const warehouseQuery = ref({
    warehouseKeyword: '',
    warehouseType: '',
    campusName: '',
    deptName: '',
    status: ''
  })

  const departmentWarehouseCatalogQuery = ref({
    deptName: '',
    warehouseName: '',
    productKeyword: '',
    status: ''
  })

  const routeCode = ref(String(route.params.code ?? ''))
  watch(() => route.fullPath, () => {
    if (route.name === 'master-data-list') routeCode.value = String(route.params.code ?? '')
  })
  const partnerTab = ref<'supplier' | 'manufacturer'>(route.query.tab === 'manufacturer' ? 'manufacturer' : 'supplier')
  const isPartnerCombined = computed(() => routeCode.value === PARTNER_COMBINED_CODE)
  const code = computed(() => {
    if (routeCode.value === PARTNER_COMBINED_CODE) {
      return partnerTab.value === 'manufacturer' ? 'manufacturer-management' : 'supplier-management'
    }
    return routeCode.value
  })
  const isHospitalCatalog = computed(() => code.value === 'hospital-product-catalog')
  const isSupplierManagement = computed(() => code.value === 'supplier-management')
  const isManufacturerManagement = computed(() => code.value === 'manufacturer-management')
  const isCampusManagement = computed(() => code.value === 'campus-management')
  const isDepartmentManagement = computed(() => code.value === 'department-management')
  const isDepartmentWarehouseCatalog = computed(() => code.value === 'department-warehouse-catalog')
  const isWarehouseManagement = computed(() => code.value === 'warehouse-location-management')
  const selectableRows = computed(
    () =>
      isHospitalCatalog.value ||
      isSupplierManagement.value ||
      isManufacturerManagement.value ||
      isCampusManagement.value ||
      isDepartmentManagement.value ||
      isDepartmentWarehouseCatalog.value ||
      isWarehouseManagement.value
  )
  const rowKeys = computed(() => {
    const first = page.value?.rows[0]
    if (!first) return []
    const keys = Object.keys(first)
    return isDepartmentWarehouseCatalog.value ? keys.filter((key) => key !== 'code') : keys
  })
  const queryParams = computed<MasterDataQuery>(() => {
    const source = isHospitalCatalog.value
      ? hospitalQuery.value
      : isSupplierManagement.value
        ? supplierQuery.value
        : isManufacturerManagement.value
          ? manufacturerQuery.value
          : isCampusManagement.value
            ? campusQuery.value
            : isDepartmentManagement.value
              ? departmentQuery.value
              : isDepartmentWarehouseCatalog.value
                ? departmentWarehouseCatalogQuery.value
                : isWarehouseManagement.value
                  ? warehouseQuery.value
                  : {}

    const params = Object.fromEntries(
      Object.entries(source).filter(([, value]) => String(value).trim() !== '')
    ) as MasterDataQuery
    if (isHospitalCatalog.value) {
      params.page = String(hospitalCurrentPage.value)
      params.size = String(hospitalPageSize.value)
    } else if (isDepartmentWarehouseCatalog.value) {
      params.page = String(catalogCurrentPage.value)
      params.size = String(catalogPageSize.value)
    } else if (isSupplierManagement.value || isManufacturerManagement.value) {
      params.page = String(partnerCurrentPage.value)
      params.size = String(partnerPageSize.value)
    } else if (isCampusManagement.value || isDepartmentManagement.value || isWarehouseManagement.value) {
      params.page = String(listCurrentPage.value)
      params.size = String(listPageSize.value)
    }
    return params
  })

  const allVisibleSelected = computed(() => {
    const rows = page.value?.rows ?? []
    return rows.length > 0 && rows.every((row) => selectedCodes.value.includes(String(row.code)))
  })

  const selectedSupplier = computed(() => selectedRow())
  const selectedManufacturer = computed(() => selectedRow())
  const selectedCampus = computed(() => selectedRow())
  const selectedDepartment = computed(() => selectedRow())
  const selectedDepartmentWarehouseCatalog = computed(() => selectedRow())
  const selectedWarehouse = computed(() => selectedRow())

  const sortedColumns = computed(() =>
    [...hospitalColumnConfigs.value]
      .filter((column) => column.visible)
      .sort((a, b) => a.order - b.order)
  )
  const hospitalTotal = computed(() => Number(page.value?.total ?? 0))
  const hospitalPageTotal = computed(() =>
    Math.max(1, Math.ceil(hospitalTotal.value / Math.max(1, hospitalPageSize.value)))
  )
  const hospitalPageSizeOptions = [10, 20, 50, 100, 200]
  const hospitalStats = computed(() => {
    const summary = (page.value?.summary ?? {}) as Record<string, unknown>
    return {
      scopeTotal: Number(summary.scopeTotal ?? summary.total ?? hospitalTotal.value ?? 0),
      centralizedTotal: Number(summary.centralizedTotal ?? 0),
      domesticTotal: Number(summary.domesticTotal ?? 0),
      disabledTotal: Number(summary.disabledTotal ?? 0)
    }
  })
  const catalogTotal = computed(() => Number(page.value?.total ?? 0))
  const catalogPageTotal = computed(() =>
    Math.max(1, Math.ceil(catalogTotal.value / Math.max(1, catalogPageSize.value)))
  )
  const catalogPageSizeOptions = [15, 30, 50, 100, 200]
  const partnerTotal = computed(() => Number(page.value?.total ?? 0))
  const partnerPageSizeOptions = [10, 20, 50, 100, 200]

  /**
   * 非医院目录页面的统计：直接从当前页 rows 算（避免后端再开 summary）。
   * 字段 status 形如 "启用" / "停用"。
   */
  const partnerStats = computed(() => {
    const rows = (page.value?.rows ?? []) as Array<Record<string, unknown>>
    return {
      total: rows.length,
      enabled: rows.filter((row) => row.status === '启用' || row.status === 1 || row.status === '1').length,
      disabled: rows.filter((row) => row.status === '停用' || row.status === 0 || row.status === '0').length,
      selected: selectedCodes.value.length
    }
  })

  const hospitalColumnPropMap: Record<string, string> = {
    商品编码: 'code',
    商品名称: 'name',
    规格型号: 'spec',
    品牌: 'brand',
    厂家: 'manufacturer',
    供应商: 'supplier',
    是否定数管理: 'quotaManaged',
    是否定数: 'quotaManaged',
    注册证号: 'registrationNo',
    合同编码: 'contractCode',
    一级分类: 'firstCategory',
    二级分类: 'secondCategory',
    三级分类: 'thirdCategory',
    是否带量: 'volumeBased',
    是否集采: 'centralized',
    是否国产: 'domestic',
    是否收费: 'chargeable',
    重点监控: 'keyMonitored',
    招采子编码: 'tenderSubCode',
    单位: 'unit',
    采购价: 'price',
    单价: 'price',
    状态: 'status'
  }

  async function loadPage() {
    if (!active.value || route.name !== 'master-data-list') { needsReload = true; return }
    const request = ++pageRequestVersion
    const expectedCode = code.value
    const hospitalVersion = refreshStore.hospitalCatalogVersion
    if (isHospitalCatalog.value) {
      hospitalCurrentPage.value = clampHospitalPage(hospitalCurrentPage.value)
      hospitalPageJumpInput.value = String(hospitalCurrentPage.value)
    } else if (isDepartmentWarehouseCatalog.value) {
      catalogCurrentPage.value = clampCatalogPage(catalogCurrentPage.value)
      catalogPageJumpInput.value = String(catalogCurrentPage.value)
    }
    loading.value = true
    error.value = ''
    actionError.value = ''
    page.value = null

    try {
      const response = await fetchMasterDataPage(expectedCode, queryParams.value)
      if (request !== pageRequestVersion || !active.value || expectedCode !== code.value) return
      page.value = response
      needsReload = false
      if (isHospitalCatalog.value) loadedHospitalVersion = hospitalVersion
      selectedCodes.value = []
      if (isHospitalCatalog.value) {
        hospitalCurrentPage.value = Number(page.value.page || hospitalCurrentPage.value)
        hospitalPageSize.value = Number(page.value.size || hospitalPageSize.value)
        hospitalPageJumpInput.value = String(hospitalCurrentPage.value)
        buildColumnConfigs()
      } else if (isDepartmentWarehouseCatalog.value) {
        catalogCurrentPage.value = Number(page.value.page || catalogCurrentPage.value)
        catalogPageSize.value = Number(page.value.size || catalogPageSize.value)
        catalogPageJumpInput.value = String(catalogCurrentPage.value)
      }
    } catch (err) {
      if (request !== pageRequestVersion || !active.value) return
      needsReload = true
      error.value = err instanceof Error ? err.message : '数据加载失败'
    } finally {
      if (request === pageRequestVersion) loading.value = false
    }
  }

  function buildColumnConfigs() {
    if (!page.value?.rows.length) return
    const keys = Object.keys(page.value.rows[0])
    const previous = new Map(hospitalColumnConfigs.value.map(column => [column.prop, column]))
    hospitalColumnConfigs.value = page.value.columns.map((label, index) => ({
      prop: hospitalColumnPropMap[label] || keys[index] || `col_${index}`,
      label,
      visible: true,
      order: index,
      width: Math.max(132, label.length * 28),
      ...previous.get(hospitalColumnPropMap[label] || keys[index] || `col_${index}`)
    }))
  }

  function initColumnDrag() {
    nextTick(() => {
      const el = tableRef.value?.$el as HTMLElement | undefined
      if (!el) return
      const headerRow = el.querySelector('.el-table__header-wrapper thead tr') as HTMLElement | null
      if (!headerRow || (headerRow as any).__sortableInitialized) return
      ;(headerRow as any).__sortableInitialized = true

      Sortable.create(headerRow, {
        draggable: 'th:not(.el-table-column--selection)',
        animation: 200,
        onEnd: (evt) => {
          const oldIdx = evt.oldIndex
          const newIdx = evt.newIndex
          if (oldIdx === undefined || newIdx === undefined || oldIdx === newIdx) return
          const adjOld = oldIdx - 1
          const adjNew = newIdx - 1
          if (adjOld < 0 || adjNew < 0) return
          const arr = [...hospitalColumnConfigs.value]
          const [moved] = arr.splice(adjOld, 1)
          arr.splice(adjNew, 0, moved)
          hospitalColumnConfigs.value = arr.map((column, index) => ({ ...column, order: index }))
        }
      })
    })
  }

  function resetHospitalQuery() {
    resetQuery(hospitalQuery.value)
    hospitalCurrentPage.value = 1
    hospitalPageJumpInput.value = '1'
    loadPage()
  }

  function submitHospitalQuery() {
    hospitalCurrentPage.value = 1
    hospitalPageJumpInput.value = '1'
    loadPage()
  }

  function loadHospitalPage(nextPage: number) {
    hospitalCurrentPage.value = clampHospitalPage(nextPage)
    hospitalPageJumpInput.value = String(hospitalCurrentPage.value)
    loadPage()
  }

  /**
   * 切换每页条数。
   * 修复：原实现靠 v-model.number + @change 同元素双绑定存在竞争，
   *     且 loadPage 完成后会用响应 size 覆盖本地 ref，导致切换后回显错乱。
   *     改为：调用方传入已选 size，这里只做夹紧 + 重置当前页 + 触发加载。
   */
  function changeHospitalPageSize(nextSize: number) {
    const sizeNumber = Math.floor(Number(nextSize) || 20)
    const clamped = Math.min(200, Math.max(1, sizeNumber))
    if (clamped === hospitalPageSize.value && hospitalCurrentPage.value === 1) {
      return
    }
    hospitalPageSize.value = clamped
    hospitalCurrentPage.value = 1
    hospitalPageJumpInput.value = '1'
    loadPage()
  }

  function submitHospitalPageJump() {
    loadHospitalPage(Number(hospitalPageJumpInput.value) || 1)
  }

  function submitDepartmentWarehouseCatalogQuery() {
    catalogCurrentPage.value = 1
    catalogPageJumpInput.value = '1'
    loadPage()
  }

  function resetSupplierQuery() {
    resetQuery(supplierQuery.value)
    loadPage()
  }

  function resetManufacturerQuery() {
    resetQuery(manufacturerQuery.value)
    loadPage()
  }

  function resetCampusQuery() {
    resetQuery(campusQuery.value)
    listCurrentPage.value = 1
    loadPage()
  }

  function resetDepartmentQuery() {
    resetQuery(departmentQuery.value)
    listCurrentPage.value = 1
    loadPage()
  }

  function resetDepartmentWarehouseCatalogQuery() {
    resetQuery(departmentWarehouseCatalogQuery.value)
    catalogCurrentPage.value = 1
    catalogPageJumpInput.value = '1'
    loadPage()
  }

  function loadDepartmentWarehouseCatalogPage(nextPage: number) {
    catalogCurrentPage.value = clampCatalogPage(nextPage)
    catalogPageJumpInput.value = String(catalogCurrentPage.value)
    loadPage()
  }

  function changeDepartmentWarehouseCatalogPageSize(nextSize: number) {
    const sizeNumber = Math.floor(Number(nextSize) || 15)
    const clamped = Math.min(200, Math.max(1, sizeNumber))
    if (clamped === catalogPageSize.value && catalogCurrentPage.value === 1) {
      return
    }
    catalogPageSize.value = clamped
    catalogCurrentPage.value = 1
    catalogPageJumpInput.value = '1'
    loadPage()
  }

  function submitDepartmentWarehouseCatalogPageJump() {
    loadDepartmentWarehouseCatalogPage(Number(catalogPageJumpInput.value) || 1)
  }

  function resetWarehouseQuery() {
    resetQuery(warehouseQuery.value)
    loadPage()
  }

  function toggleAllRows() {
    const rows = page.value?.rows ?? []
    selectedCodes.value = allVisibleSelected.value ? [] : rows.map((row) => String(row.code))
  }

  function clearActionState() {
    actionError.value = ''
    actionMessage.value = ''
  }

  function selectedRow() {
    return (page.value?.rows ?? []).find((row) => String(row.code) === selectedCodes.value[0])
  }

  function clampHospitalPage(value: number) {
    const pageNumber = Math.floor(Number(value) || 1)
    return Math.min(hospitalPageTotal.value, Math.max(1, pageNumber))
  }

  function clampCatalogPage(value: number) {
    const pageNumber = Math.floor(Number(value) || 1)
    return Math.min(catalogPageTotal.value, Math.max(1, pageNumber))
  }

  onMounted(loadPage)
  onBeforeRouteLeave(() => {
    if (!isHospitalCatalog.value) return
    hospitalScrollTop = document.querySelector('.spd-content')?.scrollTop ?? 0
    hospitalScrollLeft = document.querySelector('.md-hospital-table-wrap')?.scrollLeft ?? 0
  })
  onActivated(async () => {
    active.value = true
    if (!loading.value && (needsReload || (isHospitalCatalog.value && loadedHospitalVersion < refreshStore.hospitalCatalogVersion))) await loadPage()
    await nextTick()
    if (!active.value || route.name !== 'master-data-list' || !isHospitalCatalog.value) return
    const content = document.querySelector('.spd-content')
    const table = document.querySelector('.md-hospital-table-wrap')
    if (content) content.scrollTop = hospitalScrollTop
    if (table) table.scrollLeft = hospitalScrollLeft
  })
  onDeactivated(() => {
    active.value = false
    pageRequestVersion++
    if (loading.value) needsReload = true
    loading.value = false
  })
  watch(() => refreshStore.hospitalCatalogVersion, () => {
    if (!isHospitalCatalog.value) return
    needsReload = true
    if (active.value && route.name === 'master-data-list') void loadPage()
  })
  watch(code, () => {
    hospitalCurrentPage.value = 1
    hospitalPageJumpInput.value = '1'
    catalogCurrentPage.value = 1
    catalogPageJumpInput.value = '1'
    partnerCurrentPage.value = 1
    loadPage()
  })
  watch(
    () => route.query.tab,
    (tab) => {
      if (route.name !== 'master-data-list') return
      const next = tab === 'manufacturer' ? 'manufacturer' : 'supplier'
      if (next !== partnerTab.value) {
        partnerTab.value = next
      }
    }
  )
  watch(page, () => {
    if (isHospitalCatalog.value) {
      initColumnDrag()
    }
  })

  function switchPartnerTab(tab: 'supplier' | 'manufacturer') {
    if (partnerTab.value === tab) return
    partnerTab.value = tab
    void router.replace({ query: { ...route.query, tab } })
  }

  function changePartnerPage(pageNumber: number) {
    partnerCurrentPage.value = Math.max(1, Math.floor(Number(pageNumber) || 1))
    loadPage()
  }

  function changePartnerPageSize(size: number) {
    partnerPageSize.value = Math.max(1, Math.floor(Number(size) || 20))
    partnerCurrentPage.value = 1
    loadPage()
  }

  const listTotal = computed(() => Number(page.value?.total ?? 0))
  const listTotalPages = computed(() =>
    Math.max(1, Math.ceil(listTotal.value / Math.max(1, listPageSize.value)))
  )

  function changeListPage(pageNumber: number) {
    listCurrentPage.value = Math.max(1, Math.floor(Number(pageNumber) || 1))
    loadPage()
  }

  function changeListPageSize(size: number) {
    listPageSize.value = Math.max(1, Math.floor(Number(size) || 20))
    listCurrentPage.value = 1
    loadPage()
  }

  return {
    page,
    loading,
    error,
    actionError,
    actionMessage,
    selectedCodes,
    hospitalColumnConfigs,
    hospitalCurrentPage,
    hospitalPageSize,
    hospitalPageJumpInput,
    catalogCurrentPage,
    catalogPageSize,
    catalogPageJumpInput,
    hospitalTotal,
    hospitalPageTotal,
    hospitalPageSizeOptions,
    hospitalStats,
    catalogTotal,
    catalogPageTotal,
    catalogPageSizeOptions,
    partnerTotal,
    tableRef,
    columnSettingsOpen,
    importInput,
    manufacturerImportInput,
    departmentImportInput,
    warehouseImportInput,
    supplierDialogOpen,
    supplierDialogMode,
    productDialogOpen,
    productDialogMode,
    batchProductDialogOpen,
    manufacturerDialogOpen,
    manufacturerDialogMode,
    campusDialogOpen,
    campusDialogMode,
    departmentDialogOpen,
    departmentDialogMode,
    departmentWarehouseCatalogDialogOpen,
    departmentWarehouseCatalogDialogMode,
    warehouseDialogOpen,
    warehouseDialogMode,
    hospitalQuery,
    supplierQuery,
    manufacturerQuery,
    campusQuery,
    departmentQuery,
    departmentWarehouseCatalogQuery,
    warehouseQuery,
    code,
    isHospitalCatalog,
    isPartnerCombined,
    partnerTab,
    switchPartnerTab,
    partnerCurrentPage,
    partnerPageSize,
    partnerPageSizeOptions,
    partnerStats,
    changePartnerPage,
    changePartnerPageSize,
    listCurrentPage,
    listPageSize,
    listTotal,
    listTotalPages,
    changeListPage,
    changeListPageSize,
    isSupplierManagement,
    isManufacturerManagement,
    isCampusManagement,
    isDepartmentManagement,
    isDepartmentWarehouseCatalog,
    isWarehouseManagement,
    selectableRows,
    rowKeys,
    queryParams,
    allVisibleSelected,
    selectedSupplier,
    selectedManufacturer,
    selectedCampus,
    selectedDepartment,
    selectedDepartmentWarehouseCatalog,
    selectedWarehouse,
    sortedColumns,
    submitHospitalQuery,
    loadHospitalPage,
    changeHospitalPageSize,
    submitHospitalPageJump,
    submitDepartmentWarehouseCatalogQuery,
    loadDepartmentWarehouseCatalogPage,
    changeDepartmentWarehouseCatalogPageSize,
    submitDepartmentWarehouseCatalogPageJump,
    loadPage,
    resetHospitalQuery,
    resetSupplierQuery,
    resetManufacturerQuery,
    resetCampusQuery,
    resetDepartmentQuery,
    resetDepartmentWarehouseCatalogQuery,
    resetWarehouseQuery,
    toggleAllRows,
    clearActionState
  }
}

function resetQuery(query: Record<string, string>) {
  Object.keys(query).forEach((key) => {
    query[key] = ''
  })
}
