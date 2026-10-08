import { Card, Empty, Spin, Tag, Typography } from '@douyinfe/semi-ui'
import { IconAlertCircle, IconTickCircle } from '@douyinfe/semi-icons'
import type { WebChain, WebChainNode } from '@/api/types'
import { fill, t } from '@/i18n'
import { CardTitle } from './CardTitle'

function NodeIcon({ node }: { node: WebChainNode }) {
  switch (node.run) {
    case 'IN_PROGRESS':
      return <Spin size="small" />
    case 'COMPLETED':
      return <IconTickCircle className="text-(--semi-color-success)" />
    case 'ERROR':
      return <IconAlertCircle className="text-(--semi-color-danger)" />
    default:
      // 本轮排队中或未开跑：实心为已勾选，空心为未勾选
      return (
        <span
          className={
            node.enabled
              ? 'size-2.5 rounded-full bg-(--semi-color-primary)'
              : 'size-2.5 rounded-full border border-(--semi-color-text-3)'
          }
        />
      )
  }
}

export function ChainCard({ chain }: { chain: WebChain }) {
  const enabled = chain.nodes.filter((it) => it.enabled).length
  return (
    <Card
      title={
        <CardTitle extra={fill(t.tasksEnabled, { n: enabled, total: chain.nodes.length })}>
          {t.tasksTitle}
        </CardTitle>
      }
      headerExtraContent={chain.profileName && <Tag color="blue">{chain.profileName}</Tag>}
      bodyStyle={{ padding: '8px 12px' }}
    >
      {chain.nodes.length === 0 ? (
        <Empty description={t.tasksEmpty} />
      ) : (
        <ul className="m-0 flex list-none flex-col p-0">
          {chain.nodes.map((node) => (
            <li
              key={node.id}
              className={
                'flex h-9 items-center gap-3 rounded-(--semi-border-radius-small) px-2 ' +
                (node.run === 'IN_PROGRESS' ? 'bg-(--semi-color-success-light-default)' : '')
              }
            >
              <span className="flex w-5 shrink-0 justify-center">
                <NodeIcon node={node} />
              </span>
              <Typography.Text
                className="min-w-0 flex-1"
                type={node.enabled ? 'primary' : 'quaternary'}
                ellipsis={{ showTooltip: true }}
              >
                {node.name}
              </Typography.Text>
              <Typography.Text type="tertiary" size="small">
                {node.run && t.run[node.run]}
              </Typography.Text>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}
