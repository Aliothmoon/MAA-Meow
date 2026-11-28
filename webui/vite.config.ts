import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 开发时先 adb forward tcp:8686 tcp:8686，接口代理到手机上的 App
const target = process.env.MAAMEOW_URL ?? 'http://127.0.0.1:8686'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    proxy: {
      '/api': {
        target,
        ws: true,
        changeOrigin: true,
        // 服务端要求 Origin 与 Host 一致
        configure: (proxy) => {
          proxy.on('proxyReqWs', (req) => req.setHeader('origin', target))
        },
      },
    },
  },
  build: {
    // Gradle 构建时直接产出到 app 的生成目录
    outDir: process.env.WEBUI_OUT_DIR ?? 'dist',
    emptyOutDir: true,
    rolldownOptions: {
      // Semi 的依赖（lottie、markdown 渲染）自带的告警，用不到也会被摇掉
      onwarn(warning, warn) {
        if (warning.id?.includes('node_modules')) return
        warn(warning)
      },
    },
  },
})
