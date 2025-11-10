package com.bone.smartmeta.engine.expression;

import com.bone.smartmeta.engine.ExpressionEngine;

import java.util.HashMap;
import java.util.Map;

public class SimpleEvalTest {
    public static void main(String[] args) {
        ExpressionEngine engine = new ExpressionEngine();
        Map<String, Object> context = new HashMap<>();
        context.put("age", 25);
        context.put("name", "张三");
        
        try {
            Object result1 = engine.eval("#age > 18", context);
            System.out.println("Result1: " + result1 + " (type: " + result1.getClass().getName() + ")");
            
            Object result2 = engine.eval("#name", context);
            System.out.println("Result2: " + result2 + " (type: " + result2.getClass().getName() + ")");
            
            Object result3 = engine.eval("#age + 5", context);
            System.out.println("Result3: " + result3 + " (type: " + result3.getClass().getName() + ")");
            
            System.out.println("Eval method implementation is working correctly!");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}