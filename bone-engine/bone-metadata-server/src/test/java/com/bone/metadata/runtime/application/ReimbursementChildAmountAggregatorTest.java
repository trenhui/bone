package com.bone.metadata.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.metadata.engine.runtime.JdbcRuntimeRecordService;
import com.bone.metadata.engine.runtime.PublishedRuntimeEntity;
import com.bone.metadata.engine.runtime.RuntimeFieldColumn;
import com.bone.metadata.engine.runtime.RuntimeRelationSpec;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * {@link ReimbursementChildAmountAggregator} 的子表金额聚合回填，外键列必须取自 {@link RuntimeRelationSpec} 声明的权威
 * {@code foreignKey}（如 {@code claim_id}），而非按实体编码拼接的 {@code expense_reimbursement_id}——
 * 后者在物理表中不存在，会导致 SUM 查询失败 / 头表金额回填错误（报销场景金额守恒契约 G10 §3）。
 */
@ExtendWith(MockitoExtension.class)
class ReimbursementChildAmountAggregatorTest {

  @Mock private NamedParameterJdbcTemplate jdbc;
  @Mock private JdbcRuntimeRecordService recordService;

  @Test
  void sumUsesCanonicalForeignKey_claimId_not_expenseClaimId() {
    ReimbursementChildAmountAggregator aggregator =
        new ReimbursementChildAmountAggregator(jdbc, recordService);
    PublishedRuntimeEntity parent =
        new PublishedRuntimeEntity(
            "expense_reimbursement", "t_expense_reimbursement", "id", 1L, List.of());
    RuntimeRelationSpec spec =
        RuntimeRelationSpec.of(
            new RuntimeFieldColumn(
                "items",
                "CHILD_TABLE",
                false,
                false,
                false,
                null,
                null,
                "{\"childTable\":{\"childEntityCode\":\"expense_reimbursement_item\","
                    + "\"foreignKey\":\"claim_id\",\"orderColumn\":\"sort_order\"}}"));

    when(jdbc.queryForObject(any(String.class), any(MapSqlParameterSource.class), eq(String.class)))
        .thenReturn("DRAFT");
    when(jdbc.queryForObject(
            any(String.class), any(MapSqlParameterSource.class), eq(BigDecimal.class)))
        .thenReturn(new BigDecimal("100"));

    BigDecimal result =
        aggregator.aggregateAndBackfill(parent, 1L, "9", "expense_reimbursement_item", spec);

    assertThat(result).isEqualByComparingTo("100");
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbc)
        .queryForObject(
            sqlCaptor.capture(), any(MapSqlParameterSource.class), eq(BigDecimal.class));
    String sumSql = sqlCaptor.getValue();
    assertThat(sumSql).contains("claim_id").contains("t_expense_reimbursement_item");
    assertThat(sumSql).doesNotContain("expense_reimbursement_id");
  }

  @Test
  void prefixedChildEntityCodeStillAggregates() {
    // 回归（2026-10-17 诊断修复）：RuntimeRelationSpec javadoc 示例曾带 t_ 前缀，按旧示例配置的
    // 关系声明传入时 equals 失配 ⇒ 聚合器静默返回 null，G10 头表金额回填无报错失效。
    ReimbursementChildAmountAggregator aggregator =
        new ReimbursementChildAmountAggregator(jdbc, recordService);
    PublishedRuntimeEntity parent =
        new PublishedRuntimeEntity(
            "expense_reimbursement", "t_expense_reimbursement", "id", 1L, List.of());
    RuntimeRelationSpec spec =
        RuntimeRelationSpec.of(
            new RuntimeFieldColumn(
                "items",
                "CHILD_TABLE",
                false,
                false,
                false,
                null,
                null,
                "{\"childTable\":{\"childEntityCode\":\"expense_reimbursement_item\","
                    + "\"foreignKey\":\"claim_id\",\"orderColumn\":\"sort_order\"}}"));

    when(jdbc.queryForObject(any(String.class), any(MapSqlParameterSource.class), eq(String.class)))
        .thenReturn("DRAFT");
    when(jdbc.queryForObject(
            any(String.class), any(MapSqlParameterSource.class), eq(BigDecimal.class)))
        .thenReturn(new BigDecimal("100"));

    BigDecimal result =
        aggregator.aggregateAndBackfill(parent, 1L, "9", "t_expense_reimbursement_item", spec);

    assertThat(result).isEqualByComparingTo("100");
  }
}
