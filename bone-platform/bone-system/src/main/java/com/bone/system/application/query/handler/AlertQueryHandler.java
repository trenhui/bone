package com.bone.system.application.query.handler;

import com.bone.metadata.sdk.query.QueryBuilder;
import com.bone.system.application.query.dto.AlertEventDTO;
import com.bone.system.application.query.dto.AlertRuleDTO;
import com.bone.system.application.query.qry.AlertRulePageQry;
import com.bone.system.common.result.PageResult;
import com.bone.system.domain.model.alert.AlertEvent;
import com.bone.system.domain.model.alert.AlertRule;
import com.bone.system.domain.repository.AlertEventRepository;
import com.bone.system.domain.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AlertQueryHandler {
    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;

    @Transactional(readOnly = true)
    public AlertRuleDTO getRuleById(Long id) {
        return alertRuleRepository.findById(id)
                .map(this::toRuleDTO)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PageResult<AlertRuleDTO> pageRules(AlertRulePageQry qry) {
        QueryBuilder<AlertRule> queryBuilder = QueryBuilder.from(AlertRule.class);
        
        if (qry.getKeyword() != null && !qry.getKeyword().isBlank()) {
            queryBuilder = queryBuilder.where("name").like(qry.getKeyword())
                    .or("description").like(qry.getKeyword());
        }
        
        if (qry.getEnabled() != null) {
            queryBuilder = queryBuilder.where("enabled").eq(qry.getEnabled());
        }

        return queryBuilder
                .orderBy("createTime", "desc")
                .page(qry.getPageNum(), qry.getPageSize())
                .mapTo(AlertRuleDTO.class);
    }

    @Transactional(readOnly = true)
    public AlertEventDTO getEventById(Long id) {
        return alertEventRepository.findById(id)
                .map(this::toEventDTO)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PageResult<AlertEventDTO> pageEvents(int pageNum, int pageSize) {
        return QueryBuilder.from(AlertEvent.class)
                .orderBy("createTime", "desc")
                .page(pageNum, pageSize)
                .mapTo(AlertEventDTO.class);
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
                .createTime(rule.getCreateTime())
                .updateTime(rule.getUpdateTime())
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
                .createTime(event.getCreateTime())
                .resolveTime(event.getResolveTime())
                .build();
    }
}
