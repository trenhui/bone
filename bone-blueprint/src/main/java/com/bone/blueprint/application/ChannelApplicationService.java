package com.bone.blueprint.application;

import com.bone.blueprint.application.port.out.TenantPort;
import com.bone.blueprint.application.query.dto.ChannelDto;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import com.bone.blueprint.domain.model.channel.Channel;
import com.bone.blueprint.domain.model.channel.valueobject.ChannelCode;
import com.bone.blueprint.domain.repository.ChannelRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 渠道应用层门面 —— 渠道的注册、启停与查询。
 *
 * <p><b>渠道的「能力」与「配置」分离</b>：能接哪些渠道由扩展实现（{@code @Extension}）决定， 是否开通由本服务维护的 {@code bp_channel}
 * 决定。因此本服务不需要理解任何渠道协议—— 它只管租户侧的开关与凭证，协议差异全部在扩展实现里。
 *
 * <p><b>事件豁免（E-5.4）</b>：本服务全部写路径均为 {@code Channel} 聚合的内部状态迁移（启停/路由标记/凭证维护）， 无跨聚合订阅方、无审计必须事实，故声明
 * {@code @NoDomainEvent}（聚合方法豁免理由见 {@code Channel} 类注释）。
 */
@NoDomainEvent
@Service
@RequiredArgsConstructor
public class ChannelApplicationService {

  private final ChannelRepository channelRepository;
  private final TenantPort tenantProvider;

  @Transactional(readOnly = true)
  public PageResult<ChannelDto> page(String channelCode, int page, int size) {
    long tenantId = tenantProvider.currentTenantId();
    PageResult<Channel> result = channelRepository.findPage(tenantId, channelCode, page, size);
    List<ChannelDto> records =
        result.getRecords() == null
            ? List.of()
            : result.getRecords().stream().map(ChannelDto::from).toList();
    return PageResult.of(records, result.getTotal(), result.getPage(), result.getSize());
  }

  @Transactional(readOnly = true)
  public ChannelDto detail(String channelCode) {
    return ChannelDto.from(requireEnabledOrAny(channelCode));
  }

  /** 启用渠道。 */
  @Transactional
  public ChannelDto enable(String channelCode) {
    Channel channel = requireChannel(channelCode);
    channel.enable();
    channelRepository.update(channel);
    return ChannelDto.from(channel);
  }

  /** 停用渠道（同时关闭订单同步，避免停用后定时任务仍在拉单）。 */
  @Transactional
  public ChannelDto disable(String channelCode) {
    Channel channel = requireChannel(channelCode);
    channel.disable();
    channelRepository.update(channel);
    return ChannelDto.from(channel);
  }

  /** 设置订单自动同步开关。 */
  @Transactional
  public ChannelDto setOrderSync(String channelCode, boolean sync) {
    Channel channel = requireChannel(channelCode);
    if (sync) {
      // 先校验启用状态：domain 的 enableOrderSync 会抛 DomainException，这里转成业务码。
      if (!channel.isEnabled()) {
        throw BlueprintErrors.of(BlueprintErrorCodes.CHANNEL_DISABLED, channelCode);
      }
      channel.enableOrderSync();
    } else {
      channel.disableOrderSync();
    }
    channelRepository.update(channel);
    return ChannelDto.from(channel);
  }

  /** 更新渠道备注与接入地址。 */
  @Transactional
  public ChannelDto update(String channelCode, String apiEndpoint, String remark) {
    Channel channel = requireChannel(channelCode);
    if (apiEndpoint != null && !apiEndpoint.isBlank()) {
      channel.updateEndpoint(apiEndpoint, null);
    }
    if (remark != null) {
      channel.updateRemark(remark);
    }
    channelRepository.update(channel);
    return ChannelDto.from(channel);
  }

  /**
   * 注册渠道（通常由种子数据预置；接口开放是为了支持租户自助开通）。
   *
   * @throws com.bone.core.exception.BizException 渠道码非法（400）或渠道已存在（409）
   */
  @Transactional
  public ChannelDto register(String channelCode, String apiEndpoint, String appKey) {
    long tenantId = tenantProvider.currentTenantId();
    if (!ChannelCode.isSupported(channelCode)) {
      throw BlueprintErrors.of(BlueprintErrorCodes.CHANNEL_CODE_INVALID, channelCode);
    }
    String normalized = ChannelCode.parseOrNull(channelCode).name();
    Channel existing = channelRepository.findByCode(tenantId, normalized);
    if (existing != null) {
      // 已存在：保证幂等（重跑种子不该失败），直接返回现有记录。
      return ChannelDto.from(existing);
    }
    Channel channel =
        Channel.register(
            DistributedIdGenerator.generateLongId(), tenantId, normalized, apiEndpoint, appKey);
    channelRepository.insert(channel);
    return ChannelDto.from(channel);
  }

  /** 查询渠道（含已停用）；不存在抛 404。 */
  private Channel requireChannel(String channelCode) {
    long tenantId = tenantProvider.currentTenantId();
    Channel channel = channelRepository.findByCode(tenantId, normalize(channelCode));
    if (channel == null) {
      throw BlueprintErrors.of(BlueprintErrorCodes.CHANNEL_NOT_FOUND, channelCode);
    }
    return channel;
  }

  /** 详情查询允许返回停用渠道（运营需要看到停用渠道才能重新启用）。 */
  private Channel requireEnabledOrAny(String channelCode) {
    return requireChannel(channelCode);
  }

  private static String normalize(String channelCode) {
    ChannelCode code = ChannelCode.parseOrNull(channelCode);
    if (code == null) {
      throw BlueprintErrors.of(BlueprintErrorCodes.CHANNEL_CODE_INVALID, channelCode);
    }
    return code.name();
  }
}
