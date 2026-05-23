package com.bone.masterdata.infrastructure.gateway;

import com.bone.masterdata.domain.gateway.MasterDataExcelImportPort;
import com.bone.masterdata.domain.record.MasterDataRecord;
import com.bone.masterdata.infrastructure.util.ExcelUtils;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MasterDataExcelImportGateway implements MasterDataExcelImportPort {

    @Override
    public List<MasterDataRecord> parseRecords(MultipartFile file, Long masterDataEntityId) {
        return ExcelUtils.parseExcelFile(file, masterDataEntityId);
    }
}
