package com.bone.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.system.application.port.out.MetricValuePort;
import com.bone.system.domain.model.alert.AlertRecord;
import com.bone.system.domain.model.alert.AlertRule;
import com.bone.system.domain.model.alert.valueobject.AlertLevel;
import com.bone.system.domain.model.alert.valueobject.MetricName;
import com.bone.system.domain.model.alert.valueobject.Threshold;
import com.bone.system.domain.repository.AlertRecordRepository;
import com.bone.system.domain.repository.AlertRuleRepository;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 告警评估用例的应用层测试：三态处置（新建 / 去重更新 / 自动恢复）+ 指标不可评估跳过。
 *
 * <p>规则与事件用真实聚合（纯领域对象），仓储与取数端口打桩；接口 default 方法必须 {@code doAnswer().when(mock)} 打桩——{@code
 * when(mock.defaultMethod())} 会真实执行 default 体， 内部再调 {@code findByCriteria} 就 NPE（且污染打桩计数）。
 */
@ExtendWith(MockitoExtension.class)
class AlertEvaluationTest {

  @Mock AlertRuleRepository alertRuleRepository;
  @Mock AlertRecordRepository alertRecordRepository;
  @Mock DomainEventPublisher domainEventPublisher;
  @Mock MetricValuePort metricValuePort;

  AlertApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new AlertApplicationService(
            alertRuleRepository, alertRecordRepository, domainEventPublisher, metricValuePort);
  }

  private static AlertRule rule(String name, String metric, double threshold) {
    return AlertRule.create(
        1L,
        name,
        "desc",
        MetricName.of(metric),
        Threshold.of(threshold),
        AlertLevel.CRITICAL,
        List.of("CONSOLE"));
  }

  @Test
  void newBreachCreatesTriggeredRecord() {
    AlertRule cpuRule = rule("CPU 过高", "cpu.usage", 85);
    doAnswer(inv -> List.of(cpuRule)).when(alertRuleRepository).findAllEnabledAllTenants();
    doAnswer(inv -> Optional.empty()).when(alertRecordRepository).findLatestTriggeredAllTenants(1L);
    when(metricValuePort.resolve("cpu.usage")).thenReturn(OptionalDouble.of(92.5));

    var summary = service.evaluateAllRules();

    assertThat(summary.triggered()).isEqualTo(1);
    verify(alertRecordRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                saved ->
                    saved.getStatus().name().equals("TRIGGERED")
                        && saved.getActualValue() == 92.5));
    verify(domainEventPublisher).publishFrom(any(AlertRecord.class));
  }

  @Test
  void ongoingBreachUpdatesExistingRecordInsteadOfDuplicating() {
    AlertRule cpuRule = rule("CPU 过高", "cpu.usage", 85);
    AlertRecord open =
        AlertRecord.create(9L, 1L, "CPU 过高", "cpu.usage", 90.0, 85.0, AlertLevel.CRITICAL, "旧消息");
    doAnswer(inv -> List.of(cpuRule)).when(alertRuleRepository).findAllEnabledAllTenants();
    doAnswer(inv -> Optional.of(open))
        .when(alertRecordRepository)
        .findLatestTriggeredAllTenants(1L);
    when(metricValuePort.resolve("cpu.usage")).thenReturn(OptionalDouble.of(95.0));

    var summary = service.evaluateAllRules();

    assertThat(summary.updated()).isEqualTo(1);
    assertThat(summary.triggered()).isZero();
    assertThat(open.getActualValue()).isEqualTo(95.0);
    assertThat(open.getStatus().name()).isEqualTo("TRIGGERED");
    verify(alertRecordRepository, never())
        .save(org.mockito.ArgumentMatchers.argThat(saved -> saved.getId() != 9L));
  }

  @Test
  void recoveredMetricResolvesOpenRecord() {
    AlertRule cpuRule = rule("CPU 过高", "cpu.usage", 85);
    AlertRecord open =
        AlertRecord.create(9L, 1L, "CPU 过高", "cpu.usage", 90.0, 85.0, AlertLevel.CRITICAL, "旧消息");
    doAnswer(inv -> List.of(cpuRule)).when(alertRuleRepository).findAllEnabledAllTenants();
    doAnswer(inv -> Optional.of(open))
        .when(alertRecordRepository)
        .findLatestTriggeredAllTenants(1L);
    when(metricValuePort.resolve("cpu.usage")).thenReturn(OptionalDouble.of(12.0));

    var summary = service.evaluateAllRules();

    assertThat(summary.resolved()).isEqualTo(1);
    assertThat(open.getStatus().name()).isEqualTo("RESOLVED");
    verify(domainEventPublisher).publishFrom(open);
  }

  @Test
  void unresolvableMetricSkipsRuleSilently() {
    AlertRule gwRule = rule("API 错误率", "api.error_rate", 5);
    doAnswer(inv -> List.of(gwRule)).when(alertRuleRepository).findAllEnabledAllTenants();
    when(metricValuePort.resolve("api.error_rate")).thenReturn(OptionalDouble.empty());

    var summary = service.evaluateAllRules();

    assertThat(summary.skipped()).isEqualTo(1);
    assertThat(summary.evaluated()).isEqualTo(1);
    verify(alertRecordRepository, never()).save(any());
    verify(domainEventPublisher, never()).publishFrom(any());
  }
}
