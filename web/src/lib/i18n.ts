import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from '../locales/en.json'

// en-US only for now; every user-facing string goes through this layer (spec §10).
void i18n.use(initReactI18next).init({
  resources: { en: { translation: en } },
  lng: 'en',
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
  returnNull: false,
})

/** Plain function form so constants and non-component modules can translate too. */
export function t(key: string, options?: Record<string, string | number>): string {
  return String(i18n.t(key, options ?? {}))
}

export default i18n
