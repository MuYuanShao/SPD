import type { PackageLabelRow } from '../api/quotaPackages'

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

/**
 * 优先使用工作站已注入的 Zebra Browser Print；未安装时降级为浏览器打印。
 */
export async function printQuotaLabel(row: PackageLabelRow) {
  if (window.BrowserPrint) {
    const printer = await getDefaultPrinter(window.BrowserPrint)
    await sendToPrinter(printer, buildQuotaLabelZpl(row))
    return printer.name || printer.uid || 'Zebra 打印机'
  }

  printQuotaLabelInBrowser(row)
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

function buildQuotaLabelZpl(row: PackageLabelRow) {
  const labelNo = zplText(row.labelNo, 40)
  const productName = zplText(row.productName, 28)
  const templateName = zplText(row.templateName, 28)
  const warehouseName = zplText(row.warehouseName, 28)
  const quantity = zplText(String(row.packageQuantity), 16)
  const batches = zplText(row.sourceBatches || '-', 42)
  return `^XA
^CI28
^PW600
^LL400
^LH0,0
^FO30,24^A0N,34,34^FDQuota Package^FS
^FO30,68^A0N,26,26^FD${labelNo}^FS
^FO30,105^BY2,2,70^BCN,70,Y,N,N^FD${labelNo}^FS
^FO30,195^A0N,22,22^FDProduct: ${productName}^FS
^FO30,225^A0N,22,22^FDTemplate: ${templateName}^FS
^FO30,255^A0N,22,22^FDWarehouse: ${warehouseName}^FS
^FO30,285^A0N,22,22^FDQty: ${quantity}^FS
^FO30,315^A0N,20,20^FDBatch: ${batches}^FS
^FO30,350^A0N,18,18^FDPrinted by SPD^FS
^XZ`
}
function printQuotaLabelInBrowser(row: PackageLabelRow) {
  const printWindow = window.open('', '_blank', 'popup,width=720,height=560')
  if (!printWindow) {
    throw new Error('打印窗口被浏览器拦截，请允许本站弹出窗口后重试')
  }

  const printDocument = printWindow.document
  printDocument.title = `定数包标签 ${row.labelNo}`
  const style = printDocument.createElement('style')
  style.textContent = `
    @page { size: 100mm 70mm; margin: 4mm; }
    body { margin: 0; color: #111827; font-family: "Microsoft YaHei", sans-serif; }
    main { width: 84mm; min-height: 54mm; border: 1.5px solid #111827; padding: 4mm; }
    h1 { margin: 0 0 2mm; font-size: 18px; text-align: center; }
    .code { border-block: 1px solid #111827; padding: 2mm 0; font: 700 20px Consolas, monospace; text-align: center; }
    dl { display: grid; grid-template-columns: 22mm 1fr; gap: 1.5mm 2mm; margin-top: 3mm; font-size: 12px; }
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
  ;[
    ['商品', row.productName],
    ['模板', row.templateName],
    ['库房', row.warehouseName],
    ['包内数量', row.packageQuantity],
    ['来源批次', row.sourceBatches || '-']
  ].forEach(([name, value]) => {
    const term = printDocument.createElement('dt')
    const description = printDocument.createElement('dd')
    term.textContent = String(name)
    description.textContent = String(value)
    details.append(term, description)
  })
  label.append(title, code, details)
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
