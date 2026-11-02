/**
 * synapClaim · 证据材料 + 附件上传（A2 Evidence 聚合 + bone-file 平台文件服务）。
 *
 * 影像本体走附件服务 STS 直传，本接口只登记 fileRef。
 */

import { createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';
import type { EvidenceResp, FileObjectResp, RegisterEvidenceReq } from './types';
import { newIdempotencyKey } from './claim';

const client = createApiClient('/api/v1/claims');

/** 平台文件服务（bone-file，端口 8107；PlatformApiPaths.FILE_V1 = /api/v1/file）。 */
const fileClient = createApiClient('/api/v1/file');

export const evidenceApi = {
  list(claimId: string): Promise<ApiResponse<EvidenceResp[]>> {
    return client.get<never, ApiResponse<EvidenceResp[]>>(`/${claimId}/evidences`);
  },
  /**
   * 登记材料，返回 evidenceId。
   *
   * 类型必须是 string 而非 number：后端返回 ApiResponse<Long>（雪花 ID），平台
   * Long→String 序列化后 JSON 里是字符串（ADR-0040：number 在 2^53 截断的真实事故）。
   */
  register(claimId: string, req: RegisterEvidenceReq, idempotencyKey = newIdempotencyKey()): Promise<ApiResponse<string>> {
    return client.post<RegisterEvidenceReq, ApiResponse<string>>(`/${claimId}/evidences`, req, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  },
  /** 锁定证据集（理算前置）。返回锁定的材料条数（小整数，number 无损）。 */
  lock(claimId: string): Promise<ApiResponse<number>> {
    return client.post<never, ApiResponse<number>>(`/${claimId}/evidences/lock`);
  },
  /** 回填解析结果（OCR/LLM 回调或人工补录）。 */
  markParsed(
    claimId: string,
    evidenceId: string,
    req: { ocrStatus?: string; ocrConfidence?: number; extractedJson?: string; verifyValid?: string },
  ): Promise<ApiResponse<void>> {
    return client.post<unknown, ApiResponse<void>>(`/${claimId}/evidences/${evidenceId}/parse-result`, req);
  },
};

export const attachmentApi = {
  /**
   * 上传影像本体到平台附件服务（multipart）。返回 FileObjectResp.objectName 即证据材料登记的 fileRef。
   * 证据登记只需 fileRef + 内容哈希，影像本体不经 synap-claim 应用线程池（§7.2）。
   *
   * @param opts.onProgress 上传百分比（0–100）。理赔影像动辄数十 MB，没有进度条时页面在哈希+上传期间
   *     完全无反馈，用户会重复点"上传"——重复登记由 contentHash 去重兜底，但交互上仍是缺陷。
   */
  upload(
    file: File,
    opts?: { bucket?: string; onProgress?: (percent: number) => void },
  ): Promise<ApiResponse<FileObjectResp>> {
    const formData = new FormData();
    formData.append('file', file);
    if (opts?.bucket) formData.append('bucket', opts.bucket);
    return fileClient.post<FormData, ApiResponse<FileObjectResp>>('/files', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      // e.total 在部分浏览器首个事件里为 undefined ⇒ 只在已知总量时给百分比，否则报 0 而非 NaN
      onUploadProgress: opts?.onProgress
        ? (e) => opts.onProgress?.(e.total ? Math.round((e.loaded / e.total) * 100) : 0)
        : undefined,
    });
  },

  /**
   * 下载地址（fileRef → bone-file GET /files/{objectName}）。
   *
   * 与 upload 共用同一前缀（VITE_API_BASE_URL + /api/v1/file）。页面里裸写
   * /api/v1/file/files/... 会绕过 VITE_API_BASE_URL，在网关前缀变更、多环境或子应用独立部署时
   * 必然失配（上传走客户端、下载走硬编码，两边不同源）。所有文件下载地址都应经此方法生成。
   */
  downloadUrl(objectName: string): string {
    return `${import.meta.env.VITE_API_BASE_URL || ''}/api/v1/file/files/${encodeURIComponent(objectName)}`;
  },
};
