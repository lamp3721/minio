# 通用分片上传模块（会话化）

本模块为前端提供一个与框架解耦的会话化分片上传能力，支持可配置分片大小、并发数、重试与断点续传。它既可在 Vue 组件中直接使用，也适用于任意框架（React、Svelte、纯 JS）。

## 为什么这样设计
- 可配置分片与并发：在不同网络与后端限速场景下灵活调优吞吐与稳定性。
- 会话化上传：通过 `sessionId` 管理上传状态，支持断点续传、秒传与可追踪的合并过程。
- 解耦的通用客户端：`universalUploader` 提供最小 API 面，易于在任何环境里调用。
- 健壮的失败重试：分片上传与合并均带指数退避与错误分类，提升整体成功率。

## 会话机制的好处
- 断点续传：客户端或网络中断后，重新获取会话状态，仅上传缺失分片。
- 秒传与校验：服务端可基于 `fileHash` 判断是否已存在，直接返回成功，避免重复流量与 CPU 计算。
- 并发安全：服务端以会话为范围保证分片计数与合并一致性，降低重复与错位风险。
- 观测与治理：会话可用于审计与监控（上传速率、失败分片、耗时等），便于后端治理与限流策略。

## 快速开始

### 便捷一次性调用
```js
import { uploadFile } from './index';

const res = await uploadFile(file, {
  apiPrefix: '/api/assets',
  folderPath: 'images/',
  chunkSize: 5 * 1024 * 1024, // 5MB
  maxConcurrency: 4,
}, {
  onProgress: (p) => console.log('progress', p),
  onComplete: () => console.log('done'),
});

if (res.isSuccess) console.log('File URL:', res.fileUrl);
```

### 创建可复用实例
```js
import { createUploader } from './index';

const uploader = createUploader({
  apiPrefix: '/api/assets',
  folderPath: 'docs/',
  chunkSize: 8 * 1024 * 1024,
  maxConcurrency: 3,
});

uploader
  .onProgress((p) => console.log(p))
  .onComplete(() => console.log('completed'));

const result = await uploader.upload(file);
```

### 在 Vue 组件中使用
```js
import { useChunkUploaderV2 } from './index';

const { upload, state } = useChunkUploaderV2({
  apiPrefix: '/api/assets',
  folderPath: 'videos/',
  chunkSize: 10 * 1024 * 1024,
  maxConcurrency: 2,
});

await upload(file);
// state 中包含 percentage、speed、eta、status、uploadedBytes 等
```

## 配置项
- `apiPrefix`：后端接口前缀，例如 `/api/assets`
- `folderPath`：存储子目录，可选，默认根目录
- `chunkSize`：分片大小（字节），默认 5MB，可调优
- `maxConcurrency`：并发上传分片数，建议 2-6 之间权衡带宽与稳定性

## 进度回调字段
- `percentage`：整体完成百分比（0-100）
- `status`：状态字符串（`hashing`、`uploading`、`merging`、`completed` 等）
- `totalUploadedBytes`：累计已上传字节数（含已存在分片）
- `speed`：估计上传速率（字节/秒）
- `eta`：预计剩余时间（秒），基于速度估算
- `fileHash`：已计算的文件哈希，用于秒传与会话检索

## 会话化上传流程
1. 计算文件哈希（分片读取，内存占用低）
2. 初始化会话：服务端返回 `sessionId` 与已上传分片集合（用于断点续传）
3. 并发上传缺失分片：带指数退避重试与失败分类（网络波动、临时错误等）
4. 会话校验：确保实际上传分片数与预期一致
5. 合并分片：失败会进行重试；如服务端判定已存在则返回秒传结果

## 断点续传与重试
- 重试策略：对临时型错误进行指数退避重试；不可重试错误直接失败并透出原因
- 断点续传：根据服务端返回的 `uploadedChunks` 跳过已存在分片，仅上传缺失部分
- 安全合并：合并前校验会话状态，避免缺分片导致损坏

## 并发调优建议
- 弱网络环境：降低 `maxConcurrency`（1-2），减小 `chunkSize`
- 高带宽环境：提升 `maxConcurrency`（4-6），适度增大 `chunkSize`
- 后端限流：适配接口速率限制，观察 429/503 等响应，调小并发与分片

## API 参考

### `createUploader(options)`
返回一个上传器实例，包含 `upload(file)`、`onProgress(fn)`、`onComplete(fn)`。

### `uploadFile(file, options, hooks)`
一次性上传函数，适合快捷调用；`hooks` 支持 `onProgress`、`onComplete`。

### `handleFileUploadV2(file, uploaderConfig, callbacks)`
核心上传函数，提供更细粒度控制；`callbacks` 与通用客户端一致。

## 常见问题
- Q：为何需要计算 `fileHash`？
  - A：用于秒传与会话检索；避免重复上传相同内容。
- Q：并发越高越好吗？
  - A：不一定，取决于网络与后端限速；需结合观测调优。
- Q：中途失败怎么办？
  - A：会话化使得你只需重试缺失分片；合并失败会自动重试。

---
如需切换现有组件到通用客户端，可参考上述 Quick Start；若你需要示例 PR，我可以直接为 `FileManagerComponent.vue` 接入并演示。

## 跨框架集成指南（Portable Integration）

- 上传模块对 UI 框架无强依赖。通过 `universalUploader` 提供的 API，可在任意框架或原生页面中使用。

### 基础配置（可选）
- 设置基础地址：
  - `import { setApiBaseUrl } from './index'`
  - `setApiBaseUrl('/minio')` 或者你的网关地址（默认已是 `/minio`）。
- 注入通知机制：
  - `import { setNotifier } from './index'`
  - `setNotifier((message, type = 'error', duration = 5000) => toast(message))`
  - 你可绑定任意通知库（React Toast、Ant Message、原生 `alert`、仅 `console`）。

### 纯 JavaScript（无框架）示例
```html
<input id="fileInput" type="file" />
<script type="module">
  import { uploadFile, setApiBaseUrl, setNotifier } from './index.js';

  setApiBaseUrl('/minio');
  setNotifier((msg, type) => console.log(`[${type}]`, msg));

  const input = document.getElementById('fileInput');
  input.addEventListener('change', async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const res = await uploadFile(file, {
      apiPrefix: '/public',
      folderPath: 'file/',
      chunkSize: 5 * 1024 * 1024,
      maxConcurrency: 4,
    }, {
      onProgress: (p) => console.log('progress', p),
      onComplete: () => console.log('done'),
    });
    console.log('result', res);
  });
</script>
```

### 任意框架中的使用（示例伪代码）
- React/Angular/Svelte 等：
  - 在组件事件中调用：
  - `const uploader = createUploader({ apiPrefix: '/private', folderPath: 'file' })`
  - `uploader.onProgress(updateUI).onComplete(showSuccess)`
  - `await uploader.upload(file)`

### 路径约定与后端接口
- 前端实际请求路径为：`<baseURL>/<apiPrefix>/upload/...`
  - 例如 `baseURL='/minio'`、`apiPrefix='/public'` → `/minio/public/upload/init`
- 上传分片、合并、会话状态均由后端处理；本模块内置重试与会话校验逻辑。

### 常见问题
- 并发与稳定性：并发建议 2–6，过高可能因网络/后端限速而不稳。
- 秒传与哈希：默认计算 MD5 用于秒传与会话检索；超大文件可考虑在后端侧做内容匹配与策略优化。