package com.bone.metadata.sdk.sql.dialect;

import com.bone.metadata.sdk.domain.enums.DatabaseType;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;

/**
 * PostgreSQL 方言实现
 */
public class PostgresColumnAllocationDialect implements ColumnAllocationDialect {

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.POSTGRESQL;
    }

    @Override
    public String getSkipLockedClause() {
        // PostgreSQL 在 SELECT … FOR UPDATE 后面直接使用 SKIP LOCKED
        return " FOR UPDATE SKIP LOCKED";
    }

    @Override
    public String getForUpdateClause() {
        return " FOR UPDATE";
    }

    @Override
    public String getInsertSQL() {
        return """
                INSERT INTO column_allocation (
                  tenant_id, app_code, biz_identity_code, entity_type,
                  data_type, column_name, column_index, status, version, created_at, created_by
                ) VALUES (
                  :tenantId, :appCode, :bizIdentityCode, :entityType,
                  :dataType, :columnName, :columnIndex, :status, :version, CURRENT_TIMESTAMP(3), :createdBy
                )
                ON CONFLICT (tenant_id, app_code, biz_identity_code, entity_type, column_name)
                DO UPDATE SET
                  status = EXCLUDED.status,
                  version = column_allocation.version + 1,
                  updated_at = CURRENT_TIMESTAMP(3),
                  updated_by = EXCLUDED.created_by,
                  recycled_at = CASE WHEN EXCLUDED.status = 'RECYCLED'
                                    THEN CURRENT_TIMESTAMP(3) ELSE NULL END
                """;
    }

    @Override
    public String getPurgeSQL(int days) {
        return """
                DELETE FROM column_allocation 
                WHERE status = 'RECYCLED'
                  AND recycled_at < (CURRENT_TIMESTAMP - INTERVAL '%d days')
                """.formatted(days);
    }

    @Override
    public String getArchiveSQL(int days) {
        return """
                WITH moved_rows AS (
                  DELETE FROM column_allocation
                  WHERE updated_at < (CURRENT_TIMESTAMP - INTERVAL '%d days')
                  RETURNING *, CURRENT_TIMESTAMP(3) AS archived_at
                )
                INSERT INTO column_allocation_history
                SELECT * FROM moved_rows;
                """.formatted(days);
    }

    @Override
    public boolean isLockTimeout(DataAccessException e) {
        if (e instanceof QueryTimeoutException) return true;
        Throwable cause = e.getCause();
        return true;
//        return (cause instanceof PSQLException
//                && ((PSQLException) cause).getSQLState().equals("55P03"));
    }
}