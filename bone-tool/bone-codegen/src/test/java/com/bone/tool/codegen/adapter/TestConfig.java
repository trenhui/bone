package com.bone.tool.codegen.adapter;

import com.bone.tool.codegen.application.converter.CodegenConverter;
import com.bone.tool.codegen.domain.repository.CodegenTableRepository;
import com.bone.tool.codegen.domain.repository.CodegenColumnRepository;
import com.bone.tool.codegen.domain.service.CodegenService;
import com.bone.tool.codegen.domain.service.CodegenServiceImpl;
import com.bone.tool.codegen.domain.service.DataSourceConfigService;
import com.bone.tool.codegen.domain.service.DatabaseTableService;
import com.bone.tool.codegen.domain.service.generator.DefaultCodeGenerator;
import com.bone.tool.codegen.domain.service.renderer.VelocityTemplateRenderer;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 测试配置类，为测试环境提供所有必要的mock bean
 */
@TestConfiguration
public class TestConfig {

    @Bean
    public CodegenService codegenService() {
        // 返回实现类的实例，因为接口是抽象的
        return new CodegenServiceImpl(
            defaultCodeGenerator(),
            databaseTableService(),
            codegenTableRepository(),
            templateRenderer()
        );
    }

    @Bean
    public VelocityTemplateRenderer templateRenderer() {
        return Mockito.mock(VelocityTemplateRenderer.class);
    }

    @Bean
    public CodegenTableRepository codegenTableRepository() {
        return Mockito.mock(CodegenTableRepository.class);
    }

    @Bean
    public CodegenColumnRepository codegenColumnRepository() {
        return Mockito.mock(CodegenColumnRepository.class);
    }

    @Bean
    public CodegenConverter codegenConverter() {
        return Mockito.mock(CodegenConverter.class);
    }

    @Bean
    public DefaultCodeGenerator defaultCodeGenerator() {
        return Mockito.mock(DefaultCodeGenerator.class);
    }

    @Bean
    public DatabaseTableService databaseTableService() {
        return Mockito.mock(DatabaseTableService.class);
    }

    @Bean
    public DataSourceConfigService dataSourceConfigService() {
        return Mockito.mock(DataSourceConfigService.class);
    }


}