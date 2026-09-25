package com.bone.studio.generator.infrastructure.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.studio.generator.domain.model.code.GeneratedFile;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import freemarker.cache.StringTemplateLoader;
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

  private TemplateRenderer templateRenderer;
  private GenTableMetadata table;

  @BeforeEach
  void setUp() {
    Configuration freemarkerConfig = new Configuration(Configuration.VERSION_2_3_32);
    freemarkerConfig.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
    templateRenderer = new TemplateRenderer(freemarkerConfig, new StringTemplateLoader());

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
        new EntityGenerator(templateRenderer)
            .generate(table, template("entity"), BASE_PACKAGE, MODULE);

    assertEquals("com/example/demo/domain/model/order/Order.java", file.getFilePath());
    assertTrue(
        file.getContent().contains("package com.example.demo.domain.model.order;"),
        file.getContent());
  }

  @Test
  void repositoryImportsAggregateFromModelPackage() {
    GeneratedFile file =
        new RepositoryGenerator(templateRenderer)
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
        new ControllerGenerator(templateRenderer)
            .generate(table, template("controller"), BASE_PACKAGE, MODULE);

    assertEquals(
        "com/example/demo/adapter/web/controller/OrderController.java", file.getFilePath());
    // HC-003：控制器只依赖响应对象，不直接暴露领域对象（此前 import 的是 domain.model.order.Order）
    assertTrue(
        file.getContent()
            .contains("import com.example.demo.adapter.web.dto.response.OrderResponse;"),
        file.getContent());
    // 路径前缀取自模块名，此前写死 PlatformApiPaths.METADATA_V1
    assertTrue(file.getContent().contains("\"/api/v1/demo/order\""), file.getContent());
  }

  @Test
  void responseLivesUnderWebDtoPackage() {
    GeneratedFile file =
        new ResponseGenerator(templateRenderer)
            .generate(table, template("response"), BASE_PACKAGE, MODULE);

    assertEquals(
        "com/example/demo/adapter/web/dto/response/OrderResponse.java", file.getFilePath());
    assertTrue(
        file.getContent().contains("import com.example.demo.domain.model.order.Order;"),
        file.getContent());
    assertTrue(
        file.getContent().contains("public static OrderResponse from(Order entity)"),
        file.getContent());
  }

  @Test
  void applicationServiceCarriesModuleSegment() {
    GeneratedFile file =
        new ApplicationServiceGenerator(templateRenderer)
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
