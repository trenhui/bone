package com.bone.lowcode.infra.application.convert;

import com.alibaba.fastjson.JSONObject;
import com.bone.lowcode.infra.application.dto.field.BatchUpdateFieldDefaultDTO;
import com.bone.lowcode.infra.application.dto.field.BatchUpdateFieldLocationDTO;
import com.bone.lowcode.infra.application.dto.field.UpdateFieldDTO;
import com.bone.lowcode.infra.application.dto.table.FieldOfTableChange;
import com.bone.lowcode.infra.application.vo.field.*;
import com.bone.lowcode.infra.application.vo.page.pageJson.Field;
import com.bone.lowcode.infra.application.vo.page.pageJson.FieldSet;
import com.bone.lowcode.infra.application.vo.page.pageJson.SubmitRuleField;
import com.bone.lowcode.infra.domain.model.SelectDatasource;
import com.bone.lowcode.infra.domain.valueobject.ComponentTypeEnum;
import com.bone.lowcode.infra.domain.valueobject.DateFormatEnum;
import com.bone.lowcode.infra.domain.valueobject.PageItemTypeEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFieldDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFieldsetDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgModelDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.MetaBizModel;
import com.bone.lowcode.infra.infrastructure.persistence.dto.FieldExtraConfig;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class FieldConvert {

    public static FieldSet getFieldSet(CfgFieldsetDO fieldsetDO) {
        FieldSet fieldSet = new FieldSet();
        BeanUtils.copyProperties(fieldsetDO, fieldSet);
        fieldSet.setId(fieldsetDO.getId().toString());
        fieldSet.setType(PageItemTypeEnum.FIELD_SET.getName());
        fieldSet.setName(fieldsetDO.getName());
        return fieldSet;
    }

    public static CfgFieldDO fieldDtoToDo(UpdateFieldDTO updateFieldDto) throws ParseException {
        CfgFieldDO fieldDO = new CfgFieldDO();
        fieldDO.setId(updateFieldDto.getId());
        fieldDO.setTitle(updateFieldDto.getShowName());
        fieldDO.setPrompt(updateFieldDto.getPrompt());
        fieldDO.setPlaceholder(updateFieldDto.getPlaceholder());
        fieldDO.setWidth(updateFieldDto.getWidth());
        fieldDO.setCharacterLimit(updateFieldDto.getLimitedLength());
        fieldDO.setInputState(updateFieldDto.getInputStatus());
        fieldDO.setDisplayed(updateFieldDto.getDisplayed());
        fieldDO.setRequired(updateFieldDto.getRequired());
        fieldDO.setDefaultValue(updateFieldDto.getDefaultValue());
        fieldDO.setValueType(updateFieldDto.getValueType());
        fieldDO.setDataFormat(updateFieldDto.getDataFormat());
        fieldDO.setMin(updateFieldDto.getMin());
        fieldDO.setMax(updateFieldDto.getMax());
        fieldDO.setDecimalDigit(updateFieldDto.getDecimalDigit());
        fieldDO.setMultiples(updateFieldDto.getMultiples());
        fieldDO.setSelectType(updateFieldDto.getSelectType());
        fieldDO.setFilterType(updateFieldDto.getFilterType());
        if (updateFieldDto.getSelectDatasource() != null) {
//            fieldDO.setDatasource(updateFieldDto.getSelectDatasource());
            fieldDO.setDatasourceType(updateFieldDto.getSelectDatasource().getType());
            fieldDO.setDatasourceCode(updateFieldDto.getSelectDatasource().getCode());
        }
        fieldDO.setPlaceholderTwo(updateFieldDto.getPlaceholderTwo());
        fieldDO.setPlaceholderThree(updateFieldDto.getPlaceholderThree());
        fieldDO.setSelectLevel(updateFieldDto.getSelectLevel());
        Byte dateFormat = updateFieldDto.getDateFormatType();
        fieldDO.setDateFormatType(dateFormat);

        if (StringUtils.hasText(updateFieldDto.getEarliestDatetime())) {
            if (dateFormat == null) {
                dateFormat = (byte) DateFormatEnum.YMD.getCode();
                fieldDO.setDateFormatType(dateFormat);
            }
            String format = DateFormatEnum.getDateFormatByCode(dateFormat);
            if (StringUtils.hasText(format)) {
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                Date earliestDatetime = sdf.parse(updateFieldDto.getEarliestDatetime());
                fieldDO.setEarliestDatetime(earliestDatetime);
            }
        }
        fieldDO.setEarliestDatetimeType(updateFieldDto.getEarliestDatetimeType());
        if (StringUtils.hasText(updateFieldDto.getLatestDatetime())) {
            if (dateFormat == null) {
                dateFormat = (byte) DateFormatEnum.YMD.getCode();
                fieldDO.setDateFormatType(dateFormat);
            }
            String format = DateFormatEnum.getDateFormatByCode(dateFormat);
            if (StringUtils.hasText(format)) {
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                Date latestDatetime = sdf.parse(updateFieldDto.getLatestDatetime());
                fieldDO.setLatestDatetime(latestDatetime);
            }
        }
        fieldDO.setLatestDatetimeType(updateFieldDto.getLatestDatetimeType());
        fieldDO.setDateApplyScene(updateFieldDto.getDateApplyScene());
        fieldDO.setUpdateTime(new Date());
        return fieldDO;
    }

    public static List<Field> fieldDOListToFieldList(List<CfgFieldDO> fieldDOList) {
        List<Field> list = new ArrayList<>();
        for (CfgFieldDO fieldDO : fieldDOList) {
            list.add(fieldDOToField(fieldDO));
        }
        return list;
    }

    public static Field fieldDOToField(CfgFieldDO fieldDO) {
        Field field = new Field();
        field.setId(fieldDO.getId().toString());
        field.setType(fieldDO.getComponentType());
        field.setName(fieldDO.getBizName());
        field.setCode(fieldDO.getBizCode());
        field.setShowName(fieldDO.getTitle());
        field.setCol(fieldDO.getColumnNo());
        field.setRow(fieldDO.getRowNo());
        field.setWidth(fieldDO.getWidth());
        field.setFieldType(fieldDO.getFieldType());
        field.setDataBinding(fieldDO.getDataBinding());
        field.setAlignment(fieldDO.getAlignment());
        field.setPrompt(fieldDO.getPrompt());
        field.setPlaceholder(fieldDO.getPlaceholder());
        field.setLimitedLength(fieldDO.getCharacterLimit());
        field.setInputStatus(fieldDO.getInputState());
        field.setDisplayed(fieldDO.getDisplayed());
        field.setRequired(fieldDO.getRequired());
        field.setDefaultValue(fieldDO.getDefaultValue());
        field.setValueType(fieldDO.getValueType());
        field.setDataFormat(fieldDO.getDataFormat());
        field.setMin(fieldDO.getMin());
        field.setMax(fieldDO.getMax());
        field.setDecimalDigit(fieldDO.getDecimalDigit());
        field.setMultiples(fieldDO.getMultiples());
        field.setSelectType(fieldDO.getSelectType());
        field.setFilterType(fieldDO.getFilterType());
//        if (StringUtils.hasText(fieldDO.getSelectDatasource())) {
//            SelectDatasource datasource = fieldDO.getDatasource();
//            field.setSelectDatasource(datasource);
//        }
        SelectDatasource datasource = new SelectDatasource(fieldDO.getDatasourceType(), fieldDO.getDatasourceCode());
        field.setSelectDatasource(datasource);

        field.setPlaceholderTwo(fieldDO.getPlaceholderTwo());
        field.setPlaceholderThree(fieldDO.getPlaceholderThree());
        field.setSelectLevel(fieldDO.getSelectLevel());
        field.setDateFormatType(fieldDO.getDateFormatType());
        if (fieldDO.getEarliestDatetime() != null) {
            field.setEarliestDatetime(DateFormatEnum.parseDateToStr(fieldDO.getEarliestDatetime()));
        }
        field.setEarliestDatetimeType(fieldDO.getEarliestDatetimeType());
        if (fieldDO.getLatestDatetime() != null) {
            field.setLatestDatetime(DateFormatEnum.parseDateToStr(fieldDO.getLatestDatetime()));
        }
        field.setLatestDatetimeType(fieldDO.getLatestDatetimeType());
        field.setDateApplyScene(fieldDO.getDateApplyScene());
        field.setSequence(fieldDO.getSequence());
        String extraStr = fieldDO.getExtraConfig();
        if (org.apache.commons.lang3.StringUtils.isBlank(extraStr)) {
            extraStr = "{}";
        }
        //设置扩展字段
        field.setExtraConfig(JSONObject.parseObject(extraStr, FieldExtraConfig.class));
        return field;
    }

    public static List<CfgFieldDO> fieldDefaultDtoListToDOList(List<BatchUpdateFieldDefaultDTO> dtoList) {
        List<CfgFieldDO> list = new ArrayList<>();
        for (BatchUpdateFieldDefaultDTO dto : dtoList) {
            CfgFieldDO fieldDO = new CfgFieldDO();
            fieldDO.setId(dto.getFieldId());
            fieldDO.setDisplayed(dto.getDisplayed());
            fieldDO.setRequired(dto.getRequired());
            if (StringUtils.hasText(dto.getDataBinding())) {
                fieldDO.setDataBinding(dto.getDataBinding().trim());
            }
            fieldDO.setComponentType(dto.getComponentType());

            if (fieldDO.getDisplayed() == null && fieldDO.getRequired() == null &&
                    !StringUtils.hasText(fieldDO.getDataBinding()) &&
                    !StringUtils.hasText(fieldDO.getComponentType())) {
                continue;
            }
            list.add(fieldDO);
        }
        return list;
    }

    public static CfgFieldDO metaFieldToDO(MetaBizModel metaField) {
        CfgFieldDO fieldDO = new CfgFieldDO();
        fieldDO.setBizCode(metaField.getFieldCode());
        fieldDO.setBizName(metaField.getFieldName());
        fieldDO.setTitle(metaField.getFieldName());
        fieldDO.setDataBinding(metaField.getDataBindingPrefix() + metaField.getFieldCode());
        fieldDO.setComponentType(metaField.getComponentType());
        return fieldDO;
    }

    public static FieldLocationVO fieldDOToFieldLocationVO(CfgFieldDO fieldDO, CfgModelDO model) {
        FieldLocationVO vo = new FieldLocationVO();
        BeanUtils.copyProperties(fieldDO, vo);
        vo.setId(fieldDO.getId().toString());
        if (fieldDO.getModelId() != null) vo.setModelId(fieldDO.getModelId().toString());
        vo.setModelCode(model.getCode());
        vo.setModelName(model.getName());
        vo.setFieldName(fieldDO.getBizName());
        vo.setFieldCode(fieldDO.getBizCode());
        vo.setRowNumber(fieldDO.getRowNo());
        vo.setColumnNumber(fieldDO.getColumnNo());
        vo.setWidth(fieldDO.getWidth());
        return vo;
    }

    public static FieldDefaultByBlockIdVO fieldDOToFieldDefaultVO(CfgFieldDO fieldDO, String modelName) {
        FieldDefaultByBlockIdVO vo = new FieldDefaultByBlockIdVO();
        vo.setId(fieldDO.getId().toString());
        vo.setModelName(modelName);
        vo.setFieldName(fieldDO.getBizName());
        vo.setFieldCode(fieldDO.getBizCode());
        vo.setDisplayed(fieldDO.getDisplayed());
        vo.setRequired(fieldDO.getRequired());
        return vo;
    }

    public static List<CfgFieldDO> fieldLocationDtoListToDOList(List<BatchUpdateFieldLocationDTO> dtoList) {
        List<CfgFieldDO> list = new ArrayList<>();
        for (BatchUpdateFieldLocationDTO dto : dtoList) {
            CfgFieldDO fieldDO = new CfgFieldDO();
            fieldDO.setId(dto.getFieldId());
            fieldDO.setRowNo(dto.getRowNumber());
            fieldDO.setColumnNo(dto.getColumnNumber());
            fieldDO.setWidth(dto.getWidth());
            list.add(fieldDO);
        }
        return list;
    }

    public static List<CfgFieldDO> fieldChangeListToDOList(List<FieldOfTableChange> fieldOfTableChangeList) {
        List<CfgFieldDO> fieldDOList = new ArrayList<>();
        for (FieldOfTableChange change : fieldOfTableChangeList) {
            if (change.getFieldId() == null || (change.getSequence() == null && change.getDisplay() == null)) {
                continue;
            }
            CfgFieldDO fieldDO = new CfgFieldDO();
            fieldDO.setId(change.getFieldId());
            fieldDO.setDisplayed(change.getDisplay());
            fieldDO.setSequence(change.getSequence());
            fieldDOList.add(fieldDO);
        }
        return fieldDOList;
    }

    public static GetByIdVo DOToGetByIdVO(CfgFieldDO fieldDO) {
        GetByIdVo vo = new GetByIdVo();
        vo.setId(fieldDO.getId().toString());
        vo.setType(fieldDO.getComponentType());
        vo.setName(fieldDO.getBizName());
        vo.setCode(fieldDO.getBizCode());
        vo.setShowName(fieldDO.getTitle());
        vo.setCol(fieldDO.getColumnNo());
        vo.setRow(fieldDO.getRowNo());
        vo.setFieldType(fieldDO.getFieldType());
        vo.setDataBinding(fieldDO.getDataBinding());
        vo.setAlignment(fieldDO.getAlignment());
        vo.setPrompt(fieldDO.getPrompt());
        vo.setWidth(fieldDO.getWidth());
        vo.setPlaceholder(fieldDO.getPlaceholder());
        vo.setLimitedLength(fieldDO.getCharacterLimit());
        vo.setInputStatus(fieldDO.getInputState());
        vo.setDisplayed(fieldDO.getDisplayed());
        vo.setRequired(fieldDO.getRequired());
        vo.setDefaultValue(fieldDO.getDefaultValue());
        vo.setValueType(fieldDO.getValueType());
        vo.setDataFormat(fieldDO.getDataFormat());
        vo.setMin(fieldDO.getMin());
        vo.setMax(fieldDO.getMax());
        vo.setDecimalDigit(fieldDO.getDecimalDigit());
        vo.setMultiples(fieldDO.getMultiples());
        vo.setSelectType(fieldDO.getSelectType());
        vo.setFilterType(fieldDO.getFilterType());
//        if (StringUtils.hasText(fieldDO.getSelectDatasource())) {
//            SelectDatasource datasource = fieldDO.getDatasource();
//            vo.setSelectDatasource(datasource);
//        }
        SelectDatasource datasource = new SelectDatasource(fieldDO.getDatasourceType(), fieldDO.getDatasourceCode());
        vo.setSelectDatasource(datasource);
        vo.setPlaceholderTwo(fieldDO.getPlaceholderTwo());
        vo.setPlaceholderThree(fieldDO.getPlaceholderThree());
        vo.setSelectLevel(fieldDO.getSelectLevel());
        Byte dateFormatType = fieldDO.getDateFormatType();
        vo.setDateFormatType(dateFormatType);
        if (fieldDO.getEarliestDatetime() != null && dateFormatType != null) {
            String format = DateFormatEnum.getDateFormatByCode(dateFormatType);
            if (StringUtils.hasText(format)) {
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                String earliestDatetime = sdf.format(fieldDO.getEarliestDatetime());
                vo.setEarliestDatetime(earliestDatetime);
            }
        }
        vo.setEarliestDatetimeType(fieldDO.getEarliestDatetimeType());
        if (fieldDO.getLatestDatetime() != null && dateFormatType != null) {
            String format = DateFormatEnum.getDateFormatByCode(dateFormatType);
            if (StringUtils.hasText(format)) {
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                String latestDatetime = sdf.format(fieldDO.getLatestDatetime());
                vo.setLatestDatetime(latestDatetime);
            }
        }
        vo.setLatestDatetimeType(fieldDO.getLatestDatetimeType());
        vo.setDateApplyScene(fieldDO.getDateApplyScene());
        return vo;
    }

    public static GetSameTypeFieldListVO DOToVO(CfgFieldDO cfgFieldDO, String modelName) {
        GetSameTypeFieldListVO vo = new GetSameTypeFieldListVO();
        BeanUtils.copyProperties(cfgFieldDO, vo);
        vo.setId(cfgFieldDO.getId().toString());
        vo.setModelName(modelName);
        if (ComponentTypeEnum.SELECT_DROP.getType().equals(cfgFieldDO.getComponentType()) ||
                ComponentTypeEnum.SELECT_CTRL.getType().equals(cfgFieldDO.getComponentType())) {
            vo.setSelectDatasource(new SelectDatasource(cfgFieldDO.getDatasourceType(), cfgFieldDO.getDatasourceCode()));
        }
        if (ComponentTypeEnum.SELECT_CTRL.getType().equals(cfgFieldDO.getComponentType())) {
            vo.setSelectLevel(cfgFieldDO.getSelectLevel());
        }
        return vo;
    }

    public static FieldRuleField DOToFieldRuleField(CfgFieldDO fieldDO) {
        FieldRuleField field = new FieldRuleField();
        field.setId(fieldDO.getId().toString());
        field.setCode(fieldDO.getBizCode());
        field.setShowName(fieldDO.getBizName());
        field.setFieldType(fieldDO.getFieldType());
        field.setComponentType(fieldDO.getComponentType());
        field.setDataBinding(fieldDO.getDataBinding());
        return field;
    }

    public static GetByPageAndComponentTypeVO DOToGetByPageAndComponentTypeVO(CfgFieldDO fieldDO) {
        GetByPageAndComponentTypeVO vo = new GetByPageAndComponentTypeVO();
        vo.setId(fieldDO.getId().toString());
        vo.setModelId(fieldDO.getModelId().toString());
        vo.setBizCode(fieldDO.getBizCode());
        vo.setBizName(fieldDO.getBizName());
        vo.setFieldType(fieldDO.getFieldType());
        vo.setComponentType(fieldDO.getComponentType());
        vo.setTitle(fieldDO.getTitle());
        vo.setDataBinding(fieldDO.getDataBinding());
        return vo;
    }

    public static SubmitRuleField DOToSubmitRuleField(CfgFieldDO fieldDO) {
        SubmitRuleField field = new SubmitRuleField();
        field.setId(fieldDO.getId().toString());
        field.setBizName(fieldDO.getBizName());
        field.setComponentType(fieldDO.getComponentType());
        field.setDataBinding(fieldDO.getDataBinding());
        return field;
    }

    public static com.bone.lowcode.infra.application.vo.simple.Field fieldDOToSimpleField(CfgFieldDO fieldDO) {
        com.bone.lowcode.infra.application.vo.simple.Field field = new com.bone.lowcode.infra.application.vo.simple.Field();
        field.setId(fieldDO.getId().toString());
        field.setBizCode(fieldDO.getBizCode());
        field.setBizName(fieldDO.getBizName());
        field.setDataBinding(fieldDO.getDataBinding());
        field.setComponentType(fieldDO.getComponentType());
        return field;
    }
}
