import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { HashRouter } from 'react-router-dom'
import '@fontsource-variable/inter'
import './styles/globals.css'
import './lib/i18n'
import App from './App'
import { ThemeProvider } from './lib/theme'
import { TooltipProvider } from './components/ui/overlay'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <TooltipProvider delayDuration={250} skipDelayDuration={400}>
        <HashRouter>
          <App />
        </HashRouter>
      </TooltipProvider>
    </ThemeProvider>
  </StrictMode>,
)
