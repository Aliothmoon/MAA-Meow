import type { LogColor, WebEvent, WebLogItem } from './types'

// 仅开发：地址带 ?mock 时不连设备，用假数据调界面；生产构建整段被摇掉
export const mockEnabled = import.meta.env.DEV && new URLSearchParams(location.search).has('mock')

const COLORS: LogColor[] = [
  'DEFAULT', 'INFO', 'SUCCESS', 'WARNING', 'ERROR', 'TRACE', 'RARE',
  'STAR_3', 'STAR_4', 'STAR_5', 'STAR_6', 'ROGUELIKE_COMBAT', 'ROGUELIKE_BOSS',
]

let nextId = 1

function log(content: string, color: LogColor = 'DEFAULT', tooltip?: string): WebLogItem {
  return { id: nextId++, time: Date.now(), content, level: 'MESSAGE', color, tooltip }
}

export function startMock(dispatch: (event: WebEvent) => void): () => void {
  dispatch({
    type: 'snapshot',
    app: { version: '0.0.0-dev', device: 'Mock Device' },
    status: {
      execution: 'RUNNING',
      backend: 'Connected',
      runMode: 'BACKGROUND',
      runDeadline: Date.now() + 2 * 3600_000,
    },
    chain: {
      profileId: 'p',
      profileName: '日常',
      nodes: [
        { id: '1', name: '开始唤醒', enabled: true, type: 'WAKE_UP', run: 'COMPLETED' },
        { id: '2', name: '自动公招', enabled: true, type: 'RECRUITING', run: 'COMPLETED' },
        { id: '3', name: '基建换班', enabled: true, type: 'BASE', run: 'ERROR' },
        { id: '4', name: '理智作战', enabled: true, type: 'COMBAT', run: 'IN_PROGRESS' },
        { id: '5', name: '信用收支', enabled: true, type: 'MALL', run: 'PENDING' },
        { id: '6', name: '自动肉鸽', enabled: false, type: 'AUTO_ROGUELIKE' },
      ],
    },
    logs: [
      log('开始任务：开始唤醒', 'INFO'),
      log('已进入游戏', 'SUCCESS'),
      log('公招识别结果：4★ 先锋干员、费用回复', 'STAR_4', '可选标签：先锋干员 / 费用回复 / 近战位'),
      log('基建换班失败：未识别到宿舍入口', 'ERROR'),
      log('开始任务：理智作战', 'INFO'),
    ],
  })

  let tick = 0
  const timer = setInterval(() => {
    tick++
    const color = COLORS[tick % COLORS.length]
    dispatch({
      type: 'logs',
      reset: false,
      items: [log(`第 ${tick} 次作战完成，掉落：固源岩 ×${tick % 4 + 1}`, color)],
    })
  }, 1200)
  return () => clearInterval(timer)
}
