package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.code.GeneratedFile;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import com.bone.studio.generator.domain.service.FileGenerator;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RepositoryGenerator implements FileGenerator {

  private final Configuration freemarkerConfig;

  @Override
  public boolean supports(String templateType) {
    return "repository".equals(templateType);
  }

  @Override
  public GeneratedFile generate(
      GenTableMetadata table, CodeTemplate template, String basePackage, String moduleName) {
    Map<String, Object> model = new HashMap<>();
    model.put("entityName", table.getCustomEntityName());
    model.put("table", table);
    model.put("basePackage", basePackage);
    model.put("moduleName", moduleName);
    model.put("utils", new GeneratorUtils());

    try {
      StringWriter writer = new StringWriter();
      freemarkerConfig.getTemplate(template.getCode() + ".ftl").process(model, writer);
      String content = writer.toString();
      String fileName = table.getCustomEntityName() + "Repository.java";
      String filePath =
          GeneratorUtils.basePath(basePackage, moduleName) + "/domain/repository/" + fileName;
      return GeneratedFile.builder()
          .filePath(filePath)
          .fileName(table.getCustomEntityName() + "Repository.java")
          .content(content)
          .fileType("java")
          .fileSize(content.length())
          .build();
    } catch (IOException | TemplateException e) {
      throw new RuntimeException("Failed to generate repository file", e);
    }
  }
}
