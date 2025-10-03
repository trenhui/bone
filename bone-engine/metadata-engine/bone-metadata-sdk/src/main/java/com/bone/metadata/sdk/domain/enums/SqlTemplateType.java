package com.bone.metadata.sdk.domain.enums;

/**
 * SQL 模板格式枚举，定义支持的模板类型及其符号表示。
 */
public enum SqlTemplateType {
    SQL("sql"),
    MYBATIS("mybatis"),
    YAML_SQL("dynamic");

    private final String symbol;

    SqlTemplateType(String symbol) {
        this.symbol = symbol;
    }

    /**
     * 获取模板类型的符号表示。
     * @return 符号字符串。
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * 根据符号查找模板类型。
     * @param symbol 符号字符串。
     * @return 对应的模板类型，若未找到则返回 null。
     */
    public static SqlTemplateType fromSymbol(String symbol) {
        for (SqlTemplateType type : values()) {
            if (type.symbol.equalsIgnoreCase(symbol)) {
                return type;
            }
        }
        return null;
    }
}