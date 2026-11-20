# Spec Delta — procurement/legacy-payment-hard-isolation

## Purpose

存量采购付款逃逸处理器的启用开关与启动期 fail-closed 契约，确保低代码 Runtime 动作不会绕过付款裁决链路直接触发付款，把 PA-04「采购域不作付款许可裁决」的文档约束落到代码层硬隔离。

## ADDED Requirements

### Requirement: 付款处理器缺省禁用（fail-closed）
系统 SHALL 以配置属性 `procura.purchase-pay-handler.enabled` 控制存量付款逃逸处理器 `purchaseOrderPayHandler` 的装配；该属性缺省值 MUST 为 `false`。当属性未配置、取值缺省或为 `false` 时，该处理器 MUST NOT 注册为可调用的 bean，运行期动作无法解析到该 bean。

#### Scenario: 缺省配置下处理器不注册
- **WHEN** 应用启动时未提供 `procura.purchase-pay-handler.enabled` 配置
- **THEN** Spring 上下文不包含 `purchaseOrderPayHandler` bean，任何按该 bean 名解析动作的调用得到"未找到处理器"的失败结果，而不是成功付款

#### Scenario: 显式关闭时处理器不注册
- **WHEN** 配置 `procura.purchase-pay-handler.enabled=false`
- **THEN** Spring 上下文不包含 `purchaseOrderPayHandler` bean

### Requirement: 显式开启才注册且仅限演示占位
系统 SHALL 仅在 `procura.purchase-pay-handler.enabled=true` 时注册该处理器 bean；开启后该处理器 MUST 保持"低代码演示 / 占位"语义，不改变存量付款逻辑（参数校验、流水号与凭证号生成），且 MUST NOT 被生产付款链路（以 `PaymentRequested` 事件为准）用作实际出金裁决。

#### Scenario: 显式开启时处理器注册
- **WHEN** 配置 `procura.purchase-pay-handler.enabled=true`
- **THEN** Spring 上下文包含且仅包含一个 `purchaseOrderPayHandler` bean，其行为与存量实现一致

#### Scenario: 开启态仍不产生真实出金
- **WHEN** 在开启态调用该处理器
- **THEN** 仅返回模拟的付款流水号与凭证号（note 明确为占位），不触发任何真实资金划转

### Requirement: 启动期 fail-closed 可观察证据
系统 SHALL 在启动期输出该开关的生效状态（启用 / 禁用），使"缺省即禁用"可从启动日志与装配测试中观察与验证；缺省或关闭态的日志措辞 MUST 明确提示该处理器已硬隔离、生产付款以事件链路为准。

#### Scenario: 禁用态启动日志可诊断
- **WHEN** 以缺省或 `false` 配置启动，且处理器未注册
- **THEN** 启动日志出现明确标识该付款处理器已禁用 / 硬隔离的提示

#### Scenario: 装配测试锁定缺省禁用
- **WHEN** 以缺省配置的 ApplicationContextRunner 加载采购自动配置
- **THEN** 断言上下文中不存在 `PurchaseOrderPayHandler` bean；另以显式 `true` 配置断言存在且仅一个

### Requirement: 不改存量付款业务逻辑与禁用清单
系统 SHALL 保持存量处理器已有的必填参数校验（channel / payAmount）与金额正向校验不变；本次硬隔离 MUST NOT 删除或改写这些业务守卫，MUST NOT 违反基线"存量类退役前不得删改"约束——仅新增装配层护栏。

#### Scenario: 既有参数守卫仍然生效
- **WHEN** 在显式开启态以缺 channel 或非正金额调用处理器
- **THEN** 仍抛出 `RUNTIME_ACTION_INVALID_PARAMS`（400 语义），错误码与文案不变
