package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.OptionSetNodeEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.OptionSet;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.OptionSetMapper;
import com.bone.core.util.PkListUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class OptionSetService {

    @Autowired
    private OptionSetMapper optionSetMapper;

    public Page<OptionSet> getRootList(Long pageNum, Long pageSize) {
        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<OptionSet>()
                .eq(OptionSet::getNodeType, OptionSetNodeEnum.ROOT_NODE.getCode())
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode());
        Page<OptionSet> page = new Page<>(pageNum, pageSize);
        optionSetMapper.selectPage(page, wrapper);
        return page;
    }

    public Page<OptionSet> getRootListByName(Long pageNum, Long pageSize, String name) {
        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<OptionSet>()
                .eq(OptionSet::getNodeType, OptionSetNodeEnum.ROOT_NODE.getCode())
                .like(OptionSet::getName, name)
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode());
        Page<OptionSet> page = new Page<>(pageNum, pageSize);
        optionSetMapper.selectPage(page, wrapper);
        return page;
    }

    public List<OptionSet> getRootList() {
        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<OptionSet>()
                .eq(OptionSet::getNodeType, OptionSetNodeEnum.ROOT_NODE.getCode())
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode());
        return optionSetMapper.selectList(wrapper);
    }

    public Page<OptionSet> optionValuePage(Integer pageNum, Integer pageSize, LambdaQueryWrapper<OptionSet> wrapper) {
        Page<OptionSet> page = new Page<>(pageNum, pageSize);
        optionSetMapper.selectPage(page, wrapper);
        return page;
    }

    public OptionSet getById(Long optionSetId) {
        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<OptionSet>()
                .eq(OptionSet::getId, optionSetId)
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode());
        return optionSetMapper.selectOne(wrapper);
    }

    public List<OptionSet> getByIdList(List<Long> idList) {
        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<OptionSet>()
                .in(OptionSet::getId, idList)
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode());
        return optionSetMapper.selectList(wrapper);
    }

    public List<OptionSet> getByParentId(Long parentId) {
        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<OptionSet>()
                .eq(OptionSet::getParentId, parentId)
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode());
        return optionSetMapper.selectList(wrapper);
    }

    public List<OptionSet> getByParentIdLimit(Long parentId, Integer count) {
        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<OptionSet>()
                .eq(OptionSet::getParentId, parentId)
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode())
                .last("limit " + count);
        return optionSetMapper.selectList(wrapper);
    }

    public Boolean batchUpdateById(List<OptionSet> list) {
        optionSetMapper.updateById(list);
        return true;
    }

    public boolean updateById(OptionSet optionSet) {
        return optionSetMapper.updateById(optionSet) > 0;
    }

    public Boolean add(OptionSet optionSet) {
        return optionSetMapper.insert(optionSet) > 0;
    }

    public Boolean batchSave(List<OptionSet> list) {
        optionSetMapper.insert(list);
        return true;
    }

    public List<OptionSet> getListBySetId(Long optionSetId) {
        OptionSet root = optionSetMapper.selectById(optionSetId);
        List<OptionSet> res = new ArrayList<>();
        res.add(root);

        part(root.getId(), res);
        return res;
    }

    private void part(Long rootId, List<OptionSet> res) {
        List<OptionSet> list = getByParentId(rootId);
        res.addAll(list);
        for (OptionSet item : list) {
            if (item.getNodeType() == OptionSetNodeEnum.LEAF_NODE.getCode()) {
                continue;//子节点为叶子节点处理方式
            }

            part(item.getId(), res);//子节点为非叶子节点处理方式
        }
    }

    @Transactional
    public void updateAndInsert(List<OptionSet> optionSetList) {
        //先删除再添加
        List<String> codeList = optionSetList.stream().map(OptionSet::getCode).toList();

        optionSetMapper.deleteByParentIdAndCode(optionSetList.get(0).getParentId(), codeList);

        optionSetMapper.batchSaveWithId(optionSetList);
    }


    public boolean deleteByIdList(List<Long> idList) {
        int count = optionSetMapper.deleteByIds(idList);
        return count > 0;
    }

    public OptionSet getRootSetByCode(String code) {
        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OptionSet::getCode, code)
                .eq(OptionSet::getNodeType, OptionSetNodeEnum.ROOT_NODE.getCode())
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode());
        return optionSetMapper.selectOne(wrapper);
    }

    public OptionSet getRootSetById(Long optionSetId) {
        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OptionSet::getId, optionSetId)
                .eq(OptionSet::getNodeType, OptionSetNodeEnum.ROOT_NODE.getCode())
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode());
        return optionSetMapper.selectOne(wrapper);
    }

    public List<OptionSet> getRootDOListBySetName(String setName) {
        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OptionSet::getName, setName)
                .eq(OptionSet::getNodeType, OptionSetNodeEnum.ROOT_NODE.getCode())
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode());
        return optionSetMapper.selectList(wrapper);
    }

    public List<OptionSet> getRootDOListBySetCode(String setCode) {
        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OptionSet::getCode, setCode)
                .eq(OptionSet::getNodeType, OptionSetNodeEnum.ROOT_NODE.getCode())
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode());
        return optionSetMapper.selectList(wrapper);
    }

    public List<OptionSet> getValueByCodeList(Collection<String> codeList) {
        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(OptionSet::getCode, codeList)
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode());
        return optionSetMapper.selectList(wrapper);
    }

    public OptionSet getByParentCodeName(Long parentId, String optionCn) {
        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OptionSet::getParentId, parentId)
                .eq(OptionSet::getName, optionCn)
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode());
        List<OptionSet> list = optionSetMapper.selectList(wrapper);
        return PkListUtil.first(list);
    }

    public OptionSet getByParentCodeCode(Long parentId, String optionCode) {
        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OptionSet::getParentId, parentId)
                .eq(OptionSet::getCode, optionCode)
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode());
        List<OptionSet> list = optionSetMapper.selectList(wrapper);
        return PkListUtil.first(list);
    }

    public List<OptionSet> getValueByParentIdAndCode(Long optionSetId, String valueCode) {
        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OptionSet::getParentId, optionSetId)
                .eq(OptionSet::getCode, valueCode)
                .eq(OptionSet::getNodeType, OptionSetNodeEnum.LEAF_NODE.getCode())
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode());
        return optionSetMapper.selectList(wrapper);
    }

    public List<OptionSet> getListBySetIdAndCodeList(Long parentId, List<String> codeList) {
        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OptionSet::getParentId, parentId)
                .in(OptionSet::getCode, codeList)
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode());
        return optionSetMapper.selectList(wrapper);
    }

    public boolean deleteById(Long valueId) {
        return optionSetMapper.deleteById(valueId) == 1;
    }

    public boolean deleteByParentId(Long parentId) {
        LambdaQueryWrapper<OptionSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OptionSet::getParentId, parentId)
                .eq(OptionSet::getDeleted, DeletedEnum.UNDELETED.getCode());
        return optionSetMapper.delete(wrapper) > 0;
    }
}
