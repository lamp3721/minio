<template>
  <div class="improved-upload-page">
    <el-row :gutter="20">
      <!-- 私有文件上传 -->
      <el-col :span="12">
        <FileManagerV2
          title="私有文件 (改进版)"
          :uploader-config="privateUploaderConfig"
          :show-visit-count="true"
          actions-width="280"
        >
          <template #actions="{ row, fetchFileList }">
            <el-button size="small" type="primary" @click="handleDownload(row)">下载</el-button>
            <el-button size="small" type="success" @click="handleCopyLink(row)">复制链接</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row, fetchFileList, 'private')">删除</el-button>
          </template>
        </FileManagerV2>
      </el-col>

      <!-- 公共文件上传 -->
      <el-col :span="12">
        <FileManagerV2
          title="公共文件 (改进版)"
          upload-tip="支持大文件分片上传，基于会话管理，文件将公开访问。"
          :uploader-config="publicUploaderConfig"
        >
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

          <template #actions="{ row, fetchFileList }">
            <el-button size="small" type="success" @click="handleCopyPublicLink(row)">复制链接</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row, fetchFileList, 'public')">删除</el-button>
          </template>
        </FileManagerV2>
      </el-col>
    </el-row>

    <!-- 功能说明 -->
    <el-card class="feature-card">
      <template #header>
        <h3>改进功能说明</h3>
      </template>
      <el-row :gutter="20">
        <el-col :span="8">
          <div class="feature-item">
            <el-icon class="feature-icon" color="#67c23a"><Check /></el-icon>
            <h4>会话管理</h4>
            <p>后端维护上传会话状态，提高安全性和可靠性</p>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="feature-item">
            <el-icon class="feature-icon" color="#409eff"><Warning /></el-icon>
            <h4>安全验证</h4>
            <p>验证分片归属权和完整性，防止恶意攻击</p>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="feature-item">
            <el-icon class="feature-icon" color="#e6a23c"><Refresh /></el-icon>
            <h4>断点续传</h4>
            <p>支持更可靠的断点续传，会话状态持久化</p>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus';
import { Document, Picture, Check, Warning, Refresh } from '@element-plus/icons-vue';
import apiClient from '../api';
import FileManagerV2 from '../components/FileManagerV2.vue';

// --- 上传器配置 ---
const privateUploaderConfig = {
  apiPrefix: '/private',
  folderPath: import.meta.env.VITE_Folder_Path
};

const publicUploaderConfig = {
  apiPrefix: '/public',
  folderPath: import.meta.env.VITE_Folder_Path
};

// --- 私有文件操作 ---
const handleDownload = (row) => {
  const link = document.createElement('a');
  link.href = row.url;
  link.setAttribute('download', row.name);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  ElMessage.success('文件已开始下载...');
};

const handleCopyLink = async (row) => {
  try {
    await navigator.clipboard.writeText(row.url);
    ElMessage.success('临时下载链接已复制到剪贴板！');
  } catch (error) {
    console.error("复制链接失败:", error);
    ElMessage.error('复制链接失败！');
  }
};

// --- 公共文件操作 ---
const handleCopyPublicLink = async (row) => {
  try {
    await navigator.clipboard.writeText(row.url);
    ElMessage.success('公开链接已复制到剪贴板！');
  } catch (error) {
    ElMessage.error('复制链接失败！');
  }
};

// --- 通用删除操作 ---
const handleDelete = async (row, fetchFileList, type) => {
  try {
    await ElMessageBox.confirm(`确定要删除文件 "${row.name}" 吗？此操作不可恢复！`, '警告', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
    
    const apiPrefix = type === 'private' ? '/private' : '/public';
    await apiClient.delete(`${apiPrefix}/delete`, { params: { filePath: row.filePath } });
    ElMessage.success('文件删除成功！');
    fetchFileList();
  } catch (error) {
    if (error !== 'cancel') {
      console.error("删除文件失败:", error);
    }
  }
};

// --- 图片预览相关 ---
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
};
</script>

<style scoped>
.improved-upload-page {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.feature-card {
  margin-top: 30px;
}

.feature-item {
  text-align: center;
  padding: 20px;
}

.feature-icon {
  font-size: 32px;
  margin-bottom: 10px;
}

.feature-item h4 {
  margin: 10px 0;
  color: #303133;
}

.feature-item p {
  color: #606266;
  font-size: 14px;
  line-height: 1.5;
}

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