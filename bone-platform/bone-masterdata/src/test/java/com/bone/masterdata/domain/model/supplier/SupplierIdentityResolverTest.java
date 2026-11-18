package com.bone.masterdata.domain.model.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.model.identity.EntityKeyMapping;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 供应商身份解析：<b>复用</b>既有 {@link com.bone.masterdata.domain.model.identity.EntityKeyMappingRegistry}，
 * 不另建去重/合并逻辑（终局分工：黄金记录的唯一 system of entry 在平台侧 Golden Record 内核）。
 *
 * <p>本测试证明采购域接平台内核的方式正确：本地 ERP/SRM 编码只是查表键，绝不成为身份真源；冲突、污染、跨租户一律 fail-closed。
 */
@DisplayName("供应商身份解析：复用 xref 注册表，冲突与跨租户 fail-closed")
class SupplierIdentityResolverTest {

  private static final long TENANT = 7L;
  private static final String SOURCE = "SRM";
  private static final String LOCAL_ID = "V-1001";

  private final SupplierIdentityResolver resolver = new SupplierIdentityResolver();

  @Test
  @DisplayName("唯一有效映射直接解析出 partyId，且不产生新的待登记映射")
  void resolvesUniqueActiveMapping() {
    List<EntityKeyMapping> existing =
        List.of(EntityKeyMapping.active(TENANT, "PARTY-A", SOURCE, LOCAL_ID));

    ResolvedSupplierIdentity identity = resolver.resolve(TENANT, SOURCE, LOCAL_ID, null, existing);

    assertThat(identity.partyId()).isEqualTo("PARTY-A");
    assertThat(identity.pendingRegistration()).isNull();
  }

  @Test
  @DisplayName("无映射且给出显式 partyId 时产出待登记映射（由应用层在裁决成立后落库）")
  void producesPendingRegistrationWhenExplicitPartyIdGiven() {
    ResolvedSupplierIdentity identity =
        resolver.resolve(TENANT, SOURCE, LOCAL_ID, "PARTY-B", List.of());

    assertThat(identity.partyId()).isEqualTo("PARTY-B");
    assertThat(identity.pendingRegistration()).isNotNull();
    assertThat(identity.pendingRegistration().getPartyId()).isEqualTo("PARTY-B");
    assertThat(identity.pendingRegistration().getTenantId()).isEqualTo(TENANT);
  }

  @Test
  @DisplayName("显式 partyId 与已解析映射不一致时拒绝，禁止错并主体")
  void rejectsExplicitPartyIdConflictingWithResolvedMapping() {
    List<EntityKeyMapping> existing =
        List.of(EntityKeyMapping.active(TENANT, "PARTY-A", SOURCE, LOCAL_ID));

    assertThatThrownBy(() -> resolver.resolve(TENANT, SOURCE, LOCAL_ID, "PARTY-B", existing))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("不一致");
  }

  @Test
  @DisplayName("本地编码曾绑其它 partyId（含已失效映射）时禁止自动改绑")
  void rejectsRebindOfExistingMapping() {
    List<EntityKeyMapping> existing =
        List.of(EntityKeyMapping.of(TENANT, "PARTY-A", SOURCE, LOCAL_ID, 0.8, false));

    assertThatThrownBy(() -> resolver.resolve(TENANT, SOURCE, LOCAL_ID, "PARTY-B", existing))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("改绑");
  }

  @Test
  @DisplayName("同一本地编码对应多个有效 partyId（数据污染）时禁止任意取值")
  void detectsPollutedDuplicatePartyIds() {
    List<EntityKeyMapping> existing =
        List.of(
            EntityKeyMapping.active(TENANT, "PARTY-A", SOURCE, LOCAL_ID),
            EntityKeyMapping.active(TENANT, "PARTY-B", SOURCE, LOCAL_ID));

    assertThatThrownBy(() -> resolver.resolve(TENANT, SOURCE, LOCAL_ID, null, existing))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("多个有效");
  }

  @Test
  @DisplayName("其它租户的同键映射不被采信：无显式 partyId 时 fail-closed")
  void ignoresOtherTenantMappings() {
    List<EntityKeyMapping> otherTenant =
        List.of(EntityKeyMapping.active(9L, "PARTY-OTHER", SOURCE, LOCAL_ID));

    assertThatThrownBy(() -> resolver.resolve(TENANT, SOURCE, LOCAL_ID, null, otherTenant))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("fail-closed");
  }

  @Test
  @DisplayName("既无本地编码也无 partyId 时拒绝，不做无依据的身份推断")
  void rejectsMissingIdentityInputs() {
    assertThatThrownBy(() -> resolver.resolve(TENANT, null, null, null, List.of()))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("partyId");
  }
}
