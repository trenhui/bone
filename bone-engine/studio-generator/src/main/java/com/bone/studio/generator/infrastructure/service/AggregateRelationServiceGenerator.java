package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.code.GeneratedFile;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import com.bone.studio.generator.domain.service.AggregateRelationFileGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 主子聚合应用服务生成器：产出 {@code {主表}AggregateApplicationService}，一次事务创建聚合根 + 全部子实体。
 *
 * <p>触发条件是命令 {@code genConfig} 里的 {@code childTable}/{@code childFkColumn}（见应用服务 {@code
 * resolveChildRelation}），不走 {@code templateIds}——它是「关系级」产物而非「表级」产物， 用户在模板管理页勾不出这种粒度。
 *
 * <p>模板：classpath {@code templates/aggregateService.ftl}（唯一真源，与内建单表模板同一回落口径）。
 */
@Component
public class AggregateRelationServiceGenerator implements AggregateRelationFileGenerator {

  private final TemplateRenderer templateRenderer;

  public AggregateRelationServiceGenerator(TemplateRenderer templateRenderer) {
    this.templateRenderer = templateRenderer;
  }

  @Override
  public GeneratedFile generate(
      GenTableMetadata parent,
      GenTableMetadata child,
      String fkColumn,
      String basePackage,
      String moduleName) {
    Map<String, Object> model = new HashMap<>();
    model.put("table", parent);
    model.put("child", child);
    model.put("parentColumns", GeneratorUtils.businessColumns(parent));
    model.put("childColumns", GeneratorUtils.businessColumns(child));
    model.put("fkColumn", fkColumn);
    model.put("basePackage", basePackage);
    model.put("moduleName", moduleName);
    model.put("utils", new GeneratorUtils());

    String entityName = parent.getCustomEntityName();
    String fileName = entityName + "AggregateApplicationService.java";
    // 占位模板对象：TemplateRenderer 只消费 code→classpath 回落与 content，不依赖其余字段
    CodeTemplate template =
        CodeTemplate.builder().code("aggregateService").name("aggregateService").build();
    String content = templateRenderer.render(template, model);
    String filePath =
        GeneratorUtils.basePath("src/main/java", basePackage, moduleName)
            + "/application/"
            + fileName;
    return GeneratedFile.builder()
        .filePath(filePath)
        .fileName(fileName)
        .content(content)
        .fileType("java")
        .fileSize(content.length())
        .build();
  }

  /** 供单测断言模板存在性：内建模板集必须包含 aggregateService。 */
  public static List<String> requiredTemplateCodes() {
    return List.of("aggregateService");
  }
}
