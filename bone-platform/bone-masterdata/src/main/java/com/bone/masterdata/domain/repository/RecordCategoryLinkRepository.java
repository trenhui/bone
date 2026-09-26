package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.category.RecordCategoryLink;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 记录-分类关联仓储（G4）。 */
public interface RecordCategoryLinkRepository extends Repository<RecordCategoryLink, Long> {

  default List<RecordCategoryLink> findByRecordId(Long recordId) {
    return findByCriteria(
        Criteria.<RecordCategoryLink>create()
            .entityClass(RecordCategoryLink.class)
            .eq("recordId", recordId));
  }

  default List<RecordCategoryLink> findByCategoryId(Long categoryId) {
    return findByCriteria(
        Criteria.<RecordCategoryLink>create()
            .entityClass(RecordCategoryLink.class)
            .eq("categoryId", categoryId));
  }

  default long countByRecordIdAndCategoryId(Long recordId, Long categoryId) {
    return countByCriteria(
        Criteria.<RecordCategoryLink>create()
            .entityClass(RecordCategoryLink.class)
            .eq("recordId", recordId)
            .eq("categoryId", categoryId));
  }
}
