import apiClient from '../api';

/**
 * 检查文件是否存在于对象存储中（秒传）。
 * @param {string} storageType - 存储类型 ('private' 或 'public')。
 * @param {string} fileHash - 文件的内容哈希。
 * @returns {Promise<object>} 后端返回的检查结果。
 */
function checkFile(storageType, fileHash) {
  const apiPrefix = `/${storageType}`;
  return apiClient.post(`${apiPrefix}/check`, { fileHash });
}

/**
 * 获取指定批次已上传的分片列表（断点续传）。
 * @param {string} storageType - 存储类型。
 * @param {string} batchId - 上传批次的唯一ID。
 * @returns {Promise<number[]>} 已上传分片的序号列表。
 */
function getUploadedChunks(storageType, batchId) {
  const apiPrefix = `/${storageType}`;
  return apiClient.get(`${apiPrefix}/uploaded/chunks`, { params: { batchId } });
}

/**
 * 上传单个文件分片。
 * @param {string} storageType - 存储类型。
 * @param {FormData} formData - 包含分片数据、批次ID和分片序号的表单数据。
 * @returns {Promise<object>} 后端返回的上传结果。
 */
function uploadChunk(storageType, formData) {
  const apiPrefix = `/${storageType}`;
  return apiClient.post(`${apiPrefix}/upload/chunk`, formData);
}

/**
 * 请求服务器合并所有分片。
 * @param {string} storageType - 存储类型。
 * @param {object} mergeData - 包含合并所需信息的对象。
 * @returns {Promise<object>} 后端返回的合并结果。
 */
function mergeChunks(storageType, mergeData) {
  const apiPrefix = `/${storageType}`;
  return apiClient.post(`${apiPrefix}/upload/merge`, mergeData);
}

/**
 * 封装了所有与后端存储API交互的纯逻辑服务。
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