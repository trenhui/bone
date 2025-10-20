package com.bone.engine.extension.metadata;

import com.bone.engine.extension.repository.ExtPointRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 扩展点元数据自动配置类
 * <p>
 * 配置扩展点元数据相关的Spring Bean
 * </p>
 * 
 * @since 1.0.0
 */
@Configuration
public class ExtPointMetadataAutoConfiguration {
    
    /**
     * 扩展点元数据服务Bean
     * 
     * @param extPointRepository 扩展点仓库
     * @return 扩展点元数据服务实现
     */
    @Bean
    @ConditionalOnMissingBean(ExtPointMetadataService.class)
    public ExtPointMetadataService extPointMetadataService(ExtPointRepository extPointRepository) {
        return new DefaultExtPointMetadataService(extPointRepository);
    }
}