# Comet SDK `complete-delivery` 在并行会话下无法 clean close — 阻塞记录

> 关联 change：`procuraai-ai-first-landing`
> 创建日期：2026-11-15
> 状态：**磁盘与远端交付 100% 完成**（3 笔连续单父提交已推送 `origin/dev`）；仅 SDK Run 未标记 `completed`。
> 性质：**并行会话下的交付范围校验边界**，不是业务缺陷、不是 SDK 崩溃（区别于 `synapclaim-architecture-convergence` 的写锁 bug）。

## 环境

- OS: macOS；工作流: Classic（`comet workflow resolve`）；Comet `@rpamis/comet` 0.5.2。
- 共享 `dev` 分支，归档期间有多个并行会话持续推送；本 change 的交付与并行提交按范围逐次核对零重叠。
- 归档目录：`openspec/changes/archive/2026-11-15-procuraai-ai-first-landing/`。

## 症状（两轮 `complete-delivery` 拒绝）

1. 第一轮：`Classic commit omits verified implementation: .comet/...`
   — 归档输入收据因 `.gitignore:219` 的 `.comet/` 规则被 `git add -A` 漏掉。
   已按 SDK「修复与归档可分成多个连续单父提交」用 `git add -f` 补 4 份输入收据（`11bf4cd31`）
   与归档清单收据（`02b55faf4`），不改写已推送历史。
2. 第二轮：`Classic 交付提交范围包含未批准的路径`
   — `delivery.baseCommit` 初始为 rebase 前的 `55afa05b6`，后刷新为 `16c2fec85`；
   并行会话提交 `285827275` 的 **149 个文件**被 rebase 夹进 `baseCommit..HEAD` 范围。
   `classic-sdk-application.js` 的 `allowed()` 只放行 `implementationFiles` / `archiveFiles` / `stateFiles`，
   **对并行会话提交无豁免**。
3. `comet state repair-delivery` 亦失败：`Classic verified project configuration changed; cannot reinterpret input ownership`
   — 并行会话改了 `.comet/config.yaml`，SDK 已验证的项目配置发生变化，拒绝重新界定输入归属。

## 根因定论

SDK 的交付校验以「**单次交付提交范围 = 本 change 已验证实现**」为不变量，
这在没有并行提交的串行工作流里成立；在本仓「共享 `dev` + 高频并行会话」模式下，
`fetch → rebase → push`（不 force 的唯一合规路径）必然把他人提交夹进 `baseCommit..HEAD`，
而 SDK 没有「排除非本 change 提交」的裁决通道，`repair-delivery` 又因配置变更拒绝重判。
因此这是**工作流模型与协作模式的边界冲突**，不是代码质量问题；不得伪造收据或声明 `outcome-unknown` 来绕过。

## 已采取动作（按业界最佳实践）

- **不改写历史、不 force、不重放 OpenSpec**；3 笔连续单父提交均 `-m` 精确暂存与 staged diff 审查后创建。
- **三方 SHA 一致**：`HEAD = origin/dev = FETCH_HEAD = 02b55faf4`，`ahead/behind = 0/0`，工作区干净。
- **逐文件验真**：对 9 个关键路径执行 `git cat-file -e origin/dev:<path>` 全 OK；
  active change 已消失；`openspec/specs/` 无 procurement 写入（`deltaCount=0`，3 份未实现 AI delta spec 以 `R100` 全文迁入 `deferred-specs/`）。
- **独立质量证据**：`mvn -o -f bone-platform/bone-masterdata/pom.xml clean test` 250/250 BUILD SUCCESS；
  `check-procura-ci.sh` 主门禁 10/10、注入自检 146/146、69/69 负向探针全转红；Verify r2 独立最终集成审查 PASS。
- **诚实标注**：Run 终态 `phase archive / status running / archived: true / verifyResult: pass / branchStatus: pending / :72 full.archive.deliver` 未完成。

## 恢复路径（留给后续 owner / 上游）

1. 等并行会话静默后，在**无新并行提交**的窗口重跑 Verify 与 `full.archive.preflight` 刷新 `delivery.baseCommit`，再执行 `complete-delivery`。
2. 或由 SDK 上游增加「并行会话提交排除」通道：在 `allowed()` 中按 `git show --name-only <sha>` 识别非本 change 提交并排除，同时放宽 `repair-delivery` 对 `.comet/config.yaml` 变更的拒绝条件。
3. **不建议**：手工编辑 `comet-state.yaml`、伪造 PASS 收据、或以 `mark-unknown` 声明 `not-executed`（原尝试确实执行过）。

## 当前交付状态

- ✅ 业务交付：Golden Record 去重内核 3 处代码修复 + procuraAI 文档族业界校准 + Verify r1/r2 报告 + 归档目录，全部已 PUSH。
- ✅ 机器可验证门禁：本地全绿，`CI 绿 ≠ AI 可上线` 仍成立。
- ⏸️ Comet Run `complete-delivery`：按上述路径恢复；**此项不阻塞代码与文档交付**。
