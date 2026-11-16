package com.bone.masterdata.testsupport.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.model.supplier.LegalEntity;
import com.bone.masterdata.domain.model.supplier.Supplier;
import com.bone.masterdata.domain.model.supplier.SupplierState;
import com.bone.masterdata.domain.model.supplier.Vendor;
import com.bone.masterdata.domain.model.supplier.VendorState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 出站端口的契约单测：证明端口语义与 adapter 无关（本切片由内存实现提供，未来 JDBC adapter 必须满足同一组断言）。
 *
 * <p>重点在三组不能靠「实现巧合」通过的语义：<b>跨租户不可见</b>、<b>同身份覆盖只保留最新版本</b>、<b>陈旧版本写入必被拒</b>。
 *
 * <p>最后一条是本切片新增的硬约束（独立审查 I-02）：端口签名带上 {@code expectedVersion}，夹具就必须真的校验它 ——
 * 否则「两个治理员同时推进同一主体」的覆盖缺陷在测试里永远不会暴露，而生产 adapter 上线后才会以 500 的形式出现。
 */
@DisplayName("供应商主数据出站端口契约")
class SupplierPortContractTest {

  private static final long TENANT = 7L;
  private static final long OTHER_TENANT = 9L;

  @Test
  @DisplayName("保存后可按键读回最新版本")
  void savesAndLoadsCurrentVersion() {
    InMemorySupplierRepositoryPort port = new InMemorySupplierRepositoryPort();
    port.save(Supplier.rehydrate(TENANT, "PARTY-1", SupplierState.ACTIVE, 3L), 0L);

    Supplier loaded = port.findCurrent(TENANT, "PARTY-1").orElseThrow();
    assertThat(loaded.state()).isEqualTo(SupplierState.ACTIVE);
    assertThat(loaded.version()).isEqualTo(3L);
  }

  @Test
  @DisplayName("同身份再次保存只保留最新版本，不产生第二条记录")
  void overwritesSamePartyId() {
    InMemorySupplierRepositoryPort port = new InMemorySupplierRepositoryPort();
    port.save(Supplier.rehydrate(TENANT, "PARTY-1", SupplierState.ACTIVE, 3L), 0L);
    port.save(Supplier.rehydrate(TENANT, "PARTY-1", SupplierState.SUSPENDED, 4L), 3L);

    assertThat(port.countByTenant(TENANT)).isEqualTo(1L);
    assertThat(port.findCurrent(TENANT, "PARTY-1").orElseThrow().version()).isEqualTo(4L);
  }

  @Test
  @DisplayName("乐观并发：按陈旧版本写入被拒，且不覆盖已落库的最新状态")
  void rejectsStaleVersionWrite() {
    InMemorySupplierRepositoryPort port = new InMemorySupplierRepositoryPort();
    port.save(Supplier.rehydrate(TENANT, "PARTY-1", SupplierState.ACTIVE, 3L), 0L);
    port.save(Supplier.rehydrate(TENANT, "PARTY-1", SupplierState.SUSPENDED, 4L), 3L);

    // 另一个治理员仍以为当前版本是 3，按 3 写入必须被拒（而不是静默覆盖 SUSPENDED）。
    assertThatThrownBy(
            () -> port.save(Supplier.rehydrate(TENANT, "PARTY-1", SupplierState.EXITED, 5L), 3L))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("并发版本冲突");

    assertThat(port.findCurrent(TENANT, "PARTY-1").orElseThrow().state())
        .isEqualTo(SupplierState.SUSPENDED);
    assertThat(port.saveCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("跨租户不可见：其它租户按键读不到，也不计入本租户总数")
  void isolatesTenants() {
    InMemorySupplierRepositoryPort port = new InMemorySupplierRepositoryPort();
    port.save(Supplier.rehydrate(OTHER_TENANT, "PARTY-1", SupplierState.ACTIVE, 1L), 0L);

    assertThat(port.findCurrent(TENANT, "PARTY-1")).isEmpty();
    assertThat(port.countByTenant(TENANT)).isZero();
    assertThat(port.countByTenant(OTHER_TENANT)).isEqualTo(1L);
  }

  @Test
  @DisplayName("按 partyId 字典序分页，offset/limit 生效")
  void pagesByStableOrder() {
    InMemorySupplierRepositoryPort port = new InMemorySupplierRepositoryPort();
    for (String partyId : new String[] {"PARTY-3", "PARTY-1", "PARTY-2"}) {
      port.save(Supplier.rehydrate(TENANT, partyId, SupplierState.PROSPECT, 1L), 0L);
    }

    assertThat(port.listByTenant(TENANT, 0, 2))
        .extracting(Supplier::partyId)
        .containsExactly("PARTY-1", "PARTY-2");
    assertThat(port.listByTenant(TENANT, 2, 2))
        .extracting(Supplier::partyId)
        .containsExactly("PARTY-3");
  }

  @Test
  @DisplayName("Vendor 端口同样按租户隔离并可覆盖")
  void vendorPortIsolatesTenants() {
    InMemoryVendorRepositoryPort port = new InMemoryVendorRepositoryPort();
    port.save(Vendor.rehydrate(TENANT, "PARTY-1", VendorState.ACTIVE_VENDOR, 2L), 0L);

    assertThat(port.findCurrent(TENANT, "PARTY-1").orElseThrow().state())
        .isEqualTo(VendorState.ACTIVE_VENDOR);
    assertThat(port.findCurrent(OTHER_TENANT, "PARTY-1")).isEmpty();
    assertThat(port.saveCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("法人身份端口：租户内可按码查、跨租户隔离、陈旧版本写入被拒")
  void legalEntityPortEnforcesIdentityScopeAndVersion() {
    InMemoryLegalEntityRepositoryPort port = new InMemoryLegalEntityRepositoryPort();
    port.save(LegalEntity.of(TENANT, "PARTY-1", "91310000MA1FL0Q23X", "TAX-1"), 0L);

    // 身份主键查询忽略大小写（统一社会信用代码在现实录入里大小写混用）。
    assertThat(port.findByUnifiedSocialCreditCode(TENANT, "91310000ma1fl0q23x")).isPresent();
    assertThat(port.findByTaxId(TENANT, "TAX-1")).isPresent();
    assertThat(port.findByUnifiedSocialCreditCode(OTHER_TENANT, "91310000MA1FL0Q23X"))
        .as("身份唯一性只在租户内成立，跨租户同码不得互相阻断")
        .isEmpty();

    assertThatThrownBy(
            () ->
                port.save(
                    LegalEntity.rehydrate(TENANT, "PARTY-1", "91310000MA1FL0Q23X", "TAX-1", 2L),
                    0L))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("并发版本冲突");
    assertThat(port.findByPartyId(TENANT, "PARTY-1").orElseThrow().version()).isEqualTo(1L);
    assertThat(port.saveCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("Vendor 端口同样拒绝陈旧版本写入")
  void vendorPortRejectsStaleVersionWrite() {
    InMemoryVendorRepositoryPort port = new InMemoryVendorRepositoryPort();
    port.save(Vendor.rehydrate(TENANT, "PARTY-1", VendorState.PENDING_VENDOR, 1L), 0L);
    port.save(Vendor.rehydrate(TENANT, "PARTY-1", VendorState.ACTIVE_VENDOR, 2L), 1L);

    assertThatThrownBy(
            () ->
                port.save(Vendor.rehydrate(TENANT, "PARTY-1", VendorState.INACTIVE_VENDOR, 3L), 1L))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("并发版本冲突");
    assertThat(port.findCurrent(TENANT, "PARTY-1").orElseThrow().state())
        .isEqualTo(VendorState.ACTIVE_VENDOR);
  }
}
