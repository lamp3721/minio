# Modern MinIO File Service

这是一个基于 Spring Boot 和 Vue 3 构建的现代化、高性能文件存储服务。它深度整合了 MinIO 对象存储，并提供了一套完整的解决方案，用于管理私有和公共文件。项目不仅实现了文件服务的基础功能，更通过精心设计的架构和优化策略，展现了企业级应用的良好实践。

## ✨ 核心功能

- **双存储模式**：支持**公共资源库**（Public Assets）和**私有文件库**（Private Files）两种模式。
  - **公共资源**：文件可通过永久公开链接直接访问，适合存放图片、CSS等静态资源。
  - **私有文件**：文件访问受限，必须通过生成的**预签名URL**（有时效性）或后端代理才能安全下载。
- **高性能上传**：
  - **分片上传**：支持将大文件分割成小块并发上传。
  - **断点续传**：上传意外中断后，可以从上次的进度继续，无需从头开始。
  - **秒传**：如果服务器已存在相同内容的文件，系统会通过文件哈希（MD5）识别，瞬间完成上传，避免重复存储。
- **完善的后端服务**：
  - **自动清理**：通过定时任务（Scheduled Task），自动清理因各种原因残留在服务器上的"孤儿"临时分片，保证存储空间不被浪费。
  - **访问时间追踪**：自动记录文件的最后访问时间，为后续的数据生命周期管理提供依据。
- **友好的前端界面**：
  - 基于 Vue 3 和 Element Plus 构建，提供清晰、易用的文件上传和列表管理界面。
  - 实时显示上传进度、速度和预估耗时，提升用户体验。

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

## 🏛️ 架构亮点与设计哲学

- **面向接口编程与依赖倒置**：
  - 通过 `ObjectStorageService` 接口，我们将业务逻辑与具体的MinIO实现解耦。未来如果想替换成阿里云OSS或S3，只需提供一个新的实现类即可，无需改动上层业务代码。
- **代码复用 (DRY - Don't Repeat Yourself)**：
  - `PublicAssetService` 和 `PrivateFileService` 中大量重复的分片上传逻辑被抽象到 `AbstractChunkedFileService` 基类中，极大地减少了代码冗余，提高了可维护性。
- **异步化提升性能**：
  - 对于"更新文件访问时间"、"删除临时分片"等非核心、耗时的操作，我们采用了 `@Async` 将其异步化。这使得API可以更快地响应用户请求，将清理工作交由后台线程处理，显著提升了用户体验。
- **配置驱动与安全性**：
  - 核心配置项（如数据库连接、MinIO地址、CORS跨域策略）全部外部化到 `application.yml` 文件中，应用的行为由配置驱动，无需硬编码。
  - CORS策略不再是简单的 `*`，而是从配置文件中读取，可以为开发、生产环境配置不同的安全策略。
- **优雅的异常处理与事务管理**：
  - 使用 `@ControllerAdvice` 实现全局异常处理，向前端返回统一、友好的错误信息。
  - 在文件合并等关键操作上使用了 `@Transactional` 注解，确保了在操作失败时（如数据库写入失败），能够自动回滚，避免了产生脏数据（例如，文件已在MinIO中合并，但数据库无记录）。

---
这个项目是学习和实践现代后端架构的一个优秀范例。欢迎贡献代码，使其更加完善！ 