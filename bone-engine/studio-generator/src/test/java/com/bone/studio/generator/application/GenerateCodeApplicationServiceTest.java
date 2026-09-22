package com.bone.studio.generator.application;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import com.bone.studio.generator.application.command.cmd.GenerateCodeCommand;
import com.bone.studio.generator.domain.model.code.CodeGenerationResponse;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GenerateCodeApplicationServiceTest {

  @Mock private GenerateCodeApplicationService generateCodeHandler;

  @Test
  void shouldGenerateCodeSuccessfully() {
    GenerateCodeCommand command =
        GenerateCodeCommand.builder()
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

    CodeGenerationResponse expectedResponse =
        CodeGenerationResponse.builder().generationId("test-id").status("SUCCESS").build();
    when(generateCodeHandler.handle(command)).thenReturn(expectedResponse);

    CodeGenerationResponse response = generateCodeHandler.handle(command);

    assertNotNull(response);
    assertNotNull(response.getGenerationId());
  }
}
