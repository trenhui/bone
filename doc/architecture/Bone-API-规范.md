# Bone 平台 API 规范

> **文档性质**：对外 **HTTP REST** 契约（URL、信封、分页、横切头、OpenAPI、契约测试）。  
> **更新**：2026-05-17  
> **关联**：[BONE-总体架构设计方案](./BONE-总体架构设计方案.md) §8、[Bone-DDD-最终实践方案](./Bone-DDD-最终实践方案.md)  
> **配套规范**：[README.md](./README.md) 工程规范索引；错误码 / 日志 / 安全 / 可观测性等见同目录 `Bone-*.md`

### 文档地图

| 章节 | 内容 |
|------|------|
| §2–§3 | URL、HTTP、成功响应与分页 |
| **§4** | 失败信封、`ProblemDetail`（业务码见独立文档） |
| §5–§9 | 分页、头、LRO、幂等、权限 |
| §10 | 日志与可观测性（摘要 → 独立文档） |
| §11–§13 | OpenAPI、Controller、模块路径与迁移 |
| **§14** | API 附属约定（时间/枚举/i18n 等） |
| **§15** | **契约测试**（OpenAPI / Mock / Pact / CI） |
| §16 | 平台规范体系索引（另立文档建议） |
| §17 | 实施检查清单 |

---

## 1. 目标与适用范围

| 项 | 说明 |
|----|------|
| **目标** | 统一 URL、HTTP 语义、响应/错误信封、分页与横切头；可 i18n、可 CI 校验。错误码与日志见配套文档。 |
| **适用** | `bone-platform/*`、`bone-engine/*` 对外 HTTP API；`bone-frontend` 各微应用；网关与 SA-Token 鉴权配置。 |
| **真源优先级** | 本规范 > 模块详设 API 章 > 代码实现；偏离须 ADR 或 §13.2「迁移登记」。 |
| **OpenAPI** | 每服务维护 OpenAPI 3.1；公共 schema 见 §11；CI 做破坏性 diff。 |
| **实现符合度** | 本规范为**目标态**；`bone-core` 双 `ApiResponse`、`code=0` 成功码等见 §13.2，逐步迁移。 |

### 1.1 参考标准（业界）

| 标准 / 指南 | Bone 采纳方式 |
|-------------|----------------|
| [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110) HTTP 语义 | 状态码、201+Location、204 DELETE |
| [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) Problem Details | 失败时 `data` 结构对齐 |
| [RFC 8594](https://www.rfc-editor.org/rfc/rfc8594) Sunset | 废弃 API 必须带 Sunset / Deprecation |
| [RFC 8288](https://www.rfc-editor.org/rfc/rfc8288) Link | 分页 `rel="next\|prev"` |
| Google API Design Guide | `:action` 自定义方法、LRO 长任务 |
| Microsoft REST API Guidelines | 版本内仅 additive、错误模型、命名 |
| OpenAPI 3.1 | 契约真源与 `$ref` 公共组件 |

### 1.2 本规范**不强制**的做法（说明即可）

| 做法 | 原因 |
|------|------|
| JSON:API (`data`/`attributes`/`relationships`) | 与现有 `ApiResponse` 割裂大，内部管理台收益有限 |
| 全资源 HATEOAS | 前端 React 以契约/OpenAPI 为主，链接发现非刚需 |
| 取消统一信封、裸返回资源 | 与存量前端/异常处理器一致，保留 `ApiResponse` |

---

## 2. URL 设计

### 2.1 路径模板（强制）

```
/api/v1/{domain}/{resource-collection}
/api/v1/{domain}/{resource-collection}/{id}
/api/v1/{domain}/{resource-collection}/{id}/{sub-resource}
/api/v1/{domain}/{resource-collection}/{id}:{action}
/api/v1/{domain}/operations/{operationId}          # 长任务（LRO）轮询
```

| 段 | 规则 | 示例 |
|----|------|------|
| `{domain}` | 小写单段，见 §2.3 | `extension`、`iam` |
| `{resource-collection}` | **复数名词**、kebab-case | `execution-logs` |
| `{id}` | `Long` 或 UUID，OpenAPI 标明 | `123` |
| `{action}` | kebab-case 动词 | `deploy`、`test-connection` |

### 2.2 版本策略

| 规则 | 说明 |
|------|------|
| 新接口 | **必须** `/api/v1/...` |
| v1 内变更 | **仅 additive**：可加可选字段；禁止删除/改类型/改必填 |
| 破坏性变更 | 升 `v2`；v1 保留 ≥1 个 minor 并标 Sunset |
| 无版本别名 | **已废止** `/api/{domain}`（无 `v1`）直出；仅 `/api/v1/{domain}/...`（见 §13.2） |

### 2.3 域（domain）注册表

| domain | 服务 | 错误码前缀（[台账 §3.1](./Bone-错误码登记.md#31-字符串业务码新接口强制)） | 说明 |
|--------|------|-------------------|------|
| `iam` | bone-iam | `IAM_` | 认证、用户、角色、权限 |
| `metadata` | 元数据（catalog + 扩展字段 EAV，统一 `/api/v1/metadata/**`） | `META_` | 与 [元数据能力对照](../design/modules/元数据能力-实现映射与竞品对照.md) 一致 |
| `runtime` | bone-metadata-server（模式 B 动态 CRUD） | `META_RUNTIME_` | OpenAPI：[metadata-runtime-v1.yaml](./openapi/metadata-runtime-v1.yaml) |
| `generator` | studio-generator | `GEN_` | 代码生成（**非** metadata-server 职责） |
| `masterdata` | bone-masterdata | `MD_` | 主数据、质量 |
| `extension` | bone-extension-studio | `EXT_` | 扩展点、插件 |
| `integration` | bone-integration | `INT_` | 连接器、流程 |
| `system` | bone-system | `SYS_` | 配置、告警 |
| `console` | 聚合读接口 | — | **只读** BFF，禁止写模型 |

### 2.4 HTTP 方法与状态码

| 方法 | 用途 | 成功状态 | 响应 |
|------|------|----------|------|
| GET | 查询 | 200 | `ApiResponse<T>` / `PageResult` |
| POST | 创建 | **201** + **`Location`** | `ApiResponse<{ id }>` 或资源 |
| POST | 动作 / 上传 | 200 或 **202**（异步） | 见 §7 |
| PUT | 全量更新 | 200 | `ApiResponse<T>` |
| PATCH | 部分更新 | 200 | 同上 |
| DELETE | 删除 | **204 无 body**（推荐）或 200 + `ApiResponse<Void>` | 团队**统一一种** |

| HTTP | 场景 | `ApiResponse.code` |
|------|------|-------------------|
| 400 | 参数错误 | 400 |
| 401 | 未登录 | 401 |
| 403 | 无权限 | 403 |
| 404 | 资源不存在 | 404 |
| 409 | 冲突 / 乐观锁 / 幂等冲突 | 409 |
| 422 | 语义校验失败（可选） | 422 |
| 429 | 限流 | 429 |
| 500 | 未预期错误 | 500 |

**禁止**（迁移期外）：HTTP 2xx 且 `success: false`。

### 2.5 查询参数

| 参数 | 规则 |
|------|------|
| `page` | 从 **1** 开始，默认 1 |
| `size` | 默认 20，**上限 100** |
| `sort` | `field,asc\|desc`，**白名单** |
| `filter.{name}` | 过滤，如 `filter.status=ENABLED`，字段白名单 |
| `keyword` | 模糊搜索（可选） |
| `cursor` + `limit` | 游标分页；**日志/审计/执行记录类接口强制**（§5.3） |

JSON Query Body：**禁止** `POST` 模拟 `GET` 列表（导出等超大查询走专用 `POST ...:export` 且异步）。

---

## 3. 统一响应信封（成功）

### 3.1 标准类型

使用 `com.bone.core.model.ApiResponse`（`bone-core`），由 `GlobalExceptionHandler` 统一失败翻译。

```json
{
  "success": true,
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": "2026-05-17T09:40:13.101Z"
}
```

| 字段 | 规则 |
|------|------|
| `success` | 与 HTTP 2xx 一致，必须为 `true` |
| `code` | **等于 HTTP 状态码**（成功固定 `200`；历史 `0` 见 §12 废弃） |
| `message` | 简短中文/英文，可展示给用户 |
| `data` | 业务载荷；无数据可为 `null` |
| `timestamp` | ISO-8601 UTC |

**单一真相**：判断成功与否以 **HTTP 状态** 为准，`success`/`code` 与之对齐。

### 3.2 创建响应

```http
HTTP/1.1 201 Created
Location: /api/v1/extension/points/711030195653443584
Content-Type: application/json

{"success":true,"code":201,"message":"创建成功","data":{"id":"711030195653443584"},"timestamp":"..."}
```

### 3.3 分页 `PageResult`

```json
{
  "records": [],
  "total": 128,
  "page": 1,
  "size": 20,
  "pages": 7,
  "hasNext": true,
  "hasPrevious": false,
  "nextCursor": null
}
```

| 字段 | 说明 |
|------|------|
| `records` | 当前页（禁止 `list`/`items`） |
| `nextCursor` | 游标分页时非空；offset 分页为 `null` |

**响应头（推荐）**：

```http
Link: </api/v1/extension/execution-logs?cursor=abc&limit=20>; rel="next"
X-Total-Count: 128
```

### 3.4 前端解析

```typescript
export type BoneApiResponse<T> = {
  success: boolean;
  code: number;
  message: string;
  data: T;
  timestamp?: string;
};

export function isOk(res: BoneApiResponse<unknown>, httpStatus: number): boolean {
  return httpStatus >= 200 && httpStatus < 300 && res.success !== false;
}
```

> 不再推荐依赖 `code === 0`；兼容层设 **2026-09-01** 移除（见 §12）。

---

## 4. 错误模型

> **业务错误码**（命名、号段、台账、PR 流程）：真源见 **[Bone-错误码登记.md](./Bone-错误码登记.md)**。  
> 本节仅定义 HTTP 层信封与 `ProblemDetail` 结构。

### 4.1 设计原则（摘要）

| 原则 | 说明 |
|------|------|
| HTTP 表达类别 | 4xx/5xx 表示客户端/服务端/网关问题 |
| 业务码表达细节 | 稳定字符串 `errorCode`，见 [错误码登记](./Bone-错误码登记.md) |
| 不把两类码塞进一个整数 | `ApiResponse.code` 仅镜像 HTTP；业务码放 `data.errorCode` |

### 4.2 失败响应结构（对齐 RFC 7807）

失败时 HTTP 状态为 4xx/5xx，`success: false`，`data` 为 **ProblemDetail**：

```json
{
  "success": false,
  "code": 404,
  "message": "插件不存在",
  "data": {
    "type": "https://bone.wps.cn/problems/extension/plugin-not-found",
    "title": "Plugin Not Found",
    "status": 404,
    "detail": "id=711030195741523968 的插件不存在或已删除",
    "instance": "/api/v1/extension/plugins/711030195741523968",
    "errorCode": "EXT_PLUGIN_NOT_FOUND",
    "traceId": "a1b2c3d4e5f6",
    "errors": [
      { "field": "name", "message": "不能为空", "rejectedValue": null }
    ]
  },
  "timestamp": "2026-05-17T09:40:13.101Z"
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `type` | 是 | 问题类型 URI（可浏览文档） |
| `title` | 是 | 简短英文标题 |
| `status` | 是 | 重复 HTTP 状态，便于日志 |
| `detail` | 是 | 开发者可读细节（**禁止**堆栈） |
| `instance` | 推荐 | 请求路径 |
| `errorCode` | 是 | **稳定业务码**，`{DOMAIN}_{REASON}` |
| `traceId` | 是 | 与 `X-Request-Id` / MDC 一致 |
| `errors` | 校验失败时 | 字段级错误数组 |

对外 Content-Type 可为 `application/json`（信封）或 `application/problem+json`（仅 `data` 部分语义）；实现阶段保持信封即可。

### 4.3 实现与登记（见独立文档）

| 主题 | 文档 |
|------|------|
| 命名、号段、台账、PR 流程、Java 枚举 | [Bone-错误码登记.md](./Bone-错误码登记.md) |
| OpenAPI `x-errorCodes` | 各服务 `openapi.yaml` + §11 |
| `GlobalExceptionHandler` | `bone-web` / `bone-core`，组装 §4.2 |

---

## 5. 分页策略

| 类型 | 适用 | 参数 | 备注 |
|------|------|------|------|
| **Offset** | 管理列表（实体、角色） | `page`, `size` | `total` 可缓存；深页警告 `page>100` |
| **Cursor** | 日志、审计、执行记录、大数据导出 | `cursor`, `limit` | 稳定、抗翻页漂移 |

**强制 cursor 的 resource**：`execution-logs`、`audit/logs`、`integration/executions`、`system/logs`。

---

## 6. 横切头

### 6.1 请求头

| 头 | 必填 | 说明 |
|----|------|------|
| `Authorization` | 除匿名接口外 | `Bearer {jwt}` |
| `Content-Type` | 有 body 时 | `application/json; charset=utf-8` |
| `Accept` | 推荐 | `application/json` |
| `X-Request-Id` | 推荐 | 客户端生成 UUID；无则网关生成 |
| `X-Tenant-Id` | 多租户 | 网关注入；**不得**仅靠 body 覆盖 |
| `X-Biz-Identity-Code` | 多身份 | 业务身份 |
| `Idempotency-Key` | 幂等写 | UUID；服务端 TTL **24h** |
| `If-Match` | 乐观锁更新 | 实体 `version` 或 ETag |

### 6.2 响应头

| 头 | 说明 |
|----|------|
| `X-Request-Id` | **回显**请求 ID，与 `data.traceId` 一致 |
| `Location` | 201 创建 |
| `Deprecation` / `Sunset` | 废弃接口 |
| `Retry-After` | 429 秒数 |
| `RateLimit-Limit` / `RateLimit-Remaining` / `RateLimit-Reset` | 限流（推荐） |
| `Link` | 分页 next/prev |

---

## 7. 长任务（LRO）

部署插件、代码生成等耗时操作：

```
POST /api/v1/extension/plugins/{id}:deploy
→ 202 Accepted
   Location: /api/v1/extension/operations/op-abc123

GET /api/v1/extension/operations/{operationId}
→ { "done": false, "progress": 40, "result": null, "error": null }

→ { "done": true, "progress": 100, "result": { ... }, "error": null }
```

| 字段 | 说明 |
|------|------|
| `done` | 是否结束 |
| `progress` | 0–100，可选 |
| `error` | 失败时 ProblemDetail |
| `result` | 成功载荷 |

同步接口仅在确认 **P99 < 3s** 时使用 200。

---

## 8. 幂等性

| 项 | 约定 |
|----|------|
| 适用 | `POST` 创建、`:deploy`、支付类写操作 |
| 键 | `Idempotency-Key` 请求头，UUID v4 |
| 存储 | 租户 + 用户 + 键 + 路径 → 响应快照，TTL **24h** |
| 重复请求 | 相同 body 返回 **相同响应**（200/201） |
| 键相同 body 不同 | **409** + `COMMON_IDEMPOTENCY_CONFLICT` |

---

## 9. 安全与权限

### 9.1 认证与租户

1. 默认需认证；白名单：`/api/v1/iam/login`、健康检查、文档（生产需鉴权）。  
2. `tenant_id` / `biz_identity_code` 从 JWT 解析写入 `TenantContext`。  
3. 禁止在日志中输出完整 Token、密码、密钥。

### 9.2 权限 Scope（OpenAPI + IAM）

```
{domain}:{resource}:{action}
```

| Scope | 说明 |
|-------|------|
| `extension:points:read` | 读扩展点 |
| `extension:points:write` | 写扩展点 |
| `extension:plugins:deploy` | 部署插件 |
| `iam:users:write` | 管理用户 |

Controller 使用 `@PreAuthorize` 或 SA-Token 注解，**权限码与 Scope 一致**。

---

## 10. 日志与可观测性（摘要）

HTTP 层与日志的**交叉约定**（须在实现中保持一致）：

| 项 | 约定 |
|----|------|
| `X-Request-Id` | 请求头推荐；响应头回显；写入 MDC `traceId` |
| `ProblemDetail.traceId` | 与 MDC / 响应头 **同一值** |
| Access Log | 每条 HTTP 一条 `[API]` INFO；`status>=400` 带 `errorCode` |
| 审计 | 关键写操作落库（见独立文档 §6） |

**完整规范**（分类、MDC、级别、脱敏、Logback、告警、分层）：**[Bone-日志规范.md](./Bone-日志规范.md)**。

---

## 11. OpenAPI 与公共组件

### 11.1 目录约定

```
{module}/src/main/resources/openapi/
  openapi.yaml          # 服务入口
  paths/
  components/
    schemas/
      ApiResponse.yaml
      PageResult.yaml
      ProblemDetail.yaml
```

### 11.2 公共 Schema（规划路径 `doc/architecture/openapi/components/`）

- `ApiResponse` / `PageResult` / `ProblemDetail` / `FieldError`  
- 各服务 `$ref` 引用，**禁止** 每服务复制字段名不一致的副本。

### 11.3 CI 规则

- 禁止删除字段、禁止改类型  
- 新增必填字段 → 视为 breaking，升版本  
- PR 必须附 OpenAPI diff 或 SpringDoc 导出变更说明  

---

## 12. Controller 实现约定

```java
@RestController
@RequestMapping("/api/v1/extension/points")
@RequiredArgsConstructor
public class ExtensionPointController {

    @GetMapping
    public ApiResponse<PageResult<ExtPointResp>> list(@Valid ExtPointPageQry qry) {
        return ApiResponse.success(listHandler.execute(qry));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> create(@Valid @RequestBody CreateExtPointReq req) {
        Long id = createHandler.execute(ExtPointAssembler.toCmd(req));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(id));
    }
}
```

| 规则 | 说明 |
|------|------|
| 包路径 | `adapter.web.controller` |
| 入参 | `*Req` / `*Qry`；`@Valid` |
| 出参 | `*Resp`；不直接返回领域实体 |
| 事务 | 仅在 `application.command.handler` |
| 文档 | `@Operation` + `@Tag`（SpringDoc） |
| 异常 | 抛 `BizException`，**禁止** Controller 内 `catch` 后 `error("xxx")` 丢失 errorCode |
| 列表 | `ApiResponse<PageResult<Resp>>` |
| 错误文档 | `@ApiResponse(responseCode)` 标注典型 `errorCode` |

---

## 13. 模块契约与迁移登记

### 13.1 扩展（extension）规范路径

前缀 `/api/v1/extension`；详设见 [5. 扩展管理模块](../design/modules/5.%20扩展管理模块详细设计方案.md) §5。  
OpenAPI 草案：[openapi/extension-v1.yaml](./openapi/extension-v1.yaml)（本地校验：`bash scripts/ci/validate-extension-openapi.sh`）。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/extension/points` | 扩展点列表 |
| POST | `/api/v1/extension/points` | 创建扩展点 |
| PUT | `/api/v1/extension/points/{id}` | 更新 |
| DELETE | `/api/v1/extension/points/{id}` | 删除 |
| POST | `/api/v1/extension/points/{id}:enable` | 启用 |
| POST | `/api/v1/extension/points/{id}:disable` | 禁用 |
| GET | `/api/v1/extension/plugins` | 插件列表 |
| GET | `/api/v1/extension/plugins/{id}` | 详情 |
| POST | `/api/v1/extension/plugins` | 创建 |
| PUT | `/api/v1/extension/plugins/{id}` | 更新 |
| DELETE | `/api/v1/extension/plugins/{id}` | 删除 |
| POST | `/api/v1/extension/plugins:upload` | 上传 JAR |
| GET | `/api/v1/extension/plugins/{id}/versions` | 版本列表 |
| POST | `/api/v1/extension/plugins/{id}:deploy` | 部署 |
| POST | `/api/v1/extension/plugins/{id}:undeploy` | 卸载 |
| POST | `/api/v1/extension/plugins/{id}:rollback` | 回滚 |
| POST | `/api/v1/extension/plugins/{id}:bind` | 绑定扩展点 |
| POST | `/api/v1/extension/plugins/{id}:unbind` | 解绑 |
| POST | `/api/v1/extension/plugins/{id}:simulate` | 沙箱模拟 |
| GET | `/api/v1/extension/overview` | 概览 |
| GET | `/api/v1/extension/execution-logs` | 执行日志（cursor） |
| POST | `/api/v1/extension/execution-logs:ingest` | SDK 上报 |
| GET | `/api/v1/extension/sandbox/config` | 沙箱配置 |
| GET | `/api/v1/extension/audit-logs` | Studio 审计（cursor；写操作见 §10.5） |
| POST | `/api/v1/extension/plugins/{id}:publish-runtime` | 发布运行时元数据 |

| GET | `/api/v1/extension/operations/{id}` | LRO 轮询 |

### 13.1.1 代码生成（generator）规范路径

前缀 `/api/v1/generator`；实现类见 `GeneratorApiPaths`（`studio-generator`）。**旧路径（`/api/data-sources`、`/api/code-generator` 等）已移除**。

| 方法 | 路径（规范） | 说明 |
|------|--------------|------|
| GET/POST | `/api/v1/generator/data-sources` | 数据源 CRUD |
| POST | `/api/v1/generator/data-sources/{id}/test` | 连接测试（过渡；目标 `{id}:test-connection`） |
| GET | `/api/v1/generator/data-sources/{id}/tables` | 物理库表发现（JDBC） |
| POST | `/api/v1/generator/data-sources/{id}/tables:sync` | 同步表结构到 `gen_*` |
| GET | `/api/v1/generator/metadata-entity-snapshots` | 已发布 `meta_*` 快照分页（`CATALOG_SNAPSHOT` 选型） |
| GET/POST | `/api/v1/generator/code-templates` | 模板（As-Is；目标 `/templates`） |
| POST | `/api/v1/generator/code-generation` | Freemarker 异步生成（As-Is） |
| POST | `/api/v1/generator/generation-tasks` | 同步字符串模板生成 |
| GET | `/api/v1/generator/capabilities` | AI 能力发现 |

### 13.2 废弃与迁移

| 状态 | 旧 | 新 | 截止 |
|------|----|----|------|
| 已移除 | `/api/ext-points`、`/api/extensions`、`/api/extension/*` | `/api/v1/extension/*` | — |
| 已移除 | `/api/data-sources` | `/api/v1/generator/data-sources` | — |
| 已移除 | `/api/table-metadata` | `/api/v1/generator/table-metadata` | — |
| 已移除 | `/api/code-templates` | `/api/v1/generator/code-templates` | — |
| 已移除 | `/api/code-generation` | `/api/v1/generator/code-generation` | — |
| 已移除 | `/api/code-generator` | `/api/v1/generator/*`（见 §13.1.1） | — |
| 已移除 | `/api/v1/generator/generate`、`/tables/{id}`、`/catalog/entities` | `generation-tasks`、`data-sources/{id}/tables`、`metadata-entity-snapshots` | — |
| 已移除 | `/api/v1/generator/table-metadata/*` | `data-sources/{id}/tables`、`tables:sync` | — |
| 过渡 | `/api/iam/users`（详设用语） | `/api/v1/iam/accounts`（As-Is Controller） | 2026-12-01 |
| 已移除 | `/api/iam/*` | `/api/v1/iam/*` | — |
| 已移除 | `/api/masterdata/*` | `/api/v1/masterdata/*` | — |
| 已移除 | `/api/system/*`、`/api/console/*` | `/api/v1/system/*`、`/api/v1/console/*` | — |
| 已移除 | `/api/integration/*`（`context-path=/api`） | `/api/v1/integration/*` | — |
| 已移除 | `/v1/metadata/*`（EAV，无 `/api` 前缀） | `/api/v1/metadata/*` | — |
| 过渡 | `ApiResponse` 无 `code` / 本地类 | `com.bone.core.model.ApiResponse` | 2026-09-01 |
| 过渡 | 成功 `code=0` | `code=200` | 2026-09-01 |
| 过渡 | 分页 `list` 字段 | `records` | 2026-09-01 |
| 过渡 | HTTP 200 + `success:false` | HTTP 4xx/5xx | 2026-09-01 |

> **说明**：**As-Is** 含扩展字段 `fields:search|searchByNames|allocate|health`（**动作式** `fields:*`）及 **catalog** `entities`、`…/entities/{entityId}/fields`、`…/relationships`；**模式 B** 动态数据 `/api/v1/runtime/entities/{entityCode}/records`（`delivery_mode=RUNTIME` 且已发布）。**禁止** catalog 与 EAV 共用顶层 `…/fields`，见 [元数据能力对照](../design/modules/元数据能力-实现映射与竞品对照.md) §1.2。

---

## 14. API 附属约定（并入本文，不另立文件）

下列与 HTTP 契约强相关，故写在本文；**数据库/DDD 仍见独立文档**（领域不同，不强行合并）。

### 14.1 数据与时间

| 规则 | 说明 |
|------|------|
| 序列化 | `Instant` / `OffsetDateTime` → ISO-8601 **UTC**，后缀 `Z` |
| 禁止 | 裸毫秒时间戳、无时区 `LocalDateTime` 直接出 API |
| 解析 | 入参同格式；无效格式 → `COMMON_VALIDATION_FAILED` |

**原因**：多租户、多时区与前端 `dayjs` 统一 UTC 可避免「差 8 小时」线上问题。

### 14.2 枚举与状态

| 规则 | 说明 |
|------|------|
| 对外 | 字符串枚举：`ENABLED`、`DEPLOYED`（OpenAPI `enum`） |
| 禁止 | `status: 1` 无文档魔法数 |
| 迁移 | 详设附状态机表；非法迁移 → **409** + 域内 errorCode |

**原因**：可读性与扩展性；与错误码、审计动作名一致。

### 14.3 国际化（i18n）

完整约定见 **[Bone-国际化规范.md](./Bone-国际化规范.md)**（`errorCode` 键、locale、UTC 时间）。

### 14.4 文件上传

| 规则 | 说明 |
|------|------|
| 协议 | `multipart/form-data`；声明 `max-file-size` |
| 校验 | MIME/扩展名白名单；JAR 做 checksum（见 `EXT_ARTIFACT_*`） |
| 响应 | 返回 `fileId` / `versionId`，**禁止** 返回服务器绝对路径 |
| 下载 | 短期鉴权 URL 或带权限的 `GET /files/{id}` |

**原因**：插件与导入是常见攻击面（木马、路径穿越）。

### 14.5 Webhook 出站（集成）

见 **[Bone-消息与事件规范.md](./Bone-消息与事件规范.md) §8**（HMAC 签名、重试、死信）。

---

## 15. 契约测试（HTTP / OpenAPI）

> **放置说明**：契约测试与 PR、OpenAPI、前端 Mock 强绑定，故写在 **本文 §15**；**异步消息**见 [Bone-消息与事件规范.md](./Bone-消息与事件规范.md)。

### 15.1 真源与职责

| 层级 | 真源 | 职责 |
|------|------|------|
| **设计** | 模块 OpenAPI 3.1 + [openapi/components/](./openapi/) 公共 schema | 字段、枚举、`errorCode`、分页形态 |
| **实现** | Spring Controller + `GlobalExceptionHandler` | 运行时行为与 HTTP 状态一致 |
| **消费方** | 前端 / 集成方 / 其他服务 HTTP 客户端 | 按契约生成类型或 Mock |

**原则**：**先改 OpenAPI（或 §13 路径表），再改代码**；禁止「代码已上线、文档未登记」。

### 15.2 测试分层

| 类型 | 工具（建议） | 覆盖 | 门禁 |
|------|--------------|------|------|
| **Schema 校验** | `openapi-diff` / Spectral | 破坏性变更（删字段、改类型、改必填） | PR 必过 |
| **契约 Mock** | Prism / Stoplight Mock | 前端并行开发、集成冒烟 | 可选本地；CI 对关键模块 |
| **Provider 测试** | Spring MockMvc + OpenAPI 断言（springdoc + 自定义） | 响应体符合 schema、`code`/`errorCode` | 模块 `verify` |
| **Consumer 契约** | Pact（跨团队时） | 调用方期望 vs 提供方 | L2 功能或对外 SDK |
| **E2E** | Playwright / REST Assured | 关键用户路径 | 发布前，非每次 PR |

**不替代**：领域单测、ArchUnit 分层检查（见 DDD 文档）。

### 15.3 PR 与 CI 流程

1. **变更 API** → 更新对应 `openapi.yaml` 或登记 §13.2 迁移行（含 `Sunset`）。  
2. **CI**：`openapi-diff` 对比 `main`；若 breaking → 必须带 `deprecated` + 截止日，或 ADR。  
3. **Provider**：新增/修改 Controller 时补充契约测试（至少：成功 200 + 典型 4xx 一条）。  
4. **前端**：`bone-frontend` 可从 OpenAPI 生成 `shared-types`（逐步）；开发期可用 Mock BaseURL。  

与 [BONE-总体架构设计方案](./BONE-总体架构设计方案.md) **§30.1**「API 契约」门禁一致。

### 15.4 破坏性变更判定（摘要）

| 变更 | 判定 |
|------|------|
| 删除/重命名响应字段 | Breaking |
| 收紧请求校验（可选→必填） | Breaking |
| 修改 `errorCode` 语义 | Breaking |
| 新增可选字段、新增端点 | 兼容 |
| HTTP 200 + `success:false` → 4xx | Breaking（见 §13.2） |

### 15.5 示例：Provider 断言要点

- 成功：`code=200`，`data` 结构符合 `ApiResponse` + 业务 DTO。  
- 失败：`ProblemDetail` 含 `errorCode`、`traceId`；HTTP 状态与 §4 表一致。  
- 分页：列表接口返回 `records` + `total` 或 `nextCursor`（与 §5 一致）。  

公共组件引用：`doc/architecture/openapi/components/ApiResponse.yaml`、`ProblemDetail.yaml`、`PageResult.yaml`。

---

## 16. 平台规范体系索引

完整索引见 **[README.md](./README.md)**。与本文直接相关的独立规范：

| 文档 | 职责 |
|------|------|
| [Bone-错误码登记.md](./Bone-错误码登记.md) | `errorCode` 台账与 PR 流程 |
| [Bone-日志规范.md](./Bone-日志规范.md) | 日志、MDC、审计 |
| [Bone-可观测性规范.md](./Bone-可观测性规范.md) | Metrics、Trace、SLO |
| [Bone-安全开发规范.md](./Bone-安全开发规范.md) | 认证、密钥、沙箱、依赖扫描 |
| [Bone-多租户规范.md](./Bone-多租户规范.md) | 租户上下文与数据隔离 |
| [Bone-消息与事件规范.md](./Bone-消息与事件规范.md) | Topic、信封、DLQ |
| [Bone-配置与环境规范.md](./Bone-配置与环境规范.md) | `.env`、Profile |
| [Bone-版本与发布规范.md](./Bone-版本与发布规范.md) | API 版本、发布顺序 |
| [Bone-测试策略.md](./Bone-测试策略.md) | 测试分层与 CI |
| [Bone-国际化规范.md](./Bone-国际化规范.md) | i18n、时区 |
| [Bone-缓存规范.md](./Bone-缓存规范.md) | Redis Key/TTL |
| [adr/](./adr/) | 架构决策记录 |

### 16.2 本规范不采纳的做法

| 做法 | 原因 |
|------|------|
| JSON:API | 与现有 `ApiResponse` 割裂大 |
| 全资源 HATEOAS | 前端以 OpenAPI 为主 |
| 取消统一信封 | 与存量异常处理器一致 |

---

## 17. 实施检查清单

### 新接口

- [ ] `/api/v1/{domain}/...` + SpringDoc  
- [ ] 成功 `ApiResponse` + HTTP 状态一致  
- [ ] 失败 `ProblemDetail` + `errorCode`  
- [ ] 错误码登记 [Bone-错误码登记.md](./Bone-错误码登记.md) §6 + §5 流程  
- [ ] MDC + `[API]`（[Bone-日志规范.md](./Bone-日志规范.md)）  
- [ ] Access Log 一条 `[API]` INFO  
- [ ] 列表分页类型正确（日志用 cursor）  
- [ ] 幂等写带 `Idempotency-Key`（如适用）  
- [ ] Scope 与 IAM 对齐（[安全规范](./Bone-安全开发规范.md)）  
- [ ] 租户上下文（[多租户规范](./Bone-多租户规范.md)）  

### 废弃接口

- [ ] `@Deprecated` + `Deprecation` + `Sunset` 头  
- [ ] §13.2 登记  

### 契约测试（§15）

- [ ] OpenAPI 已更新或与 §13 一致  
- [ ] `openapi-diff` 无未登记 breaking  
- [ ] Provider 至少覆盖成功 + 典型业务错误  
- [ ] 对外 SDK / 跨团队调用考虑 Pact  

---

## 18. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-17 | Studio 切换 `com.bone.core.model.ApiResponse` + `ProblemDetail`；SpringDoc |
| 2026-05-17 | 扩展 API 动作用冒号后缀；分页 `records`；OpenAPI extension-v1.yaml |
| 2026-05-17 | 移除 `/api/extension`、`/api/ext-points`、`/api/extensions`，仅 `/api/v1/extension` |
| 2026-05-17 | §15 契约测试；§16 索引；消息 Topic 见总体架构 §8.4 |
| 2026-05-17 | 合并错误码台账与日志规范入本文；§14 附属约定；独立文档仅保留 DB/DDD/openapi |
| 2026-05-17 | §13.1.1 generator 规范路径；§13.2 全模块迁移表；metadata/generator 双挂载 |
| 2026-05-17 | §13.2：catalog 字段嵌套路径，与 EAV `fields:*` 区分 |
| 2026-05-17 | 错误码、日志拆至独立文档；§16 增补规范体系建议 |
| 2026-05-17 | 落地安全/可观测性/消息等独立规范；§16 改为规范索引 |
