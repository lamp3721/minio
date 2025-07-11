import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
   base: '/view/', // 必须设置为部署路径
  plugins: [vue()],
  server: {
    proxy: {
      // 选项写法
      '/minio': {
        // target: 'http://192.168.0.7:902', // 目标后端服务地址
        target: 'http://192.168.1.177:900', // 目标后端服务地址
        changeOrigin: true, // 需要虚拟主机站点
      },
    }
  }
})