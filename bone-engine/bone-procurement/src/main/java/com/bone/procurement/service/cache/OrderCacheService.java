package com.bone.procurement.service.cache;

import com.bone.procurement.entity.PurchaseOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 订单缓存服务
 * 用于缓存采购订单数据，提高系统性能
 */
@Component
public class OrderCacheService {
    
    // 缓存名称
    // Spring Cache注解使用的缓存名称
    private static final String ORDER_CACHE = "purchaseOrders";
    private static final String ORDER_LIST_CACHE = "purchaseOrderLists";
    
    // Redis键前缀，用于手动缓存操作
    private static final String ORDER_CACHE_PREFIX = "purchaseOrders:";
    private static final String ORDER_LIST_CACHE_PREFIX = "purchaseOrderLists:";
    
    /**
     * 订单列表缓存过期时间（10分钟）
     */
    private static final long ORDER_LIST_CACHE_TTL = 10 * 60 * 1000;
    
    /**
     * 热门列表缓存过期时间（30分钟）
     */
    private static final long HOT_LIST_CACHE_TTL = 30 * 60 * 1000;
    
    @Autowired
    private CacheManager cacheManager;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private static final Logger log = LoggerFactory.getLogger(OrderCacheService.class);
    
    /**
     * 缓存订单详情
     * @param order 采购订单
     */
    public void cacheOrder(PurchaseOrder order) {
        if (order == null || order.getId() == null) {
            return;
        }
        
        Cache cache = cacheManager.getCache(ORDER_CACHE);
        if (cache != null) {
            cache.put(order.getId(), order);
        }
    }
    
    /**
     * 获取缓存的订单详情
     * @param orderId 订单ID
     * @return 缓存的订单，如果不存在返回null
     */
    public PurchaseOrder getCachedOrder(Long orderId) {
        if (orderId == null) {
            return null;
        }
        
        Cache cache = cacheManager.getCache(ORDER_CACHE);
        if (cache != null) {
            Cache.ValueWrapper valueWrapper = cache.get(orderId);
            if (valueWrapper != null) {
                return (PurchaseOrder) valueWrapper.get();
            }
        }
        return null;
    }
    
    /**
     * 从缓存中移除订单
     * @param orderId 订单ID
     */
    public void evictOrder(Long orderId) {
        if (orderId == null) {
            return;
        }
        
        Cache cache = cacheManager.getCache(ORDER_CACHE);
        if (cache != null) {
            cache.evict(orderId);
        }
        
        // 同时清除列表缓存
        evictOrderListCache();
    }
    
    /**
     * 缓存订单列表
     * @param key 缓存键
     * @param orders 订单列表
     */
    public void cacheOrderList(String key, Object orders) {
        Cache cache = cacheManager.getCache(ORDER_LIST_CACHE);
        if (cache != null) {
            cache.put(key, orders);
        }
    }
    
    /**
     * 获取缓存的订单列表
     * @param key 缓存键
     * @return 缓存的订单列表，如果不存在返回null
     */
    @SuppressWarnings("unchecked")
    public <T> T getCachedOrderList(String key) {
        Cache cache = cacheManager.getCache(ORDER_LIST_CACHE);
        if (cache != null) {
            Cache.ValueWrapper valueWrapper = cache.get(key);
            if (valueWrapper != null) {
                return (T) valueWrapper.get();
            }
        }
        return null;
    }
    
    /**
     * 清除订单列表缓存
     */
    public void evictOrderListCache() {
        Cache cache = cacheManager.getCache(ORDER_LIST_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }
    
    /**
     * 清理过期的订单缓存
     * @return 清理的缓存项数量
     */
    public long cleanupExpiredCache() {
        log.info("开始清理过期的订单缓存");
        
        // 获取所有可能的订单缓存键前缀
        String[] cacheKeyPatterns = {
            ORDER_CACHE_PREFIX + "*",
            ORDER_LIST_CACHE_PREFIX + "*"
        };
        
        long totalCleaned = 0;
        
        // 清理匹配的缓存键
        for (String pattern : cacheKeyPatterns) {
            try {
                // 获取所有匹配的缓存键
                Set<String> cacheKeys = redisTemplate.keys(pattern);
                if (cacheKeys != null && !cacheKeys.isEmpty()) {
                    // 批量删除缓存
                    redisTemplate.delete(cacheKeys);
                    totalCleaned += cacheKeys.size();
                    log.info("清理缓存模式: {}, 数量: {}", pattern, cacheKeys.size());
                }
            } catch (Exception e) {
                log.error("清理缓存模式 {} 失败", pattern, e);
            }
        }
        
        return totalCleaned;
    }
    
    /**
     * 构建订单列表缓存键
     * @param page 页码
     * @param size 每页大小
     * @param status 状态（可选）
     * @return 缓存键
     */
    public String buildOrderListCacheKey(int page, int size, String status) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append("page_").append(page)
                  .append("_size_").append(size);
        
        if (status != null && !status.isEmpty()) {
            keyBuilder.append("_status_").append(status);
        }
        
        return keyBuilder.toString();
    }
    
    /**
     * 预热常用的订单列表缓存
     */
    public void warmupCommonListCache() {
        log.info("开始预热订单列表缓存");
        
        // 预热前3页的热门订单列表
        for (int page = 0; page < 3; page++) {
            String cacheKey = buildOrderListCacheKey(page, 20, null);
            try {
                // 如果缓存不存在，则预热
                if (!redisTemplate.hasKey(cacheKey)) {
                    log.info("预热订单列表缓存，页码: {}", page);
                    // 这里可以通过调用服务层方法来获取数据并缓存
                    // 为了避免循环依赖，这里只创建空缓存
                    redisTemplate.opsForValue().set(
                        cacheKey, 
                        new ArrayList<>(), 
                        HOT_LIST_CACHE_TTL, 
                        TimeUnit.MILLISECONDS
                    );
                }
            } catch (Exception e) {
                log.error("预热订单列表缓存页码 {} 失败", page, e);
            }
        }
        
        // 预热各状态的订单列表缓存
        String[] commonStatuses = {"DRAFT", "SUBMITTED", "APPROVED", "EXECUTED", "CANCELLED"};
        for (String status : commonStatuses) {
            String cacheKey = buildOrderListCacheKey(0, Integer.MAX_VALUE, status);
            try {
                if (!redisTemplate.hasKey(cacheKey)) {
                    log.info("预热状态订单缓存，状态: {}", status);
                    // 为各状态创建空缓存
                    redisTemplate.opsForValue().set(
                        cacheKey, 
                        new ArrayList<>(), 
                        HOT_LIST_CACHE_TTL, 
                        TimeUnit.MILLISECONDS
                    );
                }
            } catch (Exception e) {
                log.error("预热状态订单缓存 {} 失败", status, e);
            }
        }
    }
}