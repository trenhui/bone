package com.bone.workflow.starter.designer.web;

/**
 * 上线 / 下线请求体。
 *
 * @param version 目标版本（不可变快照，按版本切换上下线，不回改内容）
 * @param enabled {@code true}=上线，{@code false}=下线
 */
public record EnabledRequest(int version, boolean enabled) {}
