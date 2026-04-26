package com.bone.tpa.test.adjust;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.bone.core.result.Result;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.adjustment.adapter.LiabilityController;
import com.bone.tpa.adjustment.adapter.LiabilityShareController;
import com.bone.tpa.adjustment.adapter.LiabilitySortController;
import com.bone.tpa.adjustment.adapter.PlanController;
import com.bone.tpa.intelligent.adjustment.dto.CoverageDTO;
import com.bone.tpa.intelligent.adjustment.dto.LiabilityShareDTO;
import com.bone.tpa.intelligent.adjustment.dto.LiabilitySharingRelationDTO;
import com.bone.tpa.intelligent.adjustment.dto.PlanDTO;
import com.bone.tpa.intelligent.adjustment.dto.request.*;
import com.bone.tpa.sdk.adjustment.enums.LiabilityTypeEnum;
import com.bone.tpa.intelligent.adjustment.enums.NextLiabilityTypeEnum;
import com.bone.tpa.intelligent.adjustment.service.CoverageService;
import com.bone.tpa.intelligent.adjustment.service.LiabilityService;
import com.bone.tpa.intelligent.adjustment.service.LiabilityShareService;
import com.bone.tpa.sdk.adjustment.model.liability.*;
import com.bone.tpa.test.BaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Transient;
import org.springframework.util.Assert;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PlanControllerTest  extends BaseTest {
    @Autowired
    PlanController controller;


    @Autowired
    LiabilitySortController sortController;


    @Autowired
    CoverageService coverageService;

    @Autowired
    LiabilityShareService shareService;


    @Autowired
    LiabilityService liabilityService;

    @Autowired
    LiabilityController liabilityController;


    @Autowired
    LiabilityShareController shareController;
    @Test
    public void testqueryPolicyByPage(){
       /* PolicyQueryRequest queryRequest = new PolicyQueryRequest();
        Result<PageResult<PolicyDTO>>  rs = controller.queryPolicyByPage(queryRequest);
        queryRequest.setPolicyNo("pc0001");
        queryRequest.setExternalPolicyNo("pk0002");
        queryRequest.setType(0);
        queryRequest.setType(0);
        queryRequest.setPageNo(1);
        queryRequest.setPageSize(10);
        System.out.println(JSONObject.toJSONString(queryRequest));
        System.out.println(JSONObject.toJSONString(rs));*/
        testCreatePlan();
    }

    @Test
    public void testExceptionHandler(){
        testCreatePlan();

    }
    @Test
    public void testCreatePlan1(){
        testCreatePlan();
    }
    @Test
    public void testCreatePlan2(){
        testCreatePlan();
    }
    @Test
    public void testCreatePlan3(){
        testCreatePlan();
    }
    @Test
    public void testCreatePlan4(){
        testCreatePlan();
    }
    @Test
    public void     testCreatePlan5(){
        testCreatePlan();
    }


        @Test
    public void testCreatePlan(){
        String policyno ="PC00011";
        PlanDTO planDTO= new PlanDTO();
        planDTO.setPlanLimit(BigDecimal.valueOf(100));
        planDTO.setPlanCode("1");
        planDTO.setPolicyNo(policyno);
        planDTO .setPlanName("test");
        Result<PlanDTO>  createPlanRs =   controller.createPlan(planDTO);
        Assert.isTrue(createPlanRs.getSuccess(),"errror");

        System.out.println(JSONObject.toJSONString(planDTO, SerializerFeature.DisableCircularReferenceDetect));
        System.out.println(JSONObject.toJSONString(createPlanRs,SerializerFeature.DisableCircularReferenceDetect));

        UpdatePlanRequest updatePlanRequest = new UpdatePlanRequest();
        updatePlanRequest.setRemark("update test");
        createPlanRs.getData().setPlanName("33333");
        createPlanRs.getData().setPlanLimit(BigDecimal.valueOf(100));
        PlanDTO updatePlan = new PlanDTO();
        updatePlan.setId(createPlanRs.getData().getId());
        updatePlan.setPlanLimit(BigDecimal.valueOf(100));
        updatePlan.setPlanCode("22");
        updatePlan.setPlanName("test2");
        updatePlanRequest.setPlanDTO(updatePlan);
        Result<PlanDTO>  updateRs =   controller.updatePlan(updatePlanRequest);
        System.out.println(JSONObject.toJSONString(updatePlanRequest,SerializerFeature.DisableCircularReferenceDetect));
        System.out.println(JSONObject.toJSONString(updateRs,SerializerFeature.DisableCircularReferenceDetect));

        CreateCoverageRequest coverageCreateRequest = new CreateCoverageRequest();
        List<CoverageDTO> coverageDTOList = new ArrayList<>();
        CoverageDTO coverageDTO = new CoverageDTO();
        coverageDTO.setPlanId(updatePlan.getId());
        coverageDTO.setCoverageCode("333");
        coverageDTO.setCoverageLimit(BigDecimal.ONE);
        coverageDTO.setCoverageName("cover");
        coverageDTO.setPolicyNo(policyno);
        coverageDTOList.add(coverageDTO);
        coverageCreateRequest.setCoverageDTOList(coverageDTOList);
        Result<Boolean>  createCoverRs =   controller.createCoverage(coverageCreateRequest);
        System.out.println(JSONObject.toJSONString(coverageCreateRequest,SerializerFeature.DisableCircularReferenceDetect));
        System.out.println(JSONObject.toJSONString(createCoverRs,SerializerFeature.DisableCircularReferenceDetect));

        CoverageDTO coverageSaved = PkListUtil.first(coverageService.queryByPlanId(Long.valueOf(updatePlan.getId())));
        CoverageDTO updateCoverage = new CoverageDTO();
        updateCoverage.setId(coverageSaved.getId());
        updateCoverage.setCoverageName(coverageSaved.getCoverageName());
        updateCoverage.setPlanId(coverageSaved.getPlanId());
        updateCoverage.setCoverageCode(coverageSaved.getCoverageCode());
        updateCoverage.setCoverageLimit(coverageSaved.getCoverageLimit());
        UpdateCoverageRequest updateCoverageRequest = new UpdateCoverageRequest();
        updateCoverageRequest.setRemark("ok");
        updateCoverageRequest.setCoverageDTO(updateCoverage);
        Result<Boolean>  updateCoverageResult =    controller.updateCoverage(updateCoverageRequest);


        System.out.println(JSONObject.toJSONString(updateCoverageRequest,SerializerFeature.DisableCircularReferenceDetect));
        System.out.println(JSONObject.toJSONString(updateCoverageResult,SerializerFeature.DisableCircularReferenceDetect));


        LiabilityCreateBatchRequest liabilityCreateBatchRequest = new LiabilityCreateBatchRequest();
        liabilityCreateBatchRequest.setPolicyNo(policyno);

        List<LiabilityCreateRequest> liabilityCreateRequestList = new ArrayList<>();
        LiabilityCreateRequest liabilityCreateRequest = new LiabilityCreateRequest();
        liabilityCreateRequestList.add(liabilityCreateRequest);
        liabilityCreateRequest.setLiabilityName("1");
        liabilityCreateRequest.setPlanId(Long.valueOf(updatePlan.getId()));
        liabilityCreateRequest.setCoverageId(Long.valueOf(coverageSaved.getId()));


        LiabilityCreateRequest liabilityCreateRequest2 = new LiabilityCreateRequest();
        liabilityCreateRequestList.add(liabilityCreateRequest2);
        liabilityCreateRequest2.setLiabilityName("222");
        liabilityCreateRequest2.setPlanId(Long.valueOf(updatePlan.getId()));
        liabilityCreateRequest2.setCoverageId(Long.valueOf(coverageSaved.getId()));

        liabilityCreateBatchRequest.setLiabilityCreateRequestList(liabilityCreateRequestList);

        Result<Boolean>  createLiaRs =  liabilityController.create(liabilityCreateBatchRequest);


        LiabilityConfig liabilityConfig = PkListUtil.first(liabilityService.queryByPlanId(Long.valueOf(updatePlan.getId())));

        Assert.notNull(liabilityConfig,"liabilityConfig is null;");

        System.out.println(JSONObject.toJSONString(liabilityCreateRequestList,SerializerFeature.DisableCircularReferenceDetect));

        System.out.println(JSONObject.toJSONString(createLiaRs,SerializerFeature.DisableCircularReferenceDetect));

        Result<List<Map<String,Object>>>  tableList =   controller.queryPlanByPolicy("PC00011");

        System.out.println(JSONObject.toJSONString(tableList,SerializerFeature.DisableCircularReferenceDetect));

        ShareCreateRequest shareCreateRequest = new ShareCreateRequest();

        List<LiabilityShareDTO> createShareCodeList =    new ArrayList<>();
        LiabilityShareDTO createShareDto = new LiabilityShareDTO();
        createShareDto.setShareCode("share_1");
        createShareDto.setPlanId(updatePlan.getId());
        createShareDto.setPolicyNo(policyno);
        createShareDto.setShareLimit(BigDecimal.valueOf(100));
        createShareCodeList.add(createShareDto);

        shareCreateRequest.setDtoList(createShareCodeList);

        Result<Boolean> createShareRs =   shareController.createShareCode(shareCreateRequest);

        System.out.println(JSONObject.toJSONString(createShareCodeList,SerializerFeature.DisableCircularReferenceDetect));
        System.out.println(JSONObject.toJSONString(createShareRs,SerializerFeature.DisableCircularReferenceDetect));


        //modifyShareCodeLimit
        List<LiabilityShareDTO> shareCodeList =  shareService.findDraftListByPolicyNo(planDTO.getPolicyNo());
        Assert.isTrue(shareCodeList.size()==1,"error");
        LiabilityShareDTO existShareCode = shareCodeList.get(0);

        LiabilityShareDTO updateShareLimitDto = new LiabilityShareDTO();
        updateShareLimitDto.setShareLimit(BigDecimal.valueOf(1));
        updateShareLimitDto.setId(existShareCode.getId());
        Result<Boolean>  modifyShareLimitRs =  shareController.modifyShareCodeLimit(updateShareLimitDto);

        System.out.println(JSONObject.toJSONString(updateShareLimitDto,SerializerFeature.DisableCircularReferenceDetect));
        System.out.println(JSONObject.toJSONString(modifyShareLimitRs,SerializerFeature.DisableCircularReferenceDetect));
        existShareCode =   shareService.findById(Long.valueOf(existShareCode.getId()));
        Assert.isTrue(existShareCode.getShareLimit().equals(new BigDecimal(1)),"error");


        //创建责任关联
        ShareRelationCreateRequest shareRelationCreateRequest = new ShareRelationCreateRequest();
        List<LiabilitySharingRelationDTO> createRelationRequest = new ArrayList<>();
        LiabilitySharingRelationDTO createRelationDto = new LiabilitySharingRelationDTO();
        createRelationRequest.add(createRelationDto);
        createRelationDto.setShareCode(existShareCode.getShareCode());
        createRelationDto.setPolicyNo(policyno);
        createRelationDto.setPlanId(updatePlan.getId());
        createRelationDto.setLiabilityUuid(liabilityConfig.getUuid());
        shareRelationCreateRequest.setDtoList(createRelationRequest);

        Result<Boolean>  createShareReleation =  shareController.createRelation(shareRelationCreateRequest);
        System.out.println(JSONObject.toJSONString(createRelationRequest,SerializerFeature.DisableCircularReferenceDetect));
        System.out.println(JSONObject.toJSONString(createShareReleation,SerializerFeature.DisableCircularReferenceDetect));
        //责任共保页面初始化

        Result<Map<String,Object>> pageInitMp =   shareController.shareConfigPageInit(planDTO.getPolicyNo());
        System.out.println(JSONObject.toJSONString(pageInitMp,SerializerFeature.DisableCircularReferenceDetect));
        //先后赔付责任关联
        List<LiabilityConfig> allConfig =  liabilityService.queryByPlanId(Long.valueOf(updatePlan.getId()));
        ChainLiabilityRequest saveSortRequest = new ChainLiabilityRequest();
        LiabilityConfig liabilityConfig1 = allConfig.get(0);
        LiabilityConfig liabilityConfig2 = allConfig.get(1);
        saveSortRequest.setNextType(NextLiabilityTypeEnum.EXCEEDING_NO_PAY.getCode());
        saveSortRequest.setId(Long.valueOf(liabilityConfig1.getId()));
        saveSortRequest.setNextUuid(liabilityConfig2.getUuid());

        Result<Boolean>  sortSaveRs =   sortController.saveSortRelation(saveSortRequest);
        System.out.println(JSONObject.toJSONString(saveSortRequest,SerializerFeature.DisableCircularReferenceDetect));
        System.out.println(JSONObject.toJSONString(sortSaveRs,SerializerFeature.DisableCircularReferenceDetect));

        Result<Map<String,Object>> sortPageInitRs=   sortController.pageInit(policyno);
        System.out.println(JSONObject.toJSONString(sortPageInitRs,SerializerFeature.DisableCircularReferenceDetect));


        Result<Boolean>  removeSortRs =  sortController.deleteSortRelation(Long.valueOf(liabilityConfig1.getId()));
        System.out.println(JSONObject.toJSONString(removeSortRs,SerializerFeature.DisableCircularReferenceDetect));


        LiabilityDeduct deduct = new LiabilityDeduct();
        getObjDefault(deduct);
        liabilityConfig1.setLiabilityDeduct(deduct);
        LiabilityLimit limitObj = new LiabilityLimit();
        getObjDefault(limitObj);
        liabilityConfig1.setLiabilityLimit(limitObj);

        liabilityConfig1.setLiabilityType(LiabilityTypeEnum.ALLOWANCE);

        RestrictObject restrictObject = new RestrictObject();
        getObjDefault(restrictObject);
        liabilityConfig1.setRestrictObject(restrictObject);

        RestrictScope scope = new RestrictScope();
        getObjDefault(scope);
        liabilityConfig1.setRestrictScope(PkListUtil.asList(scope));
        RestrictOutInsure outInsure = new RestrictOutInsure();
        getObjDefault(outInsure);
        liabilityConfig1.setRestrictOutInsure(outInsure);
        TimesLimit timesLimit = new TimesLimit();
        getObjDefault(timesLimit);
        liabilityConfig1.setTimesLimit(timesLimit);

        Result<Boolean>  updateLiabRs =     liabilityController.saveLiabilityConfig(liabilityConfig1);
        System.out.println(JSONObject.toJSONString(liabilityConfig1));
        System.out.println(JSONObject.toJSONString(updateLiabRs));


        Result<Map<String,Object>> liabilityPageInitRs =   liabilityController.liabilityEditPageInit(Long.valueOf(liabilityConfig1.getId()));
        System.out.println(JSONObject.toJSONString(liabilityPageInitRs,SerializerFeature.DisableCircularReferenceDetect));

    }


    public   void getObjDefault(Object obj) {
        // 得到类对象
        Class objCla = obj.getClass();
        Field[] fs = objCla.getDeclaredFields();
        for (int i = 0; i < fs.length; i++) {
            Field f = fs[i];
            Transient annot  =  f.getAnnotation(Transient.class);
            if(annot != null){
                continue;
            }
            // 设置些属性是可以访问的
            boolean isStatic = Modifier.isStatic(f.getModifiers());
            if (isStatic) {
                continue;
            }
            // 设置些属性是可以访问的
            f.setAccessible(true);
            try {
                // 得到此属性的值
                Object val = f.get(obj);
                // 得到此属性的类型
                String type = f.getType().toString();
                if (type.endsWith("String") && val == null) {
                    // 给属性设值
                    f.set(obj, "");
                }else if (type.endsWith("BigInteger")) {
                    f.set(obj, new BigInteger("0"));
                } else if ((type.endsWith("int") || type.endsWith("Integer") || type.endsWith("double")) && val == null) {
                    f.set(obj, 0);
                } else if ((type.toLowerCase().endsWith("byte"))) {
                    f.set(obj, Byte.valueOf("0"));
                } else if ((type.endsWith("long") || type.endsWith("Long")) && val == null) {
                    f.set(obj, 0L);
                } else if (type.endsWith("Date") && val == null) {
                    f.set(obj, Date.valueOf("1970-01-01"));
                } else if (type.endsWith("Timestamp") && val == null) {
                    f.set(obj, Timestamp.valueOf("1970-01-01 00:00:00"));
                } else if (type.endsWith("BigDecimal") && val == null) {
                    f.set(obj, new BigDecimal(0));
                } else if (type.endsWith("Float") && val == null) {
                    f.set(obj, Float.valueOf(0));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
