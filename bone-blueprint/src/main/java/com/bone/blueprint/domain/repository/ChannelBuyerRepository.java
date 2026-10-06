package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.model.channelbuyer.ChannelBuyer;
import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/**
 * 渠道买家映射仓储（SDK 代理实现）。
 *
 * <p>仅提供租户内的查询方法：买家映射含渠道侧身份信息，跨租户可见即为数据泄漏。
 */
public interface ChannelBuyerRepository extends Repository<ChannelBuyer, Long> {

  /**
   * 按映射键精确查询：{@code (tenant, channelCode, channelBuyerId)}。
   *
   * <p>这是拉单主链路唯一用到的查询——命中即订单能落到真实客户维度， 未命中则订单落 {@code customerId = 0} 并异步登记影子映射。
   */
  default ChannelBuyer findByChannelBuyer(
      Long tenantId, String channelCode, String channelBuyerId) {
    if (channelBuyerId == null || channelBuyerId.isBlank()) {
      // 映射键为空时不能退化成「查第一条」：那会把订单挂到随机客户下。
      return null;
    }
    Criteria<ChannelBuyer> criteria =
        Criteria.<ChannelBuyer>create()
            .eq(ChannelBuyer::getTenantId, tenantId)
            .eq(ChannelBuyer::getChannelCode, channelCode)
            .eq(ChannelBuyer::getChannelBuyerId, channelBuyerId.trim());
    return findOneByCriteria(criteria);
  }

  /**
   * 分页查询。
   *
   * @param bound {@code TRUE} 只看已绑定、{@code FALSE} 只看待绑定影子、{@code null} 全量
   */
  default PageResult<ChannelBuyer> findPage(
      Long tenantId, String channelCode, Boolean bound, int page, int size) {
    Criteria<ChannelBuyer> criteria =
        Criteria.<ChannelBuyer>create()
            .eq(ChannelBuyer::getTenantId, tenantId)
            .eq(
                channelCode != null && !channelCode.isBlank(),
                ChannelBuyer::getChannelCode,
                channelCode);
    // 「已绑定」是 customer_id > 0 的开区间，必须用 gt；写成 eq(1) 只会命中客户ID 恰好为 1 的那一条。
    // 「未绑定」是 customer_id = 0 的点（列 NOT NULL DEFAULT 0），用 eq 即可。
    if (bound != null) {
      criteria =
          Boolean.TRUE.equals(bound)
              ? criteria.gt(ChannelBuyer::getCustomerId, ChannelBuyer.UNBOUND_CUSTOMER_ID)
              : criteria.eq(ChannelBuyer::getCustomerId, ChannelBuyer.UNBOUND_CUSTOMER_ID);
    }
    return pageByCriteria(criteria.orderByDesc(ChannelBuyer::getLastOrderAt).page(page, size));
  }

  /**
   * 待绑定影子清单（按最近拉单时间倒序），供运营优先处理高频渠道买家。
   *
   * <p>不提供聚合统计（如「未绑定总数」）：SDK 的 {@code Criteria} 不支持 {@code groupBy}， 用代码遍历聚合在生产数据量下不可接受。
   */
  default List<ChannelBuyer> findShadowCandidates(Long tenantId, String channelCode, int limit) {
    Criteria<ChannelBuyer> criteria =
        Criteria.<ChannelBuyer>create()
            .eq(ChannelBuyer::getTenantId, tenantId)
            .eq(
                channelCode != null && !channelCode.isBlank(),
                ChannelBuyer::getChannelCode,
                channelCode)
            .eq(ChannelBuyer::getCustomerId, ChannelBuyer.UNBOUND_CUSTOMER_ID)
            .orderByDesc(ChannelBuyer::getLastOrderAt)
            .page(1, limit);
    List<ChannelBuyer> rows = findByCriteria(criteria);
    return rows == null ? List.of() : rows;
  }
}
