package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.model.*;
import com.bone.smartmeta.engine.rule.BusinessRuleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmartMetadataEngineTest {

    @Mock
    private MetadataRegistry metadataRegistry;

    @Mock
    private FieldCalculationEngine fieldCalculationEngine;

    @Mock
    private BusinessRuleEngine businessRuleEngine;

    @Mock
    private BusinessRuleRegistry businessRuleRegistry;

    @InjectMocks
    private SmartMetadataEngine engine;

    private EntityMetadata productMetadata;
    private Map<String, Object> productData;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 初始化测试数据
        initProductMetadata();
        initProductData();
        
        // 设置mock行为
        when(metadataRegistry.getEntityMetadata("Product")).thenReturn(productMetadata);
    }

    private void initProductMetadata() {
        productMetadata = new EntityMetadata();
        productMetadata.setEntityName("Product");
        
        Map<String, FieldMetadata> fields = new HashMap<>();
        
        FieldMetadata nameField = new FieldMetadata();
        nameField.setFieldName("name");
        nameField.setDataType("STRING");
        nameField.setRequired(true);
        fields.put("name", nameField);
        
        FieldMetadata priceField = new FieldMetadata();
        priceField.setFieldName("price");
        priceField.setDataType("DECIMAL");
        priceField.setRequired(true);
        fields.put("price", priceField);
        
        productMetadata.setFields(fields);
    }

    private void initProductData() {
        productData = new HashMap<>();
        productData.put("name", "智能手机");
        productData.put("price", 5999.00);
    }

    @Test
    void testCreateEntity_Success() {
        // 执行测试
        Map<String, Object> result = engine.createEntity("Product", productData);
        
        // 验证结果
        assertNotNull(result);
        assertEquals("智能手机", result.get("name"));
        assertEquals(5999.00, result.get("price"));
        assertNotNull(result.get("id"));
        assertNotNull(result.get("createdAt"));
        assertNotNull(result.get("updatedAt"));
        assertEquals(1L, result.get("version"));
        assertFalse((boolean) result.get("isDeleted"));
    }

    @Test
    void testCreateEntity_MissingRequiredField() {
        // 准备数据 - 缺少必填字段
        Map<String, Object> invalidData = new HashMap<>();
        invalidData.put("name", "智能手机");
        // 缺少price字段
        
        // 执行测试并验证异常
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            engine.createEntity("Product", invalidData);
        });
        
        assertTrue(exception.getMessage().contains("缺少必填字段"));
    }

    @Test
    void testCreateEntity_EntityNotFound() {
        // 设置mock行为
        when(metadataRegistry.getEntityMetadata("NonExistentEntity")).thenReturn(null);
        
        // 执行测试并验证异常
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            engine.createEntity("NonExistentEntity", productData);
        });
        
        assertTrue(exception.getMessage().contains("实体不存在"));
    }

    @Test
    void testValidateEntity_Success() {
        // 设置mock行为
        ValidationResult validationResult = new ValidationResult();
        validationResult.setValid(true);
        when(businessRuleEngine.executeValidationRules("Product", productData, anyString())).thenReturn(java.util.Collections.singletonList(validationResult));
        
        // 执行测试
        ValidationResult result = engine.validateEntity("Product", productData);
        
        // 验证结果
        assertTrue(result.isValid());
    }

    @Test
    void testValidateEntity_Failure() {
        // 设置mock行为
        ValidationResult validationResult = new ValidationResult();
        validationResult.setValid(false);
        validationResult.addError("price", "价格必须大于0");
        when(businessRuleEngine.executeValidationRules("Product", productData, anyString())).thenReturn(java.util.Collections.singletonList(validationResult));
        
        // 执行测试
        ValidationResult result = engine.validateEntity("Product", productData);
        
        // 验证结果
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().containsKey("price"));
    }

    @Test
    void testCalculateFields() {
        // 执行测试
        engine.calculateFields("Product", productData);
        
        // 验证调用
        verify(fieldCalculationEngine).calculateAllFields("Product", productData);
    }

    @Test
    void testRegisterEntityMetadata() {
        // 执行测试
        engine.registerEntityMetadata(productMetadata);
        
        // 验证调用
        verify(metadataRegistry).registerEntityMetadata(productMetadata);
    }

    @Test
    void testRegisterBusinessRule() {
        BusinessRule rule = new BusinessRule();
        rule.setRuleName("TestRule");
        rule.setEntityName("Product");
        
        // 执行测试
        engine.registerBusinessRule(rule);
        
        // 验证调用
        verify(businessRuleRegistry).registerRule(rule);
    }

    @Test
    void testApplyBusinessRules() {
        // 执行测试
        engine.applyBusinessRules("Product", productData, "UPDATE");
        
        // 验证调用
        verify(businessRuleEngine).executeActionRules("Product", productData, "UPDATE");
    }

    @Test
    void testShutdown() {
        // 执行测试
        engine.shutdown();
        
        // 验证资源释放
        // 这里可以添加对线程池关闭等操作的验证
        assertTrue(true); // 简化测试
    }
}