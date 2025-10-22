package com.bone.metadata.sdk.sql.executor;

import com.bone.metadata.sdk.support.cache.FieldCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 智能行映射器 - 提供自动字段映射和类型转换功能
 */
public class SmartRowMapper<T> implements RowMapper<T> {
    private static final Logger log = LoggerFactory.getLogger(SmartRowMapper.class);

    private final Class<T> mappedClass;
    private final ConcurrentMap<Class<?>, Constructor<?>> constructorCache = new ConcurrentHashMap<>();

    public SmartRowMapper(Class<T> mappedClass) {
        this.mappedClass = Objects.requireNonNull(mappedClass, "Mapped class must not be null");
    }

    /**
     * 将 ResultSet 的一行映射为实体对象
     */
    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        try {
            T result = createInstance();
            ResultSetMetaData metaData = rs.getMetaData();

            // 遍历所有列并设置对应的字段
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                String columnName = metaData.getColumnLabel(i);
                try {
                    // 1. 先尝试下划线转驼峰（或通过 @Column 映射）
                    Field field = FieldCache.getFieldByColumn(mappedClass, columnName);
                    // 2. 没找到再按原样匹配驼峰
                    if (field == null) {
                        field = FieldCache.getFieldByName(mappedClass, columnName);
                    }

                    if (field != null && !rs.wasNull()) {
                        field.setAccessible(true);
                        Object value = rs.getObject(i);  // 获取数据库列的值
                        
                        // 类型转换
                        try {
                            value = TypeConverter.convert(value, field.getType());
                            field.set(result, value);  // 设置字段值
                        } catch (ClassCastException e) {
                            log.warn("Type conversion failed for column '{}' to field '{}': {}", 
                                    columnName, field.getName(), e.getMessage());
                            // 尝试兼容转换
                            if (value != null) {
                                try {
                                    String stringValue = value.toString();
                                    value = TypeConverter.convertFromString(stringValue, field.getType());
                                    field.set(result, value);
                                } catch (Exception ex) {
                                    log.debug("Compatible conversion also failed: {}", ex.getMessage());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Error mapping column '{}' at row {}", columnName, rowNum, e);
                    // 继续处理其他列，不中断整个映射过程
                }
            }
            return result;
        } catch (Exception e) {
            throw new SQLException("Failed to map row " + rowNum + " to class " + mappedClass.getName(), e);
        }
    }

    /**
     * 创建目标类的实例
     */
    private T createInstance() throws SQLException {
        try {
            // 从缓存获取构造函数
            Constructor<T> constructor = (Constructor<T>) constructorCache.computeIfAbsent(mappedClass, 
                    clazz -> {
                        try {
                            Constructor<?> ctor = clazz.getDeclaredConstructor();
                            ctor.setAccessible(true);
                            return ctor;
                        } catch (NoSuchMethodException e) {
                            throw new RuntimeException("No default constructor found for " + clazz.getName(), e);
                        }
                    });
            
            return constructor.newInstance();
        } catch (Exception e) {
            throw new SQLException("Failed to instantiate " + mappedClass.getName() + 
                    ". Ensure class has a no-args constructor.", e);
        }
    }

}