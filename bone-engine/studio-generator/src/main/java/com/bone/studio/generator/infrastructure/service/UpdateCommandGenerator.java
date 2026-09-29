package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import org.springframework.stereotype.Component;

/** 更新命令生成器：{@code record} 形式不可变入参，首参为聚合 id。 */
@Component
public class UpdateCommandGenerator extends AbstractFileGenerator {

  public UpdateCommandGenerator(TemplateRenderer templateRenderer) {
    super(templateRenderer);
  }

  @Override
  protected String templateType() {
    return "updateCommand";
  }

  @Override
  protected String directory(GenTableMetadata table) {
    return "/application/command/";
  }

  @Override
  protected String fileName(GenTableMetadata table) {
    return "Update" + table.getCustomEntityName() + "Command.java";
  }
}
