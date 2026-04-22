package com.bone.masterdata.infrastructure.util;

import com.bone.masterdata.infrastructure.config.ExcelConfig;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.core.exception.SystemException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.DateUtil;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExcelUtils {
    public static List<MasterDataRecord> parseExcelFile(MultipartFile file, Long masterDataEntityId) {
        List<MasterDataRecord> records = new ArrayList<>();
        
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
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
                
                MasterDataRecord record = MasterDataRecord.create(MasterDataEntityId.of(masterDataEntityId), data);
                records.add(record);
            }
            
        } catch (IOException e) {
            throw new SystemException("解析Excel文件失败", e);
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