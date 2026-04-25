package com.bone.studio.generator.application.usecase;

import com.bone.core.usecase.Capability;
import com.bone.studio.generator.application.command.handler.PreviewTemplateHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Capability(
    name = "PreviewTemplate",
    description = "预览模板",
    inputSchema = "{\"templateId\": \"string\", \"parameters\": {}}]",
    outputSchema = "{\"previewContent\": \"string\"}",
    idempotent = true,
    cost = 2,
    retryable = true,
    timeout = 30
)
@Service
@RequiredArgsConstructor
public class PreviewTemplateUseCase implements UseCaseExecutor<PreviewTemplateUseCase.PreviewTemplateCommand, String> {

    private final PreviewTemplateHandler previewTemplateHandler;

    @Override
    public String execute(PreviewTemplateCommand command) {
        return previewTemplateHandler.handle(command.templateId(), command.parameters());
    }

    public record PreviewTemplateCommand(String templateId, Map<String, Object> parameters) {
    }
}