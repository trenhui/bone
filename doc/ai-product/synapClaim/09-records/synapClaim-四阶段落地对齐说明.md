---
doc_id: record.synapclaim.four-phase-alignment
role: record
currency: historical
---

# synapClaim 四阶段实施路线图 · 落地对齐说明

> 用途：面向架构/产品评审，说明路线图每个节点的真实落地程度与解锁前置。
> 事实底座：`synapClaim-实现度与联调评估报告.md`（HEAD `5b6b81ae3`，real-services-no-mock）。
> 口径：🟢 完整实现 · 🟡 部分/默认关 · 🔴 桩或未实现。四态对应 `IMPLEMENTED`/`PARTIAL_DEFAULT_OFF`/`STUB_SAFETY_LOCK`/`DESIGN_ONLY`。

## 一句话结论

**主干按路线图落地且能跑；后期阶段的「未实现」多数是刻意的安全姿态（fail-closed / Shadow / 默认关）或外部前置（盲标 / 目录 / 合规 / 双签），不是靠继续写代码就能补齐。**

## 四阶段对齐总表

| 阶段 | 🟢 完整实现 | 🟡 部分 / 默认关 | 🔴 桩 / 仅设计 |
|---|---|---|---|
| 一 · MVP 闭环 | 报案导入、保单关联（真实 `t_claim_policy`）、人机审核 | 单证发票校验（无税局验真转人工）、基础理算（生产走 `@Deprecated AdjustmentEngine` 回退）、结案回传（本地桩） | — |
| 二 · 理算额度 | 额度锁/冻结/扣减/释放（`@Version`+条带锁） | 可读推导式、复杂免赔与共享保额（六维仅落 2 维） | — |
| 三 · 智能化 | — | 质检规则引擎（抽检默认 5%、自动免审默认关）、OCR（默认 `mode:text` 正则桩） | 医保三目录对码（`catalog_policy=NONE`、无目录数据） |
| 四 · 生产级 | 多租户隔离与权限（185 权限点 + CI 护栏） | — | RocketMQ（仓库有 MQ 代码、理赔链路未用）、保单/支付中台（无独立微服务）、Go-Live 13 册 `draft` |

## 逐节点四态登记（15 节点）

### 阶段一 · MVP 闭环
| 节点 | 四态 | 证据锚点 / 说明 |
|---|---|---|
| 报案导入 | 🟢 IMPLEMENTED | 受理工作台 + 导入 E2E 通过 |
| 保单关联 | 🟢 IMPLEMENTED | 真实保单表 `t_claim_policy`（**两处实质偏离之一**：超前替换原「模拟关联」） |
| 人机审核 | 🟢 IMPLEMENTED | 审核工作台 + 复核 E2E 通过 |
| 单证发票校验 | 🟡 PARTIAL_DEFAULT_OFF | 文本层结构化，无税局验真；不通过转人工 |
| 基础理算 | 🟡 PARTIAL_DEFAULT_OFF | 生产金额仍走 `@Deprecated AdjustmentEngine` 回退 |
| 结案回传 | 🟡 PARTIAL_DEFAULT_OFF | 本地桩回传 |

### 阶段二 · 理算额度
| 节点 | 四态 | 证据锚点 / 说明 |
|---|---|---|
| 额度锁/冻结/扣减/释放 | 🟢 IMPLEMENTED | `@Version` + 条带锁，四本账对齐，impl-log 变异证据 |
| 可读推导式 | 🟡 PARTIAL_DEFAULT_OFF | 代码就绪，默认关 |
| 复杂免赔 | 🟡 PARTIAL_DEFAULT_OFF | 六维仅落 2 维（医院等级、病种，见 `LiabilityRule.java:382`）；其余 4 维无配置列 |
| 共享保额 | 🟡 PARTIAL_DEFAULT_OFF | 部分落地 |

### 阶段三 · 智能化
| 节点 | 四态 | 证据锚点 / 说明 |
|---|---|---|
| 质检规则引擎 | 🟡 PARTIAL_DEFAULT_OFF | 抽检默认 5%、自动免审默认关 |
| OCR | 🟡 PARTIAL_DEFAULT_OFF | 默认 `mode:text` 正则桩 |
| 医保三目录对码 | 🔴 DESIGN_ONLY | `catalog_policy=NONE`，无授权目录数据 |

### 阶段四 · 生产级中台
| 节点 | 四态 | 证据锚点 / 说明 |
|---|---|---|
| 多租户隔离与权限 | 🟢 IMPLEMENTED | 185 权限点 + CI 护栏 |
| RocketMQ 中继 | 🔴 DESIGN_ONLY | 仓库有 MQ 代码，理赔链路未用；`mq-enabled=false` |
| 保单/支付中台 | 🔴 DESIGN_ONLY | 无独立微服务 |
| Go-Live 合规 13 册 | 🔴 DESIGN_ONLY | 13 册全部 `draft`，未标 `current` |

## 四个必须点破的判读点

1. **代码存在 ≠ 生效**。QLExpress 规则引擎、抽检、租户隔离是真跑；自动放行（`auto-approve.enabled=false`）、LLM/图像解析（`mode:text`）、目录准入（`NONE`）、MQ 中继（`mq-enabled=false`）全部默认关——这是受监管金融理赔的 fail-closed / Shadow 姿态，不是遗漏。
2. **免赔只落 2/6 维**。医院等级、病种已落；区域/医保/药品/诊疗无配置列、依赖外部目录口径，属「表达不出来」而非「配了不读」。收口方向是显式拒绝 + 稳定原因码，不臆造规则、不新增 DDL。
3. **两处实质偏离**。保单关联已超前为真实表 `t_claim_policy`；图计算按授权做成本地确定性图筛查 `E2_GRAPH_LOCAL`（**两处实质偏离之一**：发票跨案/同证件 90 天高频，命中只转人工不碰钱），而非接 E2 平台图库。
4. **视觉反诈恒 `available=false` 是资金闸安全锁**。它是决策图恒走 `MANUAL_REQUIRED` 的唯一真桩；改成 `true` 会打开自动放款，属 L2 资金变更需双签。它不是待修的缺陷。

## 解锁路线（外部前置，AI 不代签）

| 解锁项 | 缺什么 | 门禁判据 | 责任方 | AI 边界 |
|---|---|---|---|---|
| 盲标数据集 | ≥300 条真实脱敏人工盲标 | `BASELINE_WIRED` / `FROZEN_EVALSET_ACTIVE` | 算法/评测团队 | 只跑评测；合成数据标 SIMULATION，不冒充真人 |
| 目录准入 | 授权目录数据 + `CATALOG_POLICY` NONE→MANDATORY | `CATALOG_ADMISSION_ACTIVE` | 产品 + 架构 | 不改默认策略；改错会全量停摆 |
| Go-Live 13 册 | 补正文并标 `current` | `GOLIVE_NOT_DRAFT` | 合规/法务（L4） | 禁止代签 |
| 资金闸双签 | 视觉桩 `available=false`→`true` 的 L2 变更 | 决策图 `AUTO_APPROVE` 路径 | 指定 Review 人 + 产品签核人 | 不单方变更 |

> 严格结论：方案方向与主干对齐，但「智能化升级」和「中台沉淀」两个后期阶段并未真正实现——主要是刻意的安全姿态 + 外部数据/合规前置未到位。当前成熟度仍 L0、`l3Claimable=false`；补齐外部输入后须按治理流程逐步解锁，不得把 L0 伪装成 L3。
