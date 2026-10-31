import type { BackendState, ExecutionState, LaunchPhase, RunMode, TaskRunStatus } from './api/types'

const zh = {
  appName: 'MaaMeow',
  checking: '正在连接…',
  offlineTitle: '连不上 MaaMeow',
  offlineDesc: '请确认手机上的网页控制台已开启，且与本设备在同一网络。',
  retry: '重试',

  pairTitle: '配对浏览器',
  pairDesc: '在手机上打开 MaaMeow 的「设置 → 网页控制台」，生成配对码后在下方输入。',
  pairSubmit: '配对',
  pairInvalid: '配对码不对，请重新输入',
  pairNoCode: '配对码已失效，请在 App 里重新生成',
  pairNetwork: '连接失败，请稍后再试',

  logout: '取消配对',
  connection: {
    connecting: '连接中',
    open: '已连接',
    reconnecting: '重连中',
  },

  statusTitle: '状态',
  execution: {
    IDLE: '空闲',
    STARTING: '启动中',
    RUNNING: '运行中',
    STOPPING: '停止中',
    ERROR: '出错',
  } satisfies Record<ExecutionState, string>,
  backendLabel: '后台服务',
  backend: {
    Disconnected: '未连接',
    Connecting: '连接中',
    Connected: '已连接',
    Died: '已断开',
    Error: '出错',
  } satisfies Record<BackendState, string>,
  runModeLabel: '运行模式',
  runMode: {
    FOREGROUND: '前台',
    BACKGROUND: '后台',
  } satisfies Record<RunMode, string>,
  deadlineLabel: '到点停止',
  launchPhase: {
    DevicePrep: '正在准备设备',
    Counting: '{s} 秒后开始',
    Preparing: '正在准备',
    Starting: '正在启动',
  } satisfies Record<LaunchPhase, string>,

  tasksTitle: '任务',
  tasksEnabled: '已勾选 {n}/{total}',
  tasksEmpty: '当前方案没有任务',
  run: {
    PENDING: '等待中',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    ERROR: '出错',
  } satisfies Record<TaskRunStatus, string>,

  logsTitle: '日志',
  logsEmpty: '暂无日志',
  jumpToLatest: '跳到最新',
  details: '详情',
}

type Messages = typeof zh

const en: Messages = {
  appName: 'MaaMeow',
  checking: 'Connecting…',
  offlineTitle: "Can't reach MaaMeow",
  offlineDesc: 'Make sure the web console is on in the app and this device is on the same network.',
  retry: 'Retry',

  pairTitle: 'Pair this browser',
  pairDesc: 'In MaaMeow on your phone, open Settings → Web console, generate a code and enter it below.',
  pairSubmit: 'Pair',
  pairInvalid: 'Wrong code, try again',
  pairNoCode: 'The code has expired. Generate a new one in the app',
  pairNetwork: 'Connection failed, try again later',

  logout: 'Unpair',
  connection: {
    connecting: 'Connecting',
    open: 'Connected',
    reconnecting: 'Reconnecting',
  },

  statusTitle: 'Status',
  execution: {
    IDLE: 'Idle',
    STARTING: 'Starting',
    RUNNING: 'Running',
    STOPPING: 'Stopping',
    ERROR: 'Error',
  },
  backendLabel: 'Service',
  backend: {
    Disconnected: 'Disconnected',
    Connecting: 'Connecting',
    Connected: 'Connected',
    Died: 'Lost',
    Error: 'Error',
  },
  runModeLabel: 'Run mode',
  runMode: {
    FOREGROUND: 'Foreground',
    BACKGROUND: 'Background',
  },
  deadlineLabel: 'Stops at',
  launchPhase: {
    DevicePrep: 'Preparing the device',
    Counting: 'Starts in {s}s',
    Preparing: 'Preparing',
    Starting: 'Starting',
  },

  tasksTitle: 'Tasks',
  tasksEnabled: '{n}/{total} selected',
  tasksEmpty: 'No tasks in this profile',
  run: {
    PENDING: 'Waiting',
    IN_PROGRESS: 'Running',
    COMPLETED: 'Done',
    ERROR: 'Error',
  },

  logsTitle: 'Log',
  logsEmpty: 'No logs yet',
  jumpToLatest: 'Jump to latest',
  details: 'Details',
}

export const lang = navigator.language.toLowerCase().startsWith('zh') ? 'zh' : 'en'

export const t: Messages = lang === 'zh' ? zh : en

export function fill(template: string, values: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (_, key: string) => String(values[key] ?? ''))
}
