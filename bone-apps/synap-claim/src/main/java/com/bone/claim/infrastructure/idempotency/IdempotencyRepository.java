package com.bone.claim.infrastructure.idempotency;

import com.bone.metadata.sdk.Repository;

/**
 * 幂等快照仓储（表 {@code claim_idempotency_record}）。
 *
 * <p>仅用 SDK 原生 CRUD 与 Criteria 查询（无自定义 SQL 模板）——与 blueprint {@code IdempotencyRepository} 同构，由
 * {@code @EnableSqlRepositories} 代理实现。
 */
public interface IdempotencyRepository extends Repository<IdempotencyRecord, Long> {}
