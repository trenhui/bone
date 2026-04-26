package com.bone.lowcode.infra.application.service;

import com.alibaba.fastjson.JSON;
import com.bone.core.exception.ServiceException;
import com.bone.lowcode.infra.application.convert.FieldConvert;
import com.bone.lowcode.infra.application.convert.TableConvert;
import com.bone.lowcode.infra.application.dto.processPage.UpdateProcessDetailPageDTO;
import com.bone.lowcode.infra.application.dto.processPage.UpdateProcessPageDTO;
import com.bone.lowcode.infra.application.vo.page.pageJson.*;
import com.bone.lowcode.infra.application.vo.processPage.*;
import com.bone.lowcode.infra.application.vo.table.FieldSimpleInfo;
import com.bone.lowcode.infra.application.vo.upload.UploadDataVO;
import com.bone.lowcode.infra.application.vo.upload.UploadImageVO;
import com.bone.lowcode.infra.domain.model.PageHeadField;
import com.bone.lowcode.infra.domain.model.SelectDatasource;
import com.bone.lowcode.infra.domain.model.TabCondition;
import com.bone.lowcode.infra.domain.model.TableSearchField;
import com.bone.lowcode.infra.domain.service.*;
import com.bone.lowcode.infra.domain.valueobject.*;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ProcessPageApplicationService {

    @Autowired
    private PageApplicationService pageApplicationService;
    @Autowired
    private UploadApplicationService uploadApplicationService;

    @Autowired
    private ProcessPageService processPageService;

    @Autowired
    private FieldService fieldService;

    @Autowired
    private FieldsetService fieldsetService;

    @Autowired
    private ModelService modelService;

    @Autowired
    private TableService tableService;

    @Autowired
    private EventTriggerService triggerService;

    @Autowired
    private EventService eventService;

    @Autowired
    private PageService pageService;

    @Autowired
    private ProcessDetailPageService detailPageService;


    public ProcessListPageVO getProcessListPage(String code, String bizIdentityCode) {
        ProcessListPageDO pageDO = processPageService.getProcessListPage(code, bizIdentityCode);
        if (pageDO == null)
            throw new ServiceException(500, "目标页面不存在,参数->code:" + code + ",bizIdentityCode:" + bizIdentityCode);

        ProcessListPageVO vo = new ProcessListPageVO();
        List<String> modelNameList = new ArrayList<>();
        vo.setModelNameList(modelNameList);

        ProcessPageBaseInfo baseInfo = new ProcessPageBaseInfo();
        baseInfo.setId(pageDO.getId().toString());
        baseInfo.setCode(pageDO.getCode());
        baseInfo.setName(pageDO.getName());
        baseInfo.setDescription(pageDO.getDescription());
        vo.setPageBaseInfo(baseInfo);

        ProcessPageHead pageHead = new ProcessPageHead();
        pageHead.setEnablePageHead(pageDO.getEnablePageHead());
        List<PageHeadField> headFieldList = pageDO.getPageHeadFields();
        if (pageDO.getEnablePageHead() == EnableStatusEnum.ENABLE.getCode() && !CollectionUtils.isEmpty(headFieldList)) {
            List<PageHeadFieldVO> pageHeadFieldVOList = getPageHeadFieldVOList(headFieldList);
            pageHead.setPageHeadField(pageHeadFieldVOList);
        }
        if (pageDO.getPageHeadModelId() != null) {
            Long modelId = pageDO.getPageHeadModelId();
            pageHead.setPageHeadModelId(modelId.toString());
            CfgModelDO modelDO = modelService.getDOById(modelId);
            pageHead.setModelCode(modelDO.getCode());
            modelNameList.add(modelDO.getName());
        }
        vo.setPageHead(pageHead);

        List<Object> pageBody = new ArrayList<>();
        if (pageDO.getBodyType() == PresentationFormatEnum.FIELDSET.getCode()) {
            List<CfgFieldsetDO> fieldsetDOList = fieldsetService.getDOByPageId(pageDO.getId());

            for (CfgFieldsetDO fieldsetDO : fieldsetDOList) {
                Long modelId = fieldsetDO.getModelId();
                CfgModelDO modelDO = modelService.getDOById(modelId);
                if (modelDO.getStatus() == StatusEnum.NO.getCode()) continue;

                modelNameList.add(modelDO.getName());
                List<CfgFieldDO> fieldDOList = fieldService.getDOListByModelId(modelId);
                if (CollectionUtils.isEmpty(fieldDOList)) continue;

                List<Field> fieldList = FieldConvert.fieldDOListToFieldList(fieldDOList);

                FieldSet fieldSet = FieldConvert.getFieldSet(fieldsetDO);
                fieldSet.setBody(fieldList);
                pageBody.add(fieldSet);
            }
        } else if (pageDO.getBodyType() == PresentationFormatEnum.TABLE.getCode()) {
            List<CfgTableDO> tableDOList = tableService.getDOListByPageId(pageDO.getId());

            for (CfgTableDO tableDO : tableDOList) {
                List<Long> modelIdList = tableDO.getModelIds();
                List<CfgModelDO> modelDOList = modelService.getDOListByIdList(modelIdList);
                modelDOList = modelDOList.stream().filter(i -> i.getStatus() == StatusEnum.YES.getCode()).toList();
                modelIdList = modelDOList.stream().map(CfgModelDO::getId).toList();
                if (CollectionUtils.isEmpty(modelIdList)) continue;

                modelNameList.addAll(modelDOList.stream().map(CfgModelDO::getName).toList());
                vo.setTableId(tableDO.getId().toString());

                List<CfgFieldDO> fieldDOList = fieldService.getDisplayedDOListByModelIdList(modelIdList);
                if (CollectionUtils.isEmpty(fieldDOList)) continue;

                List<Field> fieldList = FieldConvert.fieldDOListToFieldList(fieldDOList);
                fieldList.sort((o1, o2) -> {
                    if (o1.getSequence() != null && o2.getSequence() != null) {
                        return o1.getSequence() - o2.getSequence();
                    } else if (o1.getSequence() != null) {
                        return 1;
                    } else if (o2.getSequence() != null) {
                        return -1;
                    } else {
                        return 0;
                    }
                });

                Table table = TableConvert.tableDOToTable(tableDO);
                table.setBody(fieldList);

                Map<Long, CfgModelDO> map = modelDOList.stream().collect(Collectors.toMap(CfgModelDO::getId, i -> i));
                List<String> modelCodeList = modelIdList.stream().map(key -> map.get(key).getCode()).toList();
                table.setModelCodeList(modelCodeList);

                List<TableSearchField> searchFields = tableDO.getSearchFields();
                if (!CollectionUtils.isEmpty(searchFields)) {
                    List<SearchFieldVO> voList = getSearchFieldList(searchFields);
                    table.setSearchFieldList(voList);
                }

                List<CfgEventTriggerDO> triggerDOList = triggerService.getDOListByTableId(tableDO.getId());
                List<EventTrigger> triggerList = triggerDOList.stream().map(triggerDO -> {
                    EventTrigger trigger = new EventTrigger();
                    trigger.setId(triggerDO.getId().toString());
                    trigger.setLabel(triggerDO.getLabel());
                    trigger.setStyle(triggerDO.getStyle());
                    trigger.setDisplayType(triggerDO.getDisplayType());
                    trigger.setOwner(triggerDO.getOwner());
                    CfgEventDO eventDO = eventService.getById(triggerDO.getEventId());
                    if (eventDO != null) {
                        trigger.setEventId(eventDO.getId().toString());
                        trigger.setEventName(eventDO.getName());
                        trigger.setEventCode(eventDO.getCode());
                        trigger.setPrepare(eventDO.getPrepare());
                    }
                    return trigger;
                }).toList();
                table.setEventTriggerList(triggerList);
                pageBody.add(table);
            }
        }
        vo.setPageBody(pageBody);

        List<Object> uploadComponentList = new ArrayList<>();
        if (ProcessListPageEnum.SIGN_DETAIL_LIST.getCode().equals(code)) {
            UploadImageVO imageVO = uploadApplicationService.getUploadImageByCode(UploadImageEnum.SIGN_DETAIL_UPLOAD_IMAGE.getCode(), bizIdentityCode);
            if (imageVO != null) {
                uploadComponentList.add(imageVO);
            }
        }

        vo.setUploadComponentList(uploadComponentList);

        vo.setEnableTab(pageDO.getEnableTab());
        if (EnableStatusEnum.ENABLE.getCode() == pageDO.getEnableTab()) {
            List<TabCondition> tabConditions = pageDO.getTabConditions();
            List<TabConditionVO> conditionVOList = tabConditions.stream().map(item -> {
                FieldSimpleInfo field = fieldService.getFieldSimpleInfoById(Long.parseLong(item.getFieldId()));
                return new TabConditionVO(item.getTitle(), field, item.getFieldValue());
            }).toList();
            vo.setTabConditionList(conditionVOList);
        }

        vo.setDataRange(pageDO.getDataRange());
        return vo;
    }

    private List<SearchFieldVO> getSearchFieldList(List<TableSearchField> searchFields) {
        List<SearchFieldVO> list = searchFields.stream().map(i -> {
            CfgFieldDO fieldDO = fieldService.getDOById(Long.parseLong(i.getFieldId()));
            SearchFieldVO vo = new SearchFieldVO();
            vo.setFieldId(fieldDO.getId().toString());
            vo.setBizCode(fieldDO.getBizCode());
            vo.setBizName(fieldDO.getBizName());
            vo.setSequence(i.getSequence());
            vo.setTitle(fieldDO.getTitle());
            vo.setComponentType(fieldDO.getComponentType());
            vo.setSelectType(fieldDO.getSelectType());
            vo.setFilterType(fieldDO.getFilterType());
//            if (StringUtils.hasText(fieldDO.getSelectDatasource())) {
//                vo.setSelectDatasource(fieldDO.getDatasource());
//            }
            SelectDatasource datasource = new SelectDatasource(fieldDO.getDatasourceType(), fieldDO.getDatasourceCode());
            vo.setSelectDatasource(datasource);
            vo.setSelectLevel(fieldDO.getSelectLevel());
            vo.setRequired(i.getRequired());
            vo.setSearchMode(i.getSearchMode());
            return vo;
        }).sorted(Comparator.comparingInt(SearchFieldVO::getSequence)).toList();
        return list;
    }

    private List<PageHeadFieldVO> getPageHeadFieldVOList(List<PageHeadField> headFieldList) {
        List<PageHeadFieldVO> list = headFieldList.stream().map(i -> {
            CfgFieldDO fieldDO = fieldService.getDOById(Long.parseLong(i.getFieldId()));
            PageHeadFieldVO vo = new PageHeadFieldVO();
            vo.setFieldId(fieldDO.getId().toString());
            vo.setBizCode(fieldDO.getBizCode());
            vo.setBizName(fieldDO.getBizName());
            vo.setSequence(i.getSequence());
            vo.setDataBinding(fieldDO.getDataBinding());
            vo.setComponentType(fieldDO.getComponentType());
            vo.setSelectDatasource(new SelectDatasource(fieldDO.getDatasourceType(), fieldDO.getDatasourceCode()));
            vo.setDateFormatType(fieldDO.getDateFormatType());
            return vo;
        }).sorted(Comparator.comparingInt(PageHeadFieldVO::getSequence)).toList();
        return list;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateProcessListPage(UpdateProcessPageDTO dto) {
        if (dto.getTableId() != null && (dto.getEnableSearch() != null || !CollectionUtils.isEmpty(dto.getSearchFieldList()))) {
            CfgTableDO tableDO = new CfgTableDO();
            tableDO.setId(dto.getTableId());
            if (dto.getEnableSearch() != null) tableDO.setEnableSearch(dto.getEnableSearch());
            if (dto.getSearchFieldList() != null) {
                tableDO.setSearchFields(dto.getSearchFieldList());
            }
            tableService.updateById(tableDO);
        }

        if (dto.getPageId() != null) {
            ProcessListPageDO listPageDO = new ProcessListPageDO();
            listPageDO.setId(dto.getPageId());
            listPageDO.setDescription(dto.getDesc());
            listPageDO.setEnableTab(dto.getEnableTab());
            listPageDO.setTabConditions(dto.getTabConditionList());
            listPageDO.setDataRange(dto.getDataRange());
            if (dto.getEnablePageHead() == EnableStatusEnum.ENABLE.getCode()) {
                ProcessListPageDO listPageDOOld = processPageService.getProcessListPageById(dto.getPageId());
                if (listPageDOOld == null || listPageDOOld.getPageHeadModelId() == null) {
                    throw new ServiceException(500, "当前列表页未绑定页头模型，请先绑定页头模型再开启页头");
                }
            }
            listPageDO.setEnablePageHead(dto.getEnablePageHead());
            listPageDO.setPageHeadFields(dto.getPageHeadFieldList());
            processPageService.updateProcessListPageAcceptNull(listPageDO);
        }

        return true;
    }

    public ProcessDetailPageVO getProcessDetailPage(String bizIdentityCode) {
        String code = BasicPageCodeEnum.NEW_SIGN.getCode();
        CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(code, bizIdentityCode);
        UploadDataVO uploadDataVO = uploadApplicationService.getUploadDataByCode(UploadDataEnum.NEW_SIGN_UPLOAD_PEOPLE.getCode(), bizIdentityCode);

        return new ProcessDetailPageVO(pageDO.getName(), List.of(uploadDataVO));
    }

    public NewPageVO getNewSignPage(String displayMode, String code, String bizIdentityCode) {
        return pageApplicationService.previewNew(displayMode, code, bizIdentityCode);
    }

    public FirstAuditDetailPageVO getFirstAuditDetailPage(String code, String bizIdentityCode) {
        ProcessDetailPageDO detailPage = detailPageService.getDOByCode(code, bizIdentityCode);
        if (detailPage == null) {
            throw new ServiceException(500, "未创建目标页面,参数->code:" + code + ",bizIdentityCode:" + bizIdentityCode);
        }

        FirstAuditDetailPageVO vo = new FirstAuditDetailPageVO();
        BeanUtils.copyProperties(detailPage, vo);
        vo.setId(detailPage.getId().toString());
        ProcessPageHead pageHead = new ProcessPageHead();
        pageHead.setEnablePageHead(detailPage.getEnablePageHead());

        if (detailPage.getPageHeadModelId() != null) {
            Long modelId = detailPage.getPageHeadModelId();
            pageHead.setPageHeadModelId(modelId.toString());
            CfgModelDO modelDO = modelService.getDOById(modelId);
            pageHead.setModelCode(modelDO.getCode());
            vo.setModelNameList(List.of(modelDO.getName()));
        }

        List<PageHeadField> headFieldList = detailPage.getPageHeadFields();
        if (!CollectionUtils.isEmpty(headFieldList) && detailPage.getEnablePageHead() == EnableStatusEnum.ENABLE.getCode()) {
            List<PageHeadFieldVO> headFieldVOS = getPageHeadFieldVOList(headFieldList);
            pageHead.setPageHeadField(headFieldVOS);
        }
        vo.setPageHead(pageHead);
        return vo;
    }

    public boolean updateFirstAuditDetailPage(UpdateProcessDetailPageDTO dto) {
        ProcessDetailPageDO page = new ProcessDetailPageDO();
        page.setId(dto.getId());
        page.setDescription(dto.getDescription());
        page.setEnablePageHead(dto.getEnablePageHead());
        page.setPageHeadFieldList(JSON.toJSONString(dto.getPageHeadFieldList()));
        page.setOpenDetail(dto.getOpenDetail());
        page.setSpecification(dto.getSpecification());
        page.setTip(dto.getTip());
        page.setImageQuality(dto.getImageQuality());
        page.setImageType(dto.getImageType());
        return detailPageService.update(page);
    }
}
