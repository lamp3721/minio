import { createRouter, createWebHistory } from 'vue-router';
import PrivateFiles from '../views/PrivateFiles.vue';
import PublicAssets from '../views/PublicAssets.vue';

const routes = [
  {
    path: '/',
    redirect: '/private', // 设置默认重定向，访问根路径时自动跳转到私有文件页面
  },
  {
    path: '/private',
    name: 'PrivateFiles',
    component: PrivateFiles,
  },
  {
    path: '/public',
    name: 'PublicAssets',
    component: PublicAssets,
  },
];

const router = createRouter({
  history: createWebHistory(), // 使用 HTML5 History 模式，路径更美观
  routes,
});

export default router; 