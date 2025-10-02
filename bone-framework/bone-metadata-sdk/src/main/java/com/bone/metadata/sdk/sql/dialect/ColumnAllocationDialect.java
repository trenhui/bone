package com.bone.metadata.sdk.sql.dialect;

import org.springframework.dao.DataAccessException;

/**
 * ColumnAllocationDialect 定义不同数据库的 SQL 片段接口
 */
public interface ColumnAllocationDialect {

    /**
     * 返回当前方言对应的 DatabaseType
     */
    com.bone.metadata.sdk.domain.enums.DatabaseType getDatabaseType();

    /**
     * SKIP LOCKED 语法片段（查询回收列时用）
     */
    String getSkipLockedClause();

    /**
     * FOR UPDATE 语法片段（查询最大索引时用）
     */
    String getForUpdateClause();

    /**
     * 插入或冲突更新 SQL
     */
    String getInsertSQL();

    /**
     * 清理过期 RECYCLED 列的 SQL
     * @param days 表示清理 recycled_at 小于当前时间减去 days 天的记录
     */
    String getPurgeSQL(int days);

    /**
     * 归档旧列到历史表的 SQL
     * @param days 表示 archive 条件 updated_at 小于当前时间减去 days 天的记录
     */
    String getArchiveSQL(int days);

    /**
     * 是否为锁超时异常，用于判断是否需要重试或降级
     */
    boolean isLockTimeout(DataAccessException e);

    /**
     * 简单分页片段（LIMIT/OFFSET 或者其它数据库对应）。
     * @param limit  最大行数
     * @param offset 偏移量
     */
    default String buildPagination(int limit, int offset) {
        return limit > 0 ? String.format(" LIMIT %d OFFSET %d", limit, offset) : "";
    }
}
