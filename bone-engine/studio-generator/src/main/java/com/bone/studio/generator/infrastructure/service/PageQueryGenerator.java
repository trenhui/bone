package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import org.springframework.stereotype.Component;

/**
 * 分页查询入参生成器：{@code *PageRequest}。
 *
 * <p>分页入参不散落成 {@code @RequestParam}—— blueprint 用 {@code @Valid @ModelAttribute} 收整个查询对象， 边界值用
 * {@code @Min/@Max} 兜住。
 *
 * <p><b>命名对齐</b>：以 blueprint 的 {@code OrderPageRequest} 为基准（2026-10-06 由 {@code OrderPageQry} 改名）。
 * {@code Qry} 后缀此前全工程仅一处，且与应用层 {@code XxxPageQuery} 只差一个字母；层次由包名表达更准确。
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
    return table.getCustomEntityName() + "PageRequest.java";
  }
}
