---
change: procuraai-production-hardening
design-doc: openspec/changes/procuraai-production-hardening/design.md
base-ref: 9207a623849282ea3453a6ecac30a6f86e778328
archived-with: 2026-11-21-procuraai-production-hardening
---

<!-- comet-task-authority: openspec/changes/procuraai-production-hardening/tasks.md -->

# 实施计划：procuraAI 生产化加固

范围与设计取舍以 `openspec/changes/procuraai-production-hardening/design.md` 为准；规格以 `specs/procurement/production-persistence/spec.md` 为准。执行配置：autonomous / tdd / standard。任务权威为上方 tasks.md，本计划只映射任务 ID，不复制勾选框。

## 约束与不变量

- 持久化唯一走 `bone-metadata-sdk`；application 层不直连 infrastructure，不写 `FOR UPDATE`，写操作避让 ArchUnit E-5.4。
- 所有新表为 `bone-init.sql` 的 init-only 定义，不执行生产库；必备 8 列齐全，唯一键以 `tenant_id` 打头。
- 不改 REST / 事件七字段 / 披露三态；不接真实模型，不改 evalset 27 例与 `seededDraft`。
- 工作区未跟踪的 `openspec/changes/synapclaim-architecture-convergence/` 不纳入本 change。

## 1. 业务骨架仓储切换（依赖：无）

<!-- comet-task-ref:5c397865-1082-4b00-ad23-ef571a068941 -->
先写失败测试：ApplicationContextRunner 断言 sourcing/qualification/risk 各端口只有一个实现，且实现类型不是 `InMemory*`。再扩充 `ProcurementApplication` 的 `@EnableSqlRepositories` 扫描包，移除或条件化三个内存 bean，运行三模块单测转绿。

<!-- comet-task-ref:ffeb20d3-799f-4792-9ae2-968ae0dff10e -->
补持久化读回测试：终态不可回写、准入人工终决、预警来源幂等、严重度 UNKNOWN 转人工。`InMemoryQualificationRepository` 中的 `existsInReview` / 可用证照查询迁到仓储默认方法或应用服务条件，DDL 条件唯一键兜底；领域状态机规则保持原样。

失败处理：若出现同接口双 bean 或 E-5.4 转红，先核对装配与 `@NoDomainEvent`，不通过放宽架构测试解决。

## 2. 合同与治理域 DDL、实体与映射（依赖：第 1 组的构建命令确认）

<!-- comet-task-ref:c6ed86f9-b8dd-433a-ad07-2be8e12f2335 -->
TDD：先写 DDL 对账失败用例（断言 21 张 `t_proc_*` 表与 6 张新表列映射），再在 `bone-init.sql` 追加 6 张 init-only 表。审查表含 `review_id`、`idempotency_key` 与 `(tenant_id, idempotency_key)`、`(tenant_id, review_id)` 两唯一键；追溯表以 `(tenant_id, review_id, seq)` 去重；能力表以 `(tenant_id, capability_code)` 唯一。

<!-- comet-task-ref:70c46581-545e-4327-b14d-b8e89c9cc3c3 -->
同步 `数据库开发规范.md` §2.17 表数 15 → 21；用脚本对比文档数字与 SQL 建表数，防止假绿。

<!-- comet-task-ref:4ff92f7d-be30-4e6a-9e1f-da1556bde77a -->
新增 6 个持久化实体与 JSON 列往返映射；领域 record 不加持久化注解。每类复杂事实写写入→读回往返测试，并跑 `PortContractTest` 锁定端口签名。

## 3. 端口 Adapter 与加固行为（依赖：第 2 组）

<!-- comet-task-ref:a9835eea-64a0-4129-aded-dd85d20005c5 -->
`ContractReviewStore` / `ReviewTracePort` 落持久化 Adapter。先写跨租户覆写与重复投递负向测试（红），再实现服务端生成 `review_id`、按 `idempotencyKey` 去重、唯一约束兜底（绿）。原租户记录必须不被改写。

<!-- comet-task-ref:6874517f-af0f-4b93-8bed-ecb2c38a6852 -->
治理四端口落持久化 Adapter，保留 `@ConditionalOn...Enabled`。ApplicationContextRunner 断言四端口唯一 bean；跑 ArchitectureTest 确认 E-5.4 不转红。

<!-- comet-task-ref:f19cd3fd-f1a0-4fd2-84e2-e34ac4de4ea4 -->
总闸 Adapter 每次裁决读最新行、无缓存；写路径从登录态取操作人，缺失即 fail-closed。先写「切换后立即生效」与「缺操作人拒绝」两条红测，再实现转绿。

<!-- comet-task-ref:4d1bb92c-3519-49f7-95aa-95fb8a9c8329 -->
AI 建议与人工终决分字段，推翻留痕；复用 `ContractReviewPortAdapter` 的注入/PII 入口护栏，不新建扫描组件。敏感内容入口拒绝与审计字段测试转绿。

## 4. 门禁判据、三入口与负向反证（依赖：第 2、3 组行为可用）

<!-- comet-task-ref:6b70af3c-5347-4776-ab31-c77c8b038dd9 -->
在 `tools/check_procura_alignment.py` 注册 `persistenceGate` / `persistenceProbe`，补 `_cmd_key()` 与 A10.17 集合比对；罪名与文案避开 A10.16 黑名单串。验证三入口判据集合精确一致。

<!-- comet-task-ref:f7f5c23b-b942-4b26-9f1a-56470e1c50b2 -->
同步 `ci.yml`、`scripts/check.sh` G0、`check-procura-ci.sh`；必要时调整 `CI_PROCURA_STEP_MIN`。YAML 解析与步骤数断言必须绿。

<!-- comet-task-ref:3ce4c60b-531a-4379-beef-465278ff555d -->
新增注入必红探针（删唯一约束 / 移除入口判据 / 吞错）与正例对照；`--self-test` 全绿且三类注入实测转红并命中预期 key。

<!-- comet-task-ref:93a84e6f-31cc-41cd-992c-706a9bfa7515 -->
开发期 `--sync-manifest` 同步 `gateEvidenceManifest`，A10.28 逐字段比对绿；不回写历史轮次 `N/N`。

失败处理：任一判据注入不能转红即视为假绿，回改脚本并重跑自检，不降判据。

## 5. 集成验证与独立审查（依赖：前四组全部完成）

<!-- comet-task-ref:b3516fd1-4d18-4a96-8011-c0f25a8ea412 -->
`spotless:apply` 后跑采购域 Maven 全量 `clean test`（含 ArchUnit、DDL 对账、新增负向测试），BUILD SUCCESS / 0 failures。

<!-- comet-task-ref:b9492591-2359-4892-bbe7-bf9f9f883892 -->
跑对齐主判据 + `--self-test`、evalset 门禁、AsyncAPI 零漂移、frontmatter 守护；evalset 仍 `seededDraft`、27 例下界未变。

<!-- comet-task-ref:67ccc4ba-85e6-449e-87d4-1a548a769f41 -->
前端 `tsc --noEmit`、`eslint -f json` 解析 errorCount、`vitest run`；不用 stylish formatter 判定假绿。

<!-- comet-task-ref:de3ca982-c0b6-4323-96d2-f792660debb6 -->
由独立 reviewer 对最终 diff 做一次集成审查，落 Verify 报告；无 CRITICAL / IMPORTANT，记录「CI 绿不等于 AI 可上线」与 D-15/16/17 owner backlog。
