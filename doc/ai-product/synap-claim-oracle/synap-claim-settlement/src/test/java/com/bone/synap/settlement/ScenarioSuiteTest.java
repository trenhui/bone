package com.bone.synap.settlement;

import com.bone.synap.settlement.scenario.ScenarioSuite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 TPA 场景用例回归（普康 / 亿保 / 泰康条款锚定）：28 个确定性场景全部通过。
 * 每个场景都带期望值断言，覆盖报销/津贴/定额/直付预授权/拒赔/跨案累计/冲正/幂等等。
 */
class ScenarioSuiteTest {

    @Test
    void allRealTpaScenariosPass() {
        ScenarioSuite.ScenarioReport r = ScenarioSuite.runAll();
        assertTrue(r.fail.isEmpty(),
                "存在失败场景：" + r.fail);
        assertEquals(28, r.pass.size(), "应通过 28 个真实 TPA 场景");
    }
}
