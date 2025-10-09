import axios from 'axios';
import { ElMessage } from 'element-plus';

export const API_BASE_URL = import.meta.env.VITE_API_Prefix;

const apiClient = axios.create({
  baseURL: API_BASE_URL,
});

// 统一的错误处理函数（增强：保留业务错误码与HTTP状态）
const handleError = (error) => {
  console.error('API 请求错误: ', error); // 用于调试

  let message = '请求失败，请稍后重试';
  let businessCode;
  let httpStatus;

  if (error.response) {
    // 请求已发出，但服务器响应的状态码不在 2xx 范围内
    const { status, data, config } = error.response;
    httpStatus = status;
    // 如果是下载blob且失败，特殊处理
    if (config.responseType === 'blob' && data.type && data.type.toLowerCase().indexOf('json') !== -1) {
        const reader = new FileReader();
        reader.onload = () => {
            const errorData = JSON.parse(reader.result);
            ElMessage({ message: errorData.message || '文件下载失败', type: 'error', duration: 5 * 1000 });
        };
        reader.onerror = () => {
            ElMessage({ message: '文件下载失败或文件不存在', type: 'error', duration: 5 * 1000 });
        };
        reader.readAsText(data);
        const apiErr = new Error('文件下载失败');
        apiErr.httpStatus = status;
        apiErr.code = (data && data.code) || undefined;
        apiErr.isBusinessError = !!(data && data.code && data.code !== 200);
        return Promise.reject(apiErr);
    }

    switch (status) {
      case 400:
        message = data.message || '无效的请求';
        businessCode = data && data.code;
        break;
      case 401:
        message = data.message || '未授权，请重新登录';
        businessCode = data && data.code;
        // 可在此处添加重定向到登录页的逻辑
        break;
      case 403:
        message = data.message || '禁止访问';
        businessCode = data && data.code;
        break;
      case 404:
        message = data.message || '请求的资源不存在';
        businessCode = data && data.code;
        break;
      case 500:
        message = data.message || '服务器内部错误';
        businessCode = data && data.code;
        break;
      default:
        message = `请求错误，状态码：${status}`;
    }
  } else if (error.request) {
    // 请求已发出，但没有收到响应
    message = '无法连接到服务器，请检查您的网络';
  } else {
    // 在设置请求时触发了错误
    message = error.message || '请求发送失败';
  }

  ElMessage({
    message,
    type: 'error',
    duration: 5 * 1000,
  });

  const apiError = new Error(message);
  if (businessCode) apiError.code = businessCode;
  if (httpStatus) apiError.httpStatus = httpStatus;
  apiError.isBusinessError = !!businessCode;
  return Promise.reject(apiError);
};

// 添加响应拦截器
apiClient.interceptors.response.use(
  response => {
    const res = response.data;
    // 如果响应是 blob 类型，直接返回
    if (res instanceof Blob) {
        return response;
    }

    // 后端统一响应格式为 { code, message, data }
    if (res.code !== 200) {
      ElMessage({
        message: res.message || '操作失败',
        type: 'error',
        duration: 5 * 1000
      });
      const apiError = new Error(res.message || 'BusinessError');
      apiError.code = res.code;
      apiError.isBusinessError = true;
      apiError.data = res.data;
      return Promise.reject(apiError);
    } else {
      return res.data;
    }
  },
  handleError // 应用统一的错误处理函数
);

export default apiClient;
