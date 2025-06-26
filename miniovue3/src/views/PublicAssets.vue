<template>
  <div class="public-assets-container">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>上传公共文件</span>
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
            支持大文件分片上传，文件将公开访问。
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
          <span>公共文件列表</span>
          <el-button type="success" @click="fetchFileList" :loading="loading">刷新</el-button>
        </div>
      </template>
      <el-table :data="fileList" v-loading="loading" style="width: 100%">
        <el-table-column prop="name" label="文件名" />
        <el-table-column prop="size" label="大小" :formatter="formatFileSize" />
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button size="small" type="success" @click="handleCopyPublicLink(scope.row)">复制链接</el-button>
            <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import SparkMD5 from 'spark-md5';
import apiClient from '../api';

const uploadRef = ref(null);
const fileList = ref([]);
const loading = ref(false);
const uploadProgress = ref({ percentage: 0, status: '' });
const uploadSpeed = ref('');
const isUploading = ref(false);
const elapsedTime = ref('');
const uploadTimer = ref(null);

const CHUNK_SIZE = 5 * 1024 * 1024;

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
    fileReader.onerror = () => reject('文件读取失败');

    function loadNext() {
      const start = currentChunk * CHUNK_SIZE;
      const end = Math.min(start + CHUNK_SIZE, file.size);
      fileReader.readAsArrayBuffer(file.slice(start, end));
    }
    loadNext();
  });
};

const fetchFileList = async () => {
  loading.value = true;
  try {
    fileList.value = await apiClient.get('/public/list');
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const handleCopyPublicLink = async (row) => {
  try {
    await navigator.clipboard.writeText(row.url);
    ElMessage.success('公开链接已复制到剪贴板！');
  } catch (error) {
    ElMessage.error('复制链接失败！');
  }
};

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除文件 "${row.name}" 吗？`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await apiClient.delete('/public/delete', { params: { fileName: row.path } });
    ElMessage.success('文件删除成功！');
    fetchFileList();
  } catch (error) {
    if (error !== 'cancel' && (!error.message || !error.message.includes('cancel'))) {
      console.error(error);
    }
  }
};

const handleUpload = async (options) => {
  const file = options.file;
  if (isUploading.value) {
    ElMessage.warning('已有文件正在上传中，请稍后再试。');
    return;
  }
  isUploading.value = true;
  uploadProgress.value = { percentage: 0, status: '正在计算文件Hash...' };

  let fileHash;
  try {
    fileHash = await calculateFileHash(file);
    uploadProgress.value.status = `文件Hash: ${fileHash}`;
  } catch (e) {
    ElMessage.error(e);
    isUploading.value = false;
    return;
  }

  const batchId = fileHash;
  try {
    const checkResult = await apiClient.post('/public/check', { fileHash });
    if (checkResult.exists) {
      ElMessage.success('文件已存在，秒传成功！');
      uploadProgress.value = { percentage: 100, status: '秒传成功！' };
      await fetchFileList();
      isUploading.value = false;
      setTimeout(() => {
        if (!isUploading.value) {
          uploadProgress.value = { percentage: 0, status: '' };
          if (uploadRef.value) uploadRef.value.clearFiles();
        }
      }, 3000);
      return;
    }
  } catch (e) {
    isUploading.value = false;
    return;
  }

  let uploadedChunks = [];
  try {
    uploadedChunks = await apiClient.get('/public/uploaded/chunks', { params: { batchId } });
    if (uploadedChunks && uploadedChunks.length > 0) {
      ElMessage.info(`检测到上次上传进度，将从断点处继续上传。`);
    }
  } catch (e) {
    ElMessage.warning('检查断点失败，将从头开始上传。');
  }

  const chunkCount = Math.ceil(file.size / CHUNK_SIZE);
  const chunks = Array.from({ length: chunkCount }, (v, i) =>
      file.slice(i * CHUNK_SIZE, (i + 1) * CHUNK_SIZE)
  );

  const initialLoaded = uploadedChunks.reduce((acc, chunkIndex) => {
    if (chunkIndex < chunks.length) {
      return acc + chunks[chunkIndex].size;
    }
    return acc;
  }, 0);

  uploadProgress.value = { percentage: Math.floor((initialLoaded / file.size) * 100), status: '准备上传...' };
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
  uploadedChunks.forEach(chunkIndex => {
    if (chunkIndex < chunks.length) {
      chunkProgress[chunkIndex] = chunks[chunkIndex].size;
    }
  });

  try {
    const uploadPromises = chunks.map((chunk, i) => {
      if (uploadedChunks.includes(i)) {
        return Promise.resolve();
      }
      const formData = new FormData();
      formData.append('file', chunk);
      formData.append('batchId', batchId);
      formData.append('chunkNumber', String(i));
      return apiClient.post('/public/upload/chunk', formData, {
        onUploadProgress: (progressEvent) => {
          chunkProgress[i] = progressEvent.loaded;
          totalLoaded = chunkProgress.reduce((acc, cur) => acc + cur, 0);
          const currentTime = Date.now();
          const deltaTime = (currentTime - lastTime) / 1000;
          if (deltaTime > 0.5) {
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

    uploadProgress.value.status = '正在合并文件...';
    await apiClient.post('/public/upload/merge', {
      batchId,
      fileName: file.name,
      fileHash,
      fileSize: file.size,
      contentType: file.type,
    });

    uploadProgress.value = { percentage: 100, status: '文件上传成功！' };
    ElMessage.success('文件上传成功！');
    await fetchFileList();

  } catch (error) {
    uploadProgress.value.status = '上传失败，请检查网络或联系管理员。';
    console.error('上传或合并失败:', error);
  } finally {
    isUploading.value = false;
    clearInterval(uploadTimer.value);
    uploadSpeed.value = '';
    setTimeout(() => {
      if (!isUploading.value) {
        uploadProgress.value = { percentage: 0, status: '' };
        if (uploadRef.value) uploadRef.value.clearFiles();
      }
    }, 5000);
  }
};

const handleExceed = () => ElMessage.warning('一次只能上传一个文件。');

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

onMounted(fetchFileList);
</script>

<style scoped>
.public-assets-container {
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