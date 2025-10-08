import SparkMD5 from 'spark-md5';
import { storageService } from '../services/storageService';

/**
 * @description 文件分片大小，固定为5MB。
 * MinIO官方推荐分片大小为5MB到5GB之间，5MB是最小且常用的值。
 * @type {number}
 */
const CHUNK_SIZE = 5 * 1024 * 1024; // 5MB

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
        fileReader.onerror = () => reject('文件读取失败，无法计算MD5');

        const loadNext = () => {
            const start = (currentChunk - 1) * CHUNK_SIZE;
            const end = Math.min(start + CHUNK_SIZE, file.size);
            fileReader.readAsArrayBuffer(file.slice(start, end));
        };
        loadNext();
    });
};

/**
 * @description 处理文件上传的核心函数，包含秒传、断点续传和分片上传的逻辑。
 * @param {File} file - 待上传的文件。
 * @param {object} uploaderConfig - 上传器配置。
 * @param {object} [callbacks={}] - 用于进度更新和事件通知的回调函数集合。
 * @returns {Promise<object>} 返回一个包含上传结果的对象。
 */
export const handleFileUpload = async (file, uploaderConfig, callbacks = {}) => {
    // 从回调中解构出需要的函数
    const { onProgress, onUploadComplete, onHashCalculated, onUploadStarted } = callbacks;

    // 触发上传开始的回调
    onUploadStarted?.();

    // 步骤 1: 计算文件哈希
    let fileHash;
    try {
        fileHash = await calculateFileHash(file);
        // 触发哈希计算完成的回调
        onHashCalculated?.(fileHash);
    } catch (e) {
        console.error(e);
        return { isSuccess: false };
    }

    // 如果文件小于分片大小，则直接上传
    if (file.size < CHUNK_SIZE) {
        try {
            onProgress?.({ percentage: 0, status: '文件较小，正在直接上传...' });
            const formData = new FormData();
            const dto = {
                folderPath: uploaderConfig.folderPath || '',
                fileName: file.name,
                fileHash: fileHash,
                fileSize: file.size,
                contentType: file.type,
            };
            formData.append('file', file);
            formData.append('dto', new Blob([JSON.stringify(dto)], { type: 'application/json' }));

            const result = await storageService.uploadFile(uploaderConfig, formData);
            onProgress?.({ percentage: 100, status: '文件上传成功！' });
            onUploadComplete?.();
            return { isSuccess: true, gracefulResetNeeded: true, fileUrl: result };
        } catch (error) {
            console.error('Direct file upload failed:', error);
            onProgress?.({ status: `直接上传失败: ${error.message}` });
            onUploadComplete?.();
            return { isSuccess: false };
        }
    }


    // 使用文件哈希作为上传批次ID
    const batchId = fileHash;

    // 步骤 2: 检查文件是否已存在（秒传）
    try {
        const checkResult = await storageService.checkFile(uploaderConfig, fileHash);
        if (checkResult.exists) {
            // 文件已存在，触发进度100%和上传完成回调
            onProgress?.({ percentage: 100, status: '秒传成功！' });
            onUploadComplete?.();
            return { isSuccess: true, gracefulResetNeeded: true, fileUrl: checkResult.url };
        }
    } catch (e) {
        console.error(e);
        return { isSuccess: false };
    }

    // 步骤 3: 检查本地存储，实现断点续传
    let uploadedChunks = [];
    let chunkPaths = [];
    const storedChunkData = localStorage.getItem(batchId);
    if (storedChunkData) {
        try {
            const parsedData = JSON.parse(storedChunkData);
            if (parsedData && Array.isArray(parsedData.chunkPaths)) {
                chunkPaths = parsedData.chunkPaths;
                // 从已保存的路径中恢复已上传的分片序号
                uploadedChunks = chunkPaths.map((path, index) => (path ? index + 1 : -1)).filter(index => index !== -1);
            }
        } catch (e) {
            console.warn('解析本地上传记录失败，将从头开始上传。');
            localStorage.removeItem(batchId);
        }
    }

    // 步骤 4: 分片并上传
    const chunkCount = Math.ceil(file.size / CHUNK_SIZE);
    let uploadedCount = uploadedChunks.length;

    const uploadPromises = [];

    for (let i = 1; i <= chunkCount; i++) {
        // 跳过已上传的分片
        if (uploadedChunks.includes(i)) continue;

        const chunk = file.slice((i - 1) * CHUNK_SIZE, i * CHUNK_SIZE);
        const formData = new FormData();
        formData.append('file', chunk);
        formData.append('batchId', batchId);
        formData.append('chunkNumber', i);

        const promise = storageService.uploadChunk(uploaderConfig, formData).then((response) => {
            uploadedCount++;
            // 记录分片路径并保存到localStorage
            chunkPaths[response.chunkNumber - 1] = response.chunkPath;
            localStorage.setItem(batchId, JSON.stringify({ chunkPaths }));
            // 触发进度更新回调
            const totalUploadedBytes = uploadedCount * CHUNK_SIZE;
            onProgress?.({
                percentage: Math.floor((uploadedCount / chunkCount) * 100),
                status: `正在上传分片: ${uploadedCount} / ${chunkCount}`,
                totalUploadedBytes: totalUploadedBytes > file.size ? file.size : totalUploadedBytes // 确保不超过文件总大小
            });
        });
        uploadPromises.push(promise);
    }

    // 等待所有分片上传完成
    try {
        await Promise.all(uploadPromises);
    } catch (e) {
        console.error('部分分片上传失败，请重试。');
        return { isSuccess: false };
    }

    // 步骤 5: 通知服务器合并分片
    try {
        onProgress?.({ status: '正在合并文件...' });
        const mergeData = {
            batchId: batchId,
            fileName: file.name,
            fileHash: fileHash,
            fileSize: file.size,
            contentType: file.type,
            folderPath: uploaderConfig.folderPath || '',
            chunkPaths: chunkPaths.filter(p => p),
        };
        const result = await storageService.mergeChunks(uploaderConfig, mergeData);
        // 清理localStorage
        localStorage.removeItem(batchId);
        // 触发上传成功和完成回调
        onProgress?.({ percentage: 100, status: '文件上传成功！' });
        onUploadComplete?.();
        return { isSuccess: true, gracefulResetNeeded: true, fileUrl: result };
    } catch (e) {
        console.error(e);
        return { isSuccess: false };
    }
};