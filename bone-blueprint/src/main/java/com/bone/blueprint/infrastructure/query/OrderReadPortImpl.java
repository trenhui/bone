package com.bone.blueprint.infrastructure.query;

import com.bone.blueprint.domain.gateway.OrderReadPort;
import com.bone.blueprint.domain.order.read.OrderHeadRow;
import com.bone.blueprint.domain.order.read.OrderWithItemsRow;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
@RequiredArgsConstructor
public class OrderReadPortImpl implements OrderReadPort {

  private static final String SQL_PATH = "sql/order/findOrderWithItems.sql";

  private final NamedParameterJdbcTemplate jdbcTemplate;

  private volatile String cachedSql;

  @Override
  public List<OrderWithItemsRow> findOrderWithItems(long tenantId, long orderId) {
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("orderId", orderId);
    return jdbcTemplate.query(loadSql(), params, new OrderWithItemsRowMapper());
  }

  @Override
  public List<OrderHeadRow> findCreatedExpiredBefore(long tenantId, Instant before) {
    // 简单单表投影内联 SQL（与 PaymentReadPortImpl 风格一致）；复杂 Join 才外置 SQL 文件
    String sql =
        "SELECT id, customer_id, total_amount, status, created_at FROM t_order "
            + "WHERE tenant_id = :tenantId AND deleted = 0 "
            + "AND status = 'CREATED' AND created_at < :before";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("before", Timestamp.from(before));
    return jdbcTemplate.query(sql, params, new OrderHeadRowMapper());
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

  private static final class OrderWithItemsRowMapper implements RowMapper<OrderWithItemsRow> {

    @Override
    public OrderWithItemsRow mapRow(ResultSet rs, int rowNum) throws SQLException {
      OrderWithItemsRow row = new OrderWithItemsRow();
      row.setOrderId(rs.getLong("order_id"));
      row.setCustomerId(rs.getLong("customer_id"));
      row.setTotalAmount(rs.getBigDecimal("total_amount"));
      row.setStatus(rs.getString("status"));
      Timestamp createdAt = rs.getTimestamp("created_at");
      if (createdAt != null) {
        row.setCreatedAt(createdAt.toLocalDateTime());
      }
      long itemId = rs.getLong("item_id");
      if (!rs.wasNull()) {
        row.setItemId(itemId);
        row.setProductId(rs.getLong("product_id"));
        row.setProductName(rs.getString("product_name"));
        row.setQuantity(rs.getInt("quantity"));
        row.setUnitPrice(rs.getBigDecimal("unit_price"));
        row.setSubtotal(rs.getBigDecimal("subtotal"));
      }
      return row;
    }
  }

  private static final class OrderHeadRowMapper implements RowMapper<OrderHeadRow> {

    @Override
    public OrderHeadRow mapRow(ResultSet rs, int rowNum) throws SQLException {
      OrderHeadRow row = new OrderHeadRow();
      row.setOrderId(rs.getLong("id"));
      row.setCustomerId(rs.getLong("customer_id"));
      row.setTotalAmount(rs.getBigDecimal("total_amount"));
      row.setStatus(rs.getString("status"));
      Timestamp createdAt = rs.getTimestamp("created_at");
      if (createdAt != null) {
        row.setCreatedAt(createdAt.toLocalDateTime());
      }
      return row;
    }
  }
}
