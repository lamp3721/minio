import axios from 'axios';
import { ElMessage } from 'element-plus';

export const API_BASE_URL = '/api/minio';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
});

// 添加响应拦截器
apiClient.interceptors.response.use(
  response => {
    // 后端统一响应格式为 { code, message, data }
    const res = response.data;
    if (res.code !== 200) {
      ElMessage({
        message: res.message || 'Error',
        type: 'error',
        duration: 5 * 1000
      });
      // 返回一个被拒绝的 Promise，中断后续的 .then() 操作
      return Promise.reject(new Error(res.message || 'Error'));
    } else {
      // 如果 code 为 200，则直接返回核心数据 data 部分
      return res.data;
    }
  },
  error => {
    // 处理网络层面的错误
    console.error('API 请求错误: ' + error); // 用于调试
    if (error.response) {
      // 请求已发出，但服务器响应的状态码不在 2xx 范围内
      // 特别处理文件下载失败的情况，因为它可能没有标准的JSON返回体
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
