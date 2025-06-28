<template>
  <FileManager
    title="私有文件"
    :uploader-config="uploaderConfig"
    :show-visit-count="true"
    actions-width="280"
  >
    <!-- Define the action buttons for each row -->
    <template #actions="{ row, fetchFileList }">
      {{row}}
      <el-button size="small" type="primary" @click="handleDownload(row)">下载</el-button>
      <el-button size="small" type="success" @click="handleCopyLink(row)">复制链接</el-button>
      <el-button size="small" type="danger" @click="handleDelete(row, fetchFileList)">删除</el-button>
    </template>
  </FileManager>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus';
import apiClient from '../api';
import FileManager from '../components/FileManager.vue'; // Import the new component

// --- Uploader Configuration ---
const uploaderConfig = {
  apiPrefix: '/private',
  folderPath: import.meta.env.VITE_Folder_Path
};

// --- Action Handlers (specific to this view) ---
const handleDownload = async (row) => {
  const loadingMessage = ElMessage.info({
    message: '正在生成下载链接...',
    duration: 0,
  });
  try {
    const url = await apiClient.get('/private/download-url', { params: { filePath: row.filePath } });
    loadingMessage.close(); // Immediately close the loading message

    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', row.name);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    ElMessage.success('开始下载...');
  } catch (error) {
    loadingMessage.close(); // Also close on error
    console.error(error); // Interceptor handles user-facing message
  }
};

const handleCopyLink = async (row) => {
  const loadingMessage = ElMessage.info({
    message: '正在生成下载链接...',
    duration: 0,
  });
  try {
    const url = await apiClient.get('/private/download-url', { params: { filePath: row.filePath } });
    await navigator.clipboard.writeText(url);
    loadingMessage.close(); // Immediately close the loading message
    ElMessage.success('下载链接已复制到剪贴板！');
  } catch (error) {
    loadingMessage.close(); // Also close on error
    console.error(error); // Interceptor handles user-facing message
  }
};

const handleDelete = async (row, fetchFileList) => {
  try {
    await ElMessageBox.confirm(`确定要删除文件 "${row.name}" 吗？`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await apiClient.delete('/private/delete', { params: { filePath: row.filePath } });
    ElMessage.success('文件删除成功！');
    fetchFileList(); // Refresh the list
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error); // Interceptor handles user-facing message
    }
  }
};
</script> 