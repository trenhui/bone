package com.bone.masterdata.testsupport.goldenrecord;

import com.bone.masterdata.domain.gateway.GoldenRecordPort;
import com.bone.masterdata.domain.model.goldenrecord.GoldenRecord;
import com.bone.masterdata.domain.model.goldenrecord.MergeAudit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Golden Record 端口的<b>联调夹具</b>（仅 test scope，绝不进生产制品）。
 *
 * <p><b>为什么做审计行拷贝而非共享引用</b>：领域对象 {@link MergeAudit} 的撤销标记是可变字段。若适配器直接存同一个
 * 对象引用，即使编排层忘了把「已撤销」状态写回端口，测试也会因为引用共享而误判绿。这里按 DB 语义把审计拆成行、 读取时重建对象，只有编排层<b>显式回写</b>墓碑，{@link
 * #findAudit} 才能看到 rolledBack=true，从而真正守住 「撤销状态必须持久化」这条不变量。
 *
 * <p>本夹具实现的是终局端口契约本身，不是「先用内存、二期换 DB」的过渡实现：生产替换的只是 JDBC adapter， 编排层与领域内核零改动。
 */
public final class InMemoryGoldenRecordAdapter implements GoldenRecordPort {

  private static final class AuditRow {
    private final String mergeId;
    private final long tenantId;
    private final String partyId;
    private final GoldenRecord before;
    private final GoldenRecord after;
    private final List<MergeAudit.MergeDecision> decisions;
    private final java.time.Instant createdAt;
    private boolean rolledBack;
    private String reason;
    private java.time.Instant rolledBackAt;
    private String operator;

    private AuditRow(MergeAudit a) {
      this.mergeId = a.getMergeId();
      this.tenantId = a.getTenantId();
      this.partyId = a.getPartyId();
      this.before = a.getBefore();
      this.after = a.getAfter();
      this.decisions = new ArrayList<>(a.getDecisions());
      this.createdAt = a.getCreatedAt();
      this.rolledBack = a.isRolledBack();
      this.reason = a.getRollbackReason();
      this.rolledBackAt = a.getRolledBackAt();
      this.operator = a.getRolledBackBy();
    }

    private MergeAudit rebuild() {
      MergeAudit a = MergeAudit.of(mergeId, tenantId, partyId, before, after, decisions, createdAt);
      if (rolledBack) {
        a.rollback(reason, operator, rolledBackAt);
      }
      return a;
    }
  }

  private final Map<String, GoldenRecord> currents = new LinkedHashMap<>();
  private final Map<String, AuditRow> audits = new LinkedHashMap<>();
  private int appendCount;
  private int clearCount;

  @Override
  public Optional<GoldenRecord> findCurrent(long tenantId, String partyId) {
    return Optional.ofNullable(currents.get(currentKey(tenantId, partyId)));
  }

  @Override
  public Optional<MergeAudit> findAudit(long tenantId, String mergeId) {
    return Optional.ofNullable(audits.get(auditKey(tenantId, mergeId))).map(AuditRow::rebuild);
  }

  @Override
  public void appendSnapshot(GoldenRecord snapshot, MergeAudit audit) {
    if (snapshot == null || audit == null) {
      throw new IllegalArgumentException("快照与审计必须同时落库");
    }
    currents.put(currentKey(snapshot.getTenantId(), snapshot.getPartyId()), snapshot);
    audits.put(auditKey(audit.getTenantId(), audit.getMergeId()), new AuditRow(audit));
    appendCount++;
  }

  @Override
  public void clearCurrent(long tenantId, String partyId, MergeAudit tombstone) {
    if (tombstone == null || !tombstone.isRolledBack()) {
      throw new IllegalArgumentException("撤销到无必须回写已标记撤销的审计墓碑");
    }
    currents.remove(currentKey(tenantId, partyId));
    audits.put(auditKey(tenantId, tombstone.getMergeId()), new AuditRow(tombstone));
    clearCount++;
  }

  public int appendCount() {
    return appendCount;
  }

  public int clearCount() {
    return clearCount;
  }

  public int auditCount() {
    return audits.size();
  }

  public void reset() {
    currents.clear();
    audits.clear();
    appendCount = 0;
    clearCount = 0;
  }

  private static String currentKey(long tenantId, String partyId) {
    return tenantId + "|" + partyId;
  }

  private static String auditKey(long tenantId, String mergeId) {
    return tenantId + "|" + mergeId;
  }
}
