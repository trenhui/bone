package com.bone.masterdata.adapter.web.converter;

import com.bone.masterdata.adapter.web.dto.req.CreateMasterDataRecordReq;
import com.bone.masterdata.adapter.web.dto.req.UpdateMasterDataRecordReq;
import com.bone.masterdata.adapter.web.dto.resp.MasterDataRecordDetailResp;
import com.bone.masterdata.application.command.cmd.CreateMasterDataRecordCmd;
import com.bone.masterdata.application.command.cmd.UpdateMasterDataRecordCmd;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import org.springframework.stereotype.Component;

@Component
public class MasterDataRecordWebConverter {

    public CreateMasterDataRecordCmd toCmd(CreateMasterDataRecordReq req) {
        return CreateMasterDataRecordCmd.builder()
                .masterDataEntityId(req.getMasterDataEntityId())
                .data(req.getData())
                .build();
    }

    public UpdateMasterDataRecordCmd toCmd(Long id, UpdateMasterDataRecordReq req) {
        return UpdateMasterDataRecordCmd.builder()
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
