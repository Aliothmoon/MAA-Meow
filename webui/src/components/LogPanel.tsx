import { useLayoutEffect, useRef, useState } from 'react'
import { Button, Card, Empty } from '@douyinfe/semi-ui'
import { IconArrowDown } from '@douyinfe/semi-icons'
import type { WebLogItem } from '@/api/types'
import { t } from '@/i18n'
import { CardTitle } from './CardTitle'

const time = new Intl.DateTimeFormat(undefined, {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
})

// 离底部这么近仍算贴底，新日志来了继续跟随
const STICK_THRESHOLD = 32

function LogRow({ log }: { log: WebLogItem }) {
  return (
    <div className="grid grid-cols-[auto_1fr] gap-x-3 px-4 py-0.5 text-[13px] leading-6">
      <span className="text-(--semi-color-text-2) tabular-nums">{time.format(log.time)}</span>
      <div className="min-w-0">
        <span className={`log-${log.color} break-words whitespace-pre-wrap`}>{log.content}</span>
        {log.tooltip && (
          <details className="text-xs text-(--semi-color-text-2)">
            <summary className="cursor-pointer select-none">{t.details}</summary>
            <pre className="m-0 mt-1 font-[inherit] break-words whitespace-pre-wrap">{log.tooltip}</pre>
          </details>
        )}
      </div>
    </div>
  )
}

export function LogPanel({ logs, className }: { logs: WebLogItem[]; className?: string }) {
  const scroller = useRef<HTMLDivElement>(null)
  const stick = useRef(true)
  const [atBottom, setAtBottom] = useState(true)

  const onScroll = () => {
    const el = scroller.current
    if (!el) return
    const bottom = el.scrollHeight - el.scrollTop - el.clientHeight < STICK_THRESHOLD
    stick.current = bottom
    setAtBottom(bottom)
  }

  const scrollToBottom = () => {
    const el = scroller.current
    if (el) el.scrollTop = el.scrollHeight
  }

  useLayoutEffect(() => {
    if (stick.current) scrollToBottom()
  }, [logs])

  return (
    <Card
      className={`flex flex-col ${className ?? ''}`}
      title={<CardTitle extra={logs.length}>{t.logsTitle}</CardTitle>}
      headerExtraContent={
        !atBottom && (
          <Button size="small" icon={<IconArrowDown />} onClick={scrollToBottom}>
            {t.jumpToLatest}
          </Button>
        )
      }
      bodyStyle={{ padding: 0, flex: 1, minHeight: 0, display: 'flex' }}
    >
      <div ref={scroller} onScroll={onScroll} className="min-h-0 flex-1 overflow-y-auto py-2">
        {logs.length === 0 ? (
          <Empty className="pt-12" description={t.logsEmpty} />
        ) : (
          logs.map((log) => <LogRow key={log.id} log={log} />)
        )}
      </div>
    </Card>
  )
}
