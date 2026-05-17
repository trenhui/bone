package com.bone.masterdata.application.command.handler;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.domain.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityName;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 从元数据实体 ID 创建主数据实体配置（MD-03 最小实现）。
 */
@Component
@RequiredArgsConstructor
public class ConvertFromBusinessEntityHandler {

    private final MasterDataEntityRepository entityRepository;

    @Transactional
    public Long handle(Long metaEntityId) {
        MasterDataEntity entity =
                MasterDataEntity.create(
                        DistributedIdGenerator.generateLongId(),
                        MasterDataEntityName.of("MDM-" + metaEntityId),
                        "从元数据实体 " + metaEntityId + " 转换",
                        "default");
        return entityRepository.insert(entity);
    }
}
