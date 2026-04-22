package com.bone.masterdata.adapter.web.converter;

import com.bone.masterdata.adapter.web.dto.req.CreateMasterDataEntityReq;
import com.bone.masterdata.adapter.web.dto.req.UpdateMasterDataEntityReq;
import com.bone.masterdata.adapter.web.dto.resp.MasterDataEntityDetailResp;
import com.bone.masterdata.application.command.cmd.CreateMasterDataEntityCmd;
import com.bone.masterdata.application.command.cmd.UpdateMasterDataEntityCmd;
import com.bone.masterdata.application.query.dto.MasterDataEntityDTO;
import org.springframework.stereotype.Component;

@Component
public class MasterDataEntityWebConverter {

    public CreateMasterDataEntityReq toReq(CreateMasterDataEntityCmd cmd) {
        CreateMasterDataEntityReq req = new CreateMasterDataEntityReq();
        req.setName(cmd.getName());
        req.setDescription(cmd.getDescription());
        req.setCategory(cmd.getCategory());
        return req;
    }

    public CreateMasterDataEntityCmd toCmd(CreateMasterDataEntityReq req) {
        return CreateMasterDataEntityCmd.builder()
                .name(req.getName())
                .description(req.getDescription())
                .category(req.getCategory())
                .build();
    }

    public UpdateMasterDataEntityReq toReq(UpdateMasterDataEntityCmd cmd) {
        UpdateMasterDataEntityReq req = new UpdateMasterDataEntityReq();
        req.setName(cmd.getName());
        req.setDescription(cmd.getDescription());
        req.setCategory(cmd.getCategory());
        return req;
    }

    public UpdateMasterDataEntityCmd toCmd(Long id, UpdateMasterDataEntityReq req) {
        return UpdateMasterDataEntityCmd.builder()
                .id(id)
                .name(req.getName())
                .description(req.getDescription())
                .category(req.getCategory())
                .build();
    }

    public MasterDataEntityDetailResp toResp(MasterDataEntityDTO dto) {
        return MasterDataEntityDetailResp.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .category(dto.getCategory())
                .status(dto.getStatus())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .fieldCount(dto.getFieldCount())
                .build();
    }
}
