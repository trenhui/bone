package com.bone.masterdata.infrastructure.gateway;

import com.bone.core.exception.NotFoundException;
import com.bone.core.tenant.context.TenantContext;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.domain.gateway.MetaEntityCatalogPort;
import com.bone.metadata.sdk.domain.exception.MissingTenantContextException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link MetaEntityCatalogPort} JDBC 实现。
 *
 * <p><b>受控例外（已登记）</b>：{@code meta_entity} 属 bone-metadata 上下文，按 E-1.1 应走联邦视图 / 元数据服务 API， 此处仍用 JDBC
 * 直读，原因是 masterdata 尚无元数据服务客户端；拆除条件见模块 README「已知待办」。
 */
@Component
@RequiredArgsConstructor
public class MetaEntityCatalogPortAdapter implements MetaEntityCatalogPort {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public MetaEntityRow requirePublished(Long metaEntityId) {
    Long tenantId = TenantContext.getTenantIdAsLong();
    if (tenantId == null) {
      // 不回落到平台租户 0：那会读到他租户的数据且无法察觉
      throw new MissingTenantContextException("读取 meta_entity 需要租户上下文");
    }
    Optional<MetaEntityRow> row =
        jdbcTemplate.query(
            """
                        SELECT id, code, display_name, status
                        FROM meta_entity
                        WHERE id = ? AND tenant_id = ? AND deleted = 0
                        """,
            rs -> {
              if (!rs.next()) {
                return Optional.empty();
              }
              return Optional.of(
                  new MetaEntityRow(
                      rs.getLong("id"),
                      rs.getString("code"),
                      rs.getString("display_name"),
                      rs.getInt("status")));
            },
            metaEntityId,
            tenantId);
    if (row.isEmpty()) {
      throw NotFoundException.of("元数据实体不存在: " + metaEntityId);
    }
    MetaEntityRow entity = row.get();
    if (entity.status() != META_STATUS_PUBLISHED) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.META_ENTITY_NOT_PUBLISHED, "仅已发布的元数据实体可转换为主数据");
    }
    return entity;
  }

  @Override
  public java.util.List<MetaFieldRow> requireFields(Long metaEntityId) {
    Long tenantId = TenantContext.getTenantIdAsLong();
    if (tenantId == null) {
      throw new MissingTenantContextException("读取 meta_field 需要租户上下文");
    }
    return jdbcTemplate.query(
        """
                    SELECT code, display_name, type, is_required
                    FROM meta_field
                    WHERE entity_id = ? AND tenant_id = ? AND deleted = 0
                    ORDER BY id
                    """,
        (rs, rowNum) ->
            new MetaFieldRow(
                rs.getString("code"),
                rs.getString("display_name"),
                rs.getString("type"),
                rs.getBoolean("is_required")),
        metaEntityId,
        tenantId);
  }
}
