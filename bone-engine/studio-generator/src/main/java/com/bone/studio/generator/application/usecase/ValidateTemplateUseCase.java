package com.bone.studio.generator.application.usecase;

import com.bone.core.usecase.Capability;
import com.bone.studio.generator.application.command.handler.ValidateTemplateHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Capability(
    name = "ValidateTemplate",
    description = "验证模板",
    inputSchema = "{\"templateId\": \"string\"}",
    outputSchema = "{\"valid\": \"boolean\", \"message\": \"string\"}",
    idempotent = true,
    cost = 1,
    retryable = true,
    timeout = 10
)
@Service
@RequiredArgsConstructor
public class ValidateTemplateUseCase implements UseCaseExecutor<String, Boolean> {

    private final ValidateTemplateHandler validateTemplateHandler;

    @Override
    public Boolean execute(String templateId) {
        return validateTemplateHandler.handle(templateId);
    }
}