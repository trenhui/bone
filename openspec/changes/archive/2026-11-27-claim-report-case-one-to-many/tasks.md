# 任务：报案与赔案拆分（1:0..N）

> 顺序按依赖排列：编号内核 → 领域 → DDL → 应用/接口 → 前端 → 契约文档 → 验证。
> 每条任务完成即勾选；「验证」段落中的命令必须真实跑过再勾。

## 1. 编号内核（domain，无框架依赖）

- [x] 1.1 新增 `domain/model/claim/BizNoCodec.java`：抽出 Feistel 双射 + Crockford Base32 + Luhn32 + 日期段，暴露 `generate(prefix, snowflakeId, date)` / `isValid(prefix, no)` / `extractSnowflakeId(prefix, no)`，前缀不匹配抛 `IllegalArgumentException` <!-- comet-task:80a58e80-dda1-476e-8ff6-4f99884d9725 -->
- [x] 1.2 `ClaimNoGenerator` 改为 `BizNoCodec` 的薄封装（`CL` 前缀），保留全部既有公开方法签名与 `TOTAL_LEN` <!-- comet-task:d91fba62-0db9-4736-8dbe-b2ffea9a83e7 -->
- [x] 1.3 新增 `ReportNoGenerator`（`RP` 前缀），格式 `RP + yyyyMMdd(Asia/Shanghai) + 13 位 Base32 + 校验位` <!-- comet-task:bfddb43b-b2cc-40eb-9b69-4f69a9a87435 -->
- [x] 1.4 单测：`BizNoCodecTest`（round-trip / 跨前缀逆向必须抛错 / `CL→RP` 前缀替换后校验位仍合法 / 10 万连续 id 零碰撞 / 非法输入） <!-- comet-task:0c7e708a-677d-406a-b76d-ab53d1fccd17 -->

## 2. 报案聚合（domain）

- [x] 2.1 新增 `domain/model/claim/ClaimReport.java`（`@Table("t_claim_report")`，继承 `TenantAggregateRoot<Long>`）：字段 `reportNo / policyNo / planId / insuredIdHash / claimType / outInsureTime / sourceChannel / sourceSystem / status / createdAt / updatedAt / version` <!-- comet-task:b9382c55-6692-4458-a778-1924ccbeebdb -->
- [x] 2.2 `ClaimReport.open(...)` 工厂：服务端派生 `reportNo`、初始 `status=REPORTED`、校验必填与幂等入参 <!-- comet-task:aecec37e-6834-4e51-8159-c3e6986a90b6 -->
- [x] 2.3 `ClaimReport.close()` / `claimable()` 意图揭示方法；`ReportStatus` 枚举（`REPORTED` / `CLOSED`，DB 存 name） <!-- comet-task:1da4335a-d4d0-49a3-a58e-0b9ee5c32ab9 -->
- [x] 2.4 新增 `domain/model/claim/valueobject/ReportStatus.java` <!-- comet-task:40baeef2-b5aa-4608-bb40-df8c83d80b79 -->
- [x] 2.5 `ClaimCase.draft()` 增 `Long reportId` 参数（置于 id 之后），域层断言非空；新增 `@Column(name="report_id")` 字段与 `getReportId()` <!-- comet-task:7bbd1705-17a0-41de-9326-65a6e25f7e22 -->
- [x] 2.6 单测：`ClaimReportTest`（派生前缀为 RP、close 后 `claimable()` 为 false、close 幂等、字段校验）；`ClaimCaseDraft` 相关既有测试补 `reportId` <!-- comet-task:59ee3233-3a84-40db-b69f-220c1892d8ed -->

## 3. 仓储

- [x] 3.1 新增 `domain/repository/ClaimReportRepository.java`（`Repository<ClaimReport, Long>` + `findByReportNo(tenantId, reportNo)` Criteria） <!-- comet-task:2e0223c8-95ae-4189-86ba-9794beef1d3d -->
- [x] 3.2 `ClaimCaseRepository` 增 `findByReportId(tenantId, reportId)`（Criteria，按 createdAt 升序） <!-- comet-task:7038a910-d76b-4bb6-ba92-494eca048aec -->
- [x] 3.3 同步 `findClaimWithItems` 的 SQL 模板补 `report_id` / `report_no` 列；`ClaimWithItemsProjection` 增 `reportId` / `reportNo` <!-- comet-task:f1095ad6-823c-448e-9939-70b705022203 -->
- [x] 3.4 启动验证：理赔模块能起（SDK 模板缺失会在启动期抛 `TemplateNotFoundException`） <!-- comet-task:5943d7a6-360f-45f2-970e-922f7c87e020 -->

## 4. DDL 与迁移（三处同批）

- [x] 4.1 `scripts/migration/0139_synap_claim_report_case_split.sql`：建 `t_claim_report`、加 `report_id`、回填（`report_no = CONCAT('RP', SUBSTRING(claim_no,3))`）、改 NOT NULL、建索引，全程 information_schema 幂等判定 + 5 条终态断言（空值/悬空/跨租户/重号/前缀） <!-- comet-task:59daca0f-d7db-4b32-b99f-a48a8d0fc827 -->
- [x] 4.2 `migration-manifest.json` 登记 0139（order 顺延、description 与脚本一句话对齐） <!-- comet-task:432e44cc-c091-44db-886b-dce3f277a5f9 -->
- [x] 4.3 `sql/bone-init.sql` 增 `t_claim_report` 建表 + `t_claim_case.report_id` 列与索引 <!-- comet-task:71286a4b-f528-4e09-9fd3-d2fa9ad814db -->
- [x] 4.4 模块内参考态 DDL（`synap-claim-app/src/main/resources/db/migration/`）新增 `V*__synap_claim_report.sql` 并同步 `t_claim_case` <!-- comet-task:902b74c2-94be-4eb6-8605-6971bd10ab97 -->
- [x] 4.5 本地 MySQL 实跑：正向一次 + 重跑一次（幂等）+ 人为造悬空引用验证 SIGNAL 中止 <!-- comet-task:c89296c1-4e8d-4ee8-a42c-6312191dbc9c -->

## 5. 应用层

- [x] 5.1 `ClaimCaseApplicationService#submit` 改造：同事务内建报案 + 首案；`SubmittedClaim` 增 `reportNo` <!-- comet-task:53a162cc-5f5e-4b74-8f18-050f5f790b93 -->
- [x] 5.2 幂等命中路径返回首案对应报案号（查 `ClaimReportRepository.findById`），不得新建报案 <!-- comet-task:0a67f0a7-213f-4690-bb71-53a50eda07af -->
- [x] 5.3 新增 `application/ClaimReportApplicationService.java`：`appendCase(AppendCase command)` + `detail(reportNo)` <!-- comet-task:d6f00fce-76db-4316-bc73-0350ce3e60fe -->
- [x] 5.4 `appendCase`：租户内查报案（404 / CLOSED 422）→ 继承保单维度 → `ClaimCase.draft` → `accept()` → 插入 → 发事件 <!-- comet-task:8c62fe4c-63ab-4eef-91be-c94637ab069f -->
- [x] 5.5 单测：1:N（同报案两案）、幂等（行数不变）、CLOSED 拒绝、不存在 404、发票跨报案冲突仍拒、跨租户不可见 <!-- comet-task:8f90382b-2ced-4383-a315-1e13d6651302 -->

## 6. 接口层

- [x] 6.1 `ClaimCaseResp` 增 `reportNo`；`ClaimCaseController#submit` 传递 <!-- comet-task:546fb2ce-84ad-446a-9ced-fd4f14ce4280 -->
- [x] 6.2 新增 `adapter/web/controller/ClaimReportController.java`：`GET /api/v1/claims/reports/{reportNo}`、`POST /api/v1/claims/reports/{reportNo}/cases`（`@PreAuthorize` 用 `claim:claims:read` / `claim:claims:write`） <!-- comet-task:5a05d032-f3eb-41ce-a9f6-db9ce85f18a6 -->
- [x] 6.3 新增 DTO：`AppendCaseReq`（不含保单维度字段，出现即 400）、`ClaimReportResp`、`ClaimCaseBriefResp`；装配入 `adapter/web/assembler/` <!-- comet-task:8745e4f0-d327-4ace-99ee-6c018adc8e0b -->
- [x] 6.4 单测/契约测试：Controller 返回 `ApiResponse<T>`，端到端 201/404/422/400 四态 <!-- comet-task:398a92ae-11d8-477a-8d12-e59934a42cca -->

## 7. 错误码

- [x] 7.1 `ClaimErrorCodes` 新增 `CLAIM_REPORT_NOT_FOUND` / `CLAIM_REPORT_CLOSED` / `CLAIM_REPORT_CONTEXT_MISMATCH` <!-- comet-task:6ca5b9af-2cc6-4c89-ad8e-9a89a536950b -->
- [x] 7.2 `ClaimErrors.DEFAULT_HTTP_STATUS` 同步映射（404 / 422 / 422），漏映射类加载即抛 <!-- comet-task:9d06b4b7-de65-4902-b652-78c6b42ab919 -->
- [x] 7.3 前端两语言包（zh/en）补三条文案 <!-- comet-task:1ea57d97-8d8f-4415-b3bd-d5622f9df5dd -->
- [x] 7.4 `doc/architecture/Bone-错误码登记.md` 登记 <!-- comet-task:390627df-10bb-4e7d-a967-ac593144ca10 -->

## 8. 前端

- [x] 8.1 `services/types.ts` + `services/claimApi.ts`：`reportDetail(reportNo)` / `appendCase(reportNo, req, key)`；`ClaimWithItemsProjection` 增 `reportId` / `reportNo` <!-- comet-task:a7e95652-99fe-4bac-9c74-f79dc70450c9 -->
- [x] 8.2 `pages/ClaimProgressPage.tsx`：标签「报案号」→「赔案号」，新增「报案号」行（可复制）；新增「同报案下的赔案」卡片（点击跳转该案详情） <!-- comet-task:08fe3522-d7d8-4f2a-b1fa-cfee74ee7aa9 -->
- [x] 8.3 `pages/ClaimSubmitPage.tsx`：支持 `?reportNo=` 追加模式（拉报案详情、保单维度只读、提交走 `appendCase`、成功跳详情） <!-- comet-task:7e2533bf-c69c-4111-ab69-82a38ec5def4 -->
- [x] 8.4 单测：追加模式表单提交走 append 端点；详情页渲染报案号与同案清单 <!-- comet-task:c5ef4483-98ac-4c3d-a164-2e3df7ce849f -->
- [x] 8.5 `typecheck` 双端通过 <!-- comet-task:8355b828-b2a3-482a-b737-3351aeb88bd1 -->

## 9. 契约文档与治理

- [x] 9.1 `03-contract/工程详细设计-落地版.md` §9.3.1 整段重写：1:0..N 权威口径 + 两套编号格式与不变式 + 报案状态语义 + 回填口径 <!-- comet-task:6ee75b47-7826-4c58-b9fc-a73f19751fd6 -->
- [x] 9.2 同文档 §9.5 增 `t_claim_report` 表与 `t_claim_case.report_id`；术语表区分报案/赔案；`batch_no` 去掉「报案号前缀」表述 <!-- comet-task:f1b50192-194a-4789-862a-744945a7e463 -->
- [x] 9.3 契约中登记「不做清单」（跨保单合并、复制赔案、报案撤销审批） <!-- comet-task:07c7e613-3442-48ca-a942-5467ad901699 -->
- [x] 9.4 文档相对链接与 AI-INDEX / manifest 校验（若文档族受治理） <!-- comet-task:e73ec143-c17d-4acf-adca-06688ca57d12 -->

## 10. 验证

- [x] 10.1 理赔模块单测全绿（`mvn -pl bone-apps/synap-claim/synap-claim-app -am test` 或 scoped） <!-- comet-task:c00eb81b-46a2-4e63-bf20-0f02a781052c -->
- [x] 10.2 `mvn spotless:apply`（仅本次改动文件，勿整模块） <!-- comet-task:60742c33-50ab-4204-adb9-f62a1e6a8d5f -->
- [x] 10.3 scoped 门禁：命名规范 G1–G5、xref、DDL、doc_structure、迁移清单 <!-- comet-task:c7833f88-d02c-4413-bc95-e8ded4581c2a -->
- [x] 10.4 `git status` 确认只含本次路径；提交信息说明 1:N 升级 <!-- comet-task:2ea1fb6e-aa0a-47df-b434-e5e314adf1b5 -->
