package com.bone.engine.extension.studio.infrastructure.persistence.repository;

import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtIamTenantDirectory;
import com.bone.metadata.sdk.Repository;

/** 租户目录只读仓储（映射共享库 iam_tenant，仅查询） */
public interface ExtIamTenantDirectoryRepository extends Repository<ExtIamTenantDirectory, Long> {}
