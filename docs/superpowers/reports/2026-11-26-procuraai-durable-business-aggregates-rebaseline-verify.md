# Verify 报告：procuraAI 持久业务聚合补齐（B 方案，新基线重放）

- Change: `procuraai-durable-business-aggregates-rebaseline`
- 日期：2026-11-26
- 阶段：Build → Verify（autonomous + tdd + standard）
- 基线：`origin/dev = d1742235a8f81f93dff343ce685a00363f0a7fe0`（本地 HEAD 与之相同）
- 关系：原 `procuraai-durable-business-aggregates` 因并行推送导致交付基线与 `origin/dev` 分叉；本 change 在同一实现内容上换基线重走全流程，不扩大需求、不降低验收。

## 1. 授权与边界

沿用用户已确认的 Build 配置 `autonomous + tdd + standard`，以及此前明确给出的「授权 L3（不碰生产库）」。授权范围覆盖 `bone-init.sql`、增量迁移、数据库规范、CI 三入口与门禁常量/注入探针。

未授权且未执行：生产库迁移或数据修改、生产 MQ 接入、owner 契约冻结、评测转正、真实模型接入或效果达标声明。范围未扩到完整 S2P，未改既有合同 HITL / 人工复核 / 治理态势页面契约。

## 2. 交付内容（换基线后实测存在）

- 新模块 `bone-apps/bone-procurement/bone-procurement-business`，由父聚合与启动应用 `bone-procurement-app` 共同装配；四类聚合 Bid / Quotation / Award / CAPA、应用服务、SDK 持久化适配器与 HTTP 端点齐备。
- 新 DDL 四表 `t_proc_bid`、`t_proc_quotation`、`t_proc_award`、`t_proc_capa`，审计八列齐全，业务唯一键以 `tenant_id` 打头；与 `origin/dev` 的理赔/治理表在同一 `bone-init.sql` 中共存，三方合并无丢列。
- 增量迁移 `scripts/migration/0142_procura_business_aggregates.sql` 与 `migration-manifest.json` order=148、`executableCount=148`。
- 门禁 `tools/check_procura_business_aggregates.py` 已按 A10.17 接入 `ci.yml`、`scripts/check.sh`、`doc/ai-product/procuraAI/scripts/check-procura-ci.sh` 与门禁常量/注入探针。
- 身份边界：租户与操作人只认登录态；AI 评分/排序只作建议；授标与 CAPA 关闭必须具名人工；终态不可回写；响应 DTO 不回传模型 output 或 PII。

## 3. 验证证据（本次新基线上真实执行）

### 3.1 测试与构建

| 项目 | 命令 / 证据 | 结果 |
|---|---|---|
| Build 门禁 | `mvn -o -f bone-apps/bone-procurement/pom.xml test`（Comet Guard 真实执行并绑定收据） | **ALL CHECKS PASSED — phase=verify** |
| 新模块单测 | `bone-procurement-business/target/surefire-reports/TEST-*.xml` 8 份汇总 | **33 tests / 0 failures / 0 errors / 0 skipped** |
| 三方合并正确性 | `git diff origin/dev..HEAD` 中 `bone-init.sql` 同时含 4 张业务表与远端理赔表 | 通过（无覆盖） |
| 迁移清单 | `python3 scripts/check-migration-manifest.py` | `OK: 155 文件 / 148 可执行 / order 1..148 连续` |
| OpenSpec | `comet classic openspec -- validate procuraai-durable-business-aggregates-rebaseline --strict` | `Change ... is valid` |

### 3.2 门禁与负向探针

| 门禁 | 结果 |
|---|---|
| `python3 tools/check_procura_business_aggregates.py` | `[BUS-AGG] PASS — 4 表 / 审计唯一键 / 迁移规范 / SDK 持久化 / 身份边界 / 三入口` |
| `python3 tools/check_procura_business_aggregates.py --self-test` | `[SELF-TEST] ALL PASS`（含删除 CI / check.sh / 本地一键入口判据三条负向均转红） |
| `python3 tools/check_procura_alignment.py` | `全部通过 (10/10)` |
| `python3 tools/check_procura_alignment.py --self-test` | `160/160 负向探针全部转红且命中预期 key`；`probes=160`、`seedCaseCount=27` 与 README 证据清单一致 |

### 3.3 独立审查

原 change 的独立审查结论为 0 CRITICAL / 0 IMPORTANT（C1 启动应用装配、I1 权限码种子、I2 投标终态回写、I3 创建时间重写、I4 唯一键首列/init-迁移列漂移均已修复并复验）。本 change 的实现文件与 DDL/门禁内容逐字复用，基线变化只引入无关理赔/治理文件，故沿用该审查结论；上述门禁与测试在新基线上重跑无回归。

## 4. 恢复与偏差说明

- 旧 Run 的归档物理动作已成功但 SDK 记账中断，经用户确认规格净变化（+148/-0、9 需求 29 场景）后用 `--recover --confirmed` 补记；该 Run 保留为历史，不再交付。
- 交付基线与 `origin/dev` 分叉 2 提交，其中 15 个新增路径不在原 Verify 输入集合中；按用户选择放弃旧基线，新建本 change 重走全流程，未使用 force push、未改本地 remote 引用、未夹带他人提交。
- Open 阶段一次 claim 因漏传 `--approval-hash` 未执行；Design 阶段一次提案因陈旧 `.write.lock` 超时未执行；Build 检查一次因前台超时被 SIGTERM 终止且无收据。三处均按 `mark-unknown → retry(not-executed)` 恢复，恢复证据明确「not-executed 仅指 SDK 记账未落地」，随后真实重跑并拿到合法收据。
- 未做：生产库操作、评测转正、真实模型效果声明、生产 MQ 接入、owner 冻结包签署。
