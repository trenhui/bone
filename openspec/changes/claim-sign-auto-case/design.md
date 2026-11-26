---
comet_change: claim-sign-auto-case
role: technical-design
canonical_spec: openspec
status: final
---

## Context

见 proposal.md - Why。当前约束：

- `ClaimCase.draft` 要求报案（`reportId`）、保单号、计划 ID、假名化 ID、出险时间与至少一条费用明细；`ClaimReport.open` 要求保单号、计划 ID、假名化 ID、出险时间。
- `ClaimSignBatchRepository` 的 `findById` 不回填明细集合；`SignBatchItem` 目前只有 `claim_id/claim_no/image_ready`。
- 现有 15 条 TPA 明细中 12 条在本地无案件；3 条已有案件（由另一次 `import_to_bone.py` 建立）。
- 用户裁决：自动化尽量全通（建案→受理→分配初审→自动初审），只有不通过/卡住才人工；实名明文落主表。

## Goals / Non-Goals

**Goals:**
- 签收确认（线上自动 + 线下人工）后按明细自动建案，幂等且可观测。
- 自动化流水线尽量推进到初审，失败时明确定位到人工状态并留轨迹。
- 保留原「引用已有案件」路径，不对既有报案提交的响应结构做破坏性变更。

**Non-Goals:**
- 本轮不改造保司推送网关的字段协议，只保证实名数据在案件主表可查。
- 不实现完整的 OCR/影像自动完备判定；影像仍由既有登记动作驱动。
- 不修改前端权限模型与菜单结构。

## Decisions

### 1. 建案触发点：事件订阅（AFTER_COMMIT）而非在签收用例里同步建案

签收用例已在事务内完成 CAS + 轨迹 + 事件发布。新增 `ClaimSignCaseCreator` 订阅 `SignStatusChangedEvent`（`toStatus=FINISHED`），在 `TransactionPhase.AFTER_COMMIT` 执行建案流水线。这样建案失败不会回滚已完成的签收，符合「签收是业务事实、建案是后续自动化」的语义；事件重放天然走幂等查重。

备选：在 `confirmOffline` 内直接调用建案服务。否决——CAS 成功后事务提交前建案失败会连带回滚签收，把已确认的业务事实撤销。

### 2. 明细表扩展要素列，保留「引用案件」与「携带要素建案」双路径

扩展 `t_claim_sign_batch_item`：新增 `policy_no`、`plan_id`、`insured_name`、`insured_id_no`、`out_insure_time`、`claim_type`、`source_channel`、`business_key`、`case_status`、`created_case_id`。`claim_id` 为 NULL 表示尚未关联案件，确认签收时按 `business_key` 建案并回写 `created_case_id`；`claim_id` 非 NULL 表示引用已有案件，只推进阶段。老明细全部 `claim_id` 非空，行为不变。

### 3. 幂等键：明细级 `business_key`，唯一索引兜底

每条明细的 `business_key = pk-tpa-sign-<批次号>-<赔案号>`（外部推送用 `businessKey + 赔案号` 派生）。建案前按 `tenant_id + business_key` 查案件与报案，命中则跳过；`t_claim_case.idempotency_key` 的 `uk_tenant_idem` 唯一索引作为并发兜底。重复签收/事件重放均不会建重复案。

### 4. 自动化流水线：尽量推进，失败降级不回滚

建案后依次调用 `accept()` → `allocateToPreExam()` → `startPreExam(automated=true)`；每步新事务，失败时 catch 并记录轨迹（动作 `AUTO_CASE_FAILED`），案件停在失败前一步的合法状态（已受理或待初审分配）。不抛到事件发布方、不回滚签收或案件创建。初审规则引擎不可用时落到 `PENDING_PRE_EXAM`。

### 5. 实名口径：显式偏离 PIPL，明文落主表

按用户裁决，`insured_id_hash` 存明文身份证，`name_masked` 存明文姓名（字段名保留历史命名）。在 `design.md` 记录为已知风险：不满足假名化设计初衷，仅限本地开发与受控环境；日志/轨迹不打印完整证件号。

### 6. 计划 ID：沿用本地既有口径 `plan_id = 0`

本地开发库无对应保险计划，与 `import_to_bone.py` 一致用 0；`ClaimReport.open` / `ClaimCase.draft` 只要求非 NULL，0 可通过。费用明细构造一条金额为 0 的占位 `ClaimItem` 满足「至少一个明细项」的领域约束。

## Risks / Trade-offs

- **实名明文落库**：偏离 PIPL 假名化设计 → 仅限本地开发库；设计文档与 spec 明确记录，未来接生产前必须重新评估。
- **AFTER_COMMIT 事件失败不影响签收**：建案失败时签收仍为 FINISHED → 轨迹写 `AUTO_CASE_FAILED`，前端展示待人工数，人工可补录。
- **占位费用明细（金额 0）**：影响理算金额口径 → 本轮只建案不理算，案件停在初审前，人工/后续流程可补充真实费用。
- **计划 ID=0**：额度/规则引擎可能不覆盖 → 自动化失败时降级到人工，符合预期。
- **迁移扩展明细表**：存量明细行新列 NULL → 迁移用 `DEFAULT NULL`，读路径对 NULL 走「引用案件」旧逻辑。

## Migration Plan

1. 新增迁移 `0145_claim_sign_auto_case.sql`：扩展明细表要素列 + 新增 `uk` 索引；终态断言列齐全。
2. `bone-init.sql` 同步；用隔离 manifest 在本地开发库应用迁移。
3. 回填脚本把 12 个缺失案件与 15 条明细要素写入，按赔案号幂等。
4. 回滚：迁移为 ADD COLUMN，可 DROP COLUMN 回滚（需先备份实名数据）；应用代码回滚后明细表多列无害。

## Open Questions

无。所有影响范围与方案的问题均已在 Open 阶段由用户裁决。
