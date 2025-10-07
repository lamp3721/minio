import { ref, computed } from 'vue';
import { handleFileUpload } from './core';

/**
 * @description 这是一个Vue 3的组合式函数(Composable)，为分片上传提供了完整的UI交互和状态管理功能。
 * @param {object} uploaderConfig - 上传器配置，包含API前缀、文件夹路径等。
 * @returns {object} 返回一个包含上传状态、进度、控制函数等内容的对象。
 */
export function useChunkUploader(uploaderConfig) {
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
     * @description 上传速度，单位KB/s。
     * @type {import('vue').Ref<number>}
     */
    const uploadSpeed = ref(0);

    /**
     * @description 已用时间，单位秒。
     * @type {import('vue').Ref<number>}
     */
    const elapsedTime = ref(0);

    /**
     * @description 文件哈希值。
     * @type {import('vue').Ref<string>}
     */
    const fileHash = ref('');

    /**
     * @description 上传状态的文本描述。
     * @type {import('vue').Ref<string>}
     */
    const uploadStatus = ref('等待上传');

    /**
     * @description 计时器ID，用于计算上传速度和时间。
     * @type {number|null}
     */
    let timer = null;

    // --- 计算属性 ---

    /**
     * @description 格式化后的已用时间，例如：'00:01:23'。
     * @type {import('vue').ComputedRef<string>}
     */
    const formattedElapsedTime = computed(() => {
        const hours = Math.floor(elapsedTime.value / 3600).toString().padStart(2, '0');
        const minutes = Math.floor((elapsedTime.value % 3600) / 60).toString().padStart(2, '0');
        const seconds = (elapsedTime.value % 60).toString().padStart(2, '0');
        return `${hours}:${minutes}:${seconds}`;
    });

    // --- 内部函数 ---

    /**
     * @description 启动计时器，每秒更新已用时间。
     */
    const startTimer = () => {
        stopTimer(); // 先停止任何可能存在的计时器
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
        fileHash.value = '';
        uploadStatus.value = '等待上传';
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
     * @description 处理文件上传的主函数，调用core.js中的核心逻辑并管理UI状态。
     * @param {File} file - 用户选择的待上传文件。
     * @returns {Promise<void>}
     */
    const handleUpload = async (file) => {
        if (!file) return;

        // 定义回调函数，用于在核心上传逻辑中更新UI
        const callbacks = {
            onUploadStarted: () => {
                isUploading.value = true;
                uploadStatus.value = '正在计算文件哈希...';
                startTimer();
            },
            onHashCalculated: (hash) => {
                fileHash.value = hash;
                uploadStatus.value = '哈希计算完成，准备上传...';
            },
            onProgress: ({ percentage, status }) => {
                if (percentage !== undefined) uploadProgress.value = percentage;
                if (status) uploadStatus.value = status;
            },
            onUploadComplete: () => {
                isUploading.value = false;
                stopTimer();
            },
        };

        // 调用核心上传函数
        const result = await handleFileUpload(file, uploaderConfig, callbacks);

        // 根据上传结果决定是否需要优雅重置
        if (result.gracefulResetNeeded) {
            gracefulReset();
        }
    };

    // --- 返回给组件使用的接口 ---
    return {
        isUploading,
        uploadProgress,
        uploadSpeed,
        elapsedTime,
        formattedElapsedTime,
        fileHash,
        uploadStatus,
        handleUpload,
        resetUploadState,
    };
}