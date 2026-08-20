import { ref, type ComputedRef, type Ref } from 'vue'
import {
  createManufacturer,
  createSupplier,
  updateManufacturer,
  updateManufacturerStatus,
  updateSupplier,
  updateSupplierStatus,
  type ManufacturerPayload,
  type SupplierPayload
} from '../api/masterData'

interface PartnerMasterDataDeps {
  selectedCodes: Ref<string[]>
  selectedSupplier: ComputedRef<Record<string, unknown> | undefined>
  selectedManufacturer: ComputedRef<Record<string, unknown> | undefined>
  supplierDialogOpen: Ref<boolean>
  supplierDialogMode: Ref<'create' | 'edit'>
  manufacturerDialogOpen: Ref<boolean>
  manufacturerDialogMode: Ref<'create' | 'edit'>
  actionError: Ref<string>
  actionMessage: Ref<string>
  clearActionState: () => void
  loadPage: () => Promise<void>
}

export function usePartnerMasterData(deps: PartnerMasterDataDeps) {
  const supplierForm = ref<SupplierPayload>(emptySupplierForm())
  const manufacturerForm = ref<ManufacturerPayload>(emptyManufacturerForm())

  function openCreateSupplier() {
    deps.clearActionState()
    deps.supplierDialogMode.value = 'create'
    supplierForm.value = emptySupplierForm()
    deps.supplierDialogOpen.value = true
  }

  function openEditSupplier(row = deps.selectedSupplier.value, requireSelected = true) {
    deps.clearActionState()
    if (!row || (requireSelected && deps.selectedCodes.value.length !== 1)) {
      deps.actionError.value = '请选择一条供应商记录后再修改'
      return
    }

    deps.supplierDialogMode.value = 'edit'
    supplierForm.value = {
      supplierCode: String(row.code ?? ''),
      supplierName: String(row.name ?? ''),
      creditCode: String(row.creditCode ?? ''),
      supplierType: String(row.type ?? ''),
      grade: String(row.grade === '-' ? '' : row.grade ?? ''),
      contactName: String(row.contactName ?? ''),
      contactPhone: String(row.contactPhone ?? ''),
      email: String(row.email === '-' ? '' : row.email ?? ''),
      address: String(row.address === '-' ? '' : row.address ?? '')
    }
    deps.supplierDialogOpen.value = true
  }

  async function saveSupplier() {
    deps.clearActionState()
    try {
      if (deps.supplierDialogMode.value === 'create') {
        await createSupplier(supplierForm.value)
        deps.actionMessage.value = '供应商已新增'
      } else {
        await updateSupplier(supplierForm.value.supplierCode, supplierForm.value)
        deps.actionMessage.value = '供应商已修改'
      }
      deps.supplierDialogOpen.value = false
      await deps.loadPage()
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '供应商保存失败'
    }
  }

  async function disableSuppliers(supplierCodes = deps.selectedCodes.value) {
    deps.clearActionState()
    if (!supplierCodes.length) {
      deps.actionError.value = '请选择需要停用的供应商'
      return
    }
    if (!window.confirm(`确认停用已选择的 ${supplierCodes.length} 条供应商吗？`)) {
      return
    }

    try {
      const result = await updateSupplierStatus({ supplierCodes, status: 0 })
      deps.actionMessage.value = `已停用 ${result.updatedRows} 条供应商`
      await deps.loadPage()
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '供应商停用失败'
    }
  }

  function openCreateManufacturer() {
    deps.clearActionState()
    deps.manufacturerDialogMode.value = 'create'
    manufacturerForm.value = emptyManufacturerForm()
    deps.manufacturerDialogOpen.value = true
  }

  function openEditManufacturer(row = deps.selectedManufacturer.value, requireSelected = true) {
    deps.clearActionState()
    if (!row || (requireSelected && deps.selectedCodes.value.length !== 1)) {
      deps.actionError.value = '请选择一条厂家记录后再修改'
      return
    }

    deps.manufacturerDialogMode.value = 'edit'
    manufacturerForm.value = {
      manufacturerCode: String(row.code ?? ''),
      manufacturerName: String(row.name ?? ''),
      creditCode: String(row.creditCode === '-' ? '' : row.creditCode ?? ''),
      licenseNo: String(row.licenseNo === '-' ? '' : row.licenseNo ?? ''),
      contactName: String(row.contactName === '-' ? '' : row.contactName ?? ''),
      contactPhone: String(row.contactPhone === '-' ? '' : row.contactPhone ?? ''),
      address: String(row.address === '-' ? '' : row.address ?? '')
    }
    deps.manufacturerDialogOpen.value = true
  }

  async function saveManufacturer() {
    deps.clearActionState()
    try {
      if (deps.manufacturerDialogMode.value === 'create') {
        await createManufacturer(manufacturerForm.value)
        deps.actionMessage.value = '厂家已新增'
      } else {
        await updateManufacturer(manufacturerForm.value.manufacturerCode, manufacturerForm.value)
        deps.actionMessage.value = '厂家已修改'
      }
      deps.manufacturerDialogOpen.value = false
      await deps.loadPage()
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '厂家保存失败'
    }
  }

  async function disableManufacturers(manufacturerCodes = deps.selectedCodes.value) {
    deps.clearActionState()
    if (!manufacturerCodes.length) {
      deps.actionError.value = '请选择需要停用的厂家'
      return
    }
    if (!window.confirm(`确认停用已选择的 ${manufacturerCodes.length} 条厂家吗？`)) {
      return
    }

    try {
      const result = await updateManufacturerStatus({ manufacturerCodes, status: 0 })
      deps.actionMessage.value = `已停用 ${result.updatedRows} 条厂家`
      await deps.loadPage()
    } catch (err) {
      deps.actionError.value = err instanceof Error ? err.message : '厂家停用失败'
    }
  }

  return {
    supplierForm,
    manufacturerForm,
    openCreateSupplier,
    openEditSupplier,
    saveSupplier,
    disableSuppliers,
    openCreateManufacturer,
    openEditManufacturer,
    saveManufacturer,
    disableManufacturers
  }
}

function emptySupplierForm(): SupplierPayload {
  return {
    supplierCode: '',
    supplierName: '',
    creditCode: '',
    supplierType: '',
    grade: '',
    contactName: '',
    contactPhone: '',
    email: '',
    address: ''
  }
}

function emptyManufacturerForm(): ManufacturerPayload {
  return {
    manufacturerCode: '',
    manufacturerName: '',
    creditCode: '',
    licenseNo: '',
    contactName: '',
    contactPhone: '',
    address: ''
  }
}
