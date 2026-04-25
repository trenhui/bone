package com.bone.studio.generator.application.usecase;

import com.bone.core.usecase.UseCase;
import com.bone.studio.generator.application.command.cmd.UpdateCodeTemplateCommand;
import com.bone.studio.generator.application.command.handler.UpdateCodeTemplateHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(name = "UpdateCodeTemplate", description = "更新代码模板", transactional = false)
@Service
@RequiredArgsConstructor
public class UpdateCodeTemplateUseCase implements UseCaseExecutor<UpdateCodeTemplateCommand, Long> {

    private final UpdateCodeTemplateHandler updateCodeTemplateHandler;

    @Override
    public Long execute(UpdateCodeTemplateCommand command) {
        return updateCodeTemplateHandler.handle(command);
    }
}
