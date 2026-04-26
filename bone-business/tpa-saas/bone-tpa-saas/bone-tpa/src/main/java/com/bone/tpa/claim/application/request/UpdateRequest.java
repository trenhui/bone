package com.bone.tpa.claim.application.request;

import com.bone.tpa.claim.application.response.GenericQueryRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class UpdateRequest implements Serializable {

    @Schema(description = "更新信息")
    private GenericQueryRequest updateData;

    @Schema(description = "模型名称")
    private List<String> modelNames = new ArrayList<>();
}
