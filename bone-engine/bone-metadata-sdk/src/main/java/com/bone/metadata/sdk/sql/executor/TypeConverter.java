package com.bone.metadata.sdk.sql.executor;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 类型转换工具类，基于策略模式实现，支持线程安全、可扩展的类型转换。
 */
public final class TypeConverter {

    private static final Map<Class<?>, Converter<?>> CONVERTERS = new ConcurrentHashMap<>(64);
    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    );

    // region 异常定义
    public static class TypeConversionException extends RuntimeException {
        public TypeConversionException(String message) {
            super(message);
        }
        
        public TypeConversionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class UnsupportedConversionException extends RuntimeException {
        public UnsupportedConversionException(Class<?> sourceType, Class<?> targetType) {
            super(String.format("Unsupported conversion from %s to %s", sourceType.getName(), targetType.getName()));
        }
    }
    // endregion

    // region 转换器接口
    @FunctionalInterface
    public interface Converter<T> {
        T convert(Object value, Class<T> targetType) throws TypeConversionException;
    }
    // endregion

    // region 静态初始化 - 内置转换器
    static {
        registerConverter(Boolean.class, new BooleanConverter());
        registerConverter(boolean.class, new BooleanConverter());
        registerConverter(Integer.class, new IntegerConverter());
        registerConverter(int.class, new IntegerConverter());
        registerConverter(Long.class, new LongConverter());
        registerConverter(long.class, new LongConverter());
        registerConverter(Double.class, new DoubleConverter());
        registerConverter(double.class, new DoubleConverter());
        registerConverter(String.class, new StringConverter());
        registerConverter(Date.class, new DateConverter());
        registerConverter(LocalDateTime.class, new LocalDateTimeConverter());
        registerConverter(LocalDate.class, new LocalDateConverter());
        registerConverter(Timestamp.class, new TimestampConverter());
        registerConverter(Long.class, new BigIntegerConverter());
    }
    // endregion

    // region 公共API
    public static <T> void registerConverter(Class<T> targetType, Converter<T> converter) {
        CONVERTERS.put(targetType, converter);
    }

    @SuppressWarnings("unchecked")
    public static <T> T convert(Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }

        // BigInteger → Long 的特殊处理
        if (value instanceof BigInteger && (targetType == Object.class || targetType == Long.class || targetType == Long.TYPE)) {
            return (T) Long.valueOf(((BigInteger) value).longValue());
        }

        if (targetType.isInstance(value)) {
            return targetType.cast(value);
        }

        Converter<T> converter = (Converter<T>) CONVERTERS.get(targetType);
        if (converter == null) {
            throw new UnsupportedConversionException(value.getClass(), targetType);
        }
        try {
            return converter.convert(value, targetType);
        } catch (Exception e) {
            throw new TypeConversionException(
                    String.format("Type conversion failed: value=%s, sourceType=%s, targetType=%s, converter=%s",
                            value, value.getClass().getName(), targetType.getName(), converter.getClass().getSimpleName()), e);
        }
    }
    // endregion

    // region 内置转换器实现
    static class BooleanConverter implements Converter<Boolean> {
        @Override
        public Boolean convert(Object value, Class<Boolean> targetType) {
            if (value instanceof Number) return ((Number) value).intValue() != 0;
            if (value instanceof String) {
                String str = ((String) value).trim().toLowerCase();
                return str.matches("true|yes|on|1");
            }
            throw new TypeConversionException("Unsupported boolean conversion: " + value);
        }
    }

    static class IntegerConverter implements Converter<Integer> {
        @Override
        public Integer convert(Object value, Class<Integer> targetType) {
            if (value instanceof Boolean) {
                return (Boolean) value ? 1 : 0;
            }
            if (value instanceof Short) {
                return ((Short) value).intValue();
            }
            return parseNumber(value, Integer.class);
        }
    }

    static class LongConverter implements Converter<Long> {
        @Override
        public Long convert(Object value, Class<Long> targetType) {
            if (value instanceof Boolean) {
                return (Boolean) value ? 1L : 0L;
            }
            return parseNumber(value, Long.class);
        }
    }

    static class BigIntegerConverter implements Converter<Long> {
        @Override
        public Long convert(Object value, Class<Long> targetType) {
            return parseNumber(value, Long.class);
        }
    }

    static class DoubleConverter implements Converter<Double> {
        @Override
        public Double convert(Object value, Class<Double> targetType) {
            return parseNumber(value, Double.class);
        }
    }

    static class StringConverter implements Converter<String> {
        @Override
        public String convert(Object value, Class<String> targetType) {

            // 处理其他 Clob 类型（通用处理）
            if (value instanceof java.sql.Clob) {
                try {
                    java.sql.Clob clob = (java.sql.Clob) value;
                    long length = clob.length();
                    return clob.getSubString(1, (int) length);
                } catch (Exception e) {
                    return value.toString();
                }
            }

            // 处理 NClob 类型
            if (value instanceof java.sql.NClob) {
                try {
                    java.sql.NClob nclob = (java.sql.NClob) value;
                    long length = nclob.length();
                    return nclob.getSubString(1, (int) length);
                } catch (Exception e) {
                    return value.toString();
                }
            }

            // 处理 DmdbNClob 类型
            if (value.getClass().getName().equals("dm.jdbc.driver.DmdbNClob")) {
                try {
                    // 使用反射调用 getSubString 方法
                    java.lang.reflect.Method method = value.getClass().getMethod("getSubString", long.class, int.class);
                    long length = (long) value.getClass().getMethod("length").invoke(value);
                    return (String) method.invoke(value, 1L, (int) length);
                } catch (Exception e) {
                    // 如果反射失败，回退到 toString()
                    return value.toString();
                }
            }

            return value.toString();
        }
    }

    static class DateConverter implements Converter<Date> {
        @Override
        public Date convert(Object value, Class<Date> targetType) {
            if (value instanceof Long) return new Date((Long) value);
            if (value instanceof String) return parseDate((String) value);
            if (value instanceof TemporalAccessor) {
                if (value instanceof LocalDateTime) {
                    LocalDateTime localDateTime = (LocalDateTime) value;
                    ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
                    return Date.from(zonedDateTime.toInstant());
                }
                if (value instanceof Instant) {
                    return Date.from((Instant) value);
                }
                throw new TypeConversionException("Unsupported TemporalAccessor type: " + value.getClass());
            }
            throw new TypeConversionException("Unsupported date conversion: " + value);
        }

        private Date parseDate(String value) {
            return parseDateTime(value, formatter -> {
                try {
                    TemporalAccessor temporal = formatter.parse(value);
                    return Date.from(Instant.from(temporal));
                } catch (DateTimeParseException ignored) {
                    return null;
                }
            });
        }
    }

    static class LocalDateTimeConverter implements Converter<LocalDateTime> {
        @Override
        public LocalDateTime convert(Object value, Class<LocalDateTime> targetType) {
            if (value instanceof String) return parseLocalDateTime((String) value);
            if (value instanceof Long) {
                return LocalDateTime.ofInstant(Instant.ofEpochMilli((Long) value), ZoneId.systemDefault());
            }
            if (value instanceof Timestamp) return ((Timestamp) value).toLocalDateTime();
            throw new TypeConversionException("Unsupported LocalDateTime conversion: " + value);
        }
    }

    static class LocalDateConverter implements Converter<LocalDate> {
        @Override
        public LocalDate convert(Object value, Class<LocalDate> targetType) {
            if (value instanceof String) return parseLocalDate((String) value);
            if (value instanceof java.sql.Date) return ((java.sql.Date) value).toLocalDate();
            throw new TypeConversionException("Unsupported LocalDate conversion: " + value);
        }
    }

    static class TimestampConverter implements Converter<Timestamp> {
        @Override
        public Timestamp convert(Object value, Class<Timestamp> targetType) {
            if (value instanceof Long) return new Timestamp((Long) value);
            if (value instanceof String) return Timestamp.valueOf(TypeConverter.parseLocalDateTime((String) value));
            if (value instanceof LocalDateTime) return Timestamp.valueOf((LocalDateTime) value);
            throw new TypeConversionException("Unsupported Timestamp conversion: " + value);
        }
    }
    // endregion

    // region 通用的日期解析方法
    private static <T> T parseDateTime(String value, Function<DateTimeFormatter, T> parseFunction) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            T result = parseFunction.apply(formatter);
            if (result != null) {
                return result;
            }
        }
        throw new TypeConversionException("Unparseable date/time: " + value);
    }
    // endregion

    /**
     * 从字符串转换到指定类型
     */
    @SuppressWarnings("unchecked")
    public static <T> T convertFromString(String value, Class<T> targetType) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        // 对于原始类型的特殊处理
        if (targetType.isPrimitive()) {
            if (targetType == boolean.class) return (T) Boolean.valueOf(Boolean.parseBoolean(value));
            if (targetType == byte.class) return (T) Byte.valueOf(value);
            if (targetType == short.class) return (T) Short.valueOf(value);
            if (targetType == int.class) return (T) Integer.valueOf(value);
            if (targetType == long.class) return (T) Long.valueOf(value);
            if (targetType == float.class) return (T) Float.valueOf(value);
            if (targetType == double.class) return (T) Double.valueOf(value);
            if (targetType == char.class) {
                if (value.length() == 1) return (T) Character.valueOf(value.charAt(0));
                throw new TypeConversionException("Invalid char value: " + value);
            }
        }
        
        // 对于包装类型和其他常见类型
        if (targetType == Boolean.class) return (T) Boolean.valueOf(value.trim().toLowerCase().matches("true|yes|on|1"));
        if (targetType == Byte.class) return (T) Byte.valueOf(value);
        if (targetType == Short.class) return (T) Short.valueOf(value);
        if (targetType == Integer.class) return (T) Integer.valueOf(value);
        if (targetType == Long.class) return (T) Long.valueOf(value);
        if (targetType == Float.class) return (T) Float.valueOf(value);
        if (targetType == Double.class) return (T) Double.valueOf(value);
        if (targetType == String.class) return (T) value;
        
        // 对于日期类型
        if (targetType == LocalDateTime.class) return (T) parseLocalDateTime(value);
        if (targetType == LocalDate.class) return (T) parseLocalDate(value);
        if (targetType == Date.class) {
            DateConverter converter = new DateConverter();
            return (T) converter.convert(value, Date.class);
        }
        if (targetType == Timestamp.class) {
            TimestampConverter converter = new TimestampConverter();
            return (T) converter.convert(value, Timestamp.class);
        }
        
        throw new UnsupportedConversionException(String.class, targetType);
    }
    
    // region 通用的数值转换方法
    private static <T extends Number> T parseNumber(Object value, Class<T> targetType) {
        if (value instanceof Number) {
            if (targetType.isInstance(value)) {
                return targetType.cast(value);
            } else if (targetType == Integer.class && value instanceof Short) {
                return targetType.cast(((Short) value).intValue());
            } else {
                return targetType.cast(((Number) value).doubleValue());
            }
        }
        if (value instanceof String) {
            try {
                if (targetType == Integer.class) {
                    return targetType.cast(Integer.parseInt((String) value));
                } else if (targetType == Long.class) {
                    return targetType.cast(Long.parseLong((String) value));
                } else if (targetType == Double.class) {
                    return targetType.cast(Double.parseDouble((String) value));
                }
            } catch (NumberFormatException e) {
                throw new TypeConversionException("Invalid " + targetType.getSimpleName() + " format: " + value, e);
            }
        }
        throw new TypeConversionException("Unsupported number conversion: " + value);
    }
    // endregion

    // region 辅助方法
    public static LocalDateTime parseLocalDateTime(String value) {
        return parseDateTime(value, formatter -> {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        });
    }
    
    public static LocalDate parseLocalDate(String value) {
        return parseDateTime(value, formatter -> {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        });
    }
    // endregion
}