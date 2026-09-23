package com.bone.studio.generator.infrastructure.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.studio.generator.domain.model.code.GeneratedFile;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import freemarker.template.Configuration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 生成布局门禁（ADR-0036 D1）：生成的骨架必须落在 {@code domain/model/{聚合}/} 下， 且文件路径与模板声明的 package
 * 必须一致（本测试之前两者系统性错位）。
 *
 * <p>聚合包名取实体名全小写：{@code Order} → {@code order}。生成后可人工改名为领域名词。
 */
class GeneratedLayoutTest {

  private static final String BASE_PACKAGE = "com.example";
  private static final String MODULE = "demo";
  private static final String ENTITY = "Order";
  private static final String AGGREGATE = "order";

  private Configuration freemarkerConfig;
  private GenTableMetadata table;

  @BeforeEach
  void setUp() {
    freemarkerConfig = new Configuration(Configuration.VERSION_2_3_32);
    freemarkerConfig.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");

    table =
        GenTableMetadata.builder()
            .customEntityName(ENTITY)
            .originalTableName("t_order")
            .tableComment("订单")
            .columns(List.of())
            .build();
  }

  @Test
  void entityLandsUnderModelAggregatePackage() {
    GeneratedFile file =
        new EntityGenerator(freemarkerConfig)
            .generate(table, template("entity"), BASE_PACKAGE, MODULE);

    assertEquals("com/example/demo/domain/model/order/Order.java", file.getFilePath());
    assertTrue(
        file.getContent().contains("package com.example.demo.domain.model.order;"),
        file.getContent());
  }

  @Test
  void repositoryImportsAggregateFromModelPackage() {
    GeneratedFile file =
        new RepositoryGenerator(freemarkerConfig)
            .generate(table, template("repository"), BASE_PACKAGE, MODULE);

    assertEquals("com/example/demo/domain/repository/OrderRepository.java", file.getFilePath());
    assertTrue(
        file.getContent().contains("package com.example.demo.domain.repository;"),
        file.getContent());
    assertTrue(
        file.getContent().contains("import com.example.demo.domain.model.order.Order;"),
        file.getContent());
  }

  @Test
  void controllerCarriesModuleSegment() {
    GeneratedFile file =
        new ControllerGenerator(freemarkerConfig)
            .generate(table, template("controller"), BASE_PACKAGE, MODULE);

    assertEquals(
        "com/example/demo/adapter/web/controller/OrderController.java", file.getFilePath());
    assertTrue(
        file.getContent().contains("import com.example.demo.domain.model.order.Order;"),
        file.getContent());
  }

  @Test
  void applicationServiceCarriesModuleSegment() {
    GeneratedFile file =
        new ApplicationServiceGenerator(freemarkerConfig)
            .generate(table, template("applicationService"), BASE_PACKAGE, MODULE);

    assertEquals("com/example/demo/application/OrderApplicationService.java", file.getFilePath());
    assertTrue(
        file.getContent().contains("import com.example.demo.domain.model.order.Order;"),
        file.getContent());
  }

  private CodeTemplate template(String code) {
    return CodeTemplate.builder().code(code).build();
  }
}
