import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      // 字符串简写写法
      // '/foo': 'http://localhost:4567',
      // 选项写法
      '/api': {
        target: 'http://192.168.0.7:902', // 目标后端服务地址
        changeOrigin: true, // 需要虚拟主机站点
        rewrite: (path) => path.replace(/^\/api/, '') // 重写路径：去掉路径中的/api
      },
    }
  }
})