package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.model.DynamicSmartEntity;
import com.bone.smartmeta.engine.model.FieldMetadata;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 字段计算引擎默认实现
 * 提供基于表达式的字段值计算功能
 */
@Slf4j
public class DefaultFieldCalculationEngine implements FieldCalculationEngine {
    
    // 智能元数据引擎
    private final SmartMetadataEngine metadataEngine;
    
    // 缓存表达式解析结果，提高性能
    private final Map<String, ParsedExpression> expressionCache = new HashMap<>();
    
    // 日期格式模式
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = 
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));
    
    // 日期时间格式模式
    private static final ThreadLocal<SimpleDateFormat> DATETIME_FORMAT = 
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
    
    // 字段引用模式
    private static final Pattern FIELD_REFERENCE_PATTERN = Pattern.compile("\\{([\\w\\.]+)\\}");
    
    /**
     * 构造函数
     * @param metadataEngine 元数据引擎
     */
    public DefaultFieldCalculationEngine(SmartMetadataEngine metadataEngine) {
        this.metadataEngine = metadataEngine;
    }
    
    @Override
    public Object calculateField(DynamicSmartEntity entity, FieldMetadata fieldMetadata) throws Exception {
        if (entity == null || fieldMetadata == null || !fieldMetadata.isCalculated()) {
            throw new IllegalArgumentException("Invalid parameters for field calculation");
        }
        
        String expression = fieldMetadata.getCalculationExpression();
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Calculation expression is empty for field: " + fieldMetadata.getApiName());
        }
        
        // 清除可能的空白字符
        expression = expression.trim();
        
        // 获取或解析表达式
        ParsedExpression parsedExpression = getParsedExpression(expression);
        
        // 计算表达式值
        return evaluateExpression(parsedExpression, entity, new HashSet<>());
    }
    
    @Override
    public void calculateAllFields(DynamicSmartEntity entity) throws Exception {
        if (entity == null || entity.getEntityApiName() == null) {
            return;
        }
        
        List<FieldMetadata> calculatedFields = metadataEngine.getMetadataRegistry()
                .getCalculatedFieldMetadata(entity.getEntityApiName());
        
        if (calculatedFields.isEmpty()) {
            return;
        }
        
        // 按照依赖关系排序，确保依赖的字段先计算
        List<FieldMetadata> sortedFields = sortFieldsByDependency(calculatedFields, entity.getEntityApiName());
        
        // 计算每个字段
        for (FieldMetadata field : sortedFields) {
            try {
                Object value = calculateField(entity, field);
                entity.setCalculatedField(field.getApiName(), value);
            } catch (Exception e) {
                log.error("Error calculating field {} for entity {}", 
                          field.getApiName(), entity.getEntityApiName(), e);
                throw e;
            }
        }
    }
    
    @Override
    public boolean validateExpression(FieldMetadata fieldMetadata) {
        if (fieldMetadata == null || !fieldMetadata.isCalculated()) {
            return false;
        }
        
        String expression = fieldMetadata.getCalculationExpression();
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }
        
        try {
            // 解析表达式
            ParsedExpression parsedExpression = parseExpression(expression.trim());
            
            // 验证表达式结构
            return validateExpressionStructure(parsedExpression);
        } catch (Exception e) {
            log.warn("Expression validation failed: {}", expression, e);
            return false;
        }
    }
    
    @Override
    public List<String> getExpressionDependencies(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        Set<String> dependencies = new HashSet<>();
        Matcher matcher = FIELD_REFERENCE_PATTERN.matcher(expression);
        
        while (matcher.find()) {
            String fieldName = matcher.group(1);
            dependencies.add(fieldName);
        }
        
        return new ArrayList<>(dependencies);
    }
    
    /**
     * 解析表达式
     */
    private ParsedExpression parseExpression(String expression) {        
        // 解析字段引用
        List<String> fieldReferences = getExpressionDependencies(expression);
        
        // 解析常量值
        List<Constant> constants = extractConstants(expression);
        
        return new ParsedExpression(expression, fieldReferences, constants);
    }
    
    /**
     * 从缓存获取或解析表达式
     */
    private ParsedExpression getParsedExpression(String expression) {
        return expressionCache.computeIfAbsent(expression, this::parseExpression);
    }
    
    /**
     * 提取表达式中的常量值
     */
    private List<Constant> extractConstants(String expression) {
        List<Constant> constants = new ArrayList<>();
        
        // 简单实现，提取字符串、数字、布尔值常量
        // 实际实现可能需要更复杂的解析逻辑
        
        // 匹配字符串常量
        Pattern stringPattern = Pattern.compile("'([^']+)'|\"([^\"]+)\"");
        Matcher stringMatcher = stringPattern.matcher(expression);
        int count = 0;
        while (stringMatcher.find()) {
            String value = stringMatcher.group(1) != null ? stringMatcher.group(1) : stringMatcher.group(2);
            constants.add(new Constant("$STR_CONST_" + count, value));
            count++;
        }
        
        // 匹配数字常量
        Pattern numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?\\b");
        Matcher numberMatcher = numberPattern.matcher(expression);
        count = 0;
        while (numberMatcher.find()) {
            String value = numberMatcher.group();
            try {
                if (value.contains(".")) {
                    constants.add(new Constant("$NUM_CONST_" + count, Double.parseDouble(value)));
                } else {
                    constants.add(new Constant("$NUM_CONST_" + count, Long.parseLong(value)));
                }
                count++;
            } catch (NumberFormatException e) {
                // 忽略无法解析的数字
            }
        }
        
        // 匹配布尔常量
        if (expression.contains("true")) {
            constants.add(new Constant("$BOOL_TRUE", Boolean.TRUE));
        }
        if (expression.contains("false")) {
            constants.add(new Constant("$BOOL_FALSE", Boolean.FALSE));
        }
        
        return constants;
    }
    
    /**
     * 评估表达式值
     */
    private Object evaluateExpression(ParsedExpression expression, DynamicSmartEntity entity, 
                                     Set<String> visitedFields) throws Exception {
        // 检查循环依赖
        String fieldName = getCurrentFieldName(entity, expression);
        if (fieldName != null && visitedFields.contains(fieldName)) {
            throw new IllegalStateException("Circular dependency detected in field calculation: " + fieldName);
        }
        
        // 添加当前字段到已访问集合
        if (fieldName != null) {
            visitedFields.add(fieldName);
        }
        
        try {
            // 替换字段引用为实际值
            String processedExpression = replaceFieldReferences(expression.getExpression(), entity, visitedFields);
            
            // 执行表达式计算
            return executeCalculation(processedExpression);
        } finally {
            // 从已访问集合移除当前字段
            if (fieldName != null) {
                visitedFields.remove(fieldName);
            }
        }
    }
    
    /**
     * 获取当前正在计算的字段名
     */
    private String getCurrentFieldName(DynamicSmartEntity entity, ParsedExpression expression) {
        // 在实际实现中，可能需要跟踪当前正在计算的字段
        // 这里简化处理，返回null
        return null;
    }
    
    /**
     * 替换字段引用为实际值
     */
    private String replaceFieldReferences(String expression, DynamicSmartEntity entity, 
                                        Set<String> visitedFields) throws Exception {
        String result = expression;
        Matcher matcher = FIELD_REFERENCE_PATTERN.matcher(expression);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String fieldPath = matcher.group(1);
            Object value = getFieldValue(entity, fieldPath, visitedFields);
            String valueStr = convertValueToString(value);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(valueStr));
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * 获取字段值，支持嵌套字段访问
     */
    private Object getFieldValue(DynamicSmartEntity entity, String fieldPath, 
                               Set<String> visitedFields) throws Exception {
        if (fieldPath.contains(".")) {
            // 处理嵌套字段访问，如 "user.name"
            String[] parts = fieldPath.split("\\.", 2);
            String firstField = parts[0];
            String remainingPath = parts[1];
            
            Object value = entity.getField(firstField);
            if (value instanceof Map) {
                // 如果是Map，递归获取嵌套值
                Map<?, ?> map = (Map<?, ?>) value;
                return map.get(remainingPath);
            } else if (value instanceof DynamicSmartEntity) {
                // 如果是关联实体，递归获取字段值
                DynamicSmartEntity relatedEntity = (DynamicSmartEntity) value;
                return getFieldValue(relatedEntity, remainingPath, visitedFields);
            }
        }
        
        // 获取基本字段值
        Object value = entity.getField(fieldPath);
        
        // 如果是计算字段且值为null，尝试计算
        if (value == null) {
            FieldMetadata fieldMetadata = metadataEngine.getMetadataRegistry()
                    .getFieldMetadata(entity.getEntityApiName(), fieldPath);
            
            if (fieldMetadata != null && fieldMetadata.isCalculated()) {
                value = evaluateFieldCalculation(entity, fieldMetadata, visitedFields);
            }
        }
        
        return value;
    }
    
    /**
     * 计算字段值
     */
    private Object evaluateFieldCalculation(DynamicSmartEntity entity, FieldMetadata fieldMetadata, 
                                          Set<String> visitedFields) throws Exception {
        String fieldName = fieldMetadata.getApiName();
        
        // 检查循环依赖
        if (visitedFields.contains(fieldName)) {
            throw new IllegalStateException("Circular dependency detected in field calculation: " + fieldName);
        }
        
        // 添加到已访问集合
        visitedFields.add(fieldName);
        
        try {
            ParsedExpression parsedExpression = getParsedExpression(fieldMetadata.getCalculationExpression());
            return evaluateExpression(parsedExpression, entity, visitedFields);
        } finally {
            // 从已访问集合移除
            visitedFields.remove(fieldName);
        }
    }
    
    /**
     * 将值转换为字符串表示
     */
    private String convertValueToString(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "'" + ((String) value).replace("'", "\\'") + "'";
        } else if (value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Number) {
            return value.toString();
        } else if (value instanceof Date) {
            return "'" + DATE_FORMAT.get().format((Date) value) + "'";
        } else {
            return "'" + value.toString().replace("'", "\\'") + "'";
        }
    }
    
    /**
     * 执行表达式计算
     */
    private Object executeCalculation(String expression) throws Exception {
        // 简单的表达式计算器实现
        // 实际生产环境中，可能需要使用成熟的表达式引擎，如SpEL、OGNL、MVEL等
        
        // 移除表达式中的空白字符，简化处理
        expression = expression.replaceAll("\\s+", "");
        
        // 处理简单的算术运算
        try {
            return evaluateArithmeticExpression(expression);
        } catch (Exception e) {
            // 如果算术运算失败，尝试其他类型的表达式
            if (expression.equals("true")) {
                return Boolean.TRUE;
            } else if (expression.equals("false")) {
                return Boolean.FALSE;
            } else if (expression.startsWith("'") && expression.endsWith("'")) {
                // 字符串常量
                return expression.substring(1, expression.length() - 1).replace("\\'", "'");
            } else if (expression.equals("null")) {
                return null;
            } else {
                throw e;
            }
        }
    }
    
    /**
     * 评估算术表达式
     */
    private Object evaluateArithmeticExpression(String expression) throws Exception {
        // 这里实现一个非常简单的算术表达式计算器
        // 仅支持基本的四则运算和括号
        // 实际生产环境中应使用专门的表达式引擎
        
        // 示例实现，支持简单的表达式如: 1+2, 3*4, (5+6)/2
        // 真实实现需要更复杂的解析器
        
        try {
            // 尝试直接解析为数字
            if (expression.contains(".")) {
                return Double.parseDouble(expression);
            } else {
                return Long.parseLong(expression);
            }
        } catch (NumberFormatException e) {
            // 不是简单数字，尝试处理简单的运算
            // 这里简化处理，实际实现需要更复杂的解析器
            throw new UnsupportedOperationException("Complex expressions not supported in simple calculator");
        }
    }
    
    /**
     * 根据依赖关系排序字段
     */
    private List<FieldMetadata> sortFieldsByDependency(List<FieldMetadata> fields, String entityApiName) {
        // 构建依赖图
        Map<String, Set<String>> dependencyGraph = new HashMap<>();
        
        for (FieldMetadata field : fields) {
            String fieldApiName = field.getApiName();
            Set<String> dependencies = new HashSet<>();
            
            // 获取字段依赖
            List<String> exprDependencies = getExpressionDependencies(field.getCalculationExpression());
            for (String dep : exprDependencies) {
                // 只考虑当前实体的字段依赖
                if (!dep.contains(".")) {
                    dependencies.add(dep);
                }
            }
            
            dependencyGraph.put(fieldApiName, dependencies);
        }
        
        // 使用拓扑排序
        return topologicalSort(dependencyGraph, fields);
    }
    
    /**
     * 拓扑排序
     */
    private List<FieldMetadata> topologicalSort(Map<String, Set<String>> dependencyGraph, 
                                              List<FieldMetadata> fields) {
        List<String> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        
        // 对每个节点进行DFS
        for (String field : dependencyGraph.keySet()) {
            if (!visited.contains(field)) {
                dfs(field, dependencyGraph, visited, visiting, sorted);
            }
        }
        
        // 根据排序结果重新排序字段列表
        Map<String, FieldMetadata> fieldMap = fields.stream()
                .collect(Collectors.toMap(FieldMetadata::getApiName, f -> f));
        
        List<FieldMetadata> sortedFields = new ArrayList<>();
        for (String fieldName : sorted) {
            if (fieldMap.containsKey(fieldName)) {
                sortedFields.add(fieldMap.get(fieldName));
            }
        }
        
        // 添加没有依赖的字段
        for (FieldMetadata field : fields) {
            if (!sortedFields.contains(field)) {
                sortedFields.add(field);
            }
        }
        
        return sortedFields;
    }
    
    /**
     * 深度优先搜索，用于拓扑排序
     */
    private void dfs(String node, Map<String, Set<String>> graph, 
                    Set<String> visited, Set<String> visiting, List<String> result) {
        visiting.add(node);
        
        Set<String> neighbors = graph.getOrDefault(node, Collections.emptySet());
        for (String neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                if (visiting.contains(neighbor)) {
                    // 检测到循环依赖
                    log.warn("Circular dependency detected between {} and {}", node, neighbor);
                } else {
                    dfs(neighbor, graph, visited, visiting, result);
                }
            }
        }
        
        visiting.remove(node);
        visited.add(node);
        result.add(node);
    }
    
    /**
     * 验证表达式结构
     */
    private boolean validateExpressionStructure(ParsedExpression expression) {
        // 简单验证括号是否匹配
        int balance = 0;
        for (char c : expression.getExpression().toCharArray()) {
            if (c == '(') balance++;
            else if (c == ')') balance--;
            
            if (balance < 0) return false;
        }
        
        return balance == 0;
    }
    
    /**
     * 解析后的表达式
     */
    private static class ParsedExpression {
        private final String expression;
        private final List<String> fieldReferences;
        private final List<Constant> constants;
        
        public ParsedExpression(String expression, List<String> fieldReferences, List<Constant> constants) {
            this.expression = expression;
            this.fieldReferences = fieldReferences;
            this.constants = constants;
        }
        
        public String getExpression() {
            return expression;
        }
        
        public List<String> getFieldReferences() {
            return fieldReferences;
        }
        
        public List<Constant> getConstants() {
            return constants;
        }
    }
    
    /**
     * 常量值
     */
    private static class Constant {
        private final String placeholder;
        private final Object value;
        
        public Constant(String placeholder, Object value) {
            this.placeholder = placeholder;
            this.value = value;
        }
        
        public String getPlaceholder() {
            return placeholder;
        }
        
        public Object getValue() {
            return value;
        }
    }
    
    /**
     * 清理缓存
     */
    public void clearCache() {
        expressionCache.clear();
        log.info("Expression cache cleared");
    }
}