package com.bone.blueprint.infrastructure.idempotency;

import com.bone.metadata.sdk.Repository;

/**
 * 幂等快照仓储（基础设施层，SDK 代理实现）。
 *
 * <p>服务「请求不重复执行」的技术保证，不属于业务领域模型，故留在 infrastructure。
 */
public interface IdempotencyRepository extends Repository<IdempotencyRecord, Long> {}
