package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.model.EntityMetadata;
import com.bone.smartmeta.engine.model.FieldMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class DefaultFieldCalculationEngineTest {

    @Mock
    private MetadataRegistry metadataRegistry;

    @InjectMocks
    private DefaultFieldCalculationEngine calculationEngine;

    private EntityMetadata orderMetadata;
    private Map<String, Object> orderData;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 初始化测试数据
        initOrderMetadata();
        initOrderData();
        
        // 设置mock行为
        when(metadataRegistry.getEntityMetadata("Order")).thenReturn(orderMetadata);
    }

    private void initOrderMetadata() {
        orderMetadata = new EntityMetadata();
        orderMetadata.setEntityName("Order");
        
        Map<String, FieldMetadata> fields = new HashMap<>();
        
        // 基础字段
        FieldMetadata quantityField = new FieldMetadata();
        quantityField.setFieldName("quantity");
        quantityField.setDataType("INTEGER");
        fields.put("quantity", quantityField);
        
        FieldMetadata unitPriceField = new FieldMetadata();
        unitPriceField.setFieldName("unitPrice");
        unitPriceField.setDataType("DECIMAL");
        fields.put("unitPrice", unitPriceField);
        
        FieldMetadata taxRateField = new FieldMetadata();
        taxRateField.setFieldName("taxRate");
        taxRateField.setDataType("DECIMAL");
        fields.put("taxRate", taxRateField);
        
        // 计算字段 - 小计
        FieldMetadata subtotalField = new FieldMetadata();
        subtotalField.setFieldName("subtotal");
        subtotalField.setDataType("DECIMAL");
        subtotalField.setCalculated(true);
        subtotalField.setCalculationExpression("quantity * unitPrice");
        fields.put("subtotal", subtotalField);
        
        // 计算字段 - 税额
        FieldMetadata taxField = new FieldMetadata();
        taxField.setFieldName("tax");
        taxField.setDataType("DECIMAL");
        taxField.setCalculated(true);
        taxField.setCalculationExpression("subtotal * (taxRate / 100)");
        fields.put("tax", taxField);
        
        // 计算字段 - 总计
        FieldMetadata totalField = new FieldMetadata();
        totalField.setFieldName("total");
        totalField.setDataType("DECIMAL");
        totalField.setCalculated(true);
        totalField.setCalculationExpression("subtotal + tax");
        fields.put("total", totalField);
        
        orderMetadata.setFields(fields);
    }

    private void initOrderData() {
        orderData = new HashMap<>();
        orderData.put("quantity", 5);
        orderData.put("unitPrice", 100.0);
        orderData.put("taxRate", 10.0);
    }

    @Test
    void testCalculateField_SingleField() {
        // 执行测试 - 计算单个字段
        Object result = calculationEngine.calculateField("Order", orderData, "subtotal");
        
        // 验证结果
        assertNotNull(result);
        assertEquals(500.0, result);
    }

    @Test
    void testCalculateAllFields_DependencyChain() {
        // 执行测试 - 计算所有字段
        calculationEngine.calculateAllFields("Order", orderData);
        
        // 验证结果 - 检查依赖链计算是否正确
        assertEquals(500.0, orderData.get("subtotal")); // 5 * 100
        assertEquals(50.0, orderData.get("tax"));      // 500 * 0.1
        assertEquals(550.0, orderData.get("total"));    // 500 + 50
    }

    @Test
    void testValidateFieldExpression_Valid() {
        // 执行测试 - 验证有效表达式
        boolean isValid = calculationEngine.validateFieldExpression("a + b");
        
        // 验证结果
        assertTrue(isValid);
    }

    @Test
    void testValidateFieldExpression_Invalid() {
        // 执行测试 - 验证无效表达式
        boolean isValid = calculationEngine.validateFieldExpression("a + (b"); // 缺少右括号
        
        // 验证结果
        assertFalse(isValid);
    }

    @Test
    void testGetExpressionDependencies() {
        // 执行测试 - 获取表达式依赖
        String expression = "quantity * unitPrice + discount";
        java.util.Set<String> dependencies = calculationEngine.getExpressionDependencies(expression);
        
        // 验证结果
        assertNotNull(dependencies);
        assertEquals(3, dependencies.size());
        assertTrue(dependencies.contains("quantity"));
        assertTrue(dependencies.contains("unitPrice"));
        assertTrue(dependencies.contains("discount"));
    }

    @Test
    void testCalculateField_EntityNotFound() {
        // 设置mock行为
        when(metadataRegistry.getEntityMetadata("NonExistentEntity")).thenReturn(null);
        
        // 执行测试并验证异常
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            calculationEngine.calculateField("NonExistentEntity", orderData, "subtotal");
        });
        
        assertTrue(exception.getMessage().contains("实体不存在"));
    }

    @Test
    void testCalculateField_FieldNotFound() {
        // 执行测试并验证异常
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            calculationEngine.calculateField("Order", orderData, "nonExistentField");
        });
        
        assertTrue(exception.getMessage().contains("字段不存在"));
    }

    @Test
    void testCalculateField_NonCalculatedField() {
        // 执行测试并验证异常
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            calculationEngine.calculateField("Order", orderData, "quantity"); // 非计算字段
        });
        
        assertTrue(exception.getMessage().contains("不是计算字段"));
    }

    @Test
    void testCalculateAllFields_EmptyData() {
        // 准备空数据
        Map<String, Object> emptyData = new HashMap<>();
        
        // 执行测试 - 应该不会抛出异常
        assertDoesNotThrow(() -> {
            calculationEngine.calculateAllFields("Order", emptyData);
        });
    }
}