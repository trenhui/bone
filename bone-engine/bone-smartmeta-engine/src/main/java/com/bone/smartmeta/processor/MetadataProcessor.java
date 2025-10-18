package com.bone.smartmeta.processor;

import com.bone.smartmeta.metadata.EntityMetadata;

/**
 * 元数据处理器接口
 * <p>
 * 定义元数据处理的核心功能，负责对实体元数据进行转换、增强和验证
 * </p>
 *
 * @author SmartMeta Team
 */
public interface MetadataProcessor {

    /**
     * 处理实体元数据
     * 
     * @param metadata 待处理的实体元数据
     * @return 处理后的实体元数据，如果返回null表示处理失败或终止处理链
     */
    EntityMetadata process(EntityMetadata metadata);

    /**
     * 判断处理器是否支持指定的实体元数据
     * 
     * @param metadata 待检查的实体元数据
     * @return 是否支持
     */
    boolean supports(EntityMetadata metadata);
}