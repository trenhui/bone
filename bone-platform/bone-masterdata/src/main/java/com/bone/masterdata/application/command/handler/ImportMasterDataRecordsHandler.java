package com.bone.masterdata.application.command.handler;

import com.bone.masterdata.application.command.cmd.ImportMasterDataRecordsCmd;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.core.exception.BizException;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.infrastructure.util.ExcelUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ImportMasterDataRecordsHandler {
    private final MasterDataRecordRepository recordRepository;
    private final MasterDataEntityRepository entityRepository;

    @Transactional
    public List<Long> handle(ImportMasterDataRecordsCmd cmd) {
        // 验证主数据实体存在
        MasterDataEntityId entityId = MasterDataEntityId.of(cmd.getMasterDataEntityId());
        if (entityRepository.findById(entityId) == null) {
            throw new NotFoundException("主数据实体不存在");
        }

        // 解析Excel文件
        List<MasterDataRecord> records = ExcelUtils.parseExcelFile(
            cmd.getFile(),
            cmd.getMasterDataEntityId()
        );

        // 保存记录
        records.forEach(recordRepository::save);

        // 返回记录ID列表
        return records.stream()
                .map(record -> record.getId().getValue())
                .toList();
    }
}