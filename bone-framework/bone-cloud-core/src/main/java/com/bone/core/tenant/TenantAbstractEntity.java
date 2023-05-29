package com.bone.core.tenant;

import com.bone.core.domain.entity.AbstractEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "租户实体基类")
public class TenantAbstractEntity<ID> extends AbstractEntity<ID> {
    /**租户id*/
    @Schema(description = "租户id")
    private ID tenantId;
}
