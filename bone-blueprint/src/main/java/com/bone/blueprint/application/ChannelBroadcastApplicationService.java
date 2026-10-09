package com.bone.blueprint.application;

import com.bone.blueprint.application.port.out.ChannelBroadcastOutboxPort;
import com.bone.blueprint.application.port.out.ChannelBroadcastRelayPort;
import com.bone.blueprint.application.port.out.TenantPort;
import com.bone.blueprint.application.query.port.ChannelBroadcastQueryPort;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import com.bone.blueprint.domain.model.channel.ChannelProduct;
import com.bone.blueprint.domain.repository.ChannelProductRepository;
import com.bone.core.model.PageResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 渠道库存广播应用服务 —— 负责「决定广播给谁」与「给人一个干预入口」。
 *
 * <p><b>职责边界</b>：本服务<strong>不</strong>投递。投递由 {@link ChannelBroadcastRelayPort} 中继异步完成（CAS 抢占 + 重试 +
 * 死信）， 这样库存事务不会被渠道 HTTP 拖住。本服务只做三件事：
 *
 * <ol>
 *   <li>{@link #enqueueForProduct}：按「商品在该渠道 ONLINE」筛选出应广播的渠道，逐个入队（<b>必须与库存变更同事务</b>）；
 *   <li>{@link #page}/{@link #retry}：给运营可见的失败清单与人工重试入口；
 *   <li>{@link #relayNow}：管理端「立即推送一次」，用于运营不想等下一个调度周期。
 * </ol>
 *
 * <p><b>为何「哪些渠道该收到」由本服务判定而不是中继</b>：这是业务规则（商品在渠道 A 上架成功、在渠道 B 下架， 就只该广播 A）， 属于领域知识；
 * 而「怎么投、失败怎么退避」是技术策略。让中继去判断业务规则，会把业务语义埋进技术组件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelBroadcastApplicationService {

  private final ChannelProductRepository channelProductRepository;
  private final ChannelBroadcastOutboxPort broadcastOutboxPort;
  private final ChannelBroadcastQueryPort broadcastQueryPort;
  private final ChannelBroadcastRelayPort broadcastRelayPort;
  private final TenantPort tenantProvider;

  /**
   * 为一个商品向其<strong>全部 ONLINE 渠道</strong>入队库存广播任务。
   *
   * <p><b>为什么必须广播而不是单渠道</b>：多渠道共享同一实物库存，只同步一个渠道就会在其他渠道留下过期库存 → 超卖。
   *
   * <p><b>为什么要 MANDATORY 同事务</b>：库存已提交而广播任务没入队，渠道库存就永久停在旧值。 反过来（任务入队但库存回滚）只是多同步一次， 危害小得多 ——
   * 所以宁可比库存「多同步」，不可「漏同步」。
   *
   * @param productId 内部商品ID
   * @param targetStock 目标库存（覆盖值）
   * @return 入队任务对应的渠道商品（保持原接口返回形态，前端/E2E 可按渠道数断言）
   */
  @Transactional
  public List<ChannelProduct> enqueueForProduct(Long productId, int targetStock) {
    long tenantId = tenantProvider.currentTenantId();
    List<ChannelProduct> online = channelProductRepository.findOnlineByProduct(tenantId, productId);
    if (online == null || online.isEmpty()) {
      log.info("库存广播入队跳过：商品在当前租户下没有 ONLINE 渠道 | productId={}", productId);
      return List.of();
    }
    int stock = Math.max(0, targetStock);
    for (ChannelProduct entity : online) {
      broadcastOutboxPort.appendStockBroadcast(
          tenantId,
          productId,
          entity.getChannelCode(),
          entity.getChannelProductId(),
          entity.getProductName(),
          stock);
    }
    log.info("库存广播已入队 {} 条（异步投递）| productId={} | targetStock={}", online.size(), productId, stock);
    return online;
  }

  /** 广播任务分页（status 为空则不过滤）。 */
  @Transactional(readOnly = true)
  public PageResult<ChannelBroadcastQueryPort.TaskView> page(String status, int page, int size) {
    long tenantId = tenantProvider.currentTenantId();
    return broadcastQueryPort.pageByStatus(tenantId, status, page, size);
  }

  /**
   * 人工重试一条广播任务（死信 FAILED / 被合并的旧任务）。
   *
   * <p><b>流程是「先回队列，再推进一轮」</b>：只调用中继是无效的——中继只抢 PENDING， 而死信是 FAILED， 直接推等于什么都没做。
   *
   * <p><b>为何不允许重试 SENT</b>：已成功的任务再投一次会让渠道收到重复同步，虽然值相同（幂等）， 但会掩盖「是否真的成功过」的判断， 并白耗渠道接口配额。
   */
  @Transactional
  public ChannelBroadcastQueryPort.TaskView retry(String taskId) {
    long tenantId = tenantProvider.currentTenantId();
    ChannelBroadcastQueryPort.TaskView task = broadcastQueryPort.findById(tenantId, taskId);
    if (task == null) {
      throw BlueprintErrors.of(BlueprintErrorCodes.CHANNEL_BROADCAST_NOT_FOUND, taskId);
    }
    if ("SENT".equals(task.status())) {
      throw BlueprintErrors.of(
          BlueprintErrorCodes.CHANNEL_BROADCAST_NOT_FOUND, taskId + "（已投递成功，无需重试）");
    }
    if (!broadcastOutboxPort.requeueFailed(tenantId, taskId)) {
      // 已在队列中（PENDING/PROCESSING）：重试无意义，返回现状让前端提示而不是报错。
      log.info("渠道库存广播重试跳过：任务已在队列中 | taskId={} | status={}", taskId, task.status());
      return task;
    }
    int sent = broadcastRelayPort.relayPending();
    log.info("运营触发渠道库存广播重试 | taskId={} | 原状态={} | 本轮推送={}", taskId, task.status(), sent);
    return broadcastQueryPort.findById(tenantId, taskId);
  }

  /** 立即推进一轮中继（管理端「立即推送」按钮），不等下一个调度周期。 */
  public int relayNow() {
    int sent = broadcastRelayPort.relayPending();
    log.info("运营手动推进渠道库存广播 | sent={}", sent);
    return sent;
  }
}
