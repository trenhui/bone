---
change: claim-report-case-one-to-many
design-doc: openspec/changes/claim-report-case-one-to-many/design.md
base-ref: 2a2e40134af620d0ac10697c9bdb85b1fe407a5a
---

<!-- comet-task-authority: openspec/changes/claim-report-case-one-to-many/tasks.md -->

# Build Plan — 报案 / 赔案拆分 1:0..N（实施口径）

> 本计划引用已批准的 `design.md` 与 `tasks.md`（Open/Design 已锁定为已接受证据，本轮不修改）。任务完成状态一律以 tasks.md 为准；本计划只以 `comet-task-ref` 映射任务 ID，不复制 checkbox。

## 1. 执行配置

`build_mode=autonomous` / `tdd_mode=direct` / `review_mode=standard` / `subagent_dispatch=null`（用户 2026-11-22 明确选择，`propose-build --confirmed` 已批准）。`tdd_mode=direct` 不要求先写红灯，仍为每条新行为补判别力单测，并用变异验证确认用例会红。

## 2. 基线与约束

- 工作区：独立 worktree，分支 `comet/claim-report-case-one-to-many`，base = `comet/claim-id-linkage` @ `2a2e40134`（含 Feistel 编号器与 0138 迁移）。
- `claim_no` 格式、语义、`uk_claim_no` 均不改；资金幂等键 `{claimNo}:{stage}:{attempt}` 与 OSS 路径依赖其稳定。
- `uk_tenant_invoice` 不得放松；新增报案不得成为跨发票重复赔付的绕过路径。
- 依赖顺序：编号内核 → 报案聚合 → 仓储 → DDL → 应用 → 接口 → 错误码/前端 → 文档 → 验证。DDL 与代码必须同批（漏一处即「配了不生效」）。
- 分层：`adapter → application → domain ← infrastructure`；Controller 返回 `ApiResponse<T>`；实体继承 `TenantAggregateRoot`；禁 `SELECT ... FOR UPDATE`。

## 3. 任务分组与验收

### 3.1 编号内核（domain，无框架依赖）

- 1.1 新增 `domain/model/claim/BizNoCodec.java`：抽出 Feistel 双射 + Crockford Base32 + Luhn32 + 日期段，暴露 `generate(prefix, snowflakeId, date)` / `isValid(prefix, no)` / `extractSnowflakeId(prefix, no)`，前缀不匹配抛 `IllegalArgumentException` <!-- comet-task-ref:80a58e80-dda1-476e-8ff6-4f99884d9725 -->
- 1.2 `ClaimNoGenerator` 改为 `BizNoCodec` 的薄封装（`CL` 前缀），保留全部既有公开方法签名与 `TOTAL_LEN` <!-- comet-task-ref:d91fba62-0db9-4736-8dbe-b2ffea9a83e7 -->
- 1.3 新增 `ReportNoGenerator`（`RP` 前缀），格式 `RP + yyyyMMdd(Asia/Shanghai) + 13 位 Base32 + 校验位` <!-- comet-task-ref:bfddb43b-b2cc-40eb-9b69-4f69a9a87435 -->
- 1.4 单测：`BizNoCodecTest`（round-trip / 跨前缀逆向必须抛错 / `CL→RP` 前缀替换后校验位仍合法 / 10 万连续 id 零碰撞 / 非法输入） <!-- comet-task-ref:0c7e708a-677d-406a-b76d-ab53d1fccd17 -->

**验收**：`BizNoCodecTest` 全绿；`CL→RP` 前缀替换后校验位仍通过；跨前缀逆向抛错；10 万连续 id 零碰撞

### 3.2 报案聚合（domain）

- 2.1 新增 `domain/model/claim/ClaimReport.java`（`@Table("t_claim_report")`，继承 `TenantAggregateRoot<Long>`）：字段 `reportNo / policyNo / planId / insuredIdHash / claimType / outInsureTime / sourceChannel / sourceSystem / status / createdAt / updatedAt / version` <!-- comet-task-ref:b9382c55-6692-4458-a778-1924ccbeebdb -->
- 2.2 `ClaimReport.open(...)` 工厂：服务端派生 `reportNo`、初始 `status=REPORTED`、校验必填与幂等入参 <!-- comet-task-ref:aecec37e-6834-4e51-8159-c3e6986a90b6 -->
- 2.3 `ClaimReport.close()` / `claimable()` 意图揭示方法；`ReportStatus` 枚举（`REPORTED` / `CLOSED`，DB 存 name） <!-- comet-task-ref:1da4335a-d4d0-49a3-a58e-0b9ee5c32ab9 -->
- 2.4 新增 `domain/model/claim/valueobject/ReportStatus.java` <!-- comet-task-ref:40baeef2-b5aa-4608-bb40-df8c83d80b79 -->
- 2.5 `ClaimCase.draft()` 增 `Long reportId` 参数（置于 id 之后），域层断言非空；新增 `@Column(name="report_id")` 字段与 `getReportId()` <!-- comet-task-ref:7bbd1705-17a0-41de-9326-65a6e25f7e22 -->
- 2.6 单测：`ClaimReportTest`（派生前缀为 RP、close 后 `claimable()` 为 false、close 幂等、字段校验）；`ClaimCaseDraft` 相关既有测试补 `reportId` <!-- comet-task-ref:59ee3233-3a84-40db-b69f-220c1892d8ed -->

**验收**：`ClaimReportTest` 全绿；`draft()` 缺 `reportId` 时域层抛错

### 3.3 仓储

- 3.1 新增 `domain/repository/ClaimReportRepository.java`（`Repository<ClaimReport, Long>` + `findByReportNo(tenantId, reportNo)` Criteria） <!-- comet-task-ref:2e0223c8-95ae-4189-86ba-9794beef1d3d -->
- 3.2 `ClaimCaseRepository` 增 `findByReportId(tenantId, reportId)`（Criteria，按 createdAt 升序） <!-- comet-task-ref:7038a910-d76b-4bb6-ba92-494eca048aec -->
- 3.3 同步 `findClaimWithItems` 的 SQL 模板补 `report_id` / `report_no` 列；`ClaimWithItemsProjection` 增 `reportId` / `reportNo` <!-- comet-task-ref:f1095ad6-823c-448e-9939-70b705022203 -->
- 3.4 启动验证：理赔模块能起（SDK 模板缺失会在启动期抛 `TemplateNotFoundException`） <!-- comet-task-ref:5943d7a6-360f-45f2-970e-922f7c87e020 -->

**验收**：理赔模块启动成功（SDK 模板缺失会在启动期抛 TemplateNotFoundException）

### 3.4 DDL 与迁移（三处同批）

- 4.1 `scripts/migration/0139_synap_claim_report_case_split.sql`：建 `t_claim_report`、加 `report_id`、回填（`report_no = CONCAT('RP', SUBSTRING(claim_no,3))`）、改 NOT NULL、建索引，全程 information_schema 幂等判定 + 5 条终态断言（空值/悬空/跨租户/重号/前缀） <!-- comet-task-ref:59daca0f-d7db-4b32-b99f-a48a8d0fc827 -->
- 4.2 `migration-manifest.json` 登记 0139（order 顺延、description 与脚本一句话对齐） <!-- comet-task-ref:432e44cc-c091-44db-886b-dce3f277a5f9 -->
- 4.3 `sql/bone-init.sql` 增 `t_claim_report` 建表 + `t_claim_case.report_id` 列与索引 <!-- comet-task-ref:71286a4b-f528-4e09-9fd3-d2fa9ad814db -->
- 4.4 模块内参考态 DDL（`synap-claim-app/src/main/resources/db/migration/`）新增 `V*__synap_claim_report.sql` 并同步 `t_claim_case` <!-- comet-task-ref:902b74c2-94be-4eb6-8605-6971bd10ab97 -->
- 4.5 本地 MySQL 实跑：正向一次 + 重跑一次（幂等）+ 人为造悬空引用验证 SIGNAL 中止 <!-- comet-task-ref:c89296c1-4e8d-4ee8-a42c-6312191dbc9c -->

**验收**：本地 MySQL 正向 + 重跑幂等各一次；人为造悬空引用时 SIGNAL 中止

### 3.5 应用层

- 5.1 `ClaimCaseApplicationService#submit` 改造：同事务内建报案 + 首案；`SubmittedClaim` 增 `reportNo` <!-- comet-task-ref:53a162cc-5f5e-4b74-8f18-050f5f790b93 -->
- 5.2 幂等命中路径返回首案对应报案号（查 `ClaimReportRepository.findById`），不得新建报案 <!-- comet-task-ref:0a67f0a7-213f-4690-bb71-53a50eda07af -->
- 5.3 新增 `application/ClaimReportApplicationService.java`：`appendCase(AppendCase command)` + `detail(reportNo)` <!-- comet-task-ref:d6f00fce-76db-4316-bc73-0350ce3e60fe -->
- 5.4 `appendCase`：租户内查报案（404 / CLOSED 422）→ 继承保单维度 → `ClaimCase.draft` → `accept()` → 插入 → 发事件 <!-- comet-task-ref:8c62fe4c-63ab-4eef-91be-c94637ab069f -->
- 5.5 单测：1:N（同报案两案）、幂等（行数不变）、CLOSED 拒绝、不存在 404、发票跨报案冲突仍拒、跨租户不可见 <!-- comet-task-ref:8f90382b-2ced-4383-a315-1e13d6651302 -->

**验收**：1:N、幂等行数不变、CLOSED 拒绝、404、发票冲突五类用例全绿

### 3.6 接口层

- 6.1 `ClaimCaseResp` 增 `reportNo`；`ClaimCaseController#submit` 传递 <!-- comet-task-ref:546fb2ce-84ad-446a-9ced-fd4f14ce4280 -->
- 6.2 新增 `adapter/web/controller/ClaimReportController.java`：`GET /api/v1/claims/reports/{reportNo}`、`POST /api/v1/claims/reports/{reportNo}/cases`（`@PreAuthorize` 用 `claim:claims:read` / `claim:claims:write`） <!-- comet-task-ref:5a05d032-f3eb-41ce-a9f6-db9ce85f18a6 -->
- 6.3 新增 DTO：`AppendCaseReq`（不含保单维度字段，出现即 400）、`ClaimReportResp`、`ClaimCaseBriefResp`；装配入 `adapter/web/assembler/` <!-- comet-task-ref:8745e4f0-d327-4ace-99ee-6c018adc8e0b -->
- 6.4 单测/契约测试：Controller 返回 `ApiResponse<T>`，端到端 201/404/422/400 四态 <!-- comet-task-ref:398a92ae-11d8-477a-8d12-e59934a42cca -->

**验收**：四态（201/404/422/400）契约测试通过；响应仍为 ApiResponse<T>

### 3.7 错误码

- 7.1 `ClaimErrorCodes` 新增 `CLAIM_REPORT_NOT_FOUND` / `CLAIM_REPORT_CLOSED` / `CLAIM_REPORT_CONTEXT_MISMATCH` <!-- comet-task-ref:6ca5b9af-2cc6-4c89-ad8e-9a89a536950b -->
- 7.2 `ClaimErrors.DEFAULT_HTTP_STATUS` 同步映射（404 / 422 / 422），漏映射类加载即抛 <!-- comet-task-ref:9d06b4b7-de65-4902-b652-78c6b42ab919 -->
- 7.3 前端两语言包（zh/en）补三条文案 <!-- comet-task-ref:1ea57d97-8d8f-4415-b3bd-d5622f9df5dd -->
- 7.4 `doc/architecture/Bone-错误码登记.md` 登记 <!-- comet-task-ref:390627df-10bb-4e7d-a967-ac593144ca10 -->

**验收**：三个新错误码三处齐（码常量 + HTTP 映射 + 前端双语包），类加载不抛

### 3.8 前端

- 8.1 `services/types.ts` + `services/claimApi.ts`：`reportDetail(reportNo)` / `appendCase(reportNo, req, key)`；`ClaimWithItemsProjection` 增 `reportId` / `reportNo` <!-- comet-task-ref:a7e95652-99fe-4bac-9c74-f79dc70450c9 -->
- 8.2 `pages/ClaimProgressPage.tsx`：标签「报案号」→「赔案号」，新增「报案号」行（可复制）；新增「同报案下的赔案」卡片（点击跳转该案详情） <!-- comet-task-ref:08fe3522-d7d8-4f2a-b1fa-cfee74ee7aa9 -->
- 8.3 `pages/ClaimSubmitPage.tsx`：支持 `?reportNo=` 追加模式（拉报案详情、保单维度只读、提交走 `appendCase`、成功跳详情） <!-- comet-task-ref:7e2533bf-c69c-4111-ab69-82a38ec5def4 -->
- 8.4 单测：追加模式表单提交走 append 端点；详情页渲染报案号与同案清单 <!-- comet-task-ref:c5ef4483-98ac-4c3d-a164-2e3df7ce849f -->
- 8.5 `typecheck` 双端通过 <!-- comet-task-ref:8355b828-b2a3-482a-b737-3351aeb88bd1 -->

**验收**：typecheck 双端通过；追加模式与详情页渲染单测绿

### 3.9 契约文档与治理

- 9.1 `03-contract/工程详细设计-落地版.md` §9.3.1 整段重写：1:0..N 权威口径 + 两套编号格式与不变式 + 报案状态语义 + 回填口径 <!-- comet-task-ref:6ee75b47-7826-4c58-b9fc-a73f19751fd6 -->
- 9.2 同文档 §9.5 增 `t_claim_report` 表与 `t_claim_case.report_id`；术语表区分报案/赔案；`batch_no` 去掉「报案号前缀」表述 <!-- comet-task-ref:f1b50192-194a-4789-862a-744945a7e463 -->
- 9.3 契约中登记「不做清单」（跨保单合并、复制赔案、报案撤销审批） <!-- comet-task-ref:07c7e613-3442-48ca-a942-5467ad901699 -->
- 9.4 文档相对链接与 AI-INDEX / manifest 校验（若文档族受治理） <!-- comet-task-ref:e73ec143-c17d-4acf-adca-06688ca57d12 -->

**验收**：§9.3.1 整段为 1:N 口径；文档相对链接零失效

### 3.10 验证

- 10.1 理赔模块单测全绿（`mvn -pl bone-apps/synap-claim/synap-claim-app -am test` 或 scoped） <!-- comet-task-ref:c00eb81b-46a2-4e63-bf20-0f02a781052c -->
- 10.2 `mvn spotless:apply`（仅本次改动文件，勿整模块） <!-- comet-task-ref:60742c33-50ab-4204-adb9-f62a1e6a8d5f -->
- 10.3 scoped 门禁：命名规范 G1–G5、xref、DDL、doc_structure、迁移清单 <!-- comet-task-ref:c7833f88-d02c-4413-bc95-e8ded4581c2a -->
- 10.4 `git status` 确认只含本次路径；提交信息说明 1:N 升级 <!-- comet-task-ref:2ea1fb6e-aa0a-47df-b434-e5e314adf1b5 -->

**验收**：scoped 门禁全绿；提交仅含本次路径

## 4. 风险与回退

- 孤儿报案：报案与首案必须同事务；用例断言「冲突后报案表行数不变」。
- 跨租户挂靠：DB 无复合外键，靠应用层统一取 `TenantPort.currentTenantId()` + 迁移终态断言（跨租户挂靠数 = 0）。
- 回填：`INSERT ... WHERE NOT EXISTS` 幂等 + 5 条终态断言；失败即中止，不自动清理数据。
- 回退：反向迁移删索引 → 删列 → drop 表；报案行可由赔案重建，无外部系统依赖。

## 5. 交付判定

全部任务勾选 + 相关门禁通过（scoped 等效亦可）+ 单元测试全绿 + 契约文档已改 + `git status` 仅含本次路径。
