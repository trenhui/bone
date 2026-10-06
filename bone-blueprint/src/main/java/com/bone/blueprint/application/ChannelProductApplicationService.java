package com.bone.blueprint.application;

import com.bone.blueprint.application.port.out.ChannelExtensionPort;
import com.bone.blueprint.application.port.out.TenantPort;
import com.bone.blueprint.application.query.dto.ChannelProductDto;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import com.bone.blueprint.domain.extension.channel.ChannelListingResult;
import com.bone.blueprint.domain.extension.channel.ChannelProductContext;
import com.bone.blueprint.domain.model.channel.Channel;
import com.bone.blueprint.domain.model.channel.ChannelProduct;
import com.bone.blueprint.domain.model.channel.event.ChannelRoutedEvent;
import com.bone.blueprint.domain.model.channel.valueobject.ChannelCode;
import com.bone.blueprint.domain.model.channel.valueobject.ListingStatus;
import com.bone.blueprint.domain.repository.ChannelProductRepository;
import com.bone.blueprint.domain.repository.ChannelRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 渠道商品应用层门面 —— 多渠道商品上架 / 下架 / 库存同步。
 *
 * <p><b>编排职责边界</b>：本服务负责「状态机 + 落库 + 结果解释」， 与渠道的协议交互全部委托给 {@link
 * ChannelExtensionPort}（内部经扩展点路由到具体渠道实现）。 因此<strong>新增第五个渠道时本类零改动</strong>——这是扩展点机制在多渠道场景的核心收益。
 *
 * <p><b>状态先行原则</b>：先把实体置为中间态（LISTING / DELISTING）并落库， 再调用渠道。顺序反过来（先调渠道后落库）会在渠道成功但本地事务回滚时留下
 * 「渠道有商品、本地无记录」的幽灵商品，且无法自动发现。先落中间态则即使进程崩溃， 也能通过扫描 LISTING 超时记录发现并补偿。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelProductApplicationService {

  private final ChannelProductRepository channelProductRepository;
  private final ChannelRepository channelRepository;
  private final ChannelExtensionPort channelExtensionPort;
  private final ChannelBroadcastApplicationService channelBroadcastApplicationService;
  private final DomainEventPublisher domainEventPublisher;
  private final TenantPort tenantProvider;

  @Transactional(readOnly = true)
  public PageResult<ChannelProductDto> page(
      String channelCode, Long productId, String status, int page, int size) {
    long tenantId = tenantProvider.currentTenantId();
    ListingStatus listingStatus =
        status == null || status.isBlank() ? null : ListingStatus.valueOf(status);
    PageResult<ChannelProduct> result =
        channelProductRepository.findPage(
            tenantId, channelCode, productId, listingStatus, page, size);
    List<ChannelProductDto> records =
        result.getRecords() == null
            ? List.of()
            : result.getRecords().stream().map(ChannelProductDto::from).toList();
    return PageResult.of(records, result.getTotal(), result.getPage(), result.getSize());
  }

  /**
   * 商品上架到渠道。
   *
   * @return 上架后的渠道商品（成功为 ONLINE，渠道拒绝为 FAILED 且带失败原因）
   */
  @Transactional
  public ChannelProductDto publishProduct(
      String channelCode, Long productId, String productName, BigDecimal listingPrice) {
    long tenantId = tenantProvider.currentTenantId();
    Channel channel = requireEnabledChannel(channelCode);

    ChannelProduct entity =
        channelProductRepository.findOne(tenantId, channel.getChannelCode(), productId);
    boolean created = entity == null;
    if (created) {
      entity =
          ChannelProduct.create(
              DistributedIdGenerator.generateLongId(),
              tenantId,
              channel.getChannelCode(),
              productId,
              productName,
              listingPrice);
    } else {
      entity.updateProductName(productName);
    }
    if (!entity.canList()) {
      throw BlueprintErrors.of(
          BlueprintErrorCodes.CHANNEL_PRODUCT_STATUS_CONFLICT,
          channel.getChannelCode() + "/" + productId + " 当前状态为 " + entity.getListingStatus());
    }

    // ① 先落中间态（崩溃可发现），再调渠道（见类 javadoc「状态先行原则」）
    entity.markListing(listingPrice);
    if (created) {
      channelProductRepository.insert(entity);
    } else {
      channelProductRepository.update(entity);
    }

    // ② 经扩展点路由到渠道实现
    ChannelListingResult result =
        channelExtensionPort.publishProduct(
            ChannelProductContext.forListing(
                tenantId, channel.getChannelCode(), productId, productName, listingPrice));

    // ③ 按渠道结果推进状态机
    Instant now = Instant.now();
    if (result.success()) {
      entity.markOnline(result.channelProductId(), now);
    } else {
      entity.markFailed(result.errorCode() + " - " + result.message(), now);
      log.warn(
          "渠道上架被拒绝 | channel={} | product={} | code={} | msg={}",
          channel.getChannelCode(),
          productId,
          result.errorCode(),
          result.message());
    }
    channelProductRepository.update(entity);
    // ④ 渠道同步标记：跨聚合，交给提交后的独立事务投影（R9 一事务一聚合）
    domainEventPublisher.publish(
        new ChannelRoutedEvent(tenantId, channel.getChannelCode(), "LIST_PRODUCT", now));
    return ChannelProductDto.from(entity);
  }

  /** 商品从渠道下架。 */
  @Transactional
  public ChannelProductDto delistProduct(String channelCode, Long productId) {
    long tenantId = tenantProvider.currentTenantId();
    String code = normalizeCode(channelCode);
    ChannelProduct entity = requireChannelProduct(tenantId, code, productId);
    if (!entity.canDelist()) {
      throw BlueprintErrors.of(
          BlueprintErrorCodes.CHANNEL_PRODUCT_STATUS_CONFLICT,
          code + "/" + productId + " 当前状态为 " + entity.getListingStatus() + "，仅 ONLINE 可下架");
    }
    entity.markDelisting();
    channelProductRepository.update(entity);

    ChannelListingResult result =
        channelExtensionPort.delistProduct(
            ChannelProductContext.forExisting(
                tenantId,
                code,
                productId,
                entity.getProductName(),
                entity.getChannelProductId(),
                entity.getListingStock()));

    Instant now = Instant.now();
    if (result.success()) {
      entity.markOffline(now);
    } else {
      entity.markFailed(result.errorCode() + " - " + result.message(), now);
    }
    channelProductRepository.update(entity);
    return ChannelProductDto.from(entity);
  }

  /**
   * 为指定商品的<strong>全部已上架渠道</strong>入队库存广播任务（异步投递）。
   *
   * <p><b>为何是广播而不是单渠道</b>：多渠道共享实物库存，只同步一个渠道会在其他渠道留下过期库存 → 超卖。
   *
   * <p><b>为何不再在本方法里同步调渠道</b>：原实现在本方法内串行调用 4 个渠道的 {@code syncInventory}， 带来三个问题： 库存事务被渠道 HTTP
   * 超时拖长（连接池被同步 IO 占用）；「A 渠道已改、B 超时」造成两侧不一致且无法自愈； 失败只留日志，无重试无死信。 现在改为「同事务入队 → 中继异步投递 → 退避重试 →
   * 死信可人工重试」。
   *
   * <p><b>返回形态保持不变</b>（每个 ONLINE 渠道一条），调用方（Controller、前端、自动化用例）按渠道数断言的逻辑不受影响；
   * 但要意识到返回值语义已从「已同步」变为「<strong>已入队</strong>」。
   *
   * @return 已入队广播的渠道商品列表
   */
  @Transactional
  public List<ChannelProduct> syncInventoryToAllChannels(Long productId, int stock) {
    return channelBroadcastApplicationService.enqueueForProduct(productId, stock);
  }

  private Channel requireEnabledChannel(String channelCode) {
    long tenantId = tenantProvider.currentTenantId();
    String code = normalizeCode(channelCode);
    Channel channel = channelRepository.findByCode(tenantId, code);
    if (channel == null) {
      throw BlueprintErrors.of(BlueprintErrorCodes.CHANNEL_NOT_FOUND, channelCode);
    }
    if (!channel.isEnabled()) {
      throw BlueprintErrors.of(BlueprintErrorCodes.CHANNEL_DISABLED, channelCode);
    }
    return channel;
  }

  private ChannelProduct requireChannelProduct(long tenantId, String code, Long productId) {
    ChannelProduct entity = channelProductRepository.findOne(tenantId, code, productId);
    if (entity == null) {
      throw BlueprintErrors.of(
          BlueprintErrorCodes.CHANNEL_PRODUCT_NOT_FOUND, code + "/" + productId);
    }
    return entity;
  }

  private static String normalizeCode(String channelCode) {
    ChannelCode code = ChannelCode.parseOrNull(channelCode);
    if (code == null) {
      throw BlueprintErrors.of(BlueprintErrorCodes.CHANNEL_CODE_INVALID, channelCode);
    }
    return code.name();
  }
}
