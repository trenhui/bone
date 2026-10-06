package com.bone.integration.application.query.dto;

import java.util.List;
import java.util.Map;

/**
 * 连接器读出视图。
 *
 * <p><b>{@code config} 里永远不含凭据明文</b>（由 {@code ConnectorSecretSupport.maskForRead} 剔除）， {@code
 * secretKeysConfigured} 只回报「哪些凭据键已配置」，供界面显示「已配置」而不是把密文/掩码回填。
 */
public record ConnectorDTO(
    Long id,
    String name,
    String type,
    Map<String, Object> config,
    String status,
    List<String> secretKeysConfigured) {}
