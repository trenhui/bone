package com.bone.masterdata.application.query.port;

import com.bone.masterdata.domain.quality.DataQualityRule;
import com.bone.masterdata.domain.record.MasterDataRecord;
import java.util.List;

/**
 * 数据质量读侧端口（E-13.3 *QueryPort）。
 *
 * <p>质量检查所需的规则与记录读取走本端口；读侧 DSL（Criteria）只允许出现在 {@code infrastructure/query} 的实现中，应用层与领域层均不直接依赖持久化
 * DSL。
 */
public interface DataQualityQueryPort {

  /** 按主数据实体 ID 查询其下全部数据质量规则。 */
  List<DataQualityRule> findRulesByMasterDataEntityId(Long masterDataEntityId);

  /** 按主数据实体 ID 查询其下全部主数据记录。 */
  List<MasterDataRecord> findRecordsByMasterDataEntityId(Long masterDataEntityId);
}
