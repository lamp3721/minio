<template>
  <!-- 文件管理器根容器 -->
  <div class="file-manager-container">
    <!-- 上传区域卡片 -->
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
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
            {{ uploadTip }}
          </div>
        </template>
      </el-upload>
      <!-- 上传进度条容器，仅在有上传任务时显示 -->
      <div v-if="uploadProgress.percentage > 0" class="progress-container">
        <el-progress :percentage="uploadProgress.percentage" :text-inside="true" :stroke-width="20" class="progress-bar"/>
        <div class="progress-info">
          <span>{{ uploadProgress.status }}</span>
          <div class="sub-info">
            <span v-if="uploadSpeed" class="upload-speed">速度: {{ uploadSpeed }}</span>
            <span v-if="elapsedTime" class="elapsed-time">耗时: {{ elapsedTime }}</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 文件列表区域卡片 -->
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
import { ref, onMounted, toRefs } from 'vue';
import { ElMessage } from 'element-plus';
import apiClient from '../api';
import { useChunkUploader } from '../composables/useChunkUploader.js';

// --- 组件 Props 定义 ---
const props = defineProps({
  /**
   * @description 卡片和列表的标题。
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
    default: '支持大文件分片上传。',
  },
  /**
   * @description 上传器核心配置，传递给 useChunkUploader。
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
      default: '200'
  }
});

// 将 props 转换为响应式引用
const { uploaderConfig } = toRefs(props);

// --- 响应式状态 ---
/**
 * @description el-upload 组件的引用。
 * @type {import('vue').Ref<any>}
 */
const uploadRef = ref(null);
/**
 * @description 文件列表数据。
 * @type {import('vue').Ref<Array<object>>}
 */
const fileList = ref([]);
/**
 * @description 是否处于加载状态，用于表格和刷新按钮。
 * @type {import('vue').Ref<boolean>}
 */
const loading = ref(false);

// --- 引入分片上传 Composable ---
const {
  uploadProgress, // 上传进度对象 { percentage, status }
  uploadSpeed,    // 上传速度
  elapsedTime,    // 已耗时
  handleUpload,   // 处理上传的核心函数
  gracefulReset,  // 优雅地重置上传组件状态的函数
} = useChunkUploader(uploaderConfig.value);

// --- 上传组件钩子 ---
/**
 * @description 自定义 el-upload 的上传请求方法。
 * @param {object} options - el-upload 传递的参数，包含 file 对象。
 */
const customUploadRequest = async (options) => {
  const result = await handleUpload(options.file, fetchFileList);
  console.log('URL：', result.fileUrl);
  // 如果上传成功且需要UI重置，则调用 gracefulReset
  if (result && result.isSuccess && result.gracefulResetNeeded) {
    gracefulReset(uploadRef);
  }
};

/**
 * @description 处理超出上传文件数量限制的钩子。
 */
const handleExceed = () => {
  ElMessage.warning('一次只能上传一个文件，请先移除已有文件。');
};

// --- API 调用 ---
/**
 * @description 从后端获取文件列表。
 */
const fetchFileList = async () => {
  loading.value = true;
  try {
    // 使用 props 中的 apiPrefix 来构建请求 URL
    fileList.value = await apiClient.get(`${uploaderConfig.value.apiPrefix}/list`);
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
 * @returns {string} 格式化后的大小字符串。
 */
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

// --- 生命周期钩子 ---
/**
 * @description 组件挂载后，自动获取一次文件列表。
 */
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