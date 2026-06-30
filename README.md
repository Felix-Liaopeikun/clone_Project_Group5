# 文档摘要与知识库系统（doc-summary-kb）

> 基于 MiniMax M3 的文档自动摘要 + 知识库 + 智能问答系统。  
> 上传一份 pdf/docx/txt/md，3 秒内得到结构化摘要、分类、标签；可对所有已上传文档进行**关键词 + 语义重排检索**，并以**带原文引用**的方式向 M3 提问。

本科毕业设计课题：实现一个支持文件管理、自动摘要、来源引用、分类、检索、问答的端到端知识库系统。

<media src="/absolute/path/to/screenshot-home.png" caption="系统概览页（待补截图）" />

---

## 1. 功能清单（对应课题验收点）

| 课题验收点 | 系统能力 | 入口 / 端点 |
|---|---|---|
| **文件管理**   | 上传 pdf/docx/txt/md（≤20MB）、列表分页、详情、删除、在线预览 | `POST /api/documents/upload`、`GET /api/documents`、`GET /api/documents/{id}`、`DELETE /api/documents/{id}`、`GET /api/documents/{id}/file` |
| **摘要质量**   | 上传后异步调用 M3 生成 200~500 字结构化摘要；M3 不可达时降级为正文前 300 字 | `GET /api/documents/{id}` 中的 `summary` 字段；前端 `DocumentDetailView` |
| **来源引用**   | 问答响应中带 `citations` 数组，每条含 `documentId / title / chunkId / snippet / score`；前端可视化展示 | `POST /api/qa/ask` |
| **分类**       | 上传时 M3 自动从候选类别中分类；支持按分类聚合筛选 | `GET /api/documents/categories`、`GET /api/documents?category=...` |
| **检索**       | 关键词 LIKE 召回 → M3 语义重排（轻量）；返回 topK 命中片段 | `GET /api/documents/search?q=...&topK=5` |
| **问答**       | 检索 → 拼 prompt → M3 生成答案 + 解析引用 → 入库历史 | `POST /api/qa/ask`、`GET /api/qa/history` |

附加能力：分类分布图表（首页 ECharts）、健康检查徽标（M3 是否可达）、分页、模糊查询、流式输出（SSE）、多模型热切换、质量评测面板。

### 挑战层能力（优秀加分项）

| 挑战项 | 状态 | 说明 |
|---|---|---|
| **复杂安全控制** | ✅ 已完成 | 频率限制（令牌桶/IP+用户双维度）、XSS 过滤（HTML 转义）、CSRF 防护（HMAC 无状态 Token）、操作审计日志（AOP 切面）、文件魔数校验 |
| **多模型对比** | ✅ 已完成 | 多模型并行问答、并排展示、用户投票、胜率统计 |
| **智能体流程** | ✅ 已完成 | ReAct 风格 Agent：搜索/摘要/对比/上下文检索 4 大工具，多步推理可视化 |
| **性能优化** | ✅ 已完成 | Caffeine 多级缓存、MySQL FULLTEXT 全文索引、HikariCP 连接池调优、Gzip 压缩 |
| **创新交互** | ✅ 已完成 | 对话管理（列表/导出 Markdown）、知识图谱（ECharts 力导向图）、文档并排对比、完整上下文预览 |
| **可靠评测证据** | ✅ 已完成 | 30 条标准评测数据集、自动化评测运行器（Recall+AI Judge 4 维度）、评测报告导出

---

## 2. 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 后端 | Spring Boot + MyBatis-Plus + MySQL 8 | 3.2 / 3.5.5 / 8.0 |
| AI | MiniMax M3（OpenAI 兼容协议） | - |
| 前端 | Vue 3 + Vite + Element Plus + Pinia + Axios + ECharts | 3.4 / 5.x / 2.4 / 2.1 / 1.6 / 5.x |
| 文件解析 | PDFBox (pdf) + Apache POI (docx) + 纯文本 (txt/md) | 2.0.30 / 5.2.5 |
| 构建 | Maven（后端）+ Vite（前端） | 3.9+ / 5.x |

完整接口契约见 `docs/design/CONTRACT.md`。

---

## 3. 目录结构

```
doc-summary-kb/
├── README.md                           ← 本文件
├── AGENTS.md                           ← 前后端字段兼容性 / 工程备忘
├── .gitignore
├── backend/                            ← Spring Boot 后端
│   ├── pom.xml
│ │   ├── secrets.properties.example      ← API Key 配置模板
│   ├── deliverable.md
│   └── src/
│       ├── main/java/com/example/dockb/
│       │   ├── DocKbApplication.java
│       │   ├── common/                 # Result, ResultCode, BizException, PageResult, GlobalExceptionHandler
│       │   ├── config/                 # App/M3 Properties, CORS, MyBatis-Plus, Async, RestClient
│       │   ├── controller/             # DocumentController, QaController, HealthController
│       │   ├── service/ + impl/        # DocumentService(Impl), QaService(Impl), M3Service(Impl)
│       │   ├── mapper/                 # DocumentMapper, DocumentChunkMapper, QaHistoryMapper
│       │   ├── entity/                 # Document, DocumentChunk, QaHistory
│       │   ├── dto/                    # QaAskRequest
│       │   ├── vo/                     # DocumentVO / SearchResponseVO / QaAnswerVO / HealthVO …
│       │   ├── client/                 # M3Client / DefaultM3Client / M3Exception / dto/*
│       │   └── util/                   # TextExtractor, TextChunker, SnippetUtil
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/schema.sql           ← 拷贝自 docs/design/SCHEMA.sql
│       └── test/java/com/example/dockb/
│           └── DocumentControllerIT.java
├── frontend/                           ← Vue 3 前端
│   ├── package.json
│   ├── vite.config.js
│   ├── index.html
│   ├── README-equivalent in deliverable.md
│   ├── deliverable.md
│   ├── public/
│   ├── dist/                           ← npm run build 产物
│   └── src/
│       ├── main.js / App.vue
│       ├── router/index.js
│       ├── api/                        # request.js + document.js + qa.js + health.js
│       ├── components/                 # DocumentStatusTag, CitationItem, EChart
│       ├── layouts/MainLayout.vue
│       ├── views/                      # HomeView / DocumentListView / DocumentDetailView / QaView / NotFoundView
│       ├── styles/
│       └── utils/format.js
├── docs/
│   └── design/
│       ├── CONTRACT.md                 ← 前后端接口契约（必读）
│       └── SCHEMA.sql                  ← 数据库 DDL（与 backend 完全一致）
├── deploy/
│   └── (内容已并入根 README)
└── integration/
    ├── verify-checklist.md             ← 对照 CONTRACT §5 的逐端点验收清单
    └── test-output.txt                 ← 后端 mvn test 输出
```

---

## 4. 5 分钟跑起来

> 以下给出最短启动路径。完整 FAQ 见本文末尾。

```powershell
# 0. 准备：MySQL 8 / JDK 17 / Maven 3.9+ / Node 18+ / minimax API Key

# 1. 初始化数据库
Get-Content .\docs\design\SCHEMA.sql | & mysql -u root -p

# 2. 配置 API Key（二选一）
#    方式 A：复制 backend/secrets.properties.example → backend/secrets.properties，填入 Key
#    方式 B：设置环境变量 MINIMAX_API_KEY，或在 backend/secrets.properties 中填写
# 3. 启动后端
cd backend
mvn spring-boot:run

# 4. 另开一个终端 —— 启动前端
cd frontend
npm install
npm run dev

# 5. 浏览器打开
#    http://localhost:5173
```

> 若 `mvn` 不在环境里，可装 Maven 或用项目自带的 Maven Wrapper（如果后期加入）。  
> 若 `minimax` Key 暂时没拿到，也能跑起来 —— 上传后摘要/分类会进入"降级"分支，列表/检索/删除不受影响。

#### 默认账号（初始化数据库后可用）

```powershell
# 执行种子数据（位于 backend/src/main/resources/db/data-seed.sql）
Get-Content .\backend\src\main\resources\db\data-seed.sql | & mysql -u root -p

# 然后登录：
# admin / Admin@123456   → 管理员，可进入「用户管理」面板
# test  / Test@123456   → 普通用户
```

> ⚠️ 建议首次登录后立即修改密码（点击右上角用户名 → 修改密码）。

---

## 5. 截图占位（演示用）

> 实际截图待补；先用占位标签占位，等部署完成后替换。

- 系统概览：`<media src="/absolute/path/to/screenshot-home.png" caption="系统概览（文档数 / 分类分布 / 问答数）" />`
- 文档列表：`<media src="/absolute/path/to/screenshot-documents.png" caption="文档管理（上传 / 检索 / 分类筛选）" />`
- 文档详情：`<media src="/absolute/path/to/screenshot-document-detail.png" caption="文档详情（摘要 / 分类 / Tags / 分块）" />`
- 智能问答：`<media src="/absolute/path/to/screenshot-qa.png" caption="智能问答（带引用）" />`

---

## 6. API 速览

Base URL：`http://localhost:8080/api`，统一响应 `{ code, message, data }`，时间 `yyyy-MM-dd HH:mm:ss`。

| Method | Path | 用途 | 契约章节 |
|---|---|---|---|
| POST   | `/api/documents/upload`         | 上传（multipart，≤20MB） | §5.1 |
| GET    | `/api/documents`                | 列表（page/size/keyword/category） | §5.1 |
| GET    | `/api/documents/{id}`           | 详情 | §5.1 |
| DELETE | `/api/documents/{id}`           | 删除（级联清 chunks + 文件） | §5.1 |
| GET    | `/api/documents/{id}/file`      | 在线预览（Content-Disposition: inline） | §5.1 |
| GET    | `/api/documents/categories`     | 分类聚合 | §5.2 |
| GET    | `/api/documents/search`         | 检索（q + topK） | §5.3 |
| POST   | `/api/qa/ask`                   | 问答（带 citations） | §5.4 |
| GET    | `/api/qa/history`               | 问答历史（分页） | §5.4 |
| POST   | `/api/qa/compare`               | 多模型并行对比 | §5.6 |
| POST   | `/api/qa/agent`                 | 智能体问答 | §5.7 |
| GET    | `/api/documents/knowledge-graph` | 知识图谱 | §5.8 |
| GET    | `/api/health`                   | 健康检查（status / m3Reachable / m3Model） | §5.5 |
| POST   | `/api/evaluation/run`           | 运行自动评测 | §5.9 |

---

## 7. 测试与验收

- **后端集成测试**：`backend/src/test/java/com/example/dockb/DocumentControllerIT.java`（MockMvc，Mock 掉 M3Client / DocumentService / DocumentMapper，覆盖 `/api/health`、`/api/documents`、`/api/documents/search` + M3Client mock 自检）。
- **运行测试**：
  ```powershell
  cd backend
  mvn -q test
  ```
  完整输出已贴在 `integration/test-output.txt`。
- **逐端点验收清单**：`integration/verify-checklist.md`（对照 `docs/design/CONTRACT.md` §5 的 10 个端点）。
- **前端构建**：`cd frontend && npm run build`（已验证 7.83s 通过，dist 完整）。

---

## 8. 已知限制

1. **MiniMax M3 调用限制**：受 API 配额与网络影响；本系统已做降级（所有 AI 功能均有回退策略）。
2. **检索重排为轻量实现**：先 LIKE/FULLTEXT 召回前 30 条，再让 M3 从候选中挑 topK 并给 0~1 分；不构建向量库。
3. **PDF 仅解析文本层**：扫描件 / 加密 PDF 会失败，错误信息写入 `document.error_msg`。
4. **单进程异步任务**：上传后的摘要/分类/标签走进程内线程池（core=2, max=4, queue=50）；多副本部署需要外置队列（Redis Stream / Kafka）。
5. **没有文件去重**：相同 md5 也会重复上传（保留历史更直观）。
6. **前端未做 SSR / SEO**：纯 SPA，对演示无影响。

---

## 9. 文档导航

- 设计契约：[`docs/design/CONTRACT.md`](docs/design/CONTRACT.md)
- 数据库 DDL：[`docs/design/SCHEMA.sql`](docs/design/SCHEMA.sql)
- 后端工程：[`backend/deliverable.md`](backend/deliverable.md)
- 前端工程：[`frontend/deliverable.md`](frontend/deliverable.md)
- API Key 配置模板：[`backend/secrets.properties.example`](backend/secrets.properties.example)
- 工程备忘：[`AGENTS.md`](AGENTS.md)
- 验收清单：[`integration/verify-checklist.md`](integration/verify-checklist.md)
- 测试输出：[`integration/test-output.txt`](integration/test-output.txt)

---

## 10. 后端设计要点

### 10.1 异步处理

上传后立即返回 `id`，摘要/分类/标签通过 `@Async("docKbExecutor")` 异步执行。状态机：`pending → processing → done | failed`。详情接口实时返回最新 `status` 与 `errorMsg`。线程池 core=2, max=4, queue=50。

### 10.2 重排降级

检索先 LIKE 召回候选项，再调用 M3 语义重排。M3 不可达时退化为 TF 词频打分；问答降级时直接拼接 top2 上下文作为答案。

### 10.3 M3 协议

使用 OpenAI 兼容 `/chat/completions` 协议，超时 10s（连接）/ 60s（读取），失败 1 次重试。支持按请求切换模型（MiniMax M3 / DeepSeek 等），流式输出由 SSE 承载。

### 10.4 文件解析

- PDF：PDFBox，仅解析文本层（扫描件 / 加密 PDF 会失败，错误写入 `error_msg`）
- DOCX：Apache POI
- TXT / MD：纯文本读取

### 10.5 错误响应

所有异常统一翻译为 `{ code, message, data }`，HTTP 200/400/500，前端按 `code == 0` 判定成败。

### 10.6 CORS

默认放行 `http://localhost:5173` 与 `http://127.0.0.1:5173`；可通过 `application.yml` 的 `app.cors.allowed-origins` 追加。

---

## 11. 常见问题（FAQ）

### 11.1 端口被占

- **8080**：修改 `application.yml` 的 `server.port`，同步修改 `frontend/vite.config.js` 的代理目标
- **5173**：Vite 自动顺延到 5174

### 11.2 MiniMax M3 不可达 / Key 没填（降级行为）

| 场景 | 行为 |
|---|---|
| `mini-max.api-key` 未配置 | 启动打印 WARN；异步任务走降级分支 |
| Key 已填但 API 超时/不通 | 摘要退化为正文前 300 字；分类退化为"未分类"；检索退化为 TF 词频；问答退化为拼接 top2 上下文 |
| `GET /api/health` | `code=0`、`status="up"`、`m3Reachable=false`，不会返回 5xx |

### 11.3 文件大小 / 类型限制

- 单文件 ≤ 20MB，请求 ≤ 25MB
- 支持 `pdf / txt / md / docx`
- 超出限制：后端抛异常 → 前端 ElMessage 提示
- PDFBox 解析失败 → `status=failed`，`error_msg` 记录原因

### 11.4 上传后状态卡在 `pending` / `processing`

- 大文件排队属正常现象，前端每 3 秒轮询
- 长时间卡住：查看后端日志是否有 `M3 call failed` / `OOM` / `IOException`

### 11.5 中文文件名乱码

- 全链路已统一 UTF-8 + utf8mb4
- Windows cmd 确认 `chcp 65001`

### 11.6 前端 npm 安装失败 / 慢

```powershell
npm config set registry https://registry.npmmirror.com
```

### 11.7 跨域联调

前端通过 Vite 代理转发 `/api`，浏览器看到同源 `localhost:5173/api/...`。部署到不同域名时，更新 `app.cors.allowed-origins` 和 Vite 代理 target。

### 11.8 清空数据重新开始

```sql
USE doc_summary;
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE qa_history;
TRUNCATE document_chunk;
TRUNCATE document;
SET FOREIGN_KEY_CHECKS = 1;
```
物理文件 `uploads/` 需手动删除。

---

## 12. 升级与重装

1. **更新后端**：`mvn -q -DskipTests package` → 替换 jar，重启
2. **更新前端**：`npm run build` → dist 部署到 nginx：
   ```nginx
   location /api/ { proxy_pass http://localhost:8080; }
   location / { root .../dist; try_files $uri $uri/ /index.html; }
   ```
3. **数据库迁移**：表结构变更写兼容的 `ALTER TABLE` 脚本到 `docs/design/migrations/`，不直接改 `SCHEMA.sql`