package com.bone.blueprint.domain.model.channelbuyer;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 「渠道买家 ↔ 内部客户」映射聚合根。
 *
 * <p><b>为何必须独立成聚合，而不是把 customerId 直接挂在订单上</b>：渠道买家身份是<strong>跨订单的长期身份</strong>，订单只是它的一次行为。
 * 若把映射做成订单的字段，则「改名后还能对上同一个人」「按渠道买家聚合售后」都无从实现； 而把它做成独立聚合后， 订单只保存 {@code customerId}
 * 快照，映射关系可独立演进（先影子、后绑定、可改绑）。
 *
 * <p><b>映射键为什么是渠道买家ID 而不是昵称</b>：昵称可被买家随时修改，改名即换人——早期用 {@code buyerNick.hashCode()} 当客户ID，
 * 造成同一人的订单被拆散、且无法与会员/积分/售后 join。渠道买家账号ID（淘宝 {@code buyer_user_id}、京东 {@code buyerdno}、 抖音 {@code
 * buyer_second_id}、拼多多 {@code user_id}）才是稳定身份。
 *
 * <p><b>影子客户（{@code customerId = 0}）的语义</b>：拉单时遇到没绑过的渠道买家，本聚合登记一条 {@link BindingSource#AUTO_SHADOW}
 * 记录并让该笔订单落在 {@code customerId = 0}（未知客户）维度，而不是拒绝建单—— 拒绝会让新买家的第一单在渠道侧表现为「店铺没收到订单」，
 * 是比「客户未识别」严重得多的故障。影子记录随后出现在「未绑定清单」里，由运营或规则补绑。
 *
 * <p><b>事件豁免（E-5.4）</b>：本聚合的绑定/解绑/刷新（{@code bindTo/unbind/recordOrder/refreshNick/updateRemark}）
 * 为<b>内部状态迁移</b>，写路径不配 {@code publishFrom}、不发 DomainEvent。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("bp_channel_buyer")
public class ChannelBuyer extends TenantAggregateRoot<Long> {

  /** 未绑定（影子）状态下 {@code customerId} 的取值。用 0 而不是 null：列 NOT NULL DEFAULT 0，且 0 不是合法客户ID。 */
  public static final long UNBOUND_CUSTOMER_ID = 0L;

  private String channelCode;
  private String channelBuyerId;
  private String channelBuyerNick;
  private Long customerId;
  private String customerName;
  private String bindingSource;
  private Integer orderCount;
  private Instant firstSeenAt;
  private Instant lastOrderAt;
  private String remark;
  private Instant createdAt;
  private Instant updatedAt;

  @Version private Long version;

  private ChannelBuyer(
      long id, Long tenantId, String channelCode, String channelBuyerId, String channelBuyerNick) {
    setId(id);
    setTenantId(tenantId);
    this.channelCode = channelCode;
    this.channelBuyerId = requireBuyerId(channelBuyerId);
    this.channelBuyerNick = channelBuyerNick;
    this.customerId = UNBOUND_CUSTOMER_ID;
    this.bindingSource = BindingSource.AUTO_SHADOW.name();
    this.orderCount = 0;
    this.firstSeenAt = Instant.now();
    this.lastOrderAt = this.firstSeenAt;
    touch();
  }

  /**
   * 工厂：首次观测到某渠道买家（拉单时自动建影子映射）。
   *
   * @param channelBuyerId 渠道买家账号ID（映射键，必填）
   * @param channelBuyerNick 昵称快照（可为 {@code null}；仅供运营识别，不参与映射）
   */
  public static ChannelBuyer observe(
      long id, Long tenantId, String channelCode, String channelBuyerId, String channelBuyerNick) {
    return new ChannelBuyer(id, tenantId, channelCode, channelBuyerId, channelBuyerNick);
  }

  /**
   * 绑定到内部客户（人工或规则触发）。
   *
   * <p><b>允许改绑</b>：运营发现绑错（售后申诉、对账差异）是常态，这里刻意不做「一次绑定不可改」的强约束，而是覆盖绑定并把来源置回 {@link
   * BindingSource#MANUAL}——以便从 {@code customerName} 快照与备注中保留判断依据。 若追求严格审计，应另加绑定历史表，而不是把限制塞进本方法。
   *
   * @param customerId 内部客户ID，必须为正数（0 是「未绑定」保留值）
   * @param customerName 客户名快照（可为 {@code null}）
   * @throws IllegalArgumentException 客户ID 非法
   */
  public void bindTo(Long customerId, String customerName) {
    if (customerId == null || customerId <= UNBOUND_CUSTOMER_ID) {
      throw new IllegalArgumentException("内部客户ID必须为正数（0 表示未绑定，不能作为绑定目标）: " + customerId);
    }
    touch();
    this.customerId = customerId;
    this.customerName = customerName;
    this.bindingSource = BindingSource.MANUAL.name();
  }

  /**
   * 解绑：退回影子状态。
   *
   * <p>用于「绑错了」或「客户已合并/注销」：解绑后该渠道买家的后续订单重新落在 {@code customerId = 0}， 不会继续挂到错误客户下。
   */
  public void unbind() {
    touch();
    this.customerId = UNBOUND_CUSTOMER_ID;
    this.customerName = null;
    this.bindingSource = BindingSource.AUTO_SHADOW.name();
  }

  /** 记录一次拉单命中（累计笔数 + 最近时间），用于运营按活跃度排优先级绑定。 */
  public void recordOrder() {
    touch();
    this.orderCount = (this.orderCount == null ? 0 : this.orderCount) + 1;
    this.lastOrderAt = Instant.now();
  }

  /** 刷新昵称快照（渠道侧改名后调用；不改映射，因为键是买家ID）。 */
  public void refreshNick(String nick) {
    touch();
    this.channelBuyerNick = nick;
  }

  public void updateRemark(String remark) {
    touch();
    this.remark = remark;
  }

  /** 是否已绑定到真实内部客户。 */
  public boolean isBound() {
    return customerId != null && customerId > UNBOUND_CUSTOMER_ID;
  }

  /** 是否为待绑定的影子映射（运营清单要过滤的就是这种）。 */
  public boolean isShadow() {
    return !isBound();
  }

  private static String requireBuyerId(String channelBuyerId) {
    if (channelBuyerId == null || channelBuyerId.isBlank()) {
      // 映射键为空 = 无法区分「没传」与「传了空串」，这类脏数据会让唯一索引失效并把不同买家合并成一条。
      throw new IllegalArgumentException("渠道买家ID不能为空（它是映射键，空值会把不同买家合并成同一条记录）");
    }
    return channelBuyerId.trim();
  }

  private void touch() {
    this.updatedAt = Instant.now();
    if (this.createdAt == null) {
      this.createdAt = this.updatedAt;
    }
  }
}
