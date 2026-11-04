import { useRef, useState } from 'react'
import { Button, Card, PinCode, Typography } from '@douyinfe/semi-ui'
import { browserLabel, pair, type PairResult } from '@/api/client'
import { t } from '@/i18n'

const CODE_LENGTH = 6

const errorText: Record<Exclude<PairResult, 'ok'>, string> = {
  invalid_code: t.pairInvalid,
  no_code: t.pairNoCode,
  network: t.pairNetwork,
}

export function PairView({ onPaired }: { onPaired: () => void }) {
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const pin = useRef<PinCode>(null)

  const submit = async (value: string) => {
    if (value.length !== CODE_LENGTH || busy) return
    setBusy(true)
    setError(null)
    const result = await pair(value, browserLabel())
    setBusy(false)
    if (result === 'ok') {
      onPaired()
      return
    }
    setError(errorText[result])
    setCode('')
    // 等解除禁用后再聚焦
    setTimeout(() => pin.current?.focus(0))
  }

  return (
    <div className="flex min-h-dvh items-center justify-center p-4">
      <Card className="w-full max-w-sm" bodyStyle={{ padding: 28 }}>
        <div className="flex flex-col items-center gap-4 text-center">
          <img src="/logo.png" alt="" className="size-16 rounded-2xl" />
          <div className="flex flex-col gap-1">
            <Typography.Title heading={4}>{t.pairTitle}</Typography.Title>
            <Typography.Text type="tertiary">{t.pairDesc}</Typography.Text>
          </div>
          <PinCode
            ref={pin}
            count={CODE_LENGTH}
            format="number"
            size="large"
            value={code}
            onChange={setCode}
            onComplete={submit}
            disabled={busy}
          />
          <Typography.Text type="danger" className="min-h-5">
            {error}
          </Typography.Text>
          <Button
            theme="solid"
            size="large"
            block
            loading={busy}
            disabled={code.length !== CODE_LENGTH}
            onClick={() => submit(code)}
          >
            {t.pairSubmit}
          </Button>
        </div>
      </Card>
    </div>
  )
}
