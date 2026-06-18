# 文档摘要与知识库系统 — 设计契约（前后端必须遵守）

> 本文件是 Track A（后端）与 Track B（前端）共同遵守的接口契约。修改前请先讨论，修改后请同步两端。

## 1. 技术栈与版本

| 层 | 技术 | 版本 | 备注 |
|---|---|---|---|
| 后端框架 | Spring Boot | 3.2.x | Java 17 |
| 构建 | Maven | 3.9+ | 单模块 |
| 持久层 | MyBatis-Plus | 3.5.5+ | 简化 CRUD |
| 数据库 | MySQL | 8.0+ | utf8mb4 |
| 前端 | Vue | 3.4+ | Composition API + Vite |
| UI 组件 | Element Plus | 2.4+ | |
| HTTP | Axios | 1.6+ | |
| AI 模型 | MiniMax M3 | - | 通过 OpenAI 兼容协议调用 |

## 2. 工程目录

```
doc-summary-kb/
├── backend/                          # Spring Boot 项目
│   ├── pom.xml
│   ├── src/main/java/com/example/dockb/
│   │   ├── DocKbApplication.java
│   │   ├── common/                   # 统一响应、分页、异常
│   │   ├── config/                   # Web/CORS/MyBatis-Plus 配置
│   │   ├── controller/               # REST 控制器
│   │   ├── service/                  # 业务层
│   │   ├── mapper/                   # MyBatis Mapper
│   │   ├── entity/                   # 实体
│   │   ├── dto/                      # 入参 DTO
│   │   ├── vo/                       # 出参 VO
│   │   ├── client/                   # MiniMax M3 SDK 封装
│   │   └── util/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── mapper/                   # XML
│   │   └── db/                       # 初始化 SQL
│   └── src/test/java/...
├── frontend/                         # Vue 3 项目
│   ├── package.json
│   ├── vite.config.js
│   ├── index.html
│   ├── src/
│   │   ├── main.js
│   │   ├── App.vue
│   │   ├── router/index.js
│   │   ├── api/                      # axios 封装
│   │   ├── views/
│   │   ├── components/
│   │   └── utils/
│   └── public/
├── docs/
│   ├── design/CONTRACT.md            # 本文件
│   ├── design/SCHEMA.sql             # 数据库 DDL
│   └── api/openapi.yaml              # 可选，OpenAPI 描述
├── deploy/
│   ├── docker-compose.yml            # 可选
│   └── README.md
├── README.md
└── .gitignore
```

## 3. 数据库 Schema（MySQL 8.0）

```sql
CREATE DATABASE IF NOT EXISTS doc_kb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE doc_kb;

-- 文档主表
CREATE TABLE IF NOT EXISTS document (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    title         VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    file_path     VARCHAR(512) NOT NULL,
    file_type     VARCHAR(20)  NOT NULL COMMENT 'pdf/txt/md/docx',
    file_size     BIGINT       NOT NULL DEFAULT 0,
    category      VARCHAR(64)  NOT NULL DEFAULT '未分类' COMMENT 'AI 自动分类',
    tags          VARCHAR(255) NOT NULL DEFAULT '' COMMENT '英文逗号分隔',
    summary       TEXT         NULL COMMENT 'AI 摘要',
    status        VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/processing/done/failed',
    error_msg     VARCHAR(500) NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category (category),
    INDEX idx_status (status),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 文档分块表（用于检索 / 引用定位）
CREATE TABLE IF NOT EXISTS document_chunk (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    document_id  BIGINT       NOT NULL,
    chunk_index  INT          NOT NULL COMMENT '段落序号',
    content      MEDIUMTEXT   NOT NULL,
    char_count   INT          NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doc (document_id),
    CONSTRAINT fk_chunk_doc FOREIGN KEY (document_id) REFERENCES document(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 问答历史表
CREATE TABLE IF NOT EXISTS qa_history (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    question     TEXT         NOT NULL,
    answer       MEDIUMTEXT   NOT NULL,
    citations    JSON         NULL COMMENT '引用来源 JSON 数组',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 4. 统一响应格式

```json
{
  "code": 0,
  "message": "ok",
  "data": { ... }
}
```

- `code`: 0 成功；非 0 失败（统一 500 业务异常 + 401/403/404 常规）
- `data`: 任意业务对象或数组或分页对象
- 分页 `data` 结构：`{ "list": [...], "total": 12, "page": 1, "size": 10 }`

## 5. REST API 契约

> Base URL: `http://localhost:8080/api`
> 所有时间格式：`yyyy-MM-dd HH:mm:ss`

### 5.1 文档管理

#### `POST /api/documents/upload`
- Content-Type: `multipart/form-data`
- 字段：`file`（必填，单文件 ≤ 20MB，支持 pdf/txt/md/docx）
- 响应 `data`：`{ "id": 1, "status": "pending" }`
- 业务行为：保存文件到本地 `uploads/` 目录，写入 `document` 表（status=pending），**异步**触发摘要与分类。

#### `GET /api/documents`
- Query: `page`(默认1), `size`(默认10), `keyword`(可选, 模糊匹配title), `category`(可选)
- 响应 `data`：`{ "list": [DocumentVO], "total": N, "page": 1, "size": 10 }`
- `DocumentVO`：
  ```json
  {
    "id": 1, "title": "...", "originalName": "...", "fileType": "pdf",
    "fileSize": 12345, "category": "技术", "tags": "AI,大模型",
    "summary": "...", "status": "done", "errorMsg": null,
    "createdAt": "2026-06-16 17:00:00", "updatedAt": "2026-06-16 17:00:30"
  }
  ```

#### `GET /api/documents/{id}`
- 响应 `data`：`DocumentVO`（含 `summary`）
- 错误：404 `DOCUMENT_NOT_FOUND`

#### `DELETE /api/documents/{id}`
- 删除文档 + 物理文件 + chunks（外键级联）
- 响应 `data`：`null`

#### `GET /api/documents/{id}/file`
- 直接返回文件二进制（用于在线预览），Content-Disposition: inline

### 5.2 分类与标签

#### `GET /api/documents/categories`
- 响应 `data`：`["技术", "法律", "其他"]`（去重后的分类列表）

### 5.3 检索

#### `GET /api/documents/search`
- Query: `q`(必填), `topK`(默认5, 最大20)
- 响应 `data`：
  ```json
  {
    "query": "MiniMax M3 接口",
    "hits": [
      {
        "documentId": 1,
        "title": "MiniMax M3 接入指南",
        "category": "技术",
        "score": 0.87,
        "snippet": "...命中片段，前后各 80 字...",
        "chunkId": 12
      }
    ]
  }
  ```
- 业务：基于文档 chunk 做关键词 + MiniMax M3 语义重排（轻量实现：先 LIKE，再用 M3 让模型从候选中挑 topK，并给出 score 0~1）

### 5.4 问答

#### `POST /api/qa/ask`
- Body:
  ```json
  { "question": "M3 的计费规则是什么？", "topK": 5 }
  ```
- 响应 `data`：
  ```json
  {
    "id": 7,
    "question": "M3 的计费规则是什么？",
    "answer": "根据文档内容，...",
    "citations": [
      { "documentId": 1, "title": "M3 接入指南", "chunkId": 12, "snippet": "...", "score": 0.91 }
    ],
    "createdAt": "2026-06-16 17:10:00"
  }
  ```
- 业务：检索 -> 拼 prompt 喂给 M3 -> 要求引用 -> 解析 citations -> 入库 qa_history

#### `GET /api/qa/history`
- Query: `page`, `size`
- 响应 `data`：分页 `QaHistoryVO`

### 5.5 健康检查

#### `GET /api/health`
- 响应 `data`：`{ "status": "up", "m3Reachable": true, "m3Model": "MiniMax-M3" }`

## 6. MiniMax M3 调用约定

**M3 提供 OpenAI 兼容协议**，统一封装在 `client/M3Client.java`：

```java
public interface M3Client {
    // 分类（短文本 → 类别）
    String classify(String text, List<String> candidates);

    // 摘要（长文 → 200~500 字）
    String summarize(String text);

    // 关键词/标签
    List<String> extractTags(String text);

    // 检索重排
    List<RankedHit> rerank(String query, List<String> candidates);

    // 问答 + 引用
    QaResult answer(String question, List<Citation> context);
}
```

- Base URL 默认 `https://api.MiniMax.chat/v1`
- API Key 走环境变量 `MiniMax_API_KEY`，application.yml 用占位符
- 默认模型 `MiniMax-M3`
- 超时：连接 10s，读取 60s
- 失败重试：1 次（指数退避）

## 7. 异步处理约定

- 上传后立刻返回 `id`，摘要/分类用 `TaskExecutor`（线程池 core=2, max=4, queue=50）异步执行
- 状态机：`pending → processing → done | failed`
- 前端列表轮询：上传后前端每 3s 拉一次该文档详情，发现 status≠pending 即停止轮询并刷新

## 8. 前端路由与页面

| 路径 | 页面 | 说明 |
|---|---|---|
| `/` | HomeView | 系统概览（文档数、问答数、分类分布） |
| `/documents` | DocumentListView | 文档列表 + 上传 + 检索 + 删除 |
| `/documents/:id` | DocumentDetailView | 文档详情：摘要、分类、Tags、分块列表、问答 |
| `/qa` | QaView | 问答页：输入框、历史记录、引用面板 |

## 9. 配置与启动

- 后端：`application.yml` 端口 8080，CORS 放行 `http://localhost:5173`
- 前端：`vite.config.js` 代理 `/api` → `http://localhost:8080`
- 启动顺序：MySQL → 后端 → 前端 `npm run dev`
- M3 API Key：用户自己在 `application.yml` 填入，或通过环境变量 `MiniMax_API_KEY` 注入（**默认占位字符串，启动时校验**）

## 10. 测试约定

- 后端至少 1 个 Controller 集成测试（MockMvc）+ 1 个 M3Client 单测（Mock）
- 前端至少 1 个组件测试（可选，本科课题不强求）
- 所有可运行命令写到根 `README.md`
