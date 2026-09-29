package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import org.springframework.stereotype.Component;

/** 创建请求体生成器：{@code adapter/web/dto/request}，带 {@code jakarta.validation} 约束。 */
@Component
public class CreateRequestGenerator extends AbstractFileGenerator {

  public CreateRequestGenerator(TemplateRenderer templateRenderer) {
    super(templateRenderer);
  }

  @Override
  protected String templateType() {
    return "createRequest";
  }

  @Override
  protected String directory(GenTableMetadata table) {
    return "/adapter/web/dto/request/";
  }

  @Override
  protected String fileName(GenTableMetadata table) {
    return "Create" + table.getCustomEntityName() + "Req.java";
  }
}
