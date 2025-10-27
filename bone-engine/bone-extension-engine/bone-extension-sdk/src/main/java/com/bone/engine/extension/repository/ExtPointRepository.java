package com.bone.engine.extension.repository;

import org.springframework.lang.Nullable;
import java.util.Set;

/**
 * 扩展点仓库接口，提供扩展实现的存储和检索机制
 * <p>
 * 该接口定义了扩展点框架中存储扩展实现的核心操作，类似于Map接口但专注于扩展点场景。
 * 框架提供多种实现，如内存存储、Redis存储和Nacos存储，支持不同的部署和性能需求。
 * <p>
 * 典型的键格式为："[扩展点接口全限定名].[业务标识]"，其中业务标识由租户、业务、用例和场景组成。
 * <p>
 * 实现类需保证线程安全，以支持多线程环境下的并发访问。
 *
 * @author renhui.trh 2023-10-30
 * @since 1.0.0
 * @see MemExtPointRepository 内存实现的扩展点仓库
 * @see RedisExtPointRepository Redis实现的扩展点仓库
 * @see NacosExtPointRepository Nacos实现的扩展点仓库
 */
public interface ExtPointRepository {

    /**
     * 根据键获取扩展实现
     * 
     * @param key 扩展实现的键，通常格式为"[扩展点接口名].[业务标识]"
     * @return 对应的扩展实现，如果不存在则返回null
     */
    @Nullable
    Object get(Object key);

    /**
     * 存储扩展实现
     * 
     * @param key 扩展实现的键，通常格式为"[扩展点接口名].[业务标识]"
     * @param value 扩展实现实例
     * @return 先前关联到此键的值，如果不存在则返回null
     */
    @Nullable
    Object put(Object key, Object value);

    /**
     * 移除指定键的扩展实现
     * 
     * @param key 要移除的扩展实现的键
     * @return 被移除的值，如果不存在则返回null
     */
    @Nullable
    Object remove(Object key);

    /**
     * 清除所有扩展实现
     * 通常在系统重启或配置刷新时调用
     */
    void clear();
    
    /**
     * 检查扩展点实例是否存在
     * 
     * @param key 扩展点标识
     * @return 如果存在则返回true，否则返回false
     */
    boolean containsKey(Object key);
    
    /**
     * 获取所有扩展点实例的键集合
     * 
     * @return 键集合
     */
    Set<Object> keySet();
    
    /**
     * 获取存储的扩展点实例数量
     * 
     * @return 实例数量
     */
    int size();
}
