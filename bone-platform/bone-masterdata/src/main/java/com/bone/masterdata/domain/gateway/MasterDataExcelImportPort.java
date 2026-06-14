package com.bone.masterdata.domain.gateway;

import com.bone.masterdata.domain.record.MasterDataRecord;
import java.io.InputStream;
import java.util.List;

/** Excel 批量导入主数据记录出站端口。 */
public interface MasterDataExcelImportPort {

  List<MasterDataRecord> parseRecords(
      InputStream data, String originalFilename, Long masterDataEntityId);
}
