package com.bone.metadata.engine.runtime.adapter.po;

import com.bone.metadata.sdk.Repository;

/**
 * {@code meta_field} 表的 SDK Repository 接口。
 *
 * <p>供 {@code @EnableSqlRepositories}（见 {@code EngineSdkRepositoryConfig}）扫描并为 {@code
 * Repository<MetaFieldPo, Long>} 生成代理 bean，供 {@code SdkMetadataRepository}/{@code
 * IamMetadataBridge} 注入。
 */
public interface EngineMetaFieldRepository extends Repository<MetaFieldPo, Long> {}
