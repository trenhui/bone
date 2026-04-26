package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 区间类型枚举
 */
@Getter
public enum RangeTypeEnum {

    LEFT_OPEN("LEFT_OPEN", "左开右闭", "(", "]"),

    RIGHT_OPEN("RIGHT_OPEN", "左闭右开", "[", ")"),

    BOTH_OPEN("BOTH_OPEN", "左开右开", "(", ")"),

    BOTH_CLOSED("BOTH_CLOSED", "左闭右闭", "[", "]"),

    ;

    private final String code;
    private final String value;
    private final String leftSymbol;
    private final String rightSymbol;

    RangeTypeEnum(String code, String value, String leftSymbol, String rightSymbol) {
        this.code = code;
        this.value = value;
        this.leftSymbol = leftSymbol;
        this.rightSymbol = rightSymbol;
    }

    public static RangeTypeEnum getByCode(String code) {
        for (RangeTypeEnum e : RangeTypeEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }


    /**
     * 检查数值是否在区间内
     */
    public boolean isValueInRange(Object value, BigDecimal lower, BigDecimal upper) {
        if (lower.compareTo(upper) > 0) {
            throw new IllegalArgumentException("下限值不能大于上限值");
        }

        BigDecimal numericValue = convertToBigDecimal(value);

        boolean leftCondition = this.isLeftOpen()
                ? numericValue.compareTo(lower) > 0  // 左开：value > lower
                : numericValue.compareTo(lower) >= 0; // 左闭：value >= lower

        boolean rightCondition = this.isRightOpen()
                ? numericValue.compareTo(upper) < 0  // 右开：value < upper
                : numericValue.compareTo(upper) <= 0; // 右闭：value <= upper

        return leftCondition && rightCondition;
    }

    /**
     * 生成区间表达式字符串
     */
    public String formatInterval(BigDecimal lower, BigDecimal upper) {
        return String.format("%s%s,%s%s",
                leftSymbol,
                lower.toString(),
                upper.toString(),
                rightSymbol);
    }

    // 辅助判断方法
    private boolean isLeftOpen() {
        return this == LEFT_OPEN || this == BOTH_OPEN;
    }

    private boolean isRightOpen() {
        return this == RIGHT_OPEN || this == BOTH_OPEN;
    }


    // 原始数值校验方法（整合之前的逻辑）
    public String makeRangeString(Object value, BigDecimal lower, BigDecimal upper) {
        if (this.isValueInRange(value, lower, upper)) {
            return formatInterval(lower, upper);
        }

        return null;
    }

    private static BigDecimal convertToBigDecimal(Object value) {
        // 这里复用之前实现的类型转换逻辑
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        throw new IllegalArgumentException("不支持的数字类型");
    }
}
