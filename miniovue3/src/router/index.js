import { createRouter, createWebHistory } from 'vue-router';
import PrivateFiles from '../views/PrivateFiles.vue';
import PublicAssets from '../views/PublicAssets.vue';

const routes = [
  {
    path: '/',
    redirect: '/private', // 默认重定向到私有文件页面
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
  history: createWebHistory(),
  routes,
});

export default router; 