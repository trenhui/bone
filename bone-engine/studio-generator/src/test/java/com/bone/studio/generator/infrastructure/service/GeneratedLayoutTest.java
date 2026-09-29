package com.bone.studio.generator.infrastructure.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.studio.generator.domain.model.code.GeneratedFile;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.model.data.GenColumnMetadata;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 生成产物门禁：布局 + 与 {@code bone-blueprint} 的写法对齐。
 *
 * <p>这里守的是两条性质，缺一条生成出来就是废代码：
 *
 * <ol>
 *   <li><b>路径与 package 必须同源</b>：产物必须带 {@code src/main/java} 源根，否则拷进 Maven 工程无法编译。
 *   <li><b>骨架必须完整且方向正确</b>：一个聚合要出齐 {@code domain/application/adapter} 三层文件，且依赖只能是 {@code adapter →
 *       application → domain}，应用层反向 import {@code adapter} 会直接判失败。
 * </ol>
 */
class GeneratedLayoutTest {

  private static final String BASE_PACKAGE = "com.example";
  private static final String MODULE = "demo";
  private static final String ENTITY = "Order";
  private static final String AGGREGATE = "order";
  private static final String SOURCE_ROOT = "src/main/java/com/example/demo/";

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
                        .originalColumnName("is_deleted")
                        .javaType("Boolean")
                        .isNullable(true)
                        .columnComment("是否删除")
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
  }

  @Test
  void entityIsTenantAwareAggregateRootWithOptimisticLock() {
    GeneratedFile file = generate(new EntityGenerator(templateRenderer), "entity");

    assertEquals(SOURCE_ROOT + "domain/model/order/Order.java", file.getFilePath());
    String content = file.getContent();
    assertTrue(content.contains("package com.example.demo.domain.model.order;"), content);
    // blueprint 口径：业务聚合一律 TenantAggregateRoot（HC-008），不可退回无租户的 AggregateRoot
    assertTrue(content.contains("extends TenantAggregateRoot<Long>"), content);
    // ADR-0031 D2：SDK 原生乐观锁
    assertTrue(content.contains("@Version"), content);
    // 主键/租户/审计列由基类托管，不得重复声明
    assertFalse(content.contains("private Long id;"), content);
    assertFalse(content.contains("private Long tenantId;"), content);
    assertTrue(content.contains("private Long customerId;"), content);
    assertTrue(content.contains("private BigDecimal totalAmount;"), content);
    assertTrue(content.contains("private Boolean isDeleted;"), content);
    // blueprint 全模块零 @Column，列名靠 @Table + 驼峰推导（只判真实注解，JavaDoc 里会提到这个名字）
    assertFalse(content.contains("@Column("), content);
    // NOT NULL 列在聚合构造期守卫（customer_id 非空；total_amount/remark 可空不守卫）
    assertTrue(content.contains("customerId == null"), content);
    assertFalse(content.contains("totalAmount == null"), content);
    // Instant 审计字段（blueprint 聚合约定），非 LocalDateTime
    assertTrue(content.contains("private Instant createdAt;"), content);
    assertTrue(content.contains("import java.math.BigDecimal;"), content);
  }

  @Test
  void repositoryHasNoImplementationAndNoHandwrittenTenantCondition() {
    GeneratedFile file = generate(new RepositoryGenerator(templateRenderer), "repository");

    assertEquals(SOURCE_ROOT + "domain/repository/OrderRepository.java", file.getFilePath());
    String content = file.getContent();
    assertTrue(content.contains("extends Repository<Order, Long>"), content);
    assertTrue(content.contains("import com.example.demo.domain.model.order.Order;"), content);
    // 手写 tenant 条件会被 SDK 忽略并打 WARN（ADR-0029），故模板不得再拼 eq(tenantId)
    assertFalse(content.contains("getTenantId"), content);
  }

  @Test
  void commandsAreRecordsUnderApplicationCommandPackage() {
    GeneratedFile create = generate(new CreateCommandGenerator(templateRenderer), "createCommand");
    GeneratedFile update = generate(new UpdateCommandGenerator(templateRenderer), "updateCommand");

    assertEquals(SOURCE_ROOT + "application/command/CreateOrderCommand.java", create.getFilePath());
    assertEquals(SOURCE_ROOT + "application/command/UpdateOrderCommand.java", update.getFilePath());
    assertTrue(
        create.getContent().contains("public record CreateOrderCommand("), create.getContent());
    assertTrue(
        update.getContent().contains("public record UpdateOrderCommand(Long id"),
        update.getContent());
    assertTrue(create.getContent().contains("BigDecimal totalAmount"), create.getContent());
  }

  @Test
  void queryDtoMapsAggregateToReadModel() {
    GeneratedFile file = generate(new QueryDtoGenerator(templateRenderer), "queryDto");

    assertEquals(SOURCE_ROOT + "application/query/dto/OrderDto.java", file.getFilePath());
    String content = file.getContent();
    assertTrue(content.contains("import com.example.demo.domain.model.order.Order;"), content);
    assertTrue(content.contains("public static OrderDto from(Order entity)"), content);
    // 业务列访问器：包装类 Boolean 字段 Lombok 生成 getIsDeleted()（不是 isDeleted()）
    assertTrue(content.contains("entity.getIsDeleted()"), content);
    assertTrue(content.contains("entity.getCustomerId()"), content);
  }

  @Test
  void applicationServiceCoversCrudAndDoesNotDependOnAdapter() {
    GeneratedFile file =
        generate(new ApplicationServiceGenerator(templateRenderer), "applicationService");

    assertEquals(SOURCE_ROOT + "application/OrderApplicationService.java", file.getFilePath());
    String content = file.getContent();
    // 分层方向：application 不得反向依赖 adapter（此前模板直接 import adapter.web.dto.response 是违规的）
    assertFalse(content.contains("adapter.web"), content);
    assertTrue(content.contains("class OrderApplicationService"), content);
    assertTrue(content.contains("@Service"), content);
    assertTrue(content.contains("@Transactional\n"), content);
    assertTrue(content.contains("@Transactional(readOnly = true)"), content);
    assertTrue(content.contains("Long create(CreateOrderCommand command)"), content);
    assertTrue(content.contains("void update(UpdateOrderCommand command)"), content);
    assertTrue(content.contains("OrderDto getById(Long id)"), content);
    // 乐观锁冲突必须翻译成 409，不能漏成 500
    assertTrue(content.contains("OptimisticLockingFailureException"), content);
    assertTrue(content.contains("HttpStatus.CONFLICT.value()"), content);
    // 稳定业务码由 ERROR_PREFIX 运行时拼接，供前端 i18n.t('errors.' + errorCode) 映射
    assertTrue(content.contains("DEMO_ORDER\""), content);
    assertTrue(content.contains("ERROR_PREFIX + \"_NOT_FOUND\""), content);
  }

  @Test
  void requestDtosCarryValidationAndPageQueryBoundsSize() {
    GeneratedFile create = generate(new CreateRequestGenerator(templateRenderer), "createRequest");
    GeneratedFile page = generate(new PageQueryGenerator(templateRenderer), "pageQuery");

    assertEquals(SOURCE_ROOT + "adapter/web/dto/request/CreateOrderReq.java", create.getFilePath());
    assertEquals(SOURCE_ROOT + "adapter/web/dto/request/OrderPageQry.java", page.getFilePath());
    assertTrue(
        create.getContent().contains("@NotNull(message = \"客户ID不能为空\")"), create.getContent());
    String pageContent = page.getContent();
    assertTrue(pageContent.contains("@Max(value = 100"), pageContent);
    assertTrue(pageContent.contains("@Min(value = 1"), pageContent);
  }

  @Test
  void responseAndAssemblerLiveInAdapterLayer() {
    GeneratedFile response = generate(new ResponseGenerator(templateRenderer), "response");
    GeneratedFile assembler = generate(new AssemblerGenerator(templateRenderer), "assembler");

    // E-13.1：*Resp 后缀而非 *Response
    assertEquals(SOURCE_ROOT + "adapter/web/dto/response/OrderResp.java", response.getFilePath());
    assertEquals(
        SOURCE_ROOT + "adapter/web/assembler/OrderAssembler.java", assembler.getFilePath());
    assertTrue(response.getContent().contains("public class OrderResp"), response.getContent());
    String assemblerContent = assembler.getContent();
    assertTrue(assemblerContent.contains("@Mapper(componentModel = \"spring\")"), assemblerContent);
    assertTrue(assemblerContent.contains("OrderResp toOrderResp(OrderDto dto)"), assemblerContent);
    // 路径 id 不在请求体里：装配器显式补齐命令首参，避免多源参数映射出 id=null
    assertTrue(assemblerContent.contains("new UpdateOrderCommand(id,"), assemblerContent);
  }

  @Test
  void controllerExposesFullCrudWithValidatedBodies() {
    GeneratedFile file = generate(new ControllerGenerator(templateRenderer), "controller");

    assertEquals(SOURCE_ROOT + "adapter/web/controller/OrderController.java", file.getFilePath());
    String content = file.getContent();
    // REST 资源走复数：/api/v1/demo/orders
    assertTrue(content.contains("\"/api/v1/demo/orders\""), content);
    assertTrue(content.contains("@Valid @RequestBody CreateOrderReq"), content);
    assertTrue(content.contains("@Valid @ModelAttribute OrderPageQry"), content);
    assertTrue(content.contains("@ResponseStatus(HttpStatus.CREATED)"), content);
    assertTrue(content.contains("@PutMapping(\"/{id}\")"), content);
    assertTrue(content.contains("@DeleteMapping(\"/{id}\")"), content);
    assertTrue(content.contains("@GetMapping(\"/{id}\")"), content);
    // 控制器只依赖应用服务与装配器，不碰仓储
    assertFalse(content.contains("OrderRepository"), content);
    assertFalse(content.contains("domain.model.order.Order;"), content);
    assertTrue(content.contains("ApiResponse.success("), content);
  }

  @Test
  void optionalArtifactsLandOutsideMainSources() {
    GeneratedFile test = generate(new AggregateTestGenerator(templateRenderer), "aggregateTest");
    GeneratedFile doc = generate(new ApiDocGenerator(templateRenderer), "apiDoc");

    // 测试源码根与业务源码根不同，文档落在模块根下——混进 src/main/java 就无法编译/不成文档
    assertEquals(
        "src/test/java/com/example/demo/domain/model/order/OrderTest.java", test.getFilePath());
    assertEquals("com/example/demo/docs/order-api.md", doc.getFilePath());
    assertEquals("md", doc.getFileType());

    String testContent = test.getContent();
    assertTrue(testContent.contains("class OrderTest"), testContent);
    // 纯单测：不启 Spring、不连库
    assertFalse(testContent.contains("@SpringBootTest"), testContent);
    assertTrue(testContent.contains("new BigDecimal(\"1\")"), testContent);
    // 必填列缺失必须抛 DomainException（customer_id 非空）
    assertTrue(testContent.contains("assertThrows("), testContent);

    String docContent = doc.getContent();
    assertTrue(docContent.contains("/api/v1/demo/orders"), docContent);
    assertTrue(docContent.contains("DEMO_ORDER_NOT_FOUND"), docContent);
  }

  private GeneratedFile generate(AbstractFileGenerator generator, String code) {
    return generator.generate(table, template(code), BASE_PACKAGE, MODULE);
  }

  private CodeTemplate template(String code) {
    return CodeTemplate.builder().code(code).build();
  }
}
