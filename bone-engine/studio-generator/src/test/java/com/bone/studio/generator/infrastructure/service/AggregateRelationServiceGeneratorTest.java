package com.bone.studio.generator.infrastructure.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.studio.generator.domain.model.code.GeneratedFile;
import com.bone.studio.generator.domain.model.data.GenColumnMetadata;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import org.junit.jupiter.api.Test;

/**
 * 主子聚合应用服务生成器门禁。
 *
 * <p>聚合服务是「关系级」产物：模板同时消费主表与子表元数据，外键列参数必须替换为主表 id。 不测住的话，模板变量名漂移（如 {@code child}
 * 改名）会在用户生成时才炸，且错误信息藏在 FreeMarker 栈里。
 */
class AggregateRelationServiceGeneratorTest {

  private final AggregateRelationServiceGenerator generator =
      new AggregateRelationServiceGenerator(renderer());

  @Test
  void rendersAggregateApplicationServiceForParentChildPair() {
    GeneratedFile file = generator.generate(parent(), child(), "order_id", "com.example", "demo");

    assertEquals("OrderAggregateApplicationService.java", file.getFileName());
    assertTrue(file.getFilePath().contains("/application/"), file.getFilePath());
    assertTrue(
        file.getContent().contains("public class OrderAggregateApplicationService"), "类名必须与文件名一致");
    assertTrue(file.getContent().contains("createWithChildren"), "必须提供一次事务创建主子的用例");
    assertTrue(file.getContent().contains("OrderItemRepository"), "必须注入子表仓储（缺了必然编译失败）");
  }

  @Test
  void fkColumnArgumentIsReplacedWithParentId() {
    GeneratedFile file = generator.generate(parent(), child(), "order_id", "com.example", "demo");

    // create 参数列表里外键列位置应是 parent.getId()（主表 id 预分配，子实体创建即完成关联），
    // 不再从子命令取值——子命令即便误传了别的父 id 也会被忽略，聚合一致性由生成器保证
    assertTrue(file.getContent().contains("parent.getId()"), file.getContent());
    assertEquals(-1, file.getContent().indexOf("itemCommand.orderId()"), "外键列不得从子命令取值");
  }

  private GenTableMetadata parent() {
    return GenTableMetadata.builder()
        .customEntityName("Order")
        .originalTableName("t_order")
        .tableComment("订单")
        .columns(
            java.util.List.of(
                GenColumnMetadata.builder()
                    .originalColumnName("order_no")
                    .javaType("String")
                    .columnComment("订单号")
                    .build()))
        .build();
  }

  private GenTableMetadata child() {
    return GenTableMetadata.builder()
        .customEntityName("OrderItem")
        .originalTableName("t_order_item")
        .tableComment("订单明细")
        .columns(
            java.util.List.of(
                GenColumnMetadata.builder()
                    .originalColumnName("order_id")
                    .javaType("Long")
                    .columnComment("订单ID")
                    .build(),
                GenColumnMetadata.builder()
                    .originalColumnName("sku")
                    .javaType("String")
                    .columnComment("SKU")
                    .build()))
        .build();
  }

  private TemplateRenderer renderer() {
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
    cfg.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
    return new TemplateRenderer(cfg, new StringTemplateLoader());
  }
}
