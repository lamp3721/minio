import apiClient from '../api';

/**
 * @description 初始化上传会话
 * @param {object} uploaderConfig - 上传器配置对象
 * @param {object} initData - 初始化数据
 * @returns {Promise<object>} 会话信息
 */
function initUploadSession(uploaderConfig, initData) {
  return apiClient.post(`${uploaderConfig.apiPrefix}/upload/init`, initData);
}

/**
 * @description 获取上传会话状态
 * @param {object} uploaderConfig - 上传器配置对象
 * @param {string} sessionId - 会话ID
 * @returns {Promise<object>} 会话状态
 */
function getUploadStatus(uploaderConfig, sessionId) {
  return apiClient.get(`${uploaderConfig.apiPrefix}/upload/status/${sessionId}`);
}

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
 * @description 上传单个文件分片（新版本，基于会话）。
 * @param {object} uploaderConfig - 上传器配置对象。
 * @param {string} uploaderConfig.apiPrefix - API请求前缀。
 * @param {FormData} formData - 包含分片文件(file)、会话ID(sessionId)和分片序号(chunkNumber)的表单数据。
 * @returns {Promise<{chunkNumber: number, chunkPath: string}>} 上传成功则resolve，失败则reject。
 */
function uploadChunk(uploaderConfig, formData) {
  return apiClient.post(`${uploaderConfig.apiPrefix}/upload/chunk`, formData);
}

/**
 * @description 请求服务器合并所有已上传的分片（新版本，基于会话）。
 * @param {object} uploaderConfig - 上传器配置对象。
 * @param {string} uploaderConfig.apiPrefix - API请求前缀。
 * @param {object} mergeData - 包含合并所需信息的对象 (sessionId, fileName, fileHash, etc.)。
 * @returns {Promise<object>} 后端返回的合并结果，通常包含文件的元数据。
 */
function mergeChunks(uploaderConfig, mergeData) {
  return apiClient.post(`${uploaderConfig.apiPrefix}/upload/merge-v2`, mergeData);
}

/**
 * @description 请求服务器合并所有已上传的分片（旧版本，保持兼容性）。
 * @param {object} uploaderConfig - 上传器配置对象。
 * @param {string} uploaderConfig.apiPrefix - API请求前缀。
 * @param {object} mergeData - 包含合并所需信息的对象 (batchId, fileName, fileHash, etc.)。
 * @returns {Promise<object>} 后端返回的合并结果，通常包含文件的元数据。
 */
function mergeChunksLegacy(uploaderConfig, mergeData) {
  return apiClient.post(`${uploaderConfig.apiPrefix}/upload/merge`, mergeData);
}

/**
 * @description 直接上传单个文件。
 * @param {object} uploaderConfig - 上传器配置对象。
 * @param {string} uploaderConfig.apiPrefix - API请求前缀。
 * @param {FormData} formData - 包含文件和DTO的表单数据。
 * @returns {Promise<object>} 后端返回的上传结果。
 */
function uploadFile(uploaderConfig, formData) {
    return apiClient.post(`${uploaderConfig.apiPrefix}/upload/file`, formData, {
        headers: {
            'Content-Type': 'multipart/form-data',
        },
    });
}

/**
 * @description 封装了所有与后端文件存储API交互的服务模块。
 * 这个模块是纯粹的数据逻辑层，不涉及任何UI或组件状态。
 * @property {Function} initUploadSession - 初始化上传会话。
 * @property {Function} getUploadStatus - 获取上传状态。
 * @property {Function} checkFile - 检查文件是否存在。
 * @property {Function} uploadChunk - 上传单个分片。
 * @property {Function} mergeChunks - 请求合并分片（新版本）。
 * @property {Function} mergeChunksLegacy - 请求合并分片（旧版本）。
 * @property {Function} uploadFile - 直接上传文件。
 */
export const storageService = {
  initUploadSession,
  getUploadStatus,
  checkFile,
  uploadChunk,
  mergeChunks,
  mergeChunksLegacy,
  uploadFile,
};