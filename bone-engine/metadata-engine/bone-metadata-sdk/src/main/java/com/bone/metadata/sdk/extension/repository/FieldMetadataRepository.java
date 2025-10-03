package com.bone.metadata.sdk.extension.repository;

import com.bone.core.id.IdGenerator;
import com.bone.metadata.sdk.support.config.MetadataSdkContext;
import com.bone.metadata.sdk.domain.enums.DatabaseType;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 合并后的 FieldMetadataRepository：
 * - 提供 findByContextAndNames、findByContext、saveAll 三个核心方法
 * - 同时保留单条 upsert、批量 upsert、按路径查询等常用操作
 */
@Repository
public class FieldMetadataRepository {

    private final NamedParameterJdbcOperations jdbc;

    public FieldMetadataRepository(NamedParameterJdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    //================ Interface Methods =================//

    /**
     * 查询指定上下文、指定字段名集合对应的元数据。
     */
    public List<FieldMetadata> findByContextAndNames(AllocationContext ctx, List<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }
        String sql = "SELECT * FROM field_metadata "
                + "WHERE tenant_id = :tenantId "
                + "  AND app_code = :appCode "
                + "  AND biz_identity_code = :bizIdentityCode "
                + "  AND entity_type = :entityType "
                + "  AND deleted = false "
                + "  AND is_extension = true "
                + "  AND name IN (:names)";
        MapSqlParameterSource params = commonParams(ctx)
                .addValue("names", names);
        return jdbc.query(sql, params, new BeanPropertyRowMapper<>(FieldMetadata.class));
    }

    /**
     * 查询指定上下文下所有扩展字段元数据。
     */
    public List<FieldMetadata> findByContext(AllocationContext ctx) {
        String sql = "SELECT * FROM field_metadata "
                + "WHERE tenant_id = :tenantId "
                + "  AND app_code = :appCode "
                + "  AND biz_identity_code = :bizIdentityCode "
                + "  AND entity_type = :entityType "
                + "  AND deleted = false "
                + "  AND is_extension = true";
        return jdbc.query(sql, commonParams(ctx), new BeanPropertyRowMapper<>(FieldMetadata.class));
    }

    /**
     * 批量保存新的 FieldMetadata（批量 UPSERT）。
     */
    @Transactional
    public void batchSave(List<FieldMetadata> metadataList) {
        if (metadataList == null || metadataList.isEmpty()) {
            return;
        }

        // 为每条记录分配雪花ID（如无ID）
        for (FieldMetadata m : metadataList) {
            if (m.getId() == null) {
                m.setId(IdGenerator.generateLongID());
            }
        }

        List<FieldMetadata> toInsert = metadataList.stream()
                .filter(m -> m.getColumnName() != null)
                .toList();

        String sql;
        if (isMySQL()) {
            sql = "INSERT INTO field_metadata ("
                    + "id, tenant_id, app_code, biz_identity_code, entity_type, "
                    + "name, column_name, data_type, is_primary_key, is_nullable, "
                    + "default_value, constraints, is_virtual, is_extension, "
                    + "deleted, create_by, update_by, create_time, update_time"
                    + ") VALUES ("
                    + ":id, :tenantId, :appCode, :bizIdentityCode, :entityType, "
                    + ":name, :columnName, :dataType, :isPrimaryKey, :isNullable, "
                    + ":defaultValue, :constraints, :isVirtual, :isExtension, "
                    + ":deleted, :createBy, :updateBy, :createTime, :updateTime"
                    + ") ON DUPLICATE KEY UPDATE "
                    + "column_name = VALUES(column_name), "
                    + "data_type = VALUES(data_type), "
                    + "update_by = VALUES(update_by), "
                    + "update_time = VALUES(update_time)";
        } else {
            sql = "MERGE INTO field_metadata AS target "
                    + "USING (VALUES (:id, :tenantId, :appCode, :bizIdentityCode, :entityType, :name)) "
                    + "  AS src (id, tenant_id, app_code, biz_identity_code, entity_type, name) "
                    + "ON ( target.tenant_id = src.tenant_id "
                    + "  AND target.app_code = src.app_code "
                    + "  AND target.biz_identity_code = src.biz_identity_code "
                    + "  AND target.entity_type = src.entity_type "
                    + "  AND target.name = src.name ) "
                    + "WHEN MATCHED THEN UPDATE SET "
                    + "  column_name = :columnName, "
                    + "  data_type = :dataType, "
                    + "  update_by = :updateBy, "
                    + "  update_time = :updateTime "
                    + "WHEN NOT MATCHED THEN INSERT ("
                    + "  id, tenant_id, app_code, biz_identity_code, entity_type, name, "
                    + "  column_name, data_type, is_primary_key, is_nullable, "
                    + "  default_value, constraints, is_virtual, is_extension, "
                    + "  deleted, create_by, update_by, create_time, update_time"
                    + ") VALUES ("
                    + "  :id, :tenantId, :appCode, :bizIdentityCode, :entityType, :name, "
                    + "  :columnName, :dataType, :isPrimaryKey, :isNullable, "
                    + "  :defaultValue, :constraints, :isVirtual, :isExtension, "
                    + "  :deleted, :createBy, :updateBy, :createTime, :updateTime"
                    + ")";
        }

        MapSqlParameterSource[] params = toInsert.stream()
                .map(this::toParamSource)
                .toArray(MapSqlParameterSource[]::new);

        jdbc.batchUpdate(sql, params);
    }

    //================ Convenience Methods =================//

    /**
     * 根据上下文和字段路径查找单个元数据
     */
    public FieldMetadata findByFieldPath(AllocationContext ctx, String fieldPath) {
        String sql = "SELECT * FROM field_metadata "
                + "WHERE tenant_id = :tenantId "
                + "  AND app_code = :appCode "
                + "  AND biz_identity_code = :bizIdentityCode "
                + "  AND entity_type = :entityType "
                + "  AND name = :fieldPath "
                + "  AND deleted = false";
        MapSqlParameterSource params = commonParams(ctx)
                .addValue("fieldPath", fieldPath);
        List<FieldMetadata> list = jdbc.query(sql, params, new BeanPropertyRowMapper<>(FieldMetadata.class));
        return list.isEmpty() ? null : list.get(0);
    }

    //================ Internal Helpers =================//

    /** 构建 INSERT/UPDATE 公共参数 */
    private MapSqlParameterSource toParamSource(FieldMetadata m) {
        return new MapSqlParameterSource()
                .addValue("id", m.getId())
                .addValue("tenantId",        m.getTenantId())
                .addValue("appCode",         m.getAppCode())
                .addValue("bizIdentityCode", m.getBizIdentityCode())
                .addValue("entityType",      m.getEntityType())
                .addValue("name",            m.getName())
                .addValue("columnName",      m.getColumnName())
                .addValue("dataType",        m.getDataType())
                .addValue("isPrimaryKey",    m.isPrimaryKey())
                .addValue("isNullable",      m.isNullable())
                .addValue("defaultValue",    m.getDefaultValue())
                .addValue("constraints",     m.getConstraints())
                .addValue("isVirtual",       m.isVirtual())
                .addValue("isExtension",     m.isExtension())
                .addValue("deleted",         m.getDeleted())
                .addValue("createBy",        m.getCreateBy())
                .addValue("updateBy",        m.getUpdateBy())
                .addValue("createTime",      m.getCreateTime())
                .addValue("updateTime",      m.getUpdateTime());
    }

    /** 构建 WHERE 公共参数 */
    private MapSqlParameterSource commonParams(AllocationContext ctx) {
        return new MapSqlParameterSource()
                .addValue("tenantId",        ctx.getTenantId())
                .addValue("appCode",         ctx.getAppCode())
                .addValue("bizIdentityCode", ctx.getBizIdentityCode())
                .addValue("entityType",      ctx.getEntityType());
    }

    /** 判断当前数据库是否为 MySQL */
    private boolean isMySQL() {
        return DatabaseType.MYSQL.equals(MetadataSdkContext.getDatabaseType());
    }
}