import type { AppEnvironment } from './mobileApi'

// B 组测试基线：本次测试机实际地址 172.16.2.153/24。
// 真机发布时由原生网络插件读取本机地址，不能把此值作为生产身份判断依据。
export const bGroupNetworkTest = {
  clientIp: '172.16.2.153',
  cidr: '172.16.2.0/24',
  suggestedEnvironment: 'test' as AppEnvironment
}

export function recommendEnvironment(ip = bGroupNetworkTest.clientIp): AppEnvironment {
  return inCidr(ip, bGroupNetworkTest.cidr) ? bGroupNetworkTest.suggestedEnvironment : 'test'
}

function inCidr(ip: string, cidr: string) {
  const [network, prefixText] = cidr.split('/')
  const prefix = Number(prefixText)
  const toInt = (value: string) => value.split('.').reduce((n, part) => (n * 256) + Number(part), 0) >>> 0
  const mask = prefix === 0 ? 0 : (0xffffffff << (32 - prefix)) >>> 0
  return (toInt(ip) & mask) === (toInt(network) & mask)
}
