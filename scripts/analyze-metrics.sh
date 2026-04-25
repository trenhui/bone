#!/bin/bash
# 月度效能分析脚本 - Bone Agentic OS

OUTPUT_DIR="reports"
mkdir -p "$OUTPUT_DIR"

echo "# Bone Agentic OS 效能报告" > "$OUTPUT_DIR/metrics.md"
echo "生成时间: $(date)" >> "$OUTPUT_DIR/metrics.md"
echo "" >> "$OUTPUT_DIR/metrics.md"

# 统计功能交付周期
echo "## 功能交付周期" >> "$OUTPUT_DIR/metrics.md"
echo "| Feature | 规划 | 构建 | 测试 | 总计 | Ralph Loop |" >> "$OUTPUT_DIR/metrics.md"
echo "|---------|------|------|------|------|------------|" >> "$OUTPUT_DIR/metrics.md"

for checkpoint in .claude/state/*/checkpoint.json; do
    if [ -f "$checkpoint" ]; then
        FEATURE=$(basename $(dirname "$checkpoint"))
        PLAN_TIME=$(jq -r '.metrics.plan_time_sec // 0' "$checkpoint")
        BUILD_TIME=$(jq -r '.metrics.build_time_sec // 0' "$checkpoint")
        TEST_TIME=$(jq -r '.metrics.test_time_sec // 0' "$checkpoint")
        RALPH_ITER=$(jq -r '.metrics.ralph_loop_iterations // 0' "$checkpoint")
        TOTAL=$((PLAN_TIME + BUILD_TIME + TEST_TIME))
        echo "| $FEATURE | ${PLAN_TIME}s | ${BUILD_TIME}s | ${TEST_TIME}s | ${TOTAL}s | $RALPH_ITER |" >> "$OUTPUT_DIR/metrics.md"
    fi
done

# 统计自愈成功率
echo "" >> "$OUTPUT_DIR/metrics.md"
echo "## 自愈统计" >> "$OUTPUT_DIR/metrics.md"
echo "| 级别 | 尝试次数 | 成功次数 | 成功率 |" >> "$OUTPUT_DIR/metrics.md"
echo "|------|----------|----------|--------|" >> "$OUTPUT_DIR/metrics.md"

L1_ATTEMPTS=$(jq -s 'map(.metrics.l1_attempts // 0) | add' .claude/state/*/checkpoint.json 2>/dev/null || echo 0)
L1_SUCCESS=$(jq -s 'map(.metrics.l1_success // 0) | add' .claude/state/*/checkpoint.json 2>/dev/null || echo 0)

if [ "$L1_ATTEMPTS" -gt 0 ]; then
    L1_RATE=$(echo "scale=2; $L1_SUCCESS * 100 / $L1_ATTEMPTS" | bc)
else
    L1_RATE=0
fi

echo "| L1 | $L1_ATTEMPTS | $L1_SUCCESS | ${L1_RATE}% |" >> "$OUTPUT_DIR/metrics.md"

L2A_ATTEMPTS=$(jq -s 'map(.metrics.l2a_attempts // 0) | add' .claude/state/*/checkpoint.json 2>/dev/null || echo 0)
L2A_SUCCESS=$(jq -s 'map(.metrics.l2a_success // 0) | add' .claude/state/*/checkpoint.json 2>/dev/null || echo 0)

if [ "$L2A_ATTEMPTS" -gt 0 ]; then
    L2A_RATE=$(echo "scale=2; $L2A_SUCCESS * 100 / $L2A_ATTEMPTS" | bc)
else
    L2A_RATE=0
fi

echo "| L2-A | $L2A_ATTEMPTS | $L2A_SUCCESS | ${L2A_RATE}% |" >> "$OUTPUT_DIR/metrics.md"

echo "" >> "$OUTPUT_DIR/metrics.md"
echo "## 汇总" >> "$OUTPUT_DIR/metrics.md"
echo "- 总功能数: $(ls -d .claude/state/* 2>/dev/null | wc -l)" >> "$OUTPUT_DIR/metrics.md"
echo "- 平均交付周期: TODO" >> "$OUTPUT_DIR/metrics.md"
echo "- 平均覆盖率: TODO" >> "$OUTPUT_DIR/metrics.md"

cat "$OUTPUT_DIR/metrics.md"
