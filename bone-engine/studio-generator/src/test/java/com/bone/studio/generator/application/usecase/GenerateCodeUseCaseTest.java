package com.bone.studio.generator.application.usecase;

import com.bone.studio.generator.application.command.cmd.GenerateCodeCommand;
import com.bone.studio.generator.application.command.handler.GenerateCodeHandler;
import com.bone.studio.generator.application.usecase.standard.GenerateCodeUseCase;
import com.bone.studio.generator.domain.code.CodeGenerationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateCodeUseCaseTest {

    @Mock
    private GenerateCodeHandler generateCodeHandler;

    @InjectMocks
    private GenerateCodeUseCase generateCodeUseCase;

    @Test
    void shouldGenerateCodeSuccessfully() {
        // 准备测试数据
        GenerateCodeCommand command = GenerateCodeCommand.builder()
                .templateId("1")
                .name("TestEntity")
                .description("Test entity")
                .language("java")
                .framework("spring")
                .parameters(Map.of("package", "com.example.domain"))
                .tags(Collections.singletonList("test"))
                .outputFormat("zip")
                .outputPath("/tmp")
                .includeTests(true)
                .includeDocumentation(true)
                .build();

        // 模拟Handler返回
        CodeGenerationResponse expectedResponse = CodeGenerationResponse.builder()
                .generationId("test-id")
                .status("SUCCESS")
                .build();
        when(generateCodeHandler.handle(command)).thenReturn(expectedResponse);

        // 执行测试
        CodeGenerationResponse response = generateCodeUseCase.execute(command);

        // 验证结果
        assertNotNull(response);
        assertNotNull(response.getGenerationId());
    }
}