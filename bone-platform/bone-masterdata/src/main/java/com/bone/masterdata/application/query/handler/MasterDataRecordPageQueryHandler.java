package com.bone.masterdata.application.query.handler;

import com.bone.core.model.PageParam;
import com.bone.core.model.PageResult;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.application.query.qry.MasterDataRecordPageQry;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MasterDataRecordPageQueryHandler {
    private final MasterDataRecordRepository masterDataRecordRepository;

    @Transactional(readOnly = true)
    public PageResult<MasterDataRecordDTO> handle(MasterDataRecordPageQry qry) {
        List<MasterDataRecord> records;
        if (qry.getMasterDataEntityId() != null) {
            records = masterDataRecordRepository.findByMasterDataEntityId(MasterDataEntityId.of(qry.getMasterDataEntityId()));
        } else {
            PageParam pageParam = PageParam.of(qry.getPageNum(), qry.getPageSize());
            PageResult<MasterDataRecord> pageResult = masterDataRecordRepository.queryPage(pageParam);
            records = pageResult.getRecords();
        }

        List<MasterDataRecordDTO> dtoList = records.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());

        return PageResult.of(
            dtoList,
            (long) dtoList.size(),
            qry.getPageNum(),
            qry.getPageSize()
        );
    }

    private MasterDataRecordDTO convertToDTO(MasterDataRecord record) {
        return MasterDataRecordDTO.builder()
            .id(record.getId().getValue())
            .masterDataEntityId(record.getMasterDataEntityId().getValue())
            .data(record.getData())
            .status(record.getStatus().name())
            .createdAt(record.getCreatedAt())
            .updatedAt(record.getUpdatedAt())
            .publishTime(record.getPublishTime())
            .build();
    }
}
