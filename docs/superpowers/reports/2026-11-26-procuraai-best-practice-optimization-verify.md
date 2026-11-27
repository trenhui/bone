# 验证报告：procuraai-best-practice-optimization

> 阶段：Verify（tweak / light）　|　日期：2026-11-26　|　工作区：`dev` / current
> 本报告记录本次 change 全部真实检查结果、独立审查结论与诚实标注的暴露面；不声称未实际执行的检查。

## 1. 任务完成核对

- OpenSpec apply 状态：`all_done`，9/9 任务勾选（含 Build 内补记的 4b.1 F1 探针修复）。
- 交付文件：优化建议方案（仓库外独立路径）、`DECISION-REGISTER.md`（D-23/D-24）、03 卷 §13 内控体检矩阵、蓝图 §4.3 S4 采集口径、`CHANGELOG.md` 第二十八轮台账 + 三处轮次索引。

## 2. 真实检查结果（本轮实际执行）

| 检查 | 命令 | 结果 |
|---|---|---|
| 主门禁对齐 | `python3 tools/check_procura_alignment.py` | **EXIT=0，10/10 全通过** |
| 负向探针自检 | `python3 tools/check_procura_alignment.py --self-test` | **EXIT=0，157/157 负向探针全部转红且命中预期 key，6 条正例未误杀** |
| 评测集门禁 | `python3 doc/ai-product/procuraAI/scripts/check_procura_evalset.py` | **EXIT=0，RESULT: PASS**（27 例 seed 红线，不守模型效果） |
| frontmatter 守护 | `python3 doc/ai-product/procuraAI/scripts/check_procura_frontmatter.py` | **EXIT=0，33 份文档全绿** |
| OpenSpec 严格校验 | `comet classic openspec -- validate procuraai-best-practice-optimization --strict` | **EXIT=0，Change is valid**（skip_specs 场景零 delta 被接受） |
| 运行时真值 | `python3 tools/check_procura_alignment.py --manifest` | `{"checks":10,"criteriaMax":"A10.29","indexCount":10,"positiveControls":6,"probes":157,"seedCaseCount":27}` |

## 3. 静态核对（A10 关键不变量）

- A10.16 黑名单：新增文案统一用「不在 CI 三入口判据集合内」，未引入「未接 CI」「本轮未改 CI」「三条 ProcuraAI* blocking」等精确串。
- A10.17 `FULL_GATE_KEYS` 与三入口判据集合：未改任何门禁常量、`ci.yml`、`scripts/check.sh`、`check-procura-ci.sh`。
- 能力码与红线：`D3-v2 §1.1` 八码集合不变，`D3-v2 §3` 人工终决红线不变，`总体 §9.1` 可见性矩阵不变；未新增 G 卡 / 第九码。
- 契约工件：`openapi.yaml` / `asyncapi.yaml` / evalset / DDL 未改。
- 决策表：D-18/D-19 之前的空行断表缺陷未被重演，D-23/D-24 插入后开口项表六列连续。
- F1 探针修复：`m_foundation_drop_column` 文本锚点恢复连续，self-test 实测该探针由假阴转为转红（157/157）。

## 4. 独立审查结论

`review_mode: off`（tweak 预设），按 SDK 方法记录跳过原因：本次为纯文档族登记与建议产出，无生产代码、无接口/数据模型变更；以主门禁 10/10 + self-test 157/157 的机器判据作为主要质量证据。Build 末尾的编辑后变化（tasks.md 4b.1、design.md 决策 7）已按 edit-review 提交 non-behavioral 判断并保留原批准（两份 proposalHash 均已 record，receipt status applied）。

## 5. 暴露面（诚实标注，不掩盖）

- 新增的 D-23/D-24、03 卷内控矩阵、S4 采集口径、建议方案均为**叙述性登记，不在 CI 三入口判据集合内**——注入删除后主门禁仍绿。纳判据须按 D-11 四入口一致流程（L3），留 owner。
- 建议方案以仓库外独立路径交付，未写入 `outputs/`（A10.27 要求本机新增诊断报告须登记 E-11 并改 `indexCount` 与门禁常量，属 L3）。
- 本轮仍为 **Proposal 设计态**：`bone-procurement-*` 0 模块、`t_proc_*` 0 DDL、评测集 `seededDraft`；CI 绿 ≠ AI 可上线。

## 6. 结论

全部可执行验证通过，文档改动与任务清单一致，无 CRITICAL/IMPORTANT 未解决项。验证通过；归档与交付方式由 Archive 阶段按用户当前授权处理。
