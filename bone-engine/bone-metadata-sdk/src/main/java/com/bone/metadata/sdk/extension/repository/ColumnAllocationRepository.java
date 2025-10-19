package com.bone.metadata.sdk.extension.repository;

import com.bone.metadata.sdk.domain.enums.DataType;
import com.bone.metadata.sdk.domain.enums.AllocationColumnStatus;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.ColumnAllocation;
import com.bone.metadata.sdk.sql.dialect.ColumnAllocationDialect;
import com.bone.metadata.sdk.sql.dialect.ColumnAllocationDialectFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ColumnAllocationRepository 负责所有与 column_allocation 表相关的持久化操作。
 */
@Repository
public class ColumnAllocationRepository {

    private final NamedParameterJdbcOperations jdbc;
    private final ColumnAllocationDialectFactory dialectFactory;
    
    public ColumnAllocationRepository(NamedParameterJdbcOperations jdbc, ColumnAllocationDialectFactory dialectFactory) {
        this.jdbc = jdbc;
        this.dialectFactory = dialectFactory;
    }

    // 查询可回收列（RECYCLED），不含分页/锁定，后面拼接 LIMIT … FOR UPDATE SKIP LOCKED
    private static final String FIND_RECYCLED_SQL = """
        SELECT * FROM column_allocation
        WHERE tenant_id = :tenantId
          AND app_code = :appCode
          AND biz_identity_code = :bizIdentityCode
          AND entity_type = :entityType
          AND data_type = :dataType
          AND status = 'RECYCLED'
        ORDER BY column_index ASC
        """;

    // 查询最大索引
    private static final String FIND_MAX_INDEX_SQL = """
        SELECT COALESCE(MAX(column_index), 0)
        FROM column_allocation
        WHERE tenant_id = :tenantId
          AND app_code = :appCode
          AND biz_identity_code = :bizIdentityCode
          AND entity_type = :entityType
          AND data_type = :dataType
        """;

    // 更新状态（带乐观锁校验 version）
    private static final String UPDATE_STATUS_SQL = """
        UPDATE column_allocation
        SET status       = :status,
            version      = version + 1,
            updated_at   = CURRENT_TIMESTAMP(3),
            updated_by   = :updatedBy,
            recycled_at  = :recycledAt
        WHERE id          = :id
          AND version     = :currentVersion
        """;

    // 根据 ID 锁定查询
    private static final String FIND_BY_ID_FOR_UPDATE_SQL = """
        SELECT * FROM column_allocation
        WHERE id = :id
        FOR UPDATE
        """;

    /**
     * 查找“RECYCLED”状态的列，并且立刻用 LIMIT … FOR UPDATE SKIP LOCKED 去锁定一批（防止并发争抢）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ColumnAllocation> findRecycledColumnsWithLock(
            AllocationContext ctx, DataType dataType, int limit) {

        ColumnAllocationDialect dialect = dialectFactory.currentDialect();

        // 先构造基础的上下文参数，然后复制一份加上 limit 和 offset
        Map<String, Object> baseParams = createParams(ctx, dataType);
        Map<String, Object> params = new HashMap<>(baseParams);
        params.put("limit", limit);
        params.put("offset", 0); // 固定 0，如果需要分页，可以改成动态值

        // 正确顺序：先拼 LIMIT :limit OFFSET :offset，再拼锁定子句 FOR UPDATE SKIP LOCKED
        String sql = FIND_RECYCLED_SQL
                + dialect.buildPagination(limit, 0)    // 产生 “ LIMIT :limit OFFSET :offset”
                + dialect.getSkipLockedClause();       // 再加 “ FOR UPDATE SKIP LOCKED”

        return jdbc.query(sql, params, new BeanPropertyRowMapper<>(ColumnAllocation.class));
    }

    /**
     * 查询当前的最大 column_index（行级锁，防止并发写入）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int findCurrentMaxIndexWithLock(AllocationContext ctx, DataType dataType) {
        ColumnAllocationDialect dialect = dialectFactory.currentDialect();
        String sql = FIND_MAX_INDEX_SQL + dialect.getForUpdateClause();
        Integer result = jdbc.queryForObject(sql, createParams(ctx, dataType), Integer.class);
        return result != null ? result : 0;
    }

    /**
     * 批量插入新 ColumnAllocation 记录
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int[] batchInsert(List<ColumnAllocation> allocations) {
        ColumnAllocationDialect dialect = dialectFactory.currentDialect();
        SqlParameterSource[] batch = allocations.stream()
                .map(this::toSqlParameterSource)
                .toArray(SqlParameterSource[]::new);
        return jdbc.batchUpdate(dialect.getInsertSQL(), batch);
    }

    /**
     * 查找并锁定回收列后，更新其状态为 IN_USE
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recycleToInUse(Long id, int currentVersion, String operator) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("status", AllocationColumnStatus.IN_USE.name());
        params.put("currentVersion", currentVersion);
        params.put("updatedBy", operator);
        params.put("recycledAt", null);

        int updated = jdbc.update(UPDATE_STATUS_SQL, params);
        return updated > 0;
    }

    /**
     * 更新分配状态（IN_USE → RECYCLED 或 RECYCLED → IN_USE 等）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int updateStatus(Long id,
                            AllocationColumnStatus newStatus,
                            int currentVersion,
                            String updatedBy,
                            Timestamp recycledAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", newStatus.name())
                .addValue("currentVersion", currentVersion)
                .addValue("updatedBy", updatedBy)
                .addValue("recycledAt", recycledAt);

        return jdbc.update(UPDATE_STATUS_SQL, params);
    }

    /**
     * 根据 ID 锁定查询 ColumnAllocation
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ColumnAllocation findByIdForUpdate(Long id) {
        List<ColumnAllocation> result = jdbc.query(
                FIND_BY_ID_FOR_UPDATE_SQL,
                Map.of("id", id),
                new BeanPropertyRowMapper<>(ColumnAllocation.class));
        return result.isEmpty() ? null : result.get(0);
    }

    /**
     * 清理过期的 RECYCLED 状态列
     */
    @Transactional
    public int purgeOldRecycledColumns(int days) {
        ColumnAllocationDialect dialect = dialectFactory.currentDialect();
        String sql = dialect.getPurgeSQL(days);
        return jdbc.update(sql, Map.of());
    }

    /**
     * 归档旧数据到历史表
     */
    @Transactional
    public int archiveOldColumns(int days) {
        ColumnAllocationDialect dialect = dialectFactory.currentDialect();
        String sql = dialect.getArchiveSQL(days);
        return jdbc.update(sql, Map.of());
    }

    /**
     * 构造通用查询参数 Map（只读）
     */
    private Map<String, Object> createParams(AllocationContext ctx, DataType dataType) {
        return Map.of(
                "tenantId", ctx.getTenantId(),
                "appCode", ctx.getAppCode(),
                "bizIdentityCode", ctx.getBizIdentityCode(),
                "entityType", ctx.getEntityType(),
                "dataType", dataType.name()
        );
    }

    /**
     * 将 ColumnAllocation 对象转换为 SqlParameterSource
     */
    private SqlParameterSource toSqlParameterSource(ColumnAllocation a) {
        return new MapSqlParameterSource()
                .addValue("tenantId", a.getTenantId())
                .addValue("appCode", a.getAppCode())
                .addValue("bizIdentityCode", a.getBizIdentityCode())
                .addValue("entityType", a.getEntityType())
                .addValue("dataType", a.getDataType().name())
                .addValue("columnName", a.getColumnName())
                .addValue("columnIndex", a.getColumnIndex())
                .addValue("status", a.getStatus().name())
                .addValue("version", a.getVersion())
                // 使用 Java 的当前时间
                .addValue("createdAt", a.getCreatedAt())  // 从对象获取创建时间
                .addValue("updatedAt", a.getUpdatedAt())  // 从对象获取更新时间
                .addValue("createdBy", a.getCreatedBy())
                .addValue("updatedBy", a.getUpdatedBy());
    }
}
