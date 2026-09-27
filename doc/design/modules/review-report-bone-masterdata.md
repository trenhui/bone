# bone-masterdata 设计复核报告（A 阶段 + A' 自审）

> 夜间设计复核 v3 六段闭环：A（设计复核）→ A'（方案自审）→ B → B' → C → D
> 模块：`bone-masterdata`（:8084，主数据目录 / 记录 / 数据标准 / 质量 / 血缘 / 引用集 / 治理）
> 设计稿：`doc/design/modules/3. 主数据管理模块详细设计方案.md` / `3a. 主数据管理模块核心场景及用例设计方案.md`
> 锚定真源：`doc/design/_global-contracts.yaml`（generated_at 2026-09-26T23:40，本次复核同日刷新 HC-008 标注）
> 复核日期：2026-09-27

## 0. 范围与方法

- **方法**：逐条重跑锚定取证命令 + 直读 `bone-platform/bone-masterdata` 源码（16 个 Controller、域实体基类、DDL、事件发布器）+ 对照设计稿「意图」判定，避免把「设计稿标 Target/Vision 的规划项」误判为缺陷。
- **关键判据**：HC 是否「硬约束违反」以 AGENTS.md §一 + 锚定语义为准；设计稿自身标 💡Vision / [Target] / P1 的，属路线图非缺陷。
- **与 metadata-server 轮次的区别**：本轮 **HC-008 是真实缺口（非误报）**——`mdm_*` 表 DDL 确缺审计/租户列；其余多数 finding 为设计-代码 GAP（规划项未落地），不构成硬约束违反。

## 1. HC 覆盖复核

| HC | 锚定判定 | 复核结论 | 证据 |
|----|----------|----------|------|
| **HC-003** | implemented（「16 个 Controller 中 ResponseEntity 命中 0」） | **确认 implemented**。16 个 `@RestController` + 1 个 `@RestControllerAdvice`；controller 目录搜索 `ResponseEntity` 0 命中；所有 handler 返回 `ApiResponse<T>` / `ApiResponse<PageResult<T>>`（如 `MasterDataRecordTopController.java:41`、`MasterDataEntityController.java:25`）。无裸 `ResponseEntity<DomainDto>`。 | controller 目录 grep ResponseEntity=0；handler 返回 `ApiResponse.success(...)` |
| **HC-008** | violated（本次复核前为 pending_review） | **确认 violated（真实缺口，非误报）**。`mdm_qcheck_task`（bone-init.sql:1730-1748）仅 `created_at`/`created_by`，**缺 `updated_at`/`deleted`**；同批 `mdm_qcheck_detail`/`mdm_qcheck_report` 缺 `updated_at`/`deleted`，`mdm_record_category`/`mdm_record_version` 缺 `tenant_id`。实体基类 `TenantAggregateRoot`/`TenantAbstractEntity` 仅补 `tenantId`、审计列靠子类声明，但 DDL 未补齐。属 **L3 DDL 缺口**，登记待架构师审批，AI 不执行。 | `bone-init.sql:1719-1776`；`TenantAggregateRoot.java:13`、`AbstractEntity.java:24-39` |
| HC-001/002/004/005/006/007 | implemented | 本次复核未见反例，维持。published_events 为 Spring 进程内 `DomainEvent`（8 类）+ `MasterdataDomainEventPublisher`（`ApplicationEventPublisher`），无 IntegrationEvent/Outbox/MQ——当前无 HC 直接约束，但见建议 F5。 | `MasterdataDomainEventPublisher.java:21,37`；`DomainEvent.java:4` |

> 其余 HC（001/002/004/005/006/007）锚定标 implemented，本次复核未见反例，维持。

## 2. 阻断级 finding（复核后）

**无硬阻断级缺陷（可放行 A）。** 唯一硬约束违反是 **HC-008（L3 DDL）**，按夜间流程属「登记待审批、AI 不执行」范围，不阻塞本阶段评审结论；其修复（补列 + baseline 登记）留待人工/架构师裁定。

## 3. 建议级 finding

- **F1（低/文档）· 设计-代码参数漂移**：`MasterDataEntityController.convert` 代码用 `@RequestParam Long metaEntityId`（:69-72），设计稿 doc3 规划 `?businessEntityId=`。功能正常但文档与代码参名不一致。**【B 阶段已落地 2026-09-27】** 以「零 API 风险」为原则，将设计稿对齐代码：doc3 §0/§API 表、doc3a UC-MD-T1 三处 `businessEntityId` → `metaEntityId`（与代码及锚定 `_global-contracts.yaml` 的 `/convert?metaEntityId=` 一致）；不动代码参数名以免前端 convert 调用方断裂。
- **F2（中/路线图）· 跨模块集成事件未落地**：doc3 §630 规划「跨模块集成事件 + RocketMQ」，代码 0 命中 `IntegrationEvent`/`Outbox`/`RocketMQ`（全模块 grep）。当前为 Spring 进程内 `DomainEvent`，跨服务（如通知/集成）无法感知。属路线图非缺陷，建议归并到后续「事件总线/Outbox」专项。
- **F3（中/路线图）· 幂等键 + LRO 未实现**：doc3 §287 的 `Idempotency-Key`、HTTP `202 Accepted` + `/operations/{taskId}`（长时运行操作）代码全缺失（grep `Idempotency|ACCEPTED|/operations/`=0）。属路线图非缺陷。
- **F4（低）· DTO 纪律轻微偏离**：`MasterDataRecordTopController.java:104` 返回 `ApiResponse<List<MasterDataRecordVersion>>`，信封正确但 payload 为**领域实体**而非 DTO。非 HC-003 违规，但建议收口为 DTO 以隔离领域模型。
- **F5（中）· 事件无 Outbox/持久化**：8 类 `DomainEvent` 经 `ApplicationEventPublisher` 进程内投递，无事务外发/重试/去重。若未来需跨模块可靠投递（F2 落地），应先补 Outbox 机制而非直接发 MQ。建议列入「事件基础设施」专项，与 bone-integration 模块协同。

## 4. 跨模块契约

| 对端 | 契约 | 风险 | 状态 |
|------|------|------|------|
| bone-metadata-server | `MetaEntityCatalogPortAdapter`（masterdata 侧）直接 JDBC 读 `meta_entity`/`meta_field`（:11,24,37,73），跨上下文共享表 | 违反 P-6 优先公开 API；元数据表结构变更静默打断主数据 | ⚠️ baseline 登记例外 E-1.1（只可收缩）；注释:17 已注明「应走联邦视图/元数据服务 API」 |
| bone-iam | 鉴权经 `CurrentUserPortAdapter` 从 JWT 声明读 `userId`/`tenantId`（:23,49），**无 iam 表 JDBC 直连** | 无外键/无事件补偿；IAM 删应用后主数据引用悬挂 | ⚠️ 已登记（建议未来补孤儿清理） |
| bone-metadata-sdk | 21 个域实体/仓储编译期 import `com.bone.metadata.sdk.*`（`@Table`/`@Column`/Repository） | 合法 SDK 持久化用法 | ✅ 合规 |

## 5. 待审批清单（L3，AI 不执行，仅登记）

- **L3（HC-008）**：`mdm_qcheck_task` 补 `updated_at`/`deleted`；`mdm_qcheck_detail`/`mdm_qcheck_report` 补 `updated_at`/`deleted`；`mdm_record_category`/`mdm_record_version` 补 `tenant_id` —— 均 DDL 变更 + `ddl-required-columns-baseline.json` 登记。归 bone-masterdata 上下文。
- **L3（可选·架构）**：F5 事件 Outbox 机制、F2 跨模块 MQ 集成 —— 涉及基础设施与多模块，待架构师拍板。

## 6. A' 方案自审（三问）

1. **证据可复现？** ✅ 本报告 file:line 均可在仓库复现（16 个 Controller 返回形态、`bone-init.sql` DDL 列、事件发布器、跨模块 Adapter）。
2. **结论稳健？** ✅ HC-003 经「code grep + 直读 handler 签名」确认；HC-008 经「DDL 列核对 + 实体基类继承」确认真实缺口（与 metadata-server 轮的误报不同）。F1–F5 均区分「硬违反」与「路线图/Vision 规划项」，未将规划项误判为缺陷。
3. **无 scope creep？** ✅ 本阶段仅评审，未改动任何源码；L3 仅登记不执行。

**判定：PASS（无阻断级缺陷）**；HC-008（L3）与 F1–F5 供人工评审。建议刷新锚定文件中 `bone-masterdata` 的 HC-008 标注（本轮已从 `pending_review` 复核为 `violated` 并补具体证据）。

## 7. 结论

`bone-masterdata` 设计稿与代码总体一致，响应契约（HC-003）合规。锚定标 `violated` 的 **HC-008 经复核为真实 L3 DDL 缺口**（`mdm_*` 表缺审计/租户列），已登记待审批；与 metadata-server 轮的 HC-003/HC-008 误报不同，本轮无假阳性。无阻断级缺陷，设计可放行。建议后续：① 处理 HC-008 的 L3 DDL（人工/架构师）；② 处理建议项 F1–F5（多为路线图/文档 GAP，无硬阻塞）；③ 按夜间流程默认放行，`design_ready` 后下一晚可进 B 实现或继续其余模块轮换。

## 8. B 阶段实现闭环（F1 落地）

> B 阶段以「低风险、可验证」为筛选原则，仅落地 F1；F2/F3/F5 属路线图/基础设施、F4 需新建 DTO 隔离层，均不在单夜 B 阶段范围。

### 8.1 B（F1 文档对齐）

- **改动**：`doc/design/modules/3. 主数据管理模块详细设计方案.md`（§0 血缘表、`/convert` API 表共 2 处）、`doc/design/modules/3a. 主数据管理模块核心场景及用例设计方案.md`（UC-MD-T1 主流程 B 共 1 处）中 `businessEntityId` → `metaEntityId`。
- **对齐基线**：代码 `MasterDataEntityController.convert(@RequestParam Long metaEntityId)`（:69-72）+ 锚定 `_global-contracts.yaml` exposed_apis 已写 `/convert?metaEntityId=`。
- **风险**：纯文档对齐，不改 API 契约；前端 convert 调用方（按 `metaEntityId` 传参）不受影响。
- **门禁**：`scripts/check.sh` 文档类检查通过（无其他源码改动，编译/ORM/i18n/HC-006 不受影响）。

### 8.2 未在本夜 B 阶段处理（明确归属）

- **F4（低·DTO 纪律）**：`MasterDataRecordTopController.java:104` `versions()` 返回 `MasterDataRecordVersion`（域模型）而非 DTO。需新建 `MasterDataRecordVersionDTO` + 映射层，属「领域模型隔离」收口，建议独立占一夜，避免 scope creep。
- **F2（中·跨模块 MQ 事件）/ F3（中·幂等键+LRO）/ F5（中·事件 Outbox）**：均属事件基础设施/路线图，非单夜 B 阶段可 closure，归并到后续「事件总线/Outbox」专项，与 `bone-integration` 协同。
- **HC-008（L3 DDL）**：`mdm_qcheck_task` 等补 `updated_at`/`deleted`/`tenant_id` 待架构师审批后由人工执行；AI 不在本阶段改 DDL。

### 8.3 状态迁移

- `bone-masterdata`：`design_ready` → `implementing`（stages_done A/A'/B）；F1 已落地。
- 阻断 0；待架构师审批：HC-008（L3 DDL）、F5 Outbox / F2 MQ（可选架构）。
- 下一晚可继续 B 阶段（F4 DTO 收口）或轮换 `bone-integration`。
