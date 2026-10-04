package com.bone.file.common;

import java.util.Objects;

/**
 * 存储桶（bucket）白名单校验：把「桶名是否允许由调用方指定」收口到一处。
 *
 * <p><b>为何需要它（FL-6，2026-10-04 加固）</b>：上传/下载/删除三个端点都把 {@code bucket} 作为 {@code @RequestParam}
 * 交给客户端指定，而 {@link FileObjectKeyGenerator#assertTenantScope(String, Long)} 只校验<b>对象键前缀</b>（{@code
 * objectName.startsWith(tenantId + "-")}），完全不校验桶。二者合起来的效果是： 攻击者构造 {@code GET
 * /api/v1/file/files/42-anything.pdf?bucket=other-tenant-bucket} 时，前缀校验通过 （键名带的是自己租户前缀），桶名却原样下发到
 * MinIO —— <b>当前租户可读写任意桶</b>。只要平台把备份桶、 日志桶或其他租户的独立桶部署在同一 MinIO 实例（桶名可枚举：默认值 {@code platform-files}
 * 写在 {@code application.yml}），任一登录用户即可跨桶读写。
 *
 * <p><b>为何是「等于配置桶」而不是「维护一个桶名集合」</b>：本模块只部署了一个桶（{@code bone.file.minio.bucket}）， 端口签名 {@code
 * (bucket, objectName, ...)} 里的 bucket 参数本就是「留空即用默认」的语义。维护白名单集合
 * 会让人误以为支持多桶，而多桶的真实需求（按租户映射桶、按业务域分桶）尚未落地；真落地时也应由服务端按租户
 * <b>推导</b>桶名，而不是把桶名作为可枚举的客户端输入。故此处取最保守口径：<b>非空且不等于配置桶 ⇒ 403</b>。
 *
 * <p><b>失败关闭</b>：配置桶缺失（blank）时本类拒绝一切非空请求，而不是放行任意桶 —— 配置缺失属部署问题， 放行等于静默降级成「任意桶可写」。
 *
 * <p><b>为何放在 common 而非 controller 内的私有方法</b>：三个端点各写一次判断，第三个漏写就重新出现越权 （与本仓「授权判据只写一半」的既有教训同类）。校验器与
 * {@link FileObjectKeyGenerator} 同属「对象寻址安全」 收口点，便于一处收口、一处测。
 */
public final class BucketPolicy {

  private BucketPolicy() {}

  /**
   * 校验客户端指定的桶名是否可接受。
   *
   * @param requested 客户端通过 {@code bucket} 参数传入的值；{@code null}/空白 表示「用配置桶」，直接放行
   * @param configuredBucket 服务端配置的实际桶名（{@code bone.file.minio.bucket}），不可为空白
   * @throws com.bone.core.exception.BizException 桶名不被允许时（{@link FileErrorCodes#ACCESS_DENIED}）
   */
  public static void assertBucketAllowed(String requested, String configuredBucket) {
    if (requested == null || requested.isBlank()) {
      // 未指定桶 ⇒ 由存储适配器回落到配置桶（既有语义，见 MinioFileStorageService 的 targetBucket 逻辑）
      return;
    }
    if (configuredBucket == null || configuredBucket.isBlank()) {
      // 失败关闭：配置桶缺失时不能放行任意桶（否则「忘记配置」= 一次静默降级为全桶可写）
      throw FileErrors.of(FileErrorCodes.ACCESS_DENIED, "存储桶未配置，拒绝指定 bucket 的请求");
    }
    if (!Objects.equals(requested, configuredBucket)) {
      throw FileErrors.of(FileErrorCodes.ACCESS_DENIED, "bucket " + requested);
    }
  }
}
