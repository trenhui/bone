package com.bone.lowcode.infra.application.upload.impl;

import cn.hutool.core.util.NumberUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.read.listener.PageReadListener;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.read.metadata.property.ExcelReadHeadProperty;
import com.bone.metadata.sdk.domain.annotation.UpdateTime;
import com.bone.core.util.JsonUtil;
import com.bone.lowcode.infra.application.task.AlertRobotManager;
import com.bone.lowcode.infra.application.upload.BaseExcepDto;
import com.bone.lowcode.infra.application.upload.FileUploadAction;
import com.bone.lowcode.infra.application.upload.UploadThreadLocal;
import com.bone.lowcode.infra.application.upload.dto.OptionSetImportDto;
import com.bone.lowcode.infra.application.upload.dto.OptionSetUploadData;
import com.bone.lowcode.infra.domain.service.OptionSetService;
import com.bone.lowcode.infra.domain.valueobject.OptionSetNodeEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.FileUploadRecord;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.OptionSet;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.FileUploadRecordMapper;
import com.bone.core.util.PkListUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OptionSetImportHandler extends HandlerBase implements FileUploadAction {
    @Resource
    private FileUploadRecordMapper uploadRecordMapper;

    @Autowired
    private OptionSetService optionSetService;

    @Override
    public Class getModelClass() {
        return OptionSetImportDto.class;
    }

    @Override
    public String getBizType() {
        return "optionSet";
    }

    /**
     * 可以设置一些上下文什么的
     *
     * @param record
     */
    @Override
    public void prepare(FileUploadRecord record) {
        OptionSetUploadData data = JsonUtil.fromJson(record.getData(), OptionSetUploadData.class);

        if(StringUtils.isBlank(data.getOptionSetId())){
            throw new RuntimeException("optionSetId is null");
        }

        if(!NumberUtil.isNumber(data.getOptionSetId())){
            throw new RuntimeException("optionSetId is not number");
        }
        //可以把threadLocal

        log.info("准备结束");
    }

    /**
     * 将excel 文件读到记录中
     *
     * @param record
     * @param fileInputStream
     * @return
     */
    @Override
    public List readToRecord(FileUploadRecord record,
                                           InputStream fileInputStream) {
        //取出上传类型
        OptionSetUploadData data = JsonUtil.fromJson(record.getData(), OptionSetUploadData.class);

        try (Workbook workbook = WorkbookFactory.create(fileInputStream)){
            Sheet sheet = workbook.getSheetAt(0); // 获取第一个工作表

            //1. 首先要获取第一列字段，检查是否在parent的自定义列中
            Row title = sheet.getRow(0);
            //获取列名称
            List<String> titleList = getRowValue(title, null);
            int colCount = titleList.size();
            boolean hasOpen = false;
            //此处要检测是否和规则是同样的标题类型
            if (colCount < 2 || !titleList.get(0).equals("选项值标识") || !titleList.get(1).equals("选项值名称")) {
                throw new RuntimeException("文件标题行错误!");
            }

            //是否带有启用状态列
            if (colCount > 2) {
                if(titleList.get(titleList.size()-1).equals("启用状态")) {
                    hasOpen = true;
                }
            }

            //获取选项集
            OptionSet optionSet = optionSetService.getById(Long.valueOf(data.getOptionSetId()));
            List<String> extraPropertyKeys = optionSet.getExtraPropertyKeyList();

            //用于记录这次上传的文件的专属字段
            List<String> extraColList = new ArrayList<>();
            for (int i = 2; i < colCount; i++) {
                if (i == colCount-1 && hasOpen) {
                    break;
                }
                if (!extraPropertyKeys.contains(titleList.get(i))) {
                    throw new RuntimeException("文件标题行错误!");
                }
                extraColList.add(titleList.get(i));
            }

            //将列移除方便之后遍历
            sheet.removeRow(title);

            //2. 开始读取第二行开始的数据。和输入需要一一对应，字段多则忽略。与此同时需要检测规则设置中的重复情况。
            List<OptionSet> sheetValueList = new ArrayList<>();
            for (Row row : sheet) {
                List<String> rowValueList = getRowValue(row, colCount);

                boolean allEmpty = true;
                for (int i = 0; i < colCount; i++) {
                    if (!StringUtils.isBlank(rowValueList.get(i))) {
                        allEmpty = false;
                        break;
                    }
                }
                if (allEmpty) {
                    continue;
                }

                //进行数据组装
                OptionSet rowOptionSet = new OptionSet();
                rowOptionSet.setCode(rowValueList.get(0));
                rowOptionSet.setName(rowValueList.get(1));
                rowOptionSet.setNodeType(OptionSetNodeEnum.LEAF_NODE.getCode());
                rowOptionSet.setParentId(Long.valueOf(data.getOptionSetId()));
                rowOptionSet.setDeleted((byte) 0);

                Map<String, String> extraValueMap = new HashMap<>();
                //开始过后面的字段
                for (int i = 2; i < colCount; i++) {
                    if (i == colCount-1 && hasOpen) {
                        break;
                    }

                    extraValueMap.put(extraColList.get(i-2), rowValueList.get(i));
                }

                if (hasOpen && "否".equals(rowValueList.get(colCount-1))) {
                    rowOptionSet.setStatus((byte) 0);
                } else {
                    rowOptionSet.setStatus((byte) 1);
                }

                rowOptionSet.setExtraProperty(JsonUtil.toJson(extraValueMap));
                sheetValueList.add(rowOptionSet);
            }

            log.info("取数结束" + sheetValueList);
            return sheetValueList;
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }


    /**
     * 获取每一行的数据并转化为字符串数组
     *
     * @param row
     * @param length
     * @return
     */
    protected List<String> getRowValue(Row row, Integer length) {
        List<String> valueList = new ArrayList<>();
        //有长度的是数据行
        if (length != null) {
            for (int i = 0; i < length; i++) {
                Cell cell = row.getCell(i);
                if (cell == null) {
                    valueList.add("");
                } else {
                    valueList.add(getCellValue(cell));
                }
            }
            return valueList;
        }

        //没有长度的是标题行
        for (Cell cell : row) {
            valueList.add(getCellValue(cell));
        }

        return valueList;
    }


    public byte[] downloadTemplate(Long optionSetId) {
        //获取选项集，然后生成excel
        OptionSet optionSet = optionSetService.getById(optionSetId);

        List<String> extraPropertyKeys = optionSet.getExtraPropertyKeyList();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");

            // 创建标题行
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("选项值标识");
            headerRow.createCell(1).setCellValue("选项值名称");

            int column = 2;
            for (String fieldName : extraPropertyKeys) {
                headerRow.createCell(column).setCellValue(fieldName);
                column++;
            }

            headerRow.createCell(column).setCellValue("启用状态");

            System.out.println("Excel 文件生成成功！");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            workbook.write(baos);
            workbook.close();
            return baos.toByteArray();
        } catch (IOException e) {
            System.out.println("生成 Excel 文件时出现错误: " + e);
            return null;
        }
    }


    /**
     * 获取单个单元格的数据
     * 转换为string，仅限简单类型
     *
     * @param cell
     * @return
     */
    protected String getCellValue(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }


    /**
     * 检查数据合法性
     *
     * @param record
     * @param list
     * @return
     */
    @Override
    public boolean checkRecord(FileUploadRecord record, List list) {

        boolean rs = true;
        List<OptionSetImportDto> errorList = new ArrayList<>();

        //首先code和name不能为空，先进行这个判断
        boolean codeEmpty = false;
        boolean nameEmpty = false;
        //用于存储见到过的code,判断是否重复
        List<String> codeList = new ArrayList<>();
        for (int i = 0; i<list.size(); i++) {
            OptionSet optionSet = (OptionSet) list.get(i);
            if(StringUtils.isBlank(optionSet.getCode())){
                codeEmpty = true;
            } else {
                codeList.add(optionSet.getCode());
            }
            if(StringUtils.isBlank(optionSet.getName())){
                nameEmpty = true;
            }
        }

        if (codeEmpty || nameEmpty) {
            rs = false;
            OptionSetImportDto error  = new OptionSetImportDto();
            error.setCode("??");
            error.setName("??");
            error.setErrorMsg("部分选项值标识、选项值名称为空。");
            errorList.add(error);
        }

        Map<String, Long> frequencyMap = codeList.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        //筛选出全部的重复code
        for (String code : frequencyMap.keySet()) {
            if (frequencyMap.get(code) > 1) {
                rs = false;
                OptionSetImportDto error  = new OptionSetImportDto();
                error.setCode(code);
                error.setName("??");
                error.setErrorMsg("选项值标识 " + code + " 重复了 " + frequencyMap.get(code) + " 次。");
                errorList.add(error);
            }
        }

        if(!rs){
            try {
                uploadErrorFileTooss(record, errorList);
            } catch (IOException e) {
                log.error("uploadErrorFileTooss error ",e);
                throw new RuntimeException("报错文件存储oss错误");
            }
        }

        log.info("校验结束" + errorList);

        return rs;
    }

    /**
     * 保存数据
     * 注意可以更新进度
     *
     * @param record
     * @param list
     */
    @Override
    public boolean doSave(FileUploadRecord record, List list) {
        OptionSetUploadData data = JsonUtil.fromJson(record.getData(), OptionSetUploadData.class);
        //获取选项集
        OptionSet optionSet = optionSetService.getById(Long.valueOf(data.getOptionSetId()));

        boolean rs= true;
        List<OptionSet> insertList = new ArrayList<>();
        for(int i = 0; i < list.size(); i++){
            OptionSet dto = (OptionSet) list.get(i);
            insertList.add(dto);
        }

        List<List<OptionSet>>  subList =  PkListUtil.split(insertList,500);
        Integer batchSize = subList.size();
        for(int i = 0;i<batchSize;i++){
            log.info("批次" + i);
            try {
                //delete by code and parentId
                optionSetService.updateAndInsert(subList.get(i));
                log.info("成功存储批次" + i);

                BigDecimal percent = BigDecimal.valueOf(i + 1).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(batchSize),2,BigDecimal.ROUND_HALF_UP);
                updatePercent(record.getId(), percent);

                alertRobotManager.doAlertAsyncDefault(
                        String.format("- 选项集文件上传中: %s。 上传进度%s%%", record.getFileName(), percent.toString()));
            } catch (Exception e) {
                log.info("出现错误" + e);
                alertRobotManager.doAlertAsyncDefault(
                        String.format(" 告警 --- 选项集文件上传失败！ %s", record.getFileName()));
                throw e;
            }
        }
        return rs;
    }



    private void updatePercent(Long id ,BigDecimal percent){
        FileUploadRecord record = new FileUploadRecord();
        record.setId(id);
        record.setPercent(percent);
        uploadRecordMapper.updateById(record);
    }


}
