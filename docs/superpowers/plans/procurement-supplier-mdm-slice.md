---
change: procurement-supplier-mdm-slice
design-doc: openspec/changes/procurement-supplier-mdm-slice/design.md
base-ref: c395e4b603202c3567408c1843f8ed9b232ffa26
archived-with: 2026-11-16-procurement-supplier-mdm-slice
---

<!-- comet-task-authority: openspec/changes/procurement-supplier-mdm-slice/tasks.md -->

# Build Plan — 采购供应商主数据模块（最小可验证切片）

> 配置：`build_mode=autonomous` / `tdd_mode=tdd` / `review_mode=standard`（已由用户确认，2026-11-15）。
> 任务正文与完成状态以 `tasks.md` 为唯一权威；本计划只描述范围、依赖、约束、实施顺序与验收方式，不维护第二份勾选账本。
> 技术设计以 `design.md` 的 D1–D7 为准，行为需求以 `openspec/changes/procurement-supplier-mdm-slice/specs/procurement/supplier-mdm/spec.md`（7 Requirement / 16 Scenario）为准。

## 1. 基线事实（已现场核实，非推断）

| # | 事实 | 证据 |
|---|---|---|
| B1 | `bone-masterdata` 已含 Golden Record 全链内核：`GoldenRecordMerger.merge/rollback`、`FieldProvenance`、`FieldSurvivorshipRule`、`MergeResult` | `domain/model/goldenrecord/GoldenRecordMerger.java:36,159`、`MergeResult.java` |
| B2 | 身份解析已具备且冲突 fail-closed：`register/resolve/localIdsOf` 三方法，同键不同 partyId 直接抛 | `domain/model/identity/EntityKeyMappingRegistry.java:14,35,53` |
| B3 | 租户取值契约：`CurrentUserPort.currentTenantId()/requireTenantId()`，令牌 claim 为源 | `domain/gateway/CurrentUserPort.java:17,20` |
| B4 | 能力开关范式与「关闭即不装配」负向探针已有先例 | `adapter/web/controller/GoldenRecordController.java:32-35`、`e2e/GoldenRecordCapabilityGateTest.java:21-35` |
| B5 | 应用层租户一致性校验范式：`requireSameTenant(Long)` fail-closed | `application/GoldenRecordApplicationService.java:257-266` |
| B6 | 统一响应与权限点：`ApiResponse<T>` / `PageResult<T>`，`masterdata:governance:read|write` | `adapter/web/controller/GoldenRecordController.java:44,58`、`MasterDataEntityController.java:103` |
| B7 | 路径常量前缀唯一 | `bone-framework/bone-core/src/main/java/com/bone/core/web/PlatformApiPaths.java:8` |
| B8 | 模块内**不存在** `Map<State, Map<Event, Transition>>` 实现，本 change 建立该范式 | `Grep 'Map<State, Map<Event' bone-platform/` → 0 命中 |
| B9 | ArchUnit 冻结库 `allowStoreUpdate=false` / `allowStoreCreation=false`，10 个冻结规则基线文件均为 0 字节 ⇒ **当前零违规**，任何新增违规必红 | `archunit_store/stored.rules`、`src/test/resources/archunit.properties` |
| B10 | `adapter → application` 依赖被**显式允许**：「a well-named *ApplicationService is a valid default inbound entry」 | `bone-architecture-test/.../BoneDddArchRules.java`（stored.rules 条目 `a9fc8889…`） |
| B11 | `noCrossContextDomain/Model` 以 `com.bone.masterdata` 为自身根，模块内 `supplier` 引用 `goldenrecord` **不构成**跨上下文违规 | `BoneDddArchRules.java:1141-1160`（`isWithinOrEquals(name, selfRootPackage)` 先置 false） |
| B12 | 配置键契约门禁只校验 `@Value` / `@ConfigurationProperties`，不校验 `@ConditionalOnProperty` | `scripts/ci/check-config-key-contract.py:28-31` |
| B13 | 规模基线：main 240 java / test 62 java；`target/` 无既有 surefire 报告 ⇒ 需在本轮建立真实基线数字 | `find src -name '*.java' | wc -l` |

## 2. 交付结构（新增文件清单，路径已按模块真实分层确定）

### 2.1 domain（`adapter → application → domain ← infrastructure`，domain 不依赖外层）

```
domain/model/supplier/
  LegalEntity.java               身份载体：unifiedSocialCreditCode/taxId + partyId
  SupplierState.java             PROSPECT | ONBOARDING | ACTIVE | SUSPENDED | CORRECTIVE | EXITED
  SupplierEvent.java             onboarding_submitted | onboarding_approved | risk_exceeded |
                                 restored | corrective_entered | exit_approved
  SupplierTransition.java        from/event/to + 守卫结论
  SupplierLifecycle.java         Map<State, Map<Event, Transition>> + assertTransition
  Supplier.java                  合作资格聚合根（持有 SupplierState）
  VendorState.java               PENDING_VENDOR | ACTIVE_VENDOR | PAYMENT_BLOCKED | INACTIVE_VENDOR
  VendorEvent.java               activation_passed | payment_block_raised | payment_block_released |
                                 deactivate | restored
  VendorTransition.java
  VendorLifecycle.java
  Vendor.java                    交易与付款资格聚合（持有 VendorState，独立于 Supplier）
  SupplierExitContext.java       退出前置事实（未结 PO 数 / 未结合同数 / 未清应付额 / 未关闭 Vendor 数）
  VendorActivationContext.java   开通闸门事实（准入/银行/税务/付款条件/重复发票 五项结论）
  TransitionCommandSource.java   DOMAIN | EXTERNAL_FINANCE —— D7 命令来源
  SupplyRelationship.java        Purchasing Org data 聚合根
  SupplierItem.java              供货条目
```

### 2.2 domain/gateway（出站端口，与既有 `GoldenRecordPort` 同位置）

```
domain/gateway/SupplierRepositoryPort.java    findCurrent(tenant,partyId) / pageByTenant(...) / save(...)
domain/gateway/SupplierExitGuard.java         boolean canExit(SupplierExitContext)
domain/gateway/VendorActivationGuard.java     boolean canActivate(VendorActivationContext)
```

### 2.3 application

```
application/SupplierApplicationService.java
application/command/RegisterSupplierCommand.java
application/command/TransitionSupplierCommand.java
application/command/TransitionVendorCommand.java
application/query/qry/SupplierPageQuery.java          page / size / keyword / supplierState
application/query/dto/SupplierStateDTO.java           仅 partyId/states/versions/summary，无 PII
application/query/dto/SupplierRegistrationResultDTO.java
application/query/dto/SupplierTransitionResultDTO.java
```

### 2.4 adapter

```
adapter/web/controller/SupplierController.java
```

### 2.5 test（夹具与探针，`src/test/java`，不进生产制品）

```
testsupport/supplier/SupplierHttpTestConfiguration.java    三个端口 bean 的内存实现装配
testsupport/supplier/InMemorySupplierRepositoryPort.java
testsupport/supplier/AllowAllSupplierExitGuard.java         正向后放行（负向探针另行覆写）
testsupport/supplier/AllowAllVendorActivationGuard.java
domain/model/supplier/SupplierLifecycleTest.java
domain/model/supplier/VendorLifecycleTest.java
domain/model/supplier/LegalEntityTest.java
domain/model/supplier/SupplyRelationshipTest.java
domain/model/supplier/SupplierIdentityResolverTest.java
application/SupplierApplicationServiceTest.java
application/SupplierMergeAssemblyTest.java
adapter/web/controller/SupplierControllerContractTest.java
e2e/SupplierCapabilityGateTest.java
e2e/SupplierHttpIntegrationTest.java
```

## 3. 硬约束（实施时必须逐条满足，违反即门禁必红）

| 约束 | 判据 | 处置 |
|---|---|---|
| C1 ArchUnit 10 条冻结规则当前零违规、冻结库只读 | B9 | 新代码不得触碰 `domain.store`、`*UseCase`、`TenantContext` 直调、`*BusinessException`、adapter 注入 `*Manager`/`domain.repository`/`domain.service` |
| C2 `applicationSaveMustPairWithPublishOrExempt`（E-5.4，非冻结） | `BoneDddArchRules.java:1245` | 端口名以 `Port` 结尾不命中 `owner.endsWith("Repository")`；仍在 `SupplierApplicationService` 上声明 `@NoDomainEvent` 并在 JavaDoc 写明豁免理由（本切片状态迁移属内部状态迁移，Outbox 分发属 R-2 owner 前置） |
| C3 `adapter → application` 合法 | B10 | `SupplierController` 只注入 `SupplierApplicationService`，不注入端口 |
| C4 租户只从登录态取 | B3/B5 | `SupplierApplicationService` 用 `currentUserPort.currentTenantId()`；命令自报 `tenantId` 不一致即 `DomainException` |
| C5 配置键显式声明 | B12 | `application.yml` 增加 `bone.procurement.supplier-mdm.enabled: ${BONE_PROCUREMENT_SUPPLIER_MDM_ENABLED:false}`，默认关闭 |
| C6 无 DDL / 无 SQL / 不碰 `bone-init.sql` | 设计 Non-Goals | 端口的内存实现放 `src/test/java`，生产侧无 adapter ⇒ `enabled=true` 而缺 bean 时启动即失败（fail-closed，不产生假能力） |
| C7 状态机 fail-closed | LLD `01 卷 §5` | 迁移表未列出即抛 `DomainException`，消息携带 `current_state / attempted / allowed[]` |
| C8 不修改既有 Golden Record 内核 | 设计 D3 | 只调用 `GoldenRecordMerger` / `EntityKeyMappingRegistry` / `GoldenRecordPort`，其文件零改动 |

## 4. 任务分解与实施顺序

### 阶段 A — 领域地基（1.1 → 1.2 → 1.3）

- **A1** `LegalEntity` + `Supplier` / `Vendor` 双角色聚合，各自持有互不覆盖的状态字段；`Supplier` 不暴露任何付款/银行/冻结语义，`Vendor` 不暴露寻源/绩效语义 `<!-- comet-task-ref:378bcf63-9f1b-4477-9b61-aff4ed103bf9 -->`
  - RED：`LegalEntityTest` 先写「同一 partyId 同时持双角色」「以 Vendor 视角取合作资格状态 → 不返回」「Supplier 置 SUSPENDED 不改动 Vendor 状态」三例
  - GREEN：最小实现通过；`SupplierState`/`VendorState` 为独立枚举，无共享基类
- **A2** `SupplyRelationship` 聚合根 + `SupplierItem` 供货条目（Purchasing Org data，Owner = 采购） `<!-- comet-task-ref:d9bc9167-3e52-45a5-87ad-b265a3171c3b -->`
  - RED：`SupplyRelationshipTest` 覆盖「关系必须绑定已有 partyId」「条目不可变（改价产出新条目）」「同 org 下重复 itemCode 拒绝」
- **A3** 身份解析：复用 `EntityKeyMappingRegistry`，本地编码 → partyId，不自建 dedup `<!-- comet-task-ref:6bc3c1fd-989a-4a57-afdb-bbd4ed98a40b -->`
  - RED：`SupplierIdentityResolverTest` 覆盖「跨租户候选 → 抛」「同键多 partyId → 抛」「无映射且无显式 partyId → 抛」
  - 断言点是既有 `registry.resolve/register` 的异常语义，不新写去重逻辑

### 阶段 B — 合并装配与双状态机（2.1 → 2.2 / 2.3 → 2.4）

- **B1** 供应商合并装配：调用 `GoldenRecordMerger` + `FieldSurvivorshipRule`，把 `MergeResult` 映射为供应商视图 DTO `<!-- comet-task-ref:5c0701a1-6bda-43d4-ae12-eba999c3f66a -->`
  - RED：`SupplierMergeAssemblyTest` 覆盖「`APPLIED` 才产出视图」「缺 lineage → 内核整笔拒绝」「`MANUAL_REVIEW` → 不落库不产出」
  - 关键：**不复制**内核逻辑，`SupplierExitGuard`/开关装配之外无第二份裁决
- **B2** `SupplierLifecycle`：`PROSPECT → ONBOARDING → ACTIVE ⇄ SUSPENDED / CORRECTIVE → EXITED` `<!-- comet-task-ref:16ea2af2-fd25-4f34-a769-8a1b4392d32b -->`
  - RED：`SupplierLifecycleTest` 四例——合法路径 / `PROSPECT` 直收 `onboarding_approved` 被拒（含 `allowed[]`）/ `EXITED` 收任一事件被拒 / `ACTIVE` 收 `exit_approved` 但 `SupplierExitGuard=false` 被拒
  - `EXITED` 在迁移表中**无任何 from 行**，终态不回写由表结构本身保证
- **B3** `VendorLifecycle`：`PENDING_VENDOR → ACTIVE_VENDOR ⇄ PAYMENT_BLOCKED → INACTIVE_VENDOR` `<!-- comet-task-ref:e6b6f634-7fe9-4bad-8055-7b9332ae59b1 -->`
  - RED：`VendorLifecycleTest` 四例——闸门全通过才开通 / 未经闸门直置被拒 / 域内来源 `payment_block_released` 被拒（D7）/ 有进行中 PO 时 `deactivate` 被拒
  - `INACTIVE_VENDOR → ACTIVE_VENDOR` 不在表中 ⇒ 必须重走 `PENDING_VENDOR` 闸门
- **B4** 负向探针：具名裁决缺失整笔转人工、零副作用 `<!-- comet-task-ref:795f60e9-859d-4045-9c5e-23b7f4f7f926 -->`
  - RED：`STEWARD_OVERRIDE` 无 `stewardAccountId` → `MANUAL_REVIEW`；断言 `repositoryPort` 写入次数 0、无事件

### 阶段 C — 应用服务与端口（3.1 → 3.2 → 3.3）

- **C1** `SupplierRepositoryPort` + 内存实现 `<!-- comet-task-ref:c43f9557-28af-49ca-9e05-b71d4432934d -->`
  - 端口只声明 `findCurrent` / `pageByTenant` / `save`；内存实现放 `src/test/java`（C6）
- **C2** `SupplierApplicationService` 四用例：合并解析、Supplier 迁移、Vendor 迁移、当前状态查询 `<!-- comet-task-ref:33fe065b-9a87-488f-ae6c-df0f7fa3cee9 -->`
  - RED：四用例各一例 + 「命令 tenantId ≠ 登录租户 → 拒绝且零副作用」
- **C3** 能力开关 `bone.procurement.supplier-mdm.enabled`（默认关闭） `<!-- comet-task-ref:467ccb12-9aac-4877-a801-5fe9957823e9 -->`
  - `@ConditionalOnProperty(prefix="bone.procurement.supplier-mdm", name="enabled", havingValue="true")` 同时挂在应用服务与 Controller
  - RED：上下文测试断言关闭态不装配、开启态装配

### 阶段 D — Adapter 与 HTTP 契约（4.1 → 4.2 → 4.3）

- **D1** `SupplierController`：`ApiResponse<T>` / `PageResult<T>`，写 `masterdata:governance:write`、读 `masterdata:governance:read`，不裸返领域对象 `<!-- comet-task-ref:2b945214-f392-45ef-9790-767cdc23c9e4 -->`
  - 端点：`POST /api/v1/masterdata/suppliers`（登记/合并）、`POST /api/v1/masterdata/suppliers/{partyId}/supplier-transitions`、`POST /api/v1/masterdata/suppliers/{partyId}/vendor-transitions`、`GET /api/v1/masterdata/suppliers/{partyId}`、`GET /api/v1/masterdata/suppliers`
- **D2** 治理端点只返回裁决/版本/摘要，不回传字段原值或 PII `<!-- comet-task-ref:fde61e9a-4e4a-468c-9aa2-7f29c0dbdef0 -->`
  - RED：契约测试断言响应体不含 `unifiedSocialCreditCode` / `taxId` / 银行账号原值，只含 `partyId`（已脱敏标识）/`states`/`versions`/`summary`
- **D3** 开关关闭时端点不注册 `<!-- comet-task-ref:ae06ef26-86f1-4cb7-974b-581532afb295 -->`
  - RED：`SupplierCapabilityGateTest`（`webEnvironment=NONE` + `enabled=false`）断言 `getBeanNamesForType(SupplierController.class)` 与 `SupplierApplicationService.class` 均空

### 阶段 E — 集成验证与门禁回归（5.1 → 5.2 → 5.3）

- **E1** `mvn -o -f bone-platform/bone-masterdata/pom.xml clean test` 全绿，既有测试不退化 `<!-- comet-task-ref:aa155423-ca7f-43bc-a33b-99c9675b8c73 -->`
  - 先 `spotless:apply`（绑定 validate，未格式化将挡 test）
  - 本轮首次运行将建立真实基线数字；计划不预设数字，以「基线 = 改动前 HEAD 跑出的 surefire 总数」为准，新增测试使总数上升且失败数为 0
- **E2** 六类负向探针注入必红 + 还原必绿 `<!-- comet-task-ref:6eaa57cf-a90d-45bb-9177-496c9cccac48 -->`
  - 逐条：① 跨租户候选（改 `SourceRecord` tenant）② 身份冲突（构造两 partyId）③ 缺 lineage（删一条 `FieldProvenance`）④ 具名裁决缺失（去掉 `stewardAccountId`）⑤ 未结 PO 退出（`SupplierExitGuard=false`）⑥ 域内自发放行（`TransitionCommandSource.DOMAIN` 调 `payment_block_released`）
  - 每条独立执行、记录命令与红/绿结果；还原后全量复绿
- **E3** 文档族门禁不退化 `<!-- comet-task-ref:e4cc7283-e28a-44e8-811e-0cd9197d8a03 -->`
  - `bash doc/ai-product/procuraAI/scripts/check-procura-ci.sh`（主门禁 10/10、自检 146/146，本 change 不改文档族真源）
  - 附加：`python3 scripts/ci/check-config-key-contract.py`（C5 新增配置键后必须仍绿）

## 5. 依赖关系

```
1.1 ──┬─► 1.2
      ├─► 1.3
      ├─► 2.1
      ├─► 2.2 ─┐
      └─► 2.3 ─┴─► 2.4 ─► 3.1 ─► 3.2 ─► 3.3 ─► 4.1 ─► 4.2 ─► 4.3 ─► 5.1 ─► 5.2 ─► 5.3
```

- 串行为主：全部落在同一批包与同一条编译单元，并行会互撞。
- 1.2 / 1.3 / 2.1 可在 1.1 定型后交错，但仍以单线程顺序执行（autonomous）。
- 5.2 依赖 5.1 全绿（探针要能在真实测试树里注入）。

## 6. 验收方式（可复制执行的命令）

工作目录：`/Users/renhui.trh/code/bone/.worktrees/procurement-supplier-mdm-slice`

```bash
export JAVA_HOME=/Users/renhui.trh/tools/jdk/jdk-21.0.5.jdk/Contents/Home
MVN=~/.m2/wrapper/dists/apache-maven-3.6.3-bin/1iopthnavndlasol9gbrbg6bf2/apache-maven-3.6.3/bin/mvn

# 1) 格式化（绑定 validate，未格式化会挡 test）
"$MVN" -o -f bone-platform/bone-masterdata/pom.xml spotless:apply

# 2) 模块级全量测试（ArchUnit + 单测 + 应用测试 + 契约 + e2e 装配）
"$MVN" -o -f bone-platform/bone-masterdata/pom.xml clean test

# 3) 配置键契约（新增 bone.procurement.supplier-mdm.enabled 后必须仍绿）
python3 scripts/ci/check-config-key-contract.py

# 4) 文档族门禁不退化
bash doc/ai-product/procuraAI/scripts/check-procura-ci.sh
```

判定口径：
- 2) 退出码 0 且 surefire 失败数 0；**新增违规必红**由 C1 保证（冻结库只读，不会被静默写入基线）。
- 3) 无 FAIL（WARN 亦需为 0，新键已在 yml 显式声明）。
- 4) 主门禁 10/10、自检 146/146，与 change 前一致。

## 7. 风险与回退

| 风险 | 触发条件 | 处置 |
|---|---|---|
| R-1 冻结库只读导致新违规硬红 | 新增代码触碰 `domain.store` / `*UseCase` / `TenantContext` 直调 | 按 §3 C1 清单在实施时逐条自检；一旦红，改代码而非改基线与 `archunit.properties` |
| R-2 spotless 挡 test | 未跑 `spotless:apply` | 验收第 1 步固定执行；失败时先 apply 再重跑 |
| R-3 基线数字未知 | `target/` 无既有 surefire 报告 | E1 首次运行即建立基线，写回本计划「基线」小节；不预设「251 例」等未核实数字 |
| R-4 内存适配被误读为已接生产 | 文档/汇报口径 | 设计 D4 与 Risks 已标注 R-2 前置；验收只针对领域/应用/HTTP 行为 |
| R-5 跨域 Guard 恒真导致假绿 | 测试夹具用 `AllowAll*` 且无负向 | E2 ⑤⑥ 必须以「Guard=false / 来源=DOMAIN」注入并验证必红，杜绝恒真通过 |
| R-6 与并行会话冲突 | 他人推送同文件 | 本 change 只新增 `com.bone.masterdata` 下新包 + 3 处既有文件最小改动（`application.yml` 加 1 键）；推送前 fetch 核对 |

回退：移除 `domain/model/supplier`、`domain/gateway/Supplier*|VendorActivation*`、`application/SupplierApplicationService` 与相关 command/query、`adapter/web/controller/SupplierController`、测试夹具；撤销 `application.yml` 的 4 行配置。无持久化副作用，无 DDL，无数据迁移。

## 8. 明确不做（对齐设计 Non-Goals）

- 不写 `bone-init.sql` / `t_proc_*` DDL / 任何 SQL 迁移。
- 不实现生产 JDBC adapter（R-2）。
- 不实现其余 8 个采购模块；不接入真实 Finance / PO / 合同上游。
- 不修改既有 Golden Record 内核任何文件。
- 不碰 CI 三入口判据集合、`scripts/check.sh`、生产库/密钥/发布/tag。
- 不新建 Maven 模块、不改根 pom。
