package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bone.lowcode.infra.application.vo.table.FieldSimpleInfo;
import com.bone.lowcode.infra.domain.valueobject.*;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFieldDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.FieldMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.BatchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FieldService {

    @Autowired
    private FieldMapper fieldMapper;

    public boolean updateAcceptNull(CfgFieldDO fieldDO) {
        LambdaUpdateWrapper<CfgFieldDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CfgFieldDO::getId, fieldDO.getId())
                .set(StringUtils.hasText(fieldDO.getTitle()), CfgFieldDO::getTitle, fieldDO.getTitle())
                .set(StringUtils.hasText(fieldDO.getDataBinding()), CfgFieldDO::getDataBinding, fieldDO.getDataBinding())
                .set(StringUtils.hasText(fieldDO.getComponentType()), CfgFieldDO::getComponentType, fieldDO.getComponentType())
                .set(fieldDO.getAlignment() != null, CfgFieldDO::getAlignment, fieldDO.getAlignment())
                .set(CfgFieldDO::getPrompt, fieldDO.getPrompt())  //允许更新为null
                .set(CfgFieldDO::getPlaceholder, fieldDO.getPlaceholder())  //允许更新为null
                .set(fieldDO.getWidth() != null, CfgFieldDO::getWidth, fieldDO.getWidth())
                .set(fieldDO.getCharacterLimit() != null, CfgFieldDO::getCharacterLimit, fieldDO.getCharacterLimit())
                .set(fieldDO.getInputState() != null, CfgFieldDO::getInputState, fieldDO.getInputState())
                .set(fieldDO.getDisplayed() != null, CfgFieldDO::getDisplayed, fieldDO.getDisplayed())
                .set(fieldDO.getRequired() != null, CfgFieldDO::getRequired, fieldDO.getRequired())
                .set(CfgFieldDO::getDefaultValue, fieldDO.getDefaultValue())  //允许更新为null
                .set(fieldDO.getValueType() != null, CfgFieldDO::getValueType, fieldDO.getValueType())
                .set(fieldDO.getDataFormat() != null, CfgFieldDO::getDataFormat, fieldDO.getDataFormat())
                .set(StringUtils.hasText(fieldDO.getMin()), CfgFieldDO::getMin, fieldDO.getMin())
                .set(StringUtils.hasText(fieldDO.getMax()), CfgFieldDO::getMax, fieldDO.getMax())
                .set(fieldDO.getDecimalDigit() != null, CfgFieldDO::getDecimalDigit, fieldDO.getDecimalDigit())
                .set(StringUtils.hasText(fieldDO.getMultiples()), CfgFieldDO::getMultiples, fieldDO.getMultiples())
                .set(fieldDO.getSelectType() != null, CfgFieldDO::getSelectType, fieldDO.getSelectType())
                .set(fieldDO.getFilterType() != null, CfgFieldDO::getFilterType, fieldDO.getFilterType())
//                .set(StringUtils.hasText(fieldDO.getSelectDatasource()), CfgFieldDO::getSelectDatasource, fieldDO.getSelectDatasource())
                .set(fieldDO.getDatasourceType() != null && StringUtils.hasText(fieldDO.getDatasourceCode()), CfgFieldDO::getDatasourceType, fieldDO.getDatasourceType())
                .set(fieldDO.getDatasourceType() != null && StringUtils.hasText(fieldDO.getDatasourceCode()), CfgFieldDO::getDatasourceCode, fieldDO.getDatasourceCode())
                .set(CfgFieldDO::getPlaceholderTwo, fieldDO.getPlaceholderTwo()) //允许更新为null
                .set(CfgFieldDO::getPlaceholderThree, fieldDO.getPlaceholderThree()) //允许更新为null
                .set(fieldDO.getSelectLevel() != null, CfgFieldDO::getSelectLevel, fieldDO.getSelectLevel())
                .set(fieldDO.getDateFormatType() != null, CfgFieldDO::getDateFormatType, fieldDO.getDateFormatType())
                .set(CfgFieldDO::getEarliestDatetime, fieldDO.getEarliestDatetime()) //允许更新为null
                .set(fieldDO.getEarliestDatetimeType() != null, CfgFieldDO::getEarliestDatetimeType, fieldDO.getEarliestDatetimeType())
                .set(CfgFieldDO::getLatestDatetime, fieldDO.getLatestDatetime()) //允许更新为null
                .set(fieldDO.getLatestDatetimeType() != null, CfgFieldDO::getLatestDatetimeType, fieldDO.getLatestDatetimeType())
                .set(fieldDO.getDateApplyScene() != null, CfgFieldDO::getDateApplyScene, fieldDO.getDateApplyScene())
                .set(fieldDO.getRowNo() != null, CfgFieldDO::getRowNo, fieldDO.getRowNo())
                .set(fieldDO.getColumnNo() != null, CfgFieldDO::getColumnNo, fieldDO.getColumnNo())
                .set(fieldDO.getWidth() != null, CfgFieldDO::getWidth, fieldDO.getWidth())
                .set(fieldDO.getSequence() != null, CfgFieldDO::getSequence, fieldDO.getSequence())
                .set(fieldDO.getDeleted() != null, CfgFieldDO::getDeleted, fieldDO.getDeleted())
                .set(fieldDO.getCreateBy() != null, CfgFieldDO::getCreateBy, fieldDO.getCreateBy())
                .set(fieldDO.getCreateTime() != null, CfgFieldDO::getCreateTime, fieldDO.getCreateTime())
                .set(fieldDO.getUpdateBy() != null, CfgFieldDO::getUpdateBy, fieldDO.getUpdateBy())
                .set(fieldDO.getUpdateTime() != null, CfgFieldDO::getUpdateTime, fieldDO.getUpdateTime())
        ;
        return fieldMapper.update(wrapper) > 0;
    }

    public boolean updateFieldById(CfgFieldDO fieldDO) {
        return fieldMapper.updateById(fieldDO) > 0;
    }

    public boolean batchUpdateById(List<CfgFieldDO> fieldDOList) {
        List<BatchResult> batchResultList = fieldMapper.updateById(fieldDOList);
        log.info("field批量更新结果:{}", batchResultList.stream().map(i -> Arrays.toString(i.getUpdateCounts())).collect(Collectors.toList()));
        return true;
    }

    public List<CfgFieldDO> getDOListByModelId(Long modelId) {
        LambdaQueryWrapper<CfgFieldDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CfgFieldDO::getModelId, modelId)
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldMapper.selectList(wrapper);
    }

    public List<CfgFieldDO> getDOListByModelIds(List<Long> modelIds) {
        LambdaQueryWrapper<CfgFieldDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CfgFieldDO::getModelId, modelIds)
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldMapper.selectList(wrapper);
    }

    public List<CfgFieldDO> getDOListByIdList(List<Long> idList) {
        List<CfgFieldDO> fieldDOList = fieldMapper.selectList(new LambdaQueryWrapper<CfgFieldDO>()
                .in(CfgFieldDO::getId, idList)
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
        return fieldDOList;
    }

    public Integer batchSaveWithId(List<CfgFieldDO> fieldDOList) {
        Integer count = fieldMapper.batchSaveWithId(fieldDOList);
        return count;
    }

    public boolean batchSave(List<CfgFieldDO> fieldDOList) {
        fieldMapper.insert(fieldDOList);
        return true;
    }

    public boolean addField(CfgFieldDO fieldDO) {
        return fieldMapper.insert(fieldDO) > 0;
    }

    public boolean deleteByIdList(List<Long> idList) {
        int i = fieldMapper.deleteByIds(idList);
        return i > 0;
    }

    public boolean deleteLogic(List<Long> idList) {
        LambdaUpdateWrapper<CfgFieldDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(CfgFieldDO::getId, idList)
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode())
                .set(CfgFieldDO::getDeleted, DeletedEnum.DELETED.getCode());
        return fieldMapper.update(wrapper) > 0;
    }

    public CfgFieldDO getDOById(Long id) {
        return fieldMapper.selectById(id);
    }

    public FieldSimpleInfo getFieldSimpleInfoById(Long id) {
        CfgFieldDO fieldDO = fieldMapper.selectById(id);
        if (fieldDO == null) return null;
        return new FieldSimpleInfo(fieldDO.getId().toString(), fieldDO.getBizCode(),
                fieldDO.getBizName(), fieldDO.getDataBinding());
    }

    public List<CfgFieldDO> getDOListByModelIdAndComponentType(Long modelId, String componentType) {
        LambdaQueryWrapper<CfgFieldDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CfgFieldDO::getModelId, modelId)
                .eq(CfgFieldDO::getComponentType, componentType)
                .eq(CfgFieldDO::getDisplayed, DisplayEnum.DISPLAY.getCode())
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldMapper.selectList(wrapper);
    }


    public List<CfgFieldDO> getDOByModelIdAndComponentType(Long modelId, String componentType) {
        LambdaQueryWrapper<CfgFieldDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CfgFieldDO::getModelId, modelId)
                .eq(CfgFieldDO::getComponentType, componentType)
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldMapper.selectList(wrapper);
    }

    public List<CfgFieldDO> getDOByModelIdAndComponentTypeList(Long modelId, List<String> componentTypeList) {
        LambdaQueryWrapper<CfgFieldDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CfgFieldDO::getModelId, modelId)
                .in(CfgFieldDO::getComponentType, componentTypeList)
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldMapper.selectList(wrapper);
    }

    public List<CfgFieldDO> getDisplayedDOListByModelIdList(List<Long> modelIdList) {
        LambdaQueryWrapper<CfgFieldDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CfgFieldDO::getModelId, modelIdList)
                .eq(CfgFieldDO::getDisplayed, DisplayEnum.DISPLAY.getCode())
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldMapper.selectList(wrapper);
    }

    public List<CfgFieldDO> getDOListByModelIdAndComponentTypeList(Long modelId, List<String> componentTypeList) {
        LambdaQueryWrapper<CfgFieldDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CfgFieldDO::getModelId, modelId)
                .in(CfgFieldDO::getComponentType, componentTypeList)
                .eq(CfgFieldDO::getDisplayed, DisplayEnum.DISPLAY.getCode())
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldMapper.selectList(wrapper);
    }

    public List<CfgFieldDO> getDOByPageId(Long pageId) {
        return fieldMapper.selectList(new LambdaQueryWrapper<CfgFieldDO>()
                .eq(CfgFieldDO::getPageId, pageId)
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public List<CfgFieldDO> getDOByPageIds(Collection<Long> pageIds) {
        return fieldMapper.selectList(new LambdaQueryWrapper<CfgFieldDO>()
                .in(CfgFieldDO::getPageId, pageIds)
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public List<CfgFieldDO> getExtFieldList(List<Long> pageIdList, String bizCode) {
        LambdaQueryWrapper<CfgFieldDO> wrapper = new LambdaQueryWrapper<CfgFieldDO>()
                .eq(CfgFieldDO::getFieldType, FieldTypeEnum.BIZ.getCode())
                .in(CfgFieldDO::getPageId, pageIdList)
                .eq(CfgFieldDO::getBizCode, bizCode)
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldMapper.selectList(wrapper);
    }

    public CfgFieldDO getAnyFieldWithDatasource(String optionSetCode) {
        LambdaQueryWrapper<CfgFieldDO> wrapper = new LambdaQueryWrapper<CfgFieldDO>()
                .eq(CfgFieldDO::getDatasourceType, DataSourceTypeEnum.OPTION_SET.getType())
                .eq(CfgFieldDO::getDatasourceCode, optionSetCode)
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode())
                .last("limit 1");
        return fieldMapper.selectOne(wrapper);
    }

    public List<CfgFieldDO> getPageSelectDO(Long pageId) {
        LambdaQueryWrapper<CfgFieldDO> wrapper = new LambdaQueryWrapper<CfgFieldDO>()
                .eq(CfgFieldDO::getPageId, pageId)
                .in(CfgFieldDO::getComponentType, Arrays.asList(ComponentTypeEnum.SELECT_DROP.getType(), ComponentTypeEnum.SELECT_CTRL.getType()))
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldMapper.selectList(wrapper);
    }

    public List<CfgFieldDO> getDOByCodePage(String fieldCode, Long pageId) {
        LambdaQueryWrapper<CfgFieldDO> wrapper = new LambdaQueryWrapper<CfgFieldDO>()
                .eq(CfgFieldDO::getPageId, pageId)
                .eq(CfgFieldDO::getBizCode, fieldCode)
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldMapper.selectList(wrapper);
    }

    public List<CfgFieldDO> getSelectDOByParam(String fieldCode, List<Long> pageIdList, List<Long> modelIdList) {
        LambdaQueryWrapper<CfgFieldDO> wrapper = new LambdaQueryWrapper<CfgFieldDO>()
                .eq(CfgFieldDO::getBizCode, fieldCode)
                .in(CfgFieldDO::getPageId, pageIdList)
                .in(CfgFieldDO::getModelId, modelIdList)
                .in(CfgFieldDO::getComponentType, Arrays.asList(ComponentTypeEnum.SELECT_DROP.getType(), ComponentTypeEnum.SELECT_CTRL.getType()))
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldMapper.selectList(wrapper);
    }

    public List<CfgFieldDO> getSelectDOByParam(List<Long> pageIdList, List<Long> modelIdList) {
        LambdaQueryWrapper<CfgFieldDO> wrapper = new LambdaQueryWrapper<CfgFieldDO>()
                .in(CfgFieldDO::getPageId, pageIdList)
                .in(CfgFieldDO::getModelId, modelIdList)
                .in(CfgFieldDO::getComponentType, Arrays.asList(ComponentTypeEnum.SELECT_DROP.getType(), ComponentTypeEnum.SELECT_CTRL.getType()))
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldMapper.selectList(wrapper);
    }

    public List<CfgFieldDO> getSystemDOByModelId(Long modelId) {
        LambdaQueryWrapper<CfgFieldDO> wrapper = new LambdaQueryWrapper<CfgFieldDO>()
                .eq(CfgFieldDO::getModelId, modelId)
                .eq(CfgFieldDO::getFieldType, FieldTypeEnum.SYSTEM.getCode())
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldMapper.selectList(wrapper);
    }


    public List<CfgFieldDO> getDisplayFieldList(Long modelId, Byte display) {
        LambdaQueryWrapper<CfgFieldDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CfgFieldDO::getModelId, modelId)
                .eq(CfgFieldDO::getDisplayed, display)
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldMapper.selectList(queryWrapper);
    }

    public CfgFieldDO getDOByModelIdAndCode(Long modelId, String bizCode) {
        LambdaQueryWrapper<CfgFieldDO> wrapper = new LambdaQueryWrapper<CfgFieldDO>()
                .eq(CfgFieldDO::getModelId, modelId)
                .eq(CfgFieldDO::getBizCode, bizCode)
                .eq(CfgFieldDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldMapper.selectOne(wrapper);
    }
}
