import axios from 'axios';
import { ElMessage } from 'element-plus';

export const API_BASE_URL = '/api/minio';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
});

// 添加响应拦截器
apiClient.interceptors.response.use(
  response => {
    // 后端统一响应格式 { code, message, data }
    const res = response.data;
    if (res.code !== 200) {
      ElMessage({
        message: res.message || 'Error',
        type: 'error',
        duration: 5 * 1000
      });
      // 可以根据业务需要，返回一个被拒绝的 Promise
      return Promise.reject(new Error(res.message || 'Error'));
    } else {
      // 如果 code 为 200，则返回 data 部分
      return res.data;
    }
  },
  error => {
    // 处理网络错误等
    console.error('API Error: ' + error); // for debug
    if (error.response) {
      // 请求已发出，但服务器响应的状态码不在 2xx 范围内
      // 特别处理文件下载失败的情况，它可能没有标准json返回体
       if (error.response.request?.responseType === 'blob') {
         ElMessage({ message: '文件下载失败或文件不存在', type: 'error', duration: 5 * 1000 });
         return Promise.reject(error);
      }
    }
    ElMessage({
      message: error.message,
      type: 'error',
      duration: 5 * 1000
    });
    return Promise.reject(error);
  }
);

export default apiClient;
