---
change: synapclaim-liability-rule-json-dual-read
design-doc: openspec/changes/synapclaim-liability-rule-json-dual-read/design.md
base-ref: e3c940b9ae0c2ab654c9c2d48e5300a351602e5f
archived-with: 2026-11-10-synapclaim-liability-rule-json-dual-read
---

# 实施计划：M2 ③ 3a 双读比对 + 剩余 L3 项收口

<!-- comet-task-authority: openspec/changes/synapclaim-liability-rule-json-dual-read/tasks.md -->

本计划不复制 checkbox，完成状态一律以 `tasks.md` 为准，按 `<!-- comet-task-ref:<id> -->` 映射。下文每个阶段只列任务 ID、范围、依赖、验收与失败处理。

## 执行配置

- `build_mode=autonomous` / `tdd_mode=tdd` / `review_mode=standard` / `isolation=current`（dev）/ `subagent_dispatch=null`。

## 全局约束

- 零行为变更：默认配置下不解析 `liability_rule_json`，赔付金额与改动前逐分相同。
- 无 DDL、无迁移、无存量回填；`0123` 脚本、manifest、`bone-init.sql`、5 个 SQL 模板保持 ② 的形态，本 change 改动它们即为超范围。
- 分层：回落判据只在 `domain`，读开关与日志只在 `application`；`domain` 不得出现 Spring / Jackson 依赖。
- 装配点唯一：金额三值只经 `LiabilityRule.fromLegacy(...)` 装配，不新增第二套算法。
- 提交纪律：共享树，`git diff --cached --name-only` 核对，只提交自己路径。
- 回退路径：读开关是唯一开关，关掉即回到 ② 的行为，无需回滚代码。

## 阶段 A：基线与红用例（tasks 1.1–1.3）

- 范围：文档台账更正（O-06 触发条件、§9.1.1 归属措辞）；新增失败用例；复核 ② 基线。
- 依赖：无。
- 验收：`check_all.py` 全绿；红用例失败原因指向「回落逻辑缺失」而非夹具错误；迁移清单、SQL 列覆盖、模块编译三者通过。
- 失败处理：若「红用例」实际是绿的，说明判据无效，先改判据再加实现，不把绿当起点。

<!-- comet-task-ref:8401140f-f51e-4a87-ac95-b6bfef43e09e -->
<!-- comet-task-ref:01a27704-98d2-4309-97eb-61ff610b9750 -->
<!-- comet-task-ref:a2e33c9b-7de1-4999-bb5f-24fcb3819adf -->

## 阶段 B：领域按键回落（tasks 2.1–2.3）

- 范围：`LiabilityRuleJson.resolve(key, scalar)` 纯函数；`LiabilityRule.fromLegacy` 增 JSON 重载，复用同一 `build(...)`。
- 依赖：阶段 A 的红用例。
- 验收：单键回落 / A 类双空拒绝 / 单 B 类键 / 历史 NULL 行四类用例转绿；`ArchitectureTest` 分层判据全绿；`fromLegacy` 调用点数与改动前一致。
- 失败处理：B 类键若被当 0 或被直接拒绝，用「只配 1 个 B 类键」反例钉住语义为「不施加约束」。

<!-- comet-task-ref:1c96d52e-5296-45d1-b612-ae1eb9cca5a0 -->
<!-- comet-task-ref:b00def20-5871-4874-93ea-b312f8d4ce03 -->
<!-- comet-task-ref:b73576be-4c2c-452a-aae4-ec48588d935b -->

## 阶段 C：读开关与双轨比对（tasks 3.1–3.4）

- 范围：`bone.claim.liability.rule-json-read.enabled`（默认 false）；模式值以参数传入 domain；DUAL_READ 双轨算、返回旧路径、差异只记日志与计数；等价判据 `compareTo`；非法输入 fail-closed。
- 依赖：阶段 B。
- 验收：默认关闭时 JSON 解析次数为零；注入差异仍返回旧金额且必产含差异键的日志；`90.00` vs `90.0` 不报差异、`90` vs `80` 必报；三类负向用例断言错误码与键名/版本号。
- 失败处理：比对在装配点同步执行，日志异常一律不得冒泡成业务失败（fail-closed 场景除外，需显式报错）。

<!-- comet-task-ref:b5ff780b-b0a2-41ba-bd14-5b4d836e2eaa -->
<!-- comet-task-ref:54caf63f-2aaa-4328-a804-a2fd552499ce -->
<!-- comet-task-ref:fd77197e-11a5-4499-a918-952b2d0c792f -->
<!-- comet-task-ref:faa325f1-1252-46da-9ed4-20f26617212b -->

## 阶段 D：判别力与全量回归（tasks 4.1–4.4）

- 范围：三组专属反例；两次负向变异（整列判断、删 JSON 分支）；模块全量测试。
- 依赖：阶段 C。
- 验收：正确实现下反例全绿；两个变异各自至少 1 例转红，还原即绿，保留变异前后输出对照；`mvn -o -pl bone-apps/synap-claim/synap-claim-app -am test` BUILD SUCCESS，`ArchitectureTest` / `PortAdapterDisambiguationTest` 不退化。
- 失败处理：变异不转红说明用例无判别力，补判据后重跑，不接受「跑过」当证据。

<!-- comet-task-ref:01e755a6-44d2-4287-9dad-9804ac0edba5 -->
<!-- comet-task-ref:94afb784-d50d-4fe2-b98f-218ce5291946 -->
<!-- comet-task-ref:dac2543a-44d6-47a3-b545-c45e4fb89288 -->
<!-- comet-task-ref:8073e3e2-191d-4d7f-a299-5368e346ed7d -->

## 阶段 E：L3 两项收口为可判定阻塞（tasks 5.1–5.3）

- 范围：`engine_version` 读取点检索留证（预期零）；PITest 离线制品缺失留证；诊断报告 §14.1 状态改写。
- 依赖：无（可与 A–D 并行，实际串行以避免共享树冲突）。
- 验收：文档门禁全绿；两处措辞均含触发条件与复核命令，且不暗示已生效。
- 明确不做：不落实体、不加列、不写迁移、不接 pom / check.sh / allowlist。

<!-- comet-task-ref:3ae39c05-1dba-4d7e-8d9e-3a7a030acbe2 -->
<!-- comet-task-ref:0b5a118d-3da1-42f6-ab7f-fb5f81e00870 -->
<!-- comet-task-ref:f9815031-e58c-4710-910b-b419facb4675 -->

## 阶段 F：文档同步与全量自检（tasks 6.1–6.3）

- 范围：§9.1.1 开工状态、§13 台账相关行；确认 ② 产物未被改动；`./scripts/check.sh`。
- 依赖：阶段 A–E。
- 验收：`check_all.py` 全绿、REQUIRED 判据仍命中；`git diff --stat` 只含本 change 路径；`check.sh` 末行「✅ 本地自检通过！」。

<!-- comet-task-ref:efac5643-042d-49c8-bd3b-bf01fa61906d -->
<!-- comet-task-ref:cf3d3491-6582-48ae-bbc4-26f829ee0397 -->
<!-- comet-task-ref:0200c698-cebe-4e3d-99c9-45da1dc6cbf0 -->

## 阶段 G：提交与交付（tasks 7.1–7.2）

- 范围：按共享树纪律提交并双远程推送。
- 依赖：阶段 F。
- 验收：提交信息含 O-06 更正依据与 L3 阻塞留痕；`git show --stat` 范围正确；`git ls-remote origin refs/heads/dev` == `HEAD`。

<!-- comet-task-ref:9d8daf33-6b00-4985-866a-0b14b0500ec8 -->
<!-- comet-task-ref:d8b8ff3f-b1ca-4e23-b338-e8491459774e -->
