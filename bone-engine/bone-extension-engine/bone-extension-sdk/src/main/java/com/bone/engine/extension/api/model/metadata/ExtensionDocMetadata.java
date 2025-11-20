package com.bone.engine.extension.api.model.metadata;

import lombok.Data;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @ExtensionDoc 文档元数据
 */
@Data
public class ExtensionDocMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name = "";
    private String domain = "";
    private String category = "";
    private String description = "";
    private String scenario = "";
    private String feature = "";
    private String configuration = "";
    private String performance = "";
    private String note = "";
    private String limitation = "";
    private String version = "1.0.0";
    private String author = "";
    private String created = "";
    private String updated = "";
    private String differences = "";
    private String dependencies = "";
    private String capabilities = "";

    private final List<FAQ> faqs = new ArrayList<>();
    private final List<Change> changes = new ArrayList<>();

    @Data
    public static class FAQ implements Serializable {
        private String question;
        private String answer;
    }

    @Data
    public static class Change implements Serializable {
        private String version;
        private String description = "";
        private String date = "";
    }
}