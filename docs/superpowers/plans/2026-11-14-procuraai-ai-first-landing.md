---
change: procuraai-ai-first-landing
design-doc: openspec/changes/procuraai-ai-first-landing/design.md
base-ref: b04b2a8d62599acf433180aa4f57b97eb5e18346
---

<!-- comet-task-authority: openspec/changes/procuraai-ai-first-landing/tasks.md -->

# Build Plan — procuraAI AI-first 落地（恢复注册版，2026-11-14）

> 本计划为**恢复注册型**：实现代码已于 2026-11-13 推送（9 笔提交 `6f0ea427f..9b3c70c70`），门禁全绿；本次 build 仅做验证与登记，不重写既有实现。
> 按 `tasks.md` 纪律，3–8 组编码任务原定"DoR 全 ✓ 后才启动"，本交付**仅覆盖任务组 3（供应商主数据 MDM）的子集**作为 AI-first 落地的首交付线（D1=MVP walking skeleton）。其余任务（DoR 1.x、spike 2.x、AI 合同审查 4.x、AI 决策治理 5.x、存量收敛 6.x、门禁负向探针 7.x、集成验收 8.x）属 owner/L3 程序级工作，不在本 change 关闭，详见文末 deferred 清单。

## 实际交付范围（已推送、门禁绿）

- **模块骨架与装配**：`bone-masterdata` 六边形架构落地，`@EnableSqlRepositories` + `@Import` 装配冒烟测试通过。
- **核心领域**：`GoldenRecordApplicationService`（原 `GoldenRecordOrchestrationService` 重命名，符合 E-13.2/R2/R3 应用构件白名单）、`ChangeRequestApplicationService`、`MasterDataOutboxRelay`、`ChangeRequest.java`（GENERAL/COMPANY_CODE/PURCHASING_ORG 三段）。
- **端口与 API**：5 个端口、变更请求控制器、Golden Record 控制器，返回 `ApiResponse<T>` + `@PreAuthorize`。
- **测试**：`clean test` **250 例全绿**（含去重唯一约束、合并引用一致性、越权 403 负向、ChangeRequest 状态机）。
- **门禁对齐**：修复 `[17/59]` 配置键契约（统一 `masterdata.*` 前缀并在 yml 显式声明）、`[52/59]` 应用构件白名单、`[9a/59]` 分页命名；procura 主门禁 10/10 + 自检 146/146。
- **文档族**：`design.md`（D1–D7 业界校准版）、`proposal.md`、3 个 delta specs（ai-contract-review / ai-decision-governance / supplier-master-data）、详设与架构文档已落地。

## 任务映射（tasks.md 重定界后 = 4 项，全部已交付）

> 状态图例：✅ 已交付（本 change 推送并门禁绿）。
> 原 27 项中的 23 个未启动任务（组 1/2/4/5/6/7/8 + 3.5）已于 2026-11-14 迁出至 `openspec/changes/procuraai-ai-first-landing/deferred-backlog.md`，独立 WBS 跟踪，不属本 change 关闭项。

### 组 3 供应商主数据 MDM — ✅ 已交付（本 change 范围）
- 3.1 模块骨架+装配冒烟 <!-- comet-task-ref:493dbcea-4478-4831-977b-face2dd9c559 -->
- 3.2 实体+租户声明 <!-- comet-task-ref:eb8cf1dd-dcdc-4484-b70b-bd8985e6bcbd -->
- 3.3 Golden Record 去重+合并留痕 <!-- comet-task-ref:663f478b-5479-47f3-942d-31b8869f223a -->
- 3.4 Controller 信封+权限 <!-- comet-task-ref:bfdbcf5a-c5f1-4b85-9428-3da1f176fbfe -->

## 验收方式（复用既有证据，不重演）

1. `mvn -o -f bone-platform/bone-masterdata/pom.xml clean test` → 250 绿（已记录）。
2. `scripts/check.sh` 全仓 59 项 → 除 `[18/59]` 既有 4 个 synapClaim `Claim*` 漂移（非本域）外全绿。
3. procura 主门禁 `check-procura-ci.sh` 10/10 + `check_procura_alignment.py --self-test` 146/146。

## Deferred（owner/L3，独立 WBS）

- DDL 落库（`t_proc_*` 字段级，L3 审批，AI 只出草案）
- 生产 JDBC adapter、bone-workflow-engine CR adapter、真实消费侧
- Steward 具名署名、ADR 正式条目
- DoR 1.x 全 ✓、spike 2.x、AI 合同审查 4.x、AI 决策治理 5.x、存量收敛 6.x、集成验收 8.x

## 边界声明

- "演示通过 ≠ 生产可用"：跨域 Mock、评测 ≥200、SLO 仍属后续验收。
- 不声称 EU AI Act 高风险合规；AI 不碰钱（放款/付款）为基线。
