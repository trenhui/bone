package com.bone.tpa.claim.sync;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bone.core.result.Result;
import com.bone.metadata.sdk.MetadataFetchEngine;
import com.bone.metadata.sdk.SdkPropertyConfig;
import com.bone.metadata.sdk.dto.MetaFieldDTO;
import com.bone.metadata.sdk.enums.ExtendFieldModelCode;
import com.bone.metadata.sdk.enums.FieldModelDefine;
import com.bone.metadata.sdk.enums.FieldType;
import com.bone.core.util.DateParserUtil;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.api.enums.HintMsgType;
import com.bone.tpa.claim.application.dto.EnumOptionDTO;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.claim.domain.service.HintService;
import com.bone.tpa.claim.domain.service.PageEnumsSelectService;
import com.bone.tpa.core.redis.RedisLockManage;
import com.bone.tpa.core.synctask.AlertRobotManager;
import com.bone.tpa.core.util.ExtraStoreUtil;
import com.bone.tpa.facade.enums.BindTypeEnum;
import com.bone.tpa.facade.feign.MasterDataFeign;
import com.bone.tpa.facade.feign.PageModelConfigFeign;
import com.bone.tpa.facade.request.CollectionBindQueryRequest;
import com.bone.tpa.facade.request.MasterDataQueryRequest;
import com.bone.tpa.facade.request.OptionWithCnQueryRequest;
import com.bone.tpa.facade.request.OptionWithCodeQueryRequest;
import com.bone.tpa.facade.vo.ColletionBindVO;
import com.bone.tpa.facade.vo.MasterDataData;
import com.bone.tpa.facade.vo.OptionSetDTO;
import com.bone.tpa.sdk.claim.model.ExtraStoreBase;
import com.bone.tpa.sdk.dao.*;
import com.bone.tpa.sdk.util.OptionSetObject;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SyncBaseTool {

    final static public String  EXT_FIELD_PREFIX = "EX_";

   static final public  Long TENANT_ID=1L;

    @Autowired
    protected RedisLockManage redisLockManage;


    @Autowired
    protected  StringRedisTemplate stringRedisTemplate;
    @Autowired
    protected PageEnumsSelectService pageEnumsSelectService;
    @Autowired
    protected CommonLogService commonLogService;




    @Autowired
    protected InvoiceProjectRepository projectRepository;

    @Autowired
    protected AlertRobotManager alertRobotManager;

    @Autowired
    protected  InvoiceProjectItemRepository itemRepository;


    @Autowired
    protected ClaimStakeholderRepository stakeholderRepository;
    @Autowired
    protected SdkPropertyConfig sdkPropertyConfig;

    @Autowired
    protected MetadataFetchEngine metadataFetchEngine;
    /**
     * 页面配置接口
     */
    @Autowired
    protected PageModelConfigFeign pageModelConfigFeign;

    @Autowired
    protected  MasterDataFeign masterDataFeign;

    @Autowired
    protected SignRecordRepository signRecordRepository;


    @Autowired
    protected ClaimRepository claimRepository;

    @Autowired
    protected ClaimInvoiceRepository invoiceRepository;

    @Autowired
    protected HintService hintService;


    @Autowired
    protected ClaimImageRepository imageRepository;


    @Autowired
    protected InvoiceImageRelationRepository imageRelationRepository;

    protected final  String COLLECT_HINT_TEMPLATE = "无法匹配到选项集，传入中文:%s";

    protected final  String ENUM_HINT_TEMPLATE = "无法匹配到枚举，传入中文:%s,传入code:%s";

    protected final String MASTER_DATA_HINT_TEMPLATE="无法匹配到主数据，传入中文:%s";

    private Integer mockCode = 0;


    protected void saveHint(Long objectId, HintMsgType hintMsgType, Map<String,String> hintMap,Context context){

        hintService.saveHint(objectId, hintMsgType, hintMap,context.getClaimNumber());
    }

    /**
     * 返回tpa时候，把扩展字段映射字符串回去
     * @param dataMap
     * @return
     */
    protected Map<String,String> extendMapToTpaMap(ExtraStoreBase data,
                                                   String bizIdentityCode,
                                                   FieldModelDefine domainDefine,
                                                   Map<String,MetaExtendFieldConfig> extFieldConfigMap,
                                                   Map<String,Object> dataMap){
        Map<String,String> rs = new HashMap<>();
        if( dataMap == null){
            return rs;
        }
        log.info("extendMapToTpaMap,dataMap:{},domainDefine:{}",dataMap,domainDefine);
        for(Map.Entry<String,Object> entry: dataMap.entrySet()){
            String key = entry.getKey();
            Object value   = entry.getValue();
            log.info("extendMapToTpaMap,key:{},value:{}",key,value);
            if(!key.startsWith(EXT_FIELD_PREFIX)){
                continue;
            }

           MetaExtendFieldConfig  extendFieldConfig =    extFieldConfigMap.get(key);
            if( extendFieldConfig == null){
                continue;
            }
            String  tpaKey = StringUtils.substringAfter(key, EXT_FIELD_PREFIX);

            if( value == null){
                log.info("extendMapToTpaMap,key:{},value:{},setemtpy,becase value is null",key,value);
                rs.put(tpaKey,"");
                continue;
            }
            String fieldType =  extendFieldConfig.getFieldType();
            if (FieldType.TEXT.getCode().equals(fieldType)
                    ||
                    FieldType.VARCAHR.getCode().equals(fieldType)
            ) {
                String strValue = (String)value;
                rs.put(tpaKey,strValue);
                ColletionBindVO bindVO =   extendFieldConfig.getBindVO();
                if( bindVO != null && StringUtils.isNotBlank(strValue)){

                    BindTypeEnum bindTypeEnum =  BindTypeEnum.getByCode(bindVO.getBindType());
                    if(bindTypeEnum == BindTypeEnum.选项集){
                        OptionSetObject optionSetObject = null;
                        if(data!= null){
                            optionSetObject =  ExtraStoreUtil.getOptionSetValue(key,data.getExtraStore());
                        }
                        if( optionSetObject != null){
                            rs.put(tpaKey,optionSetObject.getName());
                        }else{
                            String matchCn = collectCnByCode(key,bizIdentityCode,domainDefine,strValue);
                            if(StringUtils.isBlank(matchCn)){
                                throw new RuntimeException("无法匹配选项集:"+key);
                            }
                            rs.put(tpaKey,matchCn);
                        }

                    }else if (bindTypeEnum == BindTypeEnum.枚举){
                        String enumsCode =   bindVO.getBindTarget();
                        if( StringUtils.isBlank(enumsCode)){
                            throw new RuntimeException("扩展字段枚举绑定配置错误:"+key);
                        }

                        EnumOptionDTO checkEnums =   pageEnumsSelectService.getOptionByCode(enumsCode,strValue);
                        if(checkEnums == null){
                            throw new RuntimeException("无法匹配枚举:"+key);
                        }
                        rs.put(tpaKey,checkEnums.getName());
                    }else {
                        throw new RuntimeException("not support bind type :"+bindTypeEnum);
                    }


                }
            }
            if (FieldType.INT.getCode().equals(fieldType)
            ) {
                rs.put(tpaKey,value==null?null:value.toString());

            }

            if (FieldType.DATE_TIME.getCode().equals(fieldType)
            ) {
                try {

                    String datestr = DateParserUtil.formatDate((Date) value,"yyyy-MM-dd");
                    rs.put(tpaKey,datestr);
                } catch (Exception e) {

                }
            }
            if (FieldType.DECIMAL.getCode().equals(fieldType)
            ) {

                rs.put(tpaKey,value==null?null:value.toString());
            }
        }

        return rs;
    }
    protected  Map<String,Object> mapToExtendMap(FieldModelDefine modelDefine,
                                                 Context context,
                                                 ExtraStoreBase extraStoreDto,
                                                 String bizIdentityCode,
                                                 Long claimNumber,
                                                 Map<String,String> hintMap ,
                                                 List<String> clearFieldList,
                                                 Map<String,MetaExtendFieldConfig> extFieldConfigMap,
                                                 Map<String,String> dataMap,ExtraStoreBase storeBaseDto){

        Map<String,Object> rs = new HashMap<>();
        if( dataMap == null){
            return rs;
        }


        for(Map.Entry<String,String> entry: dataMap.entrySet()){
           String extKey =EXT_FIELD_PREFIX+ entry.getKey();
           String extValue = entry.getValue();
           MetaExtendFieldConfig extendFieldConfig =   extFieldConfigMap.get(extKey);
           if( extendFieldConfig == null){
               //批量不到，则跳过
               hintMap.put(extKey,String.format("无法匹配扩展字段:%s ,value :%s",extKey,extValue));
               alertRobotManager.doAlertAsyncDefault(  String.format("从tpa同步数据异常, 赔案 %s, 主体 %s 无法匹配扩展字段:%s ,value :%s",claimNumber,bizIdentityCode,extKey,extValue));
               continue;
           }
           if(StringUtils.isBlank(extValue)){
               //clearFieldList.add(extKey);
               continue;
           }
           String fieldType =  extendFieldConfig.getFieldType();
            if (FieldType.TEXT.getCode().equals(fieldType)
                    ||
                    FieldType.VARCAHR.getCode().equals(fieldType)
            ) {
               rs.put(extKey,extValue);
                ColletionBindVO bindVO =   extendFieldConfig.getBindVO();
                if( bindVO != null && StringUtils.isNotBlank(extValue)){
                    BindTypeEnum bindTypeEnum =  BindTypeEnum.getByCode(bindVO.getBindType());
                    if(bindTypeEnum == BindTypeEnum.选项集){

                        OptionSetDTO matchDto  = collectCodeByCn(extKey,bizIdentityCode,modelDefine,extValue);
                        String matchCode = "";
                        if(matchDto ==null){
                            Map<String, ColletionBindVO>  allBindMap =    context.getAllOptionSetMap();
                            if(allBindMap != null){
                                ColletionBindVO fieldConfigvo =     allBindMap.get(extKey);
                                if( fieldConfigvo != null && fieldConfigvo.getDefaultValue() != null){
                                    matchDto = fieldConfigvo.getDefaultValue();
                                }
                            }

                        }
                        if( matchDto!= null){
                            matchCode = matchDto.getCode();
                            //设置扩展字段
                            ExtraStoreUtil.fillExtraStoreWithOrigin(extraStoreDto,extKey,matchDto);
                        }
                        ExtraStoreUtil.fillExtraStoreWithOrigin(storeBaseDto,extKey,matchDto);
                        rs.put(extKey,matchCode);
                        if(StringUtils.isBlank(matchCode)){
                            alertRobotManager.doAlertAsyncDefault(
                                    String.format("匹配选项集code异常, 赔案 %s, 主体 %s, 选项集code: %s, 页面code: %s, chinese: %s",
                                            claimNumber, bizIdentityCode,bindVO.getBindTarget(),extKey,extValue));
                            hintMap.put(extKey,String.format(COLLECT_HINT_TEMPLATE,extValue));
                        }
                    }else if (bindTypeEnum == BindTypeEnum.枚举){
                        String enumsCode =   bindVO.getBindTarget();
                        if( StringUtils.isBlank(enumsCode)){
                            throw new RuntimeException("扩展字段枚举绑定配置错误:"+extKey);
                        }

                        EnumOptionDTO checkEnums =   pageEnumsSelectService.getOptionByName(enumsCode,extValue);
                        if(checkEnums == null){
                            hintMap.put(extKey,String.format(ENUM_HINT_TEMPLATE,extValue,enumsCode));
                            continue;
                        }
                        rs.put(extKey,checkEnums.getCode());


                    }else {
                        throw new RuntimeException("not support bind type :"+bindTypeEnum);
                    }

                }
            }

            if (FieldType.INT.getCode().equals(fieldType)
            ) {
                try {
                    Integer testInteger =   Integer.valueOf(extValue);
                    rs.put(extKey,testInteger);
                } catch (Exception e) {
                    //not match
                    //删除这个字段
                   // clearFieldList.add(extKey);
                    hintMap.put(extKey,String.format("输入字段不是数字类型：%s",extValue));
                }
            }

            if (FieldType.DATE_TIME.getCode().equals(fieldType)
            ) {
                try {

                    Date date = DateParserUtil.parseDate(extValue);
                    rs.put(extKey,date);
                } catch (Exception e) {
                    //not match
                    //删除这个字段
                    //clearFieldList.add(extKey);
                    hintMap.put(extKey,String.format("输入字段不是日期格式类型：%s",extValue));
                }
            }
            if (FieldType.DECIMAL.getCode().equals(fieldType)
            ) {

                try {

                    BigDecimal testBigdemical =new  BigDecimal(extValue);
                    rs.put(extKey,testBigdemical);
                } catch (Exception e) {
                    //not match
                    //删除这个字段
                   // clearFieldList.add(extKey);
                    hintMap.put(extKey,String.format("输入字段不是Decimal格式类型：%s",extValue));
                }


            }

        }
        return  rs;
    }

    /**
     * 获取一个表的阔爱字段的配置
     * key->扩展字段的key
     * value-> 扩展字段的value
     * @param bizIdentityCode
     * @param modelDefine
     * @return
     */
    protected Map<String,MetaExtendFieldConfig> getExtendConfig(String bizIdentityCode,
                                                                FieldModelDefine modelDefine){

        List<ExtendFieldModelCode> modelCodeList =    ExtendFieldModelCode.getByModelDefine(modelDefine);
        if(PkListUtil.isEmpty(modelCodeList)){
            throw new IllegalArgumentException("modelCodeList is mat null:"+modelDefine);
        }

        List<String> modelCodeListStr = modelCodeList.stream().map(ExtendFieldModelCode::getCode).collect(Collectors.toList());

        String tableName = modelDefine.getTableName();
        Map<String, ColletionBindVO> collectionBindMap = loadCollectionBind(bizIdentityCode,modelCodeList);
        List<MetaFieldDTO>  allExtendFieldList =   metadataFetchEngine.getAllBizIdentityField(sdkPropertyConfig.getAppcode(),
                tableName,bizIdentityCode);
        if(PkListUtil.isEmpty(allExtendFieldList)){
            return new HashMap<>();
        }
        //过滤只属于model 范围内的拓展字段
        //为啥呢，这很扯蛋，因为人员表是共用一张表的,因此这要做一次过滤
        allExtendFieldList = allExtendFieldList.stream().filter(t->modelCodeListStr.contains(t.getFieldModelCode())).
                collect(Collectors.toList());
        if(PkListUtil.isEmpty(allExtendFieldList)){
            return new HashMap<>();
        }

        Map<String,MetaExtendFieldConfig> rs = new HashMap<>();
        for(MetaFieldDTO metaFieldDTO: allExtendFieldList){
            MetaExtendFieldConfig config = new MetaExtendFieldConfig();
            try {
                BeanUtils.copyProperties(config,metaFieldDTO);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            ColletionBindVO bindVO =   collectionBindMap.get(config.getFieldName());
            if( bindVO!= null){
                config.setBindVO(bindVO);
            }
            rs.put(config.getFieldName(),config);
        }

        return rs;
    }


    /**
     * 根据中文匹配主数据，获取provice，city，area 的code
     * @param province
     * @param city
     * @param area
     * @return
     */
    protected String matchProviceCityAreaWithChinese(String province,String city,String area,int level){
        Map<String,List<String>> rsMap = new HashMap<>();
        List<String> nameList = Lists.newArrayList();
        if(province == null){
            province = "";
        }
        if(level>0){
            nameList.add(province);

        }
        if(city == null){
            city = "";
        }
        if(level>1){
            nameList.add(city);

        }
        if(area == null){
            area = "";
        }
        if(level>2){
            nameList.add(area);
        }
        rsMap.put("desc",nameList);
        List<String> codeList = Lists.newArrayList();
        String provinceCode = "";
        if(StringUtils.isNotBlank(province)){
            MasterDataQueryRequest provinceRequest = new MasterDataQueryRequest();
            provinceRequest.setType("province_code");
            provinceRequest.setName(province);
            Result<List<MasterDataData>>  remoetRs =    masterDataFeign.queryData(provinceRequest);
            if( !remoetRs.getSuccess()){
                log.error(" masterDataFeign.queryData error ,request:{},return:{}",JSONObject.toJSONString(provinceRequest),JSONObject.toJSONString(remoetRs));

                throw new RuntimeException("请求主数据省份失败,rs:"+JSONObject.toJSONString(remoetRs));
            }
            MasterDataData masterDataData = PkListUtil.first(  remoetRs.getData());
            if( masterDataData != null){
                provinceCode   = masterDataData.getCode();
            }else{
                alertRobotManager.doAlertAsyncDefault(
                        String.format("省份匹配失败，主数据无法取到code:%s",province));
            }
        }
        String cityCode = "";
        if(StringUtils.isNotBlank(provinceCode) && StringUtils.isNotBlank(city)){
            MasterDataQueryRequest cityRequest = new MasterDataQueryRequest();
            cityRequest.setType("city_code");
            cityRequest.setName(city);
            cityRequest.setParentCode(provinceCode);
            Result<List<MasterDataData>>  remoetRs =    masterDataFeign.queryData(cityRequest);
            if( !remoetRs.getSuccess()){
                log.error(" masterDataFeign.queryData error ,request:{},return:{}",JSONObject.toJSONString(cityRequest),JSONObject.toJSONString(remoetRs));

                throw new RuntimeException("请求主数据城市失败,rs:"+JSONObject.toJSONString(remoetRs));
            }
            MasterDataData masterDataData = PkListUtil.first(  remoetRs.getData());
            if( masterDataData != null){
                cityCode   = masterDataData.getCode();
            }else{
                alertRobotManager.doAlertAsyncDefault(
                        String.format("城市匹配失败，主数据无法取到code:%s",city));
            }
        }
        String areaCode = "";

        if(StringUtils.isNotBlank(cityCode) && StringUtils.isNotBlank(area)){
            MasterDataQueryRequest cityRequest = new MasterDataQueryRequest();
            cityRequest.setType("area_code");
            cityRequest.setName(area);
            cityRequest.setParentCode(cityCode);
            Result<List<MasterDataData>>  remoetRs =    masterDataFeign.queryData(cityRequest);
            if( !remoetRs.getSuccess()){

                log.error(" masterDataFeign.queryData error ,request:{},return:{}",JSONObject.toJSONString(cityRequest),JSONObject.toJSONString(remoetRs));

                throw new RuntimeException("请求主数据地区失败,rs:"+JSONObject.toJSONString(remoetRs));
            }
            MasterDataData masterDataData = PkListUtil.first(  remoetRs.getData());
            if( masterDataData != null){
                areaCode   = masterDataData.getCode();
            }else{
                alertRobotManager.doAlertAsyncDefault(
                        String.format("区域匹配失败，主数据无法取到code:%s",area));
            }
        }
        if(level>0){
            codeList.add(provinceCode);

        }
        if(level>1){
            codeList.add(cityCode);

        }
        if(level>2){
            codeList.add(areaCode);
        }
        rsMap.put("code",codeList);
        return  JSONObject.toJSONString(rsMap);
    }


    protected List<String> getRegionCn(String region){
        if(StringUtils.isBlank(region)){
            region = "{}";
        }
        List<String> rs = PkListUtil.newArrayList();

        try {
            JSONObject jsonObject = JSONObject.parseObject(region);
            JSONArray jsonArray =  jsonObject.getJSONArray("desc");

            try {
                String provinceName = jsonArray.getString(0);
                rs.add(provinceName);
            } catch (Exception e) {
               rs.add("");
            }
            try {
                String city = jsonArray.getString(1);
                rs.add(city);
            } catch (Exception e) {
                rs.add("");
            }

            try {
                String area = jsonArray.getString(2);
                rs.add(area);
            } catch (Exception e) {
                rs.add("");
            }
        } catch (Exception e) {
            rs.add("");
            rs.add("");
            rs.add("");
        }
        return rs;

    }


    protected  String nullIfEmpty(String str){
        if( str == null){
            return  "";
        }
        return str;
    }


    public OptionSetDTO matchCollectionCodeForSync(String chinese,
                                         String pageBizCode,
                                         Context context,
                                         FieldModelDefine modelDefine,
                                         boolean hintRecord,
                                         Map<String,String> hintMap){
        OptionSetDTO rs =  matchCollectionCode(chinese, pageBizCode, context.getBizIdentityCode(),
                context.getClaimNumber(), modelDefine, hintRecord, hintMap);
        if(rs != null){
            return rs;
        }
       if(StringUtils.isNotBlank(chinese)){
            return null;
        }
        Map<String, ColletionBindVO>  allBindMap =    context.getAllOptionSetMap();
        if( allBindMap != null){
            //加载默认值
            ColletionBindVO bindVO =   allBindMap.get(pageBizCode);

            if( bindVO != null && bindVO.getDefaultValue() != null){
                log.info("defaultpageBizCode:{},bindVO:{}",pageBizCode,JSONObject.toJSONString(bindVO));
                rs = bindVO.getDefaultValue();
            }
        }
        return rs;
    }

    public  String getOptionSetDTOCode(OptionSetDTO optionSetDTO){
        if(optionSetDTO == null){
            return "";
        }
        return nullIfEmpty(optionSetDTO.getCode());
    }

    public OptionSetDTO matchCollectionCode(String chinese,
                                         String pageBizCode,
                                         String bizIdentityCode,
                                         Long claimNumber,
                                         FieldModelDefine modelDefine,
                                         boolean hintRecord,
                                         Map<String,String> hintMap){

        return  matchCollectionCodeWithAlert(chinese, pageBizCode, bizIdentityCode,
                claimNumber, modelDefine, hintRecord, true, hintMap);
    }
    public OptionSetDTO matchCollectionCodeWithAlert(String chinese,
                                         String pageBizCode,
                                         String bizIdentityCode,
                                         Long claimNumber,
                                         FieldModelDefine modelDefine,
                                         boolean hintRecord,
                                         boolean alert,
                                         Map<String,String> hintMap){
        OptionSetDTO rs = null;
        if(StringUtils.isNotBlank(chinese)){
              rs = collectCodeByCn(pageBizCode, bizIdentityCode, modelDefine, chinese);
            if(rs ==null){

                //如果匹配不到，则清空这个字段
                //记录错误信息
                if( hintRecord){
                    hintMap.put(pageBizCode, String.format(COLLECT_HINT_TEMPLATE,chinese));

                    String alertRedistKey = String.format("saas-opsentset-alsert-%s-%s-%s",bizIdentityCode,pageBizCode,chinese);
                    String checkAlerted =  stringRedisTemplate.opsForValue().get(alertRedistKey);
                    if(alert && StringUtils.isBlank(checkAlerted)){
                        alertRobotManager.doAlertAsyncDefault(
                                String.format("匹配选项集code异常 赔案 %s, 主体 %s, 页面code: %s, chinese: %s",claimNumber,bizIdentityCode,pageBizCode,chinese));
                        stringRedisTemplate.opsForValue().setIfAbsent(alertRedistKey,"1",1, TimeUnit.DAYS);
                    }

                }
            }
        }
        return  rs;
    }
    /**
     * 根据选项集中文值，获取code
     * @param pageBizCode
     * @param bizIdentityCode
     * @param domainDefine
     * @param chinese
     * @return
     */
    public OptionSetDTO collectCodeByCn(String pageBizCode,
                                     String bizIdentityCode,
                                     FieldModelDefine domainDefine ,String chinese){



        OptionSetDTO dto =   getOptionSetDTByCn(pageBizCode, bizIdentityCode, domainDefine, chinese);
        return  dto;
    }


    protected  OptionSetDTO getOptionSetDTByCn(String pageBizCode,
                                               String bizIdentityCode,
                                               FieldModelDefine domainDefine ,String chinese){
        List<ExtendFieldModelCode>  pageModelCodeList =  ExtendFieldModelCode.getByModelDefine(domainDefine);
        List<String> modelCodeList =pageModelCodeList.stream().map(ExtendFieldModelCode::getCode).collect(Collectors.toList());
        OptionWithCnQueryRequest request = new OptionWithCnQueryRequest();
        request.setBizIdentityCode(bizIdentityCode);
        request.setModeCodeList(modelCodeList);
        request.setFieldCode(pageBizCode);
        request.setOptionCn(chinese);
        request.setModelCodeMockTag(pageBizCode+"-"+String.join("",modelCodeList));
        Result<OptionSetDTO>  remoteRs=   pageModelConfigFeign.queryCollectionByOptionCn(request);
        if(!remoteRs.getSuccess()){
            log.error("queryCollectionByOptionCn error ,request:{},return:{}",JSONObject.toJSONString(request),JSONObject.toJSONString(remoteRs));

            throw new RuntimeException("查询选项集中文失败");
        }
        OptionSetDTO dto =   remoteRs.getData();

        return dto;
    }





    private String collectCnByCode(String pageBizCode,
                                   String bizIdentityCode,
                                   FieldModelDefine domainDefine ,String code){


        List<ExtendFieldModelCode>  pageModelCodeList =  ExtendFieldModelCode.getByModelDefine(domainDefine);
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
        Result<OptionSetDTO>  remoteRs=   pageModelConfigFeign.queryCollectionByOptionCode(request);
        log.info("collectCnByCode,param:{},return:{}",JSONObject.toJSONString(request),JSONObject.toJSONString(remoteRs));
        if(!remoteRs.getSuccess()){
            log.error("queryCollectionByOptionCode error ,request:{},return:{}",JSONObject.toJSONString(request),JSONObject.toJSONString(remoteRs));
            throw new RuntimeException("查询选项集code失败");
        }
        OptionSetDTO dto =   remoteRs.getData();
        if( dto == null){
            return "";
        }
        return  dto.getName();
    }
    /**
     * 获取选项集绑定信息
     * @param bizIdentityCode
     * @param modelCodeList
     * @return
     */
    protected Map<String,ColletionBindVO> loadCollectionBind(String bizIdentityCode, List<ExtendFieldModelCode> modelCodeList){
        CollectionBindQueryRequest collectionBindQueryRequest = new CollectionBindQueryRequest();
        collectionBindQueryRequest.setBizIdentityCode(bizIdentityCode);
        collectionBindQueryRequest.setModelCodeList(modelCodeList.stream().map(ExtendFieldModelCode::getCode).collect(Collectors.toList()));
        //做mock用的，这个字段没啥用
        collectionBindQueryRequest.setModelCodeMockTag(String.join("",collectionBindQueryRequest.getModelCodeList()));
        //赔案的字段
        Result<List<ColletionBindVO>> remoteRs  = pageModelConfigFeign.queryCollectionBind(collectionBindQueryRequest);
        if(!remoteRs.getSuccess()){
            log.error("queryCollectionBind error ,request:{},return:{}",JSONObject.toJSONString(collectionBindQueryRequest),JSONObject.toJSONString(remoteRs));
            throw new RuntimeException("获取字段绑定关系失败:"+JSONObject.toJSONString(remoteRs));
        }
        List<ColletionBindVO> list =   remoteRs.getData();
        if(PkListUtil.isEmpty(list)){
            return  new HashMap<>();
        }
        return  PkListUtil.listToMap(list, ColletionBindVO::getFieldCode);
    }
}
