<template>
  <!-- 
    复用 FileManager 组件来管理私有文件。
    - title: 设置卡片标题为"私有文件"。
    - uploader-config: 传入私有文件上传所需的后端API配置。
    - show-visit-count: 开启"下载次数"列的显示。
    - actions-width: 为操作列提供更宽的宽度以容纳三个按钮。
  -->
  <FileManager
    title="私有文件"
    :uploader-config="uploaderConfig"
    :show-visit-count="true"
    actions-width="280"
  >
    <!--
      使用 #actions 插槽来自定义每行文件的操作按钮。
      私有文件的操作包括：下载、复制临时链接、删除。
    -->
    <template #actions="{ row, fetchFileList }">
      <el-button size="small" type="primary" @click="handleDownload(row)">下载</el-button>
      <el-button size="small" type="success" @click="handleCopyLink(row)">复制链接</el-button>
      <el-button size="small" type="danger" @click="handleDelete(row, fetchFileList)">删除</el-button>
    </template>
  </FileManager>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus';
import apiClient from '../api';
import FileManager from '../components/FileManager.vue';

// --- 上传器配置 (私有文件) ---
const uploaderConfig = {
  apiPrefix: '/private', // 后端API前缀
  folderPath: import.meta.env.VITE_Folder_Path // 从环境变量读取目标文件夹路径
};

// --- 操作处理函数 (私有文件特有) ---

/**
 * @description 处理"下载"按钮的点击事件。
 * @param {object} row - 当前行数据。
 */
const handleDownload = (row) => {
  // 使用动态创建的<a>标签来触发浏览器下载
  const link = document.createElement('a');
  link.href = row.url;
  link.setAttribute('download', row.name); // 设置下载文件名
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  ElMessage.success('文件已开始下载...');
};

/**
 * @description 处理"复制链接"按钮的点击事件。
 * @param {object} row - 当前行数据。
 */
const handleCopyLink = async (row) => {
  try {
    // 将URL复制到剪贴板
    await navigator.clipboard.writeText(row.url);
    ElMessage.success('临时下载链接已复制到剪贴板！');
  } catch (error) {
    console.error("复制链接失败:", error);
    ElMessage.error('复制链接失败！');
  }
};

/**
 * @description 处理"删除"按钮的点击事件。
 * @param {object} row - 当前行数据。
 * @param {Function} fetchFileList - 由 FileManager 组件传入的刷新列表函数。
 */
const handleDelete = async (row, fetchFileList) => {
  try {
    await ElMessageBox.confirm(`确定要删除私有文件 "${row.name}" 吗？此操作不可恢复！`, '警告', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
    // 调用后端删除接口
    await apiClient.delete('/private/delete', { params: { filePath: row.filePath } });
    ElMessage.success('文件删除成功！');
    fetchFileList(); // 刷新列表
  } catch (error) {
    if (error !== 'cancel') {
      console.error("删除文件失败:", error);
    }
  }
};
</script>