package com.bone.studio.generator.application.command.handler;

import com.bone.core.exception.NotFoundException;
import com.bone.studio.generator.domain.data.CodeTemplate;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import com.bone.studio.generator.domain.service.CodeGeneratorService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PreviewTemplateApplicationService {

  private final CodeTemplateRepository codeTemplateRepository;
  private final CodeGeneratorService codeGeneratorService;

  public String handle(String templateId, Map<String, Object> parameters) {
    // 1. 查找模板
    CodeTemplate template = codeTemplateRepository.findById(Long.parseLong(templateId));
    if (template == null) {
      throw new NotFoundException("模板不存在");
    }

    // 2. 预览模板（简化实现）
    return template.getContent();
  }
}
