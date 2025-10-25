package com.bone.metadata.sdk.query.builder;

import com.bone.metadata.sdk.domain.query.CompiledQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抽象SQL查询构建器基类，为各种SQL构建器提供共同的基础功能和缓存机制
 * 减少重复代码并提高性能
 *
 * @param <T> 构建器上下文类型
 */
public abstract class AbstractSqlQueryBuilder<T> implements SqlQueryBuilder<T> {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    // 缓存已编译的查询，提高性能
    private final Map<String, CompiledQuery> queryCache = new ConcurrentHashMap<>();
    
    /**
     * 构建SQL查询的核心方法
     * 子类必须实现此方法来构建特定类型的SQL
     *
     * @param context 构建上下文
     * @return 编译后的查询
     */
    protected abstract CompiledQuery doBuild(T context);
    
    /**
     * 为上下文生成缓存键
     * 子类可以覆盖此方法以提供更精确的缓存键生成逻辑
     *
     * @param context 构建上下文
     * @return 缓存键
     */
    protected String generateCacheKey(T context) {
        return context != null ? context.toString() : "null_context";
    }
    
    /**
     * 执行实际的构建过程，包括缓存处理
     *
     * @param context 构建上下文
     * @return 编译后的查询
     */
    @Override
    public final CompiledQuery build(T context) {
        try {
            // 验证上下文
            validateContext(context);
            
            // 尝试从缓存获取
            String cacheKey = generateCacheKey(context);
            CompiledQuery cachedQuery = queryCache.get(cacheKey);
            if (cachedQuery != null && !isCacheDisabled()) {
                logger.debug("Using cached query for: {}", cacheKey);
                return cachedQuery;
            }
            
            // 构建查询
            logger.debug("Building new query for: {}", context);
            CompiledQuery query = doBuild(context);
            
            // 缓存结果
            if (query != null && !isCacheDisabled()) {
                queryCache.put(cacheKey, query);
            }
            
            return query;
        } catch (Exception e) {
            logger.error("Error building SQL query: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to build SQL query", e);
        }
    }
    
    /**
     * 验证构建上下文
     * 子类可以覆盖此方法以提供特定的验证逻辑
     *
     * @param context 构建上下文
     */
    protected void validateContext(T context) {
        if (context == null) {
            throw new IllegalArgumentException("Query context cannot be null");
        }
    }
    
    /**
     * 检查是否禁用缓存
     * 子类可以覆盖此方法以动态控制缓存行为
     *
     * @return 是否禁用缓存
     */
    protected boolean isCacheDisabled() {
        return false;
    }
    
    /**
     * 清除查询缓存
     */
    public void clearCache() {
        queryCache.clear();
        logger.info("Query cache cleared");
    }
    
    /**
     * 获取缓存大小
     *
     * @return 缓存中的查询数量
     */
    protected int getCacheSize() {
        return queryCache.size();
    }
}