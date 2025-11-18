<template>
  <!-- 文件管理器根容器 -->
  <div class="file-manager-container">
    <!-- 上传区域卡片 -->
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <el-tag type="success" size="small">基于会话管理</el-tag>
        </div>
      </template>
      <!-- Element Plus 上传组件 -->
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
            {{ uploadTip }} (使用改进的会话管理)
          </div>
        </template>
      </el-upload>
      
      <!-- 上传进度条容器，仅在有上传任务时显示 -->
      <div v-if="uploadProgress > 0" class="progress-container">
        <el-progress :percentage="uploadProgress" :text-inside="true" :stroke-width="20" class="progress-bar"/>
        <div class="upload-info">
          <span class="upload-status">{{ uploadStatus }}</span>
          <div class="upload-stats">
            <span>速度: {{ formattedUploadSpeed }}</span>
            <span>耗时: {{ formattedElapsedTime }}</span>
            <span>剩余: {{ formattedEta }}</span>
            <span v-if="sessionId">会话: {{ sessionId.substring(0, 8) }}...</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 文件列表卡片 -->
    <el-card class="box-card file-list-card">
      <template #header>
        <div class="card-header">
          <span>{{ title }}列表</span>
          <el-button type="success" @click="fetchFileList" :loading="loading">刷新</el-button>
        </div>
      </template>
      <!-- 文件列表表格 -->
      <el-table :data="fileList" v-loading="loading" style="width: 100%">
        <!-- 插槽：用于在核心列之前插入自定义列 -->
        <slot name="columns-before" :fileList="fileList"></slot>
        
        <el-table-column prop="name" label="文件名" />
        <el-table-column prop="size" label="大小" :formatter="formatFileSize" />
        <el-table-column prop="visitCount" label="下载次数" v-if="showVisitCount"/>

        <!-- 插槽：用于定义操作列的具体按钮 -->
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
import { ref, onMounted, computed } from 'vue';
import { ElMessage } from 'element-plus';
import apiClient from '../uploader/api';
import { useChunkUploaderV2 } from '../uploader';

// --- 组件属性定义 ---
const props = defineProps({
  /**
   * @description 卡片标题，用于区分不同类型的文件管理器。
   */
  title: {
    type: String,
    required: true,
  },
  /**
   * @description 上传组件下方的提示文字。
   */
  uploadTip: {
    type: String,
    default: '支持大文件分片上传，基于会话管理。',
  },
  /**
   * @description 上传器核心配置，传递给 useChunkUploaderV2。
   * @property {string} apiPrefix - 后端接口的前缀，例如 '/api/assets'。
   * @property {string} [folderPath] - 文件在存储中存放的子目录。
   */
  uploaderConfig: {
    type: Object,
    required: true,
  },
  /**
   * @description 是否在文件列表中显示"下载次数"列。
   */
  showVisitCount: {
      type: Boolean,
      default: false,
  },
  /**
   * @description 操作列的宽度。
   */
  actionsWidth: {
      type: String,
      default: '200',
  },
});

// --- 响应式数据 ---
const fileList = ref([]);
const loading = ref(false);
const uploadRef = ref();

// --- 使用改进的分片上传器 ---
const {
  isUploading,
  uploadProgress,
  uploadSpeed,
  formattedUploadSpeed,
  elapsedTime,
  formattedElapsedTime,
  formattedEta,
  fileHash,
  sessionId,
  uploadStatus,
  handleUpload,
  resetUploadState,
} = useChunkUploaderV2(props.uploaderConfig);

// --- 上传组件钩子 ---
/**
 * @description 自定义 el-upload 的上传请求方法。
 * @param {object} options - el-upload 传递的参数，包含 file 对象。
 */
const customUploadRequest = async (options) => {
  const result = await handleUpload(options.file);
  
  // 上传完成后，无论成功与否，都刷新文件列表
  await fetchFileList();
  
  // 如果上传失败，显示错误信息
  if (!result.isSuccess) {
    ElMessage.error(`上传失败: ${result.error || '未知错误'}`);
  }
  
  // 清理 el-upload 组件的文件列表，以便可以再次选择相同的文件
  if (uploadRef.value) {
    uploadRef.value.clearFiles();
  }
};

/**
 * @description 处理文件数量超出限制的情况。
 * @param {Array} files - 当前选择的文件列表。
 */
const handleExceed = (files) => {
  ElMessage.warning(`当前限制选择 1 个文件，本次选择了 ${files.length} 个文件，请重新选择！`);
};

// --- 文件列表管理 ---
/**
 * @description 从后端获取文件列表数据。
 */
const fetchFileList = async () => {
  loading.value = true;
  try {
    const response = await apiClient.get(`${props.uploaderConfig.apiPrefix}/list`);
    fileList.value = response || [];
  } catch (error) {
    console.error("获取文件列表失败:", error);
    ElMessage.error("获取文件列表失败，请检查网络或联系管理员。")
  } finally {
    loading.value = false;
  }
};

// --- 工具函数 ---
/**
 * @description 格式化文件大小，将字节转换为 KB, MB, GB。
 * @param {object} row - 表格行数据。
 * @param {object} column - 表格列信息。
 * @param {number} cellValue - 文件大小（字节）。
 * @returns {string} 格式化后的文件大小字符串。
 */
const formatFileSize = (row, column, cellValue) => {
  if (!cellValue || cellValue === 0) return '0 B';
  
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let size = cellValue;
  let unitIndex = 0;
  
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex++;
  }
  
  return `${size.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
};

// --- 生命周期钩子 ---
onMounted(() => {
  fetchFileList();
});
</script>

<style scoped>
.file-manager-container {
  max-width: 1200px;
  margin: 0 auto;
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

.file-uploader {
  width: 100%;
}

.progress-container {
  margin-top: 20px;
  padding: 15px;
  background-color: #f8f9fa;
  border-radius: 6px;
}

.progress-bar {
  margin-bottom: 10px;
}

.upload-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #606266;
}

.upload-status {
  font-weight: 500;
  color: #409eff;
}

.upload-stats {
  display: flex;
  gap: 15px;
}

.upload-stats span {
  padding: 2px 8px;
  background-color: #e1f3d8;
  border-radius: 4px;
  font-size: 12px;
}

.file-list-card {
  min-height: 400px;
}
</style>