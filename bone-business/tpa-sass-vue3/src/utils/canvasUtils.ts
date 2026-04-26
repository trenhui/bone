/**
 * 根据图片URL和旋转角度生成旋转后的图片文件
 * @param imageUrl 图片URL地址
 * @param rotationAngle 旋转角度（度数，正数为顺时针旋转）
 * @param quality 输出图片质量 (0-1，默认0.9)
 * @param outputFormat 输出格式 (默认'auto'，自动检测)
 * @returns Promise<Blob> 旋转后的图片Blob对象
 */
export async function rotateImage(
  imageUrl: string,
  rotationAngle: number,
  quality: number = 0.9,
  outputFormat: string = "auto"
): Promise<Blob> {
  return new Promise((resolve, reject) => {
    // 创建图片对象
    const img = new Image();

    // 处理跨域问题
    img.crossOrigin = "anonymous";

    img.onload = function () {
      try {
        // 创建canvas元素
        const canvas = document.createElement("canvas");
        const ctx = canvas.getContext("2d");

        if (!ctx) {
          reject(new Error("无法获取Canvas上下文"));
          return;
        }

        // 自动检测输出格式
        let finalOutputFormat = outputFormat;
        if (outputFormat === "auto") {
          // 根据原图URL判断格式，PNG/GIF支持透明度，使用PNG；其他使用JPEG
          const extension = imageUrl.toLowerCase().split(".").pop();
          if (
            extension === "png" ||
            extension === "gif" ||
            extension === "webp"
          ) {
            finalOutputFormat = "image/png";
          } else {
            finalOutputFormat = "image/jpeg";
          }
        }

        // 将角度转换为弧度
        const radian = (rotationAngle * Math.PI) / 180;

        // 计算旋转后的边界框大小
        const cos = Math.abs(Math.cos(radian));
        const sin = Math.abs(Math.sin(radian));
        const newWidth = img.width * cos + img.height * sin;
        const newHeight = img.width * sin + img.height * cos;

        // 设置canvas尺寸
        canvas.width = newWidth;
        canvas.height = newHeight;

        // 如果输出格式支持透明度，保持透明背景
        if (
          finalOutputFormat === "image/png" ||
          finalOutputFormat === "image/webp"
        ) {
          // 清除canvas，保持透明背景
          ctx.clearRect(0, 0, newWidth, newHeight);
        } else {
          // 对于JPEG格式，设置白色背景而不是黑色
          ctx.fillStyle = "#FFFFFF";
          ctx.fillRect(0, 0, newWidth, newHeight);
        }

        // 将坐标原点移动到canvas中心
        ctx.translate(newWidth / 2, newHeight / 2);

        // 应用旋转变换
        ctx.rotate(radian);

        // 绘制图片（图片中心对齐到旋转中心）
        ctx.drawImage(img, -img.width / 2, -img.height / 2);

        // 将canvas转换为Blob
        canvas.toBlob(
          (blob) => {
            if (blob) {
              resolve(blob);
            } else {
              reject(new Error("无法生成图片Blob"));
            }
          },
          finalOutputFormat,
          quality
        );
      } catch (error) {
        reject(error);
      }
    };

    img.onerror = function () {
      reject(new Error("图片加载失败"));
    };

    // 开始加载图片 添加时间戳，防止缓存
    const timestamp = Date.now();
    const url = `${imageUrl}?timestamp=${timestamp}`;

    img.src = url;
  });
}

/**
 * 压缩图片（减小文件大小）
 * @param blob 原始图片Blob
 * @param maxWidth 最大宽度
 * @param maxHeight 最大高度
 * @param quality 压缩质量 (0-1)
 * @returns Promise<Blob> 压缩后的Blob
 */
export function compressImage(
  blob: Blob,
  maxWidth: number = 1920,
  maxHeight: number = 1080,
  quality: number = 0.8
): Promise<Blob> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d");

    if (!ctx) {
      reject(new Error("无法获取Canvas上下文"));
      return;
    }

    img.onload = () => {
      // 计算缩放比例
      let { width, height } = img;

      if (width > maxWidth || height > maxHeight) {
        const ratio = Math.min(maxWidth / width, maxHeight / height);
        width *= ratio;
        height *= ratio;
      }

      canvas.width = width;
      canvas.height = height;

      // 绘制压缩后的图片
      ctx.drawImage(img, 0, 0, width, height);

      canvas.toBlob(
        (compressedBlob) => {
          if (compressedBlob) {
            resolve(compressedBlob);
          } else {
            reject(new Error("图片压缩失败"));
          }
        },
        blob.type,
        quality
      );
    };

    img.onerror = () => reject(new Error("图片加载失败"));
    img.src = URL.createObjectURL(blob);
  });
}

/**
 * 将Blob转换为File对象
 * @param blob Blob对象
 * @param fileName 文件名
 * @returns File对象
 */
export function blobToFile(blob: Blob, fileName: string): File {
  return new File([blob], fileName, { type: blob.type });
}

/**
 * 将Blob转换为Data URL
 * @param blob Blob对象
 * @returns Promise<string> Data URL字符串
 */
export function blobToDataURL(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = () => reject(new Error("转换为Data URL失败"));
    reader.readAsDataURL(blob);
  });
}

/**
 * 将Blob转换为ArrayBuffer
 * @param blob Blob对象
 * @returns Promise<ArrayBuffer>
 */
export function blobToArrayBuffer(blob: Blob): Promise<ArrayBuffer> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as ArrayBuffer);
    reader.onerror = () => reject(new Error("转换为ArrayBuffer失败"));
    reader.readAsArrayBuffer(blob);
  });
}

/**
 * 将Blob转换为Base64字符串
 * @param blob Blob对象
 * @returns Promise<string> Base64字符串（不包含data:前缀）
 */
export function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result as string;
      // 移除data:前缀，只返回base64部分
      const base64 = result.split(",")[1];
      resolve(base64);
    };
    reader.onerror = () => reject(new Error("转换为Base64失败"));
    reader.readAsDataURL(blob);
  });
}

/**
 * 下载Blob为文件
 * @param blob Blob对象
 * @param fileName 下载的文件名
 */
export function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

/**
 * 批量旋转图片
 * @param imageUrls 图片URL数组
 * @param rotationAngle 旋转角度
 * @param quality 图片质量
 * @param outputFormat 输出格式
 * @returns Promise<Blob[]> 旋转后的图片Blob数组
 */
export async function rotateImagesInBatch(
  imageUrls: string[],
  rotationAngle: number,
  quality: number = 0.9,
  outputFormat: string = "auto"
): Promise<Blob[]> {
  const promises = imageUrls.map((url) =>
    rotateImage(url, rotationAngle, quality, outputFormat)
  );
  return Promise.all(promises);
}
