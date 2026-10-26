import { OnlineOperationFlow, copyRecord, type OnlineApi, type OnlineJournal, type OnlineRecord } from './onlineOperationFlow.ts'
import { createOperationId } from './uuid.ts'
import type { SigningScope, PickOperation, PickSnapshot, PickTask, PickUnit } from './mobileTypes.ts'
export type PickingRecord = OnlineRecord<PickSnapshot, PickOperation>
export type PickingJournal = OnlineJournal<PickSnapshot, PickOperation>
export type PickingApi = OnlineApi<PickSnapshot, PickOperation>

/** Per-line three-mode picking; actual-code quantities are always confirmed again by the server. */
export class PickingFlow extends OnlineOperationFlow<PickSnapshot, PickOperation> {
  constructor(scope: SigningScope, deviceId: string, journal: PickingJournal, api: PickingApi) {
    super(scope, deviceId, journal, api, kind => ['PICK_LOOSE', 'PICK_PACKAGE', 'PICK_HIGH_VALUE'].includes(kind), detail => detail.task)
  }
  async begin(task: PickTask) {
    return this.exclusive(async () => {
      if (this.record) throw new Error('请先处理当前拣选记录')
      if (!['approved', 'partial_picked'].includes(task.status) || task.remainingQuantity <= 0) throw new Error('该申领明细当前不可拣选')
      await this.persist({version: 1, scope: copyRecord(this.scope), detail: {task: copyRecord(task), units: [], quantity: 0, availableQuantity: null},
        state: 'draft', message: '', operation: {operationId: createOperationId(), deviceId: this.deviceId, kind: task.kind,
          taskId: task.id, deptId: task.deptId, warehouseId: task.warehouseId, sourceWarehouseId: task.sourceWarehouseId,
          quantity: task.kind === 'PICK_LOOSE' ? '' : null, rawCode: null, packageIds: [], traceCodeIds: []}})
    })
  }
  async setQuantity(quantity: string) {
    return this.edit(record => {
      if (record.operation.kind !== 'PICK_LOOSE') throw new Error('实际码拣选不能手工改数量')
      if (!/^\d{1,14}$/.test(quantity.trim()) || Number(quantity) <= 0) throw new Error('拣选数量必须为正整数，最多14位')
      record.operation.quantity = quantity.trim()
    })
  }
  async reopen() { return this.edit(() => {}) }
  async addUnit(unit: PickUnit, rawCode: string): Promise<boolean> {
    let added = false
    await this.edit(record => {
      const expected = record.operation.kind === 'PICK_LOOSE' ? 'product' : record.operation.kind === 'PICK_PACKAGE' ? 'package' : 'trace'
      if (unit.type !== expected || unit.id <= 0 || unit.quantity <= 0) throw new Error('扫码结果类型不匹配')
      if (unit.type === 'product') {
        if (unit.id !== record.detail.task.productId) throw new Error('物料不匹配')
        record.operation.rawCode = rawCode; record.detail.units = [copyRecord(unit)]; added = true
      } else {
        const ids = unit.type === 'package' ? record.operation.packageIds : record.operation.traceCodeIds
        if (!ids.includes(unit.id)) { ids.push(unit.id); record.detail.units.push(copyRecord(unit)); added = true }
      }
    })
    return added
  }
}
