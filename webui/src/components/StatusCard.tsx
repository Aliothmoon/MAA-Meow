import { Card, Descriptions, Tag, Typography } from '@douyinfe/semi-ui'
import type { TagColor } from '@douyinfe/semi-ui/lib/es/tag'
import type { BackendState, ExecutionState, WebStatus } from '@/api/types'
import { fill, t } from '@/i18n'
import { CardTitle } from './CardTitle'

const executionColor: Record<ExecutionState, TagColor> = {
  IDLE: 'grey',
  STARTING: 'amber',
  RUNNING: 'green',
  STOPPING: 'amber',
  ERROR: 'red',
}

const backendColor: Record<BackendState, TagColor> = {
  Connected: 'green',
  Connecting: 'amber',
  Disconnected: 'grey',
  Died: 'red',
  Error: 'red',
}

const clock = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' })

export function StatusCard({ status }: { status: WebStatus }) {
  const { launch } = status
  const rows = [
    {
      key: t.backendLabel,
      value: (
        <Tag color={backendColor[status.backend]} size="small">
          {t.backend[status.backend]}
        </Tag>
      ),
    },
    { key: t.runModeLabel, value: t.runMode[status.runMode] },
    ...(status.runDeadline != null
      ? [{ key: t.deadlineLabel, value: clock.format(status.runDeadline) }]
      : []),
  ]

  return (
    <Card
      title={<CardTitle>{t.statusTitle}</CardTitle>}
      headerExtraContent={
        <Tag color={executionColor[status.execution]} size="large" type="solid">
          {t.execution[status.execution]}
        </Tag>
      }
    >
      <div className="flex flex-col gap-3">
        {launch && (
          <div className="rounded-(--semi-border-radius-medium) bg-(--semi-color-fill-0) px-3 py-2">
            <Typography.Text strong>{launch.name}</Typography.Text>
            <Typography.Text type="tertiary">
              {' · '}
              {fill(t.launchPhase[launch.phase], { s: launch.remainingSeconds ?? 0 })}
            </Typography.Text>
          </div>
        )}
        <Descriptions align="left" size="small" data={rows} />
      </div>
    </Card>
  )
}
