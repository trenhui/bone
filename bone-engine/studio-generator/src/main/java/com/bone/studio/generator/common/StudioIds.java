package com.bone.studio.generator.common;

/**
 * API 路径中的 ID 与仓储 Long 主键互转。
 */
public final class StudioIds {

    private StudioIds() {
    }

    public static Long parseRequired(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id 不能为空");
        }
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("id 格式无效: " + id, e);
        }
    }

    public static String toExternal(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    public static String dataSourceKey(Long dataSourceId) {
        return toExternal(dataSourceId);
    }
}
