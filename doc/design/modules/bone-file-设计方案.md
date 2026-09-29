# 文件服务（bone-file）详细设计方案 v1

> **文档状态**：v1 从零产出（2026-09-29 夜间 A 阶段）。本模块在 `doc/design/modules/` 下**此前无任何设计稿**（锚定 `design_doc: null`），本稿为第一版。
> **对齐**：工程实现与门禁以 `doc/architecture/Bone-DDD-最终实践方案.md` 为准（其 §12 / §G-1.7 为 L4，本文档不复制门禁状态）；HC 编号语义一律引用 `doc/design/_global-contracts.yaml` 的 `hc_semantics`。
> **仓库对照（As-Is）**：唯一文件服务 `bone-platform/bone-file`，端口 **8107**，`PlatformApiPaths.FILE_V1 = "/api/v1/file"`。
> **HTTP 真源（当前）**：`FileController.java`（4 个端点）；**尚无** OpenAPI spec（`doc/architecture/openapi/` 下无 `file-v1.yaml`）。
> **DDL 真源**：`bone-init.sql`——当前 `file_` 前缀表 **0 张**（对象元数据完全寄存在 MinIO）。
> **错误码前缀**：`FILE_`（**尚未登记**，见 §5.6）。
> **配套报告**：`doc/design/modules/review-report-bone-file.md`。

---

## 0. 可观测性契约（SLI/SLO）

| SLI | 目标（**[Target]**） | As-Is |
|-----|---------------------|-------|
| 上传成功率 | ≥ 99.9% / 日 | 无埋点（仅 `log.info` 一行） |
| 上传 P95 延迟（10MB） | < 3s | 无埋点 |
| 下载首字节延迟 P95 | < 500ms | 无埋点 |
| 存储可用率 | ≥ 99.9% | `GET /files/test` 手工探测（返回 `boolean`，无告警联动） |
| 单租户容量 | 配额上限内（**[Target]**） | **无配额** |

> **As-Is 判定依据**：模块 `src/main/java` 共 5 个类，`grep -rn "MeterRegistry\|Timer\|Counter\|@Observed" src/main/java` → **0 命中**；`application.yml:34` 仅暴露 `health,info`，无 `metrics`（与本轮已复核的其它模块一致的最小暴露面）。

---

## 1. 模块概述

### 1.0 As-Is（实测，见报告附录 A）

| 维度 | 当前仓库 |
|------|----------|
| 服务 | `bone-platform/bone-file`（`:8107`） |
| 代码规模 | `src/main/java` **5 个类**（`FileApplication` / `FileController` / `FileStoragePort` / `MinioFileStorageService` / `SecurityConfig` + `JwtAuthenticationFilter`）；`src/test/java` 仅 `ArchitectureTest` |
| 分层现状 | **无 `domain` 包**（`src/main/java/com/bone/file/` 下只有 `adapter` / `application` / `infrastructure`）；`@EnableSqlRepositories(basePackages = "com.bone.file.domain.repository")` 指向**不存在的包** |
| 持久化 | **零表**（`bone-init.sql` 无 `file_` 表）；已声明 `bone-metadata-sdk` 依赖但无任何实体/Repository 使用 |
| 存储后端 | MinIO（S3 兼容），`io.minio:minio:8.5.7` |
| 对外 URL | `http://host:8107/api/v1/file/**`，经网关 `/api/v1/file/** → ${BONE_FILE_URI}`（`bone-gateway/src/main/resources/application.yml:48-51`） |
| 鉴权 | JWT（`AbstractJwtAuthenticationFilter` 子类）+ `anyRequest().authenticated()`；**方法级 `@PreAuthorize` 0 处真实使用** |
| 前端 | **无 `bone-file-app` 微应用，无文件管理页面**（`bone-frontend/apps/` 下 8 个应用不含 file） |

### 1.1 定位与边界（本设计的核心裁决点）

当前仓库里有 **三处** 对象存储直连，本模块必须先把边界划清，否则「文件服务」名不副实：

| # | 位置 | 用途 | 归属裁决 |
|---|------|------|----------|
| 1 | `bone-file/.../MinioFileStorageService` | 通用文件上传/下载/删除 | **本模块**（唯一对外出口） |
| 2 | `bone-iam/.../MinioStorageClientImpl`（`bone.iam.minio.*`） | 审计日志归档（`bucket-name: audit-logs`） | **IAM 内部基础设施**，属「模块自用归档」，不对外；但**应复用本模块的客户端工厂**，避免第二份 `MinioClient.builder()` |
| 3 | `bone-integration/.../S3ClientImpl` | 集成连接器协议客户端（S3 协议作为**被集成对象**） | **bone-integration**，与本模块无关（它连的是外部 S3，不是平台自己的桶） |

> **裁决（写入本稿）**：第 2 项维持 IAM 自有实现，但**不纳入本模块 API 面**；第 3 项不在本模块边界内。本模块只负责「平台自身文件资产」的生命周期。

### 1.2 核心目标

- 提供统一的文件上传 / 下载 / 删除能力，对业务模块屏蔽 MinIO / S3 / OSS 差异；
- 让文件**可管理**（当前只有「能传能取」，没有「知道传了什么」）；
- 让文件**可隔离**（当前对象键无租户维度、删除无归属校验）；
- 让文件**可计量**（当前无大小/类型/配额约束，仅靠 Servlet multipart 上限）。

---

## 2. 功能设计

### 2.1 文件上传（P0）

**功能描述**：接收 multipart 文件，写入对象存储，返回平台侧对象标识。

**详细功能**：
- 单文件上传（`POST /api/v1/file/files`，`multipart/form-data`）；
- 可选 `bucket` 参数（缺省回落 `bone.file.minio.bucket`，默认 `platform-files`）；
- 服务端生成对象键，**不信任客户端提供的文件名作为键**（见 §4.2）；
- 返回 `ApiResponse<FileObjectResp>`（当前返回 `ApiResponse<String>` 仅 objectName，**信息不足**）。

**用户故事**：
> 作为业务用户，我希望上传附件后拿到的返回值里能直接带上文件名、大小、内容类型和可访问标识，以便我在列表里展示它，而不必再自己解析 objectName。

### 2.2 文件下载与预览（P0）

**功能描述**：按对象标识取回流；图片 / PDF 走内联预览，其余走附件下载。

**详细功能**：
- `GET /api/v1/file/files/{objectName}` 下载（`Content-Disposition: attachment`）；
- 内联预览（**[Target]**：`GET /api/v1/file/files/{objectName}?inline=true`，按 `contentType` 判定，仅白名单类型可内联，防 XSS）；
- 预签名 URL（**[Target]**：`POST /api/v1/file/files/{objectName}:presign`，返回有时效的直连 URL，减轻本服务带宽压力）。

**当前缺口**：`FileController#download` 直接把 `objectName` 拼进 `Content-Disposition`，未做白名单/转义；且**不校验调用者是否有权访问该对象**（见 §7）。

### 2.3 文件删除与生命周期（P1）

**功能描述**：安全删除 + 可回收。

**详细功能**：
- `DELETE /api/v1/file/files/{objectName}` 删除；
- 软删除 + 回收站（**[Target]**：`deleted` 标记 + N 天后物理清理定时任务）；
- 孤儿对象清理（**[Target]**：元数据表存在但对象存储中缺失，或反之，每日对账）。

**当前缺口**：删除是**物理删除且无归属校验**——任何通过网关认证的用户只要猜到 `objectName` 就能删除任意对象。

### 2.4 文件元数据与检索（P1 · [Target]）

**功能描述**：把「传了什么」变成可查询的数据。

**详细功能**：
- 上传落库 `file_object`（见 §4.1），记录 `tenant_id` / `bucket` / `object_name` / `original_name` / `content_type` / `size` / `uploader_id` / 审计列；
- 分页列表 `GET /api/v1/file/files?page=1&size=20&keyword=...` 返回 `PageResult<FileObjectResp>`（**与当前 `GET /{objectName}` 路径不冲突**：列表走无路径变量的 `GET`，单对象走 `GET /{objectName}`）；
- 按业务归属过滤（**[Target]**：`bizType` + `bizId` 软关联，支持「某单据的附件」查询）。

> **为什么必须落库**：不落库就无法回答「这个租户用了多少容量」「这个对象是誰传的」「哪些对象已成孤儿」。纯对象存储形态只适合 demo，不适合多租户平台。

### 2.5 配额与容量（P2 · [Target]）

- 单文件大小上限（当前仅 Servlet `max-file-size: 50MB` / `max-request-size: 60MB`，**未按租户/类型细分**）；
- 租户容量配额（**[Target]**：`quota_bytes`，超配额返回 `FILE_QUOTA_EXCEEDED` 409）；
- 扩展名白名单 + MIME 嗅探（**[Target]**：默认禁止可执行文件`.exe/.sh/.bat`，防存储型攻击）。

### 2.6 权限与租户隔离（P0）

| 能力 | 目标 | As-Is |
|------|------|-------|
| 认证 | JWT（网关 + 模块双重） | ✅ 已具备 |
| 功能权限 | `@PreAuthorize("hasAuthority('file:upload')")` 等 | ❌ **0 处**（`SecurityConfig.java:29` javadoc 声称「授权由方法级 @PreAuthorize 完成」，实测仅注释） |
| 行级归属 | 下载/删除校验 `object.tenantId == 当前租户` | ❌ 无租户字段、无校验 |
| 租户注入 | 经 `TenantProvider` 端口（E-2 口径） | ❌ `grep -rn "TenantContext\|TenantProvider" src/main/java` → **0 命中** |

---

## 3. UI 设计

> 当前 **无任何文件管理页面**。本节为 v1 设计，含页面结构、交互与状态覆盖。
> 微前端契约（token 注入 / locale 订阅 / lifecycle / 独立运行 fallback）沿用 `bone-frontend/apps/*` 既有约定；路由前缀建议 `/file`（history 路由），子应用内 hash（`/file#/objects`）。

### 3.1 页面清单

| # | 页面 | 路由 | 权限码 | 优先级 |
|---|------|------|--------|--------|
| U1 | 文件管理列表 | `/file` → `/file#/objects` | `file:list` | P0 |
| U2 | 上传弹窗（拖拽 + 进度） | 列表页内 Modal | `file:upload` | P0 |
| U3 | 文件详情抽屉 | 列表页内 Drawer | `file:detail` | P1 |
| U4 | 回收站 | `/file#/trash` | `file:trash` | P2（[Target]） |
| U5 | 附件选择器（业务复用组件） | 组件 `<FilePicker />` | `file:list` | P1 |

### 3.2 文件管理页（U1）

```
┌──────────────────────────────────────────────────────────────┐
│ 文件管理                              [搜索] [上传] [刷新]    │
├──────────────────────────────────────────────────────────────┤
│ 文件名      │ 类型      │ 大小   │ 上传者 │ 上传时间 │ 操作  │
│ report.xlsx │ xlsx      │ 1.2MB  │ 张三   │ 09-29 06:10│ 下载 删除 │
│ logo.png    │ image/png │ 84KB   │ 李四   │ 09-29 05:02│ 预览 删除 │
├──────────────────────────────────────────────────────────────┤
│                           共 2 条           ‹ 1 ›             │
└──────────────────────────────────────────────────────────────┘
```

- 列表字段：`originalName` / `contentType` / `size`（前端格式化） / `uploaderId→用户名` / `createdAt` / 操作；
- 排序默认 `createdAt DESC`；
- 分页对齐 `Bone-API §2.5`：`page` / `size` 从 1 起，返回 `PageResult<T>`；
- **ID 精度**：`id` / `uploaderId` 一律 `string`，**禁止 `Number()`**（对齐 F4 口径）。

### 3.3 上传交互（U2）

- antd `Upload.Dragger`，支持多选与拖拽；
- 上传中显示**逐文件进度条**（当前后端无进度回传，前端用 `onProgress` 的 xhr 进度即可）；
- 成功后刷新列表并 `message.success(文件大小 + 文件名)`；
- 失败按 `errorCode` 显示 i18n 文案（见 §3.5），**不直接展示后端 message**。

### 3.4 状态覆盖（F3）

| 状态 | 表现 |
|------|------|
| 空态 | `Empty`：「暂无文件，点击右上角上传」 |
| 加载态 | `Table loading` + 骨架屏 |
| 错误态 | `Result` + `errorCode` 文案 + 重试按钮 |
| 无权限态 | `Result 403`：「你没有文件管理权限」（权限码缺失时） |
| 配额超限（[Target]） | 上传前置灰 + Tooltip：「租户容量已用满」 |

### 3.5 i18n 键（`file.*`，需与 `scripts/check-i18n-sync.py` 对称）

```
file.title / file.upload / file.download / file.delete / file.preview
file.confirmDelete / file.deleteSuccess / file.uploadSuccess
file.columns.name / file.columns.type / file.columns.size
file.columns.uploader / file.columns.createdAt
errors.FILE_UPLOAD_FAILED / errors.FILE_DOWNLOAD_FAILED / errors.FILE_DELETE_FAILED
errors.FILE_OBJECT_NOT_FOUND / errors.FILE_FORBIDDEN / errors.FILE_QUOTA_EXCEEDED
```

---

## 4. 数据模型

### 4.0 现状：零表（这是本模块最大的结构性缺口）

`grep -n "file_" bone-init.sql` → 命中仅为其它表的**字段**（`exts_artifact.file_path:692`、`file_size:693`、`gen_code_generation_history.file_count:915`），**无 `file_` 前缀表**。
后果：文件「传上去就失联」——无归属、无容量、无审计、无法回收。

### 4.1 `file_object` 表设计（**L3 提案，本任务不执行，待架构师审批**）

对齐 `HC-008`（新增表必须含 `tenant_id` + `created_at` + `updated_at` + `deleted`）与 `数据库开发规范`：

```sql
CREATE TABLE file_object (
  id            BIGINT       NOT NULL COMMENT '主键（分布式 ID）',
  tenant_id     BIGINT       NOT NULL COMMENT '租户 ID',
  bucket_name   VARCHAR(128) NOT NULL COMMENT '对象存储桶',
  object_name   VARCHAR(512) NOT NULL COMMENT '对象键（服务端生成）',
  original_name VARCHAR(512) NOT NULL COMMENT '客户端原始文件名（仅展示，不参与寻址）',
  content_type  VARCHAR(128) NOT NULL COMMENT 'MIME 类型',
  size_bytes    BIGINT       NOT NULL COMMENT '字节大小',
  biz_type      VARCHAR(64)  DEFAULT NULL COMMENT '业务归属类型（软关联）',
  biz_id        VARCHAR(64)  DEFAULT NULL COMMENT '业务归属标识（软关联）',
  created_by    BIGINT       DEFAULT NULL COMMENT '上传人',
  updated_by    BIGINT       DEFAULT NULL COMMENT '更新人',
  created_at    DATETIME     NOT NULL COMMENT '创建时间',
  updated_at    DATETIME     NOT NULL COMMENT '更新时间',
  deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_object_key (bucket_name, object_name),
  KEY idx_file_object_tenant (tenant_id, deleted, created_at),
  KEY idx_file_object_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件对象元数据';
```

> **`version` 列的必要性**：删除/改名走 `@Version` 乐观锁（对齐 ADR-0031），避免并发删除导致的孤儿对象。
> **DDL 落地属 L3**：本任务只产出变更稿，不改 `bone-init.sql`、不落库。

### 4.2 对象键命名规范（P0，可先于 DDL 落地）

当前键：`UUID.randomUUID() + "-" + file.getOriginalFilename()`（`FileController.java:32`）。
**问题**：① 无租户维度 → 无法按租户做生命周期/容量治理；② 原始文件名直接拼接 → 含路径分隔符或超长名时异常；③ 键全局随机 → 无法按前缀批量列举。

**目标键格式**：

```
{tenantId}/{yyyy}/{MM}/{dd}/{uuid}.{ext}
```

- `tenantId` 前置：支持 MinIO 前缀列举与按租户清理；
- `ext` 取自原始文件名并**过白名单**，拒绝空/超长（>16）/含非 `[A-Za-z0-9]` 的扩展名；
- 原始文件名**只入库展示**，不参与寻址；
- 对外暴露的标识用**库表主键 `id`**（`StudioIds`-like 外发字符串），`objectName` 不外泄，从根上消除「猜名字删对象」。

---

## 5. API 设计

### 5.0 横切约定

- 前缀 `PlatformApiPaths.FILE_V1 + "/files"` = `/api/v1/file/files`；
- 写操作返回 `ApiResponse<T>`（**HC-003**）；列表返回 `PageResult<T>`；
- 下载为**裸流**（`application/octet-stream`），属 HC-003 的合法例外（同 `bone-iam` 导出、`bone-extension-studio`）；
- 错误码统一 `FILE_` 前缀，`errorCode` 独立字段，不拼进 message（对齐 §5.6）；
- 业务 API 必须经网关 8888，直连 8107 会因无 JWT 上下文返回 401 空体。

### 5.1 上传

```
POST /api/v1/file/files
Content-Type: multipart/form-data
file: <binary>            (必填)
bucket: string            (可选，缺省 platform-files)
bizType / bizId: string   (可选，[Target])

200 ApiResponse<FileObjectResp>
{ "success": true, "code": 200, "data": {
    "id": "184...", "originalName": "report.xlsx",
    "contentType": "application/vnd...", "size": 1258291,
    "createdAt": "2026-09-29T06:10:00" } }
```

### 5.2 下载 / 预览

```
GET  /api/v1/file/files/{id}                    → 200 裸流（attachment）
GET  /api/v1/file/files/{id}?inline=true        → 200 裸流（inline，仅内容类型白名单）[Target]
POST /api/v1/file/files/{id}:presign            → 200 ApiResponse<PresignResp>  [Target]
```

### 5.3 删除

```
DELETE /api/v1/file/files/{id}  → 200 ApiResponse<Void>
```
（[Target] 改为软删：元数据 `deleted=1`，对象延迟清理，支持回收站恢复。）

### 5.4 元数据列表

```
GET /api/v1/file/files?page=1&size=20&keyword=&bizType=&bizId=
→ 200 ApiResponse<PageResult<FileObjectResp>>
```

### 5.5 连接测试（保留）

```
GET /api/v1/file/files/test → 200 ApiResponse<Boolean>
```
> 建议**收敛为 Actuator `HealthIndicator`**（当前是业务端点暴露运维信息）；保留 `/test` 仅为兼容现存调用方，标 `Deprecated`。

### 5.6 错误码表（`FILE_`，需在 `Bone-错误码登记.md` §6 新建 `FILE_` 段）

| 错误码 | HTTP | 语义 | 触发点 |
|--------|------|------|--------|
| `FILE_UPLOAD_FAILED` | 500 | 写入对象存储失败 | `MinioFileStorageService:64` |
| `FILE_DOWNLOAD_FAILED` | 500 | 读取对象流失败 | `MinioFileStorageService:75` |
| `FILE_DELETE_FAILED` | 500 | 删除对象失败 | `MinioFileStorageService:86` |
| `FILE_OBJECT_NOT_FOUND` | 404 | 对象不存在 | 新增（当前下载失败统一 500） |
| `FILE_FORBIDDEN` | 403 | 跨租户/越权访问 | 新增（当前无校验） |
| `FILE_TYPE_NOT_ALLOWED` | 400 | 扩展名/MIME 不在白名单 | 新增 |
| `FILE_SIZE_EXCEEDED` | 413 | 超过单文件上限 | 新增 |
| `FILE_QUOTA_EXCEEDED` | 409 | 租户容量超限 | 新增（[Target]） |

> **现状**：5 处 `throw new IllegalStateException("文件上传失败: " + ...)`（`FileController:38,55`；`MinioFileStorageService:64,75,86`）为**裸中文 message**，无 errorCode、无 i18n、HTTP 状态由全局兜底为 500——与本轮 `bone-integration` / `bone-system` / `bone-extension-studio` 的同一类缺陷同构。

---

## 6. 技术实现

### 6.1 分层结构（目标态）

```
com.bone.file
├── adapter/web/controller/FileController          # 仅做协议转换
├── application/
│   ├── UploadFileApplicationService               # 语义化入站（ADR-0028）
│   ├── DownloadFileApplicationService
│   ├── DeleteFileApplicationService
│   └── query/FileObjectListQueryApplicationService
├── domain/
│   ├── model/FileObject                           # 聚合根，含 tenantId + version
│   ├── repository/FileObjectRepository            # 走 metadata-sdk
│   └── gateway/TenantProvider                     # E-2 端口
└── infrastructure/
    ├── gateway/TenantProviderGatewayAdapter       # 唯一读 TenantContext 处
    └── storage/MinioFileStorageService            # 实现 domain 出站端口
```

> **注意**：当前 `application/port/out/FileStoragePort` 放在 `application` 层——出料端口按仓库通则应落在 `domain/gateway`（其它模块均为 `domain/gateway/*`，如 `CatalogMetadataGateway` / `TenantProvider`）。此项为**建议级**重构（L2），与本轮其它模块口径对齐。

### 6.2 出站端口

沿用现有 `FileStoragePort`（`upload` / `download` / `delete` / `exists` / `testConnection`），补 `presign`（[Target]）。实现类 `MinioFileStorageService` 保持 MinIO 单实现，**新增实现（OSS/COS）时以 `@ConditionalOnProperty` 切换**，保持端口唯一。

### 6.3 配置项

| 键 | 默认 | 说明 |
|----|------|------|
| `bone.file.minio.endpoint` | `http://localhost:9000` | MinIO 地址 |
| `bone.file.minio.access-key` | `minioadmin` | 访问键（**生产必须注入，禁硬编码** · HC-004） |
| `bone.file.minio.secret-key` | `minioadmin` | 密钥（同上） |
| `bone.file.minio.bucket` | `platform-files` | 默认桶 |
| `bone.file.max-file-size` | 50MB | 单文件上限（新增，替代纯 Servlet 配置） |
| `bone.file.allowed-extensions` | 白名单 | 扩展名白名单（新增） |
| `bone.file.quota-bytes-per-tenant` | — | 租户配额（[Target]） |

> `scripts/dev/restart-mvp-services.sh:49` 已为 `bone-file` 单独注入 `BONE_IAM_JWT_SECRET_KEY`（该模块无默认密钥，未注入即启不来），沿用该口径。

### 6.4 部署与集成

- 端口 8107；网关路由 `/api/v1/file/**`；启动顺序 `bone-gateway → bone-file`（无上游依赖）；
- `spring.servlet.multipart.max-file-size=50MB` 已配（`application.yml:13-15`）；**反向代理（Nginx / 网关）需同步放开 body 上限**，否则大文件在网关侧被截断；
- Actuator 暴露 `health,info`；生产如需 `metrics`，须经 JWT 鉴权（对齐 `bone-iam` B-5 口径）。

---

## 7. 性能与安全

| # | 风险 | 现状 | 处置 |
|---|------|------|------|
| S1 | **越权删除/下载**：`objectName` 由客户端提供且无归属校验 | 存在 | 改为按库表 `id` 寻址 + 租户/上传人校验（P0） |
| S2 | **路径遍历**：`objectName` 未校验即可含 `../` | 存在 | 服务端生成键，客户端输入只作展示（P0） |
| S3 | **Content-Disposition 注入**：文件名直接拼进响应头 | 存在（`FileController:52`） | 转义或改用 `filename*=UTF-8''`（P0） |
| S4 | **无类型/MIME 白名单**，可上传任意文件 | 存在 | 扩展名 + 嗅探双校验（P1） |
| S5 | **内联预览 XSS**：SVG / HTML 内联渲染执行脚本 | [Target] 引入后才有 | 内联仅放行 `image/png,jpeg,gif,webp` + `application/pdf`，且响应加 `X-Content-Type-Options: nosniff`（P1） |
| S6 | **CORS 8 个 localhost 源 + `allowCredentials(true)`** | 硬编码于 `SecurityConfig.java:73-90` | 改用配置项，生产环境按域名白名单注入（P1） |
| S7 | **无租户维度**：对象键与访问均无 `tenant_id` | 存在 | 键前缀 + `TenantProvider` 端口（P0） |
| S8 | **无权限码**：`anyRequest().authenticated()` 后无任何功能权限 | 存在 | `@PreAuthorize`（P0） |
| S9 | 大文件直传占用服务带宽 | — | 预签名直传/直下（P2 [Target]） |
| S10 | 密钥默认值 `minioadmin` | 存在（**仅默认值，非硬编码字面量密钥**） | 生产必须注入；HC-004 判定以 `scripts/scan-secrets.sh` 为准 |

---

## 8. 测试计划

| 层 | 用例 | 当前 |
|----|------|------|
| 单元 | `FileObject` 聚合校验（空名 / 超限 / 非法扩展名） | 无（无聚合） |
| 单元 | 对象键生成器（租户前缀 + 日期分片 + 扩展名白名单） | 无 |
| 单元 | `TenantProviderGatewayAdapter` 空上下文返回 `null` | 无 |
| 集成 | 上传 → 列表 → 下载 → 删除 全链路（MockMvc + MinIO Testcontainers 或 stub） | 仅 `ArchitectureTest` 8 条 |
| 契约 | `file-v1.yaml` OpenAPI（**新建**）+ 与 Controller 一致性校验 | 无 spec |
| 安全 | 跨租户下载/删除预期 403；`../` objectName 预期 400 | 无 |
| 门禁 | `./scripts/check.sh` + `python3 scripts/check-i18n-sync.py` | — |

---

## 9. 监控与告警

- **HealthIndicator**：当前只有 Spring Boot 内置健康项；**应新增** `FileStorageHealthIndicator`（`bucketExists` 探测），失败即 `DOWN`（对齐 `bone-iam` B-5 修复口径，当前 `/files/test` 是业务端点不是健康探针）；
- **指标（[Target]）**：`bone_file_upload_total{tenant,result}`、`bone_file_upload_bytes`、`bone_file_download_total`、`bone_file_storage_errors_total`；
- **日志**：关键路径须带 `tenantId` / `traceId`（MDC）；当前 `log.info("创建数据源...")` 级别的日志无租户维度。

---

## 10. 总结

`bone-file` 目前是一个**「能跑通的对象存储代理」**，而不是一个**可治理的文件服务**：它能上传、下载、删除，但不知道传了什么、属于谁、占了多少、能不能被别人删掉。

v1 设计给出的收口路径是三条主线：

1. **身份化**：对象键服务端生成（租户前缀 + 日期分片），对外只暴露库表 `id`；
2. **可治理**：新增 `file_object` 元数据表（L3），支撑列表、容量、审计、回收站；
3. **可防护**：`TenantProvider` 端口收口租户 + `@PreAuthorize` 功能权限 + 类型/大小白名单 + 响应头转义。

按 `02-nightly-design-review.md` §1.7，`design_doc == null` 的模块**本晚只产出设计稿 v1，不进实现**；状态停在 `design_ready`，等人工先看稿。

---

## 附录 A. As-Is 证据（2026-09-29 实测）

| # | 结论 | 命令 | 结果 |
|---|------|------|------|
| A1 | 无 domain 包 | `ls src/main/java/com/bone/file/` | `FileApplication.java` / `adapter` / `application` / `infrastructure`（无 `domain`） |
| A2 | Repository 扫描包不存在 | `FileApplication.java:17` | `@EnableSqlRepositories(basePackages = "com.bone.file.domain.repository")` |
| A3 | 零租户读取 | `grep -rn "TenantContext\|TenantProvider" src/main/java \| wc -l` | `0` |
| A4 | 零功能权限 | `grep -rn "PreAuthorize" src/main/java` | 仅 `SecurityConfig.java:29` javadoc（0 处注解） |
| A5 | 裸中文异常 5 处 | `grep -rn "IllegalStateException" src/main/java` | `FileController:38,55`；`MinioFileStorageService:64,75,86` |
| A6 | 无 `FILE_` 错误码 | `grep -rn "FILE_" src/main/java` | 仅 `FileController:21` 的 `PlatformApiPaths.FILE_V1` |
| A7 | 无 `file_` 表 | `grep -n "file_" bone-init.sql` | 仅 `exts_artifact.file_path:692` / `file_size:693` / `gen_code_generation_history.file_count:915`（均为字段） |
| A8 | 无 OpenAPI spec | `ls doc/architecture/openapi/` | `console/extension/generator/iam/integration/masterdata/metadata-runtime/blueprint-orders` —— **无 file** |
| A9 | 无前端应用 | `ls bone-frontend/apps/` | `bone-extension-app / bone-generator-app / bone-iam-app / bone-integration-app / bone-masterdata-app / bone-metadata-app / bone-shell / bone-system-app` —— **无 file** |
| A10 | 网关路由 | `bone-gateway/src/main/resources/application.yml:48-51` | `id: bone-file`，`uri: ${BONE_FILE_URI:http://localhost:8107}`，`Path=/api/v1/file/**` |
| A11 | 对象键生成 | `FileController.java:32` | `UUID.randomUUID() + "-" + file.getOriginalFilename()` |
| A12 | 响应头拼接 | `FileController.java:52` | `"attachment; filename=\"" + objectName + "\""` |
| A13 | 多租户模块同构缺陷对照 | 本轮其它模块 | `bone-extension-studio` X-1（租户不注入）、`studio-generator` G-1（魔法默认 0L/1L）——本模块为**同族的第三形态**：无租户维度 |
