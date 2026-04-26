package com.bone.lowcode.infra.application.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bone.core.exception.ServiceException;
import com.bone.lowcode.infra.application.dto.optionSet.*;
import com.bone.lowcode.infra.application.vo.PageResult;
import com.bone.lowcode.infra.application.vo.optionSet.*;
import com.bone.lowcode.infra.domain.constant.Constant;
import com.bone.lowcode.infra.domain.model.LinkedDisplayRuleEntry;
import com.bone.lowcode.infra.domain.service.*;
import com.bone.lowcode.infra.domain.valueobject.DataSourceTypeEnum;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.LinkedDisplayRuleTypeEnum;
import com.bone.lowcode.infra.domain.valueobject.OptionSetNodeEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.*;
import com.bone.lowcode.infra.infrastructure.persistence.dto.FieldExtraConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OptionSetApplicationService {

    @Autowired
    private FieldService fieldService;

    @Autowired
    private OptionSetService optionSetService;

    @Autowired
    private PageService pageService;

    @Autowired
    private ModelService modelService;

    @Autowired
    private FieldLinkedDisplayRuleService fieldLinkedDisplayRuleService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;


    public PageResult<OptionSetVO> getSetList(Long pageNum, Long pageSize) {
        Page<OptionSet> page = optionSetService.getRootList(pageNum, pageSize);
        List<OptionSet> optionSetList = page.getRecords();
        List<OptionSetVO> voList = getOptionSetVOList(optionSetList);

        return new PageResult<>(pageNum, pageSize, page.getTotal(), voList);
    }

    private List<OptionSetVO> getOptionSetVOList(List<OptionSet> optionSetList) {
        List<OptionSetVO> voList = optionSetList.stream().map(i -> {
            OptionSetVO vo = new OptionSetVO();
            BeanUtils.copyProperties(i, vo);
            vo.setId(i.getId().toString());
            vo.setSetName(i.getName());
            vo.setSetCode(i.getCode());
            return vo;
        }).toList();
        return voList;
    }

    public OptionSetInfoVO getSetDetail(Long optionSetId) {
        OptionSet optionSet = optionSetService.getById(optionSetId);
        if (optionSet == null || optionSet.getNodeType() != OptionSetNodeEnum.ROOT_NODE.getCode()) {
            throw new ServiceException(500, "目标选项集不存在,id:" + optionSetId);
        }

        OptionSetInfoVO optionSetInfo = new OptionSetInfoVO();
        optionSetInfo.setId(optionSet.getId().toString());
        optionSetInfo.setName(optionSet.getName());
        optionSetInfo.setCode(optionSet.getCode());
        optionSetInfo.setDesc(optionSet.getSetDesc());
        optionSetInfo.setUseScope(optionSet.getUseScope());
        optionSetInfo.setStatus(optionSet.getStatus());
        optionSetInfo.setExtraPropertyKey(optionSet.getExtraPropertyKeyList());
        return optionSetInfo;
    }

    public OptionValuePageVO valuePage(Long optionSetId, Integer pageNum, Integer pageSize, String optionCode, String optionName) {
        OptionSet optionSet = optionSetService.getById(optionSetId);
        if (optionSet == null || optionSet.getNodeType() != OptionSetNodeEnum.ROOT_NODE.getCode()) {
            throw new ServiceException(500, "目标选项集不存在,id:" + optionSetId);
        }

        OptionValuePageVO re = new OptionValuePageVO();
        re.setExtraPropertyKey(optionSet.getExtraPropertyKey());

        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OptionSet::getParentId, optionSetId)
                .eq(OptionSet::getNodeType, OptionSetNodeEnum.LEAF_NODE.getCode())
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode());
        wrapper.like(optionCode != null, OptionSet::getCode, optionCode);
        wrapper.like(optionName != null, OptionSet::getName, optionName);

        Page<OptionSet> page = optionSetService.optionValuePage(pageNum, pageSize, wrapper);
        PageResult<OptionValueVO> pageResult = new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal());
        re.setOptionValueInfo(pageResult);

        List<OptionSet> rowList = page.getRecords();
        if (!CollectionUtils.isEmpty(rowList)) {
            List<OptionValueVO> valueVOList = rowList.stream().map(value -> {
                OptionValueVO valueVO = new OptionValueVO();
                valueVO.setId(value.getId().toString());
                valueVO.setCode(value.getCode());
                valueVO.setName(value.getName());
                valueVO.setExtraProperty(value.getExtraProperty());
                valueVO.setStatus(value.getStatus());
                return valueVO;
            }).toList();
            pageResult.setRows(valueVOList);
        }
        return re;
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean addOptionSet(AddOptionSetDTO param) {
        String setName = param.getSetName();
        String setCode = param.getSetCode();
        if (!StringUtils.hasText(setName) || !StringUtils.hasText(setCode))
            throw new ServiceException(500, "选项集名称、选项集标识不能为空");

        List<OptionSet> listBySetName = optionSetService.getRootDOListBySetName(setName);
        if (!CollectionUtils.isEmpty(listBySetName)) {
            throw new ServiceException(500, "选项集名称[" + setName + "]已存在");
        }

        List<OptionSet> listBySetCode = optionSetService.getRootDOListBySetCode(setCode);
        if (!CollectionUtils.isEmpty(listBySetCode)) {
            throw new ServiceException(500, "选项集code[" + setCode + "]已存在");
        }

        List<AddSetValueDTO> valueList = param.getValueList();
        if (!CollectionUtils.isEmpty(valueList)) {
            Set<String> codeSet = new HashSet<>();
            Set<String> nameSet = new HashSet<>();
            for (AddSetValueDTO value : valueList) {
                String valueCode = value.getValueCode();
                String valueName = value.getValueName();

                if (!StringUtils.hasText(valueCode) || !StringUtils.hasText(valueName)) {
                    throw new ServiceException(500, "选项值标识、选项值名称必填");
                }

                boolean f1 = codeSet.add(valueCode);
                if (!f1) {
                    throw new ServiceException(500, "当前选项集内选项值标识[" + valueCode + "]重复");
                }

                boolean f2 = nameSet.add(valueName);
                if (!f2) {
                    throw new ServiceException(500, "当前选项集内选项值名称[" + valueName + "]重复");
                }
            }
        }

        Date now = new Date();
        OptionSet optionSet = new OptionSet();
        optionSet.setNodeType(OptionSetNodeEnum.ROOT_NODE.getCode());
        optionSet.setUseScope((byte) 1);
        optionSet.setName(setName);
        optionSet.setCode(setCode);
        optionSet.setSetDesc(param.getSetDesc());
        optionSet.setExtraPropertyKey(param.getExtraPropertyKey());
        optionSet.setCreateTime(now);
        Boolean flag = optionSetService.add(optionSet);
        if (!flag) throw new ServiceException(500, "创建选项集失败");

        if (CollectionUtils.isEmpty(valueList)) {
            return true;
        }

        Long optionSetId = optionSet.getId();
        if (optionSetId == null) {
            throw new ServiceException(500, "创建选项集id异常");
        }

        List<OptionSet> list = valueList.stream().map(i -> {
            OptionSet value = new OptionSet();
            value.setNodeType(OptionSetNodeEnum.LEAF_NODE.getCode());
            value.setParentId(optionSetId);
            value.setCode(i.getValueCode());
            value.setName(i.getValueName());
            value.setStatus(i.getValueEnable());
            value.setExtraProperty(mergeNullProperty(i.getExtraProperty(), optionSet.getExtraPropertyKeyList()));
            value.setCreateTime(now);
            return value;
        }).toList();
        return optionSetService.batchSave(list);
    }

    public Boolean deleteOptionSet(Long optionSetId) {
        OptionSet optionSet = optionSetService.getById(optionSetId);
        if (optionSet.getNodeType() != OptionSetNodeEnum.ROOT_NODE.getCode())
            throw new ServiceException(500, "根据id没有找到选项集");

        CfgFieldDO fieldDO = fieldService.getAnyFieldWithDatasource(optionSet.getCode());
        if (fieldDO != null) {
            throw new ServiceException(500, "此选项集正在被字段使用，请先停止使用再删除。相关字段code:" + fieldDO.getBizCode() + ",字段名:" + fieldDO.getBizName());
        }

        return transactionTemplate.execute(status -> {
            try {
                optionSetService.deleteById(optionSetId);
                optionSetService.deleteByParentId(optionSetId);
                return true;
            } catch (Exception e) {
                log.error("删除选项集发生异常:", e);
                status.setRollbackOnly();
                return false;
            }
        });
    }

    public List<QueryCollectionBindVO> queryCollectionBind(QueryCollectionBindDTO param) {
        String bizIdentityCode = param.getBizIdentityCode();
        List<CfgPageDO> pageDOList = pageService.getDOListByIdentityCode(bizIdentityCode);
        if (CollectionUtils.isEmpty(pageDOList)) {
            throw new ServiceException(500, "请先创建目标主体的页面,参数:" + bizIdentityCode);
        }
        List<Long> pageIdList = pageDOList.stream().map(CfgPageDO::getId).toList();

        List<String> modelCodeList = param.getModelCodeList();
        List<CfgModelDO> modelDOList = modelService.getDOByCodePageId(modelCodeList, pageIdList);
        if (CollectionUtils.isEmpty(modelDOList)) {
            throw new ServiceException(500, "根据模型code未查询到模型,modelCodeList:" + modelCodeList + ",pageIdList:" + pageIdList);
        }
        Map<Long, CfgModelDO> modelDOMap = modelDOList.stream().collect(Collectors.toMap(CfgModelDO::getId, i -> i));
        List<Long> modelIdList = modelDOList.stream().map(CfgModelDO::getId).toList();

        List<CfgFieldDO> fieldDOList = fieldService.getSelectDOByParam(pageIdList, modelIdList);
        Map<String,CfgFieldDO> fieldDOMap = new HashMap<>();//相同code的字段用同一个数据源
        for(CfgFieldDO fieldDO: fieldDOList){
            String bizCode = fieldDO.getBizCode();
            if((!fieldDOMap.containsKey(bizCode))){
                fieldDOMap.put(bizCode,fieldDO);
                continue;
            }
            if(fieldDO.getDatasourceType()!= null && fieldDO.getDatasourceType() == 1 &&
                    fieldDO.getDatasourceCode() != null && fieldDO.getDefaultValue() != null){
                fieldDOMap.put(bizCode,fieldDO);
            }
        }
        Set<CfgFieldDO> set =new HashSet<>();
        fieldDOMap.entrySet().stream().forEach(t->{
            set.add(t.getValue());
        });

        List<QueryCollectionBindVO> res = new ArrayList<>();
        for (CfgFieldDO fieldDO : set) {
            QueryCollectionBindVO vo = new QueryCollectionBindVO();
            res.add(vo);
            vo.setBizIdentityCode(bizIdentityCode);
            vo.setModeCode(modelDOMap.get(fieldDO.getModelId()).getCode());
            vo.setFieldCode(fieldDO.getBizCode());
            vo.setBindType(fieldDO.getDatasourceType());
            vo.setBindTarget(fieldDO.getDatasourceCode());

            if(vo.getBindType()!= null && vo.getBindType() == 1
                    &&org.apache.commons.lang3.StringUtils.isNotBlank(fieldDO.getDatasourceCode())
                    && org.apache.commons.lang3.StringUtils.isNotBlank(fieldDO.getDefaultValue())){
                //选项集默认值
                OptionSet optionSet = optionSetService.getRootSetByCode(fieldDO.getDatasourceCode());
                if (optionSet != null) {
                    OptionSet value = optionSetService.getByParentCodeCode(optionSet.getId(), fieldDO.getDefaultValue());
                    if (value != null) {
                        QueryCollectionByOptionCnVO defaultValue = new QueryCollectionByOptionCnVO();
                        defaultValue.setName(value.getName());
                        defaultValue.setCode(value.getCode());
                        defaultValue.setExtraProperty(value.getExtraProperty());
                        vo.setDefaultValue(defaultValue);
                    }
                }
            }
        }

        return res;
    }

    public QueryCollectionByOptionCnVO queryCollectionByOptionCn(QueryCollectionByOptionCnDTO param) {
        String bizIdentityCode = param.getBizIdentityCode();
        List<CfgPageDO> pageDOList = pageService.getDOListByIdentityCode(bizIdentityCode);
        if (CollectionUtils.isEmpty(pageDOList)) {
            throw new ServiceException(500, "请先创建目标主体的页面,参数:" + bizIdentityCode);
        }
        List<Long> pageIdList = pageDOList.stream().map(CfgPageDO::getId).toList();

        List<String> modelCodeList = param.getModeCodeList();
        List<CfgModelDO> modelDOList = modelService.getDOByCodePageId(modelCodeList, pageIdList);
        if (CollectionUtils.isEmpty(modelDOList)) {
            throw new ServiceException(500, "根据模型code未查询到模型,modelCodeList:" + modelCodeList + ",pageIdList:" + pageIdList);
        }
        Map<Long, CfgModelDO> modelDOMap = modelDOList.stream().collect(Collectors.toMap(CfgModelDO::getId, i -> i));
        List<Long> newModelIdList = modelDOList.stream().map(CfgModelDO::getId).toList();

        String fieldCode = param.getFieldCode();
        List<CfgFieldDO> fieldDOList = fieldService.getSelectDOByParam(fieldCode, pageIdList, newModelIdList);
        if (CollectionUtils.isEmpty(fieldDOList)) {
            return null;
        }

        Set<Long> modelIdSet = fieldDOList.stream().map(CfgFieldDO::getModelId).collect(Collectors.toSet());
        Set<String> modelCodeSet = modelIdSet.stream().map(modelDOMap::get).map(CfgModelDO::getCode).collect(Collectors.toSet());
        if (!CollectionUtils.isEmpty(modelCodeSet) && modelCodeSet.size() > 1) {
            throw new ServiceException(500, "根据查询条件匹配到多个模型的字段, fieldCode:" + fieldCode + ",modelCodeSet:" + modelCodeSet);
        }
        CfgFieldDO fieldDO = fieldDOList.get(0);

        String optionCn = param.getOptionCn();
        Byte datasourceType = fieldDO.getDatasourceType();
        String datasourceCode = fieldDO.getDatasourceCode();
        if (Objects.equals(datasourceType, DataSourceTypeEnum.OPTION_SET.getType())) {
            OptionSet optionSet = optionSetService.getRootSetByCode(datasourceCode);
            if (optionSet != null) {
                OptionSet value = optionSetService.getByParentCodeName(optionSet.getId(), optionCn);
                if (value != null) {
                    QueryCollectionByOptionCnVO  rs = new QueryCollectionByOptionCnVO();
                    rs.setCode(value.getCode());
                    rs.setName(value.getName());
                    String extraConfig  =   fieldDO.getExtraConfig();
                    if(org.apache.commons.lang3.StringUtils.isBlank(extraConfig)){
                        extraConfig = "{}";
                    }
                    rs.setExtraProperty(value.getExtraProperty());
                    FieldExtraConfig fieldExtraConfig =
                            JSONObject.parseObject(extraConfig,FieldExtraConfig.class);
                    rs.setOtherFlag(fieldExtraConfig.getOptionSetOtherTag());
                    rs.setOtherMatchValue(fieldExtraConfig.getOptionSetOtherMatchValue());
                    return rs;
                }
            }
        } else {
            return null;
        }
        return null;
    }

    public QueryCollectionByOptionCnVO queryCollectionByOptionCode(QueryCollectionByOptionCodeDTO param) {
        String bizIdentityCode = param.getBizIdentityCode();
        List<CfgPageDO> pageDOList = pageService.getDOListByIdentityCode(bizIdentityCode);
        if (CollectionUtils.isEmpty(pageDOList)) {
            throw new ServiceException(500, "请先创建目标主体的页面");
        }
        List<Long> pageIdList = pageDOList.stream().map(CfgPageDO::getId).toList();

        List<String> modelCodeList = param.getModeCodeList();
        List<CfgModelDO> modelDOList = modelService.getDOByCodePageId(modelCodeList, pageIdList);
        if (CollectionUtils.isEmpty(modelDOList)) {
            throw new ServiceException(500, "根据模型code未查询到模型,modelCodeList:" + modelCodeList + ",pageIdList:" + pageIdList);
        }
        List<Long> modelIdList = modelDOList.stream().map(CfgModelDO::getId).toList();
        Map<Long, CfgModelDO> modelDOMap = modelDOList.stream().collect(Collectors.toMap(CfgModelDO::getId, i -> i));

        String fieldCode = param.getFieldCode();
        List<CfgFieldDO> fieldDOList = fieldService.getSelectDOByParam(fieldCode, pageIdList, modelIdList);
        if (CollectionUtils.isEmpty(fieldDOList)) {
            return null;
        }

        List<Long> modelIdList2 = fieldDOList.stream().map(CfgFieldDO::getModelId).toList();
        Set<String> modelCodeSet = modelIdList2.stream().map(modelDOMap::get).map(CfgModelDO::getCode).collect(Collectors.toSet());
        if (!CollectionUtils.isEmpty(modelCodeSet) && modelCodeSet.size() > 1) {
            throw new ServiceException(500, "根据查询条件匹配到多个模型的字段, fieldCode:" + fieldCode + ",modelCodeSet:" + modelCodeSet);
        }

        String optionCode = param.getOptionCode();
        CfgFieldDO fieldDO = fieldDOList.get(0);
        Byte datasourceType = fieldDO.getDatasourceType();
        String datasourceCode = fieldDO.getDatasourceCode();
        if (Objects.equals(datasourceType, DataSourceTypeEnum.OPTION_SET.getType())) {
            OptionSet optionSet = optionSetService.getRootSetByCode(datasourceCode);
            if (optionSet != null) {
                OptionSet value = optionSetService.getByParentCodeCode(optionSet.getId(), optionCode);
                if (value != null) {
                    QueryCollectionByOptionCnVO rs = new QueryCollectionByOptionCnVO();
                    rs.setCode(value.getCode());
                    rs.setName(value.getName());
                    String extraConfig  =   fieldDO.getExtraConfig();
                    if(org.apache.commons.lang3.StringUtils.isBlank(extraConfig)){
                        extraConfig = "{}";
                    }
                    rs.setExtraProperty(value.getExtraProperty());
                    FieldExtraConfig fieldExtraConfig =
                            JSONObject.parseObject(extraConfig,FieldExtraConfig.class);
                    rs.setOtherFlag(fieldExtraConfig.getOptionSetOtherTag());
                    rs.setOtherMatchValue(fieldExtraConfig.getOptionSetOtherMatchValue());
                    return  rs;
                }
            }
        } else {
            throw new ServiceException(500, "其它下拉框数据源待接入");
        }
        return null;
    }

    @Transactional
    public boolean updateFieldLinkedDisplayRule(FieldLinkedDisplayRuleDTO param) {
        Byte datasourceType = param.getDatasourceType();
        if (!Objects.equals(datasourceType, DataSourceTypeEnum.OPTION_SET.getType())) {
            throw new ServiceException(500, "目前只支持选项集");
        }

        Long fieldId = param.getSelectFieldId();
        String datasourceCode = param.getDatasourceCode();
        FieldLinkedDisplayRule oldRule = fieldLinkedDisplayRuleService.getByParam(fieldId, datasourceType, datasourceCode);

        List<LinkedDisplayRuleEntry> entryList = param.getEntryList();
        if (CollectionUtils.isEmpty(entryList)) {
            if (oldRule != null) {
                return fieldLinkedDisplayRuleService.deleteById(oldRule.getId());
            } else {
                throw new ServiceException(500, "当前下拉框字段未设置字段联动展示规则");
            }
        }

        for (LinkedDisplayRuleEntry entry : entryList) {
            if (entry.getFieldId() == null) {
                throw new ServiceException(500, "受影响字段的id不能为空");
            }

            Byte type = entry.getType();
            if (type != null && !LinkedDisplayRuleTypeEnum.getAllCode().contains(type)) {
                throw new ServiceException(500, "type值未定义,type:" + type);
            }

            String property = entry.getExtraProperty();
            if (type == null || LinkedDisplayRuleTypeEnum.EXTRA_PROPERTY.getCode() == type) {
                if (!StringUtils.hasText(property)) {
                    throw new ServiceException(500, "扩展属性名不能为空");
                }
            }

            String script = entry.getScript();
            if (type != null && LinkedDisplayRuleTypeEnum.SCRIPT.getCode() == type) {
                if (!StringUtils.hasText(script)) {
                    throw new ServiceException(500, "脚本内容不能为空");
                }
            }
        }

        FieldLinkedDisplayRule rule = new FieldLinkedDisplayRule();
        if (oldRule == null) {
            //新增
            CfgFieldDO fieldDO = fieldService.getDOById(fieldId);
            BeanUtils.copyProperties(param, rule);
            rule.setPageId(fieldDO.getPageId());
            rule.setAffectField(entryList);
            rule.setCreateTime(new Date());
            return fieldLinkedDisplayRuleService.add(rule);
        } else {
            //更新
            rule.setId(oldRule.getId());
            rule.setAffectField(entryList);
            rule.setUpdateTime(new Date());
            return fieldLinkedDisplayRuleService.updateById(rule);
        }
    }

    public List<FieldLinkedDisplayRuleVO> getFieldLinkedDisplayRule(Long fieldId, Byte datasourceType, String datasourceCode) {
        if (!Objects.equals(datasourceType, DataSourceTypeEnum.OPTION_SET.getType())) {
            throw new ServiceException(500, "目前只支持选项集");
        }
        OptionSet releasedOptionSet = optionSetService.getRootSetByCode(datasourceCode);
        if (releasedOptionSet == null) {
            throw new ServiceException(500, "目标选项集不存在");
        }

        List<String> extraPropertyList = releasedOptionSet.getExtraPropertyKeyList();
        LinkedList<FieldLinkedDisplayRuleVO> voList = extraPropertyList.stream().map(FieldLinkedDisplayRuleVO::new).collect(Collectors.toCollection(LinkedList::new));
        voList.addFirst(new FieldLinkedDisplayRuleVO("name"));
        voList.forEach(i -> i.setType(LinkedDisplayRuleTypeEnum.EXTRA_PROPERTY.getCode()));

        FieldLinkedDisplayRule rule = fieldLinkedDisplayRuleService.getByParam(fieldId, datasourceType, datasourceCode);
        if (rule == null || CollectionUtils.isEmpty(rule.getAffectField())) {
            return voList;
        }

        List<LinkedDisplayRuleEntry> allConfigedList = rule.getAffectField();

        List<Long> allFieldIdList = allConfigedList.stream().map(LinkedDisplayRuleEntry::getFieldId).collect(Collectors.toList());

        Map<Long, CfgFieldDO> fieldDOMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(allFieldIdList)) {
            List<CfgFieldDO> fieldDOList = fieldService.getDOListByIdList(allFieldIdList);
            fieldDOMap = fieldDOList.stream().collect(Collectors.toMap(CfgFieldDO::getId, i -> i));
        }


        //静态字段
        List<LinkedDisplayRuleEntry> staticConfigList = allConfigedList.stream().
                filter(t-> t.getType() == null || t.getType() == LinkedDisplayRuleTypeEnum.EXTRA_PROPERTY.getCode()).collect(Collectors.toList());

        Map<String,LinkedDisplayRuleEntry> staticConfigMap = staticConfigList.stream().
                collect(Collectors.toMap(LinkedDisplayRuleEntry::getExtraProperty,i->i,(k1,k2)->k1));

        for(FieldLinkedDisplayRuleVO vo : voList){
            LinkedDisplayRuleEntry config =    staticConfigMap.get(vo.getExtraProperty());
            if( config == null || config.getFieldId() == null){
                continue;
            }
            vo.setFieldId(config.getFieldId().toString());
            setFieldLinkedDisplayRuleVO(fieldDOMap, vo, config.getFieldId());

        }
        /**
         * 脚本字段
         */
        List<LinkedDisplayRuleEntry> scriptConfigList = allConfigedList.stream().
                filter(t-> t.getType() == null || t.getType() == LinkedDisplayRuleTypeEnum.SCRIPT.getCode()).collect(Collectors.toList());

        for(LinkedDisplayRuleEntry config : scriptConfigList){
            if( config == null || config.getFieldId() == null){
                continue;
            }

            FieldLinkedDisplayRuleVO vo = new FieldLinkedDisplayRuleVO(config.getType(),config.getScript());
            vo.setFieldId(config.getFieldId().toString());
            setFieldLinkedDisplayRuleVO(fieldDOMap, vo, config.getFieldId());
            voList.add(vo);

        }

        return voList;

       /* List<LinkedDisplayRuleEntry> entryList = rule.getAffectField();
        entryList = entryList.stream()
                .filter(entry -> entry.getFieldId() != null &&
                        (StringUtils.hasText(entry.getExtraProperty()) || StringUtils.hasText(entry.getScript())))
                .toList();


        entryList.forEach(entry -> {
            if (entry.getType() != null && entry.getType() == LinkedDisplayRuleTypeEnum.SCRIPT.getCode()) {
                voList.addLast(new FieldLinkedDisplayRuleVO(entry.getType(), entry.getScript()));
            }
        });

        Map<String, Long> entryMap = entryList.stream()
                .collect(Collectors.toMap(entry -> {
                    if (entry.getType() == LinkedDisplayRuleTypeEnum.SCRIPT.getCode()) {
                        return entry.getScript();
                    }
                    return entry.getExtraProperty();
                }, LinkedDisplayRuleEntry::getFieldId));

        Map<Long, CfgFieldDO> fieldDOMap = new HashMap<>();
        List<Long> fieldIdList = entryList.stream().map(LinkedDisplayRuleEntry::getFieldId).distinct().toList();
        if (!CollectionUtils.isEmpty(fieldIdList)) {
            List<CfgFieldDO> fieldDOList = fieldService.getDOListByIdList(fieldIdList);
            fieldDOMap = fieldDOList.stream().collect(Collectors.toMap(CfgFieldDO::getId, i -> i));
        }

        for (FieldLinkedDisplayRuleVO vo : voList) {
            String property = vo.getExtraProperty();
            if (StringUtils.hasText(property) || entryMap.containsKey(property)) {
                Long fId = entryMap.get(property);
                setFieldLinkedDisplayRuleVO(fieldDOMap, vo, fId);
                continue;
            }

            String script = vo.getScript();
            if (StringUtils.hasText(script) || entryMap.containsKey(script)) {
                Long fId = entryMap.get(script);
                setFieldLinkedDisplayRuleVO(fieldDOMap, vo, fId);
            }
        }

        return voList;*/
    }

    private void setFieldLinkedDisplayRuleVO(Map<Long, CfgFieldDO> fieldDOMap, FieldLinkedDisplayRuleVO vo, Long fId) {
        if (fId != null && fieldDOMap.containsKey(fId)) {
            CfgFieldDO fieldDO = fieldDOMap.get(fId);
            vo.setFieldId(fieldDO.getId().toString());
            vo.setBizName(fieldDO.getBizName());
            vo.setBizCode(fieldDO.getBizCode());
            vo.setDataBinding(fieldDO.getDataBinding());
        }
    }

    public void synchronizeOptionSetToRedis(String optionSetCode, List<OptionSet> optionSetList) {
        deleteOptionSetFromRedis(optionSetCode);
        Map<String, String> map = optionSetList.stream().collect(Collectors.toMap(OptionSet::getCode, JSON::toJSONString));
        redisTemplate.opsForHash().putAll(Constant.OPTION_SET_KEY + optionSetCode, map);
    }

    public void deleteOptionSetFromRedis(String optionSetCode) {
        String key = Constant.OPTION_SET_KEY + optionSetCode;
        redisTemplate.delete(key);
    }

    public boolean addValue(InsertValue param) {
        Long optionSetId = param.getOptionSetId();
        OptionSet optionSet = optionSetService.getRootSetById(optionSetId);
        if (optionSet == null) {
            throw new ServiceException(500, "目标选项集不存在");
        }
        List<OptionSet> valueList = optionSetService.getValueByParentIdAndCode(optionSetId, param.getValueCode());
        if (!CollectionUtils.isEmpty(valueList)) {
            throw new ServiceException(500, "当前选项集内已经存在相同code的选项值");
        }

        OptionSet value = new OptionSet();
        value.setNodeType(OptionSetNodeEnum.LEAF_NODE.getCode());
        value.setParentId(optionSetId);
        value.setName(param.getValueName());
        value.setCode(param.getValueCode());
        value.setExtraProperty(mergeNullProperty(param.getExtraProperty(), optionSet.getExtraPropertyKeyList()));
        value.setStatus(param.getStatus());
        value.setCreateTime(new Date());
        return optionSetService.add(value);
    }

    public boolean deleteValue(Long valueId) {
        OptionSet optionSet = optionSetService.getById(valueId);
        if (optionSet == null || optionSet.getNodeType() != OptionSetNodeEnum.LEAF_NODE.getCode()) {
            throw new ServiceException(500, "目标选项值不存在,id:" + valueId);
        }
        return optionSetService.deleteById(valueId);
    }

    public boolean updateValue(UpdateValue param) {
        Long valueId = param.getValueId();
        OptionSet oldValue = optionSetService.getById(valueId);
        if (oldValue == null) {
            throw new ServiceException(500, "目标选项值不存在,id:" + valueId);
        }

        OptionSet optionSet = optionSetService.getRootSetById(oldValue.getParentId());
        if (optionSet == null) {
            throw new ServiceException(500, "目标选项集不存在,id:" + oldValue.getParentId());
        }

        List<String> extraKeyList = optionSet.getExtraPropertyKeyList();
        String extraProperty = param.getExtraProperty();
        String newExtraProperty = null;
        if (CollectionUtils.isEmpty(extraKeyList) || !StringUtils.hasText(extraProperty)) {
            newExtraProperty = "{}";
        } else {
            JSONObject jsonObj = JSON.parseObject(extraProperty);
            Set<String> keySet = new HashSet<>(jsonObj.keySet());
            for (String key : keySet) {
                if (!extraKeyList.contains(key)) {
                    jsonObj.remove(key);
                }
            }
            newExtraProperty = jsonObj.toJSONString();
        }

        OptionSet newValue = new OptionSet();
        newValue.setId(valueId);
        newValue.setName(param.getValueName());
        newValue.setExtraProperty(newExtraProperty);
        newValue.setStatus(param.getStatus());
        return optionSetService.updateById(newValue);
    }

    public boolean updateOptionSet(UpdateOptionSetDTO param) {
        Long optionSetId = param.getOptionSetId();
        OptionSet optionSet = optionSetService.getRootSetById(optionSetId);
        if (optionSet == null) {
            throw new ServiceException(500, "目标选项集不存在");
        }

        OptionSet value = new OptionSet();
        value.setId(optionSetId);
        value.setSetDesc(param.getSetDesc());
        value.setExtraPropertyKey(param.getExtraPropertyKey());
        return optionSetService.updateById(value);
    }

    public String mergeNullProperty(String extraProperty, List<String> extraPropertyKey){
        Map<String, String> extraPropertyValue = JSON.parseObject(extraProperty, new TypeReference<>() {});

        if (extraPropertyValue == null) {
            extraPropertyValue = new HashMap<>();
        }

        for (String key : extraPropertyKey) {
            if (!extraPropertyValue.containsKey(key)) {
                extraPropertyValue.put(key, null);
            }
        }

        return JSON.toJSONString(extraPropertyValue);
    }
}
