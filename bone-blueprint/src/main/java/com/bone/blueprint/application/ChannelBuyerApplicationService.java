package com.bone.blueprint.application;

import com.bone.blueprint.application.port.out.TenantPort;
import com.bone.blueprint.application.query.dto.ChannelBuyerDto;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import com.bone.blueprint.domain.extension.channel.ChannelOrderContext;
import com.bone.blueprint.domain.model.channel.valueobject.ChannelCode;
import com.bone.blueprint.domain.model.channelbuyer.ChannelBuyer;
import com.bone.blueprint.domain.model.channelbuyer.event.ChannelBuyerObservedEvent;
import com.bone.blueprint.domain.repository.ChannelBuyerRepository;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「渠道买家 ↔ 内部客户」映射应用服务。
 *
 * <p><b>它替代了什么</b>：此前渠道订单的内部客户维度是 {@code buyerNick.hashCode() & 0x7fffffff + 1000000}
 * 算出来的。昵称可被买家随时修改， 改名即换客户——同一人的订单被拆到不同客户下，客户维度的统计、会员权益、售后与对账全部失真； 且哈希值不是真实客户ID， 无法与其它域 join。
 * 现在映射是一条<strong>可运营的显式数据</strong>。
 *
 * <p><b>拉单时如何解析客户（{@link #resolveCustomerId}）</b>：
 *
 * <ul>
 *   <li>命中映射且已绑定 → 订单落真实客户ID；
 *   <li>命中影子映射（未绑定）→ 订单落 {@code customerId = 0}（未知客户），<strong>不拒绝建单</strong>：拒绝会让渠道侧看到「店铺没收到订单」，
 *       是比「客户未识别」严重得多的故障；
 *   <li>渠道未传买家ID → 无映射键可用，落 {@code 0} 并告警，绝不退化成昵称哈希（那等于把 bug 换了个实现）。
 * </ul>
 *
 * <p><b>写映射为何在 {@link Propagation#REQUIRES_NEW} 里</b>：拉单主事务只读映射（不违反 R9 一事务一聚合）， 写入由 AFTER_COMMIT
 * 处理器调本方法 完成。AFTER_COMMIT 阶段原事务已提交但连接仍绑定， 此处若用默认 {@code REQUIRED} 会加入已提交事务导致写入被静默丢弃。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelBuyerApplicationService {

  /** 待绑定清单默认条数：运营一次能处理的量级。 */
  private static final int DEFAULT_SHADOW_LIMIT = 50;

  private final ChannelBuyerRepository channelBuyerRepository;
  private final TenantPort tenantProvider;

  // ==================== 拉单主链路（只读） ====================

  /**
   * 解析渠道订单的内部客户ID（<strong>只读</strong>，不写库）。
   *
   * @return 内部客户ID；无法识别时返回 {@link ChannelBuyer#UNBOUND_CUSTOMER_ID}（0）
   */
  @Transactional(readOnly = true)
  public long resolveCustomerId(ChannelOrderContext context, String normalizedChannelCode) {
    long tenantId = tenantProvider.currentTenantId();
    String buyerId = context == null ? null : context.buyerId();
    if (buyerId == null || buyerId.isBlank()) {
      // 没有渠道买家ID就没有映射键。此处退化为「未知客户」并告警，而不是用昵称哈希：
      // 哈希会把改名买家拆成两个客户，那正是本映射表要消灭的问题。
      log.warn(
          "[{}] 渠道订单未携带买家ID，客户维度落「未知客户」| orderNo={}",
          normalizedChannelCode,
          context == null ? null : context.channelOrderNo());
      return ChannelBuyer.UNBOUND_CUSTOMER_ID;
    }
    ChannelBuyer buyer =
        channelBuyerRepository.findByChannelBuyer(tenantId, normalizedChannelCode, buyerId.trim());
    if (buyer == null) {
      return ChannelBuyer.UNBOUND_CUSTOMER_ID;
    }
    return buyer.getCustomerId() == null ? ChannelBuyer.UNBOUND_CUSTOMER_ID : buyer.getCustomerId();
  }

  // ==================== 写侧（独立事务，供 AFTER_COMMIT 处理器调用） ====================

  /**
   * 登记一次渠道买家观测：不存在则建影子映射，存在则累计笔数。
   *
   * <p>幂等：唯一键 {@code (tenant, channel_code, channel_buyer_id, deleted)} 保证并发拉单最多留下一条映射。
   *
   * <p><b>吞掉重复键异常</b>：并发拉单时两个线程可能同时判定「不存在」并各自 insert， 后到者撞唯一键。 这是预期内的竞争， 不是业务失败， 记 warn 后返回即可 ——
   * 让它冒泡会把订单的 AFTER_COMMIT 链条一起炸掉。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void observeChannelBuyer(ChannelBuyerObservedEvent event) {
    String channelCode = normalize(event.channelCode());
    String buyerId = event.channelBuyerId() == null ? "" : event.channelBuyerId().trim();
    if (buyerId.isEmpty()) {
      log.warn("[{}] 渠道买家ID为空，跳过映射登记（疑似渠道报文缺字段）", channelCode);
      return;
    }
    try {
      ChannelBuyer existing =
          channelBuyerRepository.findByChannelBuyer(event.tenantId(), channelCode, buyerId);
      if (existing == null) {
        ChannelBuyer buyer =
            ChannelBuyer.observe(
                DistributedIdGenerator.generateLongId(),
                event.tenantId(),
                channelCode,
                buyerId,
                event.channelBuyerNick());
        channelBuyerRepository.insert(buyer);
        log.info(
            "[{}] 新建渠道买家影子映射（待人工绑定）| buyerId={} | nick={} | orderId={}",
            channelCode,
            buyerId,
            event.channelBuyerNick(),
            event.orderId());
        return;
      }
      existing.recordOrder();
      if (event.channelBuyerNick() != null && !event.channelBuyerNick().isBlank()) {
        existing.refreshNick(event.channelBuyerNick());
      }
      channelBuyerRepository.update(existing);
    } catch (org.springframework.dao.DuplicateKeyException dup) {
      log.warn("[{}] 渠道买家映射并发重复插入，按幂等忽略 | buyerId={}", channelCode, buyerId);
    } catch (RuntimeException ex) {
      // AFTER_COMMIT 里抛异常会中断同一事务后注册的其他处理器，映射缺失只影响客户维度归属，放大故障面不划算。
      log.error(
          "[{}] 渠道买家映射登记失败（不影响订单）| buyerId={} | orderId={}",
          channelCode,
          buyerId,
          event.orderId(),
          ex);
    }
  }

  // ==================== 运营侧 ====================

  @Transactional(readOnly = true)
  public PageResult<ChannelBuyerDto> page(String channelCode, Boolean bound, int page, int size) {
    long tenantId = tenantProvider.currentTenantId();
    PageResult<ChannelBuyer> result =
        channelBuyerRepository.findPage(tenantId, normalizeOrNull(channelCode), bound, page, size);
    List<ChannelBuyerDto> records =
        result.getRecords() == null
            ? List.of()
            : result.getRecords().stream().map(ChannelBuyerDto::from).toList();
    return PageResult.of(records, result.getTotal(), result.getPage(), result.getSize());
  }

  /** 待绑定影子清单（按最近拉单时间倒序）：运营优先绑定高频渠道买家。 */
  @Transactional(readOnly = true)
  public List<ChannelBuyerDto> shadowCandidates(String channelCode, int limit) {
    long tenantId = tenantProvider.currentTenantId();
    int capped = limit <= 0 ? DEFAULT_SHADOW_LIMIT : Math.min(limit, 200);
    return channelBuyerRepository
        .findShadowCandidates(tenantId, normalizeOrNull(channelCode), capped)
        .stream()
        .map(ChannelBuyerDto::from)
        .toList();
  }

  /**
   * 绑定到内部客户（仅未绑定时可绑）。
   *
   * @throws com.bone.core.exception.BizException 映射不存在（404）或已被绑定（409）
   */
  @Transactional
  public ChannelBuyerDto bind(
      String channelCode, String channelBuyerId, Long customerId, String customerName) {
    ChannelBuyer buyer = requireBuyer(channelCode, channelBuyerId);
    if (buyer.isBound()) {
      // 刻意不在这里静默改绑：误覆盖会把历史订单挂到错误客户上。要改绑请显式调 rebind（需填原因）。
      throw BlueprintErrors.of(
          BlueprintErrorCodes.CHANNEL_BUYER_ALREADY_BOUND,
          channelCode + "/" + channelBuyerId + " 已绑定客户 " + buyer.getCustomerId());
    }
    buyer.bindTo(customerId, customerName);
    channelBuyerRepository.update(buyer);
    log.info(
        "[{}] 渠道买家已绑定内部客户 | buyerId={} | customerId={}", channelCode, channelBuyerId, customerId);
    return ChannelBuyerDto.from(buyer);
  }

  /**
   * 显式改绑（已绑定 → 另一客户），必须给原因。
   *
   * <p>与 {@link #bind} 分开而不是加个 force 参数：改绑是<strong>要留痕的运营动作</strong>（售后申诉、对账差异），
   * 混在同一个方法里会被日常绑定调用顺带触发。
   */
  @Transactional
  public ChannelBuyerDto rebind(
      String channelCode,
      String channelBuyerId,
      Long customerId,
      String customerName,
      String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("改绑必须填写原因（改绑会影响历史订单的客户归属，需留痕）");
    }
    ChannelBuyer buyer = requireBuyer(channelCode, channelBuyerId);
    Long previous = buyer.getCustomerId();
    buyer.bindTo(customerId, customerName);
    buyer.updateRemark("改绑：" + reason.trim());
    channelBuyerRepository.update(buyer);
    log.warn(
        "[{}] 渠道买家改绑内部客户 | buyerId={} | from={} | to={} | reason={}",
        channelCode,
        channelBuyerId,
        previous,
        customerId,
        reason);
    return ChannelBuyerDto.from(buyer);
  }

  /** 解绑：退回影子状态，后续订单重新落「未知客户」，不会继续挂到错误客户下。 */
  @Transactional
  public ChannelBuyerDto unbind(String channelCode, String channelBuyerId) {
    ChannelBuyer buyer = requireBuyer(channelCode, channelBuyerId);
    if (!buyer.isBound()) {
      return ChannelBuyerDto.from(buyer);
    }
    Long previous = buyer.getCustomerId();
    buyer.unbind();
    channelBuyerRepository.update(buyer);
    log.warn(
        "[{}] 渠道买家已解绑 | buyerId={} | previousCustomerId={}", channelCode, channelBuyerId, previous);
    return ChannelBuyerDto.from(buyer);
  }

  private ChannelBuyer requireBuyer(String channelCode, String channelBuyerId) {
    long tenantId = tenantProvider.currentTenantId();
    if (channelBuyerId == null || channelBuyerId.isBlank()) {
      throw new IllegalArgumentException("渠道买家ID不能为空（它是映射键）");
    }
    ChannelBuyer buyer =
        channelBuyerRepository.findByChannelBuyer(
            tenantId, normalize(channelCode), channelBuyerId.trim());
    if (buyer == null) {
      throw BlueprintErrors.of(
          BlueprintErrorCodes.CHANNEL_BUYER_NOT_FOUND, channelCode + "/" + channelBuyerId);
    }
    return buyer;
  }

  private static String normalize(String channelCode) {
    ChannelCode code = ChannelCode.parseOrNull(channelCode);
    if (code == null) {
      throw BlueprintErrors.of(BlueprintErrorCodes.CHANNEL_CODE_INVALID, channelCode);
    }
    return code.name();
  }

  private static String normalizeOrNull(String channelCode) {
    if (channelCode == null || channelCode.isBlank()) {
      return null;
    }
    return normalize(channelCode);
  }
}
