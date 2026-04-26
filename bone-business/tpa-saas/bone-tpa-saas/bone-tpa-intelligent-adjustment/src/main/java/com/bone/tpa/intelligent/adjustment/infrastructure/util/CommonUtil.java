package com.bone.tpa.intelligent.adjustment.infrastructure.util;

import com.bone.tpa.sdk.adjustment.response.GroupFieldRule;
import com.bone.tpa.sdk.adjustment.response.SingleFieldRule;
import com.bone.tpa.sdk.adjustment.response.UploadDataRule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
public class CommonUtil {

    //校验excel文件是否符合上传数据规则
    public static CheckExcelResult checkFile(MultipartFile file, UploadDataRule rule) {
        String errorReason = "";

        //校验 文件大小
        Integer fileMaxSize = rule.getFileMaxSize();
        if (fileMaxSize != null) {
            long fileSize = CommonUtil.getMultipartFileSize(file);
            if (fileSize >= fileMaxSize) {
                errorReason = "文件大小超过上限:" + fileMaxSize;
                log.info(errorReason);
                return new CheckExcelResult(false, errorReason);
            }
        }

        //校验 文件格式
        String fileFormat = rule.getFileFormat();
        if (StringUtils.hasText(fileFormat)) {
            String lowercaseFileName = file.getOriginalFilename().toLowerCase();
            String[] format = fileFormat.split(",");
            boolean flag = false;
            for (String ext : format) {
                if (lowercaseFileName.endsWith(ext)) {
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                errorReason = "文件格式不在范围:" + Arrays.toString(format);
                log.info(errorReason);
                return new CheckExcelResult(false, errorReason);
            }
        }

        Sheet sheet = null;
        try {
            Workbook workbook = WorkbookFactory.create(file.getInputStream());
            sheet = workbook.getSheetAt(0);
        } catch (Exception e) {
            errorReason = "将流转换成excel发生异常";
            log.info(errorReason, e);
            return new CheckExcelResult(false, errorReason);
        }

        //校验 文件数据量
        Integer fileMaxCount = rule.getFileMaxCount();
        if (fileMaxCount != null) {
            int rowCount = sheet.getLastRowNum() - 1;
            if (rowCount > fileMaxCount) {
                errorReason = "文件数据量超出上限:" + fileMaxCount;
                log.info(errorReason);
                return new CheckExcelResult(false, errorReason);
            }
        }

        //校验 导入字段（元素相同且顺序相同）
        List<String> fieldNameList = rule.getFieldNameList();
        if (CollectionUtils.isEmpty(fieldNameList)) {
            errorReason = "导入字段不能为空";
            log.info(errorReason);
            return new CheckExcelResult(false, errorReason);
        }
        Row titleRow = sheet.getRow(0);
        List<String> titleList = new ArrayList<>();
        for (Cell cell : titleRow) {
            String value = CommonUtil.getCellValue(cell);
            titleList.add(value);
        }
        if (!fieldNameList.equals(titleList)) {
            errorReason = "表头列名出错,正确列名:" + fieldNameList;
            log.info(errorReason);
            return new CheckExcelResult(false, errorReason);
        }

        //单个字段规则、组合字段规则 校验
        Map<String, Integer> columnNameIndexMap = new HashMap<>();
        for (int i = 0; i < titleList.size(); i++) {
            columnNameIndexMap.put(titleList.get(i), i);
        }
        List<SingleFieldRule> singleFieldRuleList = rule.getSingleFieldRuleList().stream().filter(i -> i.getUnique() || i.getRequired()).toList();
        Set<String> requiredColumnNameSet = new HashSet<>();
        HashMap<String, Set<String>> singleColumnUniqueMap = new HashMap<>();
        for (SingleFieldRule singleFieldRule : singleFieldRuleList) {
            String bizName = singleFieldRule.getBizName();
            if (!columnNameIndexMap.containsKey(bizName)) {
                errorReason = "单个字段规则中的字段不在文件中,字段名:" + bizName;
                log.info(errorReason);
                return new CheckExcelResult(false, errorReason);
            }
            if (singleFieldRule.getRequired()) {
                requiredColumnNameSet.add(bizName);
            }
            if (singleFieldRule.getUnique()) {
                singleColumnUniqueMap.put(bizName, new HashSet<>());
            }
        }

        HashMap<List<String>, Set<String>> groupColumnUniqueMap = new HashMap<>();
        HashMap<List<String>[], Map<String, String>> groupRuleMap = new HashMap<>();
        for (GroupFieldRule i : rule.getGroupFieldRuleList()) {
            List<String> fieldNames = i.getFieldNameList();
            for (String name : fieldNames) {
                if (!columnNameIndexMap.containsKey(name)) {
                    errorReason = "组合字段规则中的字段不在文件中,字段名:" + name;
                    log.info(errorReason);
                    return new CheckExcelResult(false, errorReason);
                }
            }
            if (i.getUnique() != null && i.getUnique()) {
                groupColumnUniqueMap.put(new ArrayList<>(fieldNames), new HashSet<>());
            }
            if (!CollectionUtils.isEmpty(i.getFieldNameListA()) && !CollectionUtils.isEmpty(i.getFieldNameListB())) {
                List<String>[] columnAB = new List[]{i.getFieldNameListA(), i.getFieldNameListB()};
                groupRuleMap.put(columnAB, new HashMap<>());
            }
        }

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);

            //检查必填字段
            for (String column : requiredColumnNameSet) {
                Integer index = columnNameIndexMap.get(column);
                Cell cell = row.getCell(index);
                if (cell == null || !StringUtils.hasText(CommonUtil.getCellValue(cell))) {
                    errorReason = "必填列缺少值,列名:" + column + ",行数:" + (i + 1);
                    log.info(errorReason);
                    return new CheckExcelResult(false, errorReason);
                }
            }

            //检查值唯一字段
            for (Map.Entry<String, Set<String>> entry : singleColumnUniqueMap.entrySet()) {
                String column = entry.getKey();
                Set<String> valueSet = entry.getValue();
                Integer index = columnNameIndexMap.get(column);
                Cell cell = row.getCell(index);
                if (cell != null) {
                    String value = CommonUtil.getCellValue(cell);
                    boolean add = valueSet.add(value);
                    if (!add) {
                        errorReason = "列值重复,列名:" + column + ",列值:" + value;
                        log.info(errorReason);
                        return new CheckExcelResult(false, errorReason);
                    }
                }
            }

            //检查组合字段的值唯一
            for (Map.Entry<List<String>, Set<String>> entry : groupColumnUniqueMap.entrySet()) {
                List<String> columnList = entry.getKey();
                Set<String> valueSet = entry.getValue();
                StringBuilder builder = new StringBuilder();
                for (String column : columnList) {
                    Integer index = columnNameIndexMap.get(column);
                    Cell cell = row.getCell(index);
                    if (cell != null) {
                        String value = CommonUtil.getCellValue(cell);
                        builder.append(value).append(",");
                    }
                }
                if (builder.length() > 0) {
                    builder.deleteCharAt(builder.length() - 1);
                }
                boolean add = valueSet.add(builder.toString());
                if (!add) {
                    errorReason = "组合字段的值重复,组合字段名:" + columnList + ",重复值:" + builder;
                    log.info(errorReason);
                    return new CheckExcelResult(false, errorReason);
                }
            }

            //检查组合字段内规则
            for (Map.Entry<List<String>[], Map<String, String>> entry : groupRuleMap.entrySet()) {
                List<String>[] columnAB = entry.getKey();
                Map<String, String> valueMap = entry.getValue();
                List<String> columnListA = columnAB[0];
                List<String> columnListB = columnAB[1];
                StringBuilder builderA = new StringBuilder();
                for (String column : columnListA) {
                    Integer index = columnNameIndexMap.get(column);
                    Cell cell = row.getCell(index);
                    if (cell != null) {
                        String value = CommonUtil.getCellValue(cell);
                        builderA.append(value).append(":");
                    }
                }
                StringBuilder builderB = new StringBuilder();
                for (String column : columnListB) {
                    Integer index = columnNameIndexMap.get(column);
                    Cell cell = row.getCell(index);
                    if (cell != null) {
                        String value = CommonUtil.getCellValue(cell);
                        builderB.append(value).append(";");
                    }
                }
                if (builderA.length() > 0) {
                    builderA.deleteCharAt(builderA.length() - 1);
                }
                if (builderB.length() > 0) {
                    builderB.deleteCharAt(builderB.length() - 1);
                }
                String aStr = builderA.toString();
                String bStr = builderB.toString();
                if (builderA.length() > 0) {
                    if (!valueMap.containsKey(aStr)) {
                        valueMap.put(aStr, bStr);
                    } else {
                        if (!valueMap.get(aStr).equals(bStr)) {
                            errorReason = "不符合组合字段规则:字段组合" + columnListA + "的值相同时,字段组合" + columnListB + "的值必须唯一。 这里" + aStr + "的对应值不唯一";
                            log.info(errorReason);
                            return new CheckExcelResult(false, errorReason);
                        }
                    }
                } else {
                    if (!valueMap.containsKey("")) {
                        valueMap.put("", bStr);
                    } else {
                        if (!valueMap.get("").equals(bStr)) {
                            errorReason = "不符合组合字段规则:字段组合" + columnListA + "的值相同时,字段组合" + columnListB + "的值必须唯一。 这里" + aStr + "的对应值不唯一";
                            log.info(errorReason);
                            return new CheckExcelResult(false, errorReason);
                        }
                    }
                }
            }
        }
        return new CheckExcelResult(true, errorReason);
    }

    /**
     * 判断字符串是不是整数或者小数
     */
    public static boolean isNumeric(String str) {
        if (!StringUtils.hasText(str)) return false;
        return str.matches("-?[0-9]+.*[0-9]*");
    }

    /**
     * 获取multipartFile文件大小(MB)
     *
     * @param multipartFile multipart格式文件传入
     */
    public static long getMultipartFileSize(MultipartFile multipartFile) {
        long fileSize = multipartFile.getSize();
        return fileSize / 1048576;
    }

    /**
     * 获取单个单元格的数据,转换为string
     */
    public static String getCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CheckExcelResult {
        private Boolean result;

        private String errorReason;
    }
}
