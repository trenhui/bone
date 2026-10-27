package com.bone.synap.settlement;

import com.bone.synap.settlement.probe.ProbeSuite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防假绿护栏回归：正确引擎 30 条探针全绿，且 19 类变异（关掉一条护栏即一个已知缺陷）全部被探针捕获。
 * 若某条护栏失效，对应变异将逃逸 -> 本测试失败，证明该护栏只是文档宣称而未被验证。
 */
class SettlementEngineProbeTest {

    @Test
    void correctEngineAllGreenAndAllMutationsCaptured() {
        ProbeSuite.Summary s = ProbeSuite.runAll();
        assertTrue(s.correctAllGreen,
                "正确引擎应 30 条探针全绿，实际逃逸变异=" + s.escaped);
        assertEquals(30, s.totalProbes, "探针数量应为 30");
        assertEquals(19, s.mutantsTotal, "变异数量应为 19");
        assertEquals(19, s.mutantsCaptured, "19 类变异应全部被捕获，逃逸=" + s.escaped);
    }
}
