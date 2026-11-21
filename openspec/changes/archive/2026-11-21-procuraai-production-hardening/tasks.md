# Tasks

## 1. 业务骨架仓储切换（已有 DDL）

- [x] 1.1 扩充 `ProcurementApplication` 的 `@EnableSqlRepositories` 扫描 `sourcing` / `qualification` / `risk` 的 `domain.repository` 包，并移除或条件化 3 个同接口 `InMemory*Repository`；验证 ApplicationContextRunner 断言每个端口恰好一个实现、三模块单测全绿。 <!-- comet-task:5c397865-1082-4b00-ad23-ef571a068941 -->
- [x] 1.2 为寻源终态不可回写、准入人工终决、风险预警幂等与 UNKNOWN 转人工补持久化读回测试；验证 `mvn -f bone-apps/bone-procurement/pom.xml -pl <三模块> test` 全绿且重启读回场景通过。 <!-- comet-task:ffeb20d3-799f-4792-9ae2-968ae0dff10e -->

## 2. 合同与治理域 DDL、实体与映射

- [x] 2.1 在 `bone-init.sql` 新增 6 张 init-only 表（`t_proc_contract_review`、`t_proc_review_trace`、`t_proc_ai_capability`、`t_proc_human_decision`、`t_proc_global_kill_switch`、`t_proc_governance_trace`），必备 8 列齐全、以 `tenant_id` 打头的唯一键与必要索引；验证 DDL 对账测试覆盖 21 张 `t_proc_*` 表且列映射零缺失。 <!-- comet-task:c6ed86f9-b8dd-433a-ad07-2be8e12f2335 -->
- [x] 2.2 更新 `数据库开发规范.md` §2.17 的采购表数登记 15 → 21；验证文档表数与 `bone-init.sql` 实际建表数一致。 <!-- comet-task:70c46581-545e-4327-b14d-b8e89c9cc3c3 -->
- [x] 2.3 新增 6 个持久化实体与复杂事实的 JSON 列往返映射，领域 record 不添加持久化注解；验证每类事实的写入→读回往返测试通过且 PortContractTest 签名锁定不破。 <!-- comet-task:4ff92f7d-be30-4e6a-9e1f-da1556bde77a -->

## 3. 端口 Adapter 与加固行为

- [x] 3.1 为 `ContractReviewStore` / `ReviewTracePort` 落持久化 Adapter，`review_id` 由服务端生成，加入 `(tenant_id, review_id)` 唯一约束；验证跨租户覆写负向测试先红后绿、原租户记录不被改写。 <!-- comet-task:a9835eea-64a0-4129-aded-dd85d20005c5 -->
- [x] 3.2 为 `CapabilityRegistry` / `HumanDecisionLedger` / `GlobalKillSwitchStore` / `GovernanceTracePort` 落持久化 Adapter，保留 `@ConditionalOn...Enabled` 能力开关语义；验证四端口唯一 bean 与 ArchUnit E-5.4 不转红。 <!-- comet-task:6874517f-af0f-4b93-8bed-ecb2c38a6852 -->
- [x] 3.3 总闸裁决每次读最新持久化快照且切 ENGAGED 后下一次裁决立即不可进入 AI 路径；缺登录态操作人的开关变更 fail-closed；验证即时生效与缺操作人拒绝两条测试通过。 <!-- comet-task:f19cd3fd-f1a0-4fd2-84e2-e34ac4de4ea4 -->
- [x] 3.4 AI 建议与人工终决分字段保存，人工推翻留痕；模型 output 原文、合同正文与身份证/银行账号不落库；验证敏感内容入口拒绝与审计字段测试通过。 <!-- comet-task:4d1bb92c-3519-49f7-95aa-95fb8a9c8329 -->

## 4. 门禁判据、三入口与负向反证

- [x] 4.1 在 `tools/check_procura_alignment.py` 注册 `persistenceGate` / `persistenceProbe`，补齐 `_cmd_key()` 路径映射与 A10.17 集合比对；验证三入口判据集合精确一致且无 A10.16 黑名单串。 <!-- comet-task:6b70af3c-5347-4776-ab31-c77c8b038dd9 -->
- [x] 4.2 在 `ci.yml`、`scripts/check.sh` G0、`check-procura-ci.sh` 同步 blocking 步骤与快反馈命令，必要时调整 `CI_PROCURA_STEP_MIN`；验证 YAML 解析通过、步骤数断言与 `--agent-json` 判据 key 命中。 <!-- comet-task:f7f5c23b-b942-4b26-9f1a-56470e1c50b2 -->
- [x] 4.3 新增注入式负向探针：删唯一约束、移除任一入口判据、吞掉失败均须转红并命中预期 key；附正例对照防一律 FAIL 假绿；验证 `--self-test` 全绿且三类注入实测必红。 <!-- comet-task:3ce4c60b-531a-4379-beef-465278ff555d -->
- [x] 4.4 开发期 `--sync-manifest` 同步 `gateEvidenceManifest` 的 checks / criteriaMax / indexCount / probes 等字段；验证 A10.28 逐字段比对绿、历史轮次数字未回写。 <!-- comet-task:93a84e6f-31cc-41cd-992c-706a9bfa7515 -->

## 5. 集成验证与独立审查

- [x] 5.1 执行 `spotless:apply` 后跑采购域 Maven 全量 `clean test`（含 ArchUnit、DDL 对账、新增负向测试）；验证 BUILD SUCCESS、0 Failures / 0 Errors。 <!-- comet-task:b3516fd1-4d18-4a96-8011-c0f25a8ea412 -->
- [x] 5.2 跑 `check_procura_alignment.py` 主判据与 `--self-test`、`check_procura_evalset.py`、`generate_asyncapi.py --check`、`check_procura_frontmatter.py`；验证全绿且评测集仍 `seededDraft`、27 例下界未变。 <!-- comet-task:b9492591-2359-4892-bbe7-bf9f9f883892 -->
- [x] 5.3 跑前端 `tsc --noEmit`、`eslint -f json` 解析 errorCount、`vitest run`；验证 errorCount=0 且前端测试全绿。 <!-- comet-task:67ccc4ba-85e6-449e-87d4-1a548a769f41 -->
- [x] 5.4 完成独立集成审查并落 Verify 报告；验证无 CRITICAL / IMPORTANT 项，明确记录「CI 绿不等于 AI 可上线」与留给 owner 的 D-15/16/17。 <!-- comet-task:de3ca982-c0b6-4323-96d2-f792660debb6 -->

## Workflow follow-up

- Verify 通过后按用户授权归档并交付；本轮不执行 git 写操作，除非用户明确要求。
