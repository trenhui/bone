package com.bone.tpa.push.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.result.Result;
import com.bone.metadata.sdk.enums.ExtendFieldModelCode;
import com.bone.metadata.sdk.enums.FieldModelDefine;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.facade.feign.PageModelConfigFeign;
import com.bone.tpa.facade.request.OptionWithCodeQueryRequest;
import com.bone.tpa.facade.vo.OptionSetDTO;
import com.bone.tpa.push.service.PageModelConfigService;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PageModelConfigServiceImpl implements PageModelConfigService {

    @Autowired
    protected PageModelConfigFeign pageModelConfigFeign;


    @Override
    public OptionSetDTO getOptionSet(String pageBizCode, String bizIdentityCode, FieldModelDefine domainDefine, String code) {
        List<ExtendFieldModelCode> pageModelCodeList =  ExtendFieldModelCode.getByModelDefine(domainDefine);
        List<String> modelCodeList =pageModelCodeList.stream().map(ExtendFieldModelCode::getCode).collect(Collectors.toList());
        if(PkListUtil.isEmpty(modelCodeList)){
            throw new RuntimeException("modelCodeList is empty");
        }


        OptionWithCodeQueryRequest request = new OptionWithCodeQueryRequest();
        request.setBizIdentityCode(bizIdentityCode);
        request.setModeCodeList(modelCodeList);
        request.setModelCodeMockTag(pageBizCode+"-"+String.join("",modelCodeList));
        request.setFieldCode(pageBizCode);
        request.setOptionCode(code);
        Result<OptionSetDTO> remoteRs=   pageModelConfigFeign.queryCollectionByOptionCode(request);
        log.info("getOptionSet,param:{},return:{}", JSONObject.toJSONString(request),JSONObject.toJSONString(remoteRs));
        if(!remoteRs.getSuccess() || 200 != remoteRs.getCode()){
            log.error("getOptionSet error ,request:{},return:{}",JSONObject.toJSONString(request),JSONObject.toJSONString(remoteRs));
            throw new TpaBizException("查询选项集失败" + remoteRs.getMessage() + "," + code);
        }
        OptionSetDTO dto = remoteRs.getData();
        return dto;
    }
}
