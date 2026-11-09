## 1. 契约与闸门基线（先写会红用例）

- [ ] 1.1 在 §13 台账更正 O-06 的升级条件（保留原条文、日期、理由、复核命令），并把 §9.1.1 ③ 的「`RuleJsonCodec.resolve()`」改为 domain 归属；验证：`doc/ai-product/synapClaim/tools/check_all.py` 全绿且台账条目仍被结构判据识别。 <!-- comet-task:8401140f-f51e-4a87-ac95-b6bfef43e09e -->
- [ ] 1.2 先写一组「只配 1 个键的 JSON，其余键仍取旧标量」的失败用例（此时实现不存在，用例应红）；验证：运行该用例集确认确实失败，且失败原因指向回落逻辑缺失而非夹具错误。 <!-- comet-task:01a27704-98d2-4309-97eb-61ff610b9750 -->
- [ ] 1.3 复核 M2 ①② 已入库基线（`0123` 脚本、manifest、5 个 SQL 模板、codec 与值对象）；验证：`python3 scripts/check-migration-manifest.py`、`scripts/check-sql-entity-column-coverage.py --check`、模块编译三者均通过。 <!-- comet-task:a2e33c9b-7de1-4999-bb5f-24fcb3819adf -->

## 2. 领域侧按键回落

- [ ] 2.1 在 `LiabilityRuleJson`（domain）实现纯函数 `resolve(key, scalar)`：A 类三键缺键回落旧标量、双空拒绝并报键名，B 类缺键表示不施加约束；验证：`LiabilityRuleJsonTest` 新增用例覆盖单键回落、A 类双空、单 B 类键、历史 NULL 行，且 domain 模块无新增框架 import（`ArchitectureTest` 通过）。 <!-- comet-task:1c96d52e-5296-45d1-b612-ae1eb9cca5a0 -->
- [ ] 2.2 为 `LiabilityRule.fromLegacy(...)` 增加接收 `LiabilityRuleJson`（可为 null）的重载，复用同一段 `build(...)` 装配链，保持既有 12 参数签名不变；验证：两个调用点编译通过，既有 `LiabilityRuleFormGuardTest` / 架构测试全绿。 <!-- comet-task:b00def20-5871-4874-93ea-b312f8d4ce03 -->
- [ ] 2.3 保持装配点唯一，确认没有第二套金额装配算法；验证：全仓检索 `fromLegacy` 调用点数量与改动前一致，新增调用全部指向重载。 <!-- comet-task:b73576be-4c2c-452a-aae4-ec48588d935b -->

## 3. 读开关与双轨比对

- [ ] 3.1 在 application 增配置 `bone.claim.liability.rule-json-read.enabled`（默认 false）与模式值参数，domain 不读配置、不感知 Spring；验证：默认配置下聚合 JSON 解析次数为零的用例通过，且 `ArchitectureTest` 分层判据全绿。 <!-- comet-task:b5ff780b-b0a2-41ba-bd14-5b4d836e2eaa -->
- [ ] 3.2 实现 `DUAL_READ` 双轨比对：两条路径都计算，返回结果始终是旧标量路径；差异写结构化日志（责任 ID / 版本 / 差异键 / 两侧数值）与计数指标，不落表、不阻断、不改金额；验证：注入差异的责任行仍返回旧金额，且能捕获到一条含差异键的日志。 <!-- comet-task:54caf63f-2aaa-4328-a804-a2fd552499ce -->
- [ ] 3.3 等价判据固定为 `BigDecimal.compareTo(...) == 0`（不比字符串），B 类维度按存在性与解析后结构比对；验证：`90.00` 与 `90.0` 判等不产生差异日志，`90` 与 `80` 判不等并产生差异日志。 <!-- comet-task:fd77197e-11a5-4499-a918-952b2d0c792f -->
- [ ] 3.4 非法 JSON / 未知版本 / 未知键在开关打开时 fail-closed 并报字段级或键级错误，不得静默回落旧路径；验证：三类负向用例分别断言错误码与错误文本中的键名/版本号。 <!-- comet-task:faa325f1-1252-46da-9ed4-20f26617212b -->

## 4. 判别力验证（双向变异）

- [ ] 4.1 补齐专属反例：只配 1 个键的 JSON（整列判断写法必红）、只配 1 个 B 类键（其余不得当 0 或拒绝）、历史行 JSON 为 NULL 逐笔等于迁移前；验证：三组用例在正确实现下全绿。 <!-- comet-task:01e755a6-44d2-4287-9dad-9804ac0edba5 -->
- [ ] 4.2 负向变异：把回落实现改成「JSON 非空则全走 JSON」⇒ 至少 1 个用例转红；还原即绿；验证：记录变异前后输出对照，证明用例集有判别力而非「跑过」。 <!-- comet-task:94afb784-d50d-4fe2-b98f-218ce5291946 -->
- [ ] 4.3 负向变异：删掉 JSON 分支 ⇒ 双轨比对用例转红；验证：同上，并确认差异日志正向钉子（注入差异必产日志）同时存在。 <!-- comet-task:dac2543a-44d6-47a3-b545-c45e4fb89288 -->
- [ ] 4.4 全量回归：`mvn -o -pl bone-apps/synap-claim/synap-claim-app -am test`；验证：`BUILD SUCCESS`，且 `ArchitectureTest` / `PortAdapterDisambiguationTest` 不退化。 <!-- comet-task:8073e3e2-191d-4d7f-a299-5368e346ed7d -->

## 5. 剩余 L3 项收口为可判定阻塞

- [ ] 5.1 `engine_version`：检索理赔模块读取点并留证（结果应为零），不落实体、不加列、不写迁移；在 §13 台账与诊断报告交人清单写明触发条件（消费方落地时同批入库）与复核命令；验证：文档门禁全绿且台账条目含可机检的触发条件文本。 <!-- comet-task:3ae39c05-1dba-4d7e-8d9e-3a7a030acbe2 -->
- [ ] 5.2 PITest：核实本地依赖仓库无制品、`mvn -o` 不可解析并留证，不接 pom / check.sh / allowlist；台账写明触发条件（CI 联网 + 路由开关生产启用评审前）与复核命令；验证：离线构建仍全绿，且没有任何文档把它写成已生效门禁。 <!-- comet-task:0b5a118d-3da1-42f6-ab7f-fb5f81e00870 -->
- [ ] 5.3 把两项在诊断报告 §14.1 的状态由「未做」改为「有触发条件的阻塞」，并注明 owner 与复核方式；验证：文档 `check_all.py` 19/19 全绿，人工复核两处措辞不再暗示已生效。 <!-- comet-task:f9815031-e58c-4710-910b-b419facb4675 -->

## 6. 文档同步与全量自检

- [ ] 6.1 更新 `04-design/总体技术方案.md` §9.1.1 开工状态（③ 3a 已落地、3b 未开工）与 §13 台账相关行；验证：`check_all.py` 全绿、`_check_numbers.py` 的 REQUIRED 判据仍命中。 <!-- comet-task:efac5643-042d-49c8-bd3b-bf01fa61906d -->
- [ ] 6.2 确认 ② 的写入期 Schema、5 个 SQL 模板、`bone-init.sql` 与 manifest 均未被本 change 改动；验证：`git diff --stat` 只含本 change 声明的路径。 <!-- comet-task:cf3d3491-6582-48ae-bbc4-26f829ee0397 -->
- [ ] 6.3 运行 `./scripts/check.sh` 全量本地自检；验证：末行「✅ 本地自检通过！」，真实红点为零（大量 ❌ 负向探针属预期）。 <!-- comet-task:0200c698-cebe-4e3d-99c9-45da1dc6cbf0 -->

## 7. 提交与交付

- [ ] 7.1 按共享树纪律只提交自己路径（`git diff --cached --name-only` 核对，避免夹带并行会话文件）；验证：提交信息含 O-06 闸门更正依据与 L3 阻塞留痕，`git show --stat` 范围正确。 <!-- comet-task:9d8daf33-6b00-4985-866a-0b14b0500ec8 -->
- [ ] 7.2 推送到双远程并核对 `git ls-remote origin refs/heads/dev` == `HEAD`；验证：Gitee 与 GitHub 两端返回同一 SHA。 <!-- comet-task:d8b8ff3f-b1ca-4e23-b338-e8491459774e -->
