package com.bone.metadata.sdk.test.testcase;

import static org.junit.jupiter.api.Assertions.*;

import com.bone.core.model.PageResult;
import com.bone.core.tenant.context.TenantContext;
import com.bone.metadata.sdk.domain.exception.MissingTenantContextException;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.test.config.TestConfig;
import com.bone.metadata.sdk.test.domain.TenantMetric;
import com.bone.metadata.sdk.test.repository.impl.TenantMetricRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * 聚合通道租户/软删谓词注入的回归测试。
 *
 * <p>修复前：{@code AggregationBuilder}/{@code CountAggregationBuilder} 不注入租户与软删谓词， 导致租户表聚合越权（违反
 * ADR-0029 fail-closed）且软删表聚合含脏数据——这正是 HC-006 中两个指标网关被迫回退裸 JDBC 的根因。
 * 本测试覆盖：当前租户作用域、软删排除、includeDeleted opt-in、无租户上下文 fail-closed、caller-EQ 兜底。
 */
@SpringBootTest(classes = TestConfig.class)
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@Slf4j
public class TenantMetricAggregationTest {

  @Autowired private TenantMetricRepository tenantMetricRepository;
  @Autowired private NamedParameterJdbcOperations jdbc;

  @BeforeEach
  public void setUp() {
    TenantContext.clear();
    jdbc.update("DELETE FROM tenant_metric", new MapSqlParameterSource());
  }

  @AfterEach
  public void tearDown() {
    TenantContext.clear();
  }

  private void insert(Long tenantId, String category, boolean deleted) {
    MapSqlParameterSource p = new MapSqlParameterSource();
    p.addValue("category", category);
    p.addValue("amount", new BigDecimal("10.00"));
    p.addValue("status", "ACTIVE");
    p.addValue("tenantId", tenantId);
    p.addValue("deleted", deleted ? 1 : 0);
    jdbc.update(
        "INSERT INTO tenant_metric (category, amount, status, tenant_id, deleted) "
            + "VALUES (:category, :amount, :status, :tenantId, :deleted)",
        p);
  }

  @Test
  public void aggregateScopedToCurrentTenantAndExcludesSoftDeleted() {
    TenantContext.setTenantId(1L);
    insert(1L, "A", false); // 计入
    insert(1L, "A", true); // 被软删排除
    insert(2L, "B", false); // 跨租户排除

    Map<String, Object> result =
        tenantMetricRepository.aggregate(
            List.of("COUNT(*) as total"), Criteria.<TenantMetric>create());

    assertNotNull(result);
    assertEquals(1L, ((Number) result.get("total")).longValue());
  }

  @Test
  public void aggregateWithIncludeDeletedCountsSoftDeletedWithinTenant() {
    TenantContext.setTenantId(1L);
    insert(1L, "A", false);
    insert(1L, "A", true);
    insert(2L, "B", false);

    Map<String, Object> result =
        tenantMetricRepository.aggregate(
            List.of("COUNT(*) as total"), Criteria.<TenantMetric>create(), true);

    assertEquals(2L, ((Number) result.get("total")).longValue());
  }

  @Test
  public void aggregateGroupByScopedToTenant() {
    TenantContext.setTenantId(1L);
    insert(1L, "A", false);
    insert(1L, "A", false);
    insert(1L, "C", false);
    insert(2L, "A", false);

    List<Map<String, Object>> results =
        tenantMetricRepository.aggregate(
            List.of("category", "COUNT(*) as cnt"),
            Criteria.<TenantMetric>create().eq("status", "ACTIVE"),
            List.of("category"));

    assertEquals(2, results.size()); // 租户 1 仅有 A、C 两组
    long sum = results.stream().mapToLong(r -> ((Number) r.get("cnt")).longValue()).sum();
    assertEquals(3L, sum); // A(2) + C(1)
    List<String> cats =
        results.stream().map(r -> (String) r.get("category")).sorted().collect(Collectors.toList());
    assertEquals(List.of("A", "C"), cats);
  }

  @Test
  public void aggregateWithoutTenantContextAndNoCallerEqFailsClosed() {
    TenantContext.clear();
    insert(1L, "A", false);
    insert(2L, "B", false);

    assertThrows(
        MissingTenantContextException.class,
        () ->
            tenantMetricRepository.aggregate(
                List.of("COUNT(*) as total"), Criteria.<TenantMetric>create()));
  }

  @Test
  public void aggregateCallerEqFallbackScopesToSingleTenant() {
    TenantContext.clear();
    insert(1L, "A", false);
    insert(2L, "B", false);

    Map<String, Object> result =
        tenantMetricRepository.aggregate(
            List.of("COUNT(*) as total"), Criteria.<TenantMetric>create().eq("tenantId", 2L));

    assertEquals(1L, ((Number) result.get("total")).longValue());
  }

  /**
   * 扩展表字段条件必须「显式失败」而非「静默丢弃」—— 静默丢弃会让聚合过滤失效、指标偏大， 比抛异常更危险。 这是一条回归锁：早期实现把 extConditions 拼成
   * `ext.<col>`（未知别名→SQL 报错）， 中间版本改为只取 mainConditions 后会静默漏过滤；本测试锁定为 fail-fast。
   */
  @Test
  public void aggregateWithExtensionFieldConditionFailsFast() {
    TenantContext.setTenantId(1L);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            tenantMetricRepository.aggregate(
                List.of("COUNT(*) as total"),
                Criteria.<TenantMetric>create().eqExtra("someExtField", "x")));
  }

  /**
   * {@code aggregateWithPagination} 不得改写调用方传入的 {@code Criteria}：分页参数随查询上下文传递。
   *
   * <p>回归锁：早期实现直接 criteria.setPage/setSize，把分页状态泄漏回调用方对象，调用方复用该 Criteria 时会意外被加上分页。 Criteria 默认
   * size=5000 / page=1，故断言其保持默认值即证明未被改写。
   */
  @Test
  public void aggregateWithPaginationDoesNotMutateCallerCriteria() {
    TenantContext.setTenantId(1L);
    insert(1L, "A", false);
    insert(1L, "B", false);
    insert(1L, "C", false);

    Criteria<TenantMetric> criteria = Criteria.<TenantMetric>create();
    PageResult<Map<String, Object>> page =
        tenantMetricRepository.aggregateWithPagination(
            List.of("category", "COUNT(*) as cnt"), criteria, List.of("category"), null, 1, 2);

    assertEquals(3L, page.getTotal()); // 3 个分组
    assertEquals(2, page.getRecords().size()); // 每页 2 条
    // 关键断言：调用方的 criteria 分页状态未被改写
    assertEquals(5000, criteria.getSize());
    assertEquals(1, criteria.getPage());
  }
}
