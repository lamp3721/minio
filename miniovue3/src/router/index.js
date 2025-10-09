import { createRouter, createWebHistory } from 'vue-router';
import FileManager from '../views/FileManager.vue';
import PrivateFiles from '../views/PrivateFiles.vue';
import PublicAssets from '../views/PublicAssets.vue';

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
  {
    path: '/private-legacy',
    name: 'PrivateFilesLegacy',
    component: PrivateFiles,
  },
  {
    path: '/public-legacy',
    name: 'PublicAssetsLegacy',
    component: PublicAssets,
  },
];

const router = createRouter({
  history: createWebHistory('/view'), // 使用 HTML5 History 模式，路径更美观
  routes,
});

export default router;