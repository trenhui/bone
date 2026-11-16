package com.bone.masterdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.masterdata.application.MasterDataOutboxRelay.ReconciliationReport;
import com.bone.masterdata.application.MasterDataOutboxRelay.RelayResult;
import com.bone.masterdata.application.command.ApplyChangeRequestCommand;
import com.bone.masterdata.application.command.CreateChangeRequestCommand;
import com.bone.masterdata.application.command.DecideSegmentCommand;
import com.bone.masterdata.application.command.SubmitChangeRequestCommand;
import com.bone.masterdata.application.query.dto.ChangeRequestStateDTO;
import com.bone.masterdata.testsupport.changerequest.InMemoryChangeRequestAdapter;
import com.bone.masterdata.testsupport.changerequest.InMemoryMasterDataProjection;
import com.bone.masterdata.testsupport.changerequest.InMemoryOutboxRelayStore;
import com.bone.masterdata.testsupport.changerequest.RecordingChangeRequestActivationAdapter;
import com.bone.masterdata.testsupport.changerequest.RecordingChangeRequestWorkflowAdapter;
import com.bone.masterdata.testsupport.goldenrecord.TestCurrentUserPort;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分段 CR 端到端联调（纯 Java，手工装配终局端口夹具）： 创建 → 提交 → 三段并行裁决 → 应用 → Active 激活 → Outbox 事件 → 中继 → 消费侧投影 →
 * 版本对账。
 *
 * <p>与纯单测互补：这里证明「领域裁决真正传导到应用副作用」（未审批零激活、越权零落库、工作流接缝按序调用）， 而不是只测到返回值。
 */
class ChangeRequestIntegrationTest {

  private static final Instant T0 = Instant.parse("2026-11-13T02:00:00Z");
  private static final String CONSUMER = "procura-srm";
  private static final String CREATOR = "101";
  private static final String OWNER_GENERAL = "201";
  private static final String OWNER_CC = "202";
  private static final String OWNER_PO = "203";
  private static final String APPLIER = "301";

  private final TestCurrentUserPort user = new TestCurrentUserPort();
  private final InMemoryChangeRequestAdapter crPort = new InMemoryChangeRequestAdapter();
  private final InMemoryOutboxRelayStore outbox = new InMemoryOutboxRelayStore();
  private final InMemoryMasterDataProjection projection = new InMemoryMasterDataProjection();
  private final RecordingChangeRequestActivationAdapter activation =
      new RecordingChangeRequestActivationAdapter(outbox);
  private final RecordingChangeRequestWorkflowAdapter workflow =
      new RecordingChangeRequestWorkflowAdapter();
  private ChangeRequestApplicationService service;
  private MasterDataOutboxRelay relay;

  @BeforeEach
  void setUp() {
    crPort.reset();
    outbox.reset();
    projection.reset();
    activation.reset();
    workflow.reset();
    user.setTenantId(1L);
    user.setUserId(Long.valueOf(CREATOR));
    projection.authorize(1L, "P-1", CONSUMER);
    service = new ChangeRequestApplicationService(crPort, activation, workflow, user);
    relay = new MasterDataOutboxRelay(outbox, projection, CONSUMER, 1);
  }

  @Test
  @DisplayName("全链路：三段并行审批 → 应用 → 激活 → Outbox → 中继投影 → 对账一致")
  void fullChain() {
    assertThat(service.create(createCmd(Map.of())).getStatus()).isEqualTo("DRAFT");
    assertThat(service.submit(submitCmd(CREATOR)).getStatus()).isEqualTo("IN_REVIEW");
    approveAll();

    as(APPLIER);
    ChangeRequestStateDTO applied = service.apply(applyCmd(APPLIER));
    assertThat(applied.getStatus()).isEqualTo("APPLIED");
    assertThat(applied.isActivated()).isTrue();
    assertThat(applied.getDistinctApprovers()).isEqualTo(3);
    assertThat(applied.getSegments()).hasSize(3);
    assertThat(activation.activationCount()).isEqualTo(1);
    assertThat(outbox.size()).isEqualTo(1);

    RelayResult result = relay.relayBatch(10);
    assertThat(result.claimed()).isEqualTo(1);
    assertThat(result.delivered()).isEqualTo(1);
    assertThat(projection.projections()).hasSize(1);
    ReconciliationReport report = relay.reconcile(1L, "P-1");
    assertThat(report.consistent()).isTrue();
    assertThat(report.outboxMaxVersion()).isEqualTo(7L);
    assertThat(report.projectionVersion()).isEqualTo(7L);
  }

  @Test
  @DisplayName("工作流接缝按序调用：提交 → 三段并行裁决 → 应用终态")
  void workflowSeamCallSequence() {
    service.create(createCmd(Map.of()));
    service.submit(submitCmd(CREATOR));
    approveAll();
    as(APPLIER);
    service.apply(applyCmd(APPLIER));

    assertThat(workflow.calls())
        .containsExactly(
            "SUBMITTED:CR-1",
            "SEGMENT:GENERAL:APPROVED",
            "SEGMENT:COMPANY_CODE:APPROVED",
            "SEGMENT:PURCHASING_ORG:APPROVED",
            "APPLIED:CR-1");
  }

  @Test
  @DisplayName("N1：未齐三段即应用被拒，零激活零分发")
  void applyWithoutAllApprovalsHasZeroSideEffects() {
    service.create(createCmd(Map.of()));
    service.submit(submitCmd(CREATOR));
    as(OWNER_GENERAL);
    service.decide(decideCmd("GENERAL", true, OWNER_GENERAL, null));

    as(APPLIER);
    assertThatThrownBy(() -> service.apply(applyCmd(APPLIER)))
        .hasMessageContaining("未全部审批通过，禁止覆盖 Active 区");
    assertThat(activation.activationCount()).isZero();
    assertThat(outbox.size()).isZero();
    assertThat(crPort.saveCount()).isEqualTo(3); // create + submit + 一次合法裁决，失败的应用不得落库
  }

  @Test
  @DisplayName("N2：提交人自审被拒，且状态保持并行审批中")
  void selfApprovalBlocked() {
    service.create(createCmd(Map.of()));
    service.submit(submitCmd(CREATOR));
    as(CREATOR);
    assertThatThrownBy(() -> service.decide(decideCmd("GENERAL", true, CREATOR, null)))
        .hasMessageContaining("提交人不得自审");
    assertThat(service.currentForCaller("CR-1").orElseThrow().getStatus()).isEqualTo("IN_REVIEW");
  }

  @Test
  @DisplayName("分段 Owner 不得是提交人（否则该段永远无法合法审批）")
  void ownerMayNotBeCreator() {
    Map<String, String> owners = defaultOwners();
    owners.put("GENERAL", CREATOR);
    assertThatThrownBy(() -> service.create(createCmd(owners)))
        .hasMessageContaining("提交人不得兼任分段审批 Owner");
    assertThat(crPort.saveCount()).isZero();
  }

  @Test
  @DisplayName("缺一段路由 / 空 Owner 均拒绝创建")
  void incompleteRouteRejected() {
    Map<String, String> owners = defaultOwners();
    owners.remove("PURCHASING_ORG");
    assertThatThrownBy(() -> service.create(createCmd(owners)))
        .hasMessageContaining("三段 specialist 路由必须齐全");
    Map<String, String> blank = defaultOwners();
    blank.put("GENERAL", "");
    assertThatThrownBy(() -> service.create(createCmd(blank)))
        .hasMessageContaining("分段 Owner 不得为空");
  }

  @Test
  @DisplayName("No.1 SoD：应用人不得是提交人或任一审批人")
  void applySodViolations() {
    service.create(createCmd(Map.of()));
    service.submit(submitCmd(CREATOR));
    approveAll();
    as(CREATOR);
    assertThatThrownBy(() -> service.apply(applyCmd(CREATOR))).hasMessageContaining("SoD 违规");
    as(OWNER_GENERAL);
    assertThatThrownBy(() -> service.apply(applyCmd(OWNER_GENERAL))).hasMessageContaining("SoD 违规");
    assertThat(activation.activationCount()).isZero();
  }

  @Test
  @DisplayName("双人复核：同一人签完三段仍不得应用")
  void dualReviewRequired() {
    Map<String, String> owners = defaultOwners();
    owners.put("GENERAL", OWNER_GENERAL);
    owners.put("COMPANY_CODE", OWNER_GENERAL);
    owners.put("PURCHASING_ORG", OWNER_GENERAL);
    service.create(createCmd(owners));
    service.submit(submitCmd(CREATOR));
    as(OWNER_GENERAL);
    for (String s : List.of("GENERAL", "COMPANY_CODE", "PURCHASING_ORG")) {
      service.decide(decideCmd(s, true, OWNER_GENERAL, null));
    }
    as(APPLIER);
    assertThatThrownBy(() -> service.apply(applyCmd(APPLIER))).hasMessageContaining("双人复核未满足");
  }

  @Test
  @DisplayName("驳回后整笔不可应用，零激活零分发")
  void rejectedCrCannotBeApplied() {
    service.create(createCmd(Map.of()));
    service.submit(submitCmd(CREATOR));
    as(OWNER_GENERAL);
    ChangeRequestStateDTO rejected =
        service.decide(decideCmd("GENERAL", false, OWNER_GENERAL, "税号与营业执照不一致"));
    assertThat(rejected.getStatus()).isEqualTo("REJECTED");
    as(APPLIER);
    assertThatThrownBy(() -> service.apply(applyCmd(APPLIER))).hasMessageContaining("未全部审批通过");
    assertThat(activation.activationCount()).isZero();
  }

  @Test
  @DisplayName("跨租户 tenantId 与代签操作人 fail-closed，且零落库")
  void tenantAndActorBinding() {
    CreateChangeRequestCommand crossTenant = createCmd(Map.of());
    crossTenant.setTenantId(2L);
    assertThatThrownBy(() -> service.create(crossTenant))
        .hasMessageContaining("命令 tenantId 与登录租户不一致");

    CreateChangeRequestCommand spoof = createCmd(Map.of());
    spoof.setCreatedBy("999");
    assertThatThrownBy(() -> service.create(spoof)).hasMessageContaining("操作人必须与登录账号一致，禁止代签");
    assertThat(crPort.saveCount()).isZero();
  }

  @Test
  @DisplayName("只读态按登录租户隔离，且响应不含业务字段值/PII")
  void readIsTenantScopedAndDesensitized() {
    service.create(createCmd(Map.of()));
    ChangeRequestStateDTO state = service.currentForCaller("CR-1").orElseThrow();
    assertThat(state.toString()).doesNotContain("91310000MA1FL0AB2C", "13800000001");
    assertThat(state.getSegments().get("GENERAL").getAssignedOwner()).isEqualTo(OWNER_GENERAL);

    user.setTenantId(2L);
    assertThat(service.currentForCaller("CR-1")).isEmpty();
  }

  @Test
  @DisplayName("同一目标版本已有已应用 CR 后禁止并行重复发布")
  void duplicateAppliedVersionRejected() {
    service.create(createCmd(Map.of()));
    service.submit(submitCmd(CREATOR));
    approveAll();
    as(APPLIER);
    service.apply(applyCmd(APPLIER));

    as(CREATOR);
    CreateChangeRequestCommand duplicate = createCmd(Map.of());
    duplicate.setChangeRequestId("CR-2");
    assertThatThrownBy(() -> service.create(duplicate)).hasMessageContaining("同一目标版本已有已应用变更请求");
  }

  // ---------------------------------------------------------------- helpers

  private void approveAll() {
    for (String[] pair :
        List.of(
            new String[] {"GENERAL", OWNER_GENERAL},
            new String[] {"COMPANY_CODE", OWNER_CC},
            new String[] {"PURCHASING_ORG", OWNER_PO})) {
      as(pair[1]);
      service.decide(decideCmd(pair[0], true, pair[1], null));
    }
  }

  private void as(String actor) {
    user.setUserId(Long.valueOf(actor));
  }

  private static Map<String, String> defaultOwners() {
    Map<String, String> owners = new LinkedHashMap<>();
    owners.put("GENERAL", OWNER_GENERAL);
    owners.put("COMPANY_CODE", OWNER_CC);
    owners.put("PURCHASING_ORG", OWNER_PO);
    return owners;
  }

  private static CreateChangeRequestCommand createCmd(Map<String, String> override) {
    CreateChangeRequestCommand cmd = new CreateChangeRequestCommand();
    cmd.setChangeRequestId("CR-1");
    cmd.setTenantId(1L);
    cmd.setPartyId("P-1");
    cmd.setTargetVersion(7L);
    cmd.setChangeDescription("新增供应商开户行");
    cmd.setCreatedBy(CREATOR);
    cmd.setCreatedAt(T0);
    cmd.setSegmentOwners(override.isEmpty() ? defaultOwners() : override);
    return cmd;
  }

  private static SubmitChangeRequestCommand submitCmd(String operator) {
    SubmitChangeRequestCommand cmd = new SubmitChangeRequestCommand();
    cmd.setTenantId(1L);
    cmd.setChangeRequestId("CR-1");
    cmd.setOperator(operator);
    cmd.setOccurredAt(T0.plusSeconds(1));
    return cmd;
  }

  private static DecideSegmentCommand decideCmd(
      String segment, boolean approved, String approver, String reason) {
    DecideSegmentCommand cmd = new DecideSegmentCommand();
    cmd.setTenantId(1L);
    cmd.setChangeRequestId("CR-1");
    cmd.setSegment(segment);
    cmd.setApproved(approved);
    cmd.setApprover(approver);
    cmd.setReason(reason);
    cmd.setOccurredAt(T0.plusSeconds(2));
    return cmd;
  }

  private static ApplyChangeRequestCommand applyCmd(String applier) {
    ApplyChangeRequestCommand cmd = new ApplyChangeRequestCommand();
    cmd.setTenantId(1L);
    cmd.setChangeRequestId("CR-1");
    cmd.setApplier(applier);
    cmd.setOccurredAt(T0.plusSeconds(9));
    return cmd;
  }
}
