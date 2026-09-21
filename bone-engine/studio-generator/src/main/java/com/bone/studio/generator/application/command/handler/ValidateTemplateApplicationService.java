package com.bone.studio.generator.application.command.handler;

import com.bone.core.exception.NotFoundException;
import com.bone.studio.generator.domain.data.CodeTemplate;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import com.bone.studio.generator.domain.service.CodeGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ValidateTemplateApplicationService {

  private final CodeTemplateRepository codeTemplateRepository;
  private final CodeGeneratorService codeGeneratorService;

  public boolean handle(String templateId) {
    CodeTemplate template = codeTemplateRepository.findById(Long.parseLong(templateId));
    if (template == null) {
      throw new NotFoundException("模板不存在");
    }

    return template.getContent() != null && !template.getContent().isEmpty();
  }
}
