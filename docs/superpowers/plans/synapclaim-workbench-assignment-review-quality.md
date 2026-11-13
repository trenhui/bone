---
change: synapclaim-workbench-assignment-review-quality
design-doc: openspec/changes/archive/2026-11-13-synapclaim-workbench-assignment-review-quality/design.md
base-ref: 6f0ea427fdee36fad49994bf7854bf075e6a9a28
archived-with: 2026-11-13-synapclaim-workbench-assignment-review-quality
---

# 实施计划：理赔作业台融合增强（分配 / 复审 / 抽检打回）

<!-- comet-task-authority: openspec/changes/synapclaim-workbench-assignment-review-quality/tasks.md -->

## 0. 执行配置与约束

- 执行方式 `autonomous`，TDD `direct`（文档型交付不做 RED/GREEN，但仍做可执行检查），审查 `standard`。
- 工作区：独立 worktree `.worktrees/synapclaim-workbench-assignment-review-quality`，分支 `comet/synapclaim-workbench-assignment-review-quality`，基于 `dev@6f0ea427f`。
- 产物语言 zh-CN。任务完成状态只认 `tasks.md`；技术取舍以唯一 Design Doc（本 change `design.md`）为准。
- **本轮不改任何 Java / TS / SQL**，不新增迁移、不改 DDL、不改权限码数据。所有字段级论断必须给出 `bone-init.sql` 或源码行号。
- 文档族治理：新文档须登记 `docs.manifest.json` + front-matter（`doc_id`/`role`/`currency`），`AI-INDEX.md` 用工具重生成，禁止手改；`check_all.py` 19 项必须保持通过。

## 1. 交付物结构

| 交付物 | 路径 | 对应任务 |
|---|---|---|
| 深度分析报告 | `docs/superpowers/reports/2026-11-13-synapclaim-workbench-assignment-review-quality-analysis.md` | 1.1 / 1.2 |
| 作业分配与代理交接设计 | `doc/ai-product/synapClaim/04-design/作业分配与代理交接设计.md` | 2.1 / 2.4 |
| 独立复审阶段设计 | `doc/ai-product/synapClaim/04-design/独立复审阶段设计.md` | 2.2 / 2.4 |
| 抽检打回闭环设计 | `doc/ai-product/synapClaim/04-design/抽检打回闭环设计.md` | 2.3 / 2.4 |
| 既有文档回写 | `04-design/工程详细设计-落地版.md`、`04-design/AI技术方案.md`、`04-design/总体技术方案.md`、`05-plan/智能理赔-易用性与角色工作台设计提案.md` | 3.1-3.4 |
| 治理同步 | `docs.manifest.json`、`AI-INDEX.md`、`README.md` | 4.1-4.3 |
| 分批落地清单 | 各设计文档末章 + 提案 v2.3 附录 | 5.1 / 5.2 |

## 2. 任务与验收方式

<!-- comet-task-ref:c10fd26b-3693-4306-af43-362fe5228680 -->
### 2.1 深度分析报告（任务 1.1 / 1.2）

范围：现状取证矩阵（后端/前端/文档三线）、业界对标、差距清单、漂移清单。
验收：每条结论可按行号复现；随机抽 5 条用 `sed -n '<行>p'` 核对与报告一致；漂移清单三项（`org_id`、任务 ID=案件 ID、≥3% 抽检只在文档）逐项确认方向正确。
约束：不得写「体验更好」类不可测结论；行业数字一律标注为方向性依据，不作本项目承诺值。

<!-- comet-task-ref:98348da3-ab60-4951-8dac-a1cb62b4b521 -->
### 2.2 作业分配与代理交接设计（任务 2.1）

范围：`t_claim_task` / `t_claim_assign_rule` / `t_claim_user_agent` 表设计（含生成列唯一约束）、分配求解流程、并发幂等、转交/代理/不可用三层、索引。
验收：每张表的列类型与命名与 `bone-init.sql` 既有风格一致；生成列唯一约束 SQL 可在 MySQL 8 直接执行（本地起库试跑后丢弃，不入仓）；分配流程四步可被批 1 直接实现。

<!-- comet-task-ref:8527dd1c-4bba-4742-a959-058598256903 -->
### 2.3 独立复审阶段设计（任务 2.2）

范围：状态码 43/46 定义、全部合法迁移、与 44 双人复核的边界、职责分离、权限码登记、复审队列与 SLA。
验收：迁移表覆盖 `ClaimStatus` 全部现有状态（用脚本比对枚举全集，无遗漏/冲突）；与 `ClaimCase.transition()` 现有守卫不矛盾。

<!-- comet-task-ref:8b4ed1c2-2e8e-46a3-8fe2-3a5609e59a5d -->
### 2.4 抽检打回闭环设计（任务 2.3）

范围：`t_claim_inspection_action`、未支付/已支付两条路径、强制冲正前置、幂等与一单一处置。
验收：与 `SettlementController`/`REFUND` 语义一致；文档明确写出「已支付未冲正禁止打回」的拒绝码与判断顺序。

<!-- comet-task-ref:3b0c8eae-ccc4-412f-b86a-a8e850c3984e -->
### 2.5 三份新文档的治理必备节（任务 2.4）

范围：必备四节（决策摘要 / 风险与回退 / 不做清单 / 待决策）+ front-matter。
验收：`check_all.py` 对应规则通过。

<!-- comet-task-ref:76c3db6f-537a-46cd-989f-04e2eed208c2 -->
### 2.6 既有文档回写（任务 3.1-3.4）

范围：§9.3/§10.3 的 `org_id` 纠正、§7.6/§11.10 复审与质检边界、AI 三层配置边界、提案升 v2.3。
验收：`org_id` 在文档族内为 0 命中（漂移说明除外）；阈值默认值与 `DecisionTriage.java:21-32` 一致；提案 diff 仅新增不删改 v2.2 菜单结论。

<!-- comet-task-ref:b5d2ec3e-f791-490b-bee2-c070a0abc256 -->
### 2.7 治理同步（任务 4.1-4.3）

验收：`_gen_ai_index.py --check` 一致；`check_all.py` 19/19；`scripts/check.sh` 无新增红点。

<!-- comet-task-ref:05fa83ec-49ae-439e-8bf7-62fb3b03f54f -->
### 2.8 分批落地清单（任务 5.1-5.2）

验收：每批可独立构成一个 change（无跨批循环依赖）；批 1 的 5 条判据均可执行（SQL 或探针）。

<!-- comet-task-ref:162a826a-cd41-43d9-9008-2c0ccafee137 -->
### 2.9 自检与提交（任务 6.1-6.2）

验收：`_check_xref.py` 通过；提交只含本 change 路径，不含主工作树并行会话文件。

## 3. 风险与回退

- 文档回写误删既有结论 → 回写前先 `git diff` 逐段核对，只做最小替换；发现歧义保留原文并加「本次修订」标注。
- 新文档与 manifest front-matter 不一致 → 先写文件再登记，用 `_check_xref.py`/`check_all.py` 反查。
- 文档体量大导致门禁超时 → 分批提交，不一次性重生成索引。

## 4. 完成判据

`tasks.md` 17 项全部勾选且每项验收命令有真实输出；`check_all.py` 19/19；本 change 产物已按精确路径提交到 change 分支。

## 5. 其余任务映射

<!-- comet-task-ref:a81a3f61-c607-4bc1-a0f6-5cd776529e8d -->
- **1.2 漂移清单**：报告中单列《文档↔代码漂移清单》，覆盖 `org_id`、「任务 ID 即案件 ID」、「≥3% 抽检只在文档未上屏」三项，逐项给文档位置 / 代码事实 / 处置建议。验收：三项方向在 `bone-init.sql` 与对应 Java 文件中复核一致。

<!-- comet-task-ref:152c4a34-2006-4bdf-a4e5-21d7b5732715 -->
- **3.2 §7.6 与 §11.10 回写**：对齐「复审=流程内质量闸门、抽检=结案后事后监督」，明确二者不重叠。验收：两节用词表交叉比对无自相矛盾。

<!-- comet-task-ref:9fb13949-e9e4-4787-9fa5-c0059408f24a -->
- **3.3 AI 技术方案 §10.1 与总体技术方案 §7/§8 回写**：补入自动初审三层配置边界与只收紧棘轮。验收：文档阈值默认值与 `DecisionTriage.java:21-32` 完全一致。

<!-- comet-task-ref:af791f59-03d6-4c3c-a31b-2cdb42009158 -->
- **3.4 提案升 v2.3**：补我的待办/全部待办、三工作台查询条件集、AI 建议触达与采纳回流、核赔页假分页修正四处增量。验收：菜单与导航结论与 v2.2 原文逐字一致（diff 仅新增）。

<!-- comet-task-ref:dbfe0860-a0e1-49ca-bf77-6dc6c412b0a7 -->
- **4.2 重生成 AI-INDEX**：`python3 tools/_gen_ai_index.py`，禁止手改。验收：`--check` 逐字节一致。

<!-- comet-task-ref:f801faad-8351-45cd-bc7e-bbcd330e0bd3 -->
- **4.3 全量文档门禁**：`python3 doc/ai-product/synapClaim/tools/check_all.py` 输出 19/19。验收：真实退出码 0 且无步骤级红点。

<!-- comet-task-ref:f4b9a667-aa09-4782-b10b-5fab8dcc7fbc -->
- **5.2 批 1 验收判据预写**：分配命中率、无人可派回落率、我的待办 P95、分页 total 正确性、索引覆盖。验收：每条均为可执行 SQL 或探针，不含不可测表述。

<!-- comet-task-ref:f5482096-1132-42ba-9b45-9096e1e890cb -->
- **6.2 精确路径提交**：只提交本 change 路径到 change 分支。验收：`git status --porcelain` 无本 change 未提交项，且未包含主工作树并行会话文件。
