package com.bone.masterdata.domain.gateway;

public interface MetadataClient {
    /**
     * 从业务实体转换为主数据实体
     * @param businessEntityId 业务实体ID
     * @return 转换结果
     */
    boolean convertFromBusinessEntity(Long businessEntityId);

    /**
     * 获取业务实体信息
     * @param businessEntityId 业务实体ID
     * @return 业务实体信息（JSON格式）
     */
    String getBusinessEntityInfo(Long businessEntityId);
}