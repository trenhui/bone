## 变更摘要

<!-- 一句话说明改了什么、为什么。关联 Issue / ADR / OpenSpec change 编号。 -->

- 关联：
- 影响模块 / 限界上下文：

## 提交前自检（本地已跑）

- [ ] `./scripts/check.sh` 通过
- [ ] `python3 scripts/check-ddd-doc-drift.py` 通过
- [ ] `python3 scripts/ci/check-ddd-doc-code-sync.py --strict` 通过
- [ ] `python3 scripts/check-ddd-gate-state.py` 通过

## DDD 交付验收（`Bone-DDD-最终实践方案.md` G-4）

- [ ] **战略边界**：上下文 / Owner / 数据所有权清晰；无直接引用其他上下文的 domain/PO；API 与事件变更带版本与兼容策略
- [ ] **战术模型**：状态迁移经聚合行为完成；值对象构造即有效且不可变；领域服务无 IO/框架/事务；Repository 只服务聚合写模型
- [ ] **应用与可靠性**：一个用例只有一个入口边界（无同义层套娃）；写事务在最外层写用例；多聚合事务例外有理由/失败语义/测试；并发、幂等、重试、事件丢失与乱序策略明确
- [ ] **验证**：纯领域测试覆盖成功与拒绝路径；Repository/QueryAdapter/ACL 有集成或契约测试；ArchUnit 与受影响测试实际执行

## 生成器与规范一致性

- [ ] 本次新增代码**不是**照抄 `bone-engine/studio-generator` 的空目录骨架（生成器默认形态尚未对齐 Application Service First，见规范 G-1.4）
- [ ] 若本 PR 改动了 `bone-engine/studio-generator` 模板，已同步核对规范 E-3.7 / E-10.1 的构件与转换边界
- [ ] 未新预创建空的 `command/` `query/` `handler/` 目录（E-3.11）

## 门禁状态纪律（仅改门禁时勾）

- [ ] 本 PR **没有**改动 `Bone-DDD-最终实践方案.md` 的 HC 表 / G-1.x 门禁状态
- [ ] 或：本 PR 改了门禁状态，且**同一个 PR 内**已把 `AGENTS.md` §12.1 / §12.7 的重复副本收敛为指向 `#hc-hard-constraints` 的薄引用（否则两处必然漂移）
- [ ] 状态只按"实现层是否真有可复现载体"填写；Planned / Manual **不得**写成 Active 或「CI 阻断」（CORE-08）
- [ ] 新增或更新规则状态时，同 PR 提交对应 `archunit_store/` 基线（基线只可收缩，禁止扩张）
- [ ] 若改动了模块源码或 `doc/_generated/` 相关文档，已重跑对应 `tools/*-compliance-collector/collect.py`

## 风险与回滚

<!-- 破坏性变更（DDL / 契约 / 事件 schema / 规则语义）必须写明：影响面、灰度或双发方案、回滚方式。 -->
