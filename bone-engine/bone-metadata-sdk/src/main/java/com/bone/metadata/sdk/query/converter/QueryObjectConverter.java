package com.bone.metadata.sdk.query.converter;

import com.bone.core.enums.Operator;
import com.bone.core.model.PageParam;
import com.bone.core.model.SortableParam;
import com.bone.core.model.SortingField;
import com.bone.metadata.sdk.domain.annotation.QueryField;
import com.bone.metadata.sdk.domain.enums.SortDirection;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/** 通用查询对象转换器 支持将任意查询对象转换为Criteria */
public class QueryObjectConverter {

  private static final Logger logger = LoggerFactory.getLogger(QueryObjectConverter.class);

  // 字段元数据缓存
  private static final Map<Class<?>, List<FieldMetadata>> FIELD_METADATA_CACHE =
      new ConcurrentHashMap<>();

  // 默认操作符映射
  private static final Map<Class<?>, Operator> DEFAULT_OPERATOR_MAP = new HashMap<>();

  static {
    DEFAULT_OPERATOR_MAP.put(String.class, Operator.LIKE);
    DEFAULT_OPERATOR_MAP.put(List.class, Operator.IN);
    DEFAULT_OPERATOR_MAP.put(Set.class, Operator.IN);
    DEFAULT_OPERATOR_MAP.put(Object[].class, Operator.IN);
    DEFAULT_OPERATOR_MAP.put(Boolean.class, Operator.EQ);
    DEFAULT_OPERATOR_MAP.put(Integer.class, Operator.EQ);
    DEFAULT_OPERATOR_MAP.put(Long.class, Operator.EQ);
    DEFAULT_OPERATOR_MAP.put(Double.class, Operator.EQ);
    DEFAULT_OPERATOR_MAP.put(Float.class, Operator.EQ);
    DEFAULT_OPERATOR_MAP.put(LocalDate.class, Operator.EQ);
    DEFAULT_OPERATOR_MAP.put(LocalDateTime.class, Operator.EQ);
    DEFAULT_OPERATOR_MAP.put(Date.class, Operator.EQ);
  }

  // 日期范围字段后缀模式
  private static final Pattern DATE_RANGE_PATTERN = Pattern.compile("(Start|End|From|To)$");

  // 自定义类型转换器注册表
  private static final Map<Class<?>, Function<Object, Object>> CUSTOM_CONVERTERS =
      new ConcurrentHashMap<>();

  // 字段元数据类
  private static class FieldMetadata {
    Field field;
    String fieldName;
    QueryField queryField;

    FieldMetadata(Field field, String fieldName, QueryField queryField) {
      this.field = field;
      this.fieldName = fieldName;
      this.queryField = queryField;
    }
  }

  /** 注册自定义类型转换器 */
  @SuppressWarnings("unchecked")
  public static <T> void registerConverter(Class<T> type, Function<T, Object> converter) {
    CUSTOM_CONVERTERS.put(type, (Function<Object, Object>) converter);
  }

  /** 将任意查询对象转换为Criteria */
  public static <T> Criteria<T> convert(Object queryObject, Class<T> entityClass) {
    Assert.notNull(queryObject, "Query object must not be null");

    Criteria<T> criteria = Criteria.create();

    // 处理查询条件
    processQueryConditions(queryObject, criteria, entityClass);

    // 处理分页和排序参数
    processPaginationAndSorting(queryObject, criteria);

    return criteria;
  }

  /** 处理查询条件 */
  private static <T> void processQueryConditions(
      Object queryObject, Criteria<T> criteria, Class<T> entityClass) {
    List<FieldMetadata> fields = getCachedFields(queryObject.getClass());

    for (FieldMetadata metadata : fields) {
      processField(queryObject, metadata, criteria, entityClass);
    }
  }

  /** 获取缓存的字段元数据 */
  private static List<FieldMetadata> getCachedFields(Class<?> clazz) {
    return FIELD_METADATA_CACHE.computeIfAbsent(
        clazz,
        k -> {
          List<FieldMetadata> metadataList = new ArrayList<>();
          Class<?> currentClass = clazz;

          // 遍历所有字段，包括父类字段
          while (currentClass != null && currentClass != Object.class) {
            Field[] fields = currentClass.getDeclaredFields();
            for (Field field : fields) {
              // 跳过序列化字段和静态字段
              if (field.getName().equals("serialVersionUID")
                  || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
              }

              field.setAccessible(true);
              QueryField queryField = field.getAnnotation(QueryField.class);
              String fieldName = getFieldName(field, queryField);

              metadataList.add(new FieldMetadata(field, fieldName, queryField));
            }
            currentClass = currentClass.getSuperclass();
          }

          return Collections.unmodifiableList(metadataList);
        });
  }

  /** 处理单个字段 */
  private static <T> void processField(
      Object queryObject, FieldMetadata metadata, Criteria<T> criteria, Class<T> entityClass) {
    try {
      Object value = metadata.field.get(queryObject);

      // 应用自定义类型转换
      value = applyCustomConverter(metadata.field, value);

      // 检查是否应该忽略该字段
      if (shouldIgnoreField(value, metadata.queryField, metadata.fieldName)) {
        return;
      }

      // 获取操作符
      Operator operator = getOperator(metadata.field, metadata.queryField, value);

      // 特殊处理日期范围字段
      if (isDateRangeField(metadata.field, value)) {
        processDateRangeField(queryObject, metadata, criteria);
        return;
      }

      // 根据操作符添加条件
      addCondition(criteria, metadata.fieldName, operator, value);
    } catch (IllegalAccessException e) {
      logger.warn(
          "Failed to access field {} on {}",
          metadata.field.getName(),
          queryObject.getClass().getName(),
          e);
    }
  }

  /** 应用自定义类型转换 */
  private static Object applyCustomConverter(Field field, Object value) {
    if (value == null) {
      return null;
    }

    Function<Object, Object> converter = CUSTOM_CONVERTERS.get(field.getType());
    if (converter != null) {
      return converter.apply(value);
    }

    return value;
  }

  /** 获取字段名（支持驼峰转下划线） */
  private static String getFieldName(Field field, QueryField queryField) {
    if (queryField != null && StringUtils.hasText(queryField.value())) {
      return queryField.value();
    }

    // 默认将驼峰命名转换为下划线命名
    String fieldName = field.getName();
    String snakeCase = camelToSnake(fieldName);

    // 验证字段名是否合法（只包含字母、数字、下划线）
    if (!snakeCase.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
      logger.warn("Invalid field name generated: {} from {}", snakeCase, fieldName);
      return fieldName; // 返回原始字段名
    }

    return snakeCase;
  }

  /** 驼峰命名转下划线命名 */
  private static String camelToSnake(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }

    StringBuilder result = new StringBuilder();
    result.append(Character.toLowerCase(str.charAt(0)));

    for (int i = 1; i < str.length(); i++) {
      char c = str.charAt(i);
      if (Character.isUpperCase(c)) {
        result.append('_').append(Character.toLowerCase(c));
      } else {
        result.append(c);
      }
    }

    return result.toString();
  }

  /** 获取操作符 */
  private static Operator getOperator(Field field, QueryField queryField, Object value) {
    // 如果注解指定了操作符且不是AUTO，则使用注解的操作符
    if (queryField != null && queryField.operator() != Operator.AUTO) {
      return queryField.operator();
    }

    // 默认操作符基于字段类型和值
    Class<?> fieldType = field.getType();

    // 处理集合和数组类型
    if (value != null) {
      if (value instanceof Collection && !(((Collection<?>) value).isEmpty())) {
        return Operator.IN;
      }
      if (fieldType.isArray() && java.lang.reflect.Array.getLength(value) > 0) {
        return Operator.IN;
      }
    }

    // 处理日期时间类型
    if (fieldType == LocalDate.class
        || fieldType == LocalDateTime.class
        || fieldType == Date.class
        || fieldType == java.sql.Date.class) {
      String fieldName = field.getName();
      if (DATE_RANGE_PATTERN.matcher(fieldName).find()) {
        return fieldName.endsWith("Start") || fieldName.endsWith("From")
            ? Operator.GTE
            : Operator.LTE;
      }
    }

    // 字符串类型特殊处理
    if (fieldType == String.class && value != null) {
      QueryField annotation = field.getAnnotation(QueryField.class);
      if (annotation != null && !annotation.fuzzy()) {
        return Operator.EQ;
      }
      return Operator.LIKE;
    }

    // 默认操作符
    return DEFAULT_OPERATOR_MAP.getOrDefault(fieldType, Operator.EQ);
  }

  /** 检查是否应该忽略字段 */
  private static boolean shouldIgnoreField(Object value, QueryField queryField, String fieldName) {
    // 特殊字段跳过（分页和排序字段）
    Set<String> ignoredFields =
        Set.of(
            "page",
            "size",
            "pageNo",
            "pageSize",
            "page_no",
            "page_size",
            "sortingFields",
            "sorting_fields",
            "order",
            "sort",
            "offset",
            "limit",
            "current",
            "total",
            "records" // 分页相关字段
            );

    if (ignoredFields.contains(fieldName)) {
      return true;
    }

    if (value == null) {
      return queryField == null || queryField.ignoreNull();
    }

    if (value instanceof String && ((String) value).isEmpty()) {
      return queryField == null || queryField.ignoreEmpty();
    }

    if (value instanceof Collection && ((Collection<?>) value).isEmpty()) {
      return true;
    }

    if (value.getClass().isArray() && java.lang.reflect.Array.getLength(value) == 0) {
      return true;
    }

    return false;
  }

  /** 检查是否为日期范围字段 */
  private static boolean isDateRangeField(Field field, Object value) {
    if (value == null) {
      return false;
    }

    Class<?> fieldType = field.getType();
    if (!(fieldType == LocalDate.class
        || fieldType == LocalDateTime.class
        || fieldType == Date.class
        || fieldType == java.sql.Date.class)) {
      return false;
    }

    String fieldName = field.getName();
    return DATE_RANGE_PATTERN.matcher(fieldName).find();
  }

  /** 处理日期范围字段 */
  private static <T> void processDateRangeField(
      Object queryObject, FieldMetadata metadata, Criteria<T> criteria) {
    try {
      String baseFieldName = metadata.fieldName.replaceAll("(Start|End|From|To)$", "");
      Object value = metadata.field.get(queryObject);

      // 查找对应的范围字段
      FieldMetadata pairMetadata = findPairDateField(queryObject, metadata);
      if (pairMetadata != null) {
        Object pairValue = pairMetadata.field.get(queryObject);

        if (pairValue != null) {
          // 两个日期字段都有值，使用BETWEEN
          if (metadata.fieldName.endsWith("Start") || metadata.fieldName.endsWith("From")) {
            criteria.between(baseFieldName, value, pairValue);
          } else {
            criteria.between(baseFieldName, pairValue, value);
          }
          return;
        }
      }

      // 只有一个日期字段有值，使用>=或<=
      if (metadata.fieldName.endsWith("Start") || metadata.fieldName.endsWith("From")) {
        criteria.gte(baseFieldName, value);
      } else {
        criteria.lte(baseFieldName, value);
      }
    } catch (IllegalAccessException e) {
      logger.warn(
          "Failed to access field {} on {}",
          metadata.field.getName(),
          queryObject.getClass().getName(),
          e);
    }
  }

  /** 查找对应的日期范围字段 */
  private static FieldMetadata findPairDateField(Object queryObject, FieldMetadata metadata) {
    String fieldName = metadata.field.getName();
    String baseName;
    String suffix;

    if (fieldName.endsWith("Start")) {
      baseName = fieldName.substring(0, fieldName.length() - 5);
      suffix = "End";
    } else if (fieldName.endsWith("End")) {
      baseName = fieldName.substring(0, fieldName.length() - 3);
      suffix = "Start";
    } else if (fieldName.endsWith("From")) {
      baseName = fieldName.substring(0, fieldName.length() - 4);
      suffix = "To";
    } else if (fieldName.endsWith("To")) {
      baseName = fieldName.substring(0, fieldName.length() - 2);
      suffix = "From";
    } else {
      return null;
    }

    String pairFieldName = baseName + suffix;

    // 在缓存的字段元数据中查找对应的字段
    List<FieldMetadata> fields = getCachedFields(queryObject.getClass());
    for (FieldMetadata fieldMetadata : fields) {
      if (fieldMetadata.field.getName().equals(pairFieldName)) {
        try {
          Object value = fieldMetadata.field.get(queryObject);
          if (value != null) {
            return fieldMetadata;
          }
        } catch (IllegalAccessException e) {
          logger.warn(
              "Failed to access pair field {} on {}",
              pairFieldName,
              queryObject.getClass().getName(),
              e);
        }
        break;
      }
    }

    return null;
  }

  /** 添加查询条件 */
  private static <T> void addCondition(
      Criteria<T> criteria, String fieldName, Operator operator, Object value) {
    switch (operator) {
      case EQ:
        criteria.eq(fieldName, value);
        break;
      case NE:
        criteria.ne(fieldName, value);
        break;
      case GT:
        criteria.gt(fieldName, value);
        break;
      case GTE:
        criteria.gte(fieldName, value);
        break;
      case LT:
        criteria.lt(fieldName, value);
        break;
      case LTE:
        criteria.lte(fieldName, value);
        break;
      case LIKE:
        if (value instanceof String stringValue) {
          // 根据注解决定是否添加通配符
          criteria.like(fieldName, stringValue);
        }
        break;
      case NOT_LIKE:
        if (value instanceof String stringValue) {
          criteria.notLike(fieldName, stringValue);
        }
        break;
      case IN:
        if (value instanceof Collection) {
          criteria.in(fieldName, (Collection<?>) value);
        } else if (value.getClass().isArray()) {
          criteria.in(fieldName, Arrays.asList((Object[]) value));
        }
        break;
      case NOT_IN:
        if (value instanceof Collection) {
          criteria.notIn(fieldName, (Collection<?>) value);
        } else if (value.getClass().isArray()) {
          criteria.notIn(fieldName, Arrays.asList((Object[]) value));
        }
        break;
      case BETWEEN:
        // 已经在日期范围处理中处理了
        break;
      case IS_NULL:
        criteria.isNull(fieldName);
        break;
      case IS_NOT_NULL:
        criteria.isNotNull(fieldName);
        break;
      default:
        criteria.eq(fieldName, value);
    }
  }

  /** 处理分页和排序参数 */
  private static <T> void processPaginationAndSorting(Object queryObject, Criteria<T> criteria) {
    // 处理分页参数
    if (queryObject instanceof PageParam pageParam) {
      if (pageParam.getPage() != null) {
        criteria.setPageNo(pageParam.getPage());
      }

      if (pageParam.getSize() != null) {
        criteria.setPageSize(pageParam.getSize());
      }
    }

    // 处理排序字段
    if (queryObject instanceof SortableParam sortableParam) {
      if (sortableParam.getSortingFields() != null && !sortableParam.getSortingFields().isEmpty()) {
        for (SortingField sortingField : sortableParam.getSortingFields()) {
          criteria.addSort(sortingField.getField(), convertSortDirection(sortingField.getOrder()));
        }
      }
    }
  }

  /** 转换排序方向 */
  private static SortDirection convertSortDirection(String order) {
    if ("desc".equalsIgnoreCase(order)) {
      return SortDirection.DESC;
    }
    return SortDirection.ASC;
  }
}
