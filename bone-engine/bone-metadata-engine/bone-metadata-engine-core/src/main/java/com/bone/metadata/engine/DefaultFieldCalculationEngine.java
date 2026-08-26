package com.bone.metadata.engine;

import com.bone.metadata.engine.domain.exception.CalculationException;
import com.bone.metadata.engine.model.DynamicSmartEntity;
import com.bone.metadata.engine.model.FieldMetadata;
import com.bone.metadata.engine.util.CommonUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of the Field Calculation Engine Provides expression-based field value
 * calculation functionality, supporting complex expression evaluation, field dependency resolution,
 * and circular dependency detection
 */
public class DefaultFieldCalculationEngine implements FieldCalculationEngine {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFieldCalculationEngine.class);

  // 元数据引擎
  private final MetadataEngine metadataEngine;

  // 缓存表达式解析结果，使用ConcurrentHashMap提高线程安全性
  @Getter private final Map<String, ParsedExpression> expressionCache = new ConcurrentHashMap<>();

  // 缓存统计信息
  private final Map<String, Integer> expressionUsageCount = new ConcurrentHashMap<>();
  private int cacheHits = 0;
  private int cacheMisses = 0;

  // 日期格式模式
  private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
      ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));

  // 日期时间格式模式
  private static final ThreadLocal<SimpleDateFormat> DATETIME_FORMAT =
      ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

  // 字段引用模式
  private static final Pattern FIELD_REFERENCE_PATTERN = Pattern.compile("\\{([\\w\\.]+)\\}");

  /**
   * Constructor with metadata engine
   *
   * @param metadataEngine the metadata engine instance
   */
  public DefaultFieldCalculationEngine(MetadataEngine metadataEngine) {
    this.metadataEngine = metadataEngine;
    LOGGER.info("DefaultFieldCalculationEngine initialized with metadata engine");
  }

  /** Default constructor, used for testing or standalone scenarios */
  public DefaultFieldCalculationEngine() {
    this.metadataEngine = null;
    LOGGER.info(
        "DefaultFieldCalculationEngine initialized without metadata engine (standalone mode)");
  }

  @Override
  public Object calculateField(DynamicSmartEntity entity, FieldMetadata fieldMetadata) {
    try {
      // 检查参数有效性
      if (entity == null || fieldMetadata == null) {
        throw new IllegalArgumentException(
            "Invalid parameters for field calculation: entity or fieldMetadata is null");
      }

      String fieldName = fieldMetadata.getApiName();
      LOGGER.debug(
          "Calculating field: {} for entity type: {}",
          fieldName,
          (String) entity.getField("entityType"));

      // 检查字段是否可计算
      boolean isCalculated = isFieldCalculated(fieldMetadata);
      if (!isCalculated) {
        throw new CalculationException(
            "Field is not calculated", fieldName, (String) entity.getField("entityType"));
      }

      String expression = fieldMetadata.getCalculationExpression();
      if (expression == null || expression.trim().isEmpty()) {
        throw new CalculationException(
            "Calculation expression is empty", fieldName, (String) entity.getField("entityType"));
      }

      // 清除可能的空白字符
      expression = expression.trim();

      // 获取或解析表达式
      ParsedExpression parsedExpression = getParsedExpression(expression);

      // 计算表达式值
      return evaluateExpression(parsedExpression, entity, new HashSet<>());
    } catch (CalculationException e) {
      LOGGER.error("Error calculating field: {}", e.getMessage(), e);
      throw e;
    } catch (Exception e) {
      String fieldName = fieldMetadata != null ? fieldMetadata.getApiName() : "unknown";
      String entityType = entity != null ? (String) entity.getField("entityType") : "unknown";
      LOGGER.error("Unexpected error calculating field {} for entity {}", fieldName, entityType, e);
      throw new CalculationException(
          "Failed to calculate field: " + e.getMessage(), fieldName, entityType, e);
    }
  }

  /**
   * Checks if a field is a calculated field
   *
   * @param fieldMetadata the field metadata
   * @return true if the field is calculated
   */
  private boolean isFieldCalculated(FieldMetadata fieldMetadata) {
    if (fieldMetadata == null) {
      return false;
    }

    // 检查计算表达式是否存在且不为空
    String expression = fieldMetadata.getCalculationExpression();
    return CommonUtils.isNotEmpty(expression);
  }

  @Override
  public void calculateAllFields(DynamicSmartEntity entity) {
    if (entity == null) {
      LOGGER.warn("Null entity passed to calculateAllFields");
      return;
    }

    String entityType = (String) entity.getField("entityType");
    LOGGER.debug("Calculating all fields for entity type: {}", entityType);

    try {
      // 从元数据引擎获取实体的所有字段元数据
      List<FieldMetadata> allFields = getEntityFields(entity);

      // 筛选出可计算字段
      List<FieldMetadata> calculatedFields =
          allFields.stream().filter(this::isFieldCalculated).collect(Collectors.toList());

      if (calculatedFields.isEmpty()) {
        LOGGER.debug("No calculated fields found for entity type: {}", entityType);
        return;
      }

      LOGGER.debug(
          "Found {} calculated fields for entity type: {}", calculatedFields.size(), entityType);

      // 按照依赖关系排序，确保依赖的字段先计算
      List<FieldMetadata> sortedFields = sortFieldsByDependency(calculatedFields, entityType);

      // 计算每个字段
      for (FieldMetadata field : sortedFields) {
        try {
          Object value = calculateField(entity, field);
          // 使用更通用的方式设置字段值
          setEntityField(entity, field.getApiName(), value);
          LOGGER.debug("Successfully calculated and set field: {} = {}", field.getApiName(), value);
        } catch (Exception e) {
          LOGGER.error(
              "Error calculating field {} for entity {}", field.getApiName(), entityType, e);
          // 继续计算其他字段，避免单个字段失败影响整体
        }
      }
    } catch (Exception e) {
      LOGGER.error("Failed to calculate all fields for entity type: {}", entityType, e);
    }
  }

  /**
   * Gets all field metadata for an entity
   *
   * @param entity the dynamic smart entity
   * @return list of field metadata
   */
  private List<FieldMetadata> getEntityFields(DynamicSmartEntity entity) {
    List<FieldMetadata> fields = new ArrayList<>();

    if (metadataEngine != null) {
      try {
        // 尝试从元数据引擎获取字段信息
        Object entityMetadata =
            metadataEngine.getEntityMetadata((String) entity.getField("entityType"));
        if (entityMetadata instanceof Map) {
          // 从元数据中提取字段信息
          // 这里是简化实现，实际应该根据元数据引擎的API进行适配
          LOGGER.debug(
              "Retrieving fields from metadata engine for entity: {}",
              entity.getField("entityType"));
        }
      } catch (Exception e) {
        LOGGER.warn("Failed to retrieve fields from metadata engine: {}", e.getMessage());
      }
    }

    // 尝试从实体中直接获取字段信息（备选方案）
    try {
      Method getFieldsMethod = entity.getClass().getMethod("getFields");
      Object result = getFieldsMethod.invoke(entity);
      if (result instanceof List) {
        @SuppressWarnings("unchecked")
        List<FieldMetadata> entityFields = (List<FieldMetadata>) result;
        fields.addAll(entityFields);
      }
    } catch (Exception e) {
      LOGGER.debug("Entity does not support getFields method: {}", e.getMessage());
    }

    return fields;
  }

  /**
   * Sets entity field value using safe reflection
   *
   * @param entity the dynamic smart entity
   * @param fieldName the field name
   * @param value the field value
   */
  private void setEntityField(DynamicSmartEntity entity, String fieldName, Object value) {
    try {
      // 尝试通过反射设置字段值
      Method setMethod = entity.getClass().getMethod("setField", String.class, Object.class);
      setMethod.invoke(entity, fieldName, value);
    } catch (Exception e) {
      // 如果反射调用失败，记录警告
      LOGGER.warn("Failed to set calculated field {}: {}", fieldName, e.getMessage());
    }
  }

  @Override
  public boolean validateExpression(FieldMetadata fieldMetadata) {
    if (fieldMetadata == null) {
      LOGGER.warn("Null fieldMetadata passed to validateExpression");
      return false;
    }

    // 检查是否有计算表达式
    String expression = null;
    try {
      expression = fieldMetadata.getCalculationExpression();

      if (expression == null || expression.trim().isEmpty()) {
        LOGGER.debug("Empty expression for field: {}", fieldMetadata.getApiName());
        return false;
      }

      // 解析表达式
      ParsedExpression parsedExpression = parseExpression(expression.trim());

      // 验证表达式结构
      boolean isValid = validateExpressionStructure(parsedExpression);

      if (!isValid) {
        LOGGER.warn(
            "Invalid expression structure for field {}: {}",
            fieldMetadata.getApiName(),
            expression);
      }

      return isValid;
    } catch (Exception e) {
      LOGGER.warn(
          "Expression validation failed for field {}: {} - Error: {}",
          fieldMetadata.getApiName(),
          expression,
          e.getMessage());
      return false;
    }
  }

  @Override
  public List<String> getExpressionDependencies(String expression) {
    if (expression == null || expression.trim().isEmpty()) {
      return Collections.emptyList();
    }

    Set<String> dependencies = new HashSet<>();
    try {
      Matcher matcher = FIELD_REFERENCE_PATTERN.matcher(expression);

      while (matcher.find()) {
        String fieldName = matcher.group(1);
        dependencies.add(fieldName);
      }

      LOGGER.debug("Found {} dependencies in expression: {}", dependencies.size(), expression);
    } catch (Exception e) {
      LOGGER.warn("Error extracting expression dependencies: {}", e.getMessage());
    }

    return new ArrayList<>(dependencies);
  }

  /**
   * Parses an expression into a ParsedExpression object
   *
   * @param expression the expression string
   * @return parsed expression object
   */
  private ParsedExpression parseExpression(String expression) {
    // 解析字段引用
    List<String> fieldReferences = getExpressionDependencies(expression);

    // 解析常量值
    List<Constant> constants = extractConstants(expression);

    return new ParsedExpression(expression, fieldReferences, constants);
  }

  /**
   * Gets a parsed expression from cache or parses it if not found
   *
   * @param expression the expression string
   * @return parsed expression object
   */
  private ParsedExpression getParsedExpression(String expression) {
    ParsedExpression cached = expressionCache.get(expression);
    if (cached != null) {
      cacheHits++;
      // 增加使用计数
      expressionUsageCount.compute(expression, (k, v) -> v == null ? 1 : v + 1);
      LOGGER.trace(
          "Expression cache hit for: {}",
          expression.substring(0, Math.min(50, expression.length())));
      return cached;
    }

    cacheMisses++;
    LOGGER.trace(
        "Expression cache miss, parsing: {}",
        expression.substring(0, Math.min(50, expression.length())));
    ParsedExpression parsed = parseExpression(expression);
    expressionCache.put(expression, parsed);
    expressionUsageCount.put(expression, 1);

    // 记录缓存统计信息
    if (expressionCache.size() % 100 == 0) {
      LOGGER.info(
          "Expression cache statistics - Size: {}, Hits: {}, Misses: {}",
          expressionCache.size(),
          cacheHits,
          cacheMisses);
    }

    return parsed;
  }

  /**
   * Gets cache statistics
   *
   * @return map containing cache statistics
   */
  public Map<String, Object> getCacheStatistics() {
    Map<String, Object> stats = new HashMap<>();
    stats.put("size", expressionCache.size());
    stats.put("hits", cacheHits);
    stats.put("misses", cacheMisses);
    stats.put(
        "hitRatio",
        cacheHits + cacheMisses > 0 ? (double) cacheHits / (cacheHits + cacheMisses) : 0.0);

    // 添加使用频率最高的前5个表达式
    List<Map.Entry<String, Integer>> topUsed =
        expressionUsageCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(5)
            .collect(Collectors.toList());
    stats.put("topUsedExpressions", topUsed);

    return stats;
  }

  /**
   * Extracts constant values from an expression
   *
   * @param expression the expression string
   * @return list of extracted constants
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
      String value =
          stringMatcher.group(1) != null ? stringMatcher.group(1) : stringMatcher.group(2);
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
   *
   * @param parsedExpression 解析后的表达式
   * @param entity 动态实体
   * @param visitedFields 已访问字段，用于检测循环依赖
   * @return 表达式计算结果
   * @throws CalculationException 如果计算失败
   */
  private Object evaluateExpression(
      ParsedExpression parsedExpression, DynamicSmartEntity entity, Set<String> visitedFields) {
    try {
      // 检查循环依赖
      String currentFieldName = getCurrentFieldName(entity, parsedExpression);
      if (currentFieldName != null) {
        if (visitedFields.contains(currentFieldName)) {
          throw new CalculationException(
              "Circular dependency detected",
              currentFieldName,
              (String) entity.getField("entityType"));
        }
        visitedFields.add(currentFieldName);
      }

      // 替换字段引用为实际值
      String expression = parsedExpression.getExpression();
      expression =
          replaceFieldReferences(
              expression, parsedExpression.getFieldReferences(), entity, visitedFields);

      // Execute calculation
      Object result = executeCalculation(expression);
      LOGGER.debug("Expression evaluated to: {}", result);
      return result;
    } catch (CalculationException e) {
      throw e;
    } catch (Exception e) {
      String entityType = entity != null ? (String) entity.getField("entityType") : "unknown";
      throw new CalculationException(
          "Failed to evaluate expression: " + e.getMessage(),
          getCurrentFieldName(entity, parsedExpression),
          entityType,
          e);
    }
  }

  /**
   * 获取当前计算字段名
   *
   * @param entity 动态实体
   * @param parsedExpression 解析后的表达式
   * @return 字段名
   */
  private String getCurrentFieldName(DynamicSmartEntity entity, ParsedExpression parsedExpression) {
    // This method is intended to extract the field name from context
    // Currently returns null as a placeholder - implement based on actual requirements
    return null;
  }

  /**
   * 替换字段引用为实际值
   *
   * @param expression 原始表达式
   * @param fieldRefs 字段引用列表
   * @param entity 动态实体
   * @param visitedFields 已访问字段集合，用于循环依赖检测
   * @return 替换后的表达式
   */
  private String replaceFieldReferences(
      String expression,
      List<String> fieldRefs,
      DynamicSmartEntity entity,
      Set<String> visitedFields) {
    String result = expression;

    for (String fieldRef : fieldRefs) {
      try {
        // 字段引用格式为{fieldName}，提取字段名
        LOGGER.trace("Resolving field reference: {}", fieldRef);

        // 获取字段值
        Object fieldValue = getFieldValue(entity, fieldRef, visitedFields);

        // 转换为字符串
        String stringValue = convertValueToString(fieldValue);

        // 替换表达式中的引用
        result = result.replace("{" + fieldRef + "}", stringValue);

        LOGGER.trace("Replaced {{{}}} with {}", fieldRef, stringValue);
      } catch (Exception e) {
        LOGGER.warn("Failed to resolve field reference {}: {}", fieldRef, e.getMessage());
        // 使用null作为默认值
        result = result.replace("{" + fieldRef + "}", "null");
      }
    }

    return result;
  }

  /**
   * 获取字段值
   *
   * @param entity 动态实体
   * @param fieldPath 字段路径
   * @param visitedFields 已访问字段集合，用于循环依赖检测
   * @return 字段值
   */
  private Object getFieldValue(
      DynamicSmartEntity entity, String fieldPath, Set<String> visitedFields) {
    // 检查参数有效性
    if (entity == null || fieldPath == null || fieldPath.trim().isEmpty()) {
      LOGGER.warn(
          "Invalid parameters for getFieldValue: entity={}, fieldPath={}", entity, fieldPath);
      return null;
    }

    // 支持嵌套字段访问，例如：field1.field2.field3
    String[] parts = fieldPath.split("\\.");
    Object current = entity;

    for (String part : parts) {
      if (current == null) {
        LOGGER.debug("Null value encountered at path segment: {} in {}", part, fieldPath);
        return null;
      }

      try {
        if (current instanceof Map) {
          current = ((Map<?, ?>) current).get(part);
          LOGGER.trace("Retrieved map value for {}: {}", part, current);
        } else if (current instanceof DynamicSmartEntity) {
          // 尝试通过getField方法获取值
          Method getMethod = current.getClass().getMethod("getField", String.class);
          Object value = getMethod.invoke(current, part);

          // 如果获取的值是可计算字段，先计算其值
          if (value instanceof FieldMetadata && isFieldCalculated((FieldMetadata) value)) {
            FieldMetadata fieldMeta = (FieldMetadata) value;
            if (visitedFields.contains(fieldMeta.getApiName())) {
              throw new CalculationException(
                  "Circular dependency in nested field reference",
                  fieldMeta.getApiName(),
                  (String) ((DynamicSmartEntity) current).getField("entityType"));
            }
            // 递归计算嵌套字段值
            value = calculateField((DynamicSmartEntity) current, fieldMeta);
          }

          current = value;
          LOGGER.trace("Retrieved entity field value for {}: {}", part, current);
        } else {
          // 对于非Map和非DynamicSmartEntity类型，尝试通过反射获取字段值
          Optional<Object> fieldValue = getFieldValueByReflection(current, part);
          if (fieldValue.isPresent()) {
            current = fieldValue.get();
            LOGGER.trace("Retrieved reflection field value for {}: {}", part, current);
          } else {
            // 如果字段不存在，尝试通过getter方法获取
            Optional<Object> getterValue = getFieldValueByGetter(current, part);
            if (getterValue.isPresent()) {
              current = getterValue.get();
              LOGGER.trace("Retrieved getter value for {}: {}", part, current);
            } else {
              LOGGER.debug("No value found for field: {} in path: {}", part, fieldPath);
              return null;
            }
          }
        }
      } catch (Exception e) {
        LOGGER.warn(
            "Error retrieving value for field segment {} in path {}: {}",
            part,
            fieldPath,
            e.getMessage());
        return null;
      }
    }

    return current;
  }

  /** 通过反射获取字段值 */
  private Optional<Object> getFieldValueByReflection(Object obj, String fieldName) {
    try {
      Field field = findField(obj.getClass(), fieldName);
      if (field != null) {
        field.setAccessible(true);
        return Optional.ofNullable(field.get(obj));
      }
    } catch (Exception e) {
      LOGGER.debug("Reflection error for field {}: {}", fieldName, e.getMessage());
    }
    return Optional.empty();
  }

  /** 通过getter方法获取字段值 */
  private Optional<Object> getFieldValueByGetter(Object obj, String fieldName) {
    try {
      // 尝试标准getter命名规则
      String getterName =
          "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

      try {
        Method getter = obj.getClass().getMethod(getterName);
        return Optional.ofNullable(getter.invoke(obj));
      } catch (NoSuchMethodException e) {
        // 如果标准getter不存在，尝试is前缀（用于布尔值）
        if (fieldName.startsWith("is")) {
          Method getter = obj.getClass().getMethod(fieldName);
          return Optional.ofNullable(getter.invoke(obj));
        }
      }
    } catch (Exception e) {
      LOGGER.debug("Getter error for field {}: {}", fieldName, e.getMessage());
    }
    return Optional.empty();
  }

  /** 在类层次结构中查找字段 */
  private Field findField(Class<?> clazz, String fieldName) {
    for (Class<?> current = clazz;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      try {
        return current.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        // 继续在父类中查找
      }
    }
    return null;
  }

  /**
   * Evaluates field calculation
   *
   * @param entity the dynamic smart entity
   * @param fieldMetadata the field metadata
   * @param visitedFields set of already visited fields for circular dependency detection
   * @return calculated field value
   * @throws Exception if calculation fails
   */
  private Object evaluateFieldCalculation(
      DynamicSmartEntity entity, FieldMetadata fieldMetadata, Set<String> visitedFields)
      throws Exception {
    String fieldName = fieldMetadata.getApiName();

    // 检查循环依赖
    if (visitedFields.contains(fieldName)) {
      throw new IllegalStateException(
          "Circular dependency detected in field calculation: " + fieldName);
    }

    // 添加到已访问集合
    visitedFields.add(fieldName);

    try {
      ParsedExpression parsedExpression =
          getParsedExpression(fieldMetadata.getCalculationExpression());
      return evaluateExpression(parsedExpression, entity, visitedFields);
    } finally {
      // 从已访问集合移除
      visitedFields.remove(fieldName);
    }
  }

  /**
   * Converts a value to its string representation
   *
   * @param value the value to convert
   * @return string representation of the value
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
   * Executes expression calculation
   *
   * @param expression the expression string
   * @return calculation result
   */
  private Object executeCalculation(String expression) {
    if (expression == null || expression.trim().isEmpty()) {
      return null;
    }

    expression = expression.trim();

    try {
      // 处理简单表达式，如常量值
      if (expression.startsWith("'")) {
        // 字符串常量
        return expression.substring(1, expression.length() - 1);
      } else if (expression.equals("true") || expression.equals("false")) {
        // 布尔常量
        return Boolean.parseBoolean(expression);
      } else if (expression.equals("null")) {
        // null值
        return null;
      } else if (expression.matches("^\\d+$")) {
        // 整数常量
        return Long.parseLong(expression);
      } else if (expression.matches("^\\d+\\.\\d+$")) {
        // 浮点数常量
        return Double.parseDouble(expression);
      } else if (expression.contains("+")) {
        // 加法表达式
        String[] operands = expression.split("\\+", 2);
        Object left = executeCalculation(operands[0].trim());
        Object right = executeCalculation(operands[1].trim());
        return evaluateArithmeticExpression(left, right, "+").doubleValue();
      } else if (expression.contains("-")) {
        // 减法表达式
        String[] operands = expression.split("-", 2);
        Object left = executeCalculation(operands[0].trim());
        Object right = executeCalculation(operands[1].trim());
        return evaluateArithmeticExpression(left, right, "-").doubleValue();
      } else if (expression.contains("*")) {
        // 乘法表达式
        String[] operands = expression.split("\\*", 2);
        Object left = executeCalculation(operands[0].trim());
        Object right = executeCalculation(operands[1].trim());
        return evaluateArithmeticExpression(left, right, "*").doubleValue();
      } else if (expression.contains("/")) {
        // 除法表达式
        String[] operands = expression.split("/", 2);
        Object left = executeCalculation(operands[0].trim());
        Object right = executeCalculation(operands[1].trim());
        return evaluateArithmeticExpression(left, right, "/").doubleValue();
      } else {
        // 对于其他情况，尝试直接解析为数字
        try {
          return Double.parseDouble(expression);
        } catch (NumberFormatException e) {
          // 如果解析失败，返回原始表达式作为字符串
          return expression;
        }
      }
    } catch (Exception e) {
      LOGGER.error(
          "Error executing calculation for expression '{}': {}", expression, e.getMessage());
      // On exception, return the original expression value
      LOGGER.error(
          "Error during calculation, returning original expression as fallback: {}", expression);
      return expression;
    }
  }

  /**
   * Evaluates arithmetic expression with two operands
   *
   * @param left the left operand
   * @param right the right operand
   * @param operator the operator (+, -, *, /)
   * @return calculation result as BigDecimal
   */
  private BigDecimal evaluateArithmeticExpression(Object left, Object right, String operator) {
    try {
      BigDecimal leftValue = convertToBigDecimal(left);
      BigDecimal rightValue = convertToBigDecimal(right);

      switch (operator) {
        case "+":
          return leftValue.add(rightValue);
        case "-":
          return leftValue.subtract(rightValue);
        case "*":
          return leftValue.multiply(rightValue);
        case "/":
          if (rightValue.compareTo(BigDecimal.ZERO) == 0) {
            LOGGER.warn("Division by zero detected");
            return BigDecimal.ZERO; // 避免除零异常
          }
          return leftValue.divide(rightValue, 10, RoundingMode.HALF_UP);
        default:
          LOGGER.error("Unsupported operator: {}", operator);
          return BigDecimal.ZERO;
      }
    } catch (Exception e) {
      LOGGER.error("Error evaluating arithmetic expression: {}", e.getMessage());
      return BigDecimal.ZERO;
    }
  }

  /**
   * Converts an object to BigDecimal
   *
   * @param value the value to convert
   * @return converted BigDecimal value
   */
  private BigDecimal convertToBigDecimal(Object value) {
    if (value == null) {
      return BigDecimal.ZERO;
    }

    if (value instanceof BigDecimal) {
      return (BigDecimal) value;
    } else if (value instanceof Number) {
      return BigDecimal.valueOf(((Number) value).doubleValue());
    } else {
      try {
        return new BigDecimal(value.toString());
      } catch (NumberFormatException e) {
        LOGGER.warn("Failed to convert {} to BigDecimal, returning 0", value);
        return BigDecimal.ZERO;
      }
    }
  }

  /**
   * Sorts fields by their dependency relationships
   *
   * @param fields list of field metadata
   * @param entityApiName the entity API name
   * @return sorted list of field metadata
   */
  private List<FieldMetadata> sortFieldsByDependency(
      List<FieldMetadata> fields, String entityApiName) {
    try {
      LOGGER.debug("Sorting {} fields by dependency for entity: {}", fields.size(), entityApiName);

      // 构建依赖图
      Map<String, Set<String>> dependencyGraph = new HashMap<>();
      Set<String> calculatedFields = new HashSet<>();

      for (FieldMetadata field : fields) {
        String fieldApiName = field.getApiName();
        Set<String> dependencies = new HashSet<>();

        // 如果是计算字段，获取其依赖
        if (isFieldCalculated(field)) {
          calculatedFields.add(fieldApiName);
          // 获取字段依赖
          List<String> exprDependencies =
              getExpressionDependencies(field.getCalculationExpression());
          for (String dep : exprDependencies) {
            // 只考虑当前实体的字段依赖
            if (!dep.contains(".")) {
              dependencies.add(dep);
              LOGGER.trace("Field {} depends on {}", fieldApiName, dep);
            }
          }
        }

        dependencyGraph.put(fieldApiName, dependencies);
      }

      // 使用拓扑排序
      return topologicalSort(dependencyGraph, fields);
    } catch (Exception e) {
      LOGGER.error(
          "Error sorting fields by dependency for entity {}: {}", entityApiName, e.getMessage());
      // 发生异常时，返回原始字段列表作为后备
      return fields;
    }
  }

  /**
   * Performs topological sort on fields based on dependencies
   *
   * @param dependencyGraph the dependency graph
   * @param fields list of field metadata
   * @return topologically sorted list of field metadata
   */
  private List<FieldMetadata> topologicalSort(
      Map<String, Set<String>> dependencyGraph, List<FieldMetadata> fields) {
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
    Map<String, FieldMetadata> fieldMap =
        fields.stream().collect(Collectors.toMap(FieldMetadata::getApiName, f -> f));

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
   * Performs depth-first search for topological sorting
   *
   * @param node current node being visited
   * @param graph the dependency graph
   * @param visited set of already visited nodes
   * @param visiting set of nodes being visited in current traversal
   * @param result list to store the topological order
   * @throws IllegalArgumentException when circular dependency is detected
   */
  private void dfs(
      String node,
      Map<String, Set<String>> graph,
      Set<String> visited,
      Set<String> visiting,
      List<String> result) {
    visiting.add(node);

    Set<String> neighbors = graph.getOrDefault(node, Collections.emptySet());
    for (String neighbor : neighbors) {
      if (!visited.contains(neighbor)) {
        if (visiting.contains(neighbor)) {
          // Detect circular dependency
          String errorMsg =
              String.format("Circular dependency detected between %s and %s", node, neighbor);
          LOGGER.error(errorMsg);
          throw new IllegalArgumentException(errorMsg);
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
   * Validates expression structure
   *
   * @param expression the parsed expression
   * @return true if expression structure is valid
   */
  private boolean validateExpressionStructure(ParsedExpression expression) {
    if (expression == null || expression.getExpression() == null) {
      LOGGER.debug("Null expression provided for validation");
      return false;
    }

    try {
      // 简单验证括号是否匹配
      int balance = 0;
      String exprStr = expression.getExpression();
      for (char c : exprStr.toCharArray()) {
        if (c == '(') {
          balance++;
        } else if (c == ')') {
          balance--;
          if (balance < 0) {
            LOGGER.debug("Unbalanced parentheses in expression: {}", exprStr);
            return false;
          }
        }
      }

      boolean result = (balance == 0);
      if (!result) {
        LOGGER.debug("Unclosed parentheses in expression: {}", exprStr);
      }

      return result;
    } catch (Exception e) {
      LOGGER.warn("Error validating expression structure: {}", e.getMessage());
      return false;
    }
  }

  /** Represents a parsed expression with field references and constants */
  private static class ParsedExpression {
    private final String expression;
    private final List<String> fieldReferences;
    private final List<Constant> constants;

    public ParsedExpression(
        String expression, List<String> fieldReferences, List<Constant> constants) {
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

  /** Represents a constant value in an expression */
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

  /** Clears the expression cache */
  public void clearCache() {
    try {
      int size = expressionCache.size();
      expressionCache.clear();
      expressionUsageCount.clear();
      cacheHits = 0;
      cacheMisses = 0;
      LOGGER.info("Expression cache cleared, removed {} entries", size);
    } catch (Exception e) {
      LOGGER.error("Error clearing expression cache: {}", e.getMessage());
    }
  }

  /**
   * Gets current cache size
   *
   * @return number of cache entries
   */
  public int getCacheSize() {
    return expressionCache.size();
  }

  /**
   * Prunes cache using LRU strategy, retaining the most frequently used expressions
   *
   * @param retainCount number of expressions to retain
   */
  public void pruneCache(int retainCount) {
    if (retainCount <= 0) {
      clearCache();
      return;
    }

    if (expressionCache.size() <= retainCount) {
      return; // 缓存大小已经在限制之内
    }

    // 按使用频率排序，保留使用频率最高的表达式
    List<String> toKeep =
        expressionUsageCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(retainCount)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

    // 移除不在保留列表中的表达式
    Set<String> toRemove = new HashSet<>(expressionCache.keySet());
    toRemove.removeAll(toKeep);

    for (String expr : toRemove) {
      expressionCache.remove(expr);
      expressionUsageCount.remove(expr);
    }

    LOGGER.info("Cache pruned, removed {} entries, retained {}", toRemove.size(), toKeep.size());
  }
}
