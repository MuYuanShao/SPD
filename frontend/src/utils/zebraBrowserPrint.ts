import type { PackageLabelRow } from '../api/quotaPackages'
import { fetchPrintTemplate, parseTemplateFields, type PrintTemplateField } from '../api/printTemplates'

type ZebraPrinterDevice = {
  name?: string
  uid?: string
  send: (data: string, success: () => void, error: (message?: unknown) => void) => void
}

type BrowserPrintApi = {
  getDefaultDevice: (
    type: 'printer',
    success: (device: ZebraPrinterDevice | null) => void,
    error: (message?: unknown) => void
  ) => void
}

declare global {
  interface Window {
    BrowserPrint?: BrowserPrintApi
  }
}

export interface QuotaLabelPrintConfig {
  fields: PrintTemplateField[]
  paperWidthMm: number
  paperHeightMm: number
}

/**
 * 读取当前启用的定数包标签打印模板配置，加载失败时回退为默认模板。
 */
export async function loadQuotaLabelPrintConfig(): Promise<QuotaLabelPrintConfig> {
  try {
    const template = await fetchPrintTemplate('quota_label')
    if (template) {
      return {
        fields: parseTemplateFields(template.fieldsJson),
        paperWidthMm: Number(template.paperWidthMm) || 40,
        paperHeightMm: Number(template.paperHeightMm) || 60
      }
    }
  } catch {
    // 配置不可用时使用默认模板
  }
  return defaultPrintConfig()
}

export function defaultPrintConfig(): QuotaLabelPrintConfig {
  return {
    fields: [
      { code: 'labelNo', label: '标签号', enabled: true },
      { code: 'productName', label: '商品', enabled: true },
      { code: 'templateName', label: '定数包模板', enabled: true },
      { code: 'warehouseName', label: '库房', enabled: true },
      { code: 'quantity', label: '包内数量', enabled: true },
      { code: 'batches', label: '来源批次', enabled: true },
      { code: 'footer', label: '页脚', enabled: true, value: 'Printed by SPD' }
    ],
    paperWidthMm: 40,
    paperHeightMm: 60
  }
}

function fieldValue(field: PrintTemplateField, row: PackageLabelRow) {
  if (field.value !== undefined) return field.value
  return switchValue(field.code, row)
}

function switchValue(code: string, row: PackageLabelRow) {
  switch (code) {
    case 'labelNo':
      return row.labelNo
    case 'productName':
      return row.productName
    case 'templateName':
      return row.templateName
    case 'warehouseName':
      return row.warehouseName
    case 'quantity':
      return String(row.packageQuantity)
    case 'batches':
      return row.sourceBatches || '-'
    default:
      return '-'
  }
}

/**
 * 优先使用工作站已注入的 Zebra Browser Print；未安装时降级为浏览器打印。
 * 打印字段与纸张尺寸取自打印模板调整中的配置。
 */
export async function printQuotaLabel(row: PackageLabelRow) {
  const config = await loadQuotaLabelPrintConfig()
  if (window.BrowserPrint) {
    const printer = await getDefaultPrinter(window.BrowserPrint)
    await sendToPrinter(printer, buildQuotaLabelZpl(row, config))
    return printer.name || printer.uid || 'Zebra 打印机'
  }

  printQuotaLabelInBrowser(row, config)
  return '浏览器打印窗口'
}

function getDefaultPrinter(browserPrint: BrowserPrintApi) {
  return new Promise<ZebraPrinterDevice>((resolve, reject) => {
    browserPrint.getDefaultDevice(
      'printer',
      (device) => {
        if (device) {
          resolve(device)
        } else {
          reject(new Error('未找到默认 Zebra 打印机，请在 Zebra Browser Print 中配置默认打印机'))
        }
      },
      (message) => reject(new Error(formatBrowserPrintError(message, '获取 Zebra 默认打印机失败')))
    )
  })
}

function sendToPrinter(printer: ZebraPrinterDevice, zpl: string) {
  return new Promise<void>((resolve, reject) => {
    printer.send(
      zpl,
      () => resolve(),
      (message) => reject(new Error(formatBrowserPrintError(message, '发送 Zebra 打印任务失败')))
    )
  })
}

function buildQuotaLabelZpl(row: PackageLabelRow, config: QuotaLabelPrintConfig) {
  const enabled = new Set(config.fields.filter((field) => field.enabled).map((field) => field.code))
  const labelNo = zplText(row.labelNo, 40)
  const width = Math.max(Math.round(config.paperWidthMm * 8), 200)
  const height = Math.max(Math.round(config.paperHeightMm * 8), 200)
  const lines: string[] = []
  let y = 24
  if (enabled.has('labelNo')) {
    lines.push(`^FO16,${y}^A0N,18,18^FD${labelNo}^FS`)
    y += 36
  }
  if (enabled.has('labelNo')) {
    lines.push(`^FO16,${y}^BY1,2,56^BCN,56,Y,N,N^FD${labelNo}^FS`)
    y += 76
  }
  const detailFields = config.fields.filter(
    (field) => field.enabled && field.code !== 'labelNo' && field.code !== 'footer'
  )
  for (const field of detailFields) {
    const text = zplText(`${field.label}: ${fieldValue(field, row)}`, 42)
    lines.push(`^FO16,${y}^A0N,16,16^FB${width - 32},2,0,L,0^FD${text}^FS`)
    y += 36
  }
  const footer = config.fields.find((field) => field.code === 'footer' && field.enabled)
  if (footer) {
    lines.push(`^FO16,${y + 6}^A0N,14,14^FD${zplText(fieldValue(footer, row), 42)}^FS`)
  }
  return `^XA
^CI28
^PW${width}
^LL${height}
^LH0,0
${lines.join('\n')}
^XZ`
}

function printQuotaLabelInBrowser(row: PackageLabelRow, config: QuotaLabelPrintConfig) {
  const printWindow = window.open('', '_blank', 'popup,width=720,height=560')
  if (!printWindow) {
    throw new Error('打印窗口被浏览器拦截，请允许本站弹出窗口后重试')
  }

  const enabledFields = config.fields.filter((field) => field.enabled)
  const printDocument = printWindow.document
  printDocument.title = `定数包标签 ${row.labelNo}`
  const style = printDocument.createElement('style')
  style.textContent = `
    @page { size: ${config.paperWidthMm}mm ${config.paperHeightMm}mm; margin: 0; }
    * { box-sizing: border-box; }
    body { margin: 0; color: #111827; font-family: "Microsoft YaHei", sans-serif; }
    main { width: ${config.paperWidthMm}mm; min-height: ${config.paperHeightMm}mm; padding: 2mm; }
    h1 { margin: 0 0 1mm; font-size: 9px; text-align: center; }
    .code { border-block: 1px solid #111827; padding: 1mm 0; font: 700 10px Consolas, monospace; overflow-wrap: anywhere; text-align: center; }
    dl { display: grid; grid-template-columns: minmax(8mm, 30%) minmax(0, 1fr); gap: 1mm; margin: 2mm 0 0; font-size: 8px; line-height: 1.25; }
    dt { color: #4b5563; }
    dd { margin: 0; font-weight: 600; overflow-wrap: anywhere; }
  `
  const label = printDocument.createElement('main')
  const title = printDocument.createElement('h1')
  title.textContent = '院内 SPD 定数包标签'
  const code = printDocument.createElement('div')
  code.className = 'code'
  code.textContent = row.labelNo
  const details = printDocument.createElement('dl')
  enabledFields
    .filter((field) => field.code !== 'labelNo')
    .forEach((field) => {
      const term = printDocument.createElement('dt')
      const description = printDocument.createElement('dd')
      term.textContent = field.label
      description.textContent = fieldValue(field, row)
      details.append(term, description)
    })
  label.append(title)
  if (enabledFields.some(field => field.code === 'labelNo')) label.append(code)
  label.append(details)
  printDocument.head.append(style)
  printDocument.body.append(label)
  printWindow.focus()
  printWindow.print()
  printWindow.close()
}

function zplText(value: string, maxLength: number) {
  return value
    .replace(/[\^~]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, maxLength)
}

function formatBrowserPrintError(message: unknown, fallback: string) {
  if (typeof message === 'string' && message.trim()) {
    return `${fallback}：${message.trim()}`
  }
  if (message instanceof Error && message.message) {
    return `${fallback}：${message.message}`
  }
  return fallback
}
