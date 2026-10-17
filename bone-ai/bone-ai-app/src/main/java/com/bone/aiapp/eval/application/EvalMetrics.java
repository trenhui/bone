package com.bone.aiapp.eval.application;

/** 评测指标字典（遗漏率/误报率/采纳率/P/R/F1/引用正确率；阈值均为可校准值，详见详设 §2.7）。 */
public record EvalMetrics(
    long total,
    long tp,
    long fp,
    long tn,
    long fn,
    double precision,
    double recall,
    double f1,
    double missRate,
    double falsePositiveRate,
    double adoptionRate,
    double citationAccuracy) {}
