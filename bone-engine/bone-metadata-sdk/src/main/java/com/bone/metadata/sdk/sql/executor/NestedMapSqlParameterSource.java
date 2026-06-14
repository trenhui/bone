package com.bone.metadata.sdk.sql.executor;

import java.util.Map;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.jdbc.core.namedparam.AbstractSqlParameterSource;

public class NestedMapSqlParameterSource extends AbstractSqlParameterSource {
  private final Map<String, Object> paramMap;

  public NestedMapSqlParameterSource(Map<String, Object> paramMap) {
    this.paramMap = paramMap;
  }

  @Override
  public boolean hasValue(String paramName) {
    return getValue(paramName) != null;
  }

  @Override
  public Object getValue(String paramName) {
    String[] parts = paramName.split("\\.");
    Object current = paramMap;
    for (String part : parts) {
      if (current == null) {
        return null;
      }
      if (current instanceof Map) {
        current = ((Map<?, ?>) current).get(part);
      } else {
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(current);
        current = wrapper.getPropertyValue(part);
      }
    }
    return current;
  }
}
