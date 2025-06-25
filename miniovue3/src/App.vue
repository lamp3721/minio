<template>
  <div id="app" class="app-container">
    <h1 class="app-title">MinIO 文件管理</h1>

    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>文件上传</span>
        </div>
      </template>
      <el-upload
          ref="uploadRef"
          :http-request="handleUpload"
          :on-remove="handleRemove"
          :on-exceed="handleExceed"
          :limit="1"
          :auto-upload="true"
          class="file-uploader"
      >
        <template #trigger>
          <el-button type="primary">选择文件</el-button>
        </template>
        <template #tip>
          <div class="el-upload__tip">
            支持大文件分片上传。
          </div>
        </template>
      </el-upload>
      <div v-if="uploadProgress.percentage > 0" class="progress-container">
         <el-progress :percentage="uploadProgress.percentage" :text-inside="true" :stroke-width="20"/>
         <span>{{ uploadProgress.status }}</span>
      </div>
    </el-card>

    <el-card class="box-card file-list-card">
      <template #header>
        <div class="card-header">
          <span>文件列表</span>
          <el-button type="success" @click="fetchFileList" :loading="loading">刷新</el-button>
        </div>
      </template>
      <el-table :data="fileList" v-loading="loading" style="width: 100%">
        <el-table-column prop="name" label="文件名" />
        <el-table-column prop="size" label="大小" :formatter="formatFileSize" />
        <el-table-column label="操作" width="280">
          <template #default="scope">
            <el-button size="small" type="primary" @click="handleDownload(scope.row)">下载</el-button>
            <el-button size="small" type="success" @click="handleCopyLink(scope.row)">复制链接</el-button>
            <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import axios from 'axios';
import { ElMessage, ElMessageBox } from 'element-plus';
import { v4 as uuidv4 } from 'uuid';

// --- Refs and Reactive State: 响应式状态定义 ---

// 对 el-upload 组件的引用，用于后续调用其方法 (例如清空文件列表)
const uploadRef = ref(null);
// 存储从后端获取的文件列表
const fileList = ref([]);
// 控制文件列表加载状态的 loading 动画
const loading = ref(false);
// 存储上传过程中的进度和状态信息
const uploadProgress = ref({
  percentage: 0, // 进度百分比
  status: '',     // 当前状态的描述文本
});

// --- Constants: 常量定义 ---

// 后端API的基础URL，请根据您的实际部署地址进行修改
const API_BASE_URL = 'http://localhost:8080/minio';
// 定义分片大小为 5MB
const CHUNK_SIZE = 5 * 1024 * 1024;

// --- API Client: Axios 实例 ---

// 创建一个 Axios 实例，用于与后端进行通信
const apiClient = axios.create({
  baseURL: API_BASE_URL,
});

// --- File Operations: 文件操作相关方法 ---

/**
 * 从后端获取文件列表
 */
const fetchFileList = async () => {
  loading.value = true; // 开始加载，显示动画
  try {
    const response = await apiClient.get('/list-files');
    fileList.value = response.data;
  } catch (error) {
    ElMessage.error('获取文件列表失败！');
    console.error(error);
  } finally {
    loading.value = false; // 结束加载，隐藏动画
  }
};

/**
 * 处理文件下载操作。
 * 通过获取预签名URL，实现客户端直接从MinIO下载，不经过后端服务器转发。
 * @param {object} row - 表格中当前行的数据，包含文件名等信息
 */
const handleDownload = async (row) => {
  try {
    const response = await apiClient.get('/download-url', { params: { fileName: row.name } });
    const url = response.data;
    // 创建一个隐藏的 a 标签并模拟点击，以触发浏览器下载
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', row.name); // 设置下载文件的默认名称
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    ElMessage.success('开始下载...');
  } catch (error) {
    ElMessage.error('获取下载链接失败！');
    console.error(error);
  }
};

/**
 * 处理复制下载链接的操作。
 * @param {object} row - 当前行的数据
 */
const handleCopyLink = async (row) => {
  try {
    const response = await apiClient.get('/download-url', { params: { fileName: row.name } });
    const url = response.data;
    // 使用浏览器原生的 Clipboard API 将URL写入剪贴板
    await navigator.clipboard.writeText(url);
    ElMessage.success('下载链接已复制到剪贴板！');
  } catch (error) {
    ElMessage.error('复制链接失败！');
    console.error(error);
  }
};

/**
 * 处理文件删除操作。
 * @param {object} row - 当前行的数据
 */
const handleDelete = async (row) => {
  // 弹出确认对话框，防止用户误操作
  await ElMessageBox.confirm(`确定要删除文件 "${row.name}" 吗？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  });
  
  try {
    await apiClient.delete('/delete', { params: { fileName: row.name } });
    ElMessage.success('文件删除成功！');
    fetchFileList(); // 删除成功后刷新文件列表
  } catch (error) {
    ElMessage.error('文件删除失败！');
    console.error(error);
  }
};


// --- Chunked Upload Logic: 分片上传核心逻辑 ---

/**
 * 自定义上传处理函数，覆盖 el-upload 的默认行为。
 * @param {object} options - el-upload 传递的参数，包含 file 对象
 */
const handleUpload = async (options) => {
  const file = options.file;
  // 为本次上传生成一个唯一的批次ID
  const batchId = uuidv4();
  // 计算总分片数
  const chunkCount = Math.ceil(file.size / CHUNK_SIZE);

  // 初始化进度条
  uploadProgress.value = { percentage: 0, status: '开始上传...' };

  try {
    // 步骤1: 循环上传每一个分片
    for (let i = 0; i < chunkCount; i++) {
      const start = i * CHUNK_SIZE;
      const end = Math.min(start + CHUNK_SIZE, file.size);
      // 在前端对文件进行切片
      const chunk = file.slice(start, end);

      // 创建 FormData 对象来包装分片数据
      const formData = new FormData();
      formData.append('file', chunk);
      formData.append('batchId', batchId);
      formData.append('chunkNumber', i);
      
      // 更新上传状态文本
      uploadProgress.value.status = `正在上传分片 ${i + 1}/${chunkCount}`;
      // 调用后端的分片上传接口
      await apiClient.post('/upload/chunk', formData);
      
      // 更新进度条，为了视觉效果，在合并前最多显示到99%
      uploadProgress.value.percentage = Math.round(((i + 1) / chunkCount) * 99);
    }
    
    // 步骤2: 所有分片上传完毕后，调用合并接口
    uploadProgress.value.status = '正在合并文件...';
    await apiClient.post('/upload/merge', {
      batchId: batchId,
      fileName: file.name,
    });
    
    // 步骤3: 合并成功，上传完成
    uploadProgress.value.percentage = 100;
    uploadProgress.value.status = '上传成功！';
    ElMessage.success('文件上传成功！');
    fetchFileList(); // 成功后刷新文件列表

  } catch (error) {
    ElMessage.error('上传失败，请检查控制台获取详细信息。');
    console.error('Upload failed:', error);
    uploadProgress.value.status = '上传失败！';
  } finally {
     // 无论成功或失败，3秒后重置上传组件和进度条
     setTimeout(() => {
        uploadProgress.value = { percentage: 0, status: '' };
        if (uploadRef.value) {
            uploadRef.value.clearFiles(); // 清空 el-upload 的文件列表
        }
    }, 3000);
  }
};

/**
 * 处理 el-upload 文件列表中的文件被移除时的钩子 (此处未使用)
 */
const handleRemove = () => {
  // 可以根据需要添加逻辑
};

/**
 * 处理超出 el-upload limit 限制时的钩子
 */
const handleExceed = () => {
  ElMessage.warning('一次只能上传一个文件。');
};

// --- Utility Functions: 工具函数 ---

/**
 * Element Plus 表格的格式化函数，用于将文件大小（字节）转换为可读的格式 (KB, MB, GB)。
 * @param {object} row - 行数据
 * @param {object} column - 列定义
 * @param {number} cellValue - 单元格原始值 (文件大小)
 * @returns {string} 格式化后的字符串
 */
const formatFileSize = (row, column, cellValue) => {
  if (cellValue === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(cellValue) / Math.log(k));
  return parseFloat((cellValue / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

// --- Lifecycle Hooks: 生命周期钩子 ---

/**
 * 在组件挂载到DOM后执行
 */
onMounted(() => {
  // 页面加载时自动获取一次文件列表
  fetchFileList();
});
</script>

<style>
.app-container {
  max-width: 960px;
  margin: 40px auto;
  padding: 20px;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', '微软雅黑', Arial, sans-serif;
}

.app-title {
  text-align: center;
  color: #333;
  margin-bottom: 30px;
}

.box-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.file-uploader {
  margin-bottom: 20px;
}

.progress-container {
    margin-top: 15px;
    display: flex;
    align-items: center;
    gap: 15px;
}
</style>
