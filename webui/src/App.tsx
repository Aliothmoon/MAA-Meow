import { useCallback, useEffect, useState } from 'react'
import { Button, Empty, Spin } from '@douyinfe/semi-ui'
import { checkSession, logout, type SessionCheck } from '@/api/client'
import { Dashboard } from '@/components/Dashboard'
import { PairView } from '@/components/PairView'
import { t } from '@/i18n'

type Session = SessionCheck | 'checking'

export function App() {
  const [session, setSession] = useState<Session>('checking')

  const recheck = useCallback(() => {
    setSession('checking')
    void checkSession().then(setSession)
  }, [])

  useEffect(recheck, [recheck])

  const onUnauthorized = useCallback(() => setSession('unpaired'), [])

  const onLogout = useCallback(async () => {
    await logout()
    setSession('unpaired')
  }, [])

  switch (session) {
    case 'checking':
      return (
        <div className="flex min-h-dvh items-center justify-center">
          <Spin size="large" tip={t.checking} />
        </div>
      )
    case 'offline':
      return (
        <div className="flex min-h-dvh items-center justify-center p-4">
          <Empty title={t.offlineTitle} description={t.offlineDesc}>
            <Button theme="solid" onClick={recheck}>
              {t.retry}
            </Button>
          </Empty>
        </div>
      )
    case 'unpaired':
      return <PairView onPaired={() => setSession('paired')} />
    case 'paired':
      return <Dashboard onUnauthorized={onUnauthorized} onLogout={onLogout} />
  }
}
