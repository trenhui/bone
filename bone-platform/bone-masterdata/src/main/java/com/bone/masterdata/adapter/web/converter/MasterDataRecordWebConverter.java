package com.bone.masterdata.adapter.web.converter;

import com.bone.masterdata.adapter.web.dto.req.CreateMasterDataRecordReq;
import com.bone.masterdata.adapter.web.dto.req.UpdateMasterDataRecordReq;
import com.bone.masterdata.adapter.web.dto.resp.MasterDataRecordDetailResp;
import com.bone.masterdata.application.command.cmd.CreateMasterDataRecordCommand;
import com.bone.masterdata.application.command.cmd.UpdateMasterDataRecordCommand;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import org.springframework.stereotype.Component;

@Component
public class MasterDataRecordWebConverter {

    public CreateMasterDataRecordCommand toCommand(CreateMasterDataRecordReq req) {
        return CreateMasterDataRecordCommand.builder()
                .masterDataEntityId(req.getMasterDataEntityId())
                .data(req.getData())
                .build();
    }

    public UpdateMasterDataRecordCommand toCommand(Long id, UpdateMasterDataRecordReq req) {
        return UpdateMasterDataRecordCommand.builder()
                .id(id)
                .data(req.getData())
                .build();
    }

    public MasterDataRecordDetailResp toResp(MasterDataRecordDTO dto) {
        return MasterDataRecordDetailResp.builder()
                .id(dto.getId())
                .masterDataEntityId(dto.getMasterDataEntityId())
                .data(dto.getData())
                .status(dto.getStatus())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .publishTime(dto.getPublishTime())
                .build();
    }
}
