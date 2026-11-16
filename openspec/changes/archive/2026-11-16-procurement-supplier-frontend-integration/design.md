---
comet_change: procurement-supplier-frontend-integration
role: technical-design
canonical_spec: openspec
---

# Design

## Context

见 proposal.md 的 Why。补充设计约束：后端 `SupplierController` 在 `bone.procurement.supplier-mdm.enabled=true` 时注册 5 个端点（`/api/v1/masterdata/suppliers`），一律 `ApiResponse<T>`；`PageResult.getList()` 带 `@JsonIgnore`，JSON 权威字段为 `records`，`total` 为后端 Long→String 序列化。`bone-masterdata-app` 已有 `createApiClient` + `normalizePage` 范式（见 `services/api.ts`），已处理 records/total 与 Long→String；后端生产持久化端口只有测试内存实现（`SupplierHttpTestConfiguration`），因此真实联调只能在"能力开关打开 + 测试端口装配"的运行形态下进行。前端页面必须在不越过后端守卫的前提下工作。

## Goals / Non-Goals

**Goals:**

- 前端 API 客户端与页面严格消费既有后端契约，不改变后端签名。
- 用自动化契约测试锁住请求/响应形状；用真实 HTTP 联调证明全链路可用。
- 对能力未启用、权限不足、顶层失败给出可理解降级，PII 不落地前端。

**Non-Goals:**

- 不建 Maven 模块、不写 DDL、不接生产库、不改后端领域规则或已批准裁决。
- 不改 CI 三入口判据集合、不改 procuraAI 文档族真源。
- 不实现生产 JDBC adapter 与真实跨域上游；不在前端重做守卫裁决。

## Decisions

### D1 — 前端落在 bone-masterdata-app，复用既有 apiClient 范式

**决策**：新增 `services/supplierApi.ts` 使用 `createApiClient('')`，路径常量 `/api/v1/masterdata/suppliers`；分页复用与 `normalizePage` 相同的 records/total 归一逻辑（长整型 total 用 `normalizeTotal`）。
**理由**：与同应用其他页面同一基座、同一 dev proxy，避免新子应用与 qiankun 注册成本。
**备选**：新建采购子应用 → 增加路由/端口/注册面且无业务必要，否决。

### D2 — 状态机事件在前端只做"允许集合的展示"，裁决仍在后端

**决策**：页面从后端返回的当前状态推导该状态可显示的事件按钮集合（只读投影，例如 `ONBOARDING` 显示 `onboarding_approved`），点击即发请求；所有前置条件（闸门、变更请求、未结 PO 等）一律由后端守卫裁决，前端不内置规则表、不隐藏后端会拒绝的合法尝试之外的逻辑。
**理由**：避免"前端规则表"与后端迁移表分叉（A-10 已论证两份规则易漂移）；前端 button 只是可读入口。
**备选**：前端复制完整迁移表 → 规则双份，否决。

### D3 — 统一响应以 success/message 顶层字段为准

**决策**：所有写操作与读操作先判 `success`，`success=false` 一律按 `message` 呈现失败，不因 HTTP 2xx 判成功；分页只消费 `records`，`total` 用 `normalizeTotal` 归一。
**理由**：与 `claim/web-response-contract` 能力和现有 `api.ts` 注释一致，避免 @JsonIgnore 后 `list` 字段恒 undefined 的假绿。

### D4 — 能力未启用 / 权限不足的可观察降级

**决策**：请求失败时按状态码与响应前缀分类：404/未注册路径 → "供应商能力未启用"；403 → "无权限"；其余 → 后端 `message`。详情/列表失败各展示对应空态与提示，写按钮在能力未启用时不渲染。
**理由**：后端 fail-closed 默认关闭端点，前端必须让这一安全决策可见，而不是空白页误导用户。

### D5 — PII 与未知码呈现边界

**决策**：页面只消费 DTO 中的状态、版本、摘要、裁决字段；不渲染、不写入 DOM 任何身份主键/电话/法人名字段。未知状态码与格式异常摘要显示原始字符串。
**理由**：与后端 spec "不回传字段原值或 PII" 保持同一边界；前端是最后一公里，不能因意外多字段破坏 PII 契约。

### D6 — 联调脚本复用测试端口装配，不接生产

**决策**：联调以 `mvn -o -f bone-platform/bone-masterdata/pom.xml test -Dtest=SupplierHttpIntegrationTest` 的真实 HTTP 测试为后端事实源，前端以 vitest 契约测试消费同一请求/响应形状；不做浏览器跨真实网络长链路（本机无生产端口装配，接生产属 R-2）。
**理由**：既有 `SupplierHttpIntegrationTest` 已是完整 Spring Boot + RANDOM_PORT + JWT 的真实 HTTP 联调，新增脚本重复自造等价物会引入假证据。本 change 的"前后端联调"以契约一致 + 真实后端 HTTP + 前端测试三者交叉为证据。

## Risks / Trade-offs

- **前端联调非浏览器直连后端** → 以"契约测试 + 后端真实 HTTP 测试 + 前端渲染测试"三层交叉替代；不宣称浏览器跨网真联调（D6）。
- **能力开关默认关闭** → 前端需降级提示，demean 上像"功能不可用"；这是正确 fail-closed 呈现，非缺陷。
- **事件按钮集合是只读投影** → 可能与后端允许集合不完全一致；以后端拒绝信息为准，前端不声称决定合法性。
- **PII 字段若后端误加** → 前端白名单过滤；测试以"意外字段不出现在 DOM"锁定。

## Migration Plan

- 纯前端新增能力，无数据迁移。回滚：删除新增前端文件与路由/菜单项即可，后端无变化。
- 上线前置：owner 完成 R-2（DDL + 生产 adapter）后开启后端能力开关，前端才从"未启用空态"转为可操作。

## Open Questions

无。范围与红线已由用户在 Open 前拍板；生产 adapter/DDL 属已裁决的 owner 前置，不在本 change 内。
