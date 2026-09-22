package com.bone.studio.generator.application;

import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.command.cmd.DeleteCodeTemplateCommand;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Capability(
    name = "deleteCodeTemplate",
    description = "删除代码模板",
    inputSchema = "{}",
    outputSchema = "{}")
public class DeleteCodeTemplateApplicationService {

  private final CodeTemplateRepository codeTemplateRepository;

  @Transactional
  public boolean handle(DeleteCodeTemplateCommand command) {
    return codeTemplateRepository.deleteById(command.getId());
  }
}
