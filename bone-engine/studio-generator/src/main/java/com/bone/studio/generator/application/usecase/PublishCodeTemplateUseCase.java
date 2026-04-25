package com.bone.studio.generator.application.usecase;

import com.bone.core.usecase.UseCase;
import com.bone.studio.generator.application.command.cmd.PublishCodeTemplateCommand;
import com.bone.studio.generator.application.command.handler.PublishCodeTemplateHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(name = "PublishCodeTemplate", description = "发布代码模板", transactional = false)
@Service
@RequiredArgsConstructor
public class PublishCodeTemplateUseCase implements UseCaseExecutor<PublishCodeTemplateCommand, Long> {

    private final PublishCodeTemplateHandler publishCodeTemplateHandler;

    @Override
    public Long execute(PublishCodeTemplateCommand command) {
        return publishCodeTemplateHandler.handle(command);
    }
}
