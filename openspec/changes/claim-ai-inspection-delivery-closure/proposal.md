## Why

V1.0「AI 后督质检 100% 全量覆盖」的业务交付（实现、1674 例测试、独立审查 PASS、4 条变异双向钉子、OpenSpec 归档目录与主 spec 同步）已全部提交并推送 `origin` 双远程，但对应 Comet Classic Run `claim-ai-inspection-full-coverage` 的 `complete-delivery` 收据无法干净闭合：SDK 以「`archive.preflight.baseCommit..HEAD` 只含本 change 已验证实现」为不变量，而 pre-commit hook 的 `impact.py --write-map` 在生成归档提交 `d4d153af2` 时，把并行会话新增的 procurement `SourcingController./rfx/{rfxNo}/award` 端点映射误带入了 `scripts/e2e/impact-map.json`。

实证取证：
- `allowed = implementationFiles ∪ archiveFiles ∪ stateFiles`（`classic-sdk-application.js:430-438`）；verify 收据的 `deliveryInputs` 仅含 7 个 Java/SQL 实现文件（归档目录 `comet-state.yaml:1872-1879`），不含 impact-map.json。
- `changed` 由 `baseCommit..HEAD` **逐提交** `diff-tree` 取路径并集（同文件 340-348 行，注释明确「避免先夹带未批准文件、再撤销的提交躲过最终 tree 对比」），因此后续提交把 impact-map.json 恢复为基线内容也无法清除该路径。
- 归档后官方恢复通道逐一实证不适用：`repair-delivery` 因 impact-map.json 在基线已追踪被拒（`cannot omit a file tracked by the approved baseline`）；`reopen-verification` 要求未归档且活动目录存在；`repair-archive` 要求 `archive.execute=failed`；改写或 rebase 已推送历史被 SDK 禁止且会丢并行会话代码。

因此需要在干净基线上补一次可机器验证的交付闭环，并把旧 Run 的偏差作为可审计事实保留，而不是伪造收据、空转口径或改写已推送历史。

## What Changes

- 新增一个独立的 Classic full change `claim-ai-inspection-delivery-closure`（`isolation: current`），以当前干净 HEAD `b2d0342fd`（其 impact-map.json 已与基线一致）为 `baseRef`，完整走 Open → Design → Build → Verify → Archive → Delivery。
- 新增可审计的交付闭环证据：范围声明、技术设计、任务清单、实施计划、验证报告与 `walkthrough.html`，随本 change 一并归档。
- 本 change **不创建任何 `specs/` 文件**（零 delta spec），因此归档时 `findSpecUpdates` 返回空、不会向 `openspec/specs/` 写入任何需求。`.openspec.yaml` 的 `skip_specs: true` 作用是让 `specs` 产物合法 `skipped`，而不是跳过主 spec 更新的直接原因。
- 复用已验证的两条真实检查命令（AI 后督模块全量测试 + SynapClaim 文档族门禁），不新增脚本、不改 `.comet/config.yaml`。
- 以连续单父提交把本 change 的实现与归档产物提交并推送 `origin` 双远程，使 `complete-delivery` 的整段范围只包含本 change 的已批准路径，Run 达到 `done`。
- 保留旧 Run `claim-ai-inspection-full-coverage` 的归档与偏差为可审计事实，不回填、不重放、不标 completed。

## Capabilities

### New Capabilities

无。本 change 零行为规格变更，不创建任何 spec capability。

### Modified Capabilities

无。不修改 `openspec/specs/` 下任何能力规格。

## Impact

- **工作流 / 交付证据**：新增本 change 的 OpenSpec 归档目录、plan、Verify 报告、walkthrough 与 `.comet/` 归档收据。
- **代码**：不修改任何生产代码；不新增 DDL；不触碰资金路径。
- **Git**：在 `comet/claim-ai-inspection-full-coverage` 分支追加连续单父提交并推送双远程；不改写既有历史。
- **兼容性**：无 API、无数据模型、无前端变更。

## Non-Goals

- 不为旧 Run 补写或回填任何 outcome，不把旧 Run 标成 completed。
- 不伪造收据、不声明 not-executed、不绕过 Guard。
- 不新增 delta spec、不合并任何需求到 `openspec/specs/`。
- 不改写已推送历史、不 force push、不 rebase 并行会话提交。
- 不引入新的检查脚本或改 `.comet/config.yaml`。

## Acceptance Scenarios

- **核心成功路径**：本 change 走完 Open → Design → Build → Verify → Archive，Guard 全程 PASS；精确暂存的交付提交推送双远程后，`comet state complete-delivery <change> --commit <sha>` 成功，`comet state next --json` 显示 `continuation: done`。
- **范围洁净场景**：`baseCommit(b2d0342fd)..HEAD` 内每个提交的路径都可被 `allowed()` 解释，尤其不含 `scripts/e2e/impact-map.json` 及任何本 change 外的已跟踪文件。
- **防夹带场景**：归档提交逐路径精确 `git add`，不使用 `git add -A`；其他 change 的在途未跟踪文件不进入 staged 与归档提交。
- **测试不退化场景**：AI 后督模块全量测试真实跑绿（`1674/0/8`、`BUILD SUCCESS`），证实交付闭环登记未破坏 V1.0 实现；SynapClaim 文档族门禁 `check_all.py` 实测 18/21 PASS，3 项失败均为与本 change 无关的预存红项（2 份历史未登记文档、A9 过时声明、`_ai_native_readiness --self-test`），按 accepted deviation 登记，判据为「无新增红项」。
- **偏差保留场景**：旧 Run 的归档目录与偏差事实仍可审计，未被修改或回填。
