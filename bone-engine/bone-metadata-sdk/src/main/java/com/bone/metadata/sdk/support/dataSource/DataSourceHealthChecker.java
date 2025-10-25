package com.bone.metadata.sdk.support.dataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 数据源健康检查器，负责监控和报告所有数据源的连接状态。
 * 支持定期健康检查、故障检测和Spring Boot Actuator集成。
 */
@Component
public class DataSourceHealthChecker implements HealthIndicator, InitializingBean {
    
    private static final Logger logger = LoggerFactory.getLogger(DataSourceHealthChecker.class);
    
    private final DataSourceManager dataSourceManager;
    
    /**
     * 健康检查间隔（秒）
     */
    private long checkInterval = 30;
    
    /**
     * 健康检查超时时间（毫秒）
     */
    private int checkTimeout = 5000;
    
    /**
     * 数据源健康状态缓存
     */
    private final Map<String, Boolean> healthStatusCache = new ConcurrentHashMap<>();
    
    /**
     * 健康检查调度器
     */
    private ScheduledExecutorService healthCheckScheduler;
    
    /**
     * 构造函数
     * 
     * @param dataSourceManager 数据源管理器
     */
    public DataSourceHealthChecker(DataSourceManager dataSourceManager) {
        this.dataSourceManager = dataSourceManager;
    }
    
    /**
     * 检查所有数据源的健康状态并更新缓存
     */
    public void checkAllDataSources() {
        logger.debug("开始执行数据源健康检查");
        
        for (String dataSourceName : dataSourceManager.getAllDataSourceNames()) {
            try {
                boolean healthy = dataSourceManager.isDataSourceHealthy(dataSourceName);
                boolean previousHealthy = healthStatusCache.getOrDefault(dataSourceName, true);
                
                healthStatusCache.put(dataSourceName, healthy);
                
                // 记录状态变化
                if (previousHealthy != healthy) {
                    if (healthy) {
                        logger.info("数据源[{}]恢复正常", dataSourceName);
                    } else {
                        logger.warn("数据源[{}]连接异常", dataSourceName);
                    }
                }
            } catch (Exception e) {
                healthStatusCache.put(dataSourceName, false);
                logger.error("数据源[{}]健康检查失败: {}", dataSourceName, e.getMessage());
            }
        }
        
        logger.debug("数据源健康检查完成，检查数据源数量: {}", healthStatusCache.size());
    }
    
    /**
     * 检查单个数据源的健康状态
     * 
     * @param dataSourceName 数据源名称
     * @return 健康状态
     */
    public boolean checkDataSourceHealth(String dataSourceName) {
        boolean healthy = dataSourceManager.isDataSourceHealthy(dataSourceName);
        healthStatusCache.put(dataSourceName, healthy);
        return healthy;
    }
    
    /**
     * 获取数据源的缓存健康状态
     * 
     * @param dataSourceName 数据源名称
     * @return 健康状态，如果不存在返回false
     */
    public boolean getDataSourceHealthStatus(String dataSourceName) {
        return healthStatusCache.getOrDefault(dataSourceName, false);
    }
    
    /**
     * 获取所有数据源的健康状态
     * 
     * @return 数据源健康状态映射
     */
    public Map<String, Boolean> getAllDataSourceHealthStatus() {
        return new ConcurrentHashMap<>(healthStatusCache);
    }
    
    /**
     * 检查是否有任何数据源不健康
     * 
     * @return 如果所有数据源都健康返回true，否则返回false
     */
    public boolean isAllHealthy() {
        return healthStatusCache.values().stream().allMatch(Boolean::booleanValue);
    }
    
    /**
     * 获取不健康的数据源列表
     * 
     * @return 不健康的数据源名称列表
     */
    public Map<String, Boolean> getUnhealthyDataSources() {
        Map<String, Boolean> unhealthy = new ConcurrentHashMap<>();
        healthStatusCache.forEach((name, healthy) -> {
            if (!healthy) {
                unhealthy.put(name, false);
            }
        });
        return unhealthy;
    }
    
    /**
     * 实现Spring Boot Actuator的健康检查接口
     * 
     * @return 健康状态对象
     */
    @Override
    public Health health() {
        // 立即执行一次健康检查
        checkAllDataSources();
        
        if (isAllHealthy()) {
            return Health.up()
                    .withDetail("dataSources", healthStatusCache)
                    .withDetail("message", "所有数据源连接正常")
                    .build();
        } else {
            Map<String, Boolean> unhealthy = getUnhealthyDataSources();
            return Health.down()
                    .withDetail("dataSources", healthStatusCache)
                    .withDetail("unhealthy", unhealthy)
                    .withDetail("message", "部分数据源连接异常")
                    .build();
        }
    }
    
    /**
     * 设置健康检查间隔
     * 
     * @param checkInterval 检查间隔（秒）
     */
    public void setCheckInterval(long checkInterval) {
        this.checkInterval = checkInterval;
    }
    
    /**
     * 设置健康检查超时时间
     * 
     * @param checkTimeout 超时时间（毫秒）
     */
    public void setCheckTimeout(int checkTimeout) {
        this.checkTimeout = checkTimeout;
    }
    
    @Override
    public void afterPropertiesSet() {
        logger.info("DataSourceHealthChecker 已初始化，检查间隔: {}秒", checkInterval);
        
        // 初始化健康检查调度器
        initHealthCheckScheduler();
        
        // 执行一次初始健康检查
        checkAllDataSources();
    }
    
    /**
     * 初始化健康检查调度器
     */
    private void initHealthCheckScheduler() {
        healthCheckScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = Executors.defaultThreadFactory().newThread(r);
            thread.setName("data-source-actuator-health-checker");
            thread.setDaemon(true);
            return thread;
        });
        
        healthCheckScheduler.scheduleAtFixedRate(this::checkAllDataSources,
                checkInterval, checkInterval, TimeUnit.SECONDS);
        
        logger.info("数据源健康检查调度器已启动，检查间隔: {}秒", checkInterval);
    }
}