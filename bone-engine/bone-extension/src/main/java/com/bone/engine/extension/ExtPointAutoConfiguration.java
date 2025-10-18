package com.bone.engine.extension;

import com.bone.engine.extension.expression.ExpressionEvaluator;
import com.bone.engine.extension.repository.ExtPointRepository;
import com.bone.engine.extension.repository.MemExtPointRepository;
import com.bone.engine.extension.register.ExtensionRegister;
import com.bone.engine.extension.route.DefaultExtPointRouter;
import com.bone.engine.extension.route.ExtPointRouter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 扩展点框架自动配置类，配置所有扩展点框架的核心组件
 */
@Configuration
public class ExtPointAutoConfiguration {
    
    /**
     * 扩展点仓库 - 存储和管理扩展点实现
     */
    @Bean
    public ExtPointRepository extPointRepository() {
        return new MemExtPointRepository();
    }
    
    // 不需要ExpressionEvaluator的Bean定义，因为它是一个静态工具类，不需要实例化
    
    /**
     * 默认扩展点路由器 - 根据业务上下文路由到合适的扩展实现
     */
    @Bean
    public ExtPointRouter extPointRouter(ExtPointRepository extPointRepository) {
        return new DefaultExtPointRouter(extPointRepository);
    }
    
    /**
     * 扩展提供者注册器，负责扫描和注册所有扩展实现
     */
    @Bean(initMethod = "init")
    public ExtensionRegister extProviderRegister(ExtPointRepository extPointRepository) {
        return new ExtensionRegister(extPointRepository);
    }
}
