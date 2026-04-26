package com.bone.tpa.test.claim.sync;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.facade.vo.*;
import com.bone.tpa.test.BaseTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class MockTest  extends BaseTest {
    @Test
    public void testSyncFromTpa2(){
        Long objectId = 1111522L;
        List<TpaLogVO> rs = PkListUtil.newArrayList();
        TpaLogVO log1 = new TpaLogVO();
        log1.setObjectId(objectId);
        log1.setOperation("创建赔案");
        log1.setRemark("备注1");
        log1.setCreateBy("何磊");
        log1.setUpdateBy("何磊");
        log1.setCreateTime(System.currentTimeMillis());
        log1.setUpdateTime(System.currentTimeMillis());
        rs.add(log1);


        TpaLogVO log2 = new TpaLogVO();
        log2.setObjectId(objectId);
        log2.setOperation("审批通过");
        log2.setRemark("备注2");
        log2.setCreateBy("何磊");
        log2.setUpdateBy("何磊");
        log2.setCreateTime(System.currentTimeMillis());
        log2.setUpdateTime(System.currentTimeMillis());
        rs.add(log2);
        ApiResult apiResult = new ApiResult();
        apiResult.setData(rs);
        apiResult.setCode(0);
        System.out.println(JSONObject.toJSONString(apiResult));
    }
    @Test
    public void testSyncFromTpa3(){
        ApiResult<List<InsuranceCompanyImageVO>> apiResult = new ApiResult<>();
        List<InsuranceCompanyImageVO> rs = PkListUtil.newArrayList();
        InsuranceCompanyImageVO v1 = new InsuranceCompanyImageVO();
        rs.add(v1);
        v1.setFieldName("保司类型1-身份证");
        v1.setImageMapCode("1");
        v1.setImageClassifyCode("pc0001");
        InsuranceCompanyImageVO v2 = new InsuranceCompanyImageVO();
        rs.add(v2);
        v2.setFieldName("保司类型2-申请书");
        v2.setImageMapCode("2");
        v2.setImageClassifyCode("pc0002");
        apiResult.setData(rs);
        apiResult.setCode(0);

        InsuranceCompanyImageVO v3 = new InsuranceCompanyImageVO();
        rs.add(v3);
        v3.setFieldName("保司类型2-票据");
        v3.setImageMapCode("3");
        v3.setImageClassifyCode("pc0003");

        InsuranceCompanyImageVO v4 = new InsuranceCompanyImageVO();
        rs.add(v4);
        v4.setFieldName("保司类型1-护照");
        v4.setImageMapCode("1");
        v4.setImageClassifyCode("pc0005");

        apiResult.setData(rs);
        apiResult.setCode(0);
        System.out.println(JSONObject.toJSONString(apiResult));
    }

    //ApiResult<List<HangUpReordVO>>
    @Test
    public void testSyncFromTpa4(){
        String claimNumber = "313234";
        ApiResult<List<HangUpReordVO>> apiResult = new ApiResult<>();
         List<HangUpReordVO> rs = PkListUtil.newArrayList();
        HangUpReordVO v1 = new HangUpReordVO();
        v1.setClaimNo(claimNumber);
        v1.setExplanation("你说呢");
        v1.setReason("你猜呢");
        v1.setHangUpTime(String.valueOf(System.currentTimeMillis()));
        v1.setReasonType("3");
        rs.add(v1);

        HangUpReordVO v2 = new HangUpReordVO();
        v2.setClaimNo(claimNumber);
        v2.setExplanation("你说呢???");
        v2.setReason("你猜呢哈哈h哈");
        v2.setHangUpTime(String.valueOf(System.currentTimeMillis()));
        v2.setReasonType("3");
        rs.add(v2);

        apiResult.setData(rs);
        apiResult.setCode(0);
        System.out.println(JSONObject.toJSONString(apiResult));
    }

    @Test
    public void testSyncFromTpa5(){
        String claimNumber =  "313234L";
        ApiResult<PkbImageResponse> apiResult = new ApiResult<>();
        PkbImageResponse rs = new PkbImageResponse();
        rs.setClaimNumber(claimNumber);
        List<PkbImageVO> imageVOList  = new ArrayList<PkbImageVO>();
        rs.setImages(imageVOList);
        PkbImageVO v1 = new PkbImageVO();
        imageVOList.add(v1);
        v1.setImageName("补充内容1");
        v1.setImageIndex(1);
        v1.setImagePath("http://bucket-pktest.oss-cn-hangzhou.aliyuncs.com/cs/photo/claim/new/caseId232/imgType1/202d06f3-7b1c-43b4-af54-b79e7bff4e39.jpg");
        v1.setClaimImageId("222");
        PkbImageVO v2 = new PkbImageVO();
        imageVOList.add(v2);
        v2.setImageName("补充内容2");
        v2.setImageIndex(2);
        v2.setImagePath("http://bucket-pktest.oss-cn-hangzhou.aliyuncs.com/cs/photo/claim/new/caseId222/imgType1/d613ac67-afb3-4719-8ad1-a592de24a6a1.png");
        v2.setClaimImageId("333");
        apiResult.setData(rs);
        apiResult.setCode(0);
        System.out.println(JSONObject.toJSONString(apiResult));
    }

    //PageBizIdentityVO
    @Test
    public void testSyncFromTpa6(){
        ApiResult<List<PageBizIdentityVO>> apiResult = new ApiResult<>();
        List<PageBizIdentityVO> rs =new ArrayList<>();
        PageBizIdentityVO v1 = new PageBizIdentityVO();
        rs.add(v1);
        v1.setBizType(1);
        v1.setBizCode("code1");
        v1.setAppCode("tpa");
        v1.setBizName("pc55332");
        apiResult.setData(rs);
        apiResult.setCode(0);
        System.out.println(JSONObject.toJSONString(apiResult));
    }
}
