import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import SparkMD5 from 'spark-md5';
import apiClient from '../api';

// --- 常量定义 ---
/**
 * 文件分片大小，这里设置为 5MB。
 * @type {number}
 */
const CHUNK_SIZE = 5 * 1024 * 1024;

/**
 * 一个处理分片上传的 Vue Composable，集成了秒传和断点续传功能。
 * @param {object} options - 配置对象。
 * @param {string} options.storageType - 存储类型， 'private' 或 'public'，用于决定API请求的前缀。
 * @returns {object} 返回一个包含响应式状态和方法的对象，用于在组件中控制上传过程。
 */
export function useChunkUploader({ storageType }) {
  // --- 响应式状态定义 ---
  /**
   * @type {import('vue').Ref<boolean>}
   * @description 标记当前是否有文件正在上传。
   */
  const isUploading = ref(false);
  /**
   * @type {import('vue').Ref<{percentage: number, status: string}>}
   * @description 上传进度对象，用于驱动UI中的进度条和状态文本。
   */
  const uploadProgress = ref({ percentage: 0, status: '' });
  /**
   * @type {import('vue').Ref<string>}
   * @description 实时上传速度。
   */
  const uploadSpeed = ref('');
  /**
   * @type {import('vue').Ref<string>}
   * @description 上传已耗时。
   */
  const elapsedTime = ref('00:00');
  /**
   * @type {import('vue').Ref<NodeJS.Timeout|null>}
   * @description 计时器的引用，用于在上传结束后清除。
   */
  const uploadTimer = ref(null);

  /**
   * @type {string}
   * @description 根据存储类型构建的API请求前缀，例如 "/private" 或 "/public"。
   */
  const apiPrefix = `/${storageType}`;

  // --- 内部辅助函数 ---

  /**
   * 计算文件的MD5哈希值。
   * @param {File} file - 需要计算哈希的文件对象。
   * @returns {Promise<string>} 返回一个Promise，解析后为文件的MD5哈希字符串。
   */
  const calculateFileHash = (file) => {
    return new Promise((resolve, reject) => {
      const spark = new SparkMD5.ArrayBuffer();
      const fileReader = new FileReader();
      const totalChunks = Math.ceil(file.size / CHUNK_SIZE);
      let currentChunk = 0;

      fileReader.onload = (e) => {
        spark.append(e.target.result);
        currentChunk++;
        if (currentChunk < totalChunks) {
          loadNext();
        } else {
          resolve(spark.end());
        }
      };
      fileReader.onerror = () => reject('文件读取失败');

      const loadNext = () => {
        const start = currentChunk * CHUNK_SIZE;
        const end = Math.min(start + CHUNK_SIZE, file.size);
        fileReader.readAsArrayBuffer(file.slice(start, end));
      };
      loadNext();
    });
  };

  /**
   * 启动一个计时器，用于更新上传耗时。
   */
  const startTimer = () => {
    let seconds = 0;
    uploadTimer.value = setInterval(() => {
      seconds++;
      const mins = Math.floor(seconds / 60).toString().padStart(2, '0');
      const secs = (seconds % 60).toString().padStart(2, '0');
      elapsedTime.value = `${mins}:${secs}`;
    }, 1000);
  };

  /**
   * 停止并清除计时器。
   */
  const stopTimer = () => {
    clearInterval(uploadTimer.value);
    uploadTimer.value = null;
  };
  
  /**
   * 重置上传状态，通常在上传失败或取消时调用。
   */
  const resetUploadState = () => {
    isUploading.value = false;
    stopTimer();
  }
  
  /**
   * 在上传成功后，优雅地重置UI状态（如进度条和文件列表）。
   * @param {import('vue').Ref} uploadRef - Element Plus上传组件的引用。
   * @param {number} [duration=3000] - 延迟执行的毫秒数。
   */
   const gracefulReset = (uploadRef, duration = 3000) => {
        setTimeout(() => {
            if (!isUploading.value) { // 再次确认已无上传任务
                uploadProgress.value = { percentage: 0, status: '' };
                uploadSpeed.value = '';
                elapsedTime.value = '00:00';
                if (uploadRef.value) {
                    uploadRef.value.clearFiles();
                }
            }
        }, duration);
    };

  // --- 核心上传逻辑 ---
  /**
   * 处理文件上传的主函数。
   * @param {File} file - 用户选择的待上传文件。
   * @param {Function} onUploadComplete - 上传成功后的回调函数，通常用于刷新文件列表。
   * @returns {Promise<{isSuccess: boolean, gracefulResetNeeded?: boolean}|undefined>}
   */
  const handleUpload = async (file, onUploadComplete) => {
    if (isUploading.value) {
      ElMessage.warning('已有文件正在上传中，请稍后再试。');
      return;
    }
    // 步骤 0: 初始化状态
    isUploading.value = true;
    uploadProgress.value = { percentage: 0, status: '正在计算文件Hash...' };
    elapsedTime.value = '00:00';
    uploadSpeed.value = '';
    
    // 步骤 1: 计算文件哈希
    let fileHash;
    try {
      fileHash = await calculateFileHash(file);
      uploadProgress.value.status = `文件Hash: ${fileHash}`;
    } catch (e) {
      ElMessage.error(e.toString());
      resetUploadState();
      return;
    }

    // 使用文件哈希作为批次ID，是实现秒传和断点续传的关键
    const batchId = fileHash;

    // 步骤 2: 检查文件是否存在（秒传功能）
    try {
      const checkResult = await apiClient.post(`${apiPrefix}/check`, { fileHash });
      if (checkResult.exists) {
        uploadProgress.value = { percentage: 100, status: '秒传成功！' };
        ElMessage.success('文件已存在，秒传成功！');
        resetUploadState();
        if (onUploadComplete) onUploadComplete();
        return { isSuccess: true, gracefulResetNeeded: true };
      }
    } catch (e) {
      // API请求拦截器会自动处理错误消息
      resetUploadState();
      return { isSuccess: false };
    }

    // 步骤 3: 检查已上传的分片（断点续传功能）
    let uploadedChunks = [];
    try {
        uploadedChunks = await apiClient.get(`${apiPrefix}/uploaded/chunks`, { params: { batchId } });
        if (uploadedChunks && uploadedChunks.length > 0) {
            ElMessage.info(`检测到上次上传进度，将从断点处继续上传。`);
        }
    } catch (e) {
        ElMessage.warning('检查断点失败，将从头开始上传。');
    }

    // 步骤 4: 对文件进行切片，并准备上传任务
    const chunkCount = Math.ceil(file.size / CHUNK_SIZE);
    let uploadedCount = uploadedChunks.length;
    let totalLoaded = uploadedChunks.length * CHUNK_SIZE; // 已上传的总字节数
    
    uploadProgress.value = { 
        percentage: Math.floor((uploadedCount / chunkCount) * 100), 
        status: '准备上传...' 
    };

    const uploadPromises = [];
    const initialLoaded = totalLoaded; // 记录初始已上传大小，用于计算平均速度
    let lastTime = Date.now();
    let lastTotalLoaded = totalLoaded; // 用于计算瞬时速度
    startTimer();

    for (let i = 0; i < chunkCount; i++) {
        if (uploadedChunks.includes(i)) continue; // 跳过已上传的分片

        const chunk = file.slice(i * CHUNK_SIZE, (i + 1) * CHUNK_SIZE);
        const formData = new FormData();
        formData.append('file', chunk);
        formData.append('batchId', batchId);
        formData.append('chunkNumber', i);
        
        const promise = apiClient.post(`${apiPrefix}/upload/chunk`, formData, {
            onUploadProgress: progressEvent => {
                // 这个回调并不能很好地处理并发，因此我们只用它来触发全局进度的重新计算
                // 注意：这里的进度计算是一个简化的估算模型
                let currentTotalLoaded = initialLoaded;
                const now = Date.now();
                const deltaTime = (now - lastTime) / 1000;
                // 为了简化，我们只在每次有分片完成时才大幅更新进度，瞬时速度在分片完成后计算
            }
        }).then(() => {
            uploadedCount++;
            totalLoaded += chunk.size;
            uploadProgress.value = { 
                percentage: Math.floor((uploadedCount / chunkCount) * 100), 
                status: `正在上传分片: ${uploadedCount} / ${chunkCount}` 
            };
            const now = Date.now();
            const speed = (totalLoaded - lastTotalLoaded) / ((now - lastTime) / 1000);
            uploadSpeed.value = `${(speed / 1024 / 1024).toFixed(2)} MB/s`;
        });
        uploadPromises.push(promise);
    }
    
    // 并发上传所有未完成的分片
    try {
        await Promise.all(uploadPromises);
    } catch(e) {
        ElMessage.error('部分分片上传失败，请重试。');
        resetUploadState();
        return { isSuccess: false };
    }

    // 步骤 5: 所有分片上传完毕，通知服务器合并
    try {
      uploadProgress.value.status = '所有分片上传完毕，正在通知服务器合并...';
      const mergeData = {
        batchId: batchId,
        fileName: file.name,
        fileHash: fileHash,
        fileSize: file.size,
        contentType: file.type
      };
      await apiClient.post(`${apiPrefix}/upload/merge`, mergeData);
      
      uploadProgress.value.status = '文件上传成功！';
      uploadProgress.value.percentage = 100; // 确保进度条达到100%
      ElMessage.success('文件上传成功！');
      resetUploadState();
      if (onUploadComplete) onUploadComplete();
      return { isSuccess: true, gracefulResetNeeded: true };

    } catch (e) {
      // API请求拦截器会自动处理错误消息
      resetUploadState();
      return { isSuccess: false };
    }
  };

  // 将需要暴露给组件的状态和方法返回
  return {
    isUploading,
    uploadProgress,
    uploadSpeed,
    elapsedTime,
    handleUpload,
    gracefulReset
  };
} 