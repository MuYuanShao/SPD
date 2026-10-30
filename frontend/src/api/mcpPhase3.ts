import { getData, postData } from './http'
export interface Phase3Message { messageId: string; documentType: string; sourceId: string; revision: string; state: string; attempts: number; message: string; nextAttempt: string }
export interface Phase3Feedback { messageId: string; settlementId: string; revision: string; supplierCode: string; feedbackType: string; receivedAt: string }
export const fetchPhase3Status = () => getData<{ rows: Phase3Message[] }>('/mcp-phase3/status')
export const fetchPhase3Feedback = () => getData<{ rows: Phase3Feedback[] }>('/mcp-phase3/feedback')
export interface Phase3BackfillRange { supplierCode: string; startDate: string; endDate: string }
export const backfillPhase3 = (range: Phase3BackfillRange) => postData<{ plans: number; settlements: number }>(`/mcp-phase3/backfill?${new URLSearchParams({ size: '100', ...range })}`, {})
export const retryPhase3 = (id: string) => postData(`/mcp-phase3/retry/${encodeURIComponent(id)}`, {})
export const republishPhase3 = (kind: string, source: string) => postData(`/mcp-phase3/republish/${encodeURIComponent(kind)}/${encodeURIComponent(source)}`, {})
