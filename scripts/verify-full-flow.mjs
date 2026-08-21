// 全流程 A→B 端到端验证脚本（真实 MySQL + 后端 API）
// 覆盖：采购订单 → 收货验收(新字段) → 库存三页签 → 定数包打包/按验收单分配/确认/打印 →
//       高值收货/唯一码/计费回传/收费明细 → 盘点/调价/召回 → 科室目录与申领 → 查询面 → 权限回归
const BASE = 'http://127.0.0.1:1818/api'
const results = []

function record(name, ok, detail) {
  results.push({ name, ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'} | ${name}${detail ? ' | ' + String(detail).slice(0, 220) : ''}`)
}

async function call(method, path, { token, body } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const text = await res.text()
  let json
  try { json = JSON.parse(text) } catch { json = { raw: text.slice(0, 200) } }
  return { status: res.status, body: json }
}

function expectOk(res, name, extra = '') {
  const ok = res.status === 200 && res.body?.code === 0
  record(name, ok, ok ? (extra || JSON.stringify(res.body?.data)?.slice(0, 160)) : `HTTP ${res.status} ${res.body?.message || ''} ${extra}`)
  return ok
}

// ===== 登录 =====
const login = await call('POST', '/auth/login', { body: { username: 'admin', password: 'admin123' } })
const token = login.body?.data?.token
record('登录 admin', !!token, '')

const ts = Date.now()

// ===== 公共夹具：供应商 =====
const optsRes = await call('GET', '/receiving-orders/options', { token })
const supplierName = optsRes.body?.data?.suppliers?.[0]?.supplierName
record('S0 取可用供应商', !!supplierName, supplierName || '')

// ===== A. 采购订单链路 =====
const poRes = await call('POST', '/purchase-orders', {
  token,
  body: {
    supplierName,
    orderSource: 'manual',
    expectedArrivalDate: '2026-09-01',
    items: [{ productCode: 'HC0214467', quantity: 100, unit: '支', estimatedUnitPrice: 120 }]
  }
})
expectOk(poRes, 'A1 采购订单创建')
const orderNo = poRes.body?.data?.orderNo
for (const action of ['submit', 'approve', 'send']) {
  const r = await call('PUT', `/purchase-orders/${orderNo}/action`, { token, body: { action, opinion: '全流程冒烟' } })
  expectOk(r, `A2 采购订单${action}`, r.body?.data?.status || '')
}

// ===== B. 收货验收（新字段 receivingType/isAgent） =====
const rcvRes = await call('POST', '/receiving-orders', {
  token,
  body: {
    purchaseOrderNo: orderNo,
    supplierName,
    warehouseName: 'SPD中心库',
    receivingType: 'agent',
    isAgent: true,
    remark: '全流程冒烟收货（代理商直送）',
    items: [{
      productCode: 'HC0214467', productionBatchNo: `FLOW-${ts}`, productionDate: '2026-08-01',
      expireDate: '2028-12-31', quantity: 100, qualifiedQuantity: 100, unqualifiedQuantity: 0
    }]
  }
})
expectOk(rcvRes, 'B1 收货单创建(agent/代理商)', '')
const receivingNo = rcvRes.body?.data?.receivingNo
const rcvApprove = await call('PUT', `/receiving-orders/${receivingNo}/action`, { token, body: { action: 'approve', opinion: '全流程冒烟' } })
expectOk(rcvApprove, 'B2 收货审核入库', '')
const rcvList = await call('GET', `/receiving-orders?page=1&size=5&receivingNo=${encodeURIComponent(receivingNo)}`, { token })
{
  const row = (rcvList.body?.data?.rows || []).find((r) => r.receivingNo === receivingNo)
  record('B3 收货列表新字段回显', !!row && row.receivingType === 'agent' && Number(row.isAgent) === 1,
    row ? `receivingType=${row.receivingType} isAgent=${row.isAgent}` : '未找到记录')
}
const rcvDetail = await call('GET', `/receiving-orders/${receivingNo}`, { token })
const rcvBatchNo = rcvDetail.body?.data?.items?.[0]?.systemBatchNo
record('B4 收货明细生成系统批次', !!rcvBatchNo, rcvBatchNo || '')

// ===== C. 库存三页签 =====
const balRes = await call('GET', `/inventory/balances?page=1&size=50&productCode=${encodeURIComponent('HC0214467')}`, { token })
expectOk(balRes, 'C1 库存汇总查询', '')
{
  const row = (balRes.body?.data?.rows || []).find((r) => r.systemBatchNo === rcvBatchNo)
  record('C2 汇总行含新字段(单价/单位/数量/金额/厂家/供应商)', !!row && row.unitPrice != null && row.qty != null && row.amount != null,
    row ? `qty=${row.qty} unitPrice=${row.unitPrice} amount=${row.amount} dept=${row.deptName}` : '未找到本批次')
}
const quotaEmpty = await call('GET', '/inventory/quota-package-stock?page=1&size=10', { token })
expectOk(quotaEmpty, 'C3 定数包库存查询(打包前)', '')

// ===== D. 定数包：打包任务 → 按验收单分配(部分/全部) → 确认 → 打印 =====
const taskRes = await call('POST', '/quota-packages/packing-tasks', {
  token, body: { templateCode: 'HC0214467001', warehouseName: 'SPD中心库', packageCount: 1 }
})
expectOk(taskRes, 'D1 打包任务创建', '')
const taskNo = taskRes.body?.data?.taskNo
const allocPartial = await call('POST', `/quota-packages/packing-tasks/${taskNo}/allocate-from-receiving`, {
  token, body: { receivingNo, quantity: 30 }
})
expectOk(allocPartial, 'D2 按验收单部分分配30', allocPartial.body?.data?.allocatedQty ?? '')
const allocFull = await call('POST', `/quota-packages/packing-tasks/${taskNo}/allocate-from-receiving`, {
  token, body: { receivingNo, quantity: null }
})
expectOk(allocFull, 'D3 按验收单全部打包分配', allocFull.body?.data?.allocatedQty ?? '')
const allocDetail = await call('GET', `/quota-packages/packing-tasks/${taskNo}/allocations`, { token })
{
  const rows = allocDetail.body?.data || []
  const mine = rows.filter((r) => r.receivingNo === receivingNo)
  record('D4 分配明细含验收单来源(30+剩余)', mine.length === 2 && mine.some((r) => Number(r.reservedQty) === 30),
    JSON.stringify(mine.map((r) => `${r.receivingNo}:${r.reservedQty}`)))
}
const confirmRes = await call('PUT', `/quota-packages/packing-tasks/${taskNo}/confirm`, { token })
expectOk(confirmRes, 'D5 打包任务确认生成标签', confirmRes.body?.data?.labels?.length + ' labels')
const labelNo = confirmRes.body?.data?.labels?.[0]
const printRes = await call('PUT', `/quota-packages/labels/${labelNo}/print`, { token })
expectOk(printRes, 'D6 标签打印', '')
const quotaStock = await call('GET', '/inventory/quota-package-stock?page=1&size=20', { token })
expectOk(quotaStock, 'D7 定数包库存查询(打包后)', '')

// ===== E. 高值耗材：收货 → 唯一码 → 计费回传 → 收费明细 =====
const hvRcv = await call('POST', '/receiving-orders', {
  token,
  body: {
    supplierName, warehouseName: 'SPD中心库',
    receivingType: 'normal', isAgent: false, remark: '全流程冒烟高值收货',
    items: [{
      productCode: 'HC1348978', productionBatchNo: `HV-${ts}`, productionDate: '2026-08-01',
      expireDate: '2028-12-31', quantity: 2, qualifiedQuantity: 2, unqualifiedQuantity: 0
    }]
  }
})
expectOk(hvRcv, 'E1 高值收货单创建', '')
const hvNo = hvRcv.body?.data?.receivingNo
const hvApprove = await call('PUT', `/receiving-orders/${hvNo}/action`, { token, body: { action: 'approve', opinion: '全流程冒烟' } })
expectOk(hvApprove, 'E2 高值收货审核(生成唯一码)', '')
const hvCodes = await call('GET', `/inventory/unique-code-stock?page=1&size=20&productCode=${encodeURIComponent('HC1348978')}`, { token })
expectOk(hvCodes, 'E3 唯一码库存查询(高值)', hvCodes.body?.data?.total + ' codes')
const codes = (hvCodes.body?.data?.rows || []).map((r) => r.uniqueCode)
record('E4 生成2个在库唯一码', codes.length === 2, codes.join(','))
for (let i = 0; i < codes.length; i++) {
  const cb = await call('POST', '/operational-closure/high-value/billing-callback', {
    token,
    body: {
      externalChargeNo: `SMK-${ts}-${i}`,
      uniqueCode: codes[i],
      productCode: 'HC1348978',
      quantity: 1,
      deptName: '手术室',
      patientNo: `P-${ts}-${i}`,
      patientNameMasked: '张**',
      sourceSystem: 'AIMS'
    }
  })
  expectOk(cb, `E5 计费回传#${i + 1}(扣库存)`, cb.body?.data?.chargeNo || '')
}
const chargeList = await call('GET', `/operational-closure/lists/high-value?page=1&size=20`, { token })
{
  const rows = chargeList.body?.data?.rows || []
  record('E6 收费明细出现2笔计费', rows.filter((r) => String(r.externalChargeNo || '').startsWith(`SMK-${ts}`)).length === 2,
    `high-value rows=${rows.length}`)
}
const hvAfter = await call('GET', `/inventory/unique-code-stock?page=1&size=20&productCode=${encodeURIComponent('HC1348978')}`, { token })
{
  const remain = (hvAfter.body?.data?.rows || []).filter((r) => codes.includes(String(r.uniqueCode))).length
  record('E7 计费后唯一码退出在库', hvAfter.body?.code === 0 && remain === 0, `在库剩余=${remain}`)
}

// ===== F. 盘点 / 调价 / 召回 =====
const rcv3 = await call('POST', '/receiving-orders', {
  token,
  body: {
    supplierName, warehouseName: '骨科二级库',
    receivingType: 'return', isAgent: false, remark: '全流程冒烟盘点/调价/召回用收货',
    items: [{
      productCode: 'HC0214467', productionBatchNo: `ST-${ts}`, productionDate: '2026-08-01',
      expireDate: '2028-12-31', quantity: 10, qualifiedQuantity: 10, unqualifiedQuantity: 0
    }]
  }
})
expectOk(rcv3, 'F1 盘点用收货单创建(退货收货)', '')
const rcv3No = rcv3.body?.data?.receivingNo
await call('PUT', `/receiving-orders/${rcv3No}/action`, { token, body: { action: 'approve', opinion: '全流程冒烟' } })
const rcv3Detail = await call('GET', `/receiving-orders/${rcv3No}`, { token })
const stBatchNo = rcv3Detail.body?.data?.items?.[0]?.systemBatchNo
const stRes = await call('POST', '/inventory/stocktaking', {
  token, body: { warehouseName: '骨科二级库', systemBatchNo: stBatchNo, actualQty: 7, reason: '全流程冒烟盘点' }
})
expectOk(stRes, 'F2 盘点单创建', '')
const stNo = stRes.body?.data?.stocktakingNo
const stApprove = await call('PUT', `/inventory/stocktaking/${stNo}/approve`, { token })
expectOk(stApprove, 'F3 盘点复核(盘亏-3)', '')
const paRes = await call('POST', '/inventory/batch-price-adjustments', {
  token, body: { systemBatchNo: stBatchNo, newUnitPrice: 150, reason: '全流程冒烟调价' }
})
expectOk(paRes, 'F4 批次调价创建', '')
const paNo = paRes.body?.data?.adjustmentNo
const paApprove = await call('PUT', `/inventory/batch-price-adjustments/${paNo}/approve`, { token })
expectOk(paApprove, 'F5 调价审批', '')
const recallRes = await call('POST', '/operational-closure/recalls', {
  token, body: { warehouseName: '骨科二级库', productCode: 'HC0214467', quantity: 5, reason: '全流程冒烟召回隔离' }
})
expectOk(recallRes, 'F6 召回隔离', '')

// ===== G. 库房商品绑定 → 科室目录 → 申领 =====
const whList = await call('GET', `/master-data/warehouses?page=1&size=100&warehouseKeyword=${encodeURIComponent('骨科二级库')}`, { token })
const wh = (whList.body?.data?.rows || []).find((r) => r.name === '骨科二级库')
record('G0 查询库房信息', !!wh, wh ? wh.code : '未找到')
if (wh) {
  const bindRes = await call('PUT', `/master-data/warehouses/${encodeURIComponent(wh.code)}`, {
    token,
    body: {
      warehouseCode: wh.code,
      warehouseName: wh.name,
      warehouseType: wh.type,
      campusName: wh.campus,
      deptName: wh.dept && wh.dept !== '-' ? wh.dept : null,
      participateStats: wh.participateStats === '参与',
      statsCategories: wh.statsCategories && wh.statsCategories !== '-' ? wh.statsCategories : null,
      status: wh.status === '启用' ? 1 : 0,
      productCodes: ['HC0214467']
    }
  })
  expectOk(bindRes, 'G0 库房商品绑定维护', '')
}
const catRes = await call('POST', '/master-data/department-warehouse-catalogs', {
  token, body: { deptName: '骨科', warehouseName: '骨科二级库', productCode: 'HC0214467', status: 1 }
})
{
  const ok = catRes.body?.code === 0 || String(catRes.body?.message || '').includes('已存在')
  record('G1 科室库房目录维护(幂等)', ok, catRes.body?.message || '')
}
const reqRes = await call('POST', '/operational-closure/requisitions', {
  token, body: { deptName: '骨科', warehouseName: '骨科二级库', productCode: 'HC0214467', quantity: 3 }
})
expectOk(reqRes, 'G2 科室申领创建', '')
const reqNo = reqRes.body?.data?.requisitionNo
const reqApprove = await call('PUT', `/operational-closure/requisitions/${reqNo}/action`, { token, body: { action: 'approve' } })
expectOk(reqApprove, 'G3 科室申领审批', '')

// ===== H. 查询面与权限回归 =====
const fo = await call('GET', '/system/field-options?fieldKey=receiving_type', { token })
expectOk(fo, 'H1 字段管理选项(收货类型)', (fo.body?.data || []).length + ' 项')
const eventsRes = await call('GET', '/inventory/events?page=1&size=20', { token })
expectOk(eventsRes, 'H2 库存交易流水', eventsRes.body?.data?.total + ' 条')
const udiRes = await call('GET', `/udi-traceability/records?page=1&size=10&keyword=${encodeURIComponent(codes[0] || '')}`, { token })
expectOk(udiRes, 'H3 UDI 追溯查询', udiRes.body?.data?.total + ' 条')
const dashRes = await call('GET', '/dashboard', { token })
expectOk(dashRes, 'H4 工作台总览', '')

// operator01 角色修复回归
const opLogin = await call('POST', '/auth/login', { body: { username: 'operator01', password: 'admin123' } })
const opToken = opLogin.body?.data?.token
record('H5 operator01 登录', !!opToken, '')
const opBal = await call('GET', '/inventory/balances?page=1&size=5', { token: opToken })
record('H6 operator01 可查库存(运营角色)', opBal.body?.code === 0, opBal.body?.message || '')
const opRcv = await call('GET', '/receiving-orders?page=1&size=5', { token: opToken })
record('H7 operator01 无收货权限(预期拒绝)', opRcv.body?.code !== 0, `code=${opRcv.body?.code}`)

// ===== 汇总 =====
const failed = results.filter((r) => !r.ok)
console.log('\n===== 全流程结果 =====')
console.log(`总计 ${results.length} 步，通过 ${results.length - failed.length}，失败 ${failed.length}`)
for (const f of failed) console.log(`FAIL | ${f.name} | ${f.detail}`)
process.exit(failed.length ? 1 : 0)
