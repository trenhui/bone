package com.bone.smartmeta.engine.test;

import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.config.SmartMetaProperties;
import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.FieldMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MetadataEngine单元测试
 */
@SpringBootTest
public class MetadataEngineTest {

    private MetadataEngine metadataEngine;
    
    @BeforeEach
    void setUp() {
        SmartMetaProperties properties = new SmartMetaProperties();
        metadataEngine = new MetadataEngine(properties);
    }
    
    @Test
    void testRegisterAndGetEntityMetadata() {
        // 创建测试实体元数据
        EntityMetadata entityMetadata = createTestEntityMetadata();
        
        // 注册元数据
        metadataEngine.registerEntity(entityMetadata);
        
        // 获取元数据
        EntityMetadata retrieved = metadataEngine.getEntityMetadata("TestEntity");
        
        assertNotNull(retrieved);
        assertEquals("TestEntity", retrieved.getEntityName());
        assertEquals(2, retrieved.getFields().size());
    }
    
    @Test
    void testUpdateEntityMetadata() {
        // 先注册一个实体
        EntityMetadata entityMetadata = createTestEntityMetadata();
        metadataEngine.registerEntity(entityMetadata);
        
        // 更新实体
        entityMetadata.setDescription("Updated Description");
        metadataEngine.registerEntity(entityMetadata);
        
        // 验证更新
        EntityMetadata updated = metadataEngine.getEntityMetadata("TestEntity");
        assertEquals("Updated Description", updated.getDescription());
    }
    
    @Test
    void testDeleteEntityMetadata() {
        // 先注册一个实体
        EntityMetadata entityMetadata = createTestEntityMetadata();
        metadataEngine.registerEntity(entityMetadata);
        
        // 删除实体
        metadataEngine.unregisterEntity("TestEntity");
        
        // 验证删除
        EntityMetadata deleted = metadataEngine.getEntityMetadata("TestEntity");
        assertNull(deleted);
    }
    
    @Test
    void testCalculateField() {
        // 注册包含计算字段的实体
        EntityMetadata entityMetadata = createEntityWithCalculatedField();
        metadataEngine.registerEntity(entityMetadata);
        
        // 创建测试数据
        Map<String, Object> data = new HashMap<>();
        data.put("price", 100);
        data.put("quantity", 5);
        
        // 计算字段值
        Map<String, Object> result = metadataEngine.processCalculatedFields("TestEntity", data);
        
        // 验证计算结果
        assertEquals(500, result.get("totalAmount"));
    }
    
    private EntityMetadata createTestEntityMetadata() {
        EntityMetadata metadata = new EntityMetadata();
        metadata.setEntityName("TestEntity");
        metadata.setDescription("Test Entity");
        metadata.setBusinessDomain("Test");
        
        // 添加字段
        FieldMetadata field1 = new FieldMetadata();
        field1.setFieldName("id");
        field1.setFieldType("Long");
        field1.setDescription("Primary Key");
        
        FieldMetadata field2 = new FieldMetadata();
        field2.setFieldName("name");
        field2.setFieldType("String");
        field2.setDescription("Entity Name");
        
        Map<String, FieldMetadata> fields = new HashMap<>();
        fields.put("id", field1);
        fields.put("name", field2);
        metadata.setFields(fields);
        
        return metadata;
    }
    
    private EntityMetadata createEntityWithCalculatedField() {
        EntityMetadata metadata = new EntityMetadata();
        metadata.setEntityName("TestEntity");
        
        // 添加基础字段
        FieldMetadata priceField = new FieldMetadata();
        priceField.setFieldName("price");
        priceField.setFieldType("Double");
        
        FieldMetadata quantityField = new FieldMetadata();
        quantityField.setFieldName("quantity");
        quantityField.setFieldType("Integer");
        
        // 添加计算字段
        FieldMetadata calculatedField = new FieldMetadata();
        calculatedField.setFieldName("totalAmount");
        calculatedField.setFieldType("Double");
        calculatedField.setCalculated(true);
        calculatedField.setExpression("price * quantity");
        
        Map<String, FieldMetadata> fields = new HashMap<>();
        fields.put("price", priceField);
        fields.put("quantity", quantityField);
        fields.put("totalAmount", calculatedField);
        metadata.setFields(fields);
        
        return metadata;
    }
    
    @TestConfiguration
    @EnableConfigurationProperties(SmartMetaProperties.class)
    @ComponentScan(basePackages = "com.bone.smartmeta.engine")
    static class TestConfig {
        @Bean
        public MetadataEngine metadataEngine(SmartMetaProperties properties) {
            return new MetadataEngine(properties);
        }
    }
}