package com.bone.masterdata.infrastructure.gateway;

import com.bone.masterdata.domain.gateway.MasterDataExcelImportPort;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.masterdata.infrastructure.util.ExcelUtils;
import java.io.InputStream;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MasterDataExcelImportPortAdapter implements MasterDataExcelImportPort {

  @Override
  public List<MasterDataRecord> parseRecords(
      InputStream data, String originalFilename, Long masterDataEntityId) {
    return ExcelUtils.parseExcelStream(data, originalFilename, masterDataEntityId);
  }
}
