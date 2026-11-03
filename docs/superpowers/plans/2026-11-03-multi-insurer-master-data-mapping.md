# 多保司主数据映射 P0 内核 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让"保司码 → 标准码"成为一条有数据库强制约束、有未命中队列、可治理的解析链：命中唯一则解析，多义/拆分/未命中一律 fail-closed，绝不回落空串。

**Architecture:** 六张身份型表（主体 / 保司码表 / 映射 / 字段绑定 / 标准码别名 / 标准码拆分）+ 一张记录型表（未命中队列）。唯一键**不含 `deleted`**（否则同码只能软删一次），映射的当前有效性由 `active_flag='Y'` 表达（MySQL 唯一索引允许多 NULL）；标准基线用 `'*'` 而非 NULL（NULL 不参与判重）。解析内核是纯函数式领域组件：调用方先按 `(保司, 域, 码)` 取候选，再交给内核裁决，内核不依赖仓储。

**Tech Stack:** Java 21 + Spring Boot 3.5、bone-metadata-sdk（Criteria 仓储）、Flyway、JUnit 5 + AssertJ。

**规格：** `openspec/changes/multi-insurer-biz-identity-catalog-mapping/design.md`（Comet Design Doc）
**权威设计：** `doc/ai-product/synapClaim/04-design/多保司保单配置面与主数据映射设计.md`

## Global Constraints

- 持久化只用 bone-metadata-sdk；禁止 `SELECT ... FOR UPDATE`（并发靠唯一键 fail-closed）。
- 解析结果**不得**回落空串/`0`：未命中必须 `MISSING` 且 `standardCode == null`。
- 身份型表只"关闭"不删除：不调用 `deleteById`，不用 `deleted` 做业务判断。
- 领域层不得依赖 infrastructure；仓储接口方法名避开 `insert/update/delete/exists/merge/flush/persist`。
- 新增 `@Table` 实体必须同时在 `bone-init.sql` 有建表，否则 `EntityTableDdlCoverageTest` 转红。
- 改 Java 后执行 `mvn -o -pl bone-apps/synap-claim spotless:apply`（仅本模块）。

---

### Task 1: 建表（迁移 + init 同步）

**Files:**
- Create: `bone-apps/synap-claim/src/main/resources/db/migration/V20__synap_claim_master_data_mapping.sql`
- Modify: `bone-init.sql`（追加 7 张表，位置在 claim 表簇末尾）

**Interfaces:**
- Produces: 表 `t_claim_insurer` / `t_claim_insurer_code_item` / `t_claim_code_mapping` / `t_claim_field_binding` / `t_claim_std_code_alias` / `t_claim_std_code_split` / `t_claim_mapping_miss`

- [ ] **Step 1: 写迁移脚本**，字段按权威设计 §6.3，含框架列（tenant_id/created_by/created_at/updated_by/updated_at/version/deleted）
- [ ] **Step 2: 同步 `bone-init.sql`**，逐表复制同一份 DDL
- [ ] **Step 3: 验证**：`mvn -o -pl bone-apps/synap-claim test -Dtest=EntityTableDdlCoverageTest`

### Task 2: 实体与仓储

**Files:**
- Create: `domain/model/mapping/{ClaimInsurer,InsurerCodeItem,CodeMapping,FieldBinding,StdCodeAlias,StdCodeSplit,MappingMiss}.java`
- Create: `domain/repository/{ClaimInsurer,InsurerCodeItem,CodeMapping,FieldBinding,StdCodeAlias,StdCodeSplit,MappingMiss}Repository.java`

**Interfaces:**
- Produces: `CodeMappingRepository.findActive(tenantId, insurerCode, domain, insurerRawCode)` → `List<CodeMapping>`
- Produces: `MappingMissRepository.findByKey(tenantId, insurerCode, domain, rawCode)` → `MappingMiss`

- [ ] **Step 1: 实体**：`TenantAggregateRoot<Long>` + `@Table` + `@Column` + `@Version`，静态 `of(...)` 工厂与 `close()/activate()` 领域方法
- [ ] **Step 2: 仓储**：一律用 `Criteria` default 方法（不新增 SQL 模板，避免模板列漂移）
- [ ] **Step 3: 验证**：`mvn -o -pl bone-apps/synap-claim compile`

### Task 3: 解析内核 + 单测（先红后绿）

**Files:**
- Create: `domain/model/mapping/{MappingResolution,ResolutionStatus,MasterDataDomain}.java`
- Create: `domain/model/mapping/CodeMappingResolver.java`
- Test: `test/java/com/bone/claim/domain/model/mapping/CodeMappingResolverTest.java`

**Interfaces:**
- Produces: `MappingResolution resolve(String rawCode, String disambiguationKey, List<CodeMapping> actives, List<StdCodeAlias> aliases, boolean splitParent)`
- `MappingResolution(status, standardCode, mappingId, reasonCode)`；`standardCode` 仅在 `RESOLVED` 时非空

- [ ] **Step 1: 写会红的用例**：A1 命中、A2 未命中为 null、A3 消歧键、A4 MERGE 重定向、A5 SPLIT 拒绝、别名成环
- [ ] **Step 2: 实现内核**（候选过滤 → 消歧 → canonicalize → split 拦截）
- [ ] **Step 3: 验证**：`mvn -o -pl bone-apps/synap-claim test -Dtest=CodeMappingResolverTest`

### Task 4: 未命中队列的记账规则 + 单测

**Files:**
- Modify: `domain/model/mapping/MappingMiss.java`（`recordOccurrence()` / `resolve(mappingId)` / `ignore(reason)`）
- Test: `test/java/com/bone/claim/domain/model/mapping/MappingMissTest.java`

- [ ] **Step 1: 用例**：重复未命中累加 `occurCount`；已解决后再未命中 `reopenCount+1` 且回 `OPEN`
- [ ] **Step 2: 实现**
- [ ] **Step 3: 验证**：`-Dtest=MappingMissTest`

### Task 5: 应用服务 + REST

**Files:**
- Create: `application/CodeMappingApplicationService.java`
- Create: `adapter/web/controller/CodeMappingController.java`

**Interfaces:**
- `resolve(ResolveCommand)` → `MappingResolution`（未命中写队列）
- `listMisses(status, page, size)` / `resolveMiss(id, standardCode, operatorId)` / `ignoreMiss(id, reason)`
- `publish(PublishCommand)`：同键先关旧行再插新行（`active_flag` 由唯一键兜底）

- [ ] **Step 1: 应用服务**（`@Transactional`，租户经 `TenantPort`）
- [ ] **Step 2: 控制器**（`/api/v1/claim-config/mappings`，权限复用 `claim:policy:read/write`）
- [ ] **Step 3: 验证**：`mvn -o -pl bone-apps/synap-claim test`

### Task 6: 收尾

- [ ] **Step 1: `mvn -o -pl bone-apps/synap-claim spotless:apply`**
- [ ] **Step 2: 全量 `mvn -o -pl bone-apps/synap-claim test`**
- [ ] **Step 3: `./scripts/check.sh`**
