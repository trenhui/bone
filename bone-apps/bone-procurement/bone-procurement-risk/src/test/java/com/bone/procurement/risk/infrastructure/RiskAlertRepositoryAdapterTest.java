package com.bone.procurement.risk.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.bone.procurement.risk.application.RiskApplicationService;
import com.bone.procurement.risk.domain.gateway.RiskAlertRepository;
import com.bone.procurement.risk.domain.model.risk.PerformanceScore;
import com.bone.procurement.risk.domain.model.risk.RiskAlert;
import com.bone.procurement.risk.domain.repository.PerformanceScoreSqlRepository;
import com.bone.procurement.risk.domain.repository.RiskAlertSqlRepository;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 风险 gateway 适配器：按 source_ref 幂等去重、租户隔离、绩效只读与新实例读回一致。 */
class RiskAlertRepositoryAdapterTest {

  @Test
  @DisplayName("相同 (租户, 供应商, 来源, source_ref) 重复投递复用同一预警，不新增记录")
  void deduplicatesBySourceRef() {
    FakeSdkStore<RiskAlert> alerts = new FakeSdkStore<>();
    FakeSdkStore<PerformanceScore> scores = new FakeSdkStore<>();
    RiskAlertRepository port = adapter(alerts, scores);
    RiskApplicationService service = new RiskApplicationService(port);

    Long first =
        service.raise(1L, "SUP-1", "EXTERNAL", "evt-1", "HIGH", "CREDIT", "ev-1", new Date());
    Long second =
        service.raise(1L, "SUP-1", "EXTERNAL", "evt-1", "HIGH", "CREDIT", "ev-1", new Date());
    Long differentRef =
        service.raise(1L, "SUP-1", "EXTERNAL", "evt-2", "HIGH", "CREDIT", "ev-2", new Date());

    assertThat(second).isEqualTo(first);
    assertThat(differentRef).isNotEqualTo(first);
    assertThat(alerts.count()).isEqualTo(2);
    assertThat(port.findBySourceRef(1L, "SUP-1", "EXTERNAL", "evt-1")).isPresent();
  }

  @Test
  @DisplayName("findBySourceRef 按租户隔离，缺失返回 empty")
  void findBySourceRefIsTenantScoped() {
    FakeSdkStore<RiskAlert> alerts = new FakeSdkStore<>();
    FakeSdkStore<PerformanceScore> scores = new FakeSdkStore<>();
    RiskAlertRepository port = adapter(alerts, scores);

    port.save(
        RiskAlert.raise(1L, "SUP-1", "EXTERNAL", "evt-1", "HIGH", "CREDIT", "ev-1", new Date()));

    assertThat(port.findBySourceRef(1L, "SUP-1", "EXTERNAL", "evt-1")).isPresent();
    assertThat(port.findBySourceRef(2L, "SUP-1", "EXTERNAL", "evt-1")).isEmpty();
    assertThat(port.findBySourceRef(1L, "SUP-2", "EXTERNAL", "evt-1")).isEmpty();
    assertThat(port.findBySourceRef(1L, "SUP-1", "OTHER", "evt-1")).isEmpty();
  }

  @Test
  @DisplayName("findScores 只返回目标租户+供应商的绩效投影")
  void findScoresAreTenantAndSupplierScoped() {
    FakeSdkStore<RiskAlert> alerts = new FakeSdkStore<>();
    FakeSdkStore<PerformanceScore> scores = new FakeSdkStore<>();
    PerformanceScore own = new PerformanceScore();
    own.setTenantId(1L);
    own.setSupplierId("SUP-1");
    own.setTotalScore(new BigDecimal("88.8"));
    scores.save(own);
    PerformanceScore other = new PerformanceScore();
    other.setTenantId(1L);
    other.setSupplierId("SUP-2");
    scores.save(other);
    RiskAlertRepository port = adapter(alerts, scores);

    List<PerformanceScore> actual = port.findScores(1L, "SUP-1");
    assertThat(actual).hasSize(1);
    assertThat(actual.get(0).getTotalScore()).isEqualByComparingTo(new BigDecimal("88.8"));
    assertThat(port.findScores(2L, "SUP-1")).isEmpty();
  }

  @Test
  @DisplayName("新适配器实例（模拟重启）读回同一份预警数据，字段一致")
  void newAdapterInstanceReadsBackSameState() {
    FakeSdkStore<RiskAlert> alerts = new FakeSdkStore<>();
    FakeSdkStore<PerformanceScore> scores = new FakeSdkStore<>();
    RiskAlertRepository first = adapter(alerts, scores);

    Long id =
        first.save(
            RiskAlert.raise(
                3L, "SUP-3", "EXTERNAL", "evt-3", "UNKNOWN", "CREDIT", "ev-3", new Date()));

    RiskAlertRepository restarted = adapter(alerts, scores);
    RiskAlert actual = restarted.findBySourceRef(3L, "SUP-3", "EXTERNAL", "evt-3").orElseThrow();
    assertThat(actual.getId()).isEqualTo(id);
    assertThat(actual.getTenantId()).isEqualTo(3L);
    assertThat(actual.getSupplierId()).isEqualTo("SUP-3");
    assertThat(actual.getSourceRef()).isEqualTo("evt-3");
    assertThat(actual.getSeverity()).isEqualTo("UNKNOWN");
  }

  private RiskAlertRepository adapter(
      FakeSdkStore<RiskAlert> alerts, FakeSdkStore<PerformanceScore> scores) {
    RiskAlertSqlRepository alertRepo = mock(RiskAlertSqlRepository.class, CALLS_REAL_METHODS);
    doAnswer(invocation -> alerts.save(invocation.getArgument(0))).when(alertRepo).save(any());
    doAnswer(invocation -> alerts.findByCriteria(invocation.getArgument(0)))
        .when(alertRepo)
        .findByCriteria(any());

    PerformanceScoreSqlRepository scoreRepo =
        mock(PerformanceScoreSqlRepository.class, CALLS_REAL_METHODS);
    doAnswer(invocation -> scores.findByCriteria(invocation.getArgument(0)))
        .when(scoreRepo)
        .findByCriteria(any());

    return new RiskAlertRepositoryAdapter(alertRepo, scoreRepo);
  }
}
