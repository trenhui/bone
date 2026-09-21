package com.bone.system.domain.gateway;

import com.bone.system.domain.console.ResourceUsage;

/**
 * 节点资源使用出站端口（JVM/操作系统）。
 *
 * <p>当前实现仅取 Micrometer JVM gauge；CPU / 磁盘留作 [Target]。
 */
public interface ResourceUsageGateway {

  ResourceUsage snapshot();
}
