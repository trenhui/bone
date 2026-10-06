package com.bone.blueprint.domain.repository;

import com.bone.blueprint.domain.model.channel.ChannelProduct;
import com.bone.blueprint.domain.model.channel.valueobject.ListingStatus;
import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 渠道商品仓储（SDK 代理实现）。 */
public interface ChannelProductRepository extends Repository<ChannelProduct, Long> {

  default ChannelProduct findOne(Long tenantId, String channelCode, Long productId) {
    Criteria<ChannelProduct> criteria =
        Criteria.<ChannelProduct>create()
            .eq(ChannelProduct::getTenantId, tenantId)
            .eq(ChannelProduct::getChannelCode, channelCode)
            .eq(ChannelProduct::getProductId, productId);
    return findOneByCriteria(criteria);
  }

  default PageResult<ChannelProduct> findPage(
      Long tenantId, String channelCode, Long productId, ListingStatus status, int page, int size) {
    Criteria<ChannelProduct> criteria =
        Criteria.<ChannelProduct>create()
            .eq(ChannelProduct::getTenantId, tenantId)
            .eq(
                channelCode != null && !channelCode.isBlank(),
                ChannelProduct::getChannelCode,
                channelCode)
            .eq(productId != null, ChannelProduct::getProductId, productId)
            .eq(status != null, ChannelProduct::getListingStatus, status)
            .orderByDesc(ChannelProduct::getUpdatedAt)
            .page(page, size);
    return pageByCriteria(criteria);
  }

  /** 某商品已上架的全部渠道（库存同步时按此清单广播）。 */
  default List<ChannelProduct> findOnlineByProduct(Long tenantId, Long productId) {
    Criteria<ChannelProduct> criteria =
        Criteria.<ChannelProduct>create()
            .eq(ChannelProduct::getTenantId, tenantId)
            .eq(ChannelProduct::getProductId, productId)
            .eq(ChannelProduct::getListingStatus, ListingStatus.ONLINE);
    return findByCriteria(criteria);
  }
}
