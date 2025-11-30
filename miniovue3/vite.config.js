import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  base: '', // 必须设置为部署路径
  plugins: [vue()],
  server: {
    proxy: {
      // 选项写法
      '/minio': {
        target: 'https://minio-spring.eeccc.cc/', // 目标后端服务地址
        changeOrigin: true, // 需要虚拟主机站点
      },
    }
  }
})