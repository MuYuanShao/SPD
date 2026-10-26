export type SigningScope = { baseUrl: string; serverId: string; userId: number }
export type Operation = {
  operationId: string; deviceId: string; kind: 'SIGN_DELIVERY'; taskId: number; deptId: number; warehouseId: number;
  packageIds: number[]; traceCodeIds: number[]; looseConfirmed: boolean; reviewHash?: string
}
export type DeliveryTask = {
  id: number; documentNo: string; status: string; deliveryType: string;
  deptId: number; deptName: string; sourceWarehouseId: number; sourceWarehouseName: string;
  warehouseId: number; warehouseName: string; productCode: string; productName: string; quantity: number; unit: string;
  requisitionNo?: string | null; specModel?: string | null; manufacturerName?: string | null; createdAt?: string | null; signedAt?: string | null
}
export type CheckItem = { type: 'package' | 'trace'; id: number; code: string; alternateCode: string; status: string }
export type DeliveryBatch = {batchId:number;systemBatchNo:string|null;productionBatchNo:string|null;productionDate:string|null;expireDate:string|null;quantity:number}
export type DeliveryDetail = { task: DeliveryTask; items: CheckItem[]; batches?: DeliveryBatch[] }
export type Review = { reviewHash: string; expiresAt: string; summary: DeliveryDetail }
export type OperationResult = { operationId: string; kind: string; taskId: number | null; documentNo: string | null;
  status: string; userId: number; deviceId: string | null; completedAt: string | null; message: string; remainingQuantity?: number;
  quantity?: number | null; amount?: number | null }
export type PickKind = 'PICK_LOOSE' | 'PICK_PACKAGE' | 'PICK_HIGH_VALUE'
export type PickTask = {id: number; documentNo: string; status: string; kind: PickKind; deptId: number; deptName: string;
  sourceWarehouseId: number; sourceWarehouseName: string; warehouseId: number; warehouseName: string; productId: number;
  productCode: string; productName: string; specModel?: string; manufacturerName?: string; supplierName?: string;
  unit: string; quantity: number; pickedQuantity: number; remainingQuantity: number; requisitionId?: number;
  packageQuantity: number | null; packageUnit: string | null}
export type PickUnit = {type: 'product' | 'package' | 'trace'; id: number; code: string; status: string; quantity: number}
export type PickOrder = {id:number;documentNo:string;status:string;deptId:number;deptName:string;sourceWarehouseId:number;sourceWarehouseName:string;warehouseId:number;warehouseName:string;pendingLineCount:number}
export type PickOrderDetail = {order:PickOrder;items:PickTask[]}
export type PickOrderMatch = {task:PickTask;unit:PickUnit}
export type PickSnapshot = {task: PickTask; units: PickUnit[]; quantity: number; availableQuantity: number | null}
export type PickOperation = {operationId: string; deviceId: string; kind: PickKind; taskId: number; deptId: number;
  warehouseId: number; sourceWarehouseId: number; quantity: string | null; rawCode: string | null;
  packageIds: number[]; traceCodeIds: number[]; reviewHash?: string}
export type ConsumePack = {id: number; labelNo: string; traceCodeId: number; productId: number; productCode: string;
  productName: string; unit: string; quantity: number; deptId: number; deptName: string; warehouseId: number;
  warehouseName: string; status: string; version: number}
export type ConsumeSnapshot = {pack: ConsumePack; batches: Array<{batchId: number; quantity: number; unitPrice: number; expireDate?: string}>}
export type ConsumeOperation = {operationId: string; deviceId: string; kind: 'CONSUME_PACKAGE'; taskId: number; deptId: number;
  warehouseId: number; rawCode: string; wholePackageConfirmed: boolean; reviewHash?: string}
export class MobileApiError extends Error {
  code: number
  constructor(code: number, message: string) { super(message); this.name = 'MobileApiError'; this.code = code }
}
