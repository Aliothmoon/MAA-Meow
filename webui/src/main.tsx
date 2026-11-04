// 必须最先引入：React 19 去掉了 Semi 弹层依赖的 API
import '@douyinfe/semi-ui/react19-adapter'
import './index.css'

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { LocaleProvider } from '@douyinfe/semi-ui'
import en_US from '@douyinfe/semi-ui/lib/es/locale/source/en_US'
import zh_CN from '@douyinfe/semi-ui/lib/es/locale/source/zh_CN'
import { App } from './App'
import { lang } from './i18n'

// 跟随系统亮暗色
const dark = window.matchMedia('(prefers-color-scheme: dark)')
const applyTheme = () => {
  if (dark.matches) document.body.setAttribute('theme-mode', 'dark')
  else document.body.removeAttribute('theme-mode')
}
applyTheme()
dark.addEventListener('change', applyTheme)

document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <LocaleProvider locale={lang === 'zh' ? zh_CN : en_US}>
      <App />
    </LocaleProvider>
  </StrictMode>,
)
