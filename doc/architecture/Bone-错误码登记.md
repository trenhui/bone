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
| 单一登记 | 新增码必须出现在本文 **§6 台账**；PR 不得只改码常量 / 状态表不改本文 |

---

## 3. 命名与号段

### 3.1 字符串业务码（新接口强制）

```
{DOMAIN_PREFIX}_{SNAKE_CASE_REASON}
```

| 前缀 | 域 | 服务 | 示例 |
|------|-----|------|------|
| `COMMON_` | 平台公共 | — | `COMMON_VALIDATION_FAILED` |
| `IAM_` | 身份 | bone-iam | `IAM_LOGIN_FAILED` |
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

**每个模块两个构件，同放 `com.bone.{module}.common`**（示例取样板模块 `bone-blueprint`；全仓 6 个模块的实际形态一致）：

```java
// ① 码常量类：只承载「稳定码字符串 + 语义」，不承载状态、不承载文案
public final class BlueprintErrorCodes {
    /** 订单不存在（含跨租户不可见）。 */
    public static final String ORDER_NOT_FOUND = "BP_ORDER_NOT_FOUND";

    private BlueprintErrorCodes() {}
}

// ② 状态表 + 工厂：「码 → HTTP 状态」的唯一真源
public final class BlueprintErrors {
    private static final Map<String, Integer> DEFAULT_HTTP_STATUS =
        Map.ofEntries(
            Map.entry(BlueprintErrorCodes.ORDER_NOT_FOUND, 404),
            Map.entry(BlueprintErrorCodes.ORDER_STATUS_CONFLICT, 409));

    static { checkEveryCodeRegistered(); }   // 类加载即反射校验，漏登记启动失败

    /** 抛业务异常：状态由表提供，抛出点只表达业务语义。 */
    public static BizException of(String errorCode, Object detail) { /* ... */ }

    /** 供 Optional.orElseThrow(...) 用的延迟构造器（不为正常分支付构造栈开销）。 */
    public static Supplier<BizException> supplier(String errorCode, Object detail) { /* ... */ }
}

// ③ 抛出点：不写状态数字
throw BlueprintErrors.of(BlueprintErrorCodes.ORDER_NOT_FOUND, orderId);
```

| 模块 | 码常量类 | 状态表（「码 → 状态」真源） |
|------|----------|----------------------------|
| bone-blueprint | `BlueprintErrorCodes` | `BlueprintErrors` |
| bone-iam | `IamErrorCodes` | `IamErrors` |
| bone-system | `SystemErrorCodes` | `SystemErrors` |
| bone-masterdata | `MasterDataErrorCodes` | 待建 |
| bone-extension-studio | `StudioErrorCodes` | 待建 |
| bone-metadata-server / metadata-engine | `MetaErrorCodes` / `ErrorCodes` | 待建 |

**为什么拆两个类**

1. `BizException` 的首参是 **HTTP 状态**（int），业务码只能以字符串拼进 message——「码」与「状态」天然是两份数据。散在各抛出点手写 `new BizException(404, 码 + ": " + 说明)` 时，任一处不一致都没有机制发现。
2. 状态表集中一处 + 类加载期**反射校验**（`checkEveryCodeRegistered()`）⇒ 漏登记不可能溜到运行期，而不是被全局处理器悄悄兜底成 400/500。
3. `BizException(String)` 的默认码是 **500**：绕过工厂手写消息，等于把「查不到 / 状态冲突 / 参数错」都报成服务端故障。

**约定**

1. 新功能**只加字符串码**；抛出走 `{Module}Errors.of(码[, 上下文])`，`Optional` 分支用 `supplier(码, 上下文)`。
2. 未登记状态即 **fail fast**（`IllegalStateException`）；**禁止**给状态表加「默认 400」的兜底。
3. message 形如 `{码}: {上下文}`（无上下文时仅 `{码}`）⇒ 前端 / 监控 / i18n 以码为键，中文只是 fallback。
4. 单测同时钉住**状态**与**码**：`hasFieldOrPropertyWithValue("code", 404)` + `hasMessageContaining(码常量)`。

`GlobalExceptionHandler` 组装 `ProblemDetail` 写入 `ApiResponse.data`；**禁止** `catch (Exception e) { return error(e.getMessage()) }` 吞掉业务码。

> **本文旧版 §4 描述的是枚举形态**（`{Module}ErrorCode` 枚举持 `errorCode/httpStatus/defaultMessage`，包 `common.exception`），
> **该写法在全仓零实现**（6 个模块 0 个枚举）：实际已统一收敛为「常量类 + 状态表」。继续把它写成「目标态」会让每个新模块都要在两种写法之间猜，故 2026-09-20 按实际实现形态改写。若日后要改回枚举，须先在一个模块实现，再同步本节与 §5 流程。

---

## 5. 新增错误码流程（PR 必做）

```
1. 在模块 {Module}ErrorCodes 定义码常量（码字符串 + 语义 javadoc）
2. 在模块 {Module}Errors 的 DEFAULT_HTTP_STATUS 登记「码 → HTTP 状态」（漏登记则启动失败）
3. 在本文 §6 对应域表格追加一行
4. OpenAPI @Operation 或 x-errorCodes 补充该码
5. PR 描述写明「新增 EXT_XXX，HTTP 409」
6. 单测：断言响应含 errorCode、traceId，并钉住抛出点的 code + 业务码
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
| `COMMON_VALIDATION_FAILED` | 400 | 参数校验失败（含字段级明细，见 `ProblemDetail.errors`） |
| `COMMON_MALFORMED_REQUEST` | 400 | 请求体无法解析（JSON 格式错误 / 类型不匹配） |
| `COMMON_UNAUTHORIZED` | 401 | 未认证 |
| `COMMON_FORBIDDEN` | 403 | 无权限 |
| `COMMON_NOT_FOUND` | 404 | 资源不存在 |
| `COMMON_METHOD_NOT_ALLOWED` | 405 | HTTP 方法不被支持 |
| `COMMON_NOT_IMPLEMENTED` | 501 | 能力未实现（连接器/适配器未实现），须与 500 区分 |
| `COMMON_CONFLICT` | 409 | 版本/状态冲突 |
| `COMMON_IDEMPOTENCY_CONFLICT` | 409 | 幂等键冲突且 body 不一致 |
| `COMMON_RATE_LIMITED` | 429 | 限流 |
| `COMMON_INTERNAL_ERROR` | 500 | 未预期系统错误 |

### IAM_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `IAM_LOGIN_FAILED` | 401 | 用户名或密码错误（不区分账号是否存在，避免账号枚举） |
| `IAM_ACCOUNT_LOCKED` | 423 | 连续失败次数超限导致账号锁定 |
| `IAM_ACCOUNT_DISABLED` | 403 | 账号被管理员禁用 |
| `IAM_REFRESH_TOKEN_REUSE` | 401 | 刷新令牌被复用（疑似泄露），已吊销该账号全部会话 |
| `IAM_REFRESH_TOKEN_REQUIRED` | 400 | 刷新令牌未提供 |
| `IAM_REFRESH_TOKEN_INVALID` | 401 | 刷新令牌非法 / 过期 / 信息缺失 |
| `IAM_REFRESH_TOKEN_ROTATE_FAILED` | 500 | 刷新令牌轮换未产出新令牌（服务端契约破坏） |
| `IAM_WEAK_PASSWORD` | 400 | 新密码不满足强度策略 |
| `IAM_OLD_PASSWORD_MISMATCH` | 401 | 自助改密时旧密码校验失败 |
| `IAM_ACCOUNT_NOT_FOUND` | 404 | 账号不存在（含跨租户不可见） |
| `IAM_USERNAME_CONFLICT` | 409 | 本租户内用户名已存在（唯一键 `uk_iam_account_username`） |
| `IAM_ACCOUNT_STATUS_CONFLICT` | 409 | 账号状态迁移不合法（如对已禁用账号重复禁用） |
| `IAM_ROLE_NOT_FOUND` | 404 | 角色不存在（含跨租户不可见） |
| `IAM_ROLE_ID_REQUIRED` | 400 | 授予角色权限时未提供角色 id |
| `IAM_PERMISSION_NOT_FOUND` | 404 | 权限不存在（含跨租户不可见） |
| `IAM_PERMISSION_PLATFORM_ONLY` | 403 | 平台域权限码（resource_path 为 tenants/permissions/sessions）不可授予租户角色——防租户自授平台能力 |
| `IAM_SESSION_NOT_FOUND` | 404 | 会话不存在（含跨租户不可见，与「参数缺失」分属不同语义） |
| `IAM_SESSION_ID_REQUIRED` | 400 | 会话 id 未提供 |
| `IAM_DEPT_NOT_FOUND` | 404 | 部门不存在（含跨租户不可见） |
| `IAM_DEPT_REQUIRED` | 400 | 归属部门为必填项（创建账号必须指定主部门；编辑时不允许清空归属部门） |
| `IAM_MENU_NOT_FOUND` | 404 | 菜单不存在（含跨租户不可见） |
| `IAM_AUDIT_SETTINGS_REQUIRED` | 400 | 审计设置未提供或为空 |
| `IAM_PROFILE_OWNERSHIP_DENIED` | 401 | 请求主体与目标账号不一致（越权访问他人 profile） |
| `IAM_TENANT_ACCESS_DENIED` | 403 | 跨租户访问被拒绝（防 IDOR） |
| `IAM_TENANT_DELETE_FORBIDDEN` | 400 | 平台租户（id = 0）不允许删除 |
| `IAM_TENANT_NOT_FOUND` | 404 | 租户不存在 |
| `IAM_TENANT_CODE_CONFLICT` | 409 | 租户编码已存在 |
| `IAM_TENANT_QUOTA_EXCEEDED` | 400 | 租户配额（账号数 / 角色数）已达上限 |
| `IAM_TENANT_ID_REQUIRED` | 400 | 租户 id 未提供 |
| `IAM_TENANT_QUOTA_INVALID` | 400 | 租户配额入参非法（如账号数 / 角色数为负数） |
| `IAM_APPLICATION_NOT_FOUND` | 404 | 应用不存在（含跨租户不可见） |
| `IAM_MODULE_NOT_FOUND` | 404 | 模块不存在（含跨租户不可见） |
| `IAM_APPLICATION_ID_REQUIRED` | 400 | 授予应用权限时未提供应用 id |
| `IAM_USER_ID_REQUIRED` | 400 | 授予应用权限时未提供被授权用户 id |
| `IAM_APP_ROLE_INVALID` | 400 | 传入的应用内角色不是合法取值 |
| `IAM_SSO_NOT_CONFIGURED` | 501 | SSO / IdP 未配置或回调未实现 |
| `IAM_MFA_NOT_AVAILABLE` | 501 | MFA 未在当前版本 / IdP 中启用 |

> **实现范围**：码常量在 `bone-iam/common/IamErrorCodes`（只承载稳定码字符串与语义）；
> **「码 → HTTP 状态」的唯一真源是 `bone-iam/common/IamErrors` 的 `DEFAULT_HTTP_STATUS` 表**（与本表逐行对应，共 35 行），
> 抛出方走 `IamErrors.of(码, 上下文)`（或 `orElseThrow` 用的 `IamErrors.supplier(码, 上下文)`），
> **不在抛出点手写状态数字**。新增码若忘记登记状态，`IamErrors` 类加载即抛 `IllegalStateException`（fail fast）。
>
> **抛出点覆盖（2026-09-20）**：application 层的**全部裸异常**已消除（`IllegalArgumentException` /
> `RuntimeException` 共 11 处）。它们不被 `IamExceptionHandler` 的任何分支匹配，会落到 `Exception` 兜底报成 **500**
> ——把「参数错 / 查不到」说成服务端故障。现分别归入 `IAM_ROLE_ID_REQUIRED`、`IAM_DEPT_NOT_FOUND`、
> `IAM_MENU_NOT_FOUND`、`IAM_TENANT_ID_REQUIRED`、`IAM_TENANT_QUOTA_INVALID`、`IAM_AUDIT_SETTINGS_REQUIRED`，
> 并复用 `IAM_ROLE_NOT_FOUND`。
>
> **回填说明**：本节曾长期停留在一份「理想码清单」（`IAM_TOKEN_EXPIRED`、`IAM_CREDENTIAL_INVALID`、
> `IAM_USER_NOT_FOUND`、`IAM_PERMISSION_DENIED` 等），与代码里实际抛出的码**双向不一致**：清单里的码无人实现、
> 实现的码无人登记。2026-09-19 依据 `IamErrorCodes` 实际内容整体重写，并消除抛出点的无码异常
> （`new BizException(NOT_FOUND, "应用不存在")` 与 `NotFoundException.of(...)` 一并收敛到 `IamErrors`）。
>
> **删除的码**：`IAM_PASSWORD_MUST_CHANGE`(403)——全仓零抛出点、零消费方。密码到期的实际机制是**登录响应标志**
> `LoginResp.requirePasswordChange`（`AuthApplicationService` 置为 `weak || expired`，前端据此切改密页，详设 IAM-20），
> 不需要一个错误码；留着只会让人以为还有第二套机制。


### META_

| errorCode | HTTP | 说明 |
|-----------|------|------|

### MD_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `MD_META_ENTITY_NOT_FOUND` | 404 | 元数据实体不存在 |
| `MD_REF_PLATFORM_SET_IMMUTABLE` | 403 | 租户尝试写平台值域（建/改/归档）——值域为平台域 artifact，租户只读 |
| `MD_REF_PLATFORM_VALUE_IMMUTABLE` | 403 | 租户尝试修改/停用平台值——租户只能操作自己的私有扩展值 |
| `MD_META_ENTITY_NOT_PUBLISHED` | 422 | 仅已发布元数据实体可转换为主数据 |
| `MD_ENTITY_ID_REQUIRED` | 400 | 主数据实体ID必填（创建质量规则等） |
| `MD_ENTITY_NAME_DUPLICATE` | 409 | 主数据实体名称已存在 |
| `MD_FIELD_NAME_DUPLICATE` | 409 | 字段名称已存在 |
| `MD_DATA_STANDARD_DUPLICATE` | 409 | 同实体+字段已存在数据标准 |
| `MD_DATA_STANDARD_NOT_FOUND` | 404 | 数据标准不存在 |
| `MD_EXPORT_SERIALIZE_FAILED` | 500 | 主数据记录导出序列化失败 |
| `MD_FILE_EMPTY` | 400 | 上传的Excel文件为空 |
| `MD_FILE_FORMAT_INVALID` | 400 | 仅支持 .xlsx / .xls 格式 |
| `MD_FILE_READ_FAILED` | 400 | 读取上传文件失败 |
| `MD_FILE_PARSE_FAILED` | 400 | 解析Excel文件失败 |
| `MD_RULE_SEVERITY_INVALID` | 400 | 质量规则严重级别非法（可选 LOW/MEDIUM/HIGH/CRITICAL） |
| `MD_RULE_EXPRESSION_INVALID` | 400 | 质量规则表达式无法求值（缺 field/pattern、min-max 非数值等） |
| `MD_RECORD_DATA_PARSE_FAILED` | 500 | 主数据记录 data 不是合法 JSON |
| `MD_TEMPLATE_DOMAIN_DUPLICATE` | 409 | 领域模板编码已存在 |
| `MD_TEMPLATE_FIELD_SCHEMA_INVALID` | 400 | 模板字段结构非法 |
| `MD_TEMPLATE_NOT_FOUND` | 404 | 领域模板不存在 |
| `MD_TEMPLATE_NOT_PUBLISHED` | 409 | 领域模板未发布 |
| `MD_TEMPLATE_VERSION_DUPLICATE` | 409 | 模板版本已存在 |

### EXT_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `EXT_RESOURCE_NOT_FOUND` | 404 | Studio 资源不存在（扩展点/插件等通用） |
| `EXT_STATE_INVALID` | 409 | 扩展/插件状态非法 |

### INT_

| errorCode | HTTP | 说明 |
|-----------|------|------|

### SYS_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `SYS_CONFIG_NOT_FOUND` | 404 | 配置项不存在 |
| `SYS_ALERT_RULE_NOT_FOUND` | 404 | 告警规则不存在 |
| `SYS_DICT_TYPE_NOT_FOUND` | 404 | 字典类型不存在（含跨租户不可见） |
| `SYS_DICT_TYPE_CODE_CONFLICT` | 409 | 同一租户作用域内该类型编码已存在 |
| `SYS_DICT_TYPE_READONLY` | 403 | 内置字典类型不可删/不可改编码，或该类型项对租户只读 |
| `SYS_DICT_TYPE_IN_USE` | 409 | 字典类型下仍有项，不允许删除 |
| `SYS_DICT_ITEM_NOT_FOUND` | 404 | 字典项不存在（含跨租户不可见） |
| `SYS_DICT_ITEM_CODE_CONFLICT` | 409 | 同一类型下该 code 已存在（uk_dict_item） |
| `SYS_DICT_ITEM_HAS_CHILDREN` | 409 | 字典项仍有子项，不允许删除 |
| `SYS_DICT_PARENT_NOT_FOUND` | 400 | 父项编码在同类型下不存在 |
| `SYS_DICT_HIERARCHY_NOT_FOUND` | 404 | 层级关系不存在（该项尚未挂到指定层级视图） |
| `SYS_DICT_PARENT_NOT_ALLOWED` | 400 | 非 CASCADE 类值域不允许设置父项 |
| `SYS_DICT_CYCLE_DETECTED` | 409 | 移动后会出现父子环 |
| `SYS_DICT_CASCADE_DEPTH_EXCEEDED` | 400 | 层级深度超过该值域 maxDepth |
| `SYS_DICT_CODE_INVALID` | 400 | 字典编码非法（空值/超长/含空白） |
| `SYS_DICT_CATEGORY_INVALID` | 400 | 值域分类非法（非 ENUM/LIST/CASCADE） |
| `SYS_DICT_ENUM_CLASS_INVALID` | 400 | 绑定枚举类不可用（未绑定/不存在/非枚举/非白名单包） |
| `SYS_DICT_IMPORT_TOO_LARGE` | 400 | 导入条目数超过单批上限 |
| `SYS_DICT_VALUE_INVALID` | 400 | 值不符合值域定义（数据类型或格式正则不匹配） |
| `SYS_DICT_EFFECTIVE_RANGE_INVALID` | 400 | 生效区间非法（开始时间晚于结束时间） |

### GEN_

| errorCode | HTTP | 说明 |
|-----------|------|------|

### BP_

| errorCode | HTTP | 说明 |
|-----------|------|------|
| `BP_ORDER_NOT_FOUND` | 404 | 订单不存在（含跨租户不可见） |
| `BP_ORDER_STATUS_CONFLICT` | 409 | 订单当前状态不允许该操作 |
| `BP_ORDER_STATUS_INVALID` | 400 | 订单状态查询入参非法 |
| `BP_ORDER_STOCK_INSUFFICIENT` | 409 | 下单商品库存不足（同步预校验失败） |
| `BP_PAYMENT_NOT_FOUND` | 404 | 支付单不存在（含跨租户不可见） |
| `BP_PAYMENT_STATUS_CONFLICT` | 409 | 支付单当前状态不允许该操作 |
| `BP_PAYMENT_SIGNATURE_INVALID` | 401 | 渠道回调签名校验失败（不可信调用方） |
| `BP_PAYMENT_CHANNEL_PREPAY_FAILED` | 502 | 渠道预下单失败（上游依赖故障） |

> **样板实现范围**：码常量在 `com.bone.blueprint.common.BlueprintErrorCodes`（只承载稳定码字符串与语义）；
> **「码 → HTTP 状态」的唯一真源是 `com.bone.blueprint.common.BlueprintErrors` 的 `DEFAULT_HTTP_STATUS` 表**（与本表逐行对应），
> 抛出方走 `BlueprintErrors.of(码, 上下文)`（或 `orElseThrow` 用的 `BlueprintErrors.supplier(码, 上下文)`），
> **不在抛出点手写状态数字**。新增码若忘记登记状态，`BlueprintErrors` 类加载即抛 `IllegalStateException`（fail fast）。
>
> **为何要收成一张表**：`BizException` 的首参是 HTTP 状态、业务码只能拼进 message，两者天然是两份数据。散在各抛出点手写
> `new BizException(HTTP 状态, 码 + ": " + 说明)` 时，任一处不一致都无机制发现。`BizException(String)` 的默认码是 **500**，
> 用它等于把「查不到 / 状态冲突 / 参数错」都报成服务端故障（本项目已据此修正 404、400 与库存不足 500 三处误报）。

---

## 7. HTTP 与业务码对照（速查）

| 场景 | HTTP | 推荐 errorCode |
|------|------|----------------|
| 参数缺失/格式错误 | 400 | `COMMON_VALIDATION_FAILED` + `errors[]` |
| 未登录 | 401 | `COMMON_UNAUTHORIZED` / `IAM_*` |
| 无权限 | 403 | `COMMON_FORBIDDEN`（平台统一）/ 域内语义码 |
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
| 2026-09-19 | 重写 §6 `IAM_` 台账（原清单为未实现的理想码，与代码双向不一致）；同步 §3.1 示例与 §7 速查中的失效码 |
| 2026-09-20 | §4 按实际实现形态改写（原枚举形态全仓零实现 → 常量类 + 状态表 + fail-fast），§5 流程同步；§6 `IAM_` 新增 6 码、删除 `IAM_PASSWORD_MUST_CHANGE`，并记录 application 层裸异常收敛 |
| 2026-09-21 | §6 `MD_` 台账补齐 10 码（`MD_ENTITY_ID_REQUIRED`/`MD_ENTITY_NAME_DUPLICATE`/`MD_FIELD_NAME_DUPLICATE`/`MD_DATA_STANDARD_DUPLICATE`/`MD_DATA_STANDARD_NOT_FOUND`/`MD_EXPORT_SERIALIZE_FAILED`/`MD_FILE_EMPTY`/`MD_FILE_FORMAT_INVALID`/`MD_FILE_READ_FAILED`/`MD_FILE_PARSE_FAILED`）；对应 `bone-masterdata` 实现 `MasterDataErrors` 状态表 + 收敛 application/adapter 层裸 `IllegalArgumentException`/`IllegalStateException`/`BizException.of(String)` 静默 500 为显式状态码 |
