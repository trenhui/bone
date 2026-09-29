package com.bone.studio.generator.application;

import com.bone.studio.generator.application.query.qry.GetCodeTemplateDetailQuery;
import com.bone.studio.generator.common.GeneratorErrorCodes;
import com.bone.studio.generator.common.GeneratorErrors;
import com.bone.studio.generator.domain.gateway.BuiltInTemplateGateway;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 模板详情读侧：前端「代码生成 / 模板管理」详情页调用，此前后端无该路由（405）。 */
@Component
@RequiredArgsConstructor
public class GetCodeTemplateDetailQueryApplicationService {

  private final CodeTemplateRepository codeTemplateRepository;
  private final BuiltInTemplateGateway builtInTemplateGateway;

  /**
   * 内置模板行在库里的 {@code content} 为空（真源是 classpath 模板），而前端预览直接展示 {@code content}；
   * 不回填的话用户点开内置模板看到的就是一片空白。回填只发生在读侧，不落库。
   */
  @Transactional(readOnly = true)
  public CodeTemplate handle(GetCodeTemplateDetailQuery qry) {
    CodeTemplate template = codeTemplateRepository.findById(qry.getId());
    if (template == null) {
      throw GeneratorErrors.of(GeneratorErrorCodes.TEMPLATE_NOT_FOUND, qry.getId());
    }
    builtInTemplateGateway.contentOf(template.getCode()).ifPresent(template::fillBuiltInContent);
    return template;
  }
}
