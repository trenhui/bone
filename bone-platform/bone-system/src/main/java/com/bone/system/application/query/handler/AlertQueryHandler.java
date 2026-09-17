package com.bone.system.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.application.query.dto.AlertRecordDTO;
import com.bone.system.application.query.dto.AlertRuleDTO;
import com.bone.system.application.query.qry.AlertRulePageQuery;
import com.bone.system.domain.alert.AlertRecord;
import com.bone.system.domain.alert.AlertRule;
import com.bone.system.domain.repository.AlertRecordRepository;
import com.bone.system.domain.repository.AlertRuleRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AlertQueryHandler {
  private final AlertRuleRepository alertRuleRepository;
  private final AlertRecordRepository alertRecordRepository;

  @Transactional(readOnly = true)
  public AlertRuleDTO getRuleById(Long id) {
    AlertRule rule = alertRuleRepository.findById(id);
    return rule != null ? toRuleDTO(rule) : null;
  }

  @Transactional(readOnly = true)
  public PageResult<AlertRuleDTO> pageRules(AlertRulePageQuery qry) {
    FluentQuery<AlertRule> query = QueryBuilder.from(AlertRule.class);

    if (qry.getKeyword() != null && !qry.getKeyword().isBlank()) {
      query
          .where(AlertRule::getName)
          .like(qry.getKeyword())
          .or(AlertRule::getDescription)
          .like(qry.getKeyword());
    }

    if (qry.getEnabled() != null) {
      query.where(AlertRule::isEnabled).eq(qry.getEnabled());
    }

    com.bone.core.model.PageResult<AlertRule> result =
        query.orderByDesc(AlertRule::getCreatedAt).page(qry.getPageNum(), qry.getPageSize());

    List<AlertRuleDTO> dtoList =
        result.getRecords().stream().map(this::toRuleDTO).collect(Collectors.toList());

    return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
  }

  @Transactional(readOnly = true)
  public AlertRecordDTO getEventById(Long id) {
    AlertRecord event = alertRecordRepository.findById(id);
    return event != null ? toEventDTO(event) : null;
  }

  @Transactional(readOnly = true)
  public PageResult<AlertRecordDTO> pageEvents(int pageNum, int pageSize) {
    FluentQuery<AlertRecord> query = QueryBuilder.from(AlertRecord.class);
    com.bone.core.model.PageResult<AlertRecord> result =
        query.orderByDesc(AlertRecord::getCreatedAt).page(pageNum, pageSize);

    List<AlertRecordDTO> dtoList =
        result.getRecords().stream().map(this::toEventDTO).collect(Collectors.toList());

    return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
  }

  private AlertRuleDTO toRuleDTO(AlertRule rule) {
    return AlertRuleDTO.builder()
        .id(rule.getId())
        .name(rule.getName())
        .description(rule.getDescription())
        .metricName(rule.getMetricName().value())
        .threshold(rule.getThreshold().value())
        .alertLevel(rule.getAlertLevel().name())
        .notificationChannels(rule.getNotificationChannels())
        .enabled(rule.isEnabled())
        .createdAt(rule.getCreatedAt())
        .updatedAt(rule.getUpdatedAt())
        .build();
  }

  private AlertRecordDTO toEventDTO(AlertRecord event) {
    return AlertRecordDTO.builder()
        .id(event.getId())
        .alertRuleId(event.getAlertRuleId())
        .ruleName(event.getRuleName())
        .metricName(event.getMetricName())
        .actualValue(event.getActualValue())
        .threshold(event.getThreshold())
        .alertLevel(event.getAlertLevel().name())
        .message(event.getMessage())
        .status(event.getStatus().name())
        .createdAt(event.getCreatedAt())
        .resolveTime(event.getResolveTime())
        .build();
  }
}
