package com.bone.lowcode.infra.application.convert;

import com.bone.lowcode.infra.application.vo.page.pageJson.Block;
import com.bone.lowcode.infra.domain.valueobject.BlockEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgBlockDO;

import java.util.ArrayList;
import java.util.List;

public class BlockConvert {

    public static List<Block> DOListToBlockList(List<CfgBlockDO> blockDOList) {
        List<Block> blockList = new ArrayList<>();
        for (CfgBlockDO blockDO : blockDOList) {
            Block block = new Block();
            block.setId(blockDO.getId().toString());
            block.setType(BlockEnum.OTHER_BLOCK.getType());
            block.setName(blockDO.getName());
            block.setSequenceNumber(blockDO.getSequenceNumber());
            if (BlockEnum.CLAIM_INFO.getName().equals(blockDO.getName())) {
                block.setType(BlockEnum.CLAIM_INFO.getType());
            }
            blockList.add(block);
        }
        return blockList;
    }
}
