---
comet_change: procurement-legacy-payment-hard-isolation
role: technical-design
canonical_spec: openspec
archived-with: 2026-11-20-procurement-legacy-payment-hard-isolation
status: final
---

# Design — 存量采购付款逃逸处理器硬隔离（D-06）

## Context

`bone-app-purchase` 是待退役的存量模块，其 `PurchaseOrderPayHandler`（bean 名 `purchaseOrderPayHandler`）由 `PurchaseComponentScanConfiguration` 扫描 `@Service` 无条件注册（`PurchaseApplicationAutoConfigurationWiringTest:19` 断言 `hasSingleBean`）。真源已裁决采购域不作付款许可裁决（PA-04 / 能力基线 §7.2 约束 #3），生产付款以 `PaymentRequested` 事件为准，但该 Runtime 动作仍可被低代码流程直接触发，存在绕过裁决的风险。约束：基线要求"退役前不得删改存量类"，因此只能在装配层加护栏，不能改付款处理逻辑本身。

## Goals / Non-Goals

**Goals:**
- 缺省 / 未配置即禁用该处理器，使 Runtime 动作无法解析到 bean（fail-closed）。
- 显式 `true` 才注册，且保持"演示 / 占位"语义。
- 启动日志与装配测试提供可观察的禁用证据。
- 决策寄存器 D-06 状态如实更新为 Landed（附证据，不伪造 owner）。

**Non-Goals:**
- 不改付款处理器的参数校验、流水号 / 凭证号生成等业务逻辑。
- 不接真模型、不新增 REST / 事件 / DDL / 权限码契约。
- 不改 CI 三入口判据集合、不碰 `ci.yml` / `check.sh` / `FULL_GATE_KEYS`（非 L3）。
- 不处理其余 17 项 owner / 法务 / 压测 / 评测 backlog。

## Decisions

### D1 — 用条件装配实现 fail-closed，而非改处理器类
在 boot 层用一个专用配置类，通过 `@ConditionalOnProperty(name="procura.purchase-pay-handler.enabled", havingValue="true", matchIfMissing=false)` 控制 `purchaseOrderPayHandler` 的注册；同时把组件扫描对该 bean 的自动注册排除（REGEX excludeFilter 增加该 bean），使开关成为唯一注册入口。

- **备选 1（否决）**：直接在 `PurchaseOrderPayHandler` 类上加 `@ConditionalOnProperty`。这会修改存量类源码，触碰"退役前不得删改存量类"红线，且把开关语义散落到业务类。
- **备选 2（否决）**：仅在处理器 `execute()` 内判断开关后抛异常。bean 仍注册、仍可被解析，不是真正的装配层硬隔离，且同样改存量类。
- **选择理由**：`matchIfMissing=false` 精确实现"缺省即禁用"；装配层隔离使失败发生在 bean 解析阶段（更早、更彻底）；不改存量类源码，符合基线约束。
- **边界澄清**："唯一注册入口"仅指 `bone-app-purchase` 业务副本。平台侧 `com.bone.metadata.runtime.application.PurchaseOrderPayHandler` 以同名 bean + `@ConditionalOnMissingClass("com.bone.app.purchase.PurchaseOrderPayHandler")` 存在（`bone-metadata-server/.../PurchaseOrderPayHandler.java:42-44`），属既有组合根让位设计：bone-boot 组合根下业务类在 classpath，平台副本不注册；standalone metadata-server 不加载采购业务链路，平台副本照常生效且不受本开关约束。本 change 不改变这一既有行为；生产付款链路两形态均以 `PaymentRequested` 事件为准。

### D2 — 启动期日志用始终注册的 `@Bean` 守卫
新增一个始终注册的 `LegacyPaymentIsolationReporter`（由 `PurchaseApplicationAutoConfiguration` 的 `@Bean` 方法注册），构造时读取开关值并输出 INFO / WARN 日志：禁用态明确提示"付款处理器已硬隔离，生产付款以 PaymentRequested 事件为准"；启用态提示"仅为演示占位，禁止生产出金"。该 bean 独立于被隔离的处理器，始终可用，保证日志证据不随开关消失。装配测试补 `hasSingleBean(LegacyPaymentIsolationReporter.class)` 断言，防止 `@Bean` 声明被误删导致日志测试假绿。

### D3 — 测试用 ApplicationContextRunner 双态断言
改造 `PurchaseApplicationAutoConfigurationWiringTest`：缺省 / `false` 配置断言 `doesNotHaveBean(PurchaseOrderPayHandler.class)`；显式 `true` 断言 `hasSingleBean`。保留 `PurchaseOrderPayHandlerTest` 的参数守卫用例不变（在纯单元测试层面直接 new 处理器，不受装配开关影响），证明业务逻辑未被改写。

### D4 — 新增 capability spec + 决策寄存器落地记录
新建 `procurement/legacy-payment-hard-isolation` capability 定义可观察契约；在 DECISION-REGISTER 把 D-06 更新为 Landed，验收判据指向本 change 与具体 `file:line`。不动 CHANGELOG 历史快照。

## Risks / Trade-offs

- [存量行为变更风险] 缺省禁用会改变 `bone-app-purchase` 现有"付款动作可用"的默认行为 → 缓解：这正是 D-06 要求的 fail-closed 目标；显式 `true` 可恢复；且该模块为演示 / 占位、待退役，生产链路以事件为准。
- [组件扫描与新配置竞争注册] 若 REGEX 排除不精确可能导致重复或漏注册 → 缓解：用精确 bean 名 / 类全限定名的 REGEX 排除，并以装配双态测试锁定"0 或 1"。
- [配置前缀与平台约定冲突] 需确认 `procura.purchase-pay-handler.enabled` 前缀未被占用 → 缓解：全仓检索确认无同名属性（已核实为零命中）。

## Migration Plan

- 部署：改动为向后兼容的"缺省更安全"变更；升级后付款动作默认不可用，需显式开启才恢复演示。
- 回滚：若某环境依赖该动作且需临时恢复，设置 `procura.purchase-pay-handler.enabled=true` 即可，无需回滚代码。
- 验证：`mvn -o -f bone-apps/bone-app-purchase/pom.xml clean test` 全绿 + procuraAI 三项门禁不回归。

## Open Questions

（无——范围、开关名、默认值、验收判据均由真源与决策寄存器明确。）
