package com.bone.metadata.sdk.sql.executor;

import com.bone.metadata.sdk.support.cache.FieldCache;
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
                    Object value = rs.getObject(i);  // 获取数据库列的值
                    value = TypeConverter.convert(value, field.getType());  // 类型转换
                    field.set(result, value);  // 设置字段值
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
    private T createInstance() throws SQLException {
        try {
            return mappedClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new SQLException("Failed to instantiate " + mappedClass.getName(), e);
        }
    }

}