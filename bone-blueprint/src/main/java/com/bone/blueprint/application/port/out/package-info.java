/**
 * application 层技术出站端口：承载技术 concern 的出站接口。
 *
 * <p>这些端口与业务语义无关，是框架/基础设施能力的抽象：Outbox 写入、MQ 投递、 租户上下文获取、回调验签等。<strong>adapter
 * 可以直引本包端口</strong>——E-10.1 只要求依赖向内（{@code adapter → application}），运维型入站适配器注入技术端口是正常形态，本模块的 {@code
 * OrderOutboxRelayJob} / {@code OrderPaymentInconsistencyJob} 即直接注入。
 *
 * <p><b>放什么</b>：OrderOutboxPort、OrderMessagePort、OrderOutboxRelayPort、TenantPort、
 * PaymentSignaturePort、ConsumedEventPort、PricingPort。
 *
 * <p><b>不再放 IdempotencyPort</b>：幂等编排已上收 framework（{@code
 * com.bone.core.idempotency.IdempotencyService} 编排 + {@code IdempotencyStore} 契约）， 本模块只在 {@code
 * infrastructure/idempotency} 提供存储实现，无需再声明本地端口。
 *
 * <p><b>不放什么</b>：业务语义的外部调用（如 PaymentGateway、InventoryGateway）—— 那是 domain/gateway 的职责。
 */
package com.bone.blueprint.application.port.out;
