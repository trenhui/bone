package com.bone.studio.generator.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.studio.generator.application.command.cmd.UpdateCodeTemplateCommand;
import com.bone.studio.generator.domain.data.CodeTemplate;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Capability(name = "updateCodeTemplate", description = "更新代码模板", inputSchema = "{}", outputSchema = "{}")
public class UpdateCodeTemplateHandler {

    private final CodeTemplateRepository codeTemplateRepository;

    @Transactional
    public Long handle(UpdateCodeTemplateCommand command) {
        CodeTemplate template = codeTemplateRepository.findById(command.getId());
        if (template == null) {
            throw new IllegalArgumentException("模板不存在: " + command.getId());
        }
        
        template.updateContent(command.getContent());
        codeTemplateRepository.save(template);
        return template.getId();
    }
}
