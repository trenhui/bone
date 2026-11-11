# Bone AI 原生工程基础设施改造方案（v3 · 独立审查 + 业界实践对照复核 · 含 11.6 深度优化落点）

> **状态**：待审批施工蓝图 **v3**（2026-11-10 收口；2026-11-11 增补 11.6 深度优化落点）。v1 经三只独立子 Agent 审查判 `Request Changes` → v2 按其发现重写、经三轮独立审查 `Approve` → v3 增补业界实践对照复核（第十一章）→ 11.6 对 11.1「合理，可优化」四项与第六章门禁族做三栏深化（现状 / 业界标杆 / Bone 可优化落点）。修订记录见附录。
> **基准声明**：审查基准固定为 **Bone 项目自身规范真源 > 公认业界实践 > 个人口味**。本文不拿通用最佳实践覆盖已登记决策（ADR / 能力边界裁定 / 显式豁免）。
> **真源指针**：HC 门禁状态 → `doc/architecture/gate-state.json`（机器真源）与 `Bone-DDD-最终实践方案.md §G-1.7`（HC 状态表）；协作条款 → `doc/agents/06 §12`；工具链裁定 → `Bone-Comet工具链基线与升级运维.md`。本文**不复制** HC 表、不写门禁状态。
> **命名空间**：改造项用 `T1–T9`；门禁活性判据用 `LIV-R*`（`scripts/check-gate-liveness.py`）；交付流程阶段用 `P1–P8`（`06 §12.5`）；本文分期用「阶段 A/B/C」。三者互不复用编号。
> **入库事实**：**v1** 于提交 `5b140a722` 入库（3 个文件：本文、`doc/architecture/README.md` 新增 6 行小节、以及**由并行会话改动**的 `openspec/changes/synapclaim-ai-governance-hardening/.comet.yaml`）；**v2** 为主工作树未提交修订稿（三轮独立审查 `Approve`）；**v3** 经 Comet tweak change `ai-native-platform-plan-review` 在独立 worktree 分支 `comet/ai-native-platform-plan-review` 完成业界实践对照复核并归档。**本 change 未改动任何 L3 / L4 工程文件**；归档前在主工作树另行完成的 `.gitignore` 例外与 `gate-state.json` 真源追平**不属本 change 提交范围**，见 11.5。

---

## 一、结论：方向成立，但 v1 的施工单不可直接用

**保留的结论**：三份外部方案的方向判断正确；Bone（受治理源码 578,479 行）不需要新建 AI 工程平台，只需收敛型改造；不引入 SCIP / Neo4j / Sourcegraph、不重建编排、不让 AI 自动起草设计文档、不缩减回归测试集。

**v1 被推翻的部分**（详见第三章）：把 Bone **已登记的人工规范与已裁定能力**写成了「零命中缺口」，并据此提出会制造第二真源、CI 恒红或恒绿的改造。典型两例：

| v1 断言 | 事实 | 后果 |
|---|---|---|
| 「`受影响测试` 全仓零命中 → 无影响分析」 | `06 §11.13 ④`（`06:50`）、`.github/PULL_REQUEST_TEMPLATE.md:26`、`Bone-DDD-最终实践方案.md:1692/2262` 已有「受影响测试**已实际执行且通过**」人工条款 | 缺口应重新定义为「无 diff→符号→测试的**自动化推导**」，而不是「没有规范」 |
| 「CodeGraph 在正式文档零引用 → 需新建能力边界登记」 | `Bone-Comet工具链基线与升级运维.md:163–170` 有 D3 专节，`:170` 已裁定「**CodeGraph 是可选能力，不纳入项目门禁**」；`:181` 裁定「多平台 skills/rules/hooks 本机生成、**不入库**」 | 新建文档＝第二真源；把「不入库」资产接进 CI 判据＝干净检出上恒红或恒绿 |

**改造规模**：9 项，其中 **T3 / T7 降为「待架构师裁定是否改变既有策略」的前置议题**，不列入本期施工；真正可施工的是 7 项，且多数是「补条款 / 复用既有资产」，不是新建系统。

| 本方案做什么 | 本方案不做什么 |
|---|---|
| 补并行边界条款、把已有图谱挂进加载路由、复用既有交接产物、做**影响面提示**、补度量口径、登记离线工具 | 不新建代码智能平台、不新建能力边界文档、不新建 VALIDATION 文档、不接 CI 判据去管不入库资产、不缩减回归 |
| 全部改造复用既有资产与既有门禁范式 | 不新增常驻服务、不扩大运维面 |

---

## 二、现状基线（2026-11-10 复核值）

| 维度 | 复核值 | 口径 / 含义 |
|---|---|---|
| 受治理生产源码 | **578,479 行**（Java 385,713 / 3,823 文件；TSX 59,712；Python 54,396；TS 30,594；SQL 28,143；Go 15,355；JS 2,449；CSS 2,117 —— 八项合计闭合） | 约为「300 万行」前提的 **19.3%**；口径：排除 node_modules / target / dist / .git / .specstory / outputs / logs / .claude / .codebuddy 等，与 `git ls-tree` 全量入库文件数不同 |
| 全部文本 | 1,706,964 行；`.specstory` 会话史 585,711、`outputs` 产物 186,706 | 体量大头不是待理解代码 |
| 代码图谱 | `.codegraph/codegraph.db` 324 MB SQLite：105,067 节点 / 151,819 边 / 4,830 文件 / FTS；`calls` 28,476、`references` 17,511、`implements` 263；`unresolved_refs` 297,588；最后增量同步 2026-11-07 10:48 | 符号定位可用、静态调用链不完备；`errors.log` 有 2026-06-25 历史「4 files with errors」，最近一次增量同步无错 |
| 图谱注册与排除 | `~/.cursor/mcp.json`（`codegraph serve --mcp`）、`~/.claude/settings.json`（`mcp__codegraph__*` + `prompt-hook`）；排除入库的是**根 `.gitignore:224 .codegraph/`** | 用户级注册、每人一份 |
| 确定性门禁 | `scripts/check.sh` **893 行 / 59 步 / 66 处 `if ! python3` 调用**；耗时「约 422 秒」引自 `.comet/config.yaml` 注释与 11-09 记录，**非本轮实测**；`ci.yml` 逐条接线；ArchUnit 覆盖 10+ 模块 | 门禁活性判据是 **LIV-R1–R4e + LIV-R6/R7/R8**（不是 v1 写的 R1–R4e） |
| 活性判据要点 | `LIV-R2`：`(blocking)` 步骤数 ≥ `MIN_BLOCKING=15`；`LIV-R6`：`partial_wire.kind` ∈ `ci_only`/`known_incomplete`；`LIV-R7`：声明 `--self-test` 必须**带该参数**在活入口调用；`LIV-R8`：声明 `--check` 必须至少一处以**判据模式**调用 | `--check` 归因 LIV-R8、`(blocking)` 归因 LIV-R2（v1 把前者记到 R2 名下） |
| 上下文 | `AGENTS.md` 58 行薄引用；`CLAUDE.md` 43 / `.cursorrules` 3 / `copilot-instructions.md` 18 行全是指针；**路径路由表真源在 `06 §12.2:76–85`**，`doc/agents/README.md` 只有横切触发表且 `:32` 明写「主干表此处不重复」 | 渐进式加载已有，靠人工查表 |
| 规格与决策 | `openspec/changes/<change>/`：proposal / design / tasks / specs；部分 change **另有 `verify.md`、`.comet.yaml`、`.comet/handoff/`、`.comet/state-events.jsonl`**；ADR：`0001–0046` 共 46 篇 + `0000-template.md` + 非编号附录 `ADR-生产数据库增量迁移策略.md` + `README.md`（编号文件共 47 个） | 交接态**已存在**，不是 3 件套 |
| 并行 | `.claude/settings.json`：`agent_teams.enabled=true`、`max_parallel_agents=10`、`auto_merge_on_completion=false`；`doc/agenticx/Bone-Agentic-Engineering.md:964` 已有 `git worktree add ... feature/${FEATURE}@agent-$i` 编排脚本 | **字段消费方未验证**；既有脚本会**创建分支**，与 `AGENTS.md:32`「AI 禁止自行创建/切换分支」冲突；结论应是「实操散落、缺统一条款」，不是「边界规范为零」 |
| 技能与规则 | `.claude/skills` 28（入库 5）、`.codebuddy/skills` **37**（入库 0）、`.github/skills` **12**（入库 0）；同名 23 个（12 comet-* + 11 openspec-*）；`comet-workflow-guard` 另有用户级 `~/.claude/rules/` 与 `~/.codebuddy/rules/` 两份各 57 行 | `.gitignore:213/231/232/234/325–328` 精确排除 → **有意不入库**，非疏忽 |
| 度量 | `analyze-metrics.sh` 实为 **2 类指标**（功能交付周期含 Ralph Loop 子列 + 自愈统计），另有 2 处 `TODO`；`.claude/state/` **为空**且被 `.gitignore:205` 忽略 | 「可复算 / 样本可追溯」目前无数据支撑 |
| 影响分析 | **不是零命中**：`06 §11.13 ④`、PR 模板、`Bone-DDD-最终实践方案.md` 已有人工条款；缺的是自动化推导 | 缺口须收窄定义 |
| 就绪度复核工具 | `doc/ai-product/synapClaim/tools/_ai_native_readiness.py` 实跑 12 项判据（5 SATISFIED / 7 BLOCKED）；**主报告不参与阻断**是 `check_all.py:82` 的 `EXCLUDED` 既有裁定，`--self-test` 已由 `check_all.py:75/107` → `.github/workflows/synap-claim-docs.yml:44,46` 两个 `(blocking)` step 间接接线；`_` 前缀不匹配 `rglob("check-*.py")`，故不进 LIV-R4 监管 | 是**判据设计范本**；既不能写成「零调用孤儿」，也不能写成「生效门禁」 |

> 材料中的收益数字（采纳率 68%、类型错误降至 1/5、回归集缩至 18%）来自异质规模仓库，**不作为 Bone 立项依据**。

---

## 三、独立审查发现与处置（本轮新增）

三只全新子 Agent（正确性 / 成本与误报 / 文档—实现漂移）并行只读审查，结论均为 `Request Changes`。合并去重后，高置信度阻断项与处置如下：

| # | 发现（文件:行） | 严重度 | 处置 |
|---|---|---|---|
| 1 | v1:40/110「受影响测试零命中」可证伪（`06:50`、PR 模板:26） | Critical | 第二章改写；T4 定位为「把 `§11.13 ④` 人工条款自动化」 |
| 2 | v1:83/86「CodeGraph 正式文档零引用」可证伪（`Bone-Comet工具链基线与升级运维.md:163–170,198–200,234`） | Critical | **撤销新建能力边界文档**；T2 改为复用既有 D3 裁定 |
| 3 | v1:74 拟新增 `§12.7`，但 `06:144` 已有「12.7 与已有资产的关系」，`Bone-DDD-最终实践方案.md:2156` 引用「原 §12.7 资产表」 | Critical | 改用 **`§12.8`**；T1「改动 2b」落实 `doc/agents/README.md:57` 编号同步 |
| 4 | v1:98/144 把「skills/rules 本机生成、不入库」的**有意裁定**当缺陷；R3/R7 接 CI 会在干净检出上恒红或恒绿 | Critical | T3 / T7 **降为待裁定策略议题**，本期不接 CI、不新增判据 |
| 5 | v1:113/114 R4 宣称 report-only 却要求 `(blocking)` + `--check`，正是 `LIV-R8`（`:544–582`）修过的「标着 blocking 却从不拦」假绿 | Critical | T4 改**互斥接线**：report-only 挂非 blocking job；转阻断另立一套 |
| 6 | v1:34/166–174 门禁判据族只写 R1–R4e，漏 LIV-R6/R7/R8；`--check` 归因错 | Critical | 第六章按 LIV-R1–R4e + R6/R7/R8 重写（无 LIV-R5） |
| 7 | v1:114/166/169 要求新增 `[N/59]` 并同步历史快照；`check.sh:857–861` 既有 G0 范式明确「**不带 `[x/59]` 编号，避免改动计数**」，`gate-state.json:_comment` 明写 `[N/M]` 是建立时历史快照 | High | 第六章改为沿用不带编号范式；历史快照不动 |
| 8 | v1:122–125/198 R5 与既有 `verify.md` / `.comet/handoff/` 重复，产物计数 3/4/6/7 自相矛盾；仓库无 `tasks.md` 模板文件 | Critical | T5 **撤销 `VALIDATION.md`**，复用 `verify.md`；明确「仅新 change 生效、存量归档不动」 |
| 9 | v1:112 R4 用 `git diff --name-only` 却要输出「变更符号」——粒度不足；图谱无 test→code 边（`contains` 占 65.6%、`calls` 指向测试路径仅 6.6%）、`unresolved_refs` 是独立表不被读取 | Critical | T4 收窄为**影响面提示**：改 `git diff -U0` + hunk 行号映射；先做全仓正向审计量化空集率 |
| 10 | v1:73/194 `max_parallel_agents` 字段消费方未验证，数字 3 无推导；`.codebuddy/settings.json` 独立、不受该字段约束 | High | T1 **删掉硬数字**，先验证字段被谁读取 |
| 11 | v1:41/174 把零调用的 `_ai_native_readiness.py` 当「生效门禁/既有范式」 | High | 第二章按 `check_all.py:82` 的 EXCLUDED 既有裁定如实标注（主报告非阻断、`--self-test` 已间接接线）；**T9** 只处理「是否让主报告参与阻断」 |
| 12 | v1:144 R7 真源写 `doc/agents/README.md`，实际路由表在 `06 §12.2` | Critical | T7 真源改 `06 §12.2` |
| 13 | v1:38/96 数字不符（`.codebuddy/skills` 37 非 40、用户级 rules 在仓库外、漏 `.github/skills` 第三套） | High | 第二章更正；T3 范围扩到三套 |
| 14 | v1:73–76/188–190 审批级 L0–L4 系统性错配；L4 漏 `doc/agents/README.md`（`:60` 属 §12.3） | High | 各 T 项按 `06 §12.3` 重标；未覆盖形态写「待架构师定级」 |
| 15 | v1:75/188 分期 `P0/P1/P2` 与 `06 §12.5` 的 `P1–P8` 撞名；改造项 `R1–R8` 与活性判据 `R1–R8` 撞名 | Medium | 改「阶段 A/B/C」+ `T*` / `LIV-R*` 前缀 |
| 16 | v1:179–180 单一真源冲突：`doc/agents/README.md:59` 称 HC 状态真源是 DDD §G-1.7，`gate-state.json:_comment` 称自己是门禁状态唯一真源 | Medium | 新增第九章辨析（含 `06 §12.1`、`AGENTS.md:38`、DDD §G-3），列入待拍板 |
| 17 | v1:3/225 与 git 事实不符（提交含 3 文件、README 是 +6 行小节；并行会话已绑定 change 至 dev） | High | 状态行与待拍板第 10 条如实改写 |

**What Looks Good（保留）**：不建平台 / 不回抄 AGENTS.md / 不缩回归 / 拒绝异质收益数字 / fail-closed / 单一真源 / 保持薄引用 / L4 只出建议稿 / 不误伤 `..adapter.schedule..` 等显式豁免 —— 这些方向三只审查员一致认可。

---

## 四、改造项 T1–T9

### T1 并行实施边界（阶段 A，最高优先级）

| 项 | 内容 |
|---|---|
| **证据** | `check.sh:10–14` 已为 worktree 适配 `FAIL_COUNT_FILE`；`doc/agenticx/Bone-Agentic-Engineering.md:964` 已有 worktree 编排脚本（会建分支，与 `AGENTS.md:32` 冲突）；共享树实证出过 `index.lock` RC=128、代提交、未暂存文件被 clean |
| **目标** | 把散落的实操收敛成统一条款；**不是**设一个没有消费方的数字 |
| **改动 1（前置核实，非改动）** | 先确认 `max_parallel_agents` / `agent_teams` 被哪个工具读取；核实前**不改** `.claude/settings.json`，不写「10→3」的量化目标 |
| **改动 2（L4，仅建议稿）** | `doc/agents/06` 新增 **`§12.8 并行实施边界`**：① 每任务声明 `scope_paths`、同一文件单写者；② 复用 `AGENTS.md:32` 既有分支纪律：worktree 用 `--detach` / base=HEAD，**禁止 `git worktree add ... <新分支>`**；③ 共享树上 `git commit -F <msgfile> --only -- <paths>`、新文件先 `git add`、关键产物完成即 `git add`；④ `index.lock` 冲突重试、提交前 `git log --oneline -2` 排除代提交；⑤ **`FAIL_COUNT_FILE` 跨 worktree 共享**：并行会话会互相叠加熔断计数，须写明共享语义与并行处置；⑥ 只对自己路径跑格式化，不整模块 apply |
| **改动 2b（L4，仅建议稿）** | `doc/agents/README.md:57` 章节编号约定补「新增 §12.8 沿用拆分前编号」，使历史引用仍可定位 |
| **改动 3（阶段 B，可选）** | 在生成 change 的 skill / 模板里加 `scope_paths` 字段；**不新增** `check-parallel-scope.py`（避免又造一个需接线的判据） |
| **验收** | 两个并行任务实操（非 mock）：`git status` 无交叉暂存、`check.sh` 末行 `✅ 本地自检通过！`、`git branch --contains` 复核落点 |
| **审批** | 改动 2 / 2b = **L4**（`doc/agents/06` §12.3 与 `doc/agents/README.md:60` 均属 L4，须架构师本人或明确授权）；改动 1/3 待架构师定级 |
| **回滚** | §12.8 由架构师撤销 |

### T2 CodeGraph 纳入加载路由（阶段 A）

> v3 业界实践增补见「十一、11.2①」。

| 项 | 内容 |
|---|---|
| **证据** | 图谱已建且 daemon 增量同步；`Bone-Comet工具链基线与升级运维.md:170` **已裁定**「CodeGraph 是可选能力，不纳入项目门禁」；`:163–170` D3 说明 pending changes 属动态状态 |
| **目标** | 让 Agent 知道「何时用、不可用怎么办、能力边界在哪」，**不新建第二份边界文档** |
| **改动 1（L4，仅建议稿）** | `doc/agents/README.md` 横切触发表增一行：`代码/符号/调用链定位 → .codegraph（本机可选能力；裁定见 Bone-Comet工具链基线与升级运维.md §五 D3）` + 降级路径（MCP 不可用改用 LSP / 结构化检索，结论须给文件路径与行号）。**只加指针，不复制 D3 正文** |
| **改动 2（L4，仅建议稿）** | `06 §12.2` 路由表挂同一指针，保持入口薄引用 |
| **改动 3（待架构师定级）** | 在既有 `Bone-Comet工具链基线与升级运维.md` D3 补一句「`unresolved_refs` 量级与静态盲区」并链接；`unresolved_refs` 精确值属动态快照，**不写死数字**，避免文档随索引过期 |
| **验收（人工一次性 + 门禁补充）** | 用 `grep -c` 检查 `doc/agents/README.md` 与 `06 §12.2` 各含且仅含一条指定指针、未复制 D3 正文；再跑 `python3 scripts/check-doc-code-symbols.py` 与 `python3 scripts/check-ddd-doc-drift.py` 作「不破坏」型补充（**不为此新增 `check-*.py`**）；关闭 MCP 后按降级路径完成一次符号定位实操 |
| **不做什么** | 不新建 `代码智能能力边界登记.md`（静态快照必然过期，且与 D3 第二真源）；不把图谱结果写进 `gate-state.json`；不升格为团队共享服务；不重建 SCIP / Neo4j |
| **审批** | 改动 1/2 = **L4**（`doc/agents/README.md:60` 明确属 §12.3 L4）；改动 3 待架构师定级 |

### T3 技能与规则一致性（**前置议题，未裁定前不施工**）

| 项 | 内容 |
|---|---|
| **既有裁定** | `Bone-Comet工具链基线与升级运维.md:181`「多平台 skills/rules/hooks **本机生成、不入库**」；`.gitignore:213/231/232/234/325–328` 精确排除。实测入库：`.claude/skills` 5 / `.codebuddy/skills` 0 / `.github/skills` 0 |
| **v1 错在哪** | 新增 `check-agent-skills-sync.py --check` 接 CI，在干净检出上**必然 fail-closed 恒红或跳过恒绿**；且判据只覆盖两套，漏 `.github/skills` 第三套与用户级 `~/.claude/rules`、`~/.codebuddy/rules`（仓库外，仓库内脚本无法比对） |
| **本期动作** | **仅提交议题**：是否改变「不入库」策略？若改变，需先改 `.gitignore`、明确入库清单、把范围定为三套 + 声明用户级目录不在仓库判据管辖内，再谈生成脚本与判据 |
| **若策略不变** | 只做**本机可选**同步脚本（不接 CI、不新增 `check-*.py`），直接引用 §六:181 既有裁定、**不新增任何文档表述**；同名 23 项由本机脚本收敛 |
| **审批** | 策略变更 = **L3（`.gitignore`）+ 待架构师定级（工具链裁定文档 / 策略变更）**；裁定前不动 |

### T4 变更影响面提示（阶段 B，report-only）

> v3 业界实践增补见「十一、11.2②」。

| 项 | 内容 |
|---|---|
| **准确定位** | 把 `06 §11.13 ④` 与 `.github/PULL_REQUEST_TEMPLATE.md:26` 的「受影响测试已实际执行」**人工条款自动化**；不是「从零建影响分析」 |
| **输入** | `git diff -U0 <base>..HEAD`，解析 hunk 行号映射到 `nodes.start_line/end_line` 定位变更符号（`--name-only` 粒度不足） |
| **图谱现实（须写进判据说明）** | edges 无 test→code 边；`contains` 占 99,546/151,819（65.6%）；`calls` 指向测试路径仅 1,889/28,476（6.6%）；`unresolved_refs` 是独立表且不被遍历读取 → **不能承诺「关联测试 / 建议回归集」** |
| **输出（收窄）** | 变更符号 / 直接调用者 / 静态盲区清单 / 相关 ADR（即 T8）/「本结论不构成测试集依据」声明 |
| **三条硬约束** | ① **不阻断、不缩减测试集**；② 高风险路径（claim、额度、权限、DDL、迁移、事件）附加「E2E 全量」提示（**消费方 = PR 模板勾选项 / E2E 清单，须先指定**）；③ 图谱读失败**或读成功但结论为空**都 fail-closed 输出「影响面未知 → 扩大回归」，绝不输出空集 |
| **前置** | 先做**全仓正向审计**量化空集率 / 噪声率；324 MB SQLite 读取几乎必然 >3s ⇒ **CI-only**，不进 pre-commit（`.comet/config.yaml` 的 900000ms 余量约 478s） |
| **互斥接线** | **report-only 阶段**：独立非 blocking CI job（step name **不写** `(blocking)`）、脚本不声明 `--check`、登记 `partial_wire kind=ci_only`；**转阻断阶段**（须另行拍板）：另立一套接线、声明 `--check` 判据语义并满足 LIV-R8。两者不得混写；新增判据沿用 G0 **不带 `[x/59]` 编号** |
| **验收** | 全仓正向审计报告 + 改一个应用服务方法的实操对照；变异：删一条边 / 清空 diff → 降级提示而非静默空集；合规正例存在 |
| **审批** | **L3**（CI 步骤） |

### T5 跨会话交接（阶段 B）

| 项 | 内容 |
|---|---|
| **既有资产** | `openspec/changes/<change>/` 已有 proposal / design / tasks / specs；部分 change 另有 **`verify.md`**（承担验证记录）、`.comet.yaml`（`phase` / `verifyResult` / `handoff_hash`）、`.comet/handoff/`、`.comet/state-events.jsonl` |
| **v1 错在哪** | 新增 `VALIDATION.md` 与 `verify.md` 撞车；「3 件套 / 六件套」数字互斥；仓库**没有** `tasks.md` 模板文件（`openspec/config.yaml` 仅 4 行），「改模板」实为改 CLI 内置模板，团队不同步 |
| **改动** | ① **复用/扩展 `verify.md`** 与 Comet handoff，不新增 `VALIDATION.md`；② 仅补 `PROGRESS.md`（已完成/未完成/阻塞）与 `DECISIONS.md`（决策 + 原因 + 关联 ADR 编号），且**只对新 change 生效、存量归档不动**；③ 明确产物计数为「4 件套 + 复用 `verify.md` + Comet handoff + 新增 2 份」；④ 模板真源须指明（哪个 skill / CLI 生成），否则改为逐 change 落地 |
| **验收** | 新建一个 change 时新产物齐全；新会话仅凭 change 目录可恢复进度（实操）；存量归档零改动 |
| **审批** | 待架构师定级（流程资产，非 L1 单测 / DTO） |

### T6 质量度量口径（阶段 C）

> v3 业界实践增补见「十一、11.2③」。

| 项 | 内容 |
|---|---|
| **证据** | `analyze-metrics.sh` 只有 2 类指标 + 2 处 `TODO`；`.claude/state/` 为空且被 `.gitignore:205` 忽略；产物 `reports/metrics.md` 落在 `.gitignore:242` 有意忽略的运行时产物目录 |
| **v1 错在哪** | 「可复算 / 样本可追溯 / 不新建采集平台」与现有数据源**不能同时成立** |
| **改动（收窄）** | 本期只出**口径文档 + 手工登记模板**（设计一次通过率 / AI 缺陷逃逸率 / 人工返工率），放在 `doc/architecture/` 顶层并登记进 README 规范索引；写明「复算需本机 checkout + 本机状态快照」；先盘点可用数据（Comet state、PR 勾选项、熔断计数），再决定是否补最小采集字段 |
| **验收** | 口径无歧义、模板可填写、数据来源逐项注明；**不承诺自动可复算** |
| **审批** | 待架构师定级（新增文档） |

### T7 路径级自动规则（**前置议题，阶段 C 可选**）

> v3 业界实践增补见「十一、11.2④」。

| 项 | 内容 |
|---|---|
| **真源更正** | 路径路由表在 **`06 §12.2`**（不是 `doc/agents/README.md`）；README `:32` 明写主干表「此处不重复」 |
| **与既有裁定冲突** | `06 §12.6:142` 禁「把拆分文档内容抄回入口文件」；`.claude/rules/bone-*.md` 与 `.github/instructions/*.instructions.md` 本身即入口/指令文件；且 `.claude/rules/`、`.github/skills/` 被有意忽略 |
| **本期动作** | 与 T3 **合并为同一策略议题**。若架构师批准改变「不入库」策略：生成物**仅含 paths 指针、不含规范正文**，与 T3 生成物目录不重叠，且**不接 CI、不新增 `check-*.py`**；否则不做 |
| **审批** | **L3（`.gitignore` / `.github`）+ 待架构师定级** |

### T8 ADR 提示（并入 T4，不单独立项）

按领域 / 关键词命中 ADR；范围须包含**非编号附录** `ADR-生产数据库增量迁移策略.md`，排除 `0000-template.md` 与 `README.md`（编号 `0001–0046` 共 46 篇 + 附录 1 篇）。纯提示不阻断。

### T9 就绪度复核工具的定位裁定（阶段 A）

| 项 | 内容 |
|---|---|
| **事实（第二轮审查更正）** | `_ai_native_readiness.py` 的 `--self-test` 已由 `check_all.py:75/107` 登记为 `EXTRA` / `EXPECTED_CALLS`，经 `.github/workflows/synap-claim-docs.yml:44,46` 两个 `(blocking)` step 间接执行；**主报告不阻断**是 `check_all.py:82` 的 `EXCLUDED` 既有裁定（「BLOCKED 是业务事实不是构建失败」）。其 `_` 前缀不匹配 `rglob("check-*.py")`，故不进 LIV-R4 监管。v2 初稿误写成「零调用孤儿」，已更正 |
| **真源** | 该资产属 **synapClaim 治理族**，定位真源是 `check_all.py:82` 与 `doc/ai-product/synapClaim/00-governance/01-文档治理规约.md §五/§六`；**不是** `Bone-Comet工具链基线与升级运维.md`（跨资产族错置） |
| **改动（二选一，待拍板）** | ① **维持现状**：沿用既有登记，不新增任何文档表述；如需澄清只改 synapClaim 治理规约 §五。② **仅当决定让主报告参与阻断时**：另立接线并按第六章全套判据（`--check` 语义 + LIV-R8 + 单测 + 负向探针 + 合规正例），同时调整 `EXCLUDED` |
| **验收** | 工具定位在文档中有唯一表述且与 `check_all.py` 一致；选 ② 时须另立 `check-*.py` 入口或调整发现规则，使主报告接线可被 LIV-R 族验证 |
| **审批** | 选 ② = **L3**；选 ① 不动文件 |

---

## 五、不改造清单与理由

| 不采纳项 | 理由 |
|---|---|
| SCIP / Tree-sitter 全库索引、Neo4j、Sourcegraph、独立检索服务层 | 58 万行 + 已有 SQLite 图谱；瓶颈是未解析引用质量，不是存储 |
| 新建「代码智能能力边界登记」文档 | D3 / §六 已有裁定，新建＝第二真源且静态快照必然过期 |
| Sync-Docs Agent 自动起草设计类文档 PR | `§12` 属 L4；且方案自己强调不能按代码反推设计文档。派生文档走 `generate --check` 确定性生成 |
| 自动缩小回归测试集 | 与「误报比漏报更致命」「全绿 ≠ 有判别力」及高风险模块需扩大回归冲突 |
| 对不入库资产新增 CI 判据（v1 的 R3 / R7 形态） | `Bone-Comet工具链基线与升级运维.md:181` 已裁定「本机生成、不入库」；干净检出上必然恒红或恒绿 |
| 新增 `VALIDATION.md` / 第二套交接目录 | 与既有 `verify.md`、Comet handoff 重复 |
| 覆盖或重写 `AGENTS.md` / 嵌套大部头 AGENTS.md | `06 §12.6`：外部通用规范先吸收进手册，入口保持薄引用 |
| 把 CodeGraph 升格为团队共享服务 | 用户级注册、本机可选；跨机器一致性无需求证据，且扩大运维面 |
| 改 `gate-state.json` 的历史快照编号 | `_comment` 明写 `[N/M]` 是门禁建立时快照，刻意不改 |

---

## 六、门禁接线与验收总纪律（v2 修订）

**新增 `scripts/check-*.py` 必被活入口调用**，判据族以 `scripts/check-gate-liveness.py` 为准：

| 判据 | 要求 |
|---|---|
| `LIV-R1` | CI 触发器（push / pull_request）必须包含当前分支 |
| `LIV-R2` | `backend-quality` 中 name 以 `(blocking)` 结尾的步骤数 ≥ `MIN_BLOCKING=15` |
| `LIV-R3` | 每个 step 必须有 `run:` 或 `uses:`（防空壳步骤） |
| `LIV-R4` / `R4e` | 每个 `check-*.py` 出现在活入口**调用行**上；派生入口自身必须被根入口调用 |
| `LIV-R4b/c/d` | 豁免只可收缩（脚本已接线/已删除仍留条目即判红）；豁免理由 ≥20 字符、owner 非空；入口读不到判红 |
| `LIV-R6` | `partial_wire` 的 `kind` ∈ `ci_only` / `known_incomplete`；**已登记的不判红**，未登记的 `unaccounted_unwired` 在 CI `--strict`（`ci.yml:115`）下判红；未进 pre-commit 的门禁单列提示 |
| `LIV-R7` | 脚本声明 `--self-test`，活入口必须**带该参数**调用（只认 argparse / sys.argv 声明，注释不算） |
| `LIV-R8` | 脚本声明 `--check`，至少一处活入口以**判据模式**调用（防「标着 blocking 却从不拦」） |

**接线细则（修正 v1）**：

1. **沿用既有 G0 范式**：`check.sh:857–861` 明确「不带 `[x/59]` 编号，避免改动计数与逐项标签」。新增判据**优先不带编号**；若必须带，需全局替换**全部 `/59` 出现处**（`grep -c` 命中 84 行 / `grep -o` 字面出现 92 处，其中纯数字步号 59 个、字母扩展步号 4 个）`→ /60` 并同步 `.comet/config.yaml` 注释，**`gate-state.json` 的历史快照不动**。
2. **report-only 与阻断互斥**：report-only 步骤不写 `(blocking)`、不声明 `--check`、登记 `partial_wire kind=ci_only`；阻断步骤才写 `(blocking)` 并满足 LIV-R8。二者不得在同一阶段混写。
3. **allowlist 口径**：两类 `unwired`（零接线）与 `partial_wire`（已接 CI 未进 pre-commit，必填 `kind`）；`reason ≥20` + `owner` 是**判据强制**，`unblock` 是仓库约定、判据**不校验**。撤接线触发 `LIV-R4`（孤儿），撤脚本留条目触发 `LIV-R4b`（豁免失效），两者不同。
4. **判据本身**：`--self-test` 负向探针（LIV-R7 带参）+ `scripts/tests/test_check_*.py` 单测 + 变异验证 + 合规正例（防「永远拒绝」）+ 真源不可读 fail-closed。用例数按 `python3 -m unittest discover -s scripts/tests` 当前输出为准，**不写无法复核的历史数字**。
5. **门禁状态**：`python3 scripts/check-ddd-gate-state.py generate --check`（不是 `gate-state.json generate --check`）；不得手改文档里的表。
6. **先正向审计再上线**：新判据先对全仓跑一遍（历史上锚定串首 / 正则只取首个匹配都造成过假红或静默漏扫）；**误报会逼作者关掉门禁，比漏报更致命**。
7. **审批级别**（按 `06 §12.3` / `AGENTS.md:31`）：L1＝单测 / DTO / OpenAPI；L2＝业务逻辑需双人 Review；L3＝CI 脚本、删码、依赖；L4＝`§12` 内容、**含 `doc/agents/README.md`（`:60` 明确属 §12.3）**、`AGENTS.md` 协作条款。配置 / 新增文档等未覆盖形态一律写「**待架构师定级**」，不自行套 L1/L2。所有 L3/L4 项本轮只出建议稿。

---

## 七、分期路线（阶段 A/B/C，含前置）

| 阶段 | 改造项 | 前置 | 交付物 | 验收 | 审批 |
|---|---|---|---|---|---|
| **A** | T1（§12.8 建议稿 + README:57 编号同步）、T2（路由指针 + D3 补句）、T9（就绪度工具定位裁定） | T1 需先核实 `max_parallel_agents` 消费方；T9 需裁定主报告是否参与阻断 | 条款建议稿、两处指针、编号同步、工具定位决议 | 并行实操 + 人工 `grep -c` 指针检查 + 文档门禁 | L4 建议稿 / 其余待定级 |
| **B** | T4（影响面提示）、T5（复用 verify.md 的交接） | T4 依赖全仓正向审计与消费方指定；T5 依赖模板真源定位 | report-only 判据 + 2 份新交接产物（仅新 change） | 正向审计报告 + 实操对照 + 存量归档零改动 | L3 / 待定级 |
| **C** | T6（口径模板）、T8（并入 T4） | T6 依赖数据源盘点；**T8 依赖 T4 report-only 判据上线** | 口径文档 + 手工登记模板 + README 规范索引登记 | 口径无歧义、数据来源逐项注明、README 索引可查 | 待定级 |
| **议题** | **T3 / T7** | **架构师先裁定是否改变「不入库」策略** | 策略决议 | 未裁定前**不施工** | L3 + 待定级 |

> v1 把最贵的 T3 放阶段 A 且未标注它依赖一个尚未裁定的策略，是排序错误；v2 把未决项移出施工清单。

| 维度 | 现状 | 目标（不再写无依据的数字） |
|---|---|---|
| 并行边界 | 实操散落、既有脚本会建分支 | `§12.8` 统一条款 + 与 `AGENTS.md:32` 分支纪律对齐 |
| 图谱加载 | D3 已裁定「可选、不入门禁」 | 加载路由 2 处指针 + 降级路径；**不新建边界文档** |
| 影响分析 | 有人工条款（`§11.13 ④` / PR 模板） | 一个 report-only 影响面提示判据 + 高风险扩大回归提示 |
| 交接产物 | 4 件套 + `verify.md` + Comet handoff | 新增 2 份（PROGRESS / DECISIONS），复用 `verify.md` |
| 质量指标 | 2 类过程指标、数据源为空 | 口径 + 手工模板，不承诺自动复算 |
| 技能与规则 | 有意不入库、三套并存 | 策略裁定后本机可选收敛；本期不改 CI |

---

## 八、风险与回滚

| 风险 | 缓解 |
|---|---|
| **新判据误报**（Bone 首要风险） | **仅 T4** 先 report-only + 全仓正向审计；T3/T7 本期不接 CI。保留合规正例与负向探针 |
| 「标着 blocking 却从不拦」假绿 | report-only 与阻断互斥接线（第六章 2）；按 LIV-R8 校验 |
| 自检耗时膨胀（约 422 秒 / 59 步，余量约 478s） | T4 读 324 MB SQLite 判为 **CI-only**；快判据才进 pre-commit；allowlist 用 `partial_wire kind=ci_only` |
| 熔断计数跨 worktree 共享导致误熔断 | `§12.8` 明示共享语义与并行处置（`check.sh:10–14`） |
| 图谱读成功但结论空 | fail-closed 覆盖「读失败**或空集**」；「扩大回归」先指定消费方，否则降级为纯提示 |
| 第二真源 | D3 裁定、`verify.md`、Comet handoff 一律复用；新增文档须登记进 `doc/architecture/README.md` 规范索引 |
| 触碰 L4 越权 | `§12.8` / `§12.2` / `doc/agents/README.md` 仅出建议稿，由架构师本人执行 |
| 整体回滚 | 每项均为单条款 / 单指针 / 单判据；判据回滚须同时撤 `check.sh` + `ci.yml` + allowlist 条目（撤接线＝LIV-R4，留条目＝LIV-R4b） |

---

## 九、单一真源口径冲突（新增，待裁定）

| 文档 | 声明 |
|---|---|
| `doc/architecture/gate-state.json:_comment` | 「门禁状态唯一真源…G-1.1 / G-1.5 / G-1.7 状态表由本文件经 `check-ddd-gate-state.py generate` 渲染」 |
| `doc/agents/README.md:59` | 「门禁与 HC 状态的唯一真源是 `Bone-DDD-最终实践方案.md §G-1.7`」 |
| `AGENTS.md:38` | 「HC-001～HC-008 的定义与实测状态见 §G-1.7…本文件、`doc/agents/`、`doc/wiki/` 一律不复制 HC 表或门禁状态」 |

**辨析**：机器状态真源是 `gate-state.json`（CI 消费），HC 条款定义与渲染后的状态表在 §G-1.7，二者是「源 → 渲染」关系。但把「实测状态唯一真源」指向 §G-1.7 的不止 `doc/agents/README.md:59`：**`06 §12.1:62–68`**（定义 / 判定方式 / 实测状态只有一处真源）、**`AGENTS.md:38`**（HC 定义与实测状态见 §G-1.7）、**`Bone-DDD-最终实践方案.md §G-3 第 7 条`（:2219，以 G-1.7 实测为准）** 同样如此；DDD 文档多处另有「本表由 gate-state.json 自动生成」提示。另 `gate-state.json:_comment` 自述「当前共 37 步」与 `check.sh` 实测 59 步不符，属**刻意保留的过期历史注释**，不是待修冲突。

**建议**：以 `gate-state.json` 为机器真源，同步收敛上述四处措辞为「源 → 渲染」口径（均属 L3/L4 待办，本轮不动）；这是审计发现的既有文档漂移，不是本次改造引入。

---

## 十、未动 / 待你拍板（真正未决项）

1. **`§12.8` 属 L4**：需你本人执行或明确授权后，我才能改 `doc/agents/06`；本轮只写建议内容。
2. **T3 / T7 的前置策略**：是否改变「多平台 skills/rules/hooks 本机生成、不入库」的既有裁定？未裁定前不施工。
3. **技能唯一真源侧别**（仅当第 2 项批准）：`.claude/skills` / `.codebuddy/skills` / `.github/skills` 三套取哪一侧。
4. **T4 是否最终转阻断**：建议先 report-only 两轮，观察空集率与噪声率；转阻断需另立互斥接线。
5. **`max_parallel_agents` 字段消费方核实结果**：确认后再定上限；v2 不写无依据的数字。
6. **单一真源口径**（第九章）：以 `gate-state.json` 为机器真源，并同步收敛 `doc/agents/README.md:59` / `06 §12.1` / `AGENTS.md:38` / DDD §G-3 措辞（L3/L4 待办），还是维持现状？
7. **T9 二选一**：维持现状（主报告不阻断、`--self-test` 已由 `check_all.py` 间接接线），还是让**主报告参与阻断**（另立接线、按第六章全套判据并调整 `EXCLUDED`）？
8. **`.gitignore:242 reports/` 精确例外**：**已于归档前落地**——加 `!doc/architecture/reports/` 与 `!docs/superpowers/reports/` 两条精确例外（见 11.5③），运行时产物规则保持不变；本文仍放 `doc/architecture/` 顶层。**仍待裁定**：例外生效后可见的 7 份历史验证报告是否批量补登入库。
9. **T5 模板真源**：`scope_paths` / PROGRESS / DECISIONS 由哪个 skill 或 CLI 生成？未定位前只能逐 change 落地。
10. **Comet 状态异常**：只读探针曾报 `classic-state-invalid`；并行会话已在提交 `5b140a722` 把 `synapclaim-ai-governance-hardening` 绑定至 `dev` 并设 `isolation=current`。该改动**非本文作者所为**，是否需复核其状态一致性？
11. **交付格式**：是否需要本文的 HTML / PPT 版本，或拆成「审批单 + 施工单」两份？

---

## 十一、业界实践对照复核与优化（v3）

> 本章为 2026-11-10 第四轮**增量**复核，经 `/comet` tweak change `ai-native-platform-plan-review` 落地。
> 与第三章三轮独立审查的分工：本章裁判是**业界成熟实践**，只做「方向对、但缺可复算落点」的增量优化；
> 不重跑同规模独立审查，不改变任何已登记裁定。

### 11.1 复核判定（9 项改造 + 不改造清单）

| 对象 | 判定 | 业界依据 | 结论 |
|---|---|---|---|
| 总体方向：瓶颈在工程基础设施、不建平台、只做收敛改造 | **合理** | OpenAI Harness（仓库知识地图 + 自动化维护）、Anthropic（大库 Context Engineering） | 保留；规模前提已校正为 578,479 行 |
| T1 并行实施边界 | **合理** | 有边界的并行 / 模块隔离 / 独立 Worktree | 保留；删除无依据的并行数字是正确修正 |
| T2 图谱纳入路由（复用 D3 裁定） | **合理，可优化** | Anthropic「Agentic Search / LSP 优于纯 RAG」 | 保留；补上下文质量评测集骨架（11.2①） |
| T3 技能一致性（前置议题） | **合理** | GitHub 分层指令单一真源 | 保留「未裁定前不施工」 |
| T4 影响面提示 | **合理，可优化** | 静态依赖 + 运行时 Trace 融合；影响分析须标注盲区 | 保留 report-only；补运行时证据字段（11.2②） |
| T5 交接（复用 `verify.md`） | **合理** | SDD / 跨会话持久化产物 | 保留 |
| T6 度量口径 | **合理，可优化** | SPACE / DORA「质量调整后的研发效率」 | 保留；补最小可复算数据源（11.2③） |
| T7 路径级规则（前置议题） | **合理，可优化** | GitHub `.github/instructions` 路径级规则 | 保留策略裁定；拆出零成本的表内增列（11.2④） |
| T8 ADR 提示并入 T4 | **合理** | ADR 为设计决策锚点 | 保留 |
| T9 就绪度工具定位裁定 | **合理** | 工具定位单一真源、避免跨资产族错置 | 保留 |
| 第五章不改造清单 | **合理** | 「1M Token Wall」「结构化索引 + RAG」「设计先行」 | 全部保留 |
| 第六章 LIV-R 全族 + 互斥接线 | **合理** | 「AI Rule 引导、确定性检查裁决」 | 保留；补可机器复算的验收清单（11.2⑤） |

**本次复核新增的不采纳项**：把「上下文检索准确率」直接写成已采集指标；把 skeleton 评测集接进 CI 阻断；按外部平台模板整体重写本方案。三者都会制造「配了不生效」或第二真源。

### 11.2 五项优化的具体落点（均为文档层，不新增工程资产）

| # | 落点 | 具体增补 | 约束 / 审批 |
|---|---|---|---|
| ① | 对 **T2 / T6** 的增补 | 引用 `scripts/check-eval-baseline.py` 的 E1–E5 判据骨架，登记「代码 / 文档检索命中率」评测集的 **skeleton 态**：只定义冻结集结构、哈希与切片维度单一真源，**不写任何实测数值**、不宣称已采集 | 复用既有门禁范式；**不新增 `check-*.py`、不接 CI**；真正落地需 L3 |
| ② | 对 **T4** 的增补 | 影响面报告增加「运行时证据」字段：Trace 覆盖 / 事件 Topic 订阅 / 配置驱动依赖，引用 `doc/architecture/Bone-可观测性规范.md` §3（Trace）、§4（Metrics）、§5（SLI/SLO）与 `Bone-消息与事件规范.md`；无证据时标注「待补」并在 PR 描述中人工确认（**仅设计意图：不新增勾选项、不改 PR 模板，落地需 L3**；与 11.2③ 的模板勾选项建议区分） | 静态盲区显式化；**不缩减回归**，高风险仍扩大 |
| ③ | 对 **T6** 的增补 | 在 `.github/PULL_REQUEST_TEMPLATE.md` 已有「若含 AI 辅助产出：已自行审核」勾选项旁，登记 2 个**可选**勾选项建议（AI 产出类型 / 是否发生人工返工），作为「AI 缺陷逃逸率 / 人工返工率」的最小可复算数据源 | **本次只登记建议，不改 PR 模板**；改模板属 L3，需另行审批 |
| ④ | 对 **T7** 的增补 | 把「在 `doc/agents/06 §12.2` 路由表增列『必查约束』一列」从整体前置议题中拆出，作为 L4 文档内动作登记；**不生成 `.github/instructions` / `.claude/rules`**，不改变既有「不入库」裁定 | 仍属 L4，只出建议稿；与 T3 生成物不重叠 |
| ⑤ | 对 **六 / 七章** 的增补 | 汇总「一次性验收命令清单」（11.3），把分散的人工验收收敛为可复算命令 | 不新增判据、不接 CI |

### 11.3 一次性验收命令清单（复用现有只读脚本）

| 检查 | 命令 | 实测（v3，2026-11-10，worktree 内） |
|---|---|---|
| 文档符号漂移 | `python3 scripts/check-doc-code-symbols.py` | **RC=0**（无未登记类名漂移） |
| DDD 文档防漂移 | `python3 scripts/check-ddd-doc-drift.py` | **RC=0** |
| 门禁状态检查 | `python3 scripts/check-ddd-gate-state.py` | **RC=0**（worktree 与主树均 OK） |
| 门禁状态表生成一致性 | `python3 scripts/check-ddd-gate-state.py generate --check` | **v3 实测 RC=1（范围外既有问题）**；归档前已定位为「`gate-state.json` 真源落后」并在主工作树追平转绿，见 11.5④。本 change 未用 `generate` 倒灌、未改该 JSON |
| 规格校验 | `comet classic openspec -- validate ai-native-platform-plan-review --json` | **RC=0 / `valid: true`**（`skip_specs` 生效，零 delta 被接受） |
| 路由指针检查 | `grep -c` 检查 `doc/agents/README.md` 与 `06 §12.2` 指定指针 | 不适用：本 change 未落地 T2 指针（仍为建议稿） |

> 上述命令均为**只读**：不新增 `check-*.py`、不接 CI、不改变任何门禁状态；结果如实记录 RC，未以「应该通过」代替实跑。
> 唯一 RC=1 项是**发现的既有问题**：按 Dirty Worktree / 范围规则保留现场并在此登记；归档前于主工作树以「真源追平」单独修复（11.5④），未纳入本 change 的提交范围。
> **两树结果差异说明**：本表实测于独立 worktree，其基线未同步主树的 `gate-state.json` 追平，故第 4 行仍 RC=1；主树修复后同命令 RC=0（11.5④）。这是基线差异，不是结论矛盾。

### 11.4 边界声明

本次优化**没有**改变：T3 / T7 的既有「不入库」裁定、L4 审批边界、第六章门禁接线纪律、第五章不改造清单、第九章单一真源辨析、第十章待拍板清单。所有涉及 L3 / L4 的内容仍只是方案内的「建议 + 审批级别」，本 change 未落地任何工程改动；归档前在主工作树另行完成的既有缺陷修复不属本 change 提交范围（见 11.5）。

### 11.5 归档前收口记录（2026-11-10）

> 本节登记 Verify 通过后、归档提交前完成的收口动作。**①② 属本文内部自洽修订；③④ 发生在主工作树、⑤ 为环境处置，均不属本 change 的工程改动**。

| # | 收口项 | 处置与证据 | 归属 |
|---|---|---|---|
| ① | 版本标识自洽（Verify 建议 2，High） | H1 / 状态行 / 入库事实行由 v2 bump 至 **v3**；第 1–10 章编号与正文不变；「v2 修订」等历史陈述按语境保留，描述改造规模的措辞去版本化 | 本文 |
| ② | 11.2② 边界措辞（Verify 建议 3，Low） | 明确「PR 人工确认」只是**设计意图**：不新增勾选项、不改 PR 模板、落地需 L3，与 11.2③ 的模板勾选项建议区分 | 本文 |
| ③ | `.gitignore:242 reports/` 误伤（第十章第 8 条） | 在 `reports/` 后加 `!doc/architecture/reports/` 与 `!docs/superpowers/reports/` 两条**精确例外**；实测两目录不再被忽略，`.workbuddy/reports/`（`:222 .workbuddy/`）与 `target/**/reports/`（`:21 target/`）仍按原规则忽略 | 主树独立修复，已 staged |
| ④ | `generate --check` RC=1 根因（11.3 第 4 行） | **不是文档漂移，而是 `gate-state.json` 真源落后**：JSON 的 17/18 行仍写「存量 96 条」「四条判定 C1–C4」，而实现与文档已是「存量 1 条」「六条判定 C1–C6」，`check.sh:809/839` 也确有 `[52/59]` / `[57/59]` 接线。**机械跑 `generate` 会用过时真源倒灌，抹掉 C5/C6 与 pre-commit 接线**。正确修法是从文档提取精确列值追平 JSON（3 处文本），实测 `generate --check` RC=0、普通检查 / 两个文档门禁均 RC=0 | 主树独立修复，已 staged |
| ⑤ | 主树 pre-commit 的 worktree 副本误扫（假红） | `check-domain-model-layout.py` 用 `rglob("src/main/java")` 扫文件系统、不尊重 `.gitignore`，把 `.worktrees/<name>/` 副本一并扫入，C4 因副本路径不在基线而判红；与本 change 的文档改动无关。处置：成果同步主树后移除该 worktree，并在 `.gitignore` 加 `.worktrees/` 防御规则；单独复验该判据 RC=0。**后续建议**：给判据的 `SKIP_PATH_PARTS` 增加 `.worktrees`（L3 待办，本轮未改判据） | 环境处置 |

**③ 的附带发现（待裁定）**：例外生效后，`docs/superpowers/reports/` 下 **7 份**历史验证报告与 `doc/architecture/reports/README.md`（共 8 个未跟踪文件）由「被忽略」变为「未跟踪可见」，但**未批量入库**——它们属他人历史产物，且既有归档 change 的 `verification_report` 字段指向该目录，是否补登需单独裁定。

**④ 的纪律意义**：与第二章「同一语义两处配置源 = 配了不生效」、第九章「单一真源口径」同族——`--check` 报红时应先判断**哪一侧落后**，而不是默认重渲染；真源落后时重渲染等于用旧事实覆盖新事实。

---

## 11.6 深度优化落点（2026-11-11 深化，承接 11.2 skeleton）

> 本节把 11.1 判定表中标 **合理，可优化** 的四项（T2 / T4 / T6 / T7）与标 **合理** 的第六章 LIV-R 门禁族，从 11.2 的 skeleton 级「设计意图」深化为「现状 → 业界标杆 → Bone 可优化落点」三栏。
> **纪律不变**：纯文档层、L4 建议稿边界；不落地任何工程改动、不新增 `check-*.py`、不接 CI、不编造实测值；业界标杆列均附可引用出处，便于架构师按来源核验。

### 11.6.1 T2 图谱纳入加载路由

| 维度 | 内容 |
|---|---|
| **现状** | CodeGraph 已建（324 MB SQLite，105,067 节点 / 151,819 边 / 4,830 文件，daemon 增量同步至 2026-11-07）；注册在用户级（`~/.cursor/mcp.json`、`~/.claude/settings.json`），被根 `.gitignore:224` 排除——本机可选、非团队共享。D3（`Bone-Comet工具链基线与升级运维.md:163–170`）已裁定「可选能力、不纳入项目门禁」。缺口：加载路由只有 D3 一处指针，Agent 没有统一的「何时用 / 不可用如何降级 / 能力边界」入口；图谱 `unresolved_refs` 达 297,588（静态盲区大），`calls` 仅 28,476、无 test→code 边 → 符号定位可用、静态调用链不完备。 |
| **业界标杆** | ① Anthropic《Effective context engineering / Building agents》：**agentic search（grep/glob/LSP 即时探索）优于纯 RAG/embedding 预检索**——语义检索「更快但更不准确、更难维护、更不透明」，建议「先 agentic search，只在需要更快时才加语义」；文件树/命名/时间戳本身即上下文信号，支持渐进式披露。② Claude Code 2.0.74（2025-12）原生 LSP 支持：LSP（go-to-definition / find-references / hover / diagnostics，按 `file:line:column`）与 agentic search 互补——「search 找到位置，LSP 把位置变成精确结构事实」。③ OpenAI Harness：给 Agent 与程序员同样的工具（find files / edit / lint / run / debug），依赖即时检索 + 工具探索而非预建索引。④ 收敛结论：业界最优是 **hybrid**——预取少量关键上下文提速 + Agent 自主探索按需取；且 **search/LSP 互补、静态盲区必须显式化**。 |
| **Bone 可优化落点（L4 建议稿）** | 1. 在 `06 §12.2` 路由表 / `doc/agents/README.md` 横切触发表补**统一入口三要素**：① 何时用——定位「符号定义/调用方/引用」且 MCP 可用时优先 CodeGraph；② 降级 SOP——MCP 不可用 → 调 LSP（`ENABLE_LSP_TOOLS`）→ 退化到 `rg`/`grep -rn` 结构化检索，**结论无论哪条路径都必须含「文件路径 + 行号」**，不得只回「可能在 X 模块」；③ 能力边界——CodeGraph 为**本机可选、非团队共享**，`unresolved_refs` 量级大 ⇒ **不得据图谱断言「无调用方 / 无影响」**。2. 路由表条目草案：`符号/调用链定位 → 首选 CodeGraph MCP｜降级 LSP → rg｜边界 本机可选，不断言无调用方`。3. 不新建边界文档（与 D3 单一真源一致），只在既有表「加指针 + 降级 SOP」，不复制 D3 正文。4. 与 11.2① 衔接：T2 的「检索命中率评测集」仍保持 skeleton（只定义冻结集结构/哈希/切片维度单一真源），落地需 L3，本 change 不改 PR 模板、不接 CI。 |

### 11.6.2 T4 变更影响面提示

| 维度 | 内容 |
|---|---|
| **现状** | 人工条款（06 §11.13④、PR 模板:26「受影响测试已实际执行」）存在，缺自动化推导。图谱现实：edges 无 test→code 边；`contains` 占 65.6%、`calls` 指向测试路径仅 6.6%；`unresolved_refs` 独立表不被遍历 ⇒ 不能承诺「关联测试 / 建议回归集」。计划：report-only；输入 `git diff -U0` + hunk 行号映射；输出变更符号/直接调用者/静态盲区/相关 ADR + 「不构成测试依据」声明；三条硬约束（不阻断、高风险扩大、fail-closed）。 |
| **业界标杆** | ① 变更影响分析（IA）研究共识（Arnold 1996；Li 2018；Chen 2015）：IA 分**静态 / 动态 / 混合**三类，**静态集合偏大（过报）、动态集合偏小（欠报）**，两者**交集置信最高**，静态独有部分需人工/动态数据收敛；实践顺序「先静态闭包 → 接 CI 覆盖（测试关联）→ 最后生产追踪补盲区」。② 不确定性影响分析（概率性 IA）：现实影响传播非确定（反射/DI/运行时条件/数据依赖），应**量化不确定性（置信度/概率）**，而非二元「是/否受影响」。③ OpenAI/Anthropic 共识：影响面必须**标注盲区（uncertainty calibration）**；「never output empty set」——读失败或结论为空都应 fail-closed（扩大回归），绝不输出空集。④ 与「误报比漏报更致命」同构：宁可过报（多看文件）不可漏报。 |
| **Bone 可优化落点（L3 建议稿，report-only）** | 1. 影响面报告**字段草案**（深化 11.2② 的「运行时证据字段」到字段级）：`变更符号`（必出，`git diff -U0`+hunk 映射）｜`直接调用者`（CodeGraph `calls`/`references`，标注覆盖率）｜`静态盲区清单`（必出，显式化 `unresolved_refs` 与非 test→code 边）｜`相关 ADR`（含非编号附录，必出）｜`运行时证据`（Trace 覆盖 / 事件 Topic 订阅 / 配置驱动依赖，引用可观测性规范 §3/§4/§5 + 消息与事件规范；**无证据填「待补」，不编造**）｜`受影响测试提示`（仅提示，消费方=PR 勾选项/E2E 清单，须先指定）｜`不确定性标注`（对每个变更符号给高/中/低置信 + 理由，呼应概率性 IA）。2. fail-closed 措辞草案：「影响面未知（图谱读失败/空集）→ 扩大回归至 E2E 全量」；高风险路径（claim/额度/权限/DDL/迁移/事件）固定附加「E2E 全量」提示。3. 与 11.2② 衔接：「PR 人工确认」仅为设计意图，**不改 PR 模板、不新增勾选项**，落地需 L3。4. 前置不变：先做全仓正向审计量化空集率/噪声率，report-only 两轮后再谈转阻断（第十章第 4 条）。 |

### 11.6.3 T6 质量度量口径

| 维度 | 内容 |
|---|---|
| **现状** | `analyze-metrics.sh` 仅 2 类指标（功能交付周期含 Ralph Loop 子列 + 自愈统计）+ 2 处 TODO；`.claude/state/` 空且被忽略；`reports/metrics.md` 落在被忽略的运行时产物目录。v1「可复算/样本可追溯/不新建采集平台」与现有数据源不能同时成立。计划：本期只出口径文档 + 手工登记模板（设计一次通过率/AI 缺陷逃逸率/人工返工率），写明「复算需本机 checkout + 本机状态快照」，先盘点可用数据。 |
| **业界标杆** | ① **DORA（Accelerate / Google Cloud DORA）**：交付效能四指标——部署频率、变更前置时间、变更失败率、服务恢复时间；AI 是「放大器」：提吞吐也可能提不稳定，「若 AI 全上但 DORA 持平，说明工具未产生杠杆」。② **SPACE（Forsgren et al. 2021，Microsoft Research/GitHub/UVic）**：生产力非单一数字，至少五维——Satisfaction / Performance / Activity / Communication / Efficiency；警告「满意度说明 AI 被认可，但不证明 AI 有效」。③ **AI 辅助研发度量陷阱（2026 共识）**：接受率（GitHub/Cursor 30–40%）是**虚荣指标**（接受≠杠杆，可能接受错误再花 15 分钟修）；原始产出量（LOC/PR 数）在 AI 时代「零效用」甚至反向；应测**逃逸缺陷率（按 AI 辅助/人工分段）**、**AI 辅助 vs 非 AI PR 的 time-to-merge**、**开发者满意度（需 6 个月窗口，首月负向是变更疲劳）**；Goodhart 律——单维看板必然说谎，须团队级而非个人排名（避免博弈）。 |
| **Bone 可优化落点（待定级建议稿）** | 1. **口径模板结构**（深化 11.2③，落到字段级）：`设计一次通过率`＝首次 PR 无需返工比例（源 Comet state + PR 勾选项；反模式 不用 LOC 代理）｜`AI 缺陷逃逸率`＝30 天内线上 bug 中 AI 辅助 PR 段占比（源 PR 勾选项 + 缺陷来源标签；反模式 不用接受率代理）｜`人工返工率`＝AI 产出经人工返工比例（源 PR 勾选项「是否发生人工返工」）｜`DORA 四指标`（并列，不替代）。2. **数据来源清单**（最小可复算、不新建平台）：Comet state（change 进度/审批）、PR 模板勾选项（11.2③ 建议的 2 个可选勾选）、熔断计数（`FAIL_COUNT_FILE`）；先盘点这三类是否够用，再决定是否补最小采集字段。3. **纪律**：① 团队级视角，禁止个人排名；② 满意度类给 6 个月窗口；③ 接受率/LOC 列为「明确不采用」反模式，写入口径文档而非仅口头。4. 与 11.2③ 衔接：2 个 PR 勾选项仍为「建议稿」，**不改 PR 模板**，改模板属 L3 需另行审批。 |

### 11.6.4 T7 路径级自动规则

| 维度 | 内容 |
|---|---|
| **现状** | 路由表真源在 `06 §12.2`（非 README；README:32 明写主干表「此处不重复」）。与既有裁定冲突：`06 §12.6:142` 禁「把拆分文档内容抄回入口文件」；`.claude/rules`、`.github/skills` 被有意忽略；`.claude/rules/bone-*.md` 与 `.github/instructions/*.instructions.md` 本身是入口/指令文件。计划：与 T3 合并为同一策略议题；若批准改变「不入库」策略，生成物仅含 paths 指针、不含规范正文、不接 CI；否则不做。 |
| **业界标杆** | ① **GitHub Copilot 路径级指令（path-specific custom instructions）**：`.github/instructions/**/*.instructions.md`，用 YAML frontmatter 的 `applyTo`（glob）匹配路径（例 `applyTo: "src/**/*.py"`）；仓库级 `copilot-instructions.md` + 路径级按需加载，**避免把仅适用部分文件的规范塞进全仓库指令**。② **Claude Code 规则**：`.claude/rules` 用 `paths`（glob 数组）而非 `applyTo` 激活；`AGENTS.md` 为跨 agent 格式，沿目录树「最近者优先」。③ **单一真源 / 分层指令**：多 harness 并存时选共享格式（如 AGENTS.md）并保持副本一致；指令须「简短、自包含」。④ 收敛结论：路径级规则的价值是**按文件类型/目录加载最小必要约束**，避免全仓库指令过载；但其前提是「本机/仓库内生成且被 harness 消费」，与 Bone 的「不入库」裁定存在张力。 |
| **Bone 可优化落点（L4 建议稿，拆出零成本动作）** | 1. 把 11.2④ 拆出的 **§12.2 路由表增列「必查约束」** 落到列草案（深化，给出具体列）：`**/domain/**` → 分层/包一致/tenantId（消费方 `check.sh`/ArchUnit）｜`**/infrastructure/**` → HC-001 禁 ORM / SQL 模板首行关键字（消费方 `check.sh`）｜`**/adapter/web/**` → ApiResponse 统一响应（消费方 `check.sh`）。说明：只列「指针 + 路径」，**不抄规范正文**（遵守 §12.6 不回抄）；「必查约束」本身由既有 `check.sh` 门禁承载，不是新增判据。2. 明确边界：本动作**只改 §12.2 表（L4 建议稿），不生成 `.github/instructions` / `.claude/rules` 文件**，不改变「不入库」裁定；与 T3 生成物目录不重叠。3. 与 T3 协同：若架构师最终批准改变「不入库」策略，T7 的 §12.2 增列已先就位，可与 T3 生成物（paths 指针）衔接；若维持现状，本动作独立有效（路由表本就是入库文档）。 |

### 11.6.5 第六章 LIV-R 门禁族（含互斥接线）

| 维度 | 内容 |
|---|---|
| **现状** | LIV-R1–R8 已定义（CI 触发器含分支 / 15 个 `(blocking)` 步 / 防空壳 / 调用行 / 豁免收缩 / `partial_wire kind` / `--self-test` 带参 / `--check` 判据模式）；report-only 与阻断互斥接线；`check.sh` 59 步、派生入口被根入口调用。11.2⑤ 已汇总「一次性验收命令清单」（只读脚本）。历史坑：「标着 blocking 却从不拦」假绿、变异 0 红先排除「没跑」、锚定串首/正则只取首个匹配造成假红/静默漏扫。 |
| **业界标杆** | ① **门禁判别力（discriminative power）**：业界共识「CI 全绿 ≠ 有判别力」。三类死法（dead / broken / **blind** gate）外表都是绿勾；**blind gate 最危险——它在跑、exit 0、但输入从未触发其逻辑**。② **变异验证（mutation testing）是唯一证明门禁「会拦」的方法**：覆盖率只说「代码跑过」，变异分说「门禁/测试真的在乎这行做了什么」；Stryker 等以 mutation score 阈值（如 70% break）进 CI。③ **负向探针（negative control）**：门禁必须同时证明「脏输入被拦」与「干净输入静默」；只测正向=假安全感。④ **gate liveness 本质**：LIV-R 族（尤其 R4 调用行、R7 带参、R8 判据模式）正是把「脚本存在 ≠ 门禁生效」变成可机器复算的判据——与业界「验证门禁真的会拦」同构。 |
| **Bone 可优化落点（L3/L4 建议稿）** | 1. **验收命令清单深化**（承接 11.2⑤/11.3）：除现有只读脚本外，补充**门禁判别力验证 SOP**（不新增 `check-*.py`，复用既有）：对每个 blocking 判据，文档登记「正向用例（必红）+ 负向探针（必绿）+ 合规正例」；**变异 0 红先排除「没跑」再判真绿**；新判据上线前先对全仓跑正向审计（呼应第六章 6、历史锚定串首/正则首匹配假红教训）。2. 把「**判据验收必须含负向探针 + 变异验证**」写入第六章纪律，作为 LIV 族上位原则（与 LIV-R7/R8 互补：R7/R8 管「接线与模式」，本条管「判别力」）。3. fail-closed 联动：门禁真源不可读 / 输入缺失 → 判红而非静默通过（与 T4 fail-closed 同源）。4. 明确：本落点为**文档层建议稿**，不新增 `check-*.py`、不接 CI；落地需 L3。 |

### 11.6.6 深化收口与边界

- 本节是对 11.2 skeleton 的**深化而非推翻**：11.1 判定、11.2 五项、11.3 命令清单、11.4 边界声明、11.5 收口记录**全部保留**，本节仅把「设计意图」补成「可落地的字段草案 / 列草案 / SOP 草案」。
- 所有落点仍受第十章「未动 / 待你拍板」约束：T2/T7 的 §12.2 / README 改动属 **L4**（需架构师本人或明确授权）；T4/T6 落地属 **L3 / 待定级**；未获授权前本 change 不改动任何 `doc/agents/06`、`doc/agents/README.md`、PR 模板或 `scripts/`。
- 业界标杆出处均为公开实践（Anthropic Context Engineering、GitHub Copilot Instructions、Google Cloud DORA、SPACE 框架、IA 静动态融合研究、mutation testing / gate liveness 共识）；未以任何外部收益数字作为 Bone 立项依据（呼应第二章第 49 行）。

---

## 附录：独立审查与修订记录

| 项 | 内容 |
|---|---|
| 流程 | `grill-and-review` **审计模式**：先锁定裁判（Bone 规范真源 > 业界实践 > 个人口味），再由 **3 只全新子 Agent** 并行只读独立审查（禁止作者自审），视角为「正确性与约束冲突」「成本 / 误报 / 可执行性」「文档—实现漂移 / 引用真实性」 |
| 审查对象 | 本文 v1（227 行）+ `doc/architecture/README.md` 登记小节 |
| Verdict | 三只均为 **Request Changes**；合并去重后 17 条高置信度发现（含 9 条 Critical），见第三章 |
| 处置 | 保留方向性结论；撤销 2 个「新建文档」计划、1 个 `VALIDATION.md`、1 个「零引用」立论、1 个硬编码并行数字；T3/T7 降为前置议题；修正审批级、编号命名空间、门禁判据族、接线纪律、全部复核数字 |
| 复核方式 | 本人对关键结论二次取证（`git show --stat`、行数 / 调用数统计、`sed` 读判据原文、技能入库计数、ADR 计数、`grep -E` 检索既有条款），未采信未复核的转述 |
| 第二轮复核 | 按阶段四再派 **2 只全新子 Agent** 只读复核 v2，结论仍为 `Request Changes`：15 条中 10 条完全消解、5 条残留，并抓到 v2 **新引入的 Critical**（T9 误判「零调用」——`--self-test` 实已由 `check_all.py` → `synap-claim-docs.yml` 接线）。本版按「最小复修集」处理：更正 T9 事实与真源、git 状态行、规模分项加总、审批级硬套、README:57 处置落实、第九章冲突面、T3 重复登记、T8 依赖、LIV-R6 strict 口径、编号纪律与索引闭环 |
| 第三轮复核 | 2 只全新子 Agent 只读复核 → **Approve**（第二轮 4 条 Critical 与 9 类约束/自洽残留全部消解，无新 Critical）；4 条低置信度润色（`/59` 计数口径、T9 二选一同构、T1 审批行穷举、T5 枚举口径）已收口 |
| 门禁 | `check-doc-code-symbols.py` / `check-ddd-doc-drift.py` 实跑 **RC=0**；本轮未改动任何 L3/L4 工程文件 |
| v3（Comet tweak） | 经 `/comet` → classic/tweak，change `ai-native-platform-plan-review`；新增第十一章「业界实践对照复核与优化」，按 8 项任务补 5 项业界实践落点；实测验收：`check-doc-code-symbols.py` RC=0、`check-ddd-doc-drift.py` RC=0、`check-ddd-gate-state.py` RC=0、`openspec validate` valid=true，另发现 1 项范围外既有问题（`generate --check` RC=1），归档前于主工作树以「真源追平」修复（见 11.5④） |
| 11.6 深化（2026-11-11） | 用户要求基于业界最佳实践对 11.1「合理，可优化」四项（T2/T4/T6/T7）与第六章 LIV-R 门禁族做三栏深化（现状 / 业界标杆 / Bone 可优化落点）；新增 11.6 六小节，均保持纯文档层、L4 建议稿边界，不落地工程改动、不新增 `check-*.py`、不接 CI、不编造实测值；业界标杆附 Anthropic Context Engineering / GitHub Copilot Instructions / Google Cloud DORA / SPACE / IA 静动态融合 / mutation testing·gate liveness 出处。本 change 未改动任何 L3/L4 工程文件，仅扩展文档 |
