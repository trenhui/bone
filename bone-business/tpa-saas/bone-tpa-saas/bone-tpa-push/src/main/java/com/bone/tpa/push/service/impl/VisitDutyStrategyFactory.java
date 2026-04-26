package com.bone.tpa.push.service.impl;

import com.bone.tpa.push.service.VisitDutyStrategy;
import org.springframework.stereotype.Component;

@Component
public class VisitDutyStrategyFactory {

    private final RbsOrTbcDutyStrategy rbsStrategy;
    private final DefaultDutyStrategy defaultStrategy;
    private final RenshouDutyStrategy renshouStrategy;

    private final WithoutRbsOrTbcDutyStrategy withoutRbsStrategy;

    public VisitDutyStrategyFactory(RbsOrTbcDutyStrategy rbsStrategy,
                                    DefaultDutyStrategy defaultStrategy,
                                    RenshouDutyStrategy renshouStrategy,
                                    WithoutRbsOrTbcDutyStrategy withoutRbsStrategy
                                    ) {
        this.rbsStrategy = rbsStrategy;
        this.defaultStrategy = defaultStrategy;
        this.renshouStrategy = renshouStrategy;
        this.withoutRbsStrategy = withoutRbsStrategy;
    }

    public VisitDutyStrategy getStrategy(boolean hasDutyConfig,
                                         boolean isRbsOrTbcClaim,
                                         boolean isRenShou) {

        if (hasDutyConfig && isRbsOrTbcClaim) {
            return rbsStrategy;
        }

        if (hasDutyConfig && !isRbsOrTbcClaim) {
            return withoutRbsStrategy;
        }

        if (isRenShou) {
            return renshouStrategy;
        }

        return defaultStrategy;
    }
}
