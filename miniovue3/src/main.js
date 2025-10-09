import { createApp } from "vue";
import App from "./App.vue";
import ElementPlus from "element-plus";
import 'element-plus/dist/index.css';
import router from './router';
import { ElMessage } from 'element-plus';
import { setNotifier } from './uploader/api';

const app = createApp(App);

app.use(ElementPlus);
app.use(router);

// 绑定上传模块的通知到 ElementPlus（其他项目可替换为自己的通知机制）
setNotifier((message, type = 'error', duration = 5000) => {
  ElMessage({ message, type, duration });
});

app.mount("#app");
