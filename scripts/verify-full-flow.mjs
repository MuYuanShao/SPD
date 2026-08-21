// 全流程 A→B 端到端验证脚本（真实 MySQL + 后端 API）
// 覆盖：新增目录→审批→定数包模板维护→科室目录维护→采购订单(创建→提交→审批→发送)→
//       收货验收(新字段 receivingType/isAgent)→库存三页签→定数包打包/按验收单分配/确认/打印/低值唯一码UDI→
//       高值链路(收货→2唯一码→计费回传×2→收费明细→唯一码退出在库)→盘点/调价/召回→
//       科室申领审批→字段管理/交易流水/UDI/工作台/权限回归
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
  record(name, ok, ok ? (extra || '') : `HTTP ${res.status} ${res.body?.message || ''} ${extra}`)
  return ok
}

// ===== 登录与公共夹具 =====
const login = await call('POST', '/auth/login', { body: { username: 'admin', password: 'admin123' } })
const token = login.body?.data?.token
record('登录 admin', !!token, '')

const optsRes = await call('GET', '/receiving-orders/options', { token })
const supplierName = optsRes.body?.data?.suppliers?.[0]?.supplierName
record('S0 取可用供应商', !!supplierName, supplierName || '')

const partnerRes = await call('GET', '/master-data/hospital-products/partner-options', { token })
const manufacturerName = partnerRes.body?.data?.manufacturers?.[0]?.name
record('S1 取可用厂家', !!manufacturerName, manufacturerName || '')

const ts = Date.now()
const NEW_PRODUCT = `AB${String(ts).slice(-10)}`
const NEW_BATCH = `ABFLOW-${ts}`

// ===== N1-N4. 新增目录 → 审批 → 定数包模板维护 → 科室目录维护 =====
const createProduct = await call('POST', '/master-data/hospital-products', {
  token,
  body: {
    productCode: NEW_PRODUCT,
    productName: `全流程AB测商品${String(ts).slice(-4)}`,
    specModel: 'AB-01',
    manufacturerName,
    supplierName,
    unit: '支',
    purchasePrice: 88,
    minPurchaseQty: 5,
    purchaseUnit: '支',
    conversionRate: 1,
    purchasePackageQty: 10,
    registrationNo: `国械注进${ts}`,
    registrationExpireDate: '2028-12-31',
    domestic: true,
    chargeable: true,
    highValue: false,
    coldChain: false,
    quotaManaged: true,
    storageCondition: '常温'
  }
})
expectOk(createProduct, 'N1 新增医院目录(新品准入)', createProduct.body?.data?.applicationNo || '')
const applicationNo = createProduct.body?.data?.applicationNo

// N2. 审批：多步审批流，循环推进至最终通过（admin 具备全部审批步骤权限）
{
  let status = ''
  let steps = 0
  for (let i = 0; i < 6; i++) {
    const detail = await call('GET', `/pending-product-applications/${applicationNo}`, { token })
    status = detail.body?.data?.approvalStatus || ''
    if (status === 'approved' || detail.body?.code !== 0) break
    steps++
    const r = await call('PUT', `/pending-product-applications/${applicationNo}/action`, {
      token, body: { action: 'approve', opinion: `全流程AB测审批第${steps}步` }
    })
    if (r.body?.code !== 0) { record('N2 新品准入审批', false, r.body?.message || ''); break }
  }
  record('N2 新品准入审批(多步)', status === 'approved', `审批${steps}步后状态=${status}`)
}
const productDetail = await call('GET', `/master-data/hospital-products/${encodeURIComponent(NEW_PRODUCT)}`, { token })
record('N2b 医院目录生成商品', productDetail.body?.code === 0, productDetail.body?.message || '')

const templateRes = await call('POST', '/quota-packages/templates', {
  token, body: { productCode: NEW_PRODUCT, quantity: 10, unit: '支' }
})
expectOk(templateRes, 'N3 定数包模板维护', templateRes.body?.data?.templateCode || '')
const newTemplateCode = templateRes.body?.data?.templateCode

const whList = await call('GET', `/master-data/warehouses?page=1&size=100&warehouseKeyword=${encodeURIComponent('骨科二级库')}`, { token })
const wh = (whList.body?.data?.rows || []).find((r) => r.name === '骨科二级库')
record('N4a 查询库房信息', !!wh, wh ? wh.code : '未找到')
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
      productCodes: [NEW_PRODUCT]
    }
  })
  expectOk(bindRes, 'N4b 库房商品绑定维护', '')
}
const catRes = await call('POST', '/master-data/department-warehouse-catalogs', {
  token, body: { deptName: '骨科', warehouseName: '骨科二级库', productCode: NEW_PRODUCT, status: 1 }
})
{
  const ok = catRes.body?.code === 0 || String(catRes.body?.message || '').includes('已存在')
  record('N4c 科室库房目录维护(幂等)', ok, catRes.body?.message || '')
}

// ===== N5. 采购订单 =====
const poRes = await call('POST', '/purchase-orders', {
  token,
  body: {
    supplierName,
    orderSource: 'manual',
    expectedArrivalDate: '2026-09-01',
    items: [{ productCode: NEW_PRODUCT, quantity: 100, unit: '支', estimatedUnitPrice: 88 }]
  }
})
expectOk(poRes, 'N5a 采购订单创建', poRes.body?.data?.orderNo || '')
const orderNo = poRes.body?.data?.orderNo
for (const action of ['submit', 'approve', 'send']) {
  const r = await call('PUT', `/purchase-orders/${orderNo}/action`, { token, body: { action, opinion: '全流程AB测' } })
  expectOk(r, `N5b 采购订单${action}`, r.body?.data?.status || '')
}

// ===== N6. 收货验收（新字段） =====
const rcvRes = await call('POST', '/receiving-orders', {
  token,
  body: {
    purchaseOrderNo: orderNo,
    supplierName,
    warehouseName: '骨科二级库',
    receivingType: 'exchange',
    isAgent: false,
    remark: '全流程AB测收货（换货收货）',
    items: [{
      productCode: NEW_PRODUCT, productionBatchNo: NEW_BATCH, productionDate: '2026-08-01',
      expireDate: '2028-12-31', quantity: 100, qualifiedQuantity: 100, unqualifiedQuantity: 0
    }]
  }
})
expectOk(rcvRes, 'N6a 收货单创建(exchange)', rcvRes.body?.data?.receivingNo || '')
const receivingNo = rcvRes.body?.data?.receivingNo
const rcvApprove = await call('PUT', `/receiving-orders/${receivingNo}/action`, { token, body: { action: 'approve', opinion: '全流程AB测' } })
expectOk(rcvApprove, 'N6b 收货审核入库', '')
const rcvList = await call('GET', `/receiving-orders?page=1&size=5&receivingNo=${encodeURIComponent(receivingNo)}`, { token })
{
  const row = (rcvList.body?.data?.rows || []).find((r) => r.receivingNo === receivingNo)
  record('N6c 收货列表新字段回显', !!row && row.receivingType === 'exchange' && Number(row.isAgent) === 0,
    row ? `receivingType=${row.receivingType} isAgent=${row.isAgent}` : '未找到记录')
}
const rcvDetail = await call('GET', `/receiving-orders/${receivingNo}`, { token })
const rcvBatchNo = rcvDetail.body?.data?.items?.[0]?.systemBatchNo
record('N6d 收货明细生成系统批次', !!rcvBatchNo, rcvBatchNo || '')

// ===== N7. 库存三页签（打包前） =====
const balRes = await call('GET', `/inventory/balances?page=1&size=50&productCode=${encodeURIComponent(NEW_PRODUCT)}`, { token })
expectOk(balRes, 'N7a 库存汇总查询', '')
{
  const row = (balRes.body?.data?.rows || []).find((r) => r.systemBatchNo === rcvBatchNo)
  record('N7b 汇总行含新字段(单价/单位/数量/金额/厂家/供应商)', !!row && Number(row.qty) === 100 && Number(row.unitPrice) === 88,
    row ? `qty=${row.qty} unitPrice=${row.unitPrice} amount=${row.amount} unit=${row.unit}` : '未找到本批次')
}
const quotaEmpty = await call('GET', `/inventory/quota-package-stock?page=1&size=10&productName=${encodeURIComponent('全流程AB测')}`, { token })
expectOk(quotaEmpty, 'N7c 定数包库存查询(打包前)', '')
const uniqueEmpty = await call('GET', `/inventory/unique-code-stock?page=1&size=10&productCode=${encodeURIComponent(NEW_PRODUCT)}`, { token })
expectOk(uniqueEmpty, 'N7d 唯一码库存查询(低值打包前为空)', '')

// ===== N8. 定数包：打包 → 按验收单分配(部分/全部) → 确认 → 打印 =====
const taskRes = await call('POST', '/quota-packages/packing-tasks', {
  token, body: { templateCode: newTemplateCode, warehouseName: '骨科二级库', packageCount: 1 }
})
expectOk(taskRes, 'N8a 打包任务创建', '')
const taskNo = taskRes.body?.data?.taskNo
const allocPartial = await call('POST', `/quota-packages/packing-tasks/${taskNo}/allocate-from-receiving`, {
  token, body: { receivingNo, quantity: 30 }
})
expectOk(allocPartial, 'N8b 按验收单部分分配30', allocPartial.body?.data?.allocatedQty ?? '')
const allocFull = await call('POST', `/quota-packages/packing-tasks/${taskNo}/allocate-from-receiving`, {
  token, body: { receivingNo, quantity: null }
})
expectOk(allocFull, 'N8c 按验收单全部打包分配', allocFull.body?.data?.allocatedQty ?? '')
const allocDetail = await call('GET', `/quota-packages/packing-tasks/${taskNo}/allocations`, { token })
{
  const rows = allocDetail.body?.data || []
  const mine = rows.filter((r) => r.receivingNo === receivingNo)
  record('N8d 分配明细含验收单来源(30+剩余)', mine.length === 2 && mine.some((r) => Number(r.reservedQty) === 30),
    JSON.stringify(mine.map((r) => `${r.receivingNo}:${r.reservedQty}`)))
}
const confirmRes = await call('PUT', `/quota-packages/packing-tasks/${taskNo}/confirm`, { token })
expectOk(confirmRes, 'N8e 打包任务确认生成标签', confirmRes.body?.data?.labels?.length + ' labels')
const labelNo = confirmRes.body?.data?.labels?.[0]
const printRes = await call('PUT', `/quota-packages/labels/${labelNo}/print`, { token })
expectOk(printRes, 'N8f 标签打印', '')

// ===== N9. 低值定数包 唯一码/UDI 追溯 =====
const labelTrace = await call('GET', `/udi-traceability/records?page=1&size=10&keyword=${encodeURIComponent(labelNo)}`, { token })
{
  const rows = labelTrace.body?.data?.rows || []
  const mine = rows.filter((r) => String(r.uniqueCode) === labelNo || String(r.packageLabelNo) === labelNo)
  record('N9a 低值定数包唯一码/UDI 追溯记录', labelTrace.body?.code === 0 && mine.length >= 1,
    JSON.stringify(mine[0] ? { uniqueCode: mine[0].uniqueCode, udiCode: mine[0].udiCode, scope: mine[0].traceScope || mine[0].scope } : null))
}
const quotaStock = await call('GET', `/inventory/quota-package-stock?page=1&size=20&productName=${encodeURIComponent('全流程AB测')}`, { token })
{
  const rows = quotaStock.body?.data?.rows || []
  const mine = rows.find((r) => String(r.packageCode) === newTemplateCode)
  record('N9b 定数包库存查询(打包后)', !!mine && Number(mine.packageCount) === 10, mine ? `packageCount=${mine.packageCount} looseQty=${mine.looseQty}` : '未找到')
}

// ===== E. 高值耗材：收货 → 唯一码 → 计费回传 → 收费明细 =====
const hvRcv = await call('POST', '/receiving-orders', {
  token,
  body: {
    supplierName, warehouseName: 'SPD中心库',
    receivingType: 'normal', isAgent: false, remark: '全流程AB测高值收货',
    items: [{
      productCode: 'HC1348978', productionBatchNo: `HV-${ts}`, productionDate: '2026-08-01',
      expireDate: '2028-12-31', quantity: 2, qualifiedQuantity: 2, unqualifiedQuantity: 0
    }]
  }
})
expectOk(hvRcv, 'E1 高值收货单创建', '')
const hvNo = hvRcv.body?.data?.receivingNo
const hvApprove = await call('PUT', `/receiving-orders/${hvNo}/action`, { token, body: { action: 'approve', opinion: '全流程AB测' } })
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
    receivingType: 'return', isAgent: false, remark: '全流程AB测盘点/调价/召回用收货',
    items: [{
      productCode: NEW_PRODUCT, productionBatchNo: `ST-${ts}`, productionDate: '2026-08-01',
      expireDate: '2028-12-31', quantity: 10, qualifiedQuantity: 10, unqualifiedQuantity: 0
    }]
  }
})
expectOk(rcv3, 'F1 盘点用收货单创建(退货收货)', '')
const rcv3No = rcv3.body?.data?.receivingNo
await call('PUT', `/receiving-orders/${rcv3No}/action`, { token, body: { action: 'approve', opinion: '全流程AB测' } })
const rcv3Detail = await call('GET', `/receiving-orders/${rcv3No}`, { token })
const stBatchNo = rcv3Detail.body?.data?.items?.[0]?.systemBatchNo
const stRes = await call('POST', '/inventory/stocktaking', {
  token, body: { warehouseName: '骨科二级库', systemBatchNo: stBatchNo, actualQty: 7, reason: '全流程AB测盘点' }
})
expectOk(stRes, 'F2 盘点单创建', '')
const stNo = stRes.body?.data?.stocktakingNo
const stApprove = await call('PUT', `/inventory/stocktaking/${stNo}/approve`, { token })
expectOk(stApprove, 'F3 盘点复核(盘亏-3)', '')
const paRes = await call('POST', '/inventory/batch-price-adjustments', {
  token, body: { systemBatchNo: stBatchNo, newUnitPrice: 96, reason: '全流程AB测调价' }
})
expectOk(paRes, 'F4 批次调价创建', '')
const paNo = paRes.body?.data?.adjustmentNo
const paApprove = await call('PUT', `/inventory/batch-price-adjustments/${paNo}/approve`, { token })
expectOk(paApprove, 'F5 调价审批', '')
const recallRes = await call('POST', '/operational-closure/recalls', {
  token, body: { warehouseName: '骨科二级库', productCode: NEW_PRODUCT, quantity: 5, reason: '全流程AB测召回隔离' }
})
expectOk(recallRes, 'F6 召回隔离', '')

// ===== G. 科室申领 =====
const reqRes = await call('POST', '/operational-closure/requisitions', {
  token, body: { deptName: '骨科', warehouseName: '骨科二级库', productCode: NEW_PRODUCT, quantity: 3 }
})
expectOk(reqRes, 'G1 科室申领创建', '')
const reqNo = reqRes.body?.data?.requisitionNo
const reqApprove = await call('PUT', `/operational-closure/requisitions/${reqNo}/action`, { token, body: { action: 'approve' } })
expectOk(reqApprove, 'G2 科室申领审批', '')

// ===== H. 查询面与权限回归 =====
const fo = await call('GET', '/system/field-options?fieldKey=receiving_type', { token })
expectOk(fo, 'H1 字段管理选项(收货类型)', (fo.body?.data || []).length + ' 项')
const eventsRes = await call('GET', '/inventory/events?page=1&size=20', { token })
expectOk(eventsRes, 'H2 库存交易流水', eventsRes.body?.data?.total + ' 条')
const udiRes = await call('GET', `/udi-traceability/records?page=1&size=10&keyword=${encodeURIComponent(codes[0] || '')}`, { token })
expectOk(udiRes, 'H3 UDI 追溯查询(高值)', udiRes.body?.data?.total + ' 条')
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
