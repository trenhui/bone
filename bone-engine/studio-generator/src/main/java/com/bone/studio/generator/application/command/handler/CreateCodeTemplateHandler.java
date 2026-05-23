package com.bone.studio.generator.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.command.cmd.CreateCodeTemplateCommand;
import com.bone.studio.generator.domain.data.CodeTemplate;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Capability(name = "createCodeTemplate", description = "创建代码模板", inputSchema = "{}", outputSchema = "{}")
public class CreateCodeTemplateHandler {

    private final CodeTemplateRepository codeTemplateRepository;

    @Transactional
    public Long handle(CreateCodeTemplateCommand command) {
        CodeTemplate template = CodeTemplate.create(
                System.currentTimeMillis(),
                0L, // 租户ID，暂时硬编码
                command.getName(),
                command.getCode(),
                command.getDescription(),
                command.getType(),
                command.getContent()
        );
        codeTemplateRepository.save(template);
        return template.getId();
    }
}
