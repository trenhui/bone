package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import org.springframework.stereotype.Component;

/**
 * 控制器生成器。
 *
 * <p>REST 路径前缀取自模块名（此前写死 {@code PlatformApiPaths.METADATA_V1}，非元数据模块生成出的路径是错的）。
 */
@Component
public class ControllerGenerator extends AbstractFileGenerator {

  public ControllerGenerator(TemplateRenderer templateRenderer) {
    super(templateRenderer);
  }

  @Override
  protected String templateType() {
    return "controller";
  }

  @Override
  protected String directory(GenTableMetadata table) {
    return "/adapter/web/controller/";
  }

  @Override
  protected String fileName(GenTableMetadata table) {
    return table.getCustomEntityName() + "Controller.java";
  }
}
