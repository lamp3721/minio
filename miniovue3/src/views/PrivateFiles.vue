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

// --- Chunked Upload Logic ---
const handleUpload = async (options) => {
  const file = options.file;
  if (isUploading.value) {
    ElMessage.warning('已有文件正在上传中，请稍后再试。');
    return;
  }
  isUploading.value = true;
  uploadProgress.value = { percentage: 0, status: '正在计算文件Hash...' };
  console.log('【私有文件】开始上传流程...');

  let fileHash;
  try {
    console.log('【私有文件】开始计算文件哈希...');
    fileHash = await calculateFileHash(file);
    uploadProgress.value.status = `文件Hash: ${fileHash}`;
    console.log(`【私有文件】哈希计算完成: ${fileHash}`);
  } catch (e) {
    ElMessage.error(e);
    isUploading.value = false;
    return;
  }

  // 为了实现断点续传，使用文件哈希作为持久化的批次ID
  const batchId = fileHash;

  // 检查文件是否已存在 (秒传)
  try {
    console.log(`【私有文件】向后端发送检查请求，哈希: ${fileHash}`);
    const checkResult = await apiClient.post('/private/check', { fileHash: fileHash, fileName: file.name });
    console.log(`【私有文件】收到后端检查响应:`, checkResult);
    if (checkResult.exists) {
        ElMessage.success('文件已存在，秒传成功！');
        uploadProgress.value = { percentage: 100, status: '秒传成功！' };
        console.log('【私有文件】后端确认文件已存在，触发秒传。');
        await fetchFileList(); // 刷新列表
        isUploading.value = false;
         setTimeout(() => {
          if (!isUploading.value) {
            uploadProgress.value = { percentage: 0, status: '' };
            if (uploadRef.value) uploadRef.value.clearFiles();
          }
        }, 3000);
        return;
    }
  } catch(e) {
    // 拦截器已处理
    console.error(e);
    isUploading.value = false;
    return;
  }

  // --- 断点续传逻辑 ---
  let uploadedChunks = [];
  try {
    console.log(`【断点续传】检查已上传的分片, batchId: ${fileHash}`);
    uploadedChunks = await apiClient.get('/private/uploaded/chunks', { params: { batchId: fileHash } });
    if (uploadedChunks && uploadedChunks.length > 0) {
      ElMessage.info(`检测到上次上传进度，将从断点处继续上传。已完成 ${uploadedChunks.length} 个分片。`);
      console.log('【断点续传】已存在的分片列表:', uploadedChunks);
    }
  } catch(e) {
    ElMessage.warning('检查断点失败，将从头开始上传。');
    console.error('【断点续传】检查失败:', e);
  }
  // --- 断点续传逻辑结束 ---

  console.log('【私有文件】后端确认文件不存在，开始执行分片上传。');
  const chunkCount = Math.ceil(file.size / CHUNK_SIZE);
  const chunks = [];
  for (let i = 0; i < chunkCount; i++) {
    chunks.push(file.slice(i * CHUNK_SIZE, (i + 1) * CHUNK_SIZE));
  }

  // --- 优化进度条初始值 ---
  let initialLoaded = 0;
  if (uploadedChunks.length > 0) {
      initialLoaded = uploadedChunks.length * CHUNK_SIZE;
  }
  // --- 优化进度条结束 ---

  uploadProgress.value = { percentage: Math.floor((initialLoaded / file.size) * 100), status: '正在计算文件...' };
  uploadSpeed.value = '';
  elapsedTime.value = '00:00';
  let lastLoaded = initialLoaded;
  let lastTime = Date.now();
  const startTime = lastTime;
  let totalLoaded = initialLoaded;
  uploadTimer.value = setInterval(() => {
    const seconds = Math.floor((Date.now() - startTime) / 1000);
    elapsedTime.value = formatDuration(seconds);
  }, 1000);

  const chunkProgress = new Array(chunkCount).fill(0);
  // --- 为已上传的分片预填充进度 ---
  uploadedChunks.forEach(chunkIndex => {
      if (chunkIndex < chunkCount) {
          chunkProgress[chunkIndex] = chunks[chunkIndex] ? chunks[chunkIndex].size : CHUNK_SIZE;
      }
  });
  // --- 进度预填充结束 ---
  try {
    const uploadPromises = chunks.map((chunk, i) => {
      // --- 跳过已上传的分片 ---
      if (uploadedChunks.includes(i)) {
        console.log(`【断点续传】分片 ${i} 已存在，跳过上传。`);
        return Promise.resolve();
      }
      // --- 跳过逻辑结束 ---

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
          if (deltaTime > 0.5) { // 更新频率控制
            const deltaLoaded = totalLoaded - lastLoaded;
            uploadSpeed.value = `${(deltaLoaded / deltaTime / 1024 / 1024).toFixed(2)} MB/s`;
            lastLoaded = totalLoaded;
            lastTime = currentTime;
          }
          uploadProgress.value.percentage = Math.floor((totalLoaded / file.size) * 100);
          uploadProgress.value.status = `正在上传... ${uploadProgress.value.percentage}%`;
        },
      });
    });

    await Promise.all(uploadPromises);
    console.log('【私有文件】所有分片上传成功。');

    // **新增：调用合并接口**
    uploadProgress.value.status = '正在合并文件...';
    console.log('【私有文件】开始调用合并接口...');
    await apiClient.post('/private/upload/merge', {
      batchId: batchId,
      fileName: file.name,
      fileHash: fileHash,

      //这连个数据不影响合共，只是用于在数据库中记录信息
      fileSize: file.size,
      contentType: file.type,
    });

    uploadProgress.value.status = '文件上传成功！';
    uploadProgress.value.percentage = 100;
    console.log('【私有文件】文件合并成功。');
    ElMessage.success('文件上传成功！');
    await fetchFileList();

  } catch (error) {
    uploadProgress.value.status = '上传失败，请检查网络或联系管理员。';
    ElMessage.error('上传过程中发生错误！');
    console.error('【私有文件】上传或合并失败:', error);
  } finally {
    isUploading.value = false;
    clearInterval(uploadTimer.value);
    uploadSpeed.value = '';
    setTimeout(() => {
      if (!isUploading.value) { // 避免覆盖新的上传状态
        uploadProgress.value = { percentage: 0, status: '' };
        if (uploadRef.value) uploadRef.value.clearFiles();
      }
    }, 5000);
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