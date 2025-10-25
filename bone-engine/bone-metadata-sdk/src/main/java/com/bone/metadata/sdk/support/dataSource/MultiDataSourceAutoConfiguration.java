package com.bone.metadata.sdk.support.dataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;

/**
 * 数据源自动配置类，提供多数据源管理和切换的自动化配置支持。
 * <p>
 * 该配置类负责初始化数据源管理器、健康检查机制以及提供数据源路由功能。
 * 默认启用，但可通过配置属性 {@code bone.metadata.datasource.enabled} 进行控制。
 */
@Configuration
@ConditionalOnProperty(prefix = "bone.metadata.datasource", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DataSourceProperties.class)
public class MultiDataSourceAutoConfiguration implements InitializingBean {
    
    private static final Logger logger = LoggerFactory.getLogger(MultiDataSourceAutoConfiguration.class);
    
    /**
     * 创建数据源管理器，负责多数据源的注册、获取和切换逻辑。
     * 使用@ConditionalOnMissingBean确保只创建一个实例，避免与DynamicDataSourceAutoConfiguration冲突
     * 
     * @return 数据源管理器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public DataSourceManager dataSourceManager() {
        logger.debug("创建默认的数据源管理器");
        return new DefaultDataSourceManager();
    }
    
    /**
     * 创建数据源健康检查指示器，用于监控所有数据源的连接状态。
     * 
     * @param dataSourceManager 数据源管理器
     * @return 数据源健康检查指示器
     */
    @Bean
    @ConditionalOnMissingBean
    @DependsOn("dataSourceManager")
    public DataSourceHealthChecker dataSourceHealthChecker(DataSourceManager dataSourceManager) {
        logger.debug("创建数据源健康检查器");
        return new DataSourceHealthChecker(dataSourceManager);
    }
    
    /**
     * 创建数据源切换拦截器，用于实现基于@DataSourceSwitch注解的数据源动态切换。
     * 
     * @return 数据源切换拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    @DependsOn("dataSourceManager")
    public DataSourceAnnotationInterceptor dataSourceAnnotationInterceptor() {
        logger.debug("创建数据源切换拦截器");
        return new DataSourceAnnotationInterceptor();
    }
    
    @Override
    public void afterPropertiesSet() {
        logger.info("多数据源自动配置已初始化完成");
    }
}
