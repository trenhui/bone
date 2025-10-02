package com.bone.metadata.sdk.extension;

import com.bone.metadata.sdk.domain.enums.DataType;

/**
 * 默认的列命名策略：使用前缀 ext_<type>_<2 位序号>。
 * 例如 DataType.STRING 和 index=2 -> "ext_string_02"。
 */
public class DefaultColumnNamingStrategy implements ColumnNamingStrategy {
    private static final String PREFIX = "ext";

    @Override
    public String generate(DataType type, int index) {
        // 序号格式化为至少2位，不足前面补 '0'
        String idx = String.format("%02d", index);
        return String.join("_", PREFIX, type.getSymbol(), idx);
    }
}