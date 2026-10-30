package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.model.decision.DecisionResult;
import com.bone.claim.domain.model.decision.DecisionTier;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 决策消费结果落库测试。
 *
 * <p>除了行为，还钉死一条**取数契约**：仓储 SQL 的 SELECT 列表必须包含新增列。SDK 按 SELECT 返回的 列名映射字段，实体加了 {@code @Column} 而
 * SQL 没选它，字段就会静默为 null——「配了不生效」的经典 形态：代码看着对、门禁全绿、线上永远查不到原因。这里用资源文件断言把它变成会红的测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DecisionExecutionConsumptionTest {

  private static final String SQL_DIR =
      "sql/com/bone/claim/domain/repository/DecisionExecutionRepository/";

  @Mock private DecisionExecutionRepository repository;
  @Mock private DomainEventPublisher domainEventPublisher;

  private DecisionExecutionApplicationService service;

  @BeforeEach
  void setUp() {
    service = new DecisionExecutionApplicationService(repository, domainEventPublisher);
  }

  @Test
  @DisplayName("落库：按 traceId 定位执行记录并写入消费结果")
  void recordsConsumption() {
    DecisionExecution execution = completedExecution();
    when(repository.findByTraceId("t-1")).thenReturn(execution);

    service.recordConsumption("t-1", "CAP_EXCEEDED");

    ArgumentCaptor<DecisionExecution> captor = ArgumentCaptor.forClass(DecisionExecution.class);
    verify(repository).update(captor.capture());
    assertThat(captor.getValue().getConsumptionReason()).isEqualTo("CAP_EXCEEDED");
    assertThat(captor.getValue().getConsumedAt()).isNotNull();
  }

  @Test
  @DisplayName("落库：找不到执行记录时不更新，也不抛出（资金动作已完成，不能因诊断信息失败）")
  void skipsWhenExecutionMissing() {
    when(repository.findByTraceId(anyString())).thenReturn(null);

    service.recordConsumption("t-missing", "APPLIED");

    verify(repository, never()).update(any());
  }

  @Test
  @DisplayName("取数契约：三个仓储 SQL 都选了 consumption_reason / consumed_at")
  void sqlSelectsConsumerColumns() throws Exception {
    for (String file :
        List.of("findActiveByClaimId.sql", "findLatestByClaimId.sql", "findByTraceId.sql")) {
      String sql = readResource(SQL_DIR + file);
      assertThat(sql).as("%s 必须选 consumption_reason", file).contains("consumption_reason");
      assertThat(sql).as("%s 必须选 consumed_at", file).contains("consumed_at");
    }
  }

  private static String readResource(String path) throws Exception {
    try (InputStream in =
        DecisionExecutionConsumptionTest.class.getClassLoader().getResourceAsStream(path)) {
      assertThat(in).as("资源不存在：%s", path).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static DecisionExecution completedExecution() {
    DecisionExecution execution =
        DecisionExecution.start(
            9001L, 42L, "t-1", 1001L, "graph-v1", "rule-v1", "model-v1", "prompt-v1", "cat-v1");
    execution.complete(
        DecisionResult.AUTO_APPROVE, DecisionTier.AUTO, new BigDecimal("0.9"), false);
    return execution;
  }
}
