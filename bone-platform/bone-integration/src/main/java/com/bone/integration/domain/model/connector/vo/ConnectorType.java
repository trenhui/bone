package com.bone.integration.domain.model.connector.vo;

import com.bone.core.exception.DomainException;

public enum ConnectorType {
    REST, SOAP, JDBC, FTP, MQ, FILE, HTTP, HTTPS, SMTP, POP3, IMAP, SFTP, S3, AZURE_BLOB, GOOGLE_CLOUD_STORAGE, KAFKA, RABBITMQ, REDIS, ELASTICSEARCH, MONGO_DB;

    public static ConnectorType fromString(String type) {
        if (type == null || type.isBlank()) {
            throw new DomainException("连接器类型不能为空");
        }
        try {
            return ConnectorType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DomainException("不支持的连接器类型: " + type);
        }
    }
}