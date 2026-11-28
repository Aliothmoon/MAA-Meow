import { Button, Dropdown, Spin, Tag, Typography } from '@douyinfe/semi-ui'
import { IconExit, IconMore } from '@douyinfe/semi-icons'
import type { TagColor } from '@douyinfe/semi-ui/lib/es/tag'
import type { Connection } from '@/api/useEvents'
import { useEvents } from '@/api/useEvents'
import { t } from '@/i18n'
import { ChainCard } from './ChainCard'
import { LogPanel } from './LogPanel'
import { StatusCard } from './StatusCard'

const connectionColor: Record<Connection, TagColor> = {
  connecting: 'amber',
  open: 'green',
  reconnecting: 'amber',
}

export function Dashboard({
  onUnauthorized,
  onLogout,
}: {
  onUnauthorized: () => void
  onLogout: () => void
}) {
  const { data, connection } = useEvents(onUnauthorized)

  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-10 border-b border-(--semi-color-border) bg-(--semi-color-bg-0)/85 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-3 px-4">
          <img src="/logo.png" alt="" className="size-8 rounded-lg" />
          <div className="flex min-w-0 flex-col leading-tight">
            <Typography.Text strong>{t.appName}</Typography.Text>
            {data && (
              <Typography.Text type="tertiary" size="small" ellipsis>
                {data.app.device} · {data.app.version}
              </Typography.Text>
            )}
          </div>
          <div className="ml-auto flex items-center gap-1">
            <Tag color={connectionColor[connection]} shape="circle">
              {t.connection[connection]}
            </Tag>
            <Dropdown
              trigger="click"
              position="bottomRight"
              render={
                <Dropdown.Menu>
                  <Dropdown.Item icon={<IconExit />} onClick={onLogout}>
                    {t.logout}
                  </Dropdown.Item>
                </Dropdown.Menu>
              }
            >
              <Button theme="borderless" type="tertiary" icon={<IconMore />} aria-label="menu" />
            </Dropdown>
          </div>
        </div>
      </header>

      {data ? (
        <main className="mx-auto grid max-w-6xl gap-4 p-4 lg:grid-cols-[minmax(0,22rem)_minmax(0,1fr)]">
          <div className="flex flex-col gap-4">
            <StatusCard status={data.status} />
            <ChainCard chain={data.chain} />
          </div>
          <LogPanel
            logs={data.logs}
            className="h-[70dvh] lg:sticky lg:top-18 lg:h-[calc(100dvh-6rem)]"
          />
        </main>
      ) : (
        <div className="flex justify-center p-12">
          <Spin tip={t.checking} />
        </div>
      )}
    </div>
  )
}
