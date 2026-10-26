# synapClaim MRM 与直付通道增强设计（E4 + C10，业界最佳实践）

> 本文是《synapClaim 依赖 BONE-AI 能力图谱与增强方案（企业级串联）》§3/§4 的工程化落地详设，
> 对齐《BONE-AI 能力中台整体方案》C7（评测门禁，E4 增强）/ C10（集成三态灰度，直付通道）
> 与《BONE-AI 架构决策记录 ADR 包》ADR-1（内容安全默认开）。
> **定位**：V1.0 Phase B 增量，不挤占 MVP I0–I4；AI 不碰钱、不自动拒赔、不自动放款。

---

## 0. 范围与契约落点

| 增强项 | 平台能力 | 决策图节点 | 端口 |
|---|---|---|---|
| E4 模型风险管理 MRM | C7 评测门禁（增强） | 全 AI 节点（AiAssess / FraudScreen / 视觉/图） | `EvaluationPort`(C7) + `TracePort`(C4) |
| C10 直付三态灰度 | C10 集成三态灰度 | `Adjust` 直付分支 | `IntegrationPort`(C10) + `LedgerPort`(C11) |

---

## 1. E4 模型风险管理（MRM，增强 C7）

### 1.1 业界实践对标

- **银保监/央行模型风险管理（SR 11-7 类）**：模型须有"模型卡 + 验证 + 持续监测 + 退出机制"全生命周期。
- **金发〔2026〕8 号**：AI 金融应用须可解释、可人工复核、可审计；模型决策留痕。
- **Google Model Cards / EU AI Act 高风险**：模型元数据、预期用途、伦理与偏差评估强制登记。
- **行业做法（平安/招行）**：模型上线前过"评测门禁"，上线后月度漂移监控 +  champion-challenger 并行。

### 1.2 模型卡（Model Card）模板（synapClaim 域）

```
ModelCard {
  modelId, version,
  owner,                    // 责任人（非"AI 团队"泛称）
  purpose,                 // 预期用途与边界（如"仅门诊小额快赔 AiAssess 建议"）
  dataLineage,             // 训练/标注数据源、时间窗、脱敏方式
  inputOutputSpec,         // 入参 schema / 出参 schema
  metrics: { precision, recall, f1, falseRejectRate, falseApproveCost },
  biasAssessment,          // 分群（年龄/地区/产品）表现差异，阈值告警
  limitations,             // 不适用的场景（如高额、非门诊）
  driftPolicy,             // 触发重训的漂移阈值
  signoff                  // 风控 + 合规 + 技术三方签字
}
```
- 所有进入 `AiAssess` / `FraudScreen` 的模型（含视觉 E1、图 E2、LLM 评测）**必须有模型卡方可启用**。
- 模型卡存 `bone-ai`（C7 评测门禁侧），synapClaim 仅持 `modelId` 引用，经 `EvaluationPort` 取评测态。

### 1.3 评测门禁（CI/CD 不可绕过）

- **负向探针（防假绿）**：每个模型门禁须包含"删掉实现哪一行会红"的对抗用例；RAG 检索须真实执行断言而非仅比对生成 SQL 文本（参照工程落地版"取证铁律"）。
- **门禁项**：精度/召回达标（如 F1 ≥ 0.85）、误伤率 ≤ 1%、内容安全（复用 C8）零高危漏检、可解释字段完整。
- **失败即阻塞**：门禁不过 → 模型不可进入 `AiAssess`/`FraudScreen`（端口侧返回 `ModelNotCleared`，调用方降级转人工）。

### 1.4 漂移与 champion-challenger

| 漂移类型 | 监测 | 动作 |
|---|---|---|
| 数据漂移（输入分布） | PSI（总体稳定性指数）> 0.2 告警、> 0.25 阻塞 | 触发重训评审 |
| 概念漂移（标签关系） | 线上精度周环比下滑 > 5% | 暂停自动建议，退回人工 |
| 性能漂移 | 端到端延迟/P95 超 SLA | 告警 + 降级 |
| 偏差 | 分群指标差异超阈值 | 合规复核 |

- **champion-challenger**：新模型先在 `Shadow` 态与现模型双跑比对的结论，一致率 ≥ 阈值且 challenger 不劣化，再升 `Assisted`；灰度比例经 `feature_toggle` 外置，改比例不发版。
- 监测数据来自 `TracePort`(C4) 决策轨迹 + `EvaluationPort`(C7) 评测回流。

### 1.5 治理工作流

1. 模型登记（模型卡）→ 2. 评测门禁（CI 阻塞）→ 3. Shadow 双跑 → 4. Assisted 灰度 → 5. 全量（仍保留人工确认护栏）→ 6. 漂移触发退出/重训。
- 退出机制：模型弃用须在 `ModelCard.signoff` 记录，端口侧返回 `ModelDeprecated`，调用方降级。

---

## 2. C10 直付通道（集成三态灰度）

### 2.1 业界实践对标

- **泰康/众安/平安"数据直连 + 院端直付"**：经保司/TPA 既有通道对接医院 HIS/医保，理赔金直结，客户"零垫付"。
- **本平台约束（工程落地版 §1.2/V1.0 修订）**：V1.0 **经保司/TPA 既有通道**做院端直付试点，复用普康宝式内部账户与直结能力，**不要求自建医保局/银行网关**；HIS/医保局深度直连留 V2。

### 2.2 三态灰度（Shadow / Assisted / Automated）

| 态 | 行为 | 资金动作 | 退出条件 |
|---|---|---|---|
| `Shadow` | 仅旁路计算直付建议，不实际出款 | 无 | 建议与人工结论一致率 ≥ 99% 且零资损样例 |
| `Assisted` | 出款前需人工确认（类型护栏 `Confirmed`） | 出款 | 误拒/误付率 ≤ 阈值且 MRM 漂移达标 |
| `Automated` | 高信任客户免单证直赔，系统直出 | 出款（仍受 C11 额度 + 反诈熔断） | 任何异常 → 回退 Assisted |

- 三态经 `feature_toggle` 外置（工程落地版 §11.4），**改态不发版**；默认 `Shadow`，禁止默认 `Automated`（合规前置，ADR-1/金发8号）。

### 2.3 接入契约与三件套（防 SSRF / 重放 / 重复出款）

```
IntegrationPort
  入参 ChannelCommand {
         tenantId,
         channelType,            // TPA / INSURER（保司/TPA 通道）
         command,                // PRE_AUTHORIZE / SETTLE / REFUND
         payloadEncrypted,       // 密文，不出明文 PII
         hmac, timestamp, nonce, // 防重放三件套
         idempotencyKey          // {claimNo}:{stage}:{attempt}
       }
  出参 ChannelResult { status, settlementRef, ledgerReservationId? }
```

- **SSRF 防御**：通道域名/IP 走**平台注册白名单**（`ATTACHMENT.uploadApi` 同源纪律），禁止前端传入任意 URL；内网 endpoint 拉取，外网仅预览。
- **HMAC + 时间戳 + nonce**：防篡改与重放（nonce 窗口内唯一，超时拒绝）；密钥进程内缓存、遇 `401` 自动重取重试一次。
- **幂等**：`idempotencyKey` 三段式（与资金支付同源），重复 `SETTLE` 返回首次 `settlementRef`，杜绝双付。
- **失败降级**：通道不可用 → 退回"报销快赔 + mock 支付网关"兜底主路径（工程落地版 §1.2），案件不卡死。

### 2.4 信任额度联动（C11 LedgerPort）

- 高信任客户免单证直赔前，先经 `LedgerPort.quotaReservation` 做两阶段 reserve/commit/release（域中立内核）。
- 反诈命中（`FraudScreen` 挂起 `FRAUD`）→ 信任额度自动 `release` 并熔断该客户 `Automated` 态，降级 `Assisted`。
- 额度仅预授权，最终出款仍受 §6.3 `SUGGEST_REJECT` 人工确认护栏（AI 不碰钱）。

### 2.5 合规与审计

- 直付全程经 `TracePort`(C4) 留痕：channel 命令、HMAC 校验态、出款 ref、人工确认 `Confirmed`。
- 对外内容（客户沟通/直付通知）复用 C8 `ContentSafetyGuard`（ADR-1 内容安全默认开）。
- 资金事件走 `OutboxPort` 事务性发件箱（工程落地版 ADR-008），零丢失。

---

## 3. 串联与治理闭环

```
AiAssess/FraudScreen(模型) ──EvaluationPort(C7)──> 评测门禁 + 漂移监控 + 模型卡
        │                                            │
        ▼                                            ▼
Adjust(直付) ──IntegrationPort(C10)──> 三态灰度 + SSRF/HMAC/幂等
        │                                            │
        ▼                                            ▼
   LedgerPort(C11) ──信任额度预授权──> 反诈命中即熔断 ──> NotificationPort(C9) 预警
        │
        ▼
   OutboxPort + TracePort(C4) ──> 资金零丢失 + 全链路可回放
```

- **MRM 守门**：未过评测门禁的模型不进 `AiAssess`/`FraudScreen`；直付通道未达 `Shadow` 一致率不升 `Assisted`。
- **企业契合**：信创（通道经保司/TPA 既有设施，不引入境外 SaaS）、数据不出域（PII 密文 + 通道白名单）、多租户（`tenantId` 贯穿所有端口）、合规（金发8号可解释 + 人工确认护栏）。

---

## 4. 风险与回退

- **直付资损高发**：`Automated` 态仅开放高信任客户 + 受 C11 额度 + 反诈熔断三重约束；任一异常即回退 `Assisted`。
- **通道供应商风险**：保司/TPA 通道故障 → 退回报销兜底，不阻塞理赔主链路。
- **模型漂移**：champion-challenger 期间 challenger 不劣化才升阶；漂移超阈值自动退回人工。
- **回退纪律**：所有增强项故障 → 统一 fail-safe 转人工（安全兜底优先于 R-FAST 快赔）。

---

## 5. 下一步

1. E4 模型卡模板落地为 `bone-ai`(C7) 结构化资产 + CI 门禁接入（送 `AiAssess`/`FraudScreen` 前强制校验）。
2. C10 直付对接首个保司/TPA 通道 PoC：`Shadow` 态双跑 + HMAC/幂等三件套压测。
3. 与 E1/E2 联动：反诈命中 → C11 信任额度下调 + C10 `Automated` 熔断回归 `Assisted`。
