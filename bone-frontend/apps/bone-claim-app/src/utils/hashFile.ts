/**
 * 文件哈希入口：优先在 Worker 中计算，避免大文件 digest 阻塞主线程。
 *
 * 只在用户真正上传时动态 import（见调用点），故单测/SSR 不会初始化 Worker。
 * 摘要口径与旧实现完全相同；Worker 不可用时按原路径主线程兜底，功能不降级。
 */
export async function hashFile(file: File): Promise<string> {
  if (typeof Worker === 'undefined') return hashOnMainThread(file);
  try {
    const worker = new Worker(new URL('../workers/sha256.worker.ts', import.meta.url), {
      type: 'module',
    });
    try {
      return await new Promise<string>((resolve, reject) => {
        worker.onmessage = (event: MessageEvent<{ id: string; hex?: string; error?: string }>) => {
          if (event.data.error) reject(new Error(event.data.error));
          else if (event.data.hex) resolve(event.data.hex);
        };
        worker.onerror = (event) => reject(new Error(event.message || '哈希 Worker 运行失败'));
        worker.postMessage({ id: 'hash', file });
      });
    } finally {
      worker.terminate();
    }
  } catch {
    // qiankun 下子应用资源来自 :3013，而页面源是 shell :3000：
    // `new Worker(new URL(...))` 会同步抛 SecurityError（实测材料上传整链断在这里）。
    // 文件头承诺「Worker 不可用时主线程兜底」，构造/运行失败都必须走兜底，不能把原始异常抛给用户。
    return hashOnMainThread(file);
  }
}

async function hashOnMainThread(file: File): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}
