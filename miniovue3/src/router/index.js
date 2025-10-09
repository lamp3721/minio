import { createRouter, createWebHistory } from 'vue-router';
import FileManager from '../views/FileManager.vue';
// 旧版视图已移除

const routes = [
  {
    path: '/',
    redirect: '/upload', // 设置默认重定向到主上传页面
  },
  {
    path: '/upload',
    name: 'MainUpload',
    component: FileManager,
  },
];

const router = createRouter({
  history: createWebHistory('/view'), // 使用 HTML5 History 模式，路径更美观
  routes,
});

export default router;