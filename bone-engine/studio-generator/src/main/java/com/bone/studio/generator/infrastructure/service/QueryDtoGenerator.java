package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import org.springframework.stereotype.Component;

/**
 * 应用层读模型生成器：{@code application/query/dto/{Agg}Dto}。
 *
 * <p><b>为何必须存在</b>：此前应用服务直接返回 {@code adapter.web.dto.response.XxxResponse}，形成 {@code application →
 * adapter} 的反向依赖，违反分层依赖 {@code adapter → application → domain}。应用层只出 {@code Dto}， 装配到对外契约由 {@code
 * adapter} 层的 Assembler 完成（blueprint 同口径）。
 */
@Component
public class QueryDtoGenerator extends AbstractFileGenerator {

  public QueryDtoGenerator(TemplateRenderer templateRenderer) {
    super(templateRenderer);
  }

  @Override
  protected String templateType() {
    return "queryDto";
  }

  @Override
  protected String directory(GenTableMetadata table) {
    return "/application/query/dto/";
  }

  @Override
  protected String fileName(GenTableMetadata table) {
    return table.getCustomEntityName() + "Dto.java";
  }
}
