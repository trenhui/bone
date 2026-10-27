package com.bone.synap.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 不可变金额值对象。所有金融计算强制使用 BigDecimal（拒绝 double 浮点），
 * 中间计算保留 CALC_SCALE=6 位，结果四舍五入至 RESULT_SCALE=2 位（HALF_UP）。
 * 对应《理算引擎内部设计》§3 的金额不变式与 SOX/NAIC「同输入同输出可复算」要求。
 */
public final class Money {
    public static final int CALC_SCALE = 6;
    public static final int RESULT_SCALE = 2;
    public static final RoundingMode RM = RoundingMode.HALF_UP;

    private final BigDecimal v;

    private Money(BigDecimal v) {
        this.v = v.setScale(CALC_SCALE, RM);
    }

    public static Money of(BigDecimal raw) { return new Money(raw); }
    public static Money of(long l) { return new Money(BigDecimal.valueOf(l)); }
    public static Money of(String s) { return new Money(new BigDecimal(s)); }

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money add(Money o) { return new Money(v.add(o.v)); }
    public Money subtract(Money o) { return new Money(v.subtract(o.v)); }
    public Money multiply(Money o) { return new Money(v.multiply(o.v)); }
    public Money divide(Money o) { return new Money(v.divide(o.v, CALC_SCALE, RM)); }

    /** 百分比（如 80 表示 80%）转为小数比例（0.8）。 */
    public Money ratio() { return new Money(v.divide(BigDecimal.valueOf(100), CALC_SCALE, RM)); }

    public Money min(Money o) { return v.compareTo(o.v) <= 0 ? this : o; }
    public Money max(Money o) { return v.compareTo(o.v) >= 0 ? this : o; }

    /** 负值钳零（参考实现 L150-153 的防御逻辑，这里提升为类型保障）。 */
    public Money clampNonNegative() { return v.signum() < 0 ? ZERO : this; }

    /** 上限钳制（赔付比例 ≤100% 等硬护栏的类型化保障）。 */
    public Money clampMax(Money cap) { return v.compareTo(cap.v) > 0 ? cap : this; }

    public Money abs() { return v.signum() < 0 ? new Money(v.negate()) : this; }
    public Money negate() { return new Money(v.negate()); }

    /** 以「分」为单位的整数差值，用于最大余数法配平分位（对账守恒）。 */
    public int cents() {
        return v.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    public boolean isNegative() { return v.signum() < 0; }
    public boolean isPositive() { return v.signum() > 0; }
    public boolean isZero() { return v.signum() == 0; }

    /** 结果金额（2 位四舍五入），用于对外输出与金额比较。 */
    public Money rounded() { return new Money(v.setScale(RESULT_SCALE, RM)); }

    public BigDecimal raw() { return v.setScale(CALC_SCALE, RM); }
    public int cmp(Money o) { return v.compareTo(o.v); }

    @Override
    public String toString() { return v.setScale(RESULT_SCALE, RM).toPlainString(); }
}
