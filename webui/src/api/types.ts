// 与 app/.../web/api/WebModels.kt 一一对应，改字段两边一起改

export type ExecutionState = 'IDLE' | 'STARTING' | 'RUNNING' | 'STOPPING' | 'ERROR'
export type BackendState = 'Disconnected' | 'Connecting' | 'Connected' | 'Died' | 'Error'
export type RunMode = 'FOREGROUND' | 'BACKGROUND'
export type LaunchPhase = 'DevicePrep' | 'Counting' | 'Preparing' | 'Starting'
export type TaskRunStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'ERROR'

export type LogColor =
  | 'DEFAULT'
  | 'INFO'
  | 'SUCCESS'
  | 'WARNING'
  | 'ERROR'
  | 'TRACE'
  | 'RARE'
  | 'STAR_1'
  | 'STAR_2'
  | 'STAR_3'
  | 'STAR_4'
  | 'STAR_5'
  | 'STAR_6'
  | 'ROBOT'
  | 'ROGUELIKE_SUCCESS'
  | 'ROGUELIKE_COMBAT'
  | 'ROGUELIKE_EMERGENCY'
  | 'ROGUELIKE_BOSS'
  | 'ROGUELIKE_ABANDON'

export interface WebAppInfo {
  version: string
  device: string
}

export interface WebLaunch {
  name: string
  phase: LaunchPhase
  remainingSeconds?: number
}

export interface WebStatus {
  execution: ExecutionState
  backend: BackendState
  runMode: RunMode
  launch?: WebLaunch
  runDeadline?: number
}

export interface WebChainNode {
  id: string
  name: string
  enabled: boolean
  type?: string
  run?: TaskRunStatus
}

export interface WebChain {
  profileId: string
  profileName: string
  nodes: WebChainNode[]
}

export interface WebLogItem {
  id: number
  time: number
  content: string
  level: string
  color: LogColor
  tooltip?: string
}

export interface WebSnapshot {
  app: WebAppInfo
  status: WebStatus
  chain: WebChain
  logs: WebLogItem[]
}

export type WebEvent =
  | ({ type: 'snapshot' } & WebSnapshot)
  | { type: 'status'; status: WebStatus }
  | { type: 'chain'; chain: WebChain }
  | { type: 'logs'; items: WebLogItem[]; reset: boolean }
