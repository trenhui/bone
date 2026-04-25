package com.bone.studio.generator.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.studio.generator.domain.data.GenTableMetadata;

import java.util.List;

public interface GenTableMetadataRepository extends Repository<GenTableMetadata, Long> {
    GenTableMetadata findByDataSourceIdAndTableName(String dataSourceId, String tableName);
    List<GenTableMetadata> findByDataSourceId(String dataSourceId);
}
