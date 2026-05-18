package com.bone.studio.generator.infrastructure.config;

import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import com.bone.studio.generator.config.GeneratorProperties;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.TemplateExceptionHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@EnableConfigurationProperties({SqlConfigProperties.class, GeneratorProperties.class})
public class GeneratorConfiguration {

    @Bean
    public freemarker.template.Configuration freemarkerConfig() throws IOException {
        freemarker.template.Configuration config = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_32);
        config.setDefaultEncoding("UTF-8");
        config.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        config.setLogTemplateExceptions(false);
        config.setWrapUncheckedExceptions(true);
        config.setFallbackOnNullLoopVariable(false);
        config.setObjectWrapper(new DefaultObjectWrapperBuilder(freemarker.template.Configuration.VERSION_2_3_32)
                .build());
        return config;
    }

}