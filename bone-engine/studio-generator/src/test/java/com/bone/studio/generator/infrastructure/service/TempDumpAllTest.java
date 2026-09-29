package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.code.GeneratedFile;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.model.data.GenColumnMetadata;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 临时用例：导出全部 14 个产物用于 javac 实测。用完即删。 */
class TempDumpAllTest {

  @Test
  void dump() throws IOException {
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
    cfg.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
    TemplateRenderer renderer = new TemplateRenderer(cfg, new StringTemplateLoader());

    GenTableMetadata table =
        GenTableMetadata.builder()
            .customEntityName("Order")
            .originalTableName("t_order")
            .tableComment("订单")
            .columns(
                List.of(
                    GenColumnMetadata.builder()
                        .originalColumnName("id")
                        .javaType("Long")
                        .isPrimaryKey(true)
                        .build(),
                    GenColumnMetadata.builder()
                        .originalColumnName("tenant_id")
                        .javaType("Long")
                        .build(),
                    GenColumnMetadata.builder()
                        .originalColumnName("created_at")
                        .javaType("LocalDateTime")
                        .build(),
                    GenColumnMetadata.builder()
                        .originalColumnName("updated_at")
                        .javaType("LocalDateTime")
                        .build(),
                    GenColumnMetadata.builder()
                        .originalColumnName("version")
                        .javaType("Long")
                        .build(),
                    GenColumnMetadata.builder()
                        .originalColumnName("customer_id")
                        .javaType("Long")
                        .isNullable(false)
                        .columnComment("客户ID")
                        .build(),
                    GenColumnMetadata.builder()
                        .originalColumnName("total_amount")
                        .javaType("BigDecimal")
                        .isNullable(true)
                        .columnComment("订单金额")
                        .build(),
                    GenColumnMetadata.builder()
                        .originalColumnName("remark")
                        .javaType("String")
                        .isNullable(true)
                        .columnComment("备注")
                        .build()))
            .build();

    List<AbstractFileGenerator> generators =
        List.of(
            new EntityGenerator(renderer),
            new RepositoryGenerator(renderer),
            new CreateCommandGenerator(renderer),
            new UpdateCommandGenerator(renderer),
            new QueryDtoGenerator(renderer),
            new ApplicationServiceGenerator(renderer),
            new CreateRequestGenerator(renderer),
            new UpdateRequestGenerator(renderer),
            new PageQueryGenerator(renderer),
            new ResponseGenerator(renderer),
            new AssemblerGenerator(renderer),
            new ControllerGenerator(renderer),
            new AggregateTestGenerator(renderer),
            new ApiDocGenerator(renderer));

    String output = System.getProperty("gen.preview.dir", "/tmp/bone-gen-preview2");
    for (AbstractFileGenerator generator : generators) {
      GeneratedFile file =
          generator.generate(
              table, CodeTemplate.builder().code(code(generator)).build(), "com.example", "demo");
      Path target = Paths.get(output).resolve(file.getFilePath());
      Files.createDirectories(target.getParent());
      Files.write(target, file.getContent().getBytes(StandardCharsets.UTF_8));
    }
  }

  private String code(AbstractFileGenerator generator) {
    String name = generator.getClass().getSimpleName();
    return name.substring(0, 1).toLowerCase() + name.substring(1).replace("Generator", "");
  }
}
