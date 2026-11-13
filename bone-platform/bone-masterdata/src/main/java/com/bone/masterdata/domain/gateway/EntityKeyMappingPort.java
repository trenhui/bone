package com.bone.masterdata.domain.gateway;

import com.bone.masterdata.domain.model.identity.EntityKeyMapping;
import java.util.List;

/**
 * 跨系统实体身份映射（xref / Key Mapping）出站端口。
 *
 * <p><b>终局契约</b>：本地 ERP/SRM 编码只用于解析平台统一身份 {@code partyId}，绝不成为跨域身份真源。 生产实现由 L3 的 JDBC
 * adapter（{@code mdm_xref}）提供；同一 {@code (tenant, sourceSystem, localId)} 只能指向一个 {@code
 * partyId}，改绑由领域内核 fail-closed 拒绝。
 */
public interface EntityKeyMappingPort {

  /** 按本地键取映射（含 inactive，供冲突检测）。 */
  List<EntityKeyMapping> findByLocalKey(long tenantId, String sourceSystem, String localId);

  /** 反查某统一身份在某来源系统下的全部本地编码（允许一对多：多法人多账套）。 */
  List<EntityKeyMapping> findByPartyId(long tenantId, String partyId);

  /** 登记一条新映射；重复键由领域内核裁决，adapter 不得静默覆盖。 */
  void register(EntityKeyMapping mapping);
}
