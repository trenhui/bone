package com.bone.tpa.test.claim.sync;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.result.Result;
import com.bone.tpa.api.request.SyncClaimWithEventRequest;
import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import com.bone.tpa.api.vo.InvoiceInfo;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.claim.sync.ClaimSyncFromTpaService;
import com.bone.tpa.claim.sync.ClaimToTpaChangeService;
import com.bone.tpa.facade.feign.MasterDataFeign;
import com.bone.tpa.facade.request.MasterDataQueryRequest;
import com.bone.tpa.facade.vo.MasterDataData;
import com.bone.tpa.task.impl.InputDealerApplyTrigger;
import com.bone.tpa.test.BaseTest;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class TpaSyncTest2   extends BaseTest {
    @Autowired
    ClaimSyncFromTpaService fromTpaService;

    @Autowired
    ClaimToTpaChangeService toTpaChangeService;


    @Autowired
    InputDealerApplyTrigger applyTrigger;


    @Autowired
    CommonLogService commonLogService;


    private String MDC_TRACE_ID = "traceId";
    @Test
    public void testAddLog(){
        if(StringUtils.isBlank(MDC.get(MDC_TRACE_ID))){
            MDC.put(MDC_TRACE_ID, UUID.randomUUID().toString() );
        }
        commonLogService.addLog("111", CommonLogType.TO_TPA_LOG, "{},{}", MDC.get("traceId"), "5", "6", "7");
    }

    @Test
    public void testTrigger(){
        applyTrigger.inputDealerApplyJob();

    }
    @Test
    public void testgenerateRequest() {
        SyncClaimWithEventRequest request =  TpaSyncTest.generateRequest();
        System.out.println(JSONObject.toJSONString(request.getClaimInfo()));
    }

    @Autowired
    MasterDataFeign masterDataFeign;
    @Test
    public void testSyncFromTpa(){
        SyncClaimWithEventRequest request =  TpaSyncTest.generateRequest();
        fromTpaService.transfer(request);
        ClaimDetailSyncVO toTpaVo =  toTpaChangeService.toSyncVo(request.getClaimNumber());
        ClaimDetailSyncVO fromTpaVo = request.getClaimInfo();
        compare(toTpaVo,fromTpaVo);
        checkPerson(fromTpaVo,toTpaVo);
        checkInvoice(fromTpaVo,toTpaVo);
        // image
        checkImage(fromTpaVo,toTpaVo);
        //imageBind

    }

    @Test
    public void testMasterdata(){
        String province="浙江省";
        MasterDataQueryRequest provinceRequest = new MasterDataQueryRequest();
        provinceRequest.setType("province_code");
        provinceRequest.setName(province);
        Result<List<MasterDataData>> remoetRs =    masterDataFeign.queryData(provinceRequest);
        if( !remoetRs.getSuccess()){
            throw new RuntimeException("请求主数据省份失败");
        }
    }

    private  void checkImage(ClaimDetailSyncVO fromTpaVo,ClaimDetailSyncVO toTpaVo){
        for(int i=0;i<fromTpaVo.getImageList().size();i++){
            compare(fromTpaVo.getImageList().get(i),toTpaVo.getImageList().get(i));
        }
        for(int i=0;i<fromTpaVo.getImageBindList().size();i++){
            compare(fromTpaVo.getImageBindList().get(i),toTpaVo.getImageBindList().get(i));
        }
    }

    /**
     * 校验发票
     * @param fromTpaVo
     * @param toTpaVo
     */
    private void checkInvoice(ClaimDetailSyncVO fromTpaVo,ClaimDetailSyncVO toTpaVo){
        for(int i=0;i<fromTpaVo.getInvoiceList().size();i++){
            compare(fromTpaVo.getInvoiceList().get(i),toTpaVo.getInvoiceList().get(i));
            InvoiceInfo fromInvoice =  fromTpaVo.getInvoiceList().get(i);
            InvoiceInfo toInvoice =  toTpaVo.getInvoiceList().get(i);
            checkProject(fromInvoice,toInvoice);
            checkItemcost(fromInvoice,toInvoice);
        }
    }

    private void checkItemcost(InvoiceInfo fromTpaVo,InvoiceInfo toTpaVo){
        for(int i=0;i<fromTpaVo.getCostItemInfoList().size();i++){
            compare(fromTpaVo.getCostItemInfoList().get(i),toTpaVo.getCostItemInfoList().get(i));
        }

    }
    private void checkProject(InvoiceInfo fromTpaVo,InvoiceInfo toTpaVo){
      /*  for(int i=0;i<fromTpaVo.getProjectInfoList().size();i++){
            compare(fromTpaVo.getProjectInfoList().get(i),toTpaVo.getProjectInfoList().get(i));
        }*/
    }
    private void checkPerson(ClaimDetailSyncVO fromTpaVo,ClaimDetailSyncVO toTpaVo){
        compare(fromTpaVo.getMainPersonInfo(),toTpaVo.getMainPersonInfo());
        compare(fromTpaVo.getApplyPersonInfo(),toTpaVo.getApplyPersonInfo());
        compare(fromTpaVo.getOutPersonInfo(),toTpaVo.getOutPersonInfo());
        for(int i=0;i<fromTpaVo.getBenefiList().size();i++){
            compare(fromTpaVo.getBenefiList().get(i),toTpaVo.getBenefiList().get(i));
        }
        compare(fromTpaVo.getCollectPersonInfo(),toTpaVo.getCollectPersonInfo());

    }


    private void compare(Object a,Object b){
       Class classA =  a.getClass();
       Class classB = b.getClass();
        try {
            for(Method method : classA.getMethods()){
                if(!method.getName().startsWith("get")){
                    continue;
                }
                Class returnClass = method.getReturnType();
                if(
                     !(   returnClass == String.class
                || returnClass == Integer.class
                || returnClass == Long.class
                || returnClass == BigDecimal.class)){

                    continue;
                }
                String methodName = method.getName();
                Method methodB =   classB.getMethod(methodName);

                Object aValue   = method.invoke(a);
                Object bValue  =  methodB.invoke(b);
                if(returnClass == BigDecimal.class){
                    if( aValue != null){
                        aValue = ((BigDecimal)aValue).setScale(2,BigDecimal.ROUND_HALF_UP);
                    }
                    if( bValue!= null){
                        bValue = ((BigDecimal)bValue).setScale(2,BigDecimal.ROUND_HALF_UP);
                    }
                    if(!Objects.equals(aValue,bValue)){
                        throw new RuntimeException("BigDecimal对比失败:"+methodName+"  a:"+aValue+" b:"+bValue);
                    }
                    continue;
                }

                if(!Objects.equals(aValue,bValue)){
                    throw new RuntimeException("对比失败:"+methodName+"  a:"+aValue+" b:"+bValue);
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
