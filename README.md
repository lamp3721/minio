# MinIO 大文件分片上传 Demo

这是一个前后端分离的项目，旨在演示如何使用 MinIO 作为对象存储服务，并实现大文件的分片上传、断点续传（逻辑上支持）、秒传（逻辑上支持）以及文件的常规管理功能。

## 项目概述

本项目包含一个基于 **Spring Boot** 的后端服务和一个基于 **Vue 3** 的前端应用程序。

- **后端**: 提供了与 MinIO 交互的所有核心逻辑，包括文件上传、下载、删除、分片处理和存储桶管理。通过 RESTful API 对外提供服务。
- **前端**: 一个用户友好的 Web 界面，允许用户上传和管理两类文件：**公共资源**（如图片，可直接访问）和**私有文件**（需要授权访问，支持大文件分片上传）。

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
- **工具**: `uuid` (用于生成分片上传批次ID)

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

-   **`GET /list`**: 获取公共存储桶中所有文件的列表。
    -   返回: `List<Map<String, Object>>`，每个Map包含 `name` 和 `url`。
-   **`POST /upload`**: 上传一个文件。
    -   参数: `file` (multipart/form-data)。
    -   返回: 上传成功后文件的公开访问URL。
-   **`DELETE /delete`**: 删除一个文件。
    -   参数: `fileName` (String)。

### 私有文件 (`/minio/private`)

用于管理需要授权访问的私有文件，并支持分片上传。

-   **`GET /list`**: 获取私有存储桶中所有（已合并完成的）文件列表。
    -   返回: `List<Map<String, Object>>`，每个Map包含 `name` 和 `size`。
-   **`POST /upload/chunk`**: **[分片上传]** 上传单个文件分片。
    -   参数: `file` (multipart/form-data), `batchId` (String), `chunkNumber` (Integer)。
-   **`POST /upload/merge`**: **[分片上传]** 通知服务器合并指定批次的所有分片。
    -   请求体: `{"batchId": "...", "fileName": "..."}`。
-   **`GET /download-url`**: 获取私有文件的预签名下载URL（有时间限制，推荐方式）。
    -   参数: `fileName` (String)。
    -   返回: 一个临时的、可直接用于下载的URL。
-   **`GET /download`**: 通过后端服务器代理下载文件（不推荐，消耗服务器资源）。
    -   参数: `fileName` (String)。
-   **`DELETE /delete`**: 删除一个私有文件。
    -   参数: `fileName` (String)。

### 存储桶 (`/minio/buckets`)

-   **`POST /init`**: 初始化应用所需的存储桶。检查并创建在 `application.yml` 中定义的桶。

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