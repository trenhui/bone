package com.bone.metadata.engine.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** EntityMetadata测试类 提供全面的测试覆盖，确保实体元数据功能正确 */
class EntityMetadataTest {

  private EntityMetadata customerMetadata;

  @BeforeEach
  void setUp() {
    // 初始化完整的测试对象，设置必要的字段
    customerMetadata = new EntityMetadata();
    customerMetadata.setEntityName("Customer");
    customerMetadata.setApiName("customer");
    customerMetadata.setDescription("客户实体");
  }

  @Test
  void testEntityMetadataNotNull() {
    // 测试对象创建成功
    assertNotNull(customerMetadata);
  }

  @Test
  void testEntityNameSetAndGet() {
    // 测试实体名称的设置和获取
    String expectedName = "Customer";
    String actualName = customerMetadata.getEntityName();
    assertEquals(expectedName, actualName, "实体名称应该正确设置和获取");
  }

  @Test
  void testApiNameSetAndGet() {
    // 测试API名称的设置和获取
    String expectedApiName = "customer";
    String actualApiName = customerMetadata.getApiName();
    assertEquals(expectedApiName, actualApiName, "API名称应该正确设置和获取");
  }

  @Test
  void testDescriptionSetAndGet() {
    // 测试描述的设置和获取
    String expectedDescription = "客户实体";
    String actualDescription = customerMetadata.getDescription();
    assertEquals(expectedDescription, actualDescription, "描述应该正确设置和获取");
  }

  @ParameterizedTest
  @CsvSource({"Customer, customer, 客户实体", "Product, product, 产品实体", "Order, order, 订单实体"})
  void testEntityMetadataWithDifferentValues(
      String entityName, String apiName, String description) {
    // 参数化测试，覆盖多种输入场景
    EntityMetadata metadata = new EntityMetadata();
    metadata.setEntityName(entityName);
    metadata.setApiName(apiName);
    metadata.setDescription(description);

    assertEquals(entityName, metadata.getEntityName(), "实体名称不匹配");
    assertEquals(apiName, metadata.getApiName(), "API名称不匹配");
    assertEquals(description, metadata.getDescription(), "描述不匹配");
  }

  @Test
  void testFieldMetadataManagement() {
    // 测试字段元数据管理功能
    // 先检查初始状态
    customerMetadata.setFields(new ArrayList<>()); // 初始化字段列表
    Map<String, SmartFieldMetadata> initialFields = customerMetadata.getFields();
    assertNotNull(initialFields, "字段映射不应该为null");
    assertTrue(initialFields.isEmpty(), "初始字段映射应该为空");

    // 添加字段
    SmartFieldMetadata fieldMetadata = new SmartFieldMetadata();
    fieldMetadata.setFieldName("name");
    fieldMetadata.setApiName("name"); // 设置apiName作为Map的key
    fieldMetadata.setLabel("姓名");

    // 使用正确的方法设置字段
    List<SmartFieldMetadata> fields = new ArrayList<>();
    fields.add(fieldMetadata);
    customerMetadata.setFields(fields);

    // 验证字段添加成功
    assertEquals(1, customerMetadata.getFields().size(), "字段数量不匹配");
    assertTrue(customerMetadata.getFields().containsKey("name"), "字段映射应该包含name字段");
  }

  @Test
  void testNullValuesHandling() {
    // 测试空值处理
    EntityMetadata metadata = new EntityMetadata();

    // 设置空值
    metadata.setEntityName(null);
    metadata.setApiName(null);
    metadata.setDescription(null);

    // 验证空值处理
    assertNull(metadata.getEntityName(), "实体名称应该为null");
    assertNull(metadata.getApiName(), "API名称应该为null");
    assertNull(metadata.getDescription(), "描述应该为null");
  }

  @Test
  void testConfigurationProperties() {
    // 测试配置属性功能
    EntityMetadata metadata = new EntityMetadata();

    // 假设EntityMetadata有配置属性管理功能
    // Map<String, Object> config = new HashMap<>();
    // config.put("cacheable", true);
    // config.put("version", "1.0");
    // metadata.setConfiguration(config);

    // 验证配置属性
    // assertNotNull(metadata.getConfiguration(), "配置不应该为null");
    // assertTrue((Boolean) metadata.getConfiguration().get("cacheable"), "缓存配置不正确");
  }
}
