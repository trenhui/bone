package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import org.springframework.stereotype.Component;

/** 创建命令生成器：{@code record} 形式不可变入参，对齐 blueprint {@code application/command}。 */
@Component
public class CreateCommandGenerator extends AbstractFileGenerator {

  public CreateCommandGenerator(TemplateRenderer templateRenderer) {
    super(templateRenderer);
  }

  @Override
  protected String templateType() {
    return "createCommand";
  }

  @Override
  protected String directory(GenTableMetadata table) {
    return "/application/command/";
  }

  @Override
  protected String fileName(GenTableMetadata table) {
    return "Create" + table.getCustomEntityName() + "Command.java";
  }
}
