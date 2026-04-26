package com.bone.tpa.claim.application;

import com.bone.core.result.PageResult;
import com.bone.core.result.QueryParam;
import com.bone.tpa.claim.application.converter.FileUploadRecordConverter;
import com.bone.tpa.claim.application.dto.FileUploadRecordDTO;
import com.bone.tpa.claim.application.request.FileUploadRequest;
import com.bone.tpa.claim.application.request.QueryListRequest;
import com.bone.tpa.claim.domain.service.FileUploadService;
import com.bone.tpa.claim.infrastructure.log.SimpleLog;
import com.bone.tpa.sdk.claim.enums.FileTypeEnum;
import com.bone.tpa.sdk.claim.model.FileUploadRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 签收应用层服务
 */
@Service
@Transactional
public class FileUploadApplicationService {
    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private FileUploadRecordConverter fileUploadRecordConverter;

    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public Boolean uploadFile(FileUploadRequest request, MultipartFile file) {
        fileUploadService.uploadFile(request, file);
        return true;
    }

    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public Boolean batchUploadFile(FileUploadRequest request) {
        fileUploadService.batchUploadFile(request);
        return true;
    }


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public byte[] downloadTemplate(Long configId) {
        return fileUploadService.downloadTemplate(configId);
    }

    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public PageResult<FileUploadRecordDTO> getFileUploadRecord(QueryListRequest request) {
        PageResult<FileUploadRecord> pageResult = fileUploadService.getFileUploadRecord(request);

        //转化为DTO
        List<FileUploadRecordDTO> fileUploadRecordDTOList = new ArrayList<>();
        for (FileUploadRecord fileUploadRecord : pageResult.getData()) {
            FileUploadRecordDTO fileUploadRecordDTO = fileUploadRecordConverter.toDTO(fileUploadRecord);

            fileUploadRecordDTO.setResult(fileUploadService.parseResult(fileUploadRecord.getSuccess(), fileUploadRecord.getStatus()));
            FileTypeEnum fileType = FileTypeEnum.getByCode(fileUploadRecord.getFileType());
            if (fileType == null) continue;

            fileUploadRecordDTO.setFileType(fileType.getValue());

            if (fileType == FileTypeEnum.IMAGE || fileType == FileTypeEnum.PICTURE) {
                fileUploadRecordDTO.setExtraInfo("上传影像的案件：\n" + fileUploadRecord.getRemark());
            }

            fileUploadRecordDTOList.add(fileUploadRecordDTO);
        }

        PageResult<FileUploadRecordDTO> result = new PageResult<>(fileUploadRecordDTOList, pageResult.getCurrPage(), pageResult.getPageSize(), pageResult.getTotalCount());

        return result;
    }

    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public List<FileUploadRecordDTO> getFileUploadRecordForEvent(String id, String type, String tenantId) {
        QueryListRequest queryListRequest = new QueryListRequest();
        queryListRequest.setId(id);
        queryListRequest.setTenantId(tenantId);
        queryListRequest.getQueryParams().add(new QueryParam("file_type", type));

        return getFileUploadRecord(queryListRequest).getData();
    }
}
