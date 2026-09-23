import assert from 'node:assert/strict'
import { build } from 'esbuild'
const bundled = await build({ entryPoints: ['frontend/src/utils/businessListSort.ts'], bundle: true, write: false, format: 'esm', platform: 'node' })
const { sortBusinessRows, pinyinInitials, collectSortedRows } = await import(`data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString('base64')}`)
assert.equal(pinyinInitials('一次性使用橡胶手套'), 'ycxsyxjst')
assert.equal(pinyinInitials('重庆医院'), 'cqyy')
const rows = [
  { productName: '一次性使用橡胶手套', spec: '7.0' },
  { productName: '一次性使用医用手套', spec: '6.0' },
  { productName: '一次性使用橡胶手套', spec: '6.0' },
  { productName: '一次性使用橡胶手套', spec: '6.0' },
  { productName: '一次使用采血管', spec: '5ml' }
]
const sorted = sortBusinessRows(rows)
assert.equal(sorted.length, rows.length)
assert.equal(sorted[0].productName, '一次使用采血管')
assert.deepEqual(sorted.slice(1, 4).map(row => row.spec), ['6.0', '6.0', '7.0'])
assert.notDeepEqual(rows, sorted)
const punctuation = [{ name: '纱布', spec: '6.0 ' }, { name: '纱布', spec: '6.0' }, { name: '纱布', spec: '6.0，' }, { spec: '6.0', name: '纱布' }]
const result = sortBusinessRows(punctuation)
assert.equal(result.length, 4)
const exact = result.map(row => row.spec)
assert.equal(exact.filter(value => value === '6.0').length, 2)
assert.equal(exact.lastIndexOf('6.0') - exact.indexOf('6.0'), 1)
assert.ok(exact.includes('6.0 '))
assert.ok(exact.includes('6.0，'))
const full = Array.from({ length: 405 }, (_, index) => ({ productName: index < 400 ? '医用手套' : '采血管', spec: String(index) }))
let calls = 0
const read = async (page, size) => { calls++; return { rows: full.slice((page - 1) * size, page * size), total: full.length, page, size } }
const all = await collectSortedRows({ rows: full.slice(0, 20), total: full.length, page: 1, size: 20 }, read)
assert.equal(all.length, 405)
assert.equal(all[0].productName, '采血管')
assert.equal(calls, 3)
await assert.rejects(() => collectSortedRows({ rows: [], total: 3 }, async () => ({ rows: [], total: 3, size: 20 })), /分页数据不完整/)
console.log('PASS: pinyin initials, duplicates, exact whitespace/punctuation, stable fields, and cross-page ordering')
