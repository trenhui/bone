# Brainstorm Summary — procurement-supplier-mdm-slice

- Change: procurement-supplier-mdm-slice
- Date: 2026-11-15
- Phase: design
- 状态：候选方案（用户已确认 2 项方向性裁决，其余为本 Agent 的技术判断，随 Design Wait 一并确认）

## 一、已由用户确认的裁决（不可再作为开放问题）

### 裁决 1 — 角色语义对齐 LLD 真源

| 角色 | 修正前（Open 时 spec） | 修正后（LLD 真源） |
|---|---|---|
| Supplier | 合同与付款对象 | **合作资格**（寻源 → 准入 → 绩效 → 风险 → 退出） |
| Vendor | 货源/履约主体 | **交易与付款资格**（财务侧 Company Code data：银行/税务/付款条件/冻结） |

依据：`doc/ai-product/procuraAI/detailed-design/01-供应商主数据模块详细设计.md:22`（"合作资格（Supplier）"与"交易/付款资格（Vendor）"两套互不覆盖的状态机）；`doc/ai-product/procuraAI/采购领域架构设计方案.md:109`（Vendor 承载财务侧 Company Code data，Owner = 财务）；同文件 `:223`（两套状态机并行、互不覆盖）。

反向后果（为何必须改）：按旧 spec 实现会把「付款对象」挂到 Supplier，与财务侧 Vendor/Company Code 既有分工相反，后续接财务发票校验与 `VendorActivation` 闸门时必然返工。

### 裁决 2 — 两套状态机均按 LLD 真源实现

**Supplier 生命周期**（`01 卷 §5`）

`PROSPECT → ONBOARDING → ACTIVE ⇄ SUSPENDED / CORRECTIVE → EXITED`

| From | Event | Guard | To |
|---|---|---|---|
| `PROSPECT` | `onboarding_submitted` | 背调 + 资质齐备 | `ONBOARDING` |
| `ONBOARDING` | `onboarding_approved` | 变更请求全段批准 + 至少 1 个 `ACTIVE_VENDOR` | `ACTIVE` |
| `ACTIVE` | `risk_exceeded` | 风险事件 / 绩效不达标 | `SUSPENDED` 或 `CORRECTIVE` |
| `SUSPENDED` | `restored` | 整改复核通过 + 审批 | `ACTIVE` |
| `ACTIVE` / `CORRECTIVE` | `exit_approved` | 零未结 PO + 未结合同 + 未清应付 + 全部 Vendor 已 `INACTIVE_VENDOR` | `EXITED` |

**Vendor 生命周期**（`01 卷 §5`）

`PENDING_VENDOR → ACTIVE_VENDOR ⇄ PAYMENT_BLOCKED → INACTIVE_VENDOR`

| From | Event | Guard | To |
|---|---|---|---|
| `PENDING_VENDOR` | `activation_passed` | 准入 + 银行核验 + 税务 + 付款条件 + 重复发票检查全 PASS | `ACTIVE_VENDOR` |
| `ACTIVE_VENDOR` | `payment_block_raised` | 财务/风控事件（**本域不可自发放行**） | `PAYMENT_BLOCKED` |
| `PAYMENT_BLOCKED` | `payment_block_released` | 财务事件 + 人工复核留痕 | `ACTIVE_VENDOR` |
| 任一非终态 | `deactivate` | 无进行中 PO 或存量 PO 允许走完 | `INACTIVE_VENDOR` |

**禁止迁移（fail-closed）**：`EXITED → 任意`（终态不可回写）；`INACTIVE_VENDOR → ACTIVE_VENDOR` 须重新走开通闸门；`PENDING_VENDOR → ACTIVE_VENDOR` 未经闸门直接置位一律拒绝。迁移表未列出即拒绝，非法迁移抛 `DomainException` 并携带 `current_state / attempted / allowed[]`。

## 二、本 Agent 的技术判断（候选，随 Design 一并确认）

### T1 — 迁移表用枚举 + 显式 `Map<State, Map<Event, Transition>>`

守卫集中在 domain 层，不在 application/adapter 判断状态合法性。理由：`01 卷 §5` 明文要求；且与本仓既有 `GoldenRecordMerger` 的「领域内核负责内容正确性」分工一致。

### T2 — 跨域 Guard 用可注入断言端口，不硬编码跨域调用

`EXITED` 的「零未结 PO / 未结合同 / 未清应付」与 Vendor 的「银行核验 / 重复发票检查」在本切片**无对应上游域**（本切片 0 个跨域 adapter，且红线禁止接生产库）。设计为域内声明式前置条件：

```
SupplierExitGuard { boolean canExit(SupplierExitContext ctx); }
VendorActivationGuard { boolean canActivate(VendorActivationContext ctx); }
```

application 层负责编排外部事实（本切片由内存夹具提供），domain 只消费布尔结论 + 原因码。这样：
- 本切片不伪造跨域能力（不写死 `true`）；
- 上游域就绪时替换端口实现即可，领域语义零变化；
- 负向探针可精确注入「未结 PO 存在 → exit 必红」。

### T3 — `PAYMENT_BLOCKED` 的「本域不可自发放行」用命令来源校验

`payment_block_raised` 只接受声明为外部财务事件的命令来源；域内调用直接拒绝。这与本仓既有「AI 不碰钱」基线同构：能力存在但默认不可达。

### T4 — `SupplyRelationship` 为独立聚合根，`SupplierItem` 为其下供货条目

依据 `采购领域架构设计方案.md:105`（Supply Relationship = 聚合根、独立聚合）。二者均属 Purchasing Org data（Owner = 采购），与 Vendor（财务 Company Code）分属不同 Owner，不并入同一聚合。

### T5 — 身份解析复用 `EntityKeyMappingRegistry`，不新建 dedup

`EntityKeyMappingRegistry` 已有 `register / resolve / localIdsOf` 三个方法，满足本地编码 → partyId 解析。冲突与跨租户 fail-closed 由既有 `GoldenRecordMerger` 保障，采购域只做视图装配。

### T6 — 持久化仍为端口 + 内存适配，能力开关默认关闭

不变（Open 时 D4 已定）。补充：`SupplierRepositoryPort` 需同时支持按 `partyId`、按租户列表分页查询，以支撑 `PageResult<T>` 契约。

## 三、Spec Patch（需在 delta spec 中修改）

| # | 位置 | 变更 | 原因 |
|---|---|---|---|
| P1 | Requirement「供应商与供应商主体的角色分离」 | Supplier 改为「合作资格」，Vendor 改为「交易与付款资格（财务 Company Code data）」 | 对齐 LLD 真源 |
| P2 | Requirement「供应商准入生命周期状态机」 | 拆为两条：Supplier 生命周期（6 态）+ Vendor 生命周期（4 态），并补「禁止迁移」Scenario | spec 缺失 Supplier 状态机；Vendor 枚举与真源不符 |
| P3 | Scenario「同一法人下的双角色」 | 保留「各自持有独立生命周期状态」，明确两套状态机互不覆盖 | 与 LLD `:223` 一致 |

**不改动**：身份唯一性、字段级血缘、租户隔离、统一响应与权限契约四条 Requirement（与 LLD 无冲突，已有 Golden Record 内核保障）。

## 四、风险与取舍

| 风险 | 应对 |
|---|---|
| [Spec Patch 后 Open 已批准内容变化] | 变化摘要随 Design Wait 一并展示；批准绑定当前提案哈希，不复用旧哈希 |
| [跨域 Guard 用端口导致「看起来能退出但没真校验」] | 端口为 `boolean` 显式结论 + 原因码；负向探针覆盖「未结 PO → 必红」；文档如实标注跨域 adapter 属 owner 前置 |
| [`PAYMENT_BLOCKED` 无 Finance 上游] | 只验证「本域不可自发放行」这条 fail-closed 语义，不宣称已接财务 |
| [内存适配 ≠ 生产持久化] | 沿用 Open 时 R-2 标注，verify 只断言领域/应用/HTTP 行为 |

## 五、测试策略

| 层 | 覆盖 |
|---|---|
| 领域单测 | 两套状态机合法/非法迁移全路径；`EXITED` 终态不可回写；`PENDING_VENDOR→ACTIVE_VENDOR` 未经闸门必拒；角色不可穿透 |
| 应用服务测试 | 合并装配（lineage 完整才产出）、准入迁移、当前状态查询、租户不一致 fail-closed |
| HTTP 契约测试 | `ApiResponse<T>` / `PageResult<T>` 包装、权限点 403、响应体不含身份原值 |
| 能力门测试 | 开关关闭时 `SupplierApplicationService` / `SupplierController` 均不装配 |
| 负向探针 | 跨租户、身份冲突、缺 lineage、具名裁决缺失、未结 PO 退出、域内自发放行 六类注入必红 + 还原必绿 |

## 六、Open Questions

无。两项方向性裁决已由用户确认；其余为本 Agent 技术判断，随本次 Design 提案一并提请确认。跨域 adapter、DDL、CI、发布属已明确归 owner 的红线外事项，非本 change 开放问题。
