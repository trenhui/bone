package com.bone.system.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.system.domain.model.console.RecentAccess;

/**
 * 最近访问记录仓储端口（S-13：落地 {@code cnsl_recent_access} 孤儿表）。
 *
 * <p>写由 {@link com.bone.system.infrastructure.web.RecentAccessRecorder} 在控制台访问后追加； 本端口仅暴露 SDK 基类的读
 * / 写能力，无自定义方法。
 */
public interface RecentAccessRepository extends Repository<RecentAccess, Long> {}
