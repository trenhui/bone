package com.bone.metadata.sdk.domain.enums;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum DataType {
    STRING("str","字符串类型"),
    NUMBER("num","高精度数字包括浮点数、DECIMAL"),
    TEXT("text","长文本类型"),
    DATE("date","日期类型"),
    BOOLEAN("boolean","布尔类型"),
    INTEGER("int","整数类型"),
    JSON("json","JSON 类型"),
    XML("xml","XML 类型"),
    GEO("geo","地理坐标"),;

    private final String symbol;
    private final String description;

    DataType(String symbol, String description) {
        this.symbol = symbol;
        this.description = description;
    }

    /**
     * 根据操作符符号获取对应的枚举类型。
     *
     * @param symbol 操作符符号
     * @return 对应的 DataType 枚举值
     */
    public static DataType getBySymbol(String symbol) {
        return Arrays.stream(values())
                .filter(op -> op.getSymbol().equals(symbol))
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据操作符的代码获取枚举值（支持大小写不敏感）。
     *
     * @param code 枚举代码
     * @return 对应的 DataType 枚举值
     */
    public static DataType fromCode(String code) {
        return Arrays.stream(values())
                .filter(op -> op.name().equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }
}