package com.bone.synap.settlement.port;

import com.bone.synap.settlement.Money;
import com.bone.synap.settlement.domain.FormulaSnapshot;
import com.bone.synap.settlement.domain.IndemnityCalculation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 理算引擎出/入站端口（六边形防腐层）。
 * 对应《智能理算引擎设计》端口铁律：domain 只经 port 消费 bone-ai-*，禁止直接 import 实现。
 *
 *   LedgerPort         -> C11 信任额度（占额/查询剩余）
 *   TracePort          -> C4  快照审计（记录公式快照，可回放）
 *   CatalogPort        -> 目录编码解析（item -> catalogCode，由 C6 受控 RAG/主数据提供）
 *   OutboxPort         -> 领域事件（最终一致，对账/后督订阅）
 *   AccumulationPort   -> 跨案累计（年度免赔 / 年度限额 / 责任限额 + 预授权冻结）
 *                          **租户隔离 + 乐观锁版本 + 按 claimId upsert + 冲正 reverse**
 *   SettlementStorePort-> 幂等结果存储（CAS 租约 + 按 claimId 查最新结论，支撑重算冲正）
 *   PreAuthPort        -> 直付预授权生命周期（hold/capture/void/TTL），接 C10 通道
 *
 * 三值额度模型（ACORD / ISO）：可用 = initial − claim(已赔付) − frozen(预授权冻结)。
 * 预授权必须计入 frozen，否则连续多次授权可突破年限额（见诊断 N3）。
 */
public final class Ports {

    public interface LedgerPort {
        void reserve(String tenantId, String claimId, Money amount);
        Money remaining(String tenantId, String claimId);
    }

    public interface TracePort {
        String record(FormulaSnapshot snap, String claimId);
    }

    public interface CatalogPort {
        String resolve(String itemCode);
    }

    public interface OutboxPort {
        void publish(String claimId, String event);
    }

    // ---------- 跨案累计：租户隔离 + 乐观锁 + 按 claimId upsert + 冲正 ----------

    /** 保单+账期维度的累计用量快照（含版本号，用于乐观锁 CAS）。 */
    public static final class AccUsage {
        public final Money deductibleUsed;   // 已消抵年度免赔额
        public final Money limitUsed;        // 已占用年度限额
        public final Money liabilityUsed;    // 已占用责任限额（责任维度亦须跨案累计，诊断 N1）
        public final Money frozen;           // 预授权冻结中（三值模型，诊断 N3）
        public final long version;

        public AccUsage(Money deductibleUsed, Money limitUsed, Money liabilityUsed, Money frozen, long version) {
            this.deductibleUsed = deductibleUsed;
            this.limitUsed = limitUsed;
            this.liabilityUsed = liabilityUsed == null ? Money.ZERO : liabilityUsed;
            this.frozen = frozen == null ? Money.ZERO : frozen;
            this.version = version;
        }

        /** 兼容构造（无责任/冻结维度的历史用法）。 */
        public AccUsage(Money deductibleUsed, Money limitUsed, long version) {
            this(deductibleUsed, limitUsed, Money.ZERO, Money.ZERO, version);
        }

        public static AccUsage empty() {
            return new AccUsage(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, 0L);
        }
    }

    /** 提交结果：COMMITTED 成功；DUPLICATE 幂等重复（不双计）；CONFLICT 版本冲突（并发，须 fail-closed）。 */
    public enum CommitResult { COMMITTED, DUPLICATE, CONFLICT }

    public interface AccumulationPort {
        /** key 由调用方按「tenantId@policyId」构造（租户隔离，ADR-4）。 */
        AccUsage getUsage(String tenantScopedPolicyId, String periodKey);

        /**
         * 提交/更新本案件占用（**按 claimId upsert**）。
         * 语义：同一 claimId 再次提交（重算/补交发票）时，以新值替换旧值，而非累加——
         * 这是「重算后额度账实相符」的关键（诊断 N2：旧实现首案 6000、重算 8000 后累计仍 6000）。
         * 不同 claimId 且版本过期 -> CONFLICT（并发 fail-closed，严禁静默覆盖）。
         */
        CommitResult commitUsage(String tenantScopedPolicyId, String periodKey, String claimId,
                                 Money deductibleDelta, Money limitDelta, Money liabilityDelta,
                                 long expectedVersion);

        /** 冲正：回退某案件的累计占用（撤件 / 后督改判 / 重算前清账）。对应 Guidewire reversal。 */
        void reverse(String tenantScopedPolicyId, String periodKey, String claimId,
                     Money deductibleDelta, Money limitDelta, Money liabilityDelta);

        /** 预授权冻结（frozen 维度），防止多次授权突破年限额。 */
        CommitResult commitFrozen(String tenantScopedPolicyId, String periodKey, String authId, Money amount);

        /** 释放预授权冻结（void / capture 完成后）。 */
        void releaseFrozen(String tenantScopedPolicyId, String periodKey, String authId);
    }

    // ---------- 幂等存储 ----------

    public interface SettlementStorePort {
        IndemnityCalculation find(String idempotencyKey);
        void save(String idempotencyKey, IndemnityCalculation calc);

        /**
         * CAS 租约：并发下只有一个请求能取得同一幂等键的执行权。
         * 返回 false 表示已有在途请求 —— 引擎必须 fail-closed 抛异常，
         * 否则 find(null) 与 save 之间的窗口会导致重复占额（诊断 N7）。
         */
        boolean tryBegin(String idempotencyKey);

        /** 按 claimId 查该案件最近一次结论（重算前用于冲正旧占用）。 */
        IndemnityCalculation findLatestByClaim(String claimId);
    }

    // ---------- 直付预授权生命周期 ----------

    public interface PreAuthPort {
        /** 冻结额度（三值模型 frozen），返回 authId；amount 为授权上限，ttlMillis 到期自动释放。 */
        String hold(String tenantId, String claimId, Money amount, long ttlMillis);
        /** 结算扣减（frozen -> claim），返回实际扣减额（≤ 授权额），剩余自动释放。 */
        Money capture(String authId, Money amount);
        /** 撤销（未结算/患者转自费），释放冻结额度。 */
        void voidAuth(String authId);
        boolean isExpired(String authId);
        Money held(String authId);
    }

    /**
     * 事件真实投递通道（与 OutboxPort 区分：OutboxPort 仅把事件写入本地 outbox 表，
     * 本端口负责把事件真正发往消息中间件 / 对账 / 后督订阅方）。
     * 投递必须**幂等**：at-least-once 语义下同一 outbox 行可能被重复投递，调用方以
     * claimId + event 作为幂等键去重（下游消费亦须幂等）。
     */
    public interface EventPublisher {
        void publish(String claimId, String event);
    }

    /** 探针专用内存实现：记录各端口调用次数，证明引擎确实经 port 而非内联 Feign（P6）。 */
    public static final class InMemoryPorts implements LedgerPort, TracePort, CatalogPort, OutboxPort,
            AccumulationPort, SettlementStorePort, PreAuthPort {
        public int reserveCalls = 0;
        public int traceCalls = 0;
        public int catalogCalls = 0;
        public int publishCalls = 0;
        public int accumulationCalls = 0;
        public int captureCalls = 0;
        public int voidCalls = 0;
        public int reverseCalls = 0;
        public int frozenCalls = 0;
        private int traceSeq = 0;
        private int authSeq = 0;

        private final Map<String, AccUsage> acc = new HashMap<>();
        private final Map<String, Money[]> claimUsage = new HashMap<>();  // key@claimId -> 上次占用
        private final Map<String, Money> frozenByAuth = new HashMap<>();  // key@authId    -> 冻结额
        private final Map<String, IndemnityCalculation> store = new LinkedHashMap<>();
        private final Map<String, String> latestByClaim = new HashMap<>();
        private final Map<String, String> leases = new HashMap<>();
        private final Map<String, AuthRec> auths = new HashMap<>();

        @Override public void reserve(String tenantId, String claimId, Money amount) { reserveCalls++; }
        @Override public Money remaining(String tenantId, String claimId) { return Money.ZERO; }
        @Override public String record(FormulaSnapshot snap, String claimId) { traceCalls++; return "TRACE-" + (++traceSeq); }
        @Override public String resolve(String itemCode) { catalogCalls++; return "CAT:" + itemCode; }
        @Override public void publish(String claimId, String event) { publishCalls++; }

        @Override
        public AccUsage getUsage(String tenantScopedPolicyId, String periodKey) {
            accumulationCalls++;
            return acc.getOrDefault(tenantScopedPolicyId + "@" + periodKey, AccUsage.empty());
        }

        @Override
        public CommitResult commitUsage(String tenantScopedPolicyId, String periodKey, String claimId,
                                        Money deductibleDelta, Money limitDelta, Money liabilityDelta,
                                        long expectedVersion) {
            accumulationCalls++;
            String k = tenantScopedPolicyId + "@" + periodKey;
            String ck = k + "#" + claimId;
            AccUsage cur = acc.getOrDefault(k, AccUsage.empty());
            Money d = nz(deductibleDelta), l = nz(limitDelta), li = nz(liabilityDelta);

            Money[] old = claimUsage.get(ck);
            if (old == null) {
                // 新案件：版本必须匹配，否则并发冲突 fail-closed
                if (cur.version != expectedVersion) return CommitResult.CONFLICT;
                acc.put(k, new AccUsage(cur.deductibleUsed.add(d), cur.limitUsed.add(l),
                        cur.liabilityUsed.add(li), cur.frozen, cur.version + 1));
                claimUsage.put(ck, new Money[]{d, l, li});
                return CommitResult.COMMITTED;
            }
            // 同案件重算：以新值替换旧值（upsert），绝不累加
            if (old[0].cmp(d) == 0 && old[1].cmp(l) == 0 && old[2].cmp(li) == 0) {
                return CommitResult.DUPLICATE;
            }
            AccUsage base = new AccUsage(cur.deductibleUsed.subtract(old[0]).clampNonNegative(),
                    cur.limitUsed.subtract(old[1]).clampNonNegative(),
                    cur.liabilityUsed.subtract(old[2]).clampNonNegative(),
                    cur.frozen, cur.version);
            acc.put(k, new AccUsage(base.deductibleUsed.add(d), base.limitUsed.add(l),
                    base.liabilityUsed.add(li), base.frozen, base.version + 1));
            claimUsage.put(ck, new Money[]{d, l, li});
            return CommitResult.COMMITTED;
        }

        @Override
        public void reverse(String tenantScopedPolicyId, String periodKey, String claimId,
                            Money deductibleDelta, Money limitDelta, Money liabilityDelta) {
            reverseCalls++;
            String k = tenantScopedPolicyId + "@" + periodKey;
            String ck = k + "#" + claimId;
            AccUsage cur = acc.getOrDefault(k, AccUsage.empty());
            Money d = nz(deductibleDelta), l = nz(limitDelta), li = nz(liabilityDelta);
            acc.put(k, new AccUsage(cur.deductibleUsed.subtract(d).clampNonNegative(),
                    cur.limitUsed.subtract(l).clampNonNegative(),
                    cur.liabilityUsed.subtract(li).clampNonNegative(),
                    cur.frozen, cur.version + 1));
            claimUsage.remove(ck);
        }

        @Override
        public CommitResult commitFrozen(String tenantScopedPolicyId, String periodKey, String authId, Money amount) {
            frozenCalls++;
            String k = tenantScopedPolicyId + "@" + periodKey;
            if (frozenByAuth.containsKey(k + "#" + authId)) return CommitResult.DUPLICATE;
            AccUsage cur = acc.getOrDefault(k, AccUsage.empty());
            frozenByAuth.put(k + "#" + authId, nz(amount));
            acc.put(k, new AccUsage(cur.deductibleUsed, cur.limitUsed, cur.liabilityUsed,
                    cur.frozen.add(nz(amount)), cur.version + 1));
            return CommitResult.COMMITTED;
        }

        @Override
        public void releaseFrozen(String tenantScopedPolicyId, String periodKey, String authId) {
            String k = tenantScopedPolicyId + "@" + periodKey;
            Money prev = frozenByAuth.remove(k + "#" + authId);
            if (prev == null) return;
            AccUsage cur = acc.getOrDefault(k, AccUsage.empty());
            acc.put(k, new AccUsage(cur.deductibleUsed, cur.limitUsed, cur.liabilityUsed,
                    cur.frozen.subtract(prev).clampNonNegative(), cur.version + 1));
        }

        @Override public IndemnityCalculation find(String idempotencyKey) { return store.get(idempotencyKey); }

        @Override public void save(String idempotencyKey, IndemnityCalculation calc) {
            store.put(idempotencyKey, calc);
            if (calc != null) latestByClaim.put(calc.claimId, idempotencyKey);
        }

        @Override public boolean tryBegin(String idempotencyKey) {
            return leases.putIfAbsent(idempotencyKey, "LEASE") == null;
        }

        @Override public IndemnityCalculation findLatestByClaim(String claimId) {
            String k = latestByClaim.get(claimId);
            return k == null ? null : store.get(k);
        }

        @Override public String hold(String tenantId, String claimId, Money amount, long ttlMillis) {
            String id = "AUTH-" + (++authSeq);
            auths.put(id, new AuthRec(amount, System.currentTimeMillis() + ttlMillis));
            return id;
        }
        @Override public Money capture(String authId, Money amount) {
            captureCalls++;
            AuthRec r = auths.get(authId);
            if (r == null || r.voided || System.currentTimeMillis() > r.expiry) return Money.ZERO;
            Money taken = amount.min(r.amount);
            r.amount = Money.ZERO; // capture 后剩余冻结额度自动释放（ACORD/ISO 预授权完成语义）
            return taken;
        }
        @Override public void voidAuth(String authId) {
            voidCalls++;
            AuthRec r = auths.get(authId);
            if (r != null) { r.voided = true; r.amount = Money.ZERO; }
        }
        @Override public boolean isExpired(String authId) {
            AuthRec r = auths.get(authId);
            return r == null || r.voided || System.currentTimeMillis() > r.expiry;
        }
        @Override public Money held(String authId) {
            AuthRec r = auths.get(authId);
            return r == null || r.voided ? Money.ZERO : r.amount;
        }

        static Money nz(Money m) { return m == null ? Money.ZERO : m; }

        static final class AuthRec {
            Money amount;
            final long expiry;
            boolean voided = false;
            AuthRec(Money amount, long expiry) { this.amount = amount; this.expiry = expiry; }
        }
    }
}
