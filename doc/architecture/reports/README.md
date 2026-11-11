# 架构报告（`doc/architecture/reports`）

人工收敛 / 审计类报告（**非** CI baseline）。

| 文档 | 说明 |
|------|------|
| [写端点授权收敛报告.md](./写端点授权收敛报告.md) | 写端点 `@PreAuthorize` 存量收敛记录 |

> **勿**把 `*-baseline.json` / `gate-state.json` / `freeze-ledger.yaml` 放进本目录——那些是 CI 写死路径，封箱在 `architecture/` 根下。
