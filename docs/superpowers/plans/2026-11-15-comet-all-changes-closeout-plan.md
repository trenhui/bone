# Comet 全量变更收口总规划

> 编制：2026-11-15 20:21 · 状态：**待裁定后执行** · 适用：当前 `dev` 共享树
> 本文是跨 change 的总体计划，**不归属任何单一 change**，不作为任何 `submit-plan` 的计划文件；各 change 自己的 Build Plan 仍以各自 `docs/superpowers/plans/2026-11-15-<change>.md` 为准。
> 编制时未跑全量 `scripts/check.sh`（约 422 秒，且与并行会话争用共享树）。它是 Wave 0 的**阻断式实测项**，不是已通过项。

---

## 0. 「完成」的定义

本项目最容易出问题的地方不是代码，是**代码写完了、流程没关**。因此把 DoD 定成四层，缺一层不算完成：

| 层 | 内容 | 可验证物 |
|---|---|---|
| L1 实现 | 任务清单全勾选、符合分层与持久化约束 | `tasks.md` 勾选、编译/单测输出 |
| L2 证据 | 门禁、变异验证、E2E、负向探针 | 验证报告 + 命令退出码 |
| L3 流程收口 | Comet Run 终态诚实、`archived=true`、归档目录与收据齐 | `comet state check ... archive --details` |
| L4 交付一致 | 本地 `HEAD` / `origin/dev` / Gitee / GitHub 四方 SHA 一致、台账登记 | `git ls-remote` 双远端 + `git status -sb` |

两条不可让渡的红线：**不伪造收据**，**不改写已推送历史**。远端 `comet-sdk-blocking-bug.md` 已把这一点写死，本规划沿用。

---

## 1. 现状基线（本轮实测）

| 对象 | 事实 | 取证 |
|---|---|---|
| 双远端 dev | **不一致**：Gitee `46e59ea7c`，GitHub `285827275` | `git ls-remote <各自 URL> refs/heads/dev` |
| 本地 dev | `285827275`，behind 6 / ahead 0，工作区干净 | `git status -sb` |
| 主线活跃 change | **2 个**：`multi-insurer-biz-identity-catalog-mapping`、`synapclaim-risk-fraud-dimensions` | `git ls-tree --name-only origin/dev openspec/changes/` |
| 本地 `comet status` | 报 3 个，多出的 `procuraai-ai-first-landing` 是落后快照造成的**假活跃** | `comet status . --json`（scope=current-worktree） |
| procuraai | 远端已归档 `archive/2026-11-15-...`，含输入/清单收据与阻塞登记；业务交付 100% 完成，仅 SDK Run `full.archive.deliver` 未标 completed | `git show 46e59ea7c` |
| synapclaim-risk-fraud | 15/15 任务完成，实现提交 `320573ffa` 已在 HEAD 祖先链；卡在 `full.open.revalidate` 的 claimed Action | `--recover --details --json`：`outcome-unknown`、`record.status=missing`、`receiptRef/manifestRef=null` |
| multi-insurer | **0/15**；无 `comet-state.yaml`（unmanaged OpenSpec） | `comet status` 报 `phase=null` |
| multi-insurer P0 内核 | **已入库**（上一轮因路径少一层目录误判为 0）：`synap-claim-app/src/main/java/com/bone/claim/domain/model/mapping/` 七实体 + `V20__/V21__` 迁移 + `scripts/migration/0120_...` | 全仓二次检索 |
| 四批次分支 | 双远端 local==remote，均未合并进 `origin/dev`；各领先 4 个提交 | `git ls-remote` + `merge-base --is-ancestor` |
| 四批次 Build 就绪度 | 批 1 已配 `executing-plans / tdd / thorough`；批 2/3/4 `build_mode/tdd_mode=null`、`review_mode=off`；**四个都 `plan: null`（未 submit-plan）** | 各分支 `comet-state.yaml` |
| selection | `.comet/current-change.json` 不存在 | `comet state current` 报 no current change |
| 用户已裁定闸门 | 「等并行会话在途改动入库、全量门禁转绿后才进 Build 写代码」 | `.comet/batches/synapclaim-workbench-4-batches.json` |

四批次依赖关系（台账）：批 1 无依赖；批 2、批 3 依赖批 1；批 4 依赖批 1 + 批 3。

---

## 2. 方法论与排序依据

### 2.1 先找瓶颈，再排任务（约束理论）

真正的瓶颈**不是 62 项任务，是共享 `dev` 上的串行交付校验窗口**。`complete-delivery` 逐提交校验 `baseCommit..HEAD` 必须全为本 change 已验证实现，任何并行提交被夹进范围即拒；`repair-delivery` 又因 `.comet/config.yaml` 变更拒绝重新界定输入归属。procuraai 已经用一次失败证明了这一点。

由此得到本规划唯一的架构级决策：

> **归档动作 WIP=1，且每个归档窗口内冻结共享树：不改 `.comet/config.yaml`、不允许其他会话提交非本 change 文件、一次只处理一个 change。**

并行只发生在**不触碰主线的实现阶段**（各自 worktree），收口阶段一律串行。这是把「并行会话」从事故源变成可控变量。

### 2.2 排序（WSJF，相对值，不估人天）

| 波次 | 用户价值 | 规模 | 风险削减 | 使能性 | 结论 |
|---|---|---|---|---|---|
| W0 基线对齐 | 中 | 小 | **最高** | **最高** | 第 1 位：不做则后面全部不可信 |
| W1 死锁清障 | 中 | 最小 | 高 | 高 | 第 2 位：先拿到一个干净收口模板 |
| W2 multi-insurer | 高 | 中 | 中 | 高 | 第 3 位：**Pilot**，跑通全链路再扩量 |
| W3 四批次 | 高 | **最大** | 中 | 低 | 第 4 位：按依赖链 1→2→3→4 |
| W4 串行归档 | 高 | 中 | 高 | — | 每个实现波次后紧跟 |
| W5 backlog 移交 | 低 | 小 | 中 | 中 | 只冻结 DoR，不编码 |

W2 放在 W3 前面，是刻意的：先用 15 项、P0 内核已就位的 change 跑通「实现 → 验证 → 归档」完整闭环，把模板和踩坑固化下来，再扩量到 47 项的四批次。小范围验证通过后再放量，比四线齐开更符合本仓的并行风险画像。

### 2.3 其余四条落到动作的准则

- **DoR / DoD 双闸门**：每个波次进入前逐条核 DoR，未过不进；退出时按本文给的命令与判据核 DoD，红了就停线（stop-the-line），不带着红门禁进下一个波次。
- **垂直切片**：multi-insurer 按「后端接线 → 导入 → 覆盖配置 → 详情 → 前端 → 复核」切成端到端薄片，每片自带单测与门禁，不按模块横向铺开。
- **证据链 / provenance**：每个收口动作保留 proposalHash、提交 SHA、验证报告、归档收据四方对应关系；`git commit --only -- <paths>` 圈定路径，中文路径提交前用 `git status --short -- <精确路径>` 复核。
- **Pre-mortem**：第 5 节的风险表按「触发信号 → 对策」写，不是概率打分；触发即执行对策，不临场判断。

---

## 3. 波次计划

### Wave 0 — 基线对齐与冻结窗口

**目标**：让「完成」有一个可验证的单一事实来源。

**DoR**：确认没有正在写入的并行会话（工作区、四个 worktree 均 `git status --short` 为空）。

**步骤**（均为写远端/改本地分支的操作，**须用户确认后执行**）：

```bash
# 0.1 取证（只读）
git status -sb
git ls-remote git@gitee.com:meishan315/bone.git  refs/heads/dev
git ls-remote git@github.com:trenhui/bone.git   refs/heads/dev

# 0.2 补齐 GitHub 漏推的 6 个提交：显式 SHA 快进，不 force、不改写历史
git push git@github.com:trenhui/bone.git 46e59ea7c:refs/heads/dev

# 0.3 本地快进到主线
git fetch origin && git merge --ff-only origin/dev

# 0.4 四个 worktree 分支同步到新 dev（在各自 worktree 内，不 force）
git -C .worktrees/<name> fetch origin
git -C .worktrees/<name> rebase origin/dev
```

**冻结约定**（贯穿 W1~W4）：`.comet/config.yaml` 保持不动；归档窗口内其他会话静默；每个窗口只处理一个 change。

**DoD**：
- 双远端 `refs/heads/dev` 均为 `46e59ea7c`；
- 本地 `HEAD == origin/dev == 46e59ea7c`，`ahead/behind = 0/0`；
- `comet status . --json` 报 2 个活跃 change（procuraai 假活跃消失）；
- 四个 worktree 分支与 `origin/dev` 无分叉扩大。

**证据**：把三条 `ls-remote` 输出与 `git status -sb` 贴进当日工作日志。

---

### Wave 1 — 流程死锁清障

**目标**：把两个「代码已完成但 Run 卡住」的 change 收口，并沉淀可复用的收口模板。

**DoR**：W0 全绿；`comet state select <change>` 明确单一所有权（当前无 selection，多候选会让 Guard 失败关闭）。

**1-A `synapclaim-risk-fraud-dimensions`**

```bash
comet state check synapclaim-risk-fraud-dimensions open --recover --details --json
```

按 `recovery.record.status` 三分支处置：

| 取值 | 动作 |
|---|---|
| `completed` | 取 `recordOutcomeRequest` 回填，再取新 `evidenceWait` 模板提交 `record-evidence`；**两步必须在同一次连续执行里完成**，分开会因共享树输入快照变化触发 `EVIDENCE_REJECTED` |
| `started` / `missing` | 先取证原执行器是否仍在运行（claim token、`.comet/runtime/classic-sdk-checks/7ffbdb.../` 内容、进程）；**只有实证原尝试完全未执行**，才 `mark-unknown` → `retry(not-executed)` → `comet check run <change> open --json -- <program>` 真实重跑，`evidence.summary` 如实写来源 |
| 证据不足 | **停手**。按项目既有结论以「15/15 任务 + 提交 `320573ffa` + 1207 tests/59-59 自检/变异验证记录」为终态，如实登记 Run 未 clean close，不声明 not-executed |

当前实测是 `missing` 且无收据，走第二条或第三条。**禁止**用 `--recover --confirmed` 覆盖既有事实，也**禁止**先 `guard ... --json` 预览（会留下 claimed Action，随后 `--apply` 必报 running Action）。

**1-B `procuraai-ai-first-landing`**：不重跑归档。二选一，由用户裁定：
- 保持终态：归档目录 + 3 笔提交为最终事实，阻塞登记继续有效，不阻塞交付；
- 尝试 clean close：在 WIP=1 静默窗口重跑 Verify 与 `full.archive.preflight` 刷新 `delivery.baseCommit`，再 `complete-delivery`；若仍因 `.comet/config.yaml` 变更被 `repair-delivery` 拒绝，回到保持终态，不硬闯。

**DoD**：两个 change 的 Run 均为 `completed`，或均有写明原因、证据与不阻塞交付结论的终态登记；`comet state check <c> archive --details` 的红项只剩 phase/verify 且可解释。

---

### Wave 2 — `multi-insurer-biz-identity-catalog-mapping`（Pilot，15 项）

**目标**：跑通一条完整闭环，作为后续四批次的模板。

**DoR**：
- 纳管方式先定（见第 6 节决策点 2）：它是 unmanaged OpenSpec（`.openspec.yaml`，无 `comet-state.yaml`），先 `comet classic validate <name>` 验 schema 再定 init/compat 路径，不要直接 `init` 覆盖已有产物；
- `comet state select <change>`；
- 复核 P0 内核七条不变量（fail-closed 无空串回落、未命中一码一行、生效唯一、回放链、身份型表无物理删除、发布前标准码验存在、软删偏离的 SDK 兼容）——内核已入库，这一步是**核对而非新建**；
- `t_claim_coverage_override` DDL 属 L3，架构师审批后由人执行，AI 只出草案。

**切片顺序**（每片自带单测 + 门禁）：

| 片 | 任务 | 关键点 |
|---|---|---|
| A 后端接线 | 1.1 / 1.2 / 2.1 / 2.3 | 主体引用收紧：**写入期拒绝 + 读取期兜底保留**，别把 `<=0` 改成 `==0` 那类静默放宽 |
| B 批量导入 | 2.2 | 全量逐行校验、任一失败整批拒绝、≤2000、内容指纹幂等；四分支单测齐全 |
| C 两段式覆盖 | 3.1 / 3.2 | 新增仓储方法同批交 SQL 模板；模板首行必须是语句关键字；生成列 + 唯一键；`@Version` |
| D 详情视图 | 4.1 | 只读聚合，复用 `QuotaAccountRepository` |
| E 前端管理面 | 5.1~5.5 | 子应用脱离 shell 不渲染，验证走 shell `:3000`；ID 用 string |
| F 验收复核 | 6.1 / 6.2 | 七条不变量输出复核清单；全量门禁 |

**DoD**：
- 15/15 勾选；
- `mvn -o -pl bone-apps/synap-claim/synap-claim-app test` 全绿（注意模块路径带 `synap-claim-app` 一层）；
- `./scripts/check.sh` **exit 0 且末行「✅ 本地自检通过！」**（中间 ❌ 是负向探针，真实红点只看步骤级）；
- 前端 `npm run build` 通过；
- **变异验证**：至少对「主体引用收紧」「整批导入原子性」各做一次阈值式 + 互补式变异，0 红先排除「没跑」，恢复后 `git diff --exit-code` 逐字一致；
- `EntityTableDdlCoverageTest` 通过；若新增错误码，`ClaimErrorCodes` + `DEFAULT_HTTP_STATUS` + 前端两语言包三处齐；
- 归档前把 `.comet/check-policy.json` 补成 v2 且 argv 精确匹配（当前只有 case360 一条，否则 archive 撞 "check inputs changed"）。

---

### Wave 3 — 四批次 Build（47 项）

**DoR（用户已裁定，不可绕过）**：并行会话在途改动全部入库、`./scripts/check.sh` 全量转绿。另需：
- 各分支在各自 worktree 内 `comet state select <change>`，**不要在主树实现子任务**；
- 四个都补 `submit-plan`（当前 `plan: null`）；
- 批 2/3/4 补 `propose-build`（当前 `build_mode/tdd_mode=null`）；配置是否沿用批 1 的 `tdd/thorough`，见决策点 3；
- 把门禁执行器从 `/tmp` 迁到 `scripts/` 并在 `check.sh` **调用行**接入且带 `--check`——只放脚本不接调用行等于恒绿假门禁，这是台账里已登记的已知限制。

**顺序**：批 1 → 批 2 → 批 3 → 批 4（依赖链决定，不并行收口）。每个批次**在各自分支上完成实现与验证后，才轮到下一个**；分支保持短生命周期，每日同步 `origin/dev`，避免最后一次性大爆炸合并。

**各批 DoD（台账 acceptance 逐条兑现）**：
- 批 1：命中率 ≥0.90、回落 100% 可自领、1 万在办 P95 <300ms、137 条 `total==137` 且逐页去重 ==137、`EXPLAIN` 命中三个索引、并发双领取仅 1 成功；
- 批 2：放开阈值的写请求被拒、关闭写 API 后治理页回只读、采纳回流带 traceId 与 AI 参与四态；
- 批 3：同人不可自审、`RE_REVIEW` 关闭后 43/46 零流量、错误码三处齐；
- 批 4：打回留痕且已支付先冲正、代理生效区间正确、超时回池可观测。

四批共同：tasks 全勾选、`mvn test` + `check.sh` 全绿、变异验证有判别力、分支双远端一致、提交链单父连续。

---

### Wave 4 — 逐 change 串行归档（WIP=1）

在每个实现波次之后紧接执行，一次一个：

```bash
# 预检交付链：每个提交恰好 1 个父，且其父等于上一个 SHA
git rev-list --reverse --parents <baseCommit>..HEAD

# Verify 报告必须写在 reports/，写进 change 目录会被 Phase Guard 判为实现写入拦下
# docs/superpowers/reports/<date>-<change>-verify.md

# 归档若选 push/pr，propose-archive 必须带 --remote origin
# guard 直接 --apply，不先 --json 预览
```

**每次窗口**：其他会话静默 → 核对 `.comet/check-policy.json` v2 → 跑 Verify → `propose-archive` → `decide-archive` → `guard ... --apply` → `git add -f` 补 `.comet/` 收据（`.gitignore:219` 会让 `git add -A` 漏掉）→ 核对归档目录与双远端 SHA。

**DoD**：归档目录存在、Run 终态诚实、双远端 + 本地 + tracking 四方 SHA 一致、`comet status . --json` 的活跃 change 数递减到 0（或仅剩明确标注的终态项）。

---

### Wave 5 — deferred backlog 与未裁定项移交

`deferred-backlog.md` 的 23 项（DoR R-1~R-6、bone-ai spike、AI 合同审查 walking skeleton、决策治理、存量收敛、负向探针、集成验收）**本轮不编码**：原 `tasks.md` 纪律写明 R-1~R-6 未全 ✓ 前不得进编码，且多属 owner/L3 程序级工作。

只做三件事：把 R-1~R-6 冻结成可勾选的 DoR 清单、按组另立独立 change、登记 owner 与排期入口。不翻转任何 AI 原生成熟度外部前置判据。

---

### Wave 6 — 全局验收与台账收口

- `comet status . --json`：活跃 change 清空；
- `comet doctor`（Trae project hook 告警是上游缺陷，幂等重跑不自愈，不必刷绿）；
- `.comet/batches/synapclaim-workbench-4-batches.json` 状态更新为 closed 并补归档收据；
- 决策登记、当日工作日志、项目知识各写一条收口经验；
- 最后一次四方 SHA 取证。

---

## 4. 验证命令矩阵

| 场景 | 命令 | 判据 | 已知陷阱 |
|---|---|---|---|
| 本地门禁 | `./scripts/check.sh` | `exit 0` + 末行「✅ 本地自检通过！」 | 中间 ❌ 是负向探针；报 report-only 的门禁必须带 `--check` |
| 模块测试 | `mvn -o -pl bone-apps/synap-claim/synap-claim-app test` | 0 failures / 0 errors | 路径少 `synap-claim-app` 会静默空结果 |
| 归档前置 | `comet state check <c> archive --details` | 无 `[FAIL]` | `guard --json` 预览会留下 claimed Action |
| 恢复取证 | `comet state check <c> <phase> --recover --details --json` | 看 `recovery.record.status` | 进程退出 ≠ 检查通过；不得编造成功收据 |
| 交付链 | `git rev-list --reverse --parents <base>..HEAD` | 每个提交单父且父链连续 | merge 提交夹入即恒定 RC=70 |
| 双远端 | `git ls-remote <Gitee URL> refs/heads/dev` + `git ls-remote <GitHub URL> refs/heads/dev` | 两者 SHA 相同 | 只信 `origin/*` tracking 会漏掉 GitHub 未推 |
| 门禁判别力 | 双向变异 + 恢复后 `git diff --exit-code` | 恰有预期红点、恢复逐字一致 | 0 红先排除「没跑」；不得依赖 `/tmp` 残留 |
| 前端 | shell `:3000` 下 `npm run build` | 构建通过且页面渲染 | 子应用脱离 shell 不渲染 |

---

## 5. 风险登记（触发信号 → 对策）

| 风险 | 触发信号 | 对策 |
|---|---|---|
| 双远端再次分叉 | `ls-remote` 两值不等 | 停线，先补齐再继续；不 force |
| 并行提交夹入交付范围 | `complete-delivery` 报「未批准的路径」 | 回到 WIP=1 静默窗口重跑 preflight 刷新 `baseCommit`；不改历史 |
| `repair-delivery` 拒绝重判 | 报 `project configuration changed` | 冻结 `.comet/config.yaml`；仍拒则保持诚实终态 |
| Run 写锁超时 | `Timed out waiting for file lock: .../.write.lock` | 串行重试，不并发跑 comet 写命令 |
| 收据漏提交 | 报 `commit omits verified implementation` | `git add -f` 补 `.comet/` 收据，用连续单父提交 |
| 门禁假绿 | 脚本存在但 `check.sh` 无调用行 / 缺 `--check` | 查调用行，变异验证判别力 |
| SQL 模板启动期炸 | `TemplateNotFoundException` | 新增仓储方法同批交 SQL；首行必须是语句关键字、不放注释 |
| 数据静默丢失 | 实体 `@Column` 与活库 `information_schema` 求差有缺列 | 上线前做全模块求差体检 |
| shared index 冲突 | `git commit` RC=128 / `index.lock` | 轮询重试；提交一律 `commit --only -- <paths>` |
| 中文路径漏扫 | `grep 中文` 静默空结果 | 用 `git status --short -- <精确路径>` 复核 |
| 归档锁写进历史 | 死 pid 的 `.openspec-archive.lock` 被 `git add -A` | 已由 `.gitignore:360` 忽略，提交前确认未跟踪 |
| 扩量翻车 | W2 未跑通就开 W3 | W2 DoD 全绿是 W3 的硬闸门 |

---

## 6. 需用户 / owner 裁定的决策点（Agent 不自行决定）

1. **W0 是否授权写远端**：把 GitHub `dev` 快进补齐，以及本地 `merge --ff-only`。
2. **multi-insurer 的纳管方式**：unmanaged OpenSpec 是走 `state init` 还是 compat 路径 `state select`；不同选择影响后续 Guard 行为。
3. **批 2/3/4 的 Build 配置**：是否沿用批 1 的 `executing-plans / tdd / thorough`，还是按各自规模降级；变更配置要走 `propose-build` 重新提案，不能用 `--confirmed` 绕过被拒提案。
4. **procuraai 是否投入 clean close**：保持诚实终态，还是等静默窗口重跑 Verify + preflight。
5. **synapclaim recovery 证据不足时的终态**：是否接受「实现已交付、Run 未 clean close」并登记，而不是继续追收据。
6. **23 项 deferred backlog 是否另立 change**：建议本轮只冻结 DoR、不编码。
7. **L3 事项**：`t_claim_coverage_override` 等 DDL、依赖与 CI 脚本改动需架构师审批；AI 不执行生产库迁移、发布打 tag。

---

## 7. 明确不做

- 不改写已推送历史、不 `push --force`、不重放已归档的 OpenSpec；
- 不手工编辑 `comet-state.yaml`、不伪造 PASS 收据、不以 `mark-unknown` 声明「未执行」；
- 不在 Verify/Archive 阶段写实现文件；不在主树实现 worktree 子任务；
- 不在 R-1~R-6 未冻结前编码 AI 合同审查等程序级工作；不声称 EU AI Act 高风险合规、不承诺单一「AI 准确率」；
- 不因「全绿」就跳过变异验证——判别力只能由变异证明。

---

## 8. 一页执行清单

```
[ ] W0  双远端 SHA 一致 + 本地 ff-only + 四分支同步 + 冻结 .comet/config.yaml
[ ] W0  实测 ./scripts/check.sh：exit 0 + 末行「✅ 本地自检通过！」
[ ] W1  select → synapclaim recovery 三分支处置 → 终态诚实登记
[ ] W1  procuraai：按裁定保持终态或 clean close
[ ] W2  select → 纳管 → 七不变量复核 → 切片 A~F → 15/15 + 变异验证 → 归档
[ ] W3  批1 → 批2 → 批3 → 批4：submit-plan / propose-build / 实现 / acceptance 全绿
[ ] W4  逐 change 串行归档：check-policy v2、Verify 报告路径、--remote、收据、四方 SHA
[ ] W5  23 项 backlog：冻结 DoR、另立 change、owner 排期（不编码）
[ ] W6  comet status 清空 + 台账/决策/工作日志收口 + 最后一次四方 SHA
```

每个 `[ ]` 打勾前，回填对应波次的 DoD 命令输出；没有输出的不打勾。
