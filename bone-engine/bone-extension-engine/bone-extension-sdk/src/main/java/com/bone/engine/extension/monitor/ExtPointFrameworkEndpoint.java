package com.bone.engine.extension.monitor;

import com.bone.engine.extension.router.DefaultExtPointRouter;
import com.bone.engine.extension.router.RouteStatsCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 扩展点框架监控端点
 * <p>
 * 提供扩展点框架的性能统计、缓存使用情况等监控信息
 * </p>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
@Endpoint(id = "extPointFramework")
@Component
public class ExtPointFrameworkEndpoint {

    private DefaultExtPointRouter extPointRouter;
    private RouteStatsCollector statsCollector;

    @Autowired
    public ExtPointFrameworkEndpoint(DefaultExtPointRouter extPointRouter, RouteStatsCollector statsCollector) {
        this.extPointRouter = extPointRouter;
        this.statsCollector = statsCollector;
    }
    
    // Setter方法
    public void setRouter(DefaultExtPointRouter router) {
        this.extPointRouter = router;
    }
    
    public void setStatsCollector(RouteStatsCollector collector) {
        this.statsCollector = collector;
    }
    
    public void setCacheManager(Object cacheManager) {
        // 预留方法，目前缓存管理器通过router访问
    }

    /**
     * 获取扩展点框架的监控信息
     * @return 框架监控信息
     */
    @ReadOperation
    public Map<String, Object> invoke() {
        Map<String, Object> result = new HashMap<>();
        
        // 1. 路由统计信息
        result.put("routeStats", extPointRouter.getRouteStats());
        
        // 2. 缓存统计信息
        result.put("cacheStats", extPointRouter.getCacheStats());
        
        // 3. 缓存命中率
        if (statsCollector != null) {
            result.put("overallCacheHitRate", statsCollector.getOverallCacheHitRate());
            result.put("detailedCacheStats", statsCollector.getAllCacheStats());
        }
        
        // 4. 实现类统计信息
        result.put("implementationStats", extPointRouter.getImplementationStats());
        
        // 5. 事件统计信息
        if (statsCollector != null) {
            Map<String, Integer> eventStats = new HashMap<>();
            eventStats.put("totalPublishedEvents", statsCollector.getTotalPublishedEvents());
            eventStats.put("asyncEvents", statsCollector.getAsyncEvents());
            eventStats.put("syncEvents", statsCollector.getSyncEvents());
            eventStats.put("failedEvents", statsCollector.getFailedEvents());
            result.put("eventStats", eventStats);
        }
        
        // 6. 系统配置信息
        Map<String, Object> configInfo = new HashMap<>();
        configInfo.put("enableCache", extPointRouter.isCacheEnabled());
        result.put("configInfo", configInfo);
        
        return result;
    }
    
    /**
     * 获取指定扩展点的详细监控信息
     * @param extPointName 扩展点名称
     * @return 扩展点详细监控信息
     */
    @ReadOperation
    public Map<String, Object> getExtPointDetails(String extPointName) {
        Map<String, Object> result = new HashMap<>();
        
        if (!StringUtils.hasText(extPointName)) {
            result.put("error", "ExtPoint name is required");
            return result;
        }
        
        // 这里可以添加根据扩展点名称获取详细信息的逻辑
        // 例如：获取该扩展点的路由统计、实现类信息等
        
        return result;
    }
    
    /**
     * 重置统计信息
     * @return 操作结果
     */
    @ReadOperation
    public Map<String, Object> resetStats() {
        if (extPointRouter != null) {
            extPointRouter.resetRouteStats();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "Stats have been reset");
        
        return result;
    }
}