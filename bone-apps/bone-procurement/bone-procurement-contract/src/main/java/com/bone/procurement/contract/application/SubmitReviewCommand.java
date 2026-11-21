package com.bone.procurement.contract.application;

import java.io.InputStream;

/**
 * 提交审查命令：租户与 reviewId 都不在 command 内，由登录态端口取 / 服务端生成，杜绝自报业务键。
 *
 * @param contractNo 合同号
 * @param document 文档流（walking skeleton 为空流，真实文件读取属 L3）
 * @param sourceRef 文档来源引用
 * @param docId 文档引用
 * @param idempotencyKey 请求携带的投递幂等键（ULID/UUID），缺失时由服务端按 docId/sourceRef 派生
 */
public record SubmitReviewCommand(
    String contractNo,
    InputStream document,
    String sourceRef,
    String docId,
    String idempotencyKey) {}
