---
status: Active
layer: entry
kind: contract
truth-for: AI / 人工编码协作合同：真源顺序、门禁命令、禁改清单
updated-on: 2026-11-12
---

# AGENTS.md — procuraAI 文档族协作合同

> **适用范围**：本目录（`doc/ai-product/procuraAI/`）及子目录。与仓库根 `AGENTS.md` 叠加，冲突时以更靠近被编辑文件的规则为准。
> **性质**：导航与行为约束，**不是业务真源**。业务结论一律以第 2 节列出的真源为准，本文件不复述结论细节。
> **对象**：AI 编码助手与人工开发者。写任何代码、契约或文档之前先读完第 1–4 节。

## 1. 定位与不可越过的红线

- procuraAI 是**采购域内的 AI 子能力**（嵌入 SRM/PMS），**不是独立可交付产品**；仅 AI 运行时因 Python + GPU 技术栈不匹配而独立部署。
- **AI 仅辅助、人工最终决策**；自动结案 / 自动放款 / AI 授标一律禁止。旧 `ADR-06`「低风险自动归档」已撤销，不得作为任何实现依据。
- 本族整体是 **Proposal 设计态**：`t_proc_*` 未落库、`bone-procurement-*` 模块未建、模型效果未测评、法务结论未出具。**文档写了 ≠ 已实现**。
- CI 绿只证明文档族自洽，不等于 AI 可上线，也不等于 CI 供应链基线已加固。
- `UNKNOWN` 不得压成「AI 未参与」；金额一律 Money 对象（`amount` 字符串 + `currency`），禁止裸数字。
- **契约先行**：编码时先读 `contracts/openapi.yaml` 与总纲附录 A–C，再读 LLD 散文；LLD 里的数值多为「草案」，不得当承诺 SLA。`asyncapi.yaml` 是派生物，只读。
- LLD 六卷 §4/§5/§6/§7/§9/§11/§12 各有一段**逐字相同的通用基线声明**（错误码 / 事件注册表 / 分页 / 冻结引用），属受控复用，用于保证只读单卷也能看到契约真源约束；**读一次即可，不要拿它推断模块差异，也不要擅自删除以「消除重复」**。

## 2. 真源读取顺序（冲突时自上而下，命中即止）

| 序 | 冲突类型 | 唯一裁决者 |
|----|----------|------------|
| 1 | 资产状态 / 文档是否已作废 | `文档资产登记与真源裁决.md §3` |
| 2 | 平台规范（HC / CORE、事件信封 / 幂等 / DLQ） | `doc/architecture/` 平台约束真源 |
| 3 | 平台能力「是否真的已实现」 | `采购域-BONE框架能力对齐与落地基线.md` 的 `file:line` 源码实证 |
| 4 | 领域语义（聚合 / 状态机 / 事件语义 / 边界） | `采购领域架构设计方案.md` |
| 5 | 技术约束 / 部署 / NFR / 事件 payload / WBS | `采购总体架构方案.md §7.1`、`§12` |
| 6 | AI 子能力契约（端点 / payload / 合规 / 错误码映射） | `采购AI-接口契约与数据字典-v2.md` |
| 7 | 实现级概念（表 / 字段 / 端点 / 事件 / 权限码） | `detailed-design/00-总纲与统一约定.md` 附录 A–F |
| 8 | 事件契约工件 | **总纲附录 B 是生成源**；`asyncapi.yaml` 是派生物 |

**按任务类型的最短读取路径**：

| 任务 | 先读 | 再读 |
|------|------|------|
| Controller / DTO | `detailed-design/contracts/openapi.yaml` | 对应卷 §6、总纲附录 A / C |
| 事件 Producer / Consumer | 总纲附录 B | 对应卷 §7、`generate_asyncapi.py --check` |
| AI 接入 / 评测 | `D3-v2 §2.1`（字段名唯一真源）、§3（合规） | `eval/ai-decision-evalset.json` |
| DDL / 实体 | 对应卷 §4 | 总纲 §14 与附录 D（**冻结前不得写 `bone-init.sql`**） |
| 新增文档 / 改状态 | `文档资产登记与真源裁决.md §3`、`§4.3` | README 索引与 frontmatter；改后跑 `check_procura_frontmatter.py` |
| 判断「还有哪些没做 / 谁来做」 | `decisions/DECISION-REGISTER.md` 第 3 节开口项 | CHANGELOG 对应轮次（当时快照） |
| AI 上下文导航（只读索引） | `llms.txt` | 本文件（行为约束以本文件为准） |

## 3. 门禁命令（改动后必跑，以退出码判定）

在仓库根执行：

```
python3 tools/check_procura_alignment.py
python3 tools/check_procura_alignment.py --self-test
python3 tools/check_procura_alignment.py --manifest
python3 doc/ai-product/procuraAI/detailed-design/contracts/generate_asyncapi.py --check
python3 doc/ai-product/procuraAI/scripts/check_procura_evalset.py
python3 doc/ai-product/procuraAI/scripts/check_procura_evalset.py --self-test
bash doc/ai-product/procuraAI/scripts/check-procura-ci.sh
python3 doc/ai-product/procuraAI/scripts/check_procura_frontmatter.py
python3 doc/ai-product/procuraAI/scripts/check_procura_frontmatter.py --self-test
```

以上均在仓库根执行。**前 7 条**是 A10.17 锁死的三入口判据全集（一键脚本覆盖同一全集，是本地评审前的权威入口）；**后 2 条**是本族 frontmatter 语义守护，属**本地补充判据、未纳入 CI 三入口判据集合**——A10.17 以 `FULL_GATE_KEYS` 严格比对判据集合，擅自加进任一入口会立刻转红，接入 CI 需同步改 `ci.yml` / `check.sh` / 一键脚本 / 门禁常量与自检探针（L3，见 `decisions/DECISION-REGISTER.md` D-11）。改 frontmatter 或旧族 banner 后必须跑最后两条。注意：`| tail` 会吞掉退出码；中文路径 git 操作须带 `-c core.quotePath=false`。

## 4. 禁止修改 / 须先审批

- `detailed-design/contracts/asyncapi.yaml`：**派生物**，禁止手改。新增事件先改总纲附录 B → 重跑生成器 → A8/A10 校验 → 才写 Producer。
- `采购AI-*` 旧 POC 族（8 份 md + 5 张旧 SVG + `procuraAI-DEMO.html`）：已判历史归档 / 受限引用，**不得作为编码依据**。其中「每客户私有化部署 / 独立库 / 不套 DDD / ADR-06」等结论已被撤销，仅在带撤销标注的语境中保留。
- `.github/workflows/ci.yml`、`scripts/check.sh`、DDL 落库、删除文档、改门禁脚本号段：属 L3，须架构师审批。
- 不得把「登记」冒充「修复」，不得把 Proposal 数值写成承诺 SLA，不得伪造证据数字或 `file:line`。
- 未经用户明确授权，不执行任何 git 写操作（提交 / PUSH / 建分支 / 切分支）。

## 5. 文档状态与变更史

- 四态（资产登记 §2）：`真源 Active` / `受限引用 Restricted` / `历史归档 Archived` / `作废 Deprecated`。**状态是文档级**；单条款撤销见资产登记 §5。
- 每份 md 头部的 YAML frontmatter 是状态的**机器可读投影**，由 `scripts/check_procura_frontmatter.py` 做本地语义守护（四态合法 / 旧族 banner / 真源不得标废）；主门禁 A10 **尚未**守护 frontmatter，接入 CI 见 D-11。（`status` / `layer` / `truth-for` / `supersedes`）；与资产登记 §3 冲突时仍以资产登记为准。
- 历轮执行台账在 `CHANGELOG.md`，其中 `N/N` 全部是**当时快照**。当前态唯一真源是 `README.md` 的 `gateEvidenceManifest` 行（由 `--manifest` 推导、`--sync-manifest` 同步）。
- 外部诊断报告 9 份在 `outputs/procuraAI-*.md`，未纳入 git 跟踪；索引见资产登记 §11，跨机器 clone 后可能不存在。

## 6. 一句话检查

改动前问自己：**我依据的是第 2 节哪一份真源？门禁跑绿了吗？我有没有把设计态写成已实现？** 三问答不上来就不要动手。
