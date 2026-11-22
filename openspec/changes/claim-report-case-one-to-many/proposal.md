## Why

现行实现把「报案」与「赔案」压成同一实体：`t_claim_case.claim_no` 一列同时承担报案号与赔案号，`report : case = 1:1`。这与金融行业标准（JR/T 0212-2020 将报案信息、立案信息、赔案信息列为三个独立实体）和国内财产险核心系统实践（一个报案号下可有多个赔案号）不一致，导致四类业务无法表达：一次事故多张发票分案理算、结案后追加索赔（二次手术）、报案未立案/注销、重复报案归并。契约 §9.3.1 已把 1:1 定性为「简化取舍」，本次把取舍收回，改为业界标准关系。

## What Changes

- **新增报案实体**：新建 `t_claim_report`（报案号 `report_no` 全局唯一、保单/计划/被保人/出险事实/来源渠道、报案状态），作为「事故告知事件」的独立聚合根。
- **赔案挂靠报案**：`t_claim_case` 新增 `report_id BIGINT NOT NULL` 外键列；`claim_no` 语义收敛为**赔案号**，不再兼任报案号。
- **编号分离**：新增 `RP` 前缀报案号生成器（复用既有 Feistel 双射 + Base32 + Luhn32 编解码内核），报价两套编号各自独立、互不推导。
- **追加赔案用例**：新增 `POST /api/v1/claims/reports/{reportNo}/cases`（同一报案下追加赔付）与 `GET /api/v1/claims/reports/{reportNo}`（报案详情 + 名下赔案清单）。
- **首案提交兼容**：`POST /api/v1/claims` 语义不变（一次提交 = 报案 + 首案，同事务），响应 DTO 增补 `reportNo` 字段（新增字段，非破坏性）。
- **存量回填**：迁移为每条既有赔案生成一条报案（`report_no` 由 `claim_no` 前缀 `CL→RP` 派生，校验位对日期+载荷计算故仍合法），`report_id` 回填为该报案，随后列改 NOT NULL。
- **契约文档重写**：§9.3.1「报案与赔案的关系」由「1:1 简化取舍」整段改写为 1:N 权威口径与落地口径，§9.5 DDL 与术语表同步。

## Capabilities

### New Capabilities

- `claim/report-case-modeling`: 报案与赔案的实体划分、1:0..N 基数、两套编号的生成与校验规范、报案状态语义、追加赔案与幂等归属、存量回填口径。

### Modified Capabilities

无。既有 `claim/*` 规格未规定报案与赔案的基数关系，本次为新增能力而非改写既有 REQUIREMENT；既有赔案状态机、发票唯一约束、复核链行为均不变。

## Impact

- **数据库**：新增表 `t_claim_report`；`t_claim_case` 增列 `report_id` + 索引；迁移 `0139`（生效通道 `scripts/migration/`）+ `sql/bone-init.sql` 全量基线 + 模块内参考态 DDL 三处同批。
- **后端**：`synap-claim-app` 的 domain（新聚合 `ClaimReport`、`ClaimCase` 增字段）、domain/repository、application（提交用例改造 + 追加赔案用例）、adapter/web（新 Controller 方法/DTO）、错误码（新增报案相关码）、SQL 模板。
- **前端**：`bone-claim-app` 案件详情页展示报案号 + 同报案赔案清单 + 「追加赔案」表单入口。
- **契约/治理**：`03-contract/工程详细设计-落地版.md` §9.3.1、§9.5、术语表；错误码台账；迁移清单 `migration-manifest.json`。
- **不影响**：赔案状态机与阶段码、发票唯一键 `uk_tenant_invoice` 的防重复赔付语义、复核/资金链路、既有 `claim/*` 规格行为。
