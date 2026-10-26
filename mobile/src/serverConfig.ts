export type AppEnvironment = 'test' | 'production'
export const DEFAULT_TEST_SERVER = 'http://172.16.2.153:1818/api'

/** HTTP is limited to the explicitly authorized B-group test host. */
export function normalizeServerUrl(input: string, environment: AppEnvironment): string {
  let url: URL
  try { url = new URL(input.trim()) } catch { throw new Error('请输入完整服务器地址，例如 https://spd.example.com/api') }
  if (url.username || url.password || url.search || url.hash) throw new Error('地址不能包含账号、密码、查询参数或片段')
  if (url.protocol !== 'https:' && !(environment === 'test' && url.protocol === 'http:' && url.hostname === '172.16.2.153' && url.port === '1818')) {
    throw new Error('请使用 HTTPS；测试版仅对 172.16.2.153:1818 开放 HTTP')
  }
  url.pathname = url.pathname.replace(/\/+$/, '')
  if (!url.pathname.endsWith('/api')) throw new Error('服务器地址必须以 /api 结尾')
  return url.href.replace(/\/+$/, '')
}
