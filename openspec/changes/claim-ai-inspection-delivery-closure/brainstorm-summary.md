# Brainstorm Summary

> 本文件区分 `[已由代码/产物实证]` 与 `[候选待确认]`，记录收口方案推导。正式设计以 `design.md` 为准。

## 目标与约束

- **目标**：为已完成业务实现但 `complete-delivery` 无法闭合的旧 Run，取得一个由官方 Guard 全程验证的 `done` 终态。
- **硬约束**：不伪造收据、不声明 not-executed、不改写已推送历史、不绕过 Guard、不触碰旧 Run 与生产代码。

## 已由代码/产物实证的事实

- `[已由代码/产物实证]` SDK 交付校验逐提交取 `baseCommit..HEAD` 的 `diff-tree` 路径并集（`classic-sdk-application.js:340-348`），再用 `allowed()` 过滤（430-438）；源码注释明确禁止"先夹带再撤销"的绕过。
- `[已由代码/产物实证]` 旧 Run verify 收据的 `deliveryInputs` 仅 7 个 Java/SQL 文件（归档 `comet-state.yaml:1872-1879`），不含 `impact-map.json`。
- `[已由代码/产物实证]` `impact-map.json` 由 pre-commit hook 的 `impact.py --write-map` 在生成 `d4d153af2` 时写入了 procurement 端点映射，属别的并行会话的工作。
- `[已由代码/产物实证]` 后续补交 `78919ac2e`（补 `.comet` 证据）、`b2d0342fd`（恢复 impact-map）未能清除 `changed` 集合里的 impact-map 路径；当前 HEAD 的 impact-map.json 已与基线字节一致。
- `[已由代码/产物实证]` 四个官方恢复通道逐一不适用：`repair-delivery`（基线已追踪文件被拒，`classic-sdk-delivery-repair.js:82-101`）、`reopen-verification`（已归档被拒）、`repair-archive`（archive 已 succeeded 被拒）、改写历史（SDK 禁止）。
- `[已由代码/产物实证]` 仓库已有同类成功先例 `procuraai-archive-delivery-closure`（archived/done/PASS），其 design.md 记录了同样的新 Run 收口范式。
- `[已由代码/产物实证]` `collectClassicSdkImplementationInputs` 排除 `openspec/`、`.comet`、`docs/superpowers` 路径，归档证据文档不会成为 `implementationFiles`。

## 方案比较

| 方案 | 结论 | 理由 |
|---|---|---|
| A. 修补旧 Run（repair-delivery / reopen-verification / repair-archive） | 否决 | 四个通道已逐条实证不适用，见上 |
| B. 改写/rebase 已推送历史，剔除 impact-map 改动 | 否决 | SDK 禁止改写历史；会丢并行会话代码；force push 违反纪律 |
| C. 手写/回填 complete-delivery 收据或标 completed | 否决 | 伪造收据，违反红线 |
| D. 新建独立收口 change，在干净基线走完整 Guard 流程 | 采纳 | 唯一既满足 SDK 不变量、又不伪造/不改历史、且有成功先例的路径 |

## 候选待确认（已在正式设计中转为决策）

- `[候选待确认→design D1]` 新 change 命名 `claim-ai-inspection-delivery-closure`、isolation=current、基线=当前干净 HEAD `b2d0342fd`。→ 已在 proposal/design 固化。
- `[候选待确认→design D2]` 零 delta spec + `skip_specs: true`，避免把工作流约定误固化为系统需求。→ 已固化。
- `[候选待确认→design D3]` 交付提交精确 `git add`、`BONE_DATA_IMPACT=off` 防 hook 回写 impact-map。→ 已固化。
- `[候选待确认→design D4/D5]` 复用已验证的两条检查命令；standard 审查 + 独立只读最终集成审查。→ 已在 design 固化，Build 配置仍需在 Build 阶段单独确认。

## 开放问题

无阻断性开放问题。Build 阶段的 build_mode / tdd_mode / review_mode 由用户在进入 Build 时单独确认（每个 change 需独立确认，不沿用旧 change 选择）。
