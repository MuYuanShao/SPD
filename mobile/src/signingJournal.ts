import { Capacitor, registerPlugin } from '@capacitor/core'
import type { SigningJournal, SigningRecord } from './signingFlow'
import type { SigningScope } from './mobileTypes'
import type { PickingJournal, PickingRecord } from './pickingFlow'
import type { ConsumptionJournal, ConsumptionRecord } from './consumptionFlow'

interface SecureStore {
  get(options: { key: string }): Promise<{ value: string | null }>;
  set(options: { key: string; value: string }): Promise<void>;
  remove(options: { key: string }): Promise<void>
}
const store = registerPlugin<SecureStore>('SpdSecureStore', import.meta.env.DEV
  ? { web: () => import('./devSigningStore').then(m => new m.DevSigningStore()) } : {})

/** Durable recovery journal for online requests, encrypted by Android Keystore, scoped to server and account. */
export function signingJournal(scope: SigningScope): SigningJournal {
  if (!Capacitor.isNativePlatform() && !import.meta.env.DEV) throw new Error('签收功能请在安卓 APP 中使用')
  const key = JSON.stringify([scope.baseUrl, scope.serverId, scope.userId])
  return {
    async load() { const { value } = await store.get({ key }); return value ? JSON.parse(value) as SigningRecord : null },
    async save(record) { await store.set({ key, value: JSON.stringify(record) }) },
    async clear() { await store.remove({ key }) }
  }
}
export function pickingJournal(scope: SigningScope): PickingJournal {
  if (!Capacitor.isNativePlatform() && !import.meta.env.DEV) throw new Error('拣选功能请在安卓 APP 中使用')
  const key = JSON.stringify([scope.baseUrl, scope.serverId, scope.userId, 'picking'])
  return {
    async load() { const {value} = await store.get({key}); return value ? JSON.parse(value) as PickingRecord : null },
    async save(record) { await store.set({key, value: JSON.stringify(record)}) },
    async clear() { await store.remove({key}) }
  }
}
export function consumptionJournal(scope: SigningScope): ConsumptionJournal {
  if(!Capacitor.isNativePlatform()&&!import.meta.env.DEV)throw new Error('消耗功能请在安卓 APP 中使用')
  const key=JSON.stringify([scope.baseUrl,scope.serverId,scope.userId,'consumption-package'])
  return {
    async load(){const {value}=await store.get({key});return value?JSON.parse(value) as ConsumptionRecord:null},
    async save(record){await store.set({key,value:JSON.stringify(record)})},
    async clear(){await store.remove({key})}
  }
}
