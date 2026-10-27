package com.bone.synap.settlement.engine;

import com.bone.synap.settlement.Money;
import com.bone.synap.settlement.domain.AdjustmentLine;
import com.bone.synap.settlement.domain.ClaimContext;
import com.bone.synap.settlement.domain.FormulaSnapshot;
import com.bone.synap.settlement.domain.IndemnityCalculation;
import com.bone.synap.settlement.engine.strategy.Strategies;
import com.bone.synap.settlement.port.Ports;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 理算引擎门面（六边形核心，无 Spring/DB/网络）。
 *
 * 编排（《理算引擎内部设计》§2 七步 + §4 LimitControl + 两轮增强）：
 *   0 幂等 CAS / 风控 fail-closed → 0.5 重算冲正 → 1 C4 快照
 *   → 2 C2 求值(策略) → 3 第三方已付抵扣(不可获利) → 4 年度免赔(基数级)
 *   → 5 比例/理算免赔/取整/分位配平 → 6 多维度 min 控额(年度+责任+费用+计划)
 *   → 7 累计提交(乐观锁 + 按 claimId upsert) + 占额 + Outbox
 *
 * 理算顺序（业界通行：泰康/平安条款 + Guidewire CalculationEngine）：
 *   原始费用 → 目录筛选 → 发票层面免赔 → 第三方已付抵扣 → 年度免赔 → 赔付比例
 *   → 理算层面免赔 → 四舍五入(分位配平) → 多维度 min 控额
 * 关键语义：**免赔额作用于「费用基数」而非「赔付额」**（先免赔后比例）。
 *
 * 第二轮修复（诊断 N1–N10，均配回归开关）：
 *   N1  责任限额跨案累计（applyLiabilityAccumulation）—— 否则同责任多次理算各用满额，超赔；
 *   N2  重算冲正（applyReversal）—— 补交发票后旧占用不回退 → 额度账实不符且少赔；
 *   N3  预授权计入冻结额度（applyPreAuthFrozen）—— ACORD 三值额度 initial−claim−frozen；
 *   N4  预授权用剩余责任限额（useRemainingLiability）；
 *   N5  无可赔费用时授权 0（zeroAuthWhenAllRejected）；
 *   N6  拒赔行免赔额记 0（rejectRowZeroDeduct）—— 拒赔 ≠ 免赔；
 *   N7  幂等 CAS 租约（applyIdempotencyCAS）—— 消除 find→reserve→save 并发窗口；
 *   N8  冲正通道 reverse（撤件/后督改判可回退累计）；
 *   N9  最大余数法配平分位（apportionRounding）—— 逐行取整与总额口径必须守恒；
 *   N10 限额 0 = 未配置（zeroMeansUnlimited）—— 否则产品未配子限额即全额拒付。
 */
public final class SettlementEngine {

    public final Ports.LedgerPort ledger;
    public final Ports.TracePort trace;
    public final Ports.CatalogPort catalog;
    public final Ports.OutboxPort outbox;
    public final Ports.AccumulationPort accumulation;
    public final Ports.SettlementStorePort store;
    public final Ports.PreAuthPort preAuth;
    public final EngineFlags flags;

    public Money aiBonusPct = Money.of(10);

    /** 仅供 freezeSnapshot=false 的变种演示（P2）。生产代码不应持有可变 livePlan。 */
    public volatile FormulaSnapshot livePlan;

    /** authId -> 累计键（用于 capture/void 时释放冻结额度）。 */
    private final Map<String, String[]> authKeys = new HashMap<>();

    /**
     * 同保单同周期的理算/冲正串行化锁（单实例正确性护栏）。
     * 根因：累计的「读快照 → 计算额度 → 提交」若不在同一临界区内，并发两请求会读到同一
     * 「剩余额度」快照，各自算出额度后双双提交，导致年度/责任限额被突破（超赔）。
     * DB 乐观锁（version）只能防「丢失更新」，防不了「基于过期快照的重复占用」，
     * 故在此用 JVM 锁把整个临界区串行化；多实例部署须升级为分布式锁 + 提交时按 live 剩余再次钳制。
     */
    private final java.util.concurrent.ConcurrentHashMap<String, Object> policyLocks =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final Object NO_LOCK = new Object();

    public SettlementEngine(Ports.LedgerPort ledger, Ports.TracePort trace,
                            Ports.CatalogPort catalog, Ports.OutboxPort outbox,
                            Ports.AccumulationPort accumulation,
                            Ports.SettlementStorePort store, Ports.PreAuthPort preAuth) {
        this(ledger, trace, catalog, outbox, accumulation, store, preAuth, EngineFlags.allOn());
    }

    public SettlementEngine(Ports.LedgerPort ledger, Ports.TracePort trace,
                            Ports.CatalogPort catalog, Ports.OutboxPort outbox,
                            Ports.AccumulationPort accumulation,
                            Ports.SettlementStorePort store, Ports.PreAuthPort preAuth,
                            EngineFlags flags) {
        this.ledger = ledger; this.trace = trace; this.catalog = catalog; this.outbox = outbox;
        this.accumulation = accumulation; this.store = store; this.preAuth = preAuth;
        this.flags = flags == null ? EngineFlags.allOn() : flags;
    }

    public IndemnityCalculation settle(ClaimContext ctx, FormulaSnapshot frozen) {
        FormulaSnapshot snap = flags.freezeSnapshot ? frozen : (livePlan != null ? livePlan : frozen);

        // 幂等键：claimId + 公式指纹 + 输入指纹。输入变化（补交发票/AI 建议状态变化）视为新请求。
        String idemKey = ctx.claimId + "|" + snap.formulaHash() + "|" + ctx.inputHash();
        if (flags.applyIdempotency) {
            IndemnityCalculation prev = store.find(idemKey);
            if (prev != null) return prev; // 重复提交/重算：返回首次结论，不重复占额、不双计累计
            // N7：CAS 租约。并发两请求可同时通过 find(null)，无租约将各占额一次。
            if (flags.applyIdempotencyCAS && !store.tryBegin(idemKey)) {
                throw new ConcurrentSettlementException(ctx.claimId);
            }
        }

        String traceId = trace.record(snap, ctx.claimId);

        // ---- 0) 反欺诈 P0：fail-closed 挂起（不出金额、不占额度、不消费累计）----
        if (ctx.fraudHold) {
            String explain = "claim=" + ctx.claimId + " policy=" + ctx.policyId
                    + " status=HOLD\n  FRAUD_P0: 风控挂起，不产生可支付结论，转人工复核\n";
            IndemnityCalculation h = IndemnityCalculation.hold(ctx.claimId, ctx.tenantId, explain,
                    snap.snapshotVersion, traceId);
            outbox.publish(ctx.claimId, "FRAUD_HOLD:" + ctx.claimId);
            if (flags.applyIdempotency) store.save(idemKey, h);
            return h;
        }

        // ---- 并发安全临界区：同保单同周期的「读快照→计算额度→提交累计」原子化 ----
        // 防御并发双请求读到同一剩余额度快照后双双提交导致超赔（年度/责任限额被突破）。
        Object policyLock = (flags.applyAccumulation || flags.applyReversal)
                ? policyLocks.computeIfAbsent(tenantScoped(ctx) + ctx.policyId + "@" + ctx.periodKey, k -> new Object())
                : NO_LOCK;
        // 以下变量在临界区内赋值，但在区外（buildExplain/calc 创建）仍被使用，故提升为方法级作用域
        boolean denied;
        Money thirdApplied;
        List<AdjustmentLine> lines;
        boolean anyReject;
        Money gross, sumCalc;
        Money net;
        Money dedApplied;
        Money limApplied;
        Money liabApplied;
        synchronized (policyLock) {

        // ---- 0.5) 重算冲正（N2/N8）：同一 claimId 存在旧结论时，先回退其累计占用 ----
        if (flags.applyReversal) {
            IndemnityCalculation prev = store.findLatestByClaim(ctx.claimId);
            if (prev != null && prev.policyId != null && prev.periodKey != null) {
                accumulation.reverse(tenantScoped(ctx) + prev.policyId, prev.periodKey, ctx.claimId,
                        prev.annualDeductibleApplied, prev.annualLimitApplied, prev.liabilityApplied);
            }
        }

        denied = !ctx.denyReasons.isEmpty();

        // ---- 1~2) C2 求值：策略只产出「可赔基数 + 比例 + 理算层面免赔」----
        List<Strategies.RawLine> raws = select(snap.type)
                .compute(ctx, snap, flags.requireConfirm, flags.clampRatioAt100, aiBonusPct);
        int n = raws.size();
        Money[] base = new Money[n], origin = new Money[n], ratio = new Money[n], dedAdj = new Money[n];
        String[] reject = new String[n];
        String[] feeType = new String[n];
        for (int i = 0; i < n; i++) {
            Strategies.RawLine r = raws.get(i);
            feeType[i] = r.feeType; origin[i] = r.origin; base[i] = r.base;
            ratio[i] = r.ratioPct; dedAdj[i] = r.deductAdjust; reject[i] = r.rejectType;
        }

        // 租户作用域前缀（累计键/控额键全部租户隔离，ADR-4）
        String tScope = flags.tenantScopedKeys ? ctx.tenantId + "@" : "";
        boolean needAccum = flags.applyAccumulation
                && (ctx.annualDeductible.isPositive() || ctx.annualLimit.isPositive()
                    || snap.liabilityLimit.isPositive());
        Ports.AccUsage usage = needAccum
                ? accumulation.getUsage(tScope + ctx.policyId, ctx.periodKey)
                : Ports.AccUsage.empty();

        // ---- 3) 不可获利原则：第三方已付（社保统筹/其他保险）先从可赔基数扣除 ----
        thirdApplied = flags.applyIndemnityPrinciple
                ? allocateDeduction(base, reject, ctx.thirdPartyPaid)
                : Money.ZERO;

        // ---- 4) 年度免赔：作用于「费用基数」（先免赔后比例，非先比例后免赔）----
        dedApplied = Money.ZERO;
        if (needAccum && ctx.annualDeductible.isPositive() && flags.deductibleBeforeRatio) {
            Money rem = ctx.annualDeductible.subtract(usage.deductibleUsed).clampNonNegative();
            dedApplied = allocateDeduction(base, reject, rem);
        }

        // ---- 5) 比例 → 理算层面免赔 → 取整 → 分位配平（N9）----
        Money[] rawCalc = new Money[n];
        Money[] feeCalculate = new Money[n];
        for (int i = 0; i < n; i++) {
            if (reject[i] != null) { rawCalc[i] = Money.ZERO; feeCalculate[i] = Money.ZERO; continue; }
            Money raw = base[i].multiply(ratio[i].ratio()).subtract(dedAdj[i]).clampNonNegative();
            rawCalc[i] = raw;
            feeCalculate[i] = raw.rounded();
        }
        if (flags.apportionRounding) apportionRounding(rawCalc, feeCalculate);

        // ---- 6) 多维度 min 控额：责任 / 费用子限额 / 计划 / 年度限额 ----
        LimitControl lc = new LimitControl();
        String kL = "L:" + tScope + snap.liabilityId;
        String kP = "P:" + tScope + ctx.planId;
        // N10：限额为 0/非正 = 该维度未配置（不限制），绝不作为「限额为零」全额拒付
        if (snap.liabilityLimit.isPositive()) {
            Money liabRem = flags.applyLiabilityAccumulation
                    ? snap.liabilityLimit.subtract(usage.liabilityUsed).clampNonNegative()  // N1 责任限额跨案累计
                    : snap.liabilityLimit;
            // 注意：kL 已是「配置限额>0」才进入（上方 if），此处传入的是「剩余值」，剩余=0 表示已用尽，
            // 必须仍放入 lc 让 cap.min(0)=0 钳到 0，绝不能走 putLimit 的 zero-means-unlimited（会把用尽的限额误判成「未配置=不限」→ 超赔）。
            lc.put(kL, liabRem);
        }
        putLimit(lc, kP, ctx.planRemaining);
        if (snap.type == FormulaSnapshot.LiabilityType.REIMBURSEMENT) {
            for (FormulaSnapshot.FeeConfig fc : snap.feeConfigs.values())
                putLimit(lc, "F:" + tScope + fc.feeType, fc.feeSubLimit);
        }
        String kA = null;
        Money annualLimitRemaining = Money.ZERO;
        if (needAccum && ctx.annualLimit.isPositive()) {
            annualLimitRemaining = ctx.annualLimit.subtract(usage.limitUsed).clampNonNegative();
            kA = "A:" + tScope + ctx.policyId + "@" + ctx.periodKey;
            // 同上：kA 仅「配置限额>0」才进入，传入的是剩余值；剩余=0（已用尽）必须仍放入 lc，
            // 让 cap.min(0)=0 钳到 0。若改用 putLimit 会把剩余=0 误判成 zero-means-unlimited（不限）→ 超赔。
            lc.put(kA, annualLimitRemaining);
        }

        lines = new ArrayList<>();
        anyReject = false;
        for (int i = 0; i < n; i++) {
            boolean rejected = reject[i] != null;
            if (rejected) anyReject = true;
            Money cap = feeCalculate[i];
            if (rejected || denied) {
                cap = Money.ZERO;                       // 拒赔：不出金额、不占额度
            } else if (flags.enforceLimitMin) {
                String kF = "F:" + tScope + feeType[i];
                if (lc.contains(kL)) cap = cap.min(lc.remaining(kL));
                if (lc.contains(kF)) cap = cap.min(lc.remaining(kF));
                if (lc.contains(kP)) cap = cap.min(lc.remaining(kP));
                if (kA != null && lc.contains(kA)) cap = cap.min(lc.remaining(kA));
                if (lc.contains(kL)) lc.consume(kL, cap);
                if (lc.contains(kF)) lc.consume(kF, cap);
                if (lc.contains(kP)) lc.consume(kP, cap);
                if (kA != null && lc.contains(kA)) lc.consume(kA, cap);
            }
            Money frac = ratio[i].ratio();
            Money occupy = frac.isPositive() ? cap.divide(frac).min(origin[i]) : Money.ZERO;
            Money remain = origin[i].subtract(cap).clampNonNegative();
            // N6：拒赔 ≠ 免赔。拒赔行免赔额记 0，原因由 rejectType 单列，避免告知书语义误导。
            Money rawDeduct = origin[i].subtract(base[i]).add(dedAdj[i]); // 发票免赔+第三方+年免赔+理算免赔
            Money feeDeduct = (rejected && flags.rejectRowZeroDeduct) ? Money.ZERO : rawDeduct;
            lines.add(new AdjustmentLine(feeType[i], feeCalculate[i], ratio[i], cap,
                    feeDeduct, occupy, remain, reject[i]));
        }

        gross = Money.ZERO; sumCalc = Money.ZERO;
        for (AdjustmentLine l : lines) { gross = gross.add(l.feePayout); sumCalc = sumCalc.add(l.feeCalculate); }

        net = gross;
        // 变异分支（旧缺陷语义）：免赔额后置于赔付额 —— 仅用于回归探针证伪，生产恒为 true
        if (!flags.deductibleBeforeRatio && needAccum && ctx.annualDeductible.isPositive()) {
            Money rem = ctx.annualDeductible.subtract(usage.deductibleUsed).clampNonNegative();
            dedApplied = gross.min(rem);
            net = gross.subtract(dedApplied);
        }
        if (denied) net = Money.ZERO;

        limApplied = (kA != null && lc.contains(kA))
                ? annualLimitRemaining.subtract(lc.remaining(kA)) : Money.ZERO;
        liabApplied = lc.contains(kL)
                ? limitValueOf(snap, usage).subtract(lc.remaining(kL)) : Money.ZERO;

        // ---- 7) 累计提交（乐观锁 + 按 claimId upsert）：并发冲突 fail-closed ----
        if (!denied && needAccum
                && (dedApplied.isPositive() || limApplied.isPositive() || liabApplied.isPositive())) {
            Ports.CommitResult cr = accumulation.commitUsage(tScope + ctx.policyId, ctx.periodKey,
                    ctx.claimId, dedApplied, limApplied, liabApplied, usage.version);
            if (cr == Ports.CommitResult.CONFLICT) {
                throw new ConcurrentAccumulationException(ctx.policyId, ctx.periodKey, ctx.claimId);
            }
        }
        } // end synchronized(policyLock)

        ledger.reserve(ctx.tenantId, ctx.claimId, net);
        outbox.publish(ctx.claimId, denied ? "REJECTED:" + ctx.denyReasons : "SETTLED:" + snap.liabilityId);

        // 规则溯源
        Map<String, List<String>> ruleTrace = new LinkedHashMap<>();
        if (flags.recordTrace) {
            List<String> baseRules = snap.type == FormulaSnapshot.LiabilityType.REIMBURSEMENT
                    ? List.of("R-DED-01", "R-RAT-01", "R-CAP-01", "R-DIR-01")
                    : (snap.type == FormulaSnapshot.LiabilityType.FIXED
                        ? List.of("L-FIXED-01") : List.of("L-ALLOW-01"));
            for (AdjustmentLine l : lines) {
                if (l.rejectType != null) {
                    ruleTrace.put(l.feeType, List.of(l.rejectType));
                } else if (thirdApplied.isPositive()) {
                    List<String> withIndemnity = new ArrayList<>(baseRules);
                    withIndemnity.add("PRINCIPLE_INDEMNITY"); // 不可获利（非规则 ID，为原则标记）
                    ruleTrace.put(l.feeType, withIndemnity);
                } else {
                    ruleTrace.put(l.feeType, baseRules);
                }
            }
        }

        IndemnityCalculation.SettlementStatus status;
        if (denied) status = IndemnityCalculation.SettlementStatus.REJECT;
        else if (anyReject || net.cmp(sumCalc) < 0 || net.isZero())
            status = IndemnityCalculation.SettlementStatus.PARTIAL;
        else status = IndemnityCalculation.SettlementStatus.PAY;

        String explain = buildExplain(ctx, lines, ruleTrace, thirdApplied, dedApplied, limApplied, net, status, denied);

        IndemnityCalculation calc = IndemnityCalculation.of(ctx.claimId, ctx.tenantId,
                ctx.policyId, ctx.periodKey, lines, net,
                dedApplied, limApplied, liabApplied, ruleTrace, explain, snap.snapshotVersion, traceId,
                status, denied, ctx.denyReasons, thirdApplied);
        if (flags.applyIdempotency) store.save(idemKey, calc);
        return calc;
    }

    /** 撤件/后督改判：回退某案件的累计占用（N8，对应 Guidewire reversal）。 */
    public void reverseSettlement(IndemnityCalculation prev) {
        if (prev == null || prev.policyId == null) return;
        if (!flags.applyReversal) return;   // 变异开关：关闭即「无冲正通道」（N8）
        String key = tenantScopedOf(prev.tenantId) + prev.policyId + "@" + prev.periodKey;
        Object lock = policyLocks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            accumulation.reverse(tenantScopedOf(prev.tenantId) + prev.policyId, prev.periodKey, prev.claimId,
                    prev.annualDeductibleApplied, prev.annualLimitApplied, prev.liabilityApplied);
        }
    }

    // ---------- 直付预授权（亿保「智慧E保即时结算」） ----------

    public PreAuthResult preAuthorize(ClaimContext ctx, FormulaSnapshot snap, Money estimatedTotal) {
        return preAuthorize(ctx, snap, estimatedTotal, false, 0L);
    }

    /** 计算并冻结（hold）预授权额度，返回 authId；ttlMillis 到期自动释放。 */
    public PreAuthResult preAuthorizeAndHold(ClaimContext ctx, FormulaSnapshot snap,
                                             Money estimatedTotal, long ttlMillis) {
        return preAuthorize(ctx, snap, estimatedTotal, true, ttlMillis);
    }

    private PreAuthResult preAuthorize(ClaimContext ctx, FormulaSnapshot snap, Money estimatedTotal,
                                       boolean hold, long ttlMillis) {
        String tScope = flags.tenantScopedKeys ? ctx.tenantId + "@" : "";
        boolean needAccum = flags.applyAccumulation
                && (ctx.annualDeductible.isPositive() || ctx.annualLimit.isPositive()
                    || snap.liabilityLimit.isPositive());
        Ports.AccUsage u = needAccum
                ? accumulation.getUsage(tScope + ctx.policyId, ctx.periodKey)
                : Ports.AccUsage.empty();

        Money remainingDed = ctx.annualDeductible.isPositive()
                ? ctx.annualDeductible.subtract(u.deductibleUsed).clampNonNegative() : Money.ZERO;
        // N3：可用年限额必须扣除「已授权未结算」的冻结额（ACORD 三值额度：initial − claim − frozen）
        Money frozen = flags.applyPreAuthFrozen ? u.frozen : Money.ZERO;
        Money remainingLim = ctx.annualLimit.isPositive()
                ? ctx.annualLimit.subtract(u.limitUsed).subtract(frozen).clampNonNegative() : Money.ZERO;
        // N4：责任限额用「剩余」而非「总额」
        Money remainingLiab = snap.liabilityLimit.isPositive()
                ? (flags.useRemainingLiability
                    ? snap.liabilityLimit.subtract(u.liabilityUsed).clampNonNegative()
                    : snap.liabilityLimit)
                : Money.ZERO;

        Money authByDed = remainingDed.isPositive()
                ? estimatedTotal.subtract(remainingDed).clampNonNegative() : estimatedTotal;
        // 维度是否参与取交，取决于「该维度是否被配置」，而非「剩余是否为正」——
        // 剩余为 0 正是额度耗尽，必须把授权压到 0（否则回到 N3 超授权老路）。
        Money auth = authByDed;
        if (ctx.annualLimit.isPositive()) auth = auth.min(remainingLim);
        if (snap.liabilityLimit.isPositive()) auth = auth.min(remainingLiab);
        if (ctx.planRemaining.isPositive()) auth = auth.min(ctx.planRemaining);
        auth = auth.clampNonNegative();

        Money ratioPct = conservativeRatioPct(snap);
        Money authorized = auth.multiply(ratioPct.ratio()).rounded();

        String authId = null;
        if (hold) {
            authId = preAuth.hold(ctx.tenantId, ctx.claimId, authorized, ttlMillis);
            authKeys.put(authId, new String[]{tScope + ctx.policyId, ctx.periodKey});
            if (flags.applyPreAuthFrozen && authorized.isPositive()) {
                accumulation.commitFrozen(tScope + ctx.policyId, ctx.periodKey, authId, authorized);
            }
        }
        return new PreAuthResult(authId, authorized, remainingDed, remainingLim,
                remainingLiab, ctx.planRemaining, ratioPct);
    }

    /**
     * 保守授权比例：报销型取各**可赔**费用类型比例的最小值（最保守口径）。
     * N5：若不存在任何可赔费用（全部目录外/免责），授权比例必须为 0 —— 否则医院直付后患者面临全额追收。
     */
    private Money conservativeRatioPct(FormulaSnapshot snap) {
        if (snap.type != FormulaSnapshot.LiabilityType.REIMBURSEMENT || snap.feeConfigs.isEmpty())
            return Money.of(100);
        Money min = Money.of(100);
        boolean anyPayable = false;
        for (FormulaSnapshot.FeeConfig fc : snap.feeConfigs.values()) {
            if (fc.rejectType == null) {
                anyPayable = true;
                min = min.min(fc.payRatioPct.clampNonNegative());
            }
        }
        if (!anyPayable && flags.zeroAuthWhenAllRejected) return Money.ZERO;
        return min;
    }

    /** 预授权扣减（结算完成）：实际扣减 ≤ 授权额，剩余冻结额度自动释放。 */
    public Money capturePreAuth(String authId, Money amount) {
        Money taken = preAuth.capture(authId, amount);
        releaseFrozen(authId);
        return taken;
    }

    /** 预授权撤销（未结算/转自费）：释放冻结额度。 */
    public void voidPreAuth(String authId) {
        preAuth.voidAuth(authId);
        releaseFrozen(authId);
    }

    private void releaseFrozen(String authId) {
        String[] k = authKeys.remove(authId);
        if (k != null && flags.applyPreAuthFrozen) accumulation.releaseFrozen(k[0], k[1], authId);
    }

    // ---------- 工具 ----------

    private String tenantScoped(ClaimContext ctx) {
        return flags.tenantScopedKeys ? ctx.tenantId + "@" : "";
    }

    private String tenantScopedOf(String tenantId) {
        return flags.tenantScopedKeys ? tenantId + "@" : "";
    }

    /** N10：非正限额 = 该维度未配置，不进入控额维度（绝不作为「限额为零」）。 */
    private void putLimit(LimitControl lc, String key, Money value) {
        if (value == null) return;
        if (!flags.zeroMeansUnlimited) { lc.put(key, value); return; }
        if (value.isPositive()) lc.put(key, value);
    }

    private Money limitValueOf(FormulaSnapshot snap, Ports.AccUsage usage) {
        if (!snap.liabilityLimit.isPositive()) return Money.ZERO;
        return flags.applyLiabilityAccumulation
                ? snap.liabilityLimit.subtract(usage.liabilityUsed).clampNonNegative()
                : snap.liabilityLimit;
    }

    /**
     * 最大余数法配平分位（N9）：逐行四舍五入后合计必须等于「总额口径」的四舍五入结果，
     * 否则对账出现 1 分差（金融对账零容忍，SOX/NAIC 可复算要求）。
     */
    static void apportionRounding(Money[] raw, Money[] rounded) {
        int n = raw.length;
        if (n == 0) return;
        Money target = Money.ZERO;
        for (Money r : raw) target = target.add(r);
        target = target.rounded();
        Money sum = Money.ZERO;
        for (Money r : rounded) sum = sum.add(r);
        int cents = target.subtract(sum).cents();
        if (cents == 0) return;

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        final int sign = cents > 0 ? 1 : -1;
        // 需要加分 -> 优先给余数最大的行；需要减分 -> 优先从余数最小的行扣
        java.util.Arrays.sort(order, new Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) {
                Money ra = raw[a].subtract(rounded[a]);
                Money rb = raw[b].subtract(rounded[b]);
                int c = sign > 0 ? rb.cmp(ra) : ra.cmp(rb);
                return c != 0 ? c : Integer.compare(a, b);
            }
        });

        Money delta = Money.of("0.01");
        int steps = Math.min(Math.abs(cents), n * 100);
        for (int k = 0; k < steps; k++) {
            int i = order[k % n];
            rounded[i] = sign > 0 ? rounded[i].add(delta) : rounded[i].subtract(delta).clampNonNegative();
        }
    }

    /**
     * 按比例把一笔「抵扣额」（第三方已付 / 年度免赔额）分摊到各费用行的可赔基数上。
     * 末行取残差，保证分摊总量守恒（不因舍入丢分位）。
     */
    private static Money allocateDeduction(Money[] base, String[] reject, Money amount) {
        if (amount == null || amount.cmp(Money.ZERO) <= 0) return Money.ZERO;
        Money total = Money.ZERO;
        int lastPositive = -1;
        for (int i = 0; i < base.length; i++) {
            if (reject[i] == null && base[i].isPositive()) { total = total.add(base[i]); lastPositive = i; }
        }
        if (total.cmp(Money.ZERO) <= 0) return Money.ZERO;
        Money apply = amount.min(total);
        Money acc = Money.ZERO;
        for (int i = 0; i < base.length; i++) {
            if (reject[i] != null || !base[i].isPositive()) continue;
            Money alloc = (i == lastPositive) ? apply.subtract(acc) : apply.multiply(base[i]).divide(total);
            alloc = alloc.min(base[i]);
            base[i] = base[i].subtract(alloc);
            acc = acc.add(alloc);
        }
        return acc;
    }

    private String buildExplain(ClaimContext ctx, List<AdjustmentLine> lines,
                                Map<String, List<String>> ruleTrace, Money thirdApplied,
                                Money dedApplied, Money limApplied, Money net,
                                IndemnityCalculation.SettlementStatus status, boolean denied) {
        StringBuilder sb = new StringBuilder();
        sb.append("claim=").append(ctx.claimId)
          .append(" policy=").append(ctx.policyId)
          .append(" period=").append(ctx.periodKey)
          .append(" status=").append(status)
          .append("\n");
        if (denied) sb.append("  REJECT reasons=").append(ctx.denyReasons).append("\n");
        if (thirdApplied.isPositive())
            sb.append("  不可获利抵扣(社保统筹/第三方已付)=").append(thirdApplied).append("\n");
        for (AdjustmentLine l : lines) {
            List<String> rules = ruleTrace.getOrDefault(l.feeType, List.of());
            sb.append("  ").append(l.feeType)
              .append(": payout=").append(l.feePayout)
              .append(" ratio=").append(l.ratioPayout).append("%")
              .append(" deduct=").append(l.feeDeduct)
              .append(l.rejectType != null ? " REJECT(" + l.rejectType + ")" : "")
              .append(" rules=").append(rules).append("\n");
        }
        Money gross = Money.ZERO;
        for (AdjustmentLine l : lines) gross = gross.add(l.feePayout);
        sb.append("  gross=").append(gross)
          .append(" deductibleApplied=").append(dedApplied)
          .append(" limitApplied=").append(limApplied)
          .append(" net=").append(net).append("\n");
        return sb.toString();
    }

    private Strategies.LiabilityStrategy select(FormulaSnapshot.LiabilityType t) {
        switch (t) {
            case FIXED: return new Strategies.FixedStrategy();
            case ALLOWANCE: return new Strategies.AllowanceStrategy();
            case REIMBURSEMENT: return new Strategies.ReimbursementStrategy();
        }
        throw new IllegalArgumentException("unknown liability type: " + t);
    }

    /** 直付预授权结果（默认仅计算；hold=true 时带 authId 并冻结额度）。 */
    public static final class PreAuthResult {
        public final String authId;                 // 冻结凭据（未 hold 时为 null）
        public final Money authorizedAmount;        // 可授权直付额度（已按保守比例折算）
        public final Money remainingDeductible;     // 剩余年度免赔额
        public final Money remainingAnnualLimit;    // 剩余年度限额（已扣 frozen）
        public final Money liabilityLimitRemaining; // 剩余责任限额
        public final Money planRemaining;           // 计划维度剩余
        public final Money authRatioPct;            // 保守授权比例

        public PreAuthResult(String authId, Money authorizedAmount, Money remainingDeductible,
                             Money remainingAnnualLimit, Money liabilityLimitRemaining,
                             Money planRemaining, Money authRatioPct) {
            this.authId = authId;
            this.authorizedAmount = authorizedAmount;
            this.remainingDeductible = remainingDeductible;
            this.remainingAnnualLimit = remainingAnnualLimit;
            this.liabilityLimitRemaining = liabilityLimitRemaining;
            this.planRemaining = planRemaining;
            this.authRatioPct = authRatioPct;
        }

        @Override public String toString() {
            return "PreAuth{authId=" + authId + " authorized=" + authorizedAmount
                    + " ratio=" + authRatioPct + "% remainingDeductible=" + remainingDeductible
                    + " remainingAnnualLimit=" + remainingAnnualLimit
                    + " liabilityLimitRemaining=" + liabilityLimitRemaining
                    + " planRemaining=" + planRemaining + "}";
        }
    }

    /** 累计并发冲突：两笔同保单理算同时提交。fail-closed 转人工，严禁静默覆盖（额度双计）。 */
    public static final class ConcurrentAccumulationException extends RuntimeException {
        public ConcurrentAccumulationException(String policyId, String periodKey, String claimId) {
            super("跨案累计并发冲突（需重试/转人工）: policy=" + policyId + " period=" + periodKey + " claim=" + claimId);
        }
    }

    /** 幂等并发冲突：同一理算请求被并发提交。fail-closed，避免重复占额（N7）。 */
    public static final class ConcurrentSettlementException extends RuntimeException {
        public ConcurrentSettlementException(String claimId) {
            super("同一理算请求并发提交（幂等租约冲突，需返回在途结论或重试）: claim=" + claimId);
        }
    }
}
