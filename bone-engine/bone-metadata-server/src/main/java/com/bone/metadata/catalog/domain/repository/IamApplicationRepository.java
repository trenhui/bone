package com.bone.metadata.catalog.domain.repository;

import com.bone.metadata.catalog.domain.model.iam.IamApplicationRef;
import com.bone.metadata.sdk.Repository;

/** 只读仓储：查询 IAM 独占的 {@code bone_application} 表，供 metadata 侧校验应用存在性。 仅使用读取能力，不暴露写操作。 */
public interface IamApplicationRepository extends Repository<IamApplicationRef, Long> {}
