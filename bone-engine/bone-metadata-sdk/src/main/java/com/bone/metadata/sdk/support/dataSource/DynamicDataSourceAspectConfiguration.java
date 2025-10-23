package com.bone.metadata.sdk.support.dataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 动态数据源切面配置类
 * 配置AOP切面，拦截@DS注解的方法调用
 */
@Configuration(proxyBeanMethods = false)
public class DynamicDataSourceAspectConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(DynamicDataSourceAspectConfiguration.class);
    
    /**
     * 默认的切点表达式，拦截所有带有@DS注解的方法和类
     */
    private static final String DEFAULT_POINTCUT_EXPRESSION = "@annotation(com.bone.metadata.sdk.support.dataSource.DS) || @within(com.bone.metadata.sdk.support.dataSource.DS)";
    
    /**
     * 创建数据源切换拦截器
     * @return 数据源切换拦截器
     */
    @Bean
    public DataSourceAnnotationInterceptor dataSourceAnnotationInterceptor() {
        log.info("Initializing DataSourceAnnotationInterceptor");
        return new DataSourceAnnotationInterceptor();
    }
    
    /**
     * 创建数据源切换的AOP切面
     * @return AOP切面
     */
    @Bean
    public Advisor dataSourceAdvisor(DataSourceAnnotationInterceptor interceptor) {
        log.info("Configuring datasource switching AOP advisor");
        
        // 创建切点
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression(DEFAULT_POINTCUT_EXPRESSION);
        
        // 创建切面
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut, interceptor);
        
        // 设置切面优先级，确保在事务切面之前执行
        advisor.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        
        log.info("Datasource switching AOP advisor configured with order: {}", advisor.getOrder());
        return advisor;
    }
}