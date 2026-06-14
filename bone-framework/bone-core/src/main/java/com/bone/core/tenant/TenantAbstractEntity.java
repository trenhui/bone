package com.bone.core.tenant;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.entity.Tenantable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * Base class for tenant entities, adding tenant-specific fields. Extends from AbstractEntity to
 * inherit auditing and deletion properties.
 *
 * @param <ID> The type of the ID field.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "租户实体基类")
public class TenantAbstractEntity<ID> extends AbstractEntity<ID> implements Tenantable<ID> {

  /** Tenant ID field for identifying the tenant. */
  @Schema(description = "租户id")
  private ID tenantId;
}
