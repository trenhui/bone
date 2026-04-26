package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.core.exception.ServiceException;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.StatusEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgBlockDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFormDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgPageDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.BlockMapper;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.FormMapper;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.PageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class BlockService {

    @Autowired
    private BlockMapper blockMapper;

    @Autowired
    private PageMapper pageMapper;

    @Autowired
    private FormMapper formMapper;

    public List<CfgBlockDO> getDOListByFormId(Long formId) {
        LambdaQueryWrapper<CfgBlockDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CfgBlockDO::getFormId, formId)
                .eq(CfgBlockDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return blockMapper.selectList(wrapper);
    }


    public List<CfgBlockDO> getDOListByBasicPageCode(String pageCode) {
        CfgPageDO pageDO = pageMapper.selectOne(new LambdaQueryWrapper<CfgPageDO>()
                .eq(CfgPageDO::getCode, pageCode)
                .isNull(CfgPageDO::getBizIdentityCode)
                .eq(CfgPageDO::getDeleted, DeletedEnum.UNDELETED.getCode())
                .select(CfgPageDO::getId));
        if (pageDO == null) throw new ServiceException(500, "未找到此基础页面");
        CfgFormDO formDO = formMapper.selectOne(new LambdaQueryWrapper<CfgFormDO>()
                .eq(CfgFormDO::getPageId, pageDO.getId())
                .eq(CfgFormDO::getDeleted, DeletedEnum.UNDELETED.getCode())
                .select(CfgFormDO::getId));
        if (formDO == null) throw new ServiceException(500, "未找到此基础页面");
        List<CfgBlockDO> blockDOList = blockMapper.selectList(
                new LambdaQueryWrapper<CfgBlockDO>()
                        .eq(CfgBlockDO::getFormId, formDO.getId())
                        .eq(CfgBlockDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
        return blockDOList;
    }


    public boolean batchSave(List<CfgBlockDO> blockDOList) {
        blockMapper.insert(blockDOList);
        return true;
    }

    public List<CfgBlockDO> getDOListByPageId(Long pageId) {
        LambdaQueryWrapper<CfgBlockDO> wrapper = new LambdaQueryWrapper<CfgBlockDO>()
                .eq(CfgBlockDO::getPageId, pageId)
                .eq(CfgBlockDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return blockMapper.selectList(wrapper);
    }

    public List<CfgBlockDO> getDOListByPageIds(Collection<Long> pageIds) {
        LambdaQueryWrapper<CfgBlockDO> wrapper = new LambdaQueryWrapper<CfgBlockDO>()
                .in(CfgBlockDO::getPageId, pageIds)
                .eq(CfgBlockDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return blockMapper.selectList(wrapper);
    }

    public List<CfgBlockDO> getByIds(Collection<Long> ids) {
        LambdaQueryWrapper<CfgBlockDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CfgBlockDO::getId, ids)
                .eq(CfgBlockDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return blockMapper.selectList(wrapper);
    }

    public CfgBlockDO getDOById(Long id) {
        return blockMapper.selectOne(new LambdaQueryWrapper<CfgBlockDO>().eq(CfgBlockDO::getId, id)
                .eq(CfgBlockDO::getDeleted, DeletedEnum.UNDELETED.getCode())
                .eq(CfgBlockDO::getStatus, StatusEnum.YES.getCode()));
    }

    public boolean updateById(CfgBlockDO blockDO) {
        return blockMapper.updateById(blockDO) > 0;
    }

    public boolean deleteByIdList(List<Long> idList) {
        int i = blockMapper.deleteByIds(idList);
        return i > 0;
    }
}
