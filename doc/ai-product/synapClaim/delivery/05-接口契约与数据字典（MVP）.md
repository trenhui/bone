# 05 接口契约与数据字典（MVP）

> 用途：开发与联调的唯一契约来源。本文档定义 MVP 核心接口（OpenAPI 风格）与关键数据字典；完整 OpenAPI 文件在 I2 迭代前由后端产出并入库。**幂等与错误处理约定为强制项。**
>
> **契约地位**：本文为 MVP **接口契约与数据字典真源**（对外枚举 `PENDING_EVIDENCE`/`REVIEWING`、MVP 端点子集、关键字段）；《synapClaim 智能理赔平台-工程详细设计（落地版）》§6.1 / §10 / §10.6 明确引用并遵循本文（其全量 OpenAPI / DDL 以工程落地版 §9 / §10 为权威，本文为其 MVP 子集）。

---

## 一、接口总览

|**分组**|**接口**|**方法/路径**|**调用方**|
|---|---|---|---|
|员工端|提交理赔申请|POST /api/v1/claims|员工端/企微|
|员工端|上传材料|POST /api/v1/claims/{id}/evidences|员工端|
|员工端|查询进度|GET /api/v1/claims/{id}|员工端|
|员工端|发起申诉|POST /api/v1/claims/{id}/appeals|员工端（契约规定；代码已落地：复数 /appeals 路径已补，与 /appeal 并存，见落地版 §12.2.1 / §10.6 #6）|
|工作台|待办列表|GET /api/v1/review/tasks|核赔工作台|
|工作台|审核决策|POST /api/v1/review/tasks/{id}/decision|核赔工作台|
|工作台|决策追溯|GET /api/v1/review/tasks/{id}/trace|核赔工作台|
|对接|案件同步（旁路）|POST /api/v1/integration/claims/sync|存量理赔系统/CDC|
|对接|结果回写|POST /api/v1/integration/claims/{id}/result|保司/TPA 系统|
|管理后台|对账报告查询|GET /api/v1/review/reconciliation|OIDC/SSO（契约规定；外部与内部端点代码**均未落地**——2026-10-25 核码：`reconciliation` 全仓 0 实现，见落地版 §12.2.1）|

> **端点子集边界（2026-10-23 补）**：① 动态表单 Schema 由平台 `BoneUiSchema` 承载（工程落地版 §10.1 非本域端点登记 / §11.10），不在本 MVP 端点子集；② 对账报告查询已列入上表（管理后台），其外部与内部端点代码**均未落地**（2026-10-25 核码：`reconciliation` 全仓 0 实现，见落地版 §12.2.1）；③ 决策结果为异步产出，员工端经轮询获取（间隔 2s、上限 15 次，工程落地版 §10.3 读己之写），本文件不设同步决策端点；④ 发起申诉（`POST /api/v1/claims/{id}/appeals`）为契约规定端点，代码已落地（复数 /appeals 路径已补，与 /appeal 并存，见落地版 §12.2.1）。

## 二、核心接口契约（示例详规）

### 2.1 提交理赔申请 POST /api/v1/claims

**请求头**：`X-Tenant-Id`（必填，租户路由提示；权威来源为认证主体，与主体不一致返回 `403`）、`Idempotency-Key`（必填，≤64 字符；**创建类取客户端生成的业务键**（如 `batchNo+invoiceNo` 或客户端 UUID——报案号由服务端生成，客户端无法预知），**状态推进类取 `{claimNo}:{stage}:{attempt}`**；幂等键不置于请求体）

**请求体**
```json
{
  "policyNo": "EG20260001",          // 保单号（A类，假名化前校验）
  // "insuredIdHash" 为 readOnly：由服务端按 HMAC-SHA256+盐计算（盐存 KMS/HSM 双人分片托管），客户端/上游不传该字段，仅传业务主键，明文不上送不落地
  "claimType": "OUTPATIENT",          // 枚举：OUTPATIENT/INPATIENT/...
  "hospitalLevel": "SECONDARY",       // 枚举：PRIMARY/SECONDARY/TERTIARY
  "items": [
    {"name": "阿莫西林胶囊", "amount": 35.80, "category": "DRUG", "socialFlag": "IN"},
    {"name": "诊查费", "amount": 10.00, "category": "SERVICE", "socialFlag": "IN"}
  ],
  // socialFlag：社保内外标识 IN/OUT/SELF（R-DED-03 医保三段拆分依赖，缺省 IN；权威见工程落地版 §9 social_flag）
  "invoiceNo": "04400001…",
  "invoiceDate": "2026-03-12"
}
```

**响应**：`201` 返回 claimId、状态（PENDING_EVIDENCE/REVIEWING）、处理结果类型（若已决策）。

**错误码**：`400` 参数校验失败（含字段级原因）；`409` 业务键或状态冲突（同报案号但请求体不一致 / 并发占用 / 同幂等键不同参；**不回传已存在案件引用**，防枚举见工程落地版 §11.3；**同幂等键重复请求不返回 409**，按幂等语义返回首次结果）；`422` 业务规则拒绝（如等待期内）；`424` 保单快照过期/失效；`503` 依赖不可用（可重试，间隔 1s 最多 2 次）。

### 2.2 审核决策 POST /api/v1/review/tasks/{id}/decision

**请求体**
```json
{
  "decision": "APPROVED",             // APPROVED/REJECTED/REQUEST_MORE_EVIDENCE
  "reviewerId": "u-1001",
  "secondReviewerId": "u-1002",       // 金额≥10000 必填（双人复核，与 reviewerId 互异；工程落地版 §11.7 不变量 5）
  "comment": "发票与清单一致，同意赔付",   // 拒赔时必填理由
  "evidenceRefs": ["doc-001", "doc-002"],  // 引用证据ID
  "rejection": {                      // decision=REJECTED 时必填：结构化拒赔五要素（R-MAN-03）
    "factFindings": "门诊诊查费不在保单责任范围",
    "clauseBasis": "条款 3.2 责任免除第 4 项",
    "evidenceRefs": ["doc-001"],
    "appealChannel": "appeals@insurer.example / 400-xxx",
    "legalDeadline": "2026-04-12"
  }
}
```

**约束**：大额（金额 ≥10000）须 secondReviewerId（双人复核，与 reviewerId 互异，工程落地版 §11.7 不变量 5）；拒赔必答结构化五要素（事实认定+条款依据+证据位置+申诉渠道+法定时限，R-MAN-03；“≥3 条理由”口径已废止）；操作全量入审计日志。数据字典须含同意留痕字段：`consent_record_id`（单独同意记录号）与 `consent_withdrawn_flag`（撤回标记，撤回即触发删除/停用流程，PIPL 第 29 条）。
> **数据字典补录（同意留痕与假名化职责，PIPL 29 条，随 claim 主表）**：`consent_record_id` VARCHAR(64)（单独同意记录号，FK→同意流水）；`consent_withdrawn_flag` TINYINT(1) DEFAULT 0（撤回标记，置 1 即触发删除/停用流程）；`insured_id_hash` CHAR(64)（**服务端计算**，HMAC-SHA256+盐、盐存 KMS/HSM 双人分片托管 + 定期轮换（工程落地版 §11.3，回写 §10.6 偏离#3），端侧与上游仅传业务主键）。

## 三、接口通用约定（强制项）

1. **幂等**：所有写接口必带 `Idempotency-Key` **请求头**（≤64 字符，不置于请求体）；取值分两类——创建类取**客户端生成的业务键**（报案号由服务端生成，客户端不可能预知），状态推进类取 `{claimNo}:{stage}:{attempt}`（工程落地版 §10.3）；服务端按键去重，重复请求返回首次结果（非 409 拒绝重放，防二次出金）
2. **重试**：仅网络类错误可重试（间隔 1s，最多 2 次）；业务类错误（422/409）不重试，转人工或返回
3. **认证**：员工端/工作台走 OIDC/SSO；对接方走 API Key + 签名（HMAC）；租户隔离：X-Tenant-Id 头部**仅路由提示**，**认证主体为唯一权威**，两者不一致返回 403（防水平越权，工程落地版 §10.3，回写 §10.6 偏离#1）
4. **审计**：每个写接口记录三件套（请求摘要 ≤200 字 + 响应结果 + 耗时）；错误必须可回放
5. **分页**：列表接口统一 `page`/`pageSize`（默认 20，上限 100）

## 四、数据字典（MVP 核心字段）

> **主键类型说明（MVP 示意）**：本节 `claim_id`/`evidence_id`/`event_id`/`trace_id` 等 `*_id` 取 CHAR(36) 为 MVP 示意；权威口径见工程落地版 §9——主键 `id` 为 BIGINT 分布式 ID + 业务号列（如 `claim_no` 报案号），偏离登记于工程落地版 §10.6。

### 4.1 案件主表 claim

|**字段**|**类型**|**说明**|**来源**|
|---|---|---|---|
|claim_id|CHAR(36)|案件ID|系统|
|policy_no|VARCHAR(32)|保单号|客户|
|insured_id_hash|CHAR(64)|员工假名化ID|服务端计算（HMAC-SHA256+盐，盐存 KMS/HSM 双人分片托管；端侧仅传业务主键，明文不上送）|
|claim_type|ENUM|OUTPATIENT 等|客户端|
|status|ENUM|PENDING_EVIDENCE/REVIEWING（对外两值投影；内部细粒度状态码不对外暴露）|系统|
|claim_amount|DECIMAL(10,2)|理算金额（MVP 示意；权威 DECIMAL(12,2)，工程落地版 §9/§10.6 偏离#4）|理算引擎|
|currency|CHAR(3)|CNY|系统|
|review_mode|ENUM|AUTO/MANUAL|决策引擎|
|ocr_status|ENUM|PENDING/PARSING/DONE/FAILED/MANUAL（材料解析状态，`ocr_confidence<0.7` 置 MANUAL 并转人工）|解析服务|
|stage|VARCHAR(32)|业务阶段码（对外筛选维度，值域 INIT/SIGNING/PRE_EXAM/SUBMITTING/INSPECTION/AUDITING/REVIEWING/FINISH）|系统|
|idempotency_key|VARCHAR(64) UNIQUE|幂等键|客户端（`Idempotency-Key` 请求头）|

> **对外 / 内部字段边界**：`status` 对外只投影 `PENDING_EVIDENCE`/`REVIEWING` 两值；`status_sub`（子状态，如录入中 22 + sub `1`普康/`2`外包）与内部细粒度状态码**仅内部使用，不对外暴露**，也不作为筛选入参；`stage` 是与 `status` 相互独立的第二维度（8 段业务大阶段），对外作为工作台筛选维度暴露，值域如上。
>
> **工作台任务状态（`GET /review/tasks` 的 `status` 查询入参，属任务维度、非案件状态）**：`WAITING`（待领取）/ `ASSIGNED`（已分配未处理）/ `PROCESSING`（处理中）/ `DONE`（已处理）。

### 4.2 材料证据表 evidence

|**字段**|**类型**|**说明**|
|---|---|---|
|evidence_id|CHAR(36)|证据ID（供决策追溯引用）|
|claim_id|CHAR(36) INDEX|所属案件|
|doc_type|ENUM|INVOICE/LIST/MEDICAL_RECORD/MEDICAL_SETTLEMENT（医保结算单；R-DED-03 三段拆分输入，RQ-MVP-001 材料类型）|
|ocr_confidence|DECIMAL(4,3)|OCR置信度（<0.7 触发转人工；与 AI 决策置信度三档口径「<0.7 强制人工 / 0.7-0.85 复核档 / ≥0.85 直出」为两个独立字段，见 PRD §6 与 Rulebook R-MAN-01；MVP 示意，权威 DECIMAL(6,4)，工程落地版 §9/§10.6 偏离#4）|
|extracted_json|JSON|结构化提取结果（含字段置信度+证据位置）|
|original_ref|VARCHAR(128)|原始文件引用（加密存储）|

### 4.3 资金流水表 funding_event（两段式状态机）

|**字段**|**类型**|**说明**|
|---|---|---|
|event_id|CHAR(36)|事件ID（不可变）|
|claim_id|CHAR(36) INDEX|案件ID|
|stage|ENUM|SOFT_RESERVED/HARD_RESERVED/REVIEW_FREEZE/PAID/RELEASED/FAILED/REFUNDED|
|idempotency_key|VARCHAR(64)|资金幂等键=`{claimNo}:{stage}:{attempt}`（`attempt` 为占额周期序号，工程落地版 §7.5）；唯一约束 uk(tenant_id, idempotency_key)，同键重复写入返回已存事件|
|amount|DECIMAL(10,2)|金额|
|operator|VARCHAR(32)|操作者/系统|
|event_time|DATETIME|事件时间（追加式，不可更新）|

### 4.4 决策追溯表 decision_trace

|**字段**|**类型**|**说明**|
|---|---|---|
|trace_id|CHAR(36)|轨迹ID|
|claim_id|CHAR(36) INDEX|案件ID|
|node_seq|INT|节点序号（证据→解析→核责→理算→决策）|
|node_type|VARCHAR(32)|节点类型（OCR/RULE/LLM/DECISION）|
|input_summary|VARCHAR(200)|输入摘要（≤200字）|
|output|JSON|模型/规则输出（完整保留）|
|action_result|VARCHAR(32)|HTTP 状态/规则结果|
|duration_ms|INT|节点耗时|

## 五、错误码全集（统一）

|**码**|**含义**|**处理**|
|---|---|---|
|400|参数校验失败|客户端修正|
|401/403|未认证/无权限|重新认证|
|404|资源不存在|检查ID|
|409|业务键或状态冲突（同报案号不同 payload / 并发占用 / 同幂等键不同参）|不回传已存在案件引用（防枚举）；同幂等键重复请求按幂等语义返回首次结果|
|422|业务规则拒绝|展示原因，转人工/补件|
|424|保单快照过期/失效|重新同步保单后重试|
|429|限流（响应须带 `Retry-After` 头）|按 Retry-After 退避重试|
|500/503|系统错误/依赖不可用|幂等重试；503 可重试（间隔 1s 最多 2 次，仅网络类错误可重试）|

> **重试矩阵唯一真源**：工程落地版 §10.3（依赖 × 错误类 × 退避 × 次数），本契约不另立口径。

> **与 PRD 的关系**：本契约是 PRD §11（接口定义）的落地细化；**枚举真源以本数据字典为准**（case 状态：PENDING_EVIDENCE/REVIEWING；decision：APPROVED/REJECTED/REQUEST_MORE_EVIDENCE；decisionResult：决策图结论 AUTO_APPROVE/SUGGEST_REJECT/MANUAL_REQUIRED/NEED_MORE_EVIDENCE）。PRD §12.1 已于 2026-10-21 回写为「内部状态码 + 对外两值投影」口径，与本文无冲突。
