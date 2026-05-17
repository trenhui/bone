package com.bone.metadata.engine.platform;

import java.util.Optional;

/**
 * 平台元数据 SPI：将 bone-metadata-engine 与 bone-metadata-sdk / meta_* 元模型桥接（META-ENG-01）。
 */
public interface MetadataPlatformBridge {

    /**
     * 按元数据实体编码加载已发布实体定义（status=1）。
     *
     * @param tenantId 租户 ID
     * @param entityCode 实体编码
     * @return 实体 JSON 快照，未接入平台时为空
     */
    Optional<String> loadPublishedEntityJson(Long tenantId, String entityCode);
}
