package com.bone.studio.generator.application;

import com.bone.core.exception.NotFoundException;
import com.bone.studio.generator.domain.gateway.BuiltInTemplateGateway;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ValidateTemplateApplicationService {

  private final CodeTemplateRepository codeTemplateRepository;
  private final BuiltInTemplateGateway builtInTemplateGateway;

  /**
   * 校验模板可用：库里正文非空即有效；为空时若该 code 存在内置模板，同样视为有效。
   *
   * <p>此前只判 {@code content} 是否为空，于是把「内置模板（content 为空、真源在 classpath）」一律判成无效， 与生成链路的实际口径相反。
   */
  public boolean handle(String templateId) {
    // 用户可从「对自己可见」的列表里选平台(0)种子模板，故按 id 读取需跨租户（受控、仅命中该 id 行）。
    CodeTemplate template = codeTemplateRepository.findByIdAllTenants(Long.parseLong(templateId));
    if (template == null) {
      throw new NotFoundException("模板不存在");
    }
    String content = template.getContent();
    return (content != null && !content.isEmpty())
        || builtInTemplateGateway.exists(template.getCode());
  }
}
