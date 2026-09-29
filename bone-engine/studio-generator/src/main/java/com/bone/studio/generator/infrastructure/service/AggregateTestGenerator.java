package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import org.springframework.stereotype.Component;

/**
 * 聚合纯单测生成器：产物落在 {@code src/test/java} 下，由 {@code includeTests} 开关控制。
 *
 * <p>不把它做成可选模板行：模板管理里能勾选的行会进入物理库链路，而该链路的入参里没有 includeTests 开关，勾选了也不会产出——与其让人勾了没反应，不如让它只由开关控制。
 */
@Component
public class AggregateTestGenerator extends AbstractFileGenerator {

  public AggregateTestGenerator(TemplateRenderer templateRenderer) {
    super(templateRenderer);
  }

  @Override
  protected String templateType() {
    return "aggregateTest";
  }

  @Override
  protected String directory(GenTableMetadata table) {
    return "/domain/model/" + GeneratorUtils.aggregateSegment(table.getCustomEntityName()) + "/";
  }

  @Override
  protected String fileName(GenTableMetadata table) {
    return table.getCustomEntityName() + "Test.java";
  }

  /** 测试源码根与业务源码根不同，这里覆盖基类的 {@code src/main/java} 默认值。 */
  @Override
  protected String sourceRoot() {
    return "src/test/java";
  }
}
