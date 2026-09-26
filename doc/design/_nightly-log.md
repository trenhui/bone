# 夜间设计复核时间线（追加式，勿覆盖）

> 每行一个夜晚：`日期 | 模块 | 阶段(A/B) | 状态迁移 | 产物路径 | 下一步 | 阻塞`
> 状态真源：`doc/design/_review-status.yaml`；模块顺序真源：`doc/design/_global-contracts.yaml`。
> 人工卡点：`doc/design/approvals/<module>.yaml`。阶段 B 默认不在周六日执行。

| 日期 | 模块 | 阶段 | 状态迁移 | 产物路径 | 下一步 | 阻塞 |
|------|------|------|----------|----------|--------|------|
| 2026-09-26 | bone-iam（轮换 #1） | A（设计） | `pending` → `design_ready` | 报告 `doc/design/modules/review-report-bone-iam.md`；v2 稿追加至 `doc/design/modules/6. IAM账号权限管理模块详细设计方案.md` 末尾 | 人工批准后（B-1 导出契约基准 / B-3 并发方案 / S-7 前端范围三点确认）建 `doc/design/approvals/bone-iam.yaml` → 阶段 B | 人工卡点未过（周六不做 B）；4 项 L3 待架构师审批（乐观锁 DDL、删码、依赖收敛、ddl-baseline 裁决） |
