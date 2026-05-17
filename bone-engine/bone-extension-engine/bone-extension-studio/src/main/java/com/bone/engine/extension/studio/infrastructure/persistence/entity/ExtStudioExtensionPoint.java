package com.bone.engine.extension.studio.infrastructure.persistence.entity;

import com.bone.core.annotation.Id;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Metadata SDK 持久化实体：扩展点 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("exts_extension_point")
public class ExtStudioExtensionPoint extends AbstractEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId = 0L;

    @Column(name = "point_name")
    private String pointName;

    @Column(name = "point_code")
    private String pointCode;

  @Column(name = "description")
    private String description;

    @Column(name = "interface_name")
    private String interfaceName;

    @Column(name = "biz_domain")
    private String bizDomain;

    @Column(name = "category")
    private String category;

    @Column(name = "status")
    private String status = "ENABLED";
}
