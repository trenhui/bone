package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import org.springframework.stereotype.Component;

/**
 * 响应契约生成器：{@code adapter/web/dto/response/{Agg}Resp}。
 *
 * <p>命名对齐《Bone-DDD》E-13.1 的 {@code *Resp} 后缀（blueprint {@code OrderSummaryResp} 注释锚点）。
 */
@Component
public class ResponseGenerator extends AbstractFileGenerator {

  public ResponseGenerator(TemplateRenderer templateRenderer) {
    super(templateRenderer);
  }

  @Override
  protected String templateType() {
    return "response";
  }

  @Override
  protected String directory(GenTableMetadata table) {
    return "/adapter/web/dto/response/";
  }

  @Override
  protected String fileName(GenTableMetadata table) {
    return table.getCustomEntityName() + "Resp.java";
  }
}
