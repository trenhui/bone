package com.bone.lowcode.infra.application.service;

import com.alibaba.fastjson.JSON;
import com.bone.core.exception.ServiceException;
import com.bone.lowcode.infra.application.dto.upload.UpdateUploadAttachmentDTO;
import com.bone.lowcode.infra.application.dto.upload.UpdateUploadImageDTO;
import com.bone.lowcode.infra.application.dto.upload.UpdateUploadPictureDTO;
import com.bone.lowcode.infra.application.dto.upload.UploadDataDTO;
import com.bone.lowcode.infra.application.vo.table.FieldSimpleInfo;
import com.bone.lowcode.infra.application.vo.upload.*;
import com.bone.lowcode.infra.domain.model.GroupFieldRule;
import com.bone.lowcode.infra.domain.model.SingleFieldRule;
import com.bone.lowcode.infra.domain.service.*;
import com.bone.lowcode.infra.domain.valueobject.UploadComponentEnum;
import com.bone.lowcode.infra.domain.valueobject.UploadDataEnum;
import com.bone.lowcode.infra.domain.valueobject.UploadImageEnum;
import com.bone.lowcode.infra.domain.valueobject.UploadPictureEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class UploadApplicationService {

    @Autowired
    private FieldService fieldService;

    @Autowired
    private ModelService modelService;

    @Autowired
    private UploadDataService uploadDataService;

    @Autowired
    private UploadImageService uploadImageService;

    @Autowired
    private UploadPictureService uploadPictureService;

    @Autowired
    private UploadAttachmentService uploadAttachmentService;

    public UploadDataDO getUploadDataDOByCode(String code, String bizIdentityCode) {
        return uploadDataService.getDOByCode(code, bizIdentityCode);
    }

    public UploadDataVO getUploadDataByCode(String code, String bizIdentityCode) {
        UploadDataDO uploadDataDO = uploadDataService.getDOByCode(code, bizIdentityCode);
        if (uploadDataDO == null) {
            throw new ServiceException(500, "根据参数没有查到上传数据组件");
        }

        UploadDataVO uploadDataVO = getUploadDataVO(uploadDataDO);
        return uploadDataVO;
    }

    public UploadDataVO getUploadDataById(Long id) {
        UploadDataDO uploadDataDO = uploadDataService.getDOById(id);
        if (uploadDataDO == null) {
            throw new ServiceException(500, "根据参数没有查到上传数据组件");
        }

        UploadDataVO uploadDataVO = getUploadDataVO(uploadDataDO);
        return uploadDataVO;
    }

    private UploadDataVO getUploadDataVO(UploadDataDO uploadDataDO) {
        UploadDataVO uploadDataVO = new UploadDataVO();
        BeanUtils.copyProperties(uploadDataDO, uploadDataVO);
        uploadDataVO.setId(uploadDataDO.getId().toString());

        CfgModelDO modelDO = modelService.getDOById(uploadDataDO.getModelId());
        ModelOfProcessPageVO modelVO = new ModelOfProcessPageVO(modelDO.getId().toString(), modelDO.getName());
        uploadDataVO.setModel(modelVO);

        if (!CollectionUtils.isEmpty(uploadDataDO.getFieldIds())) {
            List<CfgFieldDO> fieldDOList = fieldService.getDOListByIdList(uploadDataDO.getFieldIds());
            List<FieldSimpleInfo> fieldList = fieldDOList.stream()
                    .map(fieldDO -> new FieldSimpleInfo(fieldDO.getId().toString(), fieldDO.getBizCode(), fieldDO.getBizName(), fieldDO.getDataBinding()))
                    .toList();
            uploadDataVO.setFieldList(fieldList);
        }
        setSingleFieldRuleList(uploadDataDO, uploadDataVO);
        setGroupFieldRuleList(uploadDataDO, uploadDataVO);
        if (!CollectionUtils.isEmpty(uploadDataDO.getImportTypes())) {
            uploadDataVO.setImportTypeList(uploadDataDO.getImportTypes());
        }
        return uploadDataVO;
    }

    private void setSingleFieldRuleList(UploadDataDO uploadComponentDO, UploadDataVO uploadDataVO) {
        if (!CollectionUtils.isEmpty(uploadComponentDO.getSingleFieldRules())) {
            List<SingleFieldRule> singleFieldRuleList = uploadComponentDO.getSingleFieldRules();
            List<Long> fieldIdList = singleFieldRuleList.stream().map(i -> Long.parseLong(i.getFieldId())).toList();
            List<CfgFieldDO> fieldDOList = fieldService.getDOListByIdList(fieldIdList);
            Map<Long, CfgFieldDO> fieldDOMap = fieldDOList.stream().collect(Collectors.toMap(CfgFieldDO::getId, i -> i));

            List<SingleFieldRuleVO> singleFieldRuleVOList = singleFieldRuleList.stream().map(singleFieldRule -> {
                SingleFieldRuleVO vo = new SingleFieldRuleVO();
                BeanUtils.copyProperties(singleFieldRule, vo);
                vo.setBizName(fieldDOMap.get(Long.parseLong(singleFieldRule.getFieldId())).getBizName());
                return vo;
            }).toList();
            uploadDataVO.setSingleFieldRuleList(singleFieldRuleVOList);
        }
    }

    private void setGroupFieldRuleList(UploadDataDO uploadComponentDO, UploadDataVO uploadDataVO) {
        if (CollectionUtils.isEmpty(uploadComponentDO.getGroupFieldRules())) {
            return;
        }
        List<GroupFieldRule> groupFieldRuleList = uploadComponentDO.getGroupFieldRules();
        List<Long> allFieldIdList = new ArrayList<>();
        for (GroupFieldRule i : groupFieldRuleList) {
            List<String> fieldIdList = i.getFieldIdList();
            if (!CollectionUtils.isEmpty(fieldIdList)) {
                allFieldIdList.addAll(fieldIdList.stream().map(Long::parseLong).toList());
            }
            List<String> fieldIdListA = i.getFieldIdListA();
            if (!CollectionUtils.isEmpty(fieldIdListA)) {
                allFieldIdList.addAll(fieldIdListA.stream().map(Long::parseLong).toList());
            }
            List<String> fieldIdListB = i.getFieldIdListB();
            if (!CollectionUtils.isEmpty(fieldIdListB)) {
                allFieldIdList.addAll(fieldIdListB.stream().map(Long::parseLong).toList());
            }
        }
        Map<String, CfgFieldDO> fieldDOMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(allFieldIdList)) {
            List<CfgFieldDO> fieldDOList = fieldService.getDOListByIdList(allFieldIdList);
            fieldDOMap = fieldDOList.stream().collect(Collectors.toMap(i -> i.getId().toString(), i -> i));
        }

        Map<String, CfgFieldDO> finalFieldDOMap = fieldDOMap;
        List<GroupFieldRuleVO> groupFieldRuleVOList = groupFieldRuleList.stream().map(i -> {
            GroupFieldRuleVO vo = new GroupFieldRuleVO();
            vo.setUnique(i.getUnique());

            if (!CollectionUtils.isEmpty(i.getFieldIdList())) {
                List<FieldSimpleInfo> list = i.getFieldIdList().stream().map(key -> {
                    CfgFieldDO fieldDO = finalFieldDOMap.get(key);
                    return new FieldSimpleInfo(fieldDO.getId().toString(), fieldDO.getBizCode(), fieldDO.getBizName(), fieldDO.getDataBinding());
                }).toList();
                vo.setFieldList(list);
            } else {
                vo.setFieldList(new ArrayList<>());
            }

            if (!CollectionUtils.isEmpty(i.getFieldIdListA())) {
                List<FieldSimpleInfo> list1 = i.getFieldIdListA().stream().map(key -> {
                    CfgFieldDO fieldDO = finalFieldDOMap.get(key);
                    return new FieldSimpleInfo(fieldDO.getId().toString(), fieldDO.getBizCode(), fieldDO.getBizName(), fieldDO.getDataBinding());
                }).toList();
                vo.setFieldListA(list1);
            } else {
                vo.setFieldListA(new ArrayList<>());
            }

            if (!CollectionUtils.isEmpty(i.getFieldIdListB())) {
                List<FieldSimpleInfo> list2 = i.getFieldIdListB().stream().map(key -> {
                    CfgFieldDO fieldDO = finalFieldDOMap.get(key);
                    return new FieldSimpleInfo(fieldDO.getId().toString(), fieldDO.getBizCode(), fieldDO.getBizName(), fieldDO.getDataBinding());
                }).toList();
                vo.setFieldListB(list2);
            } else {
                vo.setFieldListB(new ArrayList<>());
            }
            return vo;
        }).toList();
        uploadDataVO.setGroupFieldRuleList(groupFieldRuleVOList);
    }

    public boolean updateUploadData(UploadDataDTO dto) {
        UploadDataDO uploadDataDO = new UploadDataDO();
        BeanUtils.copyProperties(dto, uploadDataDO);
        if (!CollectionUtils.isEmpty(dto.getFieldIdList())) {
            uploadDataDO.setFieldIds(dto.getFieldIdList());
        }
        if (!CollectionUtils.isEmpty(dto.getSingleFieldRuleList())) {
            uploadDataDO.setSingleFieldRule(JSON.toJSONString(dto.getSingleFieldRuleList()));
        }
        if (!CollectionUtils.isEmpty(dto.getGroupFieldRuleList())) {
            uploadDataDO.setGroupFieldRule(JSON.toJSONString(dto.getGroupFieldRuleList()));
        }
        if (!CollectionUtils.isEmpty(dto.getImportTypeList())) {
            uploadDataDO.setImportTypeList(JSON.toJSONString(dto.getImportTypeList()));
        }

        boolean flag = uploadDataService.updateAcceptNull(uploadDataDO);
        return flag;
    }

    public UploadImageVO getUploadImageByCode(String code, String bizIdentityCode) {
        UploadImageDO uploadImageDO = uploadImageService.getDOByCode(code, bizIdentityCode);
        if (uploadImageDO == null) {
            throw new ServiceException(500, "根据参数没有查到上传影像件组件");
        }
        return getUploadImageVO(uploadImageDO);
    }

    public UploadImageVO getUploadImageById(Long id) {
        UploadImageDO uploadImageDO = uploadImageService.getDOById(id);
        if (uploadImageDO == null) {
            throw new ServiceException(500, "根据参数没有查到上传影像件组件");
        }
        return getUploadImageVO(uploadImageDO);
    }

    private UploadImageVO getUploadImageVO(UploadImageDO uploadImageDO) {
        UploadImageVO vo = new UploadImageVO();
        if (uploadImageDO != null) {
            BeanUtils.copyProperties(uploadImageDO, vo);
            vo.setId(uploadImageDO.getId().toString());
            vo.setImportTypeList(uploadImageDO.getImportTypes());
        }
        return vo;
    }

    public boolean updateUploadImage(UpdateUploadImageDTO param) {
        UploadImageDO uploadImageDO = new UploadImageDO();
        BeanUtils.copyProperties(param, uploadImageDO);
        uploadImageDO.setImportTypes(param.getImportTypeList());
        return uploadImageService.updateAcceptNull(uploadImageDO);
    }


    public Object getUploadComponent(Byte type, Long id) {
        if (type == null || id == null) {
            throw new ServiceException(500, "参数不能为空");
        }

        Object re = null;
        if (type == UploadComponentEnum.UPLOAD_DATA.getCode()) {
            UploadDataDO uploadDataDO = uploadDataService.getDOById(id);
            re = getUploadDataVO2(uploadDataDO);
        } else if (type == UploadComponentEnum.UPLOAD_IMAGE.getCode()) {
            UploadImageDO uploadImageDO = uploadImageService.getDOById(id);
            re = getUploadImageVO(uploadImageDO);
        } else if (type == UploadComponentEnum.UPLOAD_PICTURE.getCode()) {
            UploadPictureDO pictureDO = uploadPictureService.getDOById(id);
            if (pictureDO == null) {
                throw new ServiceException(500, "根据参数未查询到目标上传图片组件,type:" + type + ", id:" + id);
            }
            UploadPictureVO2 pictureVO = new UploadPictureVO2();
            BeanUtils.copyProperties(pictureDO, pictureVO);
            pictureVO.setId(pictureDO.getId().toString());
            re = pictureVO;
        }
        return re;
    }

    private UploadDataVO2 getUploadDataVO2(UploadDataDO uploadDataDO) {
        UploadDataVO2 vo = new UploadDataVO2();
        vo.setId(uploadDataDO.getId().toString());
        vo.setTitle(uploadDataDO.getTitle());
        vo.setDataType(uploadDataDO.getDataType());

        Long modelId = uploadDataDO.getModelId();
        CfgModelDO modelDO = modelService.getDOById(modelId);
        vo.setModelCodeList(List.of(modelDO.getCode()));

        List<Long> fieldIds = uploadDataDO.getFieldIds();
        if (CollectionUtils.isEmpty(fieldIds)) {
            throw new ServiceException(500, "请先设置导入字段");
        }
        List<CfgFieldDO> fieldDOList = fieldService.getDOListByIdList(fieldIds);
        List<String> list = fieldDOList.stream().map(CfgFieldDO::getBizName).toList();
        vo.setFieldNameList(list);

        List<SingleFieldRule> singleFieldRuleList = uploadDataDO.getSingleFieldRules();
        List<SingleFieldRuleVO2> singleFieldRuleVOList = singleFieldRuleList.stream().map(i -> {
            long fieldId = Long.parseLong(i.getFieldId());
            CfgFieldDO fieldDO = fieldService.getDOById(fieldId);
            return new SingleFieldRuleVO2(fieldDO.getBizName(), i.getRequired(), i.getUnique());
        }).toList();
        vo.setSingleFieldRuleList(singleFieldRuleVOList);

        List<GroupFieldRule> groupFieldRuleList = uploadDataDO.getGroupFieldRules();
        List<GroupFieldRuleVO2> groupFieldRuleVOList = groupFieldRuleList.stream().map(i -> {
            List<Long> fieldIdList = i.getFieldIdList().stream().map(Long::parseLong).toList();
            List<CfgFieldDO> cfgFieldDOList = fieldService.getDOListByIdList(fieldIdList);
            Map<String, String> map = cfgFieldDOList.stream().collect(Collectors.toMap(f -> f.getId().toString(), CfgFieldDO::getBizName));

            List<String> fieldNameList = i.getFieldIdList().stream().map(map::get).toList();
            List<String> fieldNameListA = i.getFieldIdListA().stream().map(map::get).toList();
            List<String> fieldNameListB = i.getFieldIdListB().stream().map(map::get).toList();
            return new GroupFieldRuleVO2(fieldNameList, i.getUnique(), fieldNameListA, fieldNameListB);
        }).toList();
        vo.setGroupFieldRuleList(groupFieldRuleVOList);

        vo.setTemplateFileName(uploadDataDO.getTemplateFileName());
        vo.setFileMaxSize(uploadDataDO.getFileMaxSize());
        vo.setFileMaxCount(uploadDataDO.getFileMaxCount());
        vo.setFileFormat(uploadDataDO.getFileFormat());
        vo.setHeaderCheckMode(uploadDataDO.getHeaderCheckMode());
        vo.setImportTypeList(uploadDataDO.getImportTypes());
        vo.setCheckType(uploadDataDO.getCheckType());
        vo.setImportDescription(uploadDataDO.getImportDescription());
        return vo;
    }

    public UploadPictureVO getUploadPictureById(Long id) {
        UploadPictureDO uploadPictureDO = uploadPictureService.getDOById(id);
        UploadPictureVO vo = new UploadPictureVO();
        BeanUtils.copyProperties(uploadPictureDO, vo);
        vo.setId(uploadPictureDO.getId().toString());
        return vo;
    }

    public boolean updateUploadPicture(UpdateUploadPictureDTO param) {
        UploadPictureDO uploadPictureDO = new UploadPictureDO();
        BeanUtils.copyProperties(param, uploadPictureDO);
        return uploadPictureService.updateById(uploadPictureDO);
    }

    public UploadAttachmentVO getUploadAttachmentById(Long id) {
        UploadAttachmentDO uploadAttachmentDO = uploadAttachmentService.getDOById(id);
        UploadAttachmentVO vo = new UploadAttachmentVO();
        BeanUtils.copyProperties(uploadAttachmentDO, vo);
        return vo;
    }

    public boolean updateUploadAttachment(UpdateUploadAttachmentDTO param) {
        UploadAttachmentDO uploadAttachmentDO = new UploadAttachmentDO();
        BeanUtils.copyProperties(param, uploadAttachmentDO);
        return uploadAttachmentService.updateById(uploadAttachmentDO);
    }

    public List<Object> getUploadComponentList1(String bizIdentityCode) {
        ArrayList<Object> res = new ArrayList<>();
        List<UploadDataEnum> uploadDataEnumList = UploadDataEnum.list1();
        for (UploadDataEnum uploadDataEnum : uploadDataEnumList) {
            UploadDataDO uploadDataDO = uploadDataService.getDOByCode(uploadDataEnum.getCode(), bizIdentityCode);
            if (uploadDataDO == null) continue;

            UploadComponentVO vo = new UploadComponentVO();
            vo.setId(uploadDataDO.getId().toString());
            vo.setApplicablePage(uploadDataEnum.getApplicablePage());
            vo.setTitle(uploadDataDO.getTitle());
            vo.setDataType(uploadDataDO.getDataType());
            res.add(vo);
        }

        List<UploadImageEnum> uploadImageEnumList = UploadImageEnum.list1();
        for (UploadImageEnum uploadImageEnum : uploadImageEnumList) {
            UploadImageDO uploadImageDO = uploadImageService.getDOByCode(uploadImageEnum.getCode(), bizIdentityCode);
            if (uploadImageDO == null) continue;

            UploadComponentVO vo = new UploadComponentVO();
            vo.setId(uploadImageDO.getId().toString());
            vo.setApplicablePage(uploadImageEnum.getApplicablePage());
            vo.setTitle(uploadImageDO.getTitle());
            vo.setDataType(uploadImageDO.getDataType());
            res.add(vo);
        }

        List<UploadPictureEnum> uploadPictureEnumList = UploadPictureEnum.list1();
        for (UploadPictureEnum uploadPictureEnum : uploadPictureEnumList) {
            UploadPictureDO uploadPictureDO = uploadPictureService.getDOByCode(uploadPictureEnum.getCode(), bizIdentityCode);
            if (uploadPictureDO == null) continue;

            UploadComponentVO vo = new UploadComponentVO();
            vo.setId(uploadPictureDO.getId().toString());
            vo.setApplicablePage(uploadPictureEnum.getApplicablePage());
            vo.setTitle(uploadPictureDO.getTitle());
            vo.setDataType(uploadPictureDO.getDataType());
            res.add(vo);
        }
        return res;
    }

    public List<Object> getUploadComponentList2(String bizIdentityCode) {
        ArrayList<Object> res = new ArrayList<>();
        List<UploadDataEnum> uploadDataEnumList = UploadDataEnum.list2();
        for (UploadDataEnum uploadDataEnum : uploadDataEnumList) {
            UploadDataDO uploadDataDO = uploadDataService.getDOByCode(uploadDataEnum.getCode(), bizIdentityCode);
            UploadComponentVO vo = new UploadComponentVO();
            res.add(vo);

            vo.setId(uploadDataDO.getId().toString());
            vo.setApplicablePage(uploadDataEnum.getApplicablePage());
            vo.setTitle(uploadDataDO.getTitle());
            vo.setDataType(uploadDataDO.getDataType());
        }
        return res;
    }

    public UploadPictureVO getUploadPictureByCode(String code, String bizIdentityCode) {
        UploadPictureDO uploadPictureDO = uploadPictureService.getDOByCode(code, bizIdentityCode);
        UploadPictureVO vo = new UploadPictureVO();
        BeanUtils.copyProperties(uploadPictureDO, vo);
        vo.setId(uploadPictureDO.getId().toString());
        return vo;
    }
}
