package com.bone.system.application;

import com.bone.core.capability.Capability;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.system.application.command.CreateAlertRuleCommand;
import com.bone.system.application.command.UpdateAlertRuleCommand;
import com.bone.system.application.port.out.MetricValuePort;
import com.bone.system.application.query.dto.AlertRecordDto;
import com.bone.system.application.query.dto.AlertRuleDto;
import com.bone.system.application.query.qry.AlertRecordPageQuery;
import com.bone.system.application.query.qry.AlertRulePageQuery;
import com.bone.system.common.SystemErrorCodes;
import com.bone.system.common.SystemErrors;
import com.bone.system.domain.model.alert.AlertRecord;
import com.bone.system.domain.model.alert.AlertRule;
import com.bone.system.domain.model.alert.valueobject.AlertLevel;
import com.bone.system.domain.model.alert.valueobject.AlertStatus;
import com.bone.system.domain.model.alert.valueobject.MetricName;
import com.bone.system.domain.model.alert.valueobject.Threshold;
import com.bone.system.domain.repository.AlertRecordRepository;
import com.bone.system.domain.repository.AlertRuleRepository;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 告警用例入口：规则维护 + 记录流转。
 *
 * <p><b>两个聚合为何同一个入口</b>：告警记录由「规则 + 实测值」推导而来，触发判定必须在加载规则的同一个用例内完成。 若把 rule 与 record 拆到两个
 * ApplicationService，就只能在两者之间再架一层协调者——那层除了转发不会多做任何事 （E-3.2）。这不是跨聚合事务：本用例只提交 <code>AlertRecord
 * </code> 一个聚合，<code>AlertRule</code> 只读（CORE-06）。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertApplicationService {

  private final AlertRuleRepository alertRuleRepository;
  private final AlertRecordRepository alertRecordRepository;
  private final DomainEventPublisher domainEventPublisher;
  private final MetricValuePort metricValuePort;

  /** 一次评估的统计摘要（评估了多少、触发/更新/恢复/跳过各多少），供定时 Job 记录日志。 */
  public record EvaluationSummary(
      int evaluated, int triggered, int updated, int resolved, int skipped) {}

  /**
   * 周期评估：把全部启用规则与实测指标比对，维护告警事件生命周期。
   *
   * <p><b>三态处置</b>（无此评估器时规则永远不会被自动计算，告警只能靠外部手动上报）：
   *
   * <ul>
   *   <li>超阈值且无进行中事件 → 新建 TRIGGERED 事件；
   *   <li>超阈值且已有进行中事件 → 原地更新实测值（去重，不刷屏）；
   *   <li>已回落且仍有 TRIGGERED 事件 → 自动 resolve（告警闭环，无需人工确认恢复）。
   * </ul>
   *
   * <p>指标不可解析的规则跳过（{@code OptionalDouble.empty()} 是正常分支）：外置指标源（Prometheus
   * 抓取的网关错误率等）未接入时，对应规则静默等待而不是报错刷日志。
   */
  @Transactional
  public EvaluationSummary evaluateAllRules() {
    List<AlertRule> rules = alertRuleRepository.findAllEnabledAllTenants();
    int triggered = 0;
    int updated = 0;
    int resolved = 0;
    int skipped = 0;
    for (AlertRule rule : rules) {
      OptionalDouble observed = metricValuePort.resolve(rule.getMetricName().value());
      if (observed.isEmpty()) {
        skipped++;
        continue;
      }
      double value = observed.getAsDouble();
      Optional<AlertRecord> open =
          alertRecordRepository.findLatestTriggeredAllTenants(rule.getId());
      if (rule.shouldTrigger(value)) {
        if (open.isPresent()) {
          open.get().observe(value, triggerMessage(rule, value));
          alertRecordRepository.save(open.get());
          updated++;
        } else {
          AlertRecord record = createTriggeredRecord(rule, value);
          alertRecordRepository.save(record);
          domainEventPublisher.publishFrom(record);
          triggered++;
        }
      } else if (open.isPresent()) {
        open.get().resolve();
        alertRecordRepository.save(open.get());
        domainEventPublisher.publishFrom(open.get());
        resolved++;
      }
    }
    return new EvaluationSummary(rules.size(), triggered, updated, resolved, skipped);
  }

  private static AlertRecord createTriggeredRecord(AlertRule rule, double value) {
    return AlertRecord.create(
        DistributedIdGenerator.generateLongId(),
        rule.getId(),
        rule.getName(),
        rule.getMetricName().value(),
        value,
        rule.getThreshold().value(),
        rule.getAlertLevel(),
        triggerMessage(rule, value));
  }

  private static String triggerMessage(AlertRule rule, double value) {
    return String.format(
        "指标 %s 当前值 %.2f 超过阈值 %.2f",
        rule.getMetricName().value(), value, rule.getThreshold().value());
  }

  @Capability(
      name = "CreateAlertRule",
      description = "创建告警规则",
      inputSchema =
          "{\"name\": \"string\", \"description\": \"string\", \"metricName\": \"string\","
              + " \"threshold\": \"double\", \"alertLevel\": \"string\"}",
      outputSchema = "{\"ruleId\": \"long\"}",
      idempotent = false,
      cost = 2,
      retryable = true,
      timeout = 15)
  @Transactional
  public Long createRule(CreateAlertRuleCommand command) {
    AlertRule rule =
        AlertRule.create(
            DistributedIdGenerator.generateLongId(),
            command.getName(),
            command.getDescription(),
            parseMetricName(command.getMetricName()),
            parseThreshold(command.getThreshold()),
            parseAlertLevel(command.getAlertLevel()),
            command.getNotificationChannels());
    alertRuleRepository.save(rule);
    domainEventPublisher.publishFrom(rule);
    return rule.getId();
  }

  /** 局部更新：各项可缺省，缺省项保持原值（PATCH 语义）。 */
  @Transactional
  public void updateRule(UpdateAlertRuleCommand command) {
    AlertRule rule = requireRule(command.getId());
    rule.update(
        command.getName() != null ? command.getName() : rule.getName(),
        command.getDescription() != null ? command.getDescription() : rule.getDescription(),
        command.getThreshold() != null
            ? parseThreshold(command.getThreshold())
            : rule.getThreshold(),
        command.getAlertLevel() != null
            ? parseAlertLevel(command.getAlertLevel())
            : rule.getAlertLevel(),
        command.getNotificationChannels() != null
            ? command.getNotificationChannels()
            : rule.getNotificationChannels());
    alertRuleRepository.save(rule);
    domainEventPublisher.publishFrom(rule);
  }

  @Transactional
  public void enableRule(Long id) {
    AlertRule rule = requireRule(id);
    rule.enable();
    alertRuleRepository.save(rule);
    domainEventPublisher.publishFrom(rule);
  }

  @Transactional
  public void disableRule(Long id) {
    AlertRule rule = requireRule(id);
    rule.disable();
    alertRuleRepository.save(rule);
    domainEventPublisher.publishFrom(rule);
  }

  @Transactional
  public void deleteRule(Long id) {
    alertRuleRepository.deleteById(id);
  }

  /**
   * 上报实测值：达到阈值才落一条告警记录。
   *
   * @return 新建记录的 id；未触发阈值返回 {@code Optional.empty()}——「上报了但没告警」是正常分支，不是失败
   */
  @Transactional
  public Optional<Long> recordIfTriggered(Long ruleId, double actualValue) {
    AlertRule rule = requireRule(ruleId);
    if (!rule.shouldTrigger(actualValue)) {
      return Optional.empty();
    }
    AlertRecord record =
        AlertRecord.create(
            DistributedIdGenerator.generateLongId(),
            ruleId,
            rule.getName(),
            rule.getMetricName().value(),
            actualValue,
            rule.getThreshold().value(),
            rule.getAlertLevel(),
            String.format(
                "指标 %s 当前值 %.2f 超过阈值 %.2f",
                rule.getMetricName().value(), actualValue, rule.getThreshold().value()));
    alertRecordRepository.save(record);
    domainEventPublisher.publishFrom(record);
    return Optional.of(record.getId());
  }

  @Transactional
  public void resolveRecord(Long id) {
    AlertRecord record = requireRecord(id);
    record.resolve();
    alertRecordRepository.save(record);
    domainEventPublisher.publishFrom(record);
  }

  public Optional<AlertRuleDto> getRuleById(Long id) {
    return Optional.ofNullable(alertRuleRepository.findById(id)).map(AlertRuleDto::from);
  }

  public PageResult<AlertRuleDto> pageRules(AlertRulePageQuery query) {
    PageResult<AlertRule> page =
        alertRuleRepository.pageByCondition(
            query.getKeyword(),
            query.getEnabled(),
            parseAlertLevelOrNull(query.getAlertLevel()),
            query.getPageNum(),
            query.getPageSize());
    return PageResult.of(
        page.getRecords().stream().map(AlertRuleDto::from).toList(),
        page.getTotal(),
        page.getPage(),
        page.getSize());
  }

  public Optional<AlertRecordDto> getRecordById(Long id) {
    return Optional.ofNullable(alertRecordRepository.findById(id)).map(AlertRecordDto::from);
  }

  public PageResult<AlertRecordDto> pageRecords(AlertRecordPageQuery query) {
    PageResult<AlertRecord> page =
        alertRecordRepository.pageByCondition(
            query.getAlertRuleId(),
            parseAlertLevelOrNull(query.getAlertLevel()),
            parseAlertStatusOrNull(query.getStatus()),
            query.getPageNum(),
            query.getPageSize());
    return PageResult.of(
        page.getRecords().stream().map(AlertRecordDto::from).toList(),
        page.getTotal(),
        page.getPage(),
        page.getSize());
  }

  private AlertRule requireRule(Long id) {
    AlertRule rule = alertRuleRepository.findById(id);
    if (rule == null) {
      throw SystemErrors.of(SystemErrorCodes.ALERT_RULE_NOT_FOUND, id);
    }
    return rule;
  }

  private AlertRecord requireRecord(Long id) {
    AlertRecord record = alertRecordRepository.findById(id);
    if (record == null) {
      throw SystemErrors.of(SystemErrorCodes.ALERT_RECORD_NOT_FOUND, id);
    }
    return record;
  }

  private static AlertLevel parseAlertLevel(String value) {
    try {
      return AlertLevel.fromString(value);
    } catch (RuntimeException ex) {
      throw SystemErrors.of(SystemErrorCodes.ALERT_LEVEL_INVALID, value);
    }
  }

  private static MetricName parseMetricName(String value) {
    try {
      return MetricName.of(value);
    } catch (RuntimeException ex) {
      throw SystemErrors.of(SystemErrorCodes.ALERT_RULE_INVALID, "metricName=" + value);
    }
  }

  private static Threshold parseThreshold(Double value) {
    try {
      return Threshold.of(value);
    } catch (RuntimeException ex) {
      throw SystemErrors.of(SystemErrorCodes.ALERT_RULE_INVALID, "threshold=" + value);
    }
  }

  /** 过滤器可缺省：null 表示「不按级别过滤」，空串是合法缺省值，不要翻译成 400。 */
  private static AlertLevel parseAlertLevelOrNull(String value) {
    return value == null || value.isBlank() ? null : parseAlertLevel(value);
  }

  /** 过滤器可缺省：null 表示「不按状态过滤」。 */
  private static AlertStatus parseAlertStatusOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return AlertStatus.fromString(value);
    } catch (RuntimeException ex) {
      throw SystemErrors.of(SystemErrorCodes.ALERT_STATUS_INVALID, value);
    }
  }
}
