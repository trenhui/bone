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
- **F5（低/文档）· 设计稿控制器清单滞后**：**已闭合（2026-09-27 复核纠正）**。`doc2 §656` 当前已列「七个真实控制器」（`MetadataController` / `MetaEntityCatalogController` / `MetaFieldCatalogController` / `MetaRelationCatalogController` / `MetaTemplateController` / `PhysicalStructureController` / `RuntimeRecordController`），并明确 `AuthController` 随 HC-004 删除、CodeGen/Template 控制器在 `studio-generator`。原「仅列五个」为陈旧标注，无需再改设计稿。

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

## 8. B 阶段实现闭环（B' 代码复核 / C 联调 / D 验收）

> 本模块 B 阶段清单仅 F1（错误码常量类，A 阶段建议项落地）；F5 已在 A 收口阶段闭合。以下为 B' / C / D 三段的实测结论。

### 8.1 B' 代码复核

- **改动范围**：仅 F1——新增 `MetadataErrorCodes`（7 常量，与 `RuntimeRecordException` 同包，调用点免 import）；`JdbcRuntimeRecordService` / `RuntimeQuerySupport` / `RuntimePageQuery` 裸字符串与两处半吊子私有常量 `META_RUNTIME_VALIDATION_FAILED` / `META_RUNTIME_DUPLICATE` 统一替换为 `MetadataErrorCodes.*`；`GlobalExceptionHandler.handleRuntimeRecord` 的 `switch` 改用常量；`Bone-错误码登记.md` §6 登记 7 行；i18n `en-US.json` / `zh-CN.json` 各加 6 个对称 `META_RUNTIME_*` 键（`META_PRECONDITION_FAILED` 已存在不重复）。
- **硬约束**：无新增 `FOR UPDATE` / 手写悲观锁；未触碰业务层悲观锁禁区（AGENTS.md §一.7）。
- **契约漂移**：错误码**字符串值逐字不变**（如 `META_RUNTIME_RECORD_NOT_FOUND`），故 `GlobalExceptionHandler` 状态映射、i18n `errors.*` 键、前端取值全部保持原契约——**零行为变更**，属等价重构。
- **禁改文件**：未动 `.comet/`、DDD 最终实践方案 §12/§G-1.7、`gate-state.json`、`bone-init.sql`、密钥/`.env`、`.github/workflows`。
- **编译**：`mvn -o -pl bone-metadata-engine-runtime,bone-metadata-server test-compile` → BUILD SUCCESS（无回归）。
- **门禁**：`scripts/check-i18n-sync.py` → `✅ check-i18n-sync 通过`（代码常量 182 / 台账 §6 107 / 白名单 138 / 待覆盖 53）。
- **判定：PASS**（无越界、无禁改文件、无清单外改动、无 L3/L4 泄漏）。

### 8.2 C 联调（已验证 · 真实 HTTP）

- **本机裸启受阻的根因（已定位，非代码问题）**：自起 `bone-metadata-server`（`spring-boot:run` dev profile，端口 19001）时，因我未传 `BONE_REDIS_PASSWORD`，dev 配置 `spring.redis.password: ${BONE_REDIS_PASSWORD}`（无默认值）解析为空，Redisson 启动即向无密码本地 Redis 发 `AUTH ""` 被拒——属「手动启动漏传环境变量」，**非本模块代码缺陷**。
- **改用监管实例取证（真实、有效）**：IDE 监管的 `bone-metadata-server` 实例（端口 9001，sa-token 已 disable、已传 `BONE_REDIS_PASSWORD`）健康运行（`/actuator/health`=200），且读取编译后的 `target/`，即跑的是含 F1 的当前代码。直接对其打 runtime 端点，拿到**真实 HTTP 响应**：
  - 不存在的实体 → `HTTP 404`，body `errorCode:"META_RUNTIME_ENTITY_NOT_FOUND"`、`title`/`detail` 与 `status:404` 一致；
  - 非法 `q` 格式 → `HTTP 400`，body `errorCode:"META_RUNTIME_INVALID_QUERY"`、`message:"q 参数格式应为 field:value"`。
  - 这证明 F1「`RuntimeRecordException(MetadataErrorCodes.*)` → `GlobalExceptionHandler` → 正确 HTTP 状态 + `errorCode` 透传」链路在**真实运行实例**上完整生效，且契约（码字符串、HTTP 状态）与前端 i18n 键一致。
- **补充执行证据**：新增 `MetadataErrorCodesMappingTest`（**8 测试全绿**），直接调用 `GlobalExceptionHandler.handleRuntimeRecord` 断言每个 `META_RUNTIME_*` 码 → 正确状态（404/412/400/409/400）且 `errorCode` 透传；`scripts/check-i18n-sync.py` 通过。
- **判定：C 通过**（真实 HTTP + 单测 + i18n 门禁三重覆盖；无任何 5xx）。

### 8.3 D 验收（后端已验收 · 前端 skip）

- **后端验收**：F1 仅改变**异常路径**映射，其验收面 = C 段已验证的两条错误码链路（404/400）+ 8 映射单测 + i18n 门禁，三者均绿；C 段探针全程无 5xx、无服务端异常。
- **未做「已发布 RUNTIME 实体 → 200」成功路径演示**：当前 runtime 目录无已发布 RUNTIME 实体，现有 `e2e_*` RUNTIME 实体均为共享 E2E DRAFT fixture；发布需改共享数据且无干净「取消发布」入口（仅有删除），为遵守清理纪律**不改动共享 fixture**。该成功路径不触发 F1 代码，F1 验收不受影响。
- **前端微应用 `bone-metadata-app`**：按轮换口径为 `skip`，不单独占夜；前端 i18n 键 `errors.META_RUNTIME_*` 已在 `bone-frontend` 双语登记（F1 提交已含），取值链路由 C 段响应体 `errorCode` 间接验证。

### 8.4 状态迁移

- `bone-metadata-server`：`design_ready` → `implementing`（B/B'/C 完成并提交；D 后端验收完成，前端 skip）。**本模块夜间闭环结束**。
- 提交：`d1723f00c` B(F1) + `9a9547820` B'/C/D 状态回填；本轮再将 C「环境受限」误判更正为「已验证」，追加提交。
- 阻断 0；待审批：F1 已落（原 L2 建议，已执行）；F2–F5 仍为建议项（F2 发布事件归 engine Comet change，非本模块；F3–F4 低优；F5 已闭合）。

## 9. F2 补充执行：发布领域事件落地（2026-09-28，A→B 直通）

> 用户复核指令授权，按夜间 v3「默认放行」口径执行（approvals 目录无 hold/block）。**不触碰 engine**（Comet change 占用），采用报告建议①「在 catalog 发布流程补发领域事件」。

- **设计对齐**：doc2a §337 步骤⑤ = 「失效缓存（已有 ✅）+ 发布领域事件（本次补齐）」；G8 消费登记 / 集成事件 / `meta_model_consumer` 为 P1 [Target]（需 DDL），本次不做。
- **改动**（4 文件，均在 `bone-metadata-server`）：
  - 新增 `catalog/domain/model/meta/event/MetaEntityPublishedEvent.java`：record 实现 `DomainEvent`（entityId/tenantId/entityCode/tableName/deliveryMode/version/eventTime）。
  - 新增 `catalog/infrastructure/event/SpringDomainEventPublisher.java`：与 bone-system 同款，委托 Spring 事件总线（模块此前缺该 bean）。
  - `MetaEntityApplicationService.publishEntity`：注入 `DomainEventPublisher`，在「状态落库 + 缓存失效 + RUNTIME align」全部成功后发事件；align 失败即发布失败、无事件。单条与批量（`batchPublishEntities`）共用，自动覆盖。
  - `MetaEntityWorkbenchServiceTest` 构造参数 +1；新增 `MetaEntityPublishEventTest`（5 用例：GENERATIVE/RUNTIME 成功发事件且载荷正确、漂移拒绝与 404 不发事件、批量部分成功逐实体发事件）。
- **边界**：不改 `MetaEntity` 聚合层级（ADR-0016：AbstractEntity + 自有 tenantId 为有意决策，不迁 AggregateRoot）；不新增 DDL；不动 engine 的 `MetadataChangeEvent`（engine 内部热加载事件，仍归 Comet change）。
- **测试**：`MetaEntityPublishEventTest` 5/5 绿、`MetaEntityWorkbenchServiceTest` 全绿；模块全量 `Tests run: 94, Failures: 2`，2 个失败（`OrderPaymentMetadataE2ETest.publish_detectsTypeDrift_returns409` 409→400、`MetadataModuleReferenceE2ETest.createEntity_withNonExistentModuleId` 400→500）**经基线实验实证为存量问题**——临时还原 service 至 HEAD 后两用例同样失败，与本次改动无关（疑与错误码三参构造丢 errorCode 的已知坑同族，待另行排查）。
- **判定：B/B' 通过**；F2 由「建议项」转为「已落地（进程内领域事件）」，跨模块集成事件侧维持 P1 [Target] 待 G8。

## 10. 存量 E2E 失败排查与修复（2026-09-28，§9 遗留项闭环）

> §9 全量测试遗留 2 个失败（已实证与 F2 无关）。经定位为 **b6406ba64 i18n 改造重写 handler 时引入的回归**，与 F2 一并修复。

- **失败① `publish_detectsTypeDrift_returns409`（409→400）**：`validateForPublish` 抛裸 `DomainException`，i18n 改造后的 `handleDomain` 兜底为 400 `COMMON_VALIDATION_FAILED`；而 409+`META_DOMAIN_ERROR` 是 doc2a §328 明确契约（P0-6 曾真机实测通过）——**契约回退**。连带发现预览侧隐藏 bug：`collectIssues` 只 `catch (BizException)`，`DomainException` 冒穿导致漂移实体的预览接口直接 400。
  - **修复**：`MetaEntityApplicationService` 新增 `validatePhysicalStructureForPublish` 统一翻译 `DomainException → 409 + META_DOMAIN_ERROR`（四参构造铁律，含 cause）；发布与预览两个调用点共用，预览侧 `PHYSICAL_DRIFT` ERROR 上浮恢复正常。新增 `CatalogErrorCodes`（server 侧 `catalog/common`）承载该码——**不入 engine-runtime 的 `MetadataErrorCodes`（Comet change 占用）**；码已在 `config/i18n/errorcode-baseline.json` 白名单，无语言包阻断。补翻译层单测（409+码+不发事件断言）。
- **失败② `createEntity_withNonExistentModuleId`（400→500）**：`IamModuleValidator.requireModule` 三处 + `IamApplicationValidator.requireExists` 两处用**单参** `BizException.of(msg)`（默认 `code=500`）——入参校验拒绝被兜成 500。
  - **修复**：五处改 `BizException.of(400, msg)`，与 E2E「400 + 所属模块不存在」契约对齐。
- **验证**：模块全量 `Tests run: 95, Failures: 0, Errors: 0 — BUILD SUCCESS`（含此前失败的 2 个 E2E、ArchitectureTest 26/26、F2 的 6 个事件用例）。
- **判定：PASS**。§9 遗留清零；`biz-code` 语义教训入库：i18n 改造类重构必须以「存量断言码+状态」的 E2E 为回归基线，而非仅 test-compile。
