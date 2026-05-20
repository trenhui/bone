package com.bone.masterdata.infrastructure.gateway;

import com.bone.core.exception.NotFoundException;
import com.bone.core.exception.BizException;
import com.bone.core.tenant.context.TenantContext;
import com.bone.masterdata.common.MasterDataErrorCodes;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 读取 catalog {@code meta_entity}（MD-04）；与 bone-metadata-server 共享同一库。
 */
@Component
@RequiredArgsConstructor
public class MetaEntityReadGateway {

    /** {@code meta_entity.status}：1 = 已发布 */
    public static final int META_STATUS_PUBLISHED = 1;

    private final JdbcTemplate jdbcTemplate;

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

    public record MetaEntityRow(Long id, String code, String displayName, int status) {}
}
