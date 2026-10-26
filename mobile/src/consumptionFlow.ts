import { OnlineOperationFlow, copyRecord, type OnlineApi, type OnlineJournal, type OnlineRecord } from './onlineOperationFlow.ts'
import { createOperationId } from './uuid.ts'
import type { SigningScope, ConsumeOperation, ConsumeSnapshot } from './mobileTypes.ts'
export type ConsumptionRecord = OnlineRecord<ConsumeSnapshot, ConsumeOperation>
export type ConsumptionJournal = OnlineJournal<ConsumeSnapshot, ConsumeOperation>
export type ConsumptionApi = OnlineApi<ConsumeSnapshot, ConsumeOperation>

/** Whole physical package only; client never declares consumption quantities or prices. */
export class ConsumptionFlow extends OnlineOperationFlow<ConsumeSnapshot, ConsumeOperation> {
  constructor(scope: SigningScope,deviceId: string,journal: ConsumptionJournal,api: ConsumptionApi) {
    super(scope,deviceId,journal,api,kind=>kind==='CONSUME_PACKAGE',detail=>detail.pack)
  }
  async begin(detail: ConsumeSnapshot,rawCode: string) {
    return this.exclusive(async()=>{
      if(this.record)throw new Error('请先处理当前整包消耗记录')
      if(detail.pack.status!=='signed'||detail.pack.quantity<=0||!detail.pack.traceCodeId||!rawCode.trim())throw new Error('只能选择已签收的完整定数包')
      await this.persist({version:1,scope:copyRecord(this.scope),detail:copyRecord(detail),state:'draft',message:'',operation:{
        operationId:createOperationId(),deviceId:this.deviceId,kind:'CONSUME_PACKAGE',taskId:detail.pack.id,
        deptId:detail.pack.deptId,warehouseId:detail.pack.warehouseId,rawCode,wholePackageConfirmed:false}})
    })
  }
  async confirmWhole(value: boolean) {return this.edit(record=>{record.operation.wholePackageConfirmed=value})}
  override async review() {
    if(!this.current?.operation.wholePackageConfirmed)throw new Error('请先确认整包消耗及包内数量')
    return super.review()
  }
}
