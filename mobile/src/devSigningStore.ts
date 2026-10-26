/** Browser development fixture only. Production builds never register this implementation. */
export class DevSigningStore {
  async get({ key }: { key: string }) { return { value: sessionStorage.getItem('spd-dev-signing:' + key) } }
  async set({ key, value }: { key: string; value: string }) { sessionStorage.setItem('spd-dev-signing:' + key, value) }
  async remove({ key }: { key: string }) { sessionStorage.removeItem('spd-dev-signing:' + key) }
}
