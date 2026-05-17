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

/** Metadata SDK 持久化实体：插件版本 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("exts_plugin_version")
public class ExtStudioPluginVersion extends AbstractEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId = 0L;

    @Column(name = "plugin_id")
    private Long pluginId;

    @Column(name = "release_version")
    private String releaseVersion;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "is_active")
    private Boolean isActive = false;

    @Column(name = "change_log")
    private String changeLog;
}
