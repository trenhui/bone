package com.bone.engine.extension.support.config;

import com.bone.engine.extension.api.spi.ExtensionPointExecutor;
import com.bone.engine.extension.core.executor.DefaultExtensionPointExecutor;
import com.bone.engine.extension.support.studio.StudioExecutionLogReporter;
import org.springframework.beans.factory.annotation.Autowired;
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
public class ExtensionExecutorConfiguration {

    /**
     * 创建默认的扩展点执行器
     * 
     * @return 扩展点执行器实例
     */
    @Bean
    public ExtensionPointExecutor extensionPointExecutor(
            @Autowired(required = false) @Nullable StudioExecutionLogReporter studioReporter) {
        DefaultExtensionPointExecutor executor = new DefaultExtensionPointExecutor();
        executor.setStudioReporter(studioReporter);
        return executor;
    }
}
