package com.bone.tpa.intelligent.adjustment.dto.request;

import com.bone.tpa.intelligent.adjustment.dto.LiabilitySharingRelationDTO;
import lombok.Data;

import java.util.List;

@Data
public class ShareRelationCreateRequest {

    List<LiabilitySharingRelationDTO> dtoList;
}
