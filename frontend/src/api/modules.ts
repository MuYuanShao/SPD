import { getData } from './http'

export interface SpdModule {
  code: string
  name: string
  features: string[]
}

export async function fetchModules() {
  return getData<SpdModule[]>('/modules')
}
