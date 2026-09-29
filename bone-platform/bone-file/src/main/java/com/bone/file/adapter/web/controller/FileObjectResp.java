package com.bone.file.adapter.web.controller;

import com.bone.file.domain.model.FileObject;

/**
 * 上传结果响应体（FL-7）。
 *
 * <p><b>为何不再只返回 objectName 字符串</b>：AIP-133 要求创建操作回传资源表示， 只给键名会让前端拿不到大小 / 内容类型 / 原始名，必须再发一次请求或自行猜测。
 *
 * <p><b>为何没有 {@code id}</b>：库表主键要等 FL-4 的 {@code file_object} 表（L3 待审批）。 在表落地前，{@code objectName}
 * 是对象的自然标识（且已含租户前缀），故以它作为句柄返回。
 */
public record FileObjectResp(
    String objectName,
    String originalName,
    String bucket,
    String contentType,
    long size,
    Long tenantId) {

  /** 由领域对象映射。 */
  public static FileObjectResp of(FileObject fileObject) {
    return new FileObjectResp(
        fileObject.getObjectName(),
        fileObject.getOriginalName(),
        fileObject.getBucket(),
        fileObject.getContentType(),
        fileObject.getSize(),
        fileObject.getTenantId());
  }
}
