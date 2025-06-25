# MinIO 大文件分片上传与秒传 Demo

这是一个前后端分离的项目，旨在演示如何使用 MinIO 作为对象存储服务，并实现大文件的分片上传、**基于内容哈希的秒传**、以及文件的常规管理功能。

## 项目概述

本项目包含一个基于 **Spring Boot** 的后端服务和一个基于 **Vue 3** 的前端应用程序。

- **后端**: 提供了与 MinIO 交互的所有核心逻辑，包括文件上传、下载、删除、分片处理和存储桶管理。通过 RESTful API 对外提供服务。
- **前端**: 一个用户友好的 Web 界面，允许用户上传和管理两类文件：**公共资源**（如图片，可直接访问）和**私有文件**（需要授权访问，支持大文件分片上传）。

### 核心特性

- **分片上传**: 支持将大文件分割成小块独立上传，并在后端合并，提高了大文件上传的稳定性和可靠性。
- **内容寻址存储 (Content-Addressable Storage)**: 系统不使用原始文件名作为存储对象的唯一标识，而是使用文件内容的MD5哈希值。这意味着，**任何内容相同的文件，在后端只会被存储一份**，从根本上实现了数据去重，极大地节省了存储空间。
- **秒传**: 基于内容寻址存储，当用户上传一个文件时，系统会先计算其哈希值。如果发现服务器上已存在具有相同哈希值的对象，则会跳过实际的上传过程，直接完成操作，实现"秒传"。

## 技术栈

### 后端

- **核心框架**: Spring Boot 3.3.1
- **语言**: Java 17
- **对象存储**: MinIO (via `minio-java` SDK 8.5.17)
- **Web**: Spring Web
- **工具**: Lombok

### 前端

- **核心框架**: Vue 3
- **构建工具**: Vite
- **UI 组件库**: Element Plus
- **HTTP客户端**: Axios
- **路由**: Vue Router
- **工具**: `uuid` (用于生成分片上传批次ID), `spark-md5` (用于在客户端计算文件哈希)

## 目录结构

```
.
├── miniodemo_fragmentation         # 根目录
│   ├── miniovue3/                  # 前端 Vue 项目
│   │   ├── src/
│   │   │   ├── api/                # API 配置文件
│   │   │   ├── router/             # 路由
│   │   │   └── views/              # 视图组件 (PrivateFiles.vue, PublicAssets.vue)
│   │   ├── vite.config.js          # Vite 配置文件 (含代理)
│   │   └── package.json            # 前端依赖
│   │
│   ├── src/main/java/org/example/miniodemo/ # 后端 Java 源码
│   │   ├── config/                 # Spring Boot 配置类 (MinioConfig, CorsConfig等)
│   │   ├── controller/             # API 控制器 (Bucket, PrivateFile, PublicAsset)
│   │   ├── dto/                    # 数据传输对象 (MergeRequestDto)
│   │   ├── service/                # 业务逻辑服务
│   │   └── MiniodemoApplication.java # Spring Boot 启动类
│   │
│   ├── src/main/resources/
│   │   └── application.yml         # Spring Boot 核心配置文件
│   │
│   └── pom.xml                     # Maven 项目配置文件
│
└── README.md                       # 本文档
```

## 如何运行

### 1. 启动 MinIO 服务

确保你有一个正在运行的 MinIO 实例。你可以使用 Docker 快速启动一个：

```bash
docker run -p 9000:9000 -p 9001:9001 --name minio \
  -e "MINIO_ROOT_USER=YOUR_ACCESS_KEY" \
  -e "MINIO_ROOT_PASSWORD=YOUR_SECRET_KEY" \
  quay.io/minio/minio server /data --console-address ":9001"
```

### 2. 配置并运行后端

1.  打开 `src/main/resources/application.yml`。
2.  修改 `minio` 配置，填入你的 MinIO 实例地址、`access-key` 和 `secret-key`。
    ```yaml
    minio:
      endpoint: http://127.0.0.1:9000 # 你的MinIO地址
      access-key: YOUR_ACCESS_KEY     # 你的Access Key
      secret-key: YOUR_SECRET_KEY     # 你的Secret Key
      bucket:
        private-files: "private-files"
        public-assets: "public-assets"
    ```
3.  使用你的 IDE（如 IntelliJ IDEA）直接运行 `MiniodemoApplication.java`。服务将启动在 `8080` 端口。
4.  **重要**: 启动后，需要手动调用一次初始化存储桶的接口，以确保 `private-files` 和 `public-assets` 两个桶被创建。你可以使用 `curl` 或 Postman 等工具：
    ```bash
    curl -X POST http://localhost:8080/minio/buckets/init
    ```

### 3. 配置并运行前端

1.  进入前端项目目录：
    ```bash
    cd miniovue3
    ```
2.  安装依赖：
    ```bash
    npm install
    ```
3.  启动开发服务器：
    ```bash
    npm run dev
    ```
4.  前端应用将启动在 `http://localhost:5173` (或Vite指定的其他端口)。打开浏览器访问此地址即可。`vite.config.js` 中已配置了代理，所有对 `/api` 的请求都会被转发到 `http://localhost:8080`，解决了跨域问题。

## API 端点说明

API 根路径为 `/minio`。

### 公共资源 (`/minio/public`)

用于管理可公开访问的文件，如网站图片、CSS等。

-   **`POST /check`**: **[秒传]** 检查文件是否已存在。
    -   请求体: `{"fileHash": "..."}`
    -   返回: `{"exists": true/false}`
-   **`GET /list`**: 获取公共存储桶中所有文件的列表。
    -   返回: `List<Map<String, Object>>`，每个Map包含 `name` (原始文件名), `hashName` (哈希对象名), `size` 和 `url`。
-   **`POST /upload`**: 上传一个文件。
    -   参数: `file` (multipart/form-data), `fileHash` (String)。
    -   返回: 上传成功后文件的公开访问URL。
-   **`DELETE /delete`**: 删除一个文件。
    -   参数: `fileName` (String, **注意：此处应为文件的 `hashName`**)。

### 私有文件 (`/minio/private`)

用于管理需要授权访问的私有文件，并支持分片上传。

-   **`POST /check`**: **[秒传]** 检查文件是否已存在。
    -   请求体: `{"fileHash": "...", "fileName": "..."}`
    -   返回: `{"exists": true/false}`
-   **`GET /list`**: 获取私有存储桶中所有（已合并完成的）文件列表。
    -   返回: `List<Map<String, Object>>`，每个Map包含 `name` (原始文件名), `hashName` (哈希对象名) 和 `size`。
-   **`POST /upload/chunk`**: **[分片上传]** 上传单个文件分片。
    -   参数: `file` (multipart/form-data), `batchId` (String), `chunkNumber` (Integer)。
-   **`POST /upload/merge`**: **[分片上传]** 通知服务器合并指定批次的所有分片。
    -   请求体: `{"batchId": "...", "fileName": "...", "fileHash": "..."}`。
-   **`GET /download-url`**: 获取私有文件的预签名下载URL（有时间限制，推荐方式）。
    -   参数: `fileName` (String, **注意：此处应为文件的 `hashName`**)。
    -   返回: 一个临时的、可直接用于下载的URL。
-   **`DELETE /delete`**: 删除一个私有文件。
    -   参数: `fileName` (String, **注意：此处应为文件的 `hashName`**)。

### 存储桶 (`/minio/buckets`)

-   **`POST /init`**: 初始化应用所需的存储桶。检查并创建在 `application.yml` 中定义的桶。

## 核心实现原理：秒传与内容寻址

本项目的秒传功能并非基于简单的文件名判断，而是基于健壮的"内容寻址"思想。

### 为什么对文件内容哈希？

我们对**文件内容**本身进行哈希（MD5），而不是对文件名。这是因为：
- **唯一性**: 只有文件内容完全相同时，它们的哈希值才会相同。这保证了我们能准确识别出重复的数据。
- **与文件名解耦**: 不同用户可能用不同的名字上传相同内容的文件（如 `report.docx` 和 `总结.docx`），或用相同的名字上传不同内容的文件（如各自的 `avatar.png`）。基于内容哈希可以准确处理这些情况。

### 核心流程

1.  **前端计算哈希**: 当用户选择一个文件，浏览器端的 JavaScript (`spark-md5`库) 会在上传开始前，读取文件的全部二进制内容，并计算出其MD5哈希值。这个哈希值成为了该文件内容的唯一"数字指纹"。

2.  **后端预检查**: 前端将计算出的 `fileHash` 发送到后端的 `/check` 接口。

3.  **后端决策**:
    -   **存在**: 后端使用 `fileHash` 作为对象名，在MinIO中查询。如果找到了同名对象，说明文件已存在。后端立即向前端返回 `{"exists": true}`。前端收到此响应后，会终止上传，并提示用户"秒传成功"。
    -   **不存在**: 如果MinIO中没有该对象，后端返回 `{"exists": false}`。前端收到此响应后，开始执行标准的文件上传流程（对于大文件，即为分片上传）。

4.  **哈希作为对象名存储**: 当一个新文件被上传并（或分片并）合并后，后端在MinIO中存储这个文件时，会使用其**内容哈希**作为最终的**对象名 (Object Name)**。

5.  **元数据存储原始文件名**: 在以上传哈希作为对象名的同时，后端会将用户上传时的**原始文件名**存入该MinIO对象的**元数据 (Metadata)** 中（键为 `X-Amz-Meta-Original-Filename`）。

6.  **列表展示**: 当用户请求文件列表时，后端会遍历存储桶中的所有对象，逐个读取其元数据，将其中保存的原始文件名返回给前端进行展示。这样，用户看到的始终是自己熟悉的文件名，而非一长串无意义的哈希值。

## 性能说明：为什么首次上传变慢了？

在引入秒传功能后，您可能会注意到，上传一个**全新的、服务器上不存在的文件**时，上传开始前会有一个短暂的"计算中"或"准备中"的阶段。这是**正常且符合预期的现象**。

这个"延迟"来源于**第1步：前端计算哈希**。浏览器需要花费一定的时间（取决于文件大小和您电脑的性能）来完整读取文件内容并计算其哈希值。

这是一个必要的技术权衡：我们用首次上传一个新文件时增加的这一点计算时间，换取了未来任何时候再次上传该文件（或其任何副本）时，都能享受到**几乎零耗时**的"秒传"体验，并从根本上节省了服务器的存储空间。

## 前端视图说明

### PublicAssets.vue (`/public`)

-   **功能**: 提供一个简单的界面来上传、预览、复制链接和删除公共图片资源。
-   **实现**:
    -   使用 `el-upload` 并自定义 `http-request` 来处理文件上传，直接调用 `/public/upload` 接口。
    -   通过 `el-gallery` 展示图片列表。
    -   删除和复制链接功能直接与后端API交互。

### PrivateFiles.vue (`/private`)

-   **功能**: 实现私有文件的管理，核心是支持大文件的分片上传。
-   **实现**:
    -   **分片上传**:
        1.  使用 `el-upload` 控件，并接管其上传过程。
        2.  在 `handleUpload` 方法中，使用 `uuid` 生成唯一的 `batchId`。
        3.  将大文件按固定大小（`CHUNK_SIZE = 5MB`）切片。
        4.  使用 `Promise.all` 并发上传所有分片到 `/private/upload/chunk` 接口。
        5.  利用 `axios` 的 `onUploadProgress` 事件实时计算并显示上传进度和速度。
        6.  所有分片上传成功后，调用 `/private/upload/merge` 接口通知后端合并。
    -   **文件列表**: 使用 `el-table` 展示文件，提供下载、复制链接、删除等操作。
    -   **下载**: 调用 `/private/download-url` 获取预签名链接，并通过动态创建 `<a>` 标签的方式触发下载，避免页面跳转。 