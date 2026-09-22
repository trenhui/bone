package com.bone.masterdata.infrastructure.util;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.masterdata.infrastructure.config.ExcelConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/** Excel 导入解析：每行转为一个待落库的 {@link MasterDataRecord}，行内容以 JSON 存入 {@code data} 列。 */
public final class ExcelUtils {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private ExcelUtils() {}

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

    try (InputStream in = inputStream;
        Workbook workbook = WorkbookFactory.create(in)) {
      Sheet sheet = workbook.getSheetAt(0);
      if (sheet == null) {
        return records;
      }
      // 第 0 行是表头，数据从第 1 行开始
      int lastRowNum = sheet.getLastRowNum();
      if (lastRowNum > ExcelConfig.MAX_ROWS) {
        throw MasterDataErrors.of(
            MasterDataErrorCodes.FILE_PARSE_FAILED, "Excel 行数超过上限 " + ExcelConfig.MAX_ROWS + " 行");
      }

      for (int i = 1; i <= lastRowNum; i++) {
        Row row = sheet.getRow(i);
        if (row == null) {
          continue;
        }
        String data = toJson(row);
        Long recordId = DistributedIdGenerator.generateLongId();
        records.add(MasterDataRecord.create(recordId, masterDataEntityId, data));
      }
    } catch (Exception e) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.FILE_PARSE_FAILED, "解析Excel文件失败: " + e.getMessage(), e);
    }

    return records;
  }

  /** 行内容序列化为 JSON：字段名取列下标，交由 ObjectMapper 处理转义。 */
  private static String toJson(Row row) throws Exception {
    Map<String, String> rowData = new LinkedHashMap<>();
    for (int j = 0; j < row.getLastCellNum() && j < ExcelConfig.MAX_COLUMNS; j++) {
      Cell cell = row.getCell(j);
      if (cell == null) {
        continue;
      }
      rowData.put("field" + j, getCellValue(cell));
    }
    return OBJECT_MAPPER.writeValueAsString(rowData);
  }

  private static String getCellValue(Cell cell) {
    switch (cell.getCellType()) {
      case STRING:
        return cell.getStringCellValue();
      case NUMERIC:
        if (DateUtil.isCellDateFormatted(cell)) {
          return cell.getDateCellValue().toString();
        }
        return String.valueOf(cell.getNumericCellValue());
      case BOOLEAN:
        return String.valueOf(cell.getBooleanCellValue());
      case FORMULA:
        // 未求值，落库的是公式文本；下游按字符串消费，不做表达式执行
        return cell.getCellFormula();
      default:
        return "";
    }
  }
}
