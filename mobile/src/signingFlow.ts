import { createOperationId } from './uuid.ts'
import type { SigningScope, Operation, DeliveryDetail, CheckItem } from './mobileTypes.ts'
import { OnlineOperationFlow, copyRecord, type OnlineRecord, type OnlineJournal, type OnlineApi, type OnlineState } from './onlineOperationFlow.ts'

export type SigningState = OnlineState
export type SigningRecord = OnlineRecord<DeliveryDetail, Operation>
export type SigningJournal = OnlineJournal<DeliveryDetail, Operation>
export type SigningApi = OnlineApi<DeliveryDetail, Operation>

/** One durable online signing operation per identity; an uncertain operation freezes its original payload. */
export class SigningFlow extends OnlineOperationFlow<DeliveryDetail, Operation> {
  constructor(scope: SigningScope, deviceId: string, journal: SigningJournal, api: SigningApi) {
    super(scope, deviceId, journal, api, kind => kind === 'SIGN_DELIVERY', detail => detail.task)
  }
  async begin(detail: DeliveryDetail) {
    return this.exclusive(async () => {
      if (this.record) throw new Error('请先处理当前签收记录')
      if (detail.task.status !== 'picked') throw new Error('配送单当前不可签收，请刷新')
      await this.persist({ version: 1, scope: copyRecord(this.scope), detail: copyRecord(detail), state: 'draft', message: '',
        operation: { operationId: createOperationId(), deviceId: this.deviceId, kind: 'SIGN_DELIVERY', taskId: detail.task.id,
          deptId: detail.task.deptId, warehouseId: detail.task.warehouseId, packageIds: [], traceCodeIds: [], looseConfirmed: false } })
    })
  }
  async confirmLoose(confirmed: boolean) {
    return this.edit(record => {
      if (record.detail.task.deliveryType !== 'loose') throw new Error('只有散货需要实收确认')
      record.operation.looseConfirmed = confirmed
    })
  }
  async addItem(item: CheckItem): Promise<boolean> {
    let added = false
    await this.edit(record => {
      if (!record.detail.items.some(expected => expected.type === item.type && expected.id === item.id)) throw new Error('实物不属于本次配送单，请刷新核对')
      const ids = item.type === 'package' ? record.operation.packageIds : record.operation.traceCodeIds
      if (!ids.includes(item.id)) { ids.push(item.id); added = true }
    })
    return added
  }
}
