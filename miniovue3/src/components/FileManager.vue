<template>
  <div class="file-manager-container">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
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
            {{ uploadTip }}
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
          <span>{{ title }}列表</span>
          <el-button type="success" @click="fetchFileList" :loading="loading">刷新</el-button>
        </div>
      </template>
      <el-table :data="fileList" v-loading="loading" style="width: 100%">
        <!-- Slot for custom columns before the main ones -->
        <slot name="columns-before" :fileList="fileList"></slot>
        
        <el-table-column prop="name" label="文件名" />
        <el-table-column prop="size" label="大小" :formatter="formatFileSize" />
        <el-table-column prop="visitCount" label="下载次数" v-if="showVisitCount"/>

        <!-- The action column is now a slot -->
        <el-table-column label="操作" :width="actionsWidth">
          <template #default="scope">
            <slot name="actions" :row="scope.row" :fetchFileList="fetchFileList"></slot>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, toRefs } from 'vue';
import { ElMessage } from 'element-plus';
import apiClient from '../api';
import { useChunkUploader } from '../composables/useChunkUploader.js';

// --- Props Definition ---
const props = defineProps({
  title: {
    type: String,
    required: true,
  },
  uploadTip: {
    type: String,
    default: '支持大文件分片上传。',
  },
  uploaderConfig: {
    type: Object,
    required: true,
  },
  showVisitCount: {
      type: Boolean,
      default: false,
  },
  actionsWidth: {
      type: String,
      default: '200'
  }
});

const { uploaderConfig } = toRefs(props);

// --- Responsive State ---
const uploadRef = ref(null);
const fileList = ref([]);
const loading = ref(false);

// --- Chunk Uploader ---
const {
  uploadProgress,
  uploadSpeed,
  elapsedTime,
  handleUpload,
  gracefulReset,
} = useChunkUploader(uploaderConfig.value);

// --- Upload Component Hooks ---
const customUploadRequest = async (options) => {
  const result = await handleUpload(options.file, fetchFileList);
  if (result && result.isSuccess && result.gracefulResetNeeded) {
    gracefulReset(uploadRef);
  }
};

const handleExceed = () => {
  ElMessage.warning('一次只能上传一个文件，请先移除已有文件。');
};

// --- API Calls ---
const fetchFileList = async () => {
  loading.value = true;
  try {
    fileList.value = await apiClient.get(`${uploaderConfig.value.apiPrefix}/list`);
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

// --- Utility Functions ---
const formatFileSize = (row, column, cellValue) => {
  if (!cellValue) return '';
  const size = parseFloat(cellValue);
  if (size > 1024 * 1024 * 1024) {
    return `${(size / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  }
  if (size > 1024 * 1024) {
    return `${(size / (1024 * 1024)).toFixed(2)} MB`;
  }
  if (size > 1024) {
    return `${(size / 1024).toFixed(2)} KB`;
  }
  return `${size} Bytes`;
};

// --- Lifecycle Hooks ---
onMounted(() => {
  fetchFileList();
});
</script>

<style scoped>
.file-manager-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.box-card {
  width: 100%;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.file-uploader {
  width: 100%;
}
.progress-container {
  margin-top: 20px;
}
.progress-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #606266;
  margin-top: 5px;
}
.sub-info {
    display: flex;
    gap: 15px;
}
</style> 