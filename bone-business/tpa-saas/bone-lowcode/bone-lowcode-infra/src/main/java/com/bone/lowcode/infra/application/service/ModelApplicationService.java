package com.bone.lowcode.infra.application.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bone.core.exception.ServiceException;
import com.bone.lowcode.infra.application.convert.FieldConvert;
import com.bone.lowcode.infra.application.convert.ModelConvert;
import com.bone.lowcode.infra.application.dto.model.UpdateModelDTO;
import com.bone.lowcode.infra.application.vo.PageResult;
import com.bone.lowcode.infra.application.vo.model.FieldByModelIdVO;
import com.bone.lowcode.infra.application.vo.model.GetAllModelByPageCodeVo;
import com.bone.lowcode.infra.application.vo.model.GetAllModelVo;
import com.bone.lowcode.infra.application.vo.model.SimpleModelInfo;
import com.bone.lowcode.infra.domain.service.*;
import com.bone.lowcode.infra.domain.util.SnowflakeIdUtil;
import com.bone.lowcode.infra.domain.valueobject.*;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.*;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.MetaBizModelMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ModelApplicationService {

    @Autowired
    private FieldApplicationService fieldApplicationService;

    @Autowired
    private ModelService modelService;

    @Autowired
    private TableService tableService;

    @Autowired
    private BlockService blockService;

    @Autowired
    private FieldsetService fieldsetService;

    @Autowired
    private FieldService fieldService;

    @Autowired
    private PageService pageService;

    @Autowired
    private MetaBizModelMapper metaBizModelMapper;

    @Autowired
    private MetaBizModelService metaBizModelService;

    @Resource
    private TransactionTemplate transactionTemplate;

    public List<GetAllModelByPageCodeVo> getAllModelByPageCode(String pageCode, String bizIdentityCode) {
        CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, bizIdentityCode);
        List<CfgBlockDO> blockDOList = blockService.getDOListByPageId(pageDO.getId());
        List<GetAllModelByPageCodeVo> voList = getModelVoList(blockDOList);
        return voList;
    }

    private List<GetAllModelByPageCodeVo> getModelVoList(List<CfgBlockDO> blockDOList) {
        List<GetAllModelByPageCodeVo> voList = new ArrayList<>();
        for (CfgBlockDO blockDO : blockDOList) {
            List<CfgFieldsetDO> fieldsetDOList = fieldsetService.getDOByBlockId(blockDO.getId());
            for (CfgFieldsetDO fieldsetDO : fieldsetDOList) {
                Long modelId = fieldsetDO.getModelId();
                CfgModelDO modelDO = modelService.getDOById(modelId);
                if (modelDO == null) continue;
                GetAllModelByPageCodeVo vo = getGetAllModelByPageCodeVo(blockDO, (byte) 0, modelDO);
                voList.add(vo);
            }

            List<CfgTableDO> tableDOList = tableService.getDOByBlockId(blockDO.getId());
            for (CfgTableDO tableDO : tableDOList) {
                List<Long> modelIdList = tableDO.getModelIds();
                for (Long modelId : modelIdList) {
                    CfgModelDO modelDO = modelService.getDOById(modelId);
                    if (modelDO == null) continue;
                    GetAllModelByPageCodeVo vo = getGetAllModelByPageCodeVo(blockDO, (byte) 1, modelDO);
                    voList.add(vo);
                }
            }
        }
        return voList;
    }

    public boolean updateAllFieldWithMetaData() {
        List<CfgModelDO> allModelDOList = modelService.getFromMetaDOList();

        List<CfgPageDO> pageDOList = pageService.getAll();
        HashMap<Long, Set<Long>> quotedFieldMap = new HashMap<>();
        for (CfgPageDO pageDO : pageDOList) {
            Set<Long> quotedField = fieldApplicationService.getQuotedField(pageDO.getId());
            quotedFieldMap.put(pageDO.getId(), quotedField);
        }

        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            try {
                for (CfgModelDO modelDO : allModelDOList) {
                    if (StringUtils.hasText(modelDO.getCode())) {
                        checkModelField(modelDO, quotedFieldMap);
                    }
                }
                return true;
            } catch (Exception e) {
                log.error("同步元数据发生异常:", e);
                status.setRollbackOnly();
                return false;
            }
        }));
    }

    public List<CfgModelDO> getModelListByBasicPageCode(String pageCode) {
        List<CfgBlockDO> blockDOList = blockService.getDOListByBasicPageCode(pageCode);
        List<CfgModelDO> allModelDOList = new ArrayList<>();
        for (CfgBlockDO blockDO : blockDOList) {

            List<CfgFieldsetDO> fieldsetDOList = fieldsetService.getDOByBlockId(blockDO.getId());
            for (CfgFieldsetDO fieldsetDO : fieldsetDOList) {
                CfgModelDO modelDO = modelService.getDOById(fieldsetDO.getModelId());
                if (modelDO != null) allModelDOList.add(modelDO);
            }

            List<CfgTableDO> tableDOList = tableService.getDOByBlockId(blockDO.getId());
            for (CfgTableDO tableDO : tableDOList) {
                List<Long> modelIdList = tableDO.getModelIds();
                for (Long modelId : modelIdList) {
                    CfgModelDO modelDO = modelService.getDOById(modelId);
                    if (modelDO != null) allModelDOList.add(modelDO);
                }
            }
        }
        return allModelDOList;
    }

    public void checkModelField(CfgModelDO modelDO, HashMap<Long, Set<Long>> quotedFieldMap) {
        Long modelId = modelDO.getId();
        String modelCode = modelDO.getCode();
        if (modelId == null || !StringUtils.hasText(modelCode) || modelDO.getOwnerType() == null) {
            throw new ServiceException(500, "模型数据缺失");
        }

        if (modelDO.getFromMetadata() == null || modelDO.getFromMetadata() != StatusEnum.YES.getCode()) {
            log.info("禁止当前模型及字段从元数据更新,模型code:{},模型id:{}", modelCode, modelId);
            return;
        }

        Set<Long> quotedField = new HashSet<>();
        if (modelDO.getPageId() != null && !CollectionUtils.isEmpty(quotedFieldMap) && quotedFieldMap.containsKey(modelDO.getPageId())) {
            quotedField = quotedFieldMap.get(modelDO.getPageId());
        }

        List<MetaBizModel> metaFieldList = metaBizModelMapper.selectList(new LambdaQueryWrapper<MetaBizModel>()
                .eq(MetaBizModel::getCode, modelCode)
                .eq(MetaBizModel::getStatus, StatusEnum.YES.getCode())
                .eq(MetaBizModel::getDeleted, DeletedEnum.UNDELETED.getCode()));
        if (CollectionUtils.isEmpty(metaFieldList)) {
            //删除对应模型及字段
            log.info("根据模型code未找到元数据,模型code:{}, 模型id:{},", modelCode, modelId);
            List<CfgFieldDO> fieldDOList = fieldService.getDOListByModelId(modelId);
            List<Long> fieldIdList = fieldDOList.stream().map(CfgFieldDO::getId).toList();
            modelService.deleteLogic(List.of(modelId));
            if (!CollectionUtils.isEmpty(fieldIdList)) {
                fieldService.deleteLogic(fieldIdList);
                log.info("逻辑删除不在元数据表中的模型及基础字段, 模型code:{}, 字段个数:{}, 对应字段id:{}", modelCode, fieldIdList.size(), fieldIdList);
            }
            return;
        }

        List<CfgFieldDO> fieldDOList = fieldService.getSystemDOByModelId(modelId);
        Set<String> fieldFlagSet = metaFieldList.stream().map(MetaBizModel::getFieldCode).collect(Collectors.toSet());
        List<CfgFieldDO> redundantFieldList = fieldDOList.stream().filter(i -> !fieldFlagSet.contains(i.getBizCode())).toList();
        if (!CollectionUtils.isEmpty(redundantFieldList)) {
            //删除字段
            for (CfgFieldDO field : redundantFieldList) {
                if (quotedField.contains(field.getId())) {
                    throw new ServiceException(500, "待删除字段被引用,请先删除相关数据再同步元数据,字段信息:" + JSON.toJSONString(field));
                }
            }

            List<Long> redundantIdList = redundantFieldList.stream().map(CfgFieldDO::getId).toList();
            fieldService.deleteLogic(redundantIdList);
            log.info("逻辑删除不在元数据表中的基础字段,模型code:{}, 个数:{}, 对应字段id:{}", modelCode, redundantIdList.size(), redundantIdList);
            fieldDOList = fieldDOList.stream().filter(i -> !redundantIdList.contains(i.getId())).collect(Collectors.toList());
        }

        List<CfgFieldDO> addList = new ArrayList<>();
        List<CfgFieldDO> updateList = new ArrayList<>();
        List<Long> deleteIdList = new ArrayList<>();

        Map<String, List<CfgFieldDO>> fieldMap = fieldDOList.stream().collect(Collectors.groupingBy(CfgFieldDO::getBizCode));
        Date now = new Date();
        for (MetaBizModel metaField : metaFieldList) {
            CfgFieldDO fieldDO = getFieldDO(modelDO, metaField);

            List<CfgFieldDO> list = fieldMap.get(metaField.getFieldCode());
            if (CollectionUtils.isEmpty(list)) {
                fieldDO.setId(SnowflakeIdUtil.getId());
                fieldDO.setPageId(modelDO.getPageId());
                fieldDO.setModelId(modelId);
                fieldDO.setFieldType(FieldTypeEnum.SYSTEM.getCode());
                fieldDO.setDisplayed(DisplayEnum.UN_DISPLAY.getCode());
                fieldDO.setCreateBy(null);
                fieldDO.setCreateTime(now);
                addList.add(fieldDO);
            } else {
                for (int i = 1; i < list.size(); i++) {
                    deleteIdList.add(list.get(i).getId());
                }

                CfgFieldDO oldField = list.get(0);
                if ((!StringUtils.hasText(oldField.getBizName()) || !oldField.getBizName().equals(fieldDO.getBizName())) ||
                        (!StringUtils.hasText(oldField.getTitle()) || !oldField.getTitle().equals(fieldDO.getTitle())) ||
                        (!StringUtils.hasText(oldField.getDataBinding()) || !oldField.getDataBinding().equals(fieldDO.getDataBinding())) ||
                        (!StringUtils.hasText(oldField.getComponentType()) || !oldField.getComponentType().equals(fieldDO.getComponentType()))) {
                    fieldDO.setId(oldField.getId());
                    fieldDO.setUpdateBy(null);
                    fieldDO.setUpdateTime(now);
                    updateList.add(fieldDO);
                }
            }
        }

        if (!CollectionUtils.isEmpty(deleteIdList)) {
            for (Long fieldId : deleteIdList) {
                if (quotedField.contains(fieldId)) {
                    throw new ServiceException(500, "待删除字段被引用,请先删除相关数据再同步元数据,字段id:" + JSON.toJSONString(deleteIdList));
                }
            }
            fieldService.deleteLogic(deleteIdList);
        }
        if (!CollectionUtils.isEmpty(addList)) {
            fieldService.batchSave(addList);
        }
        if (!CollectionUtils.isEmpty(updateList)) {
            fieldService.batchUpdateById(updateList);
        }

        CfgModelDO newModel = new CfgModelDO();
        newModel.setId(modelId);
        newModel.setName(metaFieldList.get(0).getName());
        newModel.setDataBindingPrefix(metaFieldList.get(0).getDataBindingPrefix());
        newModel.setExtraFieldPrefix(metaFieldList.get(0).getExtraFieldPrefix());
        newModel.setTableName(metaFieldList.get(0).getTableName());
        newModel.setUpdateTime(now);
        modelService.updateById(newModel);

        List<Long> addIdList = addList.stream().map(CfgFieldDO::getId).toList();
        List<Long> updateIdList = updateList.stream().map(CfgFieldDO::getId).toList();
        log.info("完成元数据拉取, 模型code:{}, 模型id:{}, \n新增字段的个数:{}及id:{}, \n更新字段的个数:{}及id:{}, \n删除冗余字段的个数:{}及id:{}。\n",
                modelCode, modelId,
                addIdList.size(), addIdList,
                updateIdList.size(), updateIdList,
                deleteIdList.size(), deleteIdList);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateSingleFieldWithMetaData(Long modelId) {
        CfgModelDO modelDO = modelService.getDOById(modelId);
        if (modelDO == null) {
            throw new ServiceException(500, "模型不存在");
        }

        Byte flag = modelDO.getFromMetadata();
        if (flag == null || flag != StatusEnum.YES.getCode()) {
            throw new ServiceException(500, "不允许从元数据同步数据到当前模型");
        }

        HashMap<Long, Set<Long>> quotedFieldMap = new HashMap<>();
        if (modelDO.getPageId() != null) {
            CfgPageDO pageDO = pageService.getDoById(modelDO.getPageId());
            if (pageDO != null) {
                Set<Long> quotedField = fieldApplicationService.getQuotedField(pageDO.getId());
                quotedFieldMap.put(pageDO.getId(), quotedField);
            }
        }

        checkModelField(modelDO, quotedFieldMap);
        return true;
    }

    private GetAllModelByPageCodeVo getGetAllModelByPageCodeVo(CfgBlockDO blockDO, Byte presentationFormat, CfgModelDO modelDO) {
        GetAllModelByPageCodeVo vo = new GetAllModelByPageCodeVo();
        vo.setBlockName(blockDO.getName());
        vo.setModelName(modelDO.getName());
        vo.setPresentationFormat(presentationFormat);
        vo.setStatus(modelDO.getStatus());
        vo.setModelId(modelDO.getId().toString());
        return vo;
    }

    public PageResult<GetAllModelVo> getAllModel(Long pageNum, Long pageSize, String modelName, String modelCode) {
        List<MetaBizModel> modelList = metaBizModelService.getAllModel();
        Long total = (long) modelList.size();

        modelList = modelList.stream()
                .filter(model -> {
                    if (!StringUtils.hasText(modelName)) {
                        return true;
                    } else {
                        return model.getName().contains(modelName);
                    }
                })
                .filter(model -> {
                    if (!StringUtils.hasText(modelCode)) {
                        return true;
                    } else {
                        return model.getCode().contains(modelCode);
                    }
                })
                .skip((pageNum - 1) * pageSize).limit(pageSize).toList();

        List<GetAllModelVo> re = new ArrayList<>();
        for (MetaBizModel model : modelList) {
            GetAllModelVo vo = new GetAllModelVo();
            vo.setModelName(model.getName());
            vo.setModelCode(model.getCode());
            vo.setCreateTime(model.getCreateTime());
            vo.setUpdateTime(model.getUpdateTime());
            re.add(vo);
        }
        return new PageResult<>(pageNum, pageSize, total, re);
    }

    public List<SimpleModelInfo> listByFieldSetId(Long fieldSetId) {
        CfgFieldsetDO fieldsetDO = fieldsetService.getDOById(fieldSetId);
        Long modelId = fieldsetDO.getModelId();
        CfgModelDO modelDO = modelService.getDOById(modelId);
        SimpleModelInfo vo = new SimpleModelInfo();
        vo.setModelId(modelDO.getId().toString());
        vo.setModelName(modelDO.getName());

        List<SimpleModelInfo> voList = new ArrayList<>();
        voList.add(vo);
        return voList;
    }

    public List<SimpleModelInfo> listByTableId(Long tableId) {
        CfgTableDO tableDO = tableService.getDOById(tableId);

        List<SimpleModelInfo> voList = new ArrayList<>();
        List<Long> modelIdList = tableDO.getModelIds();
        for (Long modelId : modelIdList) {
            CfgModelDO modelDO = modelService.getDOById(modelId);
            SimpleModelInfo vo = new SimpleModelInfo();
            vo.setModelId(modelDO.getId().toString());
            vo.setModelName(modelDO.getName());
            voList.add(vo);
        }
        return voList;
    }

    public boolean update(UpdateModelDTO dto) {
        CfgModelDO modelDO = ModelConvert.updateModelDTOToDO(dto);
        boolean flag = modelService.updateById(modelDO);
        return flag;
    }

    public PageResult<FieldByModelIdVO> getFieldByModelId(String modelCode, Long pageNum, Long pageSize,
                                                          String fieldName, String componentType) {
        Page<MetaBizModel> page = metaBizModelService.getDOByModelCode(pageNum, pageSize, modelCode, fieldName, componentType);
        List<MetaBizModel> records = page.getRecords();

        List<FieldByModelIdVO> re = new ArrayList<>();
        for (MetaBizModel field : records) {
            FieldByModelIdVO vo = new FieldByModelIdVO();
            vo.setId(field.getId().toString());
            vo.setFieldName(field.getFieldName());
            vo.setFieldCode(field.getFieldCode());
            vo.setComponentType(field.getComponentType());
            vo.setCreateTime(field.getCreateTime());
            re.add(vo);
        }
        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), re);
    }

    public boolean updateById(CfgModelDO model) {
        if (model.getId() == null) {
            throw new ServiceException(500, "modelId不能为空");
        }
        return modelService.updateById(model);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean setModel(String type, Long id, String modelCode) {
        long modelId = SnowflakeIdUtil.getId();
        CfgModelDO modelDO = new CfgModelDO();
        modelDO.setId(modelId);
        modelDO.setFromMetadata(StatusEnum.YES.getCode());
        modelDO.setName("temName");
        modelDO.setCode(modelCode);
        modelDO.setCreateTime(new Date());

        if ("1".equals(type)) {
            //区块字段
            CfgFieldsetDO fieldsetDO = fieldsetService.getDOById(id);
            if (fieldsetDO == null) {
                throw new ServiceException(500, "找不到fieldsetDO");
            }
            modelDO.setPageId(fieldsetDO.getPageId());
            modelDO.setOwnerType(ModelOwnerEnum.FIELD_SET.getCode());

            CfgFieldsetDO newFieldsetDO = new CfgFieldsetDO();
            newFieldsetDO.setId(id);
            newFieldsetDO.setModelId(modelId);
            fieldsetService.updateById(newFieldsetDO);
        } else if ("2".equals(type)) {
            //表格
            CfgTableDO tableDO = tableService.getDOById(id);
            if (tableDO == null) {
                throw new ServiceException(500, "找不到tableDO");
            }
            modelDO.setPageId(tableDO.getPageId());
            modelDO.setOwnerType(ModelOwnerEnum.TABLE.getCode());

            CfgTableDO newTableDO = new CfgTableDO();
            newTableDO.setId(id);
            newTableDO.setModelIdListFromList(List.of(modelId));
            tableService.updateById(newTableDO);
        } else {
            throw new ServiceException(500, "不支持的方式");
        }

        return modelService.add(modelDO);
    }

    public boolean updateFieldWithMetaDataByIdentityCode(String identityCode) {
        List<CfgPageDO> pageDOList = pageService.getDOListByIdentityCode(identityCode);
        if (CollectionUtils.isEmpty(pageDOList)) {
            throw new ServiceException(500, "根据主体code没查询到页面,identityCode:" + identityCode);
        }
        Set<Long> pageIdSet = pageDOList.stream().map(CfgPageDO::getId).collect(Collectors.toSet());
        List<CfgModelDO> modelDOList = modelService.getByPageIds(pageIdSet);
        if (CollectionUtils.isEmpty(modelDOList)) {
            return false;
        }

        return transactionTemplate.execute(status -> {
            try {
                for (CfgModelDO modelDO : modelDOList) {
                    checkModelField(modelDO);
                }
                return true;
            } catch (Exception e) {
                log.error("为指定主体同步元数据发生异常, identityCode:{}:", identityCode, e);
                status.setRollbackOnly();
                return false;
            }
        });
    }

    public void checkModelField(CfgModelDO modelDO) {
        Long modelId = modelDO.getId();
        String modelCode = modelDO.getCode();
        if (modelId == null || !StringUtils.hasText(modelCode) || modelDO.getOwnerType() == null) {
            log.info("模型数据缺失,model:{}", modelDO);
            return;
        }

        List<MetaBizModel> metaFieldList = metaBizModelMapper.selectList(new LambdaQueryWrapper<MetaBizModel>()
                .eq(MetaBizModel::getCode, modelCode)
                .eq(MetaBizModel::getStatus, StatusEnum.YES.getCode())
                .eq(MetaBizModel::getDeleted, DeletedEnum.UNDELETED.getCode()));
        if (CollectionUtils.isEmpty(metaFieldList)) {
            log.info("根据模型code未找到元数据,模型code:{}, 模型id:{},", modelCode, modelId);
            return;
        }

        List<CfgFieldDO> fieldDOList = fieldService.getSystemDOByModelId(modelId);
        Map<String, List<CfgFieldDO>> fieldMap = fieldDOList.stream().collect(Collectors.groupingBy(CfgFieldDO::getBizCode));
        List<CfgFieldDO> addList = new ArrayList<>();
        List<CfgFieldDO> updateList = new ArrayList<>();
        Date now = new Date();
        for (MetaBizModel metaField : metaFieldList) {
            List<CfgFieldDO> list = fieldMap.get(metaField.getFieldCode());

            if (CollectionUtils.isEmpty(list)) {
                CfgFieldDO fieldDO = getFieldDO(modelDO, metaField);
                fieldDO.setId(SnowflakeIdUtil.getId());
                fieldDO.setPageId(modelDO.getPageId());
                fieldDO.setModelId(modelId);
                fieldDO.setFieldType(FieldTypeEnum.SYSTEM.getCode());
                fieldDO.setDisplayed(DisplayEnum.UN_DISPLAY.getCode());
                fieldDO.setCreateTime(now);
                addList.add(fieldDO);
            } else {
                for (CfgFieldDO oldField : list) {
                    String bizName = oldField.getBizName();
                    String dataBinding = oldField.getDataBinding();
                    String componentType = oldField.getComponentType();
                    CfgFieldDO fieldDO = getFieldDO(modelDO, metaField);

                    if ((!StringUtils.hasText(bizName) || !bizName.equals(fieldDO.getBizName())) ||
                            (!StringUtils.hasText(dataBinding) || !dataBinding.equals(fieldDO.getDataBinding())) ||
                            (!StringUtils.hasText(componentType) || !componentType.equals(fieldDO.getComponentType()))) {
                        fieldDO.setId(oldField.getId());
                        fieldDO.setUpdateBy(null);
                        fieldDO.setUpdateTime(now);
                        updateList.add(fieldDO);
                    }
                }
            }
        }

        if (!CollectionUtils.isEmpty(addList)) {
            fieldService.batchSave(addList);
        }
        if (!CollectionUtils.isEmpty(updateList)) {
            fieldService.batchUpdateById(updateList);
        }

        CfgModelDO newModel = new CfgModelDO();
        newModel.setId(modelId);
        newModel.setName(metaFieldList.get(0).getName());
        newModel.setDataBindingPrefix(metaFieldList.get(0).getDataBindingPrefix());
        newModel.setExtraFieldPrefix(metaFieldList.get(0).getExtraFieldPrefix());
        newModel.setTableName(metaFieldList.get(0).getTableName());
        newModel.setUpdateTime(now);
        modelService.updateById(newModel);

        List<Long> addIdList = addList.stream().map(CfgFieldDO::getId).toList();
        List<Long> updateIdList = updateList.stream().map(CfgFieldDO::getId).toList();
        log.info("完成元数据拉取, 模型code:{}, 模型id:{}, \n新增字段的个数及id:{},{}, \n更新字段的个数及id:{},{}",
                modelCode, modelId,
                addIdList.size(), addIdList,
                updateIdList.size(), updateIdList);
    }

    private CfgFieldDO getFieldDO(CfgModelDO modelDO, MetaBizModel metaField) {
        CfgFieldDO fieldDO = FieldConvert.metaFieldToDO(metaField);
        if (modelDO.getOwnerType() == ModelOwnerEnum.TABLE.getCode() || modelDO.getOwnerType() == ModelOwnerEnum.PAGE_HEAD.getCode()) {
            fieldDO.setDataBinding("$.main." + metaField.getFieldCode());
        }
        return fieldDO;
    }

    public List<SimpleModelInfo> listByBizIdentityCode(String bizIdentityCode) {
        List<CfgPageDO> pageDOList = pageService.getDOListByIdentityCode(bizIdentityCode);
        if (CollectionUtils.isEmpty(pageDOList)) {
            throw new ServiceException(500, "没有配置专属页面");
        }
        List<Long> pageIdList = pageDOList.stream().map(CfgPageDO::getId).toList();
        List<CfgModelDO> modeDoList = modelService.getDOListByPageIdList(pageIdList);
        if (CollectionUtils.isEmpty(modeDoList)) {
            return new ArrayList<>();
        }

        Set<String> hasAdd = new HashSet<>();
        List<SimpleModelInfo> simpleModelInfoList = new ArrayList<>(modeDoList.size());
        for (CfgModelDO modelDO : modeDoList) {
            if (hasAdd.contains(modelDO.getCode())) {
                continue;
            }

            SimpleModelInfo simpleModelInfo = new SimpleModelInfo();
            simpleModelInfo.setModelId(modelDO.getId().toString());
            simpleModelInfo.setModelName(modelDO.getName());
            simpleModelInfoList.add(simpleModelInfo);
            hasAdd.add(modelDO.getCode());
        }
        return simpleModelInfoList;
    }

    public SimpleModelInfo getModelByName(String pageCode, String bizIdentityCode, String modelName) {

        CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, bizIdentityCode);
        if (Objects.isNull(pageDO)) {
            log.error("no page. pageCode: {}, bizIdentityCode: {}, modelName: {}", pageCode, bizIdentityCode, modelName);
            throw new ServiceException(500, "没有配置专属页面");
        }
        Long pageId = pageDO.getId();
        CfgModelDO modelDO = modelService.getByPageIdAndModelName(pageId, modelName);
        if (Objects.isNull(modelDO)) {
            log.error("no page. pageCode: {}, bizIdentityCode: {}, modelName: {}", pageCode, bizIdentityCode, modelName);
            throw new ServiceException(500, "模型不存在");
        }

        SimpleModelInfo simpleModelInfo = new SimpleModelInfo();
        simpleModelInfo.setModelId(String.valueOf(modelDO.getId()));
        simpleModelInfo.setModelName(modelDO.getName());
        return simpleModelInfo;

    }
}
