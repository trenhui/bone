package com.bone.blueprint.infrastructure.query;

import com.bone.blueprint.domain.gateway.PaymentReadPort;
import com.bone.blueprint.domain.payment.read.PaymentRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** 支付读侧实现：NamedParameterJdbcTemplate 直查 bp_payment（§18.5 读模型）。 */
@Component
@RequiredArgsConstructor
public class PaymentReadPortImpl implements PaymentReadPort {

  private static final String COLUMNS =
      "tenant_id, id, order_id, customer_id, amount, channel, status, channel_trade_no, "
          + "pay_url, paid_at, refunded_at, refund_amount, created_at";

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  public Optional<PaymentRow> findById(long tenantId, long paymentId) {
    String sql =
        "SELECT "
            + COLUMNS
            + " FROM bp_payment WHERE tenant_id = :tenantId AND id = :id AND deleted = 0";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", paymentId);
    List<PaymentRow> rows = jdbcTemplate.query(sql, params, new PaymentRowMapper());
    return rows.stream().findFirst();
  }

  @Override
  public List<PaymentRow> findPayableExpiredBefore(long tenantId, Instant before) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("before", Timestamp.from(before));
    return jdbcTemplate.query(
        PAYABLE_EXPIRED_SQL + " AND tenant_id = :tenantId", params, new PaymentRowMapper());
  }

  @Override
  public List<PaymentRow> findPayableExpiredBeforeAllTenants(Instant before) {
    // 全租户运维扫描：不带 tenant_id 条件；调用方须为已登记的定时任务
    return jdbcTemplate.query(
        PAYABLE_EXPIRED_SQL,
        new MapSqlParameterSource().addValue("before", Timestamp.from(before)),
        new PaymentRowMapper());
  }

  @Override
  public List<PaymentRow> findSuccessCreatedBeforeAllTenants(Instant before) {
    // 「钱货不一致」对账扫描：已成功的支付单，供调用方核对订单是否已确认支付
    String sql =
        "SELECT "
            + COLUMNS
            + " FROM bp_payment "
            + "WHERE deleted = 0 AND status = 'SUCCESS' AND created_at < :before";
    return jdbcTemplate.query(
        sql,
        new MapSqlParameterSource().addValue("before", Timestamp.from(before)),
        new PaymentRowMapper());
  }

  private static final String PAYABLE_EXPIRED_SQL =
      "SELECT "
          + COLUMNS
          + " FROM bp_payment "
          + "WHERE deleted = 0 AND status IN ('PENDING','PAYING') AND created_at < :before";

  private static final class PaymentRowMapper implements RowMapper<PaymentRow> {

    @Override
    public PaymentRow mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new PaymentRow(
          rs.getLong("tenant_id"),
          rs.getLong("id"),
          rs.getLong("order_id"),
          rs.getLong("customer_id"),
          rs.getBigDecimal("amount"),
          rs.getString("channel"),
          rs.getString("status"),
          rs.getString("channel_trade_no"),
          rs.getString("pay_url"),
          toInstant(rs.getTimestamp("paid_at")),
          toInstant(rs.getTimestamp("refunded_at")),
          rs.getBigDecimal("refund_amount"),
          toInstant(rs.getTimestamp("created_at")));
    }

    private Instant toInstant(Timestamp ts) {
      return ts == null ? null : ts.toInstant();
    }
  }
}
