package com.bone.smartmeta.engine.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * 简单的引擎测试类
 */
public class EngineTest {
    
    public static void main(String[] args) {
        // 测试TransformationEngine
        TransformationEngine transformationEngine = new TransformationEngine();
        Map<String, Object> testData = new HashMap<>();
        testData.put("name", "test");
        testData.put("value", 123);
        
        Map<String, Object> transformedData = transformationEngine.transformData("testEntity", testData);
        System.out.println("TransformationEngine test: " + transformedData);
        
        // 测试ExpressionEngine
        ExpressionEngine expressionEngine = new ExpressionEngine();
        Map<String, Object> calculatedFields = expressionEngine.calculateFields("testEntity", testData);
        System.out.println("ExpressionEngine test: " + calculatedFields);
        
        System.out.println("Engines initialized successfully!");
    }
}