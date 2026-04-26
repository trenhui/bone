package com.bone.tpa.core.util.localImageTool.response;

import lombok.Data;

@Data
public class ImageToolResponse {

    private boolean success;

    private String msg;

    private Integer code;

    private Integer costTime;

    private Content data;

    @Data
    public static class Content {

        private String classify;

        private double prob;

        private Integer inferCostTime;
    }

    public boolean success() {
        return success && Integer.valueOf(200).equals(code);
    }
}
