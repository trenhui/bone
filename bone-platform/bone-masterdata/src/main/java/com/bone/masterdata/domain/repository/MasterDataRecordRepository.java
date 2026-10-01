package com.bone.masterdata.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.masterdata.domain.model.record.valueobject.MasterDataRecordStatus;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
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

  /**
   * 按实体 + 业务编码查重（租户+实体内 record_code 唯一）。
   *
   * <p>DB 上已有 uk_mdm_record_code 唯一索引，但直接依赖约束会让"重码"变成一次 DataIntegrityViolationException（500
   * 兜底）；应用层先查一次，才能给出 409 + 明确业务码。
   */
  default long countByEntityIdAndRecordCode(Long masterDataEntityId, String recordCode) {
    if (masterDataEntityId == null || recordCode == null || recordCode.isBlank()) {
      return 0L;
    }
    Long count =
        countByCriteria(
            Criteria.<MasterDataRecord>create()
                .entityClass(MasterDataRecord.class)
                .eq("masterDataEntityId", masterDataEntityId)
                .eq("recordCode", recordCode));
    return count == null ? 0L : count;
  }

  /**
   * 按实体 + 业务编码精确定位记录（下游消费主数据的主路径）。
   *
   * <p>真实场景：blueprint 下单时按商品编码 1001 取商品主数据，而不是扫全表在 JSON 里捞。 本方法是「记录业务主键」对外可用的最小接口。
   */
  default List<MasterDataRecord> findByEntityIdAndRecordCode(
      Long masterDataEntityId, String recordCode) {
    if (masterDataEntityId == null || recordCode == null || recordCode.isBlank()) {
      return List.of();
    }
    return findByCriteria(
        Criteria.<MasterDataRecord>create()
            .entityClass(MasterDataRecord.class)
            .eq("masterDataEntityId", masterDataEntityId)
            .eq("recordCode", recordCode));
  }

  /** 按实体 ID 分页查询记录（读模型，ADR-0030）。 */
  default PageResult<MasterDataRecord> pageByEntityId(Long masterDataEntityId, int page, int size) {
    Criteria<MasterDataRecord> criteria =
        Criteria.<MasterDataRecord>create()
            .entityClass(MasterDataRecord.class)
            .page(page, size)
            .orderByDesc("createdAt");
    if (masterDataEntityId != null) {
      criteria.eq("masterDataEntityId", masterDataEntityId);
    }
    return pageByCriteria(criteria);
  }

  /**
   * 按实体 ID + 状态 + 关键字 + 生效期分页查询记录（读模型，ADR-0030）。
   *
   * <p>keyword 三路 OR：record_code / display_name / data JSON。此前只 like data JSON——
   * 用户按业务编码或名称搜记录永远搜不到， 只能靠整段 JSON 原文碰运气。OR 组以括号片段与外层 AND 组合， 不破坏软删除与租户护栏（ADR-0029）。
   *
   * <p>onlyCurrent=true 时只返回「当前版本且此刻在生效期内」的记录；未配置生效窗口视为长期有效。
   */
  default PageResult<MasterDataRecord> pageByEntityIdStatusAndKeyword(
      Long masterDataEntityId,
      MasterDataRecordStatus status,
      String keyword,
      Boolean onlyCurrent,
      int page,
      int size) {
    Criteria<MasterDataRecord> criteria =
        Criteria.<MasterDataRecord>create()
            .entityClass(MasterDataRecord.class)
            .page(page, size)
            .orderByDesc("createdAt");
    if (masterDataEntityId != null) {
      criteria.eq("masterDataEntityId", masterDataEntityId);
    }
    if (status != null) {
      // 枚举以 name() 入参，避免 JDBC setObject 直接绑定枚举的方言差异
      criteria.eq("status", status.name());
    }
    if (Boolean.TRUE.equals(onlyCurrent)) {
      appendEffectiveWindowFilter(criteria, java.time.LocalDateTime.now());
    }
    if (keyword != null && !keyword.isBlank()) {
      String kw = keyword.trim();
      criteria.or(sub -> sub.like("recordCode", kw).like("displayName", kw).like("data", kw));
    }
    return pageByCriteria(criteria);
  }

  /** onlyCurrent 过滤：当前版本 + 生效窗口含此刻（窗口为 NULL 视为长期有效）。 */
  private static void appendEffectiveWindowFilter(
      Criteria<MasterDataRecord> criteria, java.time.LocalDateTime now) {
    criteria.eq("isCurrent", Boolean.TRUE);
    criteria.or(sub -> sub.isNull("effectiveFrom").lte("effectiveFrom", now));
    criteria.or(sub -> sub.isNull("effectiveTo").gte("effectiveTo", now));
  }
}
