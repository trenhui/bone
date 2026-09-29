package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import org.springframework.stereotype.Component;

/**
 * Web 装配器生成器：MapStruct {@code @Mapper(componentModel = "spring")} 接口，对齐 blueprint {@code
 * OrderAssembler}。
 *
 * <p>职责是把协议对象（{@code *Req}）翻译成应用层命令、把应用层 {@code Dto} 翻译成对外契约 {@code *Resp}， 使领域对象不出现在 adapter
 * 层（HC-003）。
 */
@Component
public class AssemblerGenerator extends AbstractFileGenerator {

  public AssemblerGenerator(TemplateRenderer templateRenderer) {
    super(templateRenderer);
  }

  @Override
  protected String templateType() {
    return "assembler";
  }

  @Override
  protected String directory(GenTableMetadata table) {
    return "/adapter/web/assembler/";
  }

  @Override
  protected String fileName(GenTableMetadata table) {
    return table.getCustomEntityName() + "Assembler.java";
  }
}
