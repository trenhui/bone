# 工作流引擎 SDK 持久化迁移 — Review 快照

> **commit**: `dc1be80ae2bda5f2b0fc05f3aa1e8efb3d11411b`
> **父**: `8e350e264`（Gitee dev @ 推送时）
> **作者**: renhui.trh
> **日期**: 2026-10-24 19:24 +0800
> **双远程**: Gitee `1b9435f00`（含本提交 + 2 后续提交）｜GitHub `dc1be80ae`（本提交）
> **主题**: feat(workflow): 引擎持久化全量收敛至 SDK @Sql 通道，移除 HC-006 五条豁免

---

## 1. 变更概要

- **目标**：把裸 JDBC 持久化（`JdbcWorkflowInstanceRepository` / `JdbcWorkflowDefinitionRepository` / `WfSql`）整体改写为 `bone-metadata-sdk` 的 `@Sql` 仓储，移除 HC-006 五条技术豁免（`technicalExemptions` 25→20、`exemptionReasons` 26→21），HC-006 防线恢复零豁免口径；ArchUnit 绝对门禁（starter 禁 `jdbc`/`java.sql`）随之**真生效**。
- **规模**：47 个文件条目 = **4 删除 / 22 新增 / 20 修改 / 1 重命名**。
- **质量**：SDK 321 + core 70 + starter 56 = **447 测试全绿**；spotless 已 apply；`scripts/check.sh` 门禁 38/38 绿。

---

## 2. 文件清单（按模块）

### 2.1 删除（旧裸 JDBC 实现，D）
- `starter/adapter/JdbcWorkflowDefinitionRepository.java`
- `starter/adapter/JdbcWorkflowInstanceRepository.java`
- `starter/persistence/WfSql.java`
- `starter/adapter/JdbcWorkflowDefinitionRepositoryH2Test.java`（测试）
- `starter/adapter/JdbcWorkflowInstanceRepositoryH2Test.java` → 重命名为 `SdkWorkflowInstanceRepositoryH2Test.java`（R091，91% 相似）

### 2.2 新增 — SDK `@Sql` 仓储族（`sdk/` 包，Row + Repository 成对）
- `sdk/WfInstanceRow.java` / `sdk/WfInstanceSdkRepository.java`
- `sdk/WfTaskRow.java` / `sdk/WfTaskSdkRepository.java`（底层 11 方法：insertTask / insertTaskIfAbsent / findTaskRow / listPendingTaskRows / listTaskRowsByNode / listTaskRowsByAssignee / listTaskRows / compareAndSetTaskStatus / reassignTask / cancelPendingTasks / cancelPendingTasksOfNode）
- `sdk/WfProcessDefRow.java` / `sdk/WfProcessDefSdkRepository.java`
- `sdk/WfBindingRow.java` / `sdk/WfBindingSdkRepository.java`
- `sdk/WfStatusOutboxRow.java` / `sdk/WfStatusOutboxSdkRepository.java`
- `sdk/WfActiveInstanceRow.java` / `sdk/WfActiveInstanceSdkRepository.java`

### 2.3 新增 — 适配器 / 绑定 / 配置
- `adapter/SdkWorkflowDefinitionRepository.java`
- `adapter/SdkWorkflowInstanceRepository.java`（领域级契约，21 方法）
- `binding/WorkflowBindingRepository.java`（TenantContext fail-closed、多绑定 fail-fast）
- `config/WorkflowSdkRepositoryConfig.java`（`@EnableSqlRepositories` 装配）

### 2.4 新增 — 测试
- `adapter/SdkWorkflowDefinitionRepositoryH2Test.java`
- `adapter/SdkWorkflowInstanceRepositoryH2Test.java`（含 `commentContainingSqlKeywords_roundTripsThroughSdkChannel`：自由文本 `please update the amount; DROP TABLE wf_task` 原样存取，验证 SDK 通道不拦截合法文本）
- `binding/WorkflowBindingRepositoryH2Test.java`（8 方法：bindingHit / noBinding / disabledBinding / softDeletedBinding / duplicateActiveBindings_failFast / missingTenantContext_failsClosed / bindingIsTenantScoped 等）
- `sdk/WfProcessDefSdkRepositoryH2Test.java`
- `sdk/WorkflowSdkTestContext.java`（5 bean：embeddedDatabase / namedParameterJdbcTemplate / jdbcTemplate / dataSourceProperties / objectMapper）
- `sql/processor/MyBatisSqlProcessorTest.java`（metadata-sdk，8 例）
- `sql/template/TemplateSecurityValidatorTest.java`（metadata-sdk，7 例）

### 2.5 新增 — 生产迁移 DDL
- `scripts/migration/0056_wf_workflow_tables.sql`（六表，详见 §4.2）

### 2.6 修改（M）
**引擎核心**
- `core/ports/spi/WorkflowInstanceRepository.java`（端口含 `saveTaskIfAbsent`）
- `core/runtime/WorkflowEngineImpl.java`（存储层 CAS 去重 + 终态通知）
- `core/runtime/WorkflowEngineImplTest.java`
- `starter/WorkflowBoneIntegrationAutoConfiguration.java`（装配切换）
- `starter/outbox/RuntimeActionIntentExecutor.java`、`StatusProjectionRelay.java`、`WorkflowStatusOutboxRepository.java`（outbox WfSql-free + 窗口 + purgeDone）
- `starter/.../META-INF/spring/...AutoConfiguration.imports`
- `starter/test/.../WorkflowEngineWiringH2Test.java`、`StarterArchitectureTest.java`、`StatusProjectionRelayH2Test.java`、`wf-schema-h2.sql`
- `README.md`

**SDK 共享缺陷修复**（见 §3.6）
- `metadata-sdk/sql/processor/MyBatisSqlProcessor.java`
- `metadata-sdk/sql/proxy/RepositoryFactoryBean.java`
- `metadata-sdk/sql/template/TemplateSecurityValidator.java`

**基线 / 文档 / 门禁**
- `doc/architecture/doc-code-symbols-baseline.json`（追加 7 条登记）
- `doc/architecture/sdk-persistence-bypass-baseline.json`（豁免 25→20）
- `doc/design/modules/12. 工作流引擎设计.md`（§9/§10/§7 写实）
- `scripts/check.sh`（聚合器 + `-pl` + `-am` 修跨模块编译假错）

---

## 3. 核心设计决策（与设计文档 §9/§10 对照）

### 3.1 持久化通道收敛
裸 JDBC（`Jdbc*` + `WfSql`）全部改写 SDK `@Sql` 代理，SQL 全文原样继承旧版，H2 原样执行做并发回归。HC-006 豁免 5 条移除，ArchUnit 绝对门禁（starter 禁 `jdbc`/`java.sql`）真生效。

### 3.2 并发纪律（三 CAS + 条件插入）
- `compareAndSetPhase`：phase 单 guard（端口签名只带 expectedVersion，guard phase）
- `compareAndAdvanceNode` / `compareAndSetTaskStatus`：任务状态**带实例终态守卫**（避免终态后被并发改）
- `saveTaskIfAbsent`：条件插入（`INSERT ... WHERE NOT EXISTS PENDING`）防会签节点并发双插待办
- 活动指针槽位 `wf_active_instance` 以 `(tenant_id, entity_code, record_id)` 自然主键，插入撞主键 → `DuplicateKeyException` → `reclaimIfStale` 对账后重试或失败（一单据一活动实例原子闸门）

### 3.3 终态通知
`withdraw` / `terminateInstance` / `completeTask(approved)` / `terminateAsRejected` 均调 `notifyTerminated`；`resume` / `assignTask` 派单调 `notifyTaskCreated`。

### 3.4 outbox 对账窗口 + purgeDone
`RECONCILE_WINDOW`(6h) / `DONE_RETENTION`(7d) 常量；`markDone` 从不置 deleted；`purgeDoneStatusIntents` 按保留期物理清理 > 对账窗口；对账判据「一行都没有」（防 FAILED 实例被每轮重复补投）；`StatusProjectionRelay.runReconcile` 确定性补投 + `@Scheduled` 30s + 每日 3:30 物理清理。

### 3.5 wf_binding 访问组件
`WorkflowBindingRepository`：TenantContext **fail-closed**（缺租户上下文即失败）、多绑定 **fail-fast**（`IncorrectResultSizeDataAccessException` 而非任选一条）。

### 3.6 SDK 三处共享缺陷修复（均带回归用例）
1. `RepositoryFactoryBean`：不再把 `DataAccessException` 包装成 `IllegalStateException`（活动槽位抢占 / 对账补投依赖 `DuplicateKeyException`）。
2. `TemplateSecurityValidator`：不再误禁合法 `DELETE` DML（软删不释放主键会把「终态后可重提」永久焊死）。
3. `MyBatisSqlProcessor`：`#{}` 显式 null 绑 `NULL`（区分缺失参数保持 fail-fast）；移除值级 SQL 关键字 / 分号误扫（真注入面只有 `${}`，由标识符约束 + `tableName` 白名单把守）。

---

## 4. 关键实现佐证

### 4.1 SdkWorkflowInstanceRepository 领域契约（21 方法，节选）
| 方法 | 语义 |
|---|---|
| `claimAndSaveInstance` / `saveInstance` | 实例落库（带操作人） |
| `findInstance` / `findInstanceByRecord` | 查询 |
| `compareAndSetPhase` | phase 单 guard CAS |
| `claimActiveInstance` / `releaseActiveInstance` / `insertActivePointer` / `reclaimIfStale` | 活动指针原子闸门 |
| `compareAndAdvanceNode` | 节点推进 CAS |
| `saveTask` / **`saveTaskIfAbsent`** | 任务落库 / 条件插入去重 |
| `findTask` / `listPendingTasks` / `listTasksByNode` / `listTasksByAssignee` / `listTasks` | 任务查询 |
| `compareAndSetTaskStatus` | 任务状态 CAS（带实例终态守卫） |
| `reassignTask` / `cancelPendingTasks` / `cancelPendingTasksOfNode` | 任务重指派 / 取消 |

### 4.2 0056 生产 DDL（六表）
- **wf_instance**：实例主表，id `VARCHAR(64)`，tenant_id `BIGINT DEFAULT 0`，phase/status 等，idx_wf_instance_record(tenant_id,entity_code,record_id)
- **wf_task**：待办，comment `VARCHAR(1024)` 自由文本（可含 SQL 字样），idx_wf_task_assignee / idx_wf_task_instance
- **wf_active_instance**：**(tenant_id,entity_code,record_id) 自然主键**，无 id 列，一单据一活动实例原子闸门
- **wf_status_outbox**：状态意图发件箱，params_json `TEXT`（承载自由文本原因），idx_outbox_poll / idx_outbox_instance
- **wf_process_def**：definition_json `JSON`（写入期校验）
- **wf_binding**：实体绑定
- 通用：deleted `TINYINT(1)`、version `INT` 乐观锁、时间列 `DATETIME(3)` 代码显式写入、ENGINE=InnoDB utf8mb4
- 模型取舍：id 用 `VARCHAR(64)`（平台雪花 String）刻意区别于其余表 BIGINT；params_json 用 TEXT 不用 JSON（JSON 会拒合法自由文本写入）

---

## 5. 测试覆盖
- **starter 56 + core 70 + SDK 321 = 447 全绿**（rebase 后新基线重跑 core+starter 经 `-am` 覆盖 SDK 依赖，0 失败）
- 重点用例：
  - `commentContainingSqlKeywords_roundTripsThroughSdkChannel`：自由文本含 `DROP TABLE` 原样存取
  - `WorkflowBindingRepositoryH2Test`：多绑定 fail-fast / 缺租户 fail-closed
  - SDK 三缺陷修复各带回归用例（RepositoryFactoryBean / TemplateSecurityValidator / MyBatisSqlProcessor）

---

## 6. 集成冲突处理（推送前）
- 本地基点 `d7e1e93ef` 与远端 dev 在 `9aa295071` 分叉，互非祖先；远端已在同批 workflow 文件另做一套设计（删 `saveTaskIfAbsent`、删终态通知、简化 outbox、仍引用已删 `WfSql`）。
- 用户拍板「**以我的设计为准**」：`rebase --onto <Gitee tip>` 仅重放我的提交，冲突文件恢复我的版本，`git rm` 旧 Jdbc/`WfSql`，保留非冲突远端改动（ai-rag / WorkflowBranchEvaluator 等）。

---

## 7. 风险与注意
- **GitHub 落后 Gitee 2 提交**：GitHub dev=`dc1be80ae`，Gitee dev=`1b9435f00`（含并行会话后续 2 提交）。如需完全镜像：`git push git@github.com:trenhui/bone.git 1b9435f00:dev`。
- **主树未动**：主树当前 `gitignore-fix2` 分支，本提交通过 worktree 推送，未切主树。
- HC-006 豁免移除后，ArchUnit 绝对门禁真生效：后续任何 workflow starter 内新增 `jdbc`/`java.sql` 引用将被门禁拦截。

---

## 8. Review 勾选建议
- [ ] SDK 三 CAS 并发语义（phase 单 guard / 任务带实例终态守卫 / 条件插入）符合 §10 设计
- [ ] `wf_active_instance` 自然主键 + `reclaimIfStale` 对账闭环
- [ ] outbox `params_json` TEXT（非 JSON）不丢自由文本原因
- [ ] SDK 三缺陷修复回归用例有牙齿（变异验证：删实现即红）
- [ ] 0056 与 H2 schema 逐列同构，生产可重复执行（IF NOT EXISTS）
- [ ] spotless / check.sh 通过（已自证 38/38）
