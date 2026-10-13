# procuraAI 接口契约与数据字典（D3）

> **用途**：前后端并行开发 + OA/ERP/信用源对接联调的依据；字段级契约冻结，随代码演进但变更需走接口评审。
> **基础**：技术方案 §5（REST API 表、异步任务模型、数据模型）细化为契约级。

---

## 1. 接口契约约定

| 项 | 约定 |
|----|------|
| 协议 | HTTPS / JSON；版本前缀 `/api/v1` |
| 鉴权 | 服务间：mTLS；用户端：OAuth2（授权码，对接客户 SSO/OA 账号体系）|
| 幂等 | 写接口支持 `Idempotency-Key` 头（24h 去重）|
| 错误码 | 统一 envelope：`{code, message, trace_id, data}`；`trace_id` 贯穿链路（联调定位） |
| 分页 | `{items, page, page_size, total}`；默认 20，最大 100 |
| 时间 | ISO-8601 UTC；业务时间字段另行携带时区 |

**错误码段**：`10xxx` 鉴权 / `20xxx` 参数校验 / `30xxx` 业务规则（30001 规则引擎不可用、30002 LLM 降级、30003 三一致存在背离）/ `40xxx` 资源（40401 任务不存在、40402 审查快照不存在）/ `50xxx` 服务端。

## 2. 核心 REST API（字段级）

### POST /api/v1/documents/analyze（上传解析 + 启动审查）
```json
请求：multipart/form-data
  file        (必填, PDF/DOCX, ≤50MB)
  doc_type    (必填: TENDER|BID|CONTRACT|AMENDMENT)
  contract_no (合同审查/三一致必填)
  project_no  (三一致校验关联用)
  priority    (NORMAL|URGENT, 默认 NORMAL)
响应 202：
  {code:0, data:{task_id:"t_9f3c", status:"QUEUED"}}
```

### GET /api/v1/tasks/{task_id}（任务轮询，间隔 ≥2s）
```json
响应：
  {code:0, data:{
    task_id, status: QUEUED|PARSING|RULE_CHECK|LLM_REVIEW|COMPLETED|FAILED|DEGRADED,
    progress: 0-100, stage_detail:"L1 解析中(3/10页)",
    review_id?, error? {code, message}
  }}
```
> `DEGRADED` = LLM 不可用降级为纯规则，前端提示"规则审查模式"。

### GET /api/v1/reviews/{review_id}/risks（风险清单分页）
```json
响应 data.items[]：
  risk_id      "R01-a3f2" (规则源) / "LLM-03-b91d"
  level        HIGH|MEDIUM|LOW
  confidence   0-1（规则源=1.0；LLM 源需 ≥0.85 才出 HIGH）
  risk_type    排他性条款/付款节点不明/违约责任失衡/资质指向性/实质性背离/...
  quote_original  原文摘录（≤200 字，含 section_ref 定位）
  section_ref  "S3-P7" (IR 章节-页)
  basis        "《招标投标法实施条例》第32条(六)"（法规库实文核对后输出）
  suggestion   修改建议文本
  decision     PENDING|ACCEPTED|REJECTED|MODIFIED
  reject_reason_type? 六类驳回理由
```

### POST /api/v1/reviews/{review_id}/risks/{risk_id}/decision
```json
请求：{decision:"ACCEPTED|REJECTED|MODIFIED", reason_type?:"不构成风险|...", comment?:"<200字"}
响应 201：{code:0, data:{audit_id:"au_...", snapshot_version:"v0.8.3-20261012"}}
```

### GET /api/v1/alerts（预警台账）
```json
query: status=UPCOMING|EXPIRED|OVERDUE|CLEARED, contract_no?, from, to
响应 data.items[]：
  alert_id, contract_no, vendor_name, node_type(PAYMENT|ACCEPTANCE|WARRANTY|DEADLINE),
  due_date, amount, days_left, status, trigger_rule
```

### POST /api/v1/alerts/{alert_id}/clear（消警）
```json
请求：{basis:"ERP_PAYMENT|ACCEPTANCE_DOC|MANUAL", doc_id?, comment?}
```

### POST /api/v1/ask（智慧问答 · M5）
```json
请求：{question:"<200字", doc_scope?:[doc_ids]}
响应：{answer, sources:[{doc_id, title, quote, score}], no_answer?:true}
约束：无依据必须明确说明"未在库内找到依据"（FR-601）
```

### POST /api/v1/rules（规则管理 · 管理员）
```json
请求：{source:"REGULATORY|ENTERPRISE", pattern:{...DSL...}, risk_type, basis, enabled, gray_percent}
动作：PUT /api/v1/rules/{rule_id}（更新）· POST /api/v1/rules/{rule_id}/publish（灰度/发布）· POST /api/v1/rules/{rule_id}/rollback
```

## 3. 数据字典（核心表字段级）

### risk_item（风险条目）
| 字段 | 类型 | 说明 |
|------|------|------|
| risk_id | varchar(32) PK | 规则源 R+序号+hash / LLM 源 LLM-序号 |
| review_id | varchar(32) FK | 所属审查 |
| level / confidence | enum / decimal(4,3) | 级别 / 置信度 |
| risk_type | varchar(64) | 风险类型字典 |
| quote_original | text | 原文摘录（脱敏后展示） |
| section_ref | varchar(32) | IR 章节引用 |
| basis | varchar(256) | 法规依据（法规库 ID 外键，可跳转） |
| suggestion | text | 修改建议 |
| decision | enum | PENDING/ACCEPTED/REJECTED/MODIFIED |
| reject_reason_type / comment | varchar(16) / text | 驳回理由/备注 |
| engine_version | varchar(32) | 产出引擎版本（规则包版本/模型版本） |
| created_at / decided_at / decided_by | ts / ts / varchar(64) | 时间与确认人 |

### review（审查任务）
| 字段 | 说明 |
|------|------|
| review_id / task_id | 主键 / 来源任务 |
| doc_id / doc_type / contract_no / project_no | 文档与业务关联 |
| status | RUNNING/CONFIRMING/ARCHIVED（对应报告状态机） |
| risk_high / medium / low | 各级计数（确认后实时） |
| snapshot_id | 审查快照引用（可重放） |
| rule_version / model_version | 引擎版本快照 |
| cost_estimate | token 用量（LLM 成本月统计用） |

### review_snapshot（审查快照，可复现）
| 字段 | 说明 |
|------|------|
| snapshot_id / review_id | 主键 / 关联 |
| ir_version | 解析 IR 版本（原文 token 化版本） |
| rule_version / model_version / prompt_version | 引擎三版本 |
| inputs_hash | 输入（文档 hash + 规则包 hash）——同一输入重放结果一致 |
| payload | 全链路留痕（解析切片/规则命中/LLM 请求与响应/校验记录）——大对象存对象存储，表存引用 |

### alert_task（履约预警）
| 字段 | 说明 |
|------|------|
| alert_id / contract_no | 主键 / 合同号（贯穿 OA-ERP） |
| node_type / due_date / amount | 节点类型 / 到期日 / 金额 |
| status | UPCOMING/EXPIRED/OVERDUE/CLEARED |
| cleared_by_erp | bool（ERP 付款/验收回写自动消警） |
| escalation_to | 逾期升级抄送人 |
| trigger_rule | 触发规则（T-15/T-0/T+0） |

### rule（规则库双源）
| 字段 | 说明 |
|------|------|
| rule_id / source | 主键 / REGULATORY\|ENTERPRISE |
| pattern | QLExpress DSL 表达式（热更脚本） |
| risk_type / basis / level | 风险类型 / 依据 / 建议级别 |
| status / gray_percent | DRAFT/ENABLED/DISABLED / 灰度比例 |
| test_ids | 关联回归测试集（≥1 正例 + 1 反例） |
| confirmed_by / confirmed_at | 法务确认人与时间（可引用前提） |

## 4. 事件订阅（OA/ERP webhook）

| 事件 | 触发方 | 负载要点 | 系统动作 |
|------|--------|----------|----------|
| document.archived | OA（招标文件归档） | `{project_no, doc_id, url}` | 触发体检（M1） |
| contract.signed | OA（合同归档） | `{contract_no, project_no?, doc_id, sign_date}` | 触发审查 + 三一致校验（R25）+ 建台账 |
| payment.recorded | ERP 财务 | `{contract_no, amount, pay_date}` | 履约节点消警（cleared_by_erp=true） |
| acceptance.recorded | ERP/验收模块 | `{contract_no, item, date}` | 验收节点消警 |

> 订阅机制：webhook（客户回调）或轮询适配器（客户无回调能力时，按 15min 增量拉取，幂等落库）。

## 5. 版本与兼容策略

- 接口向后兼容：新增字段以 `+` 注释标记，不删不改既有字段语义；破坏性变更升 `/api/v2` 并双版本并行 ≥1 个季度；
- 数据字典变更走评审（字段名/枚举值变更影响联调方，需邮件通知 + 变更记录）；
- 测试契约：`/api/v1/openapi.json` 导出与 Postman 集合随版本发布，联调用 `trace_id` 定位。

---

## 附：与 OA/ERP 适配的字段映射（统一字典，缓解多 ERP 口径不一）

| procuraAI 标准字段 | U8 | SAP | Oracle | 金蝶 |
|--------------------|----|-----|--------|------|
| contract_no | 合同编号 | ZKONNR | CONTRACT_NUM | 合同号 |
| amount | 合同金额 | ZAMOUNT | AMOUNT | 金额 |
| pay_date | 付款日期 | ZPAYDATE | PAY_DATE | 付款日期 |
| vendor_id | 供应商编码 | LIFNR | VENDOR_ID | 供应商编号 |
