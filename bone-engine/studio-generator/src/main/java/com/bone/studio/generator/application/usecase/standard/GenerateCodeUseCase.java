package com.bone.studio.generator.application.usecase.standard;

import com.bone.core.usecase.Capability;
import com.bone.studio.generator.application.command.cmd.GenerateCodeCommand;
import com.bone.studio.generator.application.command.handler.GenerateCodeHandler;
import com.bone.studio.generator.domain.code.CodeGenerationResponse;
import org.springframework.stereotype.Component;

@Component
@Capability(name = "generateCodeUseCase", description = "代码生成用例", inputSchema = "{}", outputSchema = "{}")
public class GenerateCodeUseCase {

    private final GenerateCodeHandler generateCodeHandler;

    public GenerateCodeUseCase(GenerateCodeHandler generateCodeHandler) {
        this.generateCodeHandler = generateCodeHandler;
    }

    public CodeGenerationResponse execute(GenerateCodeCommand command) {
        return generateCodeHandler.handle(command);
    }
}