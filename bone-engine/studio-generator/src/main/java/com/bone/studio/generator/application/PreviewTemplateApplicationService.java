package com.bone.studio.generator.application;

import com.bone.core.exception.NotFoundException;
import com.bone.studio.generator.domain.gateway.BuiltInTemplateGateway;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PreviewTemplateApplicationService {

  private final CodeTemplateRepository codeTemplateRepository;
  private final BuiltInTemplateGateway builtInTemplateGateway;

  /**
   * 预览模板正文：优先取库里的自定义正文，为空则回落到同名内置模板。
   *
   * <p>参数 {@code parameters} 预留给后续真实渲染；当前仍返回模板原文，但口径已与生成链路一致—— 此前 content 为空时预览只返回
   * null，用户看不到内置模板的内容。
   */
  public String handle(String templateId, Map<String, Object> parameters) {
    CodeTemplate template = codeTemplateRepository.findById(Long.parseLong(templateId));
    if (template == null) {
      throw new NotFoundException("模板不存在");
    }
    String content = template.getContent();
    if (content != null && !content.isEmpty()) {
      return content;
    }
    return builtInTemplateGateway.contentOf(template.getCode()).orElse(null);
  }
}
