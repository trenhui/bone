package com.bone.blueprint.infrastructure.messaging.outbox;

import com.bone.metadata.sdk.Repository;

/**
 * Outbox 记录仓储——<strong>基础设施层仓储</strong>，不是领域仓储。
 *
 * <p>与 {@code domain/repository} 下的领域仓储的区别：领域仓储服务于聚合的加载与保存，是领域模型的一部分； 本仓储服务于消息投递的技术状态，故随 Outbox
 * 记录一并放在基础设施层。
 */
public interface OrderOutboxRepository extends Repository<OrderOutboxRecord, Long> {}
