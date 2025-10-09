# 通用上传模块使用说明

该模块提供一个与 Vue 解耦的通用上传客户端，以及保留的 Vue 组合式上传器。任何前端上传组件（原生 `<input type="file">`、Element、AntD、React、Svelte 等）都可以通过统一 API 简单调用文件上传。

## 快速开始（通用客户端）

```js
import { createUploader, uploadFile } from '@/uploader';

// 方式一：一次性调用
await uploadFile(file, {
  apiPrefix: '/api/assets',
  folderPath: 'images/',
  chunkSize: 5 * 1024 * 1024, // 可选，自定义分片大小
  maxConcurrency: 4,          // 可选，分片并发上传数
}, {
  onProgress: (p) => console.log(p.percentage, p.status),
  onComplete: () => console.log('完成'),
});

// 方式二：创建可复用的上传器实例
const uploader = createUploader({ apiPrefix: '/api/assets', folderPath: 'docs/' });
uploader
  .onProgress(p => {
    // 这里可以更新任何框架的UI，如React setState、Vue ref、Svelte store
    console.log(p);
  })
  .onComplete(() => console.log('完成'));
await uploader.upload(file);
```

## Vue 组件内使用（保持兼容）

```js
import { useChunkUploaderV2 } from '@/uploader';

const {
  isUploading, uploadProgress, uploadSpeed,
  formattedEta, uploadStatus, handleUpload,
} = useChunkUploaderV2({ apiPrefix: '/api/assets', folderPath: 'videos/' });

// 调用：handleUpload(file)
```

## 配置项说明

- `apiPrefix`: 必填，后端接口前缀，例如 `/api/assets`
- `folderPath`: 选填，目标存储子目录，例如 `images/`
- `chunkSize`: 选填，分片大小（默认 5MB）
- `maxConcurrency`: 选填，并发上传分片数（默认 4，最大 8）

## 进度回调字段

- `percentage`: 0-100 的整数进度
- `status`: 文本状态，如“正在上传分片: 3 / 20”
- `totalUploadedBytes`: 已上传字节数（支持断点续传累加）

## 断点续传与失败重试

- 会话管理支持断点续传：初始化会话后会返回已上传分片序号列表，未上传的分片会被调度上传
- 分片上传与合并操作自带指数退避重试
- 合并失败后保留会话，允许补传分片后再次合并；成功后由定时任务统一清理