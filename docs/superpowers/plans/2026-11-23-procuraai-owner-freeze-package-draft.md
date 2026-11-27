---
change: procuraai-owner-freeze-package-draft
design-doc: openspec/changes/procuraai-owner-freeze-package-draft/design.md
base-ref: 6577b293a1165dea5b4d741b3b989808fc3349d3
archived-with: 2026-11-23-procuraai-owner-freeze-package-draft
---

<!-- comet-task-authority: openspec/changes/procuraai-owner-freeze-package-draft/tasks.md -->

# Build Plan — procuraAI Owner 冻结包草案（实施口径唯一真源，2026-11-23）

> 本计划引用已批准的 `design.md` 与 `tasks.md`；任务完成状态一律以 tasks.md 为准，本计划仅以 `comet-task-ref` 映射任务 ID，不复制 checkbox。

## 1. 执行配置

`build_mode=autonomous` / `tdd_mode=tdd` / `review_mode=standard` / `subagent_dispatch=null` / `direct_override=false`。沿用用户已确认的 Build 配置；TDD 在本 change 中体现为“正例通过 / 注入转红 / 还原通过”的负向探针，standard 覆盖最终集成审查。

## 2. 基线事实（2026-11-23 源码实证）

- `eval/ai-decision-evalset.json` 保持 `status=seededDraft`、`needsOwnerReview=true`、`scalePlan.status=notStarted`、`caseCount=27`、`targetCases=200`。
- `doc/ai-product/procuraAI/decisions/DECISION-REGISTER.md` 登记 D-01…D-18；D-06 / D-11 已 Landed，但不得回写为 owner 签署。
- 契约真源为 `采购AI-接口契约与数据字典-v2.md`（§1.1 / §2 / §3.1）、`detailed-design/` 六卷与 `detailed-design/contracts/openapi.yaml`、`asyncapi.yaml`；冲突按 `文档资产登记与真源裁决.md` 八级链裁决。
- A10.17 以 `tools/check_procura_alignment.py` 的 `FULL_GATE_KEYS` 锁死 `.github/workflows/ci.yml` / `scripts/check.sh` / `check-procura-ci.sh` 三入口判据集合；本 change 不改这些常量。
- 本 change 不新增运行时接口、事件、权限点、DDL，也不接生产 DB / MQ。

## 3. 实施纪律

- 唯一新增业务产物：`openspec/changes/procuraai-owner-freeze-package-draft/owner-freeze-package-draft.md`；既有真源文件只读。
- 每项统一六字段：状态 `Draft / 待 owner 批准`、owner（未指派写“未指派”）、验收判据、未闭合风险、真源锚点（路径 + 章节 / JSON 字段）、待裁决动作。
- 状态诚实 fail-closed：不写 owner 签名、法务结论、模型达标数值、压测链接、契约 Frozen 状态。
- D-01…D-18 每个编号在索引中恰好出现一次；已落地项仅引用工程证据。
- 本地检查必须做注入反证：删除 D-15 转红、篡改 `caseCount` 转红，还原后转绿。
- 既有门禁只复核不改；发现失败先判断是否基线假红，不绕过门禁。
- 共享工作区精确 `git add`，排除两个 `synapclaim-*` 在途 change；push 前 fetch / rebase，不 force。

## 4. 任务映射

### 组 1 · 草案骨架与状态护栏

- 1.1 创建草案骨架、六字段模板、四类材料目录与不改真源护栏，并做文本存在检查。 <!-- comet-task-ref:90ef3225-a9c4-426c-9df2-e24071aaaa59 -->
- 1.2 固化 evalset 状态诚实断言与契约/owner/法务/模型结论待填字段，并用 JSON 与草案对照验证。 <!-- comet-task-ref:1201dba3-e24b-44de-bc12-bdb12a0a9035 -->

### 组 2 · 四类 owner 待签材料

- 2.1 契约冻结评审清单：D3-v2 / LLD 六卷 / OpenAPI / AsyncAPI 差异、能力码、裁决链位置与待裁决动作。 <!-- comet-task-ref:e4764cc9-af31-4450-b598-5ee9d15ee563 -->
- 2.2 评测扩容计划：27→200、P0/P1/P2、双人标注、第三方仲裁、Kappa ≥ 0.8、三分离、通过线待冻结。 <!-- comet-task-ref:98eefb7d-808c-4874-aebf-f4d2836e023b -->
- 2.3 法务书面结论输入清单：六条红线、主体适用性、场景射程与 `D3-v2 §3.1` C-2 条件。 <!-- comet-task-ref:d5f5f38d-5a2a-431c-8989-e8ed8792f5ab -->

### 组 3 · D-01 至 D-18 全量索引

- 3.1 逐项索引 18 个开口项，每项六字段并回指前三类材料；正则统计确认齐全且各一次。 <!-- comet-task-ref:2ad3f9e3-5f9e-477f-90c2-5550738527c3 -->

### 组 4 · 本地确定性与既有门禁复核

- 4.1 一次性本地确定性检查：四类材料、18 编号、状态诚实、三入口常量；正例 / 注入 / 还原三类输出。 <!-- comet-task-ref:3211e82b-4c4e-47ee-8c47-22c0745404fe -->
- 4.2 真实运行 procuraAI 既有门禁（对齐 / evalset / AsyncAPI / frontmatter / persistence / event-chain / 迁移清单），证明零漂移。 <!-- comet-task-ref:ed0ef9a0-0b2c-4fd7-b2ca-97af6a0c7426 -->

### 组 5 · 验收与交付准备

- 5.1 最终集成审查，确认 0 CRITICAL / 0 IMPORTANT，生成 verify 报告并准备归档与精确推送。 <!-- comet-task-ref:1e9943d1-10d8-4ddc-9911-f7a0811807a2 -->
