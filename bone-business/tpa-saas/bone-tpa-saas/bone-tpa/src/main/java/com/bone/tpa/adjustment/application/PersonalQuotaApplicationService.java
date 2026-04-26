package com.bone.tpa.adjustment.application;

import cn.hutool.db.Page;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.claim.application.request.FileUploadRequestExtend;
import com.bone.tpa.intelligent.adjustment.model.GetPersonalQuotaListReq;
import com.bone.tpa.intelligent.adjustment.model.PersonInfo;
import com.bone.tpa.intelligent.adjustment.model.PersonalQuotaChangeResponse;
import com.bone.tpa.intelligent.adjustment.limit.service.PersonalQuotaService;
import com.bone.tpa.intelligent.adjustment.infrastructure.util.CommonUtil;
import com.bone.tpa.claim.infrastructure.external.InfraClient;
import com.bone.tpa.sdk.adjustment.model.PersonalQuota;
import com.bone.tpa.sdk.adjustment.model.PersonalQuotaChange;
import com.bone.tpa.sdk.adjustment.model.PersonalQuotaChangeBatch;
import com.bone.tpa.sdk.adjustment.response.UploadDataRule;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.enums.FileTypeEnum;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
public class PersonalQuotaApplicationService {

    @Autowired
    private InfraClient infraClient;
    @Autowired
    private PersonalQuotaService personalQuotaService;

    public boolean uploadPersonQuota(MultipartFile file, FileUploadRequestExtend request) throws IOException {
        FileTypeEnum fileTypeEnum = FileTypeEnum.getByConfigCode(request.getType());
        if (!FileTypeEnum.EXCEL.equals(fileTypeEnum)) {
            throw new TpaBizException("仅支持excel文件规则校验");
        }

        UploadDataRule rule = getUploadDataRule(request);
        CommonUtil.CheckExcelResult checkResult = CommonUtil.checkFile(file, rule);
        if (!checkResult.getResult()) {
            throw new TpaBizException("excel文件规则校验未通过,原因:" + checkResult.getErrorReason());
        }
        log.info("excel文件规则校验通过");

        return personalQuotaService.uploadPersonQuota(file, request.getPolicyNo(), request.getOperationType());
    }

    private UploadDataRule getUploadDataRule(FileUploadRequestExtend request) {
        Result<Map<String, Object>> result = infraClient.getUploadComponent(request.getType(), request.getConfigId());
        if (result == null || !result.getSuccess()) {
            throw new TpaBizException(BizErrorCode.OUTER_CLIENT_ERROR, "Get no config for " + request.getType() + " id:" + request.getConfigId());
        }
        ObjectMapper objectMapper = new ObjectMapper();
        UploadDataRule rule = objectMapper.convertValue(result.getData(), UploadDataRule.class);
        return rule;
    }

    public PageResult<String> getInsuredNameListByPolicyNo(String policyNo, Integer pageNo, Integer pageSize) {
        PageResult<String> result = personalQuotaService.getInsuredNameListByPolicyNo(policyNo, pageNo, pageSize);
        return result;
    }

    public PageResult<String> getOperateBatchList(String policyNo, Integer pageNo, Integer pageSize) {
        return personalQuotaService.getOperateBatchList(policyNo, pageNo, pageSize);
    }

    public PageResult<PersonalQuota> getPersonalQuotaList(GetPersonalQuotaListReq request) {
        return personalQuotaService.getPersonalQuotaList(request);
    }

    public PageResult<PersonalQuotaChangeResponse> getPersonalQuotaChangeList(PersonInfo personInfo) {
        PageResult<PersonalQuotaChange> changePageResult = personalQuotaService.getPersonalQuotaChangeList(personInfo);

        List<PersonalQuotaChange> changeList = changePageResult.getData();
        List<Long> batchIdList = changeList.stream().map(PersonalQuotaChange::getBatchId).toList();

        Map<Long, String> batchMap = personalQuotaService.getBatchRecord(batchIdList);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<PersonalQuotaChangeResponse> responseList = changeList.stream().map(i -> {
            PersonalQuotaChangeResponse response = new PersonalQuotaChangeResponse();
            BeanUtils.copyProperties(i, response);
            response.setId(i.getId());
            response.setCreateTime(sdf.format(i.getCreateTime()));
            response.setBatchName(batchMap.getOrDefault(i.getBatchId(), "批次名异常"));
            return response;
        }).toList();

        PageResult<PersonalQuotaChangeResponse> result = new PageResult<>(responseList, changePageResult.getCurrPage(),
                changePageResult.getPageSize(), changePageResult.getTotalCount());

        return result;
    }
}
