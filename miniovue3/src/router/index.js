import { createRouter, createWebHistory } from 'vue-router';
import PrivateFiles from '../views/PrivateFiles.vue';
import PublicAssets from '../views/PublicAssets.vue';
import ImprovedUpload from '../views/ImprovedUpload.vue';
import IconTest from '../views/IconTest.vue';

const routes = [
  {
    path: '/',
    redirect: '/improved', // 设置默认重定向到改进版本
  },
  {
    path: '/improved',
    name: 'ImprovedUpload',
    component: ImprovedUpload,
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
  {
    path: '/icon-test',
    name: 'IconTest',
    component: IconTest,
  },
];

const router = createRouter({
  history: createWebHistory('/view'), // 使用 HTML5 History 模式，路径更美观
  routes,
});

export default router;