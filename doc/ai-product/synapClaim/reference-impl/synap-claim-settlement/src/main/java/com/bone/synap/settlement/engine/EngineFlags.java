package com.bone.synap.settlement.engine;

/**
 * 引擎开关（同时承担「变异对照」职责：每个开关关闭 = 一个已知缺陷的回归变种）。
 *
 * 设计原则（《内部设计》§5 防假绿）：每新增一条护栏，必须配套一个可关闭它的开关，
 * 并有一条探针在开关关闭时**必然转红**——否则该护栏只是文档宣称，从未被验证。
 *
 * 第一轮已修复缺陷的回归开关：
 *   deductibleBeforeRatio  = false -> 免赔额后置于赔付比例（少赔）
 *   clampRatioAt100        = false -> 赔付比例可破 100%（超赔）
 *   applyIndemnityPrinciple= false -> 第三方已付不抵扣（被保险人得利）
 *   applyIdempotency       = false -> 重复提交双计免赔/限额（超赔）
 *   tenantScopedKeys       = false -> 跨租户额度串用（数据隔离违规）
 *
 * 第二轮新增（诊断 N1–N10）：
 *   applyLiabilityAccumulation = false -> 责任限额不跨案累计（超赔）
 *   applyReversal              = false -> 重算不冲正旧占用（额度账实不符 + 少赔）
 *   applyPreAuthFrozen         = false -> 预授权不计冻结（超年限额授权）
 *   useRemainingLiability      = false -> 预授权按责任总额而非剩余
 *   zeroAuthWhenAllRejected    = false -> 无可赔费用仍授权
 *   rejectRowZeroDeduct        = false -> 拒赔行把原始费用记成免赔额（告知书误导）
 *   applyIdempotencyCAS        = false -> 并发生成双份结论/双次占额
 *   apportionRounding          = false -> 逐行取整分位不守恒（对账不平）
 *   zeroMeansUnlimited         = false -> 限额 0 被当作「限额为零」全额拒付（静默少赔）
 */
public final class EngineFlags {

    public boolean enforceLimitMin = true;          // 多维度 min 控额
    public boolean freezeSnapshot = true;           // 只消费冻结快照
    public boolean requireConfirm = true;           // AI 建议须确认
    public boolean applyAccumulation = true;        // 保单级跨案累计
    public boolean recordTrace = true;              // 规则溯源
    public boolean applyIdempotency = true;         // 幂等键（重复提交返回首次结论）
    public boolean tenantScopedKeys = true;         // 累计/控额键含 tenantId
    public boolean applyIndemnityPrinciple = true;  // 不可获利：第三方已付先抵扣
    public boolean clampRatioAt100 = true;          // 赔付比例上限 100%
    public boolean deductibleBeforeRatio = true;    // 免赔额作用于费用基数（先免赔后比例）

    // ---- 第二轮新增护栏 ----
    public boolean applyLiabilityAccumulation = true; // 责任限额跨案累计
    public boolean applyReversal = true;              // 重算/撤件冲正
    public boolean applyPreAuthFrozen = true;         // 预授权计入冻结额度
    public boolean useRemainingLiability = true;      // 预授权用剩余责任限额
    public boolean zeroAuthWhenAllRejected = true;    // 无可赔费用 -> 授权 0
    public boolean rejectRowZeroDeduct = true;        // 拒赔行免赔额记 0
    public boolean applyIdempotencyCAS = true;        // 幂等 CAS 租约
    public boolean apportionRounding = true;          // 最大余数法配平分位
    public boolean zeroMeansUnlimited = true;         // 限额 0 = 未配置（不限制）

    public static EngineFlags allOn() { return new EngineFlags(); }

    /** 不变量护栏（负赔付钳零）不设开关：属于类型层强制保障，永不关闭。 */
    public EngineFlags copy() {
        EngineFlags f = new EngineFlags();
        f.enforceLimitMin = enforceLimitMin;
        f.freezeSnapshot = freezeSnapshot;
        f.requireConfirm = requireConfirm;
        f.applyAccumulation = applyAccumulation;
        f.recordTrace = recordTrace;
        f.applyIdempotency = applyIdempotency;
        f.tenantScopedKeys = tenantScopedKeys;
        f.applyIndemnityPrinciple = applyIndemnityPrinciple;
        f.clampRatioAt100 = clampRatioAt100;
        f.deductibleBeforeRatio = deductibleBeforeRatio;
        f.applyLiabilityAccumulation = applyLiabilityAccumulation;
        f.applyReversal = applyReversal;
        f.applyPreAuthFrozen = applyPreAuthFrozen;
        f.useRemainingLiability = useRemainingLiability;
        f.zeroAuthWhenAllRejected = zeroAuthWhenAllRejected;
        f.rejectRowZeroDeduct = rejectRowZeroDeduct;
        f.applyIdempotencyCAS = applyIdempotencyCAS;
        f.apportionRounding = apportionRounding;
        f.zeroMeansUnlimited = zeroMeansUnlimited;
        return f;
    }
}
