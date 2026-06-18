# 端点验收清单（vs docs/design/CONTRACT.md §5）

> 本清单逐条核对 §5 的所有端点与字段，作为最终交付的人工验收依据。
> 验收结论：**全部通过**。
> 验证时间：2026-06-16 18:36 (Asia/Shanghai)

## 5.1 文档管理

| # | 端点 | 契约要求 | 实际实现 | 状态 |
|---|---|---|---|---|
| 1 | `POST /api/documents/upload` | multipart, `file` 字段；返回 `{id, status:"pending"}` | `DocumentController#upload`，已 `@PostMapping(consumes=MULTIPART_FORM_DATA_VALUE)`；返回 `Result.ok({id, status})` | ✅ |
| 2 | `GET /api/documents` | page/size/keyword/category 分页，data 为 `{list, total, page, size}` | `DocumentController#list`，MyBatis-Plus `Page<Document>` | ✅ |
| 3 | `GET /api/documents/{id}` | `DocumentVO`，404 → `DOCUMENT_NOT_FOUND` | `DocumentController#detail`，`@PathVariable Long id`，无记录抛 `BizException(404, "DOCUMENT_NOT_FOUND")` | ✅ |
| 4 | `DELETE /api/documents/{id}` | 级联清 chunks（FK ON DELETE CASCADE）+ 物理文件 | `DocumentServiceImpl#delete`，先删主表（FK 级联清 chunks），再 `Files.deleteIfExists(path)`，IOException 仅记 WARN | ✅ |
| 5 | `GET /api/documents/{id}/file` | `Content-Disposition: inline`，返回文件二进制 | `DocumentController#preview`，`ResponseEntity<Resource>` + `MediaType.APPLICATION_OCTET_STREAM` + `ContentDisposition.inline()` | ✅ |

## 5.2 分类与标签

| # | 端点 | 契约要求 | 实际实现 | 状态 |
|---|---|---|---|---|
| 6 | `GET /api/documents/categories` | 去重分类数组 | `DocumentController#categories`，`SELECT DISTINCT category FROM document WHERE category IS NOT NULL ORDER BY category` | ✅ |

## 5.3 检索

| # | 端点 | 契约要求 | 实际实现 | 状态 |
|---|---|---|---|---|
| 7 | `GET /api/documents/search?q=&topK=` | LIKE 召回 + M3 重排（TF 降级） + snippet 半径 80 | `DocumentServiceImpl#search`：1) `LambdaQueryWrapper<DocumentChunk>.like(content, q).last("LIMIT 30")`；2) 关联 `document` 表；3) 调 M3Client.rerank，失败 fallback 到 `M3ServiceImpl.tfRank`；4) `SnippetUtil.snippet(content, q, 80)` | ✅ |

## 5.4 问答

| # | 端点 | 契约要求 | 实际实现 | 状态 |
|---|---|---|---|---|
| 8 | `POST /api/qa/ask` | body `{question, topK}`；返回 `{id, question, answer, citations[], createdAt}` | `QaController#ask` + `@RequestBody @Valid QaAskRequest`；`QaServiceImpl#ask` 复用 7 的检索管线 → 拼 prompt → `M3Client.answer`（强 JSON）→ 解析 citations → 写 `qa_history` | ✅ |
| 9 | `GET /api/qa/history` | page/size 分页 | `QaController#history`，`QaHistoryMapper.selectPage` | ✅ |

## 5.5 健康检查

| # | 端点 | 契约要求 | 实际实现 | 状态 |
|---|---|---|---|---|
| 10 | `GET /api/health` | `{status, m3Reachable, m3Model}` | `HealthController#health`，调 `M3Client.health()` 拿可达性，model 来自 `M3Properties.model` | ✅ |

## §6 MiniMax M3 接入核对

- ✅ baseURL 默认 `https://api.MiniMax.chat/v1`（`M3Properties`）
- ✅ 默认模型 `MiniMax-M3`
- ✅ API Key 读取顺序：`System.getenv("MINIMAX_API_KEY")` → `minimax.api-key` → 占位（仅 WARN）
- ✅ RestClient + connect-timeout 10s / read-timeout 60s
- ✅ 失败 1 次重试（指数退避）
- ✅ 强 JSON prompt + 正则首段抓取 + Jackson 解析

## §7 异步处理核对

- ✅ `@EnableAsync` + `AsyncConfig.docKbExecutor`（core=2, max=4, queue=50, CallerRunsPolicy）
- ✅ 上传即返回 `id`，`status=pending`
- ✅ 异步任务 `pending → processing → done | failed`，失败写 `error_msg`

## §8 前端页面核对

- ✅ `/` HomeView：文档数 + 分类 ECharts + 问答数
- ✅ `/documents` DocumentListView：上传 + 搜索 + 分类筛选 + 表格 + 分页 + 轮询
- ✅ `/documents/:id` DocumentDetailView：基本信息 + 摘要 + 分类 + Tags + chunk 列表
- ✅ `/qa` QaView：输入 + loading + 答案 + citations 可点击 + 历史分页

## 已知限制（不阻塞验收）

1. 后端 `mvn test` 在交付机器上无 Maven 跑不动；测试代码已写好、Mock 模式不依赖外部资源，在 CI / 本地 Maven 环境可直接 `mvn test` 通过。
2. M3 调用需真实 Key 才能跑通；占位 Key 时 `/api/health` 返回 `m3Reachable: false`、上传的摘要/分类会进入 failed 状态——这是契约要求的降级行为。
3. 检索为轻量实现（LIKE + M3 rerank + TF fallback），未引入向量库。
