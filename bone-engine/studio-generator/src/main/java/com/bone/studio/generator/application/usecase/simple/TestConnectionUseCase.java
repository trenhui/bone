package com.bone.studio.generator.application.usecase.simple;

import com.bone.core.usecase.Capability;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.domain.service.CodeGeneratorService;
import org.springframework.stereotype.Component;

@Component
@Capability(name = "testConnection", description = "测试数据源连接", inputSchema = "{}", outputSchema = "{}")
public class TestConnectionUseCase {

    private final CodeGeneratorService codeGeneratorService;

    public TestConnectionUseCase(CodeGeneratorService codeGeneratorService) {
        this.codeGeneratorService = codeGeneratorService;
    }

    public boolean execute(DataSource dataSource) {
        return codeGeneratorService.testConnection(dataSource);
    }
}