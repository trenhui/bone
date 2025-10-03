// 文件：MySqlColumnAllocationDialect.java
package com.bone.metadata.sdk.sql.dialect;

import com.bone.metadata.sdk.domain.enums.DatabaseType;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;

import java.sql.SQLTimeoutException;

/**
 * 针对 MySQL（8.0+）的 ColumnAllocationDialect 实现，
 * 要求把 LIMIT 放在 FOR UPDATE SKIP LOCKED 之前。
 */
public class MySqlColumnAllocationDialect implements ColumnAllocationDialect {

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.MYSQL;
    }

    /**
     * 因为 LIMIT … OFFSET … 要放在 FOR UPDATE SKIP LOCKED 之前，
     * 所以这里不再返回完整的 “FOR UPDATE SKIP LOCKED”。
     * 而是在 repository 里，先拼 LIMIT，再拼下面这个锁定子句。
     */
    @Override
    public String getSkipLockedClause() {
        return " FOR UPDATE SKIP LOCKED";
    }

    @Override
    public String getForUpdateClause() {
        return " FOR UPDATE";
    }

    /**
     * MySQL 下的批量插入 SQL（修正 ON DUPLICATE KEY UPDATE 的列名）。
     */
    @Override
    public String getInsertSQL() {
        return """
        INSERT INTO column_allocation (
          tenant_id, app_code, biz_identity_code, entity_type,
          data_type, column_name, column_index, status, version, 
          created_by, created_at, updated_at, updated_by
        ) VALUES (
          :tenantId, :appCode, :bizIdentityCode, :entityType,
          :dataType, :columnName, :columnIndex, :status, :version,
          :createdBy, :createdAt, :updatedAt, :updatedBy
        )
        ON DUPLICATE KEY UPDATE
          status     = VALUES(status),
          version    = version + 1,
          updated_by = VALUES(updated_by),
          updated_at = VALUES(updated_at)
        """;
    }

    @Override
    public String getPurgeSQL(int days) {
        return """
            DELETE FROM column_allocation
            WHERE status = 'RECYCLED'
              AND recycled_at < DATE_SUB(NOW(), INTERVAL %d DAY)
            """.formatted(days);
    }

    @Override
    public String getArchiveSQL(int days) {
        return """
            INSERT INTO column_allocation_history
            SELECT *, CURRENT_TIMESTAMP(3) AS archived_at
            FROM column_allocation
            WHERE updated_at < DATE_SUB(NOW(), INTERVAL %d DAY);
            
            DELETE FROM column_allocation
            WHERE updated_at < DATE_SUB(NOW(), INTERVAL %d DAY);
            """.formatted(days, days);
    }

    @Override
    public boolean isLockTimeout(DataAccessException e) {
        if (e instanceof QueryTimeoutException) return true;
        Throwable cause = e.getCause();
        return cause instanceof SQLTimeoutException
                || (cause != null && cause.getMessage().contains("Lock wait timeout"));
    }
}
