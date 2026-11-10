import type { CapacitorConfig } from '@capacitor/cli'

// The same package id as the native prototype, so this build updates ChatNook in
// place instead of installing a second app. It also has to match the Firebase
// Android app, or push registration is rejected.
const config: CapacitorConfig = {
  appId: 'com.chatnook.app',
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
