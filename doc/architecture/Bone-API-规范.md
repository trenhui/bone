# Bone 平台 API 与可观测性规范

> **文档性质**：REST API、错误码、日志与审计的**统一工程契约**（单文档维护，避免规范碎片化）。  
> **更新**：2026-05-17  
> **关联**：[BONE-总体架构设计方案](./BONE-总体架构设计方案.md) §8、[Bone-DDD-最终实践方案](./Bone-DDD-最终实践方案.md)  
> **独立成文（不并入本文）**：[数据库开发规范.md](./数据库开发规范.md)（DDL/表结构）、[Bone-DDD-最终实践方案.md](./Bone-DDD-最终实践方案.md)（分层/CQRS）

### 文档地图

| 章节 | 内容 |
|------|------|
| §2–§3 | URL、HTTP、成功响应与分页 |
| **§4** | 错误模型、流程、**全平台错误码台账** |
| §5–§9 | 分页、头、LRO、幂等、权限 |
| **§10** | 日志、MDC、Access/Audit、Logback |
| §11–§13 | OpenAPI、Controller、模块路径与迁移 |
| **§14** | API 附属约定（时间/枚举/i18n 等） |
| **§15** | **契约测试**（OpenAPI / Mock / Pact / CI） |
| §16 | 其他主题索引（何时另立文档） |
| §17 | 实施检查清单 |

---

## 1. 目标与适用范围

| 项 | 说明 |
|----|------|
| **目标** | 统一 URL、HTTP 语义、响应/错误信封、错误码、分页、日志与横切头；可观测、可 i18n、可 CI 校验。 |
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
| 无版本别名 | 网关可把 `/api/{domain}/...` 重写到 v1，**最长存活 2 个 release**，响应必带 `Deprecation: true` 与 `Sunset: <HTTP-date>` |

### 2.3 域（domain）注册表

| domain | 服务 | 错误码前缀（§4.4） | 说明 |
|--------|------|-------------------|------|
| `iam` | bone-iam | `IAM_` | 认证、用户、角色、权限 |
| `metadata` | 元数据（规划：实体/发布；As-Is：扩展字段见 server `/v1/metadata/fields:*`） | `META_` | 与 [元数据能力对照](../design/modules/元数据能力-实现映射与竞品对照.md) 一致 |
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

## 4. 错误模型与错误码

### 4.1 设计原则

| 原则 | 说明 |
|------|------|
| HTTP 表达类别 | 4xx/5xx 表示客户端/服务端/网关问题 |
| 业务码表达细节 | **稳定字符串** `EXT_PLUGIN_NOT_DEPLOYED`，供前端 i18n、告警聚合 |
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

### 4.3 Java 约定

```java
// 模块内：com.bone.{module}.common.exception.{Module}ErrorCode
public enum ExtensionErrorCode implements BoneErrorCode {
    PLUGIN_NOT_FOUND("EXT_PLUGIN_NOT_FOUND", 404, "插件不存在"),
    PLUGIN_NOT_DEPLOYED("EXT_PLUGIN_NOT_DEPLOYED", 409, "插件未部署");

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;
}

// 抛出：BizException / ServiceException 携带 errorCode + httpStatus
throw new BizException(ExtensionErrorCode.PLUGIN_NOT_FOUND, pluginId);
```

`GlobalExceptionHandler` 映射为 §4.2 JSON；**禁止** `catch (Exception e) { return error(e.getMessage()) }` 吞掉业务码。

### 4.4 错误码命名与号段

**字符串业务码（强制新接口使用）**

```
{DOMAIN_PREFIX}_{SNAKE_CASE_REASON}
```

| 前缀 | 域 | 示例 |
|------|-----|------|
| `IAM_` | 身份 | `IAM_TOKEN_EXPIRED` |
| `META_` | 元数据 | `META_ENTITY_PUBLISHED` |
| `MD_` | 主数据 | `MD_RECORD_DUPLICATE` |
| `EXT_` | 扩展 | `EXT_PLUGIN_DEPLOY_FAILED` |
| `INT_` | 集成 | `INT_CONNECTOR_TEST_FAILED` |
| `SYS_` | 系统 | `SYS_CONFIG_LOCKED` |
| `GEN_` | 生成器 | `GEN_TEMPLATE_INVALID` |
| `COMMON_` | 跨模块 | `COMMON_IDEMPOTENCY_CONFLICT` |

**整数码（存量 `ErrorCode` 对象，逐步迁移）**

| 范围 | 用途 |
|------|------|
| `0` | 历史成功码，**废弃**，改用 HTTP 200 |
| `400–599` | 与 HTTP 对齐的系统错误（`GlobalErrorCodeConstants`） |
| `1_000_000_000+` | 历史业务整型码（`ErrorCode.java` 注释） |
| `2000–6999` | 模块预留整型段（**新模块优先字符串码**） |

新增错误须登记到模块 `*ErrorCode` 枚举 + 模块 README 或 OpenAPI `x-errorCodes`。

### 4.5 新增错误码流程（开发必做）

```
1. 在模块 *ErrorCode 枚举中定义（字符串 errorCode + httpStatus + defaultMessage）
2. 在本文 §4.6 对应域表格追加一行（PR 必含）
3. OpenAPI @Operation 或 x-errorCodes 补充该码
4. PR 描述写明「新增 EXT_XXX，HTTP 409」
5. 单测：断言响应含 errorCode、traceId
```

| 禁止 | 原因 |
|------|------|
| `throw new BizException("xxx失败")` 无码 | 前端/监控无法聚合 |
| 复用含糊码 `COMMON_INTERNAL_ERROR` 表达已知业务失败 | 掩盖真实原因 |
| 修改已发布 `errorCode` 字符串含义 | 破坏 i18n 与告警规则 |
| 对外返回 `e.getMessage()` 含 SQL/堆栈 | 安全风险 |

### 4.6 全平台错误码台账（随 PR 更新）

新增码前先检索本节，避免重复。命名：`{前缀}_{SNAKE_CASE}`。

| 前缀 | 模块 |
|------|------|
| `COMMON_` | 平台公共 |
| `IAM_` | 身份与访问 |
| `META_` | 元数据 |
| `MD_` | 主数据 |
| `EXT_` | 扩展 |
| `INT_` | 集成 |
| `SYS_` | 系统管理 |
| `GEN_` | 代码生成 |

#### COMMON_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `COMMON_VALIDATION_FAILED` | 400 | 参数校验失败 |
| `COMMON_UNAUTHORIZED` | 401 | 未认证 |
| `COMMON_FORBIDDEN` | 403 | 无权限 |
| `COMMON_NOT_FOUND` | 404 | 资源不存在 |
| `COMMON_CONFLICT` | 409 | 版本/状态冲突 |
| `COMMON_IDEMPOTENCY_CONFLICT` | 409 | 幂等键冲突且 body 不一致 |
| `COMMON_RATE_LIMITED` | 429 | 限流 |
| `COMMON_INTERNAL_ERROR` | 500 | 未预期系统错误 |

#### IAM_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `IAM_TOKEN_EXPIRED` | 401 | 访问令牌过期 |
| `IAM_TOKEN_INVALID` | 401 | 令牌无效 |
| `IAM_CREDENTIAL_INVALID` | 401 | 用户名或密码错误 |
| `IAM_USER_NOT_FOUND` | 404 | 用户不存在 |
| `IAM_USER_DISABLED` | 403 | 用户已禁用 |
| `IAM_ROLE_NOT_FOUND` | 404 | 角色不存在 |
| `IAM_PERMISSION_DENIED` | 403 | 缺少权限 |

#### META_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `META_ENTITY_NOT_FOUND` | 404 | 实体不存在 |
| `META_ENTITY_PUBLISHED` | 409 | 实体已发布不可删 |
| `META_FIELD_NOT_FOUND` | 404 | 字段不存在 |
| `META_FIELD_NAME_DUPLICATE` | 409 | 字段名重复 |
| `META_GENERATE_TASK_FAILED` | 500 | 代码生成任务失败 |
| `META_TEMPLATE_INVALID` | 400 | 模板语法错误 |

#### MD_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `MD_ENTITY_NOT_FOUND` | 404 | 主数据实体不存在 |
| `MD_RECORD_NOT_FOUND` | 404 | 记录不存在 |
| `MD_RECORD_DUPLICATE` | 409 | 业务键重复 |
| `MD_QUALITY_CHECK_FAILED` | 422 | 质量规则不通过 |
| `MD_RECORD_NOT_PUBLISHED` | 409 | 记录未发布 |

#### EXT_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `EXT_POINT_NOT_FOUND` | 404 | 扩展点不存在 |
| `EXT_POINT_DISABLED` | 409 | 扩展点已禁用 |
| `EXT_PLUGIN_NOT_FOUND` | 404 | 插件不存在 |
| `EXT_PLUGIN_NOT_DEPLOYED` | 409 | 插件未部署 |
| `EXT_PLUGIN_ALREADY_DEPLOYED` | 409 | 插件已部署 |
| `EXT_PLUGIN_DEPLOY_FAILED` | 500 | 部署失败 |
| `EXT_PLUGIN_VERSION_NOT_FOUND` | 404 | 版本不存在 |
| `EXT_ARTIFACT_TOO_LARGE` | 400 | 制品超过大小限制 |
| `EXT_ARTIFACT_CHECKSUM_MISMATCH` | 400 | 校验和不匹配 |
| `EXT_SANDBOX_TIMEOUT` | 504 | 沙箱执行超时 |

#### INT_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `INT_CONNECTOR_NOT_FOUND` | 404 | 连接器不存在 |
| `INT_CONNECTOR_TEST_FAILED` | 502 | 连接测试失败 |
| `INT_FLOW_NOT_FOUND` | 404 | 流程不存在 |
| `INT_FLOW_INVALID_STATE` | 409 | 流程状态不允许该操作 |
| `INT_EXECUTION_NOT_FOUND` | 404 | 执行记录不存在 |

#### SYS_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `SYS_CONFIG_NOT_FOUND` | 404 | 配置项不存在 |
| `SYS_CONFIG_LOCKED` | 409 | 配置项锁定 |
| `SYS_ALERT_RULE_NOT_FOUND` | 404 | 告警规则不存在 |

#### GEN_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `GEN_DATASOURCE_NOT_FOUND` | 404 | 数据源不存在 |
| `GEN_DATASOURCE_CONNECTION_FAILED` | 502 | 数据源连接失败 |
| `GEN_TEMPLATE_NOT_FOUND` | 404 | 模板不存在 |
| `GEN_TEMPLATE_INVALID` | 400 | 模板校验失败 |
| `GEN_TASK_NOT_FOUND` | 404 | 生成任务不存在 |
| `GEN_TASK_FAILED` | 500 | 生成任务失败 |

新增行模板：`| \`DOMAIN_REASON\` | 4xx | 说明 |`

### 4.7 HTTP 与业务码对照（速查）

| 场景 | HTTP | 推荐 errorCode 类型 |
|------|------|---------------------|
| 参数缺失/格式错误 | 400 | `COMMON_VALIDATION_FAILED` + `errors[]` |
| 未登录 | 401 | `COMMON_UNAUTHORIZED` |
| 无权限 | 403 | `COMMON_FORBIDDEN` |
| 资源不存在 | 404 | `{DOMAIN}_*_NOT_FOUND` |
| 状态不允许（未部署却回滚） | 409 | `{DOMAIN}_*_CONFLICT` / `*_NOT_DEPLOYED` |
| 乐观锁冲突 | 409 | `COMMON_CONFLICT` |
| 限流 | 429 | `COMMON_RATE_LIMITED` |
| 依赖超时 | 504 | `{DOMAIN}_*_TIMEOUT` |
| 未知异常 | 500 | `COMMON_INTERNAL_ERROR`（日志记堆栈） |

### 4.8 目标实现（与存量对齐）

当前 `BizException` 使用整型 `code`（见 `bone-core`）。**目标态**：

1. 枚举实现统一契约：`getErrorCode()` 返回字符串、`getHttpStatus()` 返回 int。  
2. `GlobalExceptionHandler` 组装 §4.2 `ProblemDetail` 写入 `ApiResponse.data`。  
3. 存量整型码保留至迁移完成，新功能**只加字符串码**。

```java
// 目标枚举形态（各模块复制模式）
@Getter
@RequiredArgsConstructor
public enum ExtensionErrorCode {
    PLUGIN_NOT_FOUND("EXT_PLUGIN_NOT_FOUND", 404, "插件不存在");
    private final String errorCode;
    private final int httpStatus;
    private final String defaultMessage;
}
```

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

## 10. 日志与审计规范

> 适用于所有后端服务（不仅 REST）。表审计列与 [数据库开发规范](./数据库开发规范.md) 对齐；链路对齐 SkyWalking / `traceparent`。

### 10.1 分类与保留

| 类型 | 前缀 | 用途 | 保留建议 |
|------|------|------|----------|
| Access | `[API]` | 每次 HTTP 一条摘要 | 30–90 天 |
| Application | `[Biz]` | 用例/排障 | 7–30 天 |
| Audit | `[Audit]` + DB | 合规 | ≥1 年（按政策） |
| Integration | `[Integration]` | 出站调用 | 30 天 |
| Job | `[Job]` | 定时任务 | 30 天 |

### 10.2 MDC（强制）

入口 Filter 设置，`finally` 中 `MDC.clear()`。

| Key | 来源 |
|-----|------|
| `traceId` | `X-Request-Id` / SkyWalking |
| `tenantId` | JWT / 头 |
| `userId` | JWT |
| `bizIdentityCode` | 头 / 上下文 |
| `domain` | 服务域，如 `extension` |
| `httpMethod` / `httpPath` | 请求（**path 用 URI 模板**） |

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BoneRequestContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String traceId = Optional.ofNullable(req.getHeader("X-Request-Id"))
                .filter(StringUtils::hasText)
                .orElse(UUID.randomUUID().toString().replace("-", ""));
        MDC.put("traceId", traceId);
        res.setHeader("X-Request-Id", traceId);
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            log.info("[API] traceId={} method={} path={} status={} durationMs={} tenantId={} userId={}",
                    traceId, req.getMethod(), req.getRequestURI(), res.getStatus(),
                    System.currentTimeMillis() - start, MDC.get("tenantId"), MDC.get("userId"));
            MDC.clear();
        }
    }
}
```

### 10.3 级别与前缀

| 级别 | 场景 | 生产 |
|------|------|------|
| ERROR | 未捕获异常、依赖不可用 | 开 |
| WARN | 已知业务失败（带 errorCode）、慢请求、重试 | 开 |
| INFO | Access、状态变更、审计 | 开 |
| DEBUG | 诊断 | **关** |

| 类型 | 级别 | 内容 |
|------|------|------|
| `BizException` / 已知码 | WARN | errorCode + message，默认不打满栈 |
| 未知 `Exception` | ERROR | traceId + 堆栈 |

### 10.4 Access Log

每条对外 HTTP 至少一条 INFO；`status>=400` 必带 `errorCode`；`durationMs>3000` 额外 WARN。  
**禁止**：Authorization 全文、密码、Cookie、大 body。

### 10.5 审计

**必须审计**：登录/登出、IAM 变更、插件部署/回滚、主数据发布/删除、系统配置、集成流程激活/删除。

落库字段：`traceId`, `principal`, `tenantId`, `action`, `resourceType`, `resourceId`, `result`, `timestamp`（对齐 `iam_audit_log` 等）。

### 10.6 脱敏

| 数据 | 规则 |
|------|------|
| 密码、Token、API Key | 禁止 |
| 手机/证件 | 掩码 |
| SQL | 仅 local DEBUG |

### 10.7 Logback（推荐）

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger{36} [traceId=%X{traceId}] [tenantId=%X{tenantId}] - %msg%n</pattern>
        </encoder>
    </appender>
    <logger name="com.bone" level="INFO"/>
    <logger name="org.springframework" level="WARN"/>
    <root level="INFO"><appender-ref ref="CONSOLE"/></root>
</configuration>
```

生产可选用 `logstash-logback-encoder` 输出 JSON（`traceId`、`tenantId`、`level`、`message`）。

### 10.8 链路、前端与告警

- 响应头回显 `X-Request-Id`；`ProblemDetail.traceId` 与 MDC 一致。  
- 前端：axios 注入 `X-Request-Id`；错误 UI 可展示 `traceId` + `errorCode`。  
- 告警建议：5xx 率、P95 延迟、慢请求 WARN 数；Prometheus 标签用 `http_route`（模板路径）。

### 10.9 分层职责

| 层 | 可打 | 禁止 |
|----|------|------|
| Controller/Filter | `[API]` | 业务刷屏 |
| Application | `[Biz]` | 完整 DTO（含 PII） |
| Domain | 不变式 WARN | 基础设施日志 API |
| Infrastructure | `[Integration]` 失败 | 吞异常 |

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
OpenAPI 草案：[openapi/extension-v1.yaml](./openapi/extension-v1.yaml)。

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

| GET | `/api/v1/extension/operations/{id}` | LRO 轮询 |

### 13.2 废弃与迁移

| 状态 | 旧 | 新 | 截止 |
|------|----|----|------|
| 已移除 | `/api/ext-points`、`/api/extensions`、`/api/extension/*` | `/api/v1/extension/*` | — |
| 过渡 | `ApiResponse` 无 `code` / 本地类 | `com.bone.core.model.ApiResponse` | 2026-09-01 |
| 过渡 | 成功 `code=0` | `code=200` | 2026-09-01 |
| 过渡 | 分页 `list` 字段 | `records` | 2026-09-01 |
| 过渡 | HTTP 200 + `success:false` | HTTP 4xx/5xx | 2026-09-01 |
| 过渡 | `/v1/metadata/*`（`bone-metadata-server`，扩展字段 EAV） | `/api/v1/metadata/*`（或经网关统一加 `/api` 前缀） | 2026-12-01 |

> **说明**：当前 As-Is 仅 **扩展字段** `fields:search|searchByNames|allocate|health`（迁移后仍为 **动作式** `fields:*`）。**建模 catalog**（MVP-2）使用 `/api/v1/metadata/entities`、`…/entities/{entityId}/fields`、`…/relationships`；**禁止** catalog 与 EAV 共用顶层 `…/fields`，见 [元数据能力对照](../design/modules/元数据能力-实现映射与竞品对照.md) §1.2。

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

| 规则 | 说明 |
|------|------|
| 稳定键 | `errorCode`（如 `EXT_PLUGIN_NOT_FOUND`） |
| 展示 | 前端 `errorCode → 文案`；`message` 为默认中文 fallback |
| 禁止 | 修改已发布 `errorCode` 含义 |

**原因**：控制台多语言不能依赖后端改中文 message。

### 14.4 文件上传

| 规则 | 说明 |
|------|------|
| 协议 | `multipart/form-data`；声明 `max-file-size` |
| 校验 | MIME/扩展名白名单；JAR 做 checksum（见 `EXT_ARTIFACT_*`） |
| 响应 | 返回 `fileId` / `versionId`，**禁止** 返回服务器绝对路径 |
| 下载 | 短期鉴权 URL 或带权限的 `GET /files/{id}` |

**原因**：插件与导入是常见攻击面（木马、路径穿越）。

### 14.5 Webhook 出站（集成）

| 规则 | 说明 |
|------|------|
| 签名 | `X-Bone-Signature: HMAC-SHA256(body, secret)` |
| 重试 | 指数退避；`eventId` 幂等 |
| 失败 | 死信表（如 `int_dead_letter`） |

**原因**：对外通知需可验证、可排障、防重放。

---

## 15. 契约测试（HTTP / OpenAPI）

> **放置说明**：契约测试与 PR、OpenAPI、前端 Mock 强绑定，故写在 **本文 §15**；**异步消息 Topic** 见 [BONE-总体架构设计方案](./BONE-总体架构设计方案.md) **§8.4**（平台事件总线，非 HTTP 专属）。

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

## 16. 其他主题索引（何时另立文档）

| 主题 | 是否独立成文 | 原因 |
|------|--------------|------|
| REST / 错误码 / 日志 / **契约测试** | **否** → 本文 §2–§15 | 同一 HTTP 交付面 |
| DDL / 表 / 索引 | **是** → [数据库开发规范](./数据库开发规范.md) | DBA、持久化，与 HTTP 正交 |
| DDD 分层 / CQRS | **是** → [Bone-DDD-最终实践方案](./Bone-DDD-最终实践方案.md) | 代码结构 |
| OpenAPI 组件 YAML | **是** → [openapi/](./openapi/) | 机器可读契约 |
| **消息 Topic / 领域事件** | **否** → [BONE-总体架构](./BONE-总体架构设计方案.md) **§8.4** | 事件总线、跨服务，非 REST |
| JSON:API / 全站 HATEOAS | **不建议** | 与 `ApiResponse` 冲突 |

---

## 17. 实施检查清单

### 新接口

- [ ] `/api/v1/{domain}/...` + SpringDoc  
- [ ] 成功 `ApiResponse` + HTTP 状态一致  
- [ ] 失败 `ProblemDetail` + `errorCode`  
- [ ] 错误码登记 §4.6 + 流程 §4.5  
- [ ] MDC + `[API]` 线（§10）  
- [ ] Access Log 一条 `[API]` INFO  
- [ ] 列表分页类型正确（日志用 cursor）  
- [ ] 幂等写带 `Idempotency-Key`（如适用）  
- [ ] Scope 与 IAM 对齐  

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
| 2026-05-17 | 扩展 API 动作用冒号后缀；分页 `records`；OpenAPI extension-v1.yaml |
| 2026-05-17 | 移除 `/api/extension`、`/api/ext-points`、`/api/extensions`，仅 `/api/v1/extension` |
| 2026-05-17 | §15 契约测试；§16 索引；消息 Topic 见总体架构 §8.4 |
| 2026-05-17 | 合并错误码台账与日志规范入本文；§14 附属约定；独立文档仅保留 DB/DDD/openapi |
| 2026-05-17 | §13.2：catalog 字段嵌套路径，与 EAV `fields:*` 区分 |
