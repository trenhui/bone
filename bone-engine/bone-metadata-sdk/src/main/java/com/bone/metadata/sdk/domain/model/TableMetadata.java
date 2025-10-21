package com.bone.metadata.sdk.domain.model;

import com.bone.metadata.sdk.domain.enums.ExtensionMode;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;

import java.util.List;

public class TableMetadata {
    private final String name;  // 表名
    private final List<ColumnMetadata> columns;  // 表的列元数据
    
    // 显式添加getter方法以确保编译器能找到
    public String getName() {
        return name;
    }
    
    public List<ColumnMetadata> getColumns() {
        return columns;
    }
    @Getter(AccessLevel.NONE)
    private final ColumnMetadata primaryKey;  // 缓存的主键列
    @Getter(AccessLevel.NONE)
    private final ColumnMetadata version;  // 缓存版本列
    @Getter(AccessLevel.NONE)
    private final ColumnMetadata softDeleted;  // 缓存软删列
    private ExtensionMode extensionMode=ExtensionMode.RESERVED_COLUMNS;

    public TableMetadata(String name, List<ColumnMetadata> columns) {
        this.name = name;
        this.columns = columns;
        this.primaryKey = columns.stream()
                .filter(ColumnMetadata::isPrimaryKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No primary key found for table " + name));

        this.version = columns.stream()
                .filter(ColumnMetadata::isVersion)
                .findFirst().orElse(null);
        ;

        this.softDeleted = columns.stream()
                .filter(ColumnMetadata::isSoftDeleted)
                .findFirst().orElse(null);

    }



    public ColumnMetadata getPrimaryKey() {
        if (primaryKey == null) {
            throw new IllegalStateException(
                    String.format("表 %s 未定义主键列", name)
            );
        }
        return primaryKey;
    }

    public ColumnMetadata getSoftDeleteColumn() {
        if (softDeleted == null) {
            throw new IllegalStateException(
                    String.format("表 %s 未定义软删除列", name)
            );
        }
        return softDeleted;
    }

    public boolean isSoftDeletable() {
        return softDeleted != null;
    }
}
