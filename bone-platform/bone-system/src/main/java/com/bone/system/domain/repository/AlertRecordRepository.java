package com.bone.system.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.domain.model.alert.AlertRecord;
import com.bone.system.domain.model.alert.valueobject.AlertLevel;
import com.bone.system.domain.model.alert.valueobject.AlertStatus;
import java.util.Comparator;
import java.util.Optional;

/**
 * 告警记录仓储端口：写侧 + 本聚合读（ADR-0030）。
 *
 * <p>告警记录是只追加的流水：写路径只有 {@code insert}，读侧只有「按条件过滤 + 时间倒序分页」，二者共享同一个 仓储端口即足够，不必为它再建一层 QueryPort。
 */
public interface AlertRecordRepository extends Repository<AlertRecord, Long> {

  /**
   * 告警记录分页：三个过滤条件均可缺省（缺省即不过滤），按 {@code createdAt} 倒序。
   *
   * <p>单列等值过滤是 Criteria 的主场——自带租户与软删注入，不需要像跨列 OR 那样降级到 QueryBuilder。
   */
  default PageResult<AlertRecord> pageByCondition(
      Long ruleId, AlertLevel level, AlertStatus status, int pageNum, int pageSize) {
    Criteria<AlertRecord> criteria =
        Criteria.<AlertRecord>create()
            .entityClass(AlertRecord.class)
            .eq(ruleId != null, AlertRecord::getAlertRuleId, ruleId)
            .eq(level != null, AlertRecord::getAlertLevel, level)
            .eq(status != null, AlertRecord::getStatus, status);
    return pageByCriteria(criteria.page(pageNum, pageSize));
  }

  /**
   * 关键字分页（历史兼容入口）：命中规则名或消息内容，最近触发的在前。
   *
   * <p>跨列 OR 走 QueryBuilder，原因见 {@link com.bone.system.domain.repository.SystemConfigRepository} 对
   * {@code Criteria.or(Consumer)} 缺陷的说明。
   */
  default PageResult<AlertRecord> pageByKeyword(String keyword, int pageNum, int pageSize) {
    var query = QueryBuilder.from(AlertRecord.class);
    if (keyword != null && !keyword.isBlank()) {
      query
          .where(AlertRecord::getRuleName)
          .contains(keyword)
          .or(AlertRecord::getMessage)
          .contains(keyword);
    }
    return query.orderByDesc(AlertRecord::getCreatedAt).page(pageNum, pageSize);
  }

  /**
   * 查找某规则当前处于 TRIGGERED（未恢复）的告警记录，供定时评估器去重与自动恢复：
   *
   * <ul>
   *   <li>评估时已在告警中 → 更新实测值，不重复插行（无去重的每周期一事件会让告警列表被刷屏）；
   *   <li>评估时已恢复 → 对存量 TRIGGERED 记录执行 {@code resolve()}（告警生命周期闭环）。
   * </ul>
   *
   * <p>评估 Job 口径面向全平台，显式 {@code disableTenantFilter()}；历史数据可能存在同规则多条 TRIGGERED（人工上报等），取 {@code
   * createdAt} 最新一条。
   */
  default Optional<AlertRecord> findLatestTriggeredAllTenants(Long ruleId) {
    return findByCriteria(
            Criteria.<AlertRecord>create()
                .entityClass(AlertRecord.class)
                .eq(AlertRecord::getAlertRuleId, ruleId)
                .eq(AlertRecord::getStatus, AlertStatus.TRIGGERED)
                .disableTenantFilter())
        .stream()
        .max(Comparator.comparing(AlertRecord::getCreatedAt));
  }
}
