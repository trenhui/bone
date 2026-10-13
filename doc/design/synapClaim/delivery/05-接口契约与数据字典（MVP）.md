# 05 接口契约与数据字典（MVP）

> 用途：开发与联调的唯一契约来源。本文档定义 MVP 核心接口（OpenAPI 风格）与关键数据字典；完整 OpenAPI 文件在 I2 迭代前由后端产出并入库。**幂等与错误处理约定为强制项。**

---

## 一、接口总览

|**分组**|**接口**|**方法/路径**|**调用方**|
|---|---|---|---|
|员工端|提交理赔申请|POST /api/v1/claims|员工端/企微|
|员工端|上传材料|POST /api/v1/claims/{id}/evidences|员工端|
|员工端|查询进度|GET /api/v1/claims/{id}|员工端|
|员工端|发起申诉|POST /api/v1/claims/{id}/appeals|员工端|
|工作台|待办列表|GET /api/v1/review/tasks|核赔工作台|
|工作台|审核决策|POST /api/v1/review/tasks/{id}/decision|核赔工作台|
|工作台|决策追溯|GET /api/v1/review/tasks/{id}/trace|核赔工作台|
|对接|案件同步（旁路）|POST /api/v1/integration/claims/sync|存量理赔系统/CDC|
|对接|结果回写|POST /api/v1/integration/claims/{id}/result|保司/TPA 系统|

## 二、核心接口契约（示例详规）

### 2.1 提交理赔申请 POST /api/v1/claims

**请求体**
```json
{
  "policyNo": "EG20260001",          // 保单号（A类，假名化前校验）
  "insuredIdHash": "f3a9…8b",        // 员工假名化ID（由客户端计算，明文不上送）
  "claimType": "OUTPATIENT",          // 枚举：OUTPATIENT/INPATIENT/...
  "hospitalLevel": "SECONDARY",       // 枚举：PRIMARY/SECONDARY/TERTIARY
  "items": [
    {"name": "阿莫西林胶囊", "amount": 35.80, "category": "DRUG"},
    {"name": "诊查费", "amount": 10.00, "category": "SERVICE"}
  ],
  "invoiceNo": "04400001…",
  "invoiceDate": "2026-03-12",
  "idempotencyKey": "claim-20260312-0001"   // 幂等键=报案号（必填）
}
```

**响应**：`201` 返回 claimId、状态（PENDING_EVIDENCE/REVIEWING）、处理结果类型（若已决策）。

**错误码**：`400` 参数校验失败（含字段级原因）；`409` 幂等冲突（同幂等键已存在，返回已存在案件）；`422` 业务规则拒绝（如等待期内）；`503` 依赖不可用（可重试）。

### 2.2 审核决策 POST /api/v1/review/tasks/{id}/decision

**请求体**
```json
{
  "decision": "APPROVED",             // APPROVED/REJECTED/REQUEST_MORE_EVIDENCE
  "reviewerId": "u-1001",
  "comment": "发票与清单一致，同意赔付",   // 拒赔时必填理由
  "evidenceRefs": ["doc-001", "doc-002"]  // 引用证据ID
}
```

**约束**：大额（≥1 万）须两个 reviewerId（双人复核）；拒赔必须 ≥3 条理由；操作全量入审计日志。

## 三、接口通用约定（强制项）

1. **幂等**：所有写接口必带 `idempotencyKey`（报案号/流程号）；服务端按键去重，重复请求返回首次结果（非 409 拒绝重放，防二次出金）
2. **重试**：仅网络类错误可重试（间隔 1s，最多 2 次）；业务类错误（422/409）不重试，转人工或返回
3. **认证**：员工端/工作台走 OIDC/SSO；对接方走 API Key + 签名（HMAC）；租户隔离：TenantId 贯穿所有请求头
4. **审计**：每个写接口记录三件套（请求摘要 ≤200 字 + 响应结果 + 耗时）；错误必须可回放
5. **分页**：列表接口统一 `page`/`pageSize`（默认 20，上限 100）

## 四、数据字典（MVP 核心字段）

### 4.1 案件主表 claim

|**字段**|**类型**|**说明**|**来源**|
|---|---|---|---|
|claim_id|CHAR(36)|案件ID|系统|
|policy_no|VARCHAR(32)|保单号|客户|
|insured_id_hash|CHAR(64)|员工假名化ID|客户端|
|claim_type|ENUM|OUTPATIENT 等|客户端|
|status|ENUM|见 PRD §12.1|系统|
|claim_amount|DECIMAL(10,2)|理算金额|理算引擎|
|currency|CHAR(3)|CNY|系统|
|review_mode|ENUM|AUTO/MANUAL|决策引擎|
|idempotency_key|VARCHAR(64) UNIQUE|幂等键|客户端|

### 4.2 材料证据表 evidence

|**字段**|**类型**|**说明**|
|---|---|---|
|evidence_id|CHAR(36)|证据ID（供决策追溯引用）|
|claim_id|CHAR(36) INDEX|所属案件|
|doc_type|ENUM|INVOICE/LIST/MEDICAL_RECORD|
|ocr_confidence|DECIMAL(4,3)|OCR置信度（<0.7 触发转人工）|
|extracted_json|JSON|结构化提取结果（含字段置信度+证据位置）|
|original_ref|VARCHAR(128)|原始文件引用（加密存储）|

### 4.3 资金流水表 funding_event（两段式状态机）

|**字段**|**类型**|**说明**|
|---|---|---|
|event_id|CHAR(36)|事件ID（不可变）|
|claim_id|CHAR(36) INDEX|案件ID|
|stage|ENUM|SOFT_RESERVED/HARD_RESERVED/PAID|
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
|409|幂等冲突|返回已存在结果|
|422|业务规则拒绝|展示原因，转人工/补件|
|429|限流|退避重试|
|500/503|系统错误/依赖不可用|幂等重试；503 可重试|

> **与 PRD 的关系**：本契约是 PRD §11（接口定义）的落地细化；字段级规范以本数据字典为准，PRD §12 枚举与本字典保持一致（已核对 RQ-MVP 系列）。
