import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Sortable from 'sortablejs'
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
  const page = ref<MasterDataPage | null>(null)
  const loading = ref(false)
  const error = ref('')
  const actionError = ref('')
  const actionMessage = ref('')
  const selectedCodes = ref<string[]>([])
  const hospitalColumnConfigs = ref<ColumnConfig[]>([])
  const activeColumnGroup = ref('all')
  const activeHospitalColumn = ref('')
  const hospitalCurrentPage = ref(1)
  const hospitalPageSize = ref(20)
  const hospitalPageJumpInput = ref('1')
  const catalogCurrentPage = ref(1)
  const catalogPageSize = ref(15)
  const catalogPageJumpInput = ref('1')
  const partnerCurrentPage = ref(1)
  const partnerPageSize = ref(20)
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

  const routeCode = computed(() => String(route.params.code ?? ''))
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
  const catalogTotal = computed(() => Number(page.value?.total ?? 0))
  const catalogPageTotal = computed(() =>
    Math.max(1, Math.ceil(catalogTotal.value / Math.max(1, catalogPageSize.value)))
  )
  const catalogPageSizeOptions = [15, 30, 50, 100, 200]
  const partnerTotal = computed(() => Number(page.value?.total ?? 0))
  const partnerPageSizeOptions = [10, 20, 50, 100, 200]

  const hospitalColumnGroups = [
    { key: 'all', label: '全部字段', fields: [] },
    { key: 'basic', label: '基础信息', fields: ['商品编码', '商品名称', '规格型号', '厂家', '供应商', '单位', '单价'] },
    { key: 'qualification', label: '资质证照', fields: ['注册证号', '生产许可证号', '经营许可证号', 'UDI编码', '注册证效期'] },
    { key: 'category', label: '分类目录', fields: ['合同编码', '一级分类', '二级分类', '三级分类', '招采子编码'] },
    { key: 'purchase', label: '采购监管', fields: ['是否带量', '是否集采', '是否国产', '是否收费', '重点监控', '是否高值', '是否冷链', '是否定数'] }
  ]

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
      page.value = await fetchMasterDataPage(code.value, queryParams.value)
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
      error.value = err instanceof Error ? err.message : '数据加载失败'
    } finally {
      loading.value = false
    }
  }

  function buildColumnConfigs() {
    if (!page.value?.rows.length) return
    const keys = Object.keys(page.value.rows[0])
    hospitalColumnConfigs.value = page.value.columns.map((label, index) => ({
      prop: hospitalColumnPropMap[label] || keys[index] || `col_${index}`,
      label,
      visible: true,
      order: index,
      width: Math.max(132, label.length * 28)
    }))
    applyColumnGroup(activeColumnGroup.value)
  }

  function applyColumnGroup(groupKey: string) {
    activeColumnGroup.value = groupKey
    const group = hospitalColumnGroups.find((item) => item.key === groupKey)
    hospitalColumnConfigs.value = hospitalColumnConfigs.value.map((column) => ({ ...column, visible: true }))
    if (!group || group.key === 'all') {
      activeHospitalColumn.value = ''
      scrollHospitalTableTo(0)
      return
    }
    const target = hospitalColumnConfigs.value.find((column) => group.fields.includes(column.label))
    if (target) scrollToHospitalColumn(target.prop)
  }

  function scrollToHospitalColumn(prop: string) {
    const orderedColumns = sortedColumns.value
    const targetIndex = orderedColumns.findIndex((column) => column.prop === prop)
    if (targetIndex < 0) return

    activeHospitalColumn.value = prop
    const selectionWidth = 42
    const stickyActionWidth = 210
    const leadingWidth = orderedColumns
      .slice(0, targetIndex)
      .reduce((sum, column) => sum + Number(column.width || 120), selectionWidth)
    const targetWidth = Number(orderedColumns[targetIndex]?.width || 120)
    const tableWidth = (tableRef.value?.$el as HTMLElement | undefined)?.clientWidth ?? 0
    const scrollLeft = Math.max(0, leadingWidth - Math.max(24, (tableWidth - stickyActionWidth - targetWidth) / 2))
    scrollHospitalTableTo(scrollLeft)
  }

  function scrollHospitalTableTo(left: number) {
    nextTick(() => {
      if (typeof tableRef.value?.setScrollLeft === 'function') {
        tableRef.value.setScrollLeft(left)
        return
      }

      const el = tableRef.value?.$el as HTMLElement | undefined
      const scrollWrap = el?.querySelector('.el-scrollbar__wrap') as HTMLElement | null
      if (scrollWrap) {
        scrollWrap.scrollLeft = left
        scrollWrap.dispatchEvent(new Event('scroll'))
      }
    })
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

  function changeHospitalPageSize() {
    hospitalPageSize.value = Math.min(200, Math.max(1, Number(hospitalPageSize.value) || 20))
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
    loadPage()
  }

  function resetDepartmentQuery() {
    resetQuery(departmentQuery.value)
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

  function changeDepartmentWarehouseCatalogPageSize() {
    catalogPageSize.value = Math.min(200, Math.max(1, Number(catalogPageSize.value) || 15))
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

  return {
    page,
    loading,
    error,
    actionError,
    actionMessage,
    selectedCodes,
    hospitalColumnConfigs,
    activeColumnGroup,
    activeHospitalColumn,
    hospitalCurrentPage,
    hospitalPageSize,
    hospitalPageJumpInput,
    catalogCurrentPage,
    catalogPageSize,
    catalogPageJumpInput,
    hospitalTotal,
    hospitalPageTotal,
    hospitalPageSizeOptions,
    catalogTotal,
    catalogPageTotal,
    catalogPageSizeOptions,
    hospitalColumnGroups,
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
    partnerTotal,
    partnerPageSizeOptions,
    changePartnerPage,
    changePartnerPageSize,
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
    applyColumnGroup,
    scrollToHospitalColumn,
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
