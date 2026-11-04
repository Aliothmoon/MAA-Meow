import type { ReactNode } from 'react'
import { Typography } from '@douyinfe/semi-ui'

/** Card 的 title 传节点时不套标题样式，自己补上，右侧可带一个淡色计数 */
export function CardTitle({ children, extra }: { children: ReactNode; extra?: ReactNode }) {
  return (
    <div className="flex items-baseline gap-2">
      <Typography.Title heading={6}>{children}</Typography.Title>
      {extra != null && (
        <Typography.Text type="tertiary" size="small">
          {extra}
        </Typography.Text>
      )}
    </div>
  )
}
