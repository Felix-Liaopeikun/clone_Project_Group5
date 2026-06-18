# 部署与运行指南

> 本文件给出从零开始启动整个系统的全部命令。  
> 详细接口契约参见 `../docs/design/CONTRACT.md`；后端细节参见 `../backend/README.md`；前端细节参见 `../frontend/deliverable.md`。

---

## 1. 运行环境

| 组件       | 版本              | 用途                                | 验证命令 |
|------------|-------------------|-------------------------------------|----------|
| MySQL      | 8.0+（utf8mb4）   | 持久化文档、chunks、问答历史        | `mysql --version` |
| JDK        | 17                | 编译 / 运行 Spring Boot 后端        | `java -version` |
| Maven      | 3.9+              | 后端依赖管理与构建                  | `mvn -v` |
| Node.js    | 18+（建议 20 LTS）| 编译 / 运行 Vite 前端               | `node -v` |
| npm        | 9+                | 前端依赖管理                        | `npm -v` |
| MiniMax API Key | -            | 调用 MiniMax M3 大模型              | - |

> 端口占用：后端 `8080`、前端 `5173`、MySQL `3306`。  
> 操作系统：Windows 10/11、macOS、Linux 均可。本指南命令以 Windows PowerShell 为主，关键步骤另给 `cmd` 与 `bash` 对照。

---

## 2. 一次性配置：环境变量（API Key）

后端通过环境变量 `MINIMAX_API_KEY`（或同义 `MINIMAX_M3_API_KEY`）读取 Key，**不**写死在仓库内。

```powershell
# PowerShell（仅当前会话）
$env:MINIMAX_API_KEY = "sk-xxxxxxxxxxxxxxxx"
```

```cmd
:: cmd（仅当前会话）
set MINIMAX_API_KEY=sk-xxxxxxxxxxxxxxxx
```

```bash
# bash / zsh
export MINIMAX_API_KEY="sk-xxxxxxxxxxxxxxxx"
```

> 也可以直接编辑 `backend/src/main/resources/application.yml`，把 `minimax.api-key: REPLACE_WITH_YOUR_KEY` 替换为真实 Key。  
> 若 Key 仍是占位字符串，启动日志会打印 `WARN`，但不影响非 AI 相关功能（上传、列表、删除、分类聚合）。

---

## 3. 数据库初始化

### 3.1 启动 MySQL

确保 MySQL 8.0 服务已启动，root 密码已知（默认假设为 `root`）。

```powershell
# PowerShell —— 查看服务状态
Get-Service -Name MySQL80 | Select-Object Status, Name
```

### 3.2 执行初始化脚本

脚本位置：`docs/design/SCHEMA.sql`（与 `backend/src/main/resources/db/schema.sql` 字节级一致，已核对）。  
脚本是**幂等**的，可重复执行；不会清空已有数据。

#### PowerShell（推荐）

```powershell
# 假设 MySQL 客户端在 PATH；密码为 root
Get-Content ".\docs\design\SCHEMA.sql" | & mysql -u root -p
# 或（密码已写在命令行，仅本地测试用）
Get-Content ".\docs\design\SCHEMA.sql" | & mysql -u root -proot
```

#### cmd

```cmd
mysql -u root -p < docs\design\SCHEMA.sql
```

#### bash / Git Bash / WSL

```bash
mysql -u root -p < docs/design/SCHEMA.sql
```

### 3.3 验证

```powershell
& mysql -u root -p -e "USE doc_kb; SHOW TABLES; DESC document;"
```

预期输出包含三张表：`document`、`document_chunk`、`qa_history`。

### 3.4 插入种子数据（默认账号）

```powershell
# PowerShell
Get-Content ".\backend\src\main\resources\db\data-seed.sql" | & mysql -u root -p
```

```cmd
# cmd
mysql -u root -p < backend\src\main\resources\db\data-seed.sql
```

```bash
# bash
mysql -u root -p < backend/src/main/resources/db/data-seed.sql
```

**插入后账号：**

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | `Admin@123456` | ADMIN（管理员） |
| test  | `Test@123456`  | USER（普通用户） |

> ⚠️ 种子数据使用 `INSERT IGNORE`，重复执行不会覆盖。

---

## 4. 启动后端（Spring Boot）

```powershell
# 1. 进入后端目录
cd backend

# 2. （首次）下载依赖并编译
mvn -q -DskipTests package

# 3. 启动
mvn spring-boot:run
# 或直接跑 jar
# java -jar target/doc-summary-kb-backend.jar
```

启动成功标志（出现在控制台）：

```
Tomcat started on port 8080
Started DocKbApplication in X.XXX seconds
```

冒烟测试：

```powershell
# PowerShell
irm http://localhost:8080/api/health
# cmd / bash
# curl http://localhost:8080/api/health
```

预期返回：

```json
{"code":0,"message":"ok","data":{"status":"up","m3Reachable":true,"m3Model":"MiniMax-M3"}}
```

> 启动失败最常见原因：
> - **MySQL 连不上** → 检查 `application.yml` 的 `spring.datasource` 用户名/密码，以及 3306 端口是否放行。
> - **`minimax.api-key` 未替换且 M3 不可达** → 仍可启动；`/api/health` 会返回 `m3Reachable: false`，上传后摘要/分类会失败（错误信息会写入 `document.error_msg`）。

---

## 5. 启动前端（Vite + Vue 3）

```powershell
# 1. 进入前端目录
cd frontend

# 2. （首次）安装依赖（约 200 个包，1-3 分钟）
npm install

# 3. 启动开发服务器
npm run dev
```

启动成功后会显示：

```
  VITE v5.x  ready in xxx ms
  ➜  Local:   http://localhost:5173/
```

打开浏览器访问 [http://localhost:5173](http://localhost:5173)，即可看到系统主页。

> 前端通过 `vite.config.js` 的 `server.proxy` 把 `/api/**` 转发到 `http://localhost:8080`，**不要**直接访问后端 8080 当作前端。

---

## 6. 5 分钟跑起来（TL;DR）

```powershell
# 终端 1 —— 数据库
Get-Service MySQL80 | Start-Service
Get-Content .\docs\design\SCHEMA.sql | & mysql -u root -p

# 终端 2 —— 后端
$env:MINIMAX_API_KEY = "sk-xxx"
cd backend
mvn spring-boot:run

# 终端 3 —— 前端
cd frontend
npm install
npm run dev

# 浏览器 → http://localhost:5173
```

---

## 7. 常见问题（FAQ）

### 7.1 端口被占

- **8080 被占** → 编辑 `backend/src/main/resources/application.yml`，把 `server.port` 改成 8081 / 9090 等；并同步修改 `frontend/vite.config.js` 的 `server.proxy['/api'].target`。
- **5173 被占** → Vite 会自动顺延到 5174；终端会打印实际端口。也可在 `vite.config.js` 中显式指定 `server.port`。

定位占用进程：

```powershell
# PowerShell
Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object OwningProcess
Get-Process -Id <PID> | Select-Object ProcessName, Path
```

```cmd
netstat -ano | findstr :8080
tasklist /FI "PID eq <PID>"
```

### 7.2 MiniMax M3 不可达 / Key 没填（降级行为）

后端对所有 M3 调用做了**优雅降级**：

| 场景                              | 行为                                                                 |
|-----------------------------------|----------------------------------------------------------------------|
| `minimax.api-key` 是占位符         | 启动时打 WARN；上传后摘要/分类在异步任务里直接走降级分支               |
| Key 已填但 M3 API 超时/网络不通    | 摘要退化为**截取正文前 300 字**；分类退化为**"未分类"**；检索退化为**TF 词频打分**；问答退化为**拼接 top2 上下文** |
| `GET /api/health`                 | `code=0`、`status="up"`、`m3Reachable=false`（**不会**返回 5xx，前端不会红屏） |

只要 MySQL 在、Key 配错不会让系统崩。日志里会带 `WARN  M3 call failed, fallback to ...` 便于排查。

### 7.3 文件大小 / 类型限制

- **单文件 ≤ 20MB**（`spring.servlet.multipart.max-file-size`，对应 `application.yml`）。
- **整次请求 ≤ 25MB**（`max-request-size`）。
- **支持扩展名**：`pdf` / `txt` / `md` / `docx`。
- **超出 20MB** → 后端抛 `MaxUploadSizeExceededException` → 前端弹 `ElMessage.error("文件过大")`。
- **不支持的扩展名** → 后端 `BizException("UNSUPPORTED_FILE_TYPE")` → 前端 `code != 0`。
- **PDFBox 解析失败**（扫描件 / 加密 PDF）→ 异步任务里 `error_msg="PDF 解析失败：..."`，前端列表显示 `failed` 状态。

### 7.4 上传后状态一直是 `pending` / `processing`

- 后端有 `core=2 / max=4 / queue=50` 的线程池。默认足够；遇到大文件排队是正常现象。
- 详情接口 `/api/documents/{id}` 会返回最新 `status`、`errorMsg`；前端每 3 秒轮询一次，发现 `done|failed` 即停。
- 长时间卡住：查看后端日志是否有 `M3 call failed` / `OOM` / `IOException` 等。

### 7.5 中文文件名 / 路径乱码

- 全部链路已统一 `utf8mb4` + `StandardCharsets.UTF_8`。
- 上传时 Spring `MultipartFile` 默认按 UTF-8 解析；返回头 `Content-Disposition` 同时给出 `filename` 与 RFC 5987 `filename*=UTF-8''…`。
- Windows 下若仍乱码，请确认 `chcp 65001`（PowerShell 默认 UTF-8，cmd 默认 GBK）。

### 7.6 前端 `npm install` 失败 / 慢

- 国内网络建议设置镜像：
  ```powershell
  npm config set registry https://registry.npmmirror.com
  ```
- 报错 `EACCES` / `EPERM` → 关闭 IDE / 杀软对 `node_modules` 的扫描。
- 报错 `gyp ERR! find Python` → 当前项目**没有**原生依赖，可忽略；若执意排查，装 Python 3 + Visual Studio Build Tools。

### 7.7 跨域 / 联调

- 后端已在 `application.yml` 的 `app.cors.allowed-origins` 默认放行 `http://localhost:5173` 与 `http://127.0.0.1:5173`。
- 前端通过 Vite 代理转发 `/api`，浏览器看到的请求是同源 `http://localhost:5173/api/...`。
- 部署到不同域名时，记得把前端地址加进 `app.cors.allowed-origins`，并把 Vite 代理 target 改成后端真实地址。

### 7.8 清空数据重新开始

```sql
-- 警告：会删除全部文档与问答历史；本地调试用
USE doc_kb;
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE qa_history;
TRUNCATE document_chunk;
TRUNCATE document;
SET FOREIGN_KEY_CHECKS = 1;
```

物理文件 `uploads/` 目录不会被数据库级联清掉；如需彻底重置，手动删 `backend/uploads/` 即可。

---

## 8. 升级 / 重装

1. **更新后端 jar / 改 yml** → `mvn -q -DskipTests package` → `java -jar target/doc-summary-kb-backend.jar`。
2. **更新前端** → `npm run build` → 把 `frontend/dist/` 扔给 nginx：
   ```nginx
   location /api/ { proxy_pass http://localhost:8080; }
   location / {    root   .../dist; try_files $uri $uri/ /index.html; }
   ```
3. **数据库迁移** → 若表结构变更，写兼容的 `ALTER TABLE` 脚本进 `docs/design/migrations/`，不要直接改 `SCHEMA.sql`。

---

## 9. 相关文档

- 设计契约：`docs/design/CONTRACT.md`
- 数据库 DDL：`docs/design/SCHEMA.sql`
- 后端工程：`backend/README.md`
- 前端工程：`frontend/deliverable.md`
- 集成验证清单：`integration/verify-checklist.md`
- 测试输出：`integration/test-output.txt`
