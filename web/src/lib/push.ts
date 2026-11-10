// Native push: register this install with FCM and hand the token to the backend.
// A no-op in the browser, where notifications are not part of the product yet.
import { Capacitor } from '@capacitor/core'
import { PushNotifications } from '@capacitor/push-notifications'
import * as api from '../data/api'

let started = false
let openChannel: ((channelId: string) => void) | null = null

/** Called by the chat provider so a tapped notification can select its channel. */
export function setPushChannelHandler(fn: ((channelId: string) => void) | null) {
  openChannel = fn
}

/** Registers once per install; safe to call on every sign-in. */
export async function initPush(userId: string): Promise<void> {
  if (!Capacitor.isNativePlatform() || started) return
  started = true

  await PushNotifications.addListener('registration', ({ value }) => {
    void api.savePushToken(userId, value).catch(() => {})
  })
  await PushNotifications.addListener('pushNotificationActionPerformed', (event) => {
    const channelId = (event.notification.data as { channel_id?: string } | undefined)?.channel_id
    if (channelId) openChannel?.(channelId)
  })

  let permission = await PushNotifications.checkPermissions()
  if (permission.receive === 'prompt') permission = await PushNotifications.requestPermissions()
  if (permission.receive === 'granted') await PushNotifications.register()
}
