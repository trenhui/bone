/// <reference lib="webworker" />
/**
 * 大文件 SHA-256 计算 Worker。
 *
 * <p>产物必须与主线程 `crypto.subtle.digest('SHA-256', file.arrayBuffer())` **逐字节一致**，
 * 否则后端以 contentHash 做去重/校验时会断裂。这里只是把计算移出主线程，不改变算法。
 */
self.onmessage = async (event: MessageEvent<{ id: string; file: File }>) => {
  const { id, file } = event.data;
  try {
    const buffer = await file.arrayBuffer();
    const digest = await crypto.subtle.digest('SHA-256', buffer);
    const hex = Array.from(new Uint8Array(digest))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');
    (self as unknown as DedicatedWorkerGlobalScope).postMessage({ id, hex });
  } catch (error) {
    (self as unknown as DedicatedWorkerGlobalScope).postMessage({
      id,
      error: error instanceof Error ? error.message : String(error),
    });
  }
};
