package com.bone.studio.generator.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import java.util.List;

public interface GenTableMetadataRepository extends Repository<GenTableMetadata, Long> {

  default List<GenTableMetadata> findByDataSourceId(String dataSourceId) {
    return findByCriteria(
        Criteria.<GenTableMetadata>create().eq(GenTableMetadata::getDataSourceId, dataSourceId));
  }
}
