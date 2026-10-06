package com.bone.blueprint.domain.model.channel;

import com.bone.blueprint.domain.model.channel.valueobject.ChannelCode;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 销售渠道聚合根。
 *
 * <p><b>为何渠道要落库，而不是只靠扩展实现的 {@code @Extension} 注解</b>：注解回答「系统<em>能</em>接哪些渠道」，
 * 是<strong>能力</strong>；本表回答「这个租户<em>开通了</em>哪些渠道、是否启用、拉单开关、渠道侧凭证」，
 * 是<strong>配置</strong>。二者必须分离：同一套代码服务多租户，A 租户只开淘宝、B 租户全开。 若把状态编码进注解，改一次启用状态就要重新发版。
 *
 * <p><b>扩展实现码 {@code extImplCode}</b>：由扩展点路由结果回填，仅用于可观测（排障时确认「这次请求
 * 究竟命中了哪个实现」）。它<strong>不参与路由</strong>——路由由 {@code BizContext} 的 {@code channel} 维度决定。
 * 写成可反查的冗余字段，是为了避免「配置说走 A 实现、实际走了 B 实现」这类无法自证的偏差。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("bp_channel")
public class Channel extends TenantAggregateRoot<Long> {

  private String channelCode;
  private String channelName;
  private String extImplCode;
  private String apiEndpoint;
  private String appKey;
  private Boolean enabled;
  private Boolean orderSyncEnabled;
  private Instant lastSyncAt;
  private String remark;
  private Instant createdAt;
  private Instant updatedAt;

  @Version private Long version;

  private Channel(
      long id,
      Long tenantId,
      String channelCode,
      String channelName,
      String apiEndpoint,
      String appKey) {
    setId(id);
    setTenantId(tenantId);
    this.channelCode = channelCode;
    this.channelName = channelName;
    this.apiEndpoint = apiEndpoint;
    this.appKey = appKey;
    this.enabled = Boolean.TRUE;
    this.orderSyncEnabled = Boolean.FALSE;
    touch();
  }

  /**
   * 工厂：注册渠道。
   *
   * @param channelCode 必须是 {@link ChannelCode} 可解析的码，否则抛 {@link IllegalArgumentException}
   */
  public static Channel register(
      long id, Long tenantId, String channelCode, String apiEndpoint, String appKey) {
    ChannelCode code = ChannelCode.parseOrNull(channelCode);
    if (code == null) {
      throw new IllegalArgumentException("不支持的销售渠道码: " + channelCode);
    }
    return new Channel(id, tenantId, code.name(), code.displayName(), apiEndpoint, appKey);
  }

  /** 启用渠道。 */
  public void enable() {

    touch();
    this.enabled = Boolean.TRUE;
  }

  /** 停用渠道：已停用渠道不参与拉单、上架、发货。 */
  public void disable() {

    touch();
    this.enabled = Boolean.FALSE;
    this.orderSyncEnabled = Boolean.FALSE;
  }

  public boolean isEnabled() {
    return Boolean.TRUE.equals(enabled);
  }

  /**
   * 开启订单自动同步；渠道停用状态下拒绝开启。
   *
   * <p><b>为何拆成 enable/disable 两个具名行为方法，而不是 {@code setOrderSync(boolean)}</b>：
   * 同步开关不是「赋个值」，而是有前置约束的业务动作 （停用渠道不得开启）。写成 setter 会把约束推给调用方，
   * 调用方一旦漏判就会出现「停用渠道仍在拉单」；写成具名方法则约束内聚在聚合内，无法绕过。 同时满足 R2 反贫血门禁（应用层不得调用 {@code set*}）。
   *
   * @throws DomainException 渠道已停用
   */
  public void enableOrderSync() {
    touch();
    if (!isEnabled()) {
      throw new DomainException("渠道已停用，不能开启订单同步: " + channelCode);
    }
    this.orderSyncEnabled = Boolean.TRUE;
  }

  /** 关闭订单自动同步（随时可关，无前置约束）。 */
  public void disableOrderSync() {
    touch();
    this.orderSyncEnabled = Boolean.FALSE;
  }

  /** 记录扩展点路由命中的实现 code（可观测字段，不参与路由）。 */
  public void markRoutedImpl(String implCode) {

    touch();
    this.extImplCode = implCode;
  }

  /** 标记一次拉单完成时间。 */
  public void markSynced(Instant at) {

    touch();
    this.lastSyncAt = at;
  }

  public void updateRemark(String remark) {

    touch();
    this.remark = remark;
  }

  public void updateEndpoint(String endpoint, String appKey) {

    touch();
    this.apiEndpoint = endpoint;
    this.appKey = appKey;
  }

  /** 刷新修改时间；创建时间只在构造时赋值一次，之后不再变动。 */
  private void touch() {
    this.updatedAt = Instant.now();
    if (this.createdAt == null) {
      this.createdAt = this.updatedAt;
    }
  }
}
