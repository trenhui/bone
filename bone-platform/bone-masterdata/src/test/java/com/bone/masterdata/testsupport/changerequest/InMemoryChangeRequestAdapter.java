package com.bone.masterdata.testsupport.changerequest;

import com.bone.masterdata.domain.gateway.ChangeRequestPort;
import com.bone.masterdata.domain.model.changerequest.ChangeRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 变更请求端口的联调夹具：按 DB 语义以「租户 + CR 号」为键存整笔快照。
 *
 * <p>{@link ChangeRequest} 全字段不可变（状态迁移返回新实例），所以这里存引用不会像可变的 {@code MergeAudit} 那样产生「引用共享假绿」；编排层不显式
 * {@code save} 就读不到新状态。
 */
public final class InMemoryChangeRequestAdapter implements ChangeRequestPort {

  private final Map<String, ChangeRequest> rows = new LinkedHashMap<>();
  private int saveCount;

  private static String key(long tenantId, String changeRequestId) {
    return tenantId + "|" + changeRequestId;
  }

  @Override
  public Optional<ChangeRequest> findById(long tenantId, String changeRequestId) {
    return Optional.ofNullable(rows.get(key(tenantId, changeRequestId)));
  }

  @Override
  public void save(ChangeRequest changeRequest) {
    rows.put(key(changeRequest.getTenantId(), changeRequest.getChangeRequestId()), changeRequest);
    saveCount++;
  }

  @Override
  public boolean existsAppliedFor(long tenantId, String partyId, long targetVersion) {
    return rows.values().stream()
        .anyMatch(
            cr ->
                cr.getTenantId() == tenantId
                    && cr.getPartyId().equals(partyId)
                    && cr.getTargetVersion() == targetVersion
                    && cr.getStatus() == ChangeRequest.Status.APPLIED);
  }

  public int saveCount() {
    return saveCount;
  }

  public void reset() {
    rows.clear();
    saveCount = 0;
  }
}
