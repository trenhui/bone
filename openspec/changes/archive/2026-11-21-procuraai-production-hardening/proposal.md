# Proposal

## Why

procuraAI 已通过 7 个归档 change 交付合同 AI 审查、决策治理、安全护栏、业务骨架与建议态骨架，但工程落点仍是 walking skeleton：9 个仓储/端口由 `InMemory*` 实现，合同审查与治理域没有持久化表，跨租户写入可被覆写，事件闭环仍在进程内。门禁 10/10 与 27 例 seed 评测集只证明结构与 fail-closed 行为不退化，不能证明生产可用。本 change 把这些骨架升级为可持久化的生产加固态，同时不跨越「AI 仅辅助、人工终决、禁自动放款/授标」与「评测转正/契约冻结由 owner 拍板」两条红线。

## What Changes

- 把 `sourcing` / `qualification` / `risk` 三个业务骨架的 InMemory 仓储切换到 `bone-metadata-sdk` 的 `Repository`，复用既有 6 张 `t_proc_*` DDL 与实体注解，仅在 `ProcurementApplication` 的 `@EnableSqlRepositories` 增加扫描包并移除同接口内存 bean；应用服务签名不变。
- 为 `contract` 与 `ai-governance` 的 6 个内存端口（审查记录、审查轨迹、能力注册表、人工决策台账、全域 kill switch、治理轨迹）新增 init-only DDL 表与 SDK 实体/仓储 Adapter，保留原端口边界与能力开关语义；`review_id` 以 `(tenant_id, review_id)` 唯一约束兜底，禁止跨租户覆写。
- 把「持久化后读回一致」「总闸每次裁决读最新快照、无缓存」「人工终决与 AI 建议分字段且无 output 原文」「同一 review_id 幂等」固化为源码测试与门禁负向探针；新增判据按 A10.17 同步 `ci.yml` / `scripts/check.sh` / `check-procura-ci.sh` 三入口及自检反证，避免假绿。
- 明确非目标：真实模型接入与效果声明（D-17）、评测集 27→200 与 `seededDraft` 转正（D-15）、D3-v2 / LLD 契约冻结（D-16/D-09）、生产 MQ/Outbox/Inbox 完整接线、Award/Bid/Quotation/CAPA 完整聚合、新增前端页面、法务结论。用户已授权触碰 DDL/CI/真模型/评测集四处，但本轮只使用 DDL 与 CI 授权；不执行生产库，不伪造标注、通过线或冻结状态。
- 工作区沿用当前 `dev` 目录，不建分支/Worktree；未跟踪的 `openspec/changes/synapclaim-architecture-convergence/` 与本 change 无关，保持不处理。

**BREAKING**：无。既有 REST 端点、事件 payload 七字段、能力三态与披露文案保持不变；仅替换内部持久化实现与新增 init-only 表。

## Capabilities

### New Capabilities

- `procurement/production-persistence`: 采购域 AI 审查/治理/业务骨架的持久化生产加固行为——SDK 仓储落地、租户隔离写入、跨租户唯一约束、幂等读回、总闸无缓存即时生效、审计留痕与机器可验证的负向反证。

### Modified Capabilities

（无。本轮只增加可观察的持久化与隔离能力，不修改既有 capability 的对外需求文本。）

## Impact

- 代码：`bone-apps/bone-procurement` 下 `bone-procurement-contract`、`bone-procurement-ai-governance`、`bone-procurement-sourcing`、`bone-procurement-qualification`、`bone-procurement-risk`、`bone-procurement-app`。
- 数据：`bone-init.sql` 新增 6 张 init-only 表（合同审查、审查轨迹、能力登记、人工决策台账、全域开关、治理轨迹），沿用必备 8 列与 `uk_` 租户打头约定；`数据库开发规范.md` 表数登记同步（15 → 21）。
- 门禁：`tools/check_procura_alignment.py` 判据常量、三入口 blocking 步骤、A10.28 证据清单与 `--self-test` 注入反证；不削弱既有 A1–A10 判据。
- 验证：后端 Maven 全量单测 + ArchUnit（含 E-5.4）、DDL 对账测试、门禁主判据与负向反证、前端 tsc/eslint(json)/vitest；CI 绿只证明结构与隔离行为，不等于 AI 可上线。
