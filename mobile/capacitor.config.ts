import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'com.hospital.spd.mobile.test',
  appName: 'SPD 联调测试',
  webDir: 'dist',
  android: { allowMixedContent: false, minWebViewVersion: 66 },
  server: { errorPath: 'webview-error.html' },
  plugins: {
    CapacitorHttp: { enabled: true }
  }
}
export default config
