package com.bone.system.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.domain.model.alert.AlertRule;
import com.bone.system.domain.model.alert.valueobject.AlertLevel;

/**
 * 告警规则仓储端口：写侧 + 本聚合读（ADR-0030）。
 *
 * <p>分页统一用 {@code QueryBuilder}：关键字需跨 `name` / `description` 两列 OR，而 {@code Criteria.or(Consumer)}
 * 生成的条件片段会被当作等值条件拼接（SDK 已知缺陷），在其修复前不使用 Criteria 表达 OR。
 */
public interface AlertRuleRepository extends Repository<AlertRule, Long> {

  /** 告警规则分页：关键字命中名称或描述，`enabled` / `alertLevel` 非空时精确过滤；最近创建的在前。 */
  default PageResult<AlertRule> pageByCondition(
      String keyword, Boolean enabled, AlertLevel level, int pageNum, int pageSize) {
    var query = QueryBuilder.from(AlertRule.class);
    boolean hasKeyword = keyword != null && !keyword.isBlank();
    if (hasKeyword) {
      query.where(AlertRule::getName).like(keyword).or(AlertRule::getDescription).like(keyword);
    }
    if (enabled != null) {
      query.and(AlertRule::isEnabled).eq(enabled);
    }
    if (level != null) {
      query.and(AlertRule::getAlertLevel).eq(level);
    }
    return query.orderByDesc(AlertRule::getCreatedAt).page(pageNum, pageSize);
  }
}
