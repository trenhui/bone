package com.bone.system.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bone.system.domain.console.KeyMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/** 校验 keyMetrics 的 graceful degradation：单表故障时该字段计 0，不影响其他字段。 */
@ExtendWith(MockitoExtension.class)
class JdbcKeyMetricsGatewayTest {

  @Mock JdbcTemplate jdbcTemplate;

  @Test
  void countsHappyPath() {
    when(jdbcTemplate.queryForObject(contains("iam_account"), eq(Long.class))).thenReturn(10L);
    when(jdbcTemplate.queryForObject(contains("meta_entity"), eq(Long.class))).thenReturn(20L);
    when(jdbcTemplate.queryForObject(contains("int_flow"), eq(Long.class))).thenReturn(30L);
    when(jdbcTemplate.queryForObject(contains("exts_plugin_version"), eq(Long.class)))
        .thenReturn(40L);

    KeyMetrics dto =
        new JdbcKeyMetricsGatewayAdapter(jdbcTemplate, new SimpleMeterRegistry()).collect();

    assertThat(dto.getUserCount()).isEqualTo(10);
    assertThat(dto.getEntityCount()).isEqualTo(20);
    assertThat(dto.getIntegrationFlowCount()).isEqualTo(30);
    assertThat(dto.getExtensionPluginCount()).isEqualTo(40);
  }

  @Test
  void singleTableFailureDoesNotPropagate() {
    when(jdbcTemplate.queryForObject(contains("iam_account"), eq(Long.class)))
        .thenThrow(new DataAccessResourceFailureException("boom"));
    when(jdbcTemplate.queryForObject(contains("meta_entity"), eq(Long.class))).thenReturn(2L);
    when(jdbcTemplate.queryForObject(contains("int_flow"), eq(Long.class))).thenReturn(3L);
    when(jdbcTemplate.queryForObject(contains("exts_plugin_version"), eq(Long.class)))
        .thenReturn(4L);

    KeyMetrics dto =
        new JdbcKeyMetricsGatewayAdapter(jdbcTemplate, new SimpleMeterRegistry()).collect();

    assertThat(dto.getUserCount()).isZero();
    assertThat(dto.getEntityCount()).isEqualTo(2);
    assertThat(dto.getIntegrationFlowCount()).isEqualTo(3);
    assertThat(dto.getExtensionPluginCount()).isEqualTo(4);
  }
}
