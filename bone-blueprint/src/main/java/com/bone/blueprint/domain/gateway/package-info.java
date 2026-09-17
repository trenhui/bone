/**
 * domain 层业务网关：表达"业务规则依赖的外部事实"的出站端口。
 *
 * <p>这些端口承载的是 <strong>业务语义</strong>（支付网关、库存查询、余额校验等）， 由 application 层编排使用，由 infrastructure
 * 层提供实现（Mock / Feign / HTTP 等）。
 *
 * <p><b>放什么</b>：PaymentGateway、InventoryGateway——代表"账户余额、库存数量"这类 业务规则依赖的外部系统事实。
 *
 * <p><b>不放什么</b>：技术 concern 如 MQ 投递、Outbox 写入、租户上下文、缓存、时钟、 通知、文件、幂等等——这些是 application/port/out 的职责。
 *
 * <p>当前白名单由 {@code ArchitectureTest.domain_gateway_only_business_gateways} 门禁， 新增业务网关须同步更新白名单。
 */
package com.bone.blueprint.domain.gateway;
