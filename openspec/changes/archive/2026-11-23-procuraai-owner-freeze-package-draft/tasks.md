# Tasks

## 1. 草案骨架与状态护栏

- [x] 1.1 在 change 目录创建 `owner-freeze-package-draft.md`，写清文档性质、六字段模板、四类材料目录及“不改真源状态”护栏；用本地文本检查确认四个章节标题与 `Draft / 待 owner 批准` 标记存在。 <!-- comet-task:90ef3225-a9c4-426c-9df2-e24071aaaa59 -->
- [x] 1.2 固化状态诚实断言：`evalset.status=seededDraft`、`scalePlan.status=notStarted`、`caseCount=27`、契约冻结状态不变、owner/法务/模型效果结论均为待填；用 JSON 字段读取与草案关键字检查验证没有伪造状态。 <!-- comet-task:1201dba3-e24b-44de-bc12-bdb12a0a9035 -->

## 2. 四类 owner 待签材料

- [x] 2.1 编写契约冻结评审清单，按 D3-v2 / LLD 六卷 / `contracts/openapi.yaml` / `contracts/asyncapi.yaml` 列差异、影响能力码、真源裁决链位置与待裁决动作；用每行含真源锚点、owner、验收判据、未闭合风险的本地检查验证。 <!-- comet-task:e4764cc9-af31-4450-b598-5ee9d15ee563 -->
- [x] 2.2 编写评测扩容计划，覆盖 27→200、P0/P1/P2、双人标注、第三方仲裁、Kappa ≥ 0.8、训练/验证/冻结三分离与通过线待冻结字段；用 JSON 与草案对照检查确认新增数值未被写成已达标。 <!-- comet-task:98eefb7d-808c-4874-aebf-f4d2836e023b -->
- [x] 2.3 编写法务书面结论输入清单，覆盖六条不可稀释红线、主体适用性、场景射程及 `D3-v2 §3.1` C-2 按客户属性叠加算法备案 / 安全评估材料包条件；用六条红线编号与 C-2 关键字检查验证无缺项。 <!-- comet-task:d5f5f38d-5a2a-431c-8989-e8ed8792f5ab -->

## 3. D-01 至 D-18 全量索引

- [x] 3.1 编写 D-01…D-18 逐项索引，每项给出当前状态、owner、验收判据、未闭合风险、真源锚点和待裁决动作，并回指前三类材料；用正则统计确认 18 个编号齐全且各出现一次。 <!-- comet-task:2ad3f9e3-5f9e-477f-90c2-5550738527c3 -->

## 4. 本地确定性与既有门禁复核

- [x] 4.1 执行一次性本地确定性检查，覆盖四类材料齐全、18 项编号齐全、状态诚实字段未改写、CI 三入口判据常量未变，并做删除 D-15 / 篡改 caseCount 的注入反证；用“正例通过、注入转红、还原通过”三类输出作为完成证据。 <!-- comet-task:3211e82b-4c4e-47ee-8c47-22c0745404fe -->
- [x] 4.2 真实运行 procuraAI 对齐、evalset、AsyncAPI、frontmatter、persistence、event-chain 与迁移清单既有门禁，证明本 change 未引入文档或契约回归；用各命令 PASS / 零漂移输出作为完成证据。 <!-- comet-task:ed0ef9a0-0b2c-4fd7-b2ca-97af6a0c7426 -->

## 5. 验收与交付准备

- [x] 5.1 完成唯一最终集成审查，重点检查草案未伪造 owner/法务/模型结论、未改 CI 判据集合、未改真源状态，并生成 verify 报告；用审查结论“0 CRITICAL / 0 IMPORTANT”和报告落盘作为完成证据。 <!-- comet-task:1e9943d1-10d8-4ddc-9911-f7a0811807a2 -->

## Workflow follow-up

- 完成 Comet Verify 的唯一最终集成审查后再归档。
- 归档交付方式沿用用户已给出的“提交并推送 origin/dev”授权；精确提交当前 change 文件，排除他人并行在途 change。
