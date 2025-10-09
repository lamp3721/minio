import SparkMD5 from 'spark-md5';
import { storageService } from './services/storageService';

/**
 * @description 文件分片大小，固定为5MB。
 * MinIO官方推荐分片大小为5MB到5GB之间，5MB是最小且常用的值。
 * @type {number}
 */
// 默认分片大小 5MB，可通过 uploaderConfig.chunkSize 覆盖
const DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024; // 5MB

// 重试相关配置
const MAX_CHUNK_RETRIES = 3;
const MAX_MERGE_RETRIES = 3;
const RETRY_BASE_DELAY_MS = 1000;

/**
 * 延时辅助函数
 * @param {number} ms 毫秒数
 * @returns {Promise<void>}
 */
const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

const shouldRetryChunk = (error) => {
    const code = error?.code;
    const httpStatus = error?.httpStatus;
    const isBusiness = error?.isBusinessError;
    if (!isBusiness && !httpStatus) return true;
    if (httpStatus && httpStatus >= 500) return true;
    if (code === 1001) return true; // FILE_UPLOAD_FAILED
    return false;
};

const shouldRetryMerge = (error) => {
    const code = error?.code;
    const httpStatus = error?.httpStatus;
    if (httpStatus && httpStatus >= 500) return true;
    // 状态不匹配（如 MERGED/EXPIRED）不应重试合并，改为短路处理
    if (code === 1001) return true; // FILE_UPLOAD_FAILED
    return false;
};

/**
 * 上传单个分片（带指数退避重试）
 * @param {object} uploaderConfig - 包含 `apiPrefix`、`folderPath` 等
 * @param {FormData} formData - 包含 `file`、`sessionId`、`chunkNumber`
 * @param {number} chunkNumber - 分片序号（从 1 开始）
 * @param {(p:{status?:string})=>void} [onProgress] - 状态回调
 * @returns {Promise<any>} - 后端返回
 */
const uploadChunkWithRetry = async (uploaderConfig, formData, chunkNumber, onProgress) => {
    let attempt = 0;
    // eslint-disable-next-line no-constant-condition
    while (true) {
        try {
            return await storageService.uploadChunk(uploaderConfig, formData);
        } catch (err) {
            if (attempt < MAX_CHUNK_RETRIES && shouldRetryChunk(err)) {
                attempt += 1;
                onProgress?.({ status: `分片 ${chunkNumber} 上传失败，正在重试(${attempt}/${MAX_CHUNK_RETRIES})...` });
                await sleep(RETRY_BASE_DELAY_MS * attempt);
                continue;
            }
            throw err;
        }
    }
};

/**
 * 合并分片（带状态校验与指数退避重试）
 * @param {object} uploaderConfig - 上传器配置
 * @param {object} mergeData - 合并请求体：{ sessionId, fileName, fileHash, folderPath, expectedChunkCount }
 * @param {string} sessionId - 会话ID
 * @param {number} totalChunks - 分片总数
 * @param {(p:{status?:string})=>void} [onProgress] - 状态回调
 * @returns {Promise<any>} - 合并结果或 null（秒传）
 */
const mergeWithRetry = async (uploaderConfig, mergeData, sessionId, totalChunks, onProgress) => {
    let attempt = 0;
    let lastError;
    while (attempt < MAX_MERGE_RETRIES) {
        try {
            const status = await storageService.getUploadStatus(uploaderConfig, sessionId);
            // 如果后端会话已经是 MERGED，直接视为成功，避免再次发起合并
            if (status.status === 'MERGED') {
                onProgress?.({ status: '文件已合并，秒传成功' });
                return null;
            }
            if (status.uploadedChunks !== totalChunks) {
                onProgress?.({ status: `等待会话同步，已上传 ${status.uploadedChunks}/${totalChunks} ...` });
                await sleep(RETRY_BASE_DELAY_MS);
            }
            return await storageService.mergeChunks(uploaderConfig, mergeData);
        } catch (err) {
            lastError = err;
            // 如果是会话状态不允许合并（例如已 MERGED），再次确认状态后直接成功
            if (err?.code === 1010) { // UPLOAD_SESSION_STATE_MISMATCH
                const s = await storageService.getUploadStatus(uploaderConfig, sessionId);
                if (s.status === 'MERGED') {
                    onProgress?.({ status: '文件已合并，秒传成功' });
                    return null;
                }
            }
            if (shouldRetryMerge(err)) {
                attempt += 1;
                onProgress?.({ status: `合并失败，正在重试(${attempt}/${MAX_MERGE_RETRIES})...` });
                await sleep(RETRY_BASE_DELAY_MS * attempt);
                continue;
            }
            throw err;
        }
    }
    throw lastError;
};

/**
 * 计算文件 MD5 哈希（分片读取以降低内存占用）
 * @param {File} file - 原始文件对象
 * @param {number} [chunkSize=DEFAULT_CHUNK_SIZE] - 分片大小（字节）
 * @returns {Promise<string>} 文件 MD5
 */
const calculateFileHash = (file, chunkSize = DEFAULT_CHUNK_SIZE) => {
    return new Promise((resolve, reject) => {
        const spark = new SparkMD5.ArrayBuffer();
        const fileReader = new FileReader();
        const totalChunks = Math.ceil(file.size / chunkSize);
        let currentChunk = 1;

        fileReader.onload = (e) => {
            spark.append(e.target.result);
            currentChunk++;

            if (currentChunk <= totalChunks) {
                loadNext();
            } else {
                resolve(spark.end());
            }
        };

        fileReader.onerror = () => {
            reject(new Error('文件读取失败'));
        };

        function loadNext() {
            const start = (currentChunk - 1) * chunkSize;
            const end = Math.min(start + chunkSize, file.size);
            fileReader.readAsArrayBuffer(file.slice(start, end));
        }

        loadNext();
    });
};

/**
 * 会话化分片上传核心（可配置分片大小与并发）
 * 1) 计算哈希与秒传；2) 初始化会话断点续传；3) 并发上传分片；4) 合并完成。
 */
export const handleFileUploadV2 = async (file, uploaderConfig, callbacks = {}) => {
    const { onProgress, onUploadComplete, onHashCalculated, onUploadStarted } = callbacks;

    const CHUNK_SIZE = uploaderConfig?.chunkSize || DEFAULT_CHUNK_SIZE;
    const MAX_CONCURRENCY = Math.max(1, Math.min(8, uploaderConfig?.maxConcurrency || 4));

    // 触发上传开始的回调
    onUploadStarted?.();

    // 步骤 1: 计算文件哈希
    let fileHash;
    try {
        fileHash = await calculateFileHash(file, CHUNK_SIZE);
        onHashCalculated?.(fileHash);
    } catch (e) {
        console.error('计算文件哈希失败:', e);
        return { isSuccess: false, error: e.message };
    }

    // 如果文件小于分片大小，则直接上传
    if (file.size < CHUNK_SIZE) {
        try {
            onProgress?.({ percentage: 0, status: '文件较小，正在直接上传...' });
            
            const formData = new FormData();
            formData.append('file', file);
            
            const dto = {
                fileHash: fileHash,
                folderPath: uploaderConfig.folderPath || ''
            };
            formData.append('dto', new Blob([JSON.stringify(dto)], { type: 'application/json' }));

            const result = await storageService.uploadFile(uploaderConfig, formData);
            onProgress?.({ percentage: 100, status: '文件上传成功！' });
            onUploadComplete?.();
            return { isSuccess: true, gracefulResetNeeded: true, fileUrl: result };
        } catch (error) {
            console.error('直接上传失败:', error);
            onProgress?.({ status: `直接上传失败: ${error.message}` });
            onUploadComplete?.();
            return { isSuccess: false, error: error.message };
        }
    }

    // 步骤 2: 初始化上传会话
    const totalChunks = Math.ceil(file.size / CHUNK_SIZE);
    let sessionId;
    
    try {
        onProgress?.({ status: '正在初始化上传会话...' });
        
        const initData = {
            fileName: file.name,
            fileHash: fileHash,
            fileSize: file.size,
            contentType: file.type,
            totalChunks: totalChunks,
            folderPath: uploaderConfig.folderPath || ''
        };
        
        const sessionResponse = await storageService.initUploadSession(uploaderConfig, initData);
        sessionId = sessionResponse.sessionId;
        
        // 检查是否支持秒传或已合并
        if (sessionResponse.status === 'MERGED' || sessionResponse.uploadedChunks === totalChunks) {
            onProgress?.({ percentage: 100, status: '秒传成功！' });
            onUploadComplete?.();
            return { isSuccess: true, gracefulResetNeeded: true };
        }
        
        // 获取已上传的分片
        const uploadedChunkNumbers = sessionResponse.uploadedChunkNumbers || [];
        onProgress?.({ 
            percentage: Math.floor((uploadedChunkNumbers.length / totalChunks) * 100),
            status: `会话初始化成功，已上传 ${uploadedChunkNumbers.length}/${totalChunks} 个分片` 
        });
        
    } catch (error) {
        console.error('初始化上传会话失败:', error);
        return { isSuccess: false, error: error.message };
    }

    // 步骤 3: 分片上传
    const completedChunks = new Set();

    // 获取当前会话状态
    try {
        const statusResponse = await storageService.getUploadStatus(uploaderConfig, sessionId);
        // 如果会话状态已是 MERGED，直接返回成功，避免后续上传和合并
        if (statusResponse.status === 'MERGED') {
            onProgress?.({ percentage: 100, status: '文件已合并（秒传），无需上传分片。' });
            onUploadComplete?.();
            return { isSuccess: true, gracefulResetNeeded: true };
        }
        const uploadedChunkNumbers = statusResponse.uploadedChunkNumbers || [];
        
        // 将已上传的分片添加到完成集合中
        uploadedChunkNumbers.forEach(num => completedChunks.add(num));
        
        // 并发调度上传任务
        const queue = [];
        for (let i = 1; i <= totalChunks; i++) {
            if (uploadedChunkNumbers.includes(i)) continue; // 跳过已上传
            queue.push(i);
        }

        const runNext = async () => {
            const next = queue.shift();
            if (next === undefined) return;

            const chunk = file.slice((next - 1) * CHUNK_SIZE, next * CHUNK_SIZE);
            const formData = new FormData();
            formData.append('file', chunk);
            formData.append('sessionId', sessionId);
            formData.append('chunkNumber', next.toString());

            await uploadChunkWithRetry(uploaderConfig, formData, next, onProgress);
            completedChunks.add(next);
            const uploadedCount = completedChunks.size;
            const totalUploadedBytes = uploadedCount * CHUNK_SIZE;
            onProgress?.({
                percentage: Math.floor((uploadedCount / totalChunks) * 100),
                status: `正在上传分片: ${uploadedCount} / ${totalChunks}`,
                totalUploadedBytes: totalUploadedBytes > file.size ? file.size : totalUploadedBytes
            });

            return runNext();
        };

        const workers = Array.from({ length: Math.min(MAX_CONCURRENCY, queue.length) }, () => runNext());
        await Promise.all(workers);
        
        // 验证所有分片都已上传
        if (completedChunks.size !== totalChunks) {
            throw new Error(`分片上传不完整: ${completedChunks.size}/${totalChunks}`);
        }
        
    } catch (e) {
        console.error('部分分片上传失败，请重试。', e);
        return { isSuccess: false, error: '分片上传失败: ' + e.message };
    }

    // 步骤 4: 验证会话状态并合并分片
    try {
        onProgress?.({ status: '验证上传状态...' });
        
        // 等待会话状态更新，最多重试5次
        let retryCount = 0;
        const maxRetries = 5;
        let finalStatusResponse;
        
        while (retryCount < maxRetries) {
            finalStatusResponse = await storageService.getUploadStatus(uploaderConfig, sessionId);
            
            if (finalStatusResponse.uploadedChunks === totalChunks) {
                break; // 所有分片都已上传
            }
            
            if (retryCount < maxRetries - 1) {
                console.log(`等待会话状态更新... 重试 ${retryCount + 1}/${maxRetries}, 当前: ${finalStatusResponse.uploadedChunks}/${totalChunks}`);
                await new Promise(resolve => setTimeout(resolve, 1000)); // 等待1秒
            }
            retryCount++;
        }
        
        if (finalStatusResponse.uploadedChunks !== totalChunks) {
            throw new Error(`分片上传验证失败: ${finalStatusResponse.uploadedChunks}/${totalChunks}`);
        }
        
        onProgress?.({ status: '正在合并文件...' });
        
        const mergeData = {
            sessionId: sessionId,
            fileName: file.name,
            fileHash: fileHash,
            folderPath: uploaderConfig.folderPath || '',
            expectedChunkCount: totalChunks
        };
        
        const result = await mergeWithRetry(uploaderConfig, mergeData, sessionId, totalChunks, onProgress);
        
        onProgress?.({ percentage: 100, status: '文件上传成功！' });
        onUploadComplete?.();
        return { isSuccess: true, gracefulResetNeeded: true, fileUrl: result };
        
    } catch (e) {
        console.error('文件合并失败:', e);
        return { isSuccess: false, error: '文件合并失败: ' + e.message };
    }
};

// 导出原有函数以保持兼容性
export { calculateFileHash };