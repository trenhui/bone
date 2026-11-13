## 1. 深度分析报告

- [ ] 1.1 产出《作业台融合增强深度分析报告》`docs/superpowers/reports/2026-11-13-synapclaim-workbench-assignment-review-quality-analysis.md`：含后端/前端/文档三线现状取证矩阵（每条带 `文件:行号`）、业界对标（Guidewire ClaimCenter 工作队列与 Adjuster Workbench、SAP Fiori Spaces 配额、Duck Creek 90% 人机一致率、Five Sigma 四级自主度、NAIC/EIP 审慎口径）、差距清单与优先级排序；验证方式：报告中每条现状结论均可按其行号在 worktree 中复现，随机抽 5 条用 `sed -n` 核对一致 <!-- comet-task:c10fd26b-3693-4306-af43-362fe5228680 -->
- [ ] 1.2 报告中单列《文档↔代码漂移清单》：至少覆盖 `org_id`（文档 §9.3/§10.3 有、DDL 无）、「任务 ID 即案件 ID」、「审核质量抽检 ≥3% 只在文档未上屏」三项，每项给「文档位置 / 代码事实 / 处置建议」；验证方式：逐项在 `bone-init.sql` 与对应 Java 文件中确认结论方向正确 <!-- comet-task:a81a3f61-c607-4bc1-a0f6-5cd776529e8d -->

## 2. 新增设计文档（`doc/ai-product/synapClaim/04-design/`）

- [ ] 2.1 新增《作业分配与代理交接设计》：含 `t_claim_task` 与 `t_claim_assign_rule` 表结构（列、索引、生成列唯一约束）、分配求解流程（显式指派 → 规则 → 候选集打分 → 回池）、并发与幂等、转交/代理/不可用三层模型；验证方式：文档中每张表的每列与 `bone-init.sql` 现有表命名/类型风格一致，且生成列唯一约束写法可在 MySQL 8 上直接执行（本地起库试跑后丢弃） <!-- comet-task:98348da3-ab60-4951-8dac-a1cb62b4b521 -->
- [ ] 2.2 新增《独立复审阶段设计》：状态码 43/46 定义与全部合法迁移、与 44 双人复核的边界、职责分离校验、复审队列与 SLA、权限码 `claim:re-review:write` 登记；验证方式：状态迁移表覆盖 `ClaimStatus.java` 全部现有状态，无遗漏无冲突（用脚本比对枚举全集） <!-- comet-task:8527dd1c-4bba-4742-a959-058598256903 -->
- [ ] 2.3 新增《抽检打回闭环设计》：处置动作与 `t_claim_inspection_action`、未支付/已支付两条打回路径、强制冲正前置、幂等与一单一处置约束；验证方式：设计中的资金约束与 `SettlementController`/`REFUND` 语义一致，文档明确写出「已支付未冲正禁止打回」的拒绝码 <!-- comet-task:8b4ed1c2-2e8e-46a3-8fe2-3a5609e59a5d -->
- [ ] 2.4 三份新文档均含治理规约要求的必备节（决策摘要 / 风险与回退 / 不做清单 / 待决策）与 front-matter（`doc_id`/`role`/`currency`）；验证方式：`python3 doc/ai-product/synapClaim/tools/check_all.py` 对应规则通过 <!-- comet-task:3b0c8eae-ccc4-412f-b86a-a8e850c3984e -->

## 3. 既有设计文档回写

- [ ] 3.1 回写《工程详细设计（落地版）》§9.3/§10.3：删除不存在的 `org_id` 依据，改为作业组 = `iam_dept`，并指向新《作业分配与代理交接设计》；验证方式：全文 grep `org_id` 在 synapClaim 文档族内为 0 命中（或仅出现在「漂移说明」中） <!-- comet-task:76c3db6f-537a-46cd-989f-04e2eed208c2 -->
- [ ] 3.2 回写 §7.6 与 §11.10：把「100% AI 质检 + 人工分层抽检」与本次「打回重办」对齐，明确「复审=流程内质量闸门、抽检=结案后事后监督」不重叠；验证方式：两节对复审/质检的定义无自相矛盾（交叉比对用词表） <!-- comet-task:152c4a34-2006-4bdf-a4e5-21d7b5732715 -->
- [ ] 3.3 回写《AI技术方案》§10.1 与《总体技术方案》§7/§8：补充「自动初审三层配置边界（L1 代码规则 / L2 租户策略 / L3 作业参数）」与只收紧棘轮，指向新增配置 API；验证方式：文档中的阈值默认值与 `DecisionTriage.java:21-32` 完全一致 <!-- comet-task:9fb13949-e9e4-4787-9fa5-c0059408f24a -->
- [ ] 3.4 升级《智能理赔-易用性与角色工作台设计提案》至 v2.3：补「我的待办 vs 全部待办」「三工作台查询条件集」「AI 建议触达与采纳回流」「核赔页假分页修正」四处增量，保留 v2.2 已收口的菜单/导航结论不动；验证方式：文档版本历史新增一行，且 §3.x 菜单结论与 v2.2 原文逐字一致（diff 仅新增不删改） <!-- comet-task:af791f59-03d6-4c3c-a31b-2cdb42009158 -->

## 4. 文档族治理同步

- [ ] 4.1 在 `docs.manifest.json` 登记 3 份新文档（`id`/`path`/`layer`/`diataxis`/`status`/`summary`/`authority`/`role`/`currency`），并修正 `README.md` 中已漂移的文档总数；验证方式：`_gen_ai_index.py --check` 与 M1/M9 规则通过 <!-- comet-task:b5d2ec3e-f791-490b-bee2-c070a0abc256 -->
- [ ] 4.2 重新生成 `AI-INDEX.md`（禁止手改）；验证方式：`python3 tools/_gen_ai_index.py --check` 逐字节一致 <!-- comet-task:dbfe0860-a0e1-49ca-bf77-6dc6c412b0a7 -->
- [ ] 4.3 跑全量文档门禁；验证方式：`python3 doc/ai-product/synapClaim/tools/check_all.py` 输出 19/19 通过，`scripts/check.sh` 无新增红点 <!-- comet-task:f801faad-8351-45cd-bc7e-bbcd330e0bd3 -->

## 5. 分批落地清单

- [ ] 5.1 产出《分批落地清单》：4 批各自的范围、涉及文件、DDL 变更、验收判据、依赖与回滚开关，写入设计文档末章并同步到提案；验证方式：每批都能独立构成一个 OpenSpec change（范围自洽、无跨批循环依赖） <!-- comet-task:05fa83ec-49ae-439e-8bf7-62fb3b03f54f -->
- [ ] 5.2 为批 1 预写验收判据：分配命中率、无人可派回落率、我的待办 P95、分页 total 正确性、4 类索引覆盖；验证方式：每条判据都有可执行的度量方式（SQL 或探针），不含「体验更好」这类不可测表述 <!-- comet-task:f4b9a667-aa09-4782-b10b-5fab8dcc7fbc -->

## 6. 自检与提交

- [ ] 6.1 全仓文档引用检查：所有新增 `《短名》 §X` 引用的短名已在 manifest `shortNames` 登记，相对链接可达；验证方式：`_check_xref.py` 通过，`grep -c` 统计新增引用数与登记数一致 <!-- comet-task:162a826a-cd41-43d9-9008-2c0ccafee137 -->
- [ ] 6.2 按精确路径提交本 change 交付物到 `comet/synapclaim-workbench-assignment-review-quality` 分支；验证方式：`git status --porcelain` 中本 change 路径全部为已提交状态，且未包含主工作树并行会话的任何文件 <!-- comet-task:f5482096-1132-42ba-9b45-9096e1e890cb -->
