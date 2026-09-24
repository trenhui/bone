package com.bone.studio.generator.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.command.cmd.PublishCodeTemplateCommand;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 发布代码模板。
 *
 * <p><b>不发 DomainEvent 豁免（E-5.4）</b>：本服务写入聚合，当前不发布领域事件； 若将来接入事件发布，须改为调用 publishFrom 并移除本豁免。
 */
@Component
@RequiredArgsConstructor
@NoDomainEvent
@Capability(
    name = "publishCodeTemplate",
    description = "发布代码模板",
    inputSchema = "{}",
    outputSchema = "{}")
public class PublishCodeTemplateApplicationService {

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
