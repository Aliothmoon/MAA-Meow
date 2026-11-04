import { useEffect, useReducer, useState } from 'react'
import { mockEnabled, startMock } from './mock'
import type { WebEvent, WebSnapshot } from './types'

// 与 App 端运行日志上限一致，多留一点余量
const MAX_LOGS = 1000

export type Connection = 'connecting' | 'open' | 'reconnecting'

function reduce(state: WebSnapshot | null, event: WebEvent): WebSnapshot | null {
  if (event.type === 'snapshot') {
    return { app: event.app, status: event.status, chain: event.chain, logs: event.logs }
  }
  if (!state) return state
  switch (event.type) {
    case 'status':
      return { ...state, status: event.status }
    case 'chain':
      return { ...state, chain: event.chain }
    case 'logs': {
      const logs = event.reset ? event.items : [...state.logs, ...event.items]
      return { ...state, logs: logs.length > MAX_LOGS ? logs.slice(-MAX_LOGS) : logs }
    }
  }
}

/** 断线自动重连，重连后服务端会重发整份快照 */
export function useEvents(onUnauthorized: () => void) {
  const [data, dispatch] = useReducer(reduce, null)
  const [connection, setConnection] = useState<Connection>('connecting')

  useEffect(() => {
    if (mockEnabled) {
      setConnection('open')
      return startMock(dispatch)
    }
    let socket: WebSocket | null = null
    let timer: ReturnType<typeof setTimeout> | undefined
    let attempt = 0
    let disposed = false

    const connect = () => {
      const scheme = location.protocol === 'https:' ? 'wss' : 'ws'
      socket = new WebSocket(`${scheme}://${location.host}/api/events`)
      socket.onopen = () => {
        attempt = 0
        setConnection('open')
      }
      socket.onmessage = (e) => dispatch(JSON.parse(e.data as string) as WebEvent)
      socket.onclose = (e) => {
        if (disposed) return
        // 1008 + unauthorized：会话被 App 端移除
        if (e.code === 1008 && e.reason === 'unauthorized') {
          onUnauthorized()
          return
        }
        setConnection('reconnecting')
        timer = setTimeout(connect, Math.min(1000 * 2 ** attempt++, 15_000))
      }
    }

    connect()
    return () => {
      disposed = true
      clearTimeout(timer)
      socket?.close()
    }
  }, [onUnauthorized])

  return { data, connection }
}
