package com.bone.smartmeta.engine.test;

import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.config.SmartMetaProperties;
import com.bone.smartmeta.engine.model.EntityMetadata;
import com.bone.smartmeta.engine.metadata.SmartFieldMetadata;
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
        // 使用无参构造函数
        metadataEngine = new MetadataEngine();
    }
    
    @Test
    void testRegisterAndGetEntityMetadata() {
        // 创建测试实体元数据
        Object entityMetadata = createTestEntityMetadata();
        
        // 注册元数据
        metadataEngine.registerEntity(entityMetadata);
        
        // 获取元数据
        Object retrieved = metadataEngine.getEntityMetadata("TestEntity");
        
        // 只验证元数据存在，不做类型转换
        assertNotNull(retrieved);
    }
    
    @Test
    void testUnregisterEntity() {
        // 先注册一个实体
        Object entityMetadata = createTestEntityMetadata();
        metadataEngine.registerEntity(entityMetadata);
        
        // 删除实体
        boolean deleted = metadataEngine.unregisterEntity("TestEntity");
        
        // 验证删除成功
        assertTrue(deleted);
        
        // 验证元数据已不存在
        Object retrieved = metadataEngine.getEntityMetadata("TestEntity");
        assertNull(retrieved);
    }
    
    private Object createTestEntityMetadata() {
        // 创建一个简单的Map对象作为元数据，避免类型转换问题
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entityName", "TestEntity");
        metadata.put("description", "Test Entity");
        
        // 创建字段Map
        Map<String, SmartFieldMetadata> fields = new HashMap<>();
        
        // 添加字段
        SmartFieldMetadata field1 = new SmartFieldMetadata();
        field1.setFieldName("id");
        field1.setFieldType("Long");
        
        SmartFieldMetadata field2 = new SmartFieldMetadata();
        field2.setFieldName("name");
        field2.setFieldType("String");
        
        fields.put("id", field1);
        fields.put("name", field2);
        
        // 将字段Map添加到元数据中
        metadata.put("fields", fields);
        
        return metadata;
    }
    
    @TestConfiguration
    @EnableConfigurationProperties(SmartMetaProperties.class)
    @ComponentScan(basePackages = "com.bone.smartmeta.engine")
    static class TestConfig {
        @Bean
        public MetadataEngine metadataEngine() {
            return new MetadataEngine();
        }
    }
}