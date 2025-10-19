package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.SmartFieldMetadata;
import com.bone.smartmeta.engine.metadata.BusinessRuleMetadata;
import com.bone.smartmeta.engine.metadata.ValidationRuleMetadata;
import com.bone.smartmeta.engine.rule.CustomFunctionRegistry;
import com.bone.smartmeta.engine.rule.EvaluationContextFactory;
import com.bone.smartmeta.engine.rule.ExpressionCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RuleEngine测试类
 */
class RuleEngineTest {

    @Mock
    private ExpressionEngine expressionEngine;
    
    @Mock
    private MetadataEngine metadataEngine;
    
    @Mock
    private ExpressionCache expressionCache;
    
    @Mock
    private EvaluationContextFactory contextFactory;
    
    @Mock
    private CustomFunctionRegistry functionRegistry;
    
    @Mock
    private EvaluationContextFactory.EvaluationContext evaluationContext;
    
    private RuleEngine ruleEngine;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 模拟上下文创建
        when(contextFactory.createContext(anyMap(), any())).thenReturn(evaluationContext);
        
        // 模拟表达式缓存
        when(expressionCache.get(anyString(), any())).thenAnswer(invocation -> {
            String expression = invocation.getArgument(0);
            return expression;
        });
        
        ruleEngine = new RuleEngine(expressionEngine, metadataEngine, 
                                  expressionCache, contextFactory, functionRegistry);
    }
    
    @Test
    void testCalculateFieldsSuccess() {
        // 准备测试数据
        String entityName = "TestEntity";
        Map<String, Object> entityData = new HashMap<>();
        entityData.put("field1", 100);
        entityData.put("field2", 200);
        
        // 准备元数据
        EntityMetadata metadata = mock(EntityMetadata.class);
        SmartFieldMetadata field = mock(SmartFieldMetadata.class);
        
        when(field.getName()).thenReturn("calculatedField");
        when(field.getType()).thenReturn("calculated");
        when(field.getExpression()).thenReturn("field1 + field2");
        
        List<SmartFieldMetadata> fields = Collections.singletonList(field);
        when(metadata.getFields()).thenReturn(fields);
        when(metadataEngine.getEntityMetadata(entityName)).thenReturn(metadata);
        
        // 模拟表达式计算
        when(expressionEngine.evaluate("field1 + field2", entityData)).thenReturn(300);
        
        // 执行计算
        Map<String, Object> result = ruleEngine.calculateFields(entityName, entityData, null);
        
        // 验证结果
        assertEquals(300, result.get("calculatedField"));
        verify(expressionEngine).evaluate("field1 + field2", entityData);
        verify(evaluationContext).updateEntityData("calculatedField", 300);
    }
    
    @Test
    void testValidateRulesSuccess() {
        // 准备测试数据
        String entityName = "TestEntity";
        Map<String, Object> entityData = new HashMap<>();
        entityData.put("amount", 1500);
        
        List<String> triggerEvents = Collections.singletonList("save");
        
        // 准备元数据
        EntityMetadata metadata = mock(EntityMetadata.class);
        BusinessRuleMetadata rule = mock(BusinessRuleMetadata.class);
        
        when(rule.getName()).thenReturn("amountValidation");
        when(rule.getCondition()).thenReturn("amount > 1000");
        when(rule.getType()).thenReturn("validation");
        when(rule.getField()).thenReturn("amount");
        when(rule.getErrorMessage()).thenReturn("金额不能超过1000");
        when(rule.getTriggerEvents()).thenReturn(Collections.singletonList("save"));
        when(rule.getExecutionTiming()).thenReturn("before");
        when(rule.isEnabled()).thenReturn(true);
        
        List<BusinessRuleMetadata> rules = Collections.singletonList(rule);
        when(metadata.getValidationRules()).thenReturn(rules);
        when(metadataEngine.getEntityMetadata(entityName)).thenReturn(metadata);
        
        // 模拟表达式计算
        when(expressionEngine.evaluate("amount > 1000", entityData)).thenReturn(true);
        
        // 执行验证
        ValidationResult result = ruleEngine.validateRules(entityName, entityData, triggerEvents);
        
        // 验证结果
        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        assertEquals("金额不能超过1000", result.getErrors().get("amount").get(0));
    }
    
    @Test
    void testRegisterBuiltInFunctions() {
        // 验证内置函数注册
        verify(functionRegistry).registerFunction("isNull", any(), anyString());
        verify(functionRegistry).registerFunction("isNotNull", any(), anyString());
        verify(functionRegistry).registerFunction("isEmpty", any(), anyString());
        verify(functionRegistry).registerFunction("isNotEmpty", any(), anyString());
        verify(functionRegistry).registerFunction("length", any(), anyString());
    }
    
    @Test
    void testCalculateFieldsWithSpecificFields() {
        // 准备测试数据 - 只计算特定字段
        String entityName = "TestEntity";
        Map<String, Object> entityData = new HashMap<>();
        entityData.put("value1", 10);
        entityData.put("value2", 20);
        
        List<String> fieldNames = Collections.singletonList("fieldToCalculate");
        
        // 验证方法能够接受指定字段列表
        EntityMetadata metadata = mock(EntityMetadata.class);
        when(metadataEngine.getEntityMetadata(entityName)).thenReturn(metadata);
        when(metadata.getFields()).thenReturn(Collections.emptyList());
        
        // 执行计算 - 应该成功但没有实际字段计算
        Map<String, Object> result = ruleEngine.calculateFields(entityName, entityData, fieldNames);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(entityData, result); // 由于没有计算字段，结果应该与输入相同
    }
}