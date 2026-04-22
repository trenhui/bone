package com.bone.masterdata.application.query.handler;

import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.application.query.qry.MasterDataRecordByIdQry;
import com.bone.masterdata.domain.model.record.vo.MasterDataRecordId;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MasterDataRecordDetailQueryHandler {
    private final MasterDataRecordRepository masterDataRecordRepository;

    @Transactional(readOnly = true)
    public MasterDataRecordDTO handle(MasterDataRecordByIdQry qry) {
        MasterDataRecord record = masterDataRecordRepository.findById(MasterDataRecordId.of(qry.getId()));
        if (record == null) {
            throw new NotFoundException("主数据记录不存在: " + qry.getId());
        }

        return MasterDataRecordDTO.builder()
            .id(record.getId().getValue())
            .masterDataEntityId(record.getMasterDataEntityId().getValue())
            .data(record.getData())
            .status(record.getStatus().name())
            .createTime(record.getCreateTime())
            .updateTime(record.getUpdateTime())
            .publishTime(record.getPublishTime())
            .build();
    }
}
