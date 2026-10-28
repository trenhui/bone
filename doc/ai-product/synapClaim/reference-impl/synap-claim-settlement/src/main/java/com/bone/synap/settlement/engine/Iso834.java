package com.bone.synap.settlement.engine;

import com.bone.synap.settlement.Money;

import java.util.ArrayList;
import java.util.List;

/**
 * 多保单责任分摊（ISO 834 / 保险分摊原则）。
 *
 * 同一损失涉多家保险人时，各保险人按约定方式分摊赔偿责任：
 *   - 比例分摊（主规则）：责任方份额 = 其保额 / 全部涉事保额之和；
 *   - 等额分摊（备选）：每方份额 = 1 / N。
 *
 * 返回值为「份额百分比」（0~100，{@code Money.ratio()} 得乘数），便于引擎直接折算本保单应付。
 * 纯函数、无副作用，单测见 {@code BestPracticeVariantTest}。
 */
public final class Iso834 {

    private Iso834() {}

    /** 比例分摊：第 i 方份额% = sumInsured_i / Σ sumInsured × 100；总保额为零时退化为等额。 */
    public static List<Money> proportional(List<Money> sumInsured) {
        List<Money> out = new ArrayList<>();
        if (sumInsured == null || sumInsured.isEmpty()) return out;
        Money total = Money.ZERO;
        for (Money m : sumInsured) total = total.add(m == null ? Money.ZERO : m);
        if (total.cmp(Money.ZERO) <= 0) return equal(sumInsured.size());
        for (Money m : sumInsured) {
            Money share = (m == null ? Money.ZERO : m).multiply(Money.of(100)).divide(total);
            out.add(share);
        }
        return out;
    }

    /** 等额分摊：每方份额% = 100 / N。 */
    public static List<Money> equal(int n) {
        List<Money> out = new ArrayList<>();
        if (n <= 0) return out;
        Money eq = Money.of(100).divide(Money.of(n));
        for (int i = 0; i < n; i++) out.add(eq);
        return out;
    }
}
