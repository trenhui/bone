import { useEffect, useState, type CSSProperties, type ReactElement } from 'react';
import { getToken } from '@bone/shared-services';
import { attachmentApi } from '../services/claimApi';

/**
 * 影像件预览图（带 JWT 的 blob 加载）。
 *
 * <p><b>为什么不能直接用 &lt;img src={downloadUrl}&gt;</b>：bone-file 下载端点要求认证
 * （网关白名单只放登录/回调/健康），img 标签不会携带 Authorization，裸 URL 必然 401。
 * 这里用 fetch 带 token 取 blob 再生成本地 objectURL，组件卸载时 revoke 防内存泄漏。
 */
export function isPreviewableImage(fileRef?: string): boolean {
  return Boolean(fileRef && /\.(png|jpe?g|webp)$/i.test(fileRef));
}

export interface EvidenceImageProps {
  fileRef?: string;
  alt?: string;
  style?: CSSProperties;
  testId?: string;
}

export function EvidenceImage({ fileRef, alt = '影像预览', style, testId }: EvidenceImageProps): ReactElement {
  const [src, setSrc] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setSrc(null);
    setFailed(false);
    if (!isPreviewableImage(fileRef)) return;
    let alive = true;
    let objectUrl: string | null = null;
    const token = getToken();
    fetch(
      attachmentApi.downloadUrl(fileRef as string),
      token ? { headers: { Authorization: `Bearer ${token}` } } : undefined,
    )
      .then((resp) => {
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        return resp.blob();
      })
      .then((blob) => {
        objectUrl = URL.createObjectURL(blob);
        if (alive) setSrc(objectUrl);
        else URL.revokeObjectURL(objectUrl);
      })
      .catch(() => {
        if (alive) setFailed(true);
      });
    return () => {
      alive = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [fileRef]);

  if (!isPreviewableImage(fileRef)) {
    return <span data-testid={testId} style={style}>该材料暂无可内嵌预览的图片</span>;
  }
  if (!src) {
    return <span data-testid={testId} style={style}>{failed ? '影像加载失败（请检查文件服务）' : '影像加载中…'}</span>;
  }
  return <img data-testid={testId} alt={alt} src={src} style={style} />;
}

/** 同样带 JWT 触发浏览器下载；文件名取自 objectName（服务端生成的租户前缀键）。 */
export async function downloadEvidenceFile(fileRef: string): Promise<void> {
  const token = getToken();
  const resp = await fetch(
    attachmentApi.downloadUrl(fileRef),
    token ? { headers: { Authorization: `Bearer ${token}` } } : undefined,
  );
  if (!resp.ok) throw new Error(`下载影像失败：HTTP ${resp.status}`);
  const objectUrl = URL.createObjectURL(await resp.blob());
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = fileRef.split('/').pop() || 'evidence';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(objectUrl);
}
