# 测试覆盖汇总

> 生成时间：2026-06-18  
> 测试框架：JUnit 5 + Mockito + Spring MockMvc (WebMvcTest)

## 一、测试文件清单

### 后端（10 个文件，约 50+ 测试用例）

| # | 测试文件 | 类型 | 测试数 | 覆盖模块 |
|---|---------|------|--------|---------|
| 1 | `ResultTest.java` | 单元 | 5 | Result/ResultCode |
| 2 | `JwtUtilTest.java` | 单元 | 9 | JWT 签发/验证/边界 |
| 3 | `SnippetUtilTest.java` | 单元 | 4 | 摘要片段提取 |
| 4 | `TextChunkerTest.java` | 单元 | 4 | 文本分块 |
| 5 | `QaServiceImplTest.java` | 单元 | 3 | 问答服务核心逻辑 |
| 6 | `DocumentServiceImplTest.java` | 单元 | 5 | 文档服务核心逻辑 |
| 7 | `DocumentControllerIT.java` | 集成 | 6 | 文档管理 API |
| 8 | `QaControllerIT.java` | 集成 | 6 | 问答 API + 评测 API |
| 9 | `AuthControllerIT.java` | 集成 | 5 | 认证 API |
| 10 | `GlobalExceptionHandler` | 隐式 | - | 各集成测试覆盖 |

### 前端（3 个文件）

| # | 测试文件 | 类型 | 覆盖 |
|---|---------|------|------|
| 1 | `format.test.js` | 单元 | relativeTime 等工具函数 |
| 2 | `DocumentStatusTag.test.js` | 组件 | 状态标签渲染 |
| 3 | `qa.test.js` | 单元 | qa API 模块 |

---

## 二、功能覆盖矩阵

| 功能模块 | 单元测试 | 集成测试 | 正常路径 | 异常路径 | 边界 |
|---------|:---:|:---:|:---:|:---:|:---:|
| 用户注册 | - | ✅ | ✅ | ✅ (重复用户名/校验失败) | - |
| 用户登录 | - | ✅ | ✅ | ✅ (密码错误) | - |
| JWT 认证 | ✅ | - | ✅ | ✅ (无效token/不同密钥) | ✅ (null/blank/短密钥) |
| 文档上传 | ✅ | ✅ | ✅ | ✅ (空文件/不支持类型) | - |
| 文档列表/详情/删除 | ✅ | ✅ | ✅ | ✅ (404) | ✅ (分页) |
| 文档检索 | ✅ | ✅ | ✅ | ✅ (空结果) | - |
| 分类聚合 | - | ✅ | ✅ | - | - |
| 在线预览 | - | ✅ | ✅ | ✅ (404) | - |
| AI 问答 (同步) | ✅ | ✅ | ✅ | ✅ (空上下文/M3降级) | - |
| AI 问答 (流式) | - | ✅ | ✅ | ✅ | - |
| 问答历史 | ✅ | ✅ | ✅ | ✅ (401未登录) | ✅ (分页) |
| 回答评测 | - | ✅ | ✅ | ✅ (记录不存在) | - |
| 评测统计 | - | ✅ | ✅ | - | - |
| 健康检查 | - | ✅ | ✅ | - | - |
| 权限拦截 | - | - | ✅ | ✅ (401/403) | - |
| 模型切换 | - | - | ✅ | ✅ (模型不存在) | - |

---

## 三、运行测试

```powershell
# 后端全部测试
cd backend
mvn test

# 单测指定类
mvn test -Dtest=JwtUtilTest

# 集成测试
mvn test -Dtest=AuthControllerIT

# 跳过测试打包
mvn -DskipTests package
```

```bash
# 前端测试
cd frontend
npx vitest run
```

---

## 四、已知覆盖缺口

| 缺口 | 原因 | 优先级 |
|------|------|--------|
| M3Service 降级逻辑 | 需要真实 M3 调用或完整 Mock 链 | 低 |
| 文件上传集成测试 | MockMvc 不支持 multipart 流式 | 低 |
| 前端 E2E 测试 | 无 Cypress/Playwright 配置 | 中 |
| 权限拦截器独立测试 | 可追加 HandlerInterceptor 单元测试 | 低 |
| 并发/性能测试 | 非功能性需求 | 低 |
