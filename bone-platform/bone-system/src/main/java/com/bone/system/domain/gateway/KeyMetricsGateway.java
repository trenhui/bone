package com.bone.system.domain.gateway;

import com.bone.system.domain.console.KeyMetrics;

/**
 * 关键业务指标出站端口（控制台概览"keyMetrics"）。
 *
 * <p>实现按 DDL 表存在性做最佳努力 COUNT；单表故障/缺失时该字段返回 0，不能让概览整体失败。
 */
public interface KeyMetricsGateway {

  KeyMetrics collect();
}
