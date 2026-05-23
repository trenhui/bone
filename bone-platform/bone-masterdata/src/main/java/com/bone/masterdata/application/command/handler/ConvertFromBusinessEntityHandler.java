package com.bone.masterdata.application.command.handler;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.domain.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityName;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.gateway.MetaEntityCatalogPort;
import com.bone.masterdata.domain.gateway.MetaEntityCatalogPort.MetaEntityRow;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 从已发布 {@code meta_entity} 创建主数据实体（MD-04 · ADR-0002）。
 */
@Component
@RequiredArgsConstructor
public class ConvertFromBusinessEntityHandler {

    private final MasterDataEntityRepository entityRepository;
    private final MetaEntityCatalogPort metaEntityCatalogPort;

    @Transactional
    public Long handle(Long metaEntityId) {
        List<MasterDataEntity> existing =
                entityRepository.findByCriteria(
                        Criteria.<MasterDataEntity>create().eq("metaEntityId", metaEntityId));
        if (!existing.isEmpty()) {
            return existing.get(0).getId();
        }

        MetaEntityRow meta = metaEntityCatalogPort.requirePublished(metaEntityId);
        String displayName =
                meta.displayName() != null && !meta.displayName().isBlank()
                        ? meta.displayName()
                        : meta.code();
        MasterDataEntity entity =
                MasterDataEntity.create(
                        DistributedIdGenerator.generateLongId(),
                        metaEntityId,
                        MasterDataEntityName.of(displayName),
                        "从元数据实体 " + metaEntityId + " 转换",
                        "catalog");
        return entityRepository.insert(entity);
    }
}
