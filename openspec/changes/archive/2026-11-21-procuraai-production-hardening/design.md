---
comet_change: procuraai-production-hardening
role: technical-design
canonical_spec: openspec
archived-with: 2026-11-21-procuraai-production-hardening
status: final
---

# Design

## Context

当前采购域只有 `transaction` 与 `fulfillment` 真正接了 `bone-metadata-sdk` 持久化（实体带 `@Table/@Column/@Id/@Version`，Repository 由 `RepositoryRegistrar` 动态代理，`ProcurementApplication` 通过 `@EnableSqlRepositories` 扫描）；`sourcing` / `qualification` / `risk` 已有实体与 DDL 但注入 `InMemory*Repository`；`contract` 与 `ai-governance` 的 6 个端口既无实体也无 DDL，仅由 `@ConditionalOn...Enabled` 的内存实现支撑。ArchUnit E-5.4 约束 application 层直接调用 `*Repository.save*` 必须带 `@NoDomainEvent` 或 `publishFrom()`；A10.17 要求新增判据同步 `ci.yml`、`scripts/check.sh`、`check-procura-ci.sh` 三入口并有注入式反证。动机与范围见 `proposal.md`，行为契约见 `specs/procurement/production-persistence/spec.md`。

## Goals / Non-Goals

**Goals:**

- 9 个内存端口/仓储由持久化事实支撑，进程重启不丢状态；既有 REST 契约与能力三态不变。
- 审查业务键的跨租户写入被唯一约束阻断；审计事实不可变、无 PII 原文。
- 全域开关每次裁决读最新持久化快照，切换即时生效。
- 加固判据进入门禁三入口，且以注入必红/正例不误杀证明不是假绿。

**Non-Goals:**

- 不接真实模型、不声明模型效果；不改 evalset 27 例下界与 `seededDraft` 状态（D-15/17）。
- 不冻结 D3-v2 契约或 LLD 六卷；不改对外 API、事件七字段与披露文案。
- 不接生产 MQ/Outbox/Inbox，不执行任何生产库 DDL，不改未跟踪的 `synapclaim-architecture-convergence/`。

## Approaches

- **方案 A（推荐，已选）**：全量切 SDK 持久化。A 类三模块直接换 Repository bean；B 类新增实体 + 端口 Adapter + init-only DDL；门禁新增 `persistenceGate` / `persistenceProbe` 并同步三入口。优点是 9 个内存事实一次性转为可重启读回的生产态，隔离与幂等能被机器验证；代价是新增 6 表与门禁常量，改动面较大。
- **方案 B**：只切 A 类三模块，contract/governance 继续内存态。改动最小、风险最低，但跨租户覆写、审计不可变与总闸即时生效仍无法从持久化保障，交付后仍不能称生产加固，违背本次范围。
- **方案 C**：手写 JDBC/MyBatis 直接落库。可精确控制 SQL，但违反 HC-001「持久化唯一走 bone-metadata-sdk」与现有 DDL 对账测试范式，且会使 ArchUnit/门禁失效，不采用。

## Decisions

1. **持久化统一走 SDK，禁止手写 JDBC/JPA**：A 类（sourcing/qualification/risk）直接在 `ProcurementApplication` 的 `@EnableSqlRepositories` 增加三个 `domain.repository` 包，并移除同接口 `@Repository` 内存 bean；实体与 DDL 已存在，无需新增表。B 类（contract/governance）新增 `*Entity` + SDK `Repository` + 实现原端口的 infrastructure Adapter，应用服务仍只依赖端口，从而同时满足分层约束与 E-5.4。备选「应用服务直调仓储」被否，因为会触发 E-5.4 且破坏端口边界。

2. **新增 6 张 init-only 表，不建增量迁移**：`t_proc_contract_review`、`t_proc_review_trace`、`t_proc_ai_capability`、`t_proc_human_decision`、`t_proc_global_kill_switch`、`t_proc_governance_trace`。沿用采购既有 8 个必备列（雪花 ID、`tenant_id`、审计四列、`deleted`、`version`）与 `uk_` 租户打头唯一键、`idx_` 辅助索引约定；在 `bone-init.sql` 内 `DROP TABLE IF EXISTS` + `CREATE`，`数据库开发规范.md` 表数 15 → 21。备选「增量 Flyway 迁移」被否：采购域既有切片统一 init-only，增量脚本会破坏既有对账测试与初始化口径。

3. **跨租户写入双防线**：服务端生成 `review_id`（不接受请求自报）＋ `(tenant_id, review_id)` 唯一约束。只做服务端生成无法防止同一租户内重复业务键覆写；只做唯一约束仍可能让请求指定他人键触发异常路径。两者并用后，越权请求在读侧也无法命中他人记录。相似键（能力码、供应商+来源引用）按同一模式加租户打头唯一约束。

4. **总闸无缓存**：`GlobalKillSwitchStore` 的持久化 Adapter 每次 `find` 读取最新行；裁决路径不得缓存开关态；写路径从登录态取操作人，缺失即拒绝并记录失败审计。备选「短 TTL 缓存」被否，因为规格要求切换后下一次裁决立即生效。

5. **复杂事实以 JSON 列存储，原文禁入**：审查明细、追溯条目、AI 建议与人工终决分列；只持久化结构化摘要、引用与 hash，模型 `output` 原文在入口扫描后即丢弃。实体与领域 record 分离，Adapter 负责转换，避免给领域类加持久化注解。

6. **门禁判据显式注册**：新增 `persistenceGate` 与 `persistenceProbe` 两个 key，按 A10.17 同步 `tools/check_procura_alignment.py` 常量与 `_cmd_key()`、三入口 blocking 步骤、`CI_PROCURA_STEP_MIN`、`--self-test` 注入反证与正例对照，并开发期 `--sync-manifest` 同步 `gateEvidenceManifest`。备选「塞进既有 docGate」被否，因为会绕过判据集合的机器比对，形成假绿。文档措辞避开 A10.16 黑名单，不改历史轮次数字。

7. **验证按风险分层**：A 类复用既有 DDL 对账测试；B 类新增 DDL 对账 + 实体映射 + 端口契约测试；门禁新增注入必红探针；最后跑 Maven 全量（含 ArchUnit）、对齐主判据与 self-test、前端 tsc/eslint(json)/vitest。CLI 绿只证明结构与隔离行为，不证明 AI 效果或生产库可执行。

## Risks / Trade-offs

- **[同接口双 bean 启动失败]** → 内存实现改为条件 bean 或在切换提交中移除；用 ApplicationContextRunner 断言每个端口恰好一个实现。
- **[E-5.4 转红]** → 全部写操作放在 Adapter，不在 application 层直调 `*Repository.save*`；必要时 `@NoDomainEvent` 显式豁免并附理由。
- **[DDL 未在真实 MySQL 执行]** → 本轮只做静态对账与测试库验证；真实库迁移验收明确转 L3，不伪造已执行。
- **[JSON 列映射漂移]** → 为每类复杂事实写往返映射测试；字段新增必须同步 Adapter 与测试。
- **[门禁 manifest 漂移]** → 用 `--sync-manifest` 一次性同步，注入必红与正例不误杀双验；历史 146/146 等数字不回写。
- **[范围外文件误改]** → 工作区现有未跟踪 change 保持原样；提交只精确 add 本 change 与实现文件。

## 施工前澄清（Builder Check；不改变已批准方案）

- **幂等键**：请求携带 `idempotencyKey`（ULID/UUID），`review_id` 仍由服务端生成且唯一；审查表同时设 `idempotency_key` 列与 `(tenant_id, idempotency_key)` 唯一约束。重复投递按幂等键去重并返回原 `review_id`，而不是再次生成新键——否则规格中的「重复投递只落一条」无法成立。
- **A 类不变量承载**：终态不可回写、状态迁移、严重度 UNKNOWN 等规则已在领域模型（`SourcingProjectStatus`、`Qualification`、`Onboarding`、`Severity`），切换仓储不重写领域规则；仅把 `InMemoryQualificationRepository` 中的「同一供应商 IN_REVIEW 唯一」与「可用证照查询」迁到持久化仓储默认方法或应用服务条件，DDL 条件唯一键兜底。
- **PII 与注入护栏**：复用 `ContractReviewPortAdapter` 已有的 `HeuristicInjectionGuard`、`containsPii` 与 `PII_REJECTED` 轨迹入口，持久化 Adapter 不重复实现扫描；缺口径时以现有归档 spec 的 fail-closed 行为为准。
- **测试库与重启模拟**：H2 命名内存库（`DB_CLOSE_DELAY=-1`）在两个 ApplicationContext 间共享，模拟进程重启后的读回；不引入 Testcontainers 或共享测试 MySQL，真实 MySQL 验收仍属 L3。

## Migration Plan

1. 切换 A 类三个仓储：扩充扫描包、移除内存 bean、跑模块单测与 DDL 对账。
2. 新增 B 类 6 张 init-only 表与实体/仓储/Adapter，保留能力开关与端口语义；补持久化与负向测试。
3. 新增 `persistenceGate` / `persistenceProbe` 并同步三入口、常量、探针与证据清单；注入反证实测必红。
4. 全量复验：Maven 全模块 + ArchUnit、门禁主判据与 self-test、evalset 门禁零漂移、前端类型/lint(json)/单测。
5. Verify 通过后归档；不执行生产库，不接触 owner 冻结项。回滚策略：本轮全部为 init-only 与源码改动，逐提交 revert 即可恢复内存态骨架（仅丢失加固后写入的测试数据）。
