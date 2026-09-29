package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.code.GeneratedFile;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import com.bone.studio.generator.domain.service.FileGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件生成器公共实现：统一装配模板变量 + 统一 {@code filePath} 与 {@code package} 的对齐规则。
 *
 * <p><b>为何抽基类</b>：此前五个 Generator 各自 {@code model.put} 一份，变量集合并不一致——{@code repository} 塞了模板从不使用的
 * {@code entityName}；{@code applicationService} 没塞 {@code columns}，导致模板一旦要遍历列就抛 {@code
 * InvalidReferenceException}。抽成基类后变量全集只有一处定义。
 *
 * <p><b>变量全集</b>（模板可用）：{@code table}、{@code columns}、{@code businessColumns}、{@code
 * businessTypeImports}、{@code basePackage}、{@code moduleName}、{@code apiPrefix}、{@code utils}。
 */
public abstract class AbstractFileGenerator implements FileGenerator {

  protected final TemplateRenderer templateRenderer;

  protected AbstractFileGenerator(TemplateRenderer templateRenderer) {
    this.templateRenderer = templateRenderer;
  }

  @Override
  public boolean supports(String templateType) {
    return templateType().equals(templateType);
  }

  @Override
  public GeneratedFile generate(
      GenTableMetadata table, CodeTemplate template, String basePackage, String moduleName) {
    Map<String, Object> model = new HashMap<>();
    model.put("table", table);
    model.put(
        "columns", table == null || table.getColumns() == null ? List.of() : table.getColumns());
    model.put("businessColumns", GeneratorUtils.businessColumns(table));
    model.put("businessTypeImports", GeneratorUtils.businessTypeImports(table));
    model.put("basePackage", basePackage);
    model.put("moduleName", moduleName);
    model.put("utils", new GeneratorUtils());
    model.put("apiPrefix", GeneratorUtils.apiPrefix(moduleName));

    String content = templateRenderer.render(template, model);
    String fileName = fileName(table);
    String filePath =
        GeneratorUtils.basePath(sourceRoot(), basePackage, moduleName)
            + directory(table)
            + fileName;
    return GeneratedFile.builder()
        .filePath(filePath)
        .fileName(fileName)
        .content(content)
        .fileType(fileType())
        .fileSize(content.length())
        .build();
  }

  /** 模板类型码，与 {@code gen_code_template.type} 及 classpath 下 {@code *.ftl} 文件名一致。 */
  protected abstract String templateType();

  /** 产物源根：主源码 {@code src/main/java}、测试源码 {@code src/test/java}、文档为空串（模块根）。 */
  protected String sourceRoot() {
    return "src/main/java";
  }

  /** 产物类型：默认 java，文档类模板覆盖为 md。 */
  protected String fileType() {
    return "java";
  }

  /** 产物相对目录，必须以 {@code /} 结尾，且与模板声明的 package 保持同源规则。 */
  protected abstract String directory(GenTableMetadata table);

  /** 产物文件名（含 {@code .java}）。 */
  protected abstract String fileName(GenTableMetadata table);
}
