package com.bone.ailedger.infrastructure.ledger;

import com.bone.ailedger.domain.gateway.LedgerAuditPort;
import com.bone.ailedger.domain.model.LedgerAuditRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认额度审计实现：结构化日志留痕（接入方以 {@code @Bean} 覆盖为落库 / 数仓 / MQ）。
 *
 * <p>只记日志不落库是刻意的默认：库形态模块不自带 DDL 表（与 bone-ai 其他模块一致）， 而「不留痕」对碰钱内核不可接受，
 * 故默认至少把机械真相写进可采集的日志流，接入方按需落到持久层。
 */
public class LoggingLedgerAuditAdapter implements LedgerAuditPort {

  private static final Logger log = LoggerFactory.getLogger(LoggingLedgerAuditAdapter.class);

  @Override
  public void record(LedgerAuditRecord record) {
    log.info(
        "额度变异审计: op={}, scope={}/{}, reservation={}, amount={}, reason={}, "
            + "before(limit={},consumed={},reserved={},frozen={},available={}), "
            + "after(limit={},consumed={},reserved={},frozen={},available={}), at={}",
        record.operation(),
        record.scope().dimension(),
        record.scope().scopeKey(),
        record.reservationId(),
        record.amount(),
        record.reason(),
        record.before().limit(),
        record.before().consumed(),
        record.before().reserved(),
        record.before().frozen(),
        record.before().available(),
        record.after().limit(),
        record.after().consumed(),
        record.after().reserved(),
        record.after().frozen(),
        record.after().available(),
        record.occurredAt());
  }
}
