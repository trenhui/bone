package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.model.channel.Channel;
import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;

/**
 * 渠道仓储（SDK 代理实现）。
 *
 * <p>仅提供租户内的默认方法，避免跨租户读：渠道是租户级配置，跨租户可见即为配置泄漏。
 */
public interface ChannelRepository extends Repository<Channel, Long> {

  /** 按渠道码精确查询（租户过滤由 SDK 自动注入）。 */
  default Channel findByCode(Long tenantId, String channelCode) {
    Criteria<Channel> criteria =
        Criteria.<Channel>create()
            .eq(Channel::getTenantId, tenantId)
            .eq(Channel::getChannelCode, channelCode);
    return findOneByCriteria(criteria);
  }

  /** 租户下启用中的渠道列表。 */
  default java.util.List<Channel> findEnabled(Long tenantId) {
    Criteria<Channel> criteria =
        Criteria.<Channel>create()
            .eq(Channel::getTenantId, tenantId)
            .eq(Channel::getEnabled, Boolean.TRUE)
            .orderByAsc(Channel::getChannelCode);
    return findByCriteria(criteria);
  }

  default PageResult<Channel> findPage(Long tenantId, String channelCode, int page, int size) {
    Criteria<Channel> criteria =
        Criteria.<Channel>create()
            .eq(Channel::getTenantId, tenantId)
            .eq(channelCode != null && !channelCode.isBlank(), Channel::getChannelCode, channelCode)
            .orderByAsc(Channel::getChannelCode)
            .page(page, size);
    return pageByCriteria(criteria);
  }
}
