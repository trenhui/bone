package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.StatusEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.MetaBizModel;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.MetaBizModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

@Service
public class MetaBizModelService {

    @Autowired
    private MetaBizModelMapper metaBizModelMapper;

    public List<MetaBizModel> getAllModel() {
        List<MetaBizModel> fieldList = metaBizModelMapper.selectList(new LambdaQueryWrapper<MetaBizModel>()
                .eq(MetaBizModel::getStatus, StatusEnum.YES.getCode())
                .eq(MetaBizModel::getDeleted, DeletedEnum.UNDELETED.getCode()));

        TreeSet<MetaBizModel> set = new TreeSet<>(Comparator.comparing(MetaBizModel::getCode));
        for (MetaBizModel field : fieldList) {
            if (set.contains(field)) continue;
            set.add(field);
        }
        return new ArrayList<>(set);
    }

    public Page<MetaBizModel> getDOByModelCode(Long pageNum, Long pageSize, String modelCode, String fieldName, String componentType) {
        LambdaQueryWrapper<MetaBizModel> wrapper = new LambdaQueryWrapper<MetaBizModel>()
                .eq(MetaBizModel::getCode, modelCode)
                .like(StringUtils.hasText(fieldName), MetaBizModel::getFieldName, fieldName)
                .eq(StringUtils.hasText(componentType), MetaBizModel::getComponentType, componentType)
                .eq(MetaBizModel::getStatus, StatusEnum.YES.getCode())
                .eq(MetaBizModel::getDeleted, DeletedEnum.UNDELETED.getCode());
        Page<MetaBizModel> page = new Page<>(pageNum, pageSize);
        metaBizModelMapper.selectPage(page, wrapper);
        return page;
    }
}
