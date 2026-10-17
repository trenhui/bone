package com.bone.aiapp.eval.gateway;

/** LLM-as-Judge 输入（系统/判据/输入/期望/实际输出）。 */
public record JudgeInput(
    String system, String criterion, String input, String expected, String actual) {}
