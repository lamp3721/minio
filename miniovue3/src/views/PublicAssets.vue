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
      <el-table :data="fileListWithPreviewIndex" v-loading="loading" style="width: 100%">
        <el-table-column label="预览" width="100">
          <template #default="scope">
            <el-image
                v-if="scope.row.contentType && scope.row.contentType.startsWith('image/')"
                :src="scope.row.url"
                :preview-src-list="imagePreviewList"
                :initial-index="scope.row.previewIndex"
                preview-teleported
                style="width: 60px; height: 60px; border-radius: 4px;"
                fit="cover"
                lazy
            >
              <template #error>
                <div class="image-slot-error">
                  <el-icon><Picture /></el-icon>
                </div>
              </template>
            </el-image>
            <div v-else class="file-icon-placeholder">
              <el-icon><Document /></el-icon>
            </div>
          </template>
        </el-table-column>
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
import { ref, onMounted, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Document, Picture } from '@element-plus/icons-vue';
import apiClient from '../api';
import { useChunkUploader } from '../composables/useChunkUploader.js';

// --- 响应式状态定义 ---
const uploadRef = ref(null);
const fileList = ref([]);
const loading = ref(false);

// --- 引入分片上传模块 ---
// 调用Composable函数，获取所有上传相关的状态和方法
const {
  isUploading,
  uploadProgress,
  uploadSpeed,
  elapsedTime,
  handleUpload,
  gracefulReset,
} = useChunkUploader({ storageType: 'public' });

// --- 上传组件钩子 ---
/**
 * 自定义Element Plus上传组件的http-request行为。
 * @param {object} options - Element Plus上传组件传递的参数，包含file对象。
 */
const customUploadRequest = async (options) => {
  const result = await handleUpload(options.file, fetchFileList);
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

// --- 计算属性 (用于UI展示) ---
/**
 * 计算一个仅包含图片URL的数组，用于Element Plus图片预览组件。
 * @returns {string[]} 图片URL列表。
 */
const imagePreviewList = computed(() => {
  return fileList.value
      .filter(file => file.contentType && file.contentType.startsWith('image/'))
      .map(file => file.url);
});

/**
 * 为文件列表中的每个图片文件计算其在预览列表中的索引。
 * 这是实现点击小图能在大图预览中正确定位所必需的。
 * @returns {object[]} 带有 previewIndex 字段的文件列表。
 */
const fileListWithPreviewIndex = computed(() => {
  // 先筛选出所有图片
  const images = fileList.value.filter(file => file.contentType && file.contentType.startsWith('image/'));
  // 为原列表的每一项进行映射
  return fileList.value.map(file => {
    if (file.contentType && file.contentType.startsWith('image/')) {
      return {
        ...file,
        // 找到当前图片在纯图片数组中的位置
        previewIndex: images.findIndex(img => img.url === file.url)
      };
    }
    return file;
  });
});

// --- 文件操作 (本视图特有) ---
/**
 * 从后端获取公共文件列表。
 */
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

/**
 * 处理复制公开访问链接的操作。
 * @param {object} row - 表格中当前行的数据对象。
 */
const handleCopyPublicLink = async (row) => {
  try {
    await navigator.clipboard.writeText(row.url);
    ElMessage.success('公开链接已复制到剪贴板！');
  } catch (error) {
    ElMessage.error('复制链接失败！');
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
    await apiClient.delete('/public/delete', { params: { fileName: row.path } });
    ElMessage.success('文件删除成功！');
    fetchFileList();
  } catch (error) {
    if (error !== 'cancel' && (!error.message || !error.message.includes('cancel'))) {
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
.image-slot-error, .file-icon-placeholder {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 100%;
  background: #f5f7fa;
  color: #c0c4cc;
  font-size: 24px;
}
</style> 