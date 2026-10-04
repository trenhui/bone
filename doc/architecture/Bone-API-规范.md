# Bone 平台 API 规范
> **与 DDD 规范的关系**：本文定义 HTTP/OpenAPI 层的契约规范，与 `Bone-DDD-最终实践方案.md` 共同构成 Bone 架构双真源。两个文档的语义重叠通过 DDD 规范的 HC 硬约束保持对齐：
>
> | API 规范内容 | DDD 规范对应 | 执行载体 |
> |-------------|-------------|---------|
> | Controller 返回 `ApiResponse<T>` / `PageResult<T>` | HC-003 | **无机器载体（Planned）**：`controllerMustReturnApiResponse` 不在共享规则库；本规范定义目标态。状态真源见 [Bone-DDD-最终实践方案 §G-1.7](./Bone-DDD-最终实践方案.md)，勿在此复述 |
> | API 设计态与实现态一致性 | HC-007 | oasdiff（CI）+ OpenAPI spec（`openapi/`） |
> | 请求 DTO 不得直传 domain 类型 | E-4.1 / E-4.2 | adapter 层 Assembler + ArchUnit 包依赖方向 |
> | 多租户头 `X-Tenant-Id` 传递链 | E-10.2 / `TenantProvider` | Spring 过滤器 + TenantContext |
>
> API 规范独有的条目（URL 风格、HTTP 方法选择、分页参数、LRO、幂等键等）不重复出现在 DDD 规范，也不受 ArchUnit 硬执行，属于 Engineering Advisory 层级。

> **文档性质**：对外 **HTTP REST** 契约（URL、信封、分页、横切头、OpenAPI、契约测试）。  
> **更新**：2026-05-20  
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
| **适用** | `bone-platform/*`、`bone-engine/*` 对外 HTTP API；`bone-frontend` 各微应用；网关与 **Spring Security + JWT** 鉴权配置（平台默认栈；SA-Token **非**默认栈，仅限可选行业包 `bone-business/*`）。 |
| **真源优先级** | 本规范 > 模块详设 API 章 > 代码实现；偏离须 ADR 或 §13.2「迁移登记」。 |
| **OpenAPI** | 每服务维护 OpenAPI 3.1；公共 schema 见 §11；CI 做破坏性 diff。 |
| **实现符合度** | 本规范为**目标态**；`bone-core` 双 `ApiResponse`、`code=0` 成功码、分页入参 `pageNum`/`pageSize` 族等见 §13.2，逐步迁移。 |

### 1.1 参考标准

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
| `iam` | bone-iam | `IAM_` | 认证、用户、角色、权限；OpenAPI：[iam-v1.yaml](./openapi/iam-v1.yaml)（骨架，持续补全） |
| `metadata` | 元数据（catalog + 扩展字段 `fields:*`，统一 `/api/v1/metadata/**`） | `META_` | 与 [元数据能力对照](../design/modules/元数据能力-实现映射与竞品对照.md) 一致 |
| `runtime` | bone-metadata-server（模式 B 动态 CRUD） | `META_RUNTIME_` | OpenAPI：[metadata-runtime-v1.yaml](./openapi/metadata-runtime-v1.yaml) |
| `generator` | studio-generator | `GEN_` | 代码生成；OpenAPI：[generator-v1.yaml](./openapi/generator-v1.yaml) |
| `masterdata` | bone-masterdata | `MD_` | 主数据、质量；OpenAPI：[masterdata-v1.yaml](./openapi/masterdata-v1.yaml) |
| `extension` | bone-extension-studio | `EXT_` | 扩展点、插件 |
| `integration` | bone-integration | `INT_` | 连接器、流程 |
| `system` | bone-system | `SYS_` | 配置、告警 |
| `console` | bone-system（聚合读） | `SYS_` | **只读** BFF；OpenAPI：[console-v1.yaml](./openapi/console-v1.yaml) |

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
| `code` | **等于 HTTP 状态码**（成功为对应 2xx：**创建 `201`**，其余 `200`；历史 `0` 见 §13.2 废弃） |
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

> 不再推荐依赖 `code === 0`；兼容层设 **2026-09-01** 移除（见 **§13.2**）。

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

### 5.1 入参命名：新增端点只允许 `page`/`size`（Offset 族）

**规定**：所有Offset 分页的新增端点，入参**必须**用 `page`/`size`。
`pageNum`/`pageSize` **不再是可选项**，存量收敛完成即禁止。

**`page` 的基数（必须显式声明，避免 0-based / 1-based 歧义）**：`page` 为 **1-based**（首页 = 1）。注意与 Spring Data `Pageable#getPageNumber()`（**0-based**）区分，从 `Pageable` 装配 `PageResult` 时须做 **`+1`** 转换；`PageResult.empty()` 与 `cursorOf()` 均以 1 为基。若把 0-based 直接塞入，会产出 `page=0`，前端将显示「第 0 页」。

**为什么写死这一条**（2026-10-02 E2E 实测）：Spring 对多余的 query 参数**静默忽略**并回落默认值，
不报错、不告警。前端传 `pageSize=1` 而后端字段是 `size` 时，请求照样 200，只是 `size` 回显后端默认值 100。
于是「只拉 1 条」实际拉回 100 条、`pageNum>1` 翻页静默回到第 1 页——
**弱断言（只看 200）永远测不出这类缺陷**，本轮实测正是如此。

#### 存量现状（2026-10-02 实测，禁止误读为「规范已落地」）

| 命名族 | 对外入参类数 | 状态 |
|---|---:|---|
| `page`/`size` | 8 | ✅ 符合本规范 |
| `pageNum`/`pageSize` | 22 |🟡 存量待收敛（L3，见下） |
| `cursor`/`limit` | 2 | ✅ 语义不同，不算违规 |

> **统计口径（2026-10-03 复核）**：数字与`scripts/check-paging-param-names.py` 的实测一致，
> 口径是「**按字段声明归族**」（`private int page` / `private Integer page` /继承 `PageParam`
> 均计入），**不是**按 `Integer` 单词匹配。
> 手工核对时若只grep `private Integer page;`，会漏掉用 `int` 声明的类（本轮实测漏了 6 个：
> `LoadCatalogTablesQuery` / `ModuleListQuery` / `DataSourceListQuery` / `AccountPageQuery` /
> `TenantPageQuery` / `ApplicationPageQuery`），从而把 8 误算成 2 —— 曾在 AI 侧造成过一次
> 「规范数字漂移」的误判。以门禁输出为准。

其中 **7 组是「web 层 `PageReq` + application 层 `PageQuery`双层字段重复**，
靠 MapStruct Assembler 逐字段拷贝转换：

```
AlertRecord / AlertRule / Config / DictItem / DictType / Log / ScheduleTask
```

**改字段名的实际成本**：每个端点要同时动**两层类 + Assembler**，
而非按「query 类个数」线性估算。

#### 机器门禁

```bash
python3 scripts/check-paging-param-names.py          # 门禁本体（已阻断第三套命名）
python3 scripts/check-paging-param-names.py --report-only   # 存量清零前只看存量清单
```

- 🔴 **阻断**：出现白名单外的分页字段名（`pageIndex`/`perPage` 等**第三套命名**）、同一类里跨族字段混搭；
- 🟡 **警告**：22 个 `pageNum`/`pageSize` 存量类（不阻断，按§5.2 节奏清零）。

**接入状态（2026-10-03 补齐）**：本门禁此前**完全游离于所有门禁之外**（`check.sh` / `ci-check.sh` / `.github/workflows` 均未调用），
是§5.1「新增端点只允许 `page`/`size`」长期未被机械执行的**根因**。现已接入 `scripts/check.sh` `[7/10]`（pre-commit 阻断，
覆盖本地提交与 AI 代提交两条路径）与 `scripts/ci-check.sh` `[11/12]`（CI 侧，覆盖绕过本地提交的变更）。
负向验证已确认：注入 `pageIndex`/`perPage` 的探针类会被判 `FAIL`、退出码 1（两条路径均验过）。

**豁免范围**：`bone-metadata-sdk` 内部的 `FluentQuery.page(int pageNum, int pageSize)`、
`Criteria.page()` 是 **SDK 公共 API 命名**，不属于对外入参，不受本节约束。
改SDK 命名会波及全部持久化调用方，是独立议题。

### 5.2 存量收敛路线（路线 ② 已于 2026-10-03 落地并接入 CI；① 保留为独立议题）

两条路线互斥：

| 路线 | 动作 | 成本 | 风险 |
|---|---|---|---|
| **① 改后端** | 22 个入参类改名 + Assembler 同步 + 回归全部前端调用方 | 高（双层×22 + 回归） | 漏改任一调用方即静默失效 |
| **② 改规范 + 门禁** ✅已落地 | 承认两族并存，本节只约束新增端点；用 §5.1 门禁防住第三套 | 低 | 存量继续存在，但**不再扩散** |

**裁定（2026-10-03，用户确认「按最推荐方案执行」→ 采用路线 ②）**：走 ② 止血，① 作为后续独立议题。

**② 的落地要求是「门禁在所有提交路径上生效」，仅此而已是不够的**——
若门禁只挂在 pre-commit，绕过本地提交路径的变更（CI 直推、协作者未跑 `check.sh`）
仍能引入第三套命名，等于没设防。故路线 ② 补齐了 CI 侧：

| 执行路径 | 是否调用 `check-paging-param-names.py` | 登记日期 |
|---|---|---|
| `scripts/check.sh` `[7/10]`（pre-commit，本地与 AI 代提交） | ✅ | 2026-10-03 |
| `scripts/ci-check.sh` `[11/12]`（CI 全量门禁） | ✅ **本轮补齐** | 2026-10-03 |
| `.github/workflows/ci.yml` | 经 `ci-check.sh` 间接覆盖 | — |

负向验证已确认：注入 `pageIndex` 的探针类在两条路径上均被判 `FAIL`、退出码 1；
清理后回到 `PASS`。

若后续选①，务必以本节门禁 + `scripts/e2e/chains.py` 的分页断言做回归护栏。

### 5.3 响应字段：`records` 是唯一权威当前页字段

§3.3 已规定 `records` 为权威字段、`list` 为待淘汰过渡字段。**实测补充（2026-10-02）**：

`bone-core`的 `PageResult` 有 3 个 `@Deprecated` 废弃 getter（`getList()` / `getPageNum()` / `getPageSize()`）。
**Jackson 默认不因 `@Deprecated` 忽略它们**，故序列化时两套都吐。实测
`GET /api/v1/iam/audit/logs` 响应键为：

```
['empty','hasNext','hasPrevious','list','nextCursor','offset','page',
 'pageNum','pageSize','pages','recordCount','records','size','total']
```

14 个键里 `list`/`records`、`page`/`pageNum`、`size`/`pageSize` 三组语义重复。

**`total` 为字符串的根因（2026-10-03 定位，非配置漂移）**：

`PageResult.total` 声明为 `private Long total;`（`com.bone.core.model.PageResult:19`），而
`bone-metadata-sdk` 的 `MetadataAutoConfiguration.boneLongToStringCustomizer()` 注册了
**全平台兜底**的 `serializerByType(Long.class, ToStringSerializer.instance)`
（另含 `Long.TYPE`）。该定制器是**为保护雪花 ID 精度**而设（18~19 位 long 超出JS Number 安全上限
2^53，以 JSON number 返回会静默截断末位），所有应用都 `@Import` 本配置，因此成为唯一落点。

⇒ **`Long` 字段一律序列化为字符串**，`Integer` 字段不受影响。实测对照：

| 字段 | Java 类型 | 运行时 JSON 类型 |
|---|---|---|
| `total` | `Long` | **string**（`'1055'`） |
| `page` / `size` / `pageNum` / `pageSize` / `pages` | `Integer` | number |
| `hasNext` / `hasPrevious` / `empty` | `Boolean` | boolean |

**架构裁定（2026-10-03）：`total` 后端保持 `java.lang.Long`，不动全局序列化器。**
改它会连带削弱雪花 ID 的精度保护（`Long` 一旦放开，18~19 位 ID 立刻出现静默截断），
且属跨全平台的行为变更，收益（让一个计数变成 JSON number）远小于风险。

**前端约定**：

1. `shared-types` 的 `PageResult.total` 声明为 `string | number`，如实反映运行期。
2. 消费点须在**收敛点**用 `normalizeTotal()`（`@bone/shared-utils` 的 `paging.ts`）归一为
   `number`，不得把字符串 `total` 送入 `useState<number>` / 算术 / 比较。
3. 归一后的类型用 `NormalizedPageResult`（`total: number`）表达，让「已归一」在类型上可见。
4. **`Number()` 对分页 `total` 安全，对雪花 `id` 绝对禁止** —— 两者不可混同处理：
   `total` 是行数计数（远小于 2^53），`id` 是标识符（18~19 位超上限）。
5. 门禁：`scripts/check-paging-total-normalize.py`（`check.sh` [9/9]）扫描 R3-1~R3-4 四类
   漏点（setState / number 字段赋值 / antd `value` / 参与算术）。TypeScript 抓不到
   `<Statistic value={total}>` 与中间变量两类，需依赖该门禁。

`records`/`page`/`size` 为权威字段；消费 `list`/`pageNum`/`pageSize` 的代码属技术债，
在 `@JsonIgnore` 收敛（全量影响 8 个子应用与后端契约）落地前不得新增使用点。

#### `list` / `pageNum` / `pageSize` 已收敛为 `@JsonIgnore`（2026-10-03）

**处置**：给 `PageResult` 的 3 个废弃 getter 加 `@JsonIgnore`，响应不再输出 `list`/`pageNum`/`pageSize`，
只保留权威的 `records`/`page`/`size`/`total`/`pages`/`hasNext`/`hasPrevious`/`nextCursor`。

**顺序不可颠倒**（这是本节最容易被后人踩空的地方）：

| 步骤 | 内容 | 若跳过的后果 |
|---|---|---|
| ① 先改前端 | 全部消费点从 `data.list` 改为 `data.records` | 直接做② ⇒读 `list` 的页面**白屏**，且 TypeScript 不报错（`shared-types` 早已不声明 `list`，但存量编译产物仍在） |
| ② 再动后端 | 加 `@JsonIgnore` | — |
| ③ 最后接门禁 | `scripts/check-paging-current-field.py`（`check.sh` `[14/17]`） | 前端回退读 `list` 无人拦截，等于白做 |

**保留方法本身、不删**：`getList()`/`getPageNum()`/`getPageSize()` 是 `PageResult` 公共 API，
删掉破坏二进制兼容（`map()` 等内部方法依赖），SDK/第三方亦可能调用。`@JsonIgnore` 只切断序列化出口。

**类型侧同步**：`shared-types` 的 `PageResult` 已把 `records` 设为唯一必填字段
（`list` 从类型上消失）；`NormalizedPageResult` 同步改名；`PageResultIamCompat.list`
保留为 `@deprecated` 过渡读取。

**豁免**：`packages/shared-services/src/configService.ts` 自声明了
`ApiResponse<{ list: SystemConfig[] }>` —— 该端点的响应契约由前端自己定义，不经 `bone-core PageResult`，
故 `list` 是其唯一契约，门禁按`ApiResponse<{ list:` 判据整文件豁免。

**⚠️ 不要把 `@JsonAlias` 当成删除废弃 getter 的安全网（2026-10-03 实测）**：

`PageResult` 的 `records`/`page`/`size` 字段上**曾**挂有 `@JsonAlias("list")`/`("pageNum")`/`("pageSize")`，
容易让人以为「废弃 getter 删掉后，入参仍能用旧键」。**实测该别名从未生效过**（已于 2026-10-04 移除）：

- `PageResult` 只有私有全参构造，无无参构造也无 `@JsonCreator` ⇒ Jackson **无法反序列化本类**，
  实跑抛 `InvalidDefinitionException: no Creators, like default constructor, exist`
  （独立 `javac` + jackson-databind 2.20.0 验证，非Maven 环境问题；对照实验已排除 classpath 因素）。
- 全仓无任何反序列化入口：无 `@RequestBody PageResult`、无 `readValue(..., PageResult.class)`。
  `PageResult` 是**纯出站响应模型**。
- 因此「响应里有 `list` 键」的原因与 `@JsonAlias` **无关**，纯粹是 `@Deprecated` 的 `getList()`
  被 Jackson 照常序列化（Jackson **默认不因 `@Deprecated` 忽略 getter**）。

现状由 `PageResultTest.pageResultIsNotDeserializable` 锁住：若将来给本类加无参构造器或
`@JsonCreator`，该测试会失败并提示「本类开始可被反序列化，需重新评估入参侧契约」。

**仍未收敛**：`getOffset()` / `getRecordCount()` 两个派生计算属性仍会被Jackson 序列化
（`offset`/`recordCount` 键）。二者全仓零消费点，但它们不是 `@Deprecated`，
属便捷方法而非兼容层，删/留属独立议题，本轮不动。

---

## 6. 横切头

### 6.1 请求头

| 头 | 必填 | 说明 |
|----|------|------|
| `Authorization` | 除匿名接口外 | `Bearer {jwt}` |
| `Content-Type` | 有 body 时 | `application/json; charset=utf-8` |
| `Accept` | 推荐 | `application/json` |
| `X-Request-Id` | 推荐 | 客户端生成 UUID；无则网关生成 |
| `X-Tenant-Id` | 多租户 | 网关注入（**仅透传/对齐用**）；租户身份**权威来源为已校验 JWT 的 claim**（见 §9.2），与 JWT 不符的请求须拒绝；**不得**仅靠 body 覆盖 |
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

**限流策略**（网关统一执行，模块不各自实现）：

| 维度 | 说明 |
|------|----------|
| 维度键 | `tenantId` + 端点（按租户隔离配额，避免单租户耗尽全局额度） |
| 阈值 | **由网关配置定义**（`bone-gateway` 的 resilience4j 限流器），本规范**不**硬编码具体 QPS，避免与配置漂移 |
| 超限响应 | `429` + `Retry-After` + `RateLimit-Limit` / `RateLimit-Remaining` / `RateLimit-Reset` |
| 错误码 | `COMMON_RATE_LIMITED`（见 [Bone-错误码登记](./Bone-错误码登记.md)） |
| 登录端点 | 须额外按 **IP + 账号**做防爆破，配额独立于普通端点 |

- 实现载体：网关 `spring-cloud-starter-circuitbreaker-reactor-resilience4j` + Redis 响应式限流；平台**未**引入 Sentinel，勿按 Sentinel 配置。
- 客户端 `X-Tenant-Id` 不参与配额维度（租户身份以 JWT 为准，见 §5、§9.2）。

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

### 8.1 模块横切约定索引（详设 §5.0）

各模块详设中的 **§5.0 / §5.A 横切表** 为本规范的**实现检查单**；实现须满足 §6–§8，不得仅在详设重复叙述而偏离平台契约。

| 模块 | 详设横切节 | 强制头 / 行为 | 错误码前缀 |
|------|------------|---------------|------------|
| 元数据 | [§5.A](../design/modules/2.%20元数据管理模块详细设计方案.md#5a-横切约定对齐扩展-71) | `Idempotency-Key`（publish/allocate）；`If-Match`（catalog/runtime） | `META_` |
| 主数据 | [§5.0](../design/modules/3.%20主数据管理模块详细设计方案.md#50-横切约定对齐扩展-71) | 同上；导入/质量检查幂等；LRO **[Target]** | `MD_` |
| 集成 | [§5.0](../design/modules/4.%20集成管理模块详细设计方案.md#50-横切约定对齐扩展-71) | `POST /executions` 幂等；LRO **[Target]**（INT-11） | `INT_` |
| 扩展 | [§7.1](../design/modules/5.%20扩展管理模块详细设计方案.md#71-横切约定) | 201+Location；`:deploy` 202+LRO；cursor 分页 | `EXT_` |
| 代码生成 | [§5](../design/modules/8.Studio%20Generator%20详细设计方案.md#5-api-设计) + §0 LRO | `POST /code-generation` 202 + `/operations/{id}` | `GEN_` |
| IAM / 系统 / 控制台 | 各文 §5 | 读接口无幂等要求；写接口按上表扩展 | `IAM_` / `SYS_` |

**可观测性**：SLI 与 PromQL 见 [Bone-可观测性规范.md](./Bone-可观测性规范.md) §4.2.1。

---

## 9. 安全与权限

### 9.1 认证与租户

1. 默认需认证；白名单：`/api/v1/iam/login`、健康检查、文档（生产需鉴权）。  
2. `tenant_id` / `biz_identity_code` **权威来源为已校验 JWT 的 claim**，解析后写入 `TenantContext`；客户端传入的 `X-Tenant-Id` 仅作透传，服务端以 JWT 为准，与 JWT 不符的请求直接拒绝（防跨租户越权）。  
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

Controller 使用 `@PreAuthorize`（Spring Security）注解，**权限码与 Scope 一致**。SA-Token 非平台默认栈（全仓无该依赖），仅限可选行业包 `bone-business/*`，勿写入平台 API 契约。

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
        Long id = createHandler.execute(extPointAssembler.toCreateCommand(req));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(id));
    }
}
```

| 规则 | 说明 |
|------|------|
| 包路径 | `adapter.web.controller` |
| 入参 | `*Req` / `*Qry`（**adapter 层** REST 入参 DTO，见 [DDD 命名风格](./Bone-DDD-最终实践方案.md#naming-style)）；`@Valid` |
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
| POST | `/api/v1/generator/data-sources/{id}:test-connection` | 连接测试 |
| GET | `/api/v1/generator/data-sources/{id}/tables` | 物理库表发现（JDBC） |
| GET | `/api/v1/generator/data-sources/{id}/synced-tables` | 已同步表（`gen_table_metadata`） |
| POST | `/api/v1/generator/data-sources/{id}/tables:sync` | 同步表结构到 `gen_*`（body 可选 `tableNames`） |
| GET | `/api/v1/generator/metadata-entity-snapshots` | 已发布 `meta_*` 快照分页（`CATALOG_SNAPSHOT` 选型） |
| GET/POST | `/api/v1/generator/templates` | 模板 CRUD；`POST …/{id}:publish` 发布 |
| POST | `/api/v1/generator/code-generation` | Freemarker 异步生成（As-Is） |
| POST | `/api/v1/generator/generation-tasks` | 同步字符串模板生成 |
| GET | `/api/v1/generator/capabilities` | AI 能力发现 |

### 13.2 废弃与迁移

| 状态 | 旧 | 新 | 截止 |
|------|----|----|------|
| 已移除 | `/api/ext-points`、`/api/extensions`、`/api/extension/*` | `/api/v1/extension/*` | — |
| 已移除 | `/api/data-sources` | `/api/v1/generator/data-sources` | — |
| 已移除 | `/api/table-metadata` | `/api/v1/generator/table-metadata` | — |
| 已移除 | `/api/code-templates` | `/api/v1/generator/templates` | — |
| 已移除 | `/api/v1/generator/code-templates` | `/api/v1/generator/templates` | — |
| 已移除 | `data-sources/{id}/test` | `data-sources/{id}:test-connection` | — |
| 已移除 | `/api/code-generation` | `/api/v1/generator/code-generation` | — |
| 已移除 | `/api/code-generator` | `/api/v1/generator/*`（见 §13.1.1） | — |
| 已移除 | `/api/v1/generator/generate`、`/tables/{id}`、`/catalog/entities` | `generation-tasks`、`data-sources/{id}/tables`、`metadata-entity-snapshots` | — |
| 已移除 | `/api/v1/generator/table-metadata/*` | `data-sources/{id}/tables`、`tables:sync` | — |
| 过渡 | `/api/iam/users`（详设用语） | `/api/v1/iam/accounts`（As-Is Controller） | 2026-12-01 |
| 已移除 | `/api/iam/*` | `/api/v1/iam/*` | — |
| 已移除 | `/api/masterdata/*` | `/api/v1/masterdata/*` | — |
| 已移除 | `/api/system/*`、`/api/console/*` | `/api/v1/system/*`、`/api/v1/console/*` | — |
| 已移除 | `/api/integration/*`（`context-path=/api`） | `/api/v1/integration/*` | — |
| 已移除 | `/v1/metadata/*`（扩展字段 API，无 `/api` 前缀） | `/api/v1/metadata/*` | — |
| 过渡 | `ApiResponse` 无 `code` / 本地类 | `com.bone.core.model.ApiResponse` | 2026-09-01 |
| 过渡 | 成功 `code=0` | `code=200` | 2026-09-01 |
| 过渡 | 分页 `list` 字段 | `records` | 2026-09-01 |
| 过渡 | HTTP 200 + `success:false` | HTTP 4xx/5xx | 2026-09-01 |
| 过渡 | 分页入参 `pageNum`/`pageSize`（**22 个入参类**） | `page`/`size`（见 §5.1，含 7 组 web/application 双层字段重复） | **待架构师裁定** |
| 过渡 | `PageResult` 额外吐 `list`/`pageNum`/`pageSize`（`@Deprecated` getter 未被 Jackson 忽略） | 仅 `records`/`page`/`size`（见 §5.3，全量影响 8 个子应用契约） | **待架构师裁定** |

> **说明**：**As-Is** 含扩展字段 `fields:search|searchByNames|allocate|health`（**动作式** `fields:*`）及 **catalog** `entities`、`…/entities/{entityId}/fields`、`…/relationships`；**模式 B** 动态数据 `/api/v1/runtime/entities/{entityCode}/records`（`delivery_mode=RUNTIME` 且已发布）。**禁止** catalog 与扩展字段 `fields:*` 共用顶层 `…/fields`，见 [元数据能力对照](../design/modules/元数据能力-实现映射与竞品对照.md) §1.2。

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
| 2026-05-17 | §13.2：catalog 字段嵌套路径，与扩展字段 `fields:*` 区分 |
| 2026-05-20 | §8.1 模块横切约定索引；链到详设 §5.0 与可观测性 §4.2.1 |
| 2026-05-17 | 错误码、日志拆至独立文档；§16 增补规范体系建议 |
| 2026-05-17 | 实现安全/可观测性/消息等独立规范；§16 改为规范索引 |
