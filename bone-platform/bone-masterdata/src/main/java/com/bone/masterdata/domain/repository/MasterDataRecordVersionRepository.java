package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.record.MasterDataRecordVersion;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 记录版本历史仓储：写能力来自基类 {@link Repository}，读侧以 default 方法承载（ADR-0030）。 */
public interface MasterDataRecordVersionRepository
    extends Repository<MasterDataRecordVersion, Long> {

  /** 按记录 ID 查询版本历史（新版本在前由调用方排序）。 */
  default List<MasterDataRecordVersion> findByRecordId(Long recordId) {
    return findByCriteria(
        Criteria.<MasterDataRecordVersion>create()
            .entityClass(MasterDataRecordVersion.class)
            .eq("recordId", recordId));
  }

  /** 判断某记录某版本号是否已存在（快照幂等校验）。 */
  default long countByRecordIdAndVersion(Long recordId, Integer versionNumber) {
    return countByCriteria(
        Criteria.<MasterDataRecordVersion>create()
            .entityClass(MasterDataRecordVersion.class)
            .eq("recordId", recordId)
            .eq("versionNumber", versionNumber));
  }
}
