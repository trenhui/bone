package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.SmartFieldMetadata;
import com.bone.smartmeta.engine.model.DynamicSmartEntity;
import com.bone.smartmeta.engine.model.FieldMetadata;
import com.bone.smartmeta.engine.repository.MetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class DefaultFieldCalculationEngineTest {

    @Mock
    private MetadataRepository metadataRepository;

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
        when(metadataRepository.findEntityByApiName("Order")).thenReturn(orderMetadata);
    }

    private void initOrderMetadata() {
        orderMetadata = new EntityMetadata();
        orderMetadata.setEntityName("Order");
        
        // 注意：这里我们不再设置字段映射，因为我们将直接创建FieldMetadata对象用于测试
    }
    
    // 创建用于测试的FieldMetadata对象
    private FieldMetadata createFieldMetadata(String fieldName, FieldMetadata.DataType dataType, boolean isCalculated, String calculationExpression) {
        FieldMetadata field = new FieldMetadata();
        field.setApiName(fieldName); // DefaultFieldCalculationEngine使用apiName
        field.setDataType(dataType);
        field.setCalculationExpression(calculationExpression); // 计算表达式
        return field;
    }
    
    // 创建DynamicSmartEntity对象
    private DynamicSmartEntity createDynamicSmartEntity(Map<String, Object> data, String entityType) {
        DynamicSmartEntity entity = new DynamicSmartEntity();
        // 设置实体类型
        entity.setField("entityType", entityType);
        // 设置所有字段值
        if (data != null) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                entity.setField(entry.getKey(), entry.getValue());
            }
        }
        return entity;
    }

    private void initOrderData() {
        orderData = new HashMap<>();
        orderData.put("quantity", 5);
        orderData.put("unitPrice", 100.0);
        orderData.put("taxRate", 10.0);
    }

    @Test
    void testCalculateField_SingleField() {
        // 创建DynamicSmartEntity对象
        DynamicSmartEntity entity = createDynamicSmartEntity(orderData, "Order");
        
        // 创建字段元数据
        FieldMetadata subtotalField = createFieldMetadata("subtotal", FieldMetadata.DataType.DECIMAL, true, "quantity * unitPrice");
        
        // 执行测试 - 计算单个字段
        Object result = calculationEngine.calculateField(entity, subtotalField);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(500.0, result);
    }

    @Test
    void testCalculateAllFields_DependencyChain() {
        // 创建DynamicSmartEntity对象
        DynamicSmartEntity entity = createDynamicSmartEntity(orderData, "Order");
        
        // 执行测试 - 计算所有字段
        calculationEngine.calculateAllFields(entity);
        
        // 验证结果 - 检查依赖链计算是否正确
        assertEquals(500.0, entity.getField("subtotal")); // 5 * 100
        assertEquals(50.0, entity.getField("tax"));      // 500 * 0.1
        assertEquals(550.0, entity.getField("total"));    // 500 + 50
    }

    @Test
    void testValidateExpression_Success() {
        // 准备有效的表达式
        FieldMetadata fieldMetadata = createFieldMetadata("subtotal", FieldMetadata.DataType.DECIMAL, true, "quantity * unitPrice");
        
        // 执行测试
        boolean result = calculationEngine.validateExpression(fieldMetadata);
        
        // 验证结果
        assertTrue(result);
    }

    @Test
    void testValidateExpression_Failure() {
        // 准备无效的表达式
        FieldMetadata fieldMetadata = createFieldMetadata("subtotal", FieldMetadata.DataType.DECIMAL, true, "quantity * unitPrice +");
        
        // 执行测试
        boolean result = calculationEngine.validateExpression(fieldMetadata);
        
        // 验证结果
        assertFalse(result);
    }

    @Test
    void testExpressionDependencies() {
        // 准备表达式
        String expression = "quantity * unitPrice + tax";
        
        // 执行测试
        List<String> dependencies = calculationEngine.getExpressionDependencies(expression);
        
        // 验证结果
        assertNotNull(dependencies);
        assertTrue(dependencies.contains("quantity"));
        assertTrue(dependencies.contains("unitPrice"));
        assertTrue(dependencies.contains("tax"));
    }

    @Test
    void testCalculateField_EntityNotFound() {
        // 创建DynamicSmartEntity对象
        DynamicSmartEntity entity = createDynamicSmartEntity(orderData, "NonExistentEntity");
        
        // 创建字段元数据
        FieldMetadata subtotalField = createFieldMetadata("subtotal", FieldMetadata.DataType.DECIMAL, true, "quantity * unitPrice");
        
        // 执行测试 - 由于metadataEngine为null，可能不会抛出预期的异常
        // 但我们仍然可以测试计算功能
        Exception exception = assertThrows(Exception.class, () -> {
            calculationEngine.calculateField(entity, subtotalField);
        });
    }

    @Test
    void testCalculateField_FieldNotFound() {
        // 创建DynamicSmartEntity对象
        DynamicSmartEntity entity = createDynamicSmartEntity(orderData, "Order");
        
        // 创建字段元数据
        FieldMetadata nonExistentField = createFieldMetadata("nonExistentField", FieldMetadata.DataType.DECIMAL, true, "1 + 1");
        
        // 执行测试并验证异常
        Exception exception = assertThrows(Exception.class, () -> {
            calculationEngine.calculateField(entity, nonExistentField);
        });
    }

    @Test
    void testCalculateField_NonCalculatedField() {
        // 创建DynamicSmartEntity对象
        DynamicSmartEntity entity = createDynamicSmartEntity(orderData, "Order");
        
        // 创建非计算字段元数据
        FieldMetadata quantityField = createFieldMetadata("quantity", FieldMetadata.DataType.INTEGER, false, null);
        
        // 执行测试并验证异常
        Exception exception = assertThrows(Exception.class, () -> {
            calculationEngine.calculateField(entity, quantityField);
        });
    }

    @Test
    void testCalculateAllFields_EmptyData() {
        // 准备空数据
        Map<String, Object> emptyData = new HashMap<>();
        DynamicSmartEntity entity = createDynamicSmartEntity(emptyData, "Order");
        
        // 执行测试 - 应该不会抛出异常
        assertDoesNotThrow(() -> {
            calculationEngine.calculateAllFields(entity);
        });
    }
}