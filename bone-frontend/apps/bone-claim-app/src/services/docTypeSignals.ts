/**
 * 上传影像时提取版式信号，供后端 DocTypeClassifier 做内容级自动分类。
 *
 * 背景：TPA 签名影像只有 hash 文件名（如 1CA9AC...jpg），纯文件名规则会把它误判为发票；
 * 「汕头口腔医疗中心-收费单」按"收费单"关键词也会误判为发票（实为费用清单）。
 * 因此上传时在浏览器 canvas 上按后端约定算法提取 3 个数值信号，与文件名一起交给后端融合判定：
 *   - inkRatio：偏离背景中位色的前景像素占比（签名低、票据高）
 *   - inkConcentration：前景在最密集 4 格（8×6 网格）的占比（签名高、票据低）
 *   - tableLineRatio：含长表格线的行占比（票据/清单高、签名低）
 *
 * 不传或提取失败时字段为 undefined，后端退化为纯文件名分类（向后兼容）。
 * 全程在前端完成，不上传缩略图、不经后端线程池解码影像（§7.2 上传通道纪律）。
 */
export interface DocTypeImageSignals {
  inkRatio?: number;
  inkConcentration?: number;
  tableLineRatio?: number;
  aspectPercent?: number;
}

const SAMPLE_W = 160;
const SAMPLE_H = 120;
const GRID_W = 8;
const GRID_H = 6;

/** 从图片 File 提取版式信号；失败（非图片/解码异常）返回空对象，不影响登记主流程。 */
export async function extractDocTypeSignals(file: File): Promise<DocTypeImageSignals> {
  if (!file.type?.startsWith('image/')) return {};
  try {
    const bitmap = await createImageBitmap(file);
    const canvas = document.createElement('canvas');
    canvas.width = SAMPLE_W;
    canvas.height = SAMPLE_H;
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    if (!ctx) return {};
    ctx.drawImage(bitmap, 0, 0, SAMPLE_W, SAMPLE_H);
    bitmap.close();

    const data = ctx.getImageData(0, 0, SAMPLE_W, SAMPLE_H).data;
    const gray = new Uint8Array(SAMPLE_W * SAMPLE_H);
    for (let i = 0; i < gray.length; i++) {
      const o = i * 4;
      gray[i] = Math.round(0.299 * data[o] + 0.587 * data[o + 1] + 0.114 * data[o + 2]);
    }
    const signals = signalsFromGray(gray, SAMPLE_W, SAMPLE_H);
    const aspectPercent = bitmap.width > 0 ? Math.round((bitmap.height / bitmap.width) * 100) : undefined;
    return { ...signals, aspectPercent };
  } catch {
    // jsdom/不支持 createImageBitmap 的环境：不影响登记，后端退化为文件名分类
    return {};
  }
}

/**
 * 纯函数版式特征提取：输入灰度像素与尺寸，输出三个信号（可单测、不依赖 canvas）。
 *
 * 算法与后端 DocTypeClassifier 及 Python 取证脚本一致：自适应阈值（偏离中位色 > 35）、
 * 8×6 网格前 4 格集中度、横向长游程表格线检测。
 */
export function signalsFromGray(
  gray: Uint8Array,
  width: number,
  height: number,
): Pick<DocTypeImageSignals, 'inkRatio' | 'inkConcentration' | 'tableLineRatio'> {
  const sorted = Array.from(gray).sort((a, b) => a - b);
  const median = sorted[Math.floor(sorted.length / 2)];
  const fg = new Uint8Array(gray.length);
  let ink = 0;
  for (let i = 0; i < gray.length; i++) {
    if (Math.abs(gray[i] - median) > 35) {
      fg[i] = 1;
      ink++;
    }
  }
  const inkRatio = ink / gray.length;

  const cells: number[] = [];
  const cellW = width / GRID_W;
  const cellH = height / GRID_H;
  for (let gy = 0; gy < GRID_H; gy++) {
    for (let gx = 0; gx < GRID_W; gx++) {
      let count = 0;
      for (let y = Math.floor(gy * cellH); y < Math.floor((gy + 1) * cellH); y++) {
        for (let x = Math.floor(gx * cellW); x < Math.floor((gx + 1) * cellW); x++) {
          count += fg[y * width + x];
        }
      }
      cells.push(count);
    }
  }
  const totalInk = cells.reduce((a, b) => a + b, 0);
  const top4 = cells.sort((a, b) => b - a).slice(0, 4).reduce((a, b) => a + b, 0);
  const inkConcentration = totalInk > 0 ? top4 / totalInk : 0;

  let lineRows = 0;
  for (let y = 0; y < height; y++) {
    let run = 0;
    let max = 0;
    for (let x = 0; x < width; x++) {
      run = fg[y * width + x] ? run + 1 : 0;
      if (run > max) max = run;
    }
    if (max >= Math.floor(width * 0.375)) lineRows++;
  }
  return { inkRatio, inkConcentration, tableLineRatio: lineRows / height };
}
