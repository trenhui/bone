package com.bone.masterdata.application.query.handler;

import com.bone.masterdata.application.query.dto.MasterDataEntityDTO;
import com.bone.masterdata.application.query.qry.MasterDataEntityByIdQry;
import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import com.bone.core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MasterDataEntityDetailQueryHandler {
    private final MasterDataEntityRepository entityRepository;
    private final MasterDataFieldRepository fieldRepository;

    @Transactional(readOnly = true)
    public MasterDataEntityDTO handle(MasterDataEntityByIdQry qry) {
        MasterDataEntity entity = entityRepository.findById(MasterDataEntityId.of(qry.getId()));
        if (entity == null) {
            throw new NotFoundException("主数据实体不存在");
        }

        MasterDataEntityDTO dto = new MasterDataEntityDTO();
        dto.setId(entity.getId().getValue());
        dto.setName(entity.getName().value());
        dto.setDescription(entity.getDescription());
        dto.setCategory(entity.getCategory());
        dto.setStatus(entity.getStatus().getDescription());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // 获取字段数量
        List<?> fields = fieldRepository.findByMasterDataEntityId(entity.getId());
        dto.setFieldCount(fields.size());

        return dto;
    }
}