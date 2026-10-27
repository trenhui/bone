package com.bone.workflow.starter.designer.web;

/**
 * 保存草稿请求体。
 *
 * @param definitionJson 调用方提交的<b>原文</b> JSON（含 {@code ui} 段，命令服务原样落库）
 * @param expectedRevision 覆盖已有草稿时的乐观锁版本；新建草稿传 {@code null}
 */
public record SaveDraftRequest(String definitionJson, Integer expectedRevision) {}
