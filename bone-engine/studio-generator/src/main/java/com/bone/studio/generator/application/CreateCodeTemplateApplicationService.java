package com.bone.studio.generator.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.command.cmd.CreateCodeTemplateCommand;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 创建代码模板。
 *
 * <p><b>不发 DomainEvent 豁免（E-5.4）</b>：本服务写入聚合，当前不发布领域事件； 若将来接入事件发布，须改为调用 publishFrom 并移除本豁免。
 */
@Component
@RequiredArgsConstructor
@NoDomainEvent
@Capability(
    name = "createCodeTemplate",
    description = "创建代码模板",
    inputSchema = "{}",
    outputSchema = "{}")
public class CreateCodeTemplateApplicationService {

  private final CodeTemplateRepository codeTemplateRepository;

  @Transactional
  public Long handle(CreateCodeTemplateCommand command) {
    CodeTemplate template =
        CodeTemplate.create(
            System.currentTimeMillis(),
            0L, // 租户ID，暂时硬编码
            command.getName(),
            command.getCode(),
            command.getDescription(),
            command.getType(),
            command.getContent());
    codeTemplateRepository.save(template);
    return template.getId();
  }
}
