package com.bone.metadata.sdk.sql.dialect;

import com.bone.metadata.sdk.domain.enums.DatabaseType;
import org.springframework.dao.DataAccessException;

/**
 * H2 方言实现（仅供测试/开发时使用）
 */
public class H2ColumnAllocationDialect implements ColumnAllocationDialect {

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.H2;
    }

    @Override
    public String getSkipLockedClause() {
        // H2 不支持 SKIP LOCKED，只能简单 FOR UPDATE
        return " ";//" FOR UPDATE";
    }

    @Override
    public String getForUpdateClause() {
        return " ";// " FOR UPDATE";
    }

    @Override
    public String getInsertSQL() {
        return """
            MERGE INTO column_allocation (
                tenant_id, app_code, biz_identity_code, entity_type,
                data_type, column_name, column_index, status, version,
                created_by, created_at, updated_by, updated_at
            )
            KEY (tenant_id, app_code, biz_identity_code, entity_type, data_type, column_index)
            VALUES (
                :tenantId, :appCode, :bizIdentityCode, :entityType,
                :dataType, :columnName, :columnIndex, :status, :version,
                :createdBy, :createdAt, :updatedBy, :updatedAt
            )
            """;
    }

    @Override
    public String getPurgeSQL(int days) {
        // H2 可以直接使用日期函数
        return """
            DELETE FROM column_allocation
            WHERE status = 'RECYCLED'
              AND recycled_at < DATEADD('DAY', -%d, CURRENT_TIMESTAMP())
            """.formatted(days);
    }

    @Override
    public String getArchiveSQL(int days) {
        return """
            INSERT INTO column_allocation_history
            SELECT *, CURRENT_TIMESTAMP() AS archived_at
            FROM column_allocation
            WHERE updated_at < DATEADD('DAY', -%d, CURRENT_TIMESTAMP());
            
            DELETE FROM column_allocation
            WHERE updated_at < DATEADD('DAY', -%d, CURRENT_TIMESTAMP());
            """.formatted(days, days);
    }

    @Override
    public boolean isLockTimeout(DataAccessException e) {
        Throwable cause = e.getCause();
        return cause != null && cause.getMessage().toLowerCase().contains("timeout");
    }
}