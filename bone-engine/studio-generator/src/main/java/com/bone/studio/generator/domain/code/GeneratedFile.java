package com.bone.studio.generator.domain.code;

public class GeneratedFile {
    private String fileName;
    private String filePath;
    private String content;
    private String fileType;
    private long fileSize;

    private GeneratedFile() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getContent() {
        return content;
    }

    public String getFileType() {
        return fileType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public static class Builder {
        private String fileName;
        private String filePath;
        private String content;
        private String fileType;
        private long fileSize;

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder fileType(String fileType) {
            this.fileType = fileType;
            return this;
        }

        public Builder fileSize(long fileSize) {
            this.fileSize = fileSize;
            return this;
        }

        public GeneratedFile build() {
            GeneratedFile file = new GeneratedFile();
            file.fileName = fileName;
            file.filePath = filePath;
            file.content = content;
            file.fileType = fileType;
            file.fileSize = fileSize;
            return file;
        }
    }
}