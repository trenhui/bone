package com.bone.system.application.query.handler;

import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.application.query.dto.AlertEventDTO;
import com.bone.system.application.query.dto.AlertRuleDTO;
import com.bone.system.application.query.qry.AlertRulePageQry;
import com.bone.system.common.result.PageResult;
import com.bone.system.domain.alert.AlertEvent;
import com.bone.system.domain.alert.AlertRule;
import com.bone.system.domain.repository.AlertEventRepository;
import com.bone.system.domain.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AlertQueryHandler {
    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;

    @Transactional(readOnly = true)
    public AlertRuleDTO getRuleById(Long id) {
        AlertRule rule = alertRuleRepository.findById(id);
        return rule != null ? toRuleDTO(rule) : null;
    }

    @Transactional(readOnly = true)
    public PageResult<AlertRuleDTO> pageRules(AlertRulePageQry qry) {
        FluentQuery<AlertRule> query = QueryBuilder.from(AlertRule.class);

        if (qry.getKeyword() != null && !qry.getKeyword().isBlank()) {
            query.where(AlertRule::getName).like(qry.getKeyword())
                    .or(AlertRule::getDescription).like(qry.getKeyword());
        }

        if (qry.getEnabled() != null) {
            query.where(AlertRule::isEnabled).eq(qry.getEnabled());
        }

        com.bone.core.model.PageResult<AlertRule> result = query.orderByDesc(AlertRule::getCreatedAt)
                .page(qry.getPageNum(), qry.getPageSize());

        List<AlertRuleDTO> dtoList = result.getRecords().stream()
                .map(this::toRuleDTO)
                .collect(Collectors.toList());

        return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
    }

    @Transactional(readOnly = true)
    public AlertEventDTO getEventById(Long id) {
        AlertEvent event = alertEventRepository.findById(id);
        return event != null ? toEventDTO(event) : null;
    }

    @Transactional(readOnly = true)
    public PageResult<AlertEventDTO> pageEvents(int pageNum, int pageSize) {
        FluentQuery<AlertEvent> query = QueryBuilder.from(AlertEvent.class);
        com.bone.core.model.PageResult<AlertEvent> result = query.orderByDesc(AlertEvent::getCreatedAt)
                .page(pageNum, pageSize);

        List<AlertEventDTO> dtoList = result.getRecords().stream()
                .map(this::toEventDTO)
                .collect(Collectors.toList());

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

    private AlertEventDTO toEventDTO(AlertEvent event) {
        return AlertEventDTO.builder()
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
