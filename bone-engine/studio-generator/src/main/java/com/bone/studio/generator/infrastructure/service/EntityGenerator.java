package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import org.springframework.stereotype.Component;

/** 聚合根生成器：落在 {@code domain/model/{聚合}/} 下（ADR-0036 D1）。 */
@Component
public class EntityGenerator extends AbstractFileGenerator {

  public EntityGenerator(TemplateRenderer templateRenderer) {
    super(templateRenderer);
  }

  @Override
  protected String templateType() {
    return "entity";
  }

  @Override
  protected String directory(GenTableMetadata table) {
    return "/domain/model/" + GeneratorUtils.aggregateSegment(table.getCustomEntityName()) + "/";
  }

  @Override
  protected String fileName(GenTableMetadata table) {
    return table.getCustomEntityName() + ".java";
  }
}
