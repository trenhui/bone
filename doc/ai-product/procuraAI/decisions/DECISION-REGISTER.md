---
status: Active
layer: decision
truth-for: 建议 → 决策四态登记与开口项索引（导航投影，不参与业务裁决）
updated-on: 2026-11-26
---

# procuraAI 决策登记（建议 → 决策状态机）

> **性质**：导航投影 / 状态索引，**不是业务真源，不参与冲突裁决**。冲突一律按 [文档资产登记与真源裁决.md](../文档资产登记与真源裁决.md) 的八级裁决链处理。
> **解决的问题**：28 轮执行台账散落在 [CHANGELOG.md](../CHANGELOG.md)，`outputs/` 另有 10 份诊断报告（E-1…E-10，见资产登记 §11）；"建议"与"已落地"混在同一段当时快照里，AI 与人工都无法回答「还有哪些没做、谁来做、怎么算做完」。本登记把建议族按决策状态投影成一张表，并集中列出**开口项**。
> **纪律**：不复制台账原文、不回改历史数字、不伪造 owner 与验收结论。历史原文以 CHANGELOG 为准；`N/N` 一律是当时快照，当前态以 README 的 `gateEvidenceManifest` 行为准。

## 1. 四态状态机（MADR / ADR 惯例）

| 状态 | 含义 | 进入条件 | 退出条件 |
|------|------|----------|----------|
| `Proposed` | 已提出，未拍板 | 建议有编号与来源 | 架构评审接受 → `Accepted`；被后续决策取代 → `Superseded` |
| `Accepted` | 已拍板，待落地 | 有 owner + 可验证的验收判据 | 落地物与守护齐备 → `Landed` |
| `Landed` | 已落地且**有机器守护或 `file:line` 证据** | 文档 + 门禁 / 工件 / 源码三者至少之一可验 | 被撤销 → `Superseded` |
| `Superseded` | 已撤销 / 被取代，**保留证据链不删** | 有新决策明确取代，并写明取代者 | 终态 |

**硬规则**：

1. 只有「台账写了落点」不等于 `Landed` —— 还须有验收判据（门禁号段 / 工件字段 / 源码路径）。文档就位但代码、签名、跨域交付仍待 owner 的，标 `Accepted + 外部依赖`，并在第 3 节开口项登记。
2. `Superseded` 条目**不得删除**：已撤销 ADR-06、私有化部署结论需要「何时、为何撤销」的审计链，且 A10 把旧族全文当反向守护对象扫描撤销标记。
3. 每条 `Accepted` / `Landed` 必须能回答：`owner`（未指派就写「未指派」，不编造）、`验收判据`（可机器验证优先）、`来源`（CHANGELOG 行号或文档路径）。

## 2. 建议族索引（按来源分组，不复制原文）

| 建议族 | 来源 | 条数 | 状态速览 | 验收判据形态 |
|--------|------|------|----------|--------------|
| R1–R14 全方案独立诊断落点 | `CHANGELOG.md:31` 起「v0.3 收口修订」表 | 14 | 主体 `Landed`（含 R1/R13 资产三态、R2 撤销 ADR-06、R5 门禁外部锚点、R11 版本戳） | 真源文档 + A8/A10 号段 + 契约工件 |
| O1–O7 续诊建议 | `CHANGELOG.md:71` 起 | 7 | O1/O3/O4/O6/O7 `Landed + 门禁`；**O2 / O5 `Accepted + 外部依赖`** | 表内「验收 / 门禁守护」列（A8.6 / A10.10–A10.12 等） |
| C-A~C-F / F-E / AI-native | `CHANGELOG.md:87` 起 | 8 | C-A/C-B/C-C/C-E/C-F `Landed`；**C-D / F-E `Accepted + 外部依赖`**；AI-native `Proposed` | A10.3.1 / A10.4.1 / 字段真源 D3-v2 §2.1 |
| 业界校准 G11–G17 / G-A / G-B | 第十二轮台账 | — | `Landed`（文档 + 判据），详见 CHANGELOG | 蓝图章节 + 门禁 |
| G14 / G16 战略选项 | 第十轮前后台账 | 2 | `Proposed`（ADR 草案，决策栏「待定」） | 蓝图 §5 草案 + owner / 监管决策点 |
| 第二十轮前各轮收口 | `CHANGELOG.md:126` 起 | 9 轮 | `Landed`（含 A10.16–A10.28） | 自检探针 + 注入必红反证 |

> **未穷举声明**：`outputs/procuraAI-*.md` 9 份外部诊断报告的建议未逐条投影；这些报告未纳入 git 跟踪、跨机器 clone 后可能不存在，索引见资产登记第 11 节。补齐属 owner 动作，不以本表冒充全覆盖。

## 3. 开口项清单（真正还需要人拍板 / 交付的事）

| # | 开口项 | 类别 | 状态 | 验收判据（可机器验证优先） | owner |
|---|--------|------|------|---------------------------|-------|
| D-01 | L38–L43 关键 SLO 数值冻结（O2） | 数据 / 压测 | `Accepted + 外部依赖` | 真实压测链接 + 冻结台账 owner 签名；当前仍标「草案」，不得伪造 | 未指派 |
| D-02 | RA-2 可见性矩阵会签（O4） | 审批 | `Accepted + 外部依赖` | `总体 §9.1` 四角色会签栏实名签署；门禁 A10.10 守结构不守签名 | 未指派 |
| D-03 | P-RFC-1 实名署名 + ≥1 真实跨域接入 PR（O5） | 跨域交付 | `Accepted + 外部依赖` | 平台 RFC §5.1 署名 + 可点开的 PR 链接 | 未指派 |
| D-04 | ATAM 质量属性最终优先级（O6） | 评审决策 | `Accepted + 外部依赖` | 排序会纪要 + 签字；当前仅 H/M 工程预排序 | 未指派 |
| D-05 | 跨域 ACL 交付 F-A（C-D） | 代码交付 | `Accepted + 外部依赖` | INBOUND 事件由「接口预留」转「运行期可触发」的源码证据 | 未指派 |
| D-06 | 存量 `PurchaseOrderPayHandler` 硬隔离开关（F-E） | 代码交付 | `Landed`（2026-11-20） | 已落地：装配层 fail-closed 硬隔离（缺省即禁用）；证据 `bone-app-purchase/boot/PaymentIsolationProperties.java`（开关默认 false）、`PayHandlerConditionalRegistration.java`（`@ConditionalOnProperty` 单点注册）、`PurchaseComponentScanConfiguration.java`（REGEX 排除）、`LegacyPaymentIsolationReporter.java`（启动期日志）、`PurchaseApplicationAutoConfigurationWiringTest.java`（双态断言）；模块 25 例测试全绿（存量 `PurchaseOrderPayHandlerTest` 4 例未改）。Comet change `procurement-legacy-payment-hard-isolation` | 未指派（用户 2026-11-20 明确授权本轮落地，未伪造 owner 签署） |
| D-07 | G1–G16 AI 原生能力逐项立项 | 立项 / 评测 | `Proposed` | 逐项立项 + 通过评测集 + owner 排期；当前 Proposal 不实现 | 未指派 |
| D-08 | 法源射程 ADR 法务结论 | 合规决策 | `Proposed` | 法务书面结论；未出具前六条红线仅为设计约束；**裁决范围须含**主体适用性与场景射程、**以及 `D3-v2 §3.1` C-2 按客户属性叠加算法备案 / 安全评估材料包的适用条件**（2026-11-14 补注）；结论出具前 `法源射程分层与自治边界-ADR.md` 档 B 保持 `DesignOnly` 不可执行 | 未指派 |
| D-09 | D3-v2 契约冻结 / `t_proc_` DDL 落库 | 设计态边界 | `Proposed` | 冻结评审 + 迁移脚本；**当前禁止写 `bone-init.sql`** | 未指派 |
| D-10 | 模型效果测评 / 漂移闭环 | 数据 / 评测 | `Proposed` | 评测集从 `seededDraft` 转正 + 漂移闭环证据 | 未指派 |
| D-11 | frontmatter 守护接入 CI（本轮新增补充判据） | 门禁 / L3 | `Landed`（2026-11-20） | 已落地：新增 `frontmatterGate`/`frontmatterProbe` 两 key，`ci.yml` 6 条 blocking（+frontmatter 主判据与注入反证）、`check.sh` G0 快反馈（+frontmatterGate）、一键脚本全集（+两命令）；证据 `tools/check_procura_alignment.py` 常量区/`_cmd_key`/3 条 A10.17 注入反证、三入口源码。对齐 10/10、自检 149/149（探针 146→149）、frontmatter 守护 33 份文档 + 7/7 自检；3 条反证实测删任一入口即转红。用户 2026-11-20 L3 授权，未改供应链 target 态、未回写历史快照 | 未指派（用户 2026-11-20 授权本轮落地，未伪造 owner 签署） |
| D-12 | 旧 POC 族物理迁 `archive/`、三张登记表收敛、唯一删除候选处置 | 治理 / L3 | `Accepted + 外部依赖`（探针已补 2026-11-20；物理迁移与 A10 铁律冲突，转 backlog） | 已落地无冲突子项：`tools/check_procura_alignment.py` 新增 A6 负向探针 `m_legacy_file_dropped`（模拟旧族少一份 → A6「资产集合不完整」转红），自检 151/151。**未落地（与 A10 铁律冲突，禁伪造）**：物理迁 `archive/` 会改变 `BASE.glob('采购AI-*.md')` 扫描结果与 `BASE/name` 可达性，击穿 A10 把旧族全文当反向守护语料的前置（资产登记:97 / CHANGELOG:163 / outputs 复诊均定性黄区/红区「不做」）；三表物理收敛与删除候选处置须 owner 裁决具体文档（当前仅 D3-v1 已物理合并移除先例）。`LEGACY_DOCS` 保持 glob 锚定（fail-closed 已有效），frontmatter `LEGACY` 同源 + `len<8` 守护在。| 未指派（用户 2026-11-20 L3 授权探针部分，物理迁移待 owner，未伪造） |
| D-13 | CI 软件供应链基线加固（第十八轮只登记现状） | 安全 / L3 | `Accepted + 外部依赖`（CIS-1 已落地 2026-11-20；CIS-2/3/4/5 + Scorecard 待 owner） | 已落地 CIS-1：`.github/workflows/ci.yml` 顶层 `permissions: contents: read` + docker-build job 级 `security-events: write`（SARIF 上传所需，GitHub 官方文档实证）；文档 §10 CIS-1 `permissions: 0→2`；注入反证 `m_ci_drop_permissions`（删顶层块即转红）。YAML 解析 + 门禁存活 + 对齐 10/10 + 自检 150/150 实证。**未落地（不伪造）**：CIS-4 20 个 `uses:` pin 全长 SHA（缺 owner 验证的官方 SHA，禁 fork）、CIS-2/3 timeout/concurrency、OpenSSF Scorecard workflow（全仓零配置，须另行立项）——保留 owner 决策。用户 2026-11-20 L3 授权 CIS-1，owner 签署未伪造 | 未指派（用户 2026-11-20 授权 CIS-1 落地，未伪造 owner 签署） |
| D-14 | AI 原生运行时契约层 X1–X4 冻结 + A·B·C 分级重排 | 立项 / 契约 / 合规 | `Proposed` | ① X2 执行层 + X3 审计可解释三合一契约为放开任何自治档的硬前置，须与 SOX / GDPR Art.22 / EU AI Act 逐项对齐并出字段级 schema；② Intake（N1）/ 品类分类（G10）/ 辅助评标（M4）提前与 G1/G3/G7/G8 异常预警子类拆档，须先补齐 `D3-v2 §1.1` 能力码 + evalset 专属负向 + 蓝图 §7 登记总表 + `总体 §12.3` W-AI 估工，且 F1–F4 验收；③ 重排后一律 `ADVISORY_ONLY`、禁授标 / 禁准入定性，M4 挂 G15 公平护栏与 T3 三方会签；④ 新增门禁判据须按 D-11 四入口一致流程（含注入必红反证）另行立项 | 未指派 |
| D-15 | AI 评测集 27→200 扩容与转正（批次2 转 backlog） | 数据 / 评测 | `Proposed` | 双人标注 + Kappa≥0.8 + 模型选型与通过线冻结，`check_procura_evalset.py` 的 `status=seededDraft` / `scalePlan.status=notStarted` 须由 owner 裁决后改；当前不得伪造 200 例或改门禁下界 | 未指派 |
| D-16 | D3-v2 AI 子能力契约冻结（M1/M5/M7/G10 等 8 码） | 契约 / 设计态 | `Proposed` | owner 冻结评审：`ProcurementAiOrchestrationPort` 方法签名 / DTO / 端点登记 + `D3-v2 §1.1` 八码契约；冻结前工程只做三态 fail-closed / 披露 / rules_only 降级骨架，不接真实模型、不声明效果达标 | 未指派 |
| D-17 | 业务 AI 真模型接入与效果达标声明 | 模型 / 评测 | `Proposed` | 依赖 D-15（评测转正）+ D-16（契约冻结）+ D-10（漂移闭环）；接入后仍须 `ADVISORY_ONLY`、AI 仅建议、人工终决，禁自动授标 / 自动准入定性 | 未指派 |
| D-18 | 自动结案 / 自动放款 / AI 授标红线确认 | 合规决策 | `Accepted + 外部依赖` | 资产登记第 5 节 + R2 + ADR-06 撤销链已定「一律禁止」；批次4 源码中 `awardByHuman` / `approveByHuman` 已强制具名操作人，红线在工程侧已落地，最终以法务书面结论（D-08）收口 | 未指派 |
| D-19 | 结构化资产基座（F1⑤ 结构化率口径） | 数据 / 评测 | `Proposed` | 四类结构化资产（报价行 / 合同条款 / 价格基线 / 供应商能力画像）各出字段级清单与结构化率基线，并与 `04 卷 §5.3`、`01 卷` `vendor_profile` 字段对齐；未达标前 G1 / G2 / G3 / G4 不得标为「可训练输入」 | 未指派 |
| D-20 | 审批智能体（报价 / PO / 接收审批）能力码登记 | 立项 / 契约 | `Proposed`（阻塞于 D-16） | **不得擅自在 `D3-v2 §1.1` 增第九码**；须由 owner 裁决「并入 M1/M2 端口」还是「登记新码」，并补评测集专属负向 + `总体 §12.3` W-AI 估工；裁决前只做「决策要素透出」叙述、不接真实模型 | 未指派 |
| D-21 | 业务身份配置挂载点（`business_identity_id`） | 架构 / 审批 | `Proposed` | ADR 通过 + `总体 §9.1` 矩阵版本 bump + RA-2 四角色会签（D-02）；验收：隔离 / 共享两态解析有单测，缺 `business_identity` 时 fail-closed 且不改变任何数据可见性 | 未指派 |
| D-22 | 商业化客户分层与第二曲线（数据服务 / 供应商网络 / 租赁） | 商业化 | `Proposed` | 三档客户（千亿级 / 百亿级 / 4 亿级）与基础 / 标准 / 高级版映射有书面判据；第二曲线不得虚构成交价，须 owner 财务回填成本口径后再定价 | 未指派 |
| D-23 | 配置化交付与审批流自助 playbook（三层交付 / PD 自交付） | 交付 / 产品化 | `Proposed` | 三层交付（配置→低代码→扩展）、审批流 PD 自交付、0 条件接入、90% 标准接口为历史材料实证方向；须出「标准接口清单 + 配置化覆盖判据 + 扩展点白名单」并由 owner 估工，关联 D-22 商业化分层；未出书面判据前不得声称「产品化交付已达成」 | 未指派 |
| D-24 | 内控体检规则集（拆单 / 倒签 / 未签合同先履约等） | 内控 / 规则 | `Proposed` | 六条纯规则（拆分规避审批、合同倒签、未签合同先履约、超长未接收、超长未付款、未归档）只出确定性命中与人工跟进线索，**不涉模型、不自动定性、不改变任何授信或准入结论**；03 卷 §13 判定表已登记行为与 fail-closed，规范编号待 owner 在企业源 ER 库分配（不占用冻结的法规源 R01–R25），可纳入 M2 候选但须法务 / 内控会签 | 未指派 |

> **D-23 / D-24 来源（2026-11-26，开口项自查 N1/N4、N2）**：N1「配置化交付与自交付率」在全族仅上一轮登记说明出现，是把 FDE 人力生意转为产品生意的唯一杠杆，与 D-22 强相关；N4「审批流 / 规则自助配置 playbook」并入 D-23，避免重复。N2「内控体检规则集」（拆单 / 倒签 / 未签合同先履约 / 超长未接收 / 未付款 / 未归档）此前「拆单」「倒签」全族零命中，纯规则、不涉模型、ROI 高，作为 M2 候选登记。二者均只登记开口项、不改能力码与红线、规范编号待 owner 裁决。

> **D-19–D-22 来源（2026-11-26，历史采购中台材料对标）**：对 2017–2019 采购中台/平台材料（组织业务身份、配置化交付、智能化阶梯、智能审批三件套、成本基线与供应商能力矩阵、体验分层）做对标后，**只登记为开口项**，不新增能力卡 / 能力码、不改 `D3-v2 §3` 红线、不改 `总体 §9.1` 矩阵冻结语义、不动 CI 三入口判据集合。其中 D-20 若涉及新能力码，属 L3 + D-16 流程，未裁决前不得实现。

> 开口项状态**只在本表维护**，不要回写 CHANGELOG 的历史轮次；状态变更须注明日期与依据。D-11/D-12/D-13 涉及 `.github/workflows/ci.yml` 与主门禁脚本号段，属 L3，执行前须架构评审；**D-14 为 2026-11-14 业界校准登记**（见 `AI接入判定与AI原生内核补充设计.md §7`），本轮只登记设计、未改 A·B·C 清单、未新增能力码；新增内容不在 CI 三入口判据集合内（A10.17 判据集合与门禁常量均未改）。

> **D-06 状态变更（2026-11-20）**：由 `Accepted + 外部依赖` 转 `Landed`。依据为 Comet change `procurement-legacy-payment-hard-isolation`：在存量 `bone-app-purchase` 装配层落地 fail-closed 硬隔离（开关默认 false + 条件注册 + 启动期日志 + 双态装配测试），25 例模块测试全绿且存量付款处理器 4 例守卫测试未改。用户 2026-11-20 明确授权本轮落地，owner 签署未伪造；未改 CI 三入口判据集合、未动 DDL、未回写 CHANGELOG 历史快照。

> **D-12 状态变更（2026-11-20）**：由 `Proposed` 转 `Accepted + 外部依赖`（部分落地）。源码实证确认「物理迁 `archive/`」与 A10 守护铁律**结构性冲突**：`LEGACY_DOCS`（tools/check_procura_alignment.py:220-221）以 `BASE.glob("采购AI-*.md")` 动态扫描顶层并按 `BASE/name` 读全文，A6(:1003)/A10.1(:1766-1782) 依赖 8 份旧族全文作反向守护语料；物理移动会令 glob 变空/变小而转红或失守。仓库自身已定性该迁移为黄区/红区「不做」（资产登记:97、CHANGELOG:163、outputs 复诊:1101）。故仅落地不冲突的验收子项——补 A6 探针 `m_legacy_file_dropped`（删一份旧族语料即转红），自检 151/151 实证。物理迁移、三表物理收敛、删除候选处置转 backlog 待 owner。未回写 CHANGELOG 历史快照、未伪造 owner 签署。

> **D-13 状态变更（2026-11-20）**：由 `Proposed` 转 `Accepted + 外部依赖`（部分落地）。用户 L3 授权后仅落地 CIS-1 最小权限：ci.yml 顶层 `permissions: contents: read` + docker-build job 级 `security-events: write`；依据 GitHub 官方文档（SARIF 上传需 security-events scope，job 级 permissions 可覆盖顶层）确认不会破坏 codeql upload-sarif。文档 §10 边界段同步声明 CIS-1 已加固、CIS-2/3/4/5 仍待 owner。新增 `m_ci_drop_permissions` 负向探针（150/150 自检）；修正 `m_cis_fake_current` 与 `m_cis_supply_fixed_ok` 两处硬编码旧基线（permissions: 0）以适配新基线 2。CIS-4 action pin 缺 owner 验证的官方 SHA 输入、Scorecard 需独立立项，均**未伪造**，故整体不标 Landed。未回写 CHANGELOG 历史快照、未改 CI 三入口判据集合语义之外的内容、未伪造 owner 签署。

> **D-11 状态变更（2026-11-20）**：由 `Proposed` 转 `Landed`。依据为用户 L3 授权后落地的 frontmatter 三入口接入：`tools/check_procura_alignment.py` 的 `GATE_KEYS`（5→7 key）/`FAST_GATE_KEYS`（3→4）/`CI_PROCURA_STEP_MIN`（4→6）/`_cmd_key`/`GATE_CMD_RE` 五处，`ci.yml` +2 blocking step、`scripts/check.sh` G0 +1 快反馈、一键脚本 +2 命令，并补 3 条 A10.17 注入必红反证（删任一入口判据即转红，实测命中）。`--sync-manifest` 同步探针数 146→149。同步更新 frontmatter 脚本 docstring / AGENTS.md / README 接入陈述，A10.16 陈旧表述守护仍绿。本轮未改 CI 供应链 target 态（D-13）、未物理迁移旧族（D-12）、未回写 CHANGELOG 历史快照、未伪造 owner 签署。

> **D-14 一次裁决所需最小输入（2026-11-14 补齐）**：① X1–X4 字段级契约草案已由 `AI接入判定 §8` 给出，裁决时须逐字段确认或驳回，并指定契约冻结的 owner 与版本戳；② A·B·C 重排需先交 `D3-v2 §1.1` 能力码登记（N1 / G10）、三者 evalset 专属负向用例、蓝图 §7 登记总表修订、`总体 §12.3` W-AI 独立行估工，以及 F1–F4 验收证据；③ M4 另需 G15 公平护栏与 T3 三方会签验收，且三者一律 `ADVISORY_ONLY`、禁授标 / 禁准入定性；④ 若把 §8 反证规格纳入 CI 三入口判据集合，须按 D-11 四入口一致流程同步 `FULL_GATE_KEYS`、门禁常量与注入自检探针（L3）。**在 ①②③④ 任一未闭合前，D-14 保持 `Proposed`、A·B·C 现行清单不改。**

## 4. 已撤销决策（保留证据链，禁止删除）

| 决策 | 撤销依据 | 现行结论 |
|------|----------|----------|
| ADR-06「低风险 AI 自动归档」 | 资产登记第 5 节 AI 合规裁决 + R2 | AI 仅辅助、人工最终决策；自动结案 / 自动放款 / AI 授标一律禁止 |
| 每客户独立私有化部署 / 独立库 / 不套 DDD | 资产登记第 7 节 | 单应用统一发布 + 行级多租户，遵循 BONE DDD 与平台约束 |
| `AUTO_APPROVE` 作为采购域能力码 | 第四轮台账（域适配诊断） | 从理赔域复制的断言已剔除，评测集按采购域重做 |

## 5. 边界

- 本表不参与业务裁决，不新增真源；与资产登记冲突时以资产登记为准。
- 本表状态是**当前投影**，CHANGELOG 各轮 `N/N` 与「已提交 / 已推送」描述均属当时快照。
- 未经用户明确授权，不执行 git 写操作；不在 `outputs/` 落新诊断报告（A10.27 要求本机新增报告须登记）。
