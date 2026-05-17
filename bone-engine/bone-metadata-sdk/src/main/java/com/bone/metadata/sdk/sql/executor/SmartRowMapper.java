package com.bone.metadata.sdk.sql.executor;

import com.bone.metadata.sdk.support.cache.FieldCache;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Objects;

public class SmartRowMapper<T> implements RowMapper<T> {

    private final Class<T> mappedClass;

    @Autowired
    public SmartRowMapper(Class<T> mappedClass) {
        this.mappedClass = Objects.requireNonNull(mappedClass, "Mapped class must not be null");
    }

    /**
     * 将 ResultSet 的一行映射为实体对象
     */
    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        T result = createInstance();
        ResultSetMetaData metaData = rs.getMetaData();

        // 遍历所有列并设置对应的字段
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            String columnName = metaData.getColumnLabel(i);
            // 1. 先尝试下划线转驼峰（或通过 @Column 映射）
            Field field = FieldCache.getFieldByColumn(mappedClass, columnName);
            // 2. 没找到再按原样匹配驼峰
            if (field == null) {
                field = FieldCache.getFieldByName(mappedClass, columnName);
            }

            if (field != null) {
                try {
                    field.setAccessible(true);
                    Object value = rs.getObject(i);
                    value = convertFieldValue(value, field.getType());
                    field.set(result, value);
                } catch (IllegalAccessException e) {
                    throw new SQLException("Failed to set value for column: " + columnName + ", field: " + field.getName(), e);
                }
            }
        }
        return result;
    }

    /**
     * 创建目标类的实例
     */
    private Object convertFieldValue(Object value, Class<?> targetType) throws SQLException {
        if (value == null) {
            return null;
        }
        value = unwrapJdbcLob(value);
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (value instanceof Boolean boolValue) {
                return boolValue;
            }
            if (value instanceof Number number) {
                return number.intValue() != 0;
            }
            if (value instanceof String str) {
                return Boolean.parseBoolean(str);
            }
        }
        try {
            return TypeConverter.convert(value, targetType);
        } catch (TypeConverter.UnsupportedConversionException | TypeConverter.TypeConversionException ex) {
            if (targetType.isEnum() && value instanceof String str) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Class<? extends Enum> enumType = (Class<? extends Enum>) targetType;
                return Enum.valueOf(enumType, str);
            }
            if (targetType.isRecord() && value instanceof String str) {
                try {
                    return targetType.getMethod("of", String.class).invoke(null, str);
                } catch (ReflectiveOperationException ignored) {
                    // fall through
                }
            }
            throw new SQLException(
                    "Failed to convert column value to " + targetType.getName() + ": " + value, ex);
        }
    }

    private static Object unwrapJdbcLob(Object value) throws SQLException {
        if (value instanceof java.sql.Clob clob) {
            long length = clob.length();
            return clob.getSubString(1, (int) Math.min(length, Integer.MAX_VALUE));
        }
        if (value instanceof java.sql.NClob nclob) {
            long length = nclob.length();
            return nclob.getSubString(1, (int) Math.min(length, Integer.MAX_VALUE));
        }
        return value;
    }

    private T createInstance() throws SQLException {
        try {
            return BeanUtils.instantiateClass(mappedClass);
        } catch (Exception e) {
            throw new SQLException("Failed to instantiate " + mappedClass.getName(), e);
        }
    }

}