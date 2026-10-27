package com.bone.workflow.starter.designer.web;

/**
 * 发布请求体。
 *
 * @param expectedRevision 草稿当前 revision；与后端不一致即 409（他人已改）
 */
public record PublishRequest(Integer expectedRevision) {}
