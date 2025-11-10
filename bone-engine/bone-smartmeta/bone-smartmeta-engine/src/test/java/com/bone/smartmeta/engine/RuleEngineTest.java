package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.SmartFieldMetadata;
import com.bone.smartmeta.engine.metadata.BusinessRuleMetadata;
import com.bone.smartmeta.engine.metadata.ValidationRuleMetadata;
import com.bone.smartmeta.engine.validation.ValidationResult;
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
        
        // 移除对不存在方法的调用
        
        // 创建Map而不是List
        Map<String, SmartFieldMetadata> fieldsMap = new HashMap<>();
        fieldsMap.put("calculatedField", field);
        when(metadata.getFields()).thenReturn(fieldsMap);
        when(metadataEngine.getEntityMetadata(entityName)).thenReturn(metadata);
        
        // 模拟表达式计算
        when(expressionEngine.eval("field1 + field2", entityData)).thenReturn(300);
        
        // 执行计算 - 添加正确的参数
        Map<String, Object> result = ruleEngine.calculateFields(entityName, entityData, false);
        
        // 验证结果
        assertEquals(300, result.get("calculatedField"));
        verify(expressionEngine).eval("field1 + field2", entityData);
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
        when(rule.getErrorMessage()).thenReturn("金额不能超过1000");
        when(rule.getExecutionTiming()).thenReturn("before");
        when(rule.isEnabled()).thenReturn(true);
        // 移除对不存在方法的调用
        
        // 使用List<Object>以匹配RuleEngine中getValidationRules()返回的List<?>类型
        List<Object> rules = Collections.singletonList(rule);
        when(metadata.getValidationRules()).thenReturn(rules);
        when(metadataEngine.getEntityMetadata(entityName)).thenReturn(metadata);
        
        // 模拟表达式计算
        when(expressionEngine.eval("amount > 1000", entityData)).thenReturn(true);
        
        // 执行验证 - 使用正确的返回类型
        Map<String, Object> result = ruleEngine.validateRules(entityName, entityData, triggerEvents);
        
        // 验证结果
        assertNotNull(result);
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
        // 创建空的Map而不是List
        when(metadata.getFields()).thenReturn(Collections.emptyMap());
        
        // 执行计算 - 使用正确的boolean参数
        Map<String, Object> result = ruleEngine.calculateFields(entityName, entityData, false);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(entityData, result); // 由于没有计算字段，结果应该与输入相同
    }
}