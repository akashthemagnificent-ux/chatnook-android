// Route-level error and not-found states (spec §3.21). No stack traces to users.
import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Compass, TriangleAlert } from 'lucide-react'
import { t } from '../lib/i18n'
import { Button, Card, CardContent } from './ui/base'

export function NotFound() {
  return (
    <main className="grid min-h-full place-items-center bg-base p-6">
      <Card className="w-full max-w-md text-center">
        <CardContent className="flex flex-col items-center gap-3 p-8">
          <span className="grid size-12 place-items-center rounded-xl bg-brand/12 text-brand">
            <Compass className="size-6" />
          </span>
          <h1 className="text-title font-semibold">{t('errors.notFoundTitle')}</h1>
          <p className="text-sm text-ink-secondary">{t('errors.notFoundBody')}</p>
          <Button asChild className="mt-2">
            <a href="#/">{t('errors.goHome')}</a>
          </Button>
        </CardContent>
      </Card>
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
      <main className="grid min-h-full place-items-center bg-base p-6">
        <Card className="w-full max-w-md text-center">
          <CardContent className="flex flex-col items-center gap-3 p-8">
            <span className="grid size-12 place-items-center rounded-xl bg-danger/12 text-danger">
              <TriangleAlert className="size-6" />
            </span>
            <h1 className="text-title font-semibold">{t('errors.title')}</h1>
            <p className="text-sm text-ink-secondary">{t('errors.body')}</p>
            <Button className="mt-2" onClick={() => window.location.reload()}>{t('common.retry')}</Button>
          </CardContent>
        </Card>
      </main>
    )
  }
}
