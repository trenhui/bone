# procuraAI 生产化加固验证报告（Build→Verify 交接）

- Change：`procuraai-production-hardening`
- 日期：2026-11-21
- 工作区：当前 `dev` 目录（未建分支 / Worktree）
- 性质：Comet Classic full change 的 Build 阶段最终验证 + 独立审查结论；最终 Verify 将复用并复核本报告。

## 1. 交付范围

已落地：

- A 类业务骨架：sourcing / qualification / risk 的 3 个 gateway 端口切换 SDK 持久化仓储，退役同接口 InMemory bean。
- B 类合同 / 治理：6 张 init-only 表、6 个持久化实体 + SDK Repository、6 个持久化 Adapter；`review_id` 服务端生成，请求携带 `idempotencyKey`，审查表双唯一键。
- 加固行为：租户隔离 fail-closed、跨租户覆写拒绝、总闸每次读最新持久化快照、缺操作人拒绝写入、AI 建议与人工终决分字段、PII / 注入原文不落库。
- 门禁：新增 A10.29 `tools/check_procura_persistence.py`，同步 CI / `check.sh` G0 / `check-procura-ci.sh` 三入口，含注入式负向反证。
- 文档：`doc/architecture/数据库开发规范.md` §2.17 采购表数 15 → 21；`README.md` 证据清单同步至 checks 10 / criteriaMax A10.29 / probes 154。

明确非目标：真实模型接入、评测集 seededDraft 转正或 27→200、D3-v2 契约冻结、生产 MQ/Outbox/Inbox、真实 MySQL DDL 执行、提交 / push / PR。

## 2. 机器验证证据

| 检查 | 结果 |
| --- | --- |
| Maven 采购域 reactor `spotless:apply` + `clean test` | BUILD SUCCESS；9 个含测试模块合计 368 例，0 Failures / 0 Errors / 0 Skipped |
| A 类三模块单独 `clean test` | sourcing 25 / qualification 26 / risk 26，全绿 |
| 合同 / 治理模块修复后 `clean test` | contract 80 / ai-governance 88，全绿 |
| `check_procura_alignment.py` | 主门禁 10/10 全绿 |
| `check_procura_alignment.py --self-test` | 154/154 负向探针全部转红且命中预期 key |
| `check_procura_evalset.py` / `--self-test` | PASS / 69/69 |
| `generate_asyncapi.py --check` | 29 条事件零漂移 |
| `check_procura_frontmatter.py` / `--self-test` | 33 份文档通过 / 7/7 |
| `check_procura_persistence.py` / `--self-test` | PASS / 全部负向转红且正例不误杀 |
| OpenSpec strict validate | `procuraai-production-hardening` valid |
| 前端 procurement app | tsc RC=0；eslint JSON errors=0 warnings=0；vitest 9 files / 87 tests passed |
| `git diff --check`、shell / Python / YAML 语法 | 全通过 |

## 3. 独立审查与修复闭环

Build 阶段 standard review 由独立 reviewer 完成两轮：

1. 首轮发现 1 项 CRITICAL、2 项 IMPORTANT：
   - C1：合同域人工推翻二次写入未复用主键，真实 DB 会撞 `(tenant_id, review_id)` 唯一键；fake repo 未模拟唯一约束导致假绿。
   - I1：请求仍自报 `reviewId`，与设计「服务端生成」不符。
   - I2：治理域 4 个端口缺租户时回退 `"0"`，fail-open。
2. 已全部修复并有代码 + 测试双证据：
   - C1：`ContractReviewStoreAdapter.persist()` 复用原 `id` + `version`；fake repo 模拟双唯一键；新增 `repeatedOverrideUpdatesExistingRow`。
   - I1：DTO / Command 去 `reviewId`；服务端生成 `rv-<UUID>`；请求 `idempotencyKey` 优先；新增服务端生成与幂等去重测试。
   - I2：`GovernanceTenant.current()` 缺租户抛错；新增治理四端口缺租户无副作用测试。
3. 同步处理：
   - 6 个持久化 Adapter 去掉 `@Primary`，避免掩盖双 bean；
   - 新增 5 个 `ApplicationContextRunner` 单 bean 唯一性测试；
   - `HumanDecisionLedgerAdapter` 对 `finalConclusion` 也做 PII 脱敏；
   - 持久化门禁扩展识别 `@Service` / `@Bean` 注册路径并新增负向探针。
4. 复审结论：**无 CRITICAL / IMPORTANT 阻塞项，可进入 Verify**。

## 4. 残留风险与 owner 项

- W1（非阻塞）：5 个单 bean 测试使用 `withBean` 手动装配，未触发真实组件扫描；该回归由 S2 静态门禁（禁止 InMemory 以 `@Repository/@Component/@Service/@Bean` 注册）兜底，且已移除 `@Primary` 掩盖。
- 真实 MySQL DDL 未执行，DDL 仅做静态对账与测试替身验证。
- CI / 门禁全绿只证明结构、隔离与行为契约；**CI 绿不等于 AI 可上线**。
- D-15 / D-16 / D-17 仍为 owner backlog：真实模型效果、评测集转正与下界、契约冻结；本轮未触碰、未伪造状态。
- 未执行 git 提交、push 或 PR。

## 5. 结论

Build 阶段实现与验证完成，17 项任务全部勾选。Verify 阶段已完成唯一最终集成审查，结论 PASS：无 CRITICAL / IMPORTANT 阻塞项，C1/I1/I2/S1/S2 无回归，6 条 Requirement 与 17 项任务全覆盖。本报告即 Verify 落盘材料；后续归档与交付仍需用户明确授权。
