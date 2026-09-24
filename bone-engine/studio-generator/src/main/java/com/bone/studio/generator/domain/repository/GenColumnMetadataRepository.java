package com.bone.studio.generator.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.studio.generator.domain.model.data.GenColumnMetadata;
import java.util.List;

public interface GenColumnMetadataRepository extends Repository<GenColumnMetadata, Long> {

  default List<GenColumnMetadata> findByTableMetadataId(Long tableMetadataId) {
    return findByCriteria(
        Criteria.<GenColumnMetadata>create()
            .eq(GenColumnMetadata::getTableMetadataId, tableMetadataId));
  }

  default void removeByTableMetadataId(Long tableMetadataId) {
    deleteByCriteria(
        Criteria.<GenColumnMetadata>create()
            .eq(GenColumnMetadata::getTableMetadataId, tableMetadataId));
  }
}
