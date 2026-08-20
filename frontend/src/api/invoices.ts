import { getData, postData, putData, type PageResult } from './http'

export interface InvoiceRow {
  invoiceId: number
  invoiceNo: string
  invoiceCode: string
  invoiceNumber: string
  supplierName: string
  invoiceDate: string
  amount: number
  taxAmount: number
  status: string
  settlementNo?: string
  remark?: string
  createTime: string
}

export interface InvoiceCreatePayload {
  supplierId: number
  invoiceCode: string
  invoiceNumber: string
  invoiceDate: string
  amount: number
  taxAmount: number
  remark?: string
}

export function fetchInvoices(params: Record<string, string>) {
  return getData<PageResult<InvoiceRow>>('/invoices', { params })
}

export function fetchInvoiceOptions() {
  return getData<Array<{ supplierId: number; supplierName: string }>>('/invoices/options')
}

export function createInvoice(payload: InvoiceCreatePayload) {
  return postData<{ invoiceNo: string; status: string }>('/invoices', payload)
}

export function verifyInvoice(invoiceNo: string) {
  return putData<{ invoiceNo: string; status: string }>(`/invoices/${encodeURIComponent(invoiceNo)}/verify`)
}
