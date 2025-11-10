package com.bone.procurement.service;

import com.bone.procurement.entity.PurchaseOrder;

/**
 * 订单缓存服务接口
 * 提供订单相关的缓存操作
 */
public interface OrderCacheService {
    
    /**
     * 将订单存入缓存
     * @param order 采购订单对象
     */
    void cacheOrder(PurchaseOrder order);
    
    /**
     * 从缓存中获取订单
     * @param orderId 订单ID
     * @return 采购订单对象，如果不存在返回null
     */
    PurchaseOrder getOrderFromCache(Long orderId);
    
    /**
     * 从缓存中删除订单
     * @param orderId 订单ID
     */
    void removeOrderFromCache(Long orderId);
    
    /**
     * 更新缓存中的订单
     * @param order 采购订单对象
     */
    void updateOrderInCache(PurchaseOrder order);
    
    /**
     * 检查订单是否在缓存中
     * @param orderId 订单ID
     * @return 如果订单在缓存中存在则返回true，否则返回false
     */
    boolean isOrderInCache(Long orderId);
    
    /**
     * 清除订单列表缓存
     */
    void evictOrderListCache();
}
