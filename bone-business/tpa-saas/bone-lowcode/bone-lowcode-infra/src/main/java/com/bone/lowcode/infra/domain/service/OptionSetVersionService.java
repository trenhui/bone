package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.OptionSet;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.OptionSetVersion;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.OptionSetVersionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OptionSetVersionService {

    @Autowired
    private OptionSetVersionMapper optionSetVersionMapper;

    public Long getCountBySetId(Long optionSetId) {
        return optionSetVersionMapper.selectCount(new LambdaQueryWrapper<OptionSetVersion>()
                .eq(OptionSetVersion::getOptionSetId, optionSetId)
                .eq(OptionSetVersion::getDeleted, DeletedEnum.UNDELETED.getCode())
        );
    }

    public OptionSetVersion getLatestBySetId(Long optionSetId) {
        return optionSetVersionMapper.selectOne(new LambdaQueryWrapper<OptionSetVersion>()
                .eq(OptionSetVersion::getOptionSetId, optionSetId)
                .eq(OptionSetVersion::getDeleted, DeletedEnum.UNDELETED.getCode())
                .orderByDesc(OptionSetVersion::getCreateTime)
                .last("limit 1"));
    }

    public Boolean add(OptionSetVersion version) {
        return optionSetVersionMapper.insert(version) > 0;
    }

    public List<OptionSetVersion> getListByOptionSetId(Long optionSetId) {
        return optionSetVersionMapper.selectList(new LambdaQueryWrapper<OptionSetVersion>()
                .eq(OptionSetVersion::getOptionSetId, optionSetId)
                .eq(OptionSetVersion::getDeleted, DeletedEnum.UNDELETED.getCode())
                .orderByDesc(OptionSetVersion::getCreateTime)
        );
    }

    public List<OptionSet> getReleaseSetList() {
        LambdaQueryWrapper<OptionSetVersion> lqw = new QueryWrapper<OptionSetVersion>()
                .select("DISTINCT option_set_id")
                .lambda()
                .eq(OptionSetVersion::getDeleted, DeletedEnum.UNDELETED.getCode());
        List<OptionSetVersion> list = optionSetVersionMapper.selectList(lqw);
        List<Long> setIdList = list.stream().map(OptionSetVersion::getOptionSetId).toList();

        List<OptionSet> optionSetList = setIdList.stream().map(i -> {
            OptionSetVersion latestVersion = getLatestBySetId(i);
            return latestVersion.getOptionSet();
        }).toList();
        return optionSetList;
    }

    public List<OptionSetVersion> getLatestOptionSetVersionList() {
        LambdaQueryWrapper<OptionSetVersion> lqw = new QueryWrapper<OptionSetVersion>()
                .select("DISTINCT option_set_id")
                .lambda()
                .eq(OptionSetVersion::getDeleted, DeletedEnum.UNDELETED.getCode());
        List<OptionSetVersion> list = optionSetVersionMapper.selectList(lqw);
        List<Long> setIdList = list.stream().map(OptionSetVersion::getOptionSetId).toList();

        return setIdList.stream().map(this::getLatestBySetId).toList();
    }

    public boolean deleteByOptionSetId(Long optionSetId) {
//        LambdaUpdateWrapper<OptionSetVersion> wrapper = new LambdaUpdateWrapper<>();
//        wrapper.eq(OptionSetVersion::getOptionSetId, optionSetId)
//                .set(OptionSetVersion::getDeleted, DeletedEnum.DELETED.getCode());
        LambdaQueryWrapper<OptionSetVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OptionSetVersion::getOptionSetId, optionSetId);
        return optionSetVersionMapper.delete(wrapper) > 0;
    }

    public Page<OptionSetVersion> page(LambdaQueryWrapper<OptionSetVersion> wrapper, Long pageNum, Long pageSize) {
        Page<OptionSetVersion> page = new Page<>(pageNum, pageSize);
        optionSetVersionMapper.selectPage(page, wrapper);
        return page;
    }
}
