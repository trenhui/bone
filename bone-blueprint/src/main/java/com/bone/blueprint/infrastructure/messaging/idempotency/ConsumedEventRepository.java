package com.bone.blueprint.infrastructure.messaging.idempotency;

import com.bone.metadata.sdk.Repository;

/**
 * 幂等去重表仓储（基础设施层）。
 *
 * <p>与 {@code domain/repository} 的领域仓储不同：它服务「消息不重复处理」这一技术保证，不属于业务领域模型， 故随幂等组件留在 infrastructure。
 */
public interface ConsumedEventRepository extends Repository<ConsumedEventRecord, Long> {}
