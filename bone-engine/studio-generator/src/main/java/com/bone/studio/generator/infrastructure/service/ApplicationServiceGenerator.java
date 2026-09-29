package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import org.springframework.stereotype.Component;

/**
 * 应用服务生成器：语义化 {@code *ApplicationService}，命令直接内联处理（ADR-0028）。
 *
 * <p>不生成 {@code *CommandHandler} / {@code *QueryHandler}：一个用例只选一种构件，禁止 ApplicationService 与 Handler
 * 套娃。
 */
@Component
public class ApplicationServiceGenerator extends AbstractFileGenerator {

  public ApplicationServiceGenerator(TemplateRenderer templateRenderer) {
    super(templateRenderer);
  }

  @Override
  protected String templateType() {
    return "applicationService";
  }

  @Override
  protected String directory(GenTableMetadata table) {
    return "/application/";
  }

  @Override
  protected String fileName(GenTableMetadata table) {
    return table.getCustomEntityName() + "ApplicationService.java";
  }
}
