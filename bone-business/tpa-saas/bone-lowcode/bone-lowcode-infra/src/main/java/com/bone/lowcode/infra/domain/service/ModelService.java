package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.StatusEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgModelDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.ModelMapper;
import com.bone.core.util.PkListUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.BatchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ModelService {

    @Autowired
    private ModelMapper modelMapper;


    public CfgModelDO getDOById(Long id) {
        LambdaQueryWrapper<CfgModelDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CfgModelDO::getId, id)
                .eq(CfgModelDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return modelMapper.selectOne(wrapper);
    }

    public boolean add(CfgModelDO modelDO) {
        return modelMapper.insert(modelDO) == 1;
    }

    public boolean batchSave(List<CfgModelDO> modelDOList) {
        modelMapper.insert(modelDOList);
        return true;
    }

    public List<CfgModelDO> getDOListByIdList(List<Long> idList) {
        LambdaQueryWrapper<CfgModelDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CfgModelDO::getId, idList)
                .eq(CfgModelDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return modelMapper.selectList(wrapper);
    }

    public boolean updateById(CfgModelDO modelDO) {
        return modelMapper.updateById(modelDO) > 0;
    }

    public boolean batchUpdateById(List<CfgModelDO> modelDOList) {
        List<BatchResult> batchResultList = modelMapper.updateById(modelDOList);
        log.info("model批量更新结果:{}", batchResultList.stream().map(i -> Arrays.toString(i.getUpdateCounts())).collect(Collectors.toList()));
        return true;
    }

    public boolean deleteByIdList(List<Long> idList) {
        int i = modelMapper.deleteByIds(idList);
        return i > 0;
    }

    public List<CfgModelDO> getDOListByPageId(Long pageId) {
        return modelMapper.selectList(new LambdaQueryWrapper<CfgModelDO>()
                .eq(CfgModelDO::getPageId, pageId)
                .eq(CfgModelDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public List<CfgModelDO> getDOListByCodePageId(String modelCode, List<Long> pageIdList) {
        return modelMapper.selectList(new LambdaQueryWrapper<CfgModelDO>()
                .eq(CfgModelDO::getCode, modelCode)
                .in(CfgModelDO::getPageId, pageIdList)
                .eq(CfgModelDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public List<CfgModelDO> getFromMetaDOList() {
        return modelMapper.selectList(new LambdaQueryWrapper<CfgModelDO>()
                .eq(CfgModelDO::getFromMetadata, StatusEnum.YES.getCode())
                .eq(CfgModelDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public List<CfgModelDO> getDOByCodePageId(List<String> modeCodeList, List<Long> pageIdList) {
        LambdaQueryWrapper<CfgModelDO> wrapper = new LambdaQueryWrapper<CfgModelDO>()
                .in(CfgModelDO::getPageId, pageIdList)
                .eq(CfgModelDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        if(PkListUtil.isNotEmpty(modeCodeList)){
            wrapper.in(CfgModelDO::getCode, modeCodeList);
        }
        return modelMapper.selectList(wrapper);
    }

    public boolean deleteLogic(List<Long> idList) {
        LambdaUpdateWrapper<CfgModelDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(CfgModelDO::getId, idList)
                .eq(CfgModelDO::getDeleted, DeletedEnum.UNDELETED.getCode())
                .set(CfgModelDO::getDeleted, DeletedEnum.DELETED.getCode());
        return modelMapper.update(wrapper) > 0;
    }


    public List<CfgModelDO> getByPageIds(Collection<Long> pageIds) {
        LambdaQueryWrapper<CfgModelDO> wrapper = new LambdaQueryWrapper<CfgModelDO>()
                .in(CfgModelDO::getPageId, pageIds)
                .eq(CfgModelDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return modelMapper.selectList(wrapper);
    }

    public CfgModelDO getDOByPageIdAndCode(Long pageId, String code) {
        LambdaQueryWrapper<CfgModelDO> wrapper = new LambdaQueryWrapper<CfgModelDO>()
                .in(CfgModelDO::getPageId, pageId)
                .eq(CfgModelDO::getCode, code)
                .eq(CfgModelDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return modelMapper.selectOne(wrapper);
    }

    public List<CfgModelDO> getDOByPageIdAndCodes(Long pageId, Collection<String> modelCodes) {
        LambdaQueryWrapper<CfgModelDO> wrapper = new LambdaQueryWrapper<CfgModelDO>()
                .in(CfgModelDO::getPageId, pageId)
                .in(CfgModelDO::getCode, modelCodes)
                .eq(CfgModelDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return modelMapper.selectList(wrapper);
    }

    public List<CfgModelDO> getDOListByPageIdList(List<Long> pageIdList) {
        return modelMapper.selectList(new LambdaQueryWrapper<CfgModelDO>()
                .in(CfgModelDO::getPageId, pageIdList)
                .eq(CfgModelDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public CfgModelDO getByPageIdAndModelName(Long pageId, String modelName) {
        LambdaQueryWrapper<CfgModelDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CfgModelDO::getPageId, pageId)
                .eq(CfgModelDO::getName, modelName)
                .eq(CfgModelDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return modelMapper.selectOne(queryWrapper);
    }


}
