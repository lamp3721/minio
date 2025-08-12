# MinIO 文件服务 API 文档

本文档详细说明了 MinIO 文件服务的后端 API 接口。

## 1. 存储桶接口 (Bucket API)

**控制器**: `BucketController.java`
**基础路径**: `/minio/buckets`

---

### 1.1 初始化存储桶

*   **功能描述**: 初始化应用所需的两个核心存储桶：一个用于私有文件（`private-files`），一个用于公共资产（`public-assets`）。如果存储桶已存在，则此操作会直接跳过。该接口通常在应用启动时自动调用。
*   **Endpoint**: `POST /minio/buckets/init`
*   **请求参数**: 无
*   **成功响应 (200 OK)**:
    ```json
    {
        "code": 200,
        "message": "Success",
        "data": "存储桶初始化成功或已存在。"
    }
    ```
*   **失败响应 (500 Internal Server Error)**:
    ```json
    {
        "code": 5003,
        "message": "Bucket creation failed",
        "data": "存储桶初始化失败: [具体的错误信息]"
    }
    ```

## 2. 基础文件接口 (Base API)

**控制器**: `BaseFileController.java`
**说明**: 以下接口是通用的，可被 `PublicAssetController` 和 `PrivateFileController` 继承和复用。请求时需使用具体控制器的基础路径（例如 `/minio/public` 或 `/minio/private`）。

---

### 2.1 上传文件分片

*   **功能描述**: 上传单个文件分片。这是大文件分块上传的核心部分。
*   **Endpoint**: `POST /upload/chunk`
*   **请求参数 (form-data)**:
    *   `file` (file, required): 文件分片内容。
    *   `batchId` (string, required): 文件的唯一标识符，通常是文件内容的哈希值。
    *   `chunkNumber` (integer, required): 当前分片的序号。
*   **成功响应 (200 OK)**:
    ```json
    {
        "code": 200,
        "message": "Success",
        "data": "分片 [chunkNumber] 上传成功"
    }
    ```
*   **失败响应**:
    *   `400 Bad Request`: `{"code": 400, "message": "Bad Request", "data": "文件、批次ID或分片序号不能为空"}`
    *   `500 Internal Server Error`: `{"code": 5002, "message": "File upload failed", "data": "分片上传失败: ..."}`

### 2.2 获取已上传分片列表

*   **功能描述**: 根据文件标识查询已成功上传的分片序号列表，用于实现断点续传。
*   **Endpoint**: `GET /uploaded/chunks`
*   **请求参数**:
    *   `batchId` (string, required): 文件的唯一标识符。
*   **成功响应 (200 OK)**:
    ```json
    {
        "code": 200,
        "message": "Success",
        "data": [1, 2, 3, 5]
    }
    ```
*   **失败响应 (500 Internal Server Error)**: `{"code": 500, "message": "Internal Server Error", "data": []}`

### 2.3 删除文件

*   **功能描述**: 从存储桶中永久删除一个文件。
*   **Endpoint**: `DELETE /delete`
*   **请求参数**:
    *   `filePath` (string, required): 要删除的文件的完整路径。
*   **成功响应 (200 OK)**:
    ```json
    {
        "code": 200,
        "message": "Success",
        "data": "文件删除成功: [filePath]"
    }
    ```
*   **失败响应**:
    *   `400 Bad Request`: `{"code": 400, "message": "Bad Request", "data": "文件名不能为空"}`
    *   `500 Internal Server Error`: `{"code": 5006, "message": "File deletion failed", "data": "删除失败: ..."}`

## 3. 公共文件接口 (Public Assets API)

**控制器**: `PublicAssetController.java`
**基础路径**: `/minio/public`
**说明**: 此控制器处理可公开访问的静态资源。它继承自 `BaseFileController`，复用了第2节中的所有基础接口。

---

### 3.1 获取公共文件列表

*   **功能描述**: 获取公共存储桶中所有文件的列表信息。
*   **Endpoint**: `GET /minio/public/list`
*   **成功响应 (200 OK)**:
    ```json
    {
        "code": 200,
        "message": "Success",
        "data": [{"fileName": "...", "fileSize": ..., "lastModified": "...", "url": "..."}]
    }
    ```

### 3.2 检查文件是否存在（秒传）

*   **功能描述**: 根据文件哈希检查文件是否存在。如果存在，直接返回文件的公开访问URL。
*   **Endpoint**: `POST /minio/public/check`
*   **请求体**: `{"fileHash": "..."}`
*   **成功响应 (200 OK)**:
    ```json
    {
        "code": 200,
        "message": "Success",
        "data": {
            "exists": true,
            "url": "http://localhost:9000/public-assets/..."
        }
    }
    ```

### 3.3 合并公共文件分片

*   **功能描述**: 将所有上传的分片合并成一个完整的公共文件，并返回其最终的公开访问URL。
*   **Endpoint**: `POST /minio/public/upload/merge`
*   **请求体**: `{"fileName": "...", "fileHash": "...", "chunkSize": ...}`
*   **成功响应 (200 OK)**:
    ```json
    {
        "code": 200,
        "message": "Success",
        "data": "http://localhost:9000/public-assets/..."
    }
    ```
*   **失败响应 (500 Internal Server Error)**: `{"code": 5005, "message": "Merge operation failed", "data": "文件合并失败: ..."}`


## 4. 私有文件接口 (Private Files API)

**控制器**: `PrivateFileController.java`
**基础路径**: `/minio/private`
**说明**: 此控制器处理需要授权访问的私有文件。它继承自 `BaseFileController`，复用了第2节中的所有基础接口。

---

### 4.1 获取私有文件列表

*   **功能描述**: 获取私有存储桶中所有已合并文件的列表。
*   **Endpoint**: `GET /minio/private/list`
*   **成功响应 (200 OK)**:
    ```json
    {
        "code": 200,
        "message": "Success",
        "data": [{"fileName": "...", "fileSize": ..., "lastModified": "...", "url": null}]
    }
    ```

### 4.2 检查文件是否存在（秒传）

*   **功能描述**: 根据文件哈希检查私有文件是否存在，用于实现“秒传”功能。
*   **Endpoint**: `POST /minio/private/check`
*   **请求体**: `{"fileHash": "..."}`
*   **成功响应 (200 OK)**:
    ```json
    {
        "code": 200,
        "message": "Success",
        "data": {"exists": true}
    }
    ```

### 4.3 合并文件分片

*   **功能描述**: 将所有上传的分片合并成一个完整的私有文件。
*   **Endpoint**: `POST /minio/private/upload/merge`
*   **请求体**: `{"fileName": "...", "fileHash": "...", "chunkSize": ...}`
*   **成功响应 (200 OK)**:
    ```json
    {
        "code": 200,
        "message": "Success",
        "data": "文件合并成功: [fileName]"
    }
    ```
*   **失败响应 (500 Internal Server Error)**: `{"code": 5005, "message": "Merge operation failed", "data": "文件合并失败: ..."}`

### 4.4 获取预签名下载 URL

*   **功能描述**: 为私有文件生成一个有时间限制的预签名下载链接。
*   **Endpoint**: `GET /minio/private/download-url`
*   **请求参数**:
    *   `filePath` (string, required): 文件的完整路径。
*   **成功响应 (200 OK)**:
    ```json
    {
        "code": 200,
        "message": "Success",
        "data": "http://localhost:9000/private-files/...?X-Amz-Algorithm=..."
    }
    ```
*   **失败响应**:
    *   `400 Bad Request`: `{"code": 400, "message": "Bad Request", "data": "检测到无效的文件路径..."}`
    *   `500 Internal Server Error`: `{"code": 5004, "message": "File download failed", "data": "获取 URL 失败: ..."}`

### 4.5 代理下载私有文件

*   **功能描述**: 通过后端服务器直接下载私有文件。此接口返回文件流。
*   **Endpoint**: `GET /minio/private/download`
*   **请求参数**:
    *   `filePath` (string, required): 文件的完整路径。
*   **成功响应 (200 OK)**:
    *   **Headers**: `Content-Disposition: attachment; filename="..."`, `Content-Type: application/octet-stream`
    *   **Body**: 文件二进制流
