package com.bone.engine.extension.core.cache;

/**
 * 分布式缓存接口
 * <p>
 * 用于扩展引擎的路由结果缓存，支持跨节点的缓存同步
 * </p>
 *
 * @since 1.0.0
 */
public interface DistributedCache {

    /**
     * 获取缓存值
     *
     * @param key 缓存键
     * @return 缓存值，如果不存在返回null
     */
    Object get(String key);

    /**
     * 设置缓存值
     *
     * @param key   缓存键
     * @param value 缓存值
     * @param expireSeconds 过期时间（秒）
     */
    void set(String key, Object value, long expireSeconds);

    /**
     * 删除缓存
     *
     * @param key 缓存键
     */
    void delete(String key);

    /**
     * 清空缓存
     */
    void clear();

    /**
     * 检查缓存是否存在
     *
     * @param key 缓存键
     * @return 是否存在
     */
    boolean exists(String key);
}
