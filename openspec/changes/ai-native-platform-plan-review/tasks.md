# 任务清单

- [x] 1. 复核 v2 的 9 项改造（T1–T9）与不改造清单，形成「合理 / 可优化 / 不采纳」判定表并写入方案新增章节
- [x] 2. 补「上下文质量可复算验收」：T2 / T6 引用 `scripts/check-eval-baseline.py` 的 skeleton 骨架，明确不编造实测指标
- [x] 3. 补「运行时证据字段」：T4 影响面报告增加运行时 Trace / 事件订阅占位与确认口径，引用 `Bone-可观测性规范.md` §3 / §4 / §5
- [x] 4. 补「AI 产出归因最小数据源」：T6 给出 PR 模板可选勾选项建议，标注需 L3 审批才落地
- [x] 5. 拆分 T7：保留 `06 §12.2` 路由表增列「必查约束」的文档内动作，明确不生成指令文件、不改变「不入库」裁定
- [x] 6. 汇总各 T 验收为一张复用现有只读门禁脚本的命令清单（不新增 `check-*.py`、不接 CI）
- [x] 7. 同步附录 v3 修订记录与 `doc/architecture/README.md` 登记说明
- [x] 8. 实跑 `python3 scripts/check-doc-code-symbols.py` 与 `python3 scripts/check-ddd-doc-drift.py` 记录真实 RC，并复核方案内数字与引用无漂移
