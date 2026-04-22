package com.bone.masterdata.application.query.handler;

import com.bone.core.model.PageParam;
import com.bone.core.model.PageResult;
import com.bone.masterdata.application.query.dto.MasterDataFieldDTO;
import com.bone.masterdata.application.query.qry.MasterDataFieldListQry;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.field.MasterDataField;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MasterDataFieldListQueryHandler {
    private final MasterDataFieldRepository masterDataFieldRepository;

    @Transactional(readOnly = true)
    public List<MasterDataFieldDTO> handle(MasterDataFieldListQry qry) {
        List<MasterDataField> fields;
        if (qry.getMasterDataEntityId() != null) {
            fields = masterDataFieldRepository.findByMasterDataEntityId(MasterDataEntityId.of(qry.getMasterDataEntityId()));
        } else {
            // 当没有实体ID时，查询所有字段
            PageParam pageParam = PageParam.of(1, 1000);
            PageResult<MasterDataField> pageResult = masterDataFieldRepository.queryPage(pageParam);
            fields = pageResult.getRecords();
        }

        return fields.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    private MasterDataFieldDTO convertToDTO(MasterDataField field) {
        return MasterDataFieldDTO.builder()
            .id(field.getId().getValue())
            .masterDataEntityId(field.getMasterDataEntityId().getValue())
            .name(field.getName().value())
            .code(field.getCode().value())
            .type(field.getType())
            .length(field.getLength())
            .required(field.getRequired())
            .defaultValue(field.getDefaultValue())
            .description(field.getDescription())
            .sortOrder(field.getSortOrder())
            .build();
    }
}
