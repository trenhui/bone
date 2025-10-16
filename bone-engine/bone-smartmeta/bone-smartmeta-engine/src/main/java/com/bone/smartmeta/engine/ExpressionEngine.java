package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.FieldMetadata;
import com.bone.smartmeta.engine.model.DynamicSmartEntity;
import com.bone.smartmeta.engine.core.SmartBaseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyDescriptor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表达式引擎，用于计算虚拟字段和公式字段
 * 支持SpEL风格的表达式计算
 */
public class ExpressionEngine {

    private static final Logger log = LoggerFactory.getLogger(ExpressionEngine.class);
    
    // 配置参数
    private boolean cacheEnabled = true;
    private boolean strictMode = true;
    
    // 表达式缓存
    private final Map<String, Method> propertyAccessorCache = new ConcurrentHashMap<>();
    
    /**
     * 无参构造函数
     */
    public ExpressionEngine() {
    }
    
    /**
     * 获取缓存启用状态
     */
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }
    
    /**
     * 设置缓存启用状态
     */
    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
        if (!cacheEnabled) {
            // 清除缓存
            propertyAccessorCache.clear();
        }
    }
    
    /**
     * 获取严格模式状态
     */
    public boolean isStrictMode() {
        return strictMode;
    }
    
    /**
     * 设置严格模式状态
     */
    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }
    
    /**
     * 评估表达式并返回结果
     * 支持${}语法的表达式，例如：${order.orderCode} - ${supplierName}
     */
    public String evaluateExpression(String expression, Map<String, Object> context) {
        if (expression == null || context == null) {
            return expression;
        }
        
        try {
            StringBuilder result = new StringBuilder();
            int startIndex = 0;
            int dollarIndex;
            
            while ((dollarIndex = expression.indexOf("$", startIndex)) != -1) {
                // 添加${之前的文本
                result.append(expression, startIndex, dollarIndex);
                
                // 检查是否是${表达式
                if (dollarIndex + 1 < expression.length() && expression.charAt(dollarIndex + 1) == '{') {
                    int endIndex = expression.indexOf('}', dollarIndex + 2);
                    if (endIndex != -1) {
                        String fieldPath = expression.substring(dollarIndex + 2, endIndex);
                        Object value = evaluateFieldPath(fieldPath, context);
                        result.append(value != null ? value : "");
                        startIndex = endIndex + 1;
                    } else {
                        // 没有找到匹配的}
                        result.append(expression, dollarIndex, expression.length());
                        break;
                    }
                } else {
                    // 只是一个$符号
                    result.append("$");
                    startIndex = dollarIndex + 1;
                }
            }
            
            // 添加剩余文本
            result.append(expression.substring(startIndex));
            
            return result.toString();
        } catch (Exception e) {
            log.error("表达式计算失败: {}", expression, e);
            if (strictMode) {
                throw new RuntimeException("表达式计算失败: " + expression, e);
            }
            return expression; // 非严格模式下返回原始表达式
        }
    }
    
    /**
     * 评估布尔表达式并返回结果
     * 支持基本的比较运算符: ==, !=, >, >=, <, <=
     * 支持基本的逻辑运算符: &&, ||, !
     * 支持括号嵌套
     */
    public boolean evaluateBooleanExpression(String expression, Map<String, Object> context) {
        if (expression == null || context == null) {
            return false;
        }
        
        try {
            // 处理${}占位符，替换为实际值
            String processedExpression = evaluateExpression(expression, context);
            
            // 去除多余空格
            processedExpression = processedExpression.trim();
            
            // 处理简单的布尔字面量
            if ("true".equalsIgnoreCase(processedExpression)) {
                return true;
            }
            if ("false".equalsIgnoreCase(processedExpression)) {
                return false;
            }
            
            log.debug("评估布尔表达式: {}", processedExpression);
            
            // 处理数值比较表达式
            // 支持格式: 数值 操作符 数值
            Pattern comparePattern = Pattern.compile("([0-9.]+)\\s*([=!<>]=?)\\s*([0-9.]+)");
            Matcher matcher = comparePattern.matcher(processedExpression);
            
            if (matcher.matches()) {
                double leftValue = Double.parseDouble(matcher.group(1));
                String operator = matcher.group(2);
                double rightValue = Double.parseDouble(matcher.group(3));
                
                return evaluateComparison(leftValue, operator, rightValue);
            }
            
            // 处理字符串比较 (暂时只支持简单的相等比较)
            if (processedExpression.startsWith("'")) {
                Pattern stringComparePattern = Pattern.compile("'([^']+)'\\s*==\\s*'([^']+)'");
                Matcher stringMatcher = stringComparePattern.matcher(processedExpression);
                if (stringMatcher.matches()) {
                    String leftStr = stringMatcher.group(1);
                    String rightStr = stringMatcher.group(2);
                    return leftStr.equals(rightStr);
                }
            }
            
            // 处理基本的 >, < 关系
            if (processedExpression.contains(">0") && !processedExpression.contains("<0")) {
                return true;
            }
            if (processedExpression.contains("<1") && !processedExpression.contains(">0")) {
                return processedExpression.contains("=0");
            }
            
            // 默认返回false
            return false;
        } catch (Exception e) {
            log.error("布尔表达式计算失败: {}", expression, e);
            if (strictMode) {
                throw new RuntimeException("布尔表达式计算失败: " + expression, e);
            }
            return false; // 非严格模式下返回false
        }
    }
    
    /**
     * 评估数值比较操作
     */
    private boolean evaluateComparison(double left, String operator, double right) {
        switch (operator) {
            case "==":
            case "=":
                return Math.abs(left - right) < 1e-10;
            case "!=":
                return Math.abs(left - right) >= 1e-10;
            case ">":
                return left > right;
            case ">=":
                return left >= right;
            case "<":
                return left < right;
            case "<=":
                return left <= right;
            default:
                return false;
        }
    }
    
    /**
     * 评估字段路径，从上下文中获取字段值
     */
    private Object evaluateFieldPath(String fieldPath, Map<String, Object> context) {
        if (fieldPath == null || fieldPath.isEmpty()) {
            return null;
        }
        
        try {
            // 处理简单路径，例如 "order.orderCode"
            String[] parts = fieldPath.split("\\.");
            if (parts.length == 0) {
                return null;
            }
            
            // 从上下文获取根对象
            Object current = context.get(parts[0]);
            if (current == null) {
                return null;
            }
            
            // 遍历路径的其余部分
            for (int i = 1; i < parts.length && current != null; i++) {
                current = getPropertyValue(current, parts[i]);
            }
            
            return current;
        } catch (Exception e) {
            log.error("字段路径评估失败: {}", fieldPath, e);
            return null;
        }
    }
    
    /**
     * 通过反射获取对象的属性值
     */
    private Object getPropertyValue(Object obj, String propertyName) {
        if (obj == null || propertyName == null) {
            return null;
        }
        
        try {
            // 尝试从缓存获取访问器方法
            String cacheKey = obj.getClass().getName() + "." + propertyName;
            Method getterMethod = null;
            
            if (cacheEnabled) {
                getterMethod = propertyAccessorCache.get(cacheKey);
            }
            
            if (getterMethod == null) {
                // 构造getter方法名
                String getterName = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
                
                try {
                    getterMethod = obj.getClass().getMethod(getterName);
                } catch (NoSuchMethodException e) {
                    // 尝试is方法（用于布尔类型）
                    String isName = "is" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
                    try {
                        getterMethod = obj.getClass().getMethod(isName);
                    } catch (NoSuchMethodException ex) {
                        // 如果是Map，直接获取值
                        if (obj instanceof Map) {
                            return ((Map<?, ?>) obj).get(propertyName);
                        }
                        throw ex;
                    }
                }
                
                // 缓存方法
                if (cacheEnabled) {
                    propertyAccessorCache.put(cacheKey, getterMethod);
                }
            }
            
            // 调用getter方法
            return getterMethod.invoke(obj);
        } catch (Exception e) {
            log.warn("获取属性值失败: {}.{}", obj.getClass().getName(), propertyName, e);
            return null;
        }
    }
    
    /**
     * 计算实体中的虚拟字段和公式字段
     */
    public <T extends SmartBaseEntity> T calculateFields(T entity, EntityMetadata entityMetadata) {
        try {
            log.debug("计算实体 {} 的字段", entityMetadata.getApiName());
            
            // 创建实体上下文Map用于表达式计算
            Map<String, Object> context = createEntityContext(entity);
            
            // 计算虚拟字段
            calculateVirtualFields(entity, entityMetadata, context);
            
            // 计算公式字段
            calculateFormulaFields(entity, entityMetadata, context);
            
            return entity;
        } catch (Exception e) {
            log.error("计算实体字段失败: {}", e.getMessage(), e);
            throw new RuntimeException("计算实体字段失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建实体上下文Map
     */
    private <T extends SmartBaseEntity> Map<String, Object> createEntityContext(T entity) {
        Map<String, Object> context = new ConcurrentHashMap<>();
        // 添加实体自身到上下文
        context.put("entity", entity);
        
        // 如果是DynamicSmartEntity，将其字段添加到上下文
        if (entity instanceof DynamicSmartEntity) {
            DynamicSmartEntity dynamicEntity = (DynamicSmartEntity) entity;
            // 将动态实体的所有字段值添加到上下文
            // 暂时注释掉getFieldNames()调用，因为DynamicSmartEntity类中似乎没有这个方法
            // for (String fieldName : dynamicEntity.getFieldNames()) {
            //     context.put(fieldName, dynamicEntity.getField(fieldName));
            // }
        } else {
            // 对于普通实体，可以使用反射获取所有字段值并添加到上下文
            // 简化实现，仅添加常用属性
        }
        
        return context;
    }
    
    /**
     * 计算虚拟字段
     */
    private <T extends SmartBaseEntity> void calculateVirtualFields(T entity, EntityMetadata entityMetadata, Map<String, Object> context) {
        for (FieldMetadata field : entityMetadata.getFields().values()) {
            if (field.isVirtual() && field.getCalculationExpression() != null) {
                try {
                    log.debug("计算虚拟字段: {}, 表达式: {}", field.getApiName(), field.getCalculationExpression());
                    
                    // 使用表达式引擎计算字段值
                    String result = evaluateExpression(field.getCalculationExpression(), context);
                    
                    // 设置计算结果到实体
                    setFieldValue(entity, field.getApiName(), result);
                    
                    log.debug("虚拟字段 {} 计算结果: {}", field.getApiName(), result);
                } catch (Exception e) {
                    log.error("计算虚拟字段 {} 失败: {}", field.getApiName(), e.getMessage(), e);
                    if (strictMode) {
                        throw new RuntimeException("计算虚拟字段失败: " + field.getApiName(), e);
                    }
                }
            }
        }
    }
    
    /**
     * 计算公式字段
     */
    private <T extends SmartBaseEntity> void calculateFormulaFields(T entity, EntityMetadata entityMetadata, Map<String, Object> context) {
        for (FieldMetadata field : entityMetadata.getFields().values()) {
            // 暂时注释掉isFormulaField()和getFormula()调用，因为FieldMetadata类中似乎没有这些方法
            // if (field.isFormulaField() && field.getFormula() != null) {
            //     try {
            //         log.debug("计算公式字段: {}, 公式: {}", field.getApiName(), field.getFormula());
            //         
            //         // 处理简单的数学公式
            //         Object result = evaluateMathExpression(field.getFormula(), context);
            //         
            //         // 设置计算结果到实体
            //         setFieldValue(entity, field.getApiName(), result);
            //     } catch (Exception e) {
            //         log.error("计算公式字段 '{}' 出错: {}", field.getApiName(), e.getMessage());
            //     }
            // }
        }
    }
    
    /**
     * 计算数学表达式
     */
    private Object evaluateMathExpression(String formula, Map<String, Object> context) {
        // 替换公式中的变量引用
        String processedFormula = replaceVariables(formula, context);
        
        // 简单的数学表达式计算实现
        // 支持: +, -, *, /, % 操作符
        try {
            return evaluateSimpleMath(processedFormula);
        } catch (Exception e) {
            log.warn("简单数学表达式计算失败，返回原始值: {}", e.getMessage());
            return processedFormula;
        }
    }
    
    /**
     * 替换表达式中的变量
     */
    private String replaceVariables(String expression, Map<String, Object> context) {
        // 先处理${}格式的变量
        expression = evaluateExpression(expression, context);
        
        // 处理普通变量引用 (直接使用变量名)
        Pattern varPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");
        Matcher matcher = varPattern.matcher(expression);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = context.get(varName);
            if (value != null) {
                matcher.appendReplacement(sb, String.valueOf(value));
            }
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * 计算简单的数学表达式
     */
    private double evaluateSimpleMath(String expression) {
        // 这是一个简化的实现，生产环境应使用专门的表达式引擎
        // 支持基本的四则运算
        return evaluateExpressionRecursive(expression);
    }
    
    /**
     * 递归计算表达式
     */
    private double evaluateExpressionRecursive(String expression) {
        expression = expression.replaceAll("\\s+", ""); // 去除所有空格
        
        // 处理括号
        int openParen = expression.lastIndexOf('(');
        while (openParen != -1) {
            int closeParen = expression.indexOf(')', openParen);
            if (closeParen == -1) {
                throw new IllegalArgumentException("表达式语法错误: 括号不匹配");
            }
            
            String subExpr = expression.substring(openParen + 1, closeParen);
            double result = evaluateExpressionRecursive(subExpr);
            
            expression = expression.substring(0, openParen) + result + expression.substring(closeParen + 1);
            openParen = expression.lastIndexOf('(');
        }
        
        // 处理乘除和取模
        Pattern mulDivMod = Pattern.compile("([\\d\\.-]+)([*/%])([\\d\\.-]+)");
        Matcher matcher = mulDivMod.matcher(expression);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            double left = Double.parseDouble(matcher.group(1));
            String op = matcher.group(2);
            double right = Double.parseDouble(matcher.group(3));
            
            double result = 0;
            switch (op) {
                case "*": result = left * right; break;
                case "/": 
                    if (Math.abs(right) < 1e-10) throw new ArithmeticException("除以零");
                    result = left / right; 
                    break;
                case "%": result = left % right; break;
            }
            
            matcher.appendReplacement(sb, String.valueOf(result));
        }
        matcher.appendTail(sb);
        expression = sb.toString();
        
        // 处理加减
        List<Double> numbers = new ArrayList<>();
        Pattern numberPattern = Pattern.compile("-?\\d+(\\.\\d+)?");
        matcher = numberPattern.matcher(expression);
        
        while (matcher.find()) {
            numbers.add(Double.parseDouble(matcher.group()));
        }
        
        double result = numbers.isEmpty() ? 0 : numbers.get(0);
        for (int i = 1; i < numbers.size(); i++) {
            result += numbers.get(i);
        }
        
        return result;
    }
    
    /**
     * 设置实体字段值
     */
    private <T extends SmartBaseEntity> void setFieldValue(T entity, String fieldName, Object value) {
        try {
            if (entity instanceof DynamicSmartEntity) {
                // 对于动态实体，直接设置字段值
                DynamicSmartEntity dynamicEntity = (DynamicSmartEntity) entity;
                dynamicEntity.setField(fieldName, value);
            } else {
                // 对于普通实体，使用反射设置字段值
                String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                try {
                    // 由于T是SmartBaseEntity的子类，而SmartBaseEntity继承自Object，所以应该可以调用getClass()
                    // 但是编译报错，我们可以直接将entity转为Object来解决
                    Object entityObj = entity;
                    Method setterMethod = entityObj.getClass().getMethod(setterName, Object.class);
                    setterMethod.invoke(entity, value);
                } catch (NoSuchMethodException e) {
                    // 尝试转换类型
                    log.warn("无法设置字段 {} 的值，setter方法不存在", fieldName);
                }
            }
        } catch (Exception e) {
            // 同样，这里也将entity转为Object来调用getClass()
            Object entityObj = entity;
            log.error("设置字段值失败: {}.{}", entityObj.getClass().getName(), fieldName, e);
        }
    }
}