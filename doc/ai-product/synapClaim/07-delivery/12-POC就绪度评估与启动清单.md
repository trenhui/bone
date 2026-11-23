---
doc_id: delivery.12
role: guide
currency: current
---

# 12 POC 就绪度评估与启动清单

> 定位：把「报案 1:N 升级后的智能理赔系统能不能进企业真实场景做 POC」这件事，从口头判断固化为**可执行、可审计、可回退**的交付物。本文是 11 号《L3 外部前置运行手册》的**前置读本**——先判断该不该进、进到哪一态，再用 11 号手册操作 provider → 探活 → E2E → 归因。
>
> **数字口径真源**：本文所有就绪度数字来自复核器 `tools/_ai_native_readiness.py` **运行实测**（2026-11-23），不是从历史报告抄写。`09-records/` 下的 `SATISFIED 5 / BLOCKED 7` 等是 2026-11-07 时点的**历史快照**（审计轨迹，不得回改）；两者差异见 §四。

---

## 一、结论：按三态判断「能不能进」

「能不能做 POC」和「能不能投产」是两个门槛，混为一谈会导致要么过早承诺、要么过度保守。按工程落地版 §11.4 三态开关判定：

| POC 形态 | 当前可否 | 解锁条件 | 对客影响 |
|---|---|---|---|
| **Shadow 技术验证**（真实数据跑全链路，AI 只做影子比对，不出对客结论） | ✅ **现在可进** | 无硬门槛，见 §二 就绪能力 | 旁路只读，不改客户现有流程，随时可关停 |
| **Assisted 对客建议**（AI 出建议，人工确认后生效） | ⚠️ 差 3 项能力 | 真实 provider + 图像多模态感知 + 模型真上链路，见 §五 | AI 建议须人工确认，不允许自动生效 |
| **Automated 自动赔付 / 正式投产** | ❌ 差 16 项门禁 | Go-Live 门禁全闭环 + L3 宣称前提，见 §六 | 自动出款，资金安全与监管硬前置 |

**红线（工程落地版 §11.4、§12.2）**：任一监管前置未闭环，系统**仅允许 Shadow 态**，不得对客出理赔结论。因此企业第一阶段的 POC 应明确锁定为 Shadow 技术验证。

---

## 二、Shadow POC 已就绪能力（可进场的实证）

以下能力均已在当前代码实测通过，构成「现在可进」的证据：

| 能力层 | 已就绪内容 | 实测证据 |
|---|---|---|
| 后端全链路 | 报案→解析→核责→理算→决策→资金；报案 1:0..N 拆分 | 核心测试 20 run / 0 fail（3 E2E 按设计跳过），BUILD SUCCESS |
| 前端 | 三方法（reportDetail/appendCase/revokeConsent）+ 幂等键 ≤64 修复 | 222/222 测试通过，tsc 零错误 |
| 前后端 E2E | 真实服务栈跑通，运行期证据 nodes=148 / aiParticipated=8 | 产物 `claims-e2e-result.json`，probeVerdict=REACHABLE |
| 基线采集 | 6 项非 ML 基线全 wired，带采集器 provenance | 复核器 `BASELINE_WIRED` = SATISFIED |
| 冻结评测集 | status=active，300 cases，manifest 校验通过 | 复核器 `FROZEN_EVALSET_ACTIVE` = SATISFIED |
| 评测门禁 | 带 `--check` 接线（report-only 恒绿是假绿，已排除） | 复核器 `EVAL_GATES_WIRED` = SATISFIED |
| 学习回流 / 图像解析接线 / RAG 接线 / 灰度回滚演练 | 四层均已接线 | 复核器对应 4 项均 SATISFIED |
| 交付文档 | MOU/NDA/DPA、脱敏方案、POC 演示脚本、规则书、接口契约、目录库方案等 10 项 | `00-落地交付包清单.md` |

---

## 三、当前态实测（复核器运行输出，2026-11-23）

```
判据 12 项：SATISFIED 7 / BLOCKED 5 / RUNTIME_ONLY 0
当前可判定成熟度：L0（运行态：AI 全链路降级，纯规则路径）
l3Claimable = false
运行期证据：nodes=148 aiParticipated=8 participationRate=0.0541
            probeVerdict=REACHABLE probeEvidence=OK
```

复现命令：

```bash
python3 doc/ai-product/synapClaim/tools/_ai_native_readiness.py
```

**SATISFIED 7**：BASELINE_WIRED / FROZEN_EVALSET_ACTIVE / EVAL_GATES_WIRED / FLYWHEEL_DOWNSTREAM / IMAGE_PARSE_WIRED / RAG_WIRED / ROLLOUT_ROLLBACK_DRILL

**BLOCKED 5**：PROVIDER_CONFIGURED / PERCEPTION_MODEL_DRIVEN / CATALOG_ADMISSION_ACTIVE / GOLIVE_NOT_DRAFT / RUNTIME_MODEL_COVERAGE

**成熟度诚实定位**：L1 已越过、L2 部分达到、未配真实 provider 时运行态接近 L0。`l3Claimable=false`，不得对外宣称 L3。

---

## 四、为什么历史报告写「5 SAT / 7 BLOCKED」（口径对齐）

`09-records/` 的历史快照（2026-11-07）与实现度评估报告记录 `SATISFIED 5 / BLOCKED 7`。这是**时点差**，不是错误：

- 翻转的 2 项：`BASELINE_WIRED`（`BASELINE_DECLARED_NOT_WIRED` → `ADMITTED`）、`FROZEN_EVALSET_ACTIVE`（`FROZEN_SET_NOT_ACTIVE` → `ADMITTED`）。
- 根因：基线资产从「声明未接线」变为「带 provenance 真实接线」、冻结集从 skeleton 变为 `status=active` + 300 cases，由提交 `5724e492c` / `d14b8a47c` 落地。
- 处置：**历史快照与报告的原文不动**（审计轨迹），其时点结论在各自文内已标注 2026-11-07。对外沟通与本合同口径以本文 §三的**2026-11-23 实测**为准。

---

## 五、5 个 BLOCKED 的 owner 与解锁条件（按 POC 影响排序）

| 判据 | 根因 | 影响的 POC 形态 | owner | 解锁动作（合法唯一路径） |
|---|---|---|---|---|
| `PROVIDER_CONFIGURED` | `BONE_AI_GATEWAY_BASE_URL` 未设 | Assisted / Automated | 平台 / 交付 | 采购 + 密钥托管后注入环境变量 |
| `PERCEPTION_MODEL_DRIVEN` | 解析模式非 llm 或无 provider | Assisted / Automated | 平台 / 架构 | 配 provider + `BONE_CLAIM_DOCUMENT_PARSE_MODE=llm` |
| `RUNTIME_MODEL_COVERAGE` | provider 未配 + 参与率证据未闭环 | Assisted / Automated | 平台 / 交付 | 按 11 号手册探活 REACHABLE + 实跑 E2E + 证据束自洽 |
| `CATALOG_ADMISSION_ACTIVE` | 生效通道默认 NONE，目录准入护栏未生效 | Automated / 资金安全 | 业务 / 法务 | 目录数据接入 + 策略 MANDATORY，经业务法务签核 |
| `GOLIVE_NOT_DRAFT` | 13 册合规文档仍 draft | 正式投产 | 安全合规 | PIA/DPIA、等保、算法备案等逐册转正并配验收证据 |

前 3 项决定「能不能对客出 AI 建议」（技术接入，可控）；后 2 项决定「能不能碰钱和过监管」（跨团队签核，不由研发节奏决定）。

---

## 六、正式投产的 16 项门禁（Go-Live 总纲）

`08-go-live/00-总纲与Go-Live门禁.md` 定义 G1–G16，其中 **9 项 A 阶段阻断**：PIA/DPIA、等保备案、AI 算法备案、SBOM+漏洞门禁、规则发布防投毒、条款费率报批、目录准入资金护栏、理算三重路径收敛等。这些是安全合规 / 资金安全硬前置，任一项未闭环不得对正式客户投产。

**L3 不是宣传目标**：在 G0–G4 全通过前，对外仍按 L1/L2 分阶段表述。

---

## 七、不得触碰的红线（业界最佳实践）

1. **不得为观感刷绿**：无 provider 时 `participationRate=0` 是正确读数；不得改默认值、批量改 draft→current、伪造探活判词或证据束。把 BLOCKED 改成 SATISFIED 的唯一合法路径是**闭环其对应外部条件**。
2. **模型跑通 ≠ 自动放款**：`aiParticipated>0` 但案件仍 MANUAL_REQUIRED 是 fail-closed 的正确表现。把视觉桩 `available=false` 改成 `true` 会打开 AUTO_APPROVE 资金闸，属 L2 资金行为变更，须双人 Review + 产品签核。
3. **不得先改文档宣告完成**：13 个 go-live 分册的 `currency: draft` 是准确状态，逐册升 current 必须与某一 G 门禁验收证据一一对应。
4. **合规前置未闭环仅 Shadow**：金发〔2026〕8 号 + PIPL 第 24 条，未经批准不得对客出结论。

---

## 八、Shadow POC 启动 Checklist（进场逐条勾）

**进场前（我方）**
- [ ] 复核器实测 = §三数字（7 SAT / 5 BLOCKED，L0，l3Claimable=false）
- [ ] 后端核心测试 20/20 + 前端 222/222 + tsc 零错
- [ ] 交付文档 01–11 齐备，POC 演示脚本三案例可讲
- [ ] 明确对客口径：**本次为 Shadow 技术验证，AI 不出对客结论**

**进场后（与客户）**
- [ ] 签 NDA → MOU → DPA，完成数据交接（交付物 01/02）
- [ ] 旁路只读接入，不动客户存量流程，确认可一键关停
- [ ] 用授权脱敏样本跑通报案→解析→核责→理算→决策→资金
- [ ] 现场生成评测指标（字段准确率 ≥95%、规则匹配 ≥90%），不预置实测值
- [ ] 差异比对：影子结论 vs 客户人工结论，按 §11.4 Shadow 比价口径记录
- [ ] 演示结束出具评测报告 + 决策回放（任一案件可回放决策链）

**不下场（守住边界）**
- [ ] 不打开任何自动出款路径
- [ ] 不修改任何治理资产 status / frontmatter / 默认值
- [ ] Assisted/Automated 留待 §五 3 项能力 + §六 门禁闭环后另立阶段

---

## 九、相关文档

- `00-落地交付包清单.md`：10 项交付物总览与四阶段节奏
- `03-POC演示脚本.md`：45 分钟演示剧本与异议应答
- `11-L3外部前置运行手册.md`：provider → 探活 → E2E → 复核器归因的操作闭环
- `../08-go-live/00-总纲与Go-Live门禁.md`：G1–G16 门禁与 owner
- `../09-records/evidence/README.md`：历史快照与证据固化说明

---

*本文为 POC 就绪度决策真源。数字口径以复核器运行实测为准；历史快照原文保留作审计轨迹。新增或翻转判据时须同步本文 §三/§四，并经复核器 `--self-test` 验证。*
