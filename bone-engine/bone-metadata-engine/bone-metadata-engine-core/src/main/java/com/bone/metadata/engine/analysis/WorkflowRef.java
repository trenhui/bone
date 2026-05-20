package com.bone.metadata.engine.analysis;

/** 工作流引用（影响分析用轻量 DTO）。 */
public record WorkflowRef(String name, String targetEntity) {}
