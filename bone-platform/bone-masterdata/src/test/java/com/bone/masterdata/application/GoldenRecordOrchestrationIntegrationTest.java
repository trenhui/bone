package com.bone.masterdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.application.command.ResolveAndMergeCommand;
import com.bone.masterdata.application.command.RollbackMergeCommand;
import com.bone.masterdata.application.query.dto.GoldenRecordMergeResultDTO;
import com.bone.masterdata.application.query.dto.GoldenRecordStateDTO;
import com.bone.masterdata.domain.model.dispatch.MasterDataRecordDispatchedEvent;
import com.bone.masterdata.domain.model.goldenrecord.GoldenRecord;
import com.bone.masterdata.domain.model.identity.EntityKeyMapping;
import com.bone.masterdata.testsupport.goldenrecord.InMemoryEntityKeyMappingAdapter;
import com.bone.masterdata.testsupport.goldenrecord.InMemoryGoldenRecordAdapter;
import com.bone.masterdata.testsupport.goldenrecord.InMemoryMasterDataOutboxAdapter;
import com.bone.masterdata.testsupport.goldenrecord.TestCurrentUserPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Golden Record 端到端联调：真实编排服务 + 端口夹具，验证「xref 解析 → survivorship 合并 → 快照/审计落库 → Outbox 幂等分发 →
 * 整笔回滚」这条链路，以及领域内核五条硬约束在应用层的传导。
 *
 * <p>与 31 例纯单测的区别：这里断言的是<b>跨组件副作用</b>（有没有落库、有没有登记映射、有没有投递事件、 审计有没有被回写），而不是单个领域对象的返回值。夹具按 DB
 * 语义做审计行拷贝，避免引用共享造成假绿。
 */
class GoldenRecordOrchestrationIntegrationTest {

  private static final String PEPPER = "integration-test-pepper-2026-11-13";
  private static final Instant T0 = Instant.parse("2026-11-13T02:00:00Z");
  private static final String USCC = "91310000MA1FL0AB2C";
  private static final String PHONE = "13800000001";
  private static final String NAME_A = "上海某某科技有限公司";

  private InMemoryGoldenRecordAdapter golden;
  private InMemoryEntityKeyMappingAdapter xref;
  private InMemoryMasterDataOutboxAdapter outbox;
  private TestCurrentUserPort currentUser;
  private GoldenRecordOrchestrationService service;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    golden = new InMemoryGoldenRecordAdapter();
    xref = new InMemoryEntityKeyMappingAdapter();
    outbox = new InMemoryMasterDataOutboxAdapter();
    currentUser = new TestCurrentUserPort();
    service = newService(PEPPER);
  }

  private GoldenRecordOrchestrationService newService(String pepper) {
    return new GoldenRecordOrchestrationService(golden, xref, outbox, currentUser, pepper);
  }

  // ---------------------------------------------------------------- 正常链路

  @Test
  @DisplayName("全链路：xref 登记 → 合并落库 → 审计 → 幂等事件，且对客脱敏")
  void fullChainShouldLandSnapshotAuditAndOneDesensitizedEvent() throws Exception {
    GoldenRecordMergeResultDTO dto =
        service.resolveAndMerge(cmd("M1", "P-1", "L-1", values1(), values2()));

    assertThat(dto.getOutcome()).isEqualTo("APPLIED");
    assertThat(dto.getVersion()).isEqualTo(1L);
    assertThat(dto.getFieldCount()).isEqualTo(4);
    assertThat(dto.isDispatched()).isTrue();
    assertThat(dto.getIdempotencyKey()).isEqualTo("1|P-1|1|GOLDEN_RECORD_MERGED");

    GoldenRecord stored = golden.findCurrent(1L, "P-1").orElseThrow();
    assertThat(stored.getValue("legal_name")).isEqualTo(NAME_A); // trustRank 1 胜出
    assertThat(stored.getProvenance("city").getSourceRecordId()).isEqualTo("S2");
    assertThat(stored.getProvenance("legal_name").getSourceSystem()).isEqualTo("ERP");
    assertThat(golden.auditCount()).isEqualTo(1);
    assertThat(golden.findAudit(1L, "M1")).isPresent();

    assertThat(xref.registerCount()).isEqualTo(1);
    assertThat(xref.findByLocalKey(1L, "ERP", "L-1")).hasSize(1);
    assertThat(xref.findByLocalKey(1L, "ERP", "L-1").get(0).getPartyId()).isEqualTo("P-1");

    assertThat(outbox.pending()).hasSize(1);
    MasterDataRecordDispatchedEvent event = outbox.pending().get(0);
    assertThat(event.getEventType()).isEqualTo("GOLDEN_RECORD_MERGED");
    assertThat(event.getPayloadHash()).hasSize(64).doesNotContain(PHONE, NAME_A, USCC);
    assertThat(event.getSchemaVersion()).isEqualTo(1);

    String json = objectMapper.writeValueAsString(dto);
    assertThat(json).doesNotContain(PHONE, NAME_A, USCC);
    assertThat(json).contains("idempotencyKey", "payloadHash");

    GoldenRecordStateDTO state = service.current(1L, "P-1").orElseThrow();
    assertThat(state.getVersion()).isEqualTo(1L);
    assertThat(state.getFieldCodes()).contains("legal_name", "city", "contact_phone");
    assertThat(objectMapper.writeValueAsString(state)).doesNotContain(NAME_A, PHONE);
  }

  @Test
  @DisplayName("二次合并版本单调递增，幂等键随版本变化且新事件可判定更新")
  void secondMergeShouldBumpVersionAndEmitDistinctKey() {
    service.resolveAndMerge(cmd("M1", "P-1", "L-1", values1(), values2()));
    GoldenRecordMergeResultDTO second =
        service.resolveAndMerge(cmd("M2", "P-1", "L-1", values1b(), values2()));

    assertThat(second.getVersion()).isEqualTo(2L);
    assertThat(second.getIdempotencyKey()).isEqualTo("1|P-1|2|GOLDEN_RECORD_MERGED");
    assertThat(outbox.pending()).hasSize(2);
    List<MasterDataRecordDispatchedEvent> events = outbox.pending();
    assertThat(events.get(1).isNewerThan(events.get(0))).isTrue();
    // 同一本地键不应重复登记映射
    assertThat(xref.registerCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("Outbox 已有同幂等键时编排层不重复投递，也不触发唯一索引冲突")
  void replayShouldNotDoubleDispatch() {
    outbox.append(
        MasterDataRecordDispatchedEvent.of(
            "PRESET", 1L, "P-1", 1L, "GOLDEN_RECORD_MERGED", 1, T0, "preset-hash"));
    GoldenRecordMergeResultDTO dto =
        service.resolveAndMerge(cmd("M1", "P-1", "L-1", values1(), values2()));

    assertThat(dto.isDispatched()).isFalse();
    assertThat(outbox.pending()).hasSize(1);
    assertThat(golden.findCurrent(1L, "P-1")).isPresent();
  }

  // ---------------------------------------------------------------- 硬约束传导

  @Test
  @DisplayName("身份字段冲突转人工：零快照、零 xref 登记、零分发")
  void identityConflictShouldHaveZeroSideEffects() {
    Map<String, String> a = new LinkedHashMap<>(values1());
    a.put("tax_id", "TAX-A");
    Map<String, String> b = new LinkedHashMap<>(values2());
    b.put("tax_id", "TAX-B");

    GoldenRecordMergeResultDTO dto = service.resolveAndMerge(cmd("M1", "P-1", "L-1", a, b));
    assertThat(dto.getOutcome()).isEqualTo("MANUAL_REVIEW_REQUIRED");
    assertThat(dto.getReason()).contains("身份字段冲突");
    assertZeroSideEffects();
  }

  @Test
  @DisplayName("MANUAL_REVIEW 策略整笔转人工，零副作用")
  void manualReviewStrategyShouldHaveZeroSideEffects() {
    ResolveAndMergeCommand c = cmd("M1", "P-1", "L-1", values1(), values2());
    c.setStrategies(Map.of("legal_name", "MANUAL_REVIEW"));

    assertThat(service.resolveAndMerge(c).getOutcome()).isEqualTo("MANUAL_REVIEW_REQUIRED");
    assertZeroSideEffects();
  }

  @Test
  @DisplayName("STEWARD_OVERRIDE 缺具名裁决整笔转人工，零副作用")
  void stewardOverrideWithoutNamedDecisionShouldHaveZeroSideEffects() {
    ResolveAndMergeCommand c = cmd("M1", "P-1", "L-1", values1(), values2());
    c.setStrategies(Map.of("legal_name", "STEWARD_OVERRIDE"));

    assertThat(service.resolveAndMerge(c).getOutcome()).isEqualTo("MANUAL_REVIEW_REQUIRED");
    assertZeroSideEffects();
  }

  @Test
  @DisplayName("非法胜出策略 fail-closed，且零副作用")
  void illegalStrategyShouldFailClosed() {
    ResolveAndMergeCommand c = cmd("M1", "P-1", "L-1", values1(), values2());
    c.setStrategies(Map.of("legal_name", "WHATEVER"));

    assertThatThrownBy(() -> service.resolveAndMerge(c)).isInstanceOf(DomainException.class);
    assertZeroSideEffects();
  }

  @Test
  @DisplayName("xref 同键改绑 fail-closed，且不新增登记")
  void xrefRebindingShouldFailClosed() {
    // inactive 旧映射不会被 resolve 命中，但 register 仍必须拒绝同键改绑（防串主体）。
    xref.seedRaw(EntityKeyMapping.of(1L, "P-OTHER", "ERP", "L-1", 0.5, false));
    int baseline = xref.registerCount();

    assertThatThrownBy(() -> service.resolveAndMerge(cmd("M1", "P-1", "L-1", values1(), values2())))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("禁止自动改绑");
    assertThat(xref.registerCount()).isEqualTo(baseline);
    assertThat(golden.appendCount()).isZero();
    assertThat(outbox.pending()).isEmpty();
  }

  @Test
  @DisplayName("xref 解析结果与命令显式 partyId 不一致时禁止错并主体")
  void xrefVsExplicitPartyMismatchShouldFailClosed() {
    xref.seedRaw(EntityKeyMapping.active(1L, "P-EXIST", "ERP", "L-1"));

    assertThatThrownBy(() -> service.resolveAndMerge(cmd("M1", "P-1", "L-1", values1(), values2())))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("不一致");
    assertThat(golden.appendCount()).isZero();
  }

  @Test
  @DisplayName("本地编码无映射且未给 partyId：fail-closed，不自动造身份")
  void unmappedLocalKeyWithoutPartyShouldFailClosed() {
    ResolveAndMergeCommand c = cmd("M1", "P-1", "L-1", values1(), values2());
    c.setPartyId(null);

    assertThatThrownBy(() -> service.resolveAndMerge(c))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("fail-closed");
    assertZeroSideEffects();
  }

  // ---------------------------------------------------------------- 租户隔离

  @Test
  @DisplayName("命令 tenantId 与登录态不一致：跨租户治理 fail-closed")
  void crossTenantCommandShouldFailClosed() {
    ResolveAndMergeCommand c = cmd("M1", "P-1", "L-1", values1(), values2());
    c.setTenantId(2L);

    assertThatThrownBy(() -> service.resolveAndMerge(c))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("跨租户");
    assertZeroSideEffects();
  }

  @Test
  @DisplayName("无登录租户上下文：写与读都 fail-closed")
  void missingTenantContextShouldFailClosed() {
    currentUser.clearTenant();
    ResolveAndMergeCommand c = cmd("M1", "P-1", "L-1", values1(), values2());

    assertThatThrownBy(() -> service.resolveAndMerge(c)).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> service.currentForCaller("P-1")).isInstanceOf(DomainException.class);
    assertZeroSideEffects();
  }

  // ---------------------------------------------------------------- 回滚

  @Test
  @DisplayName("误合并整笔回滚：内容恢复、补偿版本递增、审计墓碑可追溯")
  void rollbackShouldRestoreContentWithMonotonicCompensationVersion() {
    service.resolveAndMerge(cmd("M1", "P-1", "L-1", values1(), values2()));
    GoldenRecord afterFirst = golden.findCurrent(1L, "P-1").orElseThrow();
    service.resolveAndMerge(cmd("M2", "P-1", "L-1", values1b(), values2()));

    GoldenRecordMergeResultDTO rb = service.rollback(rollback("M2", "错并供应商", "steward-01"));

    assertThat(rb.getOutcome()).isEqualTo("GOLDEN_RECORD_ROLLED_BACK");
    assertThat(rb.getVersion()).isEqualTo(3L);
    GoldenRecord restored = golden.findCurrent(1L, "P-1").orElseThrow();
    assertThat(restored.getVersion()).isEqualTo(3L);
    assertThat(restored.getValues()).isEqualTo(afterFirst.getValues()); // 内容完整恢复
    assertThat(restored.getProvenance().keySet()).isEqualTo(afterFirst.getProvenance().keySet());
    assertThat(restored.getProvenance("legal_name").getSourceRecordId())
        .isEqualTo(afterFirst.getProvenance("legal_name").getSourceRecordId());

    var audit = golden.findAudit(1L, "M2").orElseThrow();
    assertThat(audit.isRolledBack()).isTrue();
    assertThat(audit.getRollbackReason()).isEqualTo("错并供应商");
    assertThat(audit.getRolledBackBy()).isEqualTo("steward-01");

    MasterDataRecordDispatchedEvent compensation = outbox.pending().get(2);
    assertThat(compensation.getEventType()).isEqualTo("GOLDEN_RECORD_ROLLED_BACK");
    assertThat(compensation.getRecordVersion()).isEqualTo(3L);
    assertThat(compensation.isNewerThan(outbox.pending().get(1))).isTrue();
  }

  @Test
  @DisplayName("首次建记录被撤销：回到「无」，墓碑审计必须持久化（不物理删）")
  void rollbackOfFirstMergeShouldPersistTombstone() {
    service.resolveAndMerge(cmd("M1", "P-1", "L-1", values1(), values2()));
    service.rollback(rollback("M1", "主体识别错误", "steward-02"));

    assertThat(golden.findCurrent(1L, "P-1")).isEmpty();
    assertThat(golden.clearCount()).isEqualTo(1);
    var audit = golden.findAudit(1L, "M1").orElseThrow();
    assertThat(audit.isRolledBack()).isTrue();
    assertThat(audit.getRollbackReason()).isEqualTo("主体识别错误");
    assertThat(outbox.pending()).hasSize(2);
    assertThat(outbox.pending().get(1).getEventType()).isEqualTo("GOLDEN_RECORD_ROLLED_BACK");
  }

  @Test
  @DisplayName("重复回滚被拒绝，且不再追加补偿事件")
  void repeatedRollbackShouldBeRejected() {
    service.resolveAndMerge(cmd("M1", "P-1", "L-1", values1(), values2()));
    service.rollback(rollback("M1", "误合并", "steward-01"));
    int eventsAfterFirst = outbox.pending().size();

    assertThatThrownBy(() -> service.rollback(rollback("M1", "再撤一次", "steward-02")))
        .isInstanceOf(DomainException.class);
    assertThat(outbox.pending()).hasSize(eventsAfterFirst);
  }

  @Test
  @DisplayName("并发写入后回滚旧审计：防覆盖式回滚")
  void rollbackAfterConcurrentWriteShouldBeRejected() {
    service.resolveAndMerge(cmd("M1", "P-1", "L-1", values1(), values2()));
    service.resolveAndMerge(cmd("M2", "P-1", "L-1", values1b(), values2()));

    assertThatThrownBy(() -> service.rollback(rollback("M1", "旧审计", "steward-01")))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("禁止覆盖式回滚");
  }

  @Test
  @DisplayName("回滚命令缺原因或操作人 fail-closed，零补偿事件")
  void incompleteRollbackCommandShouldFailClosed() {
    service.resolveAndMerge(cmd("M1", "P-1", "L-1", values1(), values2()));
    RollbackMergeCommand c = rollback("M1", " ", "steward-01");

    assertThatThrownBy(() -> service.rollback(c)).isInstanceOf(DomainException.class);
    assertThat(outbox.pending()).hasSize(1);
    assertThat(golden.findAudit(1L, "M1").orElseThrow().isRolledBack()).isFalse();
  }

  // ---------------------------------------------------------------- PII / HMAC

  @Test
  @DisplayName("摘要为密钥参与 HMAC：不同平台密钥产生不同摘要，且同一内容确定性可复算")
  void payloadHashShouldBeKeyedAndDeterministic() {
    ResolveAndMergeCommand c = cmd("M1", "P-1", "L-1", values1(), values2());
    String h1 = service.resolveAndMerge(c).getPayloadHash();

    golden.reset();
    xref.reset();
    outbox.reset();
    GoldenRecordOrchestrationService other = newService("another-platform-pepper-2026-11-13");
    String h2 =
        other.resolveAndMerge(cmd("M1", "P-1", "L-1", values1(), values2())).getPayloadHash();

    assertThat(h1).isNotEqualTo(h2).hasSize(64);
    assertThat(h1).doesNotContain(PHONE, USCC, NAME_A);

    golden.reset();
    xref.reset();
    outbox.reset();
    assertThat(
            service.resolveAndMerge(cmd("M1", "P-1", "L-1", values1(), values2())).getPayloadHash())
        .isEqualTo(h1);
  }

  @Test
  @DisplayName("摘要密钥缺失/过短时拒绝装配（fail-closed）")
  void missingPepperShouldFailClosed() {
    assertThatThrownBy(() -> newService("short"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("摘要密钥");
  }

  // ---------------------------------------------------------------- helpers

  private void assertZeroSideEffects() {
    assertThat(golden.appendCount()).isZero();
    assertThat(golden.auditCount()).isZero();
    assertThat(golden.findCurrent(1L, "P-1")).isEmpty();
    assertThat(xref.registerCount()).isZero();
    assertThat(outbox.pending()).isEmpty();
  }

  private static ResolveAndMergeCommand cmd(
      String mergeId,
      String partyId,
      String localId,
      Map<String, String> v1,
      Map<String, String> v2) {
    ResolveAndMergeCommand c = new ResolveAndMergeCommand();
    c.setMergeId(mergeId);
    c.setTenantId(1L);
    c.setPartyId(partyId);
    c.setSourceSystem("ERP");
    c.setLocalId(localId);
    c.setMergedAt(T0);
    c.setSources(List.of(spec("S1", "ERP", 1, v1), spec("S2", "SRM", 2, v2)));
    return c;
  }

  private static ResolveAndMergeCommand.SourceRecordSpec spec(
      String recordId, String sourceSystem, int rank, Map<String, String> values) {
    ResolveAndMergeCommand.SourceRecordSpec s = new ResolveAndMergeCommand.SourceRecordSpec();
    s.setRecordId(recordId);
    s.setSourceSystem(sourceSystem);
    s.setTrustRank(rank);
    s.setObservedAt(T0.minusSeconds(10));
    s.setValues(new LinkedHashMap<>(values));
    return s;
  }

  private static RollbackMergeCommand rollback(String mergeId, String reason, String operator) {
    RollbackMergeCommand c = new RollbackMergeCommand();
    c.setTenantId(1L);
    c.setMergeId(mergeId);
    c.setReason(reason);
    c.setOperator(operator);
    return c;
  }

  private static Map<String, String> values1() {
    Map<String, String> v = new LinkedHashMap<>();
    v.put("legal_name", NAME_A);
    v.put("unified_social_credit_code", USCC);
    v.put("contact_phone", PHONE);
    return v;
  }

  private static Map<String, String> values1b() {
    Map<String, String> v = new LinkedHashMap<>(values1());
    v.put("legal_name", "上海某某科技（集团）有限公司");
    return v;
  }

  private static Map<String, String> values2() {
    Map<String, String> v = new LinkedHashMap<>();
    v.put("legal_name", "上海某某科技（集团）有限公司");
    v.put("city", "上海");
    return v;
  }
}
