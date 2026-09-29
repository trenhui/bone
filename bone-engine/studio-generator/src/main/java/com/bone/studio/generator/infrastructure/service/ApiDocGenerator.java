package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import org.springframework.stereotype.Component;

/**
 * 接口文档生成器：产出 {@code docs/{聚合}-api.md}，由 {@code includeDocumentation} 开关控制。
 *
 * <p>文档不是可编辑模板（勾选模板行的链路不会带上这个开关），故同样只由开关控制，不进模板管理。
 */
@Component
public class ApiDocGenerator extends AbstractFileGenerator {

  public ApiDocGenerator(TemplateRenderer templateRenderer) {
    super(templateRenderer);
  }

  @Override
  protected String templateType() {
    return "apiDoc";
  }

  @Override
  protected String directory(GenTableMetadata table) {
    return "/docs/";
  }

  @Override
  protected String fileName(GenTableMetadata table) {
    return GeneratorUtils.aggregateSegment(table.getCustomEntityName()) + "-api.md";
  }

  /** 文档不落在源码根下。 */
  @Override
  protected String sourceRoot() {
    return "";
  }

  @Override
  protected String fileType() {
    return "md";
  }
}
