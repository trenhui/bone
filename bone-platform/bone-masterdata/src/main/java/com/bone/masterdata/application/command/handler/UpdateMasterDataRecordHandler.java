package com.bone.masterdata.application.command.handler;

import com.bone.masterdata.application.command.cmd.UpdateMasterDataRecordCmd;
import com.bone.masterdata.domain.model.record.vo.MasterDataRecordId;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateMasterDataRecordHandler {
    private final MasterDataRecordRepository masterDataRecordRepository;

    @Transactional
    public void handle(UpdateMasterDataRecordCmd cmd) {
        MasterDataRecord record = masterDataRecordRepository.findById(MasterDataRecordId.of(cmd.getId()));
        if (record == null) {
            throw new NotFoundException("主数据记录不存在");
        }

        record.update(cmd.getData());
        masterDataRecordRepository.update(record);
    }
}
