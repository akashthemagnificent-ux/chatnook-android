// Route-level error and not-found states (spec §3.21). No stack traces to users.
import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Compass, TriangleAlert } from 'lucide-react'
import { t } from '../lib/i18n'
import { Button, Card, CardContent } from './ui/base'
import { Aurora } from './fx/backgrounds'
import { BorderBeam, SpotlightCard } from './fx/surfaces'

export function NotFound() {
  return (
    <main className="grain relative grid min-h-full place-items-center overflow-hidden bg-base p-6">
      <Aurora dim />
      <SpotlightCard className="relative w-full max-w-md rounded-2xl">
        <BorderBeam />
        <Card className="rounded-2xl border-line/10 bg-surface/85 shadow-pop backdrop-blur-xl">
          <CardContent className="flex flex-col items-center gap-3 p-8 text-center">
            <span className="grid size-14 place-items-center rounded-2xl border border-brand/20 bg-brand/12 text-brand shadow-glow">
              <Compass className="size-6" />
            </span>
            <h1 className="text-title font-semibold">{t('errors.notFoundTitle')}</h1>
            <p className="text-sm text-ink-secondary">{t('errors.notFoundBody')}</p>
            <Button asChild className="mt-2">
              <a href="#/">{t('errors.goHome')}</a>
            </Button>
          </CardContent>
        </Card>
      </SpotlightCard>
    </main>
  )
}

type BoundaryState = { failed: boolean }

export class ErrorBoundary extends Component<{ children: ReactNode }, BoundaryState> {
  state: BoundaryState = { failed: false }

  static getDerivedStateFromError(): BoundaryState {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('ChatNook UI error', error, info.componentStack)
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <main className="grain relative grid min-h-full place-items-center overflow-hidden bg-base p-6">
        <Aurora dim />
        <SpotlightCard glow={0.1} className="relative w-full max-w-md rounded-2xl">
          <BorderBeam />
          <Card className="rounded-2xl border-line/10 bg-surface/85 shadow-pop backdrop-blur-xl">
            <CardContent className="flex flex-col items-center gap-3 p-8 text-center">
              <span className="grid size-14 place-items-center rounded-2xl border border-danger/20 bg-danger/10 text-danger">
                <TriangleAlert className="size-6" />
              </span>
              <h1 className="text-title font-semibold">{t('errors.title')}</h1>
              <p className="text-sm text-ink-secondary">{t('errors.body')}</p>
              <Button className="mt-2" onClick={() => window.location.reload()}>{t('common.retry')}</Button>
            </CardContent>
          </Card>
        </SpotlightCard>
      </main>
    )
  }
}
