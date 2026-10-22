package com.bone.claim.domain.model.settlement;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;

/**
 * 资金流水事件（`settle_funding_event`，追加式不可变）。
 *
 * <p>资金状态机只由 {@code SettlementOrder} 聚合根推进，每步状态变更落一条 {@code FundingEvent}——
 * 不可更新不可删除（append-only），是资金审计与三向对账（§11.7）的事实源。幂等键 {@code {claimNo}:{stage}:{attempt}}
 * 由应用层生成，同键重复写入返回已存事件（UK 拦截）。
 */
@Getter
@Table("settle_funding_event")
public class FundingEvent extends TenantAbstractEntity<Long> {

  @Column(name = "settlement_no", nullable = false)
  private String settlementNo;

  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "stage", nullable = false)
  private String stage;

  @Column(name = "amount", nullable = false)
  private BigDecimal amount;

  @Column(name = "operator")
  private String operator;

  @Column(name = "account_type")
  private String accountType;

  @Column(name = "idempotency_key", nullable = false)
  private String idempotencyKey;

  @Column(name = "event_time", nullable = false)
  private Instant eventTime;

  FundingEvent() {}

  FundingEvent(
      Long id,
      Long tenantId,
      String settlementNo,
      Long claimId,
      FundingStage stage,
      BigDecimal amount,
      String operator,
      AccountType accountType,
      String idempotencyKey,
      Instant eventTime) {
    setId(id);
    setTenantId(tenantId);
    this.settlementNo = settlementNo;
    this.claimId = claimId;
    this.stage = stage.name();
    this.amount = amount;
    this.operator = operator;
    this.accountType = accountType == null ? AccountType.NO_ACCOUNT.name() : accountType.name();
    this.idempotencyKey = idempotencyKey;
    this.eventTime = eventTime;
  }

  static FundingEvent of(
      Long id,
      Long tenantId,
      String settlementNo,
      Long claimId,
      FundingStage stage,
      BigDecimal amount,
      String operator,
      AccountType accountType,
      String idempotencyKey,
      Instant eventTime) {
    return new FundingEvent(
        id,
        tenantId,
        settlementNo,
        claimId,
        stage,
        amount,
        operator,
        accountType,
        idempotencyKey,
        eventTime);
  }

  public FundingStage stageEnum() {
    return FundingStage.valueOf(stage);
  }

  public AccountType accountTypeEnum() {
    return AccountType.valueOf(accountType);
  }
}
