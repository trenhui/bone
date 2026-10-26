# procuraAI 接口契约与数据字典（D3）

> **用途**：前后端并行开发 + OA/ERP/信用源对接联调的依据；字段级契约冻结，随代码演进但变更需走接口评审。
> **基础**：技术方案 §5（REST API 表、异步任务模型、数据模型）细化为契约级。
> **2026-10-26 修订**：状态枚举对齐技术方案 §2.2（补齐 `MERGING/PENDING_REVIEW/REVISING/RE_REVIEWING/FINALIZED`，移除冗余 `DEGRADED` 终态，降级改以 `rules_only` 标记表达）。

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
    task_id, status: QUEUED|PARSING|RULE_CHECK|LLM_REVIEW|MERGING|PENDING_REVIEW|REVISING|RE_REVIEWING|FINALIZED|COMPLETED|FAILED,
    progress: 0-100, stage_detail:"L1 解析中(3/10页)",
    review_id?, error? {code, message}
  }}
```
> `rules_only=true`（LLM 降级为纯规则，对应技术方案 §2.2 `COMPLETED(rules_only)`）时前端提示"规则审查模式"；**不另设 `DEGRADED` 独立终态**（状态真源 = 技术方案 §2.2）。

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
  reject_reason_type? NOT_A_RISK|OUT_OF_SCOPE|BASIS_INVALID|DUPLICATE|MISREAD|OTHER（六类，见 §5）
```

### POST /api/v1/reviews/{review_id}/risks/{risk_id}/decision
```json
请求：{decision:"ACCEPTED|REJECTED|MODIFIED", reject_reason_type?:"NOT_A_RISK|OUT_OF_SCOPE|BASIS_INVALID|DUPLICATE|MISREAD|OTHER", comment?:"<200字"}
响应 201：{code:0, data:{audit_id:"au_...", snapshot_version:"v0.8.3-20261012"}}
```

### GET /api/v1/alerts（预警台账）
```json
query: status=UPCOMING|DUE|OVERDUE|CLEARED（EXPIRED=OVERDUE 兼容别名）, contract_no?, from, to
响应 data.items[]：
  alert_id, contract_no, vendor_name, node_type(PAYMENT|ACCEPTANCE|WARRANTY|DEADLINE|PERFORMANCE),
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
| status | QUEUED/PARSING/RULE_CHECK/LLM_REVIEW/MERGING/PENDING_REVIEW/REVISING/RE_REVIEWING/FINALIZED/COMPLETED/FAILED（执行态，真源=技术方案 §2.2） |
| report_status | RUNNING/CONFIRMING/ARCHIVED（报告态，由 status 派生，映射见 §5） |
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
| contract.draft | OA（用印申请/合同定稿） | `{contract_no, project_no?, doc_id}` | **触发审查 + 三一致校验（R25）+ 预审摘要**（供签署前决策，对齐 PRD FR-201/§4.2） |
| contract.signed | OA（合同归档） | `{contract_no, project_no?, doc_id, sign_date}` | 归档 + 建台账 + 履约预警启动；已审合同做归档增量复核（重复审查幂等跳过） |
| payment.recorded | ERP 财务 | `{contract_no, amount, pay_date}` | 履约节点消警（cleared_by_erp=true） |
| acceptance.recorded | ERP/验收模块 | `{contract_no, item, date}` | 验收节点消警 |

> 订阅机制：webhook（客户回调）或轮询适配器（客户无回调能力时，按 15min 增量拉取，幂等落库）。
> **触发时序（2026-10-14 修订）**：审查主路径在**签署前**——`contract.draft`（用印/定稿）触发审查与三一致；`contract.signed` 仅归档、建台账、启动履约预警，避免“签完才审”使签署前拦截失效。若客户 OA 无用印事件，降级为 `POST /documents/analyze` 手动/定时触发（同为签署前路径）。

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

---

## 4. 补充契约（2026-10-14 复核补齐）

### 4.1 revision_task（FR-205 处置链）

表字典（与技术方案 §3.4.1 DDL 对齐）：revision_id PK / review_id FK / contract_no / status(PENDING_REVISION/REVISING/RE_REVIEWING/FINALIZED/ESCALATED) / adopted_risk_ids json / round(≤3) / revised_doc_hash / created_by / created_at。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/v1/reviews/{review_id}/revisions | 由 decision=ACCEPTED 的风险条目按 section_ref 汇集生成修订任务 → 201 {revision_id, adopted_risk_ids} |
| POST | /api/v1/revisions/{revision_id}/submit | 提交修订稿（人工批注修订稿或 M7 代改）；必带 revised_doc_hash → 触发增量重审 |
| POST | /api/v1/revisions/{revision_id}/re-review | 段落级 diff 增量重审（变更条款 L2+L3，未变更条款抽检 20%）→ 202 异步 |
| GET | /api/v1/revisions/{revision_id} | 处置链查询（首审→修订→重审→定稿；round、ESCALATED 转人工记录、定稿稿 hash） |

约束：round ≤3 超轮 ESCALATED 转人工全程处理；定稿稿回流条款库（FR-721）；重审结论标注"新风险/历史沿用"。

### 4.2 vendor_profile / clause_library 与 /vendors（FR-501 / FR-721）

vendor_profile 字典：vendor_id PK / credit_source varchar / dimension_score json（多维评分明细）/ risk_flags json / data_date / refreshed_at。
clause_library 字典：clause_id PK / tenant_id / category(STANDARD/ALTERNATIVE/FORBIDDEN) / status(draft/legal_reviewed/active) / source_review_id / version / content / legal_owner / usage_count。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/v1/vendors/{vendor_id}/profile | 信用画像（维度分+风险标记+依据来源）；高风险标红，无记录显示"未发现风险" |
| GET | /api/v1/vendors?risk_flag=&updated_after= | 供应商检索（分页） |
| GET | /api/v1/clauses?category=&status= | 条款库检索（起草/审查复用） |
| POST | /api/v1/clauses | 条款沉淀（带 source_review_id；仅 legal_reviewed 状态可被起草引用，FR-721） |
| POST | /api/v1/clauses/{clause_id}/publish | 版本化发布（旧版本保留可追溯） |

### 4.3 投标/起草/批量/反馈接口条目（FR-711~714、701、721~723、801~803；字段级详规随接口评审冻结）

| 方法 | 路径 | FR | 说明 |
|---|---|---|---|
| POST | /api/v1/bids/decompose | FR-711 | 招标文件拆解（200 页 ≤5 分钟出拆解报告）→ 202 + task_id |
| POST/GET | /api/v1/bids/{bid_id}/tasks | FR-711 | 角色任务派发/看板（责任人/截止时间/9:00、17:00 提醒） |
| POST | /api/v1/bids/{bid_id}/response-templates | FR-712 | 响应文件模板生成（工商信息逐字核对，不一致置人工确认） |
| POST | /api/v1/bids/{bid_id}/score-estimate | FR-713 | 评分预估+失分点+改进建议（仅决策参考） |
| POST | /api/v1/tenders/diff | FR-714 | 新旧版对比+影响分析+受影响项重审触发 |
| POST | /api/v1/evaluations/{evaluation_id}/verify | FR-701 | 评标要素提取与评分核验（偏离仅提示复核） |
| POST | /api/v1/drafts | FR-722 | 智能起草（条款库组装+合规预检；标注"AI 初稿需人工确认"） |
| POST | /api/v1/drafts/{draft_id}/review | FR-723 | 起草稿回审（复用 M2 审查；标注"起草引入/历史沿用"） |
| POST | /api/v1/reviews/batch（GET /api/v1/tasks/{task_id}） | FR-801 | 批量排队（FIFO+优先级；失败重试 ≤3 次；保留原始输入） |
| POST | /api/v1/feedback | FR-802 | 质量反馈（action + reject_reason_type 六类/MISSED；回流评测集） |
| GET | /api/v1/reviews/{review_id}/evidence/{risk_id} | FR-803 | 原文高亮锚点（section_ref+span，随 snapshot 定位） |

### 4.3.1 字段级详规（冻结 v1，2026-10-15；变更走接口评审）

| 接口 | 请求关键字段 | 响应关键字段 | 约束 |
|---|---|---|---|
| POST /api/v1/bids/decompose | doc_id（招标文件）、deadline_at? | 202：task_id、est_minutes；完成后 GET task 拿 project_elements{name,code,deadline,method}/qualifications[]/scoring_rules[]/废标条款[] | 200 页 ≤5 分钟；要素缺失标 UNKNOWN 不臆造 |
| POST/GET /api/v1/bids/{bid_id}/tasks | role(SALES/CONTRACT_BP/SE)、owner_id、due_at、checklist[] | task_id、remind_plan（09:00/17:00）、board 快照 | 责任人/截止时间完整才允许派发 |
| POST /api/v1/bids/{bid_id}/response-templates | doc_scope[]（格式章节）、biz_profile_ref | template_ids[]、field_check[]：{field,filled,biz_matched,need_confirm} | 工商核对不一致必须置人工确认，禁静默通过 |
| POST /api/v1/bids/{bid_id}/score-estimate | response_doc_id、scoring_ref | estimates[]：{item,weight,est_score,loss_reason}、suggestions[] | 输出仅供参考；客观分须与人工核算一致 |
| POST /api/v1/tenders/diff | old_doc_id、new_doc_id | 202→diffs[]：{section,change_type,impact}、affected_tasks[]、affected_rules[] | 变更确认后触发受影响项重审 |
| POST /api/v1/evaluations/{evaluation_id}/verify | score_sheets[]、objective_rules | checks[]：{item,expected,actual,verdict}、deviations[] | 偏离仅提示复核，不替代专家判断 |
| POST /api/v1/drafts | contract_type(PROCUREMENT/FRAMEWORK/NDA/SERVICE)、parties、amount、term、deliverables、clause_pick[]? | draft_doc_id、compliance_precheck[]、ai_draft_notice=true | 只组装 legal_reviewed 条款；禁用条款拦截 |
| POST /api/v1/drafts/{draft_id}/review | draft_doc_id | 202：review_id、source_tag(DRAFT_INTRODUCED/LEGACY) | 复用 M2 审查引擎；结论回流条款库 |
| POST /api/v1/reviews/batch（GET /api/v1/tasks/{task_id}） | doc_refs[]、priority | batch_id、task_ids[]；GET：status、retry_count(≤3)、raw_input_ref | 失败保留原始输入供人工处理 |
| POST /api/v1/feedback | risk_id、action(ADOPT/REJECT/REVISE)、reject_reason_type?（REJECT/REVISE 必填，六类 + MISSED）、comment | 201：feedback_id、eval_set_entry=true | 回流评测集（FR-802）；字段枚举以 §5 为准 |
| GET /api/v1/reviews/{review_id}/evidence/{risk_id} | — | section_ref、span{start,end}、quote_original、snapshot_version | 锚点随 review_snapshot 定位，版本回滚后仍一致（FR-803） |

> 通用：所有写接口带 idempotencyKey；错误码复用 §2 约定（400/409/422/503）；分页统一 page/page_size/total。

## 5. 枚举真源表（冻结于 2026-10-14，联调唯一依据）

| 枚举 | 取值（code / label） | 说明 |
|---|---|---|
| risk_item.decision | PENDING 待复核 / ACCEPTED 采纳 / REJECTED 驳回 / MODIFIED 改判 | 全文档统一，废止 ADOPTED/REVISED/纯中文值 |
| risk_item.reject_reason_type | NOT_A_RISK 不构成风险 / OUT_OF_SCOPE 适用范围不符 / BASIS_INVALID 依据不成立 / DUPLICATE 重复条目 / MISREAD 误读原文 / OTHER 其他 | PRD FR-802 操作层六类；字段名统一 reject_reason_type |
| 报表层三桶映射 | 误报 = NOT_A_RISK ∪ DUPLICATE；理由不充分 = OUT_OF_SCOPE ∪ MISREAD；依据引用错误 = BASIS_INVALID；OTHER 单列 | 仅报表归并，不落库 |
| feedback.action | ADOPT / REJECT / REVISE | reason 复用六类，另加 MISSED 漏检（仅反馈场景，入漏检率） |
| review.status（执行态） | QUEUED/PARSING/RULE_CHECK/LLM_REVIEW/MERGING/PENDING_REVIEW/REVISING/RE_REVIEWING/FINALIZED/COMPLETED/FAILED | 真源：技术方案 §2.2 状态机 |
| review.report_status（派生） | RUNNING / CONFIRMING / ARCHIVED | RUNNING=PENDING_REVIEW 之前；CONFIRMING=PENDING_REVIEW/REVISING/RE_REVIEWING；ARCHIVED=FINALIZED/COMPLETED；FAILED 单列 |
| alert_task.node_type | PAYMENT 付款 / ACCEPTANCE 验收 / WARRANTY 质保 / DEADLINE 硬性到期 / PERFORMANCE 履约节点 | 合并 PRD「PERFORMANCE」与旧契约「DEADLINE」两套 |
| alert_task.status | UPCOMING / DUE / OVERDUE / CLEARED | EXPIRED 并入 OVERDUE（API 兼容别名） |

> 回写完成：PRD §7.2、技术方案 §3.4/DDL/API 表均已对齐本表。变更枚举须走接口评审并同步三处。

---

## 附注（待补清单，2026-10-14 复核挂账）

- ~~§4.1 revision_task 契约~~（原 §4.9 悬空指针）：**已补**（FR-205 处置链可进联调）。
- ~~vendor_profile / clause_library 字典与 /vendors 接口~~：**已补**（§4.2）。
- ~~枚举统一~~：**已冻结**（§5）并回写 PRD §7.2 / 技术方案。
- FR-711~714/701/721~723/801~803：**已补契约级条目**（§4.3），字段级详规随接口评审冻结后回填。
