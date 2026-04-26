package com.bone.tpa;

import com.alibaba.fastjson.JSON;
import com.bone.core.result.Result;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.facade.feign.PageModelConfigFeign;
import com.bone.tpa.facade.request.OptionWithCodeQueryRequest;
import com.bone.tpa.facade.vo.OptionSetDTO;
import com.bone.tpa.push.feign.client.InsurancePushManagerFeignClient;
import com.bone.tpa.push.feign.client.InsurancebizServiceFeignClient;
import com.bone.tpa.push.feign.request.ClaimCancelRequest;
import com.bone.tpa.push.feign.request.QueryReviewFlagRequest;
import com.bone.tpa.push.feign.response.QueryReviewFlagResponse;
import com.bone.tpa.push.service.PushClaimService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;

import static com.bone.tpa.push.constants.CommonConstant.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = com.bone.tpa.TpaApplication.class,
        properties = {"env=DEV", "apollo.meta=http://192.168.8.136:8096"
                ,
                "file.encoding=UTF-8"
        })
class PushServiceTest {

    @Autowired
    private PushClaimService pushClaimService;

    @MockBean
    private InsurancebizServiceFeignClient insurancebizServiceFeignClient;

    @MockBean
    private InsurancePushManagerFeignClient insurancePushManagerFeignClient;

    @MockBean
    private PageModelConfigFeign pageModelConfigFeign;


    @BeforeEach
    void setUp() {
        // 初始化测试数据
    }

    @Test
    void testGetUserById_Success() {
      /*  // Given
        Long claimNo = 254902628001L;

        ApiResult<String> mockResp = new ApiResult<>();
        mockResp.setData("1");
        Mockito.when(insurancebizServiceFeignClient.queryPolicyConfig(Mockito.any(QueryReviewFlagRequest.class)))
                .thenReturn(mockResp);

        QueryReviewFlagResponse mockResp2 = new QueryReviewFlagResponse();
        mockResp2.setData("0");
        Mockito.when(insurancebizServiceFeignClient.queryReviewFlag(Mockito.any(QueryReviewFlagRequest.class)))
                .thenReturn(mockResp2);

        ApiResult<String> mockResp3 = new ApiResult<>();
        mockResp.setData("1");
        Mockito.when(insurancePushManagerFeignClient.claimCancel(Mockito.any(ClaimCancelRequest.class)))
                .thenReturn(mockResp3);

        OptionSetDTO data = new OptionSetDTO();
        Result<OptionSetDTO> mockResp4 = Result.success(data);
        data.setExtraProperty(JSON.toJSONString(new HashMap<String, String>() {
            {
                put(MAININSURE_IDENTITY_TYPE_MAIN_MAPPING_NAME, "a");
                put(MAININSURE_IDENTITY_TYPE_SECOND_MAPPING_NAME, "b");
                put(OUTINSURE_IDENTITY_TYPE_MAIN_MAPPING_NAME, "c");
                put(COLLECTINSURE_IDENTITY_TYPE_MAIN_MAPPING_NAME, "d");
                put(COLLECTINSURE_WITH_MAININSURE_RELATION_MAPPING_NAME, "e");
                put(BENEINSURE_WITH_OUTINSURE_RELATION_MAPPING_NAME, "f");
            }
        }));
        Mockito.when(pageModelConfigFeign.queryCollectionByOptionCode(Mockito.any(OptionWithCodeQueryRequest.class)))
                .thenReturn(mockResp4);


        // When
        pushClaimService.push(claimNo, null);*/
    }
}
