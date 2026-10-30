package com.bone.claim.scenario;

import com.bone.claim.application.AutoApprovalApplicationService;
import com.bone.claim.application.ClaimCaseApplicationService;
import com.bone.claim.application.DecisionExecutionApplicationService;
import com.bone.claim.application.DecisionGraphExecutor;
import com.bone.claim.application.DecisionRunApplicationService;
import com.bone.claim.application.IndemnityApplicationService;
import com.bone.claim.application.QuotaApplicationService;
import com.bone.claim.application.SettlementApplicationService;
import com.bone.claim.application.handler.AdjustNodeHandler;
import com.bone.claim.application.handler.AiAssessNodeHandler;
import com.bone.claim.application.handler.ArbitrateNodeHandler;
import com.bone.claim.application.handler.EvidenceLoadNodeHandler;
import com.bone.claim.application.handler.RuleCheckNodeHandler;
import com.bone.claim.application.port.out.AccumulationPort;
import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.DecisionMetricsPort;
import com.bone.claim.application.port.out.DecisionNodeHandler;
import com.bone.claim.application.port.out.GraphFraudPort;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import com.bone.claim.application.port.out.NotificationPort;
import com.bone.claim.application.port.out.PolicyQueryPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.application.port.out.VisualForensicsPort;
import com.bone.claim.domain.model.adjustment.Adjustment;
import com.bone.claim.domain.model.adjustment.AdjustmentLine;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimItem;
import com.bone.claim.domain.model.claim.projection.ClaimHeadProjection;
import com.bone.claim.domain.model.claim.projection.ClaimTenantRef;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.model.decision.DecisionTraceNode;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import com.bone.claim.domain.model.evidence.valueobject.OcrStatus;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.quota.QuotaTransaction;
import com.bone.claim.domain.model.settlement.FundingEvent;
import com.bone.claim.domain.model.settlement.SettlementOrder;
import com.bone.claim.domain.repository.AdjustmentRepository;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimItemRepository;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.claim.domain.repository.LiabilityRepository;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import com.bone.claim.domain.repository.SettlementRepository;
import com.bone.claim.infrastructure.ai.LocalAiGatewayPortAdapter;
import com.bone.claim.infrastructure.notify.LocalNotificationPortAdapter;
import com.bone.claim.infrastructure.quota.QuotaDrivenAccumulationPortAdapter;
import com.bone.claim.infrastructure.rule.LocalRuleEnginePortAdapter;
import com.bone.core.domain.entity.Entity;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.model.PageParam;
import com.bone.core.model.PageResult;
import com.bone.core.model.Query;
import com.bone.core.model.QueryParam;
import com.bone.core.model.SortingField;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 智能理赔端到端场景验证基础设施。
 *
 * <p>项目刻意回避 Spring+H2 集成测试（SDK 仓储实现硬依赖宿主元数据栈，且 H2 集成脆弱）， 本类用<b>真实生产服务</b> + <b>内存仓储/端口替身</b>
 * 把全链路（报案 → 核赔决策 → 结案 → 结算 → 额度） 真跑一遍，模拟企业真实场景数据并验证「整体串联」。
 *
 * <p>内存仓储仅实现场景实际调用的方法；未调用的 SDK 抽象方法返回安全默认值，不参与断言。
 */
public class ScenarioHarness {

  public final String tenant = "tenant-e2e-demo";
  public final long tenantId = 1000L;

  // 可切换的反欺诈 / 知识图谱 行为（按场景设置）
  public boolean fraudHit = false;
  public boolean knowledgeReasonable = true;
  // 可切换的规则引擎可用性：默认 false（诚实本地桩，fail-closed 升级人工）；
  // 置 true 模拟「C2 规则引擎已生产部署」——自动通过 AUTO_APPROVE 以真实规则核责为前置。
  public boolean ruleEngineAvailable = false;

  public final InMemoryPolicyQueryPort policyPort = new InMemoryPolicyQueryPort();
  public final CapturingEventPublisher events = new CapturingEventPublisher();

  public final InMemoryClaimItemRepository itemRepo = new InMemoryClaimItemRepository();
  public final InMemoryClaimCaseRepository claimRepo;
  public final InMemoryAdjustmentRepository adjRepo = new InMemoryAdjustmentRepository();
  public final InMemoryLiabilityRepository liabRepo = new InMemoryLiabilityRepository();
  public final InMemoryEvidenceRepository eviRepo = new InMemoryEvidenceRepository();
  public final InMemoryDecisionExecutionRepository decRepo =
      new InMemoryDecisionExecutionRepository();
  public final InMemorySettlementRepository settleRepo = new InMemorySettlementRepository();
  public final InMemoryQuotaAccountRepository quotaRepo = new InMemoryQuotaAccountRepository();

  public final ScenarioContextPort contextPort = new ScenarioContextPort();

  public final ClaimCaseApplicationService claimSvc;
  public final IndemnityApplicationService indemnitySvc;
  public final DecisionExecutionApplicationService decExecSvc;
  public final DecisionGraphExecutor decisionExecutor;
  public final DecisionRunApplicationService decisionRun;
  public final AutoApprovalApplicationService autoApproveSvc;
  public final SettlementApplicationService settleSvc;
  public final QuotaApplicationService quotaSvc;

  public ScenarioHarness() {
    TenantPort tenantPort = () -> tenantId;
    claimRepo = new InMemoryClaimCaseRepository(itemRepo);
    claimSvc = new ClaimCaseApplicationService(claimRepo, itemRepo, policyPort, tenantPort, events);
    // 累计读侧用真实适配器（不是桩）：场景里 A7 占额写的行，A6 下一次理算就能读到 —— 跨案闭环必须走同一条读路径
    AccumulationPort accumulationPort = new QuotaDrivenAccumulationPortAdapter(quotaRepo);
    indemnitySvc =
        new IndemnityApplicationService(
            adjRepo, liabRepo, eviRepo, claimSvc, accumulationPort, tenantPort, events);
    decExecSvc = new DecisionExecutionApplicationService(decRepo, events);

    // 反欺诈 / 知识图谱 端口：默认「能力可用、无命中」，可按场景切换
    VisualForensicsPort visual =
        c -> new VisualForensicsPort.ForensicsResult(false, BigDecimal.ZERO, true);
    GraphFraudPort graph =
        c -> new GraphFraudPort.FraudResult(fraudHit, fraudHit ? "cluster-9" : null, true);
    KnowledgeRetrievalPort knowledge =
        c ->
            new KnowledgeRetrievalPort.KnowledgeResult(
                knowledgeReasonable, BigDecimal.ZERO, "integrated-E3", true);
    AiGatewayPort ai = new LocalAiGatewayPortAdapter();
    // 规则引擎端口：按 ruleEngineAvailable 动态决定「诚实本地桩(available=false, fail-closed)」
    // 还是「模拟已部署的真实引擎(available=true, 据证据核验结果诚实判定闸)」。
    RuleEnginePort rule =
        new RuleEnginePort() {
          private final LocalRuleEnginePortAdapter stub = new LocalRuleEnginePortAdapter();

          @Override
          public RuleCheckResult check(ClaimContextPort.ClaimContextView ctx) {
            if (ruleEngineAvailable) {
              return new RuleCheckResult(
                  true, false, false, ctx.evidenceAllVerifyPassed(), new BigDecimal("0.95"));
            }
            return stub.check(ctx);
          }

          @Override
          public BigDecimal calculatePayout(ClaimContextPort.ClaimContextView ctx) {
            return ctx.claimAmount();
          }
        };
    NotificationPort notif = new LocalNotificationPortAdapter();

    List<DecisionNodeHandler> handlers =
        List.of(
            new EvidenceLoadNodeHandler(contextPort),
            new RuleCheckNodeHandler(contextPort, rule, visual, graph),
            new AiAssessNodeHandler(contextPort, ai, knowledge, notif),
            new AdjustNodeHandler(contextPort, rule),
            new ArbitrateNodeHandler());
    DecisionMetricsPort metrics = DecisionMetricsPort.NOOP;
    decisionExecutor =
        new DecisionGraphExecutor(decExecSvc, claimSvc, tenantPort, metrics, handlers);

    quotaSvc = new QuotaApplicationService(quotaRepo);
    settleSvc =
        new SettlementApplicationService(
            settleRepo, quotaSvc, indemnitySvc, claimSvc, tenantPort, events);
    autoApproveSvc =
        new AutoApprovalApplicationService(claimSvc, settleSvc, indemnitySvc, decExecSvc);
    decisionRun = new DecisionRunApplicationService(decisionExecutor, autoApproveSvc);
  }

  // ===================== 种子数据助手（模拟企业真实配置/保单/额度） =====================

  /** 责任 UUID（种子责任配置与责任账分行共用，两处不同源即读写两本账）。 */
  public static final String LIABILITY_UUID = "LIAB-UUID-1";

  /** 种子责任配置：社保内、赔付比例 90%、免赔 0、年限额 10 万。 */
  public void seedLiability(Long planId) {
    liabRepo.insert(
        AdjLiability.of(
            1L,
            tenantId,
            LIABILITY_UUID,
            "v1",
            planId,
            "R01",
            "REIMBURSEMENT",
            new BigDecimal("90"),
            BigDecimal.ZERO,
            new BigDecimal("100000"),
            1,
            0,
            "ACTIVE"));
  }

  /**
   * 种子额度账户：个人账 + 责任账两本一起开。
   *
   * <p>为什么必须两本：{@code PERSONAL} 记"这个人今年还能赔多少"，{@code LIABILITY_CAP}（按 {@code liability_uuid}
   * 分行）记"这项责任今年还能赔多少"。只开个人账时 A6 理算读不到责任账（{@code accumulationAvailable=false}）， A7 结算会 fail-closed
   * 拒绝占额——这正是"账实不明不记账"的护栏，不是缺陷。
   */
  public void seedQuotaAccount(
      String insuredIdHash, String policyNo, Long planId, BigDecimal initial) {
    quotaRepo.insert(
        QuotaAccount.open(
            2L,
            tenantId,
            insuredIdHash,
            policyNo,
            planId,
            Year.now().getValue(),
            "PERSONAL",
            "",
            initial));
    quotaRepo.insert(
        QuotaAccount.open(
            3L,
            tenantId,
            insuredIdHash,
            policyNo,
            planId,
            Year.now().getValue(),
            "LIABILITY_CAP",
            LIABILITY_UUID,
            initial));
  }

  /** 种子保单快照：默认长期有效。 */
  public void seedPolicy(String policyNo, Long planId, String insuredIdHash) {
    policyPort.seed(
        policyNo,
        planId,
        insuredIdHash,
        Instant.parse("2020-01-01T00:00:00Z"),
        Instant.parse("2099-12-31T23:59:59Z"));
  }

  private long evidenceSeq = 5000L;

  /** 种子已解析+锁定证据（理算前置：证据须锁定）。 */
  public void seedEvidence(long claimId, boolean tampered) {
    Evidence e =
        Evidence.register(
            evidenceSeq++,
            tenantId,
            claimId,
            DocType.INVOICE,
            "file-ref-" + claimId,
            "content-hash-" + claimId,
            tampered ? "FAIL" : "PASS");
    e.markParsed(OcrStatus.DONE, new BigDecimal("0.95"), "{}", tampered ? "FAIL" : "PASS");
    e.lock("op-evidence", 1);
    eviRepo.insert(e);
  }

  /** 种子费用明细（与报案 DTO 同口径；社保内标记以满足社保内赔付责任）。 */
  public void seedClaimItems(long claimId, List<ItemSeed> items) {
    for (ItemSeed it : items) {
      ClaimItem item =
          ClaimItem.of(
              it.id,
              tenantId,
              claimId,
              it.name,
              it.code,
              it.category,
              it.quantity,
              it.unitPrice,
              it.amount);
      setPrivateField(item, "socialFlag", "IN");
      itemRepo.insert(item);
    }
  }

  /** 强制登记第一审核人（自动通过路径不经过核赔领取，补充 SoD 比较基准）。 */
  public void setClaimFirstReviewer(long claimId, String reviewer) {
    ClaimCase claim = claimRepo.findById(claimId);
    if (claim != null) {
      setPrivateField(claim, "firstReviewerId", reviewer);
    }
  }

  private static void setPrivateField(Object target, String name, Object value) {
    try {
      Field f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** 费用明细种子。 */
  public record ItemSeed(
      long id,
      String name,
      String code,
      ItemCategory category,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal amount) {}

  // ===================== 内存仓储基类 =====================

  static class InMemoryRepository<T extends Entity<Long>> implements Repository<T, Long> {
    protected final Map<Long, T> store = new LinkedHashMap<>();

    @Override
    public T findById(Long id) {
      return store.get(id);
    }

    @Override
    public T findByIdIncludingDeleted(Long id) {
      return store.get(id);
    }

    @Override
    public List<T> findByIds(List<Long> ids) {
      List<T> r = new ArrayList<>();
      for (Long id : ids) {
        T v = store.get(id);
        if (v != null) r.add(v);
      }
      return r;
    }

    @Override
    public List<T> findByIdsIncludingDeleted(List<Long> ids) {
      return findByIds(ids);
    }

    @Override
    public Long insert(T entity) {
      store.put(entity.getId(), entity);
      return entity.getId();
    }

    @Override
    public void batchInsert(List<T> entities) {
      for (T e : entities) store.put(e.getId(), e);
    }

    @Override
    public boolean update(T entity) {
      store.put(entity.getId(), entity);
      return true;
    }

    @Override
    public int updateByCriteria(T entity, Criteria<T> criteria) {
      store.put(entity.getId(), entity);
      return 1;
    }

    @Override
    public Long save(T entity) {
      store.put(entity.getId(), entity);
      return entity.getId();
    }

    @Override
    public void batchSave(List<T> entities) {
      for (T e : entities) store.put(e.getId(), e);
    }

    @Override
    public boolean deleteById(Long id) {
      return store.remove(id) != null;
    }

    @Override
    public void deleteByIds(List<Long> ids) {
      ids.forEach(store::remove);
    }

    @Override
    public int deleteByCriteria(Criteria<T> criteria) {
      int n = store.size();
      store.clear();
      return n;
    }

    @Override
    public List<T> findByCriteria(Criteria<T> criteria) {
      return new ArrayList<>(store.values());
    }

    @Override
    public T findOneByCriteria(Criteria<T> criteria) {
      return store.values().stream().findFirst().orElse(null);
    }

    @Override
    public PageResult<T> pageByCriteria(Criteria<T> criteria) {
      return PageResult.of(
          new ArrayList<>(store.values()), (long) store.size(), 1, Math.max(1, store.size()));
    }

    @Override
    public Long countByCriteria(Criteria<T> criteria) {
      return (long) store.size();
    }

    @Override
    public PageResult<T> queryByCondition(
        List<QueryParam> qp, List<SortingField> sf, Integer page, Integer size, String biz) {
      return PageResult.of(
          new ArrayList<>(store.values()),
          (long) store.size(),
          page == null ? 1 : page,
          size == null ? 10 : size);
    }

    @Override
    public List<T> query(Query query) {
      return new ArrayList<>(store.values());
    }

    @Override
    public PageResult<T> queryPage(PageParam pageParam) {
      int page = pageParam == null || pageParam.getPage() == null ? 1 : pageParam.getPage();
      int size = pageParam == null || pageParam.getSize() == null ? 10 : pageParam.getSize();
      return PageResult.of(new ArrayList<>(store.values()), (long) store.size(), page, size);
    }

    @Override
    public List<Map<String, Object>> aggregate(List<String> a, Criteria<T> c, List<String> g) {
      return List.of();
    }

    @Override
    public List<Map<String, Object>> aggregate(
        List<String> a, Criteria<T> c, List<String> g, List<String> h) {
      return List.of();
    }

    @Override
    public List<Map<String, Object>> aggregate(
        List<String> a, Criteria<T> c, List<String> g, List<String> h, boolean inc) {
      return List.of();
    }

    @Override
    public Map<String, Object> aggregate(List<String> a, Criteria<T> c) {
      return Map.of();
    }

    @Override
    public Map<String, Object> aggregate(List<String> a, Criteria<T> c, boolean inc) {
      return Map.of();
    }

    @Override
    public PageResult<Map<String, Object>> aggregateWithPagination(
        List<String> a, Criteria<T> c, List<String> g, List<String> h, int page, int size) {
      return PageResult.of(List.of(), 0L, page, size);
    }

    @Override
    public PageResult<Map<String, Object>> aggregateWithPagination(
        List<String> a,
        Criteria<T> c,
        List<String> g,
        List<String> h,
        int page,
        int size,
        boolean inc) {
      return PageResult.of(List.of(), 0L, page, size);
    }

    @Override
    public SqlExecutor getSqlExecutor() {
      return null;
    }
  }

  // ===================== 各聚合内存仓储 =====================

  static class InMemoryClaimCaseRepository extends InMemoryRepository<ClaimCase>
      implements ClaimCaseRepository {
    private final InMemoryClaimItemRepository itemRepo;

    InMemoryClaimCaseRepository(InMemoryClaimItemRepository itemRepo) {
      this.itemRepo = itemRepo;
    }

    @Override
    public List<ClaimWithItemsProjection> findClaimWithItems(long claimId) {
      ClaimCase c = store.get(claimId);
      if (c == null) return List.of();
      List<ClaimItem> items = itemRepo.findByClaimId(claimId);
      List<ClaimWithItemsProjection> rows = new ArrayList<>();
      for (ClaimItem it : items) {
        ClaimWithItemsProjection p = new ClaimWithItemsProjection();
        p.setClaimId(c.getId());
        p.setClaimNo(c.getClaimNo());
        p.setPolicyNo(c.getPolicyNo());
        p.setPlanId(c.getPlanId());
        p.setInsuredIdHash(c.getInsuredIdHash());
        p.setClaimType(c.getClaimType());
        p.setStatus(c.status().name());
        p.setStage(c.status().name());
        p.setHangUpStatus(0);
        p.setInvoiceCode(c.getInvoiceCode());
        p.setInvoiceNo(c.getInvoiceNo());
        p.setOutInsureTime(c.getOutInsureTime());
        p.setSourceChannel(c.getSourceChannel());
        p.setSourceSystem(c.getSourceSystem());
        p.setClaimAmount(c.getClaimAmount());
        p.setApprovedAmount(c.getApprovedAmount());
        p.setFirstReviewerId(c.getFirstReviewerId());
        p.setSecondReviewerId(c.getSecondReviewerId());
        p.setRejectComment(c.getRejectComment());
        p.setRejectType(c.getRejectType());
        p.setPlanVersion(c.getPlanVersion());
        p.setCreatedAt(c.getCreatedAt());
        p.setItemId(it.getId());
        p.setItemName(it.getItemName());
        p.setItemCode(it.getItemCode());
        p.setItemCategory(it.getCategory());
        p.setItemQuantity(it.getQuantity());
        p.setItemUnitPrice(it.getUnitPrice());
        p.setItemAmount(it.getAmount());
        p.setItemSocialFlag(it.getSocialFlag());
        p.setItemSelfPay1(it.getSelfPay1());
        p.setItemValidAmount(it.getValidAmount());
        p.setItemInvalidAmount(it.getInvalidAmount());
        rows.add(p);
      }
      return rows;
    }

    @Override
    public List<ClaimTenantRef> findStaleEvidenceClaimsAllTenants(java.sql.Timestamp before) {
      return List.of();
    }

    @Override
    public List<ClaimTenantRef> findFinishedForInspectionAllTenants(int limit) {
      return List.of();
    }

    @Override
    public List<ClaimTenantRef> findActiveClaimsAllTenants(int limit) {
      return List.of();
    }

    @Override
    public ClaimCase findByClaimNo(Long t, String claimNo) {
      return store.values().stream()
          .filter(c -> Objects.equals(c.getClaimNo(), claimNo))
          .findFirst()
          .orElse(null);
    }

    @Override
    public ClaimCase findByIdempotencyKey(Long t, String key) {
      return store.values().stream()
          .filter(c -> Objects.equals(c.getIdempotencyKey(), key))
          .findFirst()
          .orElse(null);
    }

    @Override
    public PageResult<ClaimHeadProjection> findClaimPage(
        Long t, List<ClaimStatus> statuses, String sourceChannel, int page, int size) {
      List<ClaimHeadProjection> list =
          store.values().stream()
              .filter(c -> statuses == null || statuses.isEmpty() || statuses.contains(c.status()))
              .map(ClaimHeadProjection::from)
              .toList();
      return PageResult.of(list, (long) list.size(), page, size);
    }
  }

  static class InMemoryClaimItemRepository extends InMemoryRepository<ClaimItem>
      implements ClaimItemRepository {
    @Override
    public List<ClaimItem> findByClaimId(long claimId) {
      return store.values().stream()
          .filter(it -> Objects.equals(it.getClaimId(), claimId))
          .toList();
    }
  }

  static class InMemoryAdjustmentRepository extends InMemoryRepository<Adjustment>
      implements AdjustmentRepository {
    @Override
    public Adjustment findById(long id) {
      return store.get(id);
    }

    @Override
    public Adjustment findLatestByClaimId(long claimId) {
      return store.values().stream()
          .filter(a -> Objects.equals(a.getClaimId(), claimId))
          .max(Comparator.comparingLong(Adjustment::getId))
          .orElse(null);
    }

    @Override
    public List<AdjustmentLine> findLinesByCalculationId(long id) {
      Adjustment a = store.get(id);
      return a == null ? List.of() : a.getLines();
    }
  }

  static class InMemoryLiabilityRepository extends InMemoryRepository<AdjLiability>
      implements LiabilityRepository {
    @Override
    public List<AdjLiability> findActiveByPlanId(long planId) {
      return store.values().stream()
          .filter(l -> Objects.equals(l.getPlanId(), planId) && l.isActive())
          .toList();
    }

    @Override
    public AdjLiability findByUuidAndVersion(String uuid, String version) {
      return store.values().stream()
          .filter(
              l ->
                  Objects.equals(l.getLiabilityUuid(), uuid)
                      && Objects.equals(l.getLiabilityVersion(), version))
          .findFirst()
          .orElse(null);
    }
  }

  static class InMemoryEvidenceRepository extends InMemoryRepository<Evidence>
      implements EvidenceRepository {
    @Override
    public Evidence findById(long id) {
      return store.get(id);
    }

    @Override
    public List<Evidence> findByClaimId(long claimId) {
      return store.values().stream().filter(e -> Objects.equals(e.getClaimId(), claimId)).toList();
    }
  }

  static class InMemoryDecisionExecutionRepository extends InMemoryRepository<DecisionExecution>
      implements DecisionExecutionRepository {
    @Override
    public DecisionExecution findActiveByClaimId(long claimId) {
      return store.values().stream()
          .filter(
              e ->
                  Objects.equals(e.getClaimId(), claimId)
                      && Integer.valueOf(1).equals(e.getActiveFlag()))
          .findFirst()
          .orElse(null);
    }

    @Override
    public DecisionExecution findLatestByClaimId(long claimId) {
      return store.values().stream()
          .filter(e -> Objects.equals(e.getClaimId(), claimId))
          .max(Comparator.comparingLong(DecisionExecution::getId))
          .orElse(null);
    }

    @Override
    public List<DecisionTraceNode> findTracesByTraceId(String traceId) {
      return List.of();
    }
  }

  static class InMemorySettlementRepository extends InMemoryRepository<SettlementOrder>
      implements SettlementRepository {
    @Override
    public SettlementOrder findLatestByClaimId(long claimId) {
      return store.values().stream()
          .filter(s -> Objects.equals(s.getClaimId(), claimId))
          .max(Comparator.comparingLong(SettlementOrder::getId))
          .orElse(null);
    }

    @Override
    public List<SettlementOrder> findByBizDate(LocalDate bizDate) {
      return List.of();
    }

    @Override
    public List<FundingEvent> findFundingEventsBySettlementId(long settlementId) {
      return List.of();
    }
  }

  static class InMemoryQuotaAccountRepository extends InMemoryRepository<QuotaAccount>
      implements QuotaAccountRepository {
    @Override
    public QuotaAccount findById(long id) {
      return store.get(id);
    }

    @Override
    public QuotaAccount findAccount(
        String insuredIdHash,
        String policyNo,
        long planId,
        int quotaYear,
        String quotaType,
        String liabilityUuid) {
      return store.values().stream()
          .filter(
              a ->
                  Objects.equals(a.getInsuredIdHash(), insuredIdHash)
                      && Objects.equals(a.getPolicyNo(), policyNo)
                      && Objects.equals(a.getPlanId(), planId)
                      && Objects.equals(a.getQuotaYear(), quotaYear)
                      && Objects.equals(a.getQuotaType(), quotaType)
                      && Objects.equals(a.getLiabilityUuid(), liabilityUuid))
          .findFirst()
          .orElse(null);
    }

    @Override
    public List<QuotaTransaction> findTransactions(long accountId) {
      QuotaAccount a = store.get(accountId);
      return a == null ? List.of() : a.getTransactions();
    }

    @Override
    public List<QuotaAccount> findAllAccounts() {
      return new ArrayList<>(store.values());
    }
  }

  // ===================== 端口替身 =====================

  static class InMemoryPolicyQueryPort implements PolicyQueryPort {
    private final Map<String, PolicySnapshot> map = new LinkedHashMap<>();

    void seed(String policyNo, Long planId, String insuredIdHash, Instant from, Instant to) {
      map.put(
          policyNo + "|" + planId,
          new PolicySnapshot(policyNo, planId, "v1", insuredIdHash, from, to));
    }

    @Override
    public Optional<PolicySnapshot> findActivePolicy(String policyNo, Long planId) {
      return Optional.ofNullable(map.get(policyNo + "|" + planId));
    }
  }

  static class CapturingEventPublisher implements DomainEventPublisher {
    public final List<Object> published = new ArrayList<>();

    @Override
    public void publish(com.bone.core.domain.DomainEvent event) {
      published.add(event);
    }

    @Override
    public void publishAll(
        java.util.Collection<? extends com.bone.core.domain.DomainEvent> events) {
      published.addAll(events);
    }
  }

  static class ScenarioContextPort implements ClaimContextPort {
    private final Map<Long, ClaimContextView> views = new LinkedHashMap<>();

    void set(long claimId, ClaimContextView view) {
      views.put(claimId, view);
    }

    @Override
    public ClaimContextView getContext(long claimId) {
      return views.getOrDefault(
          claimId,
          new ClaimContextView(
              claimId, "OUTPATIENT", BigDecimal.ZERO, false, false, BigDecimal.ZERO, false));
    }
  }
}
