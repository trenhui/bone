package com.bone.lowcode.infra.application.service;

import com.alibaba.fastjson.JSON;
import com.bone.core.exception.ServiceException;
import com.bone.lowcode.infra.application.convert.*;
import com.bone.lowcode.infra.application.dto.page.UpdatePageDTO;
import com.bone.lowcode.infra.application.vo.Sequence;
import com.bone.lowcode.infra.application.vo.bizIdentity.BizIdentityVO;
import com.bone.lowcode.infra.application.vo.field.FieldRuleField;
import com.bone.lowcode.infra.application.vo.field.GroupAggregateField;
import com.bone.lowcode.infra.application.vo.fieldTableRule.FieldTableRuleVO;
import com.bone.lowcode.infra.application.vo.optionSet.FieldLinkedDisplayRuleVO;
import com.bone.lowcode.infra.application.vo.page.EditableColumnVO;
import com.bone.lowcode.infra.application.vo.page.PageHeadVO;
import com.bone.lowcode.infra.application.vo.page.pageJson.*;
import com.bone.lowcode.infra.application.vo.page.structure.Fieldset;
import com.bone.lowcode.infra.application.vo.page.structure.Model;
import com.bone.lowcode.infra.application.vo.page.structure.Page;
import com.bone.lowcode.infra.application.vo.table.AggregateRuleVO;
import com.bone.lowcode.infra.application.vo.table.FieldSimpleInfo;
import com.bone.lowcode.infra.application.vo.tableRule.CrossTableDataEditVO;
import com.bone.lowcode.infra.application.vo.tableRule.CrossTableDataVerifyRuleVO;
import com.bone.lowcode.infra.application.vo.tableRule.TableRowEditRuleVO;
import com.bone.lowcode.infra.application.vo.tableRule.TableRowVerifyRuleVO;
import com.bone.lowcode.infra.domain.model.*;
import com.bone.lowcode.infra.domain.service.*;
import com.bone.lowcode.infra.domain.util.SnowflakeIdUtil;
import com.bone.lowcode.infra.domain.valueobject.*;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Component
@Slf4j
public class PageApplicationService {

    @Autowired
    private TableApplicationService tableApplicationService;
    @Autowired
    private TableRuleApplicationService tableRuleApplicationService;
    @Autowired
    private PageService pageService;
    @Autowired
    private ReleasedPageService releasedPageService;
    @Autowired
    private FormService formService;
    @Autowired
    private BlockService blockService;
    @Autowired
    private BizIdentityService bizIdentityService;

    @Autowired
    private TableService tableService;

    @Autowired
    private ModelService modelService;

    @Autowired
    private FieldService fieldService;

    @Autowired
    private FieldsetService fieldsetService;

    @Autowired
    private VersionService versionService;

    @Autowired
    private FieldLinkageRuleService fieldLinkageRuleService;

    @Autowired
    private SubmitRuleService submitRuleService;

    @Autowired
    private EventTriggerService eventTriggerService;

    @Autowired
    private EventService eventService;

    @Autowired
    private TableDataGroupAggregateService tableDataGroupAggregateService;

    @Autowired
    private TableDataRelationService tableDataRelationService;

    @Autowired
    private TableDataRowVerifyService tableDataRowVerifyService;

    @Autowired
    private TableDataRowEditService tableDataRowEditService;

    @Autowired
    private TableDataCrossEditService crossTableDataEditService;

    @Autowired
    private TableDataCrossVerifyService crossTableDataVerifyService;

    @Autowired
    private FieldLinkedDisplayRuleService fieldLinkedDisplayRuleService;

    @Autowired
    private FieldTableRuleService fieldTableRuleService;

    @Autowired
    private ProcessDetailPageService detailPageService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Autowired
    @Qualifier("commonPool")
    private ThreadPoolExecutor commonPool;

    private static final String triggerOwnerMapKeyForm = "form_";
    private static final String triggerOwnerMapKeyBlock = "block_";
    private static final String triggerOwnerMapKeyTable = "table_";

    public boolean publishBasic() {
        long versionId = SnowflakeIdUtil.getId();

        List<String> basicPageCodeList = BasicPageCodeEnum.getAllBasicPageCode();
        List<CfgReleasedPageDO> list = new ArrayList<>();
        Date now = new Date();
        for (String pageCode : basicPageCodeList) {
            CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, null);
            if (pageDO == null) {
                throw new ServiceException(500, "目标模板页面不存在,pageCode:" + pageCode);
            }

            String voStr = getPageInfoStr(pageDO);
            CfgReleasedPageDO releasedPageDO = new CfgReleasedPageDO();
            releasedPageDO.setId(SnowflakeIdUtil.getId());
            releasedPageDO.setVersionId(versionId);
            releasedPageDO.setType(PageTypeEnum.TEMPLATE.getCode());
            releasedPageDO.setPageId(pageDO.getId());
            releasedPageDO.setPageCode(pageDO.getCode());
            releasedPageDO.setPageInfo(voStr);
            releasedPageDO.setDeleted(DeletedEnum.UNDELETED.getCode());
            releasedPageDO.setCreateTime(now);
            list.add(releasedPageDO);
        }

        // detailPage
        List<ProcessDetailPageDO> detailPageDOList = detailPageService.getBasePageList();
        List<CfgReleasedPageDO> list3 = detailPageDOList.stream().map(i -> {
            ProcessDetailPage detailPage = new ProcessDetailPage();
            detailPage.setDetailPageDO(i);
            Long modelId = i.getPageHeadModelId();
            if (modelId != null) {
                CfgModelDO modelDO = modelService.getDOById(modelId);
                if (modelDO != null) {
                    detailPage.setModelDOList(List.of(modelDO));
                    List<CfgFieldDO> fieldDOList = fieldService.getDOListByModelId(modelId);
                    detailPage.setFieldDOList(fieldDOList);
                }
            }

            CfgReleasedPageDO releasedPageDO = new CfgReleasedPageDO();
            releasedPageDO.setId(SnowflakeIdUtil.getId());
            releasedPageDO.setPageInfo(JSON.toJSONString(detailPage));
            releasedPageDO.setType(PageTypeEnum.TEMPLATE.getCode());
            releasedPageDO.setPageId(i.getId());
            releasedPageDO.setPageCode(i.getCode());
            releasedPageDO.setVersionId(versionId);
            releasedPageDO.setDeleted(DeletedEnum.UNDELETED.getCode());
            releasedPageDO.setCreateTime(now);
            return releasedPageDO;
        }).toList();
        list.addAll(list3);

        return Boolean.TRUE.equals(
                transactionTemplate.execute(status -> {
                    try {
                        versionService.saveVersionInfo(PageTypeEnum.TEMPLATE.getCode(), "发布基础页面", versionId, null);
                        return releasedPageService.batchSave(list);
                    } catch (Exception e) {
                        log.error("发布基础页面发生异常:", e);
                        status.setRollbackOnly();
                        return false;
                    }
                })
        );
    }

    public String getPageInfoStr(CfgPageDO pageDO) {
        BasicPageInfo pageInfo = new BasicPageInfo();
        pageInfo.setPageDO(pageDO);

        Long pageId = pageDO.getId();

        CfgFormDO formDO = formService.getDOByPageId(pageId);
        pageInfo.setFormDO(formDO);

        List<CfgBlockDO> blockDOList = blockService.getDOListByPageId(pageId);
        pageInfo.setBlockDOList(blockDOList);

        List<CfgFieldsetDO> fieldsetDOList = fieldsetService.getDOByPageId(pageId);
        pageInfo.setFieldsetDOList(fieldsetDOList);

        List<CfgTableDO> tableDOList = tableService.getDOListByPageId(pageId);
        pageInfo.setTableDOList(tableDOList);

        List<CfgModelDO> modelDOList = modelService.getDOListByPageId(pageId);
        pageInfo.setModelDOList(modelDOList);

        List<CfgFieldDO> fieldDOList = fieldService.getDOByPageId(pageId);
        pageInfo.setFieldDOList(fieldDOList);

        List<CfgEventTriggerDO> eventTriggerDOList = eventTriggerService.getDOListByPageId(pageId);
        pageInfo.setEventTriggerList(eventTriggerDOList);

        List<CfgSubmitRuleDO> submitRuleDOList = submitRuleService.getByPageId(pageId);
        pageInfo.setSubmitRuleList(submitRuleDOList);

        List<CfgFieldLinkageRuleDO> fieldRuleDOList = fieldLinkageRuleService.getRuleByPageId(pageId);
        pageInfo.setFieldLinkageRuleList(fieldRuleDOList);

        List<FieldLinkedDisplayRule> linkedDisplayRuleList = fieldLinkedDisplayRuleService.getByPageId(pageId);
        pageInfo.setFieldLinkedDisplayRuleList(linkedDisplayRuleList);

        List<CfgFieldTableRule> fieldTableRuleList = fieldTableRuleService.getByPageId(pageId);
        pageInfo.setFieldTableRuleList(fieldTableRuleList);

        List<CfgTableDataGroupAggregateDO> tableDataGroupDOList = tableDataGroupAggregateService.getDOListByPageId(pageId);
        pageInfo.setTableDataGroupAggregateDOList(tableDataGroupDOList);

        List<CfgTableDataRowVerifyDO> rowVerifyDOList = tableDataRowVerifyService.getDOListByPageId(pageId);
        pageInfo.setTableDataRowVerifyDOList(rowVerifyDOList);

        List<CfgTableDataRowEditDO> rowEditDOList = tableDataRowEditService.getDOListByPageId(pageId);
        pageInfo.setTableDataRowEditDOList(rowEditDOList);

        List<CfgTableDataRelationDO> tableDataRelationDOList = tableDataRelationService.getDOListByPageId(pageId);
        pageInfo.setTableDataRelationDOList(tableDataRelationDOList);

        List<CfgTableDataCrossEditDO> crossTableDataEditDOList = new ArrayList<>();
        List<CfgTableDataCrossVerifyDO> crossTableDataVerifyDOList = new ArrayList<>();
        for (CfgTableDataRelationDO relationDO : tableDataRelationDOList) {
            List<CfgTableDataCrossEditDO> crossDataEditDOList = crossTableDataEditService.getDOListByRelationId(relationDO.getId());
            crossTableDataEditDOList.addAll(crossDataEditDOList);

            List<CfgTableDataCrossVerifyDO> crossDataVerifyDOList = crossTableDataVerifyService.getDOListByRelationId(relationDO.getId());
            crossTableDataVerifyDOList.addAll(crossDataVerifyDOList);
        }
        pageInfo.setCrossTableDataEditDOList(crossTableDataEditDOList);
        pageInfo.setCrossTableDataVerifyDOList(crossTableDataVerifyDOList);

        String pageInfoStr = JSON.toJSONString(pageInfo);
        return pageInfoStr;
    }


    private Long copyModelAndField(Long modelId, Map<Long, CfgModelDO> modelDOMap,
                                   Map<Long, List<CfgFieldDO>> fieldDOListMapByModel, Long pageId) {
        CfgModelDO modelDO = modelDOMap.get(modelId);
        List<CfgFieldDO> fieldDOList = fieldDOListMapByModel.get(modelDO.getId());

        modelDO.setId(SnowflakeIdUtil.getId());
        modelDO.setFromMetadata(StatusEnum.YES.getCode());
        modelDO.setPageId(pageId);
        modelDO.setCreateBy(null);
        modelDO.setCreateTime(new Date());
        modelDO.setUpdateBy(null);
        modelDO.setUpdateTime(null);

        if (fieldDOList == null) return modelDO.getId();

        for (CfgFieldDO fieldDO : fieldDOList) {
            fieldDO.setId(SnowflakeIdUtil.getId());
            fieldDO.setPageId(pageId);
            fieldDO.setModelId(modelDO.getId());
            fieldDO.setCreateBy(null);
            fieldDO.setCreateTime(new Date());
            fieldDO.setUpdateBy(null);
            fieldDO.setUpdateTime(null);
        }
        return modelDO.getId();
    }

    private SysBizIdentityDO createIdentity(Byte bizType, String insuranceCompanyCode, String insuranceCompanyName,
                                            String insuranceCompanyBranchCode, String insuranceCompanyBranchName,
                                            String insuredCompanyCode, String insuredCompanyName, String policyNo) {
        SysBizIdentityDO identityDO = new SysBizIdentityDO();
        identityDO.setBizType(bizType);
        identityDO.setAppCode("tpa");
        identityDO.setStatus(StatusEnum.YES.getCode());
        identityDO.setCreateTime(new Date());
        if (bizType == IdentityTypeEnum.INSURANCE_COMPANY.getCode()) {
            if (!StringUtils.hasText(insuranceCompanyCode) || !StringUtils.hasText(insuranceCompanyName)) {
                throw new ServiceException(500, "保险公司code、保险公司name均不能为空");
            }

            identityDO.setCode(bizType + ":" + insuranceCompanyCode);
            identityDO.setName(insuranceCompanyName);
            identityDO.setOriginalCode(insuranceCompanyCode);
        } else if (bizType == IdentityTypeEnum.INSURANCE_COMPANY_BRANCH.getCode()) {
            if (!StringUtils.hasText(insuranceCompanyCode) || !StringUtils.hasText(insuranceCompanyName) ||
                    !StringUtils.hasText(insuranceCompanyBranchCode) || !StringUtils.hasText(insuranceCompanyBranchName)) {
                throw new ServiceException(500, "保险公司code、保险公司name、保险分公司code、保险分公司name均不能为空");
            }

            identityDO.setCode(bizType + ":" + insuranceCompanyCode + ":" + insuranceCompanyBranchCode);
            identityDO.setName(insuranceCompanyBranchName);
            identityDO.setParentCode(insuranceCompanyCode);
            identityDO.setParentName(insuranceCompanyName);
            identityDO.setOriginalCode(insuranceCompanyBranchCode);
        } else if (bizType == IdentityTypeEnum.INSURED_COMPANY.getCode()) {
            if (!StringUtils.hasText(insuredCompanyCode) || !StringUtils.hasText(insuredCompanyName)) {
                throw new ServiceException(500, "投保公司code、投保公司name均不能为空");
            }

            identityDO.setCode(bizType + ":" + insuredCompanyCode);
            identityDO.setName(insuredCompanyName);
            identityDO.setOriginalCode(insuredCompanyCode);
        } else if (bizType == IdentityTypeEnum.POLICY.getCode()) {
            if (!StringUtils.hasText(insuranceCompanyCode) || !StringUtils.hasText(insuranceCompanyName) ||
                    !StringUtils.hasText(insuranceCompanyBranchCode) || !StringUtils.hasText(insuranceCompanyBranchName) ||
                    !StringUtils.hasText(policyNo)) {
                throw new ServiceException(500, "保险公司code、保险公司name、保险分公司code、保险分公司name、保单号均不能为空");
            }

            identityDO.setCode(bizType + ":" + insuranceCompanyCode + ":" + insuranceCompanyBranchCode + ":" + policyNo);
            identityDO.setName(policyNo);
            identityDO.setParentCode(insuranceCompanyBranchCode);
            identityDO.setParentName(insuranceCompanyBranchName);
            identityDO.setOriginalCode(policyNo);
        } else {
            throw new ServiceException(500, "不支持的主体类型:" + bizType);
        }
        return identityDO;
    }

    @Transactional(rollbackFor = Throwable.class)
    public BizIdentityVO createExclusivePage(Byte bizType, String insuranceCompanyCode, String insuranceCompanyName,
                                             String insuranceCompanyBranchCode, String insuranceCompanyBranchName,
                                             String insuredCompanyCode, String insuredCompanyName, String policyNo) {
        long createBy = System.currentTimeMillis();
        SysBizIdentityDO identity = createIdentity(bizType, insuranceCompanyCode, insuranceCompanyName,
                insuranceCompanyBranchCode, insuranceCompanyBranchName, insuredCompanyCode, insuredCompanyName, policyNo);
        String bizIdentityCode = identity.getCode();

        CfgVersionDO versionDO = versionService.getLastByType(PageTypeEnum.TEMPLATE.getCode(), null);
        if (versionDO == null) {
            throw new ServiceException(500, "请先发布基础页面再创建专属页面");
        }

        List<CfgPageDO> pageDOS = pageService.getDOListByIdentityCode(bizIdentityCode);
        if (!CollectionUtils.isEmpty(pageDOS)) {
            throw new ServiceException(500, "目标专属页面已创建,bizIdentityCode:" + bizIdentityCode);
        }

        SysBizIdentityDO bizIdentityDO = bizIdentityService.getDOByCode(bizIdentityCode);
        if (bizIdentityDO != null) {
            throw new ServiceException(500, "已经存在主体,bizIdentityCode:" + bizIdentityCode);
        }

        bizIdentityService.insert(identity);

        Long versionId = versionDO.getId();
        //全配置页面
        List<CfgPageDO> pageDOList = new ArrayList<>();
        List<CfgFormDO> formDOList = new ArrayList<>();
        List<CfgBlockDO> blockDOList = new ArrayList<>();
        List<CfgFieldsetDO> fieldsetDOList = new ArrayList<>();
        List<CfgTableDO> tableDOList = new ArrayList<>();
        List<CfgModelDO> modelDOList = new ArrayList<>();
        List<CfgFieldDO> fieldDOList = new ArrayList<>();
        List<CfgFieldLinkageRuleDO> fieldLinkageRuleDOList = new ArrayList<>();
        List<FieldLinkedDisplayRule> fieldLinkedDisplayRuleList = new ArrayList<>();
        List<CfgFieldTableRule> fieldTableRuleList = new ArrayList<>();
        List<CfgSubmitRuleDO> submitRuleDOList = new ArrayList<>();
        List<CfgEventTriggerDO> eventTriggerDOList = new ArrayList<>();
        List<CfgTableDataGroupAggregateDO> tableDataGroupAggregateDOList = new ArrayList<>();
        List<CfgTableDataRowVerifyDO> tableDataRowVerifyDOList = new ArrayList<>();
        List<CfgTableDataRowEditDO> tableDataRowEditDOList = new ArrayList<>();
        List<CfgTableDataRelationDO> tableDataRelationDOList = new ArrayList<>();
        List<CfgTableDataCrossEditDO> tableDataCrossEditDOList = new ArrayList<>();
        List<CfgTableDataCrossVerifyDO> tableDataCrossVerifyDOList = new ArrayList<>();

        List<String> pageCodeList = BasicPageCodeEnum.getAllBasicPageCode();
        for (String pageCode : pageCodeList) {
            getNewData(versionId, pageCode, bizIdentityCode, pageDOList, formDOList, blockDOList, fieldsetDOList, tableDOList,
                    modelDOList, fieldDOList, fieldLinkageRuleDOList, fieldLinkedDisplayRuleList, fieldTableRuleList,
                    submitRuleDOList, eventTriggerDOList, tableDataGroupAggregateDOList, tableDataRowVerifyDOList,
                    tableDataRowEditDOList, tableDataRelationDOList, tableDataCrossEditDOList, tableDataCrossVerifyDOList);
        }

        //初审详情页
        List<ProcessDetailPageDO> detailPageDOList = getNewDetailPage(bizIdentityCode, versionId, modelDOList, fieldDOList);

        log.info("准备批量插入...");
        pageService.batchSave(pageDOList);
        formService.batchSave(formDOList);
        blockService.batchSave(blockDOList);
        fieldsetService.batchSave(fieldsetDOList);
        tableService.batchSave(tableDOList);
        modelService.batchSave(modelDOList);
        fieldService.batchSave(fieldDOList);
        if (!CollectionUtils.isEmpty(fieldLinkageRuleDOList)) {
            fieldLinkageRuleService.batchSave(fieldLinkageRuleDOList);
        }

        if (!CollectionUtils.isEmpty(fieldLinkedDisplayRuleList)) {
            fieldLinkedDisplayRuleService.batchSave(fieldLinkedDisplayRuleList);
        }

        if (!CollectionUtils.isEmpty(fieldTableRuleList)) {
            fieldTableRuleService.batchSave(fieldTableRuleList);
        }

        if (!CollectionUtils.isEmpty(submitRuleDOList)) {
            submitRuleService.batchSave(submitRuleDOList);
        }

        if (!CollectionUtils.isEmpty(eventTriggerDOList)) {
            eventTriggerService.batchSave(eventTriggerDOList);
        }

        if (!CollectionUtils.isEmpty(tableDataGroupAggregateDOList)) {
            tableDataGroupAggregateService.batchSave(tableDataGroupAggregateDOList);
        }

        if (!CollectionUtils.isEmpty(tableDataRowVerifyDOList)) {
            tableDataRowVerifyService.batchSave(tableDataRowVerifyDOList);
        }

        if (!CollectionUtils.isEmpty(tableDataRowEditDOList)) {
            tableDataRowEditService.batchSave(tableDataRowEditDOList);
        }

        if (!CollectionUtils.isEmpty(tableDataRelationDOList)) {
            tableDataRelationService.batchSave(tableDataRelationDOList);
        }

        if (!CollectionUtils.isEmpty(tableDataCrossEditDOList)) {
            crossTableDataEditService.batchSave(tableDataCrossEditDOList);
        }

        if (!CollectionUtils.isEmpty(tableDataCrossVerifyDOList)) {
            crossTableDataVerifyService.batchSave(tableDataCrossVerifyDOList);
        }

        if (!CollectionUtils.isEmpty(detailPageDOList)) {
            detailPageService.batchSave(detailPageDOList);
        }

        double timeCost = (System.currentTimeMillis() - createBy) / 1000.0;
        log.info("创建专属页面花费的时间:{}s, bizIdentityCode:{}", timeCost, bizIdentityCode);
        BizIdentityVO vo = new BizIdentityVO(bizType, identity.getName(), identity.getCode(), identity.getAppCode());
        if (identity.getBizType() == IdentityTypeEnum.POLICY.getCode()) {
            vo.setBizName(identity.getParentName() + "(" + identity.getName() + ")");
        }
        return vo;
    }

    private List<ProcessDetailPageDO> getNewDetailPage(String bizIdentityCode, Long versionId,
                                                       List<CfgModelDO> modelDOList, List<CfgFieldDO> fieldDOList) {
        String detailPageCode = "firstAuditDetail";
        ProcessDetailPageDO detailPage = detailPageService.getDOByCode(detailPageCode, bizIdentityCode);
        if (detailPage != null) {
            throw new ServiceException(500, "目标初审详情页已经创建");
        }

        CfgReleasedPageDO releasedPageDO = releasedPageService.getDOByVersion(versionId, detailPageCode);
        if (releasedPageDO == null || !StringUtils.hasText(releasedPageDO.getPageInfo())) {
            throw new ServiceException(500, "初审详情页模板数据不存在");
        }

        ProcessDetailPage processDetailPage = JSON.parseObject(releasedPageDO.getPageInfo(), ProcessDetailPage.class);
        ProcessDetailPageDO detailPageDO = processDetailPage.getDetailPageDO();
        List<CfgModelDO> modelList = processDetailPage.getModelDOList();
        List<CfgFieldDO> fieldList = processDetailPage.getFieldDOList();
        modelDOList.addAll(modelList);
        fieldDOList.addAll(fieldList);

        Map<Long, CfgModelDO> modelMap = modelList.stream().collect(Collectors.toMap(CfgModelDO::getId, i -> i));
        Map<Long, List<CfgFieldDO>> modelFieldMap = fieldList.stream().collect(Collectors.groupingBy(CfgFieldDO::getModelId));
        Map<String, CfgFieldDO> fieldMap = fieldList.stream().collect(Collectors.toMap(fieldDO -> fieldDO.getId().toString(), i -> i));

        detailPageDO.setId(SnowflakeIdUtil.getId());
        detailPageDO.setType(PageTypeEnum.BIZ_IDENTITY.getCode());
        detailPageDO.setBizIdentityCode(bizIdentityCode);
        detailPageDO.setCreateBy(null);
        detailPageDO.setCreateTime(new Date());
        detailPageDO.setUpdateBy(null);
        detailPageDO.setUpdateTime(null);
        if (detailPageDO.getPageHeadModelId() != null) {
            Long newModelId = copyModelAndField(detailPageDO.getPageHeadModelId(), modelMap, modelFieldMap, detailPageDO.getId());
            detailPageDO.setPageHeadModelId(newModelId);
        }
        if (!CollectionUtils.isEmpty(detailPageDO.getPageHeadFields())) {
            List<PageHeadField> pageHeadFieldList = detailPageDO.getPageHeadFields();
            pageHeadFieldList.forEach(pageHeadField -> {
                String oldFieldId = pageHeadField.getFieldId();
                if (fieldMap.containsKey(oldFieldId)) {
                    String newFieldId = fieldMap.get(oldFieldId).getId().toString();
                    pageHeadField.setFieldId(newFieldId);
                }
            });
            detailPageDO.setPageHeadFields(pageHeadFieldList);
        }

        return List.of(detailPageDO);
    }

    public void getNewData(Long versionId, String pageCode, String bizIdentityCode, List<CfgPageDO> allPageDOList,
                           List<CfgFormDO> allFormDOList, List<CfgBlockDO> allBlockDOList, List<CfgFieldsetDO> allFieldsetDOList,
                           List<CfgTableDO> allTableDOList, List<CfgModelDO> allModelDOList, List<CfgFieldDO> allFieldDOList,
                           List<CfgFieldLinkageRuleDO> allFieldRuleDOList, List<FieldLinkedDisplayRule> allFieldLinkedDisplayRuleList,
                           List<CfgFieldTableRule> allFieldTableRuleList, List<CfgSubmitRuleDO> allSubmitRuleDOList,
                           List<CfgEventTriggerDO> allTriggerDOList, List<CfgTableDataGroupAggregateDO> allGroupAggregateDOList,
                           List<CfgTableDataRowVerifyDO> allRowVerifyDOList, List<CfgTableDataRowEditDO> allRowEditDOList,
                           List<CfgTableDataRelationDO> allTableRelationDOList, List<CfgTableDataCrossEditDO> allCrossEditDOList,
                           List<CfgTableDataCrossVerifyDO> allCrossVerifyDOList) {
        CfgPageDO exclusivePageDO = pageService.getByPageCodeAndIdentityCode(pageCode, bizIdentityCode);
        if (exclusivePageDO != null) {
            throw new ServiceException(500, "目标页面已创建");
        }

        CfgReleasedPageDO releasedPageDO = releasedPageService.getDOByVersion(versionId, pageCode);
        if (releasedPageDO == null || !StringUtils.hasText(releasedPageDO.getPageInfo())) {
            throw new ServiceException(500, "目标页面模板数据不存在,code:" + pageCode);
        }
        BasicPageInfo pageInfo = JSON.parseObject(releasedPageDO.getPageInfo(), BasicPageInfo.class);

        CfgPageDO pageDO = pageInfo.getPageDO();
        pageDO.setId(SnowflakeIdUtil.getId());
        pageDO.setBizIdentityCode(bizIdentityCode);
        pageDO.setType(PageTypeEnum.BIZ_IDENTITY.getCode());
        pageDO.setStatus(StatusEnum.YES.getCode());
        pageDO.setCreateBy(null);
        pageDO.setCreateTime(new Date());
        pageDO.setUpdateBy(null);
        pageDO.setUpdateTime(null);
        allPageDOList.add(pageDO);

        Long pageId = pageDO.getId();

        CfgFormDO formDO = pageInfo.getFormDO();
        Long oldFormId = formDO.getId();
        formDO.setId(SnowflakeIdUtil.getId());
        formDO.setPageId(pageId);
        formDO.setCreateBy(null);
        formDO.setCreateTime(new Date());
        formDO.setUpdateBy(null);
        formDO.setUpdateTime(null);
        allFormDOList.add(formDO);

        List<CfgBlockDO> blockDOList = pageInfo.getBlockDOList();
        blockDOList = blockDOList.stream().filter(i -> i.getFormId() != null && Objects.equals(i.getFormId(), oldFormId)).toList();
        List<Long> oldBlockIdList = blockDOList.stream().map(CfgBlockDO::getId).toList();
        Map<Long, CfgBlockDO> blockDOMap = blockDOList.stream().collect(Collectors.toMap(CfgBlockDO::getId, i -> i));
        for (CfgBlockDO blockDO : blockDOList) {
            blockDO.setId(SnowflakeIdUtil.getId());
            blockDO.setPageId(pageId);
            blockDO.setFormId(formDO.getId());
            blockDO.setCreateBy(null);
            blockDO.setCreateTime(new Date());
            blockDO.setUpdateBy(null);
            blockDO.setUpdateTime(null);
        }
        allBlockDOList.addAll(blockDOList);

        List<CfgModelDO> modelDOList = pageInfo.getModelDOList();
        Map<Long, CfgModelDO> modelDOMap = modelDOList.stream().collect(Collectors.toMap(CfgModelDO::getId, i -> i));

        List<CfgFieldDO> fieldDOList = pageInfo.getFieldDOList();
        HashMap<Long, List<CfgFieldDO>> fieldDOListMap = fieldDOList.stream().collect(Collectors.groupingBy(CfgFieldDO::getModelId, HashMap::new, Collectors.toList()));
        Map<Long, CfgFieldDO> fieldDOMap = fieldDOList.stream().collect(Collectors.toMap(CfgFieldDO::getId, i -> i));

        List<CfgTableDO> tableDOList = pageInfo.getTableDOList();
        tableDOList = tableDOList.stream().filter(i -> i.getBlockId() != null && oldBlockIdList.contains(i.getBlockId())).toList();
        Map<Long, CfgTableDO> tableDOMap = tableDOList.stream().collect(Collectors.toMap(CfgTableDO::getId, i -> i));
        for (CfgTableDO tableDO : tableDOList) {
            getNewTable(tableDO, pageId, blockDOMap, modelDOMap, fieldDOListMap, fieldDOMap);
        }
        for (CfgTableDO tableDO : tableDOList) {
            if (tableDO.getParentTableId() != null) {
                CfgTableDO tableDO1 = tableDOMap.get(tableDO.getParentTableId());
                Long newParentTableId = tableDO1.getId();
                tableDO.setParentTableId(newParentTableId);
            }
        }
        allTableDOList.addAll(tableDOList);

        List<CfgFieldsetDO> fieldsetDOList = pageInfo.getFieldsetDOList();
        fieldsetDOList = fieldsetDOList.stream().filter(i -> i.getBlockId() != null && oldBlockIdList.contains(i.getBlockId())).toList();
        for (CfgFieldsetDO fieldsetDO : fieldsetDOList) {
            getNewFieldSet(fieldsetDO, pageId, blockDOMap, modelDOMap, fieldDOListMap);
        }
        allFieldsetDOList.addAll(fieldsetDOList);

        List<CfgFieldLinkageRuleDO> fieldRuleDOList = pageInfo.getFieldLinkageRuleList();
        for (CfgFieldLinkageRuleDO ruleDO : fieldRuleDOList) {
            createNewFieldLinkageRuleDO(pageDO.getId(), fieldDOMap, ruleDO);
        }
        allFieldRuleDOList.addAll(fieldRuleDOList);

        List<FieldLinkedDisplayRule> fieldLinkedDisplayRuleList = pageInfo.getFieldLinkedDisplayRuleList();
        for (FieldLinkedDisplayRule displayRule : fieldLinkedDisplayRuleList) {
            createNewFieldLinkedDisplayRuleDO(pageId, fieldDOMap, displayRule);
        }
        allFieldLinkedDisplayRuleList.addAll(fieldLinkedDisplayRuleList);

        List<CfgFieldTableRule> fieldTableRuleList = pageInfo.getFieldTableRuleList();
        for (CfgFieldTableRule rule : fieldTableRuleList) {
            createNewFieldTableRuleDO(pageId, fieldDOMap, tableDOMap, rule);
        }
        allFieldTableRuleList.addAll(fieldTableRuleList);

        List<CfgSubmitRuleDO> submitRuleDOList = pageInfo.getSubmitRuleList();
        for (CfgSubmitRuleDO submitRuleDO : submitRuleDOList) {
            createNewSubmitRuleDO(pageDO.getId(), fieldDOMap, submitRuleDO);
        }
        allSubmitRuleDOList.addAll(submitRuleDOList);

        List<CfgEventTriggerDO> eventTriggerList = pageInfo.getEventTriggerList();
        createNewEventTriggerList(pageId, formDO.getId(), blockDOMap, tableDOMap, eventTriggerList);
        allTriggerDOList.addAll(eventTriggerList);

        List<CfgTableDataGroupAggregateDO> tableDataGroupAggregateDOList = pageInfo.getTableDataGroupAggregateDOList();
        createNewTableDataGroupAggregateDOList(pageId, fieldDOMap, tableDOMap, tableDataGroupAggregateDOList);
        allGroupAggregateDOList.addAll(tableDataGroupAggregateDOList);

        List<CfgTableDataRowVerifyDO> tableDataRowVerifyDOList = pageInfo.getTableDataRowVerifyDOList();
        createNewTableDataRowVerifyDOList(pageId, fieldDOMap, tableDOMap, tableDataRowVerifyDOList);
        allRowVerifyDOList.addAll(tableDataRowVerifyDOList);

        List<CfgTableDataRowEditDO> tableDataRowEditDOList = pageInfo.getTableDataRowEditDOList();
        createNewTableDataRowEditDOList(pageId, fieldDOMap, tableDOMap, tableDataRowEditDOList);
        allRowEditDOList.addAll(tableDataRowEditDOList);

        List<CfgTableDataRelationDO> tableDataRelationDOList = pageInfo.getTableDataRelationDOList();
        Map<Long, CfgTableDataRelationDO> tableDataRelationMap = tableDataRelationDOList.stream().collect(Collectors.toMap(CfgTableDataRelationDO::getId, i -> i));
        createNewTableDataRelationDOList(pageId, fieldDOMap, tableDOMap, tableDataRelationDOList);
        allTableRelationDOList.addAll(tableDataRelationDOList);

        List<CfgTableDataCrossEditDO> crossTableDataEditDOList = pageInfo.getCrossTableDataEditDOList();
        createNewCrossTableDataEditDOList(pageId, fieldDOMap, tableDataRelationMap, crossTableDataEditDOList);
        allCrossEditDOList.addAll(crossTableDataEditDOList);

        List<CfgTableDataCrossVerifyDO> crossTableDataVerifyDOList = pageInfo.getCrossTableDataVerifyDOList();
        createNewCrossTableDataVerifyDOList(pageId, fieldDOMap, tableDataRelationMap, crossTableDataVerifyDOList);
        allCrossVerifyDOList.addAll(crossTableDataVerifyDOList);

        List<CfgModelDO> newModelDOList = modelDOMap.entrySet().stream()
                .filter(entry -> !Objects.equals(entry.getKey(), entry.getValue().getId()))
                .map(Map.Entry::getValue)
                .toList();
        allModelDOList.addAll(newModelDOList);

        List<CfgFieldDO> newFieldDOList = fieldDOMap.entrySet().stream()
                .filter(entry -> !Objects.equals(entry.getKey(), entry.getValue().getId()))
                .map(Map.Entry::getValue)
                .toList();
        allFieldDOList.addAll(newFieldDOList);
    }

    private void getNewTable(CfgTableDO tableDO, Long pageId, Map<Long, CfgBlockDO> blockDOMap, Map<Long, CfgModelDO> modelDOMap,
                             HashMap<Long, List<CfgFieldDO>> fieldDOListMap, Map<Long, CfgFieldDO> fieldDOMap) {
        List<Long> oldModelIds = tableDO.getModelIds();

        tableDO.setId(SnowflakeIdUtil.getId());
        tableDO.setPageId(pageId);
        tableDO.setBlockId(blockDOMap.get(tableDO.getBlockId()).getId());
        tableDO.setModelIdList(null);
        if (StringUtils.hasText(tableDO.getOrderByList())) {
            List<TableFieldSortType> sortTypeList = tableDO.getSortTypeList();
            for (TableFieldSortType sortType : sortTypeList) {
                sortType.setId(fieldDOMap.get(Long.parseLong(sortType.getId())).getId().toString());
            }
            tableDO.setSortTypeList(sortTypeList);
        }

        if (StringUtils.hasText(tableDO.getEditableColumnList())) {
            List<EditableColumn> columnList = tableDO.getEditableColumns();
            for (EditableColumn column : columnList) {
                column.setFieldId(fieldDOMap.get(Long.parseLong(column.getFieldId())).getId().toString());
            }
            tableDO.setEditableColumns(columnList);
        }

        tableDO.setCreateBy(null);
        tableDO.setCreateTime(new Date());
        tableDO.setUpdateBy(null);
        tableDO.setUpdateTime(null);
        if (StringUtils.hasText(tableDO.getDataSummaryRule())) {
            List<DataSummaryRule> dataSummaryRuleList = tableDO.getDataSummaryRules();
            for (DataSummaryRule dataSummaryRule : dataSummaryRuleList) {
                dataSummaryRule.setFieldId(fieldDOMap.get(Long.parseLong(dataSummaryRule.getFieldId())).getId().toString());
            }
            tableDO.setDataSummaryRules(dataSummaryRuleList);
        }

        if (CollectionUtils.isEmpty(oldModelIds)) {
            return;
        }
        List<Long> newModelIds = new ArrayList<>();
        for (Long oldModelId : oldModelIds) {
            CfgModelDO modelDO = modelDOMap.get(oldModelId);
            if (modelDO == null) {
                continue;
            }

            modelDO.setId(SnowflakeIdUtil.getId());
            modelDO.setFromMetadata(StatusEnum.YES.getCode());
            modelDO.setPageId(pageId);
            modelDO.setCreateBy(null);
            modelDO.setCreateTime(new Date());
            modelDO.setUpdateBy(null);
            modelDO.setUpdateTime(null);
            newModelIds.add(modelDO.getId());

            List<CfgFieldDO> temFieldDOList = fieldDOListMap.get(oldModelId);
            if (!CollectionUtils.isEmpty(temFieldDOList)) {
                for (CfgFieldDO fieldDO : temFieldDOList) {
                    fieldDO.setId(SnowflakeIdUtil.getId());
                    fieldDO.setPageId(pageId);
                    fieldDO.setModelId(modelDO.getId());
                    fieldDO.setWidth(100);//表格字段宽度初始值设置为100
                    fieldDO.setCreateBy(null);
                    fieldDO.setCreateTime(new Date());
                    fieldDO.setUpdateBy(null);
                    fieldDO.setUpdateTime(null);
                }
            }
        }
        tableDO.setModelIdListFromList(newModelIds);
    }

    private void getNewFieldSet(CfgFieldsetDO fieldsetDO, Long pageId, Map<Long, CfgBlockDO> blockDOMap,
                                Map<Long, CfgModelDO> modelDOMap, HashMap<Long, List<CfgFieldDO>> fieldDOListMap) {
        Long oldModelId = fieldsetDO.getModelId();
        fieldsetDO.setId(SnowflakeIdUtil.getId());
        fieldsetDO.setPageId(pageId);
        fieldsetDO.setBlockId(blockDOMap.get(fieldsetDO.getBlockId()).getId());
        fieldsetDO.setModelId(null);
        fieldsetDO.setCreateBy(null);
        fieldsetDO.setCreateTime(new Date());
        fieldsetDO.setUpdateBy(null);
        fieldsetDO.setUpdateTime(null);

        CfgModelDO modelDO = modelDOMap.get(oldModelId);
        if (modelDO == null) {
            return;
        }

        modelDO.setId(SnowflakeIdUtil.getId());
        modelDO.setFromMetadata(StatusEnum.YES.getCode());
        modelDO.setPageId(pageId);
        modelDO.setCreateBy(null);
        modelDO.setCreateTime(new Date());
        modelDO.setUpdateBy(null);
        modelDO.setUpdateTime(null);
        fieldsetDO.setModelId(modelDO.getId());

        List<CfgFieldDO> temFieldDOList = fieldDOListMap.get(oldModelId);
        if (!CollectionUtils.isEmpty(temFieldDOList)) {
            for (CfgFieldDO fieldDO : temFieldDOList) {
                fieldDO.setId(SnowflakeIdUtil.getId());
                fieldDO.setPageId(pageId);
                fieldDO.setModelId(modelDO.getId());
                fieldDO.setCreateBy(null);
                fieldDO.setCreateTime(new Date());
                fieldDO.setUpdateBy(null);
                fieldDO.setUpdateTime(null);
            }
        }
    }

    private void createNewFieldTableRuleDO(Long pageId, Map<Long, CfgFieldDO> fieldDOMap, Map<Long, CfgTableDO> tableDOMap, CfgFieldTableRule rule) {
        Long oldId = rule.getId();
        rule.setId(SnowflakeIdUtil.getId());
        rule.setPageId(pageId);
        rule.setSourceId(oldId);
        rule.setFieldId(fieldDOMap.get(rule.getFieldId()).getId());
        if (ValueTypeEnum.DYNAMIC.getValue().equals(rule.getSourceValueType())) {
            Long sourceValue = Long.valueOf(rule.getSourceValue());
            String newSourceValue = fieldDOMap.get(sourceValue).getId().toString();
            rule.setSourceValue(newSourceValue);
        }
        rule.setTableId(tableDOMap.get(rule.getTableId()).getId());
        rule.setCreateBy(null);
        rule.setCreateTime(new Date());
        rule.setUpdateBy(null);
        rule.setUpdateTime(null);
    }

    private void createNewFieldLinkedDisplayRuleDO(Long pageId, Map<Long, CfgFieldDO> fieldDOMap, FieldLinkedDisplayRule displayRule) {
        Long oldId = displayRule.getId();
        displayRule.setId(SnowflakeIdUtil.getId());
        displayRule.setPageId(pageId);
        displayRule.setSourceId(oldId);
        displayRule.setSelectFieldId(fieldDOMap.get(displayRule.getSelectFieldId()).getId());
        List<LinkedDisplayRuleEntry> affectField = displayRule.getAffectField();
        for (LinkedDisplayRuleEntry entry : affectField) {
            Long newId = fieldDOMap.get(entry.getFieldId()).getId();
            entry.setFieldId(newId);
        }
        displayRule.setAffectField(affectField);
        displayRule.setCreateBy(null);
        displayRule.setCreateTime(new Date());
        displayRule.setUpdateBy(null);
        displayRule.setUpdateTime(null);
    }

    private void createNewCrossTableDataVerifyDOList(Long pageId, Map<Long, CfgFieldDO> fieldDOMap,
                                                     Map<Long, CfgTableDataRelationDO> tableDataRelationMap,
                                                     List<CfgTableDataCrossVerifyDO> crossTableDataVerifyDOList) {
        for (CfgTableDataCrossVerifyDO verifyDO : crossTableDataVerifyDOList) {
            Long oldId = verifyDO.getId();
            verifyDO.setId(SnowflakeIdUtil.getId());
            verifyDO.setPageId(pageId);
            verifyDO.setSourceId(oldId);
            verifyDO.setRelationId(tableDataRelationMap.get(verifyDO.getRelationId()).getId());

            List<Long> idList = new ArrayList<>();
            for (Long fieldId : verifyDO.getCurrentTableFieldIds()) {
                idList.add(fieldDOMap.get(fieldId).getId());
            }
            verifyDO.setCurrentTableFieldIds(idList);

            verifyDO.setTargetTableFieldId(fieldDOMap.get(verifyDO.getTargetTableFieldId()).getId());
            verifyDO.setCreateBy(null);
            verifyDO.setCreateTime(new Date());
            verifyDO.setUpdateBy(null);
            verifyDO.setUpdateTime(null);
        }
    }

    private void createNewCrossTableDataEditDOList(Long pageId, Map<Long, CfgFieldDO> fieldDOMap,
                                                   Map<Long, CfgTableDataRelationDO> tableDataRelationMap,
                                                   List<CfgTableDataCrossEditDO> crossTableDataEditDOList) {
        for (CfgTableDataCrossEditDO editDO : crossTableDataEditDOList) {
            Long oldId = editDO.getId();
            editDO.setId(SnowflakeIdUtil.getId());
            editDO.setPageId(pageId);
            editDO.setSourceId(oldId);
            editDO.setRelationId(tableDataRelationMap.get(editDO.getRelationId()).getId());

            List<Long> idList = new ArrayList<>();
            for (Long fieldId : editDO.getCurrentTableFieldIds()) {
                idList.add(fieldDOMap.get(fieldId).getId());
            }
            editDO.setCurrentTableFieldIds(idList);

            editDO.setTargetTableFieldId(fieldDOMap.get(editDO.getTargetTableFieldId()).getId());
            editDO.setCreateBy(null);
            editDO.setCreateTime(new Date());
            editDO.setUpdateBy(null);
            editDO.setUpdateTime(null);
        }
    }

    private void createNewTableDataRowEditDOList(Long pageId, Map<Long, CfgFieldDO> fieldDOMap,
                                                 Map<Long, CfgTableDO> tableDOMap, List<CfgTableDataRowEditDO> tableDataRowEditDOList) {
        for (CfgTableDataRowEditDO editDO : tableDataRowEditDOList) {
            Long oldId = editDO.getId();
            editDO.setId(SnowflakeIdUtil.getId());
            editDO.setTableId(tableDOMap.get(editDO.getTableId()).getId());
            editDO.setPageId(pageId);
            editDO.setSourceId(oldId);

            List<Long> idList = new ArrayList<>();
            for (Long fieldId : editDO.getSourceFieldIds()) {
                idList.add(fieldDOMap.get(fieldId).getId());
            }
            editDO.setSourceFieldIds(idList);

            editDO.setTargetFieldId(fieldDOMap.get(editDO.getTargetFieldId()).getId());
            editDO.setCreateBy(null);
            editDO.setCreateTime(new Date());
            editDO.setUpdateBy(null);
            editDO.setUpdateTime(null);
        }
    }

    private void createNewTableDataRowVerifyDOList(Long pageId, Map<Long, CfgFieldDO> fieldDOMap,
                                                   Map<Long, CfgTableDO> tableDOMap,
                                                   List<CfgTableDataRowVerifyDO> tableDataRowVerifyDOList) {
        for (CfgTableDataRowVerifyDO verifyDO : tableDataRowVerifyDOList) {
            Long oldId = verifyDO.getId();
            verifyDO.setId(SnowflakeIdUtil.getId());
            verifyDO.setTableId(tableDOMap.get(verifyDO.getTableId()).getId());
            verifyDO.setPageId(pageId);
            verifyDO.setSourceId(oldId);

            List<Long> idList = new ArrayList<>();
            for (Long fieldId : verifyDO.getFieldIds()) {
                idList.add(fieldDOMap.get(fieldId).getId());
            }
            verifyDO.setFieldIds(idList);

            if (ValueTypeEnum.DYNAMIC.getValue().equals(verifyDO.getValueType())) {
                Long fieldId = Long.parseLong(verifyDO.getValue());
                Long newFieldId = fieldDOMap.get(fieldId).getId();
                verifyDO.setValue(newFieldId.toString());
            }
            verifyDO.setCreateBy(null);
            verifyDO.setCreateTime(new Date());
            verifyDO.setUpdateBy(null);
            verifyDO.setUpdateTime(null);
        }
    }

    private void createNewTableDataRelationDOList(Long pageId, Map<Long, CfgFieldDO> fieldDOMap,
                                                  Map<Long, CfgTableDO> tableDOMap, List<CfgTableDataRelationDO> tableDataRelationDOList) {
        for (CfgTableDataRelationDO relationDO : tableDataRelationDOList) {
            relationDO.setId(SnowflakeIdUtil.getId());
            relationDO.setPageId(pageId);
            relationDO.setCurrentTableId(tableDOMap.get(relationDO.getCurrentTableId()).getId());
            relationDO.setCurrentRelationFieldId(fieldDOMap.get(relationDO.getCurrentRelationFieldId()).getId());
            relationDO.setTargetTableId(tableDOMap.get(relationDO.getTargetTableId()).getId());
            relationDO.setTargetRelationFieldId(fieldDOMap.get(relationDO.getTargetRelationFieldId()).getId());
        }
    }

    private void createNewEventTriggerList(Long pageId, Long newFormId, Map<Long, CfgBlockDO> blockDOMap,
                                           Map<Long, CfgTableDO> tableDOMap,
                                           List<CfgEventTriggerDO> eventTriggerList) {
        for (CfgEventTriggerDO triggerDO : eventTriggerList) {
            triggerDO.setId(SnowflakeIdUtil.getId());
            triggerDO.setPageId(pageId);
            if (triggerDO.getOwner() == TriggerOwnerEnum.FORM.getCode()) {
                triggerDO.setOwnerId(newFormId);
            } else if (triggerDO.getOwner() == TriggerOwnerEnum.BLOCK.getCode()) {
                CfgBlockDO blockDO = blockDOMap.get(triggerDO.getOwnerId());
                triggerDO.setOwnerId(blockDO.getId());
            } else if (triggerDO.getOwner() == TriggerOwnerEnum.TABLE_ROW.getCode() ||
                    triggerDO.getOwner() == TriggerOwnerEnum.TABLE_LEFT.getCode() ||
                    triggerDO.getOwner() == TriggerOwnerEnum.TABLE_RIGHT.getCode()) {
                CfgTableDO tableDO = tableDOMap.get(triggerDO.getOwnerId());
                triggerDO.setOwnerId(tableDO.getId());
            }
            triggerDO.setCreateBy(null);
            triggerDO.setCreateTime(new Date());
            triggerDO.setUpdateBy(null);
            triggerDO.setUpdateTime(null);
        }
    }

    private void createNewTableDataGroupAggregateDOList(Long pageId, Map<Long, CfgFieldDO> fieldDOMap,
                                                        Map<Long, CfgTableDO> tableDOMap,
                                                        List<CfgTableDataGroupAggregateDO> tableDataGroupAggregateDOList) {
        for (CfgTableDataGroupAggregateDO aggregateDO : tableDataGroupAggregateDOList) {
            Long oldId = aggregateDO.getId();
            aggregateDO.setId(SnowflakeIdUtil.getId());
            aggregateDO.setTableId(tableDOMap.get(aggregateDO.getTableId()).getId());
            aggregateDO.setPageId(pageId);
            aggregateDO.setSourceId(oldId);

            if (StringUtils.hasText(aggregateDO.getGroupFieldInfo())) {
                GroupAggregateField field = aggregateDO.getGroupField();
                field.setId(fieldDOMap.get(Long.parseLong(field.getId())).getId().toString());
                aggregateDO.setGroupField(field);
            }

            if (StringUtils.hasText(aggregateDO.getAggregateFieldInfo())) {
                List<GroupAggregateField> aggregateFieldList = aggregateDO.getAggregateField();
                for (GroupAggregateField field : aggregateFieldList) {
                    field.setId(fieldDOMap.get(Long.parseLong(field.getId())).getId().toString());
                }
                aggregateDO.setAggregateField(aggregateFieldList);
            }

            if (StringUtils.hasText(aggregateDO.getOtherFieldInfo())) {
                List<GroupAggregateField> otherFieldList = aggregateDO.getOtherField();
                for (GroupAggregateField field : otherFieldList) {
                    field.setId(fieldDOMap.get(Long.parseLong(field.getId())).getId().toString());
                }
                aggregateDO.setOtherField(otherFieldList);
            }
            aggregateDO.setCreateBy(null);
            aggregateDO.setCreateTime(new Date());
            aggregateDO.setUpdateBy(null);
            aggregateDO.setUpdateTime(null);
        }
    }

    private void createNewSubmitRuleDO(Long pageId, Map<Long, CfgFieldDO> fieldDOMap, CfgSubmitRuleDO submitRuleDO) {
        List<Long> fieldIds = submitRuleDO.getFieldIds();
        List<Long> newFieldIds = new ArrayList<>();
        for (Long fieldId : fieldIds) {
            newFieldIds.add(fieldDOMap.get(fieldId).getId());
        }
        submitRuleDO.setFieldIds(newFieldIds);
        if (ValueTypeEnum.DYNAMIC.getValue().equals(submitRuleDO.getValueType())) {
            Long fieldId = Long.valueOf(submitRuleDO.getValue());
            Long newFieldId = fieldDOMap.get(fieldId).getId();
            submitRuleDO.setValue(newFieldId.toString());
        }
        Long oldId = submitRuleDO.getId();

        submitRuleDO.setId(SnowflakeIdUtil.getId());
        submitRuleDO.setPageId(pageId);
        submitRuleDO.setSourceId(oldId);
        submitRuleDO.setCreateBy(null);
        submitRuleDO.setCreateTime(new Date());
        submitRuleDO.setUpdateBy(null);
        submitRuleDO.setUpdateTime(null);
    }

    private void createNewFieldLinkageRuleDO(Long pageId, Map<Long, CfgFieldDO> fieldDOMap, CfgFieldLinkageRuleDO ruleDO) {
        Long oldId = ruleDO.getId();
        ruleDO.setId(SnowflakeIdUtil.getId());
        ruleDO.setPageId(pageId);
        ruleDO.setSourceId(oldId);
        Long fieldId = ruleDO.getFieldId();
        Long newFieldId = fieldDOMap.get(fieldId).getId();
        ruleDO.setFieldId(newFieldId);

        if (ValueTypeEnum.DYNAMIC.getValue().equals(ruleDO.getSourceValueType())) {
            Long sourceValue = Long.valueOf(ruleDO.getSourceValue());
            String newSourceValue = fieldDOMap.get(sourceValue).getId().toString();
            ruleDO.setSourceValue(newSourceValue);
        }

        List<Long> targetFieldIdList = ruleDO.getTargetFieldIdList();
        List<Long> newTargetFieldIdList = new ArrayList<>();
        for (Long targetFieldId : targetFieldIdList) {
            newTargetFieldIdList.add(fieldDOMap.get(targetFieldId).getId());
        }
        ruleDO.setTargetFieldIdList(newTargetFieldIdList);

        if (ValueTypeEnum.DYNAMIC.getValue().equals(ruleDO.getTargetValueType())) {
            Long targetValue = Long.valueOf(ruleDO.getTargetValue());
            String newTargetValue = fieldDOMap.get(targetValue).getId().toString();
            ruleDO.setTargetValue(newTargetValue);
        }
        ruleDO.setCreateBy(null);
        ruleDO.setCreateTime(new Date());
        ruleDO.setUpdateBy(null);
        ruleDO.setUpdateTime(null);
    }

    public NewPageVO previewNew(String displayMode, String pageCode, String bizIdentityCode) {
        CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, bizIdentityCode);
        if (pageDO == null) {
            throw new ServiceException(500, "未找到目标页面,pageCode:" + pageCode + ", bizIdentityCode:" + bizIdentityCode);
        }
        SysBizIdentityDO bizIdentityDO = null;
        if (pageDO.getType() == PageTypeEnum.BIZ_IDENTITY.getCode() && StringUtils.hasText(bizIdentityCode)) {
            bizIdentityDO = bizIdentityService.getDOByCode(bizIdentityCode);
        }

        Long pageId = pageDO.getId();
        CfgFormDO formDO = formService.getDOByPageId(pageId);
        List<CfgBlockDO> blockDOList = blockService.getDOListByPageId(pageId);
        List<CfgFieldsetDO> fieldsetDOList = fieldsetService.getDOByPageId(pageId);
        List<CfgTableDO> tableDOList = tableService.getDOListByPageId(pageId);
        List<CfgModelDO> modelDOList = modelService.getDOListByPageId(pageId);
        List<CfgFieldDO> fieldDOList = fieldService.getDOByPageId(pageId);
        List<CfgSubmitRuleDO> submitRuleDOList = submitRuleService.getEnableByPageId(pageId);
        List<CfgFieldLinkageRuleDO> fieldLinkageRuleDOList = fieldLinkageRuleService.getEnableByPageId(pageId);
        List<CfgFieldTableRule> fieldTableRuleList = fieldTableRuleService.getEnableByPageId(pageId);
        List<FieldLinkedDisplayRule> linkedDisplayRuleList = fieldLinkedDisplayRuleService.getByPageId(pageId);
        List<CfgEventTriggerDO> eventTriggerDOList = eventTriggerService.getDOListByPageId(pageId);
        Set<Long> eventIdSet = eventTriggerDOList.stream().map(CfgEventTriggerDO::getEventId).collect(Collectors.toSet());
        List<CfgEventDO> eventDOList = !CollectionUtils.isEmpty(eventIdSet) ? eventService.getDOListByIdList(eventIdSet) : new ArrayList<>();
        NewPageVO pageVO = getPageVO(displayMode, bizIdentityDO, pageDO, formDO, blockDOList, fieldsetDOList, tableDOList,
                modelDOList, fieldDOList, eventTriggerDOList, eventDOList, submitRuleDOList, fieldLinkageRuleDOList,
                fieldTableRuleList, linkedDisplayRuleList);

        TableRules tableRules = getRestRule(pageCode, bizIdentityCode, PublishStatusEnum.EDIT.getCode());
        pageVO.setTableRules(tableRules);
        return pageVO;
    }

    private NewPageVO getPageVO(String displayMode, SysBizIdentityDO bizIdentityDO, CfgPageDO pageDO, CfgFormDO formDO,
                                List<CfgBlockDO> blockDOList, List<CfgFieldsetDO> fieldsetDOList, List<CfgTableDO> tableDOList,
                                List<CfgModelDO> modelDOList, List<CfgFieldDO> fieldDOList,
                                List<CfgEventTriggerDO> eventTriggerDOList, List<CfgEventDO> eventDOList,
                                List<CfgSubmitRuleDO> submitRuleDOList, List<CfgFieldLinkageRuleDO> fieldLinkageRuleDOList,
                                List<CfgFieldTableRule> fieldTableRuleList, List<FieldLinkedDisplayRule> linkedDisplayRuleList) {
        Map<Long, List<CfgFieldsetDO>> fieldsetGroupMap = fieldsetDOList.stream().collect(Collectors.groupingBy(CfgFieldsetDO::getBlockId));
        Map<Long, List<CfgTableDO>> tableGroupMap = tableDOList.stream().collect(Collectors.groupingBy(CfgTableDO::getBlockId));
        Map<Long, CfgTableDO> tableDOMap = tableDOList.stream().collect(Collectors.toMap(CfgTableDO::getId, i -> i));
        Map<Long, CfgModelDO> modelDOMap = modelDOList.stream().collect(Collectors.toMap(CfgModelDO::getId, i -> i));
        Map<Long, List<CfgFieldDO>> fieldGroupMap = fieldDOList.stream().collect(Collectors.groupingBy(CfgFieldDO::getModelId));
        Map<Long, CfgFieldDO> fieldDOMap = fieldDOList.stream().collect(Collectors.toMap(CfgFieldDO::getId, i -> i));
        Map<Long, CfgEventDO> eventDOMap = eventDOList.stream().collect(Collectors.toMap(CfgEventDO::getId, i -> i));
        HashMap<String, List<EventTrigger>> triggerGroupMap = getTriggerOwnerMapNew(eventTriggerDOList, eventDOMap);

        List<SubmitRule> submitRuleList = getSubmitRuleListNew(submitRuleDOList, fieldDOMap);
        List<FieldLinkageRule> fieldLinkageRuleList = getLinkageRuleNew(fieldLinkageRuleDOList, fieldDOMap);
        List<FieldTableRuleVO> fieldTableRuleVOList = getFieldTableRuleVOS(fieldTableRuleList, fieldDOMap, tableDOMap, modelDOMap);
        List<LinkedDisplayRuleVO> linkedDisplayRuleVOList = getLinkedDisplayRuleList(linkedDisplayRuleList, fieldDOMap);

        PageRules pageRules = new PageRules();
        pageRules.setSubmitRuleList(submitRuleList);
        pageRules.setFieldLinkageRuleList(fieldLinkageRuleList);
        pageRules.setFieldTableRuleVOList(fieldTableRuleVOList);
        pageRules.setLinkedDisplayRuleVOList(linkedDisplayRuleVOList);

        HashMap<String, LinkedList<Sequence>> blockBodyMap = new HashMap<>();
        for (CfgBlockDO blockDO : blockDOList) {
            Long blockId = blockDO.getId();
            if (!blockBodyMap.containsKey(blockId.toString())) {
                blockBodyMap.put(blockId.toString(), new LinkedList<>());
            }
            LinkedList<Sequence> blockItemList = blockBodyMap.get(blockId.toString());

            List<CfgFieldsetDO> temFieldsetDOList = fieldsetGroupMap.get(blockId);
            if (!CollectionUtils.isEmpty(temFieldsetDOList)) {
                for (CfgFieldsetDO fieldsetDO : temFieldsetDOList) {
                    Long modelId = fieldsetDO.getModelId();
                    CfgModelDO modelDO = modelDOMap.get(modelId);
                    if (modelDO == null || modelDO.getStatus() == StatusEnum.NO.getCode()) {
                        continue;
                    }

                    List<CfgFieldDO> temFieldDOList = fieldGroupMap.get(modelId);
                    FieldSet fieldSet = getFieldSetNew(fieldsetDO, temFieldDOList);
                    blockItemList.add(fieldSet);
                }
            }

            List<CfgTableDO> temTableDOList = tableGroupMap.get(blockId);
            if (!CollectionUtils.isEmpty(temTableDOList)) {
                for (CfgTableDO tableDO : temTableDOList) {
                    List<Long> modelIdList = tableDO.getModelIds();
                    List<CfgModelDO> temModelDOList = modelIdList.stream().map(modelDOMap::get).toList();
                    modelIdList = temModelDOList.stream().filter(i -> i.getStatus() == StatusEnum.YES.getCode()).map(CfgModelDO::getId).toList();
                    if (CollectionUtils.isEmpty(modelIdList)) {
                        continue;
                    }

                    List<CfgFieldDO> temFieldDOList = modelIdList.stream()
                            .map(fieldGroupMap::get)
                            .flatMap((Function<List<CfgFieldDO>, Stream<CfgFieldDO>>) Collection::stream)
                            .toList();
                    temFieldDOList = temFieldDOList.stream().filter(i -> i.getDisplayed() == DisplayEnum.DISPLAY.getCode()).toList();
                    List<String> modelCodeList = modelIdList.stream().map(i -> modelDOMap.get(i).getCode()).toList();
                    Table table = getTableNew(tableDO, temFieldDOList, fieldDOMap, modelCodeList);

                    String key = triggerOwnerMapKeyTable + table.getId();
                    if (triggerGroupMap.containsKey(key)) {
                        table.setEventTriggerList(triggerGroupMap.get(key));
                    }
                    blockItemList.add(table);
                }
            }

            blockItemList.sort((o1, o2) -> {
                if (o1.returnSequence() != null && o2.returnSequence() != null) {
                    return o1.returnSequence().compareTo(o2.returnSequence());
                } else {
                    return o1.returnSequence() != null ? 1 : -1;
                }
            });
        }

        List<Block> blockList = BlockConvert.DOListToBlockList(blockDOList);
        blockList = blockList.stream().sorted(Comparator.comparingInt(Block::getSequenceNumber))
                .filter(block -> {
                    boolean flag = blockBodyMap.containsKey(block.getId());
                    if (!flag) return false;
                    return blockBodyMap.get(block.getId()).size() > 0;
                })
                .toList();
        blockList.forEach(block -> {
            if (blockBodyMap.containsKey(block.getId())) {
                block.setBody(blockBodyMap.get(block.getId()));
            }
            String key = triggerOwnerMapKeyBlock + block.getId();
            if (triggerGroupMap.containsKey(key)) {
                block.setEventTriggerList(triggerGroupMap.get(key));
            }
        });

        Form form = new Form();
        form.setId(formDO.getId().toString());
        form.setType(PageItemTypeEnum.FORM.getName());
        String keyForm = triggerOwnerMapKeyForm + formDO.getId();
        if (triggerGroupMap.containsKey(keyForm)) {
            form.setEventTriggerList(triggerGroupMap.get(keyForm));
        }
        form.setBody(blockList);

        PageVO pageVO = PageConvert.pageDOToPageVO(pageDO, displayMode);
        if (bizIdentityDO != null) {
            BizIdentity bizIdentity = BizIdentityConvert.doToBizIdentity(bizIdentityDO);
            pageVO.setBizInfo(bizIdentity);
        }
        pageVO.setBody(List.of(form));
        return new NewPageVO(pageVO, pageRules);
    }

    private FieldSet getFieldSetNew(CfgFieldsetDO fieldsetDO, List<CfgFieldDO> fieldDOList) {
        List<Field> fieldList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(fieldDOList)) {
            fieldList = FieldConvert.fieldDOListToFieldList(fieldDOList);
        }
        FieldSet fieldSet = FieldConvert.getFieldSet(fieldsetDO);
        fieldList.sort(Comparator.comparing(Field::getSequence, Comparator.nullsLast(Comparator.naturalOrder())));
        fieldSet.setBody(fieldList);
        return fieldSet;
    }

    private Table getTableNew(CfgTableDO tableDO, List<CfgFieldDO> fieldDOList, Map<Long, CfgFieldDO> fieldDOMap,
                              List<String> modelCodeList) {
        List<Field> fieldList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(fieldDOList)) {
            fieldList = FieldConvert.fieldDOListToFieldList(fieldDOList);
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
        }

        Table table = TableConvert.tableDOToTable(tableDO);
        if (!CollectionUtils.isEmpty(tableDO.getEditableColumns())) {
            List<EditableColumnVO> editableColumnVOList = new ArrayList<>();
            List<EditableColumn> editableColumnList = tableDO.getEditableColumns();
            for (EditableColumn column : editableColumnList) {
                CfgFieldDO fieldDO = fieldDOMap.get(Long.parseLong(column.getFieldId()));
                if (fieldDO == null) continue;
                FieldSimpleInfo fieldInfo = new FieldSimpleInfo(fieldDO.getId().toString(), fieldDO.getBizCode(), fieldDO.getBizName(), fieldDO.getDataBinding());
                EditableColumnVO columnVO = new EditableColumnVO(fieldInfo, column.getSingleLineEditable(), column.getBatchEditable());
                editableColumnVOList.add(columnVO);
            }
            table.setEditableColumnList(editableColumnVOList);
        }
        table.setBody(fieldList);
        table.setModelCodeList(modelCodeList);
        return table;
    }

    private List<SubmitRule> getSubmitRuleListNew(List<CfgSubmitRuleDO> submitRuleDOList, Map<Long, CfgFieldDO> fieldDOMap) {
        List<SubmitRule> submitRuleList = new ArrayList<>();
        if (CollectionUtils.isEmpty(submitRuleDOList) || fieldDOMap == null) {
            return submitRuleList;
        }

        for (CfgSubmitRuleDO submitRuleDO : submitRuleDOList) {
            SubmitRule submitRule = SubmitRuleConvert.DOToSubmitRule(submitRuleDO);
            submitRuleList.add(submitRule);

            List<Long> fieldIdList = submitRuleDO.getFieldIds();
            List<CfgFieldDO> fieldDOList = fieldIdList.stream().map(fieldDOMap::get).toList();
            List<SubmitRuleField> fieldList = new ArrayList<>();
            for (CfgFieldDO fieldDO : fieldDOList) {
                SubmitRuleField field = FieldConvert.DOToSubmitRuleField(fieldDO);
                fieldList.add(field);
            }
            submitRule.setFieldList(fieldList);

            Object value = null;
            if (ValueTypeEnum.DYNAMIC.getValue().equals(submitRuleDO.getValueType())) {
                Long fieldId = Long.valueOf(submitRuleDO.getValue());
                CfgFieldDO fieldDO = fieldDOMap.get(fieldId);
                value = FieldConvert.DOToSubmitRuleField(fieldDO);
            } else if (ValueTypeEnum.FIXED.getValue().equals(submitRuleDO.getValueType())) {
                value = submitRuleDO.getValue();
            }
            submitRule.setValue(value);
        }
        return submitRuleList;
    }

    private List<FieldLinkageRule> getLinkageRuleNew(List<CfgFieldLinkageRuleDO> ruleDOList, Map<Long, CfgFieldDO> fieldDOMap) {
        if (CollectionUtils.isEmpty(ruleDOList) || fieldDOMap == null) {
            return new ArrayList<>();
        }

        List<FieldLinkageRule> re = new ArrayList<>();
        for (CfgFieldLinkageRuleDO ruleDO : ruleDOList) {
            FieldLinkageRule linkageRule = FieldLinkageRuleConvert.DOToFieldLinkageRule(ruleDO);
            CfgFieldDO cfgFieldDO = fieldDOMap.get(ruleDO.getFieldId());
            linkageRule.setComponentType(cfgFieldDO.getComponentType());

            String sourceValueType = ruleDO.getSourceValueType();
            String sourceValue = ruleDO.getSourceValue();
            Object newSourceValue = getValueNew(sourceValueType, sourceValue, fieldDOMap);
            linkageRule.setSourceValue(newSourceValue);

            List<Long> targetFieldIdList = ruleDO.getTargetFieldIdList();
            List<CfgFieldDO> fieldDOList = targetFieldIdList.stream().map(fieldDOMap::get).toList();
            List<FieldRuleField> fieldList = new ArrayList<>();
            for (CfgFieldDO fieldDO : fieldDOList) {
                FieldRuleField field = FieldConvert.DOToFieldRuleField(fieldDO);
                fieldList.add(field);
            }
            linkageRule.setTargetFields(fieldList);

            String targetValueType = ruleDO.getTargetValueType();
            String targetValue = ruleDO.getTargetValue();
            Object newTargetValue = getValueNew(targetValueType, targetValue, fieldDOMap);
            linkageRule.setTargetValue(newTargetValue);

            re.add(linkageRule);
        }
        return re;
    }

    public List<FieldTableRuleVO> getFieldTableRuleVOS(List<CfgFieldTableRule> ruleList, Map<Long, CfgFieldDO> fieldDOMap,
                                                       Map<Long, CfgTableDO> tableDOMap, Map<Long, CfgModelDO> modelDOMap) {
        if (CollectionUtils.isEmpty(ruleList) || fieldDOMap == null || tableDOMap == null || modelDOMap == null) {
            return List.of();
        }

        Set<Long> fieldIdSet = ruleList.stream().map(CfgFieldTableRule::getFieldId).collect(Collectors.toSet());
        List<CfgFieldDO> fieldDOList = fieldIdSet.stream().map(fieldDOMap::get).toList();
        Map<Long, String> modelNameMap = new HashMap<>();
        for (CfgFieldDO fieldDO : fieldDOList) {
            CfgModelDO modelDO = modelDOMap.get(fieldDO.getModelId());
            if (modelDO != null) {
                modelNameMap.put(fieldDO.getId(), modelDO.getName());
            }
        }

        List<FieldTableRuleVO> voList = ruleList.stream().map(rule -> {
            FieldTableRuleVO vo = new FieldTableRuleVO();
            BeanUtils.copyProperties(rule, vo);
            vo.setId(rule.getId().toString());

            Long currentFieldId = rule.getFieldId();
            CfgFieldDO currentFieldDO = fieldDOMap.get(currentFieldId);
            com.bone.lowcode.infra.application.vo.simple.Field field = FieldConvert.fieldDOToSimpleField(currentFieldDO);
            vo.setCurrentField(field);
            if (ValueTypeEnum.DYNAMIC.getValue().equals(rule.getSourceValueType())) {
                CfgFieldDO dynamicFieldDO = fieldDOMap.get(Long.valueOf(rule.getSourceValue()));
                com.bone.lowcode.infra.application.vo.simple.Field dynamicField = FieldConvert.fieldDOToSimpleField(dynamicFieldDO);
                vo.setSourceValue(dynamicField);
            }
            CfgTableDO tableDO = tableDOMap.get(rule.getTableId());
            com.bone.lowcode.infra.application.vo.simple.Table table = TableConvert.tableDOToSimpleTable(tableDO);
            vo.setTargetTable(table);
            vo.setModelName(modelNameMap.get(rule.getFieldId()));
            return vo;
        }).toList();
        return voList;
    }

    private HashMap<String, List<EventTrigger>> getTriggerOwnerMapNew(List<CfgEventTriggerDO> eventTriggerDOList,
                                                                      Map<Long, CfgEventDO> eventDOMap) {
        HashMap<String, List<EventTrigger>> triggerOwnerMap = new HashMap<>();
        for (CfgEventTriggerDO triggerDO : eventTriggerDOList) {
            EventTrigger eventTrigger = new EventTrigger();
            eventTrigger.setId(triggerDO.getId().toString());
            eventTrigger.setLabel(triggerDO.getLabel());
            eventTrigger.setStyle(triggerDO.getStyle());
            eventTrigger.setDisplayType(triggerDO.getDisplayType());
            eventTrigger.setOwner(triggerDO.getOwner());
            CfgEventDO eventDO = eventDOMap.get(triggerDO.getEventId());
            if (eventDO != null) {
                eventTrigger.setEventId(eventDO.getId().toString());
                eventTrigger.setEventName(eventDO.getName());
                eventTrigger.setEventCode(eventDO.getCode());
                eventTrigger.setPrepare(eventDO.getPrepare());
            }

            String key = null;
            if (triggerDO.getOwner() == TriggerOwnerEnum.FORM.getCode()) {
                key = triggerOwnerMapKeyForm + triggerDO.getOwnerId();
            } else if (triggerDO.getOwner() == TriggerOwnerEnum.BLOCK.getCode()) {
                key = triggerOwnerMapKeyBlock + triggerDO.getOwnerId();
            } else if (triggerDO.getOwner() == TriggerOwnerEnum.TABLE_ROW.getCode() ||
                    triggerDO.getOwner() == TriggerOwnerEnum.TABLE_LEFT.getCode() ||
                    triggerDO.getOwner() == TriggerOwnerEnum.TABLE_RIGHT.getCode()) {
                key = triggerOwnerMapKeyTable + triggerDO.getOwnerId();
            }
            if (triggerOwnerMap.containsKey(key)) {
                triggerOwnerMap.get(key).add(eventTrigger);
            } else {
                List<EventTrigger> list = new ArrayList<>();
                list.add(eventTrigger);
                triggerOwnerMap.put(key, list);
            }
        }
        return triggerOwnerMap;
    }

    private Object getValueNew(String sourceValueType, String sourceValue, Map<Long, CfgFieldDO> fieldDOMap) {
        Object newSourceValue = null;
        if (ValueTypeEnum.DYNAMIC.getValue().equals(sourceValueType)) {
            Long temFieldId = Long.valueOf(sourceValue);
            CfgFieldDO fieldDO = fieldDOMap.get(temFieldId);
            newSourceValue = FieldConvert.DOToFieldRuleField(fieldDO);
        } else if (ValueTypeEnum.FIXED.getValue().equals(sourceValueType)) {
            newSourceValue = sourceValue;
        }
        return newSourceValue;
    }

    private Map<String, List<FieldLinkedDisplayRuleVO>> getDisplayRuleMap(List<CfgFieldDO> fieldDOList) {
        List<Long> selectDropFieldIdList = fieldDOList.stream()
                .filter(fieldDO -> Objects.equals(fieldDO.getComponentType(), ComponentTypeEnum.SELECT_DROP.getType()))
                .map(CfgFieldDO::getId)
                .toList();
        if (CollectionUtils.isEmpty(selectDropFieldIdList)) {
            return null;
        }

        List<FieldLinkedDisplayRule> displayRuleList = fieldLinkedDisplayRuleService.getByFieldIdList(selectDropFieldIdList);
        if (CollectionUtils.isEmpty(displayRuleList)) {
            return null;
        }

        List<Long> affectedFieldIdList = displayRuleList.stream()
                .map(FieldLinkedDisplayRule::getAffectField)
                .flatMap((Function<List<LinkedDisplayRuleEntry>, Stream<LinkedDisplayRuleEntry>>) Collection::stream)
                .map(LinkedDisplayRuleEntry::getFieldId)
                .filter(Objects::nonNull)
                .toList();
        if (CollectionUtils.isEmpty(affectedFieldIdList)) {
            return null;
        }

        List<CfgFieldDO> affectedFieldList = fieldService.getDOListByIdList(affectedFieldIdList);
        if (CollectionUtils.isEmpty(affectedFieldList)) {
            return null;
        }

        Map<Long, CfgFieldDO> affectedFieldMap = affectedFieldList.stream().collect(Collectors.toMap(CfgFieldDO::getId, i -> i));
        return displayRuleList.stream().collect(Collectors.toMap(
                rule -> rule.getSelectFieldId().toString(),
                rule -> {
                    List<LinkedDisplayRuleEntry> ruleEntryList = rule.getAffectField();
                    return ruleEntryList.stream().map(entry -> {
                        FieldLinkedDisplayRuleVO vo = new FieldLinkedDisplayRuleVO();
                        vo.setExtraProperty(entry.getExtraProperty());
                        Long fieldId = entry.getFieldId();
                        vo.setFieldId(fieldId.toString());
                        if (affectedFieldMap.containsKey(fieldId)) {
                            CfgFieldDO affectedField = affectedFieldMap.get(fieldId);
                            vo.setBizName(affectedField.getBizName());
                            vo.setBizCode(affectedField.getBizCode());
                            vo.setDataBinding(affectedField.getDataBinding());
                        }
                        return vo;
                    }).toList();
                }));
    }

    private HashMap<String, List<EventTrigger>> getTriggerOwnerMap(List<CfgEventTriggerDO> eventTriggerDOList) {
        Set<Long> eventIdSet = eventTriggerDOList.stream().map(CfgEventTriggerDO::getEventId).collect(Collectors.toSet());
        Map<Long, CfgEventDO> eventDOMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(eventIdSet)) {
            List<CfgEventDO> eventDOList = eventService.getDOListByIdList(eventIdSet);
            eventDOMap = eventDOList.stream().collect(Collectors.toMap(CfgEventDO::getId, i -> i));
        }

        HashMap<String, List<EventTrigger>> triggerOwnerMap = new HashMap<>();
        for (CfgEventTriggerDO triggerDO : eventTriggerDOList) {
            EventTrigger eventTrigger = new EventTrigger();
            eventTrigger.setId(triggerDO.getId().toString());
            eventTrigger.setLabel(triggerDO.getLabel());
            eventTrigger.setStyle(triggerDO.getStyle());
            eventTrigger.setDisplayType(triggerDO.getDisplayType());
            eventTrigger.setOwner(triggerDO.getOwner());
            CfgEventDO eventDO = eventDOMap.get(triggerDO.getEventId());
            if (eventDO != null) {
                eventTrigger.setEventId(eventDO.getId().toString());
                eventTrigger.setEventName(eventDO.getName());
                eventTrigger.setEventCode(eventDO.getCode());
                eventTrigger.setPrepare(eventDO.getPrepare());
            }

            String key = null;
            if (triggerDO.getOwner() == TriggerOwnerEnum.FORM.getCode()) {
                key = triggerOwnerMapKeyForm + triggerDO.getOwnerId();
            } else if (triggerDO.getOwner() == TriggerOwnerEnum.BLOCK.getCode()) {
                key = triggerOwnerMapKeyBlock + triggerDO.getOwnerId();
            } else if (triggerDO.getOwner() == TriggerOwnerEnum.TABLE_ROW.getCode() ||
                    triggerDO.getOwner() == TriggerOwnerEnum.TABLE_LEFT.getCode() ||
                    triggerDO.getOwner() == TriggerOwnerEnum.TABLE_RIGHT.getCode()) {
                key = triggerOwnerMapKeyTable + triggerDO.getOwnerId();
            }
            if (triggerOwnerMap.containsKey(key)) {
                triggerOwnerMap.get(key).add(eventTrigger);
            } else {
                List<EventTrigger> list = new ArrayList<>();
                list.add(eventTrigger);
                triggerOwnerMap.put(key, list);
            }
        }
        return triggerOwnerMap;
    }

    private List<SubmitRule> getSubmitRuleList(List<CfgSubmitRuleDO> submitRuleDOList) {
        List<SubmitRule> submitRuleList = new ArrayList<>();
        for (CfgSubmitRuleDO submitRuleDO : submitRuleDOList) {
            SubmitRule submitRule = SubmitRuleConvert.DOToSubmitRule(submitRuleDO);

            List<Long> fieldIds = submitRuleDO.getFieldIds();
            List<CfgFieldDO> fieldDOList = fieldService.getDOListByIdList(fieldIds);
            Map<Long, CfgFieldDO> fieldDOMap = fieldDOList.stream().collect(Collectors.toMap(CfgFieldDO::getId, i -> i));
            List<CfgFieldDO> fieldDOListNew = fieldIds.stream().map(fieldDOMap::get).toList();
            List<SubmitRuleField> fieldList = new ArrayList<>();
            for (CfgFieldDO fieldDO : fieldDOListNew) {
                SubmitRuleField field = FieldConvert.DOToSubmitRuleField(fieldDO);
                fieldList.add(field);
            }
            submitRule.setFieldList(fieldList);

            Object value = null;
            if (ValueTypeEnum.DYNAMIC.getValue().equals(submitRuleDO.getValueType())) {
                Long fieldId = Long.valueOf(submitRuleDO.getValue());
                CfgFieldDO fieldDO = fieldService.getDOById(fieldId);
                value = FieldConvert.DOToSubmitRuleField(fieldDO);
            } else if (ValueTypeEnum.FIXED.getValue().equals(submitRuleDO.getValueType())) {
                value = submitRuleDO.getValue();
            }
            submitRule.setValue(value);
            submitRuleList.add(submitRule);
        }
        return submitRuleList;
    }

    private List<FieldLinkageRule> getFieldLinkageRuleByPageId(Long pageId) {
        List<CfgFieldLinkageRuleDO> ruleDOList = fieldLinkageRuleService.getEnableByPageId(pageId);
        return getLinkageRule(ruleDOList);
    }

    private List<FieldLinkageRule> getLinkageRule(List<CfgFieldLinkageRuleDO> ruleDOList) {
        List<FieldLinkageRule> re = new ArrayList<>();
        for (CfgFieldLinkageRuleDO ruleDO : ruleDOList) {
            FieldLinkageRule linkageRule = FieldLinkageRuleConvert.DOToFieldLinkageRule(ruleDO);
            CfgFieldDO cfgFieldDO = fieldService.getDOById(ruleDO.getFieldId());
            linkageRule.setComponentType(cfgFieldDO.getComponentType());

            String sourceValueType = ruleDO.getSourceValueType();
            String sourceValue = ruleDO.getSourceValue();
            Object newSourceValue = getValue(sourceValueType, sourceValue);
            linkageRule.setSourceValue(newSourceValue);

            List<Long> targetFieldIdList = ruleDO.getTargetFieldIdList();
            List<CfgFieldDO> fieldDOList = fieldService.getDOListByIdList(targetFieldIdList);
            List<FieldRuleField> fieldList = new ArrayList<>();
            for (CfgFieldDO fieldDO : fieldDOList) {
                FieldRuleField field = FieldConvert.DOToFieldRuleField(fieldDO);
                fieldList.add(field);
            }
            linkageRule.setTargetFields(fieldList);

            String targetValueType = ruleDO.getTargetValueType();
            String targetValue = ruleDO.getTargetValue();
            Object newTargetValue = getValue(targetValueType, targetValue);
            linkageRule.setTargetValue(newTargetValue);

            re.add(linkageRule);
        }
        return re;
    }

    private Object getValue(String sourceValueType, String sourceValue) {
        Object newSourceValue = null;
        if (ValueTypeEnum.DYNAMIC.getValue().equals(sourceValueType)) {
            Long temFieldId = Long.valueOf(sourceValue);
            CfgFieldDO fieldDO = fieldService.getDOById(temFieldId);
            newSourceValue = FieldConvert.DOToFieldRuleField(fieldDO);
        } else if (ValueTypeEnum.FIXED.getValue().equals(sourceValueType)) {
            newSourceValue = sourceValue;
        }
        return newSourceValue;
    }

    private Object getValueByRelease(String sourceValueType, String sourceValue, Map<Long, CfgFieldDO> fieldMap) {
        Object newSourceValue = null;
        if (ValueTypeEnum.DYNAMIC.getValue().equals(sourceValueType)) {
            Long temFieldId = Long.valueOf(sourceValue);
            CfgFieldDO fieldDO = fieldMap.get(temFieldId);
            newSourceValue = FieldConvert.DOToFieldRuleField(fieldDO);
        } else if (ValueTypeEnum.FIXED.getValue().equals(sourceValueType)) {
            newSourceValue = sourceValue;
        }
        return newSourceValue;
    }

    public boolean publishExclusive(String identityCode) {
        long versionId = System.currentTimeMillis();
        Date now = new Date();

        List<String> basicPageCodeList = BasicPageCodeEnum.getAllBasicPageCode();
        List<CfgReleasedPageDO> list = new ArrayList<>();
        for (String pageCode : basicPageCodeList) {
            CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, identityCode);
            if (pageDO == null) {
                throw new ServiceException(500, "目标专属页面不存在,pageCode:" + pageCode + ",bizIdentityCode:" + identityCode);
            }

            String voStr = getPageInfoStr(pageDO);
            CfgReleasedPageDO releasedPageDO = new CfgReleasedPageDO();
            releasedPageDO.setId(SnowflakeIdUtil.getId());
            releasedPageDO.setVersionId(versionId);
            releasedPageDO.setType(PageTypeEnum.BIZ_IDENTITY.getCode());
            releasedPageDO.setPageId(pageDO.getId());
            releasedPageDO.setPageCode(pageDO.getCode());
            releasedPageDO.setBizIdentityCode(identityCode);
            releasedPageDO.setPageInfo(voStr);
            releasedPageDO.setDeleted(DeletedEnum.UNDELETED.getCode());
            releasedPageDO.setCreateTime(now);
            list.add(releasedPageDO);
        }

        return Boolean.TRUE.equals(
                transactionTemplate.execute(status -> {
                    try {
                        versionService.saveVersionInfo(PageTypeEnum.BIZ_IDENTITY.getCode(), "发布专属页面,identityCode:" + identityCode, versionId, identityCode);
                        return releasedPageService.batchSave(list);
                    } catch (Exception e) {
                        log.error("发布专属页面发生异常:", e);
                        status.setRollbackOnly();
                        return false;
                    }
                })
        );
    }

    public Date lastPublishTime(String bizIdentityCode) {
        Byte type = null;
        if (StringUtils.hasText(bizIdentityCode)) {
            type = PageTypeEnum.BIZ_IDENTITY.getCode();
        } else {
            type = PageTypeEnum.TEMPLATE.getCode();
        }

        CfgVersionDO versionDO = versionService.getLastByType(type, bizIdentityCode);
        if (versionDO == null) {
            log.info("目标页面未发布过,bizIdentityCode:{}", bizIdentityCode);
            return null;
        }
        return versionDO.getCreateTime();
    }

    public Boolean deleteBizIdentity(String bizIdentityCode) {
        if (!StringUtils.hasText(bizIdentityCode)) {
            throw new ServiceException(500, "主体code不能为空");
        }

        //cfg_page
        List<CfgPageDO> pageDOList = pageService.getDOListByIdentityCode(bizIdentityCode);
        if (CollectionUtils.isEmpty(pageDOList)) {
            throw new ServiceException(500, "该主体下不存在配置页面,无需删除");
        }
        List<Long> pageIdList = pageDOList.stream().map(CfgPageDO::getId).toList();

        //cfg_form
        List<CfgFormDO> formDOList = formService.getDOByPageIds(pageIdList);
        List<Long> formIdList = formDOList.stream().map(CfgFormDO::getId).toList();

        //cfg_block
        List<CfgBlockDO> blockDOList = blockService.getDOListByPageIds(pageIdList);
        List<Long> blockIdList = blockDOList.stream().map(CfgBlockDO::getId).toList();

        //cfg_fieldset
        List<CfgFieldsetDO> fieldsetDOList = fieldsetService.getDOByPageIds(pageIdList);
        List<Long> fieldsetIdList = fieldsetDOList.stream().map(CfgFieldsetDO::getId).toList();

        //cfg_table
        List<CfgTableDO> tableDOList = tableService.getByPageIds(pageIdList);
        List<Long> tableIdList = tableDOList.stream().map(CfgTableDO::getId).collect(Collectors.toList());

        //cfg_model
        List<CfgModelDO> modelDOList = modelService.getByPageIds(pageIdList);
        List<Long> modelIdList = modelDOList.stream().map(CfgModelDO::getId).collect(Collectors.toList());

        //cfg_field
        List<CfgFieldDO> fieldDOList = fieldService.getDOByPageIds(pageIdList);
        List<Long> fieldIdList = fieldDOList.stream().map(CfgFieldDO::getId).collect(Collectors.toList());

        //cfg_event_trigger
        List<CfgEventTriggerDO> triggerDOList = eventTriggerService.getByPageIds(pageIdList);
        List<Long> triggerIdList = triggerDOList.stream().map(CfgEventTriggerDO::getId).toList();

        //cfg_submit_rule
        List<CfgSubmitRuleDO> submitRuleDOList = submitRuleService.getByPageIds(pageIdList);
        List<Long> submitRuleIdList = submitRuleDOList.stream().map(CfgSubmitRuleDO::getId).toList();

        //cfg_field_linkage_rule
        List<CfgFieldLinkageRuleDO> linkageRuleDOList = fieldLinkageRuleService.getByPageIds(pageIdList);
        List<Long> fieldLinkageRuleIdList = linkageRuleDOList.stream().map(CfgFieldLinkageRuleDO::getId).toList();

        //cfg_field_linked_display_rule
        List<FieldLinkedDisplayRule> fieldLinkedDisplayRuleList = fieldLinkedDisplayRuleService.getByPageIds(pageIdList);
        List<Long> displayRuleIdList = fieldLinkedDisplayRuleList.stream().map(FieldLinkedDisplayRule::getId).toList();

        //cfg_field_table_rule
        List<CfgFieldTableRule> fieldTableRuleList = fieldTableRuleService.getByPageIds(pageIdList);
        List<Long> fieldTableRuleIdList = fieldTableRuleList.stream().map(CfgFieldTableRule::getId).toList();

        //cfg_table_data_group_aggregate
        List<CfgTableDataGroupAggregateDO> tableDataGroupAggregateDOList = tableDataGroupAggregateService.getByPageIds(pageIdList);
        List<Long> tableDataGroupAggregateIdList = tableDataGroupAggregateDOList.stream().map(CfgTableDataGroupAggregateDO::getId).toList();

        //cfg_table_data_row_edit
        List<CfgTableDataRowEditDO> tableDataRowEditDOList = tableDataRowEditService.getByPageIds(pageIdList);
        List<Long> tableDataRowEditIdList = tableDataRowEditDOList.stream().map(CfgTableDataRowEditDO::getId).toList();

        //cfg_table_data_row_verify
        List<CfgTableDataRowVerifyDO> tableDataRowVerifyDOList = tableDataRowVerifyService.getByPageIds(pageIdList);
        List<Long> tableDataRowVerifyIdList = tableDataRowVerifyDOList.stream().map(CfgTableDataRowVerifyDO::getId).toList();

        //cfg_table_data_relation
        List<CfgTableDataRelationDO> relationDOList = tableDataRelationService.getByPageIds(pageIdList);
        List<Long> tableDataRelationIdList = relationDOList.stream().map(CfgTableDataRelationDO::getId).toList();

        //cfg_table_data_cross_edit
        List<CfgTableDataCrossEditDO> crossEditDOList = crossTableDataEditService.getByRelationIds(tableDataRelationIdList);
        List<Long> crossTableDataEditIdList = crossEditDOList.stream().map(CfgTableDataCrossEditDO::getId).toList();

        //cfg_table_data_cross_verify
        List<CfgTableDataCrossVerifyDO> crossVerifyDOList = crossTableDataVerifyService.getByRelationIds(tableDataRelationIdList);
        List<Long> crossTableDataVerifyIdList = crossVerifyDOList.stream().map(CfgTableDataCrossVerifyDO::getId).toList();

        //初审详情页相关
        List<ProcessDetailPageDO> detailPageDOList = detailPageService.getByBizCode(bizIdentityCode);
        List<Long> detailPageIdList = detailPageDOList.stream().map(ProcessDetailPageDO::getId).toList();

        List<Long> modelIdList4 = detailPageDOList.stream().map(ProcessDetailPageDO::getPageHeadModelId).filter(Objects::nonNull).toList();
        modelIdList.addAll(modelIdList4);

        List<CfgFieldDO> fieldDOList2 = fieldService.getDOListByModelIds(modelIdList4);
        List<Long> fieldIdList3 = fieldDOList2.stream().map(CfgFieldDO::getId).toList();
        fieldIdList.addAll(fieldIdList3);

        boolean flag = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            try {
                bizIdentityService.deleteByCode(bizIdentityCode);
                if (!CollectionUtils.isEmpty(pageIdList)) pageService.deleteByIdList(pageIdList);
                if (!CollectionUtils.isEmpty(formIdList)) formService.deleteByIdList(formIdList);
                if (!CollectionUtils.isEmpty(blockIdList)) blockService.deleteByIdList(blockIdList);
                if (!CollectionUtils.isEmpty(fieldsetIdList)) fieldsetService.deleteByIdList(fieldsetIdList);
                if (!CollectionUtils.isEmpty(tableIdList)) tableService.deleteByIdList(tableIdList);
                if (!CollectionUtils.isEmpty(modelIdList)) modelService.deleteByIdList(modelIdList);
                if (!CollectionUtils.isEmpty(fieldIdList)) fieldService.deleteByIdList(fieldIdList);
                if (!CollectionUtils.isEmpty(fieldLinkageRuleIdList))
                    fieldLinkageRuleService.deleteByIdList(fieldLinkageRuleIdList);
                if (!CollectionUtils.isEmpty(displayRuleIdList))
                    fieldLinkedDisplayRuleService.deleteByIdList(displayRuleIdList);
                if (!CollectionUtils.isEmpty(fieldTableRuleIdList))
                    fieldTableRuleService.deleteByIdList(fieldTableRuleIdList);
                if (!CollectionUtils.isEmpty(submitRuleIdList)) submitRuleService.deleteByIdList(submitRuleIdList);
                if (!CollectionUtils.isEmpty(triggerIdList)) eventTriggerService.deleteByIdList(triggerIdList);
                if (!CollectionUtils.isEmpty(tableDataGroupAggregateIdList))
                    tableDataGroupAggregateService.deleteByIdList(tableDataGroupAggregateIdList);
                if (!CollectionUtils.isEmpty(tableDataRowEditIdList))
                    tableDataRowEditService.deleteByIdList(tableDataRowEditIdList);
                if (!CollectionUtils.isEmpty(tableDataRowVerifyIdList))
                    tableDataRowVerifyService.deleteByIdList(tableDataRowVerifyIdList);
                if (!CollectionUtils.isEmpty(tableDataRelationIdList))
                    tableDataRelationService.deleteByIdList(tableDataRelationIdList);
                if (!CollectionUtils.isEmpty(crossTableDataEditIdList))
                    crossTableDataEditService.deleteByIdList(crossTableDataEditIdList);
                if (!CollectionUtils.isEmpty(crossTableDataVerifyIdList))
                    crossTableDataVerifyService.deleteByIdList(crossTableDataVerifyIdList);

                if (!CollectionUtils.isEmpty(detailPageIdList)) detailPageService.deleteByIdList(detailPageIdList);
                return true;
            } catch (Exception e) {
                log.error("删除主体的页面发生异常,bizIdentityCode:" + bizIdentityCode, e);
                status.setRollbackOnly();
                return false;
            }
        }));
        return flag;
    }

    public NewPageVO previewByRelease(String pageCode, String bizIdentityCode, String displayMode) {
        byte type = StringUtils.hasText(bizIdentityCode) ? PageTypeEnum.BIZ_IDENTITY.getCode() : PageTypeEnum.TEMPLATE.getCode();
        CfgVersionDO lastVersionDO = versionService.getLastByType(type, bizIdentityCode);
        if (lastVersionDO == null) {
            throw new ServiceException(500, "目标页面未发布,pageCode:" + pageCode + ", bizIdentityCode:" + bizIdentityCode);
        }

        CfgReleasedPageDO releasedPageDO = releasedPageService.getDOByVersion(lastVersionDO.getId(), pageCode);
        if (releasedPageDO == null || !StringUtils.hasText(releasedPageDO.getPageInfo())) {
            throw new ServiceException(500, "未找到目标页面的发布态数据,pageCode:" + pageCode + ", bizIdentityCode:" + bizIdentityCode);
        }
        BasicPageInfo basicPageInfo = JSON.parseObject(releasedPageDO.getPageInfo(), BasicPageInfo.class);

        CfgPageDO pageDO = basicPageInfo.getPageDO();
        if (pageDO == null) {
            throw new ServiceException(500, "未找到目标页面,pageCode:" + pageCode + ", bizIdentityCode:" + bizIdentityCode);
        }

        SysBizIdentityDO bizIdentityDO = null;
        if (pageDO.getType() == PageTypeEnum.BIZ_IDENTITY.getCode() && StringUtils.hasText(bizIdentityCode)) {
            bizIdentityDO = bizIdentityService.getDOByCode(bizIdentityCode);
        }

        CfgFormDO formDO = basicPageInfo.getFormDO();
        List<CfgBlockDO> blockDOList = basicPageInfo.getBlockDOList();
        List<CfgFieldsetDO> fieldsetDOList = basicPageInfo.getFieldsetDOList();
        List<CfgTableDO> tableDOList = basicPageInfo.getTableDOList();
        List<CfgModelDO> modelDOList = basicPageInfo.getModelDOList();
        List<CfgFieldDO> fieldDOList = basicPageInfo.getFieldDOList();
        List<CfgSubmitRuleDO> submitRuleDOList = basicPageInfo.getSubmitRuleList();
        if (!CollectionUtils.isEmpty(submitRuleDOList)) {
            submitRuleDOList = submitRuleDOList.stream().filter(i -> i.getStatus() == StatusEnum.YES.getCode()).toList();
        }

        List<CfgFieldLinkageRuleDO> fieldLinkageRuleDOList = basicPageInfo.getFieldLinkageRuleList();
        if (!CollectionUtils.isEmpty(fieldLinkageRuleDOList)) {
            fieldLinkageRuleDOList = fieldLinkageRuleDOList.stream().filter(i -> i.getStatus() == StatusEnum.YES.getCode()).toList();
        }

        List<CfgFieldTableRule> fieldTableRuleList = basicPageInfo.getFieldTableRuleList();
        if (!CollectionUtils.isEmpty(fieldTableRuleList)) {
            fieldTableRuleList = fieldTableRuleList.stream().filter(i -> i.getStatus() == StatusEnum.YES.getCode()).toList();
        }

        List<FieldLinkedDisplayRule> linkedDisplayRuleList = basicPageInfo.getFieldLinkedDisplayRuleList();
        List<CfgEventTriggerDO> eventTriggerDOList = basicPageInfo.getEventTriggerList();
        Set<Long> eventIdSet = eventTriggerDOList.stream().map(CfgEventTriggerDO::getEventId).collect(Collectors.toSet());
        List<CfgEventDO> eventDOList = !CollectionUtils.isEmpty(eventIdSet) ? eventService.getDOListByIdList(eventIdSet) : new ArrayList<>();
        NewPageVO pageVO = getPageVO(displayMode, bizIdentityDO, pageDO, formDO, blockDOList, fieldsetDOList, tableDOList,
                modelDOList, fieldDOList, eventTriggerDOList, eventDOList, submitRuleDOList, fieldLinkageRuleDOList,
                fieldTableRuleList, linkedDisplayRuleList);

        TableRules tableRules = getRestRule(pageCode, bizIdentityCode, PublishStatusEnum.PUBLISH.getCode());
        pageVO.setTableRules(tableRules);
        return pageVO;
    }

    @Transactional(rollbackFor = Throwable.class)
    public void copyEntryToQualityCheck(String identityCode) {
        CfgPageDO entryPageDO = pageService.getByPageCodeAndIdentityCode(BasicPageCodeEnum.ENTRY.getCode(), identityCode);
        if (entryPageDO == null) {
            throw new ServiceException(500, "目标录入页面不存在,identityCode:" + identityCode);
        }

        //删除原有质检页面相关元素
        CfgPageDO qualityCheckPageDO = pageService.getByPageCodeAndIdentityCode(BasicPageCodeEnum.QUALITY_CHECK.getCode(), identityCode);
        if (qualityCheckPageDO != null) {
            deletePage(qualityCheckPageDO.getId());
        }

        //获取录入页面数据
        Long entryPageId = entryPageDO.getId();
        CfgFormDO formDO = formService.getDOByPageId(entryPageId);
        List<CfgBlockDO> blockDOList = blockService.getDOListByPageId(entryPageId);
        List<CfgFieldsetDO> fieldsetDOList = fieldsetService.getDOByPageId(entryPageId);
        List<CfgTableDO> tableDOList = tableService.getDOListByPageId(entryPageId);
        List<CfgModelDO> modelDOList = modelService.getDOListByPageId(entryPageId);
        List<CfgFieldDO> fieldDOList = fieldService.getDOByPageId(entryPageId);
        List<CfgEventTriggerDO> triggerDOList = eventTriggerService.getDOListByPageId(entryPageId);
        List<CfgSubmitRuleDO> submitRuleDOList = submitRuleService.getByPageId(entryPageId);

        List<CfgFieldLinkageRuleDO> fieldLinkageRuleDOList = fieldLinkageRuleService.getRuleByPageId(entryPageId);
        List<FieldLinkedDisplayRule> fieldLinkedDisplayRuleList = fieldLinkedDisplayRuleService.getByPageId(entryPageId);
        List<CfgFieldTableRule> fieldTableRuleList = fieldTableRuleService.getByPageId(entryPageId);

        List<CfgTableDataGroupAggregateDO> tableDataGroupAggregateDOList = tableDataGroupAggregateService.getDOListByPageId(entryPageId);
        List<CfgTableDataRowVerifyDO> tableDataRowVerifyDOList = tableDataRowVerifyService.getDOListByPageId(entryPageId);
        List<CfgTableDataRowEditDO> tableDataRowEditDOList = tableDataRowEditService.getDOListByPageId(entryPageId);

        List<CfgTableDataRelationDO> tableDataRelationDOList = tableDataRelationService.getDOListByPageId(entryPageId);
        List<Long> tableDataRelationIdList = tableDataRelationDOList.stream().map(CfgTableDataRelationDO::getId).toList();
        List<CfgTableDataCrossEditDO> tableDataCrossEditDOList = tableDataRelationIdList.stream()
                .map(id -> crossTableDataEditService.getDOListByRelationId(id))
                .flatMap((Function<List<CfgTableDataCrossEditDO>, Stream<CfgTableDataCrossEditDO>>) Collection::stream).toList();
        List<CfgTableDataCrossVerifyDO> tableDataCrossVerifyDOList = tableDataRelationIdList.stream()
                .map(id -> crossTableDataVerifyService.getDOListByRelationId(id))
                .flatMap((Function<List<CfgTableDataCrossVerifyDO>, Stream<CfgTableDataCrossVerifyDO>>) Collection::stream).toList();

        //基于录入页面,生成新质检页面
        Map<Long, CfgBlockDO> blockMap = blockDOList.stream().collect(Collectors.toMap(CfgBlockDO::getId, i -> i));
        Map<Long, CfgTableDO> tableMap = tableDOList.stream().collect(Collectors.toMap(CfgTableDO::getId, i -> i));
        Map<Long, CfgModelDO> modelMap = modelDOList.stream().collect(Collectors.toMap(CfgModelDO::getId, i -> i));
        Map<Long, CfgFieldDO> fieldMap = fieldDOList.stream().collect(Collectors.toMap(CfgFieldDO::getId, i -> i));
        Map<Long, CfgTableDataRelationDO> tableRelationMap = tableDataRelationDOList.stream().collect(Collectors.toMap(CfgTableDataRelationDO::getId, i -> i));
        Date now = new Date();

        long newPageId = SnowflakeIdUtil.getId();
        entryPageDO.setId(newPageId);
        entryPageDO.setCode(BasicPageCodeEnum.QUALITY_CHECK.getCode());
        entryPageDO.setName("质检页面");
        entryPageDO.setCreateBy(null);
        entryPageDO.setCreateTime(now);
        entryPageDO.setUpdateBy(null);
        entryPageDO.setUpdateTime(null);

        long newFormId = SnowflakeIdUtil.getId();
        formDO.setId(newFormId);
        formDO.setPageId(newPageId);
        formDO.setName("质检表单");
        formDO.setCreateBy(null);
        formDO.setCreateTime(now);
        formDO.setUpdateBy(null);
        formDO.setUpdateTime(null);

        for (CfgBlockDO blockDO : blockDOList) {
            blockDO.setId(SnowflakeIdUtil.getId());
            blockDO.setPageId(newPageId);
            blockDO.setFormId(newFormId);
            blockDO.setCreateBy(null);
            blockDO.setCreateTime(now);
            blockDO.setUpdateBy(null);
            blockDO.setUpdateTime(null);
        }

        for (CfgModelDO modelDO : modelDOList) {
            modelDO.setId(SnowflakeIdUtil.getId());
            modelDO.setPageId(newPageId);
            modelDO.setCreateBy(null);
            modelDO.setCreateTime(now);
            modelDO.setUpdateBy(null);
            modelDO.setUpdateTime(null);
        }

        for (CfgFieldDO fieldDO : fieldDOList) {
            fieldDO.setId(SnowflakeIdUtil.getId());
            fieldDO.setPageId(newPageId);
            fieldDO.setModelId(modelMap.get(fieldDO.getModelId()).getId());
            fieldDO.setCreateBy(null);
            fieldDO.setCreateTime(now);
            fieldDO.setUpdateBy(null);
            fieldDO.setUpdateTime(null);
        }

        for (CfgFieldsetDO fieldsetDO : fieldsetDOList) {
            fieldsetDO.setId(SnowflakeIdUtil.getId());
            fieldsetDO.setPageId(newPageId);
            fieldsetDO.setBlockId(blockMap.get(fieldsetDO.getBlockId()).getId());
            fieldsetDO.setModelId(modelMap.get(fieldsetDO.getModelId()).getId());
            fieldsetDO.setCreateBy(null);
            fieldsetDO.setCreateTime(now);
            fieldsetDO.setUpdateBy(null);
            fieldsetDO.setUpdateTime(null);
        }

        for (CfgTableDO tableDO : tableDOList) {
            tableDO.setId(SnowflakeIdUtil.getId());
            tableDO.setPageId(newPageId);
            tableDO.setBlockId(blockMap.get(tableDO.getBlockId()).getId());
            tableDO.setCreateBy(null);
            tableDO.setCreateTime(now);
            tableDO.setUpdateBy(null);
            tableDO.setUpdateTime(null);

            //涉及字段
            List<Long> list = tableDO.getModelIds().stream().map(i -> modelMap.get(i).getId()).toList();
            tableDO.setModelIdListFromList(list);//model_id_list

            List<TableFieldSortType> sortTypeList = tableDO.getSortTypeList();
            sortTypeList.forEach(item -> item.setId(fieldMap.get(Long.parseLong(item.getId())).getId().toString()));
            tableDO.setSortTypeList(sortTypeList);//order_by_list

            List<EditableColumn> editableColumnList = tableDO.getEditableColumns();
            editableColumnList.forEach(item -> item.setFieldId(fieldMap.get(Long.parseLong(item.getFieldId())).getId().toString()));
            tableDO.setEditableColumns(editableColumnList);//editable_column_list

            List<DataSummaryRule> summaryRuleList = tableDO.getDataSummaryRules();
            summaryRuleList.forEach(item -> item.setFieldId(fieldMap.get(Long.parseLong(item.getFieldId())).getId().toString()));
            tableDO.setDataSummaryRules(summaryRuleList);//data_summary_rule

            List<TableSearchField> searchFieldList = tableDO.getSearchFields();
            searchFieldList.forEach(item -> item.setFieldId(fieldMap.get(Long.parseLong(item.getFieldId())).getId().toString()));
            tableDO.setSearchFields(searchFieldList);//search_field_list
        }

        for (CfgTableDO tableDO : tableDOList) {
            if (tableDO.getParentTableId() != null) {
                CfgTableDO parentTable = tableMap.get(tableDO.getParentTableId());
                Long newParentTableId = parentTable.getId();
                tableDO.setParentTableId(newParentTableId);
            }
        }

        createNewEventTriggerList(newPageId, newFormId, blockMap, tableMap, triggerDOList);

        for (CfgSubmitRuleDO submitRuleDO : submitRuleDOList) {
            createNewSubmitRuleDO(newPageId, fieldMap, submitRuleDO);
        }
        for (CfgFieldLinkageRuleDO ruleDO : fieldLinkageRuleDOList) {
            createNewFieldLinkageRuleDO(newPageId, fieldMap, ruleDO);
        }
        for (FieldLinkedDisplayRule rule : fieldLinkedDisplayRuleList) {
            createNewFieldLinkedDisplayRuleDO(newPageId, fieldMap, rule);
        }
        for (CfgFieldTableRule rule : fieldTableRuleList) {
            createNewFieldTableRuleDO(newPageId, fieldMap, tableMap, rule);
        }
        createNewTableDataGroupAggregateDOList(newPageId, fieldMap, tableMap, tableDataGroupAggregateDOList);
        createNewTableDataRowVerifyDOList(newPageId, fieldMap, tableMap, tableDataRowVerifyDOList);
        createNewTableDataRowEditDOList(newPageId, fieldMap, tableMap, tableDataRowEditDOList);
        createNewTableDataRelationDOList(newPageId, fieldMap, tableMap, tableDataRelationDOList);
        createNewCrossTableDataEditDOList(newPageId, fieldMap, tableRelationMap, tableDataCrossEditDOList);
        createNewCrossTableDataVerifyDOList(newPageId, fieldMap, tableRelationMap, tableDataCrossVerifyDOList);

        log.info("创建质检页面,准备批量插入,identityCode:{}", identityCode);
        pageService.addPage(entryPageDO);
        formService.addForm(formDO);
        if (!CollectionUtils.isEmpty(blockDOList)) {
            blockService.batchSave(blockDOList);
        }
        if (!CollectionUtils.isEmpty(fieldsetDOList)) {
            fieldsetService.batchSave(fieldsetDOList);
        }
        if (!CollectionUtils.isEmpty(tableDOList)) {
            tableService.batchSave(tableDOList);
        }
        if (!CollectionUtils.isEmpty(modelDOList)) {
            modelService.batchSave(modelDOList);
        }
        if (!CollectionUtils.isEmpty(fieldDOList)) {
            fieldService.batchSave(fieldDOList);
        }
        if (!CollectionUtils.isEmpty(triggerDOList)) {
            eventTriggerService.batchSave(triggerDOList);
        }
        if (!CollectionUtils.isEmpty(submitRuleDOList)) {
            submitRuleService.batchSave(submitRuleDOList);
        }
        if (!CollectionUtils.isEmpty(fieldLinkageRuleDOList)) {
            fieldLinkageRuleService.batchSave(fieldLinkageRuleDOList);
        }
        if (!CollectionUtils.isEmpty(fieldLinkedDisplayRuleList)) {
            fieldLinkedDisplayRuleService.batchSave(fieldLinkedDisplayRuleList);
        }
        if (!CollectionUtils.isEmpty(fieldTableRuleList)) {
            fieldTableRuleService.batchSave(fieldTableRuleList);
        }
        if (!CollectionUtils.isEmpty(tableDataGroupAggregateDOList)) {
            tableDataGroupAggregateService.batchSave(tableDataGroupAggregateDOList);
        }
        if (!CollectionUtils.isEmpty(tableDataRowVerifyDOList)) {
            tableDataRowVerifyService.batchSave(tableDataRowVerifyDOList);
        }
        if (!CollectionUtils.isEmpty(tableDataRowEditDOList)) {
            tableDataRowEditService.batchSave(tableDataRowEditDOList);
        }
        if (!CollectionUtils.isEmpty(tableDataRelationDOList)) {
            tableDataRelationService.batchSave(tableDataRelationDOList);
        }
        if (!CollectionUtils.isEmpty(tableDataCrossEditDOList)) {
            crossTableDataEditService.batchSave(tableDataCrossEditDOList);
        }
        if (!CollectionUtils.isEmpty(tableDataCrossVerifyDOList)) {
            crossTableDataVerifyService.batchSave(tableDataCrossVerifyDOList);
        }
        log.info("创建质检页面,完成,identityCode:{}", identityCode);
    }

    private void deletePage(Long checkPageId) {
        log.info("准备删除页面,pageId:{}", checkPageId);
        Long formId = formService.getDOByPageId(checkPageId).getId();
        List<Long> blockIdList = blockService.getDOListByPageId(checkPageId).stream().map(CfgBlockDO::getId).toList();
        List<Long> fieldSetIdList = fieldsetService.getDOByPageId(checkPageId).stream().map(CfgFieldsetDO::getId).toList();
        List<Long> tableIdList = tableService.getDOListByPageId(checkPageId).stream().map(CfgTableDO::getId).toList();
        List<Long> modelIdList = modelService.getDOListByPageId(checkPageId).stream().map(CfgModelDO::getId).toList();
        List<Long> fieldIdList = fieldService.getDOByPageId(checkPageId).stream().map(CfgFieldDO::getId).toList();
        List<Long> eventTriggerIdList = eventTriggerService.getDOListByPageId(checkPageId).stream().map(CfgEventTriggerDO::getId).toList();
        List<Long> submitRuleIdList = submitRuleService.getByPageId(checkPageId).stream().map(CfgSubmitRuleDO::getId).toList();

        List<Long> fieldLinkageRuleIdList = fieldLinkageRuleService.getRuleByPageId(checkPageId).stream().map(CfgFieldLinkageRuleDO::getId).toList();
        List<Long> fieldLinkedDisplayRuleIdList = fieldLinkedDisplayRuleService.getByPageId(checkPageId).stream().map(FieldLinkedDisplayRule::getId).toList();
        List<Long> fieldTableRuleIdList = fieldTableRuleService.getByPageId(checkPageId).stream().map(CfgFieldTableRule::getId).toList();

        List<Long> tableDataGroupAggregateIdList = tableDataGroupAggregateService.getDOListByPageId(checkPageId).stream().map(CfgTableDataGroupAggregateDO::getId).toList();
        List<Long> tableRuleIdList1 = tableDataRowVerifyService.getDOListByPageId(checkPageId).stream().map(CfgTableDataRowVerifyDO::getId).toList();
        List<Long> tableRuleIdList2 = tableDataRowEditService.getDOListByPageId(checkPageId).stream().map(CfgTableDataRowEditDO::getId).toList();

        List<Long> tableDataRelationIdList = tableDataRelationService.getDOListByPageId(checkPageId).stream().map(CfgTableDataRelationDO::getId).toList();
        List<Long> tableRuleIdList3 = tableDataRelationIdList.stream()
                .map(id -> crossTableDataEditService.getDOListByRelationId(id).stream().map(CfgTableDataCrossEditDO::getId).toList())
                .flatMap((Function<List<Long>, Stream<Long>>) Collection::stream).toList();
        List<Long> tableRuleIdList4 = tableDataRelationIdList.stream()
                .map(id -> crossTableDataVerifyService.getDOListByRelationId(id).stream().map(CfgTableDataCrossVerifyDO::getId).toList())
                .flatMap((Function<List<Long>, Stream<Long>>) Collection::stream).toList();

        if (checkPageId != null) {
            pageService.deleteById(checkPageId);
        }
        if (formId != null) {
            formService.deleteById(formId);
        }
        if (!CollectionUtils.isEmpty(blockIdList)) {
            blockService.deleteByIdList(blockIdList);
        }
        if (!CollectionUtils.isEmpty(fieldSetIdList)) {
            fieldsetService.deleteByIdList(fieldSetIdList);
        }
        if (!CollectionUtils.isEmpty(tableIdList)) {
            tableService.deleteByIdList(tableIdList);
        }
        if (!CollectionUtils.isEmpty(modelIdList)) {
            modelService.deleteByIdList(modelIdList);
        }
        if (!CollectionUtils.isEmpty(fieldIdList)) {
            fieldService.deleteByIdList(fieldIdList);
        }
        if (!CollectionUtils.isEmpty(eventTriggerIdList)) {
            eventTriggerService.deleteByIdList(eventTriggerIdList);
        }
        if (!CollectionUtils.isEmpty(submitRuleIdList)) {
            submitRuleService.deleteByIdList(submitRuleIdList);
        }
        if (!CollectionUtils.isEmpty(fieldLinkageRuleIdList)) {
            fieldLinkageRuleService.deleteByIdList(fieldLinkageRuleIdList);
        }
        if (!CollectionUtils.isEmpty(fieldLinkedDisplayRuleIdList)) {
            fieldLinkedDisplayRuleService.deleteByIdList(fieldLinkedDisplayRuleIdList);
        }
        if (!CollectionUtils.isEmpty(fieldTableRuleIdList)) {
            fieldTableRuleService.deleteByIdList(fieldTableRuleIdList);
        }
        if (!CollectionUtils.isEmpty(tableDataGroupAggregateIdList)) {
            tableDataGroupAggregateService.deleteByIdList(tableDataGroupAggregateIdList);
        }
        if (!CollectionUtils.isEmpty(tableRuleIdList1)) {
            tableDataRowVerifyService.deleteByIdList(tableRuleIdList1);
        }
        if (!CollectionUtils.isEmpty(tableRuleIdList2)) {
            tableDataRowEditService.deleteByIdList(tableRuleIdList2);
        }
        if (!CollectionUtils.isEmpty(tableDataRelationIdList)) {
            tableDataRelationService.deleteByIdList(tableDataRelationIdList);
        }
        if (!CollectionUtils.isEmpty(tableRuleIdList3)) {
            crossTableDataEditService.deleteByIdList(tableRuleIdList3);
        }
        if (!CollectionUtils.isEmpty(tableRuleIdList4)) {
            crossTableDataVerifyService.deleteByIdList(tableRuleIdList4);
        }
        log.info("删除页面完成,pageId:{}", checkPageId);
    }

    public Page getItems(String pageCode, String identityCode) {
        CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, identityCode);
        if (pageDO == null) {
            throw new ServiceException(500, "目标录入页面不存在,identityCode");
        }
        Page page = new Page();
        BeanUtils.copyProperties(pageDO, page);
        page.setId(pageDO.getId().toString());

        Long pageId = pageDO.getId();
        CfgFormDO formDO = formService.getDOByPageId(pageId);
        com.bone.lowcode.infra.application.vo.page.structure.Form form = new com.bone.lowcode.infra.application.vo.page.structure.Form();
        form.setId(formDO.getId().toString());
        form.setName(formDO.getName());
        page.getBody().add(form);

        Long formId = formDO.getId();
        List<CfgBlockDO> blockDOList = blockService.getDOListByFormId(formId);
        for (CfgBlockDO blockDO : blockDOList) {
            com.bone.lowcode.infra.application.vo.page.structure.Block block = new com.bone.lowcode.infra.application.vo.page.structure.Block();
            block.setId(blockDO.getId().toString());
            block.setName(blockDO.getName());
            block.setSequenceNumber(blockDO.getSequenceNumber());
            form.getBody().add(block);

            Long blockId = blockDO.getId();
            List<CfgFieldsetDO> fieldsetDOList = fieldsetService.getDOByBlockId(blockId);
            for (CfgFieldsetDO fieldsetDO : fieldsetDOList) {
                Fieldset fieldset = new Fieldset();
                fieldset.setId(fieldsetDO.getId().toString());
                fieldset.setName(fieldsetDO.getName());
                fieldset.setSequence(fieldsetDO.getSequence());
                block.getFieldsetList().add(fieldset);

                Long modelId = fieldsetDO.getModelId();
                if (modelId != null) {
                    CfgModelDO modelDO = modelService.getDOById(modelId);
                    if (modelDO != null) {
                        Model model = new Model();
                        BeanUtils.copyProperties(modelDO, model);
                        model.setId(modelDO.getId().toString());
                        fieldset.getModelList().add(model);
                    }
                }
            }

            List<CfgTableDO> tableDOList = tableService.getDOByBlockId(blockId);
            for (CfgTableDO tableDO : tableDOList) {
                com.bone.lowcode.infra.application.vo.page.structure.Table table = new com.bone.lowcode.infra.application.vo.page.structure.Table();
                table.setId(tableDO.getId().toString());
                table.setName(tableDO.getName());
                table.setSequence(tableDO.getSequence());
                block.getTableList().add(table);

                List<Long> modelIds = tableDO.getModelIds();
                if (!CollectionUtils.isEmpty(modelIds)) {
                    List<CfgModelDO> modelDOList = modelService.getDOListByIdList(modelIds);
                    for (CfgModelDO modelDO : modelDOList) {
                        Model model = new Model();
                        BeanUtils.copyProperties(modelDO, model);
                        model.setId(modelDO.getId().toString());
                        table.getModelList().add(model);
                    }
                }
            }
        }
        return page;
    }

    public TableRules getRestRule(String pageCode, String bizIdentityCode, String type) {
        //type: 1 编辑态;2 发布态

        List<CfgTableDO> tableDOList = new ArrayList<>();
        Map<Long, CfgFieldDO> fieldDOMap = new HashMap<>();
        List<CfgTableDataGroupAggregateDO> groupAggregateDOList = new ArrayList<>();
        List<CfgTableDataRowEditDO> rowEditRuleDOList = new ArrayList<>();
        List<CfgTableDataRowVerifyDO> rowVerifyDOList = new ArrayList<>();
        List<CfgTableDataRelationDO> relationDOList = new ArrayList<>();
        List<CfgTableDataCrossEditDO> crossEditDOList = new ArrayList<>();
        List<CfgTableDataCrossVerifyDO> crossVerifyDOList = new ArrayList<>();

        if (PublishStatusEnum.EDIT.getCode().equals(type)) {
            CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, bizIdentityCode);
            if (pageDO == null) {
                throw new ServiceException(500, "目标页面不存在,pageCode:" + pageCode + ",identityCode:" + bizIdentityCode);
            }
            Long pageId = pageDO.getId();
            tableDOList = tableService.getDOListByPageId(pageId);
            if (CollectionUtils.isEmpty(tableDOList)) {
                return new TableRules();
            }

            List<Long> modelIdList = tableDOList.stream().map(CfgTableDO::getModelIds).flatMap((Function<List<Long>, Stream<Long>>) Collection::stream).toList();
            if (!CollectionUtils.isEmpty(modelIdList)) {
                List<CfgFieldDO> fieldDOList = fieldService.getDOListByModelIds(modelIdList);
                fieldDOMap = fieldDOList.stream().collect(Collectors.toMap(CfgFieldDO::getId, i -> i));
            }
            groupAggregateDOList = tableDataGroupAggregateService.getDOListByPageId(pageId);
            rowEditRuleDOList = tableDataRowEditService.getEnableByPageId(pageId);
            rowVerifyDOList = tableDataRowVerifyService.getEnableByPageId(pageId);
            relationDOList = tableDataRelationService.getDOListByPageId(pageId);
            crossEditDOList = crossTableDataEditService.getEnableByPageId(pageId);
            crossVerifyDOList = crossTableDataVerifyService.getEnableByPageId(pageId);
        } else if (PublishStatusEnum.PUBLISH.getCode().equals(type)) {
            byte versionType = StringUtils.hasText(bizIdentityCode) ? PageTypeEnum.BIZ_IDENTITY.getCode() : PageTypeEnum.TEMPLATE.getCode();
            CfgVersionDO versionDO = versionService.getLastByType(versionType, bizIdentityCode);
            if (versionDO == null) {
                throw new ServiceException(500, "目标主体的页面未发布,identityCode:" + bizIdentityCode);
            }

            CfgReleasedPageDO releasedPageDO = releasedPageService.getDOByVersion(versionDO.getId(), pageCode);
            if (releasedPageDO == null || !StringUtils.hasText(releasedPageDO.getPageInfo())) {
                throw new ServiceException(500, "目标主体的页面发布态数据不存在,identityCode:" + bizIdentityCode + ",pageCode:" + pageCode);
            }

            BasicPageInfo basicPageInfo = JSON.parseObject(releasedPageDO.getPageInfo(), BasicPageInfo.class);
            tableDOList = basicPageInfo.getTableDOList();
            if (!CollectionUtils.isEmpty(basicPageInfo.getFieldDOList())) {
                fieldDOMap = basicPageInfo.getFieldDOList().stream().collect(Collectors.toMap(CfgFieldDO::getId, i -> i));
            }
            groupAggregateDOList = basicPageInfo.getTableDataGroupAggregateDOList();
            rowEditRuleDOList = basicPageInfo.getTableDataRowEditDOList();
            if (!CollectionUtils.isEmpty(rowEditRuleDOList)) {
                rowEditRuleDOList = rowEditRuleDOList.stream().filter(i -> i.getStatus() == StatusEnum.YES.getCode()).toList();
            }

            rowVerifyDOList = basicPageInfo.getTableDataRowVerifyDOList();
            if (!CollectionUtils.isEmpty(rowVerifyDOList)) {
                rowVerifyDOList = rowVerifyDOList.stream().filter(i -> i.getStatus() == StatusEnum.YES.getCode()).toList();
            }

            relationDOList = basicPageInfo.getTableDataRelationDOList();
            crossEditDOList = basicPageInfo.getCrossTableDataEditDOList();
            if (!CollectionUtils.isEmpty(crossEditDOList)) {
                crossEditDOList = crossEditDOList.stream().filter(i -> i.getStatus() == StatusEnum.YES.getCode()).toList();
            }

            crossVerifyDOList = basicPageInfo.getCrossTableDataVerifyDOList();
            if (!CollectionUtils.isEmpty(crossVerifyDOList)) {
                crossVerifyDOList = crossVerifyDOList.stream().filter(i -> i.getStatus() == StatusEnum.YES.getCode()).toList();
            }
        } else {
            throw new ServiceException(500, "不支持的规则状态:" + type);
        }

        return getTableRules(tableDOList, fieldDOMap, groupAggregateDOList, rowEditRuleDOList, rowVerifyDOList,
                relationDOList, crossEditDOList, crossVerifyDOList);
    }

    private TableRules getTableRules(List<CfgTableDO> tableDOList, Map<Long, CfgFieldDO> fieldDOMap,
                                     List<CfgTableDataGroupAggregateDO> groupAggregateDOList,
                                     List<CfgTableDataRowEditDO> rowEditRuleDOList, List<CfgTableDataRowVerifyDO> rowVerifyDOList,
                                     List<CfgTableDataRelationDO> relationDOList,
                                     List<CfgTableDataCrossEditDO> crossEditDOList, List<CfgTableDataCrossVerifyDO> crossVerifyDOList) {
        TableRules tableRules = new TableRules();
        List<AggregateRuleVO> aggregateRuleVOList = tableApplicationService.getAggregateRuleVOList(groupAggregateDOList);
        tableRules.setGroupAggregateRuleVOList(aggregateRuleVOList);

        List<TableDataSummaryRule> dataSummaryRuleList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(tableDOList)) {
            dataSummaryRuleList = tableDOList.stream()
                    .map(i -> new TableDataSummaryRule(i.getId().toString(), i.getDataSummaryRules()))
                    .filter(i -> !CollectionUtils.isEmpty(i.getDataSummaryRuleList()))
                    .toList();
        }
        tableRules.setSummaryRuleList(dataSummaryRuleList);

        List<TableRowEditRuleVO> rowEditRuleVOList = tableRuleApplicationService.getTableRowEditRule(rowEditRuleDOList, fieldDOMap);
        tableRules.setRowEditRuleVOList(rowEditRuleVOList);

        List<TableRowVerifyRuleVO> rowVerifyRuleVOList = tableRuleApplicationService.getTableRowVerifyRule(rowVerifyDOList, fieldDOMap);
        tableRules.setRowVerifyRuleVOList(rowVerifyRuleVOList);

        List<CrossTableDataEditVO> crossTableEditVOList = tableRuleApplicationService.getCrossTableEditVOList(relationDOList, crossEditDOList, fieldDOMap);
        tableRules.setCrossTableDataEditVOList(crossTableEditVOList);

        List<CrossTableDataVerifyRuleVO> crossTableDataVerifyRuleVOList = tableRuleApplicationService.getCrossTableDataVerifyRule(relationDOList, crossVerifyDOList, fieldDOMap);
        tableRules.setCrossTableDataVerifyRuleVOList(crossTableDataVerifyRuleVOList);
        return tableRules;
    }

    private List<LinkedDisplayRuleVO> getLinkedDisplayRuleList(List<FieldLinkedDisplayRule> linkedDisplayRuleList, Map<Long, CfgFieldDO> fieldDOMap) {
        if (CollectionUtils.isEmpty(linkedDisplayRuleList) || fieldDOMap == null) {
            return new ArrayList<>();
        }

        List<LinkedDisplayRuleVO> res = new ArrayList<>();
        for (FieldLinkedDisplayRule rule : linkedDisplayRuleList) {
            LinkedDisplayRuleVO vo = new LinkedDisplayRuleVO();
            res.add(vo);
            vo.setSelectFieldId(rule.getSelectFieldId().toString());

            List<LinkedDisplayRuleEntry> affectFieldList = rule.getAffectField();
            List<FieldLinkedDisplayRuleVO> list = affectFieldList.stream().map(i -> {
                FieldLinkedDisplayRuleVO re = new FieldLinkedDisplayRuleVO();
                if (i.getType() != null) {
                    re.setType(i.getType());
                }
                re.setExtraProperty(i.getExtraProperty());
                re.setScript(i.getScript());
                Long fieldId = i.getFieldId();
                re.setFieldId(fieldId.toString());
                if (fieldDOMap.containsKey(fieldId)) {
                    CfgFieldDO affectedField = fieldDOMap.get(fieldId);
                    re.setBizName(affectedField.getBizName());
                    re.setBizCode(affectedField.getBizCode());
                    re.setDataBinding(affectedField.getDataBinding());
                }
                return re;
            }).toList();
            vo.setOtherField(list);
        }
        return res;
    }

    public Boolean pullRulesFromBase(String bizIdentityCode) {
        CfgVersionDO versionDO = versionService.getLastByType(PageTypeEnum.TEMPLATE.getCode(), null);
        if (versionDO == null) {
            throw new ServiceException(500, "基础页面未发布");
        }

        List<String> pageCodeList = BasicPageCodeEnum.getAllBasicPageCode();
        Date now = new Date();

        List<CfgSubmitRuleDO> newSubmitRules = Collections.synchronizedList(new ArrayList<>());
        List<CfgFieldLinkageRuleDO> newFieldLinkageRules = Collections.synchronizedList(new ArrayList<>());
        List<FieldLinkedDisplayRule> newFieldLinkedDisplayRules = Collections.synchronizedList(new ArrayList<>());
        List<CfgFieldTableRule> newFieldTableRules = Collections.synchronizedList(new ArrayList<>());
        List<CfgTableDataRowVerifyDO> newRowVerifyList = Collections.synchronizedList(new ArrayList<>());
        List<CfgTableDataRowEditDO> newRowEditList = Collections.synchronizedList(new ArrayList<>());
        List<CfgTableDataCrossVerifyDO> newCrossVerifyList = Collections.synchronizedList(new ArrayList<>());
        List<CfgTableDataCrossEditDO> newCrossEditList = Collections.synchronizedList(new ArrayList<>());
        List<CfgTableDataGroupAggregateDO> newAggregateList = Collections.synchronizedList(new ArrayList<>());
        List<CfgTableDO> newTableList = Collections.synchronizedList(new ArrayList<>());

        long batchId = System.currentTimeMillis();
        CountDownLatch count = new CountDownLatch(pageCodeList.size());
        for (String pageCode : pageCodeList) {
            commonPool.execute(() -> {
                try {
                    handOnePage(versionDO.getId(), pageCode, bizIdentityCode, now,
                            newSubmitRules, newFieldLinkageRules, newFieldLinkedDisplayRules, newFieldTableRules, newRowVerifyList,
                            newRowEditList, newCrossVerifyList, newCrossEditList, newAggregateList, newTableList);
                } catch (Exception e) {
                    log.error("拉取基础页面规则发生异常, pageCode:{}, batchId:{}", pageCode, batchId, e);
                } finally {
                    count.countDown();
                }
            });
        }
        try {
            count.await();
        } catch (InterruptedException e) {
            log.error("拉取基础页面规则发生中断异常, batchId:{}:", batchId, e);
        }

        log.info("准备同步规则,batchId:{}," +
                        "\nsubmitRule, 数量:{}, rules:{} " +
                        "\nfieldLinkageRule, 数量:{}, rules:{} " +
                        "\nfieldLinkedDisplayRule, 数量:{}, rules:{} " +
                        "\nfieldTableRule, 数量:{}, rules:{} " +
                        "\nRowVerifyRule, 数量:{}, rules:{} " +
                        "\nRowEditRule, 数量:{}, rules:{} " +
                        "\nCrossVerifyRule, 数量:{}, rules:{} " +
                        "\nCrossEditRule, 数量:{}, rules:{} " +
                        "\nAggregateRule, 数量:{}, rules:{} " +
                        "\nsummaryRule, 数量:{}, rules:{} ",
                batchId,
                newSubmitRules.size(), JSON.toJSONString(newSubmitRules),
                newFieldLinkageRules.size(), JSON.toJSONString(newFieldLinkageRules),
                newFieldLinkedDisplayRules.size(), JSON.toJSONString(newFieldLinkedDisplayRules),
                newFieldTableRules.size(), JSON.toJSONString(newFieldTableRules),
                newRowVerifyList.size(), JSON.toJSONString(newRowVerifyList),
                newRowEditList.size(), JSON.toJSONString(newRowEditList),
                newCrossVerifyList.size(), JSON.toJSONString(newCrossVerifyList),
                newCrossEditList.size(), JSON.toJSONString(newCrossEditList),
                newAggregateList.size(), JSON.toJSONString(newAggregateList),
                newTableList.size(), JSON.toJSONString(newTableList));
        Boolean flag = transactionTemplate.execute(status -> {
            try {
                if (!CollectionUtils.isEmpty(newSubmitRules)) {
                    submitRuleService.batchSave(newSubmitRules);
                }
                if (!CollectionUtils.isEmpty(newFieldLinkageRules)) {
                    fieldLinkageRuleService.batchSave(newFieldLinkageRules);
                }
                if (!CollectionUtils.isEmpty(newFieldLinkedDisplayRules)) {
                    fieldLinkedDisplayRuleService.batchSave(newFieldLinkedDisplayRules);
                }
                if (!CollectionUtils.isEmpty(newFieldTableRules)) {
                    fieldTableRuleService.batchSave(newFieldTableRules);
                }
                if (!CollectionUtils.isEmpty(newRowVerifyList)) {
                    tableDataRowVerifyService.batchSave(newRowVerifyList);
                }
                if (!CollectionUtils.isEmpty(newRowEditList)) {
                    tableDataRowEditService.batchSave(newRowEditList);
                }
                if (!CollectionUtils.isEmpty(newCrossVerifyList)) {
                    crossTableDataVerifyService.batchSave(newCrossVerifyList);
                }
                if (!CollectionUtils.isEmpty(newCrossEditList)) {
                    crossTableDataEditService.batchSave(newCrossEditList);
                }
                if (!CollectionUtils.isEmpty(newAggregateList)) {
                    tableDataGroupAggregateService.batchSave(newAggregateList);
                }
                if (!CollectionUtils.isEmpty(newTableList)) {
                    tableService.batchUpdateById(newTableList);
                }
                return true;
            } catch (Exception e) {
                log.info("batchId:{},拉取基础页面的规则到指定主体({})下的页面发生异常:", batchId, bizIdentityCode, e);
                status.setRollbackOnly();
                return false;
            }
        });
        log.info("同步规则结果:{},batchId:{}", flag, batchId);
        return flag;
    }

    private void handOnePage(Long versionId, String pageCode, String bizIdentityCode, Date now,
                             List<CfgSubmitRuleDO> newSubmitRules,
                             List<CfgFieldLinkageRuleDO> newFieldLinkageRules,
                             List<FieldLinkedDisplayRule> newFieldLinkedDisplayRules,
                             List<CfgFieldTableRule> newFieldTableRules,
                             List<CfgTableDataRowVerifyDO> newRowVerifyList,
                             List<CfgTableDataRowEditDO> newRowEditList,
                             List<CfgTableDataCrossVerifyDO> newCrossVerifyList,
                             List<CfgTableDataCrossEditDO> newCrossEditList,
                             List<CfgTableDataGroupAggregateDO> newAggregateList,
                             List<CfgTableDO> newTableList) {
        CfgReleasedPageDO releasedPageDO = releasedPageService.getDOByVersion(versionId, pageCode);
        if (releasedPageDO == null || !StringUtils.hasText(releasedPageDO.getPageInfo())) {
            throw new ServiceException(500, "基础页面发布态数据不存在,code:" + pageCode);
        }
        BasicPageInfo basicPageInfo = JSON.parseObject(releasedPageDO.getPageInfo(), BasicPageInfo.class);

        CfgPageDO targetPage = pageService.getByPageCodeAndIdentityCode(pageCode, bizIdentityCode);
        if (targetPage == null) {
            throw new ServiceException(500, "目标页面不存在,pageCode:" + pageCode + ", bizIdentityCode:" + bizIdentityCode);
        }
        Long targetPageId = targetPage.getId();

        //submitRule
        List<CfgSubmitRuleDO> submitRuleList = basicPageInfo.getSubmitRuleList();
        copySubmitRule(now, newSubmitRules, pageCode, submitRuleList, targetPageId);

        //fieldLinkageRule
        List<CfgFieldLinkageRuleDO> fieldLinkageRuleList = basicPageInfo.getFieldLinkageRuleList();
        copyFieldLinkageRule(now, newFieldLinkageRules, pageCode, fieldLinkageRuleList, targetPageId);

        //fieldLinkedDisplayRule
        List<FieldLinkedDisplayRule> fieldLinkedDisplayRuleList = basicPageInfo.getFieldLinkedDisplayRuleList();
        copyFieldLinkedDisplayRule(now, newFieldLinkedDisplayRules, pageCode, fieldLinkedDisplayRuleList, targetPageId);

        //fieldTableRule
        List<CfgFieldTableRule> fieldTableRuleList = basicPageInfo.getFieldTableRuleList();
        copyFieldTableRule(now, newFieldTableRules, pageCode, fieldTableRuleList, targetPageId);

        //RowVerifyRule
        List<CfgTableDataRowVerifyDO> verifyDOList = basicPageInfo.getTableDataRowVerifyDOList();
        copyRowVerifyRule(now, newRowVerifyList, pageCode, verifyDOList, targetPageId);

        //RowEditRule
        List<CfgTableDataRowEditDO> rowEditDOList = basicPageInfo.getTableDataRowEditDOList();
        copyRowEditRule(now, newRowEditList, pageCode, rowEditDOList, targetPageId);

        //CrossVerifyRule
        List<CfgTableDataCrossVerifyDO> crossVerifyDOList = basicPageInfo.getCrossTableDataVerifyDOList();
        copyCrossVerifyRule(now, newCrossVerifyList, pageCode, crossVerifyDOList, targetPageId);

        //CrossEditRule
        List<CfgTableDataCrossEditDO> crossEditDOList = basicPageInfo.getCrossTableDataEditDOList();
        copyCrossEditRule(now, newCrossEditList, pageCode, crossEditDOList, targetPageId);

        //GroupAggregateRule
        List<CfgTableDataGroupAggregateDO> aggregateDOList = basicPageInfo.getTableDataGroupAggregateDOList();
        copyGroupAggregateRule(now, newAggregateList, pageCode, aggregateDOList, targetPageId);

        //summaryRule
        List<CfgTableDO> tableDOList = basicPageInfo.getTableDOList();
        copySummaryRule(newTableList, pageCode, tableDOList, targetPageId);
    }

    private void copySummaryRule(List<CfgTableDO> allTableList, String pageCode, List<CfgTableDO> oldTableList, Long targetPageId) {
        if (CollectionUtils.isEmpty(oldTableList)) {
            return;
        }

        List<CfgTableDO> newTableList = new ArrayList<>();
        for (CfgTableDO table : oldTableList) {
            try {
                List<DataSummaryRule> summaryRuleList = table.getDataSummaryRules();
                if (CollectionUtils.isEmpty(summaryRuleList)) {
                    continue;
                }

                summaryRuleList.forEach(rule -> {
                    CfgFieldDO field = getSameOriginField(Long.parseLong(rule.getFieldId()), targetPageId);
                    rule.setFieldId(field.getId().toString());
                });

                CfgTableDO targetTableDO = tableService.getDOByNameAndPageId(table.getName(), targetPageId);
                if (targetTableDO == null) {
                    continue;
                }

                CfgTableDO newTableDO = new CfgTableDO();
                newTableDO.setId(targetTableDO.getId());
                newTableDO.setDataSummaryRules(summaryRuleList);
                newTableList.add(newTableDO);
            } catch (Exception e) {
                log.info("复制SummaryRule发生异常, tableId:{}, targetPageId:{}", table.getId(), targetPageId, e);
            }
        }
        allTableList.addAll(newTableList);
        log.info("前summaryRule,pageCode:{},oldRules:{}", pageCode, oldTableList);
        log.info("后summaryRule,pageCode:{},newRules:{}", pageCode, newTableList);
    }

    private void copyGroupAggregateRule(Date now, List<CfgTableDataGroupAggregateDO> allAggregateList, String pageCode, List<CfgTableDataGroupAggregateDO> oldAggregateList, Long targetPageId) {
        if (CollectionUtils.isEmpty(oldAggregateList)) {
            return;
        }

        List<CfgTableDataGroupAggregateDO> existAggregateList = tableDataGroupAggregateService.getDOListByPageId(targetPageId);
        Set<Long> sourceIdSet9 = existAggregateList.stream().map(CfgTableDataGroupAggregateDO::getSourceId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<CfgTableDataGroupAggregateDO> newAggregateList = new ArrayList<>();
        for (CfgTableDataGroupAggregateDO rule : oldAggregateList) {
            if (sourceIdSet9.contains(rule.getId())) {
                continue;
            }

            try {
                CfgTableDataGroupAggregateDO newRule = copyGroupAggregateRule(now, targetPageId, rule);
                newAggregateList.add(newRule);
            } catch (Exception e) {
                log.info("复制GroupAggregateRule发生异常, ruleId:{}, targetPageId:{}", rule.getId(), targetPageId, e);
            }
        }
        allAggregateList.addAll(newAggregateList);
        log.info("前GroupAggregateRule,pageCode:{},oldRules:{}", pageCode, oldAggregateList);
        log.info("后GroupAggregateRule,pageCode:{},newRules:{}", pageCode, newAggregateList);
    }

    private void copyCrossEditRule(Date now, List<CfgTableDataCrossEditDO> allCrossEditList, String pageCode, List<CfgTableDataCrossEditDO> oldCrossEditList, Long targetPageId) {
        if (CollectionUtils.isEmpty(oldCrossEditList)) {
            return;
        }

        List<CfgTableDataCrossEditDO> existCrossEditList = crossTableDataEditService.getDOListByPageId(targetPageId);
        Set<Long> sourceIdSet8 = existCrossEditList.stream().map(CfgTableDataCrossEditDO::getSourceId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<CfgTableDataCrossEditDO> newCrossEditList = new ArrayList<>();
        for (CfgTableDataCrossEditDO rule : oldCrossEditList) {
            if (sourceIdSet8.contains(rule.getId())) {
                continue;
            }

            try {
                CfgTableDataCrossEditDO newRule = copyCrossEditRule(now, targetPageId, rule);
                newCrossEditList.add(newRule);
            } catch (Exception e) {
                log.info("复制CrossEditRule发生异常, ruleId:{}, targetPageId:{}", rule.getId(), targetPageId, e);
            }
        }
        allCrossEditList.addAll(newCrossEditList);
        log.info("前CrossEditRule,pageCode:{},oldRules:{}", pageCode, oldCrossEditList);
        log.info("后CrossEditRule,pageCode:{},newRules:{}", pageCode, newCrossEditList);
    }

    private void copyCrossVerifyRule(Date now, List<CfgTableDataCrossVerifyDO> allCrossVerifyList, String pageCode, List<CfgTableDataCrossVerifyDO> oldCrossVerifyList, Long targetPageId) {
        if (CollectionUtils.isEmpty(oldCrossVerifyList)) {
            return;
        }

        List<CfgTableDataCrossVerifyDO> existCrossVerifyList = crossTableDataVerifyService.getDOListByPageId(targetPageId);
        Set<Long> sourceIdSet7 = existCrossVerifyList.stream().map(CfgTableDataCrossVerifyDO::getSourceId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<CfgTableDataCrossVerifyDO> newCrossVerifyList = new ArrayList<>();
        for (CfgTableDataCrossVerifyDO rule : oldCrossVerifyList) {
            if (sourceIdSet7.contains(rule.getId())) {
                continue;
            }

            try {
                CfgTableDataCrossVerifyDO newRule = copyCrossVerifyRule(now, targetPageId, rule);
                newCrossVerifyList.add(newRule);
            } catch (Exception e) {
                log.info("复制CrossVerifyRule发生异常, ruleId:{}, targetPageId:{}", rule.getId(), targetPageId, e);
            }
        }
        allCrossVerifyList.addAll(newCrossVerifyList);
        log.info("前CrossVerifyRule,pageCode:{},oldRules:{}", pageCode, oldCrossVerifyList);
        log.info("后CrossVerifyRule,pageCode:{},newRules:{}", pageCode, newCrossVerifyList);
    }

    private void copyRowEditRule(Date now, List<CfgTableDataRowEditDO> allRowEditList, String pageCode, List<CfgTableDataRowEditDO> oldRowEditList, Long targetPageId) {
        if (CollectionUtils.isEmpty(oldRowEditList)) {
            return;
        }

        List<CfgTableDataRowEditDO> existRowEditList = tableDataRowEditService.getDOListByPageId(targetPageId);
        Set<Long> sourceIdSet6 = existRowEditList.stream().map(CfgTableDataRowEditDO::getSourceId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<CfgTableDataRowEditDO> newRowEditList = new ArrayList<>();
        for (CfgTableDataRowEditDO rule : oldRowEditList) {
            if (sourceIdSet6.contains(rule.getId())) {
                continue;
            }

            try {
                CfgTableDataRowEditDO newRule = copyRowEditRule(now, targetPageId, rule);
                newRowEditList.add(newRule);
            } catch (Exception e) {
                log.info("复制RowEditRule发生异常, ruleId:{}, targetPageId:{}", rule.getId(), targetPageId, e);
            }
        }
        allRowEditList.addAll(newRowEditList);
        log.info("前RowEditRule,pageCode:{},oldRules:{}", pageCode, oldRowEditList);
        log.info("后RowEditRule,pageCode:{},newRules:{}", pageCode, newRowEditList);
    }

    private void copyRowVerifyRule(Date now, List<CfgTableDataRowVerifyDO> allRowVerifyList, String pageCode, List<CfgTableDataRowVerifyDO> oldRowVerifyList, Long targetPageId) {
        if (CollectionUtils.isEmpty(oldRowVerifyList)) {
            return;
        }

        List<CfgTableDataRowVerifyDO> existRowVerifyList = tableDataRowVerifyService.getDOListByPageId(targetPageId);
        Set<Long> sourceIdSet5 = existRowVerifyList.stream().map(CfgTableDataRowVerifyDO::getSourceId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<CfgTableDataRowVerifyDO> newRowVerifyList = new ArrayList<>();
        for (CfgTableDataRowVerifyDO rule : oldRowVerifyList) {
            if (sourceIdSet5.contains(rule.getId())) {
                continue;
            }

            try {
                CfgTableDataRowVerifyDO newRule = copyRowVerifyRule(now, targetPageId, rule);
                newRowVerifyList.add(newRule);
            } catch (Exception e) {
                log.info("复制RowVerifyRule发生异常, ruleId:{}, targetPageId:{}", rule.getId(), targetPageId, e);
            }
        }
        allRowVerifyList.addAll(newRowVerifyList);
        log.info("前RowVerifyRule,pageCode:{},oldRules:{}", pageCode, oldRowVerifyList);
        log.info("后RowVerifyRule,pageCode:{},newRules:{}", pageCode, newRowVerifyList);
    }

    private void copyFieldTableRule(Date now, List<CfgFieldTableRule> allFieldTableRules, String pageCode, List<CfgFieldTableRule> oldFieldTableRules, Long targetPageId) {
        if (CollectionUtils.isEmpty(oldFieldTableRules)) {
            return;
        }

        List<CfgFieldTableRule> existFieldTableRules = fieldTableRuleService.getByPageId(targetPageId);
        Set<Long> sourceIdSet4 = existFieldTableRules.stream().map(CfgFieldTableRule::getSourceId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<CfgFieldTableRule> newFieldTableRules = new ArrayList<>();
        for (CfgFieldTableRule rule : oldFieldTableRules) {
            if (sourceIdSet4.contains(rule.getId())) {
                continue;
            }

            try {
                CfgFieldTableRule newRule = copyFieldTableRule(now, targetPageId, rule);
                newFieldTableRules.add(newRule);
            } catch (Exception e) {
                log.info("复制FieldTableRule发生异常, ruleId:{}, targetPageId:{}", rule.getId(), targetPageId, e);
            }
        }
        allFieldTableRules.addAll(newFieldTableRules);
        log.info("前FieldTableRule,pageCode:{},oldRules:{}", pageCode, oldFieldTableRules);
        log.info("后FieldTableRule,pageCode:{},newRules:{}", pageCode, newFieldTableRules);
    }

    private void copyFieldLinkedDisplayRule(Date now, List<FieldLinkedDisplayRule> allFieldLinkedDisplayRules, String pageCode, List<FieldLinkedDisplayRule> oldFieldLinkedDisplayRules, Long targetPageId) {
        if (CollectionUtils.isEmpty(oldFieldLinkedDisplayRules)) {
            return;
        }

        List<FieldLinkedDisplayRule> existFieldLinkedDisplayRules = fieldLinkedDisplayRuleService.getByPageId(targetPageId);
        Set<Long> sourceIdSet3 = existFieldLinkedDisplayRules.stream().map(FieldLinkedDisplayRule::getSourceId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> fieldIds = existFieldLinkedDisplayRules.stream().map(FieldLinkedDisplayRule::getSelectFieldId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<FieldLinkedDisplayRule> newFieldLinkedDisplayRules = new ArrayList<>();
        for (FieldLinkedDisplayRule rule : oldFieldLinkedDisplayRules) {
            if (sourceIdSet3.contains(rule.getId())) {
                continue;
            }

            try {
                FieldLinkedDisplayRule newRule = copyFieldLinkedDisplayRule(now, targetPageId, rule);
                if (fieldIds.contains(newRule.getSelectFieldId())) {
                    continue;//同一字段不能有多个此类规则
                }

                newFieldLinkedDisplayRules.add(newRule);
            } catch (Exception e) {
                log.info("复制FieldLinkedDisplayRule发生异常, ruleId:{}, targetPageId:{}", rule.getId(), targetPageId, e);
            }
        }
        allFieldLinkedDisplayRules.addAll(newFieldLinkedDisplayRules);
        log.info("前FieldLinkedDisplayRule,pageCode:{},oldRules:{}", pageCode, oldFieldLinkedDisplayRules);
        log.info("后FieldLinkedDisplayRule,pageCode:{},newRules:{}", pageCode, newFieldLinkedDisplayRules);
    }

    private void copyFieldLinkageRule(Date now, List<CfgFieldLinkageRuleDO> allFieldLinkageRules, String pageCode, List<CfgFieldLinkageRuleDO> oldFieldLinkageRules, Long targetPageId) {
        if (CollectionUtils.isEmpty(oldFieldLinkageRules)) {
            return;
        }

        List<CfgFieldLinkageRuleDO> existFieldLinkageRules = fieldLinkageRuleService.getRuleByPageId(targetPageId);
        Set<Long> sourceIdSet2 = existFieldLinkageRules.stream().map(CfgFieldLinkageRuleDO::getSourceId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<CfgFieldLinkageRuleDO> newFieldLinkageRules = new ArrayList<>();
        for (CfgFieldLinkageRuleDO rule : oldFieldLinkageRules) {
            if (sourceIdSet2.contains(rule.getId())) {
                continue;
            }

            try {
                CfgFieldLinkageRuleDO newRule = copyFieldLinkageRule(rule, targetPageId, now);
                newFieldLinkageRules.add(newRule);
            } catch (Exception e) {
                log.info("复制FieldLinkageRule发生异常, ruleId:{}, targetPageId:{}", rule.getId(), targetPageId, e);
            }
        }
        allFieldLinkageRules.addAll(newFieldLinkageRules);
        log.info("前FieldLinkageRule,pageCode:{},oldRules:{}", pageCode, oldFieldLinkageRules);
        log.info("后FieldLinkageRule,pageCode:{},newRules:{}", pageCode, newFieldLinkageRules);
    }

    private void copySubmitRule(Date now, List<CfgSubmitRuleDO> allSubmitRules, String pageCode, List<CfgSubmitRuleDO> oldSubmitRules, Long targetPageId) {
        if (CollectionUtils.isEmpty(oldSubmitRules)) {
            return;
        }

        List<CfgSubmitRuleDO> existSubmitRules = submitRuleService.getByPageId(targetPageId);
        Set<Long> sourceIdSet1 = existSubmitRules.stream().map(CfgSubmitRuleDO::getSourceId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<CfgSubmitRuleDO> newSubmitRules = new ArrayList<>();
        for (CfgSubmitRuleDO rule : oldSubmitRules) {
            if (sourceIdSet1.contains(rule.getId())) {
                continue;
            }

            try {
                CfgSubmitRuleDO newRule = copySubmitRule(now, targetPageId, rule);
                newSubmitRules.add(newRule);
            } catch (Exception e) {
                log.info("复制SubmitRule发生异常, ruleId:{}, targetPageId:{}", rule.getId(), targetPageId, e);
            }
        }
        allSubmitRules.addAll(newSubmitRules);
        log.info("前SubmitRule,pageCode:{},oldRules:{}", pageCode, oldSubmitRules);
        log.info("后SubmitRule,pageCode:{},newRules:{}", pageCode, newSubmitRules);
    }

    private CfgTableDataGroupAggregateDO copyGroupAggregateRule(Date now, Long targetPageId, CfgTableDataGroupAggregateDO rule) {
        CfgTableDataGroupAggregateDO newRule = new CfgTableDataGroupAggregateDO();
        BeanUtils.copyProperties(rule, newRule);
        newRule.setId(null);
        newRule.setPageId(targetPageId);
        newRule.setSourceId(rule.getId());
        newRule.setCreateBy(null);
        newRule.setCreateTime(now);
        newRule.setUpdateBy(null);
        newRule.setUpdateTime(null);

        CfgTableDO oldTableDO = tableService.getDOById(newRule.getTableId());
        CfgTableDO newTableDO = tableService.getDOByNameAndPageId(oldTableDO.getName(), targetPageId);
        newRule.setTableId(newTableDO.getId());

        GroupAggregateField groupField = newRule.getGroupField();
        if (groupField != null) {
            CfgFieldDO targetFieldDO = getSameOriginField(Long.parseLong(groupField.getId()), targetPageId);
            groupField.setId(targetFieldDO.getId().toString());
            newRule.setGroupField(groupField);
        }

        List<GroupAggregateField> aggregateFields = newRule.getAggregateField();
        aggregateFields.forEach(field -> {
            CfgFieldDO temTargetField = getSameOriginField(Long.parseLong(field.getId()), targetPageId);
            field.setId(temTargetField.getId().toString());
        });
        newRule.setAggregateField(aggregateFields);

        List<GroupAggregateField> otherField = newRule.getOtherField();
        otherField.forEach(field -> {
            CfgFieldDO temTargetField = getSameOriginField(Long.parseLong(field.getId()), targetPageId);
            field.setId(temTargetField.getId().toString());
        });
        newRule.setOtherField(otherField);
        return newRule;
    }

    private CfgTableDataCrossEditDO copyCrossEditRule(Date now, Long targetPageId, CfgTableDataCrossEditDO rule) {
        CfgTableDataCrossEditDO newRule = new CfgTableDataCrossEditDO();
        BeanUtils.copyProperties(rule, newRule);
        newRule.setId(null);
        newRule.setPageId(targetPageId);
        newRule.setSourceId(rule.getId());
        newRule.setCreateBy(null);
        newRule.setCreateTime(now);
        newRule.setUpdateBy(null);
        newRule.setUpdateTime(null);

        List<CfgTableDataRelationDO> relationDOList = tableDataRelationService.getDOListByPageId(targetPageId);
        newRule.setRelationId(relationDOList.get(0).getId());

        List<Long> oldCurrentTableFieldIds = newRule.getCurrentTableFieldIds();
        List<Long> newCurrentTableFieldIds = oldCurrentTableFieldIds.stream().map(fieldId -> getSameOriginField(fieldId, targetPageId).getId()).toList();
        newRule.setCurrentTableFieldIds(newCurrentTableFieldIds);

        CfgFieldDO newTargetTableField = getSameOriginField(newRule.getTargetTableFieldId(), targetPageId);
        newRule.setTargetTableFieldId(newTargetTableField.getId());
        newRule.setStatus(StatusEnum.NO.getCode());
        return newRule;
    }

    private CfgTableDataCrossVerifyDO copyCrossVerifyRule(Date now, Long targetPageId, CfgTableDataCrossVerifyDO rule) {
        CfgTableDataCrossVerifyDO newRule = new CfgTableDataCrossVerifyDO();
        BeanUtils.copyProperties(rule, newRule);
        newRule.setId(null);
        newRule.setPageId(targetPageId);
        newRule.setSourceId(rule.getId());
        newRule.setCreateBy(null);
        newRule.setCreateTime(now);
        newRule.setUpdateBy(null);
        newRule.setUpdateTime(null);

        List<CfgTableDataRelationDO> relationDOList = tableDataRelationService.getDOListByPageId(targetPageId);
        newRule.setRelationId(relationDOList.get(0).getId());

        List<Long> oldCurrentTableFieldIds = newRule.getCurrentTableFieldIds();
        List<Long> newCurrentTableFieldIds = oldCurrentTableFieldIds.stream().map(fieldId -> getSameOriginField(fieldId, targetPageId).getId()).toList();
        newRule.setCurrentTableFieldIds(newCurrentTableFieldIds);

        CfgFieldDO newTargetTableField = getSameOriginField(newRule.getTargetTableFieldId(), targetPageId);
        newRule.setTargetTableFieldId(newTargetTableField.getId());
        newRule.setStatus(StatusEnum.NO.getCode());
        return newRule;
    }

    private CfgTableDataRowEditDO copyRowEditRule(Date now, Long targetPageId, CfgTableDataRowEditDO rule) {
        CfgTableDataRowEditDO newRule = new CfgTableDataRowEditDO();
        BeanUtils.copyProperties(rule, newRule);
        newRule.setId(null);
        newRule.setPageId(targetPageId);
        newRule.setSourceId(rule.getId());
        newRule.setCreateBy(null);
        newRule.setCreateTime(now);
        newRule.setUpdateBy(null);
        newRule.setUpdateTime(null);

        CfgTableDO oldTableDO = tableService.getDOById(newRule.getTableId());
        CfgTableDO newTableDO = tableService.getDOByNameAndPageId(oldTableDO.getName(), targetPageId);
        newRule.setTableId(newTableDO.getId());

        List<Long> sourceFieldIds = newRule.getSourceFieldIds();
        List<Long> newSourceFieldIds = sourceFieldIds.stream().map(fieldId -> getSameOriginField(fieldId, targetPageId).getId()).toList();
        newRule.setSourceFieldIds(newSourceFieldIds);

        Long oldTargetFieldId = newRule.getTargetFieldId();
        CfgFieldDO newTargetField = getSameOriginField(oldTargetFieldId, targetPageId);
        newRule.setTargetFieldId(newTargetField.getId());
        newRule.setStatus(StatusEnum.NO.getCode());
        return newRule;
    }

    private CfgTableDataRowVerifyDO copyRowVerifyRule(Date now, Long targetPageId, CfgTableDataRowVerifyDO rule) {
        CfgTableDataRowVerifyDO newRule = new CfgTableDataRowVerifyDO();
        BeanUtils.copyProperties(rule, newRule);
        newRule.setId(null);
        newRule.setPageId(targetPageId);
        newRule.setSourceId(rule.getId());
        newRule.setCreateBy(null);
        newRule.setCreateTime(now);
        newRule.setUpdateBy(null);
        newRule.setUpdateTime(null);

        CfgTableDO oldTableDO = tableService.getDOById(newRule.getTableId());
        CfgTableDO newTableDO = tableService.getDOByNameAndPageId(oldTableDO.getName(), targetPageId);
        newRule.setTableId(newTableDO.getId());

        List<Long> fieldIds = newRule.getFieldIds();
        List<Long> newFieldIds = fieldIds.stream().map(fieldId -> getSameOriginField(fieldId, targetPageId).getId()).toList();
        newRule.setFieldIds(newFieldIds);

        String valueType = newRule.getValueType();
        String value = newRule.getValue();
        if (valueType != null && Objects.equals(valueType, ValueTypeEnum.DYNAMIC.getValue()) && StringUtils.hasText(value)) {
            long fieldId = Long.parseLong(value);
            CfgFieldDO targetField = getSameOriginField(fieldId, targetPageId);
            newRule.setValue(targetField.getId().toString());
        }
        newRule.setStatus(StatusEnum.NO.getCode());
        return newRule;
    }

    private CfgFieldTableRule copyFieldTableRule(Date now, Long targetPageId, CfgFieldTableRule rule) {
        CfgFieldTableRule newRule = new CfgFieldTableRule();
        BeanUtils.copyProperties(rule, newRule);
        newRule.setId(null);
        newRule.setPageId(targetPageId);
        newRule.setSourceId(rule.getId());
        newRule.setCreateBy(null);
        newRule.setCreateTime(now);
        newRule.setUpdateBy(null);
        newRule.setUpdateTime(null);

        CfgFieldDO targetFieldDO = getSameOriginField(newRule.getFieldId(), targetPageId);
        newRule.setFieldId(targetFieldDO.getId());

        String valueType = newRule.getSourceValueType();
        String value = newRule.getSourceValue();
        if (valueType != null && Objects.equals(valueType, ValueTypeEnum.DYNAMIC.getValue()) && StringUtils.hasText(value)) {
            long fieldId = Long.parseLong(value);
            CfgFieldDO targetField = getSameOriginField(fieldId, targetPageId);
            newRule.setSourceValue(targetField.getId().toString());
        }

        CfgTableDO oldTableDO = tableService.getDOById(newRule.getTableId());
        CfgTableDO newTableDO = tableService.getDOByNameAndPageId(oldTableDO.getName(), targetPageId);
        newRule.setTableId(newTableDO.getId());
        newRule.setStatus(StatusEnum.NO.getCode());
        return newRule;
    }

    private FieldLinkedDisplayRule copyFieldLinkedDisplayRule(Date now, Long targetPageId, FieldLinkedDisplayRule rule) {
        FieldLinkedDisplayRule newRule = new FieldLinkedDisplayRule();
        BeanUtils.copyProperties(rule, newRule);
        newRule.setId(null);
        newRule.setPageId(targetPageId);
        newRule.setSourceId(rule.getId());
        newRule.setCreateBy(null);
        newRule.setCreateTime(now);
        newRule.setUpdateBy(null);
        newRule.setUpdateTime(null);

        CfgFieldDO targetFieldDO = getSameOriginField(newRule.getSelectFieldId(), targetPageId);
        newRule.setSelectFieldId(targetFieldDO.getId());

        List<LinkedDisplayRuleEntry> affectField = newRule.getAffectField();
        affectField.forEach(entry -> {
            Long fieldId = entry.getFieldId();
            CfgFieldDO targetField = getSameOriginField(fieldId, targetPageId);
            entry.setFieldId(targetField.getId());
        });
        newRule.setAffectField(affectField);
        return newRule;
    }

    private CfgSubmitRuleDO copySubmitRule(Date now, Long targetPageId, CfgSubmitRuleDO rule) {
        CfgSubmitRuleDO newRule = new CfgSubmitRuleDO();
        BeanUtils.copyProperties(rule, newRule);
        newRule.setId(null);
        newRule.setPageId(targetPageId);
        newRule.setSourceId(rule.getId());
        newRule.setCreateBy(null);
        newRule.setCreateTime(now);
        newRule.setUpdateBy(null);
        newRule.setUpdateTime(null);

        List<Long> fieldIds = newRule.getFieldIds();
        List<Long> newFieldIds = fieldIds.stream().map(fieldId -> getSameOriginField(fieldId, targetPageId).getId()).toList();
        newRule.setFieldIds(newFieldIds);

        String valueType = newRule.getValueType();
        String value = newRule.getValue();
        if (valueType != null && Objects.equals(valueType, ValueTypeEnum.DYNAMIC.getValue()) && StringUtils.hasText(value)) {
            long fieldId = Long.parseLong(value);
            CfgFieldDO targetField = getSameOriginField(fieldId, targetPageId);
            newRule.setValue(targetField.getId().toString());
        }
        newRule.setStatus(StatusEnum.NO.getCode());
        return newRule;
    }

    private CfgFieldLinkageRuleDO copyFieldLinkageRule(CfgFieldLinkageRuleDO oldRule, Long targetPageId, Date now) {
        CfgFieldLinkageRuleDO newRule = new CfgFieldLinkageRuleDO();
        BeanUtils.copyProperties(oldRule, newRule);
        newRule.setId(null);
        newRule.setPageId(targetPageId);
        newRule.setSourceId(oldRule.getId());
        newRule.setCreateBy(null);
        newRule.setCreateTime(now);
        newRule.setUpdateBy(null);
        newRule.setUpdateTime(null);

        CfgFieldDO targetField = getSameOriginField(newRule.getFieldId(), targetPageId);
        newRule.setFieldId(targetField.getId());

        String valueType = newRule.getSourceValueType();
        String value = newRule.getSourceValue();
        if (valueType != null && Objects.equals(valueType, ValueTypeEnum.DYNAMIC.getValue()) && StringUtils.hasText(value)) {
            long fieldId = Long.parseLong(value);
            targetField = getSameOriginField(fieldId, targetPageId);
            newRule.setSourceValue(targetField.getId().toString());
        }

        List<Long> sourceFieldIdList = newRule.getTargetFieldIdList();
        List<Long> targetFieldIdList = sourceFieldIdList.stream().map(i -> getSameOriginField(i, targetPageId).getId()).toList();
        newRule.setTargetFieldIdList(targetFieldIdList);

        valueType = newRule.getTargetValueType();
        value = newRule.getTargetValue();
        if (valueType != null && Objects.equals(valueType, ValueTypeEnum.DYNAMIC.getValue()) && StringUtils.hasText(value)) {
            long fieldId = Long.parseLong(value);
            targetField = getSameOriginField(fieldId, targetPageId);
            newRule.setTargetValue(targetField.getId().toString());
        }
        newRule.setStatus(StatusEnum.NO.getCode());
        return newRule;
    }

    public CfgFieldDO getSameOriginField(Long fieldId, Long pageId) {
        CfgFieldDO targetField = null;
        try {
            CfgFieldDO sourceField = fieldService.getDOById(fieldId);
            Long modelId = sourceField.getModelId();
            CfgModelDO sourceModel = modelService.getDOById(modelId);

            CfgModelDO targetModel = modelService.getDOByPageIdAndCode(pageId, sourceModel.getCode());
            targetField = fieldService.getDOByModelIdAndCode(targetModel.getId(), sourceField.getBizCode());
        } catch (Exception e) {
            log.info("获取同源字段发生异常,sourceFieldId:{}, targetPageId:{}", fieldId, pageId, e);
        }
        return targetField;
    }

    public PageHeadVO getPage(String pageCode, String identityCode) {
        CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, identityCode);
        if (Objects.isNull(pageDO)) {
            throw new ServiceException(500, "目标页面不存在");
        }
        PageHeadVO pageVO = new PageHeadVO();
        pageVO.setId(String.valueOf(pageDO.getId()));
        pageVO.setCode(pageDO.getCode());
        pageVO.setBizIdentityCode(pageDO.getBizIdentityCode());
        pageVO.setBusinessFieldEnabled(pageDO.getBusinessFieldEnabled());
        return pageVO;
    }

    public boolean updatePage(UpdatePageDTO pageDO) {
        Byte businessFieldEnabled = pageDO.getBusinessFieldEnabled();
        if (Objects.isNull(businessFieldEnabled)) {
            throw new ServiceException(500, "配置业务字段开关参数非法");
        }
        CfgPageDO existOne = pageService.getDoById(pageDO.getId());
        if (Objects.isNull(existOne)) {
            throw new ServiceException(500, "目标页面不存在");
        }
        return pageService.updatePage(pageDO);

    }

}
