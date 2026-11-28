import { mockEnabled } from './mock'

export type SessionCheck = 'paired' | 'unpaired' | 'offline'

export async function checkSession(): Promise<SessionCheck> {
  if (mockEnabled) return 'paired'
  try {
    const res = await fetch('/api/snapshot', { cache: 'no-store' })
    if (res.status === 401) return 'unpaired'
    return res.ok ? 'paired' : 'offline'
  } catch {
    return 'offline'
  }
}

export type PairResult = 'ok' | 'invalid_code' | 'no_code' | 'network'

export async function pair(code: string, name: string): Promise<PairResult> {
  try {
    const res = await fetch('/api/pair', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code, name }),
    })
    if (res.ok) return 'ok'
    if (res.status === 401) return 'invalid_code'
    if (res.status === 410) return 'no_code'
    return 'network'
  } catch {
    return 'network'
  }
}

export async function logout(): Promise<void> {
  await fetch('/api/logout', { method: 'POST' }).catch(() => undefined)
}

/** 配对列表里给 App 端看的名字，例如 Chrome · Windows */
export function browserLabel(ua = navigator.userAgent): string {
  const browser =
    /Edg\//.test(ua) ? 'Edge'
    : /Firefox\//.test(ua) ? 'Firefox'
    : /Chrome\//.test(ua) ? 'Chrome'
    : /Safari\//.test(ua) ? 'Safari'
    : 'Browser'
  const os =
    /Windows/.test(ua) ? 'Windows'
    : /Android/.test(ua) ? 'Android'
    : /iPhone|iPad/.test(ua) ? 'iOS'
    : /Mac OS X/.test(ua) ? 'macOS'
    : /Linux/.test(ua) ? 'Linux'
    : ''
  return os ? `${browser} · ${os}` : browser
}
