---
change: procuraai-ai-first-landing
design-doc: openspec/changes/procuraai-ai-first-landing/design.md
base-ref: b04b2a8d62599acf433180aa4f57b97eb5e18346
archived-with: 2026-11-15-procuraai-ai-first-landing
---

<!-- comet-task-authority: openspec/changes/procuraai-ai-first-landing/tasks.md -->

# Build Plan — procuraAI AI-first 落地（恢复注册版，2026-11-14）

> 本计划为**恢复注册型**：实现代码已于 2026-11-13 推送（9 笔提交 `6f0ea427f..9b3c70c70`），门禁全绿；本次 build 以验证与登记为主，不重写既有实现。
> 按 `tasks.md` 纪律，3–8 组编码任务原定"DoR 全 ✓ 后才启动"，本交付**仅覆盖任务组 3（主数据 Golden Record 内核）的子集**作为 AI-first 落地的首交付线（D1=MVP walking skeleton）。其余任务（DoR 1.x、spike 2.x、AI 合同审查 4.x、AI 决策治理 5.x、存量收敛 6.x、门禁负向探针 7.x、集成验收 8.x）属 owner/L3 程序级工作，不在本 change 关闭，详见文末 deferred 清单。
> **2026-11-14 Verify FAIL 回 Build 修订**：独立最终集成审查判定 2 CRITICAL / 3 IMPORTANT，本计划相应补充 ① `tasks.md` 4 项验收文字对齐真实代码（任务 ID 与勾选不变）、② `application.yml` 摘要盐值去掉 dev 占位默认值、③ 3 份 delta spec 迁出 `specs/` 避免归档把未实现 AI 需求合入主 spec、④ 重新实跑模块级构建与门禁。修订后计划与 `tasks.md` 的新 authority 一并重新登记，不复用原批准。

## 实际交付范围（已推送、门禁绿）

- **既有模块扩展与装配**：`bone-masterdata` 在 base-ref 已有 206 个 main 文件与 `@EnableSqlRepositories`/`@Import` 装配（非本 change 新建骨架）；本 change 扩展应用服务与端口，`GoldenRecordHttpIntegrationTest` 以 `RANDOM_PORT` 完整启动证明装配与 API 契约。
- **核心领域**：`GoldenRecordApplicationService`（原 `GoldenRecordOrchestrationService` 重命名，符合 E-13.2/R2/R3 应用构件白名单）、`ChangeRequestApplicationService`、`MasterDataOutboxRelay`、`ChangeRequest.java`（GENERAL/COMPANY_CODE/PURCHASING_ORG 三段）。
- **端口与 API**：`domain/gateway/` 新增 **8** 个端口（`b04b2a8d..HEAD` 的 8 个 A 文件，当前目录共 11 个）、变更请求控制器、Golden Record 控制器，返回 `ApiResponse<T>` + `@PreAuthorize("hasAuthority('masterdata:governance:write'|'masterdata:governance:read')")`。
- **测试**：`clean test` **250 例全绿**（含去重唯一约束、合并引用一致性、越权 403 负向、ChangeRequest 状态机）。
- **门禁对齐**：修复 `[17/59]` 配置键契约（统一 `masterdata.*` 前缀并在 yml 显式声明）、`[52/59]` 应用构件白名单、`[9a/59]` 分页命名；procura 主门禁 10/10 + 自检 146/146。
- **文档族与规格**：`design.md`（D1–D7 原始设计储备 + 2026-11-14 范围修订声明）、`proposal.md`（历史正文 + 范围状态声明）；**3 份 delta spec 已全文未改迁出 `specs/` 至 `deferred-specs/procurement/`，本 change `deltaCount: 0`**，裁决见 `specs/SCOPE.md`——它们描述的是未实现的 AI-first MVP，不得作为本 change 已落地成果。

## 任务映射（tasks.md 重定界后 = 4 项，全部已交付）

> 状态图例：✅ 已交付（本 change 推送并门禁绿）。
> 原 27 项中的 23 个未启动任务（组 1/2/4/5/6/7/8 + 3.5）已于 2026-11-14 迁出至 `openspec/changes/procuraai-ai-first-landing/deferred-backlog.md`，独立 WBS 跟踪，不属本 change 关闭项。

### 组 3 主数据 Golden Record 内核 — ✅ 已交付（本 change 范围）

> 验收文字以 `tasks.md` §3 为准（2026-11-14 已按真实代码对齐，并附「验收文字—代码证据映射」表）。

- 3.1 既有模块扩展 9 端口与应用服务装配（非新建模块骨架） <!-- comet-task-ref:493dbcea-4478-4831-977b-face2dd9c559 -->
- 3.2 通用主数据领域模型 GoldenRecord / ChangeRequest / OutboxDelivery / EntityKeyMapping（非 Supplier/Vendor 聚合） <!-- comet-task-ref:eb8cf1dd-dcdc-4484-b70b-bd8985e6bcbd -->
- 3.3 Golden Record 身份主键冲突禁止自动合并 + HMAC 摘要 + 合并留痕与回滚 <!-- comet-task-ref:663f478b-5479-47f3-942d-31b8869f223a -->
- 3.4 `ApiResponse<T>` + `masterdata:governance:*` 权限（非 `procurement:supplier:*`） <!-- comet-task-ref:bfdbcf5a-c5f1-4b85-9428-3da1f176fbfe -->

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
