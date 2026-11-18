package com.bone.masterdata.domain.model.supplier;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.model.identity.EntityKeyMapping;
import com.bone.masterdata.domain.model.identity.EntityKeyMappingRegistry;
import java.util.List;
import java.util.Optional;

/**
 * 供应商身份解析（域内服务）：本地 ERP/SRM 编码 → 平台统一 partyId。
 *
 * <p><b>刻意不新增的能力</b>：本类<b>不</b>实现任何去重、相似度、合并或冲突裁决算法。它只编排既有 {@link
 * com.bone.masterdata.domain.model.identity.EntityKeyMappingRegistry} 的三个方法（{@code resolve} /
 * {@code register}），把「解析」与「首次登记」串成一条对采购域可用的语义。任何重复实现都会产生第二份身份权威，违背终局分工。
 *
 * <p><b>fail-closed 清单</b>（全部沿用注册表语义，不在此处放宽）：本地编码已绑其它 partyId 禁止改绑；同键多个有效 partyId 视为数据污染
 * 拒绝取值；其它租户的同键映射不参与解析；既无本地编码也无 partyId 时不做任何推断。
 */
public final class SupplierIdentityResolver {

  private final EntityKeyMappingRegistry registry = new EntityKeyMappingRegistry();

  /**
   * 解析统一身份。
   *
   * @param tenantId 登录态租户（不接受调用方自报）
   * @param sourceSystem 来源系统编码，可空（无本地编码时只认显式 partyId）
   * @param localId 来源系统本地编码，可空
   * @param explicitPartyId 命令显式给出的 partyId，可空
   * @param existing 该本地键在该租户下的既有映射（由出站端口查询后传入）
   * @return 解析出的 partyId 与（仅首次登记时的）待落库映射
   */
  public ResolvedSupplierIdentity resolve(
      long tenantId,
      String sourceSystem,
      String localId,
      String explicitPartyId,
      List<EntityKeyMapping> existing) {
    boolean hasLocal = !isBlank(sourceSystem) && !isBlank(localId);
    String explicit = isBlank(explicitPartyId) ? null : explicitPartyId.trim();
    if (!hasLocal) {
      if (explicit == null) {
        throw new DomainException("缺少 partyId 且无本地编码可供 xref 解析");
      }
      return new ResolvedSupplierIdentity(explicit, null);
    }
    Optional<String> resolved = registry.resolve(tenantId, sourceSystem, localId, existing);
    if (resolved.isPresent()) {
      if (explicit != null && !resolved.get().equals(explicit)) {
        throw new DomainException("xref 解析出的 partyId 与命令显式 partyId 不一致，禁止错并主体");
      }
      return new ResolvedSupplierIdentity(resolved.get(), null);
    }
    if (explicit == null) {
      throw new DomainException("本地编码无有效映射且未提供 partyId，fail-closed");
    }
    // register 会拒绝「本地编码已绑其它 partyId」的历史映射（无论其是否已失效），杜绝静默改绑。
    EntityKeyMapping registered =
        registry.register(
            existing, EntityKeyMapping.active(tenantId, explicit, sourceSystem, localId));
    return new ResolvedSupplierIdentity(registered.getPartyId(), registered);
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
