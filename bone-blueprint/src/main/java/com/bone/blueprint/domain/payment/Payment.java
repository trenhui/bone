package com.bone.blueprint.domain.payment;

import com.bone.blueprint.domain.payment.event.PaymentFailedEvent;
import com.bone.blueprint.domain.payment.event.PaymentRefundedEvent;
import com.bone.blueprint.domain.payment.event.PaymentSucceededEvent;
import com.bone.blueprint.domain.payment.valueobject.PaymentChannel;
import com.bone.blueprint.domain.payment.valueobject.PaymentStatus;
import com.bone.blueprint.domain.shared.exception.StateConflictException;
import com.bone.blueprint.domain.shared.valueobject.Money;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 支付单聚合根（支付限界上下文）。
 *
 * <p>独立于订单聚合，通过 {@code orderId} 关联订单，遵循「聚合间仅以 ID 引用」原则（P-3.1）。支付 成功经 {@link
 * #confirmSuccess(String)} 幂等确认，避免渠道重复回调造成重复入账。
 *
 * <p>状态机：{@link PaymentStatus#PENDING} → {@link PaymentStatus#PAYING} → {@link
 * PaymentStatus#SUCCESS} / {@link PaymentStatus#FAILED} / {@link PaymentStatus#CLOSED}。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("bp_payment")
public class Payment extends TenantAggregateRoot<Long> {

  private Long orderId;
  private Long customerId;
  private BigDecimal amount;
  private PaymentChannel channel;
  private PaymentStatus status;

  /** 外部支付渠道流水号（渠道回填，幂等去重键）。 */
  private String channelTradeNo;

  /** 支付链接（渠道预下单返回，用户据此完成支付）。 */
  private String payUrl;

  private Instant paidAt;
  private Instant refundedAt;
  private BigDecimal refundAmount;
  private Instant createdAt;
  private Instant updatedAt;

  /**
   * 乐观锁版本号（E-5.3：可并发写聚合必须声明并验证并发策略）。
   *
   * <p><b>已生效（v5.6 落地）</b>：领域行为方法末尾调用 {@link #incrementVersion()} 让版本递增为新值； 应用层更新场景统一走 {@code
   * PaymentRepository#saveWithVersionCheck}，以 {@code WHERE id = ? AND version = entity.version - 1}
   * 条件更新，数据库层面保证原子。
   *
   * <p>version 递增的唯一入口是 {@link #incrementVersion()}，禁止外部直接 setVersion。
   */
  private Long version;

  /** 乐观锁版本递增器——每次状态迁移末尾调用，保证与 saveWithVersionCheck 的 WHERE 条件匹配。 */
  private void incrementVersion() {
    this.version = (this.version == null ? 0L : this.version) + 1L;
  }

  /**
   * 应付金额（值对象视图）。
   *
   * <p>与 {@code Order.getTotalMoney()} 保持同构：金额一律经 {@link Money} 运算，禁止在领域内裸比 {@code
   * BigDecimal}，避免样板出现「金额两套口径」。
   */
  public Money getAmountMoney() {
    return amount == null ? Money.zero() : Money.of(amount);
  }

  /** 已退金额（值对象视图），未退款时为 0。 */
  public Money getRefundedMoney() {
    return refundAmount == null ? Money.zero() : Money.of(refundAmount);
  }

  private Payment(
      Long id,
      Long tenantId,
      Long orderId,
      Long customerId,
      BigDecimal amount,
      PaymentChannel channel,
      String payUrl) {
    this.setId(id);
    this.setTenantId(tenantId);
    this.orderId = orderId;
    this.customerId = customerId;
    this.amount = amount;
    this.channel = channel;
    this.status = PaymentStatus.PENDING;
    this.payUrl = payUrl;
    this.version = 0L;
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  /**
   * 由订单发起支付：创建一笔处于 PENDING 的支付单。
   *
   * <p>金额由订单总额决定；入参 {@code amount} 须与订单一致，调用方（Handler）负责从订单读取。
   */
  public static Payment create(
      Long id,
      Long tenantId,
      Long orderId,
      Long customerId,
      BigDecimal amount,
      PaymentChannel channel,
      String payUrl) {
    if (id == null || orderId == null || customerId == null) {
      throw new DomainException("支付单必填字段缺失");
    }
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new DomainException("支付金额必须大于0");
    }
    return new Payment(id, tenantId, orderId, customerId, amount, channel, payUrl);
  }

  /**
   * 向支付渠道提交并回填支付链接：进入支付中，等待用户完成支付（PENDING → PAYING）。
   *
   * <p><b>为何拆为独立行为</b>：渠道预下单是远程调用，须在 DB 事务外执行（见 {@code PaymentApplicationService#initiate}
   * 两段式），故「回填链接 + 状态迁移」由本方法在回写事务中完成； 提交前强制校验链接非空，防止渠道返回异常时写入脏数据。
   */
  public void submitToChannel(String payUrl) {
    if (this.status != PaymentStatus.PENDING) {
      throw new StateConflictException("只有新建状态的支付单可以提交支付");
    }
    if (payUrl == null || payUrl.isBlank()) {
      throw new DomainException("支付链接不能为空");
    }
    this.payUrl = payUrl;
    this.status = PaymentStatus.PAYING;
    this.updatedAt = Instant.now();
    incrementVersion();
  }

  /**
   * 渠道回调确认支付成功（幂等 + 金额一致性校验）。
   *
   * <p>幂等：已在 {@link PaymentStatus#SUCCESS} 的支付单再次回调，若 {@code channelTradeNo} 相同则直接
   * 返回（跳过、不重复发事件）；若不同视为异常渠道状态，抛错（防止渠道流水号被篡改覆盖）。PENDING/PAYING 可成功；FAILED/CLOSED 不能再成功。
   *
   * <p>金额一致性：实付金额 {@code paidAmount} 必须与应付金额 {@link #amount} 相等（业界支付核心不变量）， 防止部分支付/金额被篡改的异常入账。
   *
   * @param channelTradeNo 渠道流水号（幂等去重键）
   * @param paidAmount 渠道实付金额，须与应付金额一致
   * @return 本次调用是否真正完成状态迁移（false 表示幂等跳过）
   */
  public boolean confirmSuccess(String channelTradeNo, BigDecimal paidAmount) {
    if (this.status == PaymentStatus.SUCCESS) {
      if (!Objects.equals(this.channelTradeNo, channelTradeNo)) {
        throw new DomainException("支付单已成功，但回调渠道流水号不一致");
      }
      return false; // 幂等：同流水号重复回调直接跳过
    }
    if (this.status == PaymentStatus.FAILED || this.status == PaymentStatus.CLOSED) {
      throw new StateConflictException("已失败或已关闭的支付单无法确认成功");
    }
    if (paidAmount == null || paidAmount.compareTo(this.amount) != 0) {
      throw new DomainException("实付金额与应付金额不一致: paid=" + paidAmount + ", expect=" + this.amount);
    }
    this.status = PaymentStatus.SUCCESS;
    this.channelTradeNo = channelTradeNo;
    this.paidAt = Instant.now();
    this.updatedAt = Instant.now();
    incrementVersion();
    addDomainEvent(
        new PaymentSucceededEvent(
            getId(), getTenantId(), orderId, amount, channelTradeNo, Instant.now()));
    return true;
  }

  /** 渠道明确失败。 */
  public void markFailed(String channelTradeNo) {
    if (this.status == PaymentStatus.SUCCESS) {
      throw new StateConflictException("已成功的支付单不能标记为失败");
    }
    if (this.status == PaymentStatus.FAILED || this.status == PaymentStatus.CLOSED) {
      return;
    }
    this.status = PaymentStatus.FAILED;
    this.channelTradeNo = channelTradeNo;
    this.updatedAt = Instant.now();
    incrementVersion();
    addDomainEvent(new PaymentFailedEvent(getId(), getTenantId(), orderId, amount, Instant.now()));
  }

  /**
   * 超时未支付 / 订单取消时关闭支付单。
   *
   * <p><b>不发 DomainEvent</b>：CLOSED 是支付单终态，下游不再关心此聚合的后续状态迁移 （订单在取消流程中自行关闭支付单，不依赖支付单反向通知）。
   */
  public void close() {
    if (this.status == PaymentStatus.SUCCESS) {
      throw new StateConflictException("已成功的支付单不能关闭");
    }
    if (this.status == PaymentStatus.CLOSED) {
      return;
    }
    this.status = PaymentStatus.CLOSED;
    this.updatedAt = Instant.now();
    incrementVersion();
  }

  /** 是否可发起退款（已成功且尚未退款）。与 {@link #refund} 的前置条件对应。 */
  public boolean isRefundable() {
    return this.status == PaymentStatus.SUCCESS && this.refundedAt == null;
  }

  /**
   * 退款（仅已成功支付单可退款）。
   *
   * <p><b>每单仅支持一次退款</b>：本样板不实现分批累计退款（真实业务如需分批，应改为累计 {@code refundedAmount} 并校验 累计额 ≤ 已付金额）。
   *
   * <p>幂等语义（资金操作，不能静默失败）：已退款的支付单重复请求时，若金额与上次**完全一致**视为重复提交， 返回 {@code false}
   * 跳过；若金额**不一致**则视为新的退款意图（很可能是误操作或对账异常），**抛错**让调用方明确感知， 而非静默吞掉。
   *
   * @param refundAmount 退款金额（> 0 且 ≤ 已付金额）
   * @return 本次调用是否真正执行退款（false 表示幂等跳过）
   */
  public boolean refund(BigDecimal refundAmount) {
    if (this.status != PaymentStatus.SUCCESS) {
      throw new StateConflictException("只有已成功的支付单可以退款");
    }
    // Money 构造即校验：null →「金额不能为空」，负数 →「金额不能为负」
    Money refundMoney = Money.of(refundAmount);
    // 0 元是合法的 Money（零元订单），但「退 0 元」在业务上无意义且易掩盖调用方 bug，须显式拒绝。
    // 注意：不能依赖 Money 拦截——它只拒绝负数。
    if (refundMoney.isZero()) {
      throw new DomainException("退款金额必须大于0");
    }
    if (this.refundedAt != null) {
      if (this.refundAmount != null && this.refundAmount.compareTo(refundAmount) != 0) {
        throw new DomainException(
            "支付单已退款，且本次退款金额与原退款金额不一致: refunded="
                + this.refundAmount
                + ", requested="
                + refundAmount);
      }
      return false; // 幂等：金额完全一致的重复退款直接跳过
    }
    if (refundMoney.greaterThan(getAmountMoney())) {
      throw new DomainException("退款金额不能超过已付金额: refund=" + refundAmount + ", paid=" + this.amount);
    }
    this.refundAmount = refundAmount;
    this.refundedAt = Instant.now();
    this.updatedAt = Instant.now();
    incrementVersion();
    addDomainEvent(
        new PaymentRefundedEvent(
            getId(), getTenantId(), orderId, refundAmount, this.channelTradeNo, Instant.now()));
    return true;
  }

  /** 支付单是否已成功（用于订单确认支付的前置判断）。 */
  public boolean isSuccess() {
    return this.status == PaymentStatus.SUCCESS;
  }
}
