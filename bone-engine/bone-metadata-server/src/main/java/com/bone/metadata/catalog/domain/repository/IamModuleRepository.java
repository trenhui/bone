package com.bone.metadata.catalog.domain.repository;

import com.bone.metadata.catalog.domain.model.IamModuleRef;
import com.bone.metadata.sdk.Repository;

/** 只读仓储：查询 IAM 独占的 {@code bone_module} 表，供 metadata 侧校验模块存在性。 仅使用读取能力，不暴露写操作。 */
public interface IamModuleRepository extends Repository<IamModuleRef, Long> {}
