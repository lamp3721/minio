# MinIO 文件存储服务 (企业级版本)

这是一个现代化、高性能的企业级文件存储服务，基于 Spring Boot 和 Vue 3 构建，集成 MinIO 对象存储。采用**会话管理架构**，支持私有文件和公共资源的安全管理，具备企业级的可靠性和安全性。项目不仅实现了文件服务的基础功能，更通过精心设计的架构和优化策略，展现了企业级应用的良好实践。

## ✨ 核心功能

### 🔐 双重存储模式
- **公共资源库**：支持直接URL访问，适合图片、文档等公开资源
- **私有文件库**：需要授权访问，支持预签名URL，确保数据安全

### 🚀 企业级上传架构
- **会话管理**：后端维护上传状态，确保数据一致性和安全性
- **分片上传**：大文件自动分片，支持高并发上传
- **断点续传**：网络中断后可继续上传，支持会话恢复
- **秒传功能**：基于MD5哈希的文件去重，相同文件瞬间完成
- **并发安全**：解决竞态条件，确保大文件上传的可靠性

### 🛡️ 安全性增强
- **会话验证**：验证分片归属权和完整性
- **状态管理**：防止恶意攻击和数据不一致
- **重试机制**：自动处理临时故障，提高成功率

### 🧹 智能清理
- **自动清理**：上传完成后自动删除临时分片文件
- **访问统计**：记录文件访问次数和最后访问时间
- **会话过期**：自动清理过期的上传会话

### 🎨 现代化前端
- **Vue 3 + Element Plus**：响应式设计，支持图片预览
- **实时进度**：显示上传进度、速度和剩余时间
- **状态监控**：实时显示会话状态和分片进度
- **用户友好**：直观的操作界面和详细的错误提示

## 🛠️ 技术栈

| 分类     | 技术                               |
| :------- | :--------------------------------- |
| **后端** | Spring Boot 3, Java 17, Maven      |
| **数据库** | MySQL, MyBatis-Plus                |
| **对象存储** | MinIO                              |
| **前端**   | Vue 3, Vite, Element Plus, Axios |
| **其他**   | Lombok, Spark-MD5, UUID            |

## 📂 项目结构（后端）

后端项目遵循了经典的分层架构，职责清晰，易于维护。

```
src
└── main
    └── java
        └── org.example.miniodemo
            ├── common           # 通用工具类 (响应封装, 工具类)
            ├── config           # 应用配置 (MinIO, CORS, 文件上传)
            ├── controller       # API 控制器 (RESTful 接口)
            ├── domain           # 领域模型 (数据库实体, 枚举)
            ├── dto              # 数据传输对象 (用于API请求和响应)
            ├── mapper           # MyBatis-Plus Mapper 接口
            ├── repository       # 数据仓库层 (封装数据访问逻辑)
            ├── service          # 核心业务逻辑层
            │   ├── storage      # (抽象的)对象存储服务接口与实现
            │   └── (impl)       # 具体的业务服务实现
            ├── GlobalExceptionHandler.java  # 全局异常处理器
            └── MiniodemoApplication.java    # Spring Boot 主启动类
```

## 🚀 快速开始

### 1. 环境准备

- **Java**: `17` 或更高版本
- **Maven**: `3.6` 或更高版本
- **MySQL**: `8.0` 或更高版本
- **MinIO**: 最新稳定版
- **Node.js**: `16` 或更高版本 (用于运行前端)

### 2. 后端配置与启动

1.  **克隆项目**：
    ```bash
    git clone [your-repository-url]
    cd miniodemo
    ```

2.  **配置MinIO**：
    - 启动您自己的MinIO服务。
    - 修改 `src/main/resources/application-dev.yml` 文件，填入您MinIO服务的正确信息：
      ```yaml
      minio:
        endpoint: http://your-minio-ip:9000
        public-endpoint: http://your-public-ip:9000 # 如果从公网访问，请使用公网IP
        access-key: your-minio-access-key
        secret-key: your-minio-secret-key
      ```

3.  **配置数据库**：
    - 在您的MySQL中创建一个新的数据库，例如 `minio_demo`。
    - 修改 `src/main/resources/application-dev.yml` 文件，填入您的数据库连接信息：
      ```yaml
      spring:
        datasource:
          url: jdbc:mysql://localhost:3306/minio_demo?useSSL=false
          username: your-db-username
          password: your-db-password
      ```

4.  **运行后端**：
    - 使用IDE（如IntelliJ IDEA）直接运行 `MiniodemoApplication.java`。
    - 或者使用Maven在项目根目录运行：
      ```bash
      ./mvnw spring-boot:run
      ```
    - 首次运行时，需要向 `POST http://localhost:8080/minio/buckets/init` 发送一个请求来初始化存储桶。

### 3. 前端启动

1.  **进入前端目录**：
    ```bash
    cd miniovue3
    ```
2.  **安装依赖**：
    ```bash
    npm install
    ```
3.  **启动开发服务器**：
    ```bash
    npm run dev
    ```
    - Vite会自动处理代理，将API请求转发到后端。
4.  **访问应用**：
    - 打开浏览器，访问 `http://localhost:5173` (或命令行提示的其他地址)。

## 🏗️ 架构亮点

### 🔒 企业级安全架构
- **会话管理系统**：
  - 后端维护完整的上传会话状态，防止前端恶意操作
  - 分片路径由后端生成和验证，避免路径遍历攻击
  - 支持会话过期和自动清理机制

### 🔄 高可靠性设计
- **并发安全**：
  - 使用同步锁防止并发更新冲突
  - 前端使用Set数据结构避免竞态条件
  - 重试机制处理临时故障

### 🎯 面向接口编程
- **解耦设计**：
  - 通过 `ObjectStorageService` 接口，业务逻辑与具体存储实现解耦
  - 支持轻松切换到阿里云OSS、AWS S3等其他存储服务
  - `ChunkUploadSessionService` 提供统一的会话管理接口

### 📦 代码复用与模块化
- **抽象基类**：
  - `AbstractChunkedFileService` 封装通用上传逻辑
  - 新旧API共存，保持向后兼容性
  - 组件化前端架构，支持灵活组合

### ⚡ 性能优化
- **异步处理**：
  - 文件访问时间更新、临时分片清理等操作异步化
  - 事件驱动架构，解耦业务逻辑
  - 数据库操作优化，减少锁竞争

### 🛡️ 安全与配置
- **配置驱动**：
  - 所有关键配置外部化到配置文件
  - 支持不同环境的差异化配置
  - CORS策略可配置，增强安全性

### 🔧 异常处理与事务
- **全局异常处理**：
  - 统一的错误响应格式
  - 详细的错误日志和调试信息
  - 事务管理确保数据一致性

---
这个项目是学习和实践现代后端架构的一个优秀范例。欢迎贡献代码，使其更加完善！