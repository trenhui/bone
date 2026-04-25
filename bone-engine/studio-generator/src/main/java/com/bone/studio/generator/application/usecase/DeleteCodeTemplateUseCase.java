package com.bone.studio.generator.application.usecase;

import com.bone.core.usecase.UseCase;
import com.bone.studio.generator.application.command.cmd.DeleteCodeTemplateCommand;
import com.bone.studio.generator.application.command.handler.DeleteCodeTemplateHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(name = "DeleteCodeTemplate", description = "删除代码模板", transactional = false)
@Service
@RequiredArgsConstructor
public class DeleteCodeTemplateUseCase implements UseCaseExecutor<DeleteCodeTemplateCommand, Boolean> {

    private final DeleteCodeTemplateHandler deleteCodeTemplateHandler;

    @Override
    public Boolean execute(DeleteCodeTemplateCommand command) {
        return deleteCodeTemplateHandler.handle(command);
    }
}
