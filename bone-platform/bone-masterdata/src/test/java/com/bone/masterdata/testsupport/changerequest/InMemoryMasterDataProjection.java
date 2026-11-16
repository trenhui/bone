package com.bone.masterdata.testsupport.changerequest;

import com.bone.masterdata.domain.gateway.MasterDataProjectionPort;
import com.bone.masterdata.domain.model.dispatch.MasterDataRecordDispatchedEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 消费侧幂等投影夹具：授权集合 + 幂等键集合 + 版本水位，三者对应 P0-4 的三条硬约束。
 *
 * <p>{@code duplicateGuardEnabled=false} 仅用于<b>负向变异注入</b>（模拟消费侧漏做幂等）， 生产端口契约要求 {@link #applyIfNew}
 * 必须幂等。
 */
public final class InMemoryMasterDataProjection implements MasterDataProjectionPort {

  private final Set<String> authorized = new HashSet<>();
  private final Set<String> processedKeys = new HashSet<>();
  private final Map<String, Long> versions = new LinkedHashMap<>();
  private final List<MasterDataRecordDispatchedEvent> projections = new ArrayList<>();
  private boolean duplicateGuardEnabled = true;

  /** 授权粒度：租户 + 主体 + 消费方，模拟 EntitySubscription 的 published language。 */
  public void authorize(long tenantId, String partyId, String consumerId) {
    authorized.add(authorizationKey(tenantId, partyId, consumerId));
  }

  public void setDuplicateGuardEnabled(boolean enabled) {
    this.duplicateGuardEnabled = enabled;
  }

  @Override
  public boolean isAuthorized(MasterDataRecordDispatchedEvent event, String consumerId) {
    return authorized.contains(
        authorizationKey(event.getTenantId(), event.getPartyId(), consumerId));
  }

  @Override
  public boolean applyIfNew(MasterDataRecordDispatchedEvent event) {
    String key = versionKey(event.getTenantId(), event.getPartyId());
    if (!duplicateGuardEnabled) {
      // 负向变异专用：模拟消费侧完全不做幂等 / 版本守护（N7 的反例来源）。
      processedKeys.add(event.idempotencyKey());
      versions.put(key, Math.max(versions.getOrDefault(key, 0L), event.getRecordVersion()));
      projections.add(event);
      return true;
    }
    if (!processedKeys.add(event.idempotencyKey())) {
      return false;
    }
    long last = versions.getOrDefault(key, 0L);
    if (event.getRecordVersion() <= last) {
      return false;
    }
    versions.put(key, event.getRecordVersion());
    projections.add(event);
    return true;
  }

  @Override
  public long lastVersion(long tenantId, String partyId) {
    return versions.getOrDefault(versionKey(tenantId, partyId), 0L);
  }

  @Override
  public List<MasterDataRecordDispatchedEvent> projections() {
    return new ArrayList<>(projections);
  }

  public void reset() {
    authorized.clear();
    processedKeys.clear();
    versions.clear();
    projections.clear();
    duplicateGuardEnabled = true;
  }

  private static String authorizationKey(long tenantId, String partyId, String consumerId) {
    return tenantId + "|" + partyId + "|" + consumerId;
  }

  private static String versionKey(long tenantId, String partyId) {
    return tenantId + "|" + partyId;
  }
}
