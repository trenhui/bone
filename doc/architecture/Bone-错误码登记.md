# Bone 平台错误码登记

> **文档性质**：全平台**稳定业务错误码**（`errorCode`）的命名、号段、登记流程与**台账真源**。  
> **更新**：2026-05-17  
> **关联**：[Bone-API-规范.md](./Bone-API-规范.md)（HTTP 信封、`ProblemDetail` 结构）、[openapi/components/ProblemDetail.yaml](./openapi/components/ProblemDetail.yaml)

---

## 1. 与 HTTP 错误模型的关系

| 层级 | 载体 | 说明 |
|------|------|------|
| HTTP | 4xx / 5xx | 表达错误**类别**（客户端/服务端/网关） |
| 信封 | `ApiResponse` | `success: false`，`code` **仅镜像 HTTP** |
| 业务 | `data.errorCode` | **稳定字符串**，供 i18n、告警、契约测试 |

失败响应结构（`ProblemDetail`）见 [Bone-API-规范 §4.2](./Bone-API-规范.md#42-失败响应结构对齐-rfc-7807)。**禁止**把 HTTP 码与业务码混为同一个整数。

---

## 2. 设计原则

| 原则 | 说明 |
|------|------|
| 稳定 | 已发布 `errorCode` **禁止**改含义；废弃用新码 + 文档标注 |
| 可聚合 | 监控/告警按 `errorCode` 分组，禁止仅依赖中文 `message` |
| 可 i18n | 前端以 `errorCode` 为键；`message` 为默认中文 fallback |
| 安全 | `detail` 禁止 SQL、堆栈、内部路径；未知异常 → `COMMON_INTERNAL_ERROR` + 日志记堆栈 |
| 单一登记 | 新增码必须出现在本文 **§6 台账**；PR 不得只改枚举不改本文 |

---

## 3. 命名与号段

### 3.1 字符串业务码（新接口强制）

```
{DOMAIN_PREFIX}_{SNAKE_CASE_REASON}
```

| 前缀 | 域 | 服务 | 示例 |
|------|-----|------|------|
| `COMMON_` | 平台公共 | — | `COMMON_VALIDATION_FAILED` |
| `IAM_` | 身份 | bone-iam | `IAM_TOKEN_EXPIRED` |
| `META_` | 元数据 | metadata-server | `META_ENTITY_PUBLISHED` |
| `MD_` | 主数据 | bone-masterdata | `MD_RECORD_DUPLICATE` |
| `EXT_` | 扩展 | bone-extension-studio | `EXT_PLUGIN_NOT_DEPLOYED` |
| `INT_` | 集成 | bone-integration | `INT_CONNECTOR_TEST_FAILED` |
| `SYS_` | 系统 | bone-system | `SYS_CONFIG_LOCKED` |
| `GEN_` | 代码生成 | studio-generator | `GEN_TEMPLATE_INVALID` |
| `BP_` | 蓝图样板 | bone-blueprint | `BP_ORDER_NOT_FOUND` |

域注册与 URL `domain` 对齐，见 [Bone-API-规范 §2.3](./Bone-API-规范.md#23-域domain注册表)。

### 3.2 整数码（存量，逐步迁移）

| 范围 | 用途 |
|------|------|
| `0` | 历史成功码，**废弃** → HTTP 200 |
| `400–599` | 与 HTTP 对齐（`GlobalErrorCodeConstants`） |
| `1_000_000_000+` | 历史业务整型（`ErrorCode.java`） |
| `2000–6999` | 模块预留整型段；**新模块优先字符串码** |

---

## 4. Java 实现约定

```java
// 模块内：com.bone.{module}.common.exception.{Module}ErrorCode
@Getter
@RequiredArgsConstructor
public enum ExtensionErrorCode {
    PLUGIN_NOT_FOUND("EXT_PLUGIN_NOT_FOUND", 404, "插件不存在"),
    PLUGIN_NOT_DEPLOYED("EXT_PLUGIN_NOT_DEPLOYED", 409, "插件未部署");

    private final String errorCode;
    private final int httpStatus;
    private final String defaultMessage;
}

// 抛出：BizException / ServiceException 携带 errorCode + httpStatus
throw new BizException(ExtensionErrorCode.PLUGIN_NOT_FOUND, pluginId);
```

`GlobalExceptionHandler` 组装 `ProblemDetail` 写入 `ApiResponse.data`；**禁止** `catch (Exception e) { return error(e.getMessage()) }` 吞掉业务码。

**目标态**（与存量 `BizException` 整型 `code` 并存迁移）：

1. 枚举提供 `getErrorCode()`（字符串）、`getHttpStatus()`（int）。  
2. 新功能**只加字符串码**。  
3. 单测断言响应含 `errorCode`、`traceId`。

---

## 5. 新增错误码流程（PR 必做）

```
1. 在模块 *ErrorCode 枚举中定义（errorCode + httpStatus + defaultMessage）
2. 在本文 §6 对应域表格追加一行
3. OpenAPI @Operation 或 x-errorCodes 补充该码
4. PR 描述写明「新增 EXT_XXX，HTTP 409」
5. 单测：断言 ProblemDetail.errorCode、traceId
```

| 禁止 | 原因 |
|------|------|
| `throw new BizException("xxx失败")` 无码 | 前端/监控无法聚合 |
| 用 `COMMON_INTERNAL_ERROR` 掩盖已知业务失败 | 掩盖根因 |
| 修改已发布 `errorCode` 含义 | 破坏 i18n 与告警 |
| 对外返回含 SQL/堆栈的 `detail` | 安全风险 |

---

## 6. 全平台错误码台账（真源，随 PR 更新）

新增码前先检索本节，避免重复。模板：`| \`DOMAIN_REASON\` | 4xx | 说明 |`

### COMMON_

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

### IAM_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `IAM_TOKEN_EXPIRED` | 401 | 访问令牌过期 |
| `IAM_TOKEN_INVALID` | 401 | 令牌无效 |
| `IAM_CREDENTIAL_INVALID` | 401 | 用户名或密码错误 |
| `IAM_USER_NOT_FOUND` | 404 | 用户不存在 |
| `IAM_USER_DISABLED` | 403 | 用户已禁用 |
| `IAM_ROLE_NOT_FOUND` | 404 | 角色不存在 |
| `IAM_PERMISSION_DENIED` | 403 | 缺少权限 |
| `IAM_SSO_NOT_CONFIGURED` | 501 | SSO/IdP 未配置或未实现 |
| `IAM_MFA_NOT_AVAILABLE` | 501 | MFA 未启用（社区版） |

### META_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `META_ENTITY_NOT_FOUND` | 404 | 实体不存在 |
| `META_ENTITY_PUBLISHED` | 409 | 实体已发布不可删 |
| `META_FIELD_NOT_FOUND` | 404 | 字段不存在 |
| `META_FIELD_NAME_DUPLICATE` | 409 | 字段名重复 |
| `META_GENERATE_TASK_FAILED` | 500 | 代码生成任务失败（**过渡**；新接口优先 `GEN_TASK_FAILED`） |
| `META_TEMPLATE_INVALID` | 400 | 模板语法错误（**过渡**；新接口优先 `GEN_TEMPLATE_INVALID`） |
| `META_RUNTIME_ENTITY_NOT_FOUND` | 400 | 未找到已发布的 RUNTIME 实体 |
| `META_RUNTIME_RECORD_NOT_FOUND` | 404 | 运行时记录不存在 |
| `META_RUNTIME_INVALID_IDENTIFIER` | 400 | 非法表名/列名 |

### MD_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `MD_ENTITY_NOT_FOUND` | 404 | 主数据实体不存在 |
| `MD_RECORD_NOT_FOUND` | 404 | 记录不存在 |
| `MD_RECORD_DUPLICATE` | 409 | 业务键重复 |
| `MD_QUALITY_CHECK_FAILED` | 422 | 质量规则不通过 |
| `MD_RECORD_NOT_PUBLISHED` | 409 | 记录未发布 |
| `MD_QUALITY_REPORT_NOT_IMPLEMENTED` | 501 | 质量报告查询未实现 |
| `MD_RECORD_EXPORT_NOT_IMPLEMENTED` | 501 | 主数据记录导出未实现 |
| `MD_META_ENTITY_NOT_FOUND` | 404 | 元数据实体不存在 |
| `MD_META_ENTITY_NOT_PUBLISHED` | 422 | 仅已发布元数据实体可转换为主数据 |

### EXT_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `EXT_RESOURCE_NOT_FOUND` | 404 | Studio 资源不存在（扩展点/插件等通用） |
| `EXT_POINT_NOT_FOUND` | 404 | 扩展点不存在 |
| `EXT_STATE_INVALID` | 409 | 扩展/插件状态非法 |
| `EXT_POINT_DISABLED` | 409 | 扩展点已禁用 |
| `EXT_PLUGIN_NOT_FOUND` | 404 | 插件不存在 |
| `EXT_PLUGIN_NOT_DEPLOYED` | 409 | 插件未部署 |
| `EXT_PLUGIN_ALREADY_DEPLOYED` | 409 | 插件已部署 |
| `EXT_PLUGIN_DEPLOY_FAILED` | 500 | 部署失败 |
| `EXT_PLUGIN_VERSION_NOT_FOUND` | 404 | 版本不存在 |
| `EXT_ARTIFACT_TOO_LARGE` | 400 | 制品超过大小限制 |
| `EXT_ARTIFACT_CHECKSUM_MISMATCH` | 400 | 校验和不匹配 |
| `EXT_SANDBOX_TIMEOUT` | 504 | 沙箱执行超时 |

### INT_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `INT_CONNECTOR_NOT_FOUND` | 404 | 连接器不存在 |
| `INT_CONNECTOR_NOT_IMPLEMENTED` | 501 | 连接器类型尚未实现（如 REST） |
| `INT_CONNECTOR_TEST_FAILED` | 502 | 连接测试失败 |
| `INT_FLOW_NOT_FOUND` | 404 | 流程不存在 |
| `INT_FLOW_INVALID_STATE` | 409 | 流程状态不允许该操作 |
| `INT_EXECUTION_NOT_FOUND` | 404 | 执行记录不存在 |

### SYS_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `SYS_CONFIG_NOT_FOUND` | 404 | 配置项不存在 |
| `SYS_CONFIG_LOCKED` | 409 | 配置项锁定 |
| `SYS_ALERT_RULE_NOT_FOUND` | 404 | 告警规则不存在 |

### GEN_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `GEN_DATASOURCE_NOT_FOUND` | 404 | 数据源不存在 |
| `GEN_DATASOURCE_CONNECTION_FAILED` | 502 | 数据源连接失败 |
| `GEN_TEMPLATE_NOT_FOUND` | 404 | 模板不存在 |
| `GEN_TEMPLATE_INVALID` | 400 | 模板校验失败 |
| `GEN_TASK_NOT_FOUND` | 404 | 生成任务不存在 |
| `GEN_TASK_FAILED` | 500 | 生成任务失败 |

### BP_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `BP_ORDER_NOT_FOUND` | 404 | 订单不存在（含跨租户不可见） |
| `BP_ORDER_STATUS_CONFLICT` | 409 | 订单当前状态不允许该操作 |
| `BP_ORDER_STATUS_INVALID` | 400 | 订单状态查询入参非法 |
| `BP_PAYMENT_NOT_FOUND` | 404 | 支付单不存在（含跨租户不可见） |
| `BP_PAYMENT_STATUS_CONFLICT` | 409 | 支付单当前状态不允许该操作 |
| `BP_PAYMENT_SIGNATURE_INVALID` | 401 | 渠道回调签名校验失败（不可信调用方） |
| `BP_PAYMENT_CHANNEL_PREPAY_FAILED` | 502 | 渠道预下单失败（上游依赖故障） |

> **样板落地范围**：代码常量在 `bone-blueprint/common/BlueprintErrorCodes`；抛出方必须用载码构造器
> `new BizException(HTTP 状态, 码 + ": " + 说明)`。`BizException(String)` 的默认码是 **500**，用它等于把
> 「查不到 / 状态冲突 / 参数错」都报成服务端故障（本项目已据此修正 404 与 400 两处误报）。

---

## 7. HTTP 与业务码对照（速查）

| 场景 | HTTP | 推荐 errorCode |
|------|------|----------------|
| 参数缺失/格式错误 | 400 | `COMMON_VALIDATION_FAILED` + `errors[]` |
| 未登录 | 401 | `COMMON_UNAUTHORIZED` / `IAM_*` |
| 无权限 | 403 | `COMMON_FORBIDDEN` / `IAM_PERMISSION_DENIED` |
| 资源不存在 | 404 | `{DOMAIN}_*_NOT_FOUND` |
| 状态不允许 | 409 | `{DOMAIN}_*_CONFLICT` / 域内语义码 |
| 乐观锁冲突 | 409 | `COMMON_CONFLICT` |
| 语义校验失败 | 422 | `{DOMAIN}_*_FAILED` |
| 限流 | 429 | `COMMON_RATE_LIMITED` |
| 依赖超时 | 504 | `{DOMAIN}_*_TIMEOUT` |
| 未知异常 | 500 | `COMMON_INTERNAL_ERROR`（日志记堆栈） |

---

## 8. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-17 | 从 Bone-API-规范 §4 独立；台账与流程为本文真源 |
