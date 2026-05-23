package com.bone.masterdata.application.query.handler;

import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.application.query.dto.MasterDataEntityDTO;
import com.bone.masterdata.application.query.qry.MasterDataEntityByIdQuery;
import com.bone.masterdata.domain.entity.MasterDataEntity;
import com.bone.masterdata.domain.entity.MasterDataField;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MasterDataEntityDetailQueryHandler {
    private final MasterDataEntityRepository entityRepository;

    @Transactional(readOnly = true)
    public MasterDataEntityDTO handle(MasterDataEntityByIdQuery qry) {
        MasterDataEntity entity = entityRepository.findById(qry.getId());
        if (entity == null) {
            throw new NotFoundException("主数据实体不存在");
        }

        int fieldCount = (int) QueryBuilder.from(MasterDataField.class)
                .where(MasterDataField::getMasterDataEntityId)
                .eq(entity.getId())
                .count();

        return MasterDataEntityDTO.builder()
                .id(entity.getId())
                .name(entity.getName().value())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .status(entity.getStatus().name())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .fieldCount(fieldCount)
                .build();
    }
}
