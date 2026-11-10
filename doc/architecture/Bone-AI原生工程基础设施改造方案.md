# Bone AI 原生工程基础设施改造方案

> **状态**：待审批施工蓝图（2026-11-10）。本轮只新增本文与 `doc/architecture/README.md` 的一行登记，
> **未改动任何 L3 / L4 工程文件**（`check.sh`、`ci.yml`、`doc/agents/06`、`.claude/settings.json` 均保持原状）。
> **定位**：本文是改造设计与施工单，不是规范正文；不复制 HC 表、不写门禁状态。
> 门禁状态唯一真源仍是 `doc/architecture/gate-state.json`，协作条款唯一真源仍是 `doc/agents/06 §12`。
> **依据**：三份外部 AI-Native Engineering 方案 + Bone 仓库 2026-11-10 实测取证。

---

## 一、改造范围与结论

**结论**：三份方案的判断方向正确，但 Bone 不需要「建设平台」，只需要 **8 项收敛型改造**。
原因是 Bone 已完成方案 Phase 1（知识地图 / 符号索引）与 Phase 3（质量门禁），且多做了方案未提的
「门禁活性治理」；真实缺口集中在 **治理一致性**，不在「AI 找不到代码」。

| 本方案做什么 | 本方案不做什么 |
|---|---|
| 给已开启的多 Agent 并行补边界；把已有代码图谱纳入正式路由；技能双份收敛；补变更影响分析、跨会话交接、质量指标 | 不新建代码智能平台、不引入 SCIP / Neo4j / Sourcegraph、不重建 Agent 编排、不让 AI 自动起草设计文档、不缩减回归测试集 |
| 全部改造复用既有资产与既有门禁范式 | 不新增任何常驻服务、不扩大运维面 |

**统一判据**：每项改造必须回答「业界哪条实践 → Bone 哪个实证缺口 → 改哪个文件 → 怎么验收 → 谁审批」。

---

## 二、现状基线（2026-11-10 实测，非估算）

| 维度 | 实测值 | 含义 |
|---|---|---|
| 受治理生产源码 | **578,479 行**（Java 385,713 / 3,823 文件；TSX 59,712；Python 54,396；TS 30,594；SQL 28,143；Go 15,355） | 约为「300 万行」前提的 **19.3%** |
| 全部文本 | 1,706,964 行；其中 `.specstory` 会话史 585,711、`outputs` 产物 186,706 | 体量大头不是待理解代码 |
| 代码图谱 | `.codegraph/codegraph.db` 324 MB SQLite：**105,067 节点 / 151,819 边 / 4,830 文件 / FTS**；`calls` 28,476、`references` 17,511、`implements` 263；`unresolved_refs` **297,588**；最后增量同步 2026-11-07 10:48；解析错误 0 | 符号定位可用；静态调用链并不完备（多数未解析项是第三方/框架符号） |
| 图谱注册 | `~/.cursor/mcp.json` 注册 `codegraph serve --mcp`；`~/.claude/settings.json` 放行 `mcp__codegraph__*` 并用 `codegraph prompt-hook` 注入 | **用户级注册、每人一份**；`.codegraph/.gitignore` 排除入库 |
| 确定性门禁 | `scripts/check.sh` 887 行 / **59 步、约 60 个判据调用、约 422 秒**；`ci.yml` 逐条显式接线；ArchUnit 覆盖 10+ 模块；`check-gate-liveness.py --strict`（R1–R4e） | 方案「五级门禁」已完成，且多一层「门禁自身是否生效」 |
| 上下文 | `AGENTS.md` 58 行薄引用；`CLAUDE.md` 43 行 / `.cursorrules` 3 行 / `copilot-instructions.md` 18 行全是指针；`doc/agents/` 6 份按修改路径路由 | 渐进式加载已有，但**靠人工查表** |
| 规格与决策 | `openspec/changes/<change>/`：proposal + design + tasks + specs；`doc/architecture/adr/` **46 篇** | SDD 三件套已有，缺交接态 |
| 并行配置 | `.claude/settings.json`：`agent_teams.enabled=true`、`max_parallel_agents=10`、`auto_merge_on_completion=false` | 并行已开、**边界规范为零**：`AGENTS.md` / `doc/agents/` 检索 `worktree` 零命中 |
| 技能与规则 | `.claude/skills` 28 个与 `.codebuddy/skills` 40 个中 **23 个同名**（12 个 comet-* + 11 个 openspec-*）；`comet-workflow-guard.md` 两份各 57 行 | 多工具双份维护 |
| 度量 | `analyze-metrics.sh`：交付周期、自愈成功率、Ralph Loop 次数；`check-eval-baseline.py` / `check-claim-ai-evalset.py` 评测集门禁 | 偏过程效率，缺质量调整后指标 |
| 影响分析 | 全仓检索 `test impact` / `selective test` / `受影响测试` **零命中** | 明确缺口 |
| 既有防假绿范式 | `doc/ai-product/synapClaim/tools/_ai_native_readiness.py`：12 项证据驱动判据（5 SATISFIED / 7 BLOCKED）；`scripts/tests/` 中门禁单测含变异验证（如 13 例 / 5 变异、21 例 / 8 变异） | **无需引入外部评估框架**，直接复用 |

---

## 三、业界实践到 Bone 落点的映射

| 业界实践（材料所述） | 解决的问题 | Bone 等价物 | 剩余差距 | 本方案动作 |
|---|---|---|---|---|
| OpenAI Harness：`AGENTS.md` 是知识地图非百科 | 上下文被无关规范占据 | 58 行薄引用 + 6 份拆分正文，且记录过「复制 HC 表致 8 条中 5 条与实现不符」 | 无 | 保持，**禁止回抄** |
| Anthropic：LSP / Agentic Search 优于纯 RAG | 文本相似度召回孤立代码岛 | CodeGraph MCP（符号 / 调用 / 实现边 + FTS） | 未进项目级路由、未登记能力边界 | R2 |
| GitHub：分层 / 路径级指令 | 每模块不同约束 | `doc/agents/README.md` 路径路由表 | 人工查表，未机器可读、未自动生成 | R7（P2，可选） |
| SDD：规格驱动 + 人工审批门禁 | 意图到代码缺可验证契约 | OpenSpec proposal/design/tasks + Comet 状态机 + `.claude/contracts/` 契约锁 | 缺 PROGRESS / DECISIONS / VALIDATION | R5 |
| 专业 Agent 分工 + 有边界并行 + Worktree | 无约束并行造成冲突 | architect / engineer / guardian 三角色 + `auto_merge_on_completion=false` | 10 并行无 `scope_paths`、无 Worktree 规范 | R1 |
| Documentation as Code + 契约即代码 | 文档随代码过期 | `check-ddd-doc-drift` / `check-doc-code-symbols` / `check-ddl-doc-sync` / `check-openapi-contract` / `check-i18n-sync`，且 `gate-state.json generate --check` | 无（确定性生成优于 AI 起草） | 保持 |
| ArchUnit / CI 硬卡点 | AI 无意破坏分层 | ArchUnit + 59 步自检 + 门禁活性自检 | 无 | 保持；新判据照章接线（见八） |
| 变更影响分析 + 分级回归 | 改一个方法不能只测它 | CodeGraph 的 calls / references / implements 边 | 无「diff → 符号 → 测试」闭环 | R4 |
| 质量度量：考核有效交付率 | 别只数 AI 写了多少 PR | `analyze-metrics.sh` | 缺 3 类质量指标 | R6 |

> 材料中的收益数字（采纳率 68%、类型错误降至 1/5、回归集缩至 18%）来自异质规模仓库，
> **不作为 Bone 立项依据**；其中「自动缩回归」与 Bone 高风险模块口径相冲突，明确不采纳（见七）。

---

## 四、P0 改造：并行边界与治理收敛

### R1 并行 Agent 边界（最高优先级）

| 项 | 内容 |
|---|---|
| **证据** | `max_parallel_agents=10`；`worktree` 在 `AGENTS.md` / `doc/agents/` 零命中；`check.sh` 头部已为 worktree 修过 `FAIL_COUNT_FILE`（说明 worktree 场景在用，却没有规范）；共享树并行实证出过 `index.lock` RC=128、未暂存文件被 checkout/clean 扫掉、全量 `git add` 使索引非私有、提交被并行会话代写并改写信息 |
| **业界依据** | 「多个 Coding Agent 无约束并行 → 模块隔离 + 独立 Worktree」「按领域划分任务，禁止同时修改相同核心文件」 |
| **目标** | 并行上限 10 → **3**；每个并行任务声明 `scope_paths`；同一文件同一时刻只有一个写者 |
| **改动 1（可立即执行，L2）** | `.claude/settings.json`：`max_parallel_agents: 10 → 3`，保留 `auto_merge_on_completion: false`。**不写工具未验证支持的字段**（如隔离模式），隔离靠下面两条约定落地 |
| **改动 2（L4，需架构师本人执行或明确授权）** | 在 `doc/agents/06` 新增 `§12.7 并行实施边界`：① 每个任务在 tasks 里声明 `scope_paths`；② 独立 `git worktree`（base=HEAD），或共享树上严格 `git commit -F <msgfile> --only -- <paths>`；③ 新文件先 `git add`、关键产物完成即 `git add`；④ `index.lock` 冲突轮询重试、`.check-fail-count` 连 3 次熔断处理；⑤ 提交前 `git log --oneline -2` 排除被代提交；⑥ 只做自己路径的 `spotless`，不整模块 apply |
| **改动 3（P1）** | OpenSpec `tasks.md` 模板加 `scope_paths` 字段；可选新增 `check-parallel-scope.py` 校验声明路径存在且任务间不重叠（须按八接线） |
| **验收** | 两个并行任务实操（**非 mock**）：`git status` 无交叉暂存、`check.sh` 末行 `✅ 本地自检通过！`、提交落点用 `git branch --contains` 复核 |
| **回滚** | `settings.json` 单字段回退；§12.7 由架构师撤销 |

### R2 代码图谱纳入项目级路由与能力边界登记

| 项 | 内容 |
|---|---|
| **证据** | 图谱已建（105,067 节点 / 151,819 边）且 MCP daemon 增量同步；但**正式文档零引用**，只在 `.specstory` 会话史与 `.gitignore` 出现；注册在用户级配置，团队其他机器不保证有；`unresolved_refs` 297,588 |
| **业界依据** | 「不要只依赖全量 Embedding」「优先让 Agent 具备真正的代码导航能力」「静态调用关系不能完全代表真实影响范围」 |
| **改动 1（L4，建议稿）** | `doc/agents/README.md` 加载表增一行：`代码/符号/调用链定位 → .codegraph（CodeGraph MCP，本机可选能力）`；同时写入**降级路径**：MCP 不可用时改用 LSP / 结构化检索，且结论必须给出文件路径与行号 |
| **改动 2（L1，新增文档）** | `doc/architecture/代码智能能力边界登记.md`：登记覆盖语言与文件数、索引新鲜度、`unresolved_refs` 量级、静态盲区（反射 / 动态代理 / 事件总线 / 配置驱动 / SDK 元数据代理）、**不得作为门禁判定唯一证据**、不得把图谱结果写进 `gate-state.json`、新机器需自行建索引 |
| **改动 3（L4，建议稿）** | 把上述指针挂进 `06 §12.2` 路由表，仍保持入口薄引用 |
| **验收** | `python3 scripts/check-doc-code-symbols.py` 与 `check-ddd-doc-drift.py` 不新增漂移；关闭 MCP 后按降级路径完成一次符号定位实操 |
| **不做什么** | 不重建 SCIP / Tree-sitter 全库索引、不引入 Neo4j、不改 `.codegraph/.gitignore`（本机化是对的）。瓶颈是**引用解析质量**（29.8 万未解析），不是存储或查询能力；要提升应补 LSP 符号解析，而非换数据库 |
| **回滚** | 删除新增文档与路由表一行 |

### R3 多工具技能与规则单一真源

| 项 | 内容 |
|---|---|
| **证据** | 23 个同名技能双份；`comet-workflow-guard.md` 两份各 57 行；入口文件已统一薄引用，但技能正文仍双份 |
| **业界依据** | 「不要让三套 Agent 各维护一套项目规范」「模型可替换，工程知识与质量标准不能随模型变化」 |
| **改动（L1 + L3）** | ① 指定 `.claude/skills/` 为唯一真源（或由架构师另定）；② 新增 `scripts/check-agent-skills-sync.py --check`，按 SHA 比对两套同名技能与规则；③ 另一侧由**确定性生成脚本**同步（不用 AI 抄写）；④ 按八接线到 `check.sh` 与 `ci.yml` |
| **验收** | 双侧一致判绿；故意改动一侧 → 判红（变异验证）；合规正例存在（防「永远拒绝」） |
| **回滚** | 撤回生成物，保留两套现状 |

---

## 五、P1 改造：变更影响分析与交接闭环

### R4 diff → 符号 → 测试影响分析（report-only 起步）

| 项 | 内容 |
|---|---|
| **证据** | 全仓零命中；图谱已有 `calls` 28,476 / `references` 17,511 / `implements` 263 边；已有 `scripts/e2e/` 与 `verify-*-e2e.sh` 回归资产 |
| **业界依据** | 「修改一个方法，不能只测试这个方法」「静态依赖 + 运行时 Trace + 事件订阅关系」「影响范围无法完整识别的高风险变更，必须扩大回归」 |
| **改动（L3）** | 新增 `scripts/check-change-impact.py`：输入 `git diff --name-only <base>..HEAD` → 经 `nodes.file_path` 与 `edges` 做 1–2 跳反向遍历 → 输出「变更符号 / 直接调用者 / 关联测试 / 建议回归集 / 静态盲区 / 相关 ADR（即 R8）」 |
| **三条硬约束** | ① **只报告、不阻断、不缩减测试集**；② 高风险路径（claim、额度、权限、DDL、迁移、事件）强制附加「E2E 全量」标记；③ 图谱缺失或读取失败时 **fail-closed 输出「影响面未知 → 扩大回归」**，绝不输出空集 |
| **接线四件套** | `check.sh` 新增 `[N/59]`（带 `--check`，避免恒 exit 0 假绿）+ `ci.yml` 新增 step（name 以 `(blocking)` 结尾，满足 R2）+ `scripts/tests/test_check_change_impact.py` 单测与变异验证 + 若暂缓则登记 `gate-liveness-allowlist.json` |
| **验收** | 实操改一个应用服务方法，报告能列出真实调用者；变异：删一条边 / 清空 diff → 判红或降级提示；合规正例：注入完整事实可达正常报告 |
| **回滚** | 判据脚本 + 两处接线 + allowlist 条目一并撤回（缺一会被 R4b 判红） |

### R5 OpenSpec 跨会话交接三件套

| 项 | 内容 |
|---|---|
| **证据** | change 产物仅 proposal / design / tasks / specs；`.openspec.yaml` 只有 `schema` + `created`；`06 §12.5` P1–P8 无「跨会话交接」环节 |
| **业界依据** | 「不要依赖聊天历史作为项目唯一记忆」「TASK / DESIGN / EXECUTION-PLAN / PROGRESS / DECISIONS / VALIDATION」 |
| **改动（L2）** | 在 `openspec/changes/<change>/` 补 `PROGRESS.md`（已完成 / 未完成 / 阻塞）、`DECISIONS.md`（决策 + 原因 + 关联 ADR 编号）、`VALIDATION.md`（执行过的命令 + 真实结果 + 末行判据）；**不新建第二套 `task/` 目录**，不改 `.openspec.yaml` schema；归档时三份一并归档 |
| **验收** | 新建一个 change 时六件套齐全；新会话仅凭产物即可恢复进度（实操）；`check.sh` 不因新文件报错 |
| **回滚** | 从模板移除三份文件 |

### R6 质量度量补三类指标

| 项 | 内容 |
|---|---|
| **证据** | `analyze-metrics.sh` 只有交付周期 / 自愈成功率 / Ralph Loop 次数；`_ai_native_readiness.py` 的 12 项判据是**业务 AI 能力就绪度**，不是工程基础设施度量，两者不可混用 |
| **业界依据** | 「不考核 AI 写了多少代码，考核有效交付率与质量调整后的研发效率」 |
| **改动（L1）** | 在 `doc/architecture/` 顶层新增度量口径文档（**不要放 `reports/` 子目录**：`.gitignore` 的 `reports/` 规则会把它排除入库），补 **设计一次通过率 / AI 缺陷逃逸率 / 人工返工率**（可选加图谱检索命中率）；数据源复用 `.claude/state/*/checkpoint.json` + `git log` + PR review 记录，**不新建采集平台、不用 mock 数据** |
| **验收** | 每个指标可复算、口径写明、样本可追溯 |
| **回滚** | 删除口径文档 |

---

## 六、P2 改造：路径级规则与 ADR 提示

| 编号 | 内容 | 前置与风险 |
|---|---|---|
| **R7 路径级自动规则** | 把 `doc/agents/README.md` 的「修改路径 → 加载文档」表机器可读化为 `doc/agents/routing-map.yaml`，用生成脚本产出 `.claude/rules/bone-*.md`（带 paths 匹配）与 `.github/instructions/*.instructions.md`；新增 `check-agent-routing-sync.py --check` 校验三方一致。**入口仍保持薄引用，禁止把 6 份正文抄回 AGENTS.md**（`06 §12.6` 明令禁止） | 工作量最大、收益待验；涉及 `.github` 与 §12.2 → **L3 + L4 双审批**；最大风险是规则重复漂移，必须用「单一真源 + 生成 + 校验」闭环 |
| **R8 按 diff 提示相关 ADR** | 不单独立项：并入 R4 影响报告输出，按领域 / 关键词命中 46 篇 ADR，纯提示不阻断 | 依赖 R4；避免又造一个孤儿脚本 |

---

## 七、不改造清单与理由

| 不采纳项 | 理由 |
|---|---|
| SCIP / Tree-sitter 全库索引、Neo4j、Sourcegraph、独立检索服务层 | 58 万行 + 已有 10.5 万节点 SQLite 图谱；方案自身也写「不建议一开始就引入 Neo4j」。真正瓶颈是未解析引用质量，不是存储 |
| Sync-Docs Agent 自动起草设计类文档 PR | `06 §12` 属 L4（AI 不得自改）；且方案自己强调「不能让 AI 按代码反推设计文档」。Bone 的 `generate --check` 确定性生成更可靠。仅机器派生文档（baseline / OpenAPI / 数据字典）走确定性生成 |
| 自动缩小回归测试集（如「缩到 18%」） | 与 Bone「误报比漏报更致命」「全绿 ≠ 有判别力」及理赔 / 额度 / 权限高风险模块需扩大回归相冲突；收益数字来自异质仓库 |
| 重建多 Agent 编排平台 | 三角色 + 状态机 + 28 技能已具备，缺的是边界与一致性，不是更多 Agent |
| 覆盖或重写 `AGENTS.md` / 嵌套多份大部头 AGENTS.md | `06 §12.6`：外部通用规范一律先吸收进手册，入口保持薄引用 |
| 把 CodeGraph 升格为团队共享服务 | 本机资产、用户级注册；跨机器一致性尚无需求证据，且会引入新的运维面与「配了不生效」风险 |

---

## 八、门禁接线与验收总纪律（任何改造都要过）

Bone 已有的约束比通用方案更严，**新增判据必须一次做齐**，否则 G47 / R4 直接判红：

1. **新增 `scripts/check-*.py` 必被活入口调用**：`check.sh` 的 `[N/59]` 步骤（`if ! python3 ...; then exit_code=1`）
   + `ci.yml` 显式 step（**name 必须以 `(blocking)` 结尾**，R2 按此计数）+ 暂缓时登记
   `scripts/gate-liveness-allowlist.json`（理由 ≥20 字符、owner 非空、unblock 写清）。
   步骤编号顺延时同步 `.comet/config.yaml` 注释与 `gate-state.json` 的历史快照说明。
2. **R4e**：派生入口必须被根入口调用 —— 不要再建一个「没人跑的 runner」给孤儿门禁发免死金牌。
3. **判据本身**：`--self-test` 负向探针 + `scripts/tests/test_check_*.py` 单测 + **变异验证**（参照
   `test_check_gate_liveness.py` 13 例 / 5 变异、`test_check_java_import_integrity.py` 21 例 / 8 变异）；
   必须有合规正例（防「永远拒绝」式假实现）、真源不可读时 fail-closed。
   参考 `_ai_native_readiness.py` 的教训：**只匹配字符串 / import 行 / 任意非空哈希都能被低成本刷绿**。
4. **门禁状态**：只改 `gate-state.json` 后跑 `check-ddd-gate-state.py generate --check`，
   不得手改文档里的表；`AGENTS.md` / `doc/agents/` 不复制 HC 表或状态。
5. **先正向审计再上线**：新判据先对全仓跑一遍（历史上 `match()` 锚定串首、正则 `search()` 只取首个匹配
   都造成过假红 / 静默漏扫）；误报会逼作者关掉门禁，比漏报更致命。
6. **审批级别**：`ci.yml`、删码、依赖、新增 CI 步骤 = **L3（架构师审批）**；`doc/agents/06 §12`、
   `AGENTS.md` 协作条款 = **L4（AI 禁止执行，须架构师本人或明确授权）**；本次所有 L3/L4 项均只出建议稿。

---

## 九、分期路线与量化对照

| 阶段 | 改造项 | 交付物 | 验收 | 审批 |
|---|---|---|---|---|
| P0 | R1 / R2 / R3 | settings 字段、§12.7 建议稿、路由表一行、能力边界文档、技能同步判据 | 并行实操 + 文档门禁通过 + 双侧 SHA 一致 | L2 / L4 / L3 |
| P1 | R4 / R5 / R6 | 影响分析判据（含单测与变异验证）、OpenSpec 三件套、度量口径 | 实操对照 + `check.sh` 全绿 | L2 / L3 |
| P2 | R7 / R8 | 机器可读路由表 + 生成校验；ADR 提示并入 R4 | 三方一致判绿 | L3 + L4 |

| 维度 | 改造前 | 改造后目标 |
|---|---|---|
| 并行上限 / 边界规范 | 10 / 0 条 | 3 / `scope_paths` + Worktree + 路径圈定提交 |
| 代码图谱正式引用 | 0 处 | 路由 1 处 + 边界登记 1 处 + 降级路径 1 条 |
| 同名技能双份 | 23 个 | 0（单一真源 + 生成校验） |
| 影响分析判据 | 0 命中 | 1 个 report-only 判据 + 高风险扩大回归标记 |
| 跨会话交接产物 | 3 件套 | 6 件套 |
| 质量指标 | 3 类（过程效率） | +3 类（质量调整后） |

---

## 十、风险与回滚

| 风险 | 缓解 |
|---|---|
| **新判据误报**（Bone 首要风险） | R3 / R4 / R7 一律先 report-only + 全仓正向审计，再讨论是否阻断；保留合规正例与负向探针 |
| 自检耗时膨胀（现约 422 秒 / 59 步） | 快判据（≤3s）进 pre-commit；慢判据 CI-only 并登记 allowlist（已有 event-envelope 22s 先例） |
| 规则/技能漂移 | 单一真源 + 确定性生成 + `--check`，不用人工或 AI 抄写 |
| 图谱不可用导致误判 | fail-closed 降级：输出「影响面未知 → 扩大回归」，绝不静默给空集 |
| 触碰 L4 越权 | §12.7 / §12.2 仅出建议稿，由架构师本人执行 |
| 整体回滚 | 每项改动均为单字段 / 单文件 / 单判据；判据回滚须同时撤 `check.sh` + `ci.yml` + allowlist 条目 |

---

## 十一、未动 / 待你拍板

1. **R1 的 `§12.7` 属 L4**：需你本人执行或明确授权后，我才能改 `doc/agents/06`；本轮只写了建议内容。
2. **并行上限取 3 是否接受**（现为 10）？也可取 2 或保持 10 而仅加边界条款。
3. **技能唯一真源选哪一侧**：`.claude/skills` 还是 `.codebuddy/skills`？
4. **R4 影响分析是否最终进 CI 阻断**？我建议先 report-only 两个迭代，观察误报率。
5. **R7 路径级自动规则是否本期做**？工作量最大、收益待验，可推迟到 R1/R2 稳定后。
6. **CodeGraph 是否升格为团队共享索引**？我建议否，仅做「本机可选能力 + 降级路径」登记。
7. **是否同意新增 `check-*.py` 判据**（R3 / R4 / R7 都会进 G47 监管，必须同批接线，不可只写脚本）。
8. **Comet selection 状态异常**：`current-change.json` 指向 `synapclaim-ai-governance-hardening`，只读探针报 `classic-state-invalid`，而 `openspec/changes/` 下产物齐全且带运行时迁移标记。是否本轮一并修？（与本次改造无关，我未进入该流程。）
9. **交付格式**：是否需要本文的 HTML / PPT 版本，或拆成「审批单 + 施工单」两份？
10. **`.gitignore` 的 `reports/` 规则误伤 `doc/architecture/reports/`**：该目录 README 与人工收敛报告均未入库（仅 `写端点授权收敛报告.md` 曾被强制跟踪）。是否加 `!doc/architecture/reports/` 例外？本轮方案已改放 `doc/architecture/` 顶层规避。
