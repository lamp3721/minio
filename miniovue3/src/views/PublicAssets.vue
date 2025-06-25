<template>
  <div class="public-assets-container">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>上传公开图片</span>
        </div>
      </template>
      <el-upload
          :http-request="handlePublicUpload"
          :show-file-list="false"
          :before-upload="beforeImageUpload"
          accept="image/*"
      >
        <el-button type="primary">点击上传</el-button>
        <template #tip>
          <div class="el-upload__tip">
            只能上传图片文件，文件将公开访问。
          </div>
        </template>
      </el-upload>
    </el-card>

    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>已上传的公开资源</span>
           <el-button type="success" @click="fetchPublicFiles" :loading="loading">刷新</el-button>
        </div>
      </template>
      <div v-if="loading" class="loading-container">
        <p>加载中...</p>
      </div>
      <div v-else-if="publicFiles.length === 0" class="empty-container">
        <p>暂无公开资源</p>
      </div>
      <div v-else class="image-gallery">
        <div v-for="file in publicFiles" :key="file.url" class="image-card">
          <el-image :src="file.url" fit="contain" lazy class="gallery-image"/>
          <div class="image-info">
            <span class="image-name" :title="file.name">{{ file.name }}</span>
            <el-button size="small" type="danger" @click="handleDelete(file)">删除</el-button>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import axios from 'axios';
import { ElMessage } from 'element-plus';

const loading = ref(false);
const publicFiles = ref([]);

const apiClient = axios.create({
  baseURL: 'http://localhost:8080/minio',
});

const fetchPublicFiles = async () => {
  loading.value = true;
  try {
    // 注意：这里我们假设后端有一个/public/list接口
    const response = await apiClient.get('/public/list');
    publicFiles.value = response.data;
  } catch (error) {
    ElMessage.error('获取公开资源列表失败！');
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const handlePublicUpload = async (options) => {
  const formData = new FormData();
  formData.append('file', options.file);

  try {
    await apiClient.post('/public/upload', formData);
    ElMessage.success('图片上传成功！');
    fetchPublicFiles(); // 刷新列表
  } catch (error) {
    ElMessage.error('图片上传失败！');
    console.error(error);
  }
};

const beforeImageUpload = (file) => {
  const isImage = file.type.startsWith('image/');
  if (!isImage) {
    ElMessage.error('只能上传图片格式!');
  }
  return isImage;
};

const handleDelete = async (file) => {
    // ... (删除逻辑，需要后端支持)
    ElMessage.info('删除功能待实现');
};


onMounted(() => {
  fetchPublicFiles();
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
.image-gallery {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 15px;
}
.image-card {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.gallery-image {
  width: 100%;
  height: 180px;
  background-color: #f5f7fa;
}
.image-info {
  padding: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.image-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-grow: 1;
  margin-right: 10px;
}
.loading-container, .empty-container {
    text-align: center;
    color: #909399;
    padding: 40px 0;
}
</style> 