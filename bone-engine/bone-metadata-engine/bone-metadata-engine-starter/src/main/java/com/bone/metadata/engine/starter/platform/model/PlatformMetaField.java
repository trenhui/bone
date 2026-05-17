package com.bone.metadata.engine.starter.platform.model;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;

/** 只读映射 meta_field。 */
@Table("meta_field")
public class PlatformMetaField extends AbstractEntity<Long> {

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "is_required", nullable = false)
    private Boolean required;

    @Column(name = "is_pk", nullable = false)
    private Boolean pk;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    public Long getEntityId() {
        return entityId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getType() {
        return type;
    }

    public Boolean getRequired() {
        return required;
    }

    public Boolean getPk() {
        return pk;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }
}
