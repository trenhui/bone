package com.bone.aiapp.eval.application;

/** 单条评测计量记录（与持久化解耦，便于单测与流水线复用）。 */
public record EvalRecord(boolean expectedHit, boolean predictedHit, String citedClause) {}
