package com.bone.studio.generator.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.command.cmd.CreateCodeTemplateCommand;
import com.bone.studio.generator.common.GeneratorErrorCodes;
import com.bone.studio.generator.common.GeneratorErrors;
import com.bone.studio.generator.domain.gateway.TenantProvider;
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
  private final TenantProvider tenantProvider;

  @Transactional
  public Long handle(CreateCodeTemplateCommand command) {
    Long tenantId = tenantProvider.currentTenantIdOrNull();
    if (tenantId == null) {
      throw GeneratorErrors.of(GeneratorErrorCodes.TENANT_CONTEXT_MISSING, command.getName());
    }
    CodeTemplate template =
        CodeTemplate.create(
            System.currentTimeMillis(),
            tenantId,
            command.getName(),
            command.getCode(),
            command.getDescription(),
            command.getType(),
            command.getLanguage(),
            command.getEngine(),
            command.getVersion(),
            command.getContent());
    codeTemplateRepository.save(template);
    return template.getId();
  }
}
