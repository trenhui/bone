package com.bone.masterdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import com.bone.core.model.PageResult;
import com.bone.masterdata.application.command.RegisterSupplierCommand;
import com.bone.masterdata.application.command.TransitionSupplierCommand;
import com.bone.masterdata.application.command.TransitionVendorCommand;
import com.bone.masterdata.application.query.dto.SupplierStateDTO;
import com.bone.masterdata.application.query.dto.SupplierTransitionResultDTO;
import com.bone.masterdata.application.query.qry.SupplierPageQuery;
import com.bone.masterdata.domain.model.supplier.Supplier;
import com.bone.masterdata.domain.model.supplier.SupplierEvent;
import com.bone.masterdata.domain.model.supplier.SupplierState;
import com.bone.masterdata.domain.model.supplier.SupplierStateChange;
import com.bone.masterdata.domain.model.supplier.TransitionCommandSource;
import com.bone.masterdata.domain.model.supplier.VendorState;
import com.bone.masterdata.testsupport.goldenrecord.InMemoryEntityKeyMappingAdapter;
import com.bone.masterdata.testsupport.goldenrecord.TestCurrentUserPort;
import com.bone.masterdata.testsupport.supplier.InMemoryLegalEntityRepositoryPort;
import com.bone.masterdata.testsupport.supplier.InMemoryPaymentBlockReleaseReview;
import com.bone.masterdata.testsupport.supplier.InMemorySupplierExitFacts;
import com.bone.masterdata.testsupport.supplier.InMemorySupplierOnboardingFacts;
import com.bone.masterdata.testsupport.supplier.InMemorySupplierRepositoryPort;
import com.bone.masterdata.testsupport.supplier.InMemoryVendorGateFacts;
import com.bone.masterdata.testsupport.supplier.InMemoryVendorRepositoryPort;
import com.bone.masterdata.testsupport.supplier.TestSupplierExitGuard;
import com.bone.masterdata.testsupport.supplier.TestSupplierOnboardingGuard;
import com.bone.masterdata.testsupport.supplier.TestVendorActivationGuard;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 应用用例的端到端行为：合并登记 / Supplier 迁移 / Vendor 迁移 / 状态查询 / 分页列举。
 *
 * <p>贯穿断言四条边界：
 *
 * <ol>
 *   <li><b>租户只认登录态</b>：命令自报不一致即拒且零副作用；
 *   <li><b>守卫事实只能来自端口</b>：命令里没有任何事实字段，负向用例只能改夹具，不能改请求；
 *   <li><b>本域事实自己点算</b>：退出要 Vendor 已关闭、准入要存在 ACTIVE_VENDOR，两条都由应用层查 Vendor 仓库；
 *   <li><b>越权放行付款必红</b>：域内来源的提起与解除都拒绝，外部来源但无人工复核留痕同样拒绝。
 * </ol>
 */
@DisplayName("供应商主数据应用用例：五个用例 + 守卫事实端口 + 租户 fail-closed")
class SupplierApplicationServiceTest {

  private static final long TENANT = 7L;
  private static final String PARTY = "PARTY-1";
  private static final String USCC = "91310000MA1FL0Q23X";
  private static final Instant AT = Instant.parse("2026-11-16T02:00:00Z");

  private InMemorySupplierRepositoryPort supplierPort;
  private InMemoryVendorRepositoryPort vendorPort;
  private InMemoryLegalEntityRepositoryPort legalEntityPort;
  private InMemoryEntityKeyMappingAdapter keyMappingPort;
  private TestCurrentUserPort currentUserPort;
  private InMemorySupplierExitFacts exitFacts;
  private InMemorySupplierOnboardingFacts onboardingFacts;
  private InMemoryVendorGateFacts vendorGateFacts;
  private InMemoryPaymentBlockReleaseReview releaseReview;
  private SupplierApplicationService service;

  @BeforeEach
  void setUp() {
    supplierPort = new InMemorySupplierRepositoryPort();
    vendorPort = new InMemoryVendorRepositoryPort();
    legalEntityPort = new InMemoryLegalEntityRepositoryPort();
    keyMappingPort = new InMemoryEntityKeyMappingAdapter();
    currentUserPort = new TestCurrentUserPort();
    currentUserPort.setTenantId(TENANT);
    exitFacts = new InMemorySupplierExitFacts();
    onboardingFacts = new InMemorySupplierOnboardingFacts();
    vendorGateFacts = new InMemoryVendorGateFacts();
    releaseReview = new InMemoryPaymentBlockReleaseReview();
    service =
        new SupplierApplicationService(
            supplierPort,
            vendorPort,
            legalEntityPort,
            keyMappingPort,
            currentUserPort,
            exitFacts,
            new TestSupplierExitGuard(),
            onboardingFacts,
            new TestSupplierOnboardingGuard(),
            vendorGateFacts,
            new TestVendorActivationGuard(),
            releaseReview);
  }

  @Test
  @DisplayName("完整生命周期：双角色各自推进，互不覆盖；退出要求 Vendor 已关闭且上游已结清")
  void fullLifecycleAdvancesBothRolesIndependently() {
    registeredSupplier();
    assertThat(supplierPort.findCurrent(TENANT, PARTY).orElseThrow().state())
        .isEqualTo(SupplierState.PROSPECT);
    assertThat(vendorPort.findCurrent(TENANT, PARTY).orElseThrow().state())
        .isEqualTo(VendorState.PENDING_VENDOR);

    // 财务侧先开通：此时 Supplier 仍是 PROSPECT —— 双状态机互不覆盖。
    vendorGateFacts.passAllGates();
    assertThat(vendorTransition("activation_passed").getToState()).isEqualTo("ACTIVE_VENDOR");
    assertThat(supplierPort.findCurrent(TENANT, PARTY).orElseThrow().state())
        .isEqualTo(SupplierState.PROSPECT);

    assertThat(transition("onboarding_submitted").getToState()).isEqualTo("ONBOARDING");
    onboardingFacts.approve();
    assertThat(transition("onboarding_approved").getToState()).isEqualTo("ACTIVE");

    // 退出前置：Vendor 仍未关闭 → 拒绝。
    assertThatThrownBy(() -> transition("exit_approved"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("OPEN_VENDOR_ONBOARDING");

    assertThat(vendorTransition("deactivate").getToState()).isEqualTo("INACTIVE_VENDOR");
    assertThat(transition("exit_approved").getToState()).isEqualTo("EXITED");
    assertThat(vendorPort.findCurrent(TENANT, PARTY).orElseThrow().state())
        .isEqualTo(VendorState.INACTIVE_VENDOR);
  }

  @Test
  @DisplayName("退出：上游存在未结 PO 时被拒，状态与写入次数均不变")
  void rejectsSupplierExitWhenPurchaseOrderOpen() {
    reachActiveSupplier();
    int before = supplierPort.saveCount();
    exitFacts.withOpenPurchaseOrders(1);

    assertThatThrownBy(() -> transition("exit_approved"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("OPEN_PURCHASE_ORDERS");

    assertThat(supplierPort.saveCount()).isEqualTo(before);
    assertThat(supplierPort.findCurrent(TENANT, PARTY).orElseThrow().state())
        .isEqualTo(SupplierState.ACTIVE);
  }

  @Test
  @DisplayName("退出：上游未清应付同样被拒（不能只看 PO 数量）")
  void rejectsSupplierExitWhenPayableOutstanding() {
    reachActiveSupplier();
    exitFacts.withPayableBalance(new BigDecimal("12800.00"));

    assertThatThrownBy(() -> transition("exit_approved"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("PAYABLE_BALANCE_UNSETTLED");
  }

  @Test
  @DisplayName("准入：没有 ACTIVE_VENDOR 时被拒（spec:54 第二项前置）")
  void rejectsOnboardingWithoutActiveVendor() {
    registeredSupplier();
    transition("onboarding_submitted");
    onboardingFacts.approve();
    // Vendor 仍在 PENDING_VENDOR：准入不得通过。
    assertThatThrownBy(() -> transition("onboarding_approved"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("NO_ACTIVE_VENDOR");
    assertThat(supplierPort.findCurrent(TENANT, PARTY).orElseThrow().state())
        .isEqualTo(SupplierState.ONBOARDING);
  }

  @Test
  @DisplayName("准入：变更请求未全段批准时被拒（spec:54 第一项前置）")
  void rejectsOnboardingWithoutApprovedChangeRequest() {
    registeredSupplier();
    vendorGateFacts.passAllGates();
    vendorTransition("activation_passed");
    transition("onboarding_submitted");
    // 刻意不 approve()：默认 fail-closed。
    assertThatThrownBy(() -> transition("onboarding_approved"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("CHANGE_REQUEST_NOT_FULLY_APPROVED");
  }

  @Test
  @DisplayName("Vendor 开通：闸门五项齐备才成功")
  void activatesVendorWhenGatePasses() {
    registeredSupplier();
    vendorGateFacts.passAllGates();

    SupplierTransitionResultDTO result = vendorTransition("activation_passed");

    assertThat(result.getToState()).isEqualTo("ACTIVE_VENDOR");
    assertThat(result.getBasis()).isEqualTo("ACTIVATION_GATE_PASSED");
  }

  @Test
  @DisplayName("Vendor 开通：缺任一项即拒，状态不变")
  void rejectsVendorActivationWhenGateIncomplete() {
    registeredSupplier();
    vendorGateFacts.passAllGates();
    vendorGateFacts.failBankVerification();

    assertThatThrownBy(() -> vendorTransition("activation_passed"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("BANK_NOT_VERIFIED");
    assertThat(vendorPort.findCurrent(TENANT, PARTY).orElseThrow().state())
        .isEqualTo(VendorState.PENDING_VENDOR);
  }

  @Test
  @DisplayName("Vendor 停用：有进行中 PO 且不允许存量走完时被拒")
  void rejectsVendorDeactivateWithInProgressPurchaseOrder() {
    registeredSupplier();
    vendorGateFacts.passAllGates();
    vendorTransition("activation_passed");
    vendorGateFacts.withInProgressPurchaseOrders(3, false);

    assertThatThrownBy(() -> vendorTransition("deactivate"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("IN_PROGRESS_PURCHASE_ORDERS");
  }

  @Test
  @DisplayName("付款冻结：域内来源提起与解除都被拒（D7 覆盖两个事件）")
  void rejectsDomainSourcePaymentBlockBothDirections() {
    registeredSupplier();
    vendorGateFacts.passAllGates();
    vendorTransition("activation_passed");

    assertThatThrownBy(() -> vendorTransition("payment_block_raised"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("外部财务事件");

    // 先由外部财务冻结，再由域内尝试解除。
    vendorTransitionFrom("payment_block_raised", "EXTERNAL_FINANCE");
    assertThatThrownBy(() -> vendorTransition("payment_block_released"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("外部财务事件");
    assertThat(vendorPort.findCurrent(TENANT, PARTY).orElseThrow().state())
        .isEqualTo(VendorState.PAYMENT_BLOCKED);
  }

  @Test
  @DisplayName("付款冻结：外部来源但无人工复核留痕仍被拒（spec:83 两条件）")
  void rejectsFinanceReleaseWithoutManualReviewTrace() {
    registeredSupplier();
    vendorGateFacts.passAllGates();
    vendorTransition("activation_passed");
    vendorTransitionAt("payment_block_raised", "EXTERNAL_FINANCE", AT);

    assertThatThrownBy(
            () ->
                vendorTransitionAt(
                    "payment_block_released", "EXTERNAL_FINANCE", AT.plusSeconds(60)))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("人工复核留痕");

    releaseReview.traceAt(AT.plusSeconds(120));
    assertThat(
            vendorTransitionAt("payment_block_released", "EXTERNAL_FINANCE", AT.plusSeconds(180))
                .getToState())
        .isEqualTo("ACTIVE_VENDOR");
  }

  @Test
  @DisplayName("留痕绑定本次冻结：上一轮复核不能放行新一轮解除（审查 I-4）")
  void releaseTraceFromPreviousCycleDoesNotAuthorizeNewBlock() {
    registeredSupplier();
    vendorGateFacts.passAllGates();
    vendorTransition("activation_passed");

    // 第一轮：冻结 → 复核 → 解除。
    vendorTransitionAt("payment_block_raised", "EXTERNAL_FINANCE", AT);
    releaseReview.traceAt(AT.plusSeconds(60));
    assertThat(
            vendorTransitionAt("payment_block_released", "EXTERNAL_FINANCE", AT.plusSeconds(120))
                .getToState())
        .isEqualTo("ACTIVE_VENDOR");

    // 第二轮冻结：上一轮的复核早于本次冻结，不得复用（否则一次复核永久放行所有后续解除）。
    vendorTransitionAt("payment_block_raised", "EXTERNAL_FINANCE", AT.plusSeconds(180));
    assertThatThrownBy(
            () ->
                vendorTransitionAt(
                    "payment_block_released", "EXTERNAL_FINANCE", AT.plusSeconds(240)))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("人工复核留痕");
    assertThat(vendorPort.findCurrent(TENANT, PARTY).orElseThrow().state())
        .isEqualTo(VendorState.PAYMENT_BLOCKED);

    // 补一次针对本次冻结的复核即可解除。
    releaseReview.traceAt(AT.plusSeconds(300));
    assertThat(
            vendorTransitionAt("payment_block_released", "EXTERNAL_FINANCE", AT.plusSeconds(360))
                .getToState())
        .isEqualTo("ACTIVE_VENDOR");
  }

  @Test
  @DisplayName("退出：未结合同同样被拒（四类前置各自具备否决能力）")
  void rejectsSupplierExitWhenContractOpen() {
    reachActiveSupplier();
    exitFacts.withOpenContracts(1);

    assertThatThrownBy(() -> transition("exit_approved"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("OPEN_CONTRACTS");
  }

  @Test
  @DisplayName("退出：未结 PO 数量未知（null）同样按未结处理（审查 IMPORTANT-4）")
  void treatsUnknownPurchaseOrdersAsOutstanding() {
    reachActiveSupplier();
    exitFacts.withUnknownPurchaseOrders();

    assertThatThrownBy(() -> transition("exit_approved"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("OPEN_PURCHASE_ORDERS");
  }

  @Test
  @DisplayName("分页：page/size 越界被夹紧而不是抛错，且不返回重复数据（补 review SUGGESTION）")
  void clampsPagingBoundaries() {
    for (int i = 0; i < 3; i++) {
      RegisterSupplierCommand cmd = registerCommand("MERGE-P" + i);
      cmd.setPartyId("PARTY-P" + i);
      cmd.setLocalId("V-P" + i);
      cmd.getSources()
          .get(0)
          .getValues()
          .put("unified_social_credit_code", "91310000MA1FL0P" + i + "X");
      assertThat(service.register(cmd).getOutcome()).isEqualTo("APPLIED");
    }

    SupplierPageQuery qry = new SupplierPageQuery();
    qry.setPage(0);
    qry.setSize(0);
    PageResult<SupplierStateDTO> clamped = service.pageSuppliers(qry);
    assertThat(clamped.getPage()).isEqualTo(1);
    assertThat(clamped.getSize()).isEqualTo(1);
    assertThat(clamped.getTotal()).isEqualTo(3L);

    // 超大 page 不得因 (page-1)*size 溢出而回落成第 1 页（那会让对账脚本把重复数据当新数据）。
    qry.setPage(Integer.MAX_VALUE);
    qry.setSize(200);
    PageResult<SupplierStateDTO> far = service.pageSuppliers(qry);
    assertThat(far.getList()).isEmpty();
    assertThat(far.getPage()).isGreaterThan(1);
  }

  @Test
  @DisplayName("退出：应付余额未知（null）按未结处理，不得当成已结清（审查 I-1）")
  void treatsUnknownPayableAsOutstanding() {
    reachActiveSupplier();
    exitFacts.withPayableBalance(null);

    assertThatThrownBy(() -> transition("exit_approved"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("PAYABLE_BALANCE_UNSETTLED");
    assertThat(supplierPort.findCurrent(TENANT, PARTY).orElseThrow().state())
        .isEqualTo(SupplierState.ACTIVE);
  }

  @Test
  @DisplayName("重复登记未带税号：沿用已落库税号而不是清空（审查 I-2）")
  void reregistrationKeepsStoredIdentityKeys() {
    RegisterSupplierCommand first = registerCommand("MERGE-ID-1");
    first.getSources().get(0).getValues().put("tax_id", "TAX-1");
    assertThat(service.register(first).getOutcome()).isEqualTo("APPLIED");

    RegisterSupplierCommand second = registerCommand("MERGE-ID-2");
    assertThat(service.register(second).getOutcome()).isEqualTo("APPLIED");

    assertThat(legalEntityPort.findByPartyId(TENANT, PARTY).orElseThrow().taxId())
        .isEqualTo("TAX-1");
  }

  @Test
  @DisplayName("身份主键被改写：转人工且不落库，旧号不被静默释放（审查 I-2）")
  void rejectsSilentIdentityKeyChange() {
    registeredSupplier();
    int legalWrites = legalEntityPort.saveCount();

    RegisterSupplierCommand changed = registerCommand("MERGE-ID-3");
    changed.getSources().get(0).getValues().put("unified_social_credit_code", "91310000MA1FL0Q23Z");
    var result = service.register(changed);

    assertThat(result.getOutcome()).isEqualTo(SupplierApplicationService.OUTCOME_MANUAL_REVIEW);
    assertThat(result.getReason()).contains("身份主键");
    assertThat(legalEntityPort.saveCount()).isEqualTo(legalWrites);
    assertThat(legalEntityPort.findByPartyId(TENANT, PARTY).orElseThrow().unifiedSocialCreditCode())
        .isEqualTo(USCC);
  }

  @Test
  @DisplayName("状态查询：只回裁决/版本/摘要，历史按上限截断")
  void returnsCurrentStateWithoutRawValues() {
    registeredSupplier();
    // 直接注入一段超长历史，验证响应侧截断（不让响应体变成审计线索旁路）。
    List<SupplierStateChange> longHistory = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      longHistory.add(
          new SupplierStateChange(
              SupplierState.PROSPECT,
              SupplierEvent.ONBOARDING_SUBMITTED,
              SupplierState.ONBOARDING,
              "HISTORY_BASIS_" + i,
              "user:99",
              AT));
    }
    supplierPort.save(
        Supplier.rehydrate(TENANT, PARTY, SupplierState.ONBOARDING, 26L, longHistory),
        supplierPort.findCurrent(TENANT, PARTY).orElseThrow().version());

    SupplierStateDTO state = service.currentState(PARTY).orElseThrow();

    assertThat(state.getPartyId()).isEqualTo(PARTY);
    assertThat(state.getSupplierState()).isEqualTo("ONBOARDING");
    assertThat(state.getSupplierHistory())
        .hasSize(SupplierApplicationService.MAX_HISTORY_IN_RESPONSE);
    assertThat(state.getVendorState()).isEqualTo("PENDING_VENDOR");
    // 历史摘要是「状态迁移 + 事件码」的固定形状：断言形状本身就证明了它不可能夹带字段原值，
    // 比 doesNotContain(USCC) 更强 —— 后者在字段从未进入摘要时永远成立（假绿）。
    assertThat(state.getSupplierHistory())
        .isNotEmpty()
        .allMatch(entry -> entry.matches("^[A-Z_]+->[A-Z_]+ \\([a-z_]+\\)$"));
  }

  @Test
  @DisplayName("状态查询：未知主体返回 empty 而不是异常")
  void returnsEmptyForUnknownParty() {
    assertThat(service.currentState("PARTY-NOT-EXIST")).isEmpty();
  }

  @Test
  @DisplayName("租户不一致：三个写用例全部 fail-closed，且零副作用")
  void rejectsTenantMismatchOnAllWriteUseCases() {
    int supplierWrites = supplierPort.saveCount();
    int vendorWrites = vendorPort.saveCount();

    RegisterSupplierCommand register = registerCommand("MERGE-T");
    register.setTenantId(TENANT + 1);
    assertThatThrownBy(() -> service.register(register))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("租户");

    TransitionSupplierCommand supplierCmd = supplierCommand("onboarding_submitted");
    supplierCmd.setTenantId(TENANT + 1);
    assertThatThrownBy(() -> service.transitionSupplier(supplierCmd))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("租户");

    TransitionVendorCommand vendorCmd = vendorCommand("activation_passed");
    vendorCmd.setTenantId(TENANT + 1);
    assertThatThrownBy(() -> service.transitionVendor(vendorCmd))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("租户");

    assertThat(supplierPort.saveCount()).isEqualTo(supplierWrites);
    assertThat(vendorPort.saveCount()).isEqualTo(vendorWrites);
    assertThat(legalEntityPort.saveCount()).isZero();
    assertThat(keyMappingPort.registerCount()).isZero();
  }

  @Test
  @DisplayName("未知事件码与不存在的合作资格均拒绝")
  void rejectsUnknownEventAndParty() {
    registeredSupplier();
    assertThatThrownBy(() -> transition("no_such_event"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("未知的供应商生命周期事件码");

    TransitionSupplierCommand cmd = supplierCommand("onboarding_submitted");
    cmd.setPartyId("PARTY-NOT-EXIST");
    assertThatThrownBy(() -> service.transitionSupplier(cmd))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("合作资格不存在");
  }

  @Test
  @DisplayName("候选源记录租户不一致：整笔拒绝且不静默过滤（spec:33-35）")
  void rejectsSourceRecordWithForeignTenant() {
    RegisterSupplierCommand cmd = registerCommand("MERGE-FOREIGN");
    cmd.getSources().get(0).setTenantId(TENANT + 5);

    assertThatThrownBy(() -> service.register(cmd))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("与命令租户不一致");

    assertThat(supplierPort.saveCount()).isZero();
    assertThat(vendorPort.saveCount()).isZero();
    assertThat(legalEntityPort.saveCount()).isZero();
    assertThat(keyMappingPort.registerCount()).isZero();
  }

  @Test
  @DisplayName("身份主键唯一性：同一统一社会信用代码被第二个 partyId 登记时转人工且零副作用")
  void rejectsSecondPartyWithSameUnifiedSocialCreditCode() {
    registeredSupplier();
    int legalWrites = legalEntityPort.saveCount();
    int supplierWritesBefore = supplierPort.saveCount();
    int vendorWritesBefore = vendorPort.saveCount();
    int xrefWritesBefore = keyMappingPort.registerCount();

    RegisterSupplierCommand second = registerCommand("MERGE-DUP");
    second.setPartyId("PARTY-2");
    second.setLocalId("V-2002");
    var result = service.register(second);

    assertThat(result.getOutcome()).isEqualTo(SupplierApplicationService.OUTCOME_MANUAL_REVIEW);
    assertThat(result.getReason()).contains("身份主键已被");
    // 零副作用：第二个 partyId 不落任何东西（供应商、Vendor、法人身份、xref 四处一起断言）。
    assertThat(supplierPort.findCurrent(TENANT, "PARTY-2")).isEmpty();
    assertThat(vendorPort.findCurrent(TENANT, "PARTY-2")).isEmpty();
    assertThat(legalEntityPort.findByPartyId(TENANT, "PARTY-2")).isEmpty();
    assertThat(legalEntityPort.saveCount()).isEqualTo(legalWrites);
    assertThat(supplierWritesBefore).isEqualTo(supplierPort.saveCount());
    assertThat(vendorWritesBefore).isEqualTo(vendorPort.saveCount());
    assertThat(xrefWritesBefore).isEqualTo(keyMappingPort.registerCount());
  }

  @Test
  @DisplayName("身份冲突不被自命中掩盖：USCC 是自己、税号属他人时仍转人工（spec:27）")
  void detectsTaxIdConflictHiddenBehindSelfUsccMatch() {
    registeredSupplier();

    RegisterSupplierCommand other = registerCommand("MERGE-OTHER");
    other.setPartyId("PARTY-2");
    other.setLocalId("V-2002");
    other.getSources().get(0).getValues().put("unified_social_credit_code", "91310000MA1FL0Q23Y");
    other.getSources().get(0).getValues().put("tax_id", "TAX-SHARED");
    assertThat(service.register(other).getOutcome()).isEqualTo("APPLIED");

    // PARTY-1 再次登记：USCC 命中自己（不是冲突），但税号属 PARTY-2 —— 只查 USCC 就返回会把这条漏掉。
    RegisterSupplierCommand self = registerCommand("MERGE-SELF");
    self.getSources().get(0).getValues().put("tax_id", "TAX-SHARED");
    int legalWrites = legalEntityPort.saveCount();

    var result = service.register(self);

    assertThat(result.getOutcome()).isEqualTo(SupplierApplicationService.OUTCOME_MANUAL_REVIEW);
    assertThat(result.getReason()).contains("PARTY-2");
    assertThat(legalEntityPort.saveCount()).isEqualTo(legalWrites);
  }

  @Test
  @DisplayName("登记缺少两项身份主键：fail-closed，零副作用（无法参与身份裁决的主体不得入册）")
  void rejectsRegistrationWithoutIdentityKeys() {
    RegisterSupplierCommand cmd = registerCommand("MERGE-NO-IDENTITY");
    cmd.getSources().get(0).getValues().remove("unified_social_credit_code");

    assertThatThrownBy(() -> service.register(cmd))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("身份主键");

    // 拒绝必须发生在登记 xref 与任何落库之前，否则会留下「映射已建但主体未成立」的半成品。
    assertThat(keyMappingPort.registerCount()).isZero();
    assertThat(supplierPort.saveCount()).isZero();
    assertThat(vendorPort.saveCount()).isZero();
    assertThat(legalEntityPort.saveCount()).isZero();
  }

  // ---------------------------------------------------------------- helpers

  private void registeredSupplier() {
    assertThat(service.register(registerCommand("MERGE-1")).getOutcome()).isEqualTo("APPLIED");
  }

  /** 走到 ACTIVE 且 Vendor 已开通（退出前置之一通过），供退出类用例复用。 */
  private void reachActiveSupplier() {
    registeredSupplier();
    vendorGateFacts.passAllGates();
    vendorTransition("activation_passed");
    transition("onboarding_submitted");
    onboardingFacts.approve();
    transition("onboarding_approved");
  }

  private SupplierTransitionResultDTO transition(String event) {
    return service.transitionSupplier(supplierCommand(event));
  }

  private SupplierTransitionResultDTO vendorTransition(String event) {
    return service.transitionVendor(vendorCommand(event));
  }

  private SupplierTransitionResultDTO vendorTransitionAt(String event, String source, Instant at) {
    TransitionVendorCommand cmd = vendorCommand(event);
    cmd.setSource(source);
    cmd.setOccurredAt(at);
    return service.transitionVendor(cmd);
  }

  private SupplierTransitionResultDTO vendorTransitionFrom(String event, String source) {
    TransitionVendorCommand cmd = vendorCommand(event);
    cmd.setSource(source);
    return service.transitionVendor(cmd);
  }

  private RegisterSupplierCommand registerCommand(String mergeId) {
    RegisterSupplierCommand cmd = new RegisterSupplierCommand();
    cmd.setTenantId(TENANT);
    cmd.setMergeId(mergeId);
    cmd.setPartyId(PARTY);
    cmd.setSourceSystem("SRM");
    cmd.setLocalId("V-1001");
    cmd.setMergedAt(AT);
    Map<String, String> values = new LinkedHashMap<>();
    values.put("unified_social_credit_code", USCC);
    values.put("supplier_name", "ACME");
    RegisterSupplierCommand.SourceRecordSpec spec = new RegisterSupplierCommand.SourceRecordSpec();
    spec.setRecordId("R1");
    spec.setSourceSystem("SRM");
    spec.setObservedAt(AT);
    spec.setValues(values);
    cmd.setSources(List.of(spec));
    return cmd;
  }

  private TransitionSupplierCommand supplierCommand(String event) {
    TransitionSupplierCommand cmd = new TransitionSupplierCommand();
    cmd.setTenantId(TENANT);
    cmd.setPartyId(PARTY);
    cmd.setEvent(event);
    cmd.setOccurredAt(AT);
    return cmd;
  }

  private TransitionVendorCommand vendorCommand(String event) {
    TransitionVendorCommand cmd = new TransitionVendorCommand();
    cmd.setTenantId(TENANT);
    cmd.setPartyId(PARTY);
    cmd.setEvent(event);
    cmd.setSource(TransitionCommandSource.DOMAIN.name());
    cmd.setOccurredAt(AT);
    return cmd;
  }
}
