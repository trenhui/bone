package com.bone.masterdata.testsupport.goldenrecord;

import com.bone.masterdata.domain.gateway.EntityKeyMappingPort;
import com.bone.masterdata.domain.model.identity.EntityKeyMapping;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** xref 端口的联调夹具：按 (tenant, sourceSystem, localId) 建索引，供断言「转人工时零登记」。 */
public final class InMemoryEntityKeyMappingAdapter implements EntityKeyMappingPort {

  private final Map<String, List<EntityKeyMapping>> byLocalKey = new LinkedHashMap<>();
  private int registerCount;

  @Override
  public List<EntityKeyMapping> findByLocalKey(long tenantId, String sourceSystem, String localId) {
    return new ArrayList<>(
        byLocalKey.getOrDefault(key(tenantId, sourceSystem, localId), List.of()));
  }

  @Override
  public List<EntityKeyMapping> findByPartyId(long tenantId, String partyId) {
    List<EntityKeyMapping> result = new ArrayList<>();
    byLocalKey.values().stream()
        .flatMap(List::stream)
        .filter(m -> m.getTenantId() == tenantId && m.getPartyId().equals(partyId))
        .forEach(result::add);
    return result;
  }

  @Override
  public void register(EntityKeyMapping mapping) {
    String key = key(mapping.getTenantId(), mapping.getSourceSystem(), mapping.getLocalId());
    byLocalKey.computeIfAbsent(key, k -> new ArrayList<>()).add(mapping);
    registerCount++;
  }

  /** 仅供负向用例注入「同键改绑」污染数据，绕过领域裁决。 */
  public void seedRaw(EntityKeyMapping mapping) {
    register(mapping);
  }

  public int registerCount() {
    return registerCount;
  }

  public int size() {
    return byLocalKey.values().stream().mapToInt(List::size).sum();
  }

  public void reset() {
    byLocalKey.clear();
    registerCount = 0;
  }

  private static String key(long tenantId, String sourceSystem, String localId) {
    return tenantId + "|" + sourceSystem + "|" + localId;
  }
}
