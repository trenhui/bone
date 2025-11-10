package com.bone.procurement.service.impl;

import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.service.OrderCacheService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 订单缓存服务实现类
 * 提供订单缓存的基本实现
 */
@Service
public class OrderCacheServiceImpl implements OrderCacheService {
    
    private static final String ORDER_CACHE_NAME = "purchaseOrders";
    
    @Override
    @CachePut(value = ORDER_CACHE_NAME, key = "#order.id")
    public void cacheOrder(PurchaseOrder order) {
        // Spring Cache会自动处理缓存存储
    }
    
    @Override
    @Cacheable(value = ORDER_CACHE_NAME, key = "#orderId", unless = "#result == null")
    public PurchaseOrder getOrderFromCache(Long orderId) {
        // Spring Cache会自动处理缓存查询
        // 当缓存中没有数据时返回null
        return null;
    }
    
    @Override
    @CacheEvict(value = ORDER_CACHE_NAME, key = "#orderId")
    public void removeOrderFromCache(Long orderId) {
        // Spring Cache会自动处理缓存删除
    }
    
    @Override
    @CachePut(value = ORDER_CACHE_NAME, key = "#order.id")
    public void updateOrderInCache(PurchaseOrder order) {
        // Spring Cache会自动处理缓存更新
    }
    
    @Override
    public boolean isOrderInCache(Long orderId) {
        // 简单实现：尝试从缓存获取，不为null则表示在缓存中
        return getOrderFromCache(orderId) != null;
    }
    
    @Override
    @CacheEvict(value = "purchaseOrderLists", allEntries = true)
    public void evictOrderListCache() {
        // Spring Cache会自动处理缓存清除
    }
}
