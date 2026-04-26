package com.bone.tpa.test.claim;

import cn.hutool.core.lang.Assert;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.bone.core.util.JsonUtil;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.claim.adapter.ClaimController;
import com.bone.tpa.claim.adapter.GenericQueryController;
import com.bone.tpa.claim.application.request.QueryListRequest;
import com.bone.tpa.claim.application.request.QueryOneRequest;
import com.bone.tpa.claim.application.request.UpdateRequest;
import com.bone.tpa.claim.application.response.BenefitPerson;
import com.bone.tpa.claim.application.response.ClaimDetailObject;
import com.bone.tpa.claim.application.response.GenericQueryRequest;
import com.bone.tpa.claim.application.response.GenericQueryResponse;
import com.bone.tpa.claim.domain.service.ClaimInvoiceService;
import com.bone.tpa.claim.infrastructure.external.InfraClient;
import com.bone.tpa.sdk.claim.enums.ClaimStageEnum;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.task.service.vo.OcrMedicalInvoiceModel;
import com.bone.tpa.claim.application.transfer.PersonFieldTransfer;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import com.bone.tpa.test.BaseTest;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.Rollback;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bone.tpa.sdk.constants.BizConstant.DEFAULT_PAGE_SIZE;

public class ClaimTest extends BaseTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private String appCode = "tpa";

    @Autowired
    private ClaimController claimController;

    @Autowired
    private GenericQueryController genericQueryController;

    @Autowired
    private ClaimInvoiceService claimInvoiceService;

    @Autowired
    private ClaimRepository claimRepository;

    String filePath = "src/test/java/com/bone/tpa/test/claim/file/";


    @Test
    public void testUpdateMoney(){
        ClaimInvoice up = new ClaimInvoice();
        up.setTotalAmount(BigDecimal.valueOf(5000));
        claimInvoiceService.updateValidMoney(up);
        Assert.isTrue(up.getValidAmount().compareTo(BigDecimal.valueOf(5000))==0);
        up.setTotalMedicalFundPayment(BigDecimal.valueOf(1));
        claimInvoiceService.updateValidMoney(up);
        Assert.isTrue(up.getValidAmount().compareTo(BigDecimal.valueOf(4999))==0);
        //getSelfPayPart2Amount
        up.setSelfPayPart2Amount(BigDecimal.valueOf(2));
        claimInvoiceService.updateValidMoney(up);
        Assert.isTrue(up.getValidAmount().compareTo(BigDecimal.valueOf(4997))==0);

    }

    @Test
    public void testJson(){
        String str = " {\n" +
                "    \"amount\" : \"6283.79\",\n" +
                "    \"billCode\" : \"23061425\",\n" +
                "    \"billName\" : \"黑龙江省医疗住院收费票据（电子）\",\n" +
                "    \"billNumber\" : \"6422007894\",\n" +
                "    \"billType\" : \"101\",\n" +
                "    \"businessNo\" : \"S95GKF7421IP20250606102123213726\",\n" +
                "    \"checkCode\" : \"22d039\",\n" +
                "    \"checkCount\" : \"1\",\n" +
                "    \"feedetails\" : [\n" +
                "\n" +
                "    ],\n" +
                "    \"feeitems\" : [\n" +
                "      {\n" +
                "        \"itemCoding\" : \"C0103\",\n" +
                "        \"itemName\" : \"中成药费\",\n" +
                "        \"number\" : \"1\",\n" +
                "        \"remark\" : \"\",\n" +
                "        \"totalAmount\" : \"461.30\",\n" +
                "        \"unit\" : \"项\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"itemCoding\" : \"C0104\",\n" +
                "        \"itemName\" : \"西药费\",\n" +
                "        \"number\" : \"1\",\n" +
                "        \"remark\" : \"\",\n" +
                "        \"totalAmount\" : \"455.30\",\n" +
                "        \"unit\" : \"项\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"itemCoding\" : \"C0105\",\n" +
                "        \"itemName\" : \"诊查费\",\n" +
                "        \"number\" : \"1\",\n" +
                "        \"remark\" : \"\",\n" +
                "        \"totalAmount\" : \"280.00\",\n" +
                "        \"unit\" : \"项\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"itemCoding\" : \"C0107\",\n" +
                "        \"itemName\" : \"检查费\",\n" +
                "        \"number\" : \"1\",\n" +
                "        \"remark\" : \"\",\n" +
                "        \"totalAmount\" : \"2491.50\",\n" +
                "        \"unit\" : \"项\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"itemCoding\" : \"C0108\",\n" +
                "        \"itemName\" : \"化验费\",\n" +
                "        \"number\" : \"1\",\n" +
                "        \"remark\" : \"\",\n" +
                "        \"totalAmount\" : \"640.10\",\n" +
                "        \"unit\" : \"项\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"itemCoding\" : \"C0109\",\n" +
                "        \"itemName\" : \"治疗费\",\n" +
                "        \"number\" : \"1\",\n" +
                "        \"remark\" : \"\",\n" +
                "        \"totalAmount\" : \"983.40\",\n" +
                "        \"unit\" : \"项\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"itemCoding\" : \"C0112\",\n" +
                "        \"itemName\" : \"卫生材料费\",\n" +
                "        \"number\" : \"1\",\n" +
                "        \"remark\" : \"\",\n" +
                "        \"totalAmount\" : \"404.19\",\n" +
                "        \"unit\" : \"项\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"itemCoding\" : \"C0114\",\n" +
                "        \"itemName\" : \"床位费\",\n" +
                "        \"number\" : \"1\",\n" +
                "        \"remark\" : \"\",\n" +
                "        \"totalAmount\" : \"340.00\",\n" +
                "        \"unit\" : \"项\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"itemCoding\" : \"C0115\",\n" +
                "        \"itemName\" : \"护理费\",\n" +
                "        \"number\" : \"1\",\n" +
                "        \"remark\" : \"\",\n" +
                "        \"totalAmount\" : \"228.00\",\n" +
                "        \"unit\" : \"项\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"financialSeal\" : \"\",\n" +
                "    \"financialSealNumber\" : \"\",\n" +
                "    \"flushedRed\" : \"true\",\n" +
                "    \"gender\" : \"男\",\n" +
                "    \"hospitalization\" : \"0001336\",\n" +
                "    \"hospitalizationDate\" : \"20250527\",\n" +
                "    \"inpatientDepartment\" : \"脑病科(三)\",\n" +
                "    \"institutionsType\" : \"综合医院\",\n" +
                "    \"insuranceNum\" : \"230000000000000001618768\",\n" +
                "    \"invoiceDate\" : \"20250606\",\n" +
                "    \"medicarePay\" : \"4324.53\",\n" +
                "    \"medicareType\" : \"国家医保\",\n" +
                "    \"mendAmount\" : \"0.00\",\n" +
                "    \"otherPayment\" : \"0.00\",\n" +
                "    \"pay_list\" : {\n" +
                "\n" +
                "    },\n" +
                "    \"payeeName\" : \"大庆市中医医院\",\n" +
                "    \"payer\" : \"池太俊\",\n" +
                "    \"PDFInfo\" : {\n" +
                "\n" +
                "    },\n" +
                "    \"personalCashPayment\" : \"1959.26\",\n" +
                "    \"personalExpense\" : \"0.00\",\n" +
                "    \"personalPay\" : \"0.00\",\n" +
                "    \"preAmount\" : \"1959.26\",\n" +
                "    \"print\" : \"01\",\n" +
                "    \"productCode\" : \"003081\",\n" +
                "    \"receivablesInstitution\" : \"大庆市中医医院\",\n" +
                "    \"recordsNo\" : \"0001336\",\n" +
                "    \"redInvoiceDate\" : \"\",\n" +
                "    \"redInvoiceReason\" : \"\",\n" +
                "    \"redInvoiceTime\" : \"\",\n" +
                "    \"refundAmount\" : \"0.00\",\n" +
                "    \"remark\" : \"备注信息： 医保内金额:5405.03,医保外金额:878.76,起付线:600.00,其中:龙江惠民保支付:0.00 登记号:0000034345,结算前押金:5000,结算时收/退押金:-3040.74\",\n" +
                "    \"reviewer\" : \"门宁宁\",\n" +
                "    \"selfAcountAmount\" : \"0.00\",\n" +
                "    \"unifiedSocialCreditCode\" : \"230606********101X\"\n" +
                "  }";
        OcrMedicalInvoiceModel details = JSON.parseObject(str, OcrMedicalInvoiceModel.class);

        System.out.println(details);

    }


    @Test
    public void test() {
        ClaimStakeholder stakeholder = new ClaimStakeholder();
        Map<String,Object> mp = new HashMap<>();
        mp.put("333","dddd");
        stakeholder.setExtraStore(JSONObject.toJSONString(mp));
        BenefitPerson target = new BenefitPerson();
        PersonFieldTransfer.transferToBizDto(stakeholder, target);

        System.out.println(target);
    }


    @Test
    public void testGetDetail() {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(filePath + "invoice.txt");

            Long id = 1111111222222222L;

            Result<ClaimDetailObject> result = claimController.getClaimDetail(id);

            System.out.println(result.getData());

            new PrintStream(fileOutputStream).println(JsonUtil.toJson(result.getData()));
        } catch (FileNotFoundException ex) {
            System.out.println("File not found.");
        }
    }

    @Test
    public void testSubmitDetail() {
        try {
            File file = new File(filePath + "claim.txt");
            String json = FileUtils.readFileToString(file, "UTF-8");

            MockHttpServletRequest request = new MockHttpServletRequest();
            RequestAttributes requestAttributes = new ServletRequestAttributes(request);
            RequestContextHolder.setRequestAttributes(requestAttributes);

            ClaimDetailObject claimDetailObject = JsonUtil.fromJson(json, ClaimDetailObject.class);

            System.out.println(claimDetailObject);

            claimDetailObject.setAction("DRAFT");

            Result<ClaimDetailObject> result = claimController.submitClaim(claimDetailObject);

            System.out.println(result.getData());
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }


    @Test
    public void testGetList() {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(filePath + "list.txt");

            QueryListRequest queryListRequest = new QueryListRequest();
            queryListRequest.setBizIdentityCode("4:yccc:yccc-zj:awdas112");
            queryListRequest.setTenantId("1");
            queryListRequest.setId("254903131001");

//            QueryParam queryParam = new QueryParam();
//            queryParam.setField("batchNo");
//            queryParam.setValue("50001");
//            queryParam.setType(QueryTypeEnum.LIKE.getCode());

//            List<QueryParam> queryParamList = new ArrayList<>();
//            queryParamList.add(queryParam);
//            queryListRequest.setQueryParams(queryParamList);

            List<String> modelNames = new ArrayList<>();
//            modelNames.add("policy");
//            modelNames.add("mainInsurePerson");
            modelNames.add("claimInvoice");
            queryListRequest.setModelNames(modelNames);
            queryListRequest.setPageNo(1);
            queryListRequest.setPageSize(10);


            Result<PageResult<GenericQueryResponse>> result = genericQueryController.queryList(queryListRequest);

            System.out.println(result.getData());

            new PrintStream(fileOutputStream).println(JsonUtil.toJson(result.getData()));
        } catch (FileNotFoundException ex) {
            System.out.println("File not found.");
        }
    }


    @Test
    @Rollback(value = false)
    public void testUpdate() {
        try {
            File file = new File(filePath + "list.txt");
            String json = FileUtils.readFileToString(file, "UTF-8");

            MockHttpServletRequest request = new MockHttpServletRequest();
            RequestAttributes requestAttributes = new ServletRequestAttributes(request);
            RequestContextHolder.setRequestAttributes(requestAttributes);

            GenericQueryRequest data = JsonUtil.fromJson(json, GenericQueryRequest.class);

            System.out.println(data);

            UpdateRequest updateRequest = new UpdateRequest();

            List<String> modelNames = new ArrayList<>();
            modelNames.add("outInsurePerson");

            updateRequest.setUpdateData(data);
            updateRequest.setModelNames(modelNames);

            Result<Boolean> result = genericQueryController.update(updateRequest);

            System.out.println(result.getData());

        } catch (FileNotFoundException ex) {
            System.out.println("File not found.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }



    @Test
    public void testGetOne() {

        ClaimStakeholder stakeholder = new ClaimStakeholder();
        Map<String,Object> mp = new HashMap<>();
        mp.put("333","dddd");
        stakeholder.setExtraStore(JSONObject.toJSONString(mp));
        BenefitPerson target = new BenefitPerson();
        PersonFieldTransfer.transferToBizDto(stakeholder, target);

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(filePath + "list.txt");

            QueryOneRequest queryOneRequest = new QueryOneRequest();

            queryOneRequest.setId(254700556014L);

            List<String> modelNames = new ArrayList<>();
            modelNames.add("claimDetail");
            queryOneRequest.setModelNames(modelNames);

            Result<GenericQueryResponse> result = genericQueryController.queryOne(queryOneRequest);

            System.out.println(result.getData());

            new PrintStream(fileOutputStream).println(JsonUtil.toJson(result.getData()));
        } catch (FileNotFoundException ex) {
            System.out.println("File not found.");
        }
    }


    @Test
    public void testPageByCriteria() {

        Criteria<Claim> criteria = Criteria.create();
        criteria.in(Claim::getStage, ClaimStageEnum.getExceptStage(ClaimStageEnum.FINISH, ClaimStageEnum.INIT));
        criteria.in(Claim::getStatus, ClaimStatusEnum.getExceptStatus(ClaimStatusEnum.COMPLETE_AUDIT, ClaimStatusEnum.Finish,
                ClaimStatusEnum.Cancel, ClaimStatusEnum.DRAFT));
        Long count = claimRepository.countByCriteria(criteria);
        criteria.addSort(Criteria.getDefaultIdSort());
        criteria.page(DEFAULT_PAGE_SIZE, 0);
        PageResult<Claim> pageResult = claimRepository.pageByCriteria(criteria);

        System.out.println(pageResult.getData().get(0));
    }
}
