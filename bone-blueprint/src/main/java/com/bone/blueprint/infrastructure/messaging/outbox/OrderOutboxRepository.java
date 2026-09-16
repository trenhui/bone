package com.bone.blueprint.infrastructure.messaging.outbox;

import com.bone.metadata.sdk.Repository;

/**
 * Outbox 记录仓储——基础设施层仓储。
 *
 * <p>与 {@code domain/repository} 下的领域聚合仓储的区别：领域仓储承载聚合的加载/保存契约， 属于领域模型的一部分；本仓储服务于消息投递的技术状态（Outbox
 * 表），是跨聚合一致性保障的 基础设施机制，不属于业务领域，故随 Outbox 组件一并留在 infrastructure 层。
 */
public interface OrderOutboxRepository extends Repository<OrderOutboxRecord, Long> {}
