package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.code.GeneratedFile;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import com.bone.studio.generator.domain.service.FileGenerator;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 响应 DTO 生成器：HC-003 要求 Controller 不裸返领域对象，控制器模板依赖本产物。 */
@Component
@RequiredArgsConstructor
public class ResponseGenerator implements FileGenerator {

  private final TemplateRenderer templateRenderer;

  @Override
  public boolean supports(String templateType) {
    return "response".equals(templateType);
  }

  @Override
  public GeneratedFile generate(
      GenTableMetadata table, CodeTemplate template, String basePackage, String moduleName) {
    Map<String, Object> model = new HashMap<>();
    model.put("table", table);
    model.put("columns", table.getColumns());
    model.put("basePackage", basePackage);
    model.put("moduleName", moduleName);
    model.put("utils", new GeneratorUtils());

    String content = templateRenderer.render(template, model);
    String fileName = table.getCustomEntityName() + "Response.java";
    String filePath =
        GeneratorUtils.basePath(basePackage, moduleName) + "/adapter/web/dto/response/" + fileName;
    return GeneratedFile.builder()
        .filePath(filePath)
        .fileName(fileName)
        .content(content)
        .fileType("java")
        .fileSize(content.length())
        .build();
  }
}
