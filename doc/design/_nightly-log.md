# 夜间设计复核时间线（追加式，勿覆盖）

> 每行一个夜晚：`日期 | 模块 | 阶段(A/A'/B/B'/C/D) | 状态迁移 | 产物路径 | 下一步 | 阻塞`
> 状态真源：`doc/design/_review-status.yaml`；模块顺序真源：`doc/design/_global-contracts.yaml`。
> 人工卡点**默认放行**：只有 `doc/design/approvals/<module>.yaml` 写 `decision: hold/block` 才停（02 提示词 v3 §5）。**与星期几无关**。

| 日期 | 模块 | 阶段 | 状态迁移 | 产物路径 | 下一步 | 阻塞 |
|------|------|------|----------|----------|--------|------|
| 2026-09-26 | bone-iam（轮换 #1） | A（设计） + A'（自审） | `pending` → `design_ready` | 报告 `doc/design/modules/review-report-bone-iam.md`；v2 稿追加至 `doc/design/modules/6. IAM账号权限管理模块详细设计方案.md` 末尾 | 次日按 v3 §5 默认放行继续 B→B'→C→D | 自审 PASS；4 项 L3 待审批 |
| 2026-09-27 | bone-iam（轮换 #1） | **B → B' → C → D（六段走完）** | `design_ready` → `implemented` | 报告 §六/§七/§八/`§6.8`–`§6.9` 回填；新增 `SessionResp.java`、`utils/download.ts`、`pages/Profile.tsx` | 次日轮换 `bone-metadata-server`；本模块剩余项列 §九 待人工裁定 | 3 条已声明偏差（B-3 降级为单 JVM 锁、B-5 单类聚合、B-5 埋点未做）；新增 B-11 已修 / B-12 未抢改（并发会话占用 `MfaController`）/ B-13 建议；5 项 L3 待审批 |
