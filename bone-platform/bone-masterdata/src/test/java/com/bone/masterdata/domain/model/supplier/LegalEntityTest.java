package com.bone.masterdata.domain.model.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 双角色语义守护（LLD `01-供应商主数据模块详细设计.md:22`：合作资格与交易/付款资格两套互不覆盖的状态机）。
 *
 * <p>本测试只覆盖「角色分离 + 身份载体」这一层不变式；两套状态机的迁移规则分别由 {@code SupplierLifecycleTest} 与 {@code
 * VendorLifecycleTest} 覆盖。
 */
@DisplayName("供应商双角色：LegalEntity 身份载体 + Supplier/Vendor 角色隔离")
class LegalEntityTest {

  private static final long TENANT = 7L;

  @Test
  @DisplayName("同一法人可同时持有合作资格与交易付款资格，统一 partyId 关联")
  void sameLegalEntityHoldsBothRoles() {
    LegalEntity legalEntity = LegalEntity.of(TENANT, "PARTY-1", "91310000MA1FL0Q23X", "TAX-1");

    Supplier supplier = Supplier.prospect(TENANT, legalEntity.partyId());
    Vendor vendor = Vendor.pending(TENANT, legalEntity.partyId());

    assertThat(supplier.partyId()).isEqualTo(legalEntity.partyId());
    assertThat(vendor.partyId()).isEqualTo(legalEntity.partyId());
    assertThat(supplier.tenantId()).isEqualTo(TENANT);
    assertThat(vendor.tenantId()).isEqualTo(TENANT);
    assertThat(supplier.state()).isEqualTo(SupplierState.PROSPECT);
    assertThat(vendor.state()).isEqualTo(VendorState.PENDING_VENDOR);
  }

  @Test
  @DisplayName("两套状态机的状态枚举不相交，无法互相推导或覆盖")
  void stateEnumsAreDisjoint() {
    Set<String> supplierStates =
        Arrays.stream(SupplierState.values()).map(Enum::name).collect(Collectors.toSet());
    Set<String> vendorStates =
        Arrays.stream(VendorState.values()).map(Enum::name).collect(Collectors.toSet());

    assertThat(Collections.disjoint(supplierStates, vendorStates))
        .as("Supplier 与 Vendor 的状态名不得有任何交集，否则存在被互相推导的空间")
        .isTrue();
  }

  @Test
  @DisplayName("同一 partyId 两侧状态各自独立：一侧 SUSPENDED 不影响另一侧 ACTIVE_VENDOR")
  void rolesKeepIndependentStates() {
    Supplier supplier = Supplier.rehydrate(TENANT, "PARTY-1", SupplierState.SUSPENDED, 3L);
    Vendor vendor = Vendor.rehydrate(TENANT, "PARTY-1", VendorState.ACTIVE_VENDOR, 5L);

    assertThat(supplier.state()).isEqualTo(SupplierState.SUSPENDED);
    assertThat(vendor.state()).isEqualTo(VendorState.ACTIVE_VENDOR);

    // 读取一侧的状态不得改写另一侧：状态字段分属两个聚合，不存在共享引用。
    assertThat(vendor.state()).isNotEqualTo(VendorState.PENDING_VENDOR);
    assertThat(supplier.state()).isNotEqualTo(SupplierState.ACTIVE);
  }

  @Test
  @DisplayName("角色不可相互穿透：Supplier 不引用 Vendor、Vendor 不引用 Supplier")
  void rolesDoNotCrossExpose() {
    assertThat(referencedTypes(Supplier.class))
        .as("Supplier 聚合不得引用 Vendor，否则角色边界可被静默穿透")
        .doesNotContain(Vendor.class)
        .doesNotContain(VendorState.class);
    assertThat(referencedTypes(Vendor.class))
        .as("Vendor 聚合不得引用 Supplier，否则角色边界可被静默穿透")
        .doesNotContain(Supplier.class)
        .doesNotContain(SupplierState.class);
  }

  @Test
  @DisplayName("缺少 partyId、租户非法或两项身份主键均缺失时拒绝构造法人")
  void rejectsLegalEntityWithoutIdentity() {
    assertThatThrownBy(() -> LegalEntity.of(TENANT, "", "91310000MA1FL0Q23X", null))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("partyId");

    assertThatThrownBy(() -> LegalEntity.of(TENANT, "PARTY-1", "  ", ""))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("身份主键");

    // 租户是唯一性裁决的维度之一：没有租户就无法判断「同一统一社会信用代码是否已属他人」，因此禁止构造。
    assertThatThrownBy(() -> LegalEntity.of(0L, "PARTY-1", "91310000MA1FL0Q23X", null))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("租户");
  }

  @Test
  @DisplayName("身份主键冲突只在同租户内成立：跨租户同码不算冲突")
  void identityConflictIsTenantScoped() {
    LegalEntity mine = LegalEntity.of(TENANT, "PARTY-1", "91310000MA1FL0Q23X", "TAX-1");
    LegalEntity sameUsccOtherParty = LegalEntity.of(TENANT, "PARTY-2", "91310000MA1FL0Q23X", null);
    LegalEntity sameTaxIdOtherParty = LegalEntity.of(TENANT, "PARTY-3", null, "TAX-1");
    LegalEntity otherTenant = LegalEntity.of(TENANT + 1, "PARTY-4", "91310000MA1FL0Q23X", "TAX-1");

    assertThat(mine.identityConflictsWith(sameUsccOtherParty)).isTrue();
    assertThat(mine.identityConflictsWith(sameTaxIdOtherParty)).isTrue();
    assertThat(mine.identityConflictsWith(otherTenant))
        .as("跨租户不参与身份裁决，否则一个租户的编码会阻断另一个租户的登记")
        .isFalse();
    assertThat(mine.identityConflictsWith(null)).isFalse();
    // 自命中返回 true（身份主键确实相同），「是不是另一个主体」由应用层按 partyId 判定，
    // 这样同一主体重复登记走版本递增，而不是被自己判成冲突转人工。
    assertThat(mine.identityConflictsWith(mine)).isTrue();
    assertThat(mine.partyId()).isEqualTo("PARTY-1");
    assertThat(mine.version()).isEqualTo(1L);
  }

  @Test
  @DisplayName("身份主键去空白归一：空白税号变 null，否则按税号唯一性查询会命中一串空格")
  void normalizesIdentityKeys() {
    LegalEntity entity = LegalEntity.of(TENANT, "  PARTY-1  ", "  91310000MA1FL0Q23X ", "   ");

    assertThat(entity.tenantId()).isEqualTo(TENANT);
    assertThat(entity.partyId()).isEqualTo("PARTY-1");
    assertThat(entity.unifiedSocialCreditCode()).isEqualTo("91310000MA1FL0Q23X");
    assertThat(entity.taxId()).isNull();
  }

  @Test
  @DisplayName("重建带版本：版本小于 1 一律拒绝，避免把已落库身份读成「从未登记的 0 版」")
  void rejectsRebuildWithInvalidVersion() {
    assertThatThrownBy(
            () -> LegalEntity.rehydrate(TENANT, "PARTY-1", "91310000MA1FL0Q23X", null, 0L))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("版本");

    assertThat(LegalEntity.rehydrate(TENANT, "PARTY-1", "91310000MA1FL0Q23X", null, 4L).version())
        .isEqualTo(4L);
  }

  /** 聚合自身声明的全部方法签名类型（返回值 + 形参），用于结构性判定角色是否被穿透。 */
  private static Set<Class<?>> referencedTypes(Class<?> type) {
    Set<Class<?>> types = new LinkedHashSet<>();
    for (Method method : type.getDeclaredMethods()) {
      types.add(method.getReturnType());
      types.addAll(Arrays.asList(method.getParameterTypes()));
    }
    return types;
  }
}
