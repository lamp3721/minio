import apiClient from '../api';

/**
 * @description 检查文件是否已存在于服务器（用于实现秒传功能）。
 * @param {object} uploaderConfig - 上传器配置对象。
 * @param {string} uploaderConfig.apiPrefix - API请求前缀 (e.g., '/api/assets')。
 * @param {string} fileHash - 文件的MD5哈希值。
 * @returns {Promise<{exists: boolean}>} 后端返回的检查结果，`exists`为true表示文件已存在。
 */
function checkFile(uploaderConfig, fileHash) {
  return apiClient.post(`${uploaderConfig.apiPrefix}/check`, { fileHash });
}

/**
 * @description 获取指定批次已上传的分片列表（用于实现断点续传）。
 * @param {object} uploaderConfig - 上传器配置对象。
 * @param {string} uploaderConfig.apiPrefix - API请求前缀。
 * @param {string} batchId - 上传批次的唯一ID，通常是文件的哈希值。
 * @returns {Promise<number[]>} 已成功上传的分片序号组成的数组。
 */
function getUploadedChunks(uploaderConfig, batchId) {
  return apiClient.get(`${uploaderConfig.apiPrefix}/uploaded/chunks`, { params: { batchId } });
}

/**
 * @description 上传单个文件分片。
 * @param {object} uploaderConfig - 上传器配置对象。
 * @param {string} uploaderConfig.apiPrefix - API请求前缀。
 * @param {FormData} formData - 包含分片文件(file)、批次ID(batchId)和分片序号(chunkNumber)的表单数据。
 * @returns {Promise<void>} 上传成功则resolve，失败则reject。
 */
function uploadChunk(uploaderConfig, formData) {
  return apiClient.post(`${uploaderConfig.apiPrefix}/upload/chunk`, formData);
}

/**
 * @description 请求服务器合并所有已上传的分片，完成文件上传流程。
 * @param {object} uploaderConfig - 上传器配置对象。
 * @param {string} uploaderConfig.apiPrefix - API请求前缀。
 * @param {object} mergeData - 包含合并所需信息的对象 (batchId, fileName, fileHash, etc.)。
 * @returns {Promise<object>} 后端返回的合并结果，通常包含文件的元数据。
 */
function mergeChunks(uploaderConfig, mergeData) {
  return apiClient.post(`${uploaderConfig.apiPrefix}/upload/merge`, mergeData);
}

/**
 * @description 封装了所有与后端文件存储API交互的服务模块。
 * 这个模块是纯粹的数据逻辑层，不涉及任何UI或组件状态。
 * @property {Function} checkFile - 检查文件是否存在。
 * @property {Function} getUploadedChunks - 获取已上传的分片。
 * @property {Function} uploadChunk - 上传单个分片。
 * @property {Function} mergeChunks - 请求合并分片。
 */
export const storageService = {
  checkFile,
  getUploadedChunks,
  uploadChunk,
  mergeChunks,
}; 