# doc-summary-kb — Project Agent Notes

> Track A (后端) / Track B (前端) 共用的工程级备忘。
> 详细的接口契约见 `docs/design/CONTRACT.md`，这里只记录实现过程中踩过的坑与前后端字段一致性备忘。

## 1. 前后端字段兼容性备忘（前端 owner）

### 1.1 `qa_history.citations`

后端 schema 声明 `citations JSON NULL`，但 MyBatis / 驱动返回时**可能是 JSON 字符串**而不是数组。
前端 `QaView.vue::loadHistory` 必须做兼容：

```js
if (typeof it.citations === 'string') {
  try { it.citations = JSON.parse(it.citations) } catch { it.citations = [] }
}
if (!Array.isArray(it.citations)) it.citations = []
```

> 若后端后续确认总是返回数组，可删掉此兼容。

### 1.2 `document.chunks` 字段

CONTRACT 5.1 `GET /api/documents/{id}` 只声明返回 `DocumentVO`，未明确是否包含 chunks。
前端 `DocumentDetailView.vue` 已做兼容：

```js
chunks.value = data.chunks || data.chunkList || []
```

后端若把 chunks 放到独立接口（如 `/api/documents/{id}/chunks`），请同步在前端 `DocumentDetailView.fetchDoc` 里追加调用，并把接口加进 `docs/design/CONTRACT.md` 章节 5。

### 1.3 健康检查

`GET /api/health` 应返回 `{ status, m3Reachable, m3Model }`。前端 `MainLayout` 用 `status === 'up'` 判定徽标颜色。
若后端在 M3 不可达时仍返回 code 0 + status=down，前端**不会**红屏（符合预期）。

## 2. 文件预览

CONTRACT 5.1 提供 `GET /api/documents/{id}/file`（Content-Disposition: inline）但前端本期未在 UI 上做入口。
如需，预留一行：

```vue
<a :href="`/api/documents/${doc.id}/file`" target="_blank">预览原文件</a>
```

## 3. 构建/启动

- 前端：`cd frontend && npm install && npm run dev`（端口 5173，已代理 `/api` → `http://localhost:8080`）
- 后端：Spring Boot 8080；CORS 已放行 5173
- 联调顺序：MySQL → 后端 → 前端

## 4. 前端交付档案

完整目录树、与 CONTRACT 章节 4/5/8/9 的逐条对照、关键交互细节在：

- `frontend/deliverable.md`
- `C:\Users\xzqwy\.mavis\plans\plan_7476347f\outputs\frontend-impl\deliverable.md`