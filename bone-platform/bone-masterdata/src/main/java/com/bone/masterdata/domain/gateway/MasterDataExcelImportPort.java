package com.bone.masterdata.domain.gateway;

import com.bone.masterdata.domain.record.MasterDataRecord;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** Excel 批量导入主数据记录出站端口。 */
public interface MasterDataExcelImportPort {

    List<MasterDataRecord> parseRecords(MultipartFile file, Long masterDataEntityId);
}
