const defaultHeaders = {
  code: '供应商编码',
  name: '供应商名称',
  creditCode: '统一社会信用代码',
  type: '类型',
  grade: '等级',
  contactName: '联系人',
  contactPhone: '联系电话',
  email: '邮箱',
  address: '地址'
}

export function downloadCsv(
  fileName: string,
  rows: Record<string, unknown>[],
  emptyHeaderSource: Record<string, unknown> = defaultHeaders
) {
  const headers = Object.keys(rows[0] ?? emptyHeaderSource)
  const escape = (value: unknown) => `"${String(value ?? '').replace(/"/g, '""')}"`
  const content = [
    headers.join(','),
    ...rows.map((row) => headers.map((header) => escape(row[header])).join(','))
  ].join('\n')
  const blob = new Blob(['\ufeff' + content], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

export function downloadCsvContent(fileName: string, content: string) {
  const blob = new Blob(['\ufeff' + content], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

export function csvCell(value: unknown) {
  return `"${String(value ?? '').replace(/"/g, '""')}"`
}

export function parseCsvLine(line: string) {
  const cells: string[] = []
  let current = ''
  let quoted = false
  for (let index = 0; index < line.length; index += 1) {
    const char = line[index]
    const next = line[index + 1]
    if (char === '"' && quoted && next === '"') {
      current += '"'
      index += 1
    } else if (char === '"') {
      quoted = !quoted
    } else if (char === ',' && !quoted) {
      cells.push(current.replace(/^\uFEFF/, '').trim())
      current = ''
    } else {
      current += char
    }
  }
  cells.push(current.replace(/^\uFEFF/, '').trim())
  return cells
}
