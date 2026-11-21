package com.bone.procurement.risk.infrastructure;

import com.bone.core.domain.entity.Entity;
import com.bone.metadata.sdk.query.criteria.Condition;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** 轻量 SDK 仓储内存替身：只实现测试用到的 Criteria 操作符，不引 Spring 上下文或数据库。 */
final class FakeSdkStore<T extends Entity<Long>> {

  private final Map<Long, T> rows = new LinkedHashMap<>();
  private final AtomicLong seq = new AtomicLong(1);

  Long save(T entity) {
    if (entity.getId() == null) {
      entity.setId(seq.getAndIncrement());
    }
    rows.put(entity.getId(), entity);
    return entity.getId();
  }

  T findById(Long id) {
    return rows.get(id);
  }

  int count() {
    return rows.size();
  }

  List<T> findByCriteria(Criteria<T> criteria) {
    return rows.values().stream().filter(row -> matches(row, criteria)).toList();
  }

  static boolean matches(Object entity, Criteria<?> criteria) {
    for (Condition condition : criteria.getMainConditions()) {
      if (condition.isNativeFragment() || condition.getFieldName() == null) {
        continue;
      }
      Object actual = readField(entity, condition.getFieldName());
      Object[] values = condition.getValues();
      boolean ok =
          switch (condition.getOperator()) {
            case EQ -> Objects.equals(actual, values[0]);
            case NE -> !Objects.equals(actual, values[0]);
            case GT -> compare(actual, values[0]) > 0;
            case GTE -> compare(actual, values[0]) >= 0;
            case LT -> compare(actual, values[0]) < 0;
            case LTE -> compare(actual, values[0]) <= 0;
            case IS_NULL -> actual == null;
            case IS_NOT_NULL -> actual != null;
            case IN -> contains(actual, values);
            case NOT_IN -> !contains(actual, values);
            default -> throw new IllegalStateException(
                "unsupported operator " + condition.getOperator());
          };
      if (!ok) {
        return false;
      }
    }
    return true;
  }

  private static boolean contains(Object actual, Object[] values) {
    if (values.length == 1 && values[0] instanceof Collection<?> collection) {
      return collection.contains(actual);
    }
    for (Object value : values) {
      if (Objects.equals(actual, value)) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static int compare(Object actual, Object expected) {
    if (actual == null || expected == null) {
      return Integer.MIN_VALUE;
    }
    return ((Comparable) actual).compareTo(expected);
  }

  private static Object readField(Object entity, String fieldName) {
    try {
      Field field = entity.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      return field.get(entity);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("field not found: " + fieldName, e);
    }
  }
}
