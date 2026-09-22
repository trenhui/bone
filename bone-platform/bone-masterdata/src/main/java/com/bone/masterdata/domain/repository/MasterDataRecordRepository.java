package com.bone.masterdata.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.masterdata.domain.model.record.valueobject.MasterDataRecordStatus;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;

/**
 * 主数据记录仓储：写能力来自基类 {@link Repository}，读侧领域模型（ADR-0030）以 default 方法承载。
 *
 * <p>读侧 DSL（Criteria / QueryBuilder）只许出现在本接口（{@code ..domain.repository..} 是 SDK
 * 框架集成点，被门禁豁免），应用层与适配器不得直接依赖持久化 DSL。
 */
public interface MasterDataRecordRepository extends Repository<MasterDataRecord, Long> {

  /** 按实体 ID 查询记录列表（导出 / 质量检查用，ADR-0030）。 */
  default List<MasterDataRecord> findByMasterDataEntityId(Long masterDataEntityId) {
    return findByCriteria(
        Criteria.<MasterDataRecord>create()
            .entityClass(MasterDataRecord.class)
            .eq("masterDataEntityId", masterDataEntityId));
  }

  /** 按实体 ID 分页查询记录（读模型，ADR-0030）。 */
  default PageResult<MasterDataRecord> pageByEntityId(Long masterDataEntityId, int page, int size) {
    FluentQuery<MasterDataRecord> query = QueryBuilder.from(MasterDataRecord.class);
    if (masterDataEntityId != null) {
      query.where(MasterDataRecord::getMasterDataEntityId).eq(masterDataEntityId);
    }
    return query.orderByDesc(MasterDataRecord::getCreatedAt).page(page, size);
  }

  /** 按实体 ID + 状态 + 关键字分页查询记录（读模型，ADR-0030）。 */
  default PageResult<MasterDataRecord> pageByEntityIdStatusAndKeyword(
      Long masterDataEntityId, MasterDataRecordStatus status, String keyword, int page, int size) {
    FluentQuery<MasterDataRecord> query = QueryBuilder.from(MasterDataRecord.class);
    if (masterDataEntityId != null) {
      query.where(MasterDataRecord::getMasterDataEntityId).eq(masterDataEntityId);
    }
    if (status != null) {
      query.where(MasterDataRecord::getStatus).eq(status);
    }
    if (keyword != null && !keyword.isBlank()) {
      query.where(MasterDataRecord::getData).like(keyword);
    }
    return query.orderByDesc(MasterDataRecord::getCreatedAt).page(page, size);
  }
}
