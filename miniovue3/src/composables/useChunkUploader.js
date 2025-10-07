import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import SparkMD5 from 'spark-md5';
import { storageService } from '../services/storageService';

// --- 常量定义 ---
/**
 * @description 文件分片大小，固定为5MB。
 * MinIO官方推荐分片大小为5MB到5GB之间，5MB是最小且常用的值。
 * @type {number}
 */
const CHUNK_SIZE = 5 * 1024 * 1024;

/**
 * @description 一个处理分片上传的 Vue Composable，封装了秒传、断点续传和普通上传的所有逻辑。
 * @param {object} uploaderConfig - 上传器配置对象。
 * @param {string} uploaderConfig.apiPrefix - 用于API请求的URL前缀 (例如, '/api/assets' 或 '/api/private')。
 * @param {string} [uploaderConfig.folderPath] - 文件在存储中的目标文件夹路径。
 * @returns {object} 返回一个包含响应式状态和方法的对象，供Vue组件使用。
 */
export function useChunkUploader(uploaderConfig) {
  // --- 响应式状态定义 ---

  /**
   * @description 标记当前是否有文件正在上传。
   * @type {import('vue').Ref<boolean>}
   */
  const isUploading = ref(false);

  /**
   * @description 上传进度状态，用于驱动UI更新。
   * @type {import('vue').Ref<{percentage: number, status: string}>}
   */
  const uploadProgress = ref({ percentage: 0, status: '' });

  /**
   * @description 实时上传速度 (MB/s)。
   * @type {import('vue').Ref<string>}
   */
  const uploadSpeed = ref('');

  /**
   * @description 上传已耗时，格式为 "mm:ss"。
   * @type {import('vue').Ref<string>}
   */
  const elapsedTime = ref('00:00');
  
  /**
   * @description 耗时计时器的实例引用。
   * @type {import('vue').Ref<number|null>}
   */
  const uploadTimer = ref(null);

  // --- 内部辅助函数 ---

  /**
   * @description 计算文件的MD5哈希值。
   * @param {File} file - 需要计算哈希的文件对象。
   * @returns {Promise<string>} 返回文件的MD5哈希字符串。
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
      fileReader.onerror = () => reject('文件读取失败，无法计算MD5');

      const loadNext = () => {
        const start = currentChunk * CHUNK_SIZE;
        const end = Math.min(start + CHUNK_SIZE, file.size);
        fileReader.readAsArrayBuffer(file.slice(start, end));
      };
      loadNext();
    });
  };

  /**
   * @description 启动一个计时器，用于更新UI上显示的已耗时。
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
   * @description 停止并清除计时器。
   */
  const stopTimer = () => {
    clearInterval(uploadTimer.value);
    uploadTimer.value = null;
  };

  /**
   * @description 重置所有与上传相关的状态，通常在上传失败、取消或完成时调用。
   */
  const resetUploadState = () => {
    isUploading.value = false;
    stopTimer();
  }
  
  /**
   * @description 在上传成功后，延迟一段时间后优雅地重置UI状态（如进度条和文件列表）。
   * @param {import('vue').Ref} uploadRef - Element Plus上传组件的`ref`引用。
   * @param {number} [duration=3000] - 延迟执行的毫秒数。
   */
   const gracefulReset = (uploadRef, duration = 3000) => {
        setTimeout(() => {
            if (!isUploading.value) { // 再次确认已无上传任务，防止重置正在进行的上传
                uploadProgress.value = { percentage: 0, status: '' };
                uploadSpeed.value = '';
                elapsedTime.value = '00:00';
                if (uploadRef.value) {
                    uploadRef.value.clearFiles(); // 清空el-upload组件的文件列表
                }
            }
        }, duration);
    };

  // --- 核心上传逻辑 ---
  /**
   * @description 处理文件上传的主流程函数。
   * @param {File} file - 用户选择的待上传文件。
   * @param {Function} onUploadComplete - 上传成功后的回调函数，外部组件可以传入此函数来刷新文件列表。
   * @returns {Promise<{isSuccess: boolean, gracefulResetNeeded?: boolean}|undefined>,fileUrl:String} 返回一个对象，指示上传是否成功以及是否需要后续的UI重置。以及文件的URL
   */
  const handleUpload = async (file, onUploadComplete) => {
    if (isUploading.value) {
      ElMessage.warning('已有文件正在上传中，请稍后再试。');
      return;
    }
    // 步骤 0: 初始化状态
    isUploading.value = true;
    uploadProgress.value = { percentage: 0, status: '正在计算文件哈希...' };
    elapsedTime.value = '00:00';
    uploadSpeed.value = '';
    
    // 步骤 1: 计算文件哈希 (MD5)
    let fileHash;
    try {
      fileHash = await calculateFileHash(file);
      uploadProgress.value.status = `文件哈希计算完成`;
    } catch (e) {
      ElMessage.error(e.toString());
      resetUploadState();
      return;
    }

    // 使用文件哈希作为上传批次ID (batchId)，这是实现秒传和断点续传的关键。
    const batchId = fileHash;

    // 步骤 2: 检查文件是否已存在于服务器（实现秒传）
    try {
      const checkResult = await storageService.checkFile(uploaderConfig, fileHash);
      console.log('文件已存在：', checkResult.url)
      if (checkResult.exists) {
        uploadProgress.value = { percentage: 100, status: '秒传成功！' };
        ElMessage.success('文件已存在，秒传成功！');
        resetUploadState();
        if (onUploadComplete) onUploadComplete();
        return { isSuccess: true, gracefulResetNeeded: true,fileUrl: checkResult.url };
      }
    } catch (e) {
      // API请求的错误消息会由axios拦截器自动处理
      resetUploadState();
      return { isSuccess: false };
    }

    // 步骤 3: 检查已上传的分片，获取断点信息
    let uploadedChunkPaths = [];
    try {
        const response = await storageService.getUploadedChunks(uploaderConfig, batchId);
        uploadedChunkPaths = response || [];
        if (uploadedChunkPaths && uploadedChunkPaths.length > 0) {
            ElMessage.info(`检测到上次的上传进度，将从断点处继续。`);
        }
    } catch (e) {
        ElMessage.warning('检查断点失败，将从头开始上传。');
    }

    // 步骤 4: 对文件进行切片，并并发上传所有未上传的分片
    const chunkCount = Math.ceil(file.size / CHUNK_SIZE);
    const uploadedChunks = uploadedChunkPaths.map(path => parseInt(path.substring(path.lastIndexOf('/') + 1)));
    let uploadedCount = uploadedChunks.length;
    let totalLoaded = uploadedChunks.length * CHUNK_SIZE; // 初始化已上传的总字节数
    const chunkPaths = [...uploadedChunkPaths]; // 新增：用于存储分片路径
    
    uploadProgress.value = { 
        percentage: chunkCount > 0 ? Math.floor((uploadedCount / chunkCount) * 100) : 0,
        status: '准备上传...' 
    };

    const uploadPromises = [];
    let lastTime = Date.now();
    let lastTotalLoaded = totalLoaded; // 用于计算瞬时速度
    startTimer();

    for (let i = 0; i < chunkCount; i++) {
        // 跳过已上传的分片
        if (uploadedChunks.includes(i)) continue;

        const chunk = file.slice(i * CHUNK_SIZE, (i + 1) * CHUNK_SIZE);
        const formData = new FormData();
        formData.append('file', chunk);
        formData.append('batchId', batchId);
        formData.append('chunkNumber', i);
        
        const promise = storageService.uploadChunk(uploaderConfig, formData).then((response) => {
            uploadedCount++;
            totalLoaded += chunk.size;
            chunkPaths[response.chunkNumber] = response.chunkPath; // 存储分片路径
            
            // 更新进度条
            uploadProgress.value = { 
                percentage: Math.floor((uploadedCount / chunkCount) * 100), 
                status: `正在上传分片: ${uploadedCount} / ${chunkCount}` 
            };
            
            // 计算并更新上传速度
            const now = Date.now();
            const timeDiff = (now - lastTime) / 1000; // 秒
            const loadedSinceLast = totalLoaded - lastTotalLoaded;
            if (timeDiff > 0.5) { // 每隔0.5秒更新一次速度，避免频繁刷新
                const speed = loadedSinceLast / timeDiff; // B/s
                uploadSpeed.value = `${(speed / 1024 / 1024).toFixed(2)} MB/s`;
                lastTime = now;
                lastTotalLoaded = totalLoaded;
            }
        });
        uploadPromises.push(promise);
    }
    
    // 等待所有分片上传完成
    try {
        await Promise.all(uploadPromises);
    } catch(e) {
        ElMessage.error('部分分片上传失败，请重试。');
        resetUploadState();
        return { isSuccess: false };
    }

    // 步骤 5: 所有分片上传完毕，通知服务器合并文件
    try {
      uploadProgress.value.status = '正在合并文件...';
      const mergeData = {
        batchId: batchId,
        fileName: file.name,
        fileHash: fileHash,
        fileSize: file.size,
        contentType: file.type,
        folderPath: uploaderConfig.folderPath || '', // 从配置中读取 folderPath，若无则为空
        chunkPaths: chunkPaths.filter(p => p), // 新增：发送分片路径列表
      };
      const result = await storageService.mergeChunks(uploaderConfig, mergeData);
      
      let successMessage = '文件上传成功！';
      uploadProgress.value.status = successMessage;
      uploadProgress.value.percentage = 100; // 确保进度条达到100%
      ElMessage.success(successMessage);
      resetUploadState();
      if (onUploadComplete) onUploadComplete();
      return { isSuccess: true, gracefulResetNeeded: true, fileUrl: result };

    } catch (e) {
      // API请求的错误消息会由axios拦截器自动处理
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
    gracefulReset,
  };
}