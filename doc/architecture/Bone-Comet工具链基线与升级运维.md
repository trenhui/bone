---
doc_id: tooling.comet-baseline
role: runbook
currency: current
owner: 工具链维护
last_verified: 2026-11-14
---

# Bone · Comet 工具链基线与升级运维（0.5.2）

> 本文只记录**已实测**的安装基线、升级 SOP、可复现校验与已知边界。
> 定位裁定：Comet 是**流程编排 + 技能质量评估中枢**，不是 DDD 架构门禁。
> 架构正确性由 `scripts/check.sh`、ArchUnit 等**确定性静态门禁**负责，两者不得互相替代。

---

## 一、当前基线（2026-11-14 实测；0.5.2）

| 项 | 值 | 验证方式 |
|---|---|---|
| CLI 版本 | `0.5.2`（由 `0.4.0-beta.18` → `0.4.5` → `0.5.1` → `0.5.2`） | `comet --version` 与包目录 `package.json` 双证 |
| npm 安装前缀 | `/opt/homebrew`（包在 `lib/node_modules/@rpamis/comet`） | `npm install -g --prefix /opt/homebrew @rpamis/comet@0.5.2` |
| 官方 tarball 指纹 | `sha256:2003eb1797fcb693ec1c3160fcd97c86b66c0530e9f73ff78804e9d9b3dd1339`（npm `dist.shasum` = `11866c3e3a2e78a3792cd15b3afe038b9a326a6a`） | `npm pack @rpamis/comet@0.5.2` + `shasum -a 256` |
| 全包文件数 | **3656**（`package/` 下普通文件、排除 node_modules） | 官方 tarball 全量逐字节比对；实测本地 `3656/3656`，`differ=0` / `missing=0` |
| 功能资产 | `assets/` 155 文件（26 个 SKILL.md）、`eval/` 568 文件 | `verify_comet_install.py` C4 |
| 项目级默认 workflow | `classic` / `zh-CN` / `artifact_layout: legacy` | `.comet/config.yaml`（**已入库**）；C7 判据守住 |
| 项目级平台集成 | 5 平台（Claude Code / GitHub Copilot / CodeBuddy / Trae / Trae CN）各 65 skills、rules、hooks | 2026-11-07 `comet update --scope project --json` 实测；**本轮未重跑，不冒充新证据** |
| doctor | `healthy=true`，43 pass / 0 fail / 21 warn | `comet doctor --json`；其中 1 个上游缺陷（D1）、1 个工作区运行态（D7），其余为 global / 可选能力告警 |

本轮只做**基线同步与判据修正**：CLI 早已是 0.5.2，没有重装、没有执行 `comet update`、
没有改写任何 hook 配置。§四 升级 SOP 与其中的回滚点路径仍是 2026-11-07 历史快照，
保留原样；0.5.1 时期的指纹与文件数只作为历史，不再作为 C1/C3 的判据输入。

**配置入库的唯一例外**：`.gitignore` 官方托管块用 `!/.comet/config.yaml` 显式放行该文件，
它是团队共享的 workflow 配置，必须入库；其余 `.comet/*`（selection、state、run-state、
runtime）都是本机状态，一律忽略。曾出现「配置只在本机、团队 clone 后退回 legacy fallback」
的缺口，现由校验脚本 C7 守住。

---

## 二、可复现完整性校验（唯一推荐入口）

```bash
python3 scripts/ops/verify_comet_install.py                 # 严格模式(默认)：8 项判据，缺 tarball 即 UNVERIFIED
python3 scripts/ops/verify_comet_install.py --fetch-tarball # 无本地包时自动 npm pack 复现官方包并校验
python3 scripts/ops/verify_comet_install.py --self-test     # 16 条探针（含自适应白名单/严格模式/重复 Guard 变异），证明判据非恒绿
python3 scripts/ops/verify_comet_install.py --json          # 供 CI/脚本消费（含 verified/skipped/verdict）
python3 scripts/ops/verify_comet_install.py --no-strict    # 降级：C3 缺失不阻拦（本地体检用，CI/交接勿用）
```

| 判据 | 检查内容 | 为什么必须有 |
|---|---|---|
| C1 | 版本一致 | 版本号错配会让项目侧 skill 与 CLI 状态格式不兼容 |
| C2 | `bin/comet.js` 可执行位 | npm 重装常见 0644，表现为 `comet: permission denied` |
| C3 | **官方 tarball 全包逐字节比对**（fail-closed） | 详见 §三：version + doctor + 一次 update 都不够；**tarball 缺失时严格模式判 UNVERIFIED（exit 2），不再假绿**，用 `--fetch-tarball` 复现 |
| C4 | `assets/` 与 `eval/` 功能资产齐备 | `comet update` 只报它当时要用的 assets，不报 eval/ 与 .d.ts 缺失（轻量兜底，强校验见 C3） |
| C5 | doctor 健康 + **自适应**白名单 | 上游缺陷（Trae/Trae CN 互斥）仍在时容忍告警，上游修复后白名单**自动失效**，残留告警立即判红 |
| C6 | Hook Router 唯一（**自适应** platform 集合 + **同事件重复接线检测**） | 「一次写入事件最多进入一个 Guard」：① 每个平台配置至少接线 1 个 router（否则平台无保护）；② **同一 hook 事件下不得出现 2+ 个 distinct router 叶子**（= 重复 Guard，会让一次写入被双次拦截/加锁，必须判红）；③ platform 集合 ⊆ 该平台允许集（缺陷修复后 `.trae` 仅允 `trae`、`.trae-cn` 必须存在且仅 `trae-cn`）。router 路径常 `base64(node -e …)` 内嵌，判据须解码后再判定，避免误报 |
| C7 | 项目配置已入库 | 防止团队 clone 后退回 legacy fallback |
| C8 | 生成资产不污染工作树 | 多平台 5×65 文件入库会造成无意义 diff 与合并冲突 |

脚本**只读**：不跑 `comet update`、不改 Comet 状态、不写项目文件。
**刻意不接入 `scripts/check.sh`**：工具链健康是运维动作，不是每次提交的业务门禁；
接进去会把全仓提交强绑到本机全局 npm 安装，属于典型的「配了不生效」假绿。

### 变异验证纪律

`--self-test` 通过注入伪造采集结果**真实调用判据函数**，每条「必须判红」都配一条
「必须判绿」正例，避免把判据改成一律 FAIL 也能通过。当前 **16 条探针**（含 C5/C6 自适应白名单、
verdict 严格模式 SKIP→exit 2、C6 同事件重复 Guard 变异）。新增/修改判据时必须同批补探针。

---

## 三、三次被推翻的结论（升级前必读）

1. **「`comet --version` 正确 + `doctor` 全绿 = 装好了」是错的。**
   0.5.1 曾出现 CLI/Runtime 完好但 `assets/` 缺 115 文件的状态，version、doctor、
   `state next` 全部照常通过，只有真跑 `comet update` 才报 `incomplete (115 required assets are missing)`。

2. **「补齐 `comet update` 报错的 153 个 assets 就完整」也是错的。**
   对官方 tarball 做全包比对后，又发现缺 **1122** 个文件：696 个 `dist/**/*.d.ts`、
   425 个 `eval/**`、README、favicon。已装文件 `differ=0`，只是**静默缺文件**。
   结论：完整性终验必须做全包比对；`update` 只报它当时要用的资产。

3. **「只刷 `--platform codebuddy`」是错的。**
   doctor 自报其余 4 平台 `partial(1/65 或 47/65)` 且 `legacy managed Rule remains`。
   正解是不带 `--platform` 的 `--scope project` 全平台刷新。

### 批量补齐的正确姿势

写 `/opt/homebrew` 系统目录时，1122 次 `mkdir -p` + `cp` 与 `cp -R` 都会被
120s SIGTERM（Exit 137）。用单进程 tar 批量解出，约 24 秒完成，之后记得补可执行位：

```bash
# 清单里每行加 package/ 前缀，一次性解出
tar -xzf /tmp/rpamis-comet-0.5.1.tgz -C "$PKG" --strip-components=1 -T /tmp/tar-list.txt
chmod +x "$PKG/bin/comet.js"
```

---

## 四、升级 SOP（业界最佳实践）

1. **先收口在途 Classic 变更**：官方迁移规则是
   "Complete and archive active tasks using 0.4.5 before upgrading"。
   跨大版本时先用旧版把 active change 归档，再升 CLI。
2. **备份**：项目侧 `.comet/`、各平台 settings/hooks、以及升级前的全局包目录，
   落到 `/tmp/bone-comet-*-<时间戳>/`，确认可回滚再动手。
3. **离线取官方 tarball**：`npm pack @rpamis/comet@<ver>`，记录 sha256 与全包文件数，
   作为后续所有完整性证明的唯一基准（不联网比对）。
4. **安装**：`npm install -g --prefix /opt/homebrew @rpamis/comet@<ver>`；
   遇 `ENOTEMPTY` 先清残留临时目录再重试，**不要**在残缺包上继续跑 update。
5. **修可执行位**：`chmod +x /opt/homebrew/lib/node_modules/@rpamis/comet/bin/comet.js`。
6. **全包逐字节比对**（§二 C3），`differ` / `missing` 必须为 0 才可继续。
7. **项目级平台资产刷新**：

   ```bash
   comet update --current-project --scope project --skip-self-update --yes --language zh --json
   ```

   `--skip-self-update --current-project --yes` 绕开 npm 自更新与联网拉取，
   实测 8 秒完成、幂等可重复跑；不带 `--platform` 才会刷全 5 平台。
8. **验证**：`comet doctor --json`、`comet state next <change> --json`
   （`runtimeFormat: sdk` 且 diagnostics 四项全 true）、`verify_comet_install.py` 全绿。
9. **精确提交**：`comet update` 会改写 tracked 的 `AGENTS.md`、`.gitignore`、
   `.github/instructions/`、`.github/hooks/`。共享树里用
   `git commit -F <msg> --only -- <单文件路径>` 逐个提交，**绝不 `git add -A`**。

### 回滚点

- 全局包：升级前的 `/opt/homebrew/lib/node_modules/@rpamis/comet` 备份 + 官方 tarball。
- 项目资产：`/tmp/bone-comet-assets-bak-20261107-084557`（含 ide-before-platform-update 293 文件）。
- 现场快照：`/tmp/bone-comet-final-20261107-103753`（`.gitignore` / `.comet/` /
  `.github/hooks/comet-guard.json` / `.trae/hooks.json` / `tracked-diff.patch`）。
- 回滚后必须重跑 §二 全 8 项，不能只看版本号。

---

## 五、已知缺陷登记（不刷绿）

### D1：Trae / Trae CN 共用 `.trae/hooks.json`（上游缺陷）

**现象**：doctor 恒有且仅有 1 个 project 级 warn
`hooks: Trae (project) | managed Hook missing`。

**取证**：`.trae/hooks.json` 里的 base64 命令指向
`.trae-cn/skills/comet/scripts/comet-hook-router.mjs --platform trae-cn`，
而 `.trae-cn/hooks.json` 根本不存在；doctor 却按 `trae` 与 `trae-cn` 两个平台
分别校验**同一个文件**。

**可证伪实验**（已实跑）：
- `comet update --scope project`（不带 platform）→ 文件写 `trae-cn`，Trae 报 warn、Trae CN pass；
- `comet update --scope project --platform trae` → 文件改回 `trae`，
  **Trae CN 立刻报 warn、Trae pass**。

即两个平台互斥，无论刷哪个都必然剩 1 个 warn。按 doctor 的建议重跑
`--scope project` **不能自愈**。

**裁定**：不手工伪造 `.trae-cn/hooks.json`、不改 base64 来「消掉」告警。
手工改会制造双 Guard 或平台串台，且与官方 update 幂等结果冲突（下次 update 即被覆盖）。
正确做法是**白名单自适应**登记（校验脚本 C5 / C6 已显式解释该告警，并探测 `.trae-cn/hooks.json`
是否存在、各 platform 是否正确）：缺陷仍在时容忍告警；**上游修复后白名单自动失效，残留告警立即判红**，
无需人工记得删白名单。详见 §七「已收口」第 1 项。**功能不受影响**：项目实际使用的 CodeBuddy Hook 为 1 组 1 hook，路由唯一。

### D2：`--scope global` 未执行（14 个 global warn）

7 个平台 `skills partial (9/65)` 与 CodeGraph 系列告警都属**用户级**目录，
`comet update --scope global` 会改动该机器上**所有项目**的 IDE 配置。
按最小爆炸半径原则不擅自执行；需要时由用户在本机显式执行，不影响本项目的安装正确性。

### D3：CodeGraph 索引 `pending changes`

`codegraph sync` 已执行成功，但共享树并行会话持续写文件，索引会立刻再次出现
pending，属动态状态而非缺陷；CodeGraph 是可选能力，不纳入项目门禁。

### D4：SDK 全局写锁僵死（0.5.2 / macOS，偶发）

**现象**：`comet check run <change> build` 卡在「核对检查输入：0 项」，等待全局写锁
30 秒后超时，RC=70。

**取证**：锁在 `~/.comet/workspaces/<workspace>/runtime/**/.runtime-store-v2/**/.write.lock`，
**不在项目内 `.comet/`**。只读核对 6 个锁文件 pid 全部 DEAD、mtime 集中在 11-09～11-13；
`comet-sdk-blocking-bug.md` 记录前 9 次同一失败，而 `scripts/check.sh` 与 `mvn clean test`
独立运行均 PASS。

**裁定**：判为 SDK spawn 子进程静默崩溃后遗留的僵死锁，不清理全局锁、不改 SDK。
本轮 `check run ... build` 真实执行 7m16s、`exitCode=0`、Run 自动推进 verify，说明**非必现**；
登记为已知偶发缺陷，复发时须先确认 pid 已死且无后代进程，再决定是否清理。

### D5：宿主删除配额打断检查收据（0.5.2 × 宿主交互）

**现象**：Verify Guard 的 `bash scripts/check.sh` 真实跑完 411 秒、日志 59/59 +
「✅ 本地自检通过！」，但收尾写收据被
`SAFE_DELETE_BULK_CONFIRM_REQUIRED(count=50, threshold=50, scope=turn)` 拦下，
Guard RC=70，Runtime 记录停在 `status=started`。

**取证**：删除目标是全局 runtime 的 `.write.lock.contenders/*.choosing`。日志 1282 行、
manifest 1.22 MB 均已生成，但记录无 `inputBefore` / `inputAfter` / `manifestHash` /
`contentHash`。**日志全绿 ≠ Runtime 收据**：`record-outcome` 只在记录为 `completed` 时可用，
`check rerun` 需要新的 pending Action。

**裁定**：按 2026-11-10 同类故障先例，经用户确认后 `mark-unknown` → `retry`（证据摘要逐条
如实披露「子进程已执行且日志全绿 / Runtime 无可入账收据 / 无字段可回填 / 原执行器及后代
已停止」，并把 `not-executed` 明确限定为 **Runtime 侧语义**）→ **真实重跑** 7m34s，
`exitCode=0`、输入快照前后一致、收据齐全、safe-delete 0 次。不伪造成功收据；
删除配额按 turn 累计，新一轮会重置。

### D6：Archive Guard 部分执行导致交付回执死锁（结构性，未收口）

**现象**：`comet guard <change> archive --apply` 返回 RC=1，但 OpenSpec 归档已完整落盘
（active 目录移入 `archive/`、3 份主 spec 合并、plan 加 `archived-with` 标注）；
Action `full.archive.execute` 停在 `outcome-unknown`、`state.archived=false`，
诊断 `CLASSIC_EVIDENCE_DRIFT: Classic change directory does not exist`。

**取证**：源码级排除全部官方通道（完整矩阵见 §九）：`completeClassicSdkDelivery` 要求
`archived=true` + 无 running/unknown Action + 存在 pending 的 deliver Action；
`canRepairClassicDelivery` 同样要求 `archived=true`；`repair-archive` 要求「failed 且
**未创建**归档目录」；诊断给出的 `edit-review prepare-applied` 只替换
`revise-*` / `propose-escalation` / `continue-build` 类设计、计划漂移修复；
`interruptedOutcomeRequestTemplate` 仅在 `*.build.execute` 的 unknown `invoke_skill` 下生成。

**裁定**：归档是不可逆一次性副作用，**停手**；不回滚目录、不重放归档、不手改
`comet-state.yaml`、不声明 `not-executed`。以归档目录 + 精确提交 `87d753d6` 为最终事实，
Run 运行态缺口如实登记。宿主允许自由编辑文件也不改变结论：Run 真实状态在全局
runtime store，改投影文件既违规又无效。恢复条件见 §七。

### D7：doctor `current selection` 告警（0.5.2 新增检查，非安装缺陷）

**现象**：清除失效 selection 后，`comet doctor --json` 新增 project 级 warn：
`missing; Router can infer classic:<change> read-only — select it explicitly before
concurrent work`。

**取证**：这是**工作区运行态**（Router 退化为只读推断），与包完整性无关；把它计入
安装完整性 C5 会让 `verify_comet_install.py` 恒红，属判据语义错位（误报比漏报更致命）。

**裁定**：C5 精确化为「安装完整性相关告警」，运行态告警**显式登记但不计入 fail**；
分类自适应——仅当 `check` 与 message 形态完全匹配时豁免，任何 fail/error、措辞变化或
新增同类告警仍判红，并配 2 条变异探针。不静态白名单，也不伪造一个指向已归档 change
的 selection 来「消掉」告警。

---

## 六、能力边界裁定

| 场景 | 裁定 | 理由 |
|---|---|---|
| DDD 分层/依赖正确性 | **交给确定性静态门禁**（`scripts/check.sh`、ArchUnit） | `comet eval` 是 LLM 打分、非确定、成本高，不能作为架构正确性判据 |
| `comet eval` 的用途 | 只用于 **Skill 质量评估**（rubric / Pass@k / Pass^k） | 实测 `comet eval <skill> --collect --suite local` 可离线静态收集，不跑 Docker/Claude、不联网、不落盘；正式评测需 Python 3.11+、Docker、Agent CLI 与凭证 |
| Classic vs Native | 项目默认 **Classic**（`.comet/config.yaml`） | 存量 change 与 OpenSpec 产物均为 Classic 布局，切 Native 需独立 change，不做原地转换 |
| 多平台 skills/rules/hooks | 本机生成、**不入库** | 由 `.gitignore` 覆盖；每台机器按 §四 步骤 7 自行刷新 |
| `.comet/config.yaml` | **入库** | 团队共享配置，官方托管块显式放行 |

---

## 七、待拍板清单（下一轮输入）

以下条目**刻意未执行或仅完成形态设计**，作为下一轮输入：

### 已收口（本轮）
1. **D1 上游缺陷白名单自适应**：原待拍板「等上游修复后手工去掉白名单」已自动化——
   校验脚本 C5/C6 改为探测 `.trae-cn/hooks.json` 与 platform 是否真正分离，缺陷修复后
   白名单自动失效，残留告警立即判红；无需人工记得删白名单。对应本轮交付的严格模式 + 自适应白名单。

### 仍待拍板
2. **`--scope global`**：是否在本机执行 `comet update --scope global` 清掉 14 个用户级 warn，
   需用户明确授权（影响所有项目）。影响面量化：`doctor --json` 显示 14 个 warn 均为
   `*(global)` 或 `CodeGraph*`/`skills: *（global）`，分两类——
   - 7 个平台 `skills partial (9/65)`：用户级 IDE 技能不全，与本仓库安装无关；
   - `CodeGraph *` 系列：可选依赖索引，未装各 IDE CLI / MCP 时本就 pending。
   均不影响本项目安装正确性；执行命令见 §五 D2。
3. **`comet eval` 正式评测**：仅做形态设计，未落地 manifest（避免「配了不生效」死配置）。
   消费方为 `comet eval` CLI，前置条件：Python ≥ 3.11、Docker 可用、`comet eval --help` 正常、
   Agent CLI 与模型凭证就绪、项目内 `comet/eval.yaml` manifest 存在。本轮仅离线
   `comet eval <skill> --collect --suite local` 验证链路可用（不跑 Docker/Claude、不联网、不落盘）。
4. **`multi-insurer-biz-identity-catalog-mapping` 归档**：该 change 已降级为
   普通 OpenSpec plain change（`.comet.yaml` 已删除），`tasks.md` 中 **0 项勾选**
   （P0 内核由并行会话执行）。**禁止**手写 `comet-state.yaml` 或伪造 run-step 来强行归档；
   待 P0 内核完成、tasks 勾齐并跑通 verify 后，再走 `/comet-classic` 的 verify→archive。
5. **DDD 静态门禁增强**（ArchUnit / golangci-lint 覆盖更多判据）：属独立长期方向，
   与 Comet 升级无依赖，需另立 change。
6. **`.github/hooks/comet-guard.json` 的本机绝对路径**：该文件在 HEAD 里就写死了
   `/Users/renhui.trh/wps/bone`（历史遗留），0.5.1 只是把同一路径 base64 化。
   **本轮刻意不提交这个 `M`**：提交它会把不可移植路径继续固化、且 base64 化后
   可审计性更低；手工改成相对路径则会被下次 `comet update` 覆盖（配了不生效）。
   建议后续推动上游改用相对路径/平台变量，或与官方生成结果对齐后另立 change。
   该文件保持工作树未提交状态，不属本轮交付。
7. **`synapclaim-architecture-convergence` 的 SDK 交付回执**（D6）：归档目录与提交
   `87d753d6` 已是最终事实，但 Run rev90 仍停在 `outcome-unknown` / `archived=false`。
   需 Comet 提供接受「归档已落盘但收据缺失」的合法恢复通道，或用户明确接受终态；
   在此之前不重放归档、不回滚目录、不手改状态字段。
8. **`synapclaim-risk-fraud-dimensions` 运行态恢复**：业务实现已落地、`tasks.md` 15/15，
   但 Run rev6 停在 `full.open.revalidate` 的 `outcome-unknown`、无 `verify.md` / plan，
   且缺 `build_mode` / `tdd_mode`。恢复会推翻既有「只恢复架构收敛」的选择，属不可逆
   Runtime 写入，需用户新的明确授权并指定配置值，之后仍要走完整 full 流程。
9. **本地提交推送 `origin/dev`**：当前 `origin/dev...dev = 9/7` 已分叉，用户已确认
   `local` 交付（不 push、不建 PR）。若要推送，须先决定 rebase / merge 策略并处理
   与远端 9 个提交的冲突；本轮未执行、未改共享 dev 历史。

---

## 八、本轮（2026-11-07）合理性裁定矩阵

指令是「基于业界最佳实践，按最推荐的方案完成所有**合理的**优化任务」。
「合理」是判据，不是修辞；以下逐项给出裁定与依据。

| # | 候选动作 | 裁定 | 依据 |
|---|---|---|---|
| 1 | 补齐 `.gitignore` 生成资产忽略规则 | **执行** | 133 个未跟踪生成文件污染共享树，入库会造成 5×65 文件无意义 diff 与合并冲突；属配置即代码的最小收口 |
| 2 | `.comet/config.yaml` 入库 | **执行** | 官方托管块 `!/.comet/config.yaml` 显式放行，是团队共享 workflow 配置；漏入库会让 clone 退回 legacy fallback |
| 3 | 固化全包完整性校验为可执行脚本 | **执行** | 前两轮三次结论被推翻，教训只存在于会话日志；按《Rules of ML》「每条规则写成可执行 assertion」落成 C1–C8 |
| 4 | 为判据配变异验证 | **执行** | 「全绿 ≠ 有判别力」；10 条探针（4 正例 + 6 变异）真实调用判据函数 |
| 5 | 写升级/回滚 Runbook 并登记索引 | **执行** | 可复现运维基线；回滚点、SOP、已知缺陷单一真源 |
| 6 | 按 doctor 建议重跑官方 `--scope project` | **执行（幂等）** | 先走官方推荐路径再判定，实测 325 文件/0 失败，但 Trae warn 未自愈 |
| 7 | `codegraph sync` | **执行** | 官方建议的低风险索引同步，实测 EXIT=0 |
| 8 | `comet eval --collect` 离线验证 | **执行** | 不跑 Docker/Claude、不联网、不落盘，验证评估链路可用，为能力边界裁定提供实证 |
| 9 | 手工伪造 `.trae-cn/hooks.json` 或改 base64 消掉 warn | **拒绝** | 制造假绿 + 可能双 Guard；已用对照实验证明是上游互斥缺陷，不是漏装 |
| 10 | `comet update --scope global` | **拒绝** | 改动该机器所有项目的 IDE 配置，爆炸半径过大；属用户级决策 |
| 11 | 强行归档 `multi-insurer` change | **拒绝** | `tasks.md` 0 项勾选、P0 内核未完成；归档即文档抢跑，且需手写状态文件伪造 run-step |
| 12 | 把 `comet eval` 用于 DDD 架构门禁 | **拒绝** | LLM 打分非确定、成本高；架构正确性必须由 ArchUnit / check.sh 等确定性门禁负责 |
| 13 | 把完整性校验接进 `scripts/check.sh` | **拒绝** | 会把全仓提交强绑到本机全局 npm 安装，是典型「配了不生效」假绿；工具链健康属运维动作 |
| 14 | 切换默认 workflow 为 Native | **拒绝** | 存量 change 与 OpenSpec 产物均为 Classic 布局；切换需独立 change，不做原地转换 |

**业界实践依据**：Google《Rules of ML》/《ML Test Score》（判据须可证伪、写成可执行
assertion）、CD4ML（可复现证据链：内容哈希、provenance）、NIST AI RMF（文档状态须与实证
一致）、最小爆炸半径与 fail-closed 原则，以及仓库自身「误报比漏报更致命」「刷绿不可取」
的既有纪律。

### 门禁证据

- `verify_comet_install.py`：8/8 PASS（C3 全包 3570/3570 逐字节一致，differ=0 / missing=0）。
- `verify_comet_install.py --self-test`：16/16 PASS，判据可证伪（含 C6 重复 Guard 变异）。
- `comet doctor --json`：`healthy=true`，51 pass / 0 fail / 15 warn（1 个已知上游缺陷
  + 14 个用户级/可选能力告警，均已登记）。
- `comet update --scope project`：`status=complete`、325 skills、5 rules、5 hooks、0 failed。
- `./scripts/check.sh`：**59/59 全绿、EXIT=0、末行「✅ 本地自检通过！」**（2026-11-07 实跑，耗时 9m29s）。

### 本轮（2026-11-07 续）修正与新增裁定

承接 §八 既有 14 项裁定，本轮聚焦「上一版交付物自身的两个真实缺陷」：

| # | 候选动作 | 裁定 | 依据 |
|---|---|---|---|
| A | 沿用上一版脚本（C3 缺 tarball 仍打印✅ + EXIT 0） | **拒绝，已修** | 团队 clone 后 `/tmp` 无 tarball，脚本仍 EXIT=0 + "基线一致"，属 report-only 假绿；改为 fail-closed：SKIP→exit 2 |
| B | C5/C6 白名单写成静态硬编码 | **拒绝，已改自适应** | 静态白名单会永久放水；改为探测上游缺陷是否真存在，修复后自动失效 |
| C | `--fetch-tarball` 自动复现官方包 | **执行** | npm pack sha256 已实测稳定（4942b8b8…），使完整性校验可移交、不依赖会话残留 |
| D | `--json` 只给 failed 字段 | **执行（增强）** | 补充 verified/skipped/strict/verdict，使 CI 能区分「验证通过」与「未验证」 |
| E | 真实变异实验（移走 tarball 测 SKIP） | **执行** | 退出码确为 2；fetch 复现后确为 0，验证 fail-closed 真生效（非纸面） |

证据更新：
- `verify_comet_install.py`：8/8 PASS，C3 全包 3570/3570 逐字节一致；
  **严格模式无 tarball 时退出码 2（UNVERIFIED），`--fetch-tarball` 复现后退出码 0**。
- `verify_comet_install.py --self-test`：**14/14 PASS**（新增 C5/C6 自适应白名单、verdict 严格模式共 4 条探针）。
- 真实端到端：临时移走 tarball + 清缓存 → 默认实跑 exit 2 / "⚠ 未完整验证"；
  `--fetch-tarball` → npm pack 复现（sha256 一致）→ 8/8 PASS / exit 0。

### 本轮（2026-11-07 第三轮）C6「唯一」判据硬化

上一版 C6 只判 `router 条目数 >= 1`（且把 blob 数当条目数），存在两个真实缺陷：

1. **漏报**：同一 hook 事件下出现 2 个 distinct router 叶子（= 重复 Guard，一次写入被
   双次拦截/加锁）时 `n >= 1` 恒真，判据无判别力 —— 属「漏报比误报更致命」。
2. **误报风险**：router 路径以 `base64(node -e …)` 内嵌时，字面量 `comet-hook-router`
   不在序列化文本中；若按 blob 计数会把 `bash`+`powershell` 双写同一调用误计为 2 条，
   进而误红合法配置（本仓库 `.github/hooks/comet-guard.json` 即此形状）。

修正：
- 按**事件**聚合 distinct router 叶子数，要求「至少 1 条」且「任一事件 ≤ 1 条」（重复接线判红）；
- 判定前先解码 base64 再识别 router，避免把可移植双写误计为重复；
- 兜底全文本扫描用集合去重解码结果，杜绝同调用双计。

证据：`--self-test` **16/16 PASS**（新增 2 条 C6 探针：同事件 2 条 router 必须判红、
单事件单 router 必须判绿）；真实环境 `verify` 8/8 PASS、exit 0，C6 对
`.github/hooks/comet-guard.json` 正确报 `router=1 单事件最多1`（修复前误报 `router=2`）。

---

## 九、本轮（2026-11-14）合理性裁定矩阵与运行时决策矩阵

指令是「基于业界最佳实践对以上合理的建议执行优化」。承接 §八 的裁定方式：
「合理」是判据，不是修辞；逐项给出裁定与依据，执行合理项、拒绝不合理项。
本轮的「以上建议」是上一轮遗留的三类恢复建议（归档回执、change 恢复授权、
分叉推送）；优化方向是把它们从**对话里的口头建议**升级为**版本化、可核验、
可复用的工程资产**，并同步修复因此暴露的过期基线。

### 9.1 合理性裁定矩阵

| # | 候选动作 | 裁定 | 依据 |
|---|---|---|---|
| 1 | 把 `verify_comet_install.py` 基线由 0.5.1 同步到 0.5.2 | **执行** | 实测 `comet --version=0.5.2`，脚本仍期望 0.5.1 ⇒ C1 FAIL；C3 拿 0.5.1 包比对 0.5.2 安装得 2980/3570、differ=510。属 NIST AI RMF「文档状态须与实证一致」的反例 |
| 2 | 新常量全部由官方包实算，不放宽任何阈值 | **执行** | `npm pack` 得 sha256 `2003eb17…d1339`、C3 同口径 3656 文件；实测本地 **3656/3656、differ=0、missing=0**。先有证据再改常量，不是先放宽阈值刷绿 |
| 3 | 把 doctor `current selection` 静态加进白名单 | **拒绝** | 静态豁免永久放水，状态变化后不会自动失效；且它是真实运行态，不该被「消掉」 |
| 4 | C5 精确化为安装完整性告警 + 自适应分类 + 显式登记 + 变异探针 | **执行** | 把工作区运行态计入安装完整性属判据语义错位（误报）；分类仅在形态完全匹配时豁免，措辞变化即判红 |
| 5 | 补实 `claim-liability-config` 主 spec 的 `TBD Purpose` | **执行** | 归档生成的占位符属死配置；selection 清除后 Guard 放行主规格文档修订，提交 `27ee5127a`；仅改 Purpose 段，不动任何 Requirement / Scenario |
| 6 | 把 D4–D7 四个 0.5.2 运行时缺陷登记进 §五 | **执行** | Single Source of Truth + Known-Issues Register；此前只存在于本机缓存与会话记忆，团队不可见 |
| 7 | 手改 `comet-state.yaml` 把 `archived` 置 true 以「收口」交付 | **拒绝** | 伪造收据；Run 真实状态在全局 runtime store，改投影文件既违规又无效 |
| 8 | 回滚归档目录 / 重放 `guard archive --apply` | **拒绝** | 归档不可逆；二次搬运会制造更严重的证据漂移 |
| 9 | 声明 `not-executed` 走 `mark-unknown` + `retry` 复位 `:23` | **拒绝** | 归档确已执行，恢复协议明确禁止在「已执行或仍不明」时声明 not-executed |
| 10 | 把完整性校验接进 `scripts/check.sh` | **拒绝** | §八 #13 已裁定：会把全仓提交强绑本机全局 npm 安装，是「配了不生效」假绿 |
| 11 | 强行归档 `risk-fraud-dimensions` / `multi-insurer` | **拒绝** | 前者缺 verify 与 `build_mode` / `tdd_mode`、后者 0/15 且 P0 内核在途；归档即文档抢跑 |
| 12 | push / 建 PR 处理 `9/7` 分叉 | **拒绝** | 用户已确认 `local` 交付；分叉处理会改写共享 dev 历史 |
| 13 | 伪造一个指向已归档 change 的 selection 来消掉 doctor 告警 | **拒绝** | 制造假绿，并会让 Phase Guard 把后续写入错误归属 |

### 9.2 `outcome-unknown` 恢复决策矩阵（Runbook）

三条阶段共用同一取证清单：`revision` 未变（未发生写入）、`claim.token`、
`record.status`、`receiptRef` / `manifestRef`、原执行器及**所有后代进程**已停、
输入快照前后是否一致。日志、外部 marker 或进程结束本身**都不能**充当 Runtime 收据。

| 阶段 | 典型静态证据 | 唯一合法通道 | 明确禁止 |
|---|---|---|---|
| build `*.build.execute` | `command=null`、`record.status=missing`、`receipts=[]`、无执行器残留 | 核实「原尝试完全未执行」后 `mark-unknown` → `retry(not-executed)` → **真实重跑**生成新收据 | 直接重放外部动作；不写证据摘要 |
| verify `full.verify.check` | 子进程已执行且日志全绿，但记录停在 `status=started`、无输入快照与哈希 | `record-outcome` **仅在记录 `completed` 时**可用；否则经用户确认按先例复位，并在 `evidence.summary` 逐条如实披露「子进程已执行 / Runtime 无可入账收据 / 无字段可回填」，再**真实重跑** | 把 `not-executed` 泛化到子进程；把日志全绿当成功收据 |
| archive `full.archive.execute` | 归档目录已落盘、主 spec 已合并，但回执缺失、`archived=false` | **无官方通道**：停手，以归档目录 + 精确提交为终态，如实登记运行态缺口 | 回滚目录、重放归档、`mark-unknown`+`retry`、手改状态字段 |

判定要点：**verify 阶段可复位重跑，archive 阶段不可**——差别不在「能否重试命令」，
而在副作用是否可逆。归档一旦搬走 active 目录，重放即二次副作用，任何
「补一张成功收据」的做法都是伪造证据。

### 9.3 可复现核验命令（只读，任何人可复核）

```bash
comet --version                                             # 期望 0.5.2
npm view @rpamis/comet@0.5.2 dist.shasum --json            # 期望 11866c3e…326a6a
npm pack @rpamis/comet@0.5.2 && shasum -a 256 rpamis-comet-0.5.2.tgz   # 期望 2003eb17…d1339
python3 scripts/ops/verify_comet_install.py --json         # 期望 verified=8 failed=0 verdict=0
python3 scripts/ops/verify_comet_install.py --self-test    # 期望全 PASS「判据可证伪，非恒绿」
python3 scripts/ops/verify_comet_install.py --fetch-tarball --json  # 换机复现，期望 verdict=0
comet state check <change> <phase> --recover --details --json      # RC=1 是恢复态提示，不是命令失败
git show --stat 87d753d6                                   # 归档终态事实
```

`--json` 的 C5 断言：必须同时出现 `工作区运行态告警(显式登记,不计入安装缺陷)=['current selection']`
与 `未登记 project 级告警=无`——前者证明没有隐藏状态，后者证明没有放水。

### 9.4 门禁证据

- `python3 -m py_compile scripts/ops/verify_comet_install.py`：OK。
- `verify_comet_install.py --json`：**8/8 PASS、verdict 0**；C3 `3656/3656 differ=0 missing=0`。
- `verify_comet_install.py --self-test`：**18/18 PASS**，「判据可证伪，非恒绿」；含新增 2 条 C5 变异探针。
- `verify_comet_install.py --fetch-tarball --json`：verdict 0，官方包可换机复现。
- `comet doctor --json`：`healthy=true`，43 pass / 0 fail / 21 warn（D1 上游缺陷 1 个、
  D7 工作区运行态 1 个，其余 global / 可选能力告警）。
- 全量提交门禁由 pre-commit 执行，结果以对应提交记录为准；本轮未改动
  `scripts/check.sh` 的门禁接线。

**业界实践依据**：Google《Rules of ML》/ ML Test Score（判据须可证伪、写成可执行
assertion）、CD4ML（官方 registry provenance + 全包逐字节可复现比对）、NIST AI RMF
（文档状态须与实证一致，本次据此修过期基线）、Google SRE Workbook（Runbook 决策矩阵、
Known-Issues Register、恢复条件显式化）、Blameless Postmortem（unknown 如实登记、
不刷绿）、ADR（不可逆的归档终态决策版本化留痕），以及仓库既有纪律「误报比漏报更致命」
「最小爆炸半径」「fail-closed」「同一语义两处配置源 = 配了不生效」。
