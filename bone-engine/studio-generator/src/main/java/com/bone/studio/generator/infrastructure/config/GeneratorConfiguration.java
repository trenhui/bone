package com.bone.studio.generator.infrastructure.config;

import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import com.bone.studio.generator.config.GeneratorProperties;
import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.StringTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.TemplateExceptionHandler;
import java.io.IOException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SqlConfigProperties.class, GeneratorProperties.class})
public class GeneratorConfiguration {

  /** 库里模板内容的装载器：{@code content} 非空时由 {@code TemplateRenderer} 按 code 注册。 */
  @Bean
  public StringTemplateLoader dbTemplateLoader() {
    return new StringTemplateLoader();
  }

  @Bean
  public freemarker.template.Configuration freemarkerConfig(StringTemplateLoader dbTemplateLoader)
      throws IOException {
    freemarker.template.Configuration config =
        new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_32);
    config.setDefaultEncoding("UTF-8");
    config.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    config.setLogTemplateExceptions(false);
    config.setWrapUncheckedExceptions(true);
    config.setFallbackOnNullLoopVariable(false);
    config.setObjectWrapper(
        new DefaultObjectWrapperBuilder(freemarker.template.Configuration.VERSION_2_3_32).build());
    // 顺序即优先级：先库里的自定义模板，再 classpath 内置模板
    config.setTemplateLoader(
        new MultiTemplateLoader(
            new TemplateLoader[] {
              dbTemplateLoader,
              new ClassTemplateLoader(GeneratorConfiguration.class.getClassLoader(), "templates")
            }));
    return config;
  }
}
