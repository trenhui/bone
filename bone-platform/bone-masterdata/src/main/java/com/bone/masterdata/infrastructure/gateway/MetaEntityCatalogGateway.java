package com.bone.masterdata.infrastructure.gateway;

import com.bone.core.exception.BizException;
import com.bone.core.exception.NotFoundException;
import com.bone.core.tenant.context.TenantContext;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.domain.gateway.MetaEntityCatalogPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** {@link MetaEntityCatalogPort} JDBC 实现。 */
@Component
@RequiredArgsConstructor
public class MetaEntityCatalogGateway implements MetaEntityCatalogPort {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public MetaEntityRow requirePublished(Long metaEntityId) {
    Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 0L;
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
      throw BizException.of(
          422, MasterDataErrorCodes.META_ENTITY_NOT_PUBLISHED + ": 仅已发布的元数据实体可转换为主数据");
    }
    return entity;
  }
}
