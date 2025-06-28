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

// --- Refs and Reactive State ---
const uploadRef = ref(null);
const fileList = ref([]);
const loading = ref(false);

// --- Use the Composable for Upload Logic ---
const {
  isUploading,
  uploadProgress,
  uploadSpeed,
  elapsedTime,
  handleUpload,
  gracefulReset,
} = useChunkUploader({ storageType: 'private' });

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

// --- Custom Upload Request for Element Plus ---
const customUploadRequest = async (options) => {
  const result = await handleUpload(options.file, fetchFileList);
  if (result && result.isSuccess && result.gracefulResetNeeded) {
    gracefulReset(uploadRef);
  }
};

// --- File Operations ---
const fetchFileList = async () => {
  loading.value = true;
  try {
    fileList.value = await apiClient.get('/private/list');
  } catch (error) {
    // 拦截器中已处理 ElMessage
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const handleDownload = async (row) => {
  try {
    const url = await apiClient.get('/private/download-url', { params: { fileName: row.path } });
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', row.name);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    ElMessage.success('开始下载...');
  } catch (error) {
     // 拦截器中已处理 ElMessage
    console.error(error);
  }
};

const handleCopyLink = async (row) => {
  try {
    const url = await apiClient.get('/private/download-url', { params: { fileName: row.path } });
    await navigator.clipboard.writeText(url);
    ElMessage.success('下载链接已复制到剪贴板！');
  } catch (error) {
     // 拦截器中已处理 ElMessage
    console.error(error);
  }
};

const handleDelete = async (row) => {
  try {
      await ElMessageBox.confirm(`确定要删除文件 "${row.name}" 吗？`, '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      });
      await apiClient.delete('/private/delete', { params: { fileName: row.path } });
      ElMessage.success('文件删除成功！');
      fetchFileList();
  } catch (error) {
     if (error.message && !error.message.includes('cancel')) {
       // 拦截器中已处理 ElMessage
       console.error(error);
    }
  }
};

const handleExceed = () => {
  ElMessage.warning('一次只能上传一个文件，请先移除已有文件。');
};

// --- Utility Functions ---
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

// --- Lifecycle Hooks ---
onMounted(() => {
  fetchFileList();
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