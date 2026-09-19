/**
 * 跨边界集成事件（{@code *IntegrationEvent} / {@code IntegrationEnvelope}）与消费者。
 *
 * <p><b>与 {@code application/event} 区分</b>：{@code event} 处理「模块内领域事件」（聚合协作）；本包处理「发往其它上下文的 消息」——由
 * Outbox 同事务落库、经中继投递，落库前在 {@code infrastructure} 完成领域事件→集成事件的 ACL 转换。
 */
package com.bone.blueprint.application.integration;
