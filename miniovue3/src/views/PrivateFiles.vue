<template>
  <div class="private-files-container">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>私有文件上传</span>
        </div>
      </template>
      <el-upload
          ref="uploadRef"
          :http-request="customUploadRequest"
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
         <el-progress :percentage="uploadProgress.percentage" :text-inside="true" :stroke-width="20" class="progress-bar"/>
         <div class="progress-info">
           <span>{{ uploadProgress.status }}</span>
           <div class="sub-info">
             <span v-if="uploadSpeed" class="upload-speed">{{ uploadSpeed }}</span>
             <span v-if="elapsedTime" class="elapsed-time">耗时: {{ elapsedTime }}</span>
           </div>
         </div>
      </div>
    </el-card>

    <el-card class="box-card file-list-card">
      <template #header>
        <div class="card-header">
          <span>私有文件列表</span>
          <el-button type="success" @click="fetchFileList" :loading="loading">刷新</el-button>
        </div>
      </template>
      <el-table :data="fileList" v-loading="loading" style="width: 100%">
        <el-table-column prop="name" label="文件名" />
        <el-table-column prop="size" label="大小" :formatter="formatFileSize" />
        <el-table-column prop="visitCount" label="下载次数" />
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
import SparkMD5 from 'spark-md5';
import apiClient from '../api';
import { useChunkUploader } from '../composables/useChunkUploader.js';

// --- 响应式状态定义 ---
const uploadRef = ref(null);
const fileList = ref([]);
const loading = ref(false);

// --- 上传器配置 ---
const uploaderConfig = {
  apiPrefix: '/private'   // API实现细节
};

// --- 引入分片上传模块 ---
// 调用Composable函数，并传入详细的配置对象
const {
  isUploading,
  uploadProgress,
  uploadSpeed,
  elapsedTime,
  handleUpload,
  gracefulReset,
} = useChunkUploader(uploaderConfig);

// --- Constants ---
const CHUNK_SIZE = 5 * 1024 * 1024;

// --- File Hash Calculation ---
const calculateFileHash = (file) => {
  return new Promise((resolve, reject) => {
    const spark = new SparkMD5.ArrayBuffer();
    const fileReader = new FileReader();
    const totalChunks = Math.ceil(file.size / CHUNK_SIZE);
    let currentChunk = 0;

    fileReader.onload = (e) => {
      spark.append(e.target.result);
      currentChunk++;
      if (currentChunk < totalChunks) {
        loadNext();
      } else {
        const hash = spark.end();
        resolve(hash);
      }
    };

    fileReader.onerror = () => {
      reject('文件读取失败');
    };

    function loadNext() {
      const start = currentChunk * CHUNK_SIZE;
      const end = Math.min(start + CHUNK_SIZE, file.size);
      fileReader.readAsArrayBuffer(file.slice(start, end));
    }

    loadNext();
  });
};

// --- 上传组件钩子 ---
/**
 * 自定义Element Plus上传组件的http-request行为。
 * @param {object} options - Element Plus上传组件传递的参数，包含file对象。
 */
const customUploadRequest = async (options) => {
  // 调用Composable中的核心上传处理函数
  const result = await handleUpload(options.file, fetchFileList);
  // 如果上传/秒传成功，则延迟重置UI组件的状态
  if (result && result.isSuccess && result.gracefulResetNeeded) {
    gracefulReset(uploadRef);
  }
};

/**
 * 处理超出上传文件数量限制的钩子。
 */
const handleExceed = () => {
  ElMessage.warning('一次只能上传一个文件，请先移除已有文件。');
};

// --- 文件操作 (本视图特有) ---
/**
 * 从后端获取私有文件列表。
 */
const fetchFileList = async () => {
  loading.value = true;
  try {
    fileList.value = await apiClient.get('/private/list');
  } catch (error) {
    // 拦截器中已统一处理错误消息
    console.error(error);
  } finally {
    loading.value = false;
  }
};

/**
 * 处理文件下载操作。
 * @param {object} row - 表格中当前行的数据对象。
 */
const handleDownload = async (row) => {
  try {
    // 1. 先从后端获取一个带时效性的预签名URL
    const url = await apiClient.get('/private/download-url', { params: { fileName: row.path } });
    // 2. 创建一个隐藏的<a>标签来触发浏览器下载
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', row.name);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link); // 下载后移除标签
    ElMessage.success('开始下载...');
  } catch (error) {
    // 拦截器中已统一处理错误消息
    console.error(error);
  }
};

/**
 * 处理复制预签名下载链接的操作。
 * @param {object} row - 表格中当前行的数据对象。
 */
const handleCopyLink = async (row) => {
  try {
    const url = await apiClient.get('/private/download-url', { params: { fileName: row.path } });
    // 使用浏览器的剪贴板API
    await navigator.clipboard.writeText(url);
    ElMessage.success('下载链接已复制到剪贴板！');
  } catch (error) {
    // 拦截器中已统一处理错误消息
    console.error(error);
  }
};

/**
 * 处理文件删除操作。
 * @param {object} row - 表格中当前行的数据对象。
 */
const handleDelete = async (row) => {
  try {
      await ElMessageBox.confirm(`确定要删除文件 "${row.name}" 吗？`, '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      });
      // 调用后端删除接口
      await apiClient.delete('/private/delete', { params: { fileName: row.path } });
      ElMessage.success('文件删除成功！');
      fetchFileList(); // 重新加载文件列表
  } catch (error) {
    // 如果用户点击了"取消"，则不打印错误
     if (error.message && !error.message.includes('cancel')) {
       // 拦截器中已统一处理错误消息
       console.error(error);
    }
  }
};

// --- 工具函数 ---
/**
 * 格式化文件大小，将字节转换为更易读的单位 (KB, MB, GB)。
 * @param {object} row - 表格行数据。
 * @param {object} column - 表格列数据。
 * @param {number} cellValue - 单元格的原始值（文件大小，字节）。
 * @returns {string} 格式化后的大小字符串。
 */
const formatFileSize = (row, column, cellValue) => {
  if (!cellValue) return '';
  const size = parseFloat(cellValue);
  if (size > 1024 * 1024 * 1024) {
    return (size / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
  }
  if (size > 1024 * 1024) {
    return (size / (1024 * 1024)).toFixed(2) + ' MB';
  }
  if (size > 1024) {
    return (size / 1024).toFixed(2) + ' KB';
  }
  return size + ' Bytes';
};

// --- Vue生命周期钩子 ---
onMounted(() => {
  fetchFileList(); // 组件挂载后，自动获取一次文件列表
});
</script>

<style scoped>
.private-files-container {
  padding: 20px;
}
.box-card {
  margin-bottom: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.progress-container {
    margin-top: 15px;
    display: flex;
    align-items: center;
    gap: 15px;
}
.progress-bar {
  flex-grow: 1;
}
.progress-info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 120px;
}
.sub-info {
  display: flex;
  gap: 10px;
  margin-top: 4px;
}
.upload-speed, .elapsed-time {
  font-size: 12px;
  color: #909399;
}
</style> 