package com.bone.masterdata.domain.model.identity;

import com.bone.core.exception.DomainException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** xref 领域服务：注册与解析都执行冲突 fail-closed，不做「后来者覆盖」。 */
public final class EntityKeyMappingRegistry {

  /** 登记映射；同键同 partyId 幂等返回既有项，同键不同 partyId 直接拒绝。 */
  public EntityKeyMapping register(List<EntityKeyMapping> existing, EntityKeyMapping candidate) {
    if (candidate == null) {
      throw new DomainException("待登记映射为空");
    }
    for (EntityKeyMapping e : existing == null ? List.<EntityKeyMapping>of() : existing) {
      if (e.getTenantId() != candidate.getTenantId()) {
        continue;
      }
      if (!e.localKey().equals(candidate.localKey())) {
        continue;
      }
      if (!e.getPartyId().equals(candidate.getPartyId())) {
        throw new DomainException(
            "本地编码已映射到其它 partyId，禁止自动改绑: " + candidate.localKey() + " -> " + e.getPartyId());
      }
      return e;
    }
    return candidate;
  }

  /** 解析本地编码；同键出现多个 active partyId 视为数据污染，fail-closed。 */
  public Optional<String> resolve(
      long tenantId, String sourceSystem, String localId, List<EntityKeyMapping> mappings) {
    String key = sourceSystem + "|" + localId;
    Set<String> parties = new LinkedHashSet<>();
    List<EntityKeyMapping> hits = new ArrayList<>();
    for (EntityKeyMapping e : mappings == null ? List.<EntityKeyMapping>of() : mappings) {
      if (e.getTenantId() == tenantId && e.isActive() && e.localKey().equals(key)) {
        parties.add(e.getPartyId());
        hits.add(e);
      }
    }
    if (parties.size() > 1) {
      throw new DomainException("本地编码存在多个有效 partyId，禁止任意取值: " + key);
    }
    return hits.stream().map(EntityKeyMapping::getPartyId).findFirst();
  }

  /** 反查某 partyId 在某来源系统下的全部本地编码（允许一对多，如多法人多账套）。 */
  public List<EntityKeyMapping> localIdsOf(
      long tenantId, String partyId, String sourceSystem, List<EntityKeyMapping> mappings) {
    List<EntityKeyMapping> result = new ArrayList<>();
    for (EntityKeyMapping e : mappings == null ? List.<EntityKeyMapping>of() : mappings) {
      if (e.getTenantId() == tenantId
          && e.isActive()
          && e.getPartyId().equals(partyId)
          && e.getSourceSystem().equals(sourceSystem)) {
        result.add(e);
      }
    }
    return result;
  }
}
