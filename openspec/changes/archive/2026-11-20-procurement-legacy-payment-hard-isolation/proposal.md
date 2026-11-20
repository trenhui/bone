# Proposal — 存量采购付款逃逸处理器硬隔离（D-06 / F-E）

## Why

采购域真源已裁决：采购域不作付款许可裁决，生产付款链路以 `PaymentRequested` 事件为准（`采购域-BONE框架能力对齐与落地基线.md §7.2` 约束 #3、`采购总体架构方案.md` PA-04）。但存量 `bone-app-purchase` 的 `PurchaseOrderPayHandler` 目前经 autoconfig 无条件注册为 bean（`PurchaseApplicationAutoConfigurationWiringTest:19` 断言 `hasSingleBean`），低代码 Runtime 动作可直接触发该处理器"付款"，存在绕过付款裁决直接出金的生产风险。诊断 F-E 与决策寄存器 D-06 要求把这条文档约束落到代码开关：`procura.purchase-pay-handler.enabled` 默认 `false` + 启动期 fail-closed，实现代码层硬隔离。文档声明已就位，代码开关一直是待 owner 落地的开口项。

## What Changes

- 在 `bone-app-purchase` 新增配置属性 `procura.purchase-pay-handler.enabled`，**默认值 `false`**。
- 使 `PurchaseOrderPayHandler` 的装配受该开关控制：开关缺省 / 未配置 / `false` 时，该处理器不注册为 bean（fail-closed 禁用）；仅显式 `true` 时才注册，且只作为低代码演示 / 占位用途。
- 增加启动期 fail-closed 的可观察证据（配置状态日志 + 装配断言），使"缺省即禁用"在启动日志与测试中可见、可诊断。
- 保留存量付款处理逻辑本身不变（不改三单匹配由 `UiSchemaRuleEnforcer` 承载的职责边界、不改参数校验与流水号生成），仅加装配层隔离护栏——不违反基线"退役前不得删改存量类"。
- 在决策寄存器把 D-06 从 `Accepted + 外部依赖` 更新为 `Landed`，附本 change 与 `file:line` 证据；不伪造 owner 签署。

## Capabilities

### New Capabilities
- `procurement/legacy-payment-hard-isolation`: 存量采购付款逃逸处理器的启用开关与启动期 fail-closed 契约——缺省即禁用、显式开启才注册、运行期不绕过付款裁决。

### Modified Capabilities
（无）

## Impact

- 代码：`bone-apps/bone-app-purchase`（`boot` 自动配置、`PurchaseOrderPayHandler` 装配条件、启动日志、装配与负向测试）。
- 契约：新增配置属性 `procura.purchase-pay-handler.enabled`（默认 `false`）；不改变任何 REST / 事件 / DDL / 权限码契约。
- 文档：`doc/ai-product/procuraAI/decisions/DECISION-REGISTER.md` 的 D-06 状态行；不动 CHANGELOG 历史快照、不伪造 owner。
- 门禁：不新增 CI 判据、不改 `FULL_GATE_KEYS`、不碰 `.github/workflows/ci.yml` / `scripts/check.sh`（非 L3）。验证以模块 `clean test` + 现有 procuraAI 门禁不回归为准。
