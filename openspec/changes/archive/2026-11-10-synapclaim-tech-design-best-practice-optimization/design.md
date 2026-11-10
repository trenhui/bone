# 技术设计（tweak · 纯文档口径优化）

## 方案

只回写两份已有文档，不新增业务文档、不动代码。所有改动按「现状取证 → 口径更正 → 后果登记 → 交人项」四段式写，沿用本文既有的时态标注（current / target）与「按读取点取证」方法论。

## 改动落点与验证

| # | 落点 | 改动 | 验证方式 |
|---|---|---|---|
| 1 | §5.1 / §4.1.2 / §8.3 | 版本绑定：写入侧已落地、读取侧未接线；列出 `IndemnityApplicationService.resolveLiability()` / `recalculate()` / `findByUuidAndVersion` 生产零调用 / `formula_snapshot` 为输出物四类证据 | 三处时态串命中；`check_all.py` 全绿 |
| 2 | §13.1 A | A 判据剔除「分段比例参与计算」，超集表达归 B；增加「判据不得引用后续里程碑交付物」纪律 | A 行不再含该判据；纪律串在位 |
| 3 | §4.1 规约 4 | 幂等：契约 `required: true` vs 代码域内生成 UUID / TTL 24h / 全局关开关 / `put()` 覆盖首次快照，判为实现违约 | 四类证据串在位 |
| 4 | §4.1.3 | 双轨 = 等价性证据；正确性 = 独立 oracle + 冻结 golden corpus + 对账样本（目标态） | 证据等级说明在位 |
| 5 | §3 / §11 | 补 R10 多副本调度单写者；声明 Outbox 的 `@Version` 抢占已做对、不得外推 | R10 行在位 |
| 6 | §11.1 | 新增 Q5 端到端可观测、Q6 灾备 / 依赖隔离 / 留存裁决；Q4 给 RPO/RTO 工程默认值 | 表格 Q1–Q6 连续；`semantic_verdict` 全绿 |
| 7 | §1.3 | 「不用规则引擎」限定为金额算术；决策表外置为二期选型 | 限定说明在位 |
| 8 | §9.1.1 / §13 O-05 / O-11 | 迁移编号统一指向 manifest；O-05 ② 闭环为已否决；O-11 触发改绑发版事件 | `0124_…_shadow.sql` 零命中；台账 ID 连续、非终态行含「触发」 |
| 9 | 诊断报告 §14.1 | 追加 4 类交人项（复算版本回查、幂等整改、调度单写者、`engine_version` + golden corpus），列审批级别 | 4 行在位 |

## 风险与回退

- **最大风险是门禁误报**：`_check_numbers.py` 对 §13 台账结构、`semantic_verdict` 语义串、`arch` 键的 REQUIRED/FORBIDDEN 都有硬判据。对策：不新增 O-xx 行（只改现有行，避免 ID 连续性风险）、不删任何必需语义串、不新增 `##` 级标题；改完全量跑 `check_all.py` + `--self-test`。
- **时态误判**：新写的事实必须标注 2026-11-10 复核日期与读取点文件行，不写成「一直如此」。
- **回退**：纯文本改动，`git diff` 可逆；单次提交只含两份实现文件与本 change 产物。

## 验证策略

`python3 doc/ai-product/synapClaim/tools/check_all.py`（19 步）全绿；`_check_numbers.py --self-test` 全绿；逐条 `grep -c` 回读新增标记数；`git diff --stat` 核对改动范围只含声明路径。
