import { ref, computed } from 'vue';
import { handleFileUploadV2 } from './coreMain';

/**
 * @description 改进的Vue 3组合式函数，基于会话管理的分片上传（含速度平滑与预计剩余时间）
 * @param {object} uploaderConfig - 上传器配置，包含API前缀、文件夹路径等。
 * @returns {object} 返回一个包含上传状态、进度、控制函数等内容的对象。
 */
export function useChunkUploaderV2(uploaderConfig) {
    // --- 响应式状态定义 ---

    /**
     * @description 标记当前是否正在上传。
     * @type {import('vue').Ref<boolean>}
     */
    const isUploading = ref(false);

    /**
     * @description 上传进度，0到100的整数。
     * @type {import('vue').Ref<number>}
     */
    const uploadProgress = ref(0);

    /**
     * @description 上传速度，单位为字节/秒（EMA平滑）。
     * @type {import('vue').Ref<number>}
     */
    const uploadSpeed = ref(0);

    /**
     * @description 预计剩余时间（秒）。
     * @type {import('vue').Ref<number>}
     */
    const etaSeconds = ref(0);

    /**
     * @description 文件哈希值。
     * @type {import('vue').Ref<string>}
     */
    const fileHash = ref('');

    /**
     * @description 已耗时（秒）。
     * @type {import('vue').Ref<number>}
     */
    const elapsedTime = ref(0);

    /**
     * @description 上传状态文本。
     * @type {import('vue').Ref<string>}
     */
    const uploadStatus = ref('');

    /**
     * @description 当前会话ID
     * @type {import('vue').Ref<string>}
     */
    const sessionId = ref('');

    /**
     * @description 计时器引用。
     * @type {number|null}
     */
    let timer = null;

    /**
     * @description 上一次进度更新的时间点。
     * @type {number|null}
     */
    let lastProgressTime = null;

    /**
     * @description 上一次已上传的字节数。
     * @type {number}
     */
    let lastUploadedBytes = 0;

    // EMA 平滑系数 (0-1)，越大越敏感
    const EMA_ALPHA = 0.3;
    // 当前文件总字节数，用于 ETA 计算
    let currentFileSize = 0;

    // --- 计算属性 ---

    /**
     * @description 格式化后的上传速度，自动在 KB/s 和 MB/s 之间切换。
     * @type {import('vue').ComputedRef<string>}
     */
    const formattedUploadSpeed = computed(() => {
        if (uploadSpeed.value === 0) return '0 KB/s';
        if (uploadSpeed.value < 1024 * 1024) {
            return `${(uploadSpeed.value / 1024).toFixed(1)} KB/s`;
        } else {
            return `${(uploadSpeed.value / (1024 * 1024)).toFixed(1)} MB/s`;
        }
    });

    /**
     * @description 格式化后的已耗时，格式为 "分:秒"。
     * @type {import('vue').ComputedRef<string>}
     */
    const formattedElapsedTime = computed(() => {
        const minutes = Math.floor(elapsedTime.value / 60);
        const seconds = elapsedTime.value % 60;
        return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    });

    /**
     * @description 格式化后的预计剩余时间，格式为 "分:秒"。
     * @type {import('vue').ComputedRef<string>}
     */
    const formattedEta = computed(() => {
        const eta = Math.max(0, Math.floor(etaSeconds.value));
        const minutes = Math.floor(eta / 60);
        const seconds = eta % 60;
        return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    });

    // --- 内部辅助函数 ---

    /**
     * @description 启动计时器，每秒更新已用时间。
     */
    const startTimer = () => {
        stopTimer(); // 先停止任何可能存在的计时器
        elapsedTime.value = 0; // 重置耗时
        lastProgressTime = Date.now(); // 记录开始时间
        lastUploadedBytes = 0; // 重置已上传字节
        etaSeconds.value = 0; // 重置预计剩余时间
        timer = setInterval(() => {
            elapsedTime.value++;
        }, 1000);
    };

    /**
     * @description 停止计时器。
     */
    const stopTimer = () => {
        if (timer) {
            clearInterval(timer);
            timer = null;
        }
    };

    /**
     * @description 重置所有上传状态到初始值。
     */
    const resetUploadState = () => {
        isUploading.value = false;
        uploadProgress.value = 0;
        uploadSpeed.value = 0;
        elapsedTime.value = 0;
        etaSeconds.value = 0;
        uploadStatus.value = '';
        fileHash.value = '';
        sessionId.value = '';
        stopTimer();
    };

    /**
     * @description 优雅地重置状态，用于秒传或上传成功后，短暂显示成功状态再重置。
     */
    const gracefulReset = () => {
        setTimeout(() => {
            resetUploadState();
        }, 2000); // 2秒后重置
    };

    // --- 核心上传处理函数 ---

    /**
     * @description 处理文件上传的主函数，调用coreMain.js中的核心逻辑并管理UI状态。
     * @param {File} file - 用户选择的待上传文件。
     * @returns {Promise<object>} 上传结果
     */
    const handleUpload = async (file) => {
        if (!file) return { isSuccess: false, error: '文件不能为空' };
        currentFileSize = file.size;

        // 定义回调函数，用于在核心上传逻辑中更新UI
        const callbacks = {
            onUploadStarted: () => {
                isUploading.value = true;
                uploadStatus.value = '正在计算文件哈希...';
                startTimer();
            },
            onHashCalculated: (hash) => {
                fileHash.value = hash;
                sessionId.value = hash; // 使用哈希作为会话ID
                uploadStatus.value = '哈希计算完成，正在初始化会话...';
            },
            onProgress: ({ percentage, status, totalUploadedBytes }) => {
                if (percentage !== undefined) uploadProgress.value = percentage;
                if (status) uploadStatus.value = status;

                // --- 上传速度计算（EMA平滑）与 ETA 估算 ---
                const now = Date.now();
                if (totalUploadedBytes && lastProgressTime) {
                    const timeDiff = (now - lastProgressTime) / 1000; // 秒
                    if (timeDiff > 0.5) { // 每隔0.5秒以上更新一次速度
                        const bytesDiff = totalUploadedBytes - lastUploadedBytes;
                        const instantSpeed = bytesDiff / timeDiff; // 字节/秒
                        if (uploadSpeed.value === 0) {
                            uploadSpeed.value = Math.round(instantSpeed);
                        } else {
                            uploadSpeed.value = Math.round(EMA_ALPHA * instantSpeed + (1 - EMA_ALPHA) * uploadSpeed.value);
                        }
                        const remainingBytes = Math.max(0, currentFileSize - totalUploadedBytes);
                        etaSeconds.value = uploadSpeed.value > 0 ? remainingBytes / uploadSpeed.value : 0;
                        lastProgressTime = now;
                        lastUploadedBytes = totalUploadedBytes;
                    }
                } else if (totalUploadedBytes) {
                    // 第一次进度更新，初始化时间和字节
                    lastProgressTime = now;
                    lastUploadedBytes = totalUploadedBytes;
                }
            },
            onUploadComplete: () => {
                isUploading.value = false;
                uploadSpeed.value = 0; // 上传完成，速度归零
                etaSeconds.value = 0;
                stopTimer();
            },
        };

        // 调用核心上传函数
        const result = await handleFileUploadV2(file, uploaderConfig, callbacks);

        // 根据上传结果决定是否需要优雅重置
        if (result.gracefulResetNeeded) {
            gracefulReset();
        }

        return result;
    };

    // --- 返回给组件使用的接口 ---
    return {
        isUploading,
        uploadProgress,
        uploadSpeed,
        formattedUploadSpeed,
        elapsedTime,
        formattedElapsedTime,
        formattedEta,
        fileHash,
        sessionId,
        uploadStatus,
        handleUpload,
        resetUploadState,
    };
}