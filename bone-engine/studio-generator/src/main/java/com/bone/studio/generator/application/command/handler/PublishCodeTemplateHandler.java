package com.bone.studio.generator.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.command.cmd.PublishCodeTemplateCommand;
import com.bone.studio.generator.domain.data.CodeTemplate;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Capability(name = "publishCodeTemplate", description = "发布代码模板", inputSchema = "{}", outputSchema = "{}")
public class PublishCodeTemplateHandler {

    private final CodeTemplateRepository codeTemplateRepository;

    @Transactional
    public Long handle(PublishCodeTemplateCommand command) {
        CodeTemplate template = codeTemplateRepository.findById(command.getId());
        if (template == null) {
            throw new IllegalArgumentException("模板不存在: " + command.getId());
        }
        
        template.publish();
        codeTemplateRepository.save(template);
        return template.getId();
    }
}
