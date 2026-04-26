package com.bone.lowcode.infra.application.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bone.core.exception.ServiceException;
import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.convert.FieldConvert;
import com.bone.lowcode.infra.application.dto.field.BatchUpdateFieldDefaultDTO;
import com.bone.lowcode.infra.application.dto.field.BatchUpdateFieldLocationDTO;
import com.bone.lowcode.infra.application.dto.field.CreateExclusiveFieldDTO;
import com.bone.lowcode.infra.application.dto.field.UpdateFieldDTO;
import com.bone.lowcode.infra.application.dto.optionSet.SetFieldDataSourceDTO;
import com.bone.lowcode.infra.application.vo.field.*;
import com.bone.lowcode.infra.application.vo.optionSet.FieldDataSourceVO;
import com.bone.lowcode.infra.application.vo.table.FieldSimpleInfo;
import com.bone.lowcode.infra.domain.model.FieldSharedProperties;
import com.bone.lowcode.infra.domain.model.LinkedDisplayRuleEntry;
import com.bone.lowcode.infra.domain.service.*;
import com.bone.lowcode.infra.domain.util.MetaDataUtil;
import com.bone.lowcode.infra.domain.util.MultiTreeUtil;
import com.bone.lowcode.infra.domain.util.SnowflakeIdUtil;
import com.bone.lowcode.infra.domain.valueobject.*;
import com.bone.lowcode.infra.infrastructure.feign.bean.EnumEntry;
import com.bone.lowcode.infra.infrastructure.feign.util.TpaSaasBusinessFeignUtil;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.*;
import com.bone.lowcode.infra.infrastructure.persistence.dto.FieldExtraConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.text.ParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class FieldApplicationService {

    @Autowired
    private FieldService fieldService;

    @Autowired
    private ModelService modelService;

    @Autowired
    private FieldsetService fieldsetService;

    @Autowired
    private PageService pageService;

    @Autowired
    private TableService tableService;

    @Autowired
    private OptionSetService optionSetService;

    @Autowired
    private FieldLinkageRuleService fieldLinkageRuleService;

    @Autowired
    private FieldTableRuleService fieldTableRuleService;

    @Autowired
    private SubmitRuleService submitRuleService;

    @Autowired
    private FieldLinkedDisplayRuleService fieldLinkedDisplayRuleService;

    @Autowired
    private TableDataGroupAggregateService tableDataGroupAggregateService;

    @Autowired
    private TableDataRowEditService tableDataRowEditService;

    @Autowired
    private TableDataRowVerifyService tableDataRowVerifyService;

    @Autowired
    private ProcessPageService processPageService;

    @Autowired
    private ProcessDetailPageService detailPageService;

    @Autowired
    private MetaDataUtil metaDataUtil;

    @Autowired
    private TpaSaasBusinessFeignUtil tpaSaasBusinessFeignUtil;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Transactional(rollbackFor = Exception.class)
    public boolean updateField(UpdateFieldDTO dto) throws ParseException {
        Long fieldId = dto.getId();
        CfgFieldDO currentField = fieldService.getDOById(fieldId);
        if (currentField.getFieldType() == FieldTypeEnum.BIZ.getCode()) {
            if (StringUtils.hasText(dto.getShowName()) || dto.getAlignment() != null || StringUtils.hasText(dto.getPrompt()) ||
                    StringUtils.hasText(dto.getPlaceholder()) || dto.getLimitedLength() != null || dto.getInputStatus() != null ||
                    StringUtils.hasText(dto.getDefaultValue()) || dto.getValueType() != null || dto.getDataFormat() != null ||
                    StringUtils.hasText(dto.getMin()) || StringUtils.hasText(dto.getMax()) || dto.getDecimalDigit() != null ||
                    StringUtils.hasText(dto.getMultiples()) || dto.getSelectType() != null || dto.getFilterType() != null ||
                    dto.getSelectDatasource() != null || StringUtils.hasText(dto.getPlaceholderTwo()) ||
                    StringUtils.hasText(dto.getPlaceholderThree()) || dto.getSelectLevel() != null || dto.getDateFormatType() != null ||
                    StringUtils.hasText(dto.getEarliestDatetime()) || dto.getEarliestDatetimeType() != null ||
                    StringUtils.hasText(dto.getLatestDatetime()) || dto.getLatestDatetimeType() != null || dto.getDateApplyScene() != null) {
                FieldSharedProperties properties = new FieldSharedProperties();
                BeanUtils.copyProperties(dto, properties);
                properties.setTitle(dto.getShowName());
                properties.setCharacterLimit(dto.getLimitedLength());
                if (dto.getSelectDatasource() != null) {
                    properties.setSelectDatasource(JSON.toJSONString(dto.getSelectDatasource()));
                }
                if (StringUtils.hasText(dto.getEarliestDatetime())) {
                    Date date = DateFormatEnum.parseStrToDate(dto.getEarliestDatetime());
                    if (date == null) {
                        throw new ServiceException(500, "日期参数转换失败,时间:" + dto.getEarliestDatetime());
                    }
                    properties.setEarliestDatetime(date);
                }
                if (StringUtils.hasText(dto.getLatestDatetime())) {
                    Date date = DateFormatEnum.parseStrToDate(dto.getLatestDatetime());
                    if (date == null) {
                        throw new ServiceException(500, "日期参数转换失败,时间:" + dto.getLatestDatetime());
                    }
                    properties.setLatestDatetime(date);
                }
                updateSharedProperties(fieldId, properties);
            }
        }

        CfgFieldDO fieldDO = FieldConvert.fieldDtoToDo(dto);
        return fieldService.updateAcceptNull(fieldDO);
    }

    public void updateSharedProperties(Long fieldId, FieldSharedProperties properties) {
        CfgFieldDO currentField = fieldService.getDOById(fieldId);
        if (currentField.getFieldType() != FieldTypeEnum.BIZ.getCode()) {
            return;
        }

        Long pageId = currentField.getPageId();
        CfgPageDO pageDO = pageService.getDoById(pageId);
        String identityCode = pageDO.getBizIdentityCode();
        if (!StringUtils.hasText(identityCode)) {
            throw new ServiceException(500, "异常情况,专属页面的主体code缺失,页面id:" + pageId);
        }

        List<CfgPageDO> pageDOList = pageService.getDOListByIdentityCode(identityCode);
        List<Long> pageIdList = pageDOList.stream().map(CfgPageDO::getId).toList();
        String bizCode = currentField.getBizCode();
        List<CfgFieldDO> fieldDOList = fieldService.getExtFieldList(pageIdList, bizCode);
        fieldDOList = fieldDOList.stream().filter(i -> !Objects.equals(i.getId(), fieldId)).toList();

        List<CfgFieldDO> list = fieldDOList.stream().map(i -> {
            CfgFieldDO fieldDO = new CfgFieldDO();
            BeanUtils.copyProperties(properties, fieldDO);
            fieldDO.setId(i.getId());
            return fieldDO;
        }).toList();
        if (!CollectionUtils.isEmpty(list)) {
            fieldService.batchUpdateById(list);
        }
    }

    public List<FieldDefaultByBlockIdVO> listDefaultByFieldSetId(Long fieldSetId) {
        CfgFieldsetDO fieldsetDO = fieldsetService.getDOById(fieldSetId);
        Long modelId = fieldsetDO.getModelId();
        List<CfgFieldDO> fieldDOList = fieldService.getDOListByModelId(modelId);
        CfgModelDO modelDO = modelService.getDOById(modelId);

        List<FieldDefaultByBlockIdVO> res = new ArrayList<>();
        for (CfgFieldDO fieldDO : fieldDOList) {
            FieldDefaultByBlockIdVO vo = FieldConvert.fieldDOToFieldDefaultVO(fieldDO, modelDO.getName());
            res.add(vo);
        }
        return res;
    }

    public List<FieldDefaultByModelIdVO> getFieldDefaultByModelId(Long modelId) {
        List<CfgFieldDO> fieldDOList = fieldService.getDOListByModelId(modelId);

        CfgModelDO modelDO = modelService.getDOById(modelId);
        List<FieldDefaultByModelIdVO> voList = new ArrayList<>();
        for (CfgFieldDO fieldDO : fieldDOList) {
            FieldDefaultByModelIdVO vo = new FieldDefaultByModelIdVO();
            vo.setModelName(modelDO.getName());
            vo.setFieldName(fieldDO.getBizName());
            vo.setFieldCode(fieldDO.getBizCode());
            vo.setDisplayed(fieldDO.getDisplayed());
            vo.setRequired(fieldDO.getRequired());
            vo.setId(fieldDO.getId().toString());
            vo.setDataBinding(fieldDO.getDataBinding());
            vo.setComponentType(fieldDO.getComponentType());
            voList.add(vo);
        }

        return voList;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean batchUpdateDefault(List<BatchUpdateFieldDefaultDTO> modifyList) {
        if (CollectionUtils.isEmpty(modifyList)) return true;

        for (BatchUpdateFieldDefaultDTO dto : modifyList) {
            CfgFieldDO fieldDO = fieldService.getDOById(dto.getFieldId());
            if (fieldDO.getFieldType() == FieldTypeEnum.BIZ.getCode() &&
                    (StringUtils.hasText(dto.getDataBinding()) || StringUtils.hasText(dto.getComponentType()))) {
                FieldSharedProperties properties = new FieldSharedProperties();//共享字段
                properties.setDataBinding(dto.getDataBinding());
                properties.setComponentType(dto.getComponentType());
                updateSharedProperties(fieldDO.getId(), properties);
            }
        }

        List<CfgFieldDO> fieldDOList = FieldConvert.fieldDefaultDtoListToDOList(modifyList);
        boolean flag = fieldService.batchUpdateById(fieldDOList);
        return flag;
    }

    public List<FieldLocationVO> listLocationByFieldSetId(Long fieldSetId) {
        CfgFieldsetDO fieldsetDO = fieldsetService.getDOById(fieldSetId);
        Long modelId = fieldsetDO.getModelId();
        List<CfgFieldDO> fieldDOList = fieldService.getDOListByModelId(modelId);

        CfgModelDO modelDO = modelService.getDOById(modelId);
        List<FieldLocationVO> voList = new ArrayList<>();
        for (CfgFieldDO fieldDO : fieldDOList) {
            FieldLocationVO vo = FieldConvert.fieldDOToFieldLocationVO(fieldDO, modelDO);
            voList.add(vo);
        }
        voList.sort((o1, o2) -> {
            int order = o2.getDisplayed() - o1.getDisplayed();
            if (order != 0) return order;
            int rowOrder = o1.getRowNumber() - o2.getRowNumber();
            if (rowOrder != 0) return rowOrder;
            return o1.getColumnNumber() - o2.getColumnNumber();
        });
        return voList;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean batchUpdateLocation(List<BatchUpdateFieldLocationDTO> modifyList) {
        if (CollectionUtils.isEmpty(modifyList)) return true;

        for (BatchUpdateFieldLocationDTO dto : modifyList) {
            if (dto.getWidth() == null) continue;
            CfgFieldDO fieldDO = fieldService.getDOById(dto.getFieldId());
            if (fieldDO.getFieldType() != FieldTypeEnum.BIZ.getCode()) continue;
            FieldSharedProperties properties = new FieldSharedProperties();
            properties.setWidth(dto.getWidth());
            updateSharedProperties(fieldDO.getId(), properties);
        }

        List<CfgFieldDO> fieldDOList = FieldConvert.fieldLocationDtoListToDOList(modifyList);
        boolean flag = fieldService.batchUpdateById(fieldDOList);
        return flag;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean createExclusiveField(CreateExclusiveFieldDTO dto) {
        if (!StringUtils.hasText(dto.getBizCode()) || !dto.getBizCode().startsWith("EX_")) {
            throw new ServiceException(500, "专属字段code不是以EX_开头");
        }

        String bizIdentityCode = dto.getBizIdentityCode();
        List<CfgPageDO> pageDOList = pageService.getDOListByIdentityCode(bizIdentityCode);
        for (CfgPageDO pageDO : pageDOList) {
            List<CfgFieldDO> fieldDOList = fieldService.getDOByPageId(pageDO.getId());
            for (CfgFieldDO fieldDO : fieldDOList) {
                if (dto.getBizName().equals(fieldDO.getBizName())) {
                    String desc = FieldTypeEnum.getDescByCode(fieldDO.getFieldType());
                    throw new ServiceException(500, "创建失败,当前主体下字段名称需保存唯一," + pageDO.getName() + "页面已存在相同名称(" + dto.getBizName() + ")的" + desc);
                }
                if (dto.getBizCode().equals(fieldDO.getBizCode())) {
                    String desc = FieldTypeEnum.getDescByCode(fieldDO.getFieldType());
                    throw new ServiceException(500, "创建失败,当前主体下字段code需保存唯一," + pageDO.getName() + "页面已存在相同code(" + dto.getBizCode() + ")的" + desc);
                }
            }
        }

        CfgModelDO modelDO = modelService.getDOById(dto.getModelId());
        List<Long> pageIdList = pageDOList.stream().map(CfgPageDO::getId).toList();
        List<CfgModelDO> modelDOList = modelService.getDOListByCodePageId(modelDO.getCode(), pageIdList);
        Map<Long, CfgModelDO> modelMap = modelDOList.stream().collect(Collectors.toMap(CfgModelDO::getPageId, i -> i));

        Boolean flag1 = metaDataUtil.addBizField(dto, modelDO.getTableName(), modelDO.getCode());
        if (!flag1) {
            throw new ServiceException(500, "同步专属字段到元数据失败");
        }

        List<CfgFieldDO> exclusiveFieldList = new ArrayList<>();
        for (CfgPageDO pageDO : pageDOList) {
            Long pageId = pageDO.getId();
            CfgModelDO currentModel = modelMap.get(pageId);
            if (currentModel == null) continue;

            List<CfgFieldDO> fieldDOList = fieldService.getDOListByModelId(currentModel.getId());
            fieldDOList.sort(Comparator.comparingInt(CfgFieldDO::getRowNo));
            int rowNumber = fieldDOList.get(fieldDOList.size() - 1).getRowNo() + 1;

            CfgFieldDO fieldDO = setFieldDO(dto, modelDO);
            fieldDO.setId(SnowflakeIdUtil.getId());
            fieldDO.setPageId(pageId);
            fieldDO.setModelId(currentModel.getId());
            if (Objects.equals(currentModel.getOwnerType(), ModelOwnerEnum.TABLE.getCode())) {
                fieldDO.setWidth(100);
            }
            fieldDO.setRowNo(rowNumber);
            fieldDO.setColumnNo(1);
            exclusiveFieldList.add(fieldDO);
        }

        if (!CollectionUtils.isEmpty(exclusiveFieldList)) {
            boolean flag = fieldService.batchSave(exclusiveFieldList);
            log.info("创建专属字段结果:{}, 新增的字段信息:{}", flag, JSON.toJSONString(exclusiveFieldList));
            return flag;
        } else {
            return false;
        }
    }

    private CfgFieldDO setFieldDO(CreateExclusiveFieldDTO dto, CfgModelDO modelDO) {
        CfgFieldDO fieldDO = new CfgFieldDO();
        fieldDO.setBizCode(dto.getBizCode());
        fieldDO.setBizName(dto.getBizName());
        fieldDO.setTitle(dto.getTitle());
        if (modelDO.getOwnerType() == ModelOwnerEnum.TABLE.getCode()) {
            fieldDO.setDataBinding("$.main.extraProperties." + fieldDO.getBizCode());
        } else if (StringUtils.hasText(modelDO.getDataBindingPrefix())) {
            fieldDO.setDataBinding(modelDO.getDataBindingPrefix() + "extraProperties." + fieldDO.getBizCode());
        }
        fieldDO.setFieldType(FieldTypeEnum.BIZ.getCode());
        fieldDO.setComponentType(dto.getComponentType());
        fieldDO.setAlignment(dto.getAlignment());
        fieldDO.setDisplayed(DisplayEnum.DISPLAY.getCode());
        fieldDO.setCreateTime(new Date());
        return fieldDO;
    }

    public GetByIdVo getById(Long id) {
        CfgFieldDO fieldDO = fieldService.getDOById(id);
        GetByIdVo vo = FieldConvert.DOToGetByIdVO(fieldDO);
        return vo;
    }

    public List<GetSameTypeFieldListVO> getSameTypeFieldList(Long fieldId) {
        CfgFieldDO fieldDO = fieldService.getDOById(fieldId);
        Long pageId = fieldDO.getPageId();

        List<CfgModelDO> modelDOList = modelService.getDOListByPageId(pageId);
        modelDOList = modelDOList.stream().filter(i -> !"赔案信息".equals(i.getName()) && i.getOwnerType() != ModelOwnerEnum.TABLE.getCode()).toList();
        List<Long> allModelIdList = modelDOList.stream().map(CfgModelDO::getId).toList();

        List<GetSameTypeFieldListVO> re = new ArrayList<>();
        String componentType = fieldDO.getComponentType();
        for (Long modelId : allModelIdList) {
            CfgModelDO modelDO = modelService.getDOById(modelId);
            List<CfgFieldDO> fieldDOList = fieldService.getDOListByModelIdAndComponentType(modelId, componentType);
            for (CfgFieldDO cfgFieldDO : fieldDOList) {
                if (fieldId.equals(cfgFieldDO.getId())) continue;
                GetSameTypeFieldListVO vo = FieldConvert.DOToVO(cfgFieldDO, modelDO.getName());
                re.add(vo);
            }
        }
        return re;
    }

    public List<FieldVO> getByPageCode(String pageCode, String bizIdentityCode) {
        CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, bizIdentityCode);
        if (pageDO == null) {
            throw new ServiceException(500, "目标页面不存在,pageCode:" + pageCode + ", bizIdentityCode:" + bizIdentityCode);
        }

        Long pageId = pageDO.getId();
        List<CfgModelDO> modelDOList = modelService.getDOListByPageId(pageId);
        modelDOList = modelDOList.stream().filter(i -> !"赔案信息".equals(i.getName()) && i.getOwnerType() != ModelOwnerEnum.TABLE.getCode()).toList();
        List<Long> allModelIdList = modelDOList.stream().map(CfgModelDO::getId).toList();
        if (CollectionUtils.isEmpty(allModelIdList)) {
            return new ArrayList<>();
        }

        List<CfgFieldDO> fieldDOList = fieldService.getDOListByModelIds(allModelIdList);
        return fieldDOList.stream().map(fieldDO -> {
            FieldVO vo = new FieldVO();
            BeanUtils.copyProperties(fieldDO, vo);
            vo.setId(fieldDO.getId().toString());
            return vo;
        }).toList();
    }

    public List<FieldVO> getFieldByTableId(Long tableId) {
        CfgTableDO tableDO = tableService.getDOById(tableId);
        if (tableDO == null) {
            throw new ServiceException(500, "目标表格不存在,tableId:" + tableId);
        }

        List<Long> modelIds = tableDO.getModelIds();
        if (CollectionUtils.isEmpty(modelIds)) {
            return new ArrayList<>();
        }
        List<CfgFieldDO> fieldDOList = fieldService.getDOListByModelIds(modelIds);
        return fieldDOList.stream().map(fieldDO -> {
            FieldVO vo = new FieldVO();
            BeanUtils.copyProperties(fieldDO, vo);
            vo.setId(fieldDO.getId().toString());
            return vo;
        }).toList();
    }

    public List<GetSameTypeFieldListVO> getSamePageFieldList(Long fieldId) {
        CfgFieldDO fieldDO = fieldService.getDOById(fieldId);
        Long pageId = fieldDO.getPageId();

        List<Long> allModelIdList = getAllModelIdListByPageId(pageId);
        List<GetSameTypeFieldListVO> re = new ArrayList<>();
        for (Long modelId : allModelIdList) {
            CfgModelDO modelDO = modelService.getDOById(modelId);
            if (modelDO.getOwnerType() == ModelOwnerEnum.TABLE.getCode()) {
                //不取表格的字段
                continue;
            }
            List<CfgFieldDO> fieldDOList = fieldService.getDOListByModelId(modelId);
            for (CfgFieldDO cfgFieldDO : fieldDOList) {
                if (fieldId.equals(cfgFieldDO.getId())) continue;
                GetSameTypeFieldListVO vo = FieldConvert.DOToVO(cfgFieldDO, modelDO.getName());
                re.add(vo);
            }
        }
        return re;
    }

    private List<Long> getAllModelIdListByPageId(Long pageId) {
        List<CfgModelDO> modelDOList = modelService.getDOListByPageId(pageId);
        modelDOList = modelDOList.stream().filter(i -> !"赔案信息".equals(i.getName())).toList();
        List<Long> list = modelDOList.stream().map(CfgModelDO::getId).toList();
        return list;
    }

    public List<GetByPageAndComponentTypeVO> getByPageAndComponentType(List<String> componentTypeList, String pageCode, String bizIdentityCode) {
        CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, bizIdentityCode);
        List<Long> modelIdList = getAllModelIdListByPageId(pageDO.getId());
        List<GetByPageAndComponentTypeVO> voList = new ArrayList<>();
        for (Long modelId : modelIdList) {
            List<CfgFieldDO> fieldDOList = fieldService.getDOListByModelIdAndComponentTypeList(modelId, componentTypeList);
            for (CfgFieldDO fieldDO : fieldDOList) {
                GetByPageAndComponentTypeVO vo = FieldConvert.DOToGetByPageAndComponentTypeVO(fieldDO);
                voList.add(vo);
            }
        }
        voList.sort((o1, o2) -> {
            int order1 = componentTypeList.indexOf(o1.getComponentType());
            int order2 = componentTypeList.indexOf(o2.getComponentType());
            return order1 - order2;
        });
        return voList;
    }

    public GetByModelIdVO getByModelId(Long modelId) {
        CfgModelDO modelDO = modelService.getDOById(modelId);
        if (modelDO == null) {
            throw new ServiceException(500, "目标模型不存在");
        }

        List<CfgFieldDO> fieldDOList = fieldService.getDOListByModelId(modelDO.getId());
        List<FieldSimpleInfo> fieldList = fieldDOList.stream()
                .map(i -> {
                    FieldSimpleInfo info = new FieldSimpleInfo(i.getId().toString(), i.getBizCode(), i.getBizName(), i.getDataBinding());
                    info.setComponentType(i.getComponentType());
                    return info;
                }).toList();

        GetByModelIdVO vo = new GetByModelIdVO();
        vo.setModelId(modelId.toString());
        vo.setModelName(modelDO.getName());
        vo.setFieldList(fieldList);
        return vo;
    }

    public List<GetByModelIdVO> getByTableId(Long tableId) {
        CfgTableDO tableDO = tableService.getDOById(tableId);
        List<Long> modelIds = tableDO.getModelIds();

        List<GetByModelIdVO> list = new ArrayList<>();
        for (Long modelId : modelIds) {
            GetByModelIdVO vo = getByModelId(modelId);
            list.add(vo);
        }
        return list;
    }

    public List<FieldDataSourceVO> getFieldDataSource(String bizIdentityCode) {
        List<CfgPageDO> pageDOList = pageService.getDOListByIdentityCode(bizIdentityCode);
        if (CollectionUtils.isEmpty(pageDOList)) {
            throw new ServiceException(500, "请先创建目标主体的页面");
        }

        List<FieldDataSourceVO> res = new ArrayList<>();
        List<CfgFieldDO> baseFieldDOList = getIdentitySelectField(null);

        if (StringUtils.hasText(bizIdentityCode)) {
            getExclusiveMapping(res, baseFieldDOList, bizIdentityCode);
        } else {
            getBaseMapping(res, baseFieldDOList);
        }

        Set<String> optionSetCodeSet = new HashSet<>();
        for (FieldDataSourceVO vo : res) {
            if (Objects.equals(vo.getBaseSourceType(), DataSourceTypeEnum.OPTION_SET.getType())) {
                if (StringUtils.hasText(vo.getBaseSourceCode())) {
                    optionSetCodeSet.add(vo.getBaseSourceCode());
                }
            }
            if (Objects.equals(vo.getExclusiveSourceType(), DataSourceTypeEnum.OPTION_SET.getType())) {
                if (StringUtils.hasText(vo.getExclusiveSourceCode())) {
                    optionSetCodeSet.add(vo.getExclusiveSourceCode());
                }
            }
        }
        Map<String, String> optionSetCodeNameMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(optionSetCodeSet)) {
            List<OptionSet> optionSetList = optionSetService.getValueByCodeList(optionSetCodeSet);
            optionSetCodeNameMap = optionSetList.stream().collect(Collectors.toMap(OptionSet::getCode, OptionSet::getName));
        }

        List<EnumEntry> enumList = tpaSaasBusinessFeignUtil.getEnumList();
        Map<String, String> enumMap = enumList.stream().collect(Collectors.toMap(EnumEntry::getCode, EnumEntry::getName));

        for (FieldDataSourceVO vo : res) {
            if (Objects.equals(vo.getBaseSourceType(), DataSourceTypeEnum.OPTION_SET.getType())) {
                if (StringUtils.hasText(vo.getBaseSourceCode()) && !CollectionUtils.isEmpty(optionSetCodeNameMap)) {
                    vo.setBaseSourceName(optionSetCodeNameMap.get(vo.getBaseSourceCode()));
                }
            } else if (Objects.equals(vo.getBaseSourceType(), DataSourceTypeEnum.ENUM_DATA.getType())) {
                if (StringUtils.hasText(vo.getBaseSourceCode())) {
                    vo.setBaseSourceName(enumMap.get(vo.getBaseSourceCode()));
                }
            } else if (Objects.equals(vo.getBaseSourceType(), DataSourceTypeEnum.MASTER_DATA.getType())) {
                if ("province_code".equals(vo.getBaseSourceCode())) {
                    vo.setBaseSourceName("省市区");
                } else if (MultiTreeUtil.containRootCode(vo.getBaseSourceCode())) {
                    vo.setBaseSourceName(MultiTreeUtil.getRootByCode(vo.getBaseSourceCode()).getName());
                }
            }

            if (Objects.equals(vo.getExclusiveSourceType(), DataSourceTypeEnum.OPTION_SET.getType())) {
                if (StringUtils.hasText(vo.getExclusiveSourceCode()) && !CollectionUtils.isEmpty(optionSetCodeNameMap)) {
                    vo.setExclusiveSourceName(optionSetCodeNameMap.get(vo.getExclusiveSourceCode()));
                }
            } else if (Objects.equals(vo.getExclusiveSourceType(), DataSourceTypeEnum.ENUM_DATA.getType())) {
                if (StringUtils.hasText(vo.getExclusiveSourceCode())) {
                    vo.setExclusiveSourceName(enumMap.get(vo.getExclusiveSourceCode()));
                }
            } else if (Objects.equals(vo.getExclusiveSourceType(), DataSourceTypeEnum.MASTER_DATA.getType())) {
                if ("province_code".equals(vo.getExclusiveSourceCode())) {
                    vo.setExclusiveSourceName("省市区");
                } else if (MultiTreeUtil.containRootCode(vo.getExclusiveSourceCode())) {
                    vo.setExclusiveSourceName(MultiTreeUtil.getRootByCode(vo.getExclusiveSourceCode()).getName());
                }
            }
        }
        return res;
    }

    private void getExclusiveMapping(List<FieldDataSourceVO> res, List<CfgFieldDO> baseFieldDOList, String bizIdentityCode) {
        Map<String, CfgFieldDO> baseMapping = baseFieldDOList.stream()
                .collect(Collectors.toMap(CfgFieldDO::getBizCode, i -> i, (f1, f2) -> f1));

        List<CfgFieldDO> fieldDOList = getIdentitySelectField(bizIdentityCode);
        HashMap<String, Set<String>> map = new HashMap<>();

        for (CfgFieldDO fieldDO : fieldDOList) {
            String bizCode = fieldDO.getBizCode();
            String datasourceCode = fieldDO.getDatasourceCode();
            if (map.containsKey(bizCode)) {
                Set<String> sourceCodeSet = map.get(bizCode);
                if (sourceCodeSet.contains(datasourceCode)) {
                    continue;
                } else {
                    sourceCodeSet.add(datasourceCode);
                }
            } else {
                HashSet<String> sourceCodeSet = new HashSet<>();
                sourceCodeSet.add(datasourceCode);
                map.put(bizCode, sourceCodeSet);
            }

            String extraConfigStr = fieldDO.getExtraConfig();
            if (org.apache.commons.lang3.StringUtils.isBlank(extraConfigStr)) {
                extraConfigStr = "{}";
            }
            FieldExtraConfig extraConfig = JSONObject.parseObject(extraConfigStr, FieldExtraConfig.class);

            FieldDataSourceVO vo = new FieldDataSourceVO();
            res.add(vo);
            vo.setFieldCode(bizCode);
            vo.setExtraConfig(extraConfig);
            vo.setFieldName(fieldDO.getBizName());
            vo.setComponentType(fieldDO.getComponentType());
            if (baseMapping.containsKey(bizCode)) {
                CfgFieldDO field = baseMapping.get(bizCode);
                vo.setBaseSourceType(field.getDatasourceType());
                vo.setBaseSourceCode(field.getDatasourceCode());
            }
            vo.setExclusiveSourceType(fieldDO.getDatasourceType());
            vo.setExclusiveSourceCode(datasourceCode);
        }
    }

    private void getBaseMapping(List<FieldDataSourceVO> res, List<CfgFieldDO> baseFieldDOList) {
        HashMap<String, Set<String>> map = new HashMap<>();
        for (CfgFieldDO fieldDO : baseFieldDOList) {
            String bizCode = fieldDO.getBizCode();
            String datasourceCode = fieldDO.getDatasourceCode();
            if (map.containsKey(bizCode)) {
                Set<String> sourceCodeSet = map.get(bizCode);
                if (sourceCodeSet.contains(datasourceCode)) {
                    continue;
                } else {
                    sourceCodeSet.add(datasourceCode);
                }
            } else {
                HashSet<String> sourceCodeSet = new HashSet<>();
                sourceCodeSet.add(datasourceCode);
                map.put(bizCode, sourceCodeSet);
            }
            String extraConfigStr = fieldDO.getExtraConfig();
            if (org.apache.commons.lang3.StringUtils.isBlank(extraConfigStr)) {
                extraConfigStr = "{}";
            }
            FieldExtraConfig extraConfig = JSONObject.parseObject(extraConfigStr, FieldExtraConfig.class);
            FieldDataSourceVO vo = new FieldDataSourceVO();
            vo.setFieldCode(bizCode);
            vo.setExtraConfig(extraConfig);
            vo.setFieldName(fieldDO.getBizName());
            vo.setComponentType(fieldDO.getComponentType());
            vo.setBaseSourceType(fieldDO.getDatasourceType());
            vo.setBaseSourceCode(datasourceCode);
            res.add(vo);
        }
    }

    public List<CfgFieldDO> getIdentitySelectField(String bizIdentityCode) {
        List<CfgFieldDO> list = new ArrayList<>();
        List<CfgPageDO> pageDOList = pageService.getDOListByIdentityCode(bizIdentityCode);
        for (CfgPageDO pageDO : pageDOList) {
            List<CfgFieldDO> fieldDOList = fieldService.getPageSelectDO(pageDO.getId());
            list.addAll(fieldDOList);
        }
        return list;
    }

    public boolean setFieldDataSource(SetFieldDataSourceDTO param) {
        String bizIdentityCode = param.getBizIdentityCode();
        List<CfgPageDO> pageDOList = pageService.getDOListByIdentityCode(bizIdentityCode);
        if (CollectionUtils.isEmpty(pageDOList)) {
            throw new ServiceException(500, "请先创建目标主体的页面");
        }

        String fieldCode = param.getFieldCode();
        List<CfgFieldDO> oldFieldDOList = new ArrayList<>();
        for (CfgPageDO pageDO : pageDOList) {
            List<CfgFieldDO> fieldDOList = fieldService.getDOByCodePage(fieldCode, pageDO.getId());
            oldFieldDOList.addAll(fieldDOList);
        }

        List<ProcessListPageDO> listPageDOList = processPageService.getListPageByBizCode(bizIdentityCode);
        for (ProcessListPageDO listPageDO : listPageDOList) {
            List<CfgFieldDO> fieldDOList = fieldService.getDOByCodePage(fieldCode, listPageDO.getId());
            oldFieldDOList.addAll(fieldDOList);
        }

        List<ProcessDetailPageDO> detailPageDOList = detailPageService.getByBizCode(bizIdentityCode);
        for (ProcessDetailPageDO detailPageDO : detailPageDOList) {
            List<CfgFieldDO> fieldDOList = fieldService.getDOByCodePage(fieldCode, detailPageDO.getId());
            oldFieldDOList.addAll(fieldDOList);
        }

        Byte dataSourceType = param.getDataSourceType();
        String dataSourceCode = param.getDataSourceCode();
        if (param.getExtraConfig() == null) {
            param.setExtraConfig(new FieldExtraConfig());
        }
        String extraConfigStr = JSONObject.toJSONString(param.getExtraConfig());
        List<Long> fieldIdList = new ArrayList<>();
        Date now = new Date();
        List<CfgFieldDO> fieldDOList = oldFieldDOList.stream().map(fieldDO -> {
            //更新数据源时要删除相关的字段联动展示规则
            if (!Objects.equals(fieldDO.getDatasourceType(), dataSourceType) || !dataSourceCode.equals(fieldDO.getDatasourceCode())) {
                fieldIdList.add(fieldDO.getId());
            }

            CfgFieldDO newField = new CfgFieldDO();
            newField.setId(fieldDO.getId());
            newField.setDatasourceType(dataSourceType);
            newField.setDatasourceCode(dataSourceCode);
            newField.setUpdateTime(now);
            newField.setExtraConfig(extraConfigStr);
            return newField;
        }).toList();

        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            try {
                if (!CollectionUtils.isEmpty(fieldIdList)) {
                    fieldLinkedDisplayRuleService.deleteByFieldIdList(fieldIdList);
                }
                if (!CollectionUtils.isEmpty(fieldDOList)) {
                    fieldService.batchUpdateById(fieldDOList);
                }
                return true;
            } catch (Exception e) {
                log.error("更新下拉框类型字段的数据源发生异常:", e);
                status.setRollbackOnly();
                return false;
            }
        }));
    }

    public List<FieldSimpleInfo> getByFieldAndComponentType(Long fieldId, List<String> componentTypeList) {
        CfgFieldDO cfgFieldDO = fieldService.getDOById(fieldId);

        List<CfgModelDO> modelDOList = modelService.getDOListByPageId(cfgFieldDO.getPageId());
        modelDOList = modelDOList.stream()
                .filter(modelDO -> modelDO.getOwnerType() != null && modelDO.getOwnerType() == ModelOwnerEnum.FIELD_SET.getCode())
                .toList();

        List<CfgFieldDO> list = new ArrayList<>();
        for (CfgModelDO modelDO : modelDOList) {
            List<CfgFieldDO> fieldDOList = fieldService.getDOByModelIdAndComponentTypeList(modelDO.getId(), componentTypeList);
            list.addAll(fieldDOList);
        }
        list = list.stream().filter(i -> !Objects.equals(i.getId(), fieldId)).toList();

        return list.stream().map(fieldDO -> {
            FieldSimpleInfo vo = new FieldSimpleInfo();
            BeanUtils.copyProperties(fieldDO, vo);
            vo.setId(fieldDO.getId().toString());
            return vo;
        }).toList();
    }

    public List<FieldSimpleInfo> getByTableFieldIdAndComponentType(Long fieldId, List<String> componentTypeList) {
        CfgFieldDO fieldDO = fieldService.getDOById(fieldId);
        Long modelId = fieldDO.getModelId();

        List<CfgFieldDO> fieldDOList = fieldService.getDOByModelIdAndComponentTypeList(modelId, componentTypeList);
        fieldDOList = fieldDOList.stream().filter(i -> !Objects.equals(i.getId(), fieldId)).toList();

        return fieldDOList.stream().map(field -> {
            FieldSimpleInfo vo = new FieldSimpleInfo();
            BeanUtils.copyProperties(field, vo);
            vo.setId(field.getId().toString());
            return vo;
        }).toList();
    }

    public Set<Long> getQuotedField(Long pageId) {
        //fieldLinkageRule
        List<Long> linkageRuleFieldIdList = new ArrayList<>();
        List<CfgFieldLinkageRuleDO> linkageRuleDOList = fieldLinkageRuleService.getRuleByPageId(pageId);
        for (CfgFieldLinkageRuleDO ruleDO : linkageRuleDOList) {
            linkageRuleFieldIdList.add(ruleDO.getFieldId());
            if (ValueTypeEnum.DYNAMIC.getValue().equals(ruleDO.getSourceValueType())) {
                linkageRuleFieldIdList.add(Long.parseLong(ruleDO.getSourceValue()));
            }
            linkageRuleFieldIdList.addAll(ruleDO.getTargetFieldIdList());
            if (ValueTypeEnum.DYNAMIC.getValue().equals(ruleDO.getTargetValueType())) {
                linkageRuleFieldIdList.add(Long.parseLong(ruleDO.getTargetValue()));
            }
        }
        Set<Long> res = new HashSet<>(linkageRuleFieldIdList);

        //fieldLinkedDisplayRule
        List<Long> fieldLinkedDisplayRuleFieldIdList = new ArrayList<>();
        List<FieldLinkedDisplayRule> linkedDisplayRuleList = fieldLinkedDisplayRuleService.getByPageId(pageId);
        for (FieldLinkedDisplayRule displayRule : linkedDisplayRuleList) {
            fieldLinkedDisplayRuleFieldIdList.add(displayRule.getSelectFieldId());
            List<Long> fieldIdList = displayRule.getAffectField().stream().map(LinkedDisplayRuleEntry::getFieldId).toList();
            fieldLinkedDisplayRuleFieldIdList.addAll(fieldIdList);
        }
        res.addAll(fieldLinkedDisplayRuleFieldIdList);

        //fieldTableRule
        List<Long> fieldTableRuleFieldIdList = new ArrayList<>();
        List<CfgFieldTableRule> fieldTableRuleList = fieldTableRuleService.getByPageId(pageId);
        for (CfgFieldTableRule ruleDO : fieldTableRuleList) {
            fieldTableRuleFieldIdList.add(ruleDO.getFieldId());
            if (ValueTypeEnum.DYNAMIC.getValue().equals(ruleDO.getSourceValueType())) {
                fieldTableRuleFieldIdList.add(Long.parseLong(ruleDO.getSourceValue()));
            }
        }
        res.addAll(fieldTableRuleFieldIdList);

        //submitRule
        List<Long> submitRuleFieldIdList = new ArrayList<>();
        List<CfgSubmitRuleDO> submitRuleDOList = submitRuleService.getByPageId(pageId);
        for (CfgSubmitRuleDO ruleDO : submitRuleDOList) {
            submitRuleFieldIdList.addAll(ruleDO.getFieldIds());
            if (ValueTypeEnum.DYNAMIC.getValue().equals(ruleDO.getValueType())) {
                submitRuleFieldIdList.add(Long.parseLong(ruleDO.getValue()));
            }
        }
        res.addAll(submitRuleFieldIdList);

        //table
        List<Long> tableFieldIdList = new ArrayList<>();
        List<CfgTableDO> tableDOList = tableService.getDOListByPageId(pageId);
        for (CfgTableDO tableDO : tableDOList) {
            List<Long> list1 = tableDO.getSortTypeList().stream().map(i -> Long.parseLong(i.getId())).toList();
            tableFieldIdList.addAll(list1);
            List<Long> list2 = tableDO.getEditableColumns().stream().map(i -> Long.parseLong(i.getFieldId())).toList();
            tableFieldIdList.addAll(list2);
            List<Long> list3 = tableDO.getDataSummaryRules().stream().map(i -> Long.parseLong(i.getFieldId())).toList();
            tableFieldIdList.addAll(list3);
            List<Long> list4 = tableDO.getSearchFields().stream().map(i -> Long.parseLong(i.getFieldId())).toList();
            tableFieldIdList.addAll(list4);
        }
        res.addAll(tableFieldIdList);

        //tableDataGroupAggregate
        List<CfgTableDataGroupAggregateDO> aggregateDOList = tableDataGroupAggregateService.getDOListByPageId(pageId);
        List<Long> aggregateFieldIdList = new ArrayList<>();
        for (CfgTableDataGroupAggregateDO aggregateDO : aggregateDOList) {
            GroupAggregateField groupField = aggregateDO.getGroupField();
            if (groupField != null) {
                aggregateFieldIdList.add(Long.parseLong(groupField.getId()));
            }
            List<GroupAggregateField> aggregateField = aggregateDO.getAggregateField();
            List<Long> list1 = aggregateField.stream().map(i -> Long.parseLong(i.getId())).toList();
            aggregateFieldIdList.addAll(list1);
            List<GroupAggregateField> otherField = aggregateDO.getOtherField();
            List<Long> list2 = otherField.stream().map(i -> Long.parseLong(i.getId())).toList();
            aggregateFieldIdList.addAll(list2);
        }
        res.addAll(aggregateFieldIdList);

        //tableDataRowEdit
        List<CfgTableDataRowEditDO> rowEditDOList = tableDataRowEditService.getDOListByPageId(pageId);
        List<Long> rowEditFieldIdList = new ArrayList<>();
        for (CfgTableDataRowEditDO rowEditDO : rowEditDOList) {
            rowEditFieldIdList.addAll(rowEditDO.getSourceFieldIds());
            rowEditFieldIdList.add(rowEditDO.getTargetFieldId());
        }
        res.addAll(rowEditFieldIdList);

        //tableDataRowVerify
        List<CfgTableDataRowVerifyDO> rowVerifyDOList = tableDataRowVerifyService.getDOListByPageId(pageId);
        List<Long> rowVerifyFieldIdList = new ArrayList<>();
        for (CfgTableDataRowVerifyDO rowVerifyDO : rowVerifyDOList) {
            rowVerifyFieldIdList.addAll(rowVerifyDO.getFieldIds());
            if (ValueTypeEnum.DYNAMIC.getValue().equals(rowVerifyDO.getValueType())) {
                rowVerifyFieldIdList.add(Long.parseLong(rowVerifyDO.getValue()));
            }
        }
        res.addAll(rowVerifyFieldIdList);

        return res;
    }

    public Result<List<FieldDefaultByModelIdVO>> listByBizIdentityCode(String bizIdentityCode) {
        List<CfgPageDO> pageDOList = pageService.getDOListByIdentityCode(bizIdentityCode);
        if (CollectionUtils.isEmpty(pageDOList)) {
            return Result.error("没有配置专属页面");
        }
        List<Long> pageIdList = pageDOList.stream().map(CfgPageDO::getId).toList();
        List<CfgModelDO> modeDoList = modelService.getDOListByPageIdList(pageIdList);
        if (CollectionUtils.isEmpty(modeDoList)) {
            return Result.ok(new ArrayList<>());
        }

        Map<Long, CfgModelDO> modelDOMap = modeDoList.stream().collect(Collectors.toMap(CfgModelDO::getId, Function.identity()));
        List<Long> modelIdList = modeDoList.stream().map(CfgModelDO::getId).toList();
        List<CfgFieldDO> fieldDOList = fieldService.getDOListByModelIds(modelIdList);
        fieldDOList = fieldDOList.stream().filter(e -> Objects.equals(e.getFieldType(), (byte) 2)).toList();
        if (CollectionUtils.isEmpty(fieldDOList)) {
            return Result.ok(new ArrayList<>());
        }
        Map<String, List<CfgFieldDO>> bizCodeGroup = fieldDOList.stream().collect(Collectors.groupingBy(CfgFieldDO::getBizCode));

        List<FieldDefaultByModelIdVO> modelVOList = new ArrayList<>();
        for (Map.Entry<String, List<CfgFieldDO>> entry : bizCodeGroup.entrySet()) {
            List<CfgFieldDO> tempFieldList = entry.getValue();
            tempFieldList.sort(Comparator.comparing(CfgFieldDO::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
            CfgFieldDO fieldDO = tempFieldList.get(0);
            FieldDefaultByModelIdVO vo = new FieldDefaultByModelIdVO();
            CfgModelDO modelDO = modelDOMap.get(fieldDO.getModelId());
            vo.setId(fieldDO.getId().toString());
            vo.setModelName(modelDO.getName());
            vo.setFieldName(fieldDO.getBizName());
            vo.setFieldCode(fieldDO.getBizCode());
            vo.setDisplayed(fieldDO.getDisplayed());
            vo.setRequired(fieldDO.getRequired());
            vo.setId(fieldDO.getId().toString());
            vo.setDataBinding(fieldDO.getDataBinding());
            vo.setComponentType(fieldDO.getComponentType());
            vo.setCreateBy(fieldDO.getCreateBy());
            vo.setCreateTime(fieldDO.getCreateTime());
            vo.setUpdateBy(fieldDO.getUpdateBy());
            vo.setUpdateTime(fieldDO.getUpdateTime());
            modelVOList.add(vo);

        }
        return Result.ok(modelVOList);

    }

    public List<FieldDefaultByModelIdVO> getDisplayField(Long modelId, Byte display) {
        List<CfgFieldDO> fieldList = fieldService.getDisplayFieldList(modelId, display);
        if (CollectionUtils.isEmpty(fieldList)) {
            return new ArrayList<>();
        }
        fieldList.sort(Comparator.comparing(CfgFieldDO::getSequence, Comparator.nullsLast(Comparator.naturalOrder())));
        List<FieldDefaultByModelIdVO> voList = new ArrayList<>(fieldList.size());
        for (CfgFieldDO fieldDO : fieldList) {
            FieldDefaultByModelIdVO vo = new FieldDefaultByModelIdVO();
            vo.setId(String.valueOf(fieldDO.getId()));
            vo.setFieldCode(fieldDO.getBizCode());
            vo.setFieldName(fieldDO.getBizName());
            vo.setSequence(fieldDO.getSequence());
            voList.add(vo);
        }
        return voList;
    }

    public boolean updateFieldDisplay(Long modelId, List<Long> fieldIdList) {
        if (CollectionUtils.isEmpty(fieldIdList)) {
            throw new ServiceException(500, "请选择字段");
        }
        CfgModelDO modelDO = modelService.getDOById(modelId);
        if (Objects.isNull(modelDO)) {
            throw new ServiceException(500, "模型不存在");
        }

        List<CfgFieldDO> fieldList = fieldService.getDOListByModelIds(Arrays.asList(modelId));
        List<Long> allFieldIdList = fieldList.stream().map(CfgFieldDO::getId).toList();
        List<Long> illegalFieldIdList = fieldIdList.stream().filter(e -> !allFieldIdList.contains(e)).toList();
        if (!CollectionUtils.isEmpty(illegalFieldIdList)) {
            log.error("illegalFieldIdList: {}", JSON.toJSONString(illegalFieldIdList));
            throw new ServiceException(500, "字段非法");
        }

        List<CfgFieldDO> updateFiledList = new ArrayList<>(fieldList.size());
        Map<Long, Integer> indexMap = new HashMap<>(fieldList.size());
        for (int i = 0; i < fieldIdList.size(); i++) {
            Long fieldId = fieldIdList.get(i);
            if (!indexMap.containsKey(fieldId)) {
                indexMap.put(fieldId, i + 1);
            }
        }
        for (int i = 0; i < fieldList.size(); i++) {
            CfgFieldDO fieldDO = fieldList.get(i);
            CfgFieldDO updateFiled = new CfgFieldDO();
            updateFiled.setId(fieldDO.getId());
            if (fieldIdList.contains(fieldDO.getId())) {
                updateFiled.setDisplayed(DisplayEnum.DISPLAY.getCode());
                updateFiled.setSequence(indexMap.get(fieldDO.getId()));
            } else {
                updateFiled.setDisplayed(DisplayEnum.UN_DISPLAY.getCode());
                updateFiled.setSequence(null);
            }
            updateFiledList.add(updateFiled);
        }
        fieldService.batchUpdateById(updateFiledList);
        return true;
    }
}
