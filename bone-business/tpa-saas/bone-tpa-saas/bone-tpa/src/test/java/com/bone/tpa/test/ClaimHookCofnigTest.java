package com.bone.tpa.test;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.result.Result;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.core.hook.BizHookFactory;
import com.bone.tpa.hook.adapter.ClaimConfigController;
import com.bone.tpa.hook.vo.HookConfigSaveRequest;
import com.bone.tpa.sdk.dao.biz.ClaimHookConfigBiz;
import com.bone.tpa.sdk.identityRule.BizidentityHook;
import com.bone.tpa.sdk.identityRule.invoice.hospitalName.InvoiceHosptialCalHook;
import com.bone.tpa.sdk.util.SpringContextUtils;
import com.bone.tpa.sdk.vo.HookPageconfigVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClaimHookCofnigTest extends BaseTest{
    @Autowired
    ClaimHookConfigBiz claimHookConfigBiz;


    @Autowired
    ClaimConfigController hookController;


    @Autowired
    BizHookFactory bizHookFactory;


    @Test
    public void testHospitalNameHook(){
        InvoiceHosptialCalHook hook =  bizHookFactory.getInvoiceHosptialCalHook("4:yccc:yccc-zj:awdas112");
        Assert.notNull(hook,"hook is null");
    }

    @Test
    public void testNull(){

        Map<String, BizidentityHook> beanMap = SpringContextUtils.getBeansOfType(BizidentityHook.class);


        Assert.notNull(claimHookConfigBiz, "claimHookConfigBiz is null");
        String bizIdentityCode = "heleicode";
        Result<List<HookPageconfigVO>>  dictRs = hookController.getHookList(bizIdentityCode);
        System.out.println(JSONObject.toJSONString(dictRs));
        HookConfigSaveRequest request = new HookConfigSaveRequest();
        request.setBizIdentityCode(bizIdentityCode);
        List<HookPageconfigVO> dictList =  dictRs.getData();
        dictList.get(0).setStatus(1);
        request.setConfigList(dictList);
        System.out.println(JSONObject.toJSONString(request));
        Result<List<HookPageconfigVO>> updateRemoteRs =   hookController.saveHookConfig(request);
        HookPageconfigVO checkUpdate =  updateRemoteRs.getData().get(0);
        Assert.isTrue(checkUpdate.getStatus()==1,"update status error");
    }


    @Test
    public void testReason(){
        List<String> rs = new ArrayList<>();
        rs.add("33,银行信息错误,0");
        rs.add("34,银行卡号错误,33");
        rs.add("35,开户行错误, 33");
        rs.add("36,保单责任错误,0");
        rs.add("38,重复赔付,0");
        rs.add("39,发票重复赔付，案件重复赔付,38");
        rs.add("40,扣费错误,0");
        rs.add("42,责免项扣费错误, 40");
        rs.add("43,乙类、自费扣费错误, 40");
        rs.add("44,复制发票/赔案 ,0");
        rs.add("45,系统点击修改信息才能通过,44");
        rs.add("46,赔付超一万元反洗钱缺少9要素,0");
        rs.add("47,缺少身份证明、常驻地址、职业等 ,46");
        rs.add("48,发票审核错误,0");
        rs.add("49,非本人发票, 48");
        rs.add("50,发票号码录入错误,48");
        rs.add("51,发票金额错误,48");
        rs.add("52,假发票 ,48");
        rs.add("53,发票少盖章, 48");
        rs.add("54,疾病原因错误,0");
        rs.add("55,疾病原因写错,54");
        rs.add("56,疾病诊断错误,54");
        rs.add("57,除外责任错误,0");
        rs.add("58,除外责任项未扣除,57");
        rs.add("59,非除外责任项扣除了, 57");
        rs.add("60,拒赔原因错误,0");
        rs.add("61,拒付原因写错、漏写, 60");
        rs.add("62,理赔材料审核错误,0");
        rs.add("63,理赔材料缺少,62");
        rs.add("64,门诊、门规、重疾责任选错,36");
        rs.add("65,个账、公账赔错 ,36");
        rs.add("66,账户余额不足,0");
        rs.add("67,账户余额不足拒赔,66");
        rs.add("68,保单无责任, 0");
        rs.add("69,就诊日期无对应保单, 68");
        rs.add("70,提交案件无保单相关责任,68 ");
        rs.add("71,非保单有效期,0");
        rs.add("72,非保期内,71");
        rs.add("73,审核规则不清晰 ,0");
        rs.add("74,审核规则待确认 ,73");
        rs.add("75,系统推送原因,0");
        rs.add("76,校验不通过, 75");
        rs.add("77,推送保司失败,75");
        rs.add("78,外包回传,0");
        rs.add("79,赔案回传信息错误,78 ");

        List<Map<String,Object>> rootList = new ArrayList<>();
        for(String st : rs){
            String[] arr = st.split(",");
            if(arr.length != 3){
                throw new RuntimeException("error length:"+st);
            }
            if(arr[2].trim().equals("0")){
                Map<String,Object> rt = new HashMap();
                rt.put("code",arr[0]);
                rt.put("name",arr[1]);
                rt.put("children",new ArrayList<>());
                rootList.add(rt);
            }
        }

        for(Map<String,Object> rt : rootList){
            String code = (String)rt.get("code");
            for(String st : rs){
                String[] arr = st.split(",");
                if(arr[2].trim().equals(code)){
                    Map<String,Object> child = new HashMap();
                    child.put("code",arr[0]);
                    child.put("name",arr[1]);
                    ((List)rt.get("children")).add(child);
                }
            }
        }

        //returnBackReason;
        Map<String,Object> root =new HashMap<>();
        root.put("code","returnBackReason");
        root.put("name","退回原因");
        root.put("children",rootList);
        System.out.println(JSONObject.toJSONString(root));
    }
}
