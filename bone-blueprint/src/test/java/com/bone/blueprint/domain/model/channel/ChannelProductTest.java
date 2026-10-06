package com.bone.blueprint.domain.model.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.blueprint.domain.model.channel.valueobject.ListingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 渠道商品聚合纯单测（R8）。重点验证上架状态机与「渠道商品ID 生命周期」。 */
class ChannelProductTest {

  private static ChannelProduct newProduct() {
    return ChannelProduct.create(1L, 0L, "TAOBAO", 900001L, "测试商品", new BigDecimal("99.00"));
  }

  @Test
  void createStartsUnlisted() {
    ChannelProduct product = newProduct();

    assertEquals(ListingStatus.UNLISTED, product.getListingStatus());
    assertTrue(product.canList());
    assertFalse(product.canDelist(), "未上架商品不可下架");
    assertNull(product.getChannelProductId());
  }

  @Test
  void listThenOnlineBackfillsChannelProductId() {
    ChannelProduct product = newProduct();

    product.markListing(new BigDecimal("88.00"));
    assertEquals(ListingStatus.LISTING, product.getListingStatus());
    assertEquals(new BigDecimal("88.00"), product.getListingPrice(), "重复上架应刷新挂牌价");

    product.markOnline("TB900001", Instant.now());

    assertEquals(ListingStatus.ONLINE, product.getListingStatus());
    assertEquals("TB900001", product.getChannelProductId());
    assertNull(product.getFailReason());
  }

  @Test
  void relistWhileOnlineIsRejected() {
    ChannelProduct product = newProduct();
    product.markListing(null);
    product.markOnline("TB900001", Instant.now());

    assertThrows(IllegalStateException.class, () -> product.markListing(null));
  }

  @Test
  void failureCarriesReasonAndReturnsToRetryableState() {
    ChannelProduct product = newProduct();
    product.markListing(null);

    product.markFailed("CATEGORY_MISMATCH", Instant.now());

    assertEquals(ListingStatus.FAILED, product.getListingStatus());
    assertEquals("CATEGORY_MISMATCH", product.getFailReason());
    assertTrue(product.canList(), "失败态必须可重试，否则运营只能等数据修复");
  }

  @Test
  void delistClearsChannelProductId() {
    ChannelProduct product = newProduct();
    product.markListing(null);
    product.markOnline("TB900001", Instant.now());

    product.markDelisting();
    assertEquals(ListingStatus.DELISTING, product.getListingStatus());

    product.markOffline(Instant.now());

    assertEquals(ListingStatus.OFFLINE, product.getListingStatus());
    assertNull(product.getChannelProductId(), "下架后渠道商品ID已失效，留着会让后续误判仍在线");
  }

  @Test
  void stockSyncUpdatesSnapshotAndTimestamp() {
    ChannelProduct product = newProduct();
    product.markListing(null);
    product.markOnline("TB900001", Instant.now());

    product.markStockSynced(120, Instant.now());

    assertEquals(120, product.getListingStock());
    assertNotNull(product.getLastSyncAt());
  }
}
