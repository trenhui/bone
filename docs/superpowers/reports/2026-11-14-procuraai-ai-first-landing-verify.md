# 验证报告：procuraai-ai-first-landing

- 日期：2026-11-14
- 阶段：Verify（SDK Run `classic-full`，revision 66 → verify）
- 验证模式：full / `build_mode: direct`、`tdd_mode: direct`、`review_mode: standard`
- 产物语言：zh-CN
- 范围裁决依据：`openspec/changes/procuraai-ai-first-landing/tasks.md`（2026-11-14 范围重定界）与 `deferred-backlog.md`

## 1. 范围与归因（诚实重定界）

本 change 于 Open 时登记 27 个任务。经核实，本次实际交付**仅供应商主数据（MDM）模块子集**（任务 3.1–3.4），已由 9 笔提交 `6f0ea427f..9b3c70c70` 推送 `origin/dev`。其余 23 个任务（DoR / spike / AI 审查 / 治理 / 收敛 / 门禁探针 / 集成）从未启动，按用户 2026-11-14 明确决策「重定 scope 再归档」迁出至 `deferred-backlog.md`，作为独立 owner/L3 WBS，**不属于本 change 的关闭项**。

因此，本报告的验收对象严格限定为 `tasks.md` 的 4 项任务及其支撑文档与门禁，不把 deferred 项计为缺陷，也不把 MDM 骨架冒充为 AI 能力落地。

### 已接受偏差（Accepted Deviations）

| 偏差 | 事实 | 处置 |
|---|---|---|
| `proposal.md` / 3 份 delta spec 描述的是完整 AI-first MVP（AI 合同审查 + 治理），实际交付只有 MDM 子集 | proposal 与 spec 未在重定界时同步收窄 | 用户已明确拍板重定 scope 后归档；`tasks.md` 顶部与 `deferred-backlog.md` 完整记录原因与去向。归档材料保留原始 proposal/spec 不回改历史，偏差在此显式登记 |
| DoR R-1~R-6 未闭合即落地 MDM 骨架 | `tasks.md` 纪律要求 DoR 全 ✓ 后才编码 | 属已发生的工程决策，`tasks.md` 已注明「风险由 owner 在 backlog 中跟踪」 |
| 全仓 `scripts/check.sh` 存在与 change 无关的预存红项 | iam_menu 种子过期（活库 300≠种子 288，需连 DB 重生成）、seed-manifest 空清单、门禁自身单测失败、synapClaim 子域归属 mismatch、DDD doc↔code 自检 MELTDOWN | 非本 change 引入；本 change 的验证以**模块级**构建与本族门禁为准，不以全仓门禁绿为判据。见 §5 |
| 全仓 maven 在 Maven 3.6.3 下失败 | `bone-extension-sdk` 的 `spotbugs-maven-plugin:4.10.4.1` 要求 Maven ≥ 3.8.9 | 环境级预存问题（本地已缓存 3.9.0），属 owner 决策；本 change 的 build/verify 检查固定为 `bone-platform/bone-masterdata` 模块作用域 |

### 并行会话在途文件（dirty worktree）

工作区尚有 `doc/ai-product/procuraAI/` 下 6 个未提交文件（mtime 20:30–20:32，晚于本 change 最后提交 20:12:39），内容为该文档族「第二十五轮 X1–X4 字段级契约工件 + 外部一手依据入族」。其 CHANGELOG 自述属另一活跃会话、明确「未做 git 写操作（无提交/PUSH）」。按既定协作决策：**不接管、不提交、不修改、不回退**。本次验证只读复核其门禁影响（主门禁 10/10 仍绿，见 §3），不将其计入本 change 成果。

## 2. 新鲜验证证据（2026-11-14 实跑）

| 检查 | 命令 | 结果 |
|---|---|---|
| Comet Build 检查 | `comet check run procuraai-ai-first-landing build --json -- <mvn3.6.3> -f bone-platform/bone-masterdata/pom.xml clean test` | `exitCode: 0`、`tier: full`、收据 `cab86e6c-a730-4944-a668-7fbfdd2fd169.log`，`changedDuringExecution: []` |
| 模块单测（复跑） | `<mvn3.6.3> -f bone-platform/bone-masterdata/pom.xml clean test` | 250 例全绿，BUILD SUCCESS |
| 采购域主门禁 | `python3 tools/check_procura_alignment.py` | 10/10 PASS（A1–A10 全通过） |
| 门禁注入式负向反证 | `python3 tools/check_procura_alignment.py --self-test` | 146/146 探针全部转红且命中预期 key；6 条正例未被误杀 |
| 评测集结构门禁 | `python3 doc/ai-product/procuraAI/scripts/check_procura_evalset.py` | PASS（69/69；脚本明确声明「不守模型效果，CI 绿不等于 AI 能力可上线」） |
| 文档 frontmatter | `python3 doc/ai-product/procuraAI/scripts/check_procura_frontmatter.py` | 33 份全绿 |
| 任务与产物核对 | 脚本校验 `tasks.md` 勾选数、proposal/design/backlog 存在性 | 4/4 已勾选；3 份产物齐备 |

> 说明：历史上 `:34` 的 build 检查 `exitCode=1` 与本次 `:36` 的假失败均已定位为**工具链假红**，与代码无关：前者是 Comet SDK 派生的 maven 命令尾部多带 `--json`（Maven 3.6.3 不认该全局参数，直接打印 usage 退出）；后者是首次重试时 maven 绝对路径误拼了多余后缀导致可执行文件不存在。二者已分别用「显式 argv、不带 `--json`」与「正确 maven 路径」修正，最终 `:36` 真实 `exitCode=0`。

## 3. 完整验证 7 项检查

1. **tasks.md 全部完成**：PASS —— 4/4 勾选，任务 UUID 稳定（`493dbcea` / `eb8cf1dd` / `663f478b` / `bfdbcf5a`），与 Comet plan 的 4 个 `comet-task-ref` 一一映射，`planMapping.status: ready`、`unmappedCount: 0`。
2. **实现符合 change design.md 高层决策**：PASS —— 交付为设计中的「供应商主数据 MDM 子集」；设计文件存在且带 Comet 要求的 YAML frontmatter（`comet_change` / `role: technical-design` / `canonical_spec: openspec`）。AI 审查与治理能力按设计仍为 Proposal 态，本 change 未声称落地。
3. **能力规格场景一致性**：PASS-WITH-DEVIATION —— `specs/procurement/` 下 3 份 delta spec（`supplier-master-data` / `ai-contract-review` / `ai-decision-governance`）与 `proposal.md` 一致，但描述范围大于实际交付。该偏差已在 §1 显式登记并经用户决策接受；`supplier-master-data` 对应的 MDM 场景由 250 例单测覆盖。
4. **proposal.md 目标达成度**：部分达成（如实登记）—— 仅「供应商主数据最小能力」落地；AI 合同审查流水线、AI 决策治理、HITL 评测、评测集 ≥200 例、SLO 冻结等价值假设与成功指标**全部未验证**，属 deferred-backlog 的 23 项。不得据本 change 归档宣称 AI-first MVP 已交付或 AI 能力可上线。
5. **delta spec 与 design doc 无矛盾**：PASS —— 未发现 spec 有而 design 未记录的增量；X1–X4 运行时契约层在文档族中标为 `DesignOnly`（并行会话第二十五轮亦明确「不在 CI 三入口判据集合内、删除整节主门禁仍绿」），不作为已实现契约。
6. **关联设计与计划文档可定位**：PASS —— `docs/superpowers/plans/2026-11-14-procuraai-ai-first-landing.md` 存在，`<!-- comet-task-authority: … -->` 与 4 个 `<!-- comet-task-ref:… -->` 齐备；`deferred-backlog.md` 存在并登记 23 项 owner 边界。
7. **门禁与 CI 口径**：PASS —— 本族门禁 10/10 + 146/146 + evalset PASS + frontmatter 33 份；未改动 `ci.yml` / `scripts/check.sh` / `check-procura-ci.sh` 三入口判据集合，也未引入 A10.16 黑名单串。`CI 绿 ≠ AI 可上线` 的结论未变。

## 4. 独立最终集成审查

> `review_mode: standard` 要求由独立 reviewer 对最终 diff 完成一次审查。项目未安装 `requesting-code-review` / `verification-before-completion` 等 Superpowers skill，因此以独立只读子代理对 `b04b2a8d..HEAD` 的最终 diff 执行同等审查（覆盖 4 项任务的实现与测试、假绿风险、spec/design/tasks 一致性、deferred 归因）。
>
> **审查结论：FAIL**（CRITICAL 2 项 / IMPORTANT 3 项）。以下关键论断均已由本次 Verify 会话**亲自读源码复核**，非直接采信子代理报告。

### 4.1 复核证据（file:line）

| 论断 | 亲自复核命令 / 位置 | 结果 |
|---|---|---|
| 3.2 声称实现 Supplier / Vendor / VendorActivation / LE 实体 | `grep -rln "class Supplier\|class Vendor\|class LegalEntity\|class VendorActivation" bone-platform/bone-masterdata/src/main` | **零命中**；`diff --name-only b04b2a8..HEAD -- .../src/main \| grep -iE "Supplier\|Vendor\|LegalEntity"` 亦零命中 |
| 3.1「建模块骨架」非本 change 所为 | `git ls-tree -r --name-only b04b2a8 bone-platform/bone-masterdata/src/main \| wc -l` = **206**；本 change 新增 main 文件 **37** | 模块在 base-ref 已存在；`MasterdataApplication.java` 不在交付 diff，`@EnableSqlRepositories` + `@Import` 在 `b04b2a8` 版本第 11–12 行已有 |
| 实际领域模型是通用主数据，非供应商 MDM | `domain/model/goldenrecord/` 仅 FieldProvenance / SurvivorshipPolicy / GoldenRecord / GoldenRecordMerger / MergeAudit 等通用去重类；`DomainTemplate.java:39` 域编码为 `CUSTOMER/SUPPLIER/MATERIAL/ORG_UNIT/FIN_ACCOUNT/EMPLOYEE/PROJECT` | 无「统一信用代码唯一键」去重语义；`grep -rni "统一信用代码\|uscc"` 在 src/main 零命中（仅 `MasterDataErrors` 的 `java.util.function.Supplier` 与 `supplier()` 方法名命中） |
| 3.4 权限点不符 | `ChangeRequestController.java:46,53,61,72` = `hasAuthority('masterdata:governance:write')`、`:80` = `masterdata:governance:read` | tasks 3.4 写的是 `procurement:supplier:*`，实际为 `masterdata:governance:*` |
| 摘要密钥 fail-closed 声称不成立 | `application.yml:71` = `digest-pepper: ${BONE_MASTERDATA_DIGEST_PEPPER:dev-only-digest-pepper-change-in-prod}`；`GoldenRecordApplicationService.java:62` 注释称「注入且无默认值，缺失即无法装配（fail-closed）」 | 配置给了 dev 占位默认值；构造器 `:90-93` 仅校验 blank 与长度 ≥16，占位串长度 41 **必然通过**。缺失环境变量时生产会静默使用公开占位盐值，与注释矛盾 |

### 4.2 分级问题清单

- **CRITICAL-1（范围真实性）**：`tasks.md` 4 项任务全部勾选 `[x]`，但验收文字描述的交付物（供应商主数据实体、统一信用代码去重、模块骨架装配、`procurement:supplier:*` 权限点）与真实代码不对应。真实交付是**既有 `bone-masterdata` 模块上的通用 Golden Record 去重内核 + 分段变更请求 + Outbox 中继 + 治理 API**。若据此归档，等于把未实现的验收条件记为已通过。根因：2026-11-14 范围重定界时直接沿用原 27 项任务的 3.1–3.4 文字，未逐条核对代码。
- **CRITICAL-2（需求基线矛盾）**：`proposal.md` 与 3 份 delta spec 仍描述完整 AI-first MVP（AI 合同审查流水线 + AI 决策治理 + HITL + 评测集），并含 `SHALL`/`MUST` 级需求；实际交付为零 AI 能力的 MDM 内核。OpenSpec 归档会把 delta spec 合入 `openspec/specs/procurement/`，使未实现能力被登记为主规格的生效需求。
- **IMPORTANT-1**：`application.yml:71` 的 dev 占位默认值削弱 PII 摘要盐值，生产漏配环境变量即静默降级（`fail-open`），且与源码注释的 fail-closed 声明矛盾。
- **IMPORTANT-2**：`tasks.md` 3.4 权限点 `procurement:supplier:*` 与实际 `masterdata:governance:*` 不一致，验收判据不可用。
- **IMPORTANT-3**：`assertClassicBuildReady` 与 `complete-build` 只校验 tasks 勾选数、plan 证据与分支绑定，**不校验任务文字与代码的一致性**；250 例单测覆盖的是真实交付的 Golden Record / Outbox 行为，不能证明 tasks 3.1–3.4 的文字验收条件被满足（存在「测试真绿、验收文字不匹配」的假绿形态）。
- **WARNING**：`tasks.md` 顶部自述「R-1~R-6 DoR 未闭合即落地 MDM 骨架，属已发生的工程决策」，但本次复核显示其描述的是供应商 MDM 骨架，与实际交付同样不对应。
- **SUGGESTION**：无。

### 4.3 假绿风险

250 例单测是**真实的**（`clean test` 实跑 BUILD SUCCESS，非 mock 自证），覆盖 Golden Record 合并裁决、唯一键映射、Outbox 投递等真实行为；门禁 10/10、146/146 负向探针也是真实的。问题不在测试造假，而在**「测试通过」被当成了「tasks.md 文字验收条件通过」** —— 二者描述的是不同对象。

### 4.4 不在本 change 范围（不计为缺陷）

`deferred-backlog.md` 的 23 项（DDL 落库、生产跨域 adapter、CR adapter、Steward 署名、ADR、DoR/spike/AI 审查/治理/收敛/集成）为已迁出的未启动任务；并行会话在途的 6 个 procuraAI 文档文件亦不属本 change 验收对象。

### 4.5 审查局限

独立审查为静态只读（未运行构建/测试）；构建与门禁由本次 Verify 会话另行实跑（§2）。审查结论的 5 条关键论断已逐条经本次会话读源码验证。

## 5. 未执行项、暴露面与后续

- **未执行**：全仓 `scripts/check.sh`（存在 §1 所列 5 类与 change 无关的预存红项，其中 iam_menu 种子需连 DB 重生成，本环境无 DB 权限）；全仓 maven 构建（spotbugs/Maven 版本错配）；DDL 落库、生产跨域 adapter、评测集扩容至 ≥200 例、模型效果评测、压测与 SLO 冻结、法务结论、F1–F4 估工、X1–X4 契约冻结、E-10 索引登记 —— 全部属 deferred-backlog / owner。
- **暴露面**：本 change 归档只关闭 4 项 MDM 任务；procuraAI 审查流水线仍为「N 态零实现资产」（0 个 `bone-procurement-*` 模块 / 0 条 `t_proc_*` DDL / 0 个 handler）。文档族 X1–X4 字段级契约为纯叙述 DesignOnly，不在 CI 三入口判据集合内。
- **安全边界**：未执行生产数据库操作、未自行创建或切换分支、未 force push；`comet-state.yaml` 为 Runtime 投影，由 Run 自身重建。

## 6. 结论

**FAIL —— 不通过，不得进入 Archive。**

§2 的所有检查命令都真实执行且全绿（250 例单测、门禁 10/10、146/146 负向探针、Comet build check `exit=0`），但这些证据证明的是「真实交付的 Golden Record / 变更请求 / Outbox 内核质量合格」，**不能证明 `tasks.md` 3.1–3.4 的文字验收条件被满足**。独立审查发现的 2 项 CRITICAL 与 3 项 IMPORTANT 已逐条经源码复核成立，按 `comet-verify` 的「CRITICAL/IMPORTANT 不可豁免」规则不得接受为偏差。

因此本次 **未执行** `comet guard ... verify --apply` —— 在报告结论为 FAIL 时提交 Guard，等于把失败结论当作通过证据写入 Run，属于伪造验收。只读预检（`ALL CHECKS PASSED — phase=verify`）仅验证报告格式与 Run 状态，不构成验证结论通过。

### 需要决定后才能继续

1. **文档口径是否按真实交付重新收敛**：`tasks.md` 4 项验收文字、`proposal.md` 与 3 份 delta spec 仍描述供应商 MDM 骨架 + 完整 AI-first MVP，与实际交付的「通用主数据 Golden Record 内核」不一致。修改 proposal/spec 属需求基线变化，按 Comet 规则需重新确认设计/计划证据（`revise-design` / `revise-plan` / `submit-plan`）。
2. **`IMPORTANT-1` 摘要盐值 fail-open 是否在本 change 内修复**：去掉 `application.yml:71` 的 dev 占位默认值、改为缺失即启动失败；该改动属生产代码，会触发 build 检查重跑。
3. **是否执行 `comet state transition procuraai-ai-first-landing verify-fail --reason ...`**：当前 nextAction 正是 `full.verify.run`，该命令会把失败原因记入同一 Run 并返回 Build；若选择保留现有工作暂不归档，则本轮到此结束。

### 建议（供决策参考）

延续 2026-11-14「重定 scope 再归档、诚实归档不伪造完成」的原决策：把范围收敛做彻底 —— 将 `tasks.md` 4 项改写为与真实交付一致的验收描述，在 `proposal.md` / delta spec 上显式标注「本 change 仅交付主数据去重内核，AI 审查/治理与供应商 MDM 实体均为 deferred」，修掉摘要盐值默认值，然后重走 build check → verify → archive。23 项 deferred 与并行会话在途文件维持不动。
