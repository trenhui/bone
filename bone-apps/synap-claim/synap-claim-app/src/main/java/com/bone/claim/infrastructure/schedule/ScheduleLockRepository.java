package com.bone.claim.infrastructure.schedule;

import com.bone.metadata.sdk.Repository;

/**
 * 调度单写者锁仓储（表 {@code t_schedule_lock}）。
 *
 * <p>仅用 SDK 原生 CRUD 与 Criteria 查询（与 {@code IdempotencyRepository} 同构），无自定义 SQL 模板—— 由
 * {@code @EnableSqlRepositories} 代理实现。互斥语义全部在 {@link ScheduleLockService} 以「唯一键插入 +
 * {@code @Version} 更新」两条竞态路径表达，本接口只提供存储能力。
 */
public interface ScheduleLockRepository extends Repository<ScheduleLock, Long> {}
