package com.bone.metadata.sdk.query.dsl;

import com.bone.metadata.sdk.sql.executor.SqlExecutor;

/**
 * 查询构建器工厂 - 创建类型安全的流畅查询
 */
public class QueryBuilder {

    private final SqlExecutor sqlExecutor;

    public QueryBuilder(SqlExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
    }

    /**
     * 为实体类创建查询
     */
    public <T> FluentQuery<T> from(Class<T> entityClass) {
        return new DefaultFluentQuery<>(entityClass, sqlExecutor, "t");
    }

    /**
     * 为实体类创建查询（指定别名）
     */
    public <T> FluentQuery<T> from(Class<T> entityClass, String alias) {
        return new DefaultFluentQuery<>(entityClass, sqlExecutor, alias);
    }

    /**
     * 获取 SQL 执行器
     */
    public SqlExecutor getSqlExecutor() {
        return sqlExecutor;
    }

    /**
     * 静态工厂方法
     */
    public static QueryBuilder create(SqlExecutor sqlExecutor) {
        return new QueryBuilder(sqlExecutor);
    }

    /**
     * 添加静态便捷方法
     */
    public static <T> FluentQuery<T> from(SqlExecutor sqlExecutor, Class<T> entityClass) {
        return new QueryBuilder(sqlExecutor).from(entityClass);
    }

    /**
     * 添加静态便捷方法（带别名）
     */
    public static <T> FluentQuery<T> from(SqlExecutor sqlExecutor, Class<T> entityClass, String alias) {
        return new QueryBuilder(sqlExecutor).from(entityClass, alias);
    }
}