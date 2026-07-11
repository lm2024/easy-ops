import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
        ws: true
      },
      '/ws': {
        target: 'ws://127.0.0.1:8081',
        changeOrigin: true,
        ws: true,
        rewrite: (path) => path
      },
    }
  },
  build: {
    // 直接输出到 nginx 部署包内，使 frontend/nginx 目录自包含（拷走即可运行）
    outDir: 'nginx/dist',
    emptyOutDir: true,
    sourcemap: false
  }
})
