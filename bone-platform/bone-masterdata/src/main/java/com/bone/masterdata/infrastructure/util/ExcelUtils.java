package com.bone.masterdata.infrastructure.util;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.domain.record.MasterDataRecord;
import com.bone.masterdata.infrastructure.config.ExcelConfig;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.DateUtil;

public class ExcelUtils {
  public static List<MasterDataRecord> parseExcelStream(
      InputStream inputStream, String originalFilename, Long masterDataEntityId) {
    if (inputStream == null) {
      throw MasterDataErrors.of(MasterDataErrorCodes.FILE_EMPTY, "上传的Excel文件为空");
    }

    if (originalFilename != null
        && !originalFilename.toLowerCase().endsWith(".xlsx")
        && !originalFilename.toLowerCase().endsWith(".xls")) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.FILE_FORMAT_INVALID, "仅支持 .xlsx 或 .xls 格式的Excel文件");
    }

    List<MasterDataRecord> records = new ArrayList<>();

    try (Workbook workbook = WorkbookFactory.create(inputStream)) {
      Sheet sheet = workbook.getSheetAt(0);

      // 跳过表头
      for (int i = 1; i <= sheet.getLastRowNum() && i <= ExcelConfig.MAX_ROWS; i++) {
        Row row = sheet.getRow(i);
        if (row == null) continue;

        // 简单实现，将行数据转换为 JSON 字符串
        StringBuilder dataBuilder = new StringBuilder();
        dataBuilder.append("{");

        for (int j = 0; j < row.getLastCellNum() && j < ExcelConfig.MAX_COLUMNS; j++) {
          Cell cell = row.getCell(j);
          if (cell == null) continue;

          String cellValue = getCellValue(cell);
          dataBuilder.append("\"field").append(j).append("\":\"").append(cellValue).append("\"");
          if (j < row.getLastCellNum() - 1) {
            dataBuilder.append(",");
          }
        }

        dataBuilder.append("}");
        String data = dataBuilder.toString();

        Long recordId = DistributedIdGenerator.generateLongId();
        MasterDataRecord record = MasterDataRecord.create(recordId, masterDataEntityId, data);
        records.add(record);
      }

    } catch (Exception e) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.FILE_PARSE_FAILED, "解析Excel文件失败: " + e.getMessage(), e);
    }

    return records;
  }

  private static String getCellValue(Cell cell) {
    switch (cell.getCellType()) {
      case STRING:
        return cell.getStringCellValue();
      case NUMERIC:
        if (DateUtil.isCellDateFormatted(cell)) {
          return cell.getDateCellValue().toString();
        } else {
          return String.valueOf(cell.getNumericCellValue());
        }
      case BOOLEAN:
        return String.valueOf(cell.getBooleanCellValue());
      case FORMULA:
        return cell.getCellFormula();
      default:
        return "";
    }
  }
}
