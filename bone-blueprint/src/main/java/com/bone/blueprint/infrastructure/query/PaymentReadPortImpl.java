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

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  public Optional<PaymentRow> findById(long tenantId, long paymentId) {
    String sql =
        "SELECT id, order_id, customer_id, amount, channel, status, channel_trade_no, "
            + "pay_url, paid_at, refunded_at, refund_amount, created_at "
            + "FROM bp_payment WHERE tenant_id = :tenantId AND id = :id AND deleted = 0";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", paymentId);
    List<PaymentRow> rows = jdbcTemplate.query(sql, params, new PaymentRowMapper());
    return rows.stream().findFirst();
  }

  @Override
  public List<PaymentRow> findPayableExpiredBefore(long tenantId, Instant before) {
    String sql =
        "SELECT id, order_id, customer_id, amount, channel, status, channel_trade_no, "
            + "pay_url, paid_at, refunded_at, refund_amount, created_at "
            + "FROM bp_payment "
            + "WHERE tenant_id = :tenantId AND deleted = 0 "
            + "AND status IN ('PENDING','PAYING') AND created_at < :before";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("before", Timestamp.from(before));
    return jdbcTemplate.query(sql, params, new PaymentRowMapper());
  }

  private static final class PaymentRowMapper implements RowMapper<PaymentRow> {

    @Override
    public PaymentRow mapRow(ResultSet rs, int rowNum) throws SQLException {
      PaymentRow row = new PaymentRow();
      row.setPaymentId(rs.getLong("id"));
      row.setOrderId(rs.getLong("order_id"));
      row.setCustomerId(rs.getLong("customer_id"));
      row.setAmount(rs.getBigDecimal("amount"));
      row.setChannel(rs.getString("channel"));
      row.setStatus(rs.getString("status"));
      row.setChannelTradeNo(rs.getString("channel_trade_no"));
      row.setPayUrl(rs.getString("pay_url"));
      row.setPaidAt(toInstant(rs.getTimestamp("paid_at")));
      row.setRefundedAt(toInstant(rs.getTimestamp("refunded_at")));
      row.setRefundAmount(rs.getBigDecimal("refund_amount"));
      row.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
      return row;
    }

    private Instant toInstant(Timestamp ts) {
      return ts == null ? null : ts.toInstant();
    }
  }
}
