import type { CapacitorConfig } from '@capacitor/cli'

// A separate id from the native app (com.chatnook.app) so both can be installed
// during the redesign. Deep links are not used: the app is hash-routed and the
// auth redirect is handled inside the webview.
const config: CapacitorConfig = {
  appId: 'com.chatnook.web',
  appName: 'ChatNook',
  webDir: 'dist',
  android: {
    allowMixedContent: false,
    backgroundColor: '#0b0b0d',
  },
  server: {
    androidScheme: 'https',
  },
}

export default config
