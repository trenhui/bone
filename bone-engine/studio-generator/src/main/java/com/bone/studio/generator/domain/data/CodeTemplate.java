package com.bone.studio.generator.domain.data;

import com.bone.core.domain.AggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Table;

import java.time.LocalDateTime;
import java.util.List;

@Table("gen_code_template")
public class CodeTemplate extends AggregateRoot<Long> {

    private Long id;
    private Long tenantId;
    private String name;
    private String code;
    private String description;
    private String type;
    private String language;
    private String engine;
    private String version;
    private String content;
    private String sampleOutput;
    private String status;
    private LocalDateTime publishedAt;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;
    private int versionLock;

    private CodeTemplate() {
    }

    public static CodeTemplate create(Long id, Long tenantId, String name, String code, 
                                    String description, String type, String content) {
        CodeTemplate template = new CodeTemplate();
        template.id = id;
        template.tenantId = tenantId;
        template.name = name;
        template.code = code;
        template.description = description;
        template.type = type;
        template.language = "java";
        template.engine = "FREEMARKER";
        template.version = "1.0.0";
        template.content = content;
        template.status = "DRAFT";
        template.createdAt = LocalDateTime.now();
        template.updatedAt = LocalDateTime.now();
        template.deleted = false;
        template.versionLock = 0;
        return template;
    }

    public void publish() {
        this.status = "PUBLISHED";
        this.publishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.versionLock++;
    }

    public void updateContent(String content) {
        this.content = content;
        this.status = "DRAFT";
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getType() {
        return type;
    }

    public String getLanguage() {
        return language;
    }

    public String getEngine() {
        return engine;
    }

    public String getVersion() {
        return version;
    }

    public String getContent() {
        return content;
    }

    public String getSampleOutput() {
        return sampleOutput;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public int getVersionLock() {
        return versionLock;
    }

    public static class Builder {
        private Long id;
        private Long tenantId;
        private String name;
        private String code;
        private String description;
        private String type;
        private String language;
        private String engine;
        private String version;
        private String content;
        private String sampleOutput;
        private String status;
        private LocalDateTime publishedAt;
        private Long createdBy;
        private Long updatedBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private boolean deleted;
        private int versionLock;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder tenantId(Long tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder engine(String engine) {
            this.engine = engine;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder sampleOutput(String sampleOutput) {
            this.sampleOutput = sampleOutput;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder publishedAt(LocalDateTime publishedAt) {
            this.publishedAt = publishedAt;
            return this;
        }

        public Builder createdBy(Long createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder updatedBy(Long updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder deleted(boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        public Builder versionLock(int versionLock) {
            this.versionLock = versionLock;
            return this;
        }

        public CodeTemplate build() {
            CodeTemplate template = new CodeTemplate();
            template.id = id;
            template.tenantId = tenantId;
            template.name = name;
            template.code = code;
            template.description = description;
            template.type = type;
            template.language = language;
            template.engine = engine;
            template.version = version;
            template.content = content;
            template.sampleOutput = sampleOutput;
            template.status = status;
            template.publishedAt = publishedAt;
            template.createdBy = createdBy;
            template.updatedBy = updatedBy;
            template.createdAt = createdAt;
            template.updatedAt = updatedAt;
            template.deleted = deleted;
            template.versionLock = versionLock;
            return template;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
