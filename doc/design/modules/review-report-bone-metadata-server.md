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

### 8.2 C 联调（环境受限，未做全链路 HTTP 联调）

- **尝试**：自起 `bone-metadata-server`（`spring-boot:run`，dev profile，端口 19001，绕 IDE 监管 fat jar），依赖 `bone-metadata-engine-runtime` 经 `-am install` 注入 F1 新类。
- **结果**：启动失败，**根因与 F1 无关**——dev 默认 `sa-token.disable: true` 且 `spring.redis.password: ${BONE_REDIS_PASSWORD:}` 解析为空，Redisson 启动即向**无密码的本地 Redis（`localhost:6379`）**发 `AUTH`，被 `ERR AUTH <password> called without any password configured for the default user` 拒绝。三次重试（含 `-Dspring.redis.enabled=false`、排除 Redisson 自动配置）均在同一 Redis AUTH 点失败——属 dev profile 环境配置缺口，非本模块代码问题。
- **替代证据（真实执行）**：新增 `MetadataErrorCodesMappingTest`（**8 测试全绿**），直接调用 `GlobalExceptionHandler.handleRuntimeRecord` 并断言：① 每个 `META_RUNTIME_*` 码 → 正确 HTTP 状态（404/412/400/409/400）；② `errorCode` 原样透传至响应体 `ProblemDetail.errorCode`（即前端 i18n 取用的键）。该测试不依赖 Spring/Redis 上下文，真实执行了 F1「异常→状态→码」链路。
- **诚实结论**：**未做全链路 HTTP 联调**（服务在本机 dev 配置下无法裸启）。F1 等价重构已由「编译 + i18n 门禁 + 8 单测真实执行」三重覆盖。

### 8.3 D 验收（环境受限，未执行）

- 依赖已启动的服务 + 已发布 RUNTIME 实体 + 构造/清理测试数据；前端微应用 `bone-metadata-app` 按轮换口径为 `skip`，不单独占夜。
- 因 C 段服务无法启动（同 Redis AUTH 阻塞），D 段无法构造数据走通功能，**未执行**。无测试数据写入，无需清理。
- 待 dev Redis 配密码（或 Redisson 改为按需懒连）后，次夜可补 C/D 全链路。

### 8.4 状态迁移

- `bone-metadata-server`：`design_ready` → `implementing`（B/B' 完成并提交；C/D 环境阻塞，待 Redis 配置后补）。
- 提交：`d1723f00c` B(F1) + 本轮 B' 单测与状态/日志/报告回填。
- 阻断 0；待审批：F1 已落（原 L2 建议，已执行）；F2–F5 仍为建议项（F2 发布事件归 engine Comet change，非本模块；F3–F4 低优；F5 已闭合）。
