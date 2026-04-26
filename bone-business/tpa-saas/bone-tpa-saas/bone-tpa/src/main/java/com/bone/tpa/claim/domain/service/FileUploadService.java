package com.bone.tpa.claim.domain.service;


import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.bean.copier.MapToBeanCopier;
import com.bone.core.result.PageResult;
import com.bone.core.result.QueryParam;
import com.bone.core.result.Result;
import com.bone.core.util.JsonUtil;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.enums.CheckTypeEnum;
import com.bone.tpa.claim.application.request.FileUploadRequest;
import com.bone.tpa.claim.application.request.QueryListRequest;
import com.bone.tpa.claim.application.request.UploadDataVO2;
import com.bone.tpa.claim.application.request.UploadImageVO;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.dao.FileUploadRecordRepository;
import com.bone.tpa.sdk.dao.SignRecordRepository;
import com.bone.tpa.claim.infrastructure.external.InfraClient;
import com.bone.tpa.claim.util.OssUtil;
import com.bone.core.util.BizContextUtils;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.facade.request.TpaAddLogRequest;
import com.bone.tpa.sdk.claim.enums.*;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimImage;
import com.bone.tpa.sdk.claim.model.FileUploadRecord;
import com.bone.tpa.sdk.claim.model.SignRecord;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ss_sign_record Service 接口
 *
 * @author 0
 */
@Service
@Slf4j
public class FileUploadService {
    @Autowired
    private SignRecordRepository signRecordRepository;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private FileUploadRecordRepository fileUploadRecordRepository;

    @Autowired
    private OssUtil ossUtil;

    @Autowired
    private InfraClient infraClient;

    @Autowired
    private TpaDataSyncFeign tpaDataSyncFeign;

    @Autowired
    private ClaimImageService claimImageService;


    @Resource
    private FileAnalyzeService fileAnalyzeService;

    /**
     * 根据id去获取已经上传的文件并且获取分析结果
     **/
    public void uploadFile(FileUploadRequest request, MultipartFile file) {
        log.info("File config " + request.getConfigId() + ", file type " + request.getType());

        //要去调用文件上传的对应配置
        Result<Map<String, Object>> configResult = infraClient.getUploadComponent(request.getType(), request.getConfigId());
        if (configResult == null || !configResult.getSuccess() || configResult.getData() == null) {
            throw new TpaBizException(BizErrorCode.OUTER_CLIENT_ERROR, "Get no config for " + request.getType() + " id:" + request.getConfigId());
        }
        Map<String, Object> configMap = configResult.getData();
        log.info(configMap.toString());

        //检查文件类型是否正确
        String lowercaseFileName = file.getOriginalFilename().toLowerCase();

        log.info("File name is " + lowercaseFileName);
        FileTypeEnum fileTypeEnum = FileTypeEnum.getByConfigCode(request.getType());
        if (!fileTypeEnum.equals(FileTypeEnum.EXCEL) && !fileTypeEnum.equals(FileTypeEnum.IMAGE)) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "该接口只能上传数据或影像件压缩包");
        }

        //检查文件大小
        if (configMap.containsKey("fileMaxSize") && file.getSize() > (Integer) configMap.get("fileMaxSize")) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "File size too large" + file.getSize());
        }

        //获取格式并且检验
        String fileFormat = (String) configMap.get("fileFormat");
        if (fileFormat != null && !fileFormat.isEmpty()) {
            String[] format = fileFormat.split(",");

            boolean formatFlag = false;
            for (String ext : format) {
                if (lowercaseFileName.endsWith(ext)) {
                    formatFlag = true;
                    break;
                }
            }
            if (!formatFlag) {
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "File type error, " + lowercaseFileName + " not allowed!");
            }
        }


        //获取导入方式并且检验
        List<Byte> importTypeList = ((List<Byte>) configMap.get("importTypeList"));
        if (!importTypeList.contains(request.getImportType())) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "Import type error, " + request.getImportType() + " not allowed!");
        }

        SignRecord signRecord = signRecordRepository.findById(request.getRelatedId());

        //根据文件类型不同进行不同处理，非数据文件就直接上传
        FileUploadRecord record = new FileUploadRecord();

        record.setTenantId(request.getTenantId());
        record.setFileName(file.getOriginalFilename());
        record.setFileType(fileTypeEnum.getCode());

        record.setUploadScene((String) configMap.get("title"));
        record.setRelatedId(request.getRelatedId());
        record.setRelatedModel(BizModelEnum.SIGN_RECORD.getCode());
        record.setSuccess(0);
        record.setOperator(BizContextUtils.getUser());


        //上传至oss
        try {
            String path = ossUtil.upload(file.getOriginalFilename(), file.getInputStream(), signRecord.getBatchNo());
            record.setFilePath(path);
            record.setSuccess(1);
        } catch (IOException e) {
            log.error(e.getMessage());
            return;
        }

        //然后根据文件类型的不同走不同的处理逻辑
        //记得要更新文件状态
        if (fileTypeEnum.equals(FileTypeEnum.EXCEL)) {
            //转换为数据文件上传配置
            MapToBeanCopier mapToBeanCopier = new MapToBeanCopier(configMap, new UploadDataVO2(), UploadDataVO2.class, new CopyOptions());
            UploadDataVO2 excelImportConfig = (UploadDataVO2) mapToBeanCopier.copy();
            //解析数据文件
            List<String> fileContent = fileAnalyzeService.analyzeExcel(file, excelImportConfig);
            //更新状态以及剩余字段
            if (fileContent == null) {
                record.setStatus(FileStatusEnum.EXCEL_ERROR.getCode());
            } else {
                record.setStatus(FileStatusEnum.EXCEL_WAITING.getCode());
            }
            record.setRemark(JsonUtil.toJson(fileContent));
            record.setCheckType(CheckTypeEnum.getByCode(excelImportConfig.getCheckType()).getValue());
            fileUploadRecordRepository.insert(record);
        } else if (fileTypeEnum.equals(FileTypeEnum.IMAGE)) {
            //转换为影像件上传配置
            MapToBeanCopier mapToBeanCopier = new MapToBeanCopier(configMap, new UploadImageVO(), UploadImageVO.class, new CopyOptions());
            UploadImageVO imageImportConfig = (UploadImageVO) mapToBeanCopier.copy();

            record.setStatus(FileStatusEnum.IMAGE_WAITING.getCode());
            record.setImportType(ImportTypeEnum.getByConfigCode(request.getImportType()).getValue());
            record.setSameFileRule(SameFileRuleEnum.getByConfigCode(imageImportConfig.getSameFileHandle()).getValue());
            Long id = fileUploadRecordRepository.insert(record);
            fileAnalyzeService.processImageZip(file, request.getRelatedId(), imageImportConfig, request.getImportType(), id);
        }
    }


    /**
     * 该接口由前端上传文件
     * 因此request中，文件url必须不为空
     **/
    public void batchUploadFile(FileUploadRequest request) {
        List<String> fileUrlList = request.getFileUrlList();
        if (fileUrlList == null || fileUrlList.isEmpty()) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "没有上传文件");
        }

        log.info("File config " + request.getConfigId() + ", file type " + request.getType());

        //要去调用文件上传的对应配置
        Result<Map<String, Object>> configResult = infraClient.getUploadComponent(request.getType(), request.getConfigId());
        if (configResult == null || !configResult.getSuccess() || configResult.getData() == null) {
            throw new TpaBizException(BizErrorCode.OUTER_CLIENT_ERROR, "Get no config for " + request.getType() + " id:" + request.getConfigId());
        }
        Map<String, Object> configMap = configResult.getData();
        log.info(configMap.toString());

        FileTypeEnum fileTypeEnum = FileTypeEnum.getByConfigCode(request.getType());

        if (!fileTypeEnum.equals(FileTypeEnum.PICTURE) && !fileTypeEnum.equals(FileTypeEnum.ATTACHMENT)) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "该接口只能上传图片或附件");
        }


        //检查总上传数量
        if (fileUrlList.size() > (Integer) configMap.get("maxCount")) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "File too much" + fileUrlList.size());
        }

        //这个接口的关联id是claimId
        Claim claim = claimRepository.findById(request.getRelatedId());

        //只有初审阶段才能提交
        if (!claim.getStage().equals(ClaimStageEnum.PRE_EXAM.getCode())) {
            throw new TpaBizException(BizErrorCode.BIZ_STAGE_ERROR, "赔案已经不是初审阶段，不能补充影像件");
        }

        Integer maxIndex = claimImageService.getBiggestIndex(claim.getId(), claim.getTenantId());

        for (String fileUrl : fileUrlList) {

            String[] fileUrlArray = fileUrl.split("/");
            String fileName = fileUrlArray[fileUrlArray.length-1];

            //检查文件类型是否正确
            String lowercaseFileName = fileName.toLowerCase();

            log.info("File name is " + lowercaseFileName);

            //前端上传管不了了，不管了
            //检查文件大小
//            if (configMap.containsKey("fileMaxSize") && file.getSize() > (Integer) configMap.get("fileMaxSize")) {
//                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "File size too large" + file.getSize());
//            }

            //获取格式并且检验
            String fileFormat = (String) configMap.get("fileFormat");
            if (fileFormat != null && !fileFormat.isEmpty()) {
                String[] format = fileFormat.split(",");

                boolean formatFlag = false;
                for (String ext : format) {
                    if (lowercaseFileName.endsWith(ext)) {
                        formatFlag = true;
                        break;
                    }
                }
                if (!formatFlag) {
                    throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "File type error, " + lowercaseFileName + " not allowed!");
                }
            }

            //这里不获取导入方式了
            //获取导入方式并且检验
//            List<Byte> importTypeList = ((List<Byte>) configMap.get("importTypeList"));
//            if (importTypeList != null && !importTypeList.contains(request.getImportType())) {
//                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "Import type error, " + request.getImportType() + " not allowed!");
//            }

            //根据文件类型不同进行不同处理，非数据文件就直接上传
            FileUploadRecord record = new FileUploadRecord();

            record.setTenantId(request.getTenantId());
            record.setFileName(fileName);
            record.setFileType(fileTypeEnum.getCode());

            record.setUploadScene((String) configMap.get("title"));
            record.setRelatedId(request.getRelatedId());
            record.setRelatedModel(BizModelEnum.CLAIM_DETAIL.getCode());
            record.setSuccess(0);
            record.setOperator(BizContextUtils.getUser());

            record.setFilePath(fileUrl);
            record.setSuccess(1);

            //然后根据文件类型的不同走不同的处理逻辑
            //记得要更新文件状态
            if (fileTypeEnum.equals(FileTypeEnum.PICTURE)) {
                record.setStatus(FileStatusEnum.IMAGE_COMPLETE.getCode());
                record.setImportType(ImportTypeEnum.ADD.getValue());
                record.setSameFileRule(SameFileRuleEnum.KEEP.getValue());
                record.setRemark(String.valueOf(request.getRelatedId()));
                fileUploadRecordRepository.insert(record);

                //然后这里添加影像件记录
                // 记录至赔案图片表
                ClaimImage claimImage = new ClaimImage();
                claimImage.setImageDetailId(String.valueOf(UUID.randomUUID()));
                claimImage.setImageName(fileName);
                claimImage.setImagePath(fileUrl);
                claimImage.setImageIndex(++maxIndex);
                claimImage.setRelatedId(claim.getId());
                claimImage.setTenantId(claim.getTenantId());
                claimImage.setImageType(String.valueOf(1));
                claimImage.setOcrFlag(0);
                claimImage.setSourceSystem(1);
                claimImage.setPushFlag(1);
                claimImageService.createNewClaimImage(claimImage);


                try {
                    TpaAddLogRequest tpaAddLogRequest = new TpaAddLogRequest();
                    tpaAddLogRequest.setClaimNumber(Long.valueOf(claim.getClaimNo()));
                    tpaAddLogRequest.setOperation("上传影像件");
                    tpaAddLogRequest.setRemark("ClaimNo: " + request.getRelatedId());
                    tpaAddLogRequest.setCreateBy(BizContextUtils.getUser());
                    tpaAddLogRequest.setCreateTime( (new Date()).getTime());

                    ApiResult<Map<String,Object>>  remoteRs = tpaDataSyncFeign.addLog(tpaAddLogRequest);
                    log.info("日志tpa同步结果:{}",remoteRs);
                    if(!remoteRs.isSuccess()){
                        throw new RuntimeException("新增日志失败："+remoteRs.getMessage());
                    }
                } catch (Exception e) {
                    log.warn("影像件上传日志tpa同步失败！");
                }

            } else if (fileTypeEnum.equals(FileTypeEnum.ATTACHMENT)) {
                fileUploadRecordRepository.insert(record);
            }
        }
    }


    public byte[] downloadTemplate(Long configId) {
        //要去调用文件上传的对应配置
        Result configResult = infraClient.getUploadComponent(FileTypeEnum.EXCEL.getConfigCode(), configId);
        if (configResult == null) {
            throw new TpaBizException(BizErrorCode.OUTER_CLIENT_ERROR, "pageConfig system error!");
        }
        if (!configResult.getSuccess()) {
            throw new TpaBizException(BizErrorCode.OUTER_CLIENT_ERROR, configResult.getMessage());
        }
        if (configResult.getData() == null) {
            throw new TpaBizException(BizErrorCode.OUTER_CLIENT_ERROR, "Get no config for id: " + configId);
        }

        //转换为数据文件上传配置
        MapToBeanCopier mapToBeanCopier = new MapToBeanCopier((Map<?, ?>) configResult.getData(), new UploadDataVO2(), UploadDataVO2.class, new CopyOptions());
        UploadDataVO2 importConfig = (UploadDataVO2) mapToBeanCopier.copy();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");

            // 创建标题行
            Row headerRow = sheet.createRow(0);
            int column = 0;
            for (String fieldName : importConfig.getFieldNameList()) {
                headerRow.createCell(column).setCellValue(fieldName);
                column++;
            }

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
     * 获取文件上传记录
     *
     * @return
     */
    public PageResult<FileUploadRecord> getFileUploadRecord(QueryListRequest request) {
        request.getQueryParams().add(new QueryParam("relatedId", request.getId()));
        request.getQueryParams().add(new QueryParam("tenantId", request.getTenantId()));

        Integer offset = (request.getPageNo() - 1) * request.getPageSize();

        return fileUploadRecordRepository.queryByCondition(request.getQueryParams(), request.getSortingFields(), offset, request.getPageSize(),
                "ss_file_upload_record", request.getBizIdentityCode());
    }

    public String parseResult(Integer success, String status) {
        StringBuilder result = new StringBuilder();

        if (success == 1) {
            result.append("上传成功！");
        } else {
            result.append("上传失败！");
        }

        FileStatusEnum statusEnum = FileStatusEnum.getByCode(status);

        if (statusEnum != null) {
            result.append(statusEnum.getValue());
        }

        return result.toString();
    }
}
