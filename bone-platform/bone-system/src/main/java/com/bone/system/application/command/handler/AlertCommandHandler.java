package com.bone.system.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.application.command.cmd.CreateAlertRuleCmd;
import com.bone.system.application.command.cmd.DisableAlertRuleCmd;
import com.bone.system.application.command.cmd.EnableAlertRuleCmd;
import com.bone.system.application.command.cmd.ResolveAlertCmd;
import com.bone.system.application.command.cmd.UpdateAlertRuleCmd;
import com.bone.system.common.exception.NotFoundException;
import com.bone.system.domain.alert.AlertEvent;
import com.bone.system.domain.alert.AlertRule;
import com.bone.system.domain.alert.vo.MetricName;
import com.bone.system.domain.alert.vo.Threshold;
import com.bone.system.domain.repository.AlertEventRepository;
import com.bone.system.domain.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "CreateAlertRule",
    description = "创建告警规则",
    inputSchema = "{\"name\": \"string\", \"description\": \"string\", \"metricName\": \"string\", \"threshold\": \"double\", \"alertLevel\": \"string\"}",
    outputSchema = "{\"ruleId\": \"long\"}",
    idempotent = false,
    cost = 2,
    retryable = true,
    timeout = 15
)
@Component
@RequiredArgsConstructor
public class AlertCommandHandler {
    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;

    @Transactional
    public Long handle(CreateAlertRuleCmd cmd) {
        Long ruleId = DistributedIdGenerator.generateLongId();
        AlertRule rule = AlertRule.create(
                ruleId,
                cmd.getName(),
                cmd.getDescription(),
                MetricName.of(cmd.getMetricName()),
                Threshold.of(cmd.getThreshold()),
                com.bone.system.domain.alert.vo.AlertLevel.fromString(cmd.getAlertLevel()),
                cmd.getNotificationChannels()
        );

        alertRuleRepository.save(rule);
        return rule.getId();
    }

    @Transactional
    public void handle(UpdateAlertRuleCmd cmd) {
        AlertRule rule = alertRuleRepository.findById(cmd.getId());
        if (rule == null) {
            throw NotFoundException.of("告警规则不存在: " + cmd.getId());
        }

        rule.update(
                cmd.getName() != null ? cmd.getName() : rule.getName(),
                cmd.getDescription() != null ? cmd.getDescription() : rule.getDescription(),
                cmd.getThreshold() != null ? Threshold.of(cmd.getThreshold()) : rule.getThreshold(),
                cmd.getAlertLevel() != null ? com.bone.system.domain.alert.vo.AlertLevel.fromString(cmd.getAlertLevel()) : rule.getAlertLevel(),
                cmd.getNotificationChannels() != null ? cmd.getNotificationChannels() : rule.getNotificationChannels()
        );

        alertRuleRepository.save(rule);
    }

    @Transactional
    public void handle(EnableAlertRuleCmd cmd) {
        AlertRule rule = alertRuleRepository.findById(cmd.getId());
        if (rule == null) {
            throw NotFoundException.of("告警规则不存在: " + cmd.getId());
        }
        rule.enable();
        alertRuleRepository.save(rule);
    }

    @Transactional
    public void handle(DisableAlertRuleCmd cmd) {
        AlertRule rule = alertRuleRepository.findById(cmd.getId());
        if (rule == null) {
            throw NotFoundException.of("告警规则不存在: " + cmd.getId());
        }
        rule.disable();
        alertRuleRepository.save(rule);
    }

    @Transactional
    public void delete(Long id) {
        alertRuleRepository.deleteById(id);
    }

    @Transactional
    public Long createAlertEvent(Long ruleId, double actualValue) {
        AlertRule rule = alertRuleRepository.findById(ruleId);
        if (rule == null) {
            throw NotFoundException.of("告警规则不存在: " + ruleId);
        }

        if (!rule.shouldTrigger(actualValue)) {
            return null;
        }

        Long eventId = DistributedIdGenerator.generateLongId();
        AlertEvent event = AlertEvent.create(
                eventId,
                ruleId,
                rule.getName(),
                rule.getMetricName().value(),
                actualValue,
                rule.getThreshold().value(),
                rule.getAlertLevel(),
                String.format("指标 %s 当前值 %.2f 超过阈值 %.2f",
                        rule.getMetricName().value(), actualValue, rule.getThreshold().value())
        );

        alertEventRepository.save(event);

        return event.getId();
    }

    @Transactional
    public void handle(ResolveAlertCmd cmd) {
        AlertEvent event = alertEventRepository.findById(cmd.getId());
        if (event == null) {
            throw NotFoundException.of("告警事件不存在: " + cmd.getId());
        }
        event.resolve();
        alertEventRepository.save(event);
    }
}
