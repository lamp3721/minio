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
          :http-request="handleUpload"
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

// --- Refs and Reactive State ---
const uploadRef = ref(null);
const fileList = ref([]);
const loading = ref(false);
const uploadProgress = ref({ percentage: 0, status: '' });
const uploadSpeed = ref('');
const isUploading = ref(false);
const elapsedTime = ref('');
const uploadTimer = ref(null);

// --- Constants ---
const API_BASE_URL = '/api/minio';
const CHUNK_SIZE = 5 * 1024 * 1024;

// --- API Client ---
const apiClient = axios.create({ baseURL: API_BASE_URL });

// --- File Operations ---
const fetchFileList = async () => {
  loading.value = true;
  try {
    const response = await apiClient.get('/private/list');
    fileList.value = response.data;
  } catch (error) {
    ElMessage.error('获取文件列表失败！');
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const handleDownload = async (row) => {
  try {
    const response = await apiClient.get('/private/download-url', { params: { fileName: row.name } });
    const link = document.createElement('a');
    link.href = response.data;
    link.setAttribute('download', row.name);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    ElMessage.success('开始下载...');
  } catch (error) {
    ElMessage.error('获取下载链接失败！');
  }
};

const handleCopyLink = async (row) => {
  try {
    const response = await apiClient.get('/private/download-url', { params: { fileName: row.name } });
    await navigator.clipboard.writeText(response.data);
    ElMessage.success('下载链接已复制到剪贴板！');
  } catch (error) {
    ElMessage.error('复制链接失败！');
  }
};

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定要删除文件 "${row.name}" 吗？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  });
  try {
    await apiClient.delete('/private/delete', { params: { fileName: row.name } });
    ElMessage.success('文件删除成功！');
    fetchFileList();
  } catch (error) {
    ElMessage.error('文件删除失败！');
  }
};

// --- Chunked Upload Logic ---
const handleUpload = async (options) => {
  const file = options.file;
  if (isUploading.value) {
    ElMessage.warning('已有文件正在上传中，请稍后再试。');
    return;
  }
  isUploading.value = true;
  const batchId = uuidv4();
  const chunkCount = Math.ceil(file.size / CHUNK_SIZE);
  const chunks = [];
  for (let i = 0; i < chunkCount; i++) {
    chunks.push(file.slice(i * CHUNK_SIZE, (i + 1) * CHUNK_SIZE));
  }

  uploadProgress.value = { percentage: 0, status: '正在计算文件...' };
  uploadSpeed.value = '';
  elapsedTime.value = '00:00';
  let lastLoaded = 0;
  let lastTime = Date.now();
  const startTime = lastTime;
  let totalLoaded = 0;
  uploadTimer.value = setInterval(() => {
    const seconds = Math.floor((Date.now() - startTime) / 1000);
    elapsedTime.value = formatDuration(seconds);
  }, 1000);

  const chunkProgress = new Array(chunkCount).fill(0);
  const uploadPromises = chunks.map((chunk, i) => {
    const formData = new FormData();
    formData.append('file', chunk);
    formData.append('batchId', batchId);
    formData.append('chunkNumber', String(i));
    return apiClient.post('/private/upload/chunk', formData, {
      onUploadProgress: (progressEvent) => {
        chunkProgress[i] = progressEvent.loaded;
        totalLoaded = chunkProgress.reduce((acc, cur) => acc + cur, 0);
        const currentTime = Date.now();
        const deltaTime = (currentTime - lastTime) / 1000;
        const deltaLoaded = totalLoaded - lastLoaded;
        if (deltaTime > 0.5) {
          const speed = deltaLoaded / deltaTime;
          uploadSpeed.value = `${(speed / 1024 / 1024).toFixed(2)} MB/s`;
          lastTime = currentTime;
          lastLoaded = totalLoaded;
        }
        uploadProgress.value.percentage = Math.min(Math.round((totalLoaded * 100) / file.size), 99);
        uploadProgress.value.status = `正在上传...`;
      }
    });
  });

  try {
    await Promise.all(uploadPromises);
    uploadProgress.value.status = '正在合并文件...';
    await apiClient.post('/private/upload/merge', { batchId: batchId, fileName: file.name });
    uploadProgress.value.percentage = 100;
    uploadProgress.value.status = '上传成功！';
    uploadSpeed.value = '';
    ElMessage.success('文件上传成功!');
    await fetchFileList();
  } catch (error) {
    uploadProgress.value.status = '上传失败！';
    ElMessage.error('文件上传失败，服务器将后台自动清理临时文件。');
  } finally {
    isUploading.value = false;
    clearInterval(uploadTimer.value);
    setTimeout(() => {
      if (!isUploading.value) {
        uploadProgress.value = { percentage: 0, status: '' };
        uploadSpeed.value = '';
        elapsedTime.value = '';
        if (uploadRef.value) uploadRef.value.clearFiles();
      }
    }, 3000);
  }
};

const handleExceed = () => ElMessage.warning('一次只能上传一个文件。');

// --- Utility Functions ---
const formatFileSize = (row, column, cellValue) => {
  if (cellValue === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(cellValue) / Math.log(k));
  return `${parseFloat((cellValue / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
};

const formatDuration = (totalSeconds) => {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = Math.floor(totalSeconds % 60);
  const padded = (num) => num.toString().padStart(2, '0');
  return hours > 0 ? `${padded(hours)}:${padded(minutes)}:${padded(seconds)}` : `${padded(minutes)}:${padded(seconds)}`;
};

// --- Lifecycle Hooks ---
onMounted(fetchFileList);
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