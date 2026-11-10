# Bone AI 原生工程基础设施改造方案（v2 · 经独立审查修订）

> **状态**：待审批施工蓝图 v2（2026-11-10）。v1 经三只独立子 Agent 审查，全部 `Request Changes`；本版按其发现重写，处置见附录。
> **基准声明**：审查基准固定为 **Bone 项目自身规范真源 > 公认业界实践 > 个人口味**。本文不拿通用最佳实践覆盖已登记决策（ADR / 能力边界裁定 / 显式豁免）。
> **真源指针**：HC 门禁状态 → `doc/architecture/gate-state.json`（机器真源）与 `Bone-DDD-最终实践方案.md §G-1.7`（HC 状态表）；协作条款 → `doc/agents/06 §12`；工具链裁定 → `Bone-Comet工具链基线与升级运维.md`。本文**不复制** HC 表、不写门禁状态。
> **命名空间**：改造项用 `T1–T9`；门禁活性判据用 `LIV-R*`（`scripts/check-gate-liveness.py`）；交付流程阶段用 `P1–P8`（`06 §12.5`）；本文分期用「阶段 A/B/C」。三者互不复用编号。
> **入库事实**：**v1** 于提交 `5b140a722` 入库；**v2 为工作区未提交修订稿，当前 HEAD=`11f7e17af`**。该提交共 3 个文件：本文、`doc/architecture/README.md` 新增 6 行小节、以及**由并行会话改动**的 `openspec/changes/synapclaim-ai-governance-hardening/.comet.yaml`）。本轮及其修订**未改动任何 L3 / L4 工程文件**。

---

## 一、结论：方向成立，但 v1 的施工单不可直接用

**保留的结论**：三份外部方案的方向判断正确；Bone（受治理源码 578,479 行）不需要新建 AI 工程平台，只需收敛型改造；不引入 SCIP / Neo4j / Sourcegraph、不重建编排、不让 AI 自动起草设计文档、不缩减回归测试集。

**v1 被推翻的部分**（详见第三章）：把 Bone **已登记的人工规范与已裁定能力**写成了「零命中缺口」，并据此提出会制造第二真源、CI 恒红或恒绿的改造。典型两例：

| v1 断言 | 事实 | 后果 |
|---|---|---|
| 「`受影响测试` 全仓零命中 → 无影响分析」 | `06 §11.13 ④`（`06:50`）、`.github/PULL_REQUEST_TEMPLATE.md:26`、`Bone-DDD-最终实践方案.md:1692/2262` 已有「受影响测试**已实际执行且通过**」人工条款 | 缺口应重新定义为「无 diff→符号→测试的**自动化推导**」，而不是「没有规范」 |
| 「CodeGraph 在正式文档零引用 → 需新建能力边界登记」 | `Bone-Comet工具链基线与升级运维.md:163–170` 有 D3 专节，`:170` 已裁定「**CodeGraph 是可选能力，不纳入项目门禁**」；`:181` 裁定「多平台 skills/rules/hooks 本机生成、**不入库**」 | 新建文档＝第二真源；把「不入库」资产接进 CI 判据＝干净检出上恒红或恒绿 |

**v2 的改造规模**：9 项，其中 **T3 / T7 降为「待架构师裁定是否改变既有策略」的前置议题**，不列入本期施工；真正可施工的是 7 项，且多数是「补条款 / 复用既有资产」，不是新建系统。

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

| 项 | 内容 |
|---|---|
| **证据** | `analyze-metrics.sh` 只有 2 类指标 + 2 处 `TODO`；`.claude/state/` 为空且被 `.gitignore:205` 忽略；产物 `reports/metrics.md` 落在 `.gitignore:242` 有意忽略的运行时产物目录 |
| **v1 错在哪** | 「可复算 / 样本可追溯 / 不新建采集平台」与现有数据源**不能同时成立** |
| **改动（收窄）** | 本期只出**口径文档 + 手工登记模板**（设计一次通过率 / AI 缺陷逃逸率 / 人工返工率），放在 `doc/architecture/` 顶层并登记进 README 规范索引；写明「复算需本机 checkout + 本机状态快照」；先盘点可用数据（Comet state、PR 勾选项、熔断计数），再决定是否补最小采集字段 |
| **验收** | 口径无歧义、模板可填写、数据来源逐项注明；**不承诺自动可复算** |
| **审批** | 待架构师定级（新增文档） |

### T7 路径级自动规则（**前置议题，阶段 C 可选**）

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

| 维度 | 现状 | v2 目标（不再写无依据的数字） |
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
8. **`.gitignore:242 reports/` 精确例外**：规则管运行时产物是对的，但误伤 `doc/architecture/reports/` 文档子目录（其 README 未入库，`写端点授权收敛报告.md` 曾被强制跟踪）。是否加 `!doc/architecture/reports/**` 并裁定该目录是否应入库？本文已放 `doc/architecture/` 顶层规避。
9. **T5 模板真源**：`scope_paths` / PROGRESS / DECISIONS 由哪个 skill 或 CLI 生成？未定位前只能逐 change 落地。
10. **Comet 状态异常**：只读探针曾报 `classic-state-invalid`；并行会话已在提交 `5b140a722` 把 `synapclaim-ai-governance-hardening` 绑定至 `dev` 并设 `isolation=current`。该改动**非本文作者所为**，是否需复核其状态一致性？
11. **交付格式**：是否需要本文的 HTML / PPT 版本，或拆成「审批单 + 施工单」两份？

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
