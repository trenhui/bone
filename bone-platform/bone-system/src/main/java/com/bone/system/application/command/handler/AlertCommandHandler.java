package com.bone.system.application.command.handler;

import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.application.command.cmd.CreateAlertRuleCmd;
import com.bone.system.application.command.cmd.DisableAlertRuleCmd;
import com.bone.system.application.command.cmd.EnableAlertRuleCmd;
import com.bone.system.application.command.cmd.ResolveAlertCmd;
import com.bone.system.application.command.cmd.UpdateAlertRuleCmd;
import com.bone.system.common.exception.NotFoundException;
import com.bone.system.domain.model.alert.AlertEvent;
import com.bone.system.domain.model.alert.AlertRule;
import com.bone.system.domain.model.alert.vo.MetricName;
import com.bone.system.domain.model.alert.vo.Threshold;
import com.bone.system.domain.repository.AlertEventRepository;
import com.bone.system.domain.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 告警命令处理器
 */
@Component
@RequiredArgsConstructor
public class AlertCommandHandler {
    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;

    /**
     * 创建告警规则
     *
     * @param cmd 创建命令
     * @return 告警规则ID
     */
    @Transactional
    public Long handle(CreateAlertRuleCmd cmd) {
        AlertRule rule = AlertRule.create(
                cmd.getName(),
                cmd.getDescription(),
                MetricName.of(cmd.getMetricName()),
                Threshold.of(cmd.getThreshold()),
                com.bone.system.domain.model.alert.vo.AlertLevel.fromString(cmd.getAlertLevel()),
                cmd.getNotificationChannels()
        );

        alertRuleRepository.save(rule);
        return rule.getId();
    }

    /**
     * 更新告警规则
     *
     * @param cmd 更新命令
     */
    @Transactional
    public void handle(UpdateAlertRuleCmd cmd) {
        AlertRule rule = alertRuleRepository.findById(cmd.getId());
        if (rule == null) {
            throw new NotFoundException("告警规则不存在: " + cmd.getId());
        }

        rule.update(
                cmd.getName() != null ? cmd.getName() : rule.getName(),
                cmd.getDescription() != null ? cmd.getDescription() : rule.getDescription(),
                cmd.getThreshold() != null ? Threshold.of(cmd.getThreshold()) : rule.getThreshold(),
                cmd.getAlertLevel() != null ? com.bone.system.domain.model.alert.vo.AlertLevel.fromString(cmd.getAlertLevel()) : rule.getAlertLevel(),
                cmd.getNotificationChannels() != null ? cmd.getNotificationChannels() : rule.getNotificationChannels()
        );

        alertRuleRepository.save(rule);
    }

    /**
     * 启用告警规则
     *
     * @param cmd 启用命令
     */
    @Transactional
    public void handle(EnableAlertRuleCmd cmd) {
        AlertRule rule = alertRuleRepository.findById(cmd.getId());
        if (rule == null) {
            throw new NotFoundException("告警规则不存在: " + cmd.getId());
        }
        rule.enable();
        alertRuleRepository.save(rule);
    }

    /**
     * 禁用告警规则
     *
     * @param cmd 禁用命令
     */
    @Transactional
    public void handle(DisableAlertRuleCmd cmd) {
        AlertRule rule = alertRuleRepository.findById(cmd.getId());
        if (rule == null) {
            throw new NotFoundException("告警规则不存在: " + cmd.getId());
        }
        rule.disable();
        alertRuleRepository.save(rule);
    }

    /**
     * 删除告警规则
     *
     * @param id 告警规则ID
     */
    @Transactional
    public void delete(Long id) {
        alertRuleRepository.deleteById(id);
    }

    /**
     * 创建告警事件
     *
     * @param ruleId      规则ID
     * @param actualValue 实际值
     * @return 告警事件ID
     */
    @Transactional
    public Long createAlertEvent(Long ruleId, double actualValue) {
        AlertRule rule = alertRuleRepository.findById(ruleId);
        if (rule == null) {
            throw new NotFoundException("告警规则不存在: " + ruleId);
        }

        if (!rule.shouldTrigger(actualValue)) {
            return null;
        }

        AlertEvent event = AlertEvent.create(
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

    /**
     * 解决告警事件
     *
     * @param cmd 解决命令
     */
    @Transactional
    public void handle(ResolveAlertCmd cmd) {
        AlertEvent event = alertEventRepository.findById(cmd.getId());
        if (event == null) {
            throw new NotFoundException("告警事件不存在: " + cmd.getId());
        }
        event.resolve();
        alertEventRepository.save(event);
    }
}
