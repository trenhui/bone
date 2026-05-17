package com.bone.engine.extension.support.config;

import com.bone.engine.extension.api.spi.ExtensionPointExecutor;
import com.bone.engine.extension.core.executor.DefaultExtensionPointExecutor;
import com.bone.engine.extension.core.executor.ExtensionExecutionGuard;
import com.bone.engine.extension.support.studio.StudioExecutionLogReporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

/**
 * 扩展点执行器配置
 * <p>
 * 配置扩展点执行器的Bean定义，包括沙箱机制的配置
 * </p>
 * 
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(ExtensionExecutionProperties.class)
public class ExtensionExecutorConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "bone.extension.execution", name = "guard-enabled", havingValue = "true", matchIfMissing = true)
    public ExtensionExecutionGuard extensionExecutionGuard(ExtensionExecutionProperties properties) {
        return new ExtensionExecutionGuard(properties.getTimeoutMs(), properties.getBulkheadMaxConcurrent());
    }

    @Bean
    public ExtensionPointExecutor extensionPointExecutor(
            ExtensionExecutionProperties properties,
            @Autowired(required = false) @Nullable StudioExecutionLogReporter studioReporter,
            @Autowired(required = false) @Nullable ExtensionExecutionGuard executionGuard) {
        DefaultExtensionPointExecutor executor = new DefaultExtensionPointExecutor();
        executor.setStudioReporter(studioReporter);
        if (properties.isGuardEnabled() && executionGuard != null) {
            executor.setExecutionGuard(executionGuard);
        }
        return executor;
    }
}
