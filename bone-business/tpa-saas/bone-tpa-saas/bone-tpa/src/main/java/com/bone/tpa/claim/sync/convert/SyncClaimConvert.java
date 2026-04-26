package com.bone.tpa.claim.sync.convert;

import com.bone.metadata.sdk.enums.FieldModelDefine;
import com.bone.core.util.DateParserUtil;
import com.bone.tpa.api.enums.*;
import com.bone.tpa.api.vo.ClaimConfig;
import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import com.bone.tpa.claim.sync.Constant.ClaimFieldsConstat;
import com.bone.tpa.claim.sync.Context;
import com.bone.tpa.claim.sync.SyncBaseTool;
import com.bone.tpa.core.util.ExtraStoreUtil;
import com.bone.tpa.facade.vo.OptionSetDTO;
import com.bone.tpa.sdk.adjustment.enums.PolicyType;
import com.bone.tpa.sdk.claim.enums.ClmProcessType;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.util.OptionSetObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SyncClaimConvert extends SyncBaseTool {

    public void convertToClaim(ClaimDetailSyncVO syncVO,
                              Claim claim,
                              ClaimConfig claimConfig ,
                              boolean insertTag, List<String> clearField, Context context ){
        if(insertTag){
            if(syncVO.getClaimDetailId() == null){
                throw new IllegalArgumentException("claimDetailId is null");
            }
            if( claimConfig == null){
                throw new IllegalArgumentException("claimConfig is null");
            }
            //关联批次号
            claim.setRelatedId(syncVO.getClaimHeadInfo().getClaimheadnumber());
            claim.setClaimDetailUuid(syncVO.getClaimDetailId());
            //设置tpa系统来源
            claim.setSignSystemSource(SignSystemSourceEnum.TPA.getCode());
            claim.setBatchNo(syncVO.getClaimHeadInfo().getClaimheadnumber().toString());
            if(syncVO.getSourceType() == null){
                throw new IllegalArgumentException("sourceType(线上，线下） is null");
            }
            SourceType sourceTypeEnum=    SourceType.getEnumDesc(syncVO.getSourceType());
            if( sourceTypeEnum == null){
                throw new IllegalArgumentException("sourceType(线上，线下）无法匹配");
            }
            claim.setSource( sourceTypeEnum.getCode());
            String coverType =  syncVO.getInsuranceCoverage();
            if(coverType == null){
                //默认团险
                coverType = "团险";
            }
            PolicyType policyType =  PolicyType.getByDesc(coverType);
            if( policyType == null){
                throw new IllegalArgumentException("insuranceCoverage匹配失败，insuranceCoverage="+coverType);
            }
            //团险、个险中文
            claim.setBizType(policyType.getCode().toString());

            //初审赔案类型 ，线上，线下，线上线下
            // claim.setCfgClaimAuditType();
            //自动化作业类型
            if(claimConfig.getCanBeAutomation() == null){
                throw new IllegalArgumentException("是否自动化作业为空 is null");
            }
            claim.setCfgAutoType(claimConfig.getCanBeAutomation());
            //作业类型
            //其实没啥用
            if(claimConfig.getBizType() == null){
                throw new IllegalArgumentException("作业类型为空 is null");
            }
            if(claimConfig.getBizType() == 1){
                throw new IllegalArgumentException("作业类型为空 为 tpa ，不能流入saas");
            }
            claim.setCfgBizType(claimConfig.getBizType());
//            claim.setCfgBizType(3);
            //业务录入类型
            if(claimConfig.getBizInputType() == null){
                throw new IllegalArgumentException("业务录入类型为空 is null");
            }
            CfgBizInputTypeEnum matchBizInputType =   CfgBizInputTypeEnum.getByCode(claimConfig.getBizInputType());
            if( matchBizInputType == null){
                throw new IllegalArgumentException("业务录入类型无法匹配");
            }
            claim.setCfgBizInputType(matchBizInputType.getCode());
            //影像件是否分类
            if(claimConfig.getImageClassify() == null){
                throw new IllegalArgumentException("影像件是否分类为空 is null");
            }
            claim.setCfgImageClassify(claimConfig.getImageClassify() );
            //电票需验真
            if(claimConfig.getNeedVerify() == null){
                throw new IllegalArgumentException("电票需验真为空 is null");
            }
            claim.setCfgEinvoiceVerify(claimConfig.getNeedVerify()  );
            //流程类型
            if(claimConfig.getClmProcess() == null){
                throw new IllegalArgumentException("流程类型为空 is null");
            }
            //是否需要初审
            if(claimConfig.getCaseClaimAudit() == null){
                throw new IllegalArgumentException("是否需要初审 is null");

            }
            claim.setCfgNeedPreApprove(claimConfig.getCaseClaimAudit()  );

            ClmProcessType processTypeEnum =    ClmProcessType.getEnum(claimConfig.getClmProcess());
            if( processTypeEnum == null){
                throw new IllegalArgumentException("流程类型无法匹配");
            }
            claim.setProcessType(processTypeEnum.getCode());
            claim.setPolicyNo(syncVO.getPolicyNo());

        }
        claim.setInsurerClaimNo(nullIfEmpty(syncVO.getCaseNo()));
        claim.setSlipPersonPsc(nullIfEmpty(syncVO.getSlipPersonPsc()));
        claim.setSerialNumber(nullIfEmpty(syncVO.getSerialNumber()));
        //vip
        String vipSignCn =  syncVO.getVipSignCn();
        OptionSetDTO vipsignOption =  matchCollectionCodeForSync(vipSignCn, ClaimFieldsConstat.VIPSIGN,
                context, FieldModelDefine.赔案,true,context.getHintClaimDetail());
        String vipSignCode =getOptionSetDTOCode(vipsignOption);

        claim.setVipSign(vipSignCode);

        ExtraStoreUtil.fillExtraStoreWithOrigin(claim,ClaimFieldsConstat.VIPSIGN, vipsignOption);
        claim.setVipSignCn(vipSignCn);


        //accidentTime
        if(StringUtils.isNotBlank(syncVO.getAccidentTime() )){
            try {
                claim.setOutInsureTime(DateParserUtil.parseDate(syncVO.getAccidentTime()));
            } catch (Exception e) {
                context.getHintClaimDetail().put( ClaimFieldsConstat.OUT_INSURE_TIME,"日期时间格式错误:"+syncVO.getAccidentTime() );
            }
        }
        //accidentTypeCn 出险原因中文
        // ClaimFieldsConstat.OUT_INSURE_TIME
        String accidentTypeCn =   syncVO.getAccidentTypeCn();
        OptionSetDTO accidentTypeCodeOption =  matchCollectionCodeForSync(accidentTypeCn,ClaimFieldsConstat.OUT_INSURE_TYPE,
                context,FieldModelDefine.赔案,true,context.getHintClaimDetail());
        claim.setOutInsureType(getOptionSetDTOCode(accidentTypeCodeOption));
        claim.setOutInsureTypeCn(accidentTypeCn);
        ExtraStoreUtil.fillExtraStoreWithOrigin(claim,ClaimFieldsConstat.OUT_INSURE_TYPE, accidentTypeCodeOption);

        //outAccidentProvice
        //outAccidentCity
        //outoutAccidentArea
        //通过主数据获取
        String outRegionCn   =  matchProviceCityAreaWithChinese(syncVO.getOutAccidentProvice(),
                syncVO.getOutAccidentCity(),
                syncVO.getOutAccidentArea(),3 );
        claim.setOutInsureRegion(outRegionCn);

        claim.setOutInsureAddress(nullIfEmpty(syncVO.getOutAccidentAddress()));
        // signCompanyName
        claim.setSourceCode(nullIfEmpty(syncVO.getSourceCompanyCode()));
        claim.setSourceCodeCn(nullIfEmpty(syncVO.getSourceCompanyName()));
        //保司保单号
        claim.setInsurerPolicyNo(nullIfEmpty(syncVO.getInsurancePolicyNo()));
        //保单开始日期

        claim.setPolicyStartDate(nullIfEmpty(syncVO.getPolicyStartDate()));
        claim.setPolicyEndDate(nullIfEmpty(syncVO.getPolicyEndDate()));
        claim.setPolicyRelation(nullIfEmpty(syncVO.getPolicyRelation()));
        try {
            claim.setRelationType(Integer.valueOf(syncVO.getRelationType()));
        } catch (Exception e) {
            claim.setRelationType(null);
        }

        //保单结束日期
        if(syncVO.getSlipAttribute()!= null){
            SlipAttribute match =   SlipAttribute.getByCode(syncVO.getSlipAttribute());
            if( match!= null){
                claim.setPolicyAttribute(match.getCode());
            }else{
                throw new IllegalArgumentException("保单属性匹配失败,value:"+syncVO.getSlipAttribute());
            }
        }
        //保险公司名称
        claim.setInsuranceName(syncVO.getInsuranceName());
        claim.setBranchName(nullIfEmpty(syncVO.getBranchName()));
        claim.setInsureName(nullIfEmpty(syncVO.getInsureName()));

        //各级操作人和操作时间不应该由tpa返回，我们就不收了
        
        if(syncVO.getCollectPersonInfo()!= null ){
            String  collectTypeCn = syncVO.getCollectPersonInfo().getCollectTypeCn();
            if(StringUtils.isBlank(syncVO.getCollectPersonInfo().getCollectTypeCn())){
                claim.setCollectType( "");
            }else{
                CollectTypeEnum collectTypeEnum = CollectTypeEnum.getEnumByTransfer(syncVO.getCollectPersonInfo().getCollectTypeCn());
                if( collectTypeEnum == null){
                    throw new IllegalArgumentException("收款人信息匹配失败,value:"+syncVO.getCollectPersonInfo().getCollectTypeCn());
                }
                claim.setCollectType( collectTypeEnum.getCode());
            }

        }else{
            claim.setCollectType( "");
        }
       //填充赔案的扩展字段
        Map<String,Object> extendFieldMap = mapToExtendMap(FieldModelDefine.赔案,context,claim,
                context.getBizIdentityCode(), context.getClaimNumber(),
                context.getHintClaimDetail(),clearField,
                context.getClaimExtMap(),syncVO.getExtMap(),claim);
        claim.setExtraProperties(extendFieldMap);


    }


    public void convertToSyncVo(ClaimDetailSyncVO syncVO,
                               Claim claim  ,Context context ){
        syncVO.setClaimDetailId(claim.getClaimDetailUuid());
        syncVO.setClaimNumber(claim.getId());
        SourceType sourceTypeEnum = SourceType.getEnumCode(claim.getSource());
        //线上线下
        syncVO.setSourceType(sourceTypeEnum == null?"":sourceTypeEnum.getDesc());

        PolicyType policyType =     PolicyType.getByCode(Integer.valueOf(claim.getBizType()));
        syncVO.setInsuranceCoverage(policyType == null?"":policyType.getDesc());

        syncVO.setPolicyNo(claim.getPolicyNo());
        syncVO.setCaseNo(claim.getInsurerClaimNo());
        syncVO.setSlipPersonPsc(claim.getSlipPersonPsc());
        syncVO.setSerialNumber(claim.getSerialNumber());
       //ClaimFieldsConstat.VIPSIGN
        OptionSetObject vipsignStore =  ExtraStoreUtil.getOptionSetValue(ClaimFieldsConstat.VIPSIGN,
                claim.getExtraStore());
        if( vipsignStore != null){
            syncVO.setVipSignCn(vipsignStore.getName());
        }else{
            syncVO.setVipSignCn(claim.getVipSignCn());
        }

       // syncVO.setVipSignCn(claim.getVipSignCn());

        syncVO.setAccidentTime(DateParserUtil.formatDate(claim.getOutInsureTime(),"yyyy-MM-dd"));

        OptionSetObject accidentTypeExtraStore =  ExtraStoreUtil.getOptionSetValue(ClaimFieldsConstat.OUT_INSURE_TYPE,
                claim.getExtraStore());
        if( accidentTypeExtraStore != null){
            syncVO.setAccidentTypeCn(accidentTypeExtraStore.getName());
        }else{
            syncVO.setAccidentTypeCn(claim.getOutInsureTypeCn());
        }

        syncVO.setPolicyRelation(claim.getPolicyRelation());
        if(claim.getRelationType() != null){
            syncVO.setRelationType(String.valueOf(claim.getRelationType()));
        }

        //syncVO.setAccidentTypeCn(claim.getOutInsureTypeCn());
        List<String> regionCodeList =   getRegionCn(claim.getOutInsureRegion());
        syncVO.setOutAccidentProvice(regionCodeList.get(0));
        syncVO.setOutAccidentCity(regionCodeList.get(1));
        syncVO.setOutAccidentArea(regionCodeList.get(2));
        syncVO.setSourceCompanyName(claim.getSourceCodeCn());
        syncVO.setSourceCompanyCode(claim.getSourceCode());
        syncVO.setInsurancePolicyNo(claim.getInsurerPolicyNo());
        syncVO.setBranchName(claim.getBranchName());
        syncVO.setOutAccidentAddress(claim.getOutInsureAddress());
        syncVO.setPolicyStartDate(claim.getPolicyStartDate());
        syncVO.setPolicyEndDate(claim.getPolicyEndDate());
        SlipAttribute slipAttribute = SlipAttribute.getByCode(claim.getPolicyAttribute());
        syncVO.setSlipAttribute(slipAttribute == null?"":slipAttribute.getCode());

        Map<String,String> returnExtMap = extendMapToTpaMap(claim,claim.getBizIdentityCode(),
                FieldModelDefine.赔案,context.getClaimExtMap(),claim.getExtraProperties() );
        syncVO.setInsuranceName(claim.getInsuranceName());
        syncVO.setInsureName(claim.getInsureName());
        syncVO.setExtMap(returnExtMap);

    }
}
