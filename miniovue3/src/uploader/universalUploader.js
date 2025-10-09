import { handleFileUploadV2 } from './coreMain';

/**
 * 通用上传客户端，解耦 Vue，适配任意上传组件/库。
 * 提供极简 API：init、upload、onProgress、onComplete。
 */
export function createUploader(options = {}) {
  const cfg = {
    apiPrefix: options.apiPrefix || '/api/assets',
    folderPath: options.folderPath || '',
    chunkSize: options.chunkSize, // 默认由核心使用
    maxConcurrency: options.maxConcurrency,
  };

  let progressHandler = null;
  let completeHandler = null;

  const onProgress = (fn) => { progressHandler = fn; return api; };
  const onComplete = (fn) => { completeHandler = fn; return api; };

  const upload = async (file) => {
    const callbacks = {
      onUploadStarted: () => progressHandler?.({ status: '开始上传' }),
      onHashCalculated: (hash) => progressHandler?.({ status: '哈希计算完成', fileHash: hash }),
      onProgress: (p) => progressHandler?.(p),
      onUploadComplete: () => completeHandler?.(),
    };
    return handleFileUploadV2(file, cfg, callbacks);
  };

  const api = { upload, onProgress, onComplete };
  return api;
}

/**
 * 便捷函数：一次性上传并返回结果。
 */
export async function uploadFile(file, options = {}, hooks = {}) {
  const uploader = createUploader(options);
  if (hooks.onProgress) uploader.onProgress(hooks.onProgress);
  if (hooks.onComplete) uploader.onComplete(hooks.onComplete);
  return uploader.upload(file);
}