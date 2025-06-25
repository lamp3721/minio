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
            只能上传图片文件，文件将公开访问。支持秒传。
          </div>
        </template>
      </el-upload>
       <div v-if="uploadStatus" class="upload-status">
        {{ uploadStatus }}
      </div>
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
            <div class="actions">
              <el-button size="small" type="success" @click="handleCopyPublicLink(file)">复制链接</el-button>
              <el-button size="small" type="danger" @click="handleDelete(file)">删除</el-button>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import axios from 'axios';
import { ElMessage, ElMessageBox } from 'element-plus';
import SparkMD5 from 'spark-md5';
import { API_BASE_URL } from '../api'; // 从全局配置文件导入

const loading = ref(false);
const publicFiles = ref([]);
const uploadStatus = ref('');

const apiClient = axios.create({
  baseURL: API_BASE_URL,
});

const calculateFileHash = (file) => {
  return new Promise((resolve, reject) => {
    const spark = new SparkMD5.ArrayBuffer();
    const fileReader = new FileReader();
    fileReader.onload = (e) => {
      spark.append(e.target.result);
      resolve(spark.end());
    };
    fileReader.onerror = () => reject('文件读取失败');
    fileReader.readAsArrayBuffer(file);
  });
};

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
  const file = options.file;
  uploadStatus.value = '正在计算文件Hash...';
  console.log('【公共资源】开始上传流程...');

  let fileHash;
  try {
    console.log('【公共资源】开始计算文件哈希...');
    fileHash = await calculateFileHash(file);
    uploadStatus.value = `文件Hash: ${fileHash}`;
    console.log(`【公共资源】哈希计算完成: ${fileHash}`);
  } catch (e) {
    ElMessage.error(e);
    uploadStatus.value = 'Hash计算失败！';
    return;
  }

  try {
    console.log(`【公共资源】向后端发送检查请求，哈希: ${fileHash}`);
    const checkResponse = await apiClient.post('/public/check', { fileHash });
    console.log(`【公共资源】收到后端检查响应:`, checkResponse.data);
    if (checkResponse.data.exists) {
      ElMessage.success('文件已存在，秒传成功！');
      uploadStatus.value = '秒传成功！';
      console.log('【公共资源】后端确认文件已存在，触发秒传。');
      fetchPublicFiles();
      setTimeout(() => uploadStatus.value = '', 3000);
      return;
    }
  } catch (e) {
    ElMessage.error('检查文件失败，请稍后重试');
    uploadStatus.value = '检查文件失败！';
    return;
  }

  console.log('【公共资源】后端确认文件不存在，开始执行常规上传。');
  const formData = new FormData();
  formData.append('file', file);
  formData.append('fileHash', fileHash);

  try {
    uploadStatus.value = '正在上传...';
    await apiClient.post('/public/upload', formData);
    ElMessage.success('图片上传成功！');
    uploadStatus.value = '上传成功！';
    fetchPublicFiles(); // 刷新列表
  } catch (error) {
    ElMessage.error('图片上传失败！');
    uploadStatus.value = '上传失败！';
    console.error(error);
  } finally {
      setTimeout(() => uploadStatus.value = '', 3000);
  }
};

const beforeImageUpload = (file) => {
  const isImage = file.type.startsWith('image/');
  if (!isImage) {
    ElMessage.error('只能上传图片格式!');
  }
  return isImage;
};

const handleCopyPublicLink = async (file) => {
  try {
    // 公开资源的URL已经直接可用，无需再向后端请求
    await navigator.clipboard.writeText(file.url);
    ElMessage.success('公开链接已复制到剪贴板！');
  } catch (error) {
    ElMessage.error('复制链接失败！');
    console.error('复制公开链接时出错:', error);
  }
};

const handleDelete = async (file) => {
  try {
    // 弹出确认对话框，防止用户误操作
    await ElMessageBox.confirm(`确定要删除公开文件 "${file.name}" 吗？此操作不可恢复。`, '警告', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    });

    // 使用 hashName 删除
    await apiClient.delete('/public/delete', { params: { fileName: file.hashName } });

    ElMessage.success('文件删除成功！');
    fetchPublicFiles(); // 删除成功后自动刷新列表
  } catch (error) {
    if (error !== 'cancel') { // 'cancel' 是用户主动点击取消时 a
      ElMessage.error('文件删除失败！');
      console.error('删除公共文件时出错:', error);
    } else {
      ElMessage.info('已取消删除操作。');
    }
  }
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
.actions {
  display: flex;
  flex-shrink: 0;
}
.loading-container, .empty-container {
    text-align: center;
    color: #909399;
    padding: 40px 0;
}
</style> 