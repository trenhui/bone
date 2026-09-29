package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import org.springframework.stereotype.Component;

/**
 * 分页查询入参生成器：{@code *PageQry}。
 *
 * <p>分页入参不散落成 {@code @RequestParam}—— blueprint 用 {@code @Valid @ModelAttribute} 收整个查询对象， 边界值用
 * {@code @Min/@Max} 兜住。
 */
@Component
public class PageQueryGenerator extends AbstractFileGenerator {

  public PageQueryGenerator(TemplateRenderer templateRenderer) {
    super(templateRenderer);
  }

  @Override
  protected String templateType() {
    return "pageQuery";
  }

  @Override
  protected String directory(GenTableMetadata table) {
    return "/adapter/web/dto/request/";
  }

  @Override
  protected String fileName(GenTableMetadata table) {
    return table.getCustomEntityName() + "PageQry.java";
  }
}
