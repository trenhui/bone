package com.bone.masterdata.application.query.handler;

import com.bone.core.result.PageResult;
import com.bone.masterdata.application.query.dto.MasterDataEntityDTO;
import com.bone.masterdata.application.query.qry.MasterDataEntityPageQuery;
import com.bone.masterdata.domain.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityStatus;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MasterDataEntityPageQueryHandler {

    @Transactional(readOnly = true)
    public PageResult<MasterDataEntityDTO> handle(MasterDataEntityPageQuery qry) {
        FluentQuery<MasterDataEntity> query = QueryBuilder.from(MasterDataEntity.class);

        if (qry.getCategory() != null && !qry.getCategory().isBlank()) {
            query.where(MasterDataEntity::getCategory).eq(qry.getCategory());
        }

        if (qry.getStatus() != null && !qry.getStatus().isBlank()) {
            query.where(MasterDataEntity::getStatus).eq(MasterDataEntityStatus.valueOf(qry.getStatus()));
        }

        com.bone.core.model.PageResult<MasterDataEntity> result = query.orderByDesc(MasterDataEntity::getCreatedAt)
                .page(qry.getPageNum(), qry.getPageSize());

        List<MasterDataEntityDTO> dtoList = result.getRecords().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
    }

    private MasterDataEntityDTO toDto(MasterDataEntity entity) {
        return MasterDataEntityDTO.builder()
                .id(entity.getId())
                .name(entity.getName().value())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .status(entity.getStatus().name())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
