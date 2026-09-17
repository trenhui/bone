package com.bone.blueprint.infrastructure.query;

import com.bone.blueprint.application.query.dto.OrderHeadProjection;
import com.bone.blueprint.application.query.dto.OrderWithItemsProjection;
import com.bone.blueprint.application.query.port.OrderQueryPort;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.core.model.PageResult;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * 订单读侧适配器：{@link OrderQueryPort} 的基础设施实现（E-10.3 {@code infrastructure/query}）。
 *
 * <p>读模型直查投影、不加载写聚合；复杂联表 SQL 外置为资源文件，其余为内联常量。
 */
@Component
@RequiredArgsConstructor
public class OrderQueryAdapter implements OrderQueryPort {

  private static final String SQL_PATH = "sql/order/findOrderWithItems.sql";

  /** 超时未支付订单扫描（简单单表投影内联 SQL；复杂 Join 才外置 SQL 文件）。 */
  private static final String EXPIRED_CREATED_SQL =
      "SELECT tenant_id, id, customer_id, total_amount, status, created_at FROM t_order "
          + "WHERE deleted = 0 AND status = 'CREATED' AND created_at < :before";

  private final NamedParameterJdbcTemplate jdbcTemplate;

  private volatile String cachedSql;

  @Override
  public List<OrderWithItemsProjection> findOrderWithItems(long tenantId, long orderId) {
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("orderId", orderId);
    return jdbcTemplate.query(loadSql(), params, new OrderWithItemsRowMapper());
  }

  @Override
  public List<OrderHeadProjection> findCreatedExpiredBeforeAllTenants(Instant before) {
    // 全租户运维扫描：不带 tenant_id 条件；调用方须为已登记的定时任务（README 打洞登记）
    return jdbcTemplate.query(
        EXPIRED_CREATED_SQL,
        new MapSqlParameterSource().addValue("before", Timestamp.from(before)),
        new OrderHeadRowMapper());
  }

  @Override
  public Optional<String> findStatusById(long tenantId, long orderId) {
    String sql =
        "SELECT status FROM t_order WHERE tenant_id = :tenantId AND id = :orderId AND deleted = 0";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("orderId", orderId);
    List<String> statuses = jdbcTemplate.queryForList(sql, params, String.class);
    return statuses.stream().findFirst();
  }

  @Override
  public PageResult<OrderHeadProjection> findOrderPage(
      long tenantId, Long customerId, OrderStatus status, int pageNum, int pageSize) {
    // 动态条件拼接（QueryPort 内做读侧组装，QueryHandler 只依赖端口）
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId);
    StringBuilder where = new StringBuilder(" WHERE tenant_id = :tenantId AND deleted = 0");
    if (customerId != null) {
      where.append(" AND customer_id = :customerId");
      params.addValue("customerId", customerId);
    }
    if (status != null) {
      where.append(" AND status = :status");
      params.addValue("status", status.name());
    }

    Long total =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_order" + where, params, Long.class);

    String pageSql =
        "SELECT tenant_id, id, customer_id, total_amount, status, created_at FROM t_order"
            + where
            + " ORDER BY created_at DESC LIMIT :limit OFFSET :offset";
    params.addValue("limit", pageSize).addValue("offset", (long) (pageNum - 1) * pageSize);
    List<OrderHeadProjection> rows = jdbcTemplate.query(pageSql, params, new OrderHeadRowMapper());
    return PageResult.of(rows, total == null ? 0 : total, pageNum, pageSize);
  }

  private String loadSql() {
    if (cachedSql == null) {
      synchronized (this) {
        if (cachedSql == null) {
          try {
            ClassPathResource resource = new ClassPathResource(SQL_PATH);
            cachedSql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
          } catch (IOException e) {
            throw new IllegalStateException("无法加载读侧 SQL: " + SQL_PATH, e);
          }
        }
      }
    }
    return cachedSql;
  }

  private static final class OrderWithItemsRowMapper
      implements RowMapper<OrderWithItemsProjection> {

    @Override
    public OrderWithItemsProjection mapRow(ResultSet rs, int rowNum) throws SQLException {
      Long orderId = rs.getLong("order_id");
      Long customerId = rs.getLong("customer_id");
      BigDecimal totalAmount = rs.getBigDecimal("total_amount");
      String status = rs.getString("status");
      LocalDateTime createdAt = toLocalDateTime(rs.getTimestamp("created_at"));
      // 明细列：LEFT JOIN 无匹配行时 item_id 为 NULL，明细字段整体置空
      long itemId = rs.getLong("item_id");
      Long itemIdBoxed = rs.wasNull() ? null : itemId;
      Long productId = itemIdBoxed == null ? null : rs.getLong("product_id");
      String productName = itemIdBoxed == null ? null : rs.getString("product_name");
      Integer quantity = itemIdBoxed == null ? null : rs.getInt("quantity");
      BigDecimal unitPrice = itemIdBoxed == null ? null : rs.getBigDecimal("unit_price");
      BigDecimal subtotal = itemIdBoxed == null ? null : rs.getBigDecimal("subtotal");
      return new OrderWithItemsProjection(
          orderId,
          customerId,
          totalAmount,
          status,
          createdAt,
          itemIdBoxed,
          productId,
          productName,
          quantity,
          unitPrice,
          subtotal);
    }
  }

  private static final class OrderHeadRowMapper implements RowMapper<OrderHeadProjection> {

    @Override
    public OrderHeadProjection mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new OrderHeadProjection(
          rs.getLong("tenant_id"),
          rs.getLong("id"),
          rs.getLong("customer_id"),
          rs.getBigDecimal("total_amount"),
          rs.getString("status"),
          toLocalDateTime(rs.getTimestamp("created_at")));
    }
  }

  private static LocalDateTime toLocalDateTime(Timestamp ts) {
    return ts == null ? null : ts.toLocalDateTime();
  }
}
