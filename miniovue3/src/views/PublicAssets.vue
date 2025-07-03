<template>
  <!-- 
    复用 FileManager 组件来管理公共文件。
    - title: 设置卡片标题为"公共文件"。
    - upload-tip: 提供定制化的上传提示。
    - uploader-config: 传入公共文件上传所需的后端API配置。
  -->
  <FileManager
    title="公共文件"
    upload-tip="支持大文件分片上传，文件将公开访问。"
    :uploader-config="uploaderConfig"
  >
    <!-- 
      使用 #columns-before 插槽来自定义表格列。
      这里我们添加了一个"预览"列，用于显示图片缩略图。
      - { fileList: computedFileList }: 从插槽作用域中解构出当前文件列表。
    -->
    <template #columns-before="{ fileList: computedFileList }">
       <el-table-column label="预览" width="100">
          <template #default="scope">
            <!-- 如果文件是图片类型，则显示 el-image 实现预览 -->
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
            <!-- 如果不是图片，则显示一个通用的文档图标 -->
            <div v-else class="file-icon-placeholder">
              <el-icon><Document /></el-icon>
            </div>
          </template>
        </el-table-column>
    </template>
    
    <!--
      使用 #actions 插槽来自定义每行文件的操作按钮。
      - { row, fetchFileList }: 从插槽作用域中解构出当前行数据和刷新列表的方法。
    -->
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
import FileManager from '../components/FileManager.vue';

// --- 上传器配置 (公共文件) ---
const uploaderConfig = {
  apiPrefix: '/public', // 后端API前缀
  folderPath: import.meta.env.VITE_Folder_Path // 从环境变量读取目标文件夹路径
};

// --- 图片预览相关逻辑 ---
/**
 * @description 从文件列表中筛选出所有图片文件，生成预览列表。
 * @param {Array<object>} fileList - 当前文件列表。
 * @returns {Array<string>} 只包含图片URL的数组。
 */
const imagePreviewList = (fileList) => {
  if (!fileList) return [];
  return fileList
    .filter(file => file.contentType && file.contentType.startsWith('image/'))
    .map(file => file.url);
};

/**
 * @description 计算当前点击的图片在预览列表中的索引。
 * @param {object} row - 当前行数据。
 * @param {Array<object>} fileList - 当前文件列表。
 * @returns {number} 索引值。
 */
const getImagePreviewIndex = (row, fileList) => {
    if (!fileList) return 0;
    const images = fileList.filter(file => file.contentType && file.contentType.startsWith('image/'));
    return images.findIndex(img => img.url === row.url);
}

// --- 操作处理函数 (公共文件特有) ---
/**
 * @description 处理"复制链接"按钮的点击事件。
 * @param {object} row - 当前行数据。
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
 * @description 处理"删除"按钮的点击事件。
 * @param {object} row - 当前行数据。
 * @param {Function} fetchFileList - 由 FileManager 组件传入的刷新列表函数。
 */
const handleDelete = async (row, fetchFileList) => {
  try {
    await ElMessageBox.confirm(`确定要删除文件 "${row.name}" 吗？此操作不可恢复！`, '警告', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
    // 调用后端删除接口
    await apiClient.delete('/public/delete', { params: { filePath: row.filePath } });
    ElMessage.success('文件删除成功！');
    fetchFileList(); // 调用父组件的方法刷新列表
  } catch (error) {
    // 用户点击"取消"时，error为'cancel'，此时不应显示错误消息
    if (error !== 'cancel') {
        console.error("删除文件失败:", error);
        // API client 的响应拦截器会自动处理并显示网络或服务器错误消息
    }
  }
};
</script>

<style scoped>
/* 图片加载失败时的占位符样式 */
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
/* 非图片文件的图标占位符样式 */
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