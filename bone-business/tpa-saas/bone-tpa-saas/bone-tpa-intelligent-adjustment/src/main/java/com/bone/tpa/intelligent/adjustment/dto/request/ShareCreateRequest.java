package com.bone.tpa.intelligent.adjustment.dto.request;

import com.bone.tpa.intelligent.adjustment.dto.LiabilityShareDTO;
import lombok.Data;

import java.util.List;

@Data
public class ShareCreateRequest {

    List<LiabilityShareDTO> dtoList;
}
