package com.bone.metadata.catalog.common;

import java.util.List;

/**
 * 逆向建模导入结果。
 *
 * @param entityId 新实体 ID；{@code dryRun=true} 时为 null
 * @param entityCode 实体编码
 * @param tableName 物理表名
 * @param importedFields 实际建模字段数
 * @param skippedColumns 被跳过的列（保留列），供前端提示「哪些由平台托管、不建模」
 * @param dryRun 是否试运行
 */
public record ImportMetaEntityResult(
    Long entityId,
    String entityCode,
    String tableName,
    int importedFields,
    List<String> skippedColumns,
    boolean dryRun) {}
