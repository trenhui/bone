/**
 * application 层技术出站端口：承载技术 concern 的出站接口。
 *
 * <p>这些端口与业务语义无关，是框架/基础设施能力的抽象：Outbox 写入、MQ 投递、 租户上下文获取、回调验签等。adapter 层禁止直引此包下的端口（E-4.2 分层约束）。
 *
 * <p><b>放什么</b>：OrderOutboxWriter、OrderMessageSender、OrderOutboxRelayPort、
 * TenantProvider、PaymentSignaturePort。
 *
 * <p><b>不放什么</b>：业务语义的外部调用（如 PaymentGateway、InventoryGateway）—— 那是 domain/gateway 的职责。
 */
package com.bone.blueprint.application.port.out;
