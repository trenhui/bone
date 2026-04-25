package com.bone.studio.generator.application.usecase;

import com.bone.core.usecase.UseCase;
import com.bone.studio.generator.application.command.cmd.CreateCodeTemplateCommand;
import com.bone.studio.generator.application.command.handler.CreateCodeTemplateHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(name = "CreateCodeTemplate", description = "创建代码模板", transactional = false)
@Service
@RequiredArgsConstructor
public class CreateCodeTemplateUseCase implements UseCaseExecutor<CreateCodeTemplateCommand, Long> {

    private final CreateCodeTemplateHandler createCodeTemplateHandler;

    @Override
    public Long execute(CreateCodeTemplateCommand command) {
        return createCodeTemplateHandler.handle(command);
    }
}
