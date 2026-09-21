package com.bone.studio.generator.application.query.handler;

import com.bone.core.exception.BizException;
import com.bone.studio.generator.application.query.qry.GetCodeTemplateDetailQuery;
import com.bone.studio.generator.domain.data.CodeTemplate;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 模板详情读侧：前端「代码生成 / 模板管理」详情页调用，此前后端无该路由（405）。 */
@Component
@RequiredArgsConstructor
public class GetCodeTemplateDetailQueryApplicationService {

  private final CodeTemplateRepository codeTemplateRepository;

  @Transactional(readOnly = true)
  public CodeTemplate handle(GetCodeTemplateDetailQuery qry) {
    CodeTemplate template = codeTemplateRepository.findById(qry.getId());
    if (template == null) {
      throw new BizException(404, "GEN_TEMPLATE_NOT_FOUND: " + qry.getId());
    }
    return template;
  }
}
