<template>
  <FileManager
    title="公共文件"
    upload-tip="支持大文件分片上传，文件将公开访问。"
    :uploader-config="uploaderConfig"
  >
    <!-- Define custom columns before the main ones -->
    <template #columns-before="{ fileList: computedFileList }">
       <el-table-column label="预览" width="100">
          <template #default="scope">
            <el-image
                v-if="scope.row.contentType && scope.row.contentType.startsWith('image/')"
                :src="scope.row.url"
                :preview-src-list="imagePreviewList(computedFileList)"
                :initial-index="getImagePreviewIndex(scope.row, computedFileList)"
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
    </template>
    
    <!-- Define the action buttons for each row -->
    <template #actions="{ row, fetchFileList }">
      <el-button size="small" type="success" @click="handleCopyPublicLink(row)">复制链接</el-button>
      <el-button size="small" type="danger" @click="handleDelete(row, fetchFileList)">删除</el-button>
    </template>
  </FileManager>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus';
import { Document, Picture } from '@element-plus/icons-vue';
import apiClient from '../api';
import FileManager from '../components/FileManager.vue'; // Import the new component

// --- Uploader Configuration ---
const uploaderConfig = {
  apiPrefix: '/public',
  folderPath: import.meta.env.VITE_Folder_Path
};

// --- Image Preview Logic (specific to this view) ---
const imagePreviewList = (fileList) => {
  if (!fileList) return [];
  return fileList
    .filter(file => file.contentType && file.contentType.startsWith('image/'))
    .map(file => file.url);
};

const getImagePreviewIndex = (row, fileList) => {
    if (!fileList) return 0;
    const images = fileList.filter(file => file.contentType && file.contentType.startsWith('image/'));
    return images.findIndex(img => img.url === row.url);
}


// --- Action Handlers (specific to this view) ---
const handleCopyPublicLink = async (row) => {
  try {
    await navigator.clipboard.writeText(row.url);
    ElMessage.success('公开链接已复制到剪贴板！');
  } catch (error) {
    ElMessage.error('复制链接失败！');
  }
};

const handleDelete = async (row, fetchFileList) => {
  try {
    await ElMessageBox.confirm(`确定要删除文件 "${row.name}" 吗？`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await apiClient.delete('/public/delete', { params: { fileName: row.path } });
    ElMessage.success('文件删除成功！');
    fetchFileList(); // Refresh the list
  } catch (error) {
    if (error !== 'cancel') {
        console.error(error);
        // API client's interceptor will show the message
    }
  }
};
</script>

<style scoped>
.image-slot-error {
    display: flex;
    justify-content: center;
    align-items: center;
    width: 100%;
    height: 100%;
    background: #f5f7fa;
    color: #c0c4cc;
    font-size: 24px;
}
.file-icon-placeholder {
    display: flex;
    justify-content: center;
    align-items: center;
    width: 60px;
    height: 60px;
    font-size: 30px;
    color: #606266;
}
</style> 