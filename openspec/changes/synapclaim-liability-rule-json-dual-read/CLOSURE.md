# synapclaim-liability-rule-json-dual-read — 设计侧收口记录

> 创建：2026-11-09（design 已确认、change 处于 build 阶段）
> 状态：**设计侧已收口**；Build 阶段被两道非设计侧因素阻塞（见 §3）。
> 关联阻塞说明（同因）：`../synapclaim-architecture-convergence/comet-sdk-blocking-bug.md`

## 1. 设计侧收口结论

设计门禁已关闭，change 已正确推进至 `build` 阶段。

- `comet state get ... phase` → **build**
- `comet state check ... build --details` → **5/5 entry check 全部通过**（phase=build、design_doc 非空、proposal.md 非空、tasks.md 非空、bound_branch 一致）
- `comet status` → `phase: build [0/22 tasks]`，`run_step: full.build.plan`，`build_mode: autonomous`，`runtime_mode: sdk`

### 已交付设计产物（五份，彼此自洽）

| 产物 | 内容 |
|---|---|
| `proposal.md` | Why/What/非目标；明确不落实体/DDL、不接 PITest 门禁、O-06 收窄范围 |
| `design.md` | 候选方案 A/B/C 对比、**D1–D7 七项决策**、风险表、迁移计划（无 DDL）、Open Questions |
| `tasks.md` | 7 节 22 个 task（含 comet-task id），实现与文档任务分离 |
| `specs/claim/liability-rule-json-dual-read/spec.md` | **6 条 Requirement / 18 个 Scenario** 全覆盖读侧契约 |
| `.comet/handoff/design-context.{md,json}` | 确定性、可溯源的 design handoff（proposalHash `83d68d4e…`）|

设计核心决策（D1–D7）：回落逻辑落 domain 值对象 `resolve(key,scalar)`；`fromLegacy` 增重载保持装配点唯一；开关落 application 以值参数传入 domain；双轨比对差异只记录不阻断；等价判据用 `BigDecimal.compareTo`；L3 两项给可判定阻塞而非半成品；O-06 闸门按依赖交集收窄。

## 2. 设计侧「仅文档」任务盘点

以下任务属本 change 设计范畴、不写代码，但落点在两份被并行会话持续维护的共享大文档，故**备好精确改写文本、暂未机械套用**，以免与在途编辑竞态丢失。

| 任务 | 落点 | 现状 | 精确改写（备好，待安全提交时套用） |
|---|---|---|---|
| 1.1 §9.1.1 ③ domain 归属 | `总体技术方案.md` | ✅ 已落地（grep `RuleJsonCodec.resolve` 零命中，正文已按「domain 值对象回落」表述） | 无需再改 |
| 1.1 O-06 闸门收窄 | `总体技术方案.md` §13 行 701 | ❌ 未落地（仍宽口径「M2 ③ 开工时 5 个硬缺口仍未逐项验收 ⇒ 不得开工」） | 改为：「升级：与 `mapping` 有交集的配置面改造开工前，5 个硬缺口仍须逐项验收 ⇒ 记为阻塞项、不得开工；其余改造不受本条约束。触发：相关配置面改造进入开工前。原条文/日期/理由与复核命令一并留痕」 |
| 5.1 `engine_version` 收口 | `方案质量诊断报告.md` §14.1 / §5 | ❌ 仍标「未执行/须评审凭据」 | 改为「**有触发条件的阻塞**：理赔模块源码检索 `engine_version` 读取点 = 0 ⇒ 不落实体/DDL/迁移；触发条件=消费方落地时同批入库；复核命令=`grep -rn engine_version bone-apps/synap-claim` 仍为零 + 台账条目含可机检触发文本」 |
| 5.2 PITest 收口 | 同上 | ❌ 同上 | 改为「**有触发条件的阻塞**：本地依赖仓库无 `org.pitest` 制品、`mvn -o` 不可解析 ⇒ 不接 pom/check.sh/allowlist；触发条件=CI 联网 + `bone.claim.adjustment.strategy-routing.enabled` 生产启用评审前；复核命令=`mvn -o -pl ... test` 仍绿且无文档称其已生效」 |
| 5.3 状态措辞 | 同上 §14.1 | ❌ 同 5.1/5.2 | 两处由「未做/未执行」改为「有触发条件的阻塞」+ owner（默认架构师）+ 复核方式 |
| 6.1/6.2 doc sync | 两份文档 | 依赖上述 1.1/5.x | 套用上述文本后跑 `tools/check_all.py` 19/19 + `_check_numbers.py` ledger 判据 |

> **为何备好而非立即套用**：§13 与诊断报告 §14.1 是并行会话当日高频维护区（诊断报告 §11/§12/§13 均标注「并发会话提示」「改动只落本报告」）。本会话直接 Edit 同一文件存在读-改-写竞态静默丢失风险，故把精确改写留作「待套用清单」，建议：① 由并行会话在其下一轮 §14.1 维护中一并吸收，或 ② 待其编辑窗口结束、本会话单独 `git commit --only` 仅含这两份文档时再套用。

## 3. Build 阶段阻塞点（非设计侧可解）

### B1 — 实现代码未写（22 task，0/22）
tasks.md 第 2–4 节（领域侧按键回落、读开关与双轨比对、判别力双向变异）与第 7 节（提交/PUSH）全部 `[ ]`。实现本身可手写为 Java（不依赖 Comet SDK），但 Comet 归档门禁要求经 SDK 跑 `check run build`。

### B2 — Comet 归档门禁被 SDK `check run` 锁 bug 卡死
- 复现：`comet check run synapclaim-liability-rule-json-dual-read build -- ./scripts/check.sh` 卡在「核对检查输入」阶段，等 `.runtime-store-v2/<hash>/.write.lock` 30s 超时，退出码 70（runtime error）。子进程锁后静默崩溃、主进程误判持锁——**SDK 固有 bug，外部无法绕过**。
- 影响：本 change 的 `build` → `verify` → `archive` 整条门禁链无法经 SDK 推进。
- **不可伪造收据推进**：否则破坏工作流诚信（见兄弟 change 阻塞说明 §根因定论）。
- 已验证的独立质量证据：`scripts/check.sh` 59 步、`mvn clean test` 1328 例均可在 SDK 之外独立 PASS（与兄弟 change 同源措辞、同环境结论）。

## 4. 解阻塞与收尾路径

```
# 0. 先解 SDK 锁 bug（上游修复或换无此 bug 的环境，如 Linux CI）
#    已向 rpamis/comet 起草 issue（见兄弟 change 阻塞说明「推动修复」）

# 1. 套用 §2 的仅文档改写（O-06 收窄 + engine_version/PITest 收口），单独提交
git add doc/ai-product/synapClaim/04-design/总体技术方案.md \
        doc/ai-product/synapClaim/04-design/方案质量诊断报告.md
python3 doc/ai-product/synapClaim/tools/check_all.py   # 须 19/19
git commit --only -- <上述路径> -m "docs(synap): dual-read 设计侧仅文档任务收口（O-06 收窄 + L3 可判定阻塞）"

# 2. 推进 Build：解锁后由 Comet SDK 驱动（autonomous），或人工写码后补门禁
comet state next synapclaim-liability-rule-json-dual-read --json   # full.build.plan
#    → 按 tasks.md 2.x/3.x/4.x 实现 resolve/fromLegacy 重载/开关/双轨比对 + 变异用例
#    → comet check run ... build -- ./scripts/check.sh
#    → comet check run ... verify
#    → comet archive ...

# 3. 若 SDK 锁 bug 长期未修：在 Linux CI 补跑 check run，或在 Comet 修复前
#    以「代码已 PUSH + 独立 check.sh/mvn 全绿」作为交付证据（同兄弟 change 当前处置）
```

## 5. 当前未提交改动

- `openspec/changes/synapclaim-liability-rule-json-dual-read/comet-state.yaml`：design handoff 记录（由 Comet 自身写入，属运行时状态，**不由本会话手动提交**，交由 Comet/并行会话管理）。
- 本 `CLOSURE.md`：新增，记录设计侧收口与阻塞点，供后续会话/SDK 修复后直接接续。
