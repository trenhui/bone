package com.bone.metadata.sdk.query.dsl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * DSL查询构建器 - 基于文档方案实现的生产级QueryBuilder
 */
public class QueryBuilder {

    private static final Logger logger = LoggerFactory.getLogger(QueryBuilder.class);
    private static SqlExecutorAdapter sqlExecutorAdapter;
    private static QueryProperties queryProperties;

    // 私有构造函数，防止实例化
    private QueryBuilder() {}

    // 静态初始化方法，供Spring Boot自动配置使用
    public static void initialize(SqlExecutorAdapter adapter, QueryProperties properties) {
        sqlExecutorAdapter = adapter;
        queryProperties = properties;
        logger.info("QueryBuilder initialized successfully");
    }

    /**
     * 创建查询构建器的入口方法
     * @param entityClass 实体类
     * @param <T> 实体类型
     * @return 流畅的查询接口
     */
    public static <T> FluentQuery<T> from(Class<T> entityClass) {
        if (sqlExecutorAdapter == null) {
            throw new IllegalStateException("QueryBuilder not initialized. Call initialize() first.");
        }
        return new DefaultFluentQuery<>(entityClass, sqlExecutorAdapter);
    }

    /**
     * 流畅的查询接口 - 提供链式调用的查询API
     */
    public interface FluentQuery<T> {
        // 条件查询方法
        <F> Condition<T, F> where(String fieldName);
        <F> Condition<T, F> where(Function<T, F> fieldGetter);

        // 排序方法
        FluentQuery<T> orderBy(String fieldName, boolean isAsc);

        // 分页方法
        FluentQuery<T> limit(int limit);
        FluentQuery<T> offset(int offset);

        // 分组方法
        FluentQuery<T> groupBy(String... fieldNames);

        // 关联查询方法
        <J> Join<T, J> join(Class<J> joinClass, String joinAlias);
        <J> Join<T, J> leftJoin(Class<J> joinClass, String joinAlias);
        <J> Join<T, J> rightJoin(Class<J> joinClass, String joinAlias);
        <J> Join<T, J> fullJoin(Class<J> joinClass, String joinAlias);

        // 执行方法
        java.util.List<T> list();
        T single();
        long count();
        PageResult<T> page(int pageNum, int pageSize);
        boolean exists();
    }

    /**
     * 关联查询接口
     */
    public interface Join<T, J> {
        // 关联条件设置
        <F, JF> FluentQuery<T> on(String entityField, String joinEntityField);
        <F> Condition<T, F> where(String fieldName);
        FluentQuery<T> orderBy(String fieldName, boolean isAsc);
        FluentQuery<T> limit(int limit);
        FluentQuery<T> offset(int offset);
        FluentQuery<T> groupBy(String... fieldNames);
    }

    /**
     * 分页结果模型
     */
    public static class PageResult<T> {
        private java.util.List<T> records;
        private long total;
        private int pageNum;
        private int pageSize;
        private int totalPages;
        private boolean hasNext;
        private boolean hasPrevious;

        public java.util.List<T> getRecords() {
            return records;
        }

        public void setRecords(java.util.List<T> records) {
            this.records = records;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public int getPageNum() {
            return pageNum;
        }

        public void setPageNum(int pageNum) {
            this.pageNum = pageNum;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public void setTotalPages(int totalPages) {
            this.totalPages = totalPages;
        }

        public boolean isHasNext() {
            return hasNext;
        }

        public void setHasNext(boolean hasNext) {
            this.hasNext = hasNext;
        }

        public boolean isHasPrevious() {
            return hasPrevious;
        }

        public void setHasPrevious(boolean hasPrevious) {
            this.hasPrevious = hasPrevious;
        }
    }

    /**
     * 条件接口
     */
    public interface Condition<T, V> {
        // 条件方法定义
        FluentQuery<T> eq(V value);
        FluentQuery<T> neq(V value);
        FluentQuery<T> gt(V value);
        FluentQuery<T> gte(V value);
        FluentQuery<T> lt(V value);
        FluentQuery<T> lte(V value);
        FluentQuery<T> like(String value);
        FluentQuery<T> notLike(String value);
        FluentQuery<T> in(java.util.Collection<V> values);
        FluentQuery<T> notIn(java.util.Collection<V> values);
        FluentQuery<T> between(V start, V end);
        FluentQuery<T> isNull();
        FluentQuery<T> isNotNull();
    }

    /**
     * 聚合查询接口
     */
    public interface Aggregate<T> {
        long count();
        <N extends Number> N sum(String fieldName, Class<N> resultType);
        <N extends Number> N avg(String fieldName, Class<N> resultType);
        <N> N max(String fieldName, Class<N> resultType);
        <N> N min(String fieldName, Class<N> resultType);
    }

    /**
     * 查询属性配置类
     */
    public static class QueryProperties {
        private boolean enableCache = true;
        private int cacheSize = 1000;
        private int cacheExpireSeconds = 3600;

        public boolean isEnableCache() {
            return enableCache;
        }

        public void setEnableCache(boolean enableCache) {
            this.enableCache = enableCache;
        }

        public int getCacheSize() {
            return cacheSize;
        }

        public void setCacheSize(int cacheSize) {
            this.cacheSize = cacheSize;
        }

        public int getCacheExpireSeconds() {
            return cacheExpireSeconds;
        }

        public void setCacheExpireSeconds(int cacheExpireSeconds) {
            this.cacheExpireSeconds = cacheExpireSeconds;
        }
    }
}