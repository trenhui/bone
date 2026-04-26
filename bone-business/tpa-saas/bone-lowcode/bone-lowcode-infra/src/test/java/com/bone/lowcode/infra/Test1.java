package com.bone.lowcode.infra;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.service.FieldApplicationService;
import com.bone.lowcode.infra.application.service.OptionSetApplicationService;
import com.bone.lowcode.infra.application.service.PageApplicationService;
import com.bone.lowcode.infra.application.vo.page.pageJson.NewPageVO;
import com.bone.lowcode.infra.domain.constant.Constant;
import com.bone.lowcode.infra.domain.service.FieldService;
import com.bone.lowcode.infra.domain.service.OptionSetService;
import com.bone.lowcode.infra.domain.service.ReleasedPageService;
import com.bone.lowcode.infra.domain.service.VersionService;
import com.bone.lowcode.infra.domain.util.MetaDataUtil;
import com.bone.lowcode.infra.infrastructure.feign.GroupDataFeignClient;
import com.bone.lowcode.infra.infrastructure.feign.bean.AddressInfo;
import com.bone.lowcode.infra.infrastructure.feign.bean.EnumEntry;
import com.bone.lowcode.infra.infrastructure.feign.bean.GroupDataParam;
import com.bone.lowcode.infra.infrastructure.feign.bean.PageResult;
import com.bone.lowcode.infra.infrastructure.feign.util.GroupDataFeignUtil;
import com.bone.lowcode.infra.infrastructure.feign.util.TpaSaasBusinessFeignUtil;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgBlockDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFieldDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgReleasedPageDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.OptionSet;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.BlockMapper;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.FieldMapper;
import com.bone.metadata.sdk.dto.MetaFieldDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import java.util.*;

@SpringBootTest
@Slf4j
public class Test1 {


    @Autowired
    private FieldMapper fieldMapper;

    @Autowired
    private BlockMapper blockMapper;

    @Autowired
    private VersionService versionService;

    @Autowired
    private OptionSetService optionSetService;

    @Autowired
    private ReleasedPageService releasedPageService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MetaDataUtil metaDataUtil;

    @Autowired
    private FieldService fieldService;

    @Autowired
    private PageApplicationService pageApplicationService;

    @Autowired
    private GroupDataFeignClient groupDataFeignClient;

    @Autowired
    private OptionSetApplicationService optionSetApplicationService;

    //    @Test
    public void test8() {
        List<MetaFieldDTO> metaFieldList = metaDataUtil.getFieldMetaByEntityCode("ss_claim");
        metaFieldList.forEach(System.out::println);
    }

    //    @Test
    public void updateByIdAcceptNull() {
        LambdaUpdateWrapper<CfgFieldDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CfgFieldDO::getId, 12163444118330L)
                .set(CfgFieldDO::getTitle, "fieldDO.getTitle()")
        ;
        boolean b = fieldMapper.update(wrapper) > 0;
        System.out.println("b = " + b);
    }

    //    @Test
    public void test2() {
        List<OptionSet> list = optionSetService.getListBySetId(22L);
        System.out.println("list = " + list);
    }

    //    @Test
    public void test3() {
        int num = new Random().nextInt(3);
        int num1 = new Random().nextInt(2);

        int salary = 1000;
        block1:
        {
            if (0 == num) {
                System.out.println("0 == num");
                break block1;
            }
            salary += 300;
            if (0 == num1) {
                System.out.println("0 == num1");
                break block1;
            }
            salary += 200;
        }

        System.out.println("num = " + num + ", num1 = " + num1);
        System.out.println("salary = " + salary);
    }

    //    @Test
    public void test4() {
        int count = 0;
        outerLoop:
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 2; j++) {
                if (i == 2 && j == 1) {
                    break outerLoop;
                }
                count += 1;
                System.out.println("i: " + i + ", j: " + j);
            }
        }
        System.out.println("count = " + count);
    }

    //    @Test
    public void test5() {
        List<CfgReleasedPageDO> versionList = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            CfgReleasedPageDO pageDO = new CfgReleasedPageDO();
            pageDO.setId((long) (i));
            if (i != 4) {
                pageDO.setPageInfo("pageInfo" + i);
            }
            pageDO.setType((byte) 9);
            pageDO.setPageId(10000L);
            pageDO.setVersionId(10000L);
            versionList.add(pageDO);
        }
//        releasedPageService.batchSave(versionList); //结论：此方法批量插入时不具备一致性，报错不回滚

        boolean execute = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            try {
                boolean flag = releasedPageService.batchSave(versionList);
                System.out.println("flag = " + flag);
                return flag;
            } catch (Exception e) {
                log.error("发布专属页面发生异常:", e);
                status.setRollbackOnly();
                return false;
            }
        }));
        System.out.println("execute = " + execute);
    }

    //    @Test
    public void test6() {
        String str = "{\"policyStatus\":\"policy_wait_audit\",\"set2\":\"set2value2\",\"optionset1\":\"选项集1选项值2\"}";
        JSONObject jsonObject = JSON.parseObject(str);
        jsonObject.remove("policyStatus");
        jsonObject.remove("set2");
        Object o = jsonObject.remove("111");
        System.out.println("o = " + o);
        System.out.println(jsonObject.toString());
    }

    //    @Test
    public void test7() {
        String type = "province_code";
        GroupDataParam param = new GroupDataParam();
        param.setType(type);

        Result<PageResult<AddressInfo>> result = groupDataFeignClient.getAddressData(param);
        PageResult<AddressInfo> pageResult = result.getData();
        List<AddressInfo> dataList = pageResult.getData();
        for (AddressInfo info : dataList) {
            System.out.println(info);
        }
    }

//    @Autowired
//    private UpdateAddressDataService updateAddressDataService;

    //    @Test
    public void test9() {
//        updateAddressDataService.deleteKeysByPrefix("address:");
//        updateAddressDataService.updateAddress();
    }

    @Autowired
    private TpaSaasBusinessFeignUtil tpaSaasBusinessFeignUtil;

    //    @Test
    public void test10() {
        List<EnumEntry> enumList = tpaSaasBusinessFeignUtil.getEnumList();
        for (EnumEntry enumEntry : enumList) {
            System.out.println("enumEntry = " + enumEntry);
        }
    }

    //    @Test
    public void test11() {
        List<EnumEntry> enumList = tpaSaasBusinessFeignUtil.getEnumDetail("slipAttribute");
        for (EnumEntry enumEntry : enumList) {
            System.out.println("enumEntry = " + enumEntry);
        }
    }

    @Autowired
    private FieldApplicationService fieldApplicationService;

    //    @Test
    public void test12() {
        log.info("===============");
        Set<Long> set = fieldApplicationService.getQuotedField(1L);
        log.info("set = " + set);
    }

    @Autowired
    private GroupDataFeignUtil groupDataFeignUtil;

    //        @Test
    public void test13() {
        GroupDataParam param = new GroupDataParam();
        param.setParentCode("province_code");
        PageResult<AddressInfo> result = groupDataFeignUtil.getAddressList(param);
        System.out.println("result = " + result);
    }

    //    @Test
    public void test14() {
//        PageVO pageVO = pageApplicationService.preview(null, "entry", "code3:branch1:fdh20250411004");
        NewPageVO pageVO = pageApplicationService.previewNew(null, "entry", "code3:branch1:fdh20250411004");
        System.out.println("pageVO = " + JSON.toJSONString(pageVO));
    }

    //    @Test
    public void test15() {
        List<OptionSet> list = optionSetService.getListBySetId(5L);
        optionSetApplicationService.synchronizeOptionSetToRedis("PK_ComCertificateType", list);
    }

    //    @Test
    public void test16() {
        optionSetApplicationService.deleteOptionSetFromRedis("PK_ComCertificateType");
    }

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    //    @Test
    public void test17() {
        String optionSetCode = "testCode1";
        String key = Constant.OPTION_SET_KEY + optionSetCode;
        Map<Object, Object> map = redisTemplate.opsForHash().entries(key);
        System.out.println("map = \n" + map);
    }

    //    @Test
    public void test19() {
        LambdaQueryWrapper<CfgBlockDO> wrapper = new LambdaQueryWrapper<CfgBlockDO>()
                .in(CfgBlockDO::getId, Arrays.asList(3, 4, 2, 6, 5, 1));
        List<CfgBlockDO> blockDOList = blockMapper.selectList(wrapper);
        for (CfgBlockDO blockDO : blockDOList) {
            System.out.println(blockDO);
        }
        LambdaQueryWrapper<CfgBlockDO> wrapper1 = new LambdaQueryWrapper<CfgBlockDO>()
                .in(CfgBlockDO::getSequenceNumber, Arrays.asList(3, 4, 2, 6))
                .eq(CfgBlockDO::getPageId, 1);
        List<CfgBlockDO> blockDOList1 = blockMapper.selectList(wrapper1);
        for (CfgBlockDO blockDO : blockDOList1) {
            System.out.println(blockDO);
        }
        //结论：默认是按照id升序返回
    }

//    @Test
    public void test20() {
        CfgFieldDO fieldDO = pageApplicationService.getSameOriginField(16364314309920L, 17074683412768L);
        System.out.println("fieldDO = " + fieldDO);
    }
//    @Test
    public void test21() {
        pageApplicationService.pullRulesFromBase("4:rtrs:rtrsbxyxgs:GF00000413");
    }

}
