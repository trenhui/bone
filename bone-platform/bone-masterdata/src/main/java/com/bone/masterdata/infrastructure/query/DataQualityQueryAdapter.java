package com.bone.masterdata.infrastructure.query;

import com.bone.masterdata.application.query.port.DataQualityQueryPort;
import com.bone.masterdata.domain.quality.DataQualityRule;
import com.bone.masterdata.domain.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.DataQualityRuleRepository;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 数据质量读侧适配器：{@link DataQualityQueryPort} 的基础设施实现（E-10.3 {@code infrastructure/query}）。
 *
 * <p>读侧 DSL（Criteria）仅允许出现在本实现中；应用层与领域层通过端口调用，不直接依赖持久化 DSL。
 */
@Component
@RequiredArgsConstructor
public class DataQualityQueryAdapter implements DataQualityQueryPort {

  private final DataQualityRuleRepository ruleRepository;
  private final MasterDataRecordRepository recordRepository;

  @Override
  public List<DataQualityRule> findRulesByMasterDataEntityId(Long masterDataEntityId) {
    return ruleRepository.findByCriteria(
        Criteria.<DataQualityRule>create()
            .entityClass(DataQualityRule.class)
            .eq("masterDataEntityId", masterDataEntityId));
  }

  @Override
  public List<MasterDataRecord> findRecordsByMasterDataEntityId(Long masterDataEntityId) {
    return recordRepository.findByCriteria(
        Criteria.<MasterDataRecord>create()
            .entityClass(MasterDataRecord.class)
            .eq("masterDataEntityId", masterDataEntityId));
  }
}
