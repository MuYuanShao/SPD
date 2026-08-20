import { getData } from './http'

export interface FeatureMetadata {
  code: string
  title: string
  description: string
  capabilities: string[]
}

/**
 * 获取功能元数据
 * @param code - 功能编码
 * @returns 功能元数据（标题、描述、能力列表）
 */
export async function fetchFeatureMetadata(code: string) {
  return getData<FeatureMetadata>(`/features/${code}`)
}
