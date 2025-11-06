package com.bone.procurement.scheduler;

import com.bone.procurement.service.cache.OrderCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单缓存清理调度器
 * 定期清理过期的订单缓存，避免缓存膨胀
 * 
 * @author bone
 */
@Component
@Slf4j
public class OrderCacheCleanupScheduler {

    @Autowired
    private OrderCacheService orderCacheService;

    /**
     * 每天凌晨2点执行缓存清理
     * 使用cron表达式定义执行时间
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredCache() {
        log.info("开始执行订单缓存清理任务");
        try {
            // 清理过期的订单详情缓存
            long cleanedCount = orderCacheService.cleanupExpiredCache();
            log.info("订单缓存清理任务完成，清理了 {} 个过期缓存项", cleanedCount);
        } catch (Exception e) {
            log.error("执行订单缓存清理任务失败", e);
        }
    }

    /**
     * 每小时执行一次列表缓存预热
     * 预热常用的订单列表查询缓存
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void warmupCommonListCache() {
        log.info("开始执行订单列表缓存预热任务");
        try {
            orderCacheService.warmupCommonListCache();
            log.info("订单列表缓存预热任务完成");
        } catch (Exception e) {
            log.error("执行订单列表缓存预热任务失败", e);
        }
    }
}