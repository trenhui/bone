# bone-metadata-server 设计复核报告（A 阶段 + A' 自审）

> 夜间设计复核 v3 六段闭环：A（设计复核）→ A'（方案自审）→ B → B' → C → D
> 模块：`bone-metadata-server`（:9001，catalog + 扩展字段 + `/api/v1/runtime/**`）
> 设计稿：`doc/design/modules/2.` / `2a.` / `2b.` / `生产就绪优化计划(P0·P1)` / `元数据能力-实现映射与竞品对照`
> 锚定真源：`doc/design/_global-contracts.yaml`（generated_at 2026-09-26T23:40）
> 复核日期：2026-09-27

## 0. 范围与方法

- **方法**：逐条重跑锚定取证命令 + 直读 `bone-engine/bone-metadata-server` 源码 + 读 `ADR-0016`，再对照设计稿「意图」判定，避免把「文档标 Target/Vision 的规划项」误判为缺陷。
- **关键判据**：HC 是否「硬约束违反」以 AGENTS.md §一 + 锚定语义为准；设计稿自身标 💡Vision / [Target] / P1 的，属路线图非缺陷。

## 1. HC 覆盖复核（锚定标 `violated` 的两条逐条复核）

| HC | 锚定判定 | 复核结论 | 证据 |
|----|----------|----------|------|
| **HC-003** | violated（「8 个 Controller 中 7 个返回 ResponseEntity」） | **误报**。所有 Controller 方法**返回体均为 `ApiResponse` 信封**，未裸返领域对象。写操作（`create/update/detail/instantiate`、runtime `get/create/update`）用 `ResponseEntity<ApiResponse<T>>` 承载 `201+Location` / `ETag`；读操作（`page/list/fields/delete`）直接 `ApiResponse<T>`。该形态与 **IAM 模块同款**（`MfaController`、`RefreshTokenIssuerGatewayAdapter` 调用方均 `ResponseEntity<ApiResponse<...>>`）。`doc2 §269` 明确写「Target 态需迁移为 `ResponseEntity.created(URI).body(ApiResponse.success(id))`（AIP-133）」——与现状一致。锚定只 grep 到 `ResponseEntity` 即判 violated，未看 body。 | `MetaEntityCatalogController.java:46/54/68/80`；`RuntimeRecordController.java:51/66/80/112/136`；`MetaTemplateController.java:36/42/49`；`MetaRelationCatalogController.java:32/43/50/64/72` |
| **HC-008** | violated（「md_lineage 缺 tenant_id/updated_at/deleted」） | **部分误报**。（1）实体基类 `MetaEntity`/`MetaField`/`MetaEntityRelation`/`MetaModelTemplate(+Field)` 继承 `AbstractEntity<Long>` + 自有 `tenantId` 字段，是 **ADR-0016（`0016-metadata-catalog-abstract-entity-tenant.md`）有意决策**（doc2 §266 记载），非违规。（2）`md_lineage`/`md_standard`/`md_quality_rule` 已按 `doc2a` **G6（2026-09-26 P0 已落地）** 整批划归 `bone-masterdata` 并改名 `mdm_*`，原 HC-008 标的表**已不在本模块**。锚定 `hc_evidence.HC-008` 仍写「md_lineage needs-owner-decision」与 G6 落地结论冲突，**锚定该条需刷新**。 | `doc2 §266` + `doc/architecture/adr/0016-metadata-catalog-abstract-entity-tenant.md`；`doc2a G6`；`MetaEntity.java:16`（extends AbstractEntity） |

> 其余 HC（001/002/004/005/006/007）锚定标 implemented，本次复核未见反例，维持。

## 2. 阻断级 finding（复核后）

**无硬阻断。** 锚定标记的两条 `violated` 经读码 + 读 ADR 确认均为误报 / 已随 2026-09-26 的 mdm_* 划归解决。

## 3. 建议级 finding

- **F1（中）· 错误码常量类缺失**：`META_RUNTIME_*` 以裸字符串存在于 `RuntimeRecordException.getErrorCode()`，由 `GlobalExceptionHandler` 透传（`BizException` 模式，与 IAM 一致，功能合规、i18n 友好）。`doc2 §8 / §353` 声称的「`META_` 前缀错误码常量类」未落地——属**文档-代码 GAP**。建议二选一：① 补 `MetadataErrorCodes` 聚合类（对齐 `IamErrors`）；② 在文档中说明采用「异常携带 errorCode 透传」模式，删去常量类承诺。
- **F2（中）· 发布→事件链路缺口**：`doc2a §337` 步骤⑤「发布领域事件」、G8「无变更事件、无采用率」均标 `[Target]`，是路线图非缺陷；但**引擎侧 `MetadataChangeEvent` 以进程内 `ApplicationEvent` 发出、server 侧无订阅方**（锚定 cross_module 已记录），等于发布即丢弃。建议：在 catalog 发布流程补发领域事件，或明确该事件归并到 `bone-metadata-engine` 的 Comet change（`refactor/metadata-engine-boundary-ddd`）后再接。
- **F3（低）· Controller 返回形态不统一**：`ResponseEntity<ApiResponse<T>>` 与 `ApiResponse<T>` 混用。因与 IAM 同款、且需头部的方法必须用 `ResponseEntity`，建议保持现状或统一为 `ResponseEntity<ApiResponse<T>>`。
- **F4（低/文档）· 版本端点未实现**：`doc2 §5.1` `GET /entities/{id}/versions`（P1）未实现，文档已标 Vision P1，非缺陷。
- **F5（低/文档）· 设计稿控制器清单滞后**：`doc2 §656` 仅列「五个真实控制器」，实际多出 `MetaTemplateController` / `PhysicalStructureController`（及已随 HC-004 删除的 `AuthController`）。需在设计稿补记代码新增的 controller，避免文档-代码漂移。

## 4. 跨模块契约

| 对端 | 契约 | 风险 | 状态 |
|------|------|------|------|
| bone-iam | 仅逻辑外键（module_id/appId/userId），编译期零 import | 无外键/无事件补偿；IAM 删应用后元数据引用悬挂 | ⚠️ 已登记（建议未来补孤儿清理） |
| bone-masterdata | `MetaEntityCatalogPortAdapter` 用 JDBC 直读元数据目录表（跨上下文共享表） | 违反 P-6 优先公开 API；表结构变更静默打断主数据 | ⚠️ baseline 登记（只可收缩） |
| bone-metadata-engine | 进程内 `ApplicationEvent`，server 无订阅 | 引擎变更事件发布即丢弃 | ⚠️ 见 F2（engine 被 Comet 占用，skip） |
| studio-generator | generator 经 `/metadata-entity-snapshots` 读元数据与库结构 | `DatabaseMetadataGatewayAdapter` 原生 JDBC | ⚠️ baseline 登记 |

## 5. 待审批清单（L3，AI 不执行，仅登记）

- **L3**：若仍需为 `mdm_lineage`（已划归 masterdata）补 `tenant_id`/审计列 → DDL，归 `bone-masterdata` 上下文（非本模块）。
- **L3**：F2 发布事件落地 → 新增领域事件发布 + 订阅（可能涉及 DDL/依赖，待架构师拍板；或与 engine Comet change 合并）。
- **L2 可做·建议架构师确认**：F1 `MetadataErrorCodes` 聚合类（非 DDL，但属约定变更，建议对齐 IAM 后由人工确认）。

## 6. A' 方案自审（三问）

1. **证据可复现？** ✅ 本报告 file:line 均可在仓库复现（`MetaEntityCatalogController`、`RuntimeRecordController`、`MetaEntity.java`、`ADR-0016`、`doc2 §266/§269`、`doc2a G6`）。
2. **结论稳健？** ✅ 锚定两条 `violated` 经「读码 + 读 ADR + 读设计稿 Target/Vision 标注」三重交叉，确认为误报 / 已解决，非主观臆断。
3. **无 scope creep？** ✅ 本阶段仅评审，未改动任何源码；L3 仅登记不执行。

**判定：PASS（无阻断级缺陷）**；建议项 F1–F5 供人工评审。建议**刷新锚定文件**中 HC-003 / HC-008 的现状标注（前者应为 implemented，后者针对的表已划出本模块）。

## 7. 结论

`bone-metadata-server` 设计稿与代码总体一致。锚定标记的 HC-003 / HC-008「violated」均为**误报**（分别由 ADR-0016 与 2026-09-26 的 `mdm_*` 划归解决）。无阻断级缺陷，设计可放行。建议后续：① 刷新锚定对应标注；② 处理建议项 F1–F5（均为中/低，无硬阻塞）；③ 按夜间流程默认放行，`design_ready` 后下一晚可进 B 实现或直接进入其余模块轮换。
